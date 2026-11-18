package com.example.bridge

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.bridge.DoriBridgeContract.BookingCols
import com.example.bridge.DoriBridgeContract.ControlConfigCols
import com.example.bridge.DoriBridgeContract.ProviderCols
import com.example.bridge.DoriBridgeContract.SupportCols
import com.example.bridge.DoriBridgeContract.SupportMessageCols
import com.example.bridge.DoriBridgeContract.VenueCols
import com.example.data.local.BookingEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.local.SupportMessageEntity
import com.example.data.local.SupportTicketEntity
import com.example.data.local.VenueEntity
import com.example.data.model.BookingApprovalStatus
import com.example.data.model.SupportRequestType
import com.example.data.model.SupportTicketStatus
import com.example.data.model.UserAppControlConfig

data class BookingDecisionSnapshot(
    val ticketCode: String,
    val approvalStatus: String,
    val handledByProvider: String,
    val approvalNote: String,
    val status: String = "",
    val stage: String = "",
    val peopleAhead: Int = -1,
    val estimatedMinutes: Int = -1
)

data class ControlStatePayload(
    val controlConfig: UserAppControlConfig,
    val controlRevision: Long,
    val queueResetRevision: Long,
    val loyaltyRevision: Long,
    val loyaltyPoints: Int
)

object DoriLocalBridgeClient {

    fun supportToContentValues(ticket: SupportTicketEntity): ContentValues = ContentValues().apply {
        put(SupportCols.EXTERNAL_ID, ticket.externalId)
        put(SupportCols.SENDER_NAME, ticket.senderName)
        put(SupportCols.SENDER_PHONE, ticket.senderPhone)
        put(SupportCols.CATEGORY, ticket.category)
        put(SupportCols.REQUEST_TYPE, ticket.requestType)
        put(SupportCols.RELATED_VENUE_NAME, ticket.relatedVenueName)
        put(SupportCols.SUBJECT, ticket.subject)
        put(SupportCols.MESSAGE, ticket.message)
        put(SupportCols.STATUS, ticket.status)
        put(SupportCols.ADMIN_REPLY, ticket.adminReply)
        put(SupportCols.REPLIED_BY_ADMIN, ticket.repliedByAdmin)
        put(SupportCols.SYNCED_TO_CONTROL, if (ticket.syncedToControl) 1 else 0)
        put(SupportCols.CREATED_AT, ticket.createdAt)
        put(SupportCols.UPDATED_AT, ticket.updatedAt)
    }

    fun supportMessageToContentValues(message: SupportMessageEntity): ContentValues = ContentValues().apply {
        put(SupportMessageCols.EXTERNAL_MESSAGE_ID, message.externalMessageId)
        put(SupportMessageCols.TICKET_EXTERNAL_ID, message.ticketExternalId)
        put(SupportMessageCols.IS_FROM_ADMIN, if (message.isFromAdmin) 1 else 0)
        put(SupportMessageCols.SENDER_NAME, message.senderName)
        put(SupportMessageCols.BODY, message.body)
        put(SupportMessageCols.SYNCED_TO_PEER, 1)
        put(SupportMessageCols.CREATED_AT, message.createdAt)
    }

    fun bookingToContentValues(booking: BookingEntity): ContentValues = ContentValues().apply {
        put(BookingCols.TICKET_CODE, booking.ticketCode)
        put(BookingCols.QUEUE_NUMBER, booking.queueNumber)
        put(BookingCols.VENUE_ID, booking.venueId)
        put(BookingCols.VENUE_NAME, booking.venueName)
        put(BookingCols.VENUE_CITY, booking.venueCity)
        put(BookingCols.VENUE_CATEGORY, booking.venueCategory)
        put(BookingCols.VENUE_PHONE, booking.venuePhone)
        put(BookingCols.CUSTOMER_NAME, booking.customerName)
        put(BookingCols.CUSTOMER_PHONE, booking.customerPhone)
        put(BookingCols.SELECTED_OPTION, booking.selectedOption)
        put(BookingCols.NOTES, booking.notes)
        put(BookingCols.OPTIONAL_INFO, booking.optionalInfo)
        put(BookingCols.PRICE_DINAR, booking.priceDinar)
        put(BookingCols.STATUS, booking.status)
        put(BookingCols.STAGE, booking.stage)
        put(BookingCols.APPROVAL_STATUS, booking.approvalStatus)
        put(BookingCols.HANDLED_BY_PROVIDER, booking.handledByProvider)
        put(BookingCols.APPROVAL_NOTE, booking.approvalNote)
        put(BookingCols.PEOPLE_AHEAD, booking.peopleAhead)
        put(BookingCols.ESTIMATED_MINUTES, booking.estimatedMinutes)
        put(BookingCols.CREATED_AT, booking.createdAt)
    }

    fun controlStateToContentValues(payload: ControlStatePayload): ContentValues = ContentValues().apply {
        val cfg = payload.controlConfig
        put(ControlConfigCols.ALLOW_NEW_BOOKINGS, if (cfg.allowNewBookings) 1 else 0)
        put(ControlConfigCols.ENABLE_FASTEST_TURN, if (cfg.enableFastestTurn) 1 else 0)
        put(ControlConfigCols.ENABLE_QR_CODE, if (cfg.enableQrCode) 1 else 0)
        put(ControlConfigCols.ENABLE_WHATSAPP_SHARE, if (cfg.enableWhatsAppShare) 1 else 0)
        put(ControlConfigCols.ENABLE_REVIEWS, if (cfg.enableReviews) 1 else 0)
        put(ControlConfigCols.ENABLE_LOYALTY, if (cfg.enableLoyalty) 1 else 0)
        put(ControlConfigCols.ENABLE_OPTIONAL_FIELDS, if (cfg.enableOptionalFields) 1 else 0)
        put(ControlConfigCols.ENABLE_LIVE_ALERTS, if (cfg.enableLiveAlerts) 1 else 0)
        put(ControlConfigCols.POINTS_PER_BOOKING, cfg.pointsPerBooking)
        put(ControlConfigCols.DISABLED_CATEGORIES_CSV, cfg.disabledCategories.joinToString("\n"))
        put(ControlConfigCols.BROADCAST_MESSAGE, cfg.broadcastMessage)
        put(ControlConfigCols.CONTROL_REVISION, payload.controlRevision)
        put(ControlConfigCols.QUEUE_RESET_REVISION, payload.queueResetRevision)
        put(ControlConfigCols.LOYALTY_REVISION, payload.loyaltyRevision)
        put(ControlConfigCols.LOYALTY_POINTS, payload.loyaltyPoints)
    }

    fun venueToContentValues(venue: VenueEntity): ContentValues = ContentValues().apply {
        put(VenueCols.ID, venue.id)
        put(VenueCols.NAME, venue.name)
        put(VenueCols.CITY, venue.city)
        put(VenueCols.CATEGORY, venue.category)
        put(VenueCols.SPECIALTY, venue.specialty)
        put(VenueCols.AREA, venue.area)
        put(VenueCols.PRICE_DINAR, venue.priceDinar)
        put(VenueCols.RATING, venue.rating)
        put(VenueCols.REVIEW_COUNT, venue.reviewCount)
        put(VenueCols.WORKING_HOURS, venue.workingHours)
        put(VenueCols.PHONE, venue.phone)
        put(VenueCols.WHATSAPP, venue.whatsapp)
        put(VenueCols.LATITUDE, venue.latitude)
        put(VenueCols.LONGITUDE, venue.longitude)
        put(VenueCols.DESCRIPTION, venue.description)
        put(VenueCols.CUSTOM_OPTIONS_CSV, venue.customOptionsCsv)
        put(VenueCols.IS_OPEN, if (venue.isOpen) 1 else 0)
        put(VenueCols.IS_PAUSED, if (venue.isPaused) 1 else 0)
        put(VenueCols.IS_ACCEPTING_BOOKINGS, if (venue.isAcceptingBookings) 1 else 0)
        put(VenueCols.CURRENT_SERVING_NUMBER, venue.currentServingNumber)
        put(VenueCols.NEXT_TICKET_NUMBER, venue.nextTicketNumber)
        put(VenueCols.SERVED_TODAY_COUNT, venue.servedTodayCount)
        put(VenueCols.AVG_MINUTES_PER_PERSON, venue.avgMinutesPerPerson)
        put(VenueCols.WAITING_COUNT, venue.waitingCount)
    }

    fun providerToContentValues(provider: ServiceProviderEntity): ContentValues = ContentValues().apply {
        put(ProviderCols.ID, provider.id)
        put(ProviderCols.NAME, provider.name)
        put(ProviderCols.PROVIDER_ROLE, provider.providerRole)
        put(ProviderCols.CUSTOM_TITLE, provider.customTitle)
        put(ProviderCols.PHONE, provider.phone)
        put(ProviderCols.VENUE_ID, provider.venueId)
        put(ProviderCols.VENUE_NAME, provider.venueName)
        put(ProviderCols.CAN_ACCEPT_TICKETS, if (provider.canAcceptTickets) 1 else 0)
        put(ProviderCols.CAN_CALL_NEXT, if (provider.canCallNext) 1 else 0)
        put(ProviderCols.CAN_PAUSE_VENUE, if (provider.canPauseVenue) 1 else 0)
        put(ProviderCols.IS_ACTIVE, if (provider.isActive) 1 else 0)
        put(ProviderCols.ACCEPTED_COUNT, provider.acceptedCount)
        put(ProviderCols.CREATED_AT, provider.createdAt)
    }

    fun pushSupportTicketToControl(context: Context, ticket: SupportTicketEntity): Boolean {
        return runCatching {
            val values = supportToContentValues(
                ticket.copy(
                    status = SupportTicketStatus.DELIVERED.name,
                    syncedToControl = true
                )
            )
            context.contentResolver.insert(DoriBridgeContract.CONTROL_SUPPORT_URI, values) != null
        }.getOrDefault(false)
    }

    fun pushSupportReplyToUser(context: Context, ticket: SupportTicketEntity): Boolean {
        return runCatching {
            val values = supportToContentValues(ticket)
            context.contentResolver.insert(DoriBridgeContract.USER_SUPPORT_URI, values) != null
        }.getOrDefault(false)
    }

    fun pullSupportTicketsFromPeer(context: Context, isControlApp: Boolean): List<SupportTicketEntity> {
        val targetUri = if (isControlApp) {
            DoriBridgeContract.USER_SUPPORT_URI
        } else {
            DoriBridgeContract.CONTROL_SUPPORT_URI
        }
        return runCatching {
            val list = mutableListOf<SupportTicketEntity>()
            context.contentResolver.query(targetUri, null, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(readSupportTicketFromCursor(cursor))
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    fun pushSupportMessageToControl(context: Context, message: SupportMessageEntity): Boolean {
        return runCatching {
            val values = supportMessageToContentValues(message)
            context.contentResolver.insert(DoriBridgeContract.CONTROL_SUPPORT_MESSAGES_URI, values) != null
        }.getOrDefault(false)
    }

    fun pushSupportMessageToUser(context: Context, message: SupportMessageEntity): Boolean {
        return runCatching {
            val values = supportMessageToContentValues(message)
            context.contentResolver.insert(DoriBridgeContract.USER_SUPPORT_MESSAGES_URI, values) != null
        }.getOrDefault(false)
    }

    fun pullSupportMessagesFromPeer(context: Context, isControlApp: Boolean): List<SupportMessageEntity> {
        val targetUri = if (isControlApp) {
            DoriBridgeContract.USER_SUPPORT_MESSAGES_URI
        } else {
            DoriBridgeContract.CONTROL_SUPPORT_MESSAGES_URI
        }
        return runCatching {
            val list = mutableListOf<SupportMessageEntity>()
            context.contentResolver.query(targetUri, null, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(readSupportMessageFromCursor(cursor))
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    fun pushBookingToControl(context: Context, booking: BookingEntity): Boolean {
        return runCatching {
            val values = bookingToContentValues(booking)
            context.contentResolver.insert(DoriBridgeContract.CONTROL_BOOKINGS_URI, values) != null
        }.getOrDefault(false)
    }

    fun pullBookingsFromUser(context: Context): List<BookingEntity> {
        return runCatching {
            val list = mutableListOf<BookingEntity>()
            context.contentResolver.query(DoriBridgeContract.USER_BOOKINGS_URI, null, null, null, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        list.add(readBookingFromCursor(cursor))
                    }
                }
            list
        }.getOrDefault(emptyList())
    }

    fun pushBookingDecisionToUser(
        context: Context,
        ticketCode: String,
        approvalStatus: String,
        handledByProvider: String,
        approvalNote: String,
        status: String = "",
        stage: String = "",
        peopleAhead: Int = -1,
        estimatedMinutes: Int = -1
    ): Boolean {
        return runCatching {
            val values = ContentValues().apply {
                put(BookingCols.TICKET_CODE, ticketCode)
                put(BookingCols.APPROVAL_STATUS, approvalStatus)
                put(BookingCols.HANDLED_BY_PROVIDER, handledByProvider)
                put(BookingCols.APPROVAL_NOTE, approvalNote)
                if (status.isNotBlank()) put(BookingCols.STATUS, status)
                if (stage.isNotBlank()) put(BookingCols.STAGE, stage)
                if (peopleAhead >= 0) put(BookingCols.PEOPLE_AHEAD, peopleAhead)
                if (estimatedMinutes >= 0) put(BookingCols.ESTIMATED_MINUTES, estimatedMinutes)
            }
            context.contentResolver.insert(DoriBridgeContract.USER_DECISIONS_URI, values) != null
        }.getOrDefault(false)
    }

    fun pullBookingDecisionsFromControl(context: Context): List<BookingDecisionSnapshot> {
        return runCatching {
            val list = mutableListOf<BookingDecisionSnapshot>()
            context.contentResolver.query(DoriBridgeContract.CONTROL_DECISIONS_URI, null, null, null, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val code = cursor.getStringOrEmpty(BookingCols.TICKET_CODE)
                        if (code.isNotBlank()) {
                            list.add(
                                BookingDecisionSnapshot(
                                    ticketCode = code,
                                    approvalStatus = cursor.getStringOrEmpty(BookingCols.APPROVAL_STATUS)
                                        .ifBlank { BookingApprovalStatus.PENDING.name },
                                    handledByProvider = cursor.getStringOrEmpty(BookingCols.HANDLED_BY_PROVIDER),
                                    approvalNote = cursor.getStringOrEmpty(BookingCols.APPROVAL_NOTE),
                                    status = cursor.getStringOrEmpty(BookingCols.STATUS),
                                    stage = cursor.getStringOrEmpty(BookingCols.STAGE),
                                    peopleAhead = cursor.getIntOrDefault(BookingCols.PEOPLE_AHEAD, -1),
                                    estimatedMinutes = cursor.getIntOrDefault(BookingCols.ESTIMATED_MINUTES, -1)
                                )
                            )
                        }
                    }
                }
            list
        }.getOrDefault(emptyList())
    }

    fun pushControlStateToUser(context: Context, payload: ControlStatePayload): Boolean {
        return runCatching {
            val values = controlStateToContentValues(payload)
            context.contentResolver.insert(DoriBridgeContract.USER_CONFIG_URI, values) != null
        }.getOrDefault(false)
    }

    fun pullControlStateFromControl(context: Context): ControlStatePayload? {
        return runCatching {
            context.contentResolver.query(DoriBridgeContract.CONTROL_CONFIG_URI, null, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) readControlStateFromCursor(cursor) else null
                }
        }.getOrNull()
    }

    fun pushVenuesToUser(context: Context, venues: List<VenueEntity>): Boolean {
        return runCatching {
            val arr = venues.map { venueToContentValues(it) }.toTypedArray()
            if (arr.isEmpty()) {
                context.contentResolver.delete(DoriBridgeContract.USER_VENUES_URI, null, null) >= 0
            } else {
                context.contentResolver.bulkInsert(DoriBridgeContract.USER_VENUES_URI, arr) >= 0
            }
        }.getOrDefault(false)
    }

    fun pullVenuesFromControl(context: Context): List<VenueEntity>? {
        return runCatching {
            val list = mutableListOf<VenueEntity>()
            val cursor = context.contentResolver.query(
                DoriBridgeContract.CONTROL_VENUES_URI,
                null,
                null,
                null,
                null
            ) ?: return null
            cursor.use {
                while (it.moveToNext()) {
                    list.add(readVenueFromCursor(it))
                }
            }
            list
        }.getOrNull()
    }

    fun pushProvidersToUser(context: Context, providers: List<ServiceProviderEntity>): Boolean {
        return runCatching {
            val arr = providers.map { providerToContentValues(it) }.toTypedArray()
            if (arr.isEmpty()) {
                context.contentResolver.delete(DoriBridgeContract.USER_PROVIDERS_URI, null, null) >= 0
            } else {
                context.contentResolver.bulkInsert(DoriBridgeContract.USER_PROVIDERS_URI, arr) >= 0
            }
        }.getOrDefault(false)
    }

    fun pullProvidersFromControl(context: Context): List<ServiceProviderEntity>? {
        return runCatching {
            val list = mutableListOf<ServiceProviderEntity>()
            val cursor = context.contentResolver.query(
                DoriBridgeContract.CONTROL_PROVIDERS_URI,
                null,
                null,
                null,
                null
            ) ?: return null
            cursor.use {
                while (it.moveToNext()) {
                    list.add(readProviderFromCursor(it))
                }
            }
            list
        }.getOrNull()
    }

    fun controlStateFromContentValues(v: ContentValues): ControlStatePayload {
        val disabledCsv = v.getAsString(ControlConfigCols.DISABLED_CATEGORIES_CSV).orEmpty()
        val disabledSet = disabledCsv.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return ControlStatePayload(
            controlConfig = UserAppControlConfig(
                allowNewBookings = (v.getAsInteger(ControlConfigCols.ALLOW_NEW_BOOKINGS) ?: 1) == 1,
                enableFastestTurn = (v.getAsInteger(ControlConfigCols.ENABLE_FASTEST_TURN) ?: 1) == 1,
                enableQrCode = (v.getAsInteger(ControlConfigCols.ENABLE_QR_CODE) ?: 1) == 1,
                enableWhatsAppShare = (v.getAsInteger(ControlConfigCols.ENABLE_WHATSAPP_SHARE) ?: 1) == 1,
                enableReviews = (v.getAsInteger(ControlConfigCols.ENABLE_REVIEWS) ?: 1) == 1,
                enableLoyalty = (v.getAsInteger(ControlConfigCols.ENABLE_LOYALTY) ?: 1) == 1,
                enableOptionalFields = (v.getAsInteger(ControlConfigCols.ENABLE_OPTIONAL_FIELDS) ?: 1) == 1,
                enableLiveAlerts = (v.getAsInteger(ControlConfigCols.ENABLE_LIVE_ALERTS) ?: 1) == 1,
                pointsPerBooking = v.getAsInteger(ControlConfigCols.POINTS_PER_BOOKING) ?: 10,
                disabledCategories = disabledSet,
                broadcastMessage = v.getAsString(ControlConfigCols.BROADCAST_MESSAGE).orEmpty()
            ),
            controlRevision = v.getAsLong(ControlConfigCols.CONTROL_REVISION) ?: 0L,
            queueResetRevision = v.getAsLong(ControlConfigCols.QUEUE_RESET_REVISION) ?: 0L,
            loyaltyRevision = v.getAsLong(ControlConfigCols.LOYALTY_REVISION) ?: 0L,
            loyaltyPoints = v.getAsInteger(ControlConfigCols.LOYALTY_POINTS) ?: 60
        )
    }

    fun venueFromContentValues(v: ContentValues): VenueEntity? {
        val id = v.getAsInteger(VenueCols.ID) ?: return null
        return VenueEntity(
            id = id,
            name = v.getAsString(VenueCols.NAME).orEmpty(),
            city = v.getAsString(VenueCols.CITY).orEmpty(),
            category = v.getAsString(VenueCols.CATEGORY).orEmpty(),
            specialty = v.getAsString(VenueCols.SPECIALTY).orEmpty(),
            area = v.getAsString(VenueCols.AREA).orEmpty(),
            priceDinar = v.getAsInteger(VenueCols.PRICE_DINAR) ?: 15000,
            rating = v.getAsDouble(VenueCols.RATING) ?: 4.8,
            reviewCount = v.getAsInteger(VenueCols.REVIEW_COUNT) ?: 1,
            workingHours = v.getAsString(VenueCols.WORKING_HOURS).orEmpty(),
            phone = v.getAsString(VenueCols.PHONE).orEmpty(),
            whatsapp = v.getAsString(VenueCols.WHATSAPP).orEmpty(),
            latitude = v.getAsDouble(VenueCols.LATITUDE) ?: 36.3489,
            longitude = v.getAsDouble(VenueCols.LONGITUDE) ?: 43.1577,
            description = v.getAsString(VenueCols.DESCRIPTION).orEmpty(),
            customOptionsCsv = v.getAsString(VenueCols.CUSTOM_OPTIONS_CSV).orEmpty(),
            isOpen = (v.getAsInteger(VenueCols.IS_OPEN) ?: 1) == 1,
            isPaused = (v.getAsInteger(VenueCols.IS_PAUSED) ?: 0) == 1,
            isAcceptingBookings = (v.getAsInteger(VenueCols.IS_ACCEPTING_BOOKINGS) ?: 1) == 1,
            currentServingNumber = v.getAsInteger(VenueCols.CURRENT_SERVING_NUMBER) ?: 0,
            nextTicketNumber = v.getAsInteger(VenueCols.NEXT_TICKET_NUMBER) ?: 1,
            servedTodayCount = v.getAsInteger(VenueCols.SERVED_TODAY_COUNT) ?: 0,
            avgMinutesPerPerson = v.getAsInteger(VenueCols.AVG_MINUTES_PER_PERSON) ?: 8,
            isFavorite = false,
            waitingCount = v.getAsInteger(VenueCols.WAITING_COUNT) ?: 0
        )
    }

    fun providerFromContentValues(v: ContentValues): ServiceProviderEntity? {
        val id = v.getAsLong(ProviderCols.ID) ?: return null
        return ServiceProviderEntity(
            id = id,
            name = v.getAsString(ProviderCols.NAME).orEmpty(),
            providerRole = v.getAsString(ProviderCols.PROVIDER_ROLE).orEmpty(),
            customTitle = v.getAsString(ProviderCols.CUSTOM_TITLE).orEmpty(),
            phone = v.getAsString(ProviderCols.PHONE).orEmpty(),
            venueId = v.getAsInteger(ProviderCols.VENUE_ID) ?: 1,
            venueName = v.getAsString(ProviderCols.VENUE_NAME).orEmpty(),
            canAcceptTickets = (v.getAsInteger(ProviderCols.CAN_ACCEPT_TICKETS) ?: 1) == 1,
            canCallNext = (v.getAsInteger(ProviderCols.CAN_CALL_NEXT) ?: 1) == 1,
            canPauseVenue = (v.getAsInteger(ProviderCols.CAN_PAUSE_VENUE) ?: 0) == 1,
            isActive = (v.getAsInteger(ProviderCols.IS_ACTIVE) ?: 1) == 1,
            acceptedCount = v.getAsInteger(ProviderCols.ACCEPTED_COUNT) ?: 0,
            createdAt = v.getAsLong(ProviderCols.CREATED_AT) ?: System.currentTimeMillis()
        )
    }

    private fun readSupportTicketFromCursor(c: Cursor): SupportTicketEntity = SupportTicketEntity(
        externalId = c.getStringOrEmpty(SupportCols.EXTERNAL_ID),
        senderName = c.getStringOrEmpty(SupportCols.SENDER_NAME),
        senderPhone = c.getStringOrEmpty(SupportCols.SENDER_PHONE),
        category = c.getStringOrEmpty(SupportCols.CATEGORY),
        requestType = c.getStringOrEmpty(SupportCols.REQUEST_TYPE).ifBlank { SupportRequestType.CHAT.name },
        relatedVenueName = c.getStringOrEmpty(SupportCols.RELATED_VENUE_NAME),
        subject = c.getStringOrEmpty(SupportCols.SUBJECT),
        message = c.getStringOrEmpty(SupportCols.MESSAGE),
        status = c.getStringOrEmpty(SupportCols.STATUS),
        adminReply = c.getStringOrEmpty(SupportCols.ADMIN_REPLY),
        repliedByAdmin = c.getStringOrEmpty(SupportCols.REPLIED_BY_ADMIN),
        syncedToControl = c.getIntOrDefault(SupportCols.SYNCED_TO_CONTROL, 0) == 1,
        createdAt = c.getLongOrDefault(SupportCols.CREATED_AT, System.currentTimeMillis()),
        updatedAt = c.getLongOrDefault(SupportCols.UPDATED_AT, System.currentTimeMillis())
    )

    private fun readSupportMessageFromCursor(c: Cursor): SupportMessageEntity = SupportMessageEntity(
        externalMessageId = c.getStringOrEmpty(SupportMessageCols.EXTERNAL_MESSAGE_ID),
        ticketExternalId = c.getStringOrEmpty(SupportMessageCols.TICKET_EXTERNAL_ID),
        isFromAdmin = c.getIntOrDefault(SupportMessageCols.IS_FROM_ADMIN, 0) == 1,
        senderName = c.getStringOrEmpty(SupportMessageCols.SENDER_NAME),
        body = c.getStringOrEmpty(SupportMessageCols.BODY),
        syncedToPeer = true,
        createdAt = c.getLongOrDefault(SupportMessageCols.CREATED_AT, System.currentTimeMillis())
    )

    private fun readBookingFromCursor(c: Cursor): BookingEntity = BookingEntity(
        ticketCode = c.getStringOrEmpty(BookingCols.TICKET_CODE),
        queueNumber = c.getIntOrDefault(BookingCols.QUEUE_NUMBER, 1),
        venueId = c.getIntOrDefault(BookingCols.VENUE_ID, 1),
        venueName = c.getStringOrEmpty(BookingCols.VENUE_NAME),
        venueCity = c.getStringOrEmpty(BookingCols.VENUE_CITY),
        venueCategory = c.getStringOrEmpty(BookingCols.VENUE_CATEGORY),
        venuePhone = c.getStringOrEmpty(BookingCols.VENUE_PHONE),
        customerName = c.getStringOrEmpty(BookingCols.CUSTOMER_NAME),
        customerPhone = c.getStringOrEmpty(BookingCols.CUSTOMER_PHONE),
        selectedOption = c.getStringOrEmpty(BookingCols.SELECTED_OPTION),
        notes = c.getStringOrEmpty(BookingCols.NOTES),
        optionalInfo = c.getStringOrEmpty(BookingCols.OPTIONAL_INFO),
        priceDinar = c.getIntOrDefault(BookingCols.PRICE_DINAR, 0),
        status = c.getStringOrEmpty(BookingCols.STATUS),
        stage = c.getStringOrEmpty(BookingCols.STAGE),
        approvalStatus = c.getStringOrEmpty(BookingCols.APPROVAL_STATUS)
            .ifBlank { BookingApprovalStatus.PENDING.name },
        handledByProvider = c.getStringOrEmpty(BookingCols.HANDLED_BY_PROVIDER),
        approvalNote = c.getStringOrEmpty(BookingCols.APPROVAL_NOTE),
        peopleAhead = c.getIntOrDefault(BookingCols.PEOPLE_AHEAD, 0),
        estimatedMinutes = c.getIntOrDefault(BookingCols.ESTIMATED_MINUTES, 0),
        createdAt = c.getLongOrDefault(BookingCols.CREATED_AT, System.currentTimeMillis())
    )

    private fun readControlStateFromCursor(c: Cursor): ControlStatePayload {
        val disabledCsv = c.getStringOrEmpty(ControlConfigCols.DISABLED_CATEGORIES_CSV)
        val disabledSet = disabledCsv.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return ControlStatePayload(
            controlConfig = UserAppControlConfig(
                allowNewBookings = c.getIntOrDefault(ControlConfigCols.ALLOW_NEW_BOOKINGS, 1) == 1,
                enableFastestTurn = c.getIntOrDefault(ControlConfigCols.ENABLE_FASTEST_TURN, 1) == 1,
                enableQrCode = c.getIntOrDefault(ControlConfigCols.ENABLE_QR_CODE, 1) == 1,
                enableWhatsAppShare = c.getIntOrDefault(ControlConfigCols.ENABLE_WHATSAPP_SHARE, 1) == 1,
                enableReviews = c.getIntOrDefault(ControlConfigCols.ENABLE_REVIEWS, 1) == 1,
                enableLoyalty = c.getIntOrDefault(ControlConfigCols.ENABLE_LOYALTY, 1) == 1,
                enableOptionalFields = c.getIntOrDefault(ControlConfigCols.ENABLE_OPTIONAL_FIELDS, 1) == 1,
                enableLiveAlerts = c.getIntOrDefault(ControlConfigCols.ENABLE_LIVE_ALERTS, 1) == 1,
                pointsPerBooking = c.getIntOrDefault(ControlConfigCols.POINTS_PER_BOOKING, 10),
                disabledCategories = disabledSet,
                broadcastMessage = c.getStringOrEmpty(ControlConfigCols.BROADCAST_MESSAGE)
            ),
            controlRevision = c.getLongOrDefault(ControlConfigCols.CONTROL_REVISION, 0L),
            queueResetRevision = c.getLongOrDefault(ControlConfigCols.QUEUE_RESET_REVISION, 0L),
            loyaltyRevision = c.getLongOrDefault(ControlConfigCols.LOYALTY_REVISION, 0L),
            loyaltyPoints = c.getIntOrDefault(ControlConfigCols.LOYALTY_POINTS, 60)
        )
    }

    private fun readVenueFromCursor(c: Cursor): VenueEntity = VenueEntity(
        id = c.getIntOrDefault(VenueCols.ID, 1),
        name = c.getStringOrEmpty(VenueCols.NAME),
        city = c.getStringOrEmpty(VenueCols.CITY),
        category = c.getStringOrEmpty(VenueCols.CATEGORY),
        specialty = c.getStringOrEmpty(VenueCols.SPECIALTY),
        area = c.getStringOrEmpty(VenueCols.AREA),
        priceDinar = c.getIntOrDefault(VenueCols.PRICE_DINAR, 15000),
        rating = c.getDoubleOrDefault(VenueCols.RATING, 4.8),
        reviewCount = c.getIntOrDefault(VenueCols.REVIEW_COUNT, 1),
        workingHours = c.getStringOrEmpty(VenueCols.WORKING_HOURS),
        phone = c.getStringOrEmpty(VenueCols.PHONE),
        whatsapp = c.getStringOrEmpty(VenueCols.WHATSAPP),
        latitude = c.getDoubleOrDefault(VenueCols.LATITUDE, 36.3489),
        longitude = c.getDoubleOrDefault(VenueCols.LONGITUDE, 43.1577),
        description = c.getStringOrEmpty(VenueCols.DESCRIPTION),
        customOptionsCsv = c.getStringOrEmpty(VenueCols.CUSTOM_OPTIONS_CSV),
        isOpen = c.getIntOrDefault(VenueCols.IS_OPEN, 1) == 1,
        isPaused = c.getIntOrDefault(VenueCols.IS_PAUSED, 0) == 1,
        isAcceptingBookings = c.getIntOrDefault(VenueCols.IS_ACCEPTING_BOOKINGS, 1) == 1,
        currentServingNumber = c.getIntOrDefault(VenueCols.CURRENT_SERVING_NUMBER, 0),
        nextTicketNumber = c.getIntOrDefault(VenueCols.NEXT_TICKET_NUMBER, 1),
        servedTodayCount = c.getIntOrDefault(VenueCols.SERVED_TODAY_COUNT, 0),
        avgMinutesPerPerson = c.getIntOrDefault(VenueCols.AVG_MINUTES_PER_PERSON, 8),
        isFavorite = false,
        waitingCount = c.getIntOrDefault(VenueCols.WAITING_COUNT, 0)
    )

    private fun readProviderFromCursor(c: Cursor): ServiceProviderEntity = ServiceProviderEntity(
        id = c.getLongOrDefault(ProviderCols.ID, 0L),
        name = c.getStringOrEmpty(ProviderCols.NAME),
        providerRole = c.getStringOrEmpty(ProviderCols.PROVIDER_ROLE),
        customTitle = c.getStringOrEmpty(ProviderCols.CUSTOM_TITLE),
        phone = c.getStringOrEmpty(ProviderCols.PHONE),
        venueId = c.getIntOrDefault(ProviderCols.VENUE_ID, 1),
        venueName = c.getStringOrEmpty(ProviderCols.VENUE_NAME),
        canAcceptTickets = c.getIntOrDefault(ProviderCols.CAN_ACCEPT_TICKETS, 1) == 1,
        canCallNext = c.getIntOrDefault(ProviderCols.CAN_CALL_NEXT, 1) == 1,
        canPauseVenue = c.getIntOrDefault(ProviderCols.CAN_PAUSE_VENUE, 0) == 1,
        isActive = c.getIntOrDefault(ProviderCols.IS_ACTIVE, 1) == 1,
        acceptedCount = c.getIntOrDefault(ProviderCols.ACCEPTED_COUNT, 0),
        createdAt = c.getLongOrDefault(ProviderCols.CREATED_AT, System.currentTimeMillis())
    )

    private fun Cursor.getStringOrEmpty(col: String): String {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getString(idx).orEmpty() else ""
    }

    private fun Cursor.getIntOrDefault(col: String, fallback: Int): Int {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else fallback
    }

    private fun Cursor.getLongOrDefault(col: String, fallback: Long): Long {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else fallback
    }

    private fun Cursor.getDoubleOrDefault(col: String, fallback: Double): Double {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getDouble(idx) else fallback
    }
}
