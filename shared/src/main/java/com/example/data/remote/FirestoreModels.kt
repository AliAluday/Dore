package com.example.data.remote

import androidx.annotation.Keep
import com.example.data.local.BookingEntity
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.local.ReviewEntity
import com.example.data.local.VenueEntity
import com.example.data.model.BookingStatus
import com.example.data.model.NinevehDistrict
import com.example.data.model.QueueItemStatus
import com.example.data.model.TicketStage
import com.example.data.model.UserAppControlConfig
import com.example.data.model.UserProfileSettings
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudConfig(
    val userId: String = "",
    val selectedDistrict: String = NinevehDistrict.ALL_NINEVEH.displayName,
    val isDarkMode: Boolean = true,
    val userName: String = "",
    val userPhone: String = "",
    val userAge: String = "",
    val optionalNote: String = "",
    val loyaltyPoints: Int = 60,
    val muteSound: Boolean = false,
    val muteVibration: Boolean = false,
    val allowNewBookings: Boolean = true,
    val enableFastestTurn: Boolean = true,
    val enableQrCode: Boolean = true,
    val enableWhatsAppShare: Boolean = true,
    val enableReviews: Boolean = true,
    val enableLoyalty: Boolean = true,
    val enableOptionalFields: Boolean = true,
    val enableLiveAlerts: Boolean = true,
    val pointsPerBooking: Int = 10,
    val disabledCategories: List<String> = emptyList(),
    val broadcastMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toUserProfileSettings(): UserProfileSettings = UserProfileSettings(
        selectedDistrict = NinevehDistrict.fromName(selectedDistrict),
        isDarkMode = isDarkMode,
        userName = userName,
        userPhone = userPhone,
        userAge = userAge,
        optionalNote = optionalNote,
        loyaltyPoints = loyaltyPoints.coerceIn(0, 100000),
        muteSound = muteSound,
        muteVibration = muteVibration,
        controlConfig = UserAppControlConfig(
            allowNewBookings = allowNewBookings,
            enableFastestTurn = enableFastestTurn,
            enableQrCode = enableQrCode,
            enableWhatsAppShare = enableWhatsAppShare,
            enableReviews = enableReviews,
            enableLoyalty = enableLoyalty,
            enableOptionalFields = enableOptionalFields,
            enableLiveAlerts = enableLiveAlerts,
            pointsPerBooking = pointsPerBooking.coerceIn(0, 100),
            disabledCategories = disabledCategories.toSet(),
            broadcastMessage = broadcastMessage
        )
    )

    companion object {
        fun fromDocument(doc: DocumentSnapshot): CloudConfig? {
            if (!doc.exists()) return null
            val d = doc.data ?: return null
            val categories = (d["disabledCategories"] as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList()
            return CloudConfig(
                userId = d["userId"] as? String ?: "",
                selectedDistrict = d["selectedDistrict"] as? String ?: NinevehDistrict.ALL_NINEVEH.displayName,
                isDarkMode = d["isDarkMode"] as? Boolean ?: true,
                userName = d["userName"] as? String ?: "",
                userPhone = d["userPhone"] as? String ?: "",
                userAge = d["userAge"] as? String ?: "",
                optionalNote = d["optionalNote"] as? String ?: "",
                loyaltyPoints = (d["loyaltyPoints"] as? Number)?.toInt() ?: 60,
                muteSound = d["muteSound"] as? Boolean ?: false,
                muteVibration = d["muteVibration"] as? Boolean ?: false,
                allowNewBookings = d["allowNewBookings"] as? Boolean ?: true,
                enableFastestTurn = d["enableFastestTurn"] as? Boolean ?: true,
                enableQrCode = d["enableQrCode"] as? Boolean ?: true,
                enableWhatsAppShare = d["enableWhatsAppShare"] as? Boolean ?: true,
                enableReviews = d["enableReviews"] as? Boolean ?: true,
                enableLoyalty = d["enableLoyalty"] as? Boolean ?: true,
                enableOptionalFields = d["enableOptionalFields"] as? Boolean ?: true,
                enableLiveAlerts = d["enableLiveAlerts"] as? Boolean ?: true,
                pointsPerBooking = (d["pointsPerBooking"] as? Number)?.toInt() ?: 10,
                disabledCategories = categories,
                broadcastMessage = d["broadcastMessage"] as? String ?: "",
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }

        fun toCreateMap(userId: String, settings: UserProfileSettings): Map<String, Any> =
            toUpdateMap(settings) + mapOf(
                "userId" to userId,
                "createdAt" to FieldValue.serverTimestamp()
            )

        fun toUpdateMap(settings: UserProfileSettings): Map<String, Any> {
            val ctrl = settings.controlConfig
            return mapOf(
                "selectedDistrict" to settings.selectedDistrict.displayName.take(80),
                "isDarkMode" to settings.isDarkMode,
                "userName" to settings.userName.take(100),
                "userPhone" to settings.userPhone.take(30),
                "userAge" to settings.userAge.take(20),
                "optionalNote" to settings.optionalNote.take(300),
                "loyaltyPoints" to settings.loyaltyPoints.coerceIn(0, 100000),
                "muteSound" to settings.muteSound,
                "muteVibration" to settings.muteVibration,
                "allowNewBookings" to ctrl.allowNewBookings,
                "enableFastestTurn" to ctrl.enableFastestTurn,
                "enableQrCode" to ctrl.enableQrCode,
                "enableWhatsAppShare" to ctrl.enableWhatsAppShare,
                "enableReviews" to ctrl.enableReviews,
                "enableLoyalty" to ctrl.enableLoyalty,
                "enableOptionalFields" to ctrl.enableOptionalFields,
                "enableLiveAlerts" to ctrl.enableLiveAlerts,
                "pointsPerBooking" to ctrl.pointsPerBooking.coerceIn(0, 100),
                "disabledCategories" to ctrl.disabledCategories.take(10),
                "broadcastMessage" to ctrl.broadcastMessage.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class CloudVenue(
    val userId: String = "",
    val venueId: Int = 0,
    val name: String = "",
    val city: String = "",
    val category: String = "",
    val specialty: String = "",
    val area: String = "",
    val priceDinar: Int = 0,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val workingHours: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val description: String = "",
    val customOptionsCsv: String = "",
    val open: Boolean = true,
    val paused: Boolean = false,
    val acceptingBookings: Boolean = true,
    val currentServingNumber: Int = 0,
    val nextTicketNumber: Int = 1,
    val servedTodayCount: Int = 0,
    val avgMinutesPerPerson: Int = 8,
    val favorite: Boolean = false,
    val waitingCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): VenueEntity = VenueEntity(
        id = venueId,
        name = name,
        city = city,
        category = category,
        specialty = specialty,
        area = area,
        priceDinar = priceDinar,
        rating = rating,
        reviewCount = reviewCount,
        workingHours = workingHours,
        phone = phone,
        whatsapp = whatsapp,
        latitude = latitude,
        longitude = longitude,
        description = description,
        customOptionsCsv = customOptionsCsv,
        isOpen = open,
        isPaused = paused,
        isAcceptingBookings = acceptingBookings,
        currentServingNumber = currentServingNumber,
        nextTicketNumber = nextTicketNumber.coerceAtLeast(1),
        servedTodayCount = servedTodayCount,
        avgMinutesPerPerson = avgMinutesPerPerson.coerceIn(1, 180),
        isFavorite = favorite,
        waitingCount = waitingCount.coerceAtLeast(0)
    )

    companion object {
        fun fromDocument(doc: DocumentSnapshot): VenueEntity? {
            val d = doc.data ?: return null
            val vId = (d["venueId"] as? Number)?.toInt() ?: return null
            if (vId <= 0) return null
            return VenueEntity(
                id = vId,
                name = d["name"] as? String ?: "",
                city = d["city"] as? String ?: "",
                category = d["category"] as? String ?: "",
                specialty = d["specialty"] as? String ?: "",
                area = d["area"] as? String ?: "",
                priceDinar = (d["priceDinar"] as? Number)?.toInt() ?: 0,
                rating = (d["rating"] as? Number)?.toDouble() ?: 0.0,
                reviewCount = (d["reviewCount"] as? Number)?.toInt() ?: 0,
                workingHours = d["workingHours"] as? String ?: "",
                phone = d["phone"] as? String ?: "",
                whatsapp = d["whatsapp"] as? String ?: "",
                latitude = (d["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (d["longitude"] as? Number)?.toDouble() ?: 0.0,
                description = d["description"] as? String ?: "",
                customOptionsCsv = d["customOptionsCsv"] as? String ?: "",
                isOpen = d["open"] as? Boolean ?: true,
                isPaused = d["paused"] as? Boolean ?: false,
                isAcceptingBookings = d["acceptingBookings"] as? Boolean ?: true,
                currentServingNumber = (d["currentServingNumber"] as? Number)?.toInt() ?: 0,
                nextTicketNumber = ((d["nextTicketNumber"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
                servedTodayCount = (d["servedTodayCount"] as? Number)?.toInt() ?: 0,
                avgMinutesPerPerson = ((d["avgMinutesPerPerson"] as? Number)?.toInt() ?: 8).coerceIn(1, 180),
                isFavorite = d["favorite"] as? Boolean ?: false,
                waitingCount = ((d["waitingCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
            )
        }

        fun toCreateMap(userId: String, venue: VenueEntity): Map<String, Any> =
            toUpdateMap(venue) + mapOf(
                "userId" to userId,
                "venueId" to venue.id,
                "createdAt" to FieldValue.serverTimestamp()
            )

        fun toUpdateMap(venue: VenueEntity): Map<String, Any> = mapOf(
            "name" to venue.name.take(120),
            "city" to venue.city.take(80),
            "category" to venue.category.take(40),
            "specialty" to venue.specialty.take(80),
            "area" to venue.area.take(80),
            "priceDinar" to venue.priceDinar.coerceIn(0, 500000),
            "rating" to venue.rating.coerceIn(0.0, 5.0),
            "reviewCount" to venue.reviewCount.coerceIn(0, 100000),
            "workingHours" to venue.workingHours.take(80),
            "phone" to venue.phone.take(30),
            "whatsapp" to venue.whatsapp.take(30),
            "latitude" to venue.latitude,
            "longitude" to venue.longitude,
            "description" to venue.description.take(500),
            "customOptionsCsv" to venue.customOptionsCsv.take(500),
            "open" to venue.isOpen,
            "paused" to venue.isPaused,
            "acceptingBookings" to venue.isAcceptingBookings,
            "currentServingNumber" to venue.currentServingNumber.coerceIn(0, 10000),
            "nextTicketNumber" to venue.nextTicketNumber.coerceIn(1, 10001),
            "servedTodayCount" to venue.servedTodayCount.coerceIn(0, 10000),
            "avgMinutesPerPerson" to venue.avgMinutesPerPerson.coerceIn(1, 180),
            "favorite" to venue.isFavorite,
            "waitingCount" to venue.waitingCount.coerceIn(0, 10000),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

object CloudBookingMapper {
    fun fromDocument(doc: DocumentSnapshot): BookingEntity? {
        val d = doc.data ?: return null
        val bId = (d["bookingId"] as? Number)?.toLong() ?: return null
        if (bId <= 0L) return null
        val rawStatus = d["status"] as? String ?: BookingStatus.ACTIVE.name
        val safeStatus = BookingStatus.entries.firstOrNull { it.name == rawStatus }?.name
            ?: BookingStatus.ACTIVE.name
        val rawStage = d["stage"] as? String ?: TicketStage.MONITORED.name
        val safeStage = TicketStage.entries.firstOrNull { it.name == rawStage }?.name
            ?: TicketStage.MONITORED.name
        return BookingEntity(
            id = bId,
            ticketCode = d["ticketCode"] as? String ?: "",
            queueNumber = ((d["queueNumber"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
            venueId = ((d["venueId"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
            venueName = d["venueName"] as? String ?: "",
            venueCity = d["venueCity"] as? String ?: "",
            venueCategory = d["venueCategory"] as? String ?: "",
            venuePhone = d["venuePhone"] as? String ?: "",
            customerName = d["customerName"] as? String ?: "",
            customerPhone = d["customerPhone"] as? String ?: "",
            selectedOption = d["selectedOption"] as? String ?: "",
            notes = d["notes"] as? String ?: "",
            optionalInfo = d["optionalInfo"] as? String ?: "",
            priceDinar = (d["priceDinar"] as? Number)?.toInt() ?: 0,
            status = safeStatus,
            stage = safeStage,
            peopleAhead = ((d["peopleAhead"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
            estimatedMinutes = ((d["estimatedMinutes"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
            createdAt = (d["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            completedAt = (d["completedAtMillis"] as? Number)?.toLong(),
            userRating = (d["userRating"] as? Number)?.toInt(),
            userReview = d["userReview"] as? String
        )
    }

    fun toCreateMap(userId: String, booking: BookingEntity): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to userId,
            "bookingId" to booking.id,
            "ticketCode" to booking.ticketCode.take(40),
            "queueNumber" to booking.queueNumber.coerceIn(1, 10000),
            "venueId" to booking.venueId.coerceAtLeast(1),
            "venueName" to booking.venueName.take(120),
            "venueCity" to booking.venueCity.take(120),
            "venueCategory" to booking.venueCategory.take(40),
            "venuePhone" to booking.venuePhone.take(30),
            "customerName" to booking.customerName.take(120),
            "customerPhone" to booking.customerPhone.take(30),
            "selectedOption" to booking.selectedOption.take(120),
            "notes" to booking.notes.take(300),
            "optionalInfo" to booking.optionalInfo.take(300),
            "priceDinar" to booking.priceDinar.coerceIn(0, 500000),
            "status" to booking.status,
            "stage" to booking.stage,
            "peopleAhead" to booking.peopleAhead.coerceIn(0, 10000),
            "estimatedMinutes" to booking.estimatedMinutes.coerceIn(0, 10000),
            "createdAtMillis" to booking.createdAt.coerceAtLeast(0L),
            "completedAtMillis" to booking.completedAt?.coerceAtLeast(0L),
            "userRating" to booking.userRating?.coerceIn(1, 5),
            "userReview" to booking.userReview?.take(400),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toActiveUpdateMap(booking: BookingEntity): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "status" to booking.status,
            "stage" to booking.stage,
            "peopleAhead" to booking.peopleAhead.coerceIn(0, 10000),
            "estimatedMinutes" to booking.estimatedMinutes.coerceIn(0, 10000),
            "completedAtMillis" to booking.completedAt?.coerceAtLeast(0L),
            "userRating" to booking.userRating?.coerceIn(1, 5),
            "userReview" to booking.userReview?.take(400),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toHistoryReviewUpdateMap(rating: Int, comment: String): Map<String, Any> = mapOf(
        "userRating" to rating.coerceIn(1, 5),
        "userReview" to comment.take(400),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

object CloudQueueItemMapper {
    fun fromDocument(doc: DocumentSnapshot): MerchantQueueItemEntity? {
        val d = doc.data ?: return null
        val qId = (d["queueItemId"] as? Number)?.toLong() ?: return null
        if (qId <= 0L) return null
        val rawStatus = d["status"] as? String ?: QueueItemStatus.WAITING.name
        val safeStatus = QueueItemStatus.entries.firstOrNull { it.name == rawStatus }?.name
            ?: QueueItemStatus.WAITING.name
        return MerchantQueueItemEntity(
            id = qId,
            venueId = ((d["venueId"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
            ticketNumber = ((d["ticketNumber"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
            ticketCode = d["ticketCode"] as? String ?: "",
            customerName = d["customerName"] as? String ?: "",
            customerPhone = d["customerPhone"] as? String ?: "",
            selectedOption = d["selectedOption"] as? String ?: "",
            status = safeStatus,
            bookingId = (d["bookingId"] as? Number)?.toLong(),
            createdAt = (d["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    fun toCreateMap(userId: String, item: MerchantQueueItemEntity): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to userId,
            "queueItemId" to item.id,
            "venueId" to item.venueId.coerceAtLeast(1),
            "ticketNumber" to item.ticketNumber.coerceIn(1, 10000),
            "ticketCode" to item.ticketCode.take(40),
            "customerName" to item.customerName.take(120),
            "customerPhone" to item.customerPhone.take(30),
            "selectedOption" to item.selectedOption.take(120),
            "status" to item.status,
            "bookingId" to item.bookingId?.takeIf { it > 0L },
            "createdAtMillis" to item.createdAt.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(item: MerchantQueueItemEntity): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "status" to item.status,
            "bookingId" to item.bookingId?.takeIf { it > 0L },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

object CloudReviewMapper {
    fun fromDocument(doc: DocumentSnapshot): ReviewEntity? {
        val d = doc.data ?: return null
        val rId = (d["reviewId"] as? Number)?.toLong() ?: return null
        if (rId <= 0L) return null
        return ReviewEntity(
            id = rId,
            venueId = ((d["venueId"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
            authorName = d["authorName"] as? String ?: "",
            rating = ((d["rating"] as? Number)?.toInt() ?: 5).coerceIn(1, 5),
            comment = d["comment"] as? String ?: "",
            createdAt = (d["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    fun toCreateMap(userId: String, review: ReviewEntity): Map<String, Any> = mapOf(
        "userId" to userId,
        "reviewId" to review.id,
        "venueId" to review.venueId.coerceAtLeast(1),
        "authorName" to review.authorName.take(120),
        "rating" to review.rating.coerceIn(1, 5),
        "comment" to review.comment.take(400),
        "createdAtMillis" to review.createdAt.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
