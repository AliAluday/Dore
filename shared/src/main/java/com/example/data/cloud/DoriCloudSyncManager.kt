package com.example.data.cloud

import com.example.data.local.AdminAccountEntity
import com.example.data.local.BookingEntity
import com.example.data.local.DoriDao
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.local.ReviewEntity
import com.example.data.local.SeedData
import com.example.data.local.ServiceProviderEntity
import com.example.data.local.SupportMessageEntity
import com.example.data.local.SupportTicketEntity
import com.example.data.local.UserPreferencesDataStore
import com.example.data.local.VenueEntity
import com.example.data.model.UserProfileSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

enum class CloudSyncStatus {
    OFFLINE,
    SYNCING,
    CONNECTED,
    ERROR
}

data class CloudSyncUiState(
    val status: CloudSyncStatus = CloudSyncStatus.SYNCING,
    val userEmail: String = "",
    val userDisplayName: String = "",
    val errorMessage: String? = null,
    val lastSyncedAtMillis: Long = 0L
)

class DoriCloudSyncManager(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val dao: DoriDao,
    private val prefs: UserPreferencesDataStore,
    private val isControlApp: Boolean,
    private val scope: CoroutineScope
) {
    private val reconcileMutex = Mutex()
    private val pushMutex = Mutex()
    private val inFlightPushes = AtomicInteger(0)
    private val listeners = mutableListOf<ListenerRegistration>()
    private var activeUid: String? = null

    private fun launchPush(block: suspend () -> Unit) {
        inFlightPushes.incrementAndGet()
        scope.launch(Dispatchers.IO) {
            try {
                pushMutex.withLock {
                    block()
                }
            } finally {
                inFlightPushes.decrementAndGet()
            }
        }
    }

    private val _syncState = MutableStateFlow(CloudSyncUiState())
    val syncState: StateFlow<CloudSyncUiState> = _syncState.asStateFlow()

    private fun currentUid(): String? = auth.currentUser?.uid ?: activeUid

    private fun userRoot(uid: String): DocumentReference = firestore.collection("users").document(uid)
    private fun configDoc(uid: String): DocumentReference = userRoot(uid).collection("config").document("settings")
    private fun venuesCol(uid: String): CollectionReference = userRoot(uid).collection("venues")
    private fun bookingsCol(uid: String): CollectionReference = userRoot(uid).collection("bookings")
    private fun queueItemsCol(uid: String): CollectionReference = userRoot(uid).collection("queue_items")
    private fun reviewsCol(uid: String): CollectionReference = userRoot(uid).collection("reviews")
    private fun supportTicketsCol(uid: String): CollectionReference = userRoot(uid).collection("support_tickets")
    private fun supportMessagesCol(uid: String): CollectionReference = userRoot(uid).collection("support_messages")
    private fun adminsCol(uid: String): CollectionReference = userRoot(uid).collection("admins")
    private fun providersCol(uid: String): CollectionReference = userRoot(uid).collection("providers")

    fun start() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        if (activeUid == uid && listeners.isNotEmpty()) return
        stop()
        activeUid = uid
        _syncState.value = CloudSyncUiState(
            status = CloudSyncStatus.SYNCING,
            userEmail = user.email.orEmpty(),
            userDisplayName = user.displayName.orEmpty()
        )

        scope.launch(Dispatchers.IO) {
            bootstrapWorkspaceIfEmpty(uid)
            attachRealtimeListeners(uid)
        }
    }

    fun stop() {
        listeners.forEach { runCatching { it.remove() } }
        listeners.clear()
        activeUid = null
    }

    private suspend fun bootstrapWorkspaceIfEmpty(uid: String) {
        val cfgPath = "users/$uid/config/settings"
        try {
            val cfgExists = runCatching { configDoc(uid).get(Source.SERVER).await().exists() }
                .getOrElse { configDoc(uid).get(Source.DEFAULT).await().exists() }
            val venuesEmpty = runCatching { venuesCol(uid).limit(1).get(Source.SERVER).await().isEmpty }
                .getOrElse { venuesCol(uid).limit(1).get(Source.DEFAULT).await().isEmpty }

            val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()

            if (!cfgExists) {
                val settingsNow = prefs.userPreferences.first()
                val googleName = auth.currentUser?.displayName.orEmpty().trim()
                val seededSettings = if (settingsNow.userName.isBlank() && googleName.isNotBlank()) {
                    settingsNow.copy(userName = googleName)
                } else {
                    settingsNow
                }
                val meta = prefs.getControlSyncMetadata()
                writes += configDoc(uid) to DoriCloudMapper.configCreateMap(uid, seededSettings, meta)
            }

            if (venuesEmpty) {
                val localVenues = dao.getAllVenuesOnce().ifEmpty {
                    val seeded = SeedData.initialVenues()
                    dao.insertVenues(seeded)
                    seeded
                }
                val localReviews = dao.getAllReviewsOnce().ifEmpty {
                    val seeded = SeedData.initialReviews()
                    dao.insertReviews(seeded)
                    dao.getAllReviewsOnce()
                }
                val localQueue = dao.getAllQueueItemsOnce().ifEmpty {
                    val seeded = SeedData.initialQueueItems(localVenues)
                    dao.insertQueueItems(seeded)
                    dao.getAllQueueItemsOnce()
                }

                localVenues.forEach { v ->
                    writes += venuesCol(uid).document(DoriCloudMapper.venueDocId(v.id)) to
                        DoriCloudMapper.venueCreateMap(uid, v)
                }
                localReviews.forEach { r ->
                    val rDocId = DoriCloudMapper.reviewDocId(r.id, r.venueId, r.createdAt)
                    writes += reviewsCol(uid).document(rDocId) to
                        DoriCloudMapper.reviewCreateMap(uid, r)
                }
                localQueue.forEach { q ->
                    writes += queueItemsCol(uid).document(DoriCloudMapper.queueItemDocId(q.ticketCode)) to
                        DoriCloudMapper.queueItemCreateMap(uid, q, q.ticketCode)
                }
            }

            if (isControlApp) {
                val adminsEmpty = runCatching { adminsCol(uid).limit(1).get(Source.SERVER).await().isEmpty }
                    .getOrElse { adminsCol(uid).limit(1).get(Source.DEFAULT).await().isEmpty }
                if (adminsEmpty) {
                    val admins = dao.getAllAdminsOnce().ifEmpty {
                        val seeded = SeedData.initialAdmins()
                        dao.insertAdmins(seeded)
                        dao.getAllAdminsOnce()
                    }
                    admins.forEach { a ->
                        writes += adminsCol(uid).document(DoriCloudMapper.adminDocId(a.id)) to
                            DoriCloudMapper.adminCreateMap(uid, a)
                    }
                }

                val providersEmpty = runCatching { providersCol(uid).limit(1).get(Source.SERVER).await().isEmpty }
                    .getOrElse { providersCol(uid).limit(1).get(Source.DEFAULT).await().isEmpty }
                if (providersEmpty) {
                    val providers = dao.getAllProvidersOnce().ifEmpty {
                        val seeded = SeedData.initialProviders()
                        dao.insertProviders(seeded)
                        dao.getAllProvidersOnce()
                    }
                    providers.forEach { p ->
                        writes += providersCol(uid).document(DoriCloudMapper.providerDocId(p.id)) to
                            DoriCloudMapper.providerCreateMap(uid, p)
                    }
                }
            }

            commitChunked(writes)
            markConnected()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, cfgPath)
            markError(e)
        }
    }

    private fun attachRealtimeListeners(uid: String) {
        listeners += configDoc(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, "users/$uid/config/settings")
                markError(error)
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {
                scope.launch(Dispatchers.IO) {
                    reconcileMutex.withLock {
                        val decoded = DoriCloudMapper.toConfig(snap) ?: return@withLock
                        prefs.applyCloudConfigSnapshot(
                            settings = decoded.settings,
                            controlRevision = decoded.controlRevision,
                            queueResetRevision = decoded.queueResetRevision,
                            loyaltyRevision = decoded.loyaltyRevision
                        )
                    }
                    markConnected()
                }
            }
        }

        listeners += venuesCol(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users/$uid/venues")
                markError(error)
                return@addSnapshotListener
            }
            if (snap != null) {
                scope.launch(Dispatchers.IO) {
                    applyVenuesSnapshot(snap)
                    markConnected()
                }
            }
        }

        listeners += bookingsCol(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users/$uid/bookings")
                markError(error)
                return@addSnapshotListener
            }
            if (snap != null) {
                scope.launch(Dispatchers.IO) {
                    applyBookingsSnapshot(snap)
                    markConnected()
                }
            }
        }

        listeners += queueItemsCol(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users/$uid/queue_items")
                markError(error)
                return@addSnapshotListener
            }
            if (snap != null) {
                scope.launch(Dispatchers.IO) {
                    applyQueueItemsSnapshot(snap)
                    markConnected()
                }
            }
        }

        listeners += reviewsCol(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users/$uid/reviews")
                markError(error)
                return@addSnapshotListener
            }
            if (snap != null) {
                scope.launch(Dispatchers.IO) {
                    applyReviewsSnapshot(snap)
                    markConnected()
                }
            }
        }

        listeners += supportTicketsCol(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users/$uid/support_tickets")
                markError(error)
                return@addSnapshotListener
            }
            if (snap != null) {
                scope.launch(Dispatchers.IO) {
                    applySupportTicketsSnapshot(snap)
                    markConnected()
                }
            }
        }

        listeners += supportMessagesCol(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users/$uid/support_messages")
                markError(error)
                return@addSnapshotListener
            }
            if (snap != null) {
                scope.launch(Dispatchers.IO) {
                    applySupportMessagesSnapshot(snap)
                    markConnected()
                }
            }
        }

        if (isControlApp) {
            listeners += adminsCol(uid).addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "users/$uid/admins")
                    markError(error)
                    return@addSnapshotListener
                }
                if (snap != null) {
                    scope.launch(Dispatchers.IO) {
                        applyAdminsSnapshot(snap)
                        markConnected()
                    }
                }
            }

            listeners += providersCol(uid).addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "users/$uid/providers")
                    markError(error)
                    return@addSnapshotListener
                }
                if (snap != null) {
                    scope.launch(Dispatchers.IO) {
                        applyProvidersSnapshot(snap)
                        markConnected()
                    }
                }
            }
        }
    }

    suspend fun syncNow(): Result<Int> = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext Result.failure(IllegalStateException("يرجى تسجيل الدخول أولاً"))
        _syncState.update { it.copy(status = CloudSyncStatus.SYNCING, errorMessage = null) }
        var updates = 0
        try {
            val cfgSnap = configDoc(uid).get(Source.SERVER).await()
            if (cfgSnap.exists()) {
                DoriCloudMapper.toConfig(cfgSnap)?.let { decoded ->
                    reconcileMutex.withLock {
                        prefs.applyCloudConfigSnapshot(
                            decoded.settings,
                            decoded.controlRevision,
                            decoded.queueResetRevision,
                            decoded.loyaltyRevision
                        )
                    }
                    updates++
                }
            }
            if (applyVenuesSnapshot(venuesCol(uid).get(Source.SERVER).await())) updates++
            if (applyBookingsSnapshot(bookingsCol(uid).get(Source.SERVER).await())) updates++
            if (applyQueueItemsSnapshot(queueItemsCol(uid).get(Source.SERVER).await())) updates++
            if (applyReviewsSnapshot(reviewsCol(uid).get(Source.SERVER).await())) updates++
            if (applySupportTicketsSnapshot(supportTicketsCol(uid).get(Source.SERVER).await())) updates++
            if (applySupportMessagesSnapshot(supportMessagesCol(uid).get(Source.SERVER).await())) updates++
            if (isControlApp) {
                if (applyAdminsSnapshot(adminsCol(uid).get(Source.SERVER).await())) updates++
                if (applyProvidersSnapshot(providersCol(uid).get(Source.SERVER).await())) updates++
            }
            markConnected()
            Result.success(updates)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            markError(e)
            Result.failure(e)
        }
    }

    // ==================== Snapshot Reconcilers ====================

    private suspend fun applyVenuesSnapshot(snap: QuerySnapshot): Boolean = reconcileMutex.withLock {
        val remoteVenues = snap.documents.mapNotNull { DoriCloudMapper.toVenueEntity(it) }
        if (remoteVenues.isEmpty() && snap.metadata.isFromCache) return@withLock false
        val localSorted = dao.getAllVenuesOnce().sortedBy { it.id }
        val remoteSorted = remoteVenues.sortedBy { it.id }
        if (localSorted == remoteSorted) return@withLock false
        dao.replaceAllVenues(remoteSorted)
        true
    }

    private suspend fun applyBookingsSnapshot(snap: QuerySnapshot): Boolean = reconcileMutex.withLock {
        val remoteBookings = snap.documents.mapNotNull { DoriCloudMapper.toBookingEntity(it) }
        val remoteCodes = remoteBookings.map { it.ticketCode }.toSet()
        val localBookings = dao.getAllBookingsOnce()
        var changed = false

        if (!snap.metadata.isFromCache && !snap.metadata.hasPendingWrites() && inFlightPushes.get() == 0) {
            localBookings.filter { it.ticketCode !in remoteCodes }.forEach { stale ->
                dao.deleteBookingById(stale.id)
                changed = true
            }
        }

        val localByCode = dao.getAllBookingsOnce().associateBy { it.ticketCode }
        for (remote in remoteBookings) {
            val local = localByCode[remote.ticketCode]
            if (local == null) {
                dao.insertBooking(remote.copy(id = 0L))
                changed = true
            } else {
                val merged = remote.copy(id = local.id)
                if (merged != local) {
                    dao.updateBooking(merged)
                    changed = true
                }
            }
        }
        changed
    }

    private suspend fun applyQueueItemsSnapshot(snap: QuerySnapshot): Boolean = reconcileMutex.withLock {
        val remotePairs = snap.documents.mapNotNull { DoriCloudMapper.toQueueItemEntity(it) }
        val remoteCodes = remotePairs.map { it.first.ticketCode }.toSet()
        val localItems = dao.getAllQueueItemsOnce()
        var changed = false

        if (!snap.metadata.isFromCache && !snap.metadata.hasPendingWrites() && inFlightPushes.get() == 0) {
            localItems.filter { it.ticketCode !in remoteCodes }.forEach { stale ->
                dao.deleteQueueItemByTicketCode(stale.ticketCode)
                changed = true
            }
        }

        val localByCode = dao.getAllQueueItemsOnce().associateBy { it.ticketCode }
        for ((remoteItem, bookingCode) in remotePairs) {
            val matchedBookingId = dao.getBookingByTicketCode(bookingCode ?: remoteItem.ticketCode)?.id
                ?: remoteItem.bookingId
            val local = localByCode[remoteItem.ticketCode]
            if (local == null) {
                dao.insertQueueItem(remoteItem.copy(id = 0L, bookingId = matchedBookingId))
                changed = true
            } else {
                val merged = remoteItem.copy(id = local.id, bookingId = matchedBookingId)
                if (merged != local) {
                    dao.updateQueueItem(merged)
                    changed = true
                }
            }
        }
        changed
    }

    private suspend fun applyReviewsSnapshot(snap: QuerySnapshot): Boolean = reconcileMutex.withLock {
        val remoteReviews = snap.documents.mapNotNull { DoriCloudMapper.toReviewEntity(it) }
        if (remoteReviews.isEmpty() && snap.metadata.isFromCache) return@withLock false
        val localReviews = dao.getAllReviewsOnce()
        val localKeys = localReviews.map { Triple(it.venueId, it.authorName, it.createdAt) }.toSet()
        val remoteKeys = remoteReviews.map { Triple(it.venueId, it.authorName, it.createdAt) }.toSet()
        if (localKeys == remoteKeys && localReviews.size == remoteReviews.size) return@withLock false
        dao.replaceAllReviews(remoteReviews.map { it.copy(id = 0L) })
        true
    }

    private suspend fun applySupportTicketsSnapshot(snap: QuerySnapshot): Boolean = reconcileMutex.withLock {
        val remoteTickets = snap.documents.mapNotNull { DoriCloudMapper.toSupportTicketEntity(it, isControlApp) }
        val remoteIds = remoteTickets.map { it.externalId }.toSet()
        val localTickets = dao.getAllSupportTicketsOnce()
        var changed = false

        if (!snap.metadata.isFromCache && !snap.metadata.hasPendingWrites() && inFlightPushes.get() == 0) {
            localTickets.filter { it.externalId !in remoteIds }.forEach { stale ->
                dao.deleteSupportTicketById(stale.id)
                changed = true
            }
        }

        val localByExtId = dao.getAllSupportTicketsOnce().associateBy { it.externalId }
        for (remote in remoteTickets) {
            val local = localByExtId[remote.externalId]
            if (local == null) {
                dao.insertSupportTicket(remote.copy(id = 0L))
                changed = true
            } else {
                val merged = remote.copy(id = local.id)
                if (merged != local) {
                    dao.updateSupportTicket(merged)
                    changed = true
                }
            }
        }
        changed
    }

    private suspend fun applySupportMessagesSnapshot(snap: QuerySnapshot): Boolean = reconcileMutex.withLock {
        val remoteMessages = snap.documents.mapNotNull { DoriCloudMapper.toSupportMessageEntity(it, isControlApp) }
        val remoteIds = remoteMessages.map { it.externalMessageId }.toSet()
        val localMessages = dao.getAllSupportMessagesOnce()
        var changed = false

        if (!snap.metadata.isFromCache && !snap.metadata.hasPendingWrites() && inFlightPushes.get() == 0) {
            localMessages.filter { it.externalMessageId !in remoteIds }.forEach { stale ->
                dao.deleteSupportMessageById(stale.id)
                changed = true
            }
        }

        val localByMsgId = dao.getAllSupportMessagesOnce().associateBy { it.externalMessageId }
        for (remote in remoteMessages) {
            if (remote.externalMessageId !in localByMsgId) {
                dao.insertSupportMessage(remote.copy(id = 0L))
                changed = true
            }
        }
        changed
    }

    private suspend fun applyAdminsSnapshot(snap: QuerySnapshot): Boolean = reconcileMutex.withLock {
        val remoteAdmins = snap.documents.mapNotNull { DoriCloudMapper.toAdminEntity(it) }
        if (remoteAdmins.isEmpty() && snap.metadata.isFromCache) return@withLock false
        val localSorted = dao.getAllAdminsOnce().sortedBy { it.id }
        val remoteSorted = remoteAdmins.sortedBy { it.id }
        if (localSorted == remoteSorted) return@withLock false
        dao.deleteAllAdmins()
        if (remoteSorted.isNotEmpty()) dao.insertAdmins(remoteSorted)
        true
    }

    private suspend fun applyProvidersSnapshot(snap: QuerySnapshot): Boolean = reconcileMutex.withLock {
        val remoteProviders = snap.documents.mapNotNull { DoriCloudMapper.toProviderEntity(it) }
        if (remoteProviders.isEmpty() && snap.metadata.isFromCache) return@withLock false
        val localSorted = dao.getAllProvidersOnce().sortedBy { it.id }
        val remoteSorted = remoteProviders.sortedBy { it.id }
        if (localSorted == remoteSorted) return@withLock false
        dao.replaceAllProviders(remoteSorted)
        true
    }

    // ==================== Push Mutations ====================

    fun pushConfig(settings: UserProfileSettings, meta: UserPreferencesDataStore.ControlSyncMetadata) {
        val uid = currentUid() ?: return
        val docRef = configDoc(uid)
        val path = "users/$uid/config/settings"
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = docRef,
                path = path,
                updateFields = DoriCloudMapper.configMutablePatchMap(settings, meta),
                createFields = DoriCloudMapper.configCreateMap(uid, settings, meta)
            )
        }
    }

    fun pushVenue(venue: VenueEntity) {
        val uid = currentUid() ?: return
        val docId = DoriCloudMapper.venueDocId(venue.id)
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = venuesCol(uid).document(docId),
                path = "users/$uid/venues/$docId",
                updateFields = DoriCloudMapper.venueMutableMap(venue),
                createFields = DoriCloudMapper.venueCreateMap(uid, venue)
            )
        }
    }

    fun pushVenuesBatch(venues: List<VenueEntity>) {
        val uid = currentUid() ?: return
        launchPush {
            try {
                venues.chunked(350).forEach { chunk ->
                    val batch: WriteBatch = firestore.batch()
                    chunk.forEach { v ->
                        batch.update(
                            venuesCol(uid).document(DoriCloudMapper.venueDocId(v.id)),
                            DoriCloudMapper.venueMutableMap(v)
                        )
                    }
                    batch.commit().await()
                }
                markConnected()
            } catch (e: Exception) {
                venues.forEach { v ->
                    val docId = DoriCloudMapper.venueDocId(v.id)
                    upsertWithUpdateThenCreate(
                        docRef = venuesCol(uid).document(docId),
                        path = "users/$uid/venues/$docId",
                        updateFields = DoriCloudMapper.venueMutableMap(v),
                        createFields = DoriCloudMapper.venueCreateMap(uid, v)
                    )
                }
            }
        }
    }

    fun deleteVenue(venueId: Int) {
        val uid = currentUid() ?: return
        val docId = DoriCloudMapper.venueDocId(venueId)
        launchPush {
            try {
                venuesCol(uid).document(docId).delete().await()
                val qDocs = queueItemsCol(uid).whereEqualTo("venueId", venueId).get().await()
                qDocs.documents.chunked(350).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "users/$uid/venues/$docId")
                markError(e)
            }
        }
    }

    fun pushBooking(booking: BookingEntity, onlyRatingUpdate: Boolean = false) {
        val uid = currentUid() ?: return
        val docId = DoriCloudMapper.bookingDocId(booking.ticketCode)
        val path = "users/$uid/bookings/$docId"
        val docRef = bookingsCol(uid).document(docId)
        val updateMap = if (onlyRatingUpdate) {
            DoriCloudMapper.bookingUpdateFinishedReviewMap(booking.userRating, booking.userReview)
        } else {
            DoriCloudMapper.bookingUpdateActiveMap(booking)
        }
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = docRef,
                path = path,
                updateFields = updateMap,
                createFields = DoriCloudMapper.bookingCreateMap(uid, booking)
            )
        }
    }

    fun pushQueueItem(item: MerchantQueueItemEntity, bookingTicketCode: String? = null) {
        val uid = currentUid() ?: return
        val docId = DoriCloudMapper.queueItemDocId(item.ticketCode)
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = queueItemsCol(uid).document(docId),
                path = "users/$uid/queue_items/$docId",
                updateFields = DoriCloudMapper.queueItemUpdateMap(item.status, item.bookingId, bookingTicketCode ?: item.ticketCode),
                createFields = DoriCloudMapper.queueItemCreateMap(uid, item, bookingTicketCode ?: item.ticketCode)
            )
        }
    }

    fun pushReview(review: ReviewEntity) {
        val uid = currentUid() ?: return
        val docId = DoriCloudMapper.reviewDocId(review.id, review.venueId, review.createdAt)
        val path = "users/$uid/reviews/$docId"
        launchPush {
            try {
                reviewsCol(uid).document(docId).set(DoriCloudMapper.reviewCreateMap(uid, review)).await()
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, path)
                markError(e)
            }
        }
    }

    fun pushSupportTicket(ticket: SupportTicketEntity) {
        val uid = currentUid() ?: return
        val docId = ticket.externalId
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = supportTicketsCol(uid).document(docId),
                path = "users/$uid/support_tickets/$docId",
                updateFields = DoriCloudMapper.supportTicketUpdateMap(ticket),
                createFields = DoriCloudMapper.supportTicketCreateMap(uid, ticket)
            )
        }
    }

    fun pushSupportMessage(message: SupportMessageEntity) {
        val uid = currentUid() ?: return
        val docId = message.externalMessageId
        val path = "users/$uid/support_messages/$docId"
        val docRef = supportMessagesCol(uid).document(docId)
        launchPush {
            try {
                if (!docRef.get().await().exists()) {
                    docRef.set(DoriCloudMapper.supportMessageCreateMap(uid, message)).await()
                }
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, path)
                markError(e)
            }
        }
    }

    fun pushAdmin(admin: AdminAccountEntity) {
        if (!isControlApp) return
        val uid = currentUid() ?: return
        val docId = DoriCloudMapper.adminDocId(admin.id)
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = adminsCol(uid).document(docId),
                path = "users/$uid/admins/$docId",
                updateFields = DoriCloudMapper.adminMutableMap(admin),
                createFields = DoriCloudMapper.adminCreateMap(uid, admin)
            )
        }
    }

    fun deleteAdmin(adminId: Long) {
        if (!isControlApp) return
        val uid = currentUid() ?: return
        val docId = DoriCloudMapper.adminDocId(adminId)
        launchPush {
            try {
                adminsCol(uid).document(docId).delete().await()
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "users/$uid/admins/$docId")
                markError(e)
            }
        }
    }

    fun pushProvider(provider: ServiceProviderEntity) {
        if (!isControlApp) return
        val uid = currentUid() ?: return
        val docId = DoriCloudMapper.providerDocId(provider.id)
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = providersCol(uid).document(docId),
                path = "users/$uid/providers/$docId",
                updateFields = DoriCloudMapper.providerMutableMap(provider),
                createFields = DoriCloudMapper.providerCreateMap(uid, provider)
            )
        }
    }

    fun deleteProvider(providerId: Long) {
        if (!isControlApp) return
        val uid = currentUid() ?: return
        val docId = DoriCloudMapper.providerDocId(providerId)
        launchPush {
            try {
                providersCol(uid).document(docId).delete().await()
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "users/$uid/providers/$docId")
                markError(e)
            }
        }
    }

    suspend fun resetQueuesInCloud(
        resetVenues: List<VenueEntity>,
        settings: UserProfileSettings,
        meta: UserPreferencesDataStore.ControlSyncMetadata
    ) {
        val uid = currentUid() ?: return
        try {
            deleteAllInCollection(bookingsCol(uid))
            deleteAllInCollection(queueItemsCol(uid))
            pushVenuesBatch(resetVenues)
            pushConfig(settings, meta)
            markConnected()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/resetQueues")
            markError(e)
        }
    }

    suspend fun factoryResetInCloud(
        venues: List<VenueEntity>,
        reviews: List<ReviewEntity>,
        queueItems: List<MerchantQueueItemEntity>,
        admins: List<AdminAccountEntity>,
        providers: List<ServiceProviderEntity>,
        settings: UserProfileSettings,
        meta: UserPreferencesDataStore.ControlSyncMetadata
    ) {
        val uid = currentUid() ?: return
        try {
            deleteAllInCollection(bookingsCol(uid))
            deleteAllInCollection(queueItemsCol(uid))
            deleteAllInCollection(reviewsCol(uid))
            deleteAllInCollection(supportMessagesCol(uid))
            deleteAllInCollection(supportTicketsCol(uid))
            deleteAllInCollection(venuesCol(uid))
            if (isControlApp) {
                deleteAllInCollection(adminsCol(uid))
                deleteAllInCollection(providersCol(uid))
            }

            val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()
            venues.forEach { v ->
                writes += venuesCol(uid).document(DoriCloudMapper.venueDocId(v.id)) to
                    DoriCloudMapper.venueCreateMap(uid, v)
            }
            reviews.forEach { r ->
                val rDocId = DoriCloudMapper.reviewDocId(r.id, r.venueId, r.createdAt)
                writes += reviewsCol(uid).document(rDocId) to
                    DoriCloudMapper.reviewCreateMap(uid, r)
            }
            queueItems.forEach { q ->
                writes += queueItemsCol(uid).document(DoriCloudMapper.queueItemDocId(q.ticketCode)) to
                    DoriCloudMapper.queueItemCreateMap(uid, q, q.ticketCode)
            }
            if (isControlApp) {
                admins.forEach { a ->
                    writes += adminsCol(uid).document(DoriCloudMapper.adminDocId(a.id)) to
                        DoriCloudMapper.adminCreateMap(uid, a)
                }
                providers.forEach { p ->
                    writes += providersCol(uid).document(DoriCloudMapper.providerDocId(p.id)) to
                        DoriCloudMapper.providerCreateMap(uid, p)
                }
            }
            commitChunked(writes)
            pushConfig(settings, meta)
            markConnected()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/factoryReset")
            markError(e)
        }
    }

    // ==================== Internal Helpers ====================

    private suspend fun upsertWithUpdateThenCreate(
        docRef: DocumentReference,
        path: String,
        updateFields: Map<String, Any?>,
        createFields: Map<String, Any?>
    ) {
        try {
            docRef.update(updateFields).await()
            markConnected()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                try {
                    docRef.set(createFields).await()
                    markConnected()
                } catch (createEx: Exception) {
                    handleFirestoreError(createEx, OperationType.CREATE, path)
                    markError(createEx)
                }
            } else {
                handleFirestoreError(e, OperationType.UPDATE, path)
                markError(e)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            markError(e)
        }
    }

    private suspend fun deleteAllInCollection(col: CollectionReference) {
        val docs = col.get().await().documents
        docs.chunked(350).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private suspend fun commitChunked(writes: List<Pair<DocumentReference, Map<String, Any?>>>) {
        writes.chunked(350).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().await()
        }
    }

    private fun markConnected() {
        _syncState.update {
            it.copy(
                status = CloudSyncStatus.CONNECTED,
                errorMessage = null,
                lastSyncedAtMillis = System.currentTimeMillis()
            )
        }
    }

    private fun markError(error: Exception) {
        _syncState.update {
            it.copy(
                status = CloudSyncStatus.ERROR,
                errorMessage = toFriendlyFirestoreMessage(error)
            )
        }
    }
}
