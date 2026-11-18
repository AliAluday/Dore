package com.example.data.repository

import com.example.bridge.BookingDecisionSnapshot
import com.example.bridge.ControlStatePayload
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
import com.example.data.model.NinevehDistrict
import com.example.data.model.ProviderRole
import com.example.data.model.QueueItemStatus
import com.example.data.model.ServiceCategory
import com.example.data.model.SupportCategory
import com.example.data.model.SupportRequestType
import com.example.data.model.SupportTicketStatus
import com.example.data.model.TicketStage
import com.example.data.model.UserAppControlConfig
import com.example.data.model.UserProfileSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class DoriRepository(
    private val dao: DoriDao,
    private val prefs: UserPreferencesDataStore
) {
    val venues: Flow<List<VenueEntity>> = dao.observeAllVenues()
    val reviews: Flow<List<ReviewEntity>> = dao.observeAllReviews()
    val bookings: Flow<List<BookingEntity>> = dao.observeAllBookings()
    val queueItems: Flow<List<MerchantQueueItemEntity>> = dao.observeAllQueueItems()
    val supportTickets: Flow<List<SupportTicketEntity>> = dao.observeAllSupportTickets()
    val supportMessages: Flow<List<SupportMessageEntity>> = dao.observeAllSupportMessages()
    val admins: Flow<List<AdminAccountEntity>> = dao.observeAllAdmins()
    val providers: Flow<List<ServiceProviderEntity>> = dao.observeAllProviders()
    val userPreferences: Flow<UserProfileSettings> = prefs.userPreferences

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
        if (dao.getAllAdminsOnce().isEmpty()) {
            dao.insertAdmins(SeedData.initialAdmins())
            prefs.setActiveAdminId(1L)
        }
        if (dao.getAllProvidersOnce().isEmpty()) {
            dao.insertProviders(SeedData.initialProviders())
        }
    }

    suspend fun setSelectedDistrict(district: NinevehDistrict) {
        prefs.setSelectedDistrict(district)
    }

    suspend fun setDarkMode(enabled: Boolean) {
        prefs.setDarkMode(enabled)
    }

    suspend fun setMuteSound(muted: Boolean) {
        prefs.setMuteSound(muted)
    }

    suspend fun setMuteVibration(muted: Boolean) {
        prefs.setMuteVibration(muted)
    }

    suspend fun updateProfile(
        name: String,
        phone: String,
        district: NinevehDistrict,
        age: String = "",
        optionalNote: String = ""
    ) {
        prefs.updateProfile(name, phone, district, age, optionalNote)
    }

    suspend fun toggleFavorite(venueId: Int, currentFavorite: Boolean) {
        dao.setFavorite(venueId, !currentFavorite)
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
            return Result.failure(IllegalStateException("الحجوزات متوقفة مؤقتاً حالياً"))
        }

        val cleanName = customerName.trim()
        val cleanPhone = UserProfileSettings.normalizeDigits(customerPhone.trim())
        if (!UserProfileSettings.isValidFullName(cleanName)) {
            return Result.failure(IllegalArgumentException("الاسم الكامل إلزامي (3 أحرف على الأقل)"))
        }
        if (!UserProfileSettings.isValidPhone(cleanPhone)) {
            return Result.failure(IllegalArgumentException("رقم الهاتف إلزامي (10 إلى 14 رقماً)"))
        }

        val venue = dao.getVenueById(venueId)
            ?: return Result.failure(IllegalStateException("المكان غير موجود"))
        if (venue.category in ctrl.disabledCategories) {
            return Result.failure(IllegalStateException("هذا القسم غير متاح للحجز حالياً"))
        }
        if (!venue.isOpen || !venue.isAcceptingBookings) {
            return Result.failure(IllegalStateException("هذا المكان متوقف عن استقبال الحجوزات حالياً"))
        }

        val queueItems = dao.getQueueItemsForVenueOnce(venueId)
        val existingBookings = dao.getBookingsForVenueOnce(venueId)
        val activeWaiting = queueItems.count { it.status == QueueItemStatus.WAITING.name }
        val activeServing = queueItems.count { it.status == QueueItemStatus.SERVING.name }
        val maxQueueTicket = queueItems.maxOfOrNull { it.ticketNumber } ?: 0
        val maxBookingTicket = existingBookings.maxOfOrNull { it.queueNumber } ?: 0
        val ticketNum = maxOf(1, maxOf(maxQueueTicket, maxBookingTicket) + 1)
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

        dao.insertQueueItem(
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

        val updatedWaitingCount = activeWaiting + if (peopleAhead > 0) 1 else 0
        val updatedServingNumber = if (peopleAhead == 0) ticketNum else venue.currentServingNumber
        val updatedVenue = venue.copy(
            currentServingNumber = updatedServingNumber,
            nextTicketNumber = ticketNum + 1,
            waitingCount = updatedWaitingCount
        )
        dao.updateVenue(updatedVenue)

        if (currentPrefs.userName.isBlank() || currentPrefs.userPhone.isBlank()) {
            prefs.updateProfile(
                name = currentPrefs.userName.ifBlank { cleanName },
                phone = currentPrefs.userPhone.ifBlank { cleanPhone },
                district = currentPrefs.selectedDistrict,
                age = currentPrefs.userAge,
                optionalNote = currentPrefs.optionalNote
            )
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
        qItems.find { it.bookingId == bookingId }?.let { item ->
            dao.updateQueueItem(item.copy(status = QueueItemStatus.DONE.name))
        }

        val venue = dao.getVenueById(booking.venueId)
        if (venue != null) {
            val updatedVenue = if (effectiveRating != null && effectiveRating in 1..5) {
                val review = ReviewEntity(
                    venueId = venue.id,
                    authorName = booking.customerName,
                    rating = effectiveRating,
                    comment = effectiveComment ?: "خدمة ممتازة وتجربة منظمة عبر دوري"
                )
                dao.insertReview(review)
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
        if (!alreadyRated) {
            val review = ReviewEntity(
                venueId = booking.venueId,
                authorName = booking.customerName,
                rating = cleanRating,
                comment = cleanComment
            )
            dao.insertReview(review)
            dao.getVenueById(booking.venueId)?.let { venue ->
                val newCount = venue.reviewCount + 1
                val newRating = ((venue.rating * venue.reviewCount) + cleanRating) / newCount
                val rounded = kotlin.math.round(newRating * 10.0) / 10.0
                dao.updateVenue(venue.copy(rating = rounded, reviewCount = newCount))
            }
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
        qItems.find { it.bookingId == bookingId }?.let { item ->
            dao.updateQueueItem(item.copy(status = QueueItemStatus.SKIPPED.name))
        }
        syncVenueQueueAndBookings(booking.venueId)
    }

    suspend fun togglePauseQueue(venueId: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        dao.updateVenue(venue.copy(isPaused = !venue.isPaused))
        prefs.bumpControlRevision()
    }

    suspend fun toggleAcceptingBookings(venueId: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        dao.updateVenue(venue.copy(isAcceptingBookings = !venue.isAcceptingBookings))
        prefs.bumpControlRevision()
    }

    suspend fun getBookingById(bookingId: Long): BookingEntity? = dao.getBookingById(bookingId)

    suspend fun clearStaleLocalActiveBookingsBefore(cutoffTimestamp: Long) {
        if (cutoffTimestamp > 0L) {
            dao.deleteActiveBookingsCreatedAtOrBefore(cutoffTimestamp)
            dao.deleteAllQueueItems()
        }
    }

    suspend fun addWalkInCustomer(venueId: Int, name: String, option: String) {
        val venue = dao.getVenueById(venueId) ?: return
        val existingItems = dao.getQueueItemsForVenueOnce(venueId)
        val existingBookings = dao.getBookingsForVenueOnce(venueId)
        val maxTicket = maxOf(
            existingItems.maxOfOrNull { it.ticketNumber } ?: 0,
            existingBookings.maxOfOrNull { it.queueNumber } ?: 0
        )
        val num = (maxTicket + 1).coerceAtLeast(1)
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
        dao.insertQueueItem(newItem)
        dao.updateVenue(
            venue.copy(
                currentServingNumber = if (!hasServing) num else venue.currentServingNumber,
                nextTicketNumber = num + 1
            )
        )
        syncVenueQueueAndBookings(venueId)
        prefs.bumpControlRevision()
    }

    suspend fun callNextCustomer(venueId: Int): List<LiveTurnAlert> {
        val venue = dao.getVenueById(venueId) ?: return emptyList()
        val items = dao.getQueueItemsForVenueOnce(venueId)

        val currentServing = items.firstOrNull { it.status == QueueItemStatus.SERVING.name }
        if (currentServing != null) {
            dao.updateQueueItem(currentServing.copy(status = QueueItemStatus.DONE.name))
            currentServing.bookingId?.let { bId ->
                val b = dao.getBookingById(bId)
                if (b != null && b.status == BookingStatus.ACTIVE.name) {
                    dao.updateBooking(
                        b.copy(
                            status = BookingStatus.COMPLETED.name,
                            completedAt = System.currentTimeMillis(),
                            peopleAhead = 0,
                            estimatedMinutes = 0
                        )
                    )
                    val ctrl = prefs.userPreferences.first().controlConfig
                    if (ctrl.enableLoyalty && ctrl.pointsPerBooking > 0) {
                        prefs.addLoyaltyPoints(ctrl.pointsPerBooking)
                    }
                }
            }
        }
        val nextWaiting = items.firstOrNull { it.status == QueueItemStatus.WAITING.name }
        if (nextWaiting != null) {
            dao.updateQueueItem(nextWaiting.copy(status = QueueItemStatus.SERVING.name))
            dao.updateVenue(
                venue.copy(
                    currentServingNumber = nextWaiting.ticketNumber,
                    servedTodayCount = venue.servedTodayCount + (if (currentServing != null) 1 else 0)
                )
            )
        } else if (currentServing != null) {
            dao.updateVenue(venue.copy(servedTodayCount = venue.servedTodayCount + 1))
        }
        return syncVenueQueueAndBookings(venueId).also {
            prefs.bumpControlRevision()
        }
    }

    suspend fun skipCurrentCustomer(venueId: Int): List<LiveTurnAlert> {
        val venue = dao.getVenueById(venueId) ?: return emptyList()
        val items = dao.getQueueItemsForVenueOnce(venueId)

        val currentServing = items.firstOrNull { it.status == QueueItemStatus.SERVING.name }
        if (currentServing != null) {
            dao.updateQueueItem(currentServing.copy(status = QueueItemStatus.SKIPPED.name))
            currentServing.bookingId?.let { bId ->
                dao.getBookingById(bId)?.let { b ->
                    if (b.status == BookingStatus.ACTIVE.name) {
                        dao.updateBooking(
                            b.copy(status = BookingStatus.CANCELLED.name, completedAt = System.currentTimeMillis())
                        )
                    }
                }
            }
        }
        val nextWaiting = items.firstOrNull { it.status == QueueItemStatus.WAITING.name }
        if (nextWaiting != null) {
            dao.updateQueueItem(nextWaiting.copy(status = QueueItemStatus.SERVING.name))
            dao.updateVenue(venue.copy(currentServingNumber = nextWaiting.ticketNumber))
        }
        return syncVenueQueueAndBookings(venueId).also {
            prefs.bumpControlRevision()
        }
    }

    suspend fun callCustomerByName(venueId: Int, queueItemId: Long): List<LiveTurnAlert> {
        val venue = dao.getVenueById(venueId) ?: return emptyList()
        val items = dao.getQueueItemsForVenueOnce(venueId)
        val target = items.find { it.id == queueItemId } ?: return emptyList()

        items.filter { it.status == QueueItemStatus.SERVING.name && it.id != queueItemId }
            .forEach { prev ->
                dao.updateQueueItem(prev.copy(status = QueueItemStatus.DONE.name))
            }
        dao.updateQueueItem(target.copy(status = QueueItemStatus.SERVING.name))
        dao.updateVenue(venue.copy(currentServingNumber = target.ticketNumber))
        return syncVenueQueueAndBookings(venueId).also {
            prefs.bumpControlRevision()
        }
    }

    suspend fun markCustomerNoShow(venueId: Int, queueItemId: Long): List<LiveTurnAlert> {
        val items = dao.getQueueItemsForVenueOnce(venueId)
        val target = items.find { it.id == queueItemId } ?: return emptyList()
        dao.updateQueueItem(target.copy(status = QueueItemStatus.NO_SHOW.name))
        target.bookingId?.let { bId ->
            dao.getBookingById(bId)?.let { b ->
                if (b.status == BookingStatus.ACTIVE.name) {
                    dao.updateBooking(
                        b.copy(
                            status = BookingStatus.CANCELLED.name,
                            completedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return syncVenueQueueAndBookings(venueId).also {
            prefs.bumpControlRevision()
        }
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
        if (alerts.isNotEmpty()) {
            prefs.bumpControlRevision()
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

        return alerts
    }

    // ==================== Control Panel Commands ====================

    suspend fun updateControlConfig(transform: (UserAppControlConfig) -> UserAppControlConfig) {
        val current = prefs.userPreferences.first().controlConfig
        prefs.saveControlConfigAndBumpRevision(transform(current))
    }

    suspend fun resetControlConfig() {
        prefs.saveControlConfigAndBumpRevision(UserAppControlConfig())
    }

    suspend fun toggleVenueOpen(venueId: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        dao.updateVenue(venue.copy(isOpen = !venue.isOpen))
        prefs.bumpControlRevision()
    }

    suspend fun adjustVenuePrice(venueId: Int, deltaDinar: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        val nextPrice = (venue.priceDinar + deltaDinar).coerceIn(1000, 250000)
        dao.updateVenue(venue.copy(priceDinar = nextPrice))
        prefs.bumpControlRevision()
    }

    suspend fun adjustVenueWaitMinutes(venueId: Int, deltaMinutes: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        val nextAvg = (venue.avgMinutesPerPerson + deltaMinutes).coerceIn(1, 60)
        dao.updateVenue(venue.copy(avgMinutesPerPerson = nextAvg))
        syncVenueQueueAndBookings(venueId)
        prefs.bumpControlRevision()
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
            specialty = if (category == ServiceCategory.CLINICS) specialty.ifBlank { "طب عام" } else "",
            area = cleanArea,
            priceDinar = priceDinar.coerceIn(1000, 250000),
            rating = 5.0,
            reviewCount = 1,
            workingHours = "نفس اليوم",
            phone = cleanPhone,
            whatsapp = cleanPhone,
            latitude = 36.3489,
            longitude = 43.1577,
            description = "مركز خدمة معتمد في ${district.displayName} • حجز بنفس اليوم",
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
        prefs.bumpControlRevision()
        return Result.success(newVenue)
    }

    suspend fun deleteVenue(venueId: Int) {
        val venueBookings = dao.getBookingsForVenueOnce(venueId)
        val now = System.currentTimeMillis()
        venueBookings.filter { it.status == BookingStatus.ACTIVE.name }.forEach { b ->
            dao.updateBooking(
                b.copy(
                    status = BookingStatus.CANCELLED.name,
                    approvalStatus = BookingApprovalStatus.REJECTED.name,
                    approvalNote = "تم إلغاء الحجز بسبب إزالة المركز",
                    completedAt = now
                )
            )
        }
        val activeProviderId = prefs.userPreferences.first().activeProviderId
        dao.getProvidersForVenueOnce(venueId).forEach { prov ->
            if (prov.isActive) {
                dao.updateProvider(prov.copy(isActive = false))
            }
            if (prov.id == activeProviderId) {
                prefs.setActiveProviderId(0L)
            }
        }
        dao.deleteQueueItemsForVenue(venueId)
        dao.deleteVenueById(venueId)
        prefs.bumpControlRevision()
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
            prefs.bumpControlRevision()
        }
    }

    suspend fun completeAllActiveBookings(): Int {
        val active = dao.getActiveBookingsOnce()
        active.forEach { b ->
            completeBookingWithOptionalReview(b.id, null, null)
        }
        prefs.bumpControlRevision(bumpQueueReset = true)
        return active.size
    }

    suspend fun cancelAllActiveBookings(): Int {
        val active = dao.getActiveBookingsOnce()
        active.forEach { b ->
            cancelBooking(b.id)
        }
        prefs.bumpControlRevision(bumpQueueReset = true)
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
        prefs.bumpControlRevision(bumpQueueReset = true)
    }

    suspend fun adjustLoyaltyPoints(delta: Int) {
        prefs.addLoyaltyPoints(delta)
        prefs.bumpControlRevision(bumpLoyalty = true)
    }

    suspend fun resetLoyaltyPoints() {
        prefs.setLoyaltyPoints(0)
        prefs.bumpControlRevision(bumpLoyalty = true)
    }

    suspend fun factoryResetAllData() {
        dao.deleteAllBookings()
        dao.deleteAllQueueItems()
        dao.deleteAllReviews()
        dao.deleteAllVenues()
        dao.deleteAllSupportMessages()
        dao.deleteAllSupportTickets()
        dao.deleteAllAdmins()
        dao.deleteAllProviders()
        val venues = SeedData.initialVenues()
        val reviews = SeedData.initialReviews()
        val queueItems = SeedData.initialQueueItems(venues)
        dao.insertVenues(venues)
        dao.insertReviews(reviews)
        dao.insertQueueItems(queueItems)
        dao.insertAdmins(SeedData.initialAdmins())
        dao.insertProviders(SeedData.initialProviders())
        prefs.setActiveAdminId(1L)
        prefs.setActiveProviderId(0L)
        prefs.saveControlConfig(UserAppControlConfig())
        prefs.setLoyaltyPoints(60)
        prefs.bumpControlRevision(bumpQueueReset = true, bumpLoyalty = true)
    }

    // ==================== Cross-App Bridge Snapshots & Merge ====================

    suspend fun getAppliedBridgeSyncState(): UserPreferencesDataStore.AppliedBridgeSyncState =
        prefs.getAppliedBridgeSyncState()

    suspend fun getControlBridgeSnapshot(): Triple<ControlStatePayload, List<VenueEntity>, List<ServiceProviderEntity>> {
        val meta = prefs.getControlSyncMetadata()
        val payload = ControlStatePayload(
            controlConfig = meta.controlConfig,
            controlRevision = meta.controlRevision,
            queueResetRevision = meta.queueResetRevision,
            loyaltyRevision = meta.loyaltyRevision,
            loyaltyPoints = meta.loyaltyPoints
        )
        return Triple(payload, dao.getAllVenuesOnce(), dao.getAllProvidersOnce())
    }

    suspend fun applyPulledControlState(payload: ControlStatePayload): Boolean =
        prefs.applySyncedControlStateFromBridge(
            controlConfig = payload.controlConfig,
            controlRevision = payload.controlRevision,
            queueResetRevision = payload.queueResetRevision,
            loyaltyRevision = payload.loyaltyRevision,
            loyaltyPointsFromControl = payload.loyaltyPoints
        )

    suspend fun mergeSyncedVenuesFromControl(
        remoteVenues: List<VenueEntity>,
        queueResetChanged: Boolean = false
    ): Int {
        val localVenues = dao.getAllVenuesOnce()
        val localById = localVenues.associateBy { it.id }
        val activeBookingVenueIds = dao.getActiveBookingsOnce().map { it.venueId }.toSet()

        val merged = remoteVenues.map { remote ->
            val local = localById[remote.id]
            val keepLocalFav = local?.isFavorite ?: false
            if (local == null || queueResetChanged) {
                remote.copy(isFavorite = keepLocalFav)
            } else {
                val hasLocalUnpushedBooking = remote.id in activeBookingVenueIds &&
                    local.nextTicketNumber > remote.nextTicketNumber
                remote.copy(
                    isFavorite = keepLocalFav,
                    currentServingNumber = if (hasLocalUnpushedBooking) {
                        maxOf(local.currentServingNumber, remote.currentServingNumber)
                    } else {
                        remote.currentServingNumber
                    },
                    nextTicketNumber = if (hasLocalUnpushedBooking) {
                        maxOf(local.nextTicketNumber, remote.nextTicketNumber)
                    } else {
                        remote.nextTicketNumber
                    },
                    waitingCount = if (hasLocalUnpushedBooking) {
                        maxOf(local.waitingCount, remote.waitingCount)
                    } else {
                        remote.waitingCount
                    },
                    servedTodayCount = maxOf(local.servedTodayCount, remote.servedTodayCount)
                )
            }
        }

        val localSorted = localVenues.sortedBy { it.id }
        val mergedSorted = merged.sortedBy { it.id }
        if (localSorted == mergedSorted) return 0

        dao.replaceAllVenues(merged)
        return 1
    }

    suspend fun mergeSyncedProvidersFromControl(remoteProviders: List<ServiceProviderEntity>): Int {
        val localSorted = dao.getAllProvidersOnce().sortedBy { it.id }
        val remoteSorted = remoteProviders.sortedBy { it.id }
        if (localSorted == remoteSorted) return 0
        dao.replaceAllProviders(remoteProviders)
        return 1
    }

    suspend fun getAllBookingsSnapshot(): List<BookingEntity> = dao.getAllBookingsOnce()

    suspend fun bumpControlBridgeRevision(
        bumpQueueReset: Boolean = false,
        bumpLoyalty: Boolean = false
    ) {
        prefs.bumpControlRevision(bumpQueueReset = bumpQueueReset, bumpLoyalty = bumpLoyalty)
    }

    // ==================== Support Center ====================

    suspend fun getAllSupportTicketsSnapshot(): List<SupportTicketEntity> =
        dao.getAllSupportTicketsOnce()

    suspend fun getAllSupportMessagesSnapshot(): List<SupportMessageEntity> =
        dao.getAllSupportMessagesOnce()

    suspend fun markSupportMessageSynced(messageId: Long) {
        val all = dao.getAllSupportMessagesOnce()
        val existing = all.find { it.id == messageId } ?: return
        if (!existing.syncedToPeer) {
            dao.updateSupportMessage(existing.copy(syncedToPeer = true))
        }
    }

    suspend fun createSupportTicket(
        senderName: String,
        senderPhone: String,
        category: SupportCategory,
        subject: String,
        message: String,
        requestType: SupportRequestType = SupportRequestType.CHAT,
        relatedVenueName: String = "",
        minMessageLength: Int = if (requestType == SupportRequestType.COMPLAINT) 4 else 1
    ): Result<SupportTicketEntity> {
        val cleanName = senderName.trim().ifBlank { "مستخدم دوري" }
        val cleanPhone = senderPhone.trim()
        val cleanVenue = relatedVenueName.trim()
        val cleanSubject = subject.trim().ifBlank {
            if (requestType == SupportRequestType.COMPLAINT) "طلب شكوى: ${category.displayName}" else category.displayName
        }
        val cleanMessage = message.trim()
        if (cleanMessage.length < minMessageLength) {
            return Result.failure(
                IllegalArgumentException(
                    if (minMessageLength >= 4) "يرجى كتابة تفاصيل الطلب (4 أحرف على الأقل)"
                    else "يرجى كتابة رسالتك قبل الإرسال"
                )
            )
        }
        val now = System.currentTimeMillis()
        val prefix = if (requestType == SupportRequestType.COMPLAINT) "CMP" else "SUP"
        val extId = "$prefix-${UUID.randomUUID().toString().take(8).uppercase()}"
        val entity = SupportTicketEntity(
            externalId = extId,
            senderName = cleanName,
            senderPhone = cleanPhone,
            category = category.name,
            requestType = requestType.name,
            relatedVenueName = cleanVenue,
            subject = cleanSubject,
            message = cleanMessage,
            status = SupportTicketStatus.SENT.name,
            syncedToControl = false,
            createdAt = now,
            updatedAt = now
        )
        val id = dao.insertSupportTicket(entity)
        val initialMessage = SupportMessageEntity(
            externalMessageId = "MSG-${UUID.randomUUID().toString().take(10).uppercase()}",
            ticketExternalId = extId,
            isFromAdmin = false,
            senderName = cleanName,
            body = cleanMessage,
            syncedToPeer = false,
            createdAt = now
        )
        dao.insertSupportMessage(initialMessage)
        return Result.success(entity.copy(id = id))
    }

    suspend fun sendUserSupportFollowUp(
        ticketExternalId: String,
        senderName: String,
        messageBody: String
    ): Result<Pair<SupportTicketEntity, SupportMessageEntity>> {
        val cleanBody = messageBody.trim()
        if (cleanBody.isEmpty()) {
            return Result.failure(IllegalArgumentException("يرجى كتابة الرسالة قبل الإرسال"))
        }
        val existing = dao.getSupportTicketByExternalId(ticketExternalId)
            ?: return Result.failure(IllegalStateException("المحادثة غير موجودة"))
        val cleanName = senderName.trim().ifBlank { existing.senderName.ifBlank { "مستخدم دوري" } }
        val now = System.currentTimeMillis()
        val msgEntity = SupportMessageEntity(
            externalMessageId = "MSG-${UUID.randomUUID().toString().take(10).uppercase()}",
            ticketExternalId = existing.externalId,
            isFromAdmin = false,
            senderName = cleanName,
            body = cleanBody,
            syncedToPeer = false,
            createdAt = now
        )
        val msgId = dao.insertSupportMessage(msgEntity)
        val updatedTicket = existing.copy(
            senderName = if (cleanName != "مستخدم دوري") cleanName else existing.senderName,
            status = SupportTicketStatus.SENT.name,
            syncedToControl = false,
            updatedAt = now
        )
        dao.updateSupportTicket(updatedTicket)
        return Result.success(updatedTicket to msgEntity.copy(id = msgId))
    }

    suspend fun markSupportTicketDelivered(ticketId: Long) {
        val existing = dao.getSupportTicketById(ticketId) ?: return
        val nextStatus = if (existing.status == SupportTicketStatus.SENT.name) {
            SupportTicketStatus.DELIVERED.name
        } else {
            existing.status
        }
        dao.updateSupportTicket(
            existing.copy(
                status = nextStatus,
                syncedToControl = true
            )
        )
    }

    suspend fun replyToSupportTicket(
        ticketId: Long,
        replyText: String,
        adminName: String
    ): Result<SupportTicketEntity> {
        val cleanReply = replyText.trim()
        if (cleanReply.isEmpty()) {
            return Result.failure(IllegalArgumentException("يرجى كتابة رد واضح للمستخدم"))
        }
        val existing = dao.getSupportTicketById(ticketId)
            ?: return Result.failure(IllegalStateException("رسالة الدعم غير موجودة"))
        val adminLabel = adminName.ifBlank { "فريق الدعم الفني" }
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            adminReply = cleanReply,
            repliedByAdmin = adminLabel,
            status = SupportTicketStatus.REPLIED.name,
            syncedToControl = true,
            updatedAt = now
        )
        dao.updateSupportTicket(updated)
        dao.insertSupportMessage(
            SupportMessageEntity(
                externalMessageId = "MSG-${UUID.randomUUID().toString().take(10).uppercase()}",
                ticketExternalId = existing.externalId,
                isFromAdmin = true,
                senderName = adminLabel,
                body = cleanReply,
                syncedToPeer = false,
                createdAt = now
            )
        )
        return Result.success(updated)
    }

    suspend fun updateSupportTicketStatus(ticketId: Long, status: SupportTicketStatus): SupportTicketEntity? {
        val existing = dao.getSupportTicketById(ticketId) ?: return null
        val updated = existing.copy(
            status = status.name,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateSupportTicket(updated)
        return updated
    }

    suspend fun mergePulledSupportTickets(incoming: List<SupportTicketEntity>, isControlApp: Boolean): Int {
        var changed = 0
        for (remote in incoming) {
            if (remote.externalId.isBlank()) continue
            val sanitizedRepliedBy = if (!isControlApp && remote.repliedByAdmin.isNotBlank()) {
                "فريق الدعم الفني"
            } else {
                remote.repliedByAdmin
            }
            val local = dao.getSupportTicketByExternalId(remote.externalId)
            if (local == null) {
                dao.insertSupportTicket(
                    remote.copy(
                        id = 0L,
                        status = if (isControlApp && remote.status == SupportTicketStatus.SENT.name) {
                            SupportTicketStatus.DELIVERED.name
                        } else {
                            remote.status
                        },
                        repliedByAdmin = sanitizedRepliedBy,
                        syncedToControl = true
                    )
                )
                changed++
            } else if (remote.updatedAt > local.updatedAt ||
                (remote.adminReply.isNotBlank() && local.adminReply != remote.adminReply) ||
                (remote.status != local.status && remote.status == SupportTicketStatus.REPLIED.name) ||
                (isControlApp && remote.senderName.isNotBlank() && local.senderName != remote.senderName)
            ) {
                val resolvedStatus = when {
                    local.status == SupportTicketStatus.CLOSED.name && remote.updatedAt <= local.updatedAt ->
                        SupportTicketStatus.CLOSED.name
                    isControlApp && (local.status == SupportTicketStatus.REPLIED.name || local.status == SupportTicketStatus.CLOSED.name) &&
                        (remote.status == SupportTicketStatus.SENT.name || remote.status == SupportTicketStatus.DELIVERED.name) &&
                        remote.updatedAt <= local.updatedAt -> local.status
                    isControlApp && remote.status == SupportTicketStatus.SENT.name -> SupportTicketStatus.DELIVERED.name
                    !isControlApp && local.status == SupportTicketStatus.CLOSED.name &&
                        (remote.status == SupportTicketStatus.SENT.name || remote.status == SupportTicketStatus.DELIVERED.name) ->
                        local.status
                    remote.status.isNotBlank() -> remote.status
                    else -> local.status
                }
                dao.updateSupportTicket(
                    local.copy(
                        senderName = if (isControlApp && remote.senderName.isNotBlank()) remote.senderName else local.senderName,
                        status = resolvedStatus,
                        requestType = remote.requestType.ifBlank { local.requestType },
                        relatedVenueName = remote.relatedVenueName.ifBlank { local.relatedVenueName },
                        adminReply = remote.adminReply.ifBlank { local.adminReply },
                        repliedByAdmin = if (isControlApp) {
                            remote.repliedByAdmin.ifBlank { local.repliedByAdmin }
                        } else if (sanitizedRepliedBy.isNotBlank() || local.repliedByAdmin.isNotBlank()) {
                            "فريق الدعم الفني"
                        } else {
                            ""
                        },
                        syncedToControl = true,
                        updatedAt = maxOf(local.updatedAt, remote.updatedAt)
                    )
                )
                changed++
            }
            if (remote.adminReply.isNotBlank()) {
                val existingMsgs = dao.getMessagesForTicketOnce(remote.externalId)
                val alreadyHasReply = existingMsgs.any {
                    it.isFromAdmin && it.body.trim() == remote.adminReply.trim()
                }
                if (!alreadyHasReply) {
                    val synthId = "SYNC-A-${remote.externalId}-${remote.adminReply.hashCode()}"
                    if (dao.getSupportMessageByExternalId(synthId) == null) {
                        dao.insertSupportMessage(
                            SupportMessageEntity(
                                externalMessageId = synthId,
                                ticketExternalId = remote.externalId,
                                isFromAdmin = true,
                                senderName = if (isControlApp) {
                                    remote.repliedByAdmin.ifBlank { "فريق الدعم الفني" }
                                } else {
                                    "فريق الدعم الفني"
                                },
                                body = remote.adminReply,
                                syncedToPeer = true,
                                createdAt = remote.updatedAt
                            )
                        )
                        changed++
                    }
                }
            }
        }
        return changed
    }

    suspend fun mergePulledSupportMessages(
        incoming: List<SupportMessageEntity>,
        isControlApp: Boolean = false
    ): Int {
        var added = 0
        for (remote in incoming) {
            if (remote.externalMessageId.isBlank() || remote.ticketExternalId.isBlank()) continue
            if (dao.getSupportMessageByExternalId(remote.externalMessageId) == null) {
                val sanitizedSender = if (!isControlApp && remote.isFromAdmin) {
                    "فريق الدعم الفني"
                } else {
                    remote.senderName
                }
                dao.insertSupportMessage(
                    remote.copy(
                        id = 0L,
                        senderName = sanitizedSender,
                        syncedToPeer = true
                    )
                )
                added++
                val parent = dao.getSupportTicketByExternalId(remote.ticketExternalId)
                if (remote.isFromAdmin && remote.body.isNotBlank()) {
                    if (parent != null && parent.status != SupportTicketStatus.CLOSED.name) {
                        dao.updateSupportTicket(
                            parent.copy(
                                status = SupportTicketStatus.REPLIED.name,
                                adminReply = remote.body,
                                repliedByAdmin = if (isControlApp) {
                                    parent.repliedByAdmin.ifBlank { remote.senderName.ifBlank { "فريق الدعم الفني" } }
                                } else {
                                    "فريق الدعم الفني"
                                },
                                updatedAt = maxOf(parent.updatedAt, remote.createdAt)
                            )
                        )
                    }
                } else if (isControlApp && !remote.isFromAdmin && remote.body.isNotBlank()) {
                    if (parent != null && parent.status != SupportTicketStatus.CLOSED.name && remote.createdAt > parent.updatedAt) {
                        dao.updateSupportTicket(
                            parent.copy(
                                senderName = remote.senderName.ifBlank { parent.senderName },
                                status = SupportTicketStatus.DELIVERED.name,
                                syncedToControl = true,
                                updatedAt = remote.createdAt
                            )
                        )
                    }
                }
            }
        }
        return added
    }

    suspend fun mergePulledBookingsForControl(incoming: List<BookingEntity>): Int {
        var added = 0
        for (remote in incoming) {
            if (remote.ticketCode.isBlank()) continue
            if (dao.getBookingByTicketCode(remote.ticketCode) == null) {
                val bId = dao.insertBooking(remote.copy(id = 0L))
                dao.insertQueueItem(
                    MerchantQueueItemEntity(
                        venueId = remote.venueId,
                        ticketNumber = remote.queueNumber,
                        ticketCode = remote.ticketCode,
                        customerName = remote.customerName,
                        customerPhone = remote.customerPhone,
                        selectedOption = remote.selectedOption,
                        status = if (remote.peopleAhead == 0) QueueItemStatus.SERVING.name else QueueItemStatus.WAITING.name,
                        bookingId = bId,
                        createdAt = remote.createdAt
                    )
                )
                dao.getVenueById(remote.venueId)?.let { v ->
                    dao.updateVenue(
                        v.copy(
                            nextTicketNumber = maxOf(v.nextTicketNumber, remote.queueNumber + 1),
                            waitingCount = if (remote.peopleAhead > 0) v.waitingCount + 1 else v.waitingCount,
                            currentServingNumber = if (remote.peopleAhead == 0 && v.currentServingNumber == 0) {
                                remote.queueNumber
                            } else {
                                v.currentServingNumber
                            }
                        )
                    )
                }
                added++
            }
        }
        return added
    }

    suspend fun applyBookingApprovalDecisionLocal(decision: BookingDecisionSnapshot): Boolean {
        val booking = dao.getBookingByTicketCode(decision.ticketCode) ?: return false
        val nextStatus = decision.status.takeIf { it.isNotBlank() } ?: booking.status
        val nextStage = decision.stage.takeIf { it.isNotBlank() } ?: booking.stage
        val nextAhead = if (decision.peopleAhead >= 0) decision.peopleAhead else booking.peopleAhead
        val nextEta = if (decision.estimatedMinutes >= 0) decision.estimatedMinutes else booking.estimatedMinutes
        val nextCompletedAt = if (nextStatus != BookingStatus.ACTIVE.name && booking.completedAt == null) {
            System.currentTimeMillis()
        } else {
            booking.completedAt
        }
        if (booking.approvalStatus == decision.approvalStatus &&
            booking.handledByProvider == decision.handledByProvider &&
            booking.approvalNote == decision.approvalNote &&
            booking.status == nextStatus &&
            booking.stage == nextStage &&
            booking.peopleAhead == nextAhead &&
            booking.estimatedMinutes == nextEta
        ) {
            return false
        }
        dao.updateBooking(
            booking.copy(
                approvalStatus = decision.approvalStatus,
                handledByProvider = decision.handledByProvider,
                approvalNote = decision.approvalNote,
                status = nextStatus,
                stage = nextStage,
                peopleAhead = nextAhead,
                estimatedMinutes = nextEta,
                completedAt = nextCompletedAt
            )
        )
        return true
    }

    // ==================== Admin Management ====================

    suspend fun setActiveAdmin(adminId: Long) {
        prefs.setActiveAdminId(adminId)
    }

    suspend fun addAdminAccount(
        name: String,
        roleTitle: String,
        phone: String,
        canManageFeatures: Boolean,
        canManageVenues: Boolean,
        canManageQueues: Boolean,
        canReplySupport: Boolean,
        canManageProviders: Boolean,
        canManageAdmins: Boolean,
        canRunCommands: Boolean
    ): Result<AdminAccountEntity> {
        val cleanName = name.trim()
        if (cleanName.length < 3) {
            return Result.failure(IllegalArgumentException("اسم الأدمن يجب أن لا يقل عن 3 أحرف"))
        }
        val admin = AdminAccountEntity(
            name = cleanName,
            roleTitle = roleTitle.trim().ifBlank { "مشرف تشغيل" },
            phone = phone.trim(),
            isActive = true,
            isSuperAdmin = false,
            canManageFeatures = canManageFeatures,
            canManageVenues = canManageVenues,
            canManageQueues = canManageQueues,
            canReplySupport = canReplySupport,
            canManageProviders = canManageProviders,
            canManageAdmins = canManageAdmins,
            canRunCommands = canRunCommands
        )
        val id = dao.insertAdmin(admin)
        return Result.success(admin.copy(id = id))
    }

    suspend fun updateAdminAccount(admin: AdminAccountEntity) {
        val guarded = if (admin.isSuperAdmin) {
            admin.copy(isActive = true, canManageAdmins = true)
        } else {
            admin
        }
        dao.updateAdmin(guarded)
    }

    suspend fun deleteAdminAccount(adminId: Long): Result<Unit> {
        val target = dao.getAdminById(adminId)
            ?: return Result.failure(IllegalStateException("الحساب غير موجود"))
        if (target.isSuperAdmin) {
            return Result.failure(IllegalStateException("لا يمكن حذف حساب المدير العام الأساسي"))
        }
        dao.deleteAdminById(adminId)
        val currentActive = prefs.userPreferences.first().activeAdminId
        if (currentActive == adminId) {
            val fallback = dao.getAllAdminsOnce().firstOrNull()?.id ?: 1L
            prefs.setActiveAdminId(fallback)
        }
        return Result.success(Unit)
    }

    // ==================== Service Providers (Doctors / Field Owners / Merchants) ====================

    suspend fun setActiveProvider(providerId: Long) {
        prefs.setActiveProviderId(providerId)
    }

    suspend fun addServiceProvider(
        name: String,
        role: ProviderRole,
        customTitle: String,
        phone: String,
        venueId: Int,
        canAcceptTickets: Boolean,
        canCallNext: Boolean,
        canPauseVenue: Boolean
    ): Result<ServiceProviderEntity> {
        val cleanName = name.trim()
        if (cleanName.length < 3) {
            return Result.failure(IllegalArgumentException("الاسم يجب أن لا يقل عن 3 أحرف"))
        }
        val venue = dao.getVenueById(venueId)
            ?: return Result.failure(IllegalStateException("يرجى اختيار مركز صالح"))
        val provider = ServiceProviderEntity(
            name = cleanName,
            providerRole = role.name,
            customTitle = customTitle.trim().ifBlank { role.displayName },
            phone = phone.trim().ifBlank { venue.phone },
            venueId = venue.id,
            venueName = venue.name,
            canAcceptTickets = canAcceptTickets,
            canCallNext = canCallNext,
            canPauseVenue = canPauseVenue,
            isActive = true
        )
        val id = dao.insertProvider(provider)
        prefs.bumpControlRevision()
        return Result.success(provider.copy(id = id))
    }

    suspend fun updateServiceProvider(provider: ServiceProviderEntity) {
        dao.updateProvider(provider)
        val current = prefs.userPreferences.first().activeProviderId
        if (current == provider.id && !provider.isActive) {
            prefs.setActiveProviderId(0L)
        }
        prefs.bumpControlRevision()
    }

    suspend fun deleteServiceProvider(providerId: Long) {
        dao.deleteProviderById(providerId)
        val current = prefs.userPreferences.first().activeProviderId
        if (current == providerId) {
            prefs.setActiveProviderId(0L)
        }
        prefs.bumpControlRevision()
    }

    suspend fun acceptBookingTicket(
        bookingId: Long,
        actorName: String,
        note: String = "",
        providerId: Long? = null
    ): BookingEntity? {
        val booking = dao.getBookingById(bookingId) ?: return null
        if (booking.status != BookingStatus.ACTIVE.name) return null
        val updated = booking.copy(
            approvalStatus = BookingApprovalStatus.ACCEPTED.name,
            handledByProvider = actorName,
            approvalNote = note.trim().ifBlank { "تم تأكيد قبول التذكرة من $actorName" }
        )
        dao.updateBooking(updated)
        if (providerId != null && providerId > 0L) {
            dao.getProviderById(providerId)?.let { prov ->
                dao.updateProvider(prov.copy(acceptedCount = prov.acceptedCount + 1))
                prefs.bumpControlRevision()
            }
        }
        return updated
    }

    suspend fun rejectBookingTicket(
        bookingId: Long,
        actorName: String,
        note: String = ""
    ): BookingEntity? {
        val booking = dao.getBookingById(bookingId) ?: return null
        val updated = booking.copy(
            approvalStatus = BookingApprovalStatus.REJECTED.name,
            handledByProvider = actorName,
            approvalNote = note.trim().ifBlank { "تعذر استقبال الطلب حالياً — يرجى الحجز لاحقاً" },
            status = BookingStatus.CANCELLED.name,
            completedAt = System.currentTimeMillis()
        )
        dao.updateBooking(updated)
        val qItems = dao.getQueueItemsForVenueOnce(booking.venueId)
        qItems.find { it.bookingId == bookingId }?.let { item ->
            dao.updateQueueItem(item.copy(status = QueueItemStatus.SKIPPED.name))
        }
        syncVenueQueueAndBookings(booking.venueId)
        prefs.bumpControlRevision()
        return updated
    }
}
