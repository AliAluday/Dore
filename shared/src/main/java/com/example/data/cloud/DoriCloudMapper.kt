package com.example.data.cloud

import com.example.data.local.AdminAccountEntity
import com.example.data.local.BookingEntity
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.local.ReviewEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.local.SupportMessageEntity
import com.example.data.local.SupportTicketEntity
import com.example.data.local.UserPreferencesDataStore
import com.example.data.local.VenueEntity
import com.example.data.model.NinevehDistrict
import com.example.data.model.UserAppControlConfig
import com.example.data.model.UserProfileSettings
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

object DoriCloudMapper {

    fun safeDocKey(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "item" }.take(120)

    fun venueDocId(venueId: Int): String = "venue_$venueId"

    fun bookingDocId(ticketCode: String): String = "booking_${safeDocKey(ticketCode)}"

    fun queueItemDocId(ticketCode: String): String = "queue_${safeDocKey(ticketCode)}"

    fun reviewDocId(reviewId: Long, venueId: Int, createdAtMillis: Long): String =
        if (reviewId > 0L) "review_$reviewId" else "review_${venueId}_$createdAtMillis"

    fun adminDocId(adminId: Long): String = "admin_$adminId"

    fun providerDocId(providerId: Long): String = "provider_$providerId"

    // ==================== Config ====================

    fun configUpdateMap(
        userId: String,
        settings: UserProfileSettings,
        meta: UserPreferencesDataStore.ControlSyncMetadata
    ): Map<String, Any> = mapOf(
        "userId" to userId,
        "selectedDistrict" to settings.selectedDistrict.displayName,
        "isDarkMode" to settings.isDarkMode,
        "userName" to settings.userName.take(100),
        "userPhone" to settings.userPhone.take(30),
        "userAge" to settings.userAge.take(20),
        "optionalNote" to settings.optionalNote.take(300),
        "loyaltyPoints" to settings.loyaltyPoints.coerceIn(0, 100000),
        "muteSound" to settings.muteSound,
        "muteVibration" to settings.muteVibration,
        "allowNewBookings" to settings.controlConfig.allowNewBookings,
        "enableFastestTurn" to settings.controlConfig.enableFastestTurn,
        "enableQrCode" to settings.controlConfig.enableQrCode,
        "enableWhatsAppShare" to settings.controlConfig.enableWhatsAppShare,
        "enableReviews" to settings.controlConfig.enableReviews,
        "enableLoyalty" to settings.controlConfig.enableLoyalty,
        "enableOptionalFields" to settings.controlConfig.enableOptionalFields,
        "enableLiveAlerts" to settings.controlConfig.enableLiveAlerts,
        "pointsPerBooking" to settings.controlConfig.pointsPerBooking.coerceIn(0, 100),
        "disabledCategories" to settings.controlConfig.disabledCategories.toList().take(10),
        "broadcastMessage" to settings.controlConfig.broadcastMessage.take(300),
        "controlRevision" to meta.controlRevision.coerceAtLeast(0L),
        "queueResetRevision" to meta.queueResetRevision.coerceAtLeast(0L),
        "loyaltyRevision" to meta.loyaltyRevision.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun configCreateMap(
        userId: String,
        settings: UserProfileSettings,
        meta: UserPreferencesDataStore.ControlSyncMetadata
    ): Map<String, Any> =
        configUpdateMap(userId, settings, meta) + mapOf("createdAt" to FieldValue.serverTimestamp())

    fun configMutablePatchMap(
        settings: UserProfileSettings,
        meta: UserPreferencesDataStore.ControlSyncMetadata
    ): Map<String, Any> = mapOf(
        "selectedDistrict" to settings.selectedDistrict.displayName,
        "isDarkMode" to settings.isDarkMode,
        "userName" to settings.userName.take(100),
        "userPhone" to settings.userPhone.take(30),
        "userAge" to settings.userAge.take(20),
        "optionalNote" to settings.optionalNote.take(300),
        "loyaltyPoints" to settings.loyaltyPoints.coerceIn(0, 100000),
        "muteSound" to settings.muteSound,
        "muteVibration" to settings.muteVibration,
        "allowNewBookings" to settings.controlConfig.allowNewBookings,
        "enableFastestTurn" to settings.controlConfig.enableFastestTurn,
        "enableQrCode" to settings.controlConfig.enableQrCode,
        "enableWhatsAppShare" to settings.controlConfig.enableWhatsAppShare,
        "enableReviews" to settings.controlConfig.enableReviews,
        "enableLoyalty" to settings.controlConfig.enableLoyalty,
        "enableOptionalFields" to settings.controlConfig.enableOptionalFields,
        "enableLiveAlerts" to settings.controlConfig.enableLiveAlerts,
        "pointsPerBooking" to settings.controlConfig.pointsPerBooking.coerceIn(0, 100),
        "disabledCategories" to settings.controlConfig.disabledCategories.toList().take(10),
        "broadcastMessage" to settings.controlConfig.broadcastMessage.take(300),
        "controlRevision" to meta.controlRevision.coerceAtLeast(0L),
        "queueResetRevision" to meta.queueResetRevision.coerceAtLeast(0L),
        "loyaltyRevision" to meta.loyaltyRevision.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    data class DecodedConfig(
        val settings: UserProfileSettings,
        val controlRevision: Long,
        val queueResetRevision: Long,
        val loyaltyRevision: Long
    )

    fun toConfig(doc: DocumentSnapshot): DecodedConfig? {
        if (!doc.exists()) return null
        val disabledList = (doc.get("disabledCategories") as? List<*>)
            ?.mapNotNull { it as? String }
            ?.toSet()
            ?: emptySet()
        val controlConfig = UserAppControlConfig(
            allowNewBookings = doc.getBoolean("allowNewBookings") ?: true,
            enableFastestTurn = doc.getBoolean("enableFastestTurn") ?: true,
            enableQrCode = doc.getBoolean("enableQrCode") ?: true,
            enableWhatsAppShare = doc.getBoolean("enableWhatsAppShare") ?: true,
            enableReviews = doc.getBoolean("enableReviews") ?: true,
            enableLoyalty = doc.getBoolean("enableLoyalty") ?: true,
            enableOptionalFields = doc.getBoolean("enableOptionalFields") ?: true,
            enableLiveAlerts = doc.getBoolean("enableLiveAlerts") ?: true,
            pointsPerBooking = (doc.getLong("pointsPerBooking") ?: 10L).toInt().coerceIn(0, 100),
            disabledCategories = disabledList,
            broadcastMessage = doc.getString("broadcastMessage") ?: ""
        )
        val settings = UserProfileSettings(
            selectedDistrict = NinevehDistrict.fromName(doc.getString("selectedDistrict") ?: ""),
            isDarkMode = doc.getBoolean("isDarkMode") ?: true,
            userName = doc.getString("userName") ?: "",
            userPhone = doc.getString("userPhone") ?: "",
            userAge = doc.getString("userAge") ?: "",
            optionalNote = doc.getString("optionalNote") ?: "",
            loyaltyPoints = (doc.getLong("loyaltyPoints") ?: 60L).toInt().coerceAtLeast(0),
            muteSound = doc.getBoolean("muteSound") ?: false,
            muteVibration = doc.getBoolean("muteVibration") ?: false,
            controlConfig = controlConfig
        )
        return DecodedConfig(
            settings = settings,
            controlRevision = doc.getLong("controlRevision") ?: 0L,
            queueResetRevision = doc.getLong("queueResetRevision") ?: 0L,
            loyaltyRevision = doc.getLong("loyaltyRevision") ?: 0L
        )
    }

    // ==================== Venues ====================

    fun venueMutableMap(venue: VenueEntity): Map<String, Any> = mapOf(
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

    fun venueCreateMap(userId: String, venue: VenueEntity): Map<String, Any> =
        venueMutableMap(venue) + mapOf(
            "userId" to userId,
            "venueId" to venue.id,
            "createdAt" to FieldValue.serverTimestamp()
        )

    fun toVenueEntity(doc: DocumentSnapshot): VenueEntity? {
        if (!doc.exists()) return null
        val id = (doc.getLong("venueId") ?: return null).toInt()
        return VenueEntity(
            id = id,
            name = doc.getString("name") ?: return null,
            city = doc.getString("city") ?: return null,
            category = doc.getString("category") ?: return null,
            specialty = doc.getString("specialty") ?: "",
            area = doc.getString("area") ?: "",
            priceDinar = (doc.getLong("priceDinar") ?: 0L).toInt(),
            rating = doc.getDouble("rating") ?: 4.5,
            reviewCount = (doc.getLong("reviewCount") ?: 0L).toInt(),
            workingHours = doc.getString("workingHours") ?: "",
            phone = doc.getString("phone") ?: "",
            whatsapp = doc.getString("whatsapp") ?: "",
            latitude = doc.getDouble("latitude") ?: 36.3489,
            longitude = doc.getDouble("longitude") ?: 43.1577,
            description = doc.getString("description") ?: "",
            customOptionsCsv = doc.getString("customOptionsCsv") ?: "",
            isOpen = doc.getBoolean("open") ?: true,
            isPaused = doc.getBoolean("paused") ?: false,
            isAcceptingBookings = doc.getBoolean("acceptingBookings") ?: true,
            currentServingNumber = (doc.getLong("currentServingNumber") ?: 0L).toInt(),
            nextTicketNumber = (doc.getLong("nextTicketNumber") ?: 1L).toInt(),
            servedTodayCount = (doc.getLong("servedTodayCount") ?: 0L).toInt(),
            avgMinutesPerPerson = (doc.getLong("avgMinutesPerPerson") ?: 8L).toInt(),
            isFavorite = doc.getBoolean("favorite") ?: false,
            waitingCount = (doc.getLong("waitingCount") ?: 0L).toInt()
        )
    }

    // ==================== Bookings ====================

    fun bookingCreateMap(userId: String, booking: BookingEntity): Map<String, Any?> {
        val stableId = if (booking.id > 0L) booking.id else booking.createdAt.coerceAtLeast(1L)
        return hashMapOf(
            "userId" to userId,
            "bookingId" to stableId,
            "ticketCode" to booking.ticketCode.take(40),
            "queueNumber" to booking.queueNumber.coerceIn(1, 10000),
            "venueId" to booking.venueId,
            "venueName" to booking.venueName.take(120),
            "venueCity" to booking.venueCity.take(120),
            "venueCategory" to booking.venueCategory.take(40),
            "venuePhone" to booking.venuePhone.take(30),
            "customerName" to booking.customerName.take(120),
            "customerPhone" to booking.customerPhone.ifBlank { "07700000000" }.take(30),
            "selectedOption" to booking.selectedOption.take(120),
            "notes" to booking.notes.take(300),
            "optionalInfo" to booking.optionalInfo.take(300),
            "priceDinar" to booking.priceDinar.coerceIn(0, 500000),
            "status" to booking.status,
            "stage" to booking.stage,
            "approvalStatus" to booking.approvalStatus,
            "handledByProvider" to booking.handledByProvider.take(120),
            "approvalNote" to booking.approvalNote.take(300),
            "peopleAhead" to booking.peopleAhead.coerceIn(0, 10000),
            "estimatedMinutes" to booking.estimatedMinutes.coerceIn(0, 10000),
            "createdAtMillis" to booking.createdAt.coerceAtLeast(0L),
            "completedAtMillis" to booking.completedAt,
            "userRating" to booking.userRating?.coerceIn(1, 5),
            "userReview" to booking.userReview?.take(400),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun bookingUpdateActiveMap(booking: BookingEntity): Map<String, Any?> = hashMapOf(
        "status" to booking.status,
        "stage" to booking.stage,
        "approvalStatus" to booking.approvalStatus,
        "handledByProvider" to booking.handledByProvider.take(120),
        "approvalNote" to booking.approvalNote.take(300),
        "peopleAhead" to booking.peopleAhead.coerceIn(0, 10000),
        "estimatedMinutes" to booking.estimatedMinutes.coerceIn(0, 10000),
        "completedAtMillis" to booking.completedAt,
        "userRating" to booking.userRating?.coerceIn(1, 5),
        "userReview" to booking.userReview?.take(400),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun bookingUpdateFinishedReviewMap(rating: Int?, review: String?): Map<String, Any?> = hashMapOf(
        "userRating" to rating?.coerceIn(1, 5),
        "userReview" to review?.take(400),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toBookingEntity(doc: DocumentSnapshot): BookingEntity? {
        if (!doc.exists()) return null
        val ticketCode = doc.getString("ticketCode") ?: return null
        return BookingEntity(
            id = 0L,
            ticketCode = ticketCode,
            queueNumber = (doc.getLong("queueNumber") ?: 1L).toInt(),
            venueId = (doc.getLong("venueId") ?: return null).toInt(),
            venueName = doc.getString("venueName") ?: "",
            venueCity = doc.getString("venueCity") ?: "",
            venueCategory = doc.getString("venueCategory") ?: "",
            venuePhone = doc.getString("venuePhone") ?: "",
            customerName = doc.getString("customerName") ?: "",
            customerPhone = doc.getString("customerPhone") ?: "",
            selectedOption = doc.getString("selectedOption") ?: "",
            notes = doc.getString("notes") ?: "",
            optionalInfo = doc.getString("optionalInfo") ?: "",
            priceDinar = (doc.getLong("priceDinar") ?: 0L).toInt(),
            status = doc.getString("status") ?: "ACTIVE",
            stage = doc.getString("stage") ?: "MONITORED",
            approvalStatus = doc.getString("approvalStatus") ?: "PENDING",
            handledByProvider = doc.getString("handledByProvider") ?: "",
            approvalNote = doc.getString("approvalNote") ?: "",
            peopleAhead = (doc.getLong("peopleAhead") ?: 0L).toInt(),
            estimatedMinutes = (doc.getLong("estimatedMinutes") ?: 0L).toInt(),
            createdAt = doc.getLong("createdAtMillis") ?: System.currentTimeMillis(),
            completedAt = doc.getLong("completedAtMillis"),
            userRating = doc.getLong("userRating")?.toInt(),
            userReview = doc.getString("userReview")
        )
    }

    // ==================== Queue Items ====================

    fun queueItemCreateMap(
        userId: String,
        item: MerchantQueueItemEntity,
        bookingTicketCode: String? = null
    ): Map<String, Any?> {
        val stableId = if (item.id > 0L) item.id else item.createdAt.coerceAtLeast(1L)
        return hashMapOf(
            "userId" to userId,
            "queueItemId" to stableId,
            "venueId" to item.venueId,
            "ticketNumber" to item.ticketNumber.coerceIn(1, 10000),
            "ticketCode" to item.ticketCode.take(40),
            "customerName" to item.customerName.take(120),
            "customerPhone" to item.customerPhone.take(30),
            "selectedOption" to item.selectedOption.take(120),
            "status" to item.status,
            "bookingId" to item.bookingId?.takeIf { it > 0L },
            "bookingTicketCode" to bookingTicketCode?.take(40),
            "createdAtMillis" to item.createdAt.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun queueItemUpdateMap(
        status: String,
        bookingId: Long? = null,
        bookingTicketCode: String? = null
    ): Map<String, Any?> = hashMapOf(
        "status" to status,
        "bookingId" to bookingId?.takeIf { it > 0L },
        "bookingTicketCode" to bookingTicketCode?.take(40),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toQueueItemEntity(doc: DocumentSnapshot): Pair<MerchantQueueItemEntity, String?>? {
        if (!doc.exists()) return null
        val ticketCode = doc.getString("ticketCode") ?: return null
        val entity = MerchantQueueItemEntity(
            id = 0L,
            venueId = (doc.getLong("venueId") ?: return null).toInt(),
            ticketNumber = (doc.getLong("ticketNumber") ?: 1L).toInt(),
            ticketCode = ticketCode,
            customerName = doc.getString("customerName") ?: "",
            customerPhone = doc.getString("customerPhone") ?: "",
            selectedOption = doc.getString("selectedOption") ?: "",
            status = doc.getString("status") ?: "WAITING",
            bookingId = doc.getLong("bookingId"),
            createdAt = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()
        )
        return entity to doc.getString("bookingTicketCode")
    }

    // ==================== Reviews ====================

    fun reviewCreateMap(userId: String, review: ReviewEntity): Map<String, Any> {
        val stableId = if (review.id > 0L) review.id else review.createdAt.coerceAtLeast(1L)
        return mapOf(
            "userId" to userId,
            "reviewId" to stableId,
            "venueId" to review.venueId,
            "authorName" to review.authorName.take(120),
            "rating" to review.rating.coerceIn(1, 5),
            "comment" to review.comment.take(400),
            "createdAtMillis" to review.createdAt.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun toReviewEntity(doc: DocumentSnapshot): ReviewEntity? {
        if (!doc.exists()) return null
        return ReviewEntity(
            id = doc.getLong("reviewId") ?: 0L,
            venueId = (doc.getLong("venueId") ?: return null).toInt(),
            authorName = doc.getString("authorName") ?: "",
            rating = (doc.getLong("rating") ?: 5L).toInt(),
            comment = doc.getString("comment") ?: "",
            createdAt = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()
        )
    }

    // ==================== Support Tickets & Messages ====================

    fun supportTicketCreateMap(userId: String, ticket: SupportTicketEntity): Map<String, Any> = mapOf(
        "userId" to userId,
        "externalId" to ticket.externalId.take(64),
        "senderName" to ticket.senderName.take(120),
        "senderPhone" to ticket.senderPhone.take(30),
        "category" to ticket.category,
        "requestType" to ticket.requestType,
        "relatedVenueName" to ticket.relatedVenueName.take(120),
        "subject" to ticket.subject.take(160),
        "message" to ticket.message.take(1000),
        "status" to ticket.status,
        "adminReply" to ticket.adminReply.take(1000),
        "repliedByAdmin" to ticket.repliedByAdmin.take(120),
        "createdAtMillis" to ticket.createdAt.coerceAtLeast(0L),
        "updatedAtMillis" to ticket.updatedAt.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun supportTicketUpdateMap(ticket: SupportTicketEntity): Map<String, Any> = mapOf(
        "senderName" to ticket.senderName.take(120),
        "status" to ticket.status,
        "adminReply" to ticket.adminReply.take(1000),
        "repliedByAdmin" to ticket.repliedByAdmin.take(120),
        "updatedAtMillis" to ticket.updatedAt.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toSupportTicketEntity(doc: DocumentSnapshot, isControlApp: Boolean): SupportTicketEntity? {
        if (!doc.exists()) return null
        val externalId = doc.getString("externalId") ?: return null
        val rawAdmin = doc.getString("repliedByAdmin") ?: ""
        return SupportTicketEntity(
            id = 0L,
            externalId = externalId,
            senderName = doc.getString("senderName") ?: "مستخدم دوري",
            senderPhone = doc.getString("senderPhone") ?: "",
            category = doc.getString("category") ?: "GENERAL",
            requestType = doc.getString("requestType") ?: "CHAT",
            relatedVenueName = doc.getString("relatedVenueName") ?: "",
            subject = doc.getString("subject") ?: "",
            message = doc.getString("message") ?: "",
            status = doc.getString("status") ?: "SENT",
            adminReply = doc.getString("adminReply") ?: "",
            repliedByAdmin = if (!isControlApp && rawAdmin.isNotBlank()) "فريق الدعم الفني" else rawAdmin,
            syncedToControl = true,
            createdAt = doc.getLong("createdAtMillis") ?: System.currentTimeMillis(),
            updatedAt = doc.getLong("updatedAtMillis") ?: System.currentTimeMillis()
        )
    }

    fun supportMessageCreateMap(userId: String, msg: SupportMessageEntity): Map<String, Any> = mapOf(
        "userId" to userId,
        "externalMessageId" to msg.externalMessageId.take(80),
        "ticketExternalId" to msg.ticketExternalId.take(64),
        "fromAdmin" to msg.isFromAdmin,
        "senderName" to msg.senderName.ifBlank { "مستخدم دوري" }.take(120),
        "body" to msg.body.take(1000),
        "createdAtMillis" to msg.createdAt.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toSupportMessageEntity(doc: DocumentSnapshot, isControlApp: Boolean): SupportMessageEntity? {
        if (!doc.exists()) return null
        val extMsgId = doc.getString("externalMessageId") ?: return null
        val ticketExtId = doc.getString("ticketExternalId") ?: return null
        val fromAdmin = doc.getBoolean("fromAdmin") ?: false
        val rawSender = doc.getString("senderName") ?: ""
        return SupportMessageEntity(
            id = 0L,
            externalMessageId = extMsgId,
            ticketExternalId = ticketExtId,
            isFromAdmin = fromAdmin,
            senderName = if (!isControlApp && fromAdmin) "فريق الدعم الفني" else rawSender,
            body = doc.getString("body") ?: "",
            syncedToPeer = true,
            createdAt = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()
        )
    }

    // ==================== Admins & Providers (Control App Only) ====================

    fun adminMutableMap(admin: AdminAccountEntity): Map<String, Any> = mapOf(
        "name" to admin.name.take(120),
        "roleTitle" to admin.roleTitle.take(120),
        "phone" to admin.phone.take(30),
        "active" to admin.isActive,
        "superAdmin" to admin.isSuperAdmin,
        "canManageFeatures" to admin.canManageFeatures,
        "canManageVenues" to admin.canManageVenues,
        "canManageQueues" to admin.canManageQueues,
        "canReplySupport" to admin.canReplySupport,
        "canManageProviders" to admin.canManageProviders,
        "canManageAdmins" to admin.canManageAdmins,
        "canRunCommands" to admin.canRunCommands,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun adminCreateMap(userId: String, admin: AdminAccountEntity): Map<String, Any> =
        adminMutableMap(admin) + mapOf(
            "userId" to userId,
            "adminId" to admin.id.coerceAtLeast(1L),
            "createdAtMillis" to admin.createdAt.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp()
        )

    fun toAdminEntity(doc: DocumentSnapshot): AdminAccountEntity? {
        if (!doc.exists()) return null
        return AdminAccountEntity(
            id = doc.getLong("adminId") ?: return null,
            name = doc.getString("name") ?: return null,
            roleTitle = doc.getString("roleTitle") ?: "",
            phone = doc.getString("phone") ?: "",
            isActive = doc.getBoolean("active") ?: true,
            isSuperAdmin = doc.getBoolean("superAdmin") ?: false,
            canManageFeatures = doc.getBoolean("canManageFeatures") ?: true,
            canManageVenues = doc.getBoolean("canManageVenues") ?: true,
            canManageQueues = doc.getBoolean("canManageQueues") ?: true,
            canReplySupport = doc.getBoolean("canReplySupport") ?: true,
            canManageProviders = doc.getBoolean("canManageProviders") ?: true,
            canManageAdmins = doc.getBoolean("canManageAdmins") ?: false,
            canRunCommands = doc.getBoolean("canRunCommands") ?: false,
            createdAt = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()
        )
    }

    fun providerMutableMap(provider: ServiceProviderEntity): Map<String, Any> = mapOf(
        "name" to provider.name.take(120),
        "providerRole" to provider.providerRole.take(40),
        "customTitle" to provider.customTitle.take(80),
        "phone" to provider.phone.take(30),
        "venueId" to provider.venueId,
        "venueName" to provider.venueName.take(120),
        "canAcceptTickets" to provider.canAcceptTickets,
        "canCallNext" to provider.canCallNext,
        "canPauseVenue" to provider.canPauseVenue,
        "active" to provider.isActive,
        "acceptedCount" to provider.acceptedCount.coerceIn(0, 1000000),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun providerCreateMap(userId: String, provider: ServiceProviderEntity): Map<String, Any> =
        providerMutableMap(provider) + mapOf(
            "userId" to userId,
            "providerId" to provider.id.coerceAtLeast(1L),
            "createdAtMillis" to provider.createdAt.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp()
        )

    fun toProviderEntity(doc: DocumentSnapshot): ServiceProviderEntity? {
        if (!doc.exists()) return null
        return ServiceProviderEntity(
            id = doc.getLong("providerId") ?: return null,
            name = doc.getString("name") ?: return null,
            providerRole = doc.getString("providerRole") ?: "MERCHANT",
            customTitle = doc.getString("customTitle") ?: "",
            phone = doc.getString("phone") ?: "",
            venueId = (doc.getLong("venueId") ?: return null).toInt(),
            venueName = doc.getString("venueName") ?: "",
            canAcceptTickets = doc.getBoolean("canAcceptTickets") ?: true,
            canCallNext = doc.getBoolean("canCallNext") ?: true,
            canPauseVenue = doc.getBoolean("canPauseVenue") ?: false,
            isActive = doc.getBoolean("active") ?: true,
            acceptedCount = (doc.getLong("acceptedCount") ?: 0L).toInt(),
            createdAt = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()
        )
    }
}
