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
import com.example.data.model.BookingApprovalStatus
import com.example.data.model.BookingStatus
import com.example.data.model.LiveTurnAlert
import com.example.data.model.QueueItemStatus
import com.example.data.model.TicketStage
import com.example.data.model.UserAppControlConfig
import com.example.data.model.UserProfileSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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

enum class AdminAccessState {
    CHECKING,
    AUTHORIZED,
    UNAUTHORIZED
}

data class CloudSyncUiState(
    val status: CloudSyncStatus = CloudSyncStatus.SYNCING,
    val adminAccess: AdminAccessState = AdminAccessState.CHECKING,
    val userUid: String = "",
    val userEmail: String = "",
    val userDisplayName: String = "",
    val authorizedAdmins: List<CloudAdminUser> = emptyList(),
    val errorMessage: String? = null,
    val lastSyncedAtMillis: Long = 0L
)

data class AtomicBookingResult(
    val booking: BookingEntity,
    val queueItem: MerchantQueueItemEntity,
    val updatedVenue: VenueEntity
)

class DoriCloudSyncManager(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val dao: DoriDao,
    private val prefs: UserPreferencesDataStore,
    private val isControlApp: Boolean,
    private val scope: CoroutineScope,
    private val onRealtimeBookingAlert: ((LiveTurnAlert) -> Unit)? = null
) {
    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "alialhadidia2008@gmail.com"
    }

    private val reconcileMutex = Mutex()
    private val pushMutex = Mutex()
    private val inFlightPushes = AtomicInteger(0)
    private val listeners = mutableListOf<ListenerRegistration>()
    private var activeUid: String? = null

    private val _syncState = MutableStateFlow(
        CloudSyncUiState(
            adminAccess = if (isControlApp) AdminAccessState.CHECKING else AdminAccessState.AUTHORIZED
        )
    )
    val syncState: StateFlow<CloudSyncUiState> = _syncState.asStateFlow()

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

    fun currentUid(): String? = auth.currentUser?.uid ?: activeUid
    private fun currentEmail(): String = auth.currentUser?.email.orEmpty()

    // ==================== Shared Top-Level Collections ====================

    private fun platformConfigDoc(): DocumentReference = firestore.collection("platform").document("settings")
    private fun userProfileDoc(uid: String): DocumentReference = firestore.collection("users").document(uid)
    private fun venuesCol(): CollectionReference = firestore.collection("venues")
    private fun bookingsCol(): CollectionReference = firestore.collection("bookings")
    private fun queueItemsCol(): CollectionReference = firestore.collection("queue_items")
    private fun reviewsCol(): CollectionReference = firestore.collection("reviews")
    private fun supportTicketsCol(): CollectionReference = firestore.collection("support_tickets")
    private fun supportMessagesCol(): CollectionReference = firestore.collection("support_messages")
    private fun adminUsersCol(): CollectionReference = firestore.collection("admin_users")
    private fun adminsCol(): CollectionReference = firestore.collection("admins")
    private fun providersCol(): CollectionReference = firestore.collection("providers")

    private fun bookingsQuery(uid: String): Query =
        if (isControlApp) bookingsCol() else bookingsCol().whereEqualTo("userId", uid)

    private fun queueItemsQuery(uid: String): Query =
        if (isControlApp) queueItemsCol() else queueItemsCol().whereEqualTo("userId", uid)

    private fun supportTicketsQuery(uid: String): Query =
        if (isControlApp) supportTicketsCol() else supportTicketsCol().whereEqualTo("userId", uid)

    private fun supportMessagesQuery(uid: String): Query =
        if (isControlApp) supportMessagesCol() else supportMessagesCol().whereEqualTo("userId", uid)

    fun start() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        if (activeUid == uid && listeners.isNotEmpty()) return
        stop()
        activeUid = uid
        _syncState.value = CloudSyncUiState(
            status = CloudSyncStatus.SYNCING,
            adminAccess = if (isControlApp) AdminAccessState.CHECKING else AdminAccessState.AUTHORIZED,
            userUid = uid,
            userEmail = user.email.orEmpty(),
            userDisplayName = user.displayName.orEmpty()
        )

        scope.launch(Dispatchers.IO) {
            if (isControlApp) {
                val authorized = checkControlAdminAccess(uid, user.email.orEmpty(), user.isEmailVerified)
                if (!authorized) {
                    _syncState.update {
                        it.copy(
                            status = CloudSyncStatus.ERROR,
                            adminAccess = AdminAccessState.UNAUTHORIZED,
                            errorMessage = "هذا الحساب غير مخوّل للدخول إلى نسخة التحكم"
                        )
                    }
                    return@launch
                }
                _syncState.update { it.copy(adminAccess = AdminAccessState.AUTHORIZED, errorMessage = null) }
                bootstrapControlWorkspaceIfEmpty(uid)
            } else {
                ensureUserProfileExists(uid)
                if (checkControlAdminAccess(uid, user.email.orEmpty(), user.isEmailVerified)) {
                    bootstrapControlWorkspaceIfEmpty(uid)
                }
            }
            attachRealtimeListeners(uid)
        }
    }

    fun retryAdminAccessCheck() {
        val user = auth.currentUser ?: return
        stop()
        start()
    }

    fun stop() {
        listeners.forEach { runCatching { it.remove() } }
        listeners.clear()
        activeUid = null
    }

    suspend fun clearLocalSessionCache() = withContext(Dispatchers.IO + NonCancellable) {
        stop()
        reconcileMutex.withLock {
            dao.deleteAllBookings()
            dao.deleteAllQueueItems()
            dao.deleteAllSupportMessages()
            dao.deleteAllSupportTickets()
            prefs.clearUserSessionPreferences()
        }
    }

    private suspend fun checkControlAdminAccess(
        uid: String,
        email: String,
        isEmailVerified: Boolean
    ): Boolean {
        if (isEmailVerified && email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)) {
            return true
        }
        return try {
            val doc = runCatching { adminUsersCol().document(uid).get(Source.SERVER).await() }
                .getOrElse { adminUsersCol().document(uid).get(Source.DEFAULT).await() }
            doc.exists()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "admin_users/$uid")
            false
        }
    }

    private suspend fun ensureUserProfileExists(uid: String) {
        val path = "users/$uid"
        try {
            val doc = runCatching { userProfileDoc(uid).get(Source.SERVER).await() }
                .getOrElse { userProfileDoc(uid).get(Source.DEFAULT).await() }
            if (!doc.exists()) {
                val settingsNow = prefs.userPreferences.first()
                val googleName = auth.currentUser?.displayName.orEmpty().trim()
                val initial = settingsNow.copy(
                    userName = settingsNow.userName.ifBlank { googleName },
                    loyaltyPoints = 0
                )
                userProfileDoc(uid).set(
                    DoriCloudMapper.userProfileCreateMap(uid, currentEmail(), initial)
                ).await()
            } else {
                DoriCloudMapper.toUserProfile(doc)?.let { remoteProfile ->
                    reconcileMutex.withLock {
                        prefs.applyUserProfileSnapshot(remoteProfile)
                    }
                }
            }
            markConnected()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            markError(e)
        }
    }

    private suspend fun bootstrapControlWorkspaceIfEmpty(uid: String) {
        val cfgPath = "platform/settings"
        try {
            val cfgExists = runCatching { platformConfigDoc().get(Source.SERVER).await().exists() }
                .getOrElse { platformConfigDoc().get(Source.DEFAULT).await().exists() }
            val venuesEmpty = runCatching { venuesCol().limit(1).get(Source.SERVER).await().isEmpty }
                .getOrElse { venuesCol().limit(1).get(Source.DEFAULT).await().isEmpty }

            val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()

            if (!cfgExists) {
                val meta = prefs.getControlSyncMetadata()
                writes += platformConfigDoc() to DoriCloudMapper.platformConfigCreateMap(
                    UserAppControlConfig(),
                    meta
                )
            }

            if (venuesEmpty) {
                // Production cloud catalog: seed real venues with clean zeroed live queues (no fake walk-ins!)
                val cleanCatalogVenues = SeedData.initialVenues().map { venue ->
                    venue.copy(
                        currentServingNumber = 0,
                        nextTicketNumber = 1,
                        servedTodayCount = 0,
                        waitingCount = 0,
                        isFavorite = false
                    )
                }
                cleanCatalogVenues.forEach { v ->
                    writes += venuesCol().document(DoriCloudMapper.venueDocId(v.id)) to
                        DoriCloudMapper.venueCreateMap(v)
                }
                val reviewsEmpty = runCatching { reviewsCol().limit(1).get(Source.SERVER).await().isEmpty }
                    .getOrElse { reviewsCol().limit(1).get(Source.DEFAULT).await().isEmpty }
                if (reviewsEmpty) {
                    val initialReviews = SeedData.initialReviews()
                    initialReviews.forEachIndexed { idx, r ->
                        val stableRev = r.copy(id = (idx + 1).toLong())
                        val rDocId = DoriCloudMapper.reviewDocId(stableRev.id, stableRev.venueId, stableRev.createdAt)
                        writes += reviewsCol().document(rDocId) to
                            DoriCloudMapper.reviewCreateMap(uid, stableRev)
                    }
                }
            }

            val adminsEmpty = runCatching { adminsCol().limit(1).get(Source.SERVER).await().isEmpty }
                .getOrElse { adminsCol().limit(1).get(Source.DEFAULT).await().isEmpty }
            if (adminsEmpty) {
                SeedData.initialAdmins().forEach { a ->
                    writes += adminsCol().document(DoriCloudMapper.adminDocId(a.id)) to
                        DoriCloudMapper.adminCreateMap(a)
                }
            }

            val providersEmpty = runCatching { providersCol().limit(1).get(Source.SERVER).await().isEmpty }
                .getOrElse { providersCol().limit(1).get(Source.DEFAULT).await().isEmpty }
            if (providersEmpty) {
                SeedData.initialProviders().forEach { p ->
                    writes += providersCol().document(DoriCloudMapper.providerDocId(p.id)) to
                        DoriCloudMapper.providerCreateMap(p)
                }
            }

            // Ensure bootstrapped admin record is also registered in /admin_users/{uid} for transparency in UI
            val selfAdminExists = runCatching { adminUsersCol().document(uid).get(Source.SERVER).await().exists() }
                .getOrElse { false }
            if (!selfAdminExists && currentEmail().equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)) {
                writes += adminUsersCol().document(uid) to DoriCloudMapper.adminUserCreateMap(
                    uid = uid,
                    email = currentEmail(),
                    displayName = auth.currentUser?.displayName.orEmpty().ifBlank { "المدير العام" },
                    role = "SUPER_ADMIN"
                )
            }

            commitChunked(writes)
            markConnected()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, cfgPath)
            markError(e)
        }
    }

    private fun attachRealtimeListeners(uid: String) {
        listeners += platformConfigDoc().addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, "platform/settings")
                markError(error)
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {
                scope.launch(Dispatchers.IO) {
                    reconcileMutex.withLock {
                        val decoded = DoriCloudMapper.toPlatformConfig(snap) ?: return@withLock
                        prefs.applyPlatformConfigSnapshot(
                            controlConfig = decoded.controlConfig,
                            controlRevision = decoded.controlRevision,
                            queueResetRevision = decoded.queueResetRevision
                        )
                    }
                    markConnected()
                }
            }
        }

        if (!isControlApp) {
            listeners += userProfileDoc(uid).addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, "users/$uid")
                    markError(error)
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    scope.launch(Dispatchers.IO) {
                        reconcileMutex.withLock {
                            val profile = DoriCloudMapper.toUserProfile(snap) ?: return@withLock
                            prefs.applyUserProfileSnapshot(profile)
                            applyFavoritesToLocalVenues(profile.favoriteVenueIds)
                        }
                        markConnected()
                    }
                }
            }
        }

        listeners += venuesCol().addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "venues")
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

        listeners += bookingsQuery(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "bookings")
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

        listeners += queueItemsQuery(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "queue_items")
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

        listeners += reviewsCol().addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "reviews")
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

        listeners += supportTicketsQuery(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "support_tickets")
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

        listeners += supportMessagesQuery(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "support_messages")
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
            listeners += adminUsersCol().addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "admin_users")
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val list = snap.documents.mapNotNull { DoriCloudMapper.toCloudAdminUser(it) }
                        .sortedByDescending { it.role == "SUPER_ADMIN" }
                    _syncState.update { it.copy(authorizedAdmins = list) }
                }
            }

            listeners += adminsCol().addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "admins")
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

            listeners += providersCol().addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "providers")
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
        if (isControlApp && _syncState.value.adminAccess != AdminAccessState.AUTHORIZED) {
            return@withContext Result.failure(IllegalStateException("الحساب غير مخوّل للإدارة"))
        }
        _syncState.update { it.copy(status = CloudSyncStatus.SYNCING, errorMessage = null) }
        var updates = 0
        try {
            val cfgSnap = platformConfigDoc().get(Source.SERVER).await()
            if (cfgSnap.exists()) {
                DoriCloudMapper.toPlatformConfig(cfgSnap)?.let { decoded ->
                    reconcileMutex.withLock {
                        prefs.applyPlatformConfigSnapshot(
                            decoded.controlConfig,
                            decoded.controlRevision,
                            decoded.queueResetRevision
                        )
                    }
                    updates++
                }
            }
            if (!isControlApp) {
                val userSnap = userProfileDoc(uid).get(Source.SERVER).await()
                if (userSnap.exists()) {
                    DoriCloudMapper.toUserProfile(userSnap)?.let { profile ->
                        reconcileMutex.withLock {
                            prefs.applyUserProfileSnapshot(profile)
                            applyFavoritesToLocalVenues(profile.favoriteVenueIds)
                        }
                        updates++
                    }
                }
            }
            if (applyVenuesSnapshot(venuesCol().get(Source.SERVER).await())) updates++
            if (applyBookingsSnapshot(bookingsQuery(uid).get(Source.SERVER).await())) updates++
            if (applyQueueItemsSnapshot(queueItemsQuery(uid).get(Source.SERVER).await())) updates++
            if (applyReviewsSnapshot(reviewsCol().get(Source.SERVER).await())) updates++
            if (applySupportTicketsSnapshot(supportTicketsQuery(uid).get(Source.SERVER).await())) updates++
            if (applySupportMessagesSnapshot(supportMessagesQuery(uid).get(Source.SERVER).await())) updates++
            if (isControlApp) {
                if (applyAdminsSnapshot(adminsCol().get(Source.SERVER).await())) updates++
                if (applyProvidersSnapshot(providersCol().get(Source.SERVER).await())) updates++
            }
            markConnected()
            Result.success(updates)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "syncNow")
            markError(e)
            Result.failure(e)
        }
    }

    // ==================== Atomic Transactional Operations ====================

    suspend fun bookTicketAtomically(
        venueId: Int,
        customerName: String,
        customerPhone: String,
        selectedOption: String,
        notes: String,
        optionalInfo: String,
        customPriceDinar: Int?
    ): Result<AtomicBookingResult> = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext Result.failure(IllegalStateException("يرجى تسجيل الدخول بحساب Google أولاً"))
        val venueRef = venuesCol().document(DoriCloudMapper.venueDocId(venueId))
        inFlightPushes.incrementAndGet()
        try {
            val favIds = prefs.userPreferences.first().favoriteVenueIds
            val result = firestore.runTransaction { tx ->
                val venueSnap = tx.get(venueRef)
                val liveVenue = DoriCloudMapper.toVenueEntity(venueSnap, favIds)
                    ?: throw IllegalStateException("المكان غير موجود في قاعدة البيانات")
                if (!liveVenue.isOpen || !liveVenue.isAcceptingBookings) {
                    throw IllegalStateException("هذا المكان متوقف عن استقبال الحجوزات حالياً")
                }

                val ticketNum = liveVenue.nextTicketNumber.coerceAtLeast(1)
                val ticketCode = "NIN-${liveVenue.id}-$ticketNum"
                val peopleAhead = liveVenue.waitingCount.coerceAtLeast(0) +
                    if (liveVenue.currentServingNumber > 0) 1 else 0
                val stage = when {
                    peopleAhead == 0 -> TicketStage.YOUR_TURN
                    peopleAhead <= 1 -> TicketStage.APPROACHING
                    else -> TicketStage.MONITORED
                }
                val etaMinutes = if (peopleAhead == 0) 0 else (peopleAhead * liveVenue.avgMinutesPerPerson).coerceAtLeast(3)
                val now = System.currentTimeMillis()

                val updatedServing = if (peopleAhead == 0) ticketNum else liveVenue.currentServingNumber
                val updatedWaiting = liveVenue.waitingCount.coerceAtLeast(0) + if (peopleAhead > 0) 1 else 0
                val updatedVenue = liveVenue.copy(
                    currentServingNumber = updatedServing,
                    nextTicketNumber = ticketNum + 1,
                    waitingCount = updatedWaiting
                )

                val booking = BookingEntity(
                    id = 0L,
                    ticketCode = ticketCode,
                    queueNumber = ticketNum,
                    venueId = liveVenue.id,
                    venueName = liveVenue.name,
                    venueCity = "${liveVenue.city} • ${liveVenue.area}",
                    venueCategory = liveVenue.category,
                    venuePhone = liveVenue.phone,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    selectedOption = selectedOption.ifBlank { liveVenue.customOptions.firstOrNull() ?: "خدمة قياسية" },
                    notes = notes,
                    optionalInfo = optionalInfo,
                    priceDinar = customPriceDinar ?: liveVenue.priceDinar,
                    status = BookingStatus.ACTIVE.name,
                    stage = stage.name,
                    approvalStatus = BookingApprovalStatus.PENDING.name,
                    peopleAhead = peopleAhead,
                    estimatedMinutes = etaMinutes,
                    createdAt = now,
                    ownerUid = uid
                )

                val queueItem = MerchantQueueItemEntity(
                    id = 0L,
                    venueId = liveVenue.id,
                    ticketNumber = ticketNum,
                    ticketCode = ticketCode,
                    customerName = booking.customerName,
                    customerPhone = booking.customerPhone,
                    selectedOption = booking.selectedOption,
                    status = if (peopleAhead == 0) QueueItemStatus.SERVING.name else QueueItemStatus.WAITING.name,
                    bookingId = now,
                    createdAt = now,
                    ownerUid = uid
                )

                tx.update(
                    venueRef,
                    DoriCloudMapper.venueUserCounterUpdateMap(
                        currentServingNumber = updatedVenue.currentServingNumber,
                        nextTicketNumber = updatedVenue.nextTicketNumber,
                        waitingCount = updatedVenue.waitingCount
                    )
                )
                tx.set(
                    bookingsCol().document(DoriCloudMapper.bookingDocId(ticketCode)),
                    DoriCloudMapper.bookingCreateMap(uid, booking.copy(id = now))
                )
                tx.set(
                    queueItemsCol().document(DoriCloudMapper.queueItemDocId(ticketCode)),
                    DoriCloudMapper.queueItemCreateMap(uid, queueItem.copy(id = now), ticketCode)
                )

                AtomicBookingResult(booking, queueItem, updatedVenue)
            }.await()

            markConnected()
            Result.success(result)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "venues/venue_$venueId")
            markError(e)
            Result.failure(e)
        } finally {
            inFlightPushes.decrementAndGet()
        }
    }

    suspend fun <T> withLocalReconcileLock(block: suspend () -> T): T = reconcileMutex.withLock {
        block()
    }

    suspend fun persistAtomicBookingLocally(atomic: AtomicBookingResult): BookingEntity =
        reconcileMutex.withLock {
            val existingBooking = dao.getBookingByTicketCode(atomic.booking.ticketCode)
            val savedBooking = if (existingBooking == null) {
                val insertedId = dao.insertBooking(atomic.booking.copy(id = 0L))
                atomic.booking.copy(id = insertedId)
            } else {
                val merged = atomic.booking.copy(id = existingBooking.id)
                if (merged != existingBooking) dao.updateBooking(merged)
                merged
            }

            val existingQueueItem = dao.getQueueItemByTicketCode(atomic.queueItem.ticketCode)
            if (existingQueueItem == null) {
                dao.insertQueueItem(atomic.queueItem.copy(id = 0L, bookingId = savedBooking.id))
            } else {
                val mergedQueue = atomic.queueItem.copy(
                    id = existingQueueItem.id,
                    bookingId = savedBooking.id
                )
                if (mergedQueue != existingQueueItem) dao.updateQueueItem(mergedQueue)
            }

            dao.updateVenue(atomic.updatedVenue)
            savedBooking
        }

    fun updateVenueCountersAfterUserBookingEnd(
        venueId: Int,
        ticketNumber: Int,
        wasCompleted: Boolean,
        newReviewRating: Int?
    ) {
        val venueRef = venuesCol().document(DoriCloudMapper.venueDocId(venueId))
        launchPush {
            try {
                firestore.runTransaction { tx ->
                    val snap = tx.get(venueRef)
                    val live = DoriCloudMapper.toVenueEntity(snap) ?: return@runTransaction null
                    val wasServing = live.currentServingNumber == ticketNumber
                    val nextWaiting = if (wasServing) live.waitingCount else (live.waitingCount - 1).coerceAtLeast(0)
                    val nextServing = if (wasServing) 0 else live.currentServingNumber
                    val nextServed = if (wasCompleted) live.servedTodayCount + 1 else live.servedTodayCount

                    val (nextRating, nextReviewCount) = if (newReviewRating != null && newReviewRating in 1..5) {
                        val cnt = live.reviewCount + 1
                        val avg = ((live.rating * live.reviewCount) + newReviewRating) / cnt
                        (kotlin.math.round(avg * 10.0) / 10.0) to cnt
                    } else {
                        null to null
                    }

                    tx.update(
                        venueRef,
                        DoriCloudMapper.venueUserCounterUpdateMap(
                            currentServingNumber = nextServing,
                            nextTicketNumber = live.nextTicketNumber,
                            waitingCount = nextWaiting,
                            servedTodayCount = if (wasCompleted) nextServed else null,
                            rating = nextRating,
                            reviewCount = nextReviewCount
                        )
                    )
                    null
                }.await()
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "venues/venue_$venueId")
                markError(e)
            }
        }
    }

    fun updateVenueRatingAfterUserReview(venueId: Int, rating: Int) {
        val venueRef = venuesCol().document(DoriCloudMapper.venueDocId(venueId))
        launchPush {
            try {
                firestore.runTransaction { tx ->
                    val snap = tx.get(venueRef)
                    val live = DoriCloudMapper.toVenueEntity(snap) ?: return@runTransaction null
                    val nextCount = live.reviewCount + 1
                    val avg = ((live.rating * live.reviewCount) + rating.coerceIn(1, 5)) / nextCount
                    val rounded = kotlin.math.round(avg * 10.0) / 10.0
                    tx.update(venueRef, DoriCloudMapper.venueUserReviewUpdateMap(rounded, nextCount))
                    null
                }.await()
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "venues/venue_$venueId")
                markError(e)
            }
        }
    }

    fun awardLoyaltyPointsToCustomer(customerUid: String, deltaPoints: Int) {
        if (!isControlApp || customerUid.isBlank() || deltaPoints <= 0) return
        val userRef = userProfileDoc(customerUid)
        launchPush {
            try {
                firestore.runTransaction { tx ->
                    val snap = tx.get(userRef)
                    if (!snap.exists()) return@runTransaction null
                    val current = (snap.getLong("loyaltyPoints") ?: 0L).toInt().coerceAtLeast(0)
                    val next = (current + deltaPoints).coerceIn(0, 100000)
                    tx.update(userRef, DoriCloudMapper.userLoyaltyAdminPatchMap(next))
                    null
                }.await()
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "users/$customerUid")
            }
        }
    }

    // ==================== Authorized Admin Registry (Control App) ====================

    suspend fun authorizeCloudAdmin(
        targetUid: String,
        email: String,
        displayName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isControlApp) return@withContext Result.failure(IllegalStateException("غير مصرح"))
        val cleanUid = targetUid.trim()
        if (!Regex("^[a-zA-Z0-9_\\-]{6,128}$").matches(cleanUid)) {
            return@withContext Result.failure(IllegalArgumentException("معرف المستخدم (UID) غير صالح"))
        }
        val cleanName = displayName.trim().ifBlank { "مشرف النظام" }
        val docRef = adminUsersCol().document(cleanUid)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(DoriCloudMapper.adminUserUpdateMap(email, cleanName, "ADMIN")).await()
            } else {
                docRef.set(DoriCloudMapper.adminUserCreateMap(cleanUid, email, cleanName, "ADMIN")).await()
            }
            markConnected()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "admin_users/$cleanUid")
            Result.failure(e)
        }
    }

    suspend fun revokeCloudAdmin(targetUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isControlApp) return@withContext Result.failure(IllegalStateException("غير مصرح"))
        val cleanUid = targetUid.trim()
        if (cleanUid == currentUid()) {
            return@withContext Result.failure(IllegalStateException("لا يمكنك إلغاء صلاحية حسابك الحالي"))
        }
        try {
            adminUsersCol().document(cleanUid).delete().await()
            markConnected()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "admin_users/$cleanUid")
            Result.failure(e)
        }
    }

    // ==================== Snapshot Reconcilers ====================

    private suspend fun applyFavoritesToLocalVenues(favoriteIds: Set<Int>) {
        val localVenues = dao.getAllVenuesOnce()
        val updated = localVenues.map { v ->
            val shouldBeFav = v.id in favoriteIds
            if (v.isFavorite == shouldBeFav) v else v.copy(isFavorite = shouldBeFav)
        }
        if (updated != localVenues) {
            dao.insertVenues(updated)
        }
    }

    private suspend fun applyVenuesSnapshot(snap: QuerySnapshot): Boolean = reconcileMutex.withLock {
        val favIds = prefs.userPreferences.first().favoriteVenueIds
        val remoteVenues = snap.documents.mapNotNull { DoriCloudMapper.toVenueEntity(it, favIds) }
        if (remoteVenues.isEmpty() && snap.metadata.isFromCache) return@withLock false
        val localSorted = dao.getAllVenuesOnce().sortedBy { it.id }
        val remoteSorted = remoteVenues.sortedBy { it.id }
        if (localSorted == remoteSorted) return@withLock false
        dao.replaceAllVenues(remoteSorted)
        true
    }

    private suspend fun applyBookingsSnapshot(snap: QuerySnapshot): Boolean {
        val alertsToEmit = mutableListOf<LiveTurnAlert>()
        val changed = reconcileMutex.withLock {
            val remoteBookings = snap.documents.mapNotNull { DoriCloudMapper.toBookingEntity(it) }
            val remoteCodes = remoteBookings.map { it.ticketCode }.toSet()
            val localBookings = dao.getAllBookingsOnce()
            var didChange = false

            // Deduplicate any duplicate local rows with identical ticketCode
            localBookings.groupBy { it.ticketCode }.values.forEach { group ->
                if (group.size > 1) {
                    group.drop(1).forEach { dup ->
                        dao.deleteBookingById(dup.id)
                        didChange = true
                    }
                }
            }

            if (!snap.metadata.isFromCache && !snap.metadata.hasPendingWrites() && inFlightPushes.get() == 0) {
                dao.getAllBookingsOnce().filter { it.ticketCode !in remoteCodes }.forEach { stale ->
                    dao.deleteBookingById(stale.id)
                    didChange = true
                }
            }

            val localByCode = dao.getAllBookingsOnce().associateBy { it.ticketCode }
            for (remote in remoteBookings) {
                val local = localByCode[remote.ticketCode]
                val localId: Long
                if (local == null) {
                    localId = dao.insertBooking(remote.copy(id = 0L))
                    didChange = true
                } else {
                    localId = local.id
                    val merged = remote.copy(id = local.id)
                    if (merged != local) {
                        dao.updateBooking(merged)
                        didChange = true

                        if (!isControlApp && !snap.metadata.isFromCache) {
                            if (merged.status == BookingStatus.ACTIVE.name &&
                                local.stage != merged.stage &&
                                (merged.stage == TicketStage.YOUR_TURN.name || merged.stage == TicketStage.APPROACHING.name)
                            ) {
                                val nextStage = if (merged.stage == TicketStage.YOUR_TURN.name) {
                                    TicketStage.YOUR_TURN
                                } else {
                                    TicketStage.APPROACHING
                                }
                                val msg = if (nextStage == TicketStage.YOUR_TURN) {
                                    "حان دورك الآن في ${merged.venueName}! تذكرة #${merged.queueNumber}"
                                } else {
                                    "اقترب دورك في ${merged.venueName}! تبقّى ${merged.peopleAhead} أمامك"
                                }
                                alertsToEmit += LiveTurnAlert(
                                    bookingId = localId,
                                    ticketCode = merged.ticketCode,
                                    venueName = merged.venueName,
                                    stage = nextStage,
                                    message = msg
                                )
                            } else if (local.approvalStatus != merged.approvalStatus &&
                                merged.approvalStatus == BookingApprovalStatus.ACCEPTED.name
                            ) {
                                alertsToEmit += LiveTurnAlert(
                                    bookingId = localId,
                                    ticketCode = merged.ticketCode,
                                    venueName = merged.venueName,
                                    stage = TicketStage.APPROACHING,
                                    message = "تم قبول وتأكيد تذكرتك #${merged.queueNumber} في ${merged.venueName} ✓"
                                )
                            } else if (local.approvalStatus != merged.approvalStatus &&
                                merged.approvalStatus == BookingApprovalStatus.REJECTED.name
                            ) {
                                alertsToEmit += LiveTurnAlert(
                                    bookingId = localId,
                                    ticketCode = merged.ticketCode,
                                    venueName = merged.venueName,
                                    stage = TicketStage.MONITORED,
                                    message = merged.approvalNote.ifBlank {
                                        "تعذر تأكيد تذكرتك #${merged.queueNumber} في ${merged.venueName}"
                                    }
                                )
                            }
                        }
                    }
                }

                // Re-link any queue item that arrived before its booking snapshot
                dao.getQueueItemByTicketCode(remote.ticketCode)?.let { qItem ->
                    if (qItem.bookingId != localId) {
                        dao.updateQueueItem(qItem.copy(bookingId = localId))
                        didChange = true
                    }
                }
            }
            didChange
        }
        alertsToEmit.forEach { alert -> onRealtimeBookingAlert?.invoke(alert) }
        return changed
    }

    private suspend fun applyQueueItemsSnapshot(snap: QuerySnapshot): Boolean = reconcileMutex.withLock {
        val remotePairs = snap.documents.mapNotNull { DoriCloudMapper.toQueueItemEntity(it) }
        val remoteCodes = remotePairs.map { it.first.ticketCode }.toSet()
        val localItems = dao.getAllQueueItemsOnce()
        var changed = false

        localItems.groupBy { it.ticketCode }.values.forEach { group ->
            if (group.size > 1) {
                val keep = group.first()
                dao.deleteQueueItemByTicketCode(keep.ticketCode)
                dao.insertQueueItem(keep)
                changed = true
            }
        }

        if (!snap.metadata.isFromCache && !snap.metadata.hasPendingWrites() && inFlightPushes.get() == 0) {
            dao.getAllQueueItemsOnce().filter { it.ticketCode !in remoteCodes }.forEach { stale ->
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

        localTickets.groupBy { it.externalId }.values.forEach { group ->
            if (group.size > 1) {
                group.drop(1).forEach { dup ->
                    dao.deleteSupportTicketById(dup.id)
                    changed = true
                }
            }
        }

        if (!snap.metadata.isFromCache && !snap.metadata.hasPendingWrites() && inFlightPushes.get() == 0) {
            dao.getAllSupportTicketsOnce().filter { it.externalId !in remoteIds }.forEach { stale ->
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

        localMessages.groupBy { it.externalMessageId }.values.forEach { group ->
            if (group.size > 1) {
                group.drop(1).forEach { dup ->
                    dao.deleteSupportMessageById(dup.id)
                    changed = true
                }
            }
        }

        if (!snap.metadata.isFromCache && !snap.metadata.hasPendingWrites() && inFlightPushes.get() == 0) {
            dao.getAllSupportMessagesOnce().filter { it.externalMessageId !in remoteIds }.forEach { stale ->
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

    fun pushPlatformConfig(
        config: UserAppControlConfig,
        meta: UserPreferencesDataStore.ControlSyncMetadata
    ) {
        if (!isControlApp) return
        val docRef = platformConfigDoc()
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = docRef,
                path = "platform/settings",
                updateFields = DoriCloudMapper.platformConfigUpdateMap(config, meta),
                createFields = DoriCloudMapper.platformConfigCreateMap(config, meta)
            )
        }
    }

    fun pushUserProfile(settings: UserProfileSettings) {
        if (isControlApp) return
        val uid = currentUid() ?: return
        val email = currentEmail()
        val docRef = userProfileDoc(uid)
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = docRef,
                path = "users/$uid",
                updateFields = DoriCloudMapper.userProfileUpdateMap(email, settings),
                createFields = DoriCloudMapper.userProfileCreateMap(uid, email, settings)
            )
        }
    }

    fun pushVenue(venue: VenueEntity) {
        if (!isControlApp) return
        val docId = DoriCloudMapper.venueDocId(venue.id)
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = venuesCol().document(docId),
                path = "venues/$docId",
                updateFields = DoriCloudMapper.venueMutableMap(venue),
                createFields = DoriCloudMapper.venueCreateMap(venue)
            )
        }
    }

    fun pushVenuesBatch(venues: List<VenueEntity>) {
        if (!isControlApp) return
        launchPush {
            try {
                venues.chunked(350).forEach { chunk ->
                    val batch: WriteBatch = firestore.batch()
                    chunk.forEach { v ->
                        batch.update(
                            venuesCol().document(DoriCloudMapper.venueDocId(v.id)),
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
                        docRef = venuesCol().document(docId),
                        path = "venues/$docId",
                        updateFields = DoriCloudMapper.venueMutableMap(v),
                        createFields = DoriCloudMapper.venueCreateMap(v)
                    )
                }
            }
        }
    }

    fun deleteVenue(venueId: Int) {
        if (!isControlApp) return
        val docId = DoriCloudMapper.venueDocId(venueId)
        launchPush {
            try {
                venuesCol().document(docId).delete().await()
                val qDocs = queueItemsCol().whereEqualTo("venueId", venueId).get().await()
                qDocs.documents.chunked(350).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "venues/$docId")
                markError(e)
            }
        }
    }

    fun pushBooking(booking: BookingEntity, onlyRatingUpdate: Boolean = false) {
        val uid = currentUid() ?: return
        val ownerUid = booking.ownerUid.ifBlank { uid }
        val docId = DoriCloudMapper.bookingDocId(booking.ticketCode)
        val path = "bookings/$docId"
        val docRef = bookingsCol().document(docId)
        val updateMap = when {
            onlyRatingUpdate -> DoriCloudMapper.bookingUpdateFinishedReviewMap(booking.userRating, booking.userReview)
            isControlApp -> DoriCloudMapper.bookingAdminUpdateMap(booking)
            else -> DoriCloudMapper.bookingUserCompleteOrCancelMap(booking)
        }
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = docRef,
                path = path,
                updateFields = updateMap,
                createFields = DoriCloudMapper.bookingCreateMap(ownerUid, booking)
            )
        }
    }

    fun pushQueueItem(item: MerchantQueueItemEntity, bookingTicketCode: String? = null) {
        val uid = currentUid() ?: return
        val ownerUid = item.ownerUid.ifBlank { uid }
        val docId = DoriCloudMapper.queueItemDocId(item.ticketCode)
        val updateMap = if (isControlApp) {
            DoriCloudMapper.queueItemAdminUpdateMap(item.status, item.bookingId, bookingTicketCode ?: item.ticketCode)
        } else {
            DoriCloudMapper.queueItemUserStatusMap(item.status)
        }
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = queueItemsCol().document(docId),
                path = "queue_items/$docId",
                updateFields = updateMap,
                createFields = DoriCloudMapper.queueItemCreateMap(ownerUid, item, bookingTicketCode ?: item.ticketCode)
            )
        }
    }

    fun pushReview(review: ReviewEntity) {
        val uid = currentUid() ?: return
        val docId = DoriCloudMapper.reviewDocId(review.id, review.venueId, review.createdAt)
        val path = "reviews/$docId"
        launchPush {
            try {
                reviewsCol().document(docId).set(DoriCloudMapper.reviewCreateMap(uid, review)).await()
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, path)
                markError(e)
            }
        }
    }

    fun pushSupportTicket(ticket: SupportTicketEntity, isNew: Boolean = false) {
        val uid = currentUid() ?: return
        val ownerUid = ticket.ownerUid.ifBlank { uid }
        val docId = ticket.externalId
        val docRef = supportTicketsCol().document(docId)
        val path = "support_tickets/$docId"
        if (isNew) {
            launchPush {
                try {
                    docRef.set(DoriCloudMapper.supportTicketCreateMap(ownerUid, ticket)).await()
                    markConnected()
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, path)
                    markError(e)
                }
            }
            return
        }
        val updateMap = if (isControlApp) {
            DoriCloudMapper.supportTicketAdminUpdateMap(ticket)
        } else {
            DoriCloudMapper.supportTicketUserFollowUpMap(ticket)
        }
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = docRef,
                path = path,
                updateFields = updateMap,
                createFields = DoriCloudMapper.supportTicketCreateMap(ownerUid, ticket)
            )
        }
    }

    fun pushSupportMessage(message: SupportMessageEntity) {
        val uid = currentUid() ?: return
        val ownerUid = message.ownerUid.ifBlank { uid }
        val docId = message.externalMessageId
        val path = "support_messages/$docId"
        val docRef = supportMessagesCol().document(docId)
        launchPush {
            try {
                docRef.set(DoriCloudMapper.supportMessageCreateMap(ownerUid, message)).await()
                markConnected()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS) {
                    markConnected()
                } else {
                    handleFirestoreError(e, OperationType.CREATE, path)
                    markError(e)
                }
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, path)
                markError(e)
            }
        }
    }

    fun pushAdmin(admin: AdminAccountEntity) {
        if (!isControlApp) return
        val docId = DoriCloudMapper.adminDocId(admin.id)
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = adminsCol().document(docId),
                path = "admins/$docId",
                updateFields = DoriCloudMapper.adminMutableMap(admin),
                createFields = DoriCloudMapper.adminCreateMap(admin)
            )
        }
    }

    fun deleteAdmin(adminId: Long) {
        if (!isControlApp) return
        val docId = DoriCloudMapper.adminDocId(adminId)
        launchPush {
            try {
                adminsCol().document(docId).delete().await()
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "admins/$docId")
                markError(e)
            }
        }
    }

    fun pushProvider(provider: ServiceProviderEntity) {
        if (!isControlApp) return
        val docId = DoriCloudMapper.providerDocId(provider.id)
        launchPush {
            upsertWithUpdateThenCreate(
                docRef = providersCol().document(docId),
                path = "providers/$docId",
                updateFields = DoriCloudMapper.providerMutableMap(provider),
                createFields = DoriCloudMapper.providerCreateMap(provider)
            )
        }
    }

    fun deleteProvider(providerId: Long) {
        if (!isControlApp) return
        val docId = DoriCloudMapper.providerDocId(providerId)
        launchPush {
            try {
                providersCol().document(docId).delete().await()
                markConnected()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "providers/$docId")
                markError(e)
            }
        }
    }

    suspend fun resetQueuesInCloud(
        resetVenues: List<VenueEntity>,
        config: UserAppControlConfig,
        meta: UserPreferencesDataStore.ControlSyncMetadata
    ) {
        if (!isControlApp) return
        try {
            deleteAllInCollection(bookingsCol())
            deleteAllInCollection(queueItemsCol())
            pushVenuesBatch(resetVenues)
            pushPlatformConfig(config, meta)
            markConnected()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "resetQueues")
            markError(e)
        }
    }

    suspend fun factoryResetInCloud(
        venues: List<VenueEntity>,
        reviews: List<ReviewEntity>,
        admins: List<AdminAccountEntity>,
        providers: List<ServiceProviderEntity>,
        config: UserAppControlConfig,
        meta: UserPreferencesDataStore.ControlSyncMetadata
    ) {
        if (!isControlApp) return
        val uid = currentUid() ?: return
        try {
            deleteAllInCollection(bookingsCol())
            deleteAllInCollection(queueItemsCol())
            deleteAllInCollection(reviewsCol())
            deleteAllInCollection(supportMessagesCol())
            deleteAllInCollection(supportTicketsCol())
            deleteAllInCollection(venuesCol())
            deleteAllInCollection(adminsCol())
            deleteAllInCollection(providersCol())

            val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()
            venues.forEach { v ->
                val cleanVenue = v.copy(
                    currentServingNumber = 0,
                    nextTicketNumber = 1,
                    waitingCount = 0,
                    servedTodayCount = 0,
                    isFavorite = false
                )
                writes += venuesCol().document(DoriCloudMapper.venueDocId(cleanVenue.id)) to
                    DoriCloudMapper.venueCreateMap(cleanVenue)
            }
            reviews.forEachIndexed { idx, r ->
                val stableRev = if (r.id > 0L) r else r.copy(id = (idx + 1).toLong())
                val rDocId = DoriCloudMapper.reviewDocId(stableRev.id, stableRev.venueId, stableRev.createdAt)
                writes += reviewsCol().document(rDocId) to
                    DoriCloudMapper.reviewCreateMap(uid, stableRev)
            }
            admins.forEach { a ->
                writes += adminsCol().document(DoriCloudMapper.adminDocId(a.id)) to
                    DoriCloudMapper.adminCreateMap(a)
            }
            providers.forEach { p ->
                writes += providersCol().document(DoriCloudMapper.providerDocId(p.id)) to
                    DoriCloudMapper.providerCreateMap(p)
            }
            commitChunked(writes)
            pushPlatformConfig(config, meta)
            markConnected()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "factoryReset")
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
