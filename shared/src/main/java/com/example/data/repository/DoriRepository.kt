package com.example.data.repository

import com.example.data.local.BookingEntity
import com.example.data.local.DoriDao
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.local.ReviewEntity
import com.example.data.local.SeedData
import com.example.data.local.UserPreferencesDataStore
import com.example.data.local.VenueEntity
import com.example.data.model.BookingStatus
import com.example.data.model.LiveTurnAlert
import com.example.data.model.NinevehDistrict
import com.example.data.model.QueueItemStatus
import com.example.data.model.ServiceCategory
import com.example.data.model.TicketStage
import com.example.data.model.UserAppControlConfig
import com.example.data.model.UserProfileSettings
import com.example.data.remote.DoriCloudSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DoriRepository(
    private val dao: DoriDao,
    private val prefs: UserPreferencesDataStore,
    private val cloudSync: DoriCloudSyncRepository? = null
) {
    val venues: Flow<List<VenueEntity>> = dao.observeAllVenues()
    val reviews: Flow<List<ReviewEntity>> = dao.observeAllReviews()
    val bookings: Flow<List<BookingEntity>> = dao.observeAllBookings()
    val queueItems: Flow<List<MerchantQueueItemEntity>> = dao.observeAllQueueItems()
    val userPreferences: Flow<UserProfileSettings> = prefs.userPreferences

    private val cloudScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cloudWriteMutex = Mutex()

    private inline fun launchCloudWrite(
        crossinline block: suspend (DoriCloudSyncRepository) -> Unit
    ) {
        val remote = cloudSync ?: return
        cloudScope.launch {
            cloudWriteMutex.withLock {
                block(remote)
            }
        }
    }

    suspend fun ensureSeeded() {
        val currentVenues = dao.observeAllVenues().first()
        val validDistrictNames = NinevehDistrict.selectableDistricts.map { it.displayName }.toSet()
        val hasActiveBookings = dao.getActiveBookingsOnce().isNotEmpty()
        val needsReseed = currentVenues.isEmpty() ||
            currentVenues.any { it.city !in validDistrictNames } ||
            currentVenues.any { it.category == ServiceCategory.CLINICS.displayName && it.specialty.isBlank() } ||
            (!hasActiveBookings && currentVenues.any { it.currentServingNumber > 1 })
        if (needsReseed) {
            dao.deleteAllQueueItems()
            dao.deleteAllReviews()
            dao.deleteAllVenues()
            val venues = SeedData.initialVenues()
            dao.insertVenues(venues)
            dao.insertReviews(SeedData.initialReviews())
            dao.insertQueueItems(SeedData.initialQueueItems(venues))
        }
    }

    suspend fun bootstrapInitialCloudIfEmpty() {
        val remote = cloudSync ?: return
        val hasVenues = remote.hasRemoteVenues().getOrNull() ?: return
        if (!hasVenues) {
            cloudWriteMutex.withLock {
                val again = remote.hasRemoteVenues().getOrNull() ?: false
                if (!again) {
                    val currentSettings = prefs.userPreferences.first()
                    remote.upsertConfig(currentSettings)
                    remote.upsertVenuesBatch(dao.getAllVenuesOnce())
                    remote.upsertReviewsBatch(dao.getAllReviewsOnce())
                    remote.upsertQueueItemsBatch(dao.getAllQueueItemsOnce())
                    dao.getAllBookingsOnce().forEach { remote.upsertBooking(it) }
                }
            }
        }
    }

    fun observeRemoteConfig(): Flow<UserProfileSettings?>? = cloudSync?.observeConfig()
    fun observeRemoteVenues(): Flow<List<VenueEntity>>? = cloudSync?.observeVenues()
    fun observeRemoteBookings(): Flow<List<BookingEntity>>? = cloudSync?.observeBookings()
    fun observeRemoteQueueItems(): Flow<List<MerchantQueueItemEntity>>? = cloudSync?.observeQueueItems()
    fun observeRemoteReviews(): Flow<List<ReviewEntity>>? = cloudSync?.observeReviews()

    suspend fun applyRemoteConfig(settings: UserProfileSettings) {
        prefs.applySyncedSettings(settings)
    }

    suspend fun applyRemoteVenues(remoteVenues: List<VenueEntity>) {
        if (remoteVenues.isNotEmpty()) {
            dao.replaceAllVenues(remoteVenues)
        }
    }

    suspend fun applyRemoteBookings(remoteBookings: List<BookingEntity>) {
        dao.replaceAllBookings(remoteBookings)
    }

    suspend fun applyRemoteQueueItems(remoteItems: List<MerchantQueueItemEntity>) {
        dao.replaceAllQueueItems(remoteItems)
    }

    suspend fun applyRemoteReviews(remoteReviews: List<ReviewEntity>) {
        if (remoteReviews.isNotEmpty()) {
            dao.replaceAllReviews(remoteReviews)
        }
    }

    suspend fun setSelectedDistrict(district: NinevehDistrict) {
        prefs.setSelectedDistrict(district)
        val snapshot = prefs.userPreferences.first()
        launchCloudWrite { it.upsertConfig(snapshot) }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        prefs.setDarkMode(enabled)
        val snapshot = prefs.userPreferences.first()
        launchCloudWrite { it.upsertConfig(snapshot) }
    }

    suspend fun setMuteSound(muted: Boolean) {
        prefs.setMuteSound(muted)
        val snapshot = prefs.userPreferences.first()
        launchCloudWrite { it.upsertConfig(snapshot) }
    }

    suspend fun setMuteVibration(muted: Boolean) {
        prefs.setMuteVibration(muted)
        val snapshot = prefs.userPreferences.first()
        launchCloudWrite { it.upsertConfig(snapshot) }
    }

    suspend fun updateProfile(
        name: String,
        phone: String,
        district: NinevehDistrict,
        age: String = "",
        optionalNote: String = ""
    ) {
        prefs.updateProfile(name, phone, district, age, optionalNote)
        val snapshot = prefs.userPreferences.first()
        launchCloudWrite { it.upsertConfig(snapshot) }
    }

    suspend fun toggleFavorite(venueId: Int, currentFavorite: Boolean) {
        dao.setFavorite(venueId, !currentFavorite)
        dao.getVenueById(venueId)?.let { updated ->
            launchCloudWrite { it.upsertVenue(updated) }
        }
    }

    suspend fun bookQueueTicket(
        venueId: Int,
        customerName: String,
        customerPhone: String,
        selectedOption: String,
        notes: String = "",
        optionalInfo: String = "",
        customPriceDinar: Int? = null
    ): Result<BookingEntity> {
        val currentPrefs = prefs.userPreferences.first()
        val ctrl = currentPrefs.controlConfig
        if (!ctrl.allowNewBookings) {
            return Result.failure(IllegalStateException("تم إيقاف الحجوزات مؤقتاً من لوحة التحكم"))
        }

        val cleanName = customerName.trim()
        val cleanPhone = customerPhone.trim()
        if (!UserProfileSettings.isValidFullName(cleanName)) {
            return Result.failure(IllegalArgumentException("الاسم الكامل إلزامي (3 أحرف على الأقل)"))
        }
        if (!UserProfileSettings.isValidPhone(cleanPhone)) {
            return Result.failure(IllegalArgumentException("رقم الهاتف إلزامي (10 إلى 14 رقماً)"))
        }

        val venue = dao.getVenueById(venueId)
            ?: return Result.failure(IllegalStateException("المكان غير موجود"))
        if (!venue.isOpen || !venue.isAcceptingBookings) {
            return Result.failure(IllegalStateException("هذا المكان متوقف عن استقبال الحجوزات حالياً"))
        }

        val queueItems = dao.getQueueItemsForVenueOnce(venueId)
        val activeWaiting = queueItems.count { it.status == QueueItemStatus.WAITING.name }
        val activeServing = queueItems.count { it.status == QueueItemStatus.SERVING.name }
        val maxExistingTicket = queueItems.maxOfOrNull { it.ticketNumber } ?: 0
        val ticketNum = maxOf(1, maxExistingTicket + 1)
        val ticketCode = "NIN-${venue.id}-$ticketNum"
        val peopleAhead = activeWaiting + activeServing
        val stage = when {
            peopleAhead == 0 -> TicketStage.YOUR_TURN
            peopleAhead <= 1 -> TicketStage.APPROACHING
            else -> TicketStage.MONITORED
        }
        val etaMinutes = if (peopleAhead == 0) 0 else (peopleAhead * venue.avgMinutesPerPerson).coerceAtLeast(3)

        val booking = BookingEntity(
            ticketCode = ticketCode,
            queueNumber = ticketNum,
            venueId = venue.id,
            venueName = venue.name,
            venueCity = "${venue.city} • ${venue.area}",
            venueCategory = venue.category,
            venuePhone = venue.phone,
            customerName = cleanName,
            customerPhone = cleanPhone,
            selectedOption = selectedOption.ifBlank { venue.customOptions.firstOrNull() ?: "خدمة قياسية" },
            notes = notes.trim(),
            optionalInfo = if (ctrl.enableOptionalFields) optionalInfo.trim() else "",
            priceDinar = customPriceDinar ?: venue.priceDinar,
            status = BookingStatus.ACTIVE.name,
            stage = stage.name,
            peopleAhead = peopleAhead,
            estimatedMinutes = etaMinutes
        )
        val insertedId = dao.insertBooking(booking)
        val savedBooking = booking.copy(id = insertedId)

        val qItemId = dao.insertQueueItem(
            MerchantQueueItemEntity(
                venueId = venue.id,
                ticketNumber = ticketNum,
                ticketCode = ticketCode,
                customerName = savedBooking.customerName,
                customerPhone = savedBooking.customerPhone,
                selectedOption = savedBooking.selectedOption,
                status = if (peopleAhead == 0) QueueItemStatus.SERVING.name else QueueItemStatus.WAITING.name,
                bookingId = insertedId
            )
        )
        val savedQueueItem = dao.getQueueItemsForVenueOnce(venueId).find { it.id == qItemId }

        val updatedWaitingCount = activeWaiting + if (peopleAhead > 0) 1 else 0
        val updatedServingNumber = if (peopleAhead == 0) ticketNum else venue.currentServingNumber
        val updatedVenue = venue.copy(
            currentServingNumber = updatedServingNumber,
            nextTicketNumber = ticketNum + 1,
            waitingCount = updatedWaitingCount
        )
        dao.updateVenue(updatedVenue)

        launchCloudWrite { remote ->
            remote.upsertBooking(savedBooking)
            if (savedQueueItem != null) remote.upsertQueueItem(savedQueueItem)
            remote.upsertVenue(updatedVenue)
        }

        return Result.success(savedBooking)
    }

    suspend fun findFastestVenueInDistrict(district: NinevehDistrict): VenueEntity? {
        val allVenues = dao.observeAllVenues().first()
        val candidates = if (district == NinevehDistrict.ALL_NINEVEH) {
            allVenues
        } else {
            allVenues.filter { it.city == district.displayName }
        }
        return candidates
            .filter { it.isOpen && it.isAcceptingBookings && !it.isPaused }
            .minByOrNull { it.estimatedWaitMinutes }
    }

    suspend fun completeBookingWithOptionalReview(
        bookingId: Long,
        rating: Int?,
        comment: String?
    ): Int {
        val booking = dao.getBookingById(bookingId) ?: return 0
        if (booking.status == BookingStatus.COMPLETED.name) return 0
        val ctrl = prefs.userPreferences.first().controlConfig
        val pointsEarned = if (ctrl.enableLoyalty) ctrl.pointsPerBooking else 0
        val effectiveRating = if (ctrl.enableReviews) rating else null
        val effectiveComment = if (ctrl.enableReviews) comment?.takeIf { it.isNotBlank() } else null

        val completedBooking = booking.copy(
            status = BookingStatus.COMPLETED.name,
            stage = TicketStage.YOUR_TURN.name,
            peopleAhead = 0,
            estimatedMinutes = 0,
            completedAt = System.currentTimeMillis(),
            userRating = effectiveRating,
            userReview = effectiveComment
        )
        dao.updateBooking(completedBooking)

        val qItems = dao.getQueueItemsForVenueOnce(booking.venueId)
        val updatedQueueItem = qItems.find { it.bookingId == bookingId }?.let { item ->
            item.copy(status = QueueItemStatus.DONE.name).also { dao.updateQueueItem(it) }
        }

        val venue = dao.getVenueById(booking.venueId)
        var createdReview: ReviewEntity? = null
        if (venue != null) {
            val updatedVenue = if (effectiveRating != null && effectiveRating in 1..5) {
                val review = ReviewEntity(
                    venueId = venue.id,
                    authorName = booking.customerName,
                    rating = effectiveRating,
                    comment = effectiveComment ?: "خدمة ممتازة وتجربة منظمة عبر دوري"
                )
                val revId = dao.insertReview(review)
                createdReview = review.copy(id = revId)
                val newCount = venue.reviewCount + 1
                val newRating = ((venue.rating * venue.reviewCount) + effectiveRating) / newCount
                val rounded = kotlin.math.round(newRating * 10.0) / 10.0
                venue.copy(
                    rating = rounded,
                    reviewCount = newCount,
                    servedTodayCount = venue.servedTodayCount + 1
                )
            } else {
                venue.copy(servedTodayCount = venue.servedTodayCount + 1)
            }
            dao.updateVenue(updatedVenue)
            syncVenueQueueAndBookings(venue.id)
        }

        if (pointsEarned > 0) {
            prefs.addLoyaltyPoints(pointsEarned)
        }
        val latestSettings = prefs.userPreferences.first()
        val latestVenue = dao.getVenueById(booking.venueId)
        launchCloudWrite { remote ->
            remote.upsertBooking(completedBooking)
            if (updatedQueueItem != null) remote.upsertQueueItem(updatedQueueItem)
            if (createdReview != null) remote.upsertReviewsBatch(listOf(createdReview))
            if (latestVenue != null) remote.upsertVenue(latestVenue)
            if (pointsEarned > 0) remote.upsertConfig(latestSettings)
        }
        return pointsEarned
    }

    suspend fun submitReviewForHistoryBooking(bookingId: Long, rating: Int, comment: String) {
        val booking = dao.getBookingById(bookingId) ?: return
        val cleanRating = rating.coerceIn(1, 5)
        val cleanComment = comment.trim().ifBlank { "تجربة رائعة" }
        val alreadyRated = booking.userRating != null
        val updatedBooking = booking.copy(
            userRating = cleanRating,
            userReview = cleanComment
        )
        dao.updateBooking(updatedBooking)
        var createdReview: ReviewEntity? = null
        var updatedVenue: VenueEntity? = null
        if (!alreadyRated) {
            val review = ReviewEntity(
                venueId = booking.venueId,
                authorName = booking.customerName,
                rating = cleanRating,
                comment = cleanComment
            )
            val revId = dao.insertReview(review)
            createdReview = review.copy(id = revId)
            dao.getVenueById(booking.venueId)?.let { venue ->
                val newCount = venue.reviewCount + 1
                val newRating = ((venue.rating * venue.reviewCount) + cleanRating) / newCount
                val rounded = kotlin.math.round(newRating * 10.0) / 10.0
                updatedVenue = venue.copy(rating = rounded, reviewCount = newCount)
                dao.updateVenue(updatedVenue!!)
            }
        }
        launchCloudWrite { remote ->
            remote.upsertBooking(updatedBooking)
            if (createdReview != null) remote.upsertReviewsBatch(listOf(createdReview))
            if (updatedVenue != null) remote.upsertVenue(updatedVenue!!)
        }
    }

    suspend fun cancelBooking(bookingId: Long) {
        val booking = dao.getBookingById(bookingId) ?: return
        val cancelled = booking.copy(
            status = BookingStatus.CANCELLED.name,
            completedAt = System.currentTimeMillis()
        )
        dao.updateBooking(cancelled)
        val qItems = dao.getQueueItemsForVenueOnce(booking.venueId)
        val updatedQ = qItems.find { it.bookingId == bookingId }?.let { item ->
            item.copy(status = QueueItemStatus.SKIPPED.name).also { dao.updateQueueItem(it) }
        }
        syncVenueQueueAndBookings(booking.venueId)
        launchCloudWrite { remote ->
            remote.upsertBooking(cancelled)
            if (updatedQ != null) remote.upsertQueueItem(updatedQ)
        }
    }

    suspend fun togglePauseQueue(venueId: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        val updated = venue.copy(isPaused = !venue.isPaused)
        dao.updateVenue(updated)
        launchCloudWrite { it.upsertVenue(updated) }
    }

    suspend fun toggleAcceptingBookings(venueId: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        val updated = venue.copy(isAcceptingBookings = !venue.isAcceptingBookings)
        dao.updateVenue(updated)
        launchCloudWrite { it.upsertVenue(updated) }
    }

    suspend fun addWalkInCustomer(venueId: Int, name: String, option: String) {
        val venue = dao.getVenueById(venueId) ?: return
        val existingItems = dao.getQueueItemsForVenueOnce(venueId)
        val num = ((existingItems.maxOfOrNull { it.ticketNumber } ?: 0) + 1).coerceAtLeast(1)
        val hasServing = existingItems.any { it.status == QueueItemStatus.SERVING.name }
        val newItem = MerchantQueueItemEntity(
            venueId = venueId,
            ticketNumber = num,
            ticketCode = "NIN-$venueId-$num",
            customerName = name.ifBlank { "عميل حضوري #$num" },
            customerPhone = "حضوري",
            selectedOption = option.ifBlank { venue.customOptions.firstOrNull() ?: "خدمة عامة" },
            status = if (!hasServing) QueueItemStatus.SERVING.name else QueueItemStatus.WAITING.name
        )
        val qId = dao.insertQueueItem(newItem)
        val savedItem = newItem.copy(id = qId)
        dao.updateVenue(
            venue.copy(
                currentServingNumber = if (!hasServing) num else venue.currentServingNumber,
                nextTicketNumber = num + 1
            )
        )
        syncVenueQueueAndBookings(venueId)
        launchCloudWrite { it.upsertQueueItem(savedItem) }
    }

    suspend fun callNextCustomer(venueId: Int): List<LiveTurnAlert> {
        val venue = dao.getVenueById(venueId) ?: return emptyList()
        val items = dao.getQueueItemsForVenueOnce(venueId)
        val changedQueueItems = mutableListOf<MerchantQueueItemEntity>()
        val changedBookings = mutableListOf<BookingEntity>()
        var pointsAwarded = false

        val currentServing = items.firstOrNull { it.status == QueueItemStatus.SERVING.name }
        if (currentServing != null) {
            val doneItem = currentServing.copy(status = QueueItemStatus.DONE.name)
            dao.updateQueueItem(doneItem)
            changedQueueItems.add(doneItem)
            currentServing.bookingId?.let { bId ->
                val b = dao.getBookingById(bId)
                if (b != null && b.status == BookingStatus.ACTIVE.name) {
                    val doneBooking = b.copy(
                        status = BookingStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        peopleAhead = 0,
                        estimatedMinutes = 0
                    )
                    dao.updateBooking(doneBooking)
                    changedBookings.add(doneBooking)
                    val ctrl = prefs.userPreferences.first().controlConfig
                    if (ctrl.enableLoyalty && ctrl.pointsPerBooking > 0) {
                        prefs.addLoyaltyPoints(ctrl.pointsPerBooking)
                        pointsAwarded = true
                    }
                }
            }
        }
        val nextWaiting = items.firstOrNull { it.status == QueueItemStatus.WAITING.name }
        if (nextWaiting != null) {
            val servingNext = nextWaiting.copy(status = QueueItemStatus.SERVING.name)
            dao.updateQueueItem(servingNext)
            changedQueueItems.add(servingNext)
            dao.updateVenue(
                venue.copy(
                    currentServingNumber = nextWaiting.ticketNumber,
                    servedTodayCount = venue.servedTodayCount + (if (currentServing != null) 1 else 0)
                )
            )
        } else if (currentServing != null) {
            dao.updateVenue(venue.copy(servedTodayCount = venue.servedTodayCount + 1))
        }
        val alerts = syncVenueQueueAndBookings(venueId)
        val latestSettings = if (pointsAwarded) prefs.userPreferences.first() else null
        launchCloudWrite { remote ->
            if (changedQueueItems.isNotEmpty()) remote.upsertQueueItemsBatch(changedQueueItems)
            changedBookings.forEach { remote.upsertBooking(it) }
            if (latestSettings != null) remote.upsertConfig(latestSettings)
        }
        return alerts
    }

    suspend fun skipCurrentCustomer(venueId: Int): List<LiveTurnAlert> {
        val venue = dao.getVenueById(venueId) ?: return emptyList()
        val items = dao.getQueueItemsForVenueOnce(venueId)
        val changedQueueItems = mutableListOf<MerchantQueueItemEntity>()
        val changedBookings = mutableListOf<BookingEntity>()

        val currentServing = items.firstOrNull { it.status == QueueItemStatus.SERVING.name }
        if (currentServing != null) {
            val skipped = currentServing.copy(status = QueueItemStatus.SKIPPED.name)
            dao.updateQueueItem(skipped)
            changedQueueItems.add(skipped)
            currentServing.bookingId?.let { bId ->
                dao.getBookingById(bId)?.let { b ->
                    if (b.status == BookingStatus.ACTIVE.name) {
                        val cancelled = b.copy(status = BookingStatus.CANCELLED.name, completedAt = System.currentTimeMillis())
                        dao.updateBooking(cancelled)
                        changedBookings.add(cancelled)
                    }
                }
            }
        }
        val nextWaiting = items.firstOrNull { it.status == QueueItemStatus.WAITING.name }
        if (nextWaiting != null) {
            val nextServing = nextWaiting.copy(status = QueueItemStatus.SERVING.name)
            dao.updateQueueItem(nextServing)
            changedQueueItems.add(nextServing)
            dao.updateVenue(venue.copy(currentServingNumber = nextWaiting.ticketNumber))
        }
        val alerts = syncVenueQueueAndBookings(venueId)
        launchCloudWrite { remote ->
            if (changedQueueItems.isNotEmpty()) remote.upsertQueueItemsBatch(changedQueueItems)
            changedBookings.forEach { remote.upsertBooking(it) }
        }
        return alerts
    }

    suspend fun callCustomerByName(venueId: Int, queueItemId: Long): List<LiveTurnAlert> {
        val venue = dao.getVenueById(venueId) ?: return emptyList()
        val items = dao.getQueueItemsForVenueOnce(venueId)
        val target = items.find { it.id == queueItemId } ?: return emptyList()
        val changed = mutableListOf<MerchantQueueItemEntity>()

        items.filter { it.status == QueueItemStatus.SERVING.name && it.id != queueItemId }
            .forEach { prev ->
                val donePrev = prev.copy(status = QueueItemStatus.DONE.name)
                dao.updateQueueItem(donePrev)
                changed.add(donePrev)
            }
        val targetServing = target.copy(status = QueueItemStatus.SERVING.name)
        dao.updateQueueItem(targetServing)
        changed.add(targetServing)
        dao.updateVenue(venue.copy(currentServingNumber = target.ticketNumber))
        val alerts = syncVenueQueueAndBookings(venueId)
        launchCloudWrite { it.upsertQueueItemsBatch(changed) }
        return alerts
    }

    suspend fun markCustomerNoShow(venueId: Int, queueItemId: Long): List<LiveTurnAlert> {
        val items = dao.getQueueItemsForVenueOnce(venueId)
        val target = items.find { it.id == queueItemId } ?: return emptyList()
        val updatedItem = target.copy(status = QueueItemStatus.NO_SHOW.name)
        dao.updateQueueItem(updatedItem)
        var cancelledBooking: BookingEntity? = null
        target.bookingId?.let { bId ->
            dao.getBookingById(bId)?.let { b ->
                if (b.status == BookingStatus.ACTIVE.name) {
                    cancelledBooking = b.copy(
                        status = BookingStatus.CANCELLED.name,
                        completedAt = System.currentTimeMillis()
                    )
                    dao.updateBooking(cancelledBooking!!)
                }
            }
        }
        val alerts = syncVenueQueueAndBookings(venueId)
        launchCloudWrite { remote ->
            remote.upsertQueueItem(updatedItem)
            if (cancelledBooking != null) remote.upsertBooking(cancelledBooking!!)
        }
        return alerts
    }

    suspend fun advanceActiveUserTicketDemoStep(): List<LiveTurnAlert> {
        val activeBookings = dao.getActiveBookingsOnce()
        if (activeBookings.isEmpty()) return emptyList()
        val alerts = mutableListOf<LiveTurnAlert>()
        val targetVenueIds = activeBookings.map { it.venueId }.distinct()
        for (vId in targetVenueIds) {
            val venue = dao.getVenueById(vId) ?: continue
            if (venue.isPaused) continue
            val items = dao.getQueueItemsForVenueOnce(vId)
            val waiting = items.filter { it.status == QueueItemStatus.WAITING.name }
            val serving = items.firstOrNull { it.status == QueueItemStatus.SERVING.name }
            if (waiting.isNotEmpty() || (serving != null && serving.bookingId == null)) {
                alerts.addAll(callNextCustomer(vId))
            }
        }
        return alerts
    }

    private suspend fun syncVenueQueueAndBookings(venueId: Int): List<LiveTurnAlert> {
        val venue = dao.getVenueById(venueId) ?: return emptyList()
        val items = dao.getQueueItemsForVenueOnce(venueId)
        val waitingList = items.filter { it.status == QueueItemStatus.WAITING.name }
        val servingItem = items.firstOrNull { it.status == QueueItemStatus.SERVING.name }

        val updatedVenue = venue.copy(
            waitingCount = waitingList.size,
            currentServingNumber = servingItem?.ticketNumber ?: 0
        )
        dao.updateVenue(updatedVenue)

        val alerts = mutableListOf<LiveTurnAlert>()
        val updatedBookings = mutableListOf<BookingEntity>()

        if (servingItem?.bookingId != null) {
            val b = dao.getBookingById(servingItem.bookingId)
            if (b != null && b.status == BookingStatus.ACTIVE.name) {
                val previousStage = b.stage
                val updated = b.copy(
                    stage = TicketStage.YOUR_TURN.name,
                    peopleAhead = 0,
                    estimatedMinutes = 0
                )
                dao.updateBooking(updated)
                updatedBookings.add(updated)
                if (previousStage != TicketStage.YOUR_TURN.name) {
                    alerts.add(
                        LiveTurnAlert(
                            bookingId = b.id,
                            ticketCode = b.ticketCode,
                            venueName = b.venueName,
                            stage = TicketStage.YOUR_TURN,
                            message = "حان دورك الآن في ${b.venueName}! تذكرة #${b.queueNumber}"
                        )
                    )
                }
            }
        }

        waitingList.forEachIndexed { index, item ->
            val bId = item.bookingId ?: return@forEachIndexed
            val b = dao.getBookingById(bId) ?: return@forEachIndexed
            if (b.status != BookingStatus.ACTIVE.name) return@forEachIndexed
            val ahead = index + (if (servingItem != null) 1 else 0)
            val newStage = when {
                ahead <= 0 -> TicketStage.YOUR_TURN
                ahead <= 1 -> TicketStage.APPROACHING
                else -> TicketStage.MONITORED
            }
            val eta = if (ahead == 0) 0 else (ahead * venue.avgMinutesPerPerson).coerceAtLeast(3)
            val previousStage = b.stage
            val updated = b.copy(
                stage = newStage.name,
                peopleAhead = ahead,
                estimatedMinutes = eta
            )
            dao.updateBooking(updated)
            updatedBookings.add(updated)
            if (previousStage != newStage.name && (newStage == TicketStage.APPROACHING || newStage == TicketStage.YOUR_TURN)) {
                val msg = if (newStage == TicketStage.YOUR_TURN) {
                    "حان دورك الآن في ${b.venueName}! تذكرة ${b.ticketCode}"
                } else {
                    "اقترب دورك في ${b.venueName}! تبقّى $ahead فقط أمامك"
                }
                alerts.add(
                    LiveTurnAlert(
                        bookingId = b.id,
                        ticketCode = b.ticketCode,
                        venueName = b.venueName,
                        stage = newStage,
                        message = msg
                    )
                )
            }
        }

        launchCloudWrite { remote ->
            remote.upsertVenue(updatedVenue)
            updatedBookings.forEach { remote.upsertBooking(it) }
        }
        return alerts
    }

    // ==================== Control Panel Commands ====================

    suspend fun updateControlConfig(transform: (UserAppControlConfig) -> UserAppControlConfig) {
        val current = prefs.userPreferences.first().controlConfig
        prefs.saveControlConfig(transform(current))
        val updated = prefs.userPreferences.first()
        launchCloudWrite { it.upsertConfig(updated) }
    }

    suspend fun resetControlConfig() {
        prefs.saveControlConfig(UserAppControlConfig())
        val updated = prefs.userPreferences.first()
        launchCloudWrite { it.upsertConfig(updated) }
    }

    suspend fun toggleVenueOpen(venueId: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        val updated = venue.copy(isOpen = !venue.isOpen)
        dao.updateVenue(updated)
        launchCloudWrite { it.upsertVenue(updated) }
    }

    suspend fun adjustVenuePrice(venueId: Int, deltaDinar: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        val nextPrice = (venue.priceDinar + deltaDinar).coerceIn(1000, 250000)
        val updated = venue.copy(priceDinar = nextPrice)
        dao.updateVenue(updated)
        launchCloudWrite { it.upsertVenue(updated) }
    }

    suspend fun adjustVenueWaitMinutes(venueId: Int, deltaMinutes: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        val nextAvg = (venue.avgMinutesPerPerson + deltaMinutes).coerceIn(1, 60)
        dao.updateVenue(venue.copy(avgMinutesPerPerson = nextAvg))
        syncVenueQueueAndBookings(venueId)
    }

    suspend fun addCustomVenue(
        name: String,
        district: NinevehDistrict,
        area: String,
        category: ServiceCategory,
        specialty: String,
        priceDinar: Int,
        avgMinutes: Int,
        phone: String
    ): Result<VenueEntity> {
        val cleanName = name.trim()
        if (cleanName.length < 3) {
            return Result.failure(IllegalArgumentException("اسم المكان يجب أن لا يقل عن 3 أحرف"))
        }
        val cleanArea = area.trim().ifBlank { district.areas.firstOrNull() ?: "المركز" }
        val nextId = ((dao.getMaxVenueId() ?: 100) + 1).coerceAtLeast(100)
        val cleanPhone = phone.trim().ifBlank { "+9647700000000" }
        val defaultOptions = when (category) {
            ServiceCategory.CLINICS -> "كشف عام|استشارة|متابعة"
            ServiceCategory.FIELDS -> "ملعب خماسي|ملعب سباعي"
            ServiceCategory.MAINTENANCE -> "فحص وتشخيص|صيانة سريعة"
            ServiceCategory.BEAUTY -> "حلاقة وعناية|تجهيز كامل"
            ServiceCategory.LABS -> "تحليل شامل|فحص دم"
            ServiceCategory.ALL -> "خدمة قياسية"
        }
        val newVenue = VenueEntity(
            id = nextId,
            name = cleanName,
            city = district.displayName,
            category = category.displayName,
            specialty = if (category == ServiceCategory.CLINICS) specialty else "",
            area = cleanArea,
            priceDinar = priceDinar.coerceIn(1000, 250000),
            rating = 5.0,
            reviewCount = 1,
            workingHours = "نفس اليوم",
            phone = cleanPhone,
            whatsapp = cleanPhone,
            latitude = 36.3489,
            longitude = 43.1577,
            description = "مركز مضاف عبر لوحة التحكم في ${district.displayName}",
            customOptionsCsv = defaultOptions,
            isOpen = true,
            isAcceptingBookings = true,
            currentServingNumber = 0,
            nextTicketNumber = 1,
            servedTodayCount = 0,
            avgMinutesPerPerson = avgMinutes.coerceIn(2, 60),
            waitingCount = 0
        )
        dao.insertVenue(newVenue)
        launchCloudWrite { it.upsertVenue(newVenue) }
        return Result.success(newVenue)
    }

    suspend fun deleteVenue(venueId: Int) {
        dao.deleteQueueItemsForVenue(venueId)
        dao.deleteVenueById(venueId)
        launchCloudWrite { remote ->
            remote.deleteQueueItemsForVenue(venueId)
            remote.deleteVenue(venueId)
        }
    }

    suspend fun setAllVenuesOpenInDistrict(district: NinevehDistrict, open: Boolean) {
        val all = dao.observeAllVenues().first()
        val targets = if (district == NinevehDistrict.ALL_NINEVEH) {
            all
        } else {
            all.filter { it.city == district.displayName }
        }
        val updatedList = targets.map { it.copy(isOpen = open, isAcceptingBookings = open) }
        if (updatedList.isNotEmpty()) {
            dao.insertVenues(updatedList)
            launchCloudWrite { it.upsertVenuesBatch(updatedList) }
        }
    }

    suspend fun completeAllActiveBookings(): Int {
        val active = dao.getActiveBookingsOnce()
        active.forEach { b ->
            completeBookingWithOptionalReview(b.id, null, null)
        }
        return active.size
    }

    suspend fun cancelAllActiveBookings(): Int {
        val active = dao.getActiveBookingsOnce()
        active.forEach { b ->
            cancelBooking(b.id)
        }
        return active.size
    }

    suspend fun resetAllQueuesToOne() {
        dao.deleteAllBookings()
        dao.deleteAllQueueItems()
        val venues = dao.observeAllVenues().first()
        val resetVenues = venues.map { v ->
            v.copy(
                currentServingNumber = 0,
                nextTicketNumber = 1,
                waitingCount = 0,
                servedTodayCount = 0
            )
        }
        if (resetVenues.isNotEmpty()) {
            dao.insertVenues(resetVenues)
        }
        launchCloudWrite { remote ->
            remote.clearAllUserCollections(
                clearVenues = false,
                clearReviews = false,
                clearBookings = true,
                clearQueueItems = true
            )
            remote.upsertVenuesBatch(resetVenues)
        }
    }

    suspend fun adjustLoyaltyPoints(delta: Int) {
        prefs.addLoyaltyPoints(delta)
        val snapshot = prefs.userPreferences.first()
        launchCloudWrite { it.upsertConfig(snapshot) }
    }

    suspend fun resetLoyaltyPoints() {
        prefs.setLoyaltyPoints(0)
        val snapshot = prefs.userPreferences.first()
        launchCloudWrite { it.upsertConfig(snapshot) }
    }

    suspend fun factoryResetAllData() {
        dao.deleteAllBookings()
        dao.deleteAllQueueItems()
        dao.deleteAllReviews()
        dao.deleteAllVenues()
        val venues = SeedData.initialVenues()
        val reviews = SeedData.initialReviews()
        val queueItems = SeedData.initialQueueItems(venues)
        dao.insertVenues(venues)
        dao.insertReviews(reviews)
        dao.insertQueueItems(queueItems)
        prefs.saveControlConfig(UserAppControlConfig())
        prefs.setLoyaltyPoints(60)

        val savedVenues = dao.getAllVenuesOnce()
        val savedReviews = dao.getAllReviewsOnce()
        val savedQueue = dao.getAllQueueItemsOnce()
        val savedConfig = prefs.userPreferences.first()
        launchCloudWrite { remote ->
            remote.clearAllUserCollections(
                clearVenues = true,
                clearReviews = true,
                clearBookings = true,
                clearQueueItems = true
            )
            remote.upsertConfig(savedConfig)
            remote.upsertVenuesBatch(savedVenues)
            remote.upsertReviewsBatch(savedReviews)
            remote.upsertQueueItemsBatch(savedQueue)
        }
    }
}
