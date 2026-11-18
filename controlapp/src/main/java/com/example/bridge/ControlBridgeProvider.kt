package com.example.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.example.bridge.DoriBridgeContract.BookingCols
import com.example.bridge.DoriBridgeContract.ControlConfigCols
import com.example.bridge.DoriBridgeContract.ProviderCols
import com.example.bridge.DoriBridgeContract.SupportCols
import com.example.bridge.DoriBridgeContract.SupportMessageCols
import com.example.bridge.DoriBridgeContract.VenueCols
import com.example.data.local.BookingEntity
import com.example.data.local.DoriDatabase
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.local.SupportMessageEntity
import com.example.data.local.SupportTicketEntity
import com.example.data.local.UserPreferencesDataStore
import com.example.data.model.BookingApprovalStatus
import com.example.data.model.QueueItemStatus
import com.example.data.model.SupportRequestType
import com.example.data.model.SupportTicketStatus
import com.example.util.DoriNotificationHelper
import kotlinx.coroutines.runBlocking

class ControlBridgeProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(DoriBridgeContract.CONTROL_AUTHORITY, DoriBridgeContract.PATH_SUPPORT_TICKETS, CODE_SUPPORT)
        addURI(DoriBridgeContract.CONTROL_AUTHORITY, DoriBridgeContract.PATH_SUPPORT_MESSAGES, CODE_SUPPORT_MESSAGES)
        addURI(DoriBridgeContract.CONTROL_AUTHORITY, DoriBridgeContract.PATH_BOOKINGS, CODE_BOOKINGS)
        addURI(DoriBridgeContract.CONTROL_AUTHORITY, DoriBridgeContract.PATH_BOOKING_DECISIONS, CODE_DECISIONS)
        addURI(DoriBridgeContract.CONTROL_AUTHORITY, DoriBridgeContract.PATH_CONTROL_CONFIG, CODE_CONTROL_CONFIG)
        addURI(DoriBridgeContract.CONTROL_AUTHORITY, DoriBridgeContract.PATH_VENUES, CODE_VENUES)
        addURI(DoriBridgeContract.CONTROL_AUTHORITY, DoriBridgeContract.PATH_PROVIDERS, CODE_PROVIDERS)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val dao = DoriDatabase.getInstance(ctx).doriDao()

        return when (matcher.match(uri)) {
            CODE_SUPPORT -> {
                val cols = arrayOf(
                    SupportCols.EXTERNAL_ID,
                    SupportCols.SENDER_NAME,
                    SupportCols.SENDER_PHONE,
                    SupportCols.CATEGORY,
                    SupportCols.REQUEST_TYPE,
                    SupportCols.RELATED_VENUE_NAME,
                    SupportCols.SUBJECT,
                    SupportCols.MESSAGE,
                    SupportCols.STATUS,
                    SupportCols.ADMIN_REPLY,
                    SupportCols.REPLIED_BY_ADMIN,
                    SupportCols.SYNCED_TO_CONTROL,
                    SupportCols.CREATED_AT,
                    SupportCols.UPDATED_AT
                )
                MatrixCursor(cols).apply {
                    val tickets = runBlocking { dao.getAllSupportTicketsOnce() }
                    tickets.forEach { t ->
                        addRow(
                            arrayOf<Any?>(
                                t.externalId,
                                t.senderName,
                                t.senderPhone,
                                t.category,
                                t.requestType,
                                t.relatedVenueName,
                                t.subject,
                                t.message,
                                t.status,
                                t.adminReply,
                                t.repliedByAdmin,
                                if (t.syncedToControl) 1 else 0,
                                t.createdAt,
                                t.updatedAt
                            )
                        )
                    }
                }
            }

            CODE_SUPPORT_MESSAGES -> {
                val cols = arrayOf(
                    SupportMessageCols.EXTERNAL_MESSAGE_ID,
                    SupportMessageCols.TICKET_EXTERNAL_ID,
                    SupportMessageCols.IS_FROM_ADMIN,
                    SupportMessageCols.SENDER_NAME,
                    SupportMessageCols.BODY,
                    SupportMessageCols.SYNCED_TO_PEER,
                    SupportMessageCols.CREATED_AT
                )
                MatrixCursor(cols).apply {
                    val messages = runBlocking { dao.getAllSupportMessagesOnce() }
                    messages.forEach { m ->
                        addRow(
                            arrayOf<Any?>(
                                m.externalMessageId,
                                m.ticketExternalId,
                                if (m.isFromAdmin) 1 else 0,
                                m.senderName,
                                m.body,
                                if (m.syncedToPeer) 1 else 0,
                                m.createdAt
                            )
                        )
                    }
                }
            }

            CODE_DECISIONS -> {
                val cols = arrayOf(
                    BookingCols.TICKET_CODE,
                    BookingCols.APPROVAL_STATUS,
                    BookingCols.HANDLED_BY_PROVIDER,
                    BookingCols.APPROVAL_NOTE,
                    BookingCols.STATUS,
                    BookingCols.STAGE,
                    BookingCols.PEOPLE_AHEAD,
                    BookingCols.ESTIMATED_MINUTES
                )
                MatrixCursor(cols).apply {
                    val bookings = runBlocking { dao.getAllBookingsOnce() }
                    bookings.forEach { b ->
                        addRow(
                            arrayOf<Any?>(
                                b.ticketCode,
                                b.approvalStatus,
                                b.handledByProvider,
                                b.approvalNote,
                                b.status,
                                b.stage,
                                b.peopleAhead,
                                b.estimatedMinutes
                            )
                        )
                    }
                }
            }

            CODE_CONTROL_CONFIG -> {
                val cols = arrayOf(
                    ControlConfigCols.ALLOW_NEW_BOOKINGS,
                    ControlConfigCols.ENABLE_FASTEST_TURN,
                    ControlConfigCols.ENABLE_QR_CODE,
                    ControlConfigCols.ENABLE_WHATSAPP_SHARE,
                    ControlConfigCols.ENABLE_REVIEWS,
                    ControlConfigCols.ENABLE_LOYALTY,
                    ControlConfigCols.ENABLE_OPTIONAL_FIELDS,
                    ControlConfigCols.ENABLE_LIVE_ALERTS,
                    ControlConfigCols.POINTS_PER_BOOKING,
                    ControlConfigCols.DISABLED_CATEGORIES_CSV,
                    ControlConfigCols.BROADCAST_MESSAGE,
                    ControlConfigCols.CONTROL_REVISION,
                    ControlConfigCols.QUEUE_RESET_REVISION,
                    ControlConfigCols.LOYALTY_REVISION,
                    ControlConfigCols.LOYALTY_POINTS
                )
                val meta = runBlocking { UserPreferencesDataStore(ctx).getControlSyncMetadata() }
                val cfg = meta.controlConfig
                MatrixCursor(cols).apply {
                    addRow(
                        arrayOf<Any?>(
                            if (cfg.allowNewBookings) 1 else 0,
                            if (cfg.enableFastestTurn) 1 else 0,
                            if (cfg.enableQrCode) 1 else 0,
                            if (cfg.enableWhatsAppShare) 1 else 0,
                            if (cfg.enableReviews) 1 else 0,
                            if (cfg.enableLoyalty) 1 else 0,
                            if (cfg.enableOptionalFields) 1 else 0,
                            if (cfg.enableLiveAlerts) 1 else 0,
                            cfg.pointsPerBooking,
                            cfg.disabledCategories.joinToString("\n"),
                            cfg.broadcastMessage,
                            meta.controlRevision,
                            meta.queueResetRevision,
                            meta.loyaltyRevision,
                            meta.loyaltyPoints
                        )
                    )
                }
            }

            CODE_VENUES -> {
                val cols = arrayOf(
                    VenueCols.ID,
                    VenueCols.NAME,
                    VenueCols.CITY,
                    VenueCols.CATEGORY,
                    VenueCols.SPECIALTY,
                    VenueCols.AREA,
                    VenueCols.PRICE_DINAR,
                    VenueCols.RATING,
                    VenueCols.REVIEW_COUNT,
                    VenueCols.WORKING_HOURS,
                    VenueCols.PHONE,
                    VenueCols.WHATSAPP,
                    VenueCols.LATITUDE,
                    VenueCols.LONGITUDE,
                    VenueCols.DESCRIPTION,
                    VenueCols.CUSTOM_OPTIONS_CSV,
                    VenueCols.IS_OPEN,
                    VenueCols.IS_PAUSED,
                    VenueCols.IS_ACCEPTING_BOOKINGS,
                    VenueCols.CURRENT_SERVING_NUMBER,
                    VenueCols.NEXT_TICKET_NUMBER,
                    VenueCols.SERVED_TODAY_COUNT,
                    VenueCols.AVG_MINUTES_PER_PERSON,
                    VenueCols.WAITING_COUNT
                )
                MatrixCursor(cols).apply {
                    val venues = runBlocking { dao.getAllVenuesOnce() }
                    venues.forEach { v ->
                        addRow(
                            arrayOf<Any?>(
                                v.id,
                                v.name,
                                v.city,
                                v.category,
                                v.specialty,
                                v.area,
                                v.priceDinar,
                                v.rating,
                                v.reviewCount,
                                v.workingHours,
                                v.phone,
                                v.whatsapp,
                                v.latitude,
                                v.longitude,
                                v.description,
                                v.customOptionsCsv,
                                if (v.isOpen) 1 else 0,
                                if (v.isPaused) 1 else 0,
                                if (v.isAcceptingBookings) 1 else 0,
                                v.currentServingNumber,
                                v.nextTicketNumber,
                                v.servedTodayCount,
                                v.avgMinutesPerPerson,
                                v.waitingCount
                            )
                        )
                    }
                }
            }

            CODE_PROVIDERS -> {
                val cols = arrayOf(
                    ProviderCols.ID,
                    ProviderCols.NAME,
                    ProviderCols.PROVIDER_ROLE,
                    ProviderCols.CUSTOM_TITLE,
                    ProviderCols.PHONE,
                    ProviderCols.VENUE_ID,
                    ProviderCols.VENUE_NAME,
                    ProviderCols.CAN_ACCEPT_TICKETS,
                    ProviderCols.CAN_CALL_NEXT,
                    ProviderCols.CAN_PAUSE_VENUE,
                    ProviderCols.IS_ACTIVE,
                    ProviderCols.ACCEPTED_COUNT,
                    ProviderCols.CREATED_AT
                )
                MatrixCursor(cols).apply {
                    val providers = runBlocking { dao.getAllProvidersOnce() }
                    providers.forEach { p ->
                        addRow(
                            arrayOf<Any?>(
                                p.id,
                                p.name,
                                p.providerRole,
                                p.customTitle,
                                p.phone,
                                p.venueId,
                                p.venueName,
                                if (p.canAcceptTickets) 1 else 0,
                                if (p.canCallNext) 1 else 0,
                                if (p.canPauseVenue) 1 else 0,
                                if (p.isActive) 1 else 0,
                                p.acceptedCount,
                                p.createdAt
                            )
                        )
                    }
                }
            }

            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val ctx = context ?: return null
        val v = values ?: return null
        val dao = DoriDatabase.getInstance(ctx).doriDao()

        return when (matcher.match(uri)) {
            CODE_SUPPORT -> {
                val externalId = v.getAsString(SupportCols.EXTERNAL_ID)?.takeIf { it.isNotBlank() }
                    ?: return null
                val senderName = v.getAsString(SupportCols.SENDER_NAME).orEmpty().ifBlank { "مستخدم دوري" }
                val senderPhone = v.getAsString(SupportCols.SENDER_PHONE).orEmpty()
                val category = v.getAsString(SupportCols.CATEGORY).orEmpty()
                val reqType = v.getAsString(SupportCols.REQUEST_TYPE).orEmpty().ifBlank { SupportRequestType.CHAT.name }
                val venueName = v.getAsString(SupportCols.RELATED_VENUE_NAME).orEmpty()
                val subject = v.getAsString(SupportCols.SUBJECT).orEmpty().ifBlank { "رسالة دعم فني" }
                val message = v.getAsString(SupportCols.MESSAGE).orEmpty()
                val incomingStatus = v.getAsString(SupportCols.STATUS).orEmpty()
                val now = v.getAsLong(SupportCols.UPDATED_AT) ?: System.currentTimeMillis()
                var shouldNotify = false

                runBlocking {
                    val existing = dao.getSupportTicketByExternalId(externalId)
                    if (existing != null) {
                        val nextStatus = if (incomingStatus == SupportTicketStatus.SENT.name ||
                            incomingStatus == SupportTicketStatus.DELIVERED.name
                        ) {
                            SupportTicketStatus.DELIVERED.name
                        } else {
                            incomingStatus.ifBlank { existing.status }
                        }
                        shouldNotify = now > existing.updatedAt
                        dao.updateSupportTicket(
                            existing.copy(
                                senderName = senderName,
                                senderPhone = senderPhone,
                                category = category,
                                requestType = reqType,
                                relatedVenueName = venueName.ifBlank { existing.relatedVenueName },
                                subject = subject,
                                message = message,
                                status = nextStatus,
                                syncedToControl = true,
                                updatedAt = maxOf(existing.updatedAt, now)
                            )
                        )
                    } else {
                        shouldNotify = true
                        dao.insertSupportTicket(
                            SupportTicketEntity(
                                externalId = externalId,
                                senderName = senderName,
                                senderPhone = senderPhone,
                                category = category,
                                requestType = reqType,
                                relatedVenueName = venueName,
                                subject = subject,
                                message = message,
                                status = SupportTicketStatus.DELIVERED.name,
                                syncedToControl = true,
                                createdAt = v.getAsLong(SupportCols.CREATED_AT) ?: now,
                                updatedAt = now
                            )
                        )
                    }
                }

                if (shouldNotify) {
                    val badgeTitle = if (reqType == SupportRequestType.COMPLAINT.name) {
                        "📩 طلب شكوى جديد من $senderName"
                    } else {
                        "📩 رسالة دعم فني من $senderName"
                    }
                    DoriNotificationHelper.showSupportMessageNotification(
                        context = ctx,
                        title = badgeTitle,
                        body = "$subject — $message",
                        idSeed = kotlin.math.abs(externalId.hashCode() % 3000) + 3500
                    )
                }
                Uri.withAppendedPath(DoriBridgeContract.CONTROL_SUPPORT_URI, externalId)
            }

            CODE_SUPPORT_MESSAGES -> {
                val extMsgId = v.getAsString(SupportMessageCols.EXTERNAL_MESSAGE_ID)?.takeIf { it.isNotBlank() }
                    ?: return null
                val ticketExtId = v.getAsString(SupportMessageCols.TICKET_EXTERNAL_ID)?.takeIf { it.isNotBlank() }
                    ?: return null
                val isFromAdmin = (v.getAsInteger(SupportMessageCols.IS_FROM_ADMIN) ?: 0) == 1
                val senderName = v.getAsString(SupportMessageCols.SENDER_NAME).orEmpty().ifBlank { "مستخدم دوري" }
                val body = v.getAsString(SupportMessageCols.BODY).orEmpty()
                val createdAt = v.getAsLong(SupportMessageCols.CREATED_AT) ?: System.currentTimeMillis()
                var insertedNew = false
                runBlocking {
                    if (dao.getSupportMessageByExternalId(extMsgId) == null) {
                        dao.insertSupportMessage(
                            SupportMessageEntity(
                                externalMessageId = extMsgId,
                                ticketExternalId = ticketExtId,
                                isFromAdmin = isFromAdmin,
                                senderName = senderName,
                                body = body,
                                syncedToPeer = true,
                                createdAt = createdAt
                            )
                        )
                        insertedNew = true
                    }
                }
                if (insertedNew && !isFromAdmin && body.isNotBlank()) {
                    DoriNotificationHelper.showSupportMessageNotification(
                        context = ctx,
                        title = "💬 رسالة متابعة من $senderName",
                        body = body,
                        idSeed = kotlin.math.abs(extMsgId.hashCode() % 3000) + 3550
                    )
                }
                Uri.withAppendedPath(DoriBridgeContract.CONTROL_SUPPORT_MESSAGES_URI, extMsgId)
            }

            CODE_BOOKINGS -> {
                val ticketCode = v.getAsString(BookingCols.TICKET_CODE)?.takeIf { it.isNotBlank() }
                    ?: return null
                val venueId = v.getAsInteger(BookingCols.VENUE_ID) ?: 1
                val queueNumber = v.getAsInteger(BookingCols.QUEUE_NUMBER) ?: 1
                val customerName = v.getAsString(BookingCols.CUSTOMER_NAME).orEmpty()
                val customerPhone = v.getAsString(BookingCols.CUSTOMER_PHONE).orEmpty()
                val selectedOption = v.getAsString(BookingCols.SELECTED_OPTION).orEmpty()
                val peopleAhead = v.getAsInteger(BookingCols.PEOPLE_AHEAD) ?: 0

                runBlocking {
                    val existing = dao.getBookingByTicketCode(ticketCode)
                    if (existing == null) {
                        val bId = dao.insertBooking(
                            BookingEntity(
                                ticketCode = ticketCode,
                                queueNumber = queueNumber,
                                venueId = venueId,
                                venueName = v.getAsString(BookingCols.VENUE_NAME).orEmpty(),
                                venueCity = v.getAsString(BookingCols.VENUE_CITY).orEmpty(),
                                venueCategory = v.getAsString(BookingCols.VENUE_CATEGORY).orEmpty(),
                                venuePhone = v.getAsString(BookingCols.VENUE_PHONE).orEmpty(),
                                customerName = customerName,
                                customerPhone = customerPhone,
                                selectedOption = selectedOption,
                                notes = v.getAsString(BookingCols.NOTES).orEmpty(),
                                optionalInfo = v.getAsString(BookingCols.OPTIONAL_INFO).orEmpty(),
                                priceDinar = v.getAsInteger(BookingCols.PRICE_DINAR) ?: 0,
                                status = v.getAsString(BookingCols.STATUS).orEmpty(),
                                stage = v.getAsString(BookingCols.STAGE).orEmpty(),
                                approvalStatus = v.getAsString(BookingCols.APPROVAL_STATUS).orEmpty()
                                    .ifBlank { BookingApprovalStatus.PENDING.name },
                                handledByProvider = v.getAsString(BookingCols.HANDLED_BY_PROVIDER).orEmpty(),
                                approvalNote = v.getAsString(BookingCols.APPROVAL_NOTE).orEmpty(),
                                peopleAhead = peopleAhead,
                                estimatedMinutes = v.getAsInteger(BookingCols.ESTIMATED_MINUTES) ?: 0,
                                createdAt = v.getAsLong(BookingCols.CREATED_AT) ?: System.currentTimeMillis()
                            )
                        )
                        dao.insertQueueItem(
                            MerchantQueueItemEntity(
                                venueId = venueId,
                                ticketNumber = queueNumber,
                                ticketCode = ticketCode,
                                customerName = customerName,
                                customerPhone = customerPhone,
                                selectedOption = selectedOption,
                                status = if (peopleAhead == 0) QueueItemStatus.SERVING.name else QueueItemStatus.WAITING.name,
                                bookingId = bId
                            )
                        )
                        dao.getVenueById(venueId)?.let { venue ->
                            dao.updateVenue(
                                venue.copy(
                                    nextTicketNumber = maxOf(venue.nextTicketNumber, queueNumber + 1),
                                    waitingCount = if (peopleAhead > 0) venue.waitingCount + 1 else venue.waitingCount,
                                    currentServingNumber = if (peopleAhead == 0 && venue.currentServingNumber == 0) {
                                        queueNumber
                                    } else {
                                        venue.currentServingNumber
                                    }
                                )
                            )
                        }
                    }
                }
                DoriNotificationHelper.showSupportMessageNotification(
                    context = ctx,
                    title = "🎫 حجز وارد جديد ($ticketCode)",
                    body = "$customerName — بانتظار قبول مقدم الخدمة",
                    idSeed = kotlin.math.abs(ticketCode.hashCode() % 3000) + 5000
                )
                Uri.withAppendedPath(DoriBridgeContract.CONTROL_BOOKINGS_URI, ticketCode)
            }

            else -> null
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = null

    private companion object {
        const val CODE_SUPPORT = 1
        const val CODE_BOOKINGS = 2
        const val CODE_DECISIONS = 3
        const val CODE_CONTROL_CONFIG = 4
        const val CODE_VENUES = 5
        const val CODE_PROVIDERS = 6
        const val CODE_SUPPORT_MESSAGES = 7
    }
}
