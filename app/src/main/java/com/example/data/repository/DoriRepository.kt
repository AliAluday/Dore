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
import com.example.data.model.UserProfileSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class DoriRepository(
    private val dao: DoriDao,
    private val prefs: UserPreferencesDataStore
) {
    val venues: Flow<List<VenueEntity>> = dao.observeAllVenues()
    val reviews: Flow<List<ReviewEntity>> = dao.observeAllReviews()
    val bookings: Flow<List<BookingEntity>> = dao.observeAllBookings()
    val queueItems: Flow<List<MerchantQueueItemEntity>> = dao.observeAllQueueItems()
    val userPreferences: Flow<UserProfileSettings> = prefs.userPreferences

    suspend fun ensureSeeded() {
        val currentVenues = dao.observeAllVenues().first()
        val validDistrictNames = NinevehDistrict.selectableDistricts.map { it.displayName }.toSet()
        val needsReseed = currentVenues.isEmpty() ||
            currentVenues.any { it.city !in validDistrictNames } ||
            currentVenues.any { it.category == ServiceCategory.CLINICS.displayName && it.specialty.isBlank() }
        if (needsReseed) {
            val venues = SeedData.initialVenues()
            dao.insertVenues(venues)
            dao.insertReviews(SeedData.initialReviews())
            dao.insertQueueItems(SeedData.initialQueueItems(venues))
        }
    }

    suspend fun setSelectedDistrict(district: NinevehDistrict) = prefs.setSelectedDistrict(district)
    suspend fun setDarkMode(enabled: Boolean) = prefs.setDarkMode(enabled)
    suspend fun setMuteSound(muted: Boolean) = prefs.setMuteSound(muted)
    suspend fun setMuteVibration(muted: Boolean) = prefs.setMuteVibration(muted)

    suspend fun updateProfile(name: String, phone: String, district: NinevehDistrict) =
        prefs.updateProfile(name, phone, district)

    suspend fun toggleFavorite(venueId: Int, currentFavorite: Boolean) =
        dao.setFavorite(venueId, !currentFavorite)

    suspend fun bookQueueTicket(
        venueId: Int,
        customerName: String,
        customerPhone: String,
        selectedOption: String,
        notes: String = "",
        customPriceDinar: Int? = null
    ): Result<BookingEntity> {
        val venue = dao.getVenueById(venueId)
            ?: return Result.failure(IllegalStateException("المكان غير موجود"))
        if (!venue.isOpen || !venue.isAcceptingBookings) {
            return Result.failure(IllegalStateException("هذا المكان متوقف عن استقبال الحجوزات حالياً"))
        }

        val queueItems = dao.getQueueItemsForVenueOnce(venueId)
        val activeWaiting = queueItems.count { it.status == QueueItemStatus.WAITING.name }
        val activeServing = queueItems.count { it.status == QueueItemStatus.SERVING.name }
        val ticketNum = venue.nextTicketNumber
        val ticketCode = "NIN-${venue.id}-$ticketNum"
        val peopleAhead = activeWaiting + activeServing
        val stage = when {
            peopleAhead == 0 -> TicketStage.YOUR_TURN
            peopleAhead <= 1 -> TicketStage.APPROACHING
            else -> TicketStage.MONITORED
        }
        val etaMinutes = (peopleAhead * venue.avgMinutesPerPerson).coerceAtLeast(if (peopleAhead == 0) 0 else 3)

        val booking = BookingEntity(
            ticketCode = ticketCode,
            queueNumber = ticketNum,
            venueId = venue.id,
            venueName = venue.name,
            venueCity = "${venue.city} • ${venue.area}",
            venueCategory = venue.category,
            venuePhone = venue.phone,
            customerName = customerName.ifBlank { "ضيف نينوى" },
            customerPhone = customerPhone.ifBlank { "07700000000" },
            selectedOption = selectedOption.ifBlank { venue.customOptions.firstOrNull() ?: "خدمة قياسية" },
            notes = notes,
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
        dao.updateVenue(
            venue.copy(
                nextTicketNumber = ticketNum + 1,
                waitingCount = updatedWaitingCount
            )
        )

        return Result.success(savedBooking)
    }

    suspend fun bookFastestTurnInDistrict(district: NinevehDistrict): Result<BookingEntity> {
        val currentUser = prefs.userPreferences.first()
        val allVenues = dao.observeAllVenues().first()
        val candidates = if (district == NinevehDistrict.ALL_NINEVEH) {
            allVenues
        } else {
            allVenues.filter { it.city == district.displayName }
        }
        val fastest = candidates
            .filter { it.isOpen && it.isAcceptingBookings && !it.isPaused }
            .minByOrNull { it.estimatedWaitMinutes }
            ?: return Result.failure(IllegalStateException("لا توجد أماكن متاحة للحجز السريع في ${district.displayName} حالياً"))

        return bookQueueTicket(
            venueId = fastest.id,
            customerName = currentUser.userName,
            customerPhone = currentUser.userPhone,
            selectedOption = fastest.customOptions.firstOrNull() ?: "دور سريع"
        )
    }

    suspend fun completeBookingWithOptionalReview(
        bookingId: Long,
        rating: Int?,
        comment: String?
    ): Int {
        val booking = dao.getBookingById(bookingId) ?: return 0
        if (booking.status == BookingStatus.COMPLETED.name) return 0

        dao.updateBooking(
            booking.copy(
                status = BookingStatus.COMPLETED.name,
                stage = TicketStage.YOUR_TURN.name,
                peopleAhead = 0,
                estimatedMinutes = 0,
                completedAt = System.currentTimeMillis(),
                userRating = rating,
                userReview = comment?.takeIf { it.isNotBlank() }
            )
        )

        val qItems = dao.getQueueItemsForVenueOnce(booking.venueId)
        qItems.find { it.bookingId == bookingId }?.let { item ->
            dao.updateQueueItem(item.copy(status = QueueItemStatus.DONE.name))
        }

        val venue = dao.getVenueById(booking.venueId)
        if (venue != null) {
            if (rating != null && rating in 1..5) {
                dao.insertReview(
                    ReviewEntity(
                        venueId = venue.id,
                        authorName = booking.customerName,
                        rating = rating,
                        comment = comment?.takeIf { it.isNotBlank() } ?: "خدمة ممتازة وتجربة منظمة عبر دوري"
                    )
                )
                val newCount = venue.reviewCount + 1
                val newRating = ((venue.rating * venue.reviewCount) + rating) / newCount
                val rounded = kotlin.math.round(newRating * 10.0) / 10.0
                dao.updateVenue(
                    venue.copy(
                        rating = rounded,
                        reviewCount = newCount,
                        servedTodayCount = venue.servedTodayCount + 1
                    )
                )
            } else {
                dao.updateVenue(venue.copy(servedTodayCount = venue.servedTodayCount + 1))
            }
            syncVenueQueueAndBookings(venue.id)
        }

        prefs.addLoyaltyPoints(10)
        return 10
    }

    suspend fun submitReviewForHistoryBooking(bookingId: Long, rating: Int, comment: String) {
        val booking = dao.getBookingById(bookingId) ?: return
        val cleanRating = rating.coerceIn(1, 5)
        val cleanComment = comment.trim().ifBlank { "تجربة رائعة" }
        val alreadyRated = booking.userRating != null
        dao.updateBooking(
            booking.copy(
                userRating = cleanRating,
                userReview = cleanComment
            )
        )
        if (!alreadyRated) {
            dao.insertReview(
                ReviewEntity(
                    venueId = booking.venueId,
                    authorName = booking.customerName,
                    rating = cleanRating,
                    comment = cleanComment
                )
            )
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
        dao.updateBooking(
            booking.copy(
                status = BookingStatus.CANCELLED.name,
                completedAt = System.currentTimeMillis()
            )
        )
        val qItems = dao.getQueueItemsForVenueOnce(booking.venueId)
        qItems.find { it.bookingId == bookingId }?.let { item ->
            dao.updateQueueItem(item.copy(status = QueueItemStatus.SKIPPED.name))
        }
        syncVenueQueueAndBookings(booking.venueId)
    }

    suspend fun togglePauseQueue(venueId: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        dao.updateVenue(venue.copy(isPaused = !venue.isPaused))
    }

    suspend fun toggleAcceptingBookings(venueId: Int) {
        val venue = dao.getVenueById(venueId) ?: return
        dao.updateVenue(venue.copy(isAcceptingBookings = !venue.isAcceptingBookings))
    }

    suspend fun addWalkInCustomer(venueId: Int, name: String, option: String) {
        val venue = dao.getVenueById(venueId) ?: return
        val num = venue.nextTicketNumber
        dao.insertQueueItem(
            MerchantQueueItemEntity(
                venueId = venueId,
                ticketNumber = num,
                ticketCode = "W-$venueId-$num",
                customerName = name.ifBlank { "عميل حضوري #$num" },
                customerPhone = "حضوري",
                selectedOption = option.ifBlank { venue.customOptions.firstOrNull() ?: "خدمة عامة" },
                status = QueueItemStatus.WAITING.name
            )
        )
        dao.updateVenue(venue.copy(nextTicketNumber = num + 1))
        syncVenueQueueAndBookings(venueId)
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
                    prefs.addLoyaltyPoints(10)
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
        return syncVenueQueueAndBookings(venueId)
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
                        dao.updateBooking(b.copy(status = BookingStatus.CANCELLED.name, completedAt = System.currentTimeMillis()))
                    }
                }
            }
        }
        val nextWaiting = items.firstOrNull { it.status == QueueItemStatus.WAITING.name }
        if (nextWaiting != null) {
            dao.updateQueueItem(nextWaiting.copy(status = QueueItemStatus.SERVING.name))
            dao.updateVenue(venue.copy(currentServingNumber = nextWaiting.ticketNumber))
        }
        return syncVenueQueueAndBookings(venueId)
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
        return syncVenueQueueAndBookings(venueId)
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
        return syncVenueQueueAndBookings(venueId)
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

        dao.updateVenue(venue.copy(waitingCount = waitingList.size))

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
                            message = "حان دورك الآن في ${b.venueName}! رقم التذكرة ${b.ticketCode}"
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
            val eta = (ahead * venue.avgMinutesPerPerson).coerceAtLeast(if (ahead == 0) 0 else 3)
            val previousStage = b.stage
            dao.updateBooking(
                b.copy(
                    stage = newStage.name,
                    peopleAhead = ahead,
                    estimatedMinutes = eta
                )
            )
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
}
