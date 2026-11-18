package com.example.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.example.bridge.DoriBridgeContract.BookingCols
import com.example.bridge.DoriBridgeContract.SupportCols
import com.example.bridge.DoriBridgeContract.SupportMessageCols
import com.example.data.local.DoriDatabase
import com.example.data.local.SupportMessageEntity
import com.example.data.local.SupportTicketEntity
import com.example.data.local.UserPreferencesDataStore
import com.example.data.model.BookingApprovalStatus
import com.example.data.model.BookingStatus
import com.example.data.model.SupportRequestType
import com.example.data.model.SupportTicketStatus
import com.example.data.repository.DoriRepository
import com.example.util.DoriNotificationHelper
import kotlinx.coroutines.runBlocking

class UserBridgeProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(DoriBridgeContract.USER_AUTHORITY, DoriBridgeContract.PATH_SUPPORT_TICKETS, CODE_SUPPORT)
        addURI(DoriBridgeContract.USER_AUTHORITY, DoriBridgeContract.PATH_SUPPORT_MESSAGES, CODE_SUPPORT_MESSAGES)
        addURI(DoriBridgeContract.USER_AUTHORITY, DoriBridgeContract.PATH_BOOKINGS, CODE_BOOKINGS)
        addURI(DoriBridgeContract.USER_AUTHORITY, DoriBridgeContract.PATH_BOOKING_DECISIONS, CODE_DECISIONS)
        addURI(DoriBridgeContract.USER_AUTHORITY, DoriBridgeContract.PATH_CONTROL_CONFIG, CODE_CONTROL_CONFIG)
        addURI(DoriBridgeContract.USER_AUTHORITY, DoriBridgeContract.PATH_VENUES, CODE_VENUES)
        addURI(DoriBridgeContract.USER_AUTHORITY, DoriBridgeContract.PATH_PROVIDERS, CODE_PROVIDERS)
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

            CODE_BOOKINGS -> {
                val cols = arrayOf(
                    BookingCols.TICKET_CODE,
                    BookingCols.QUEUE_NUMBER,
                    BookingCols.VENUE_ID,
                    BookingCols.VENUE_NAME,
                    BookingCols.VENUE_CITY,
                    BookingCols.VENUE_CATEGORY,
                    BookingCols.VENUE_PHONE,
                    BookingCols.CUSTOMER_NAME,
                    BookingCols.CUSTOMER_PHONE,
                    BookingCols.SELECTED_OPTION,
                    BookingCols.NOTES,
                    BookingCols.OPTIONAL_INFO,
                    BookingCols.PRICE_DINAR,
                    BookingCols.STATUS,
                    BookingCols.STAGE,
                    BookingCols.APPROVAL_STATUS,
                    BookingCols.HANDLED_BY_PROVIDER,
                    BookingCols.APPROVAL_NOTE,
                    BookingCols.PEOPLE_AHEAD,
                    BookingCols.ESTIMATED_MINUTES,
                    BookingCols.CREATED_AT
                )
                MatrixCursor(cols).apply {
                    val bookings = runBlocking { dao.getActiveBookingsOnce() }
                    bookings.forEach { b ->
                        addRow(
                            arrayOf<Any?>(
                                b.ticketCode,
                                b.queueNumber,
                                b.venueId,
                                b.venueName,
                                b.venueCity,
                                b.venueCategory,
                                b.venuePhone,
                                b.customerName,
                                b.customerPhone,
                                b.selectedOption,
                                b.notes,
                                b.optionalInfo,
                                b.priceDinar,
                                b.status,
                                b.stage,
                                b.approvalStatus,
                                b.handledByProvider,
                                b.approvalNote,
                                b.peopleAhead,
                                b.estimatedMinutes,
                                b.createdAt
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
                val adminReply = v.getAsString(SupportCols.ADMIN_REPLY).orEmpty()
                val repliedBy = v.getAsString(SupportCols.REPLIED_BY_ADMIN).orEmpty()
                val incomingStatus = v.getAsString(SupportCols.STATUS).orEmpty()
                    .ifBlank { SupportTicketStatus.REPLIED.name }
                val updatedAt = v.getAsLong(SupportCols.UPDATED_AT) ?: System.currentTimeMillis()
                val reqType = v.getAsString(SupportCols.REQUEST_TYPE).orEmpty().ifBlank { SupportRequestType.CHAT.name }
                val venueName = v.getAsString(SupportCols.RELATED_VENUE_NAME).orEmpty()

                var shouldNotifyReply = false
                runBlocking {
                    val existing = dao.getSupportTicketByExternalId(externalId)
                    if (existing != null) {
                        shouldNotifyReply = adminReply.isNotBlank() &&
                            (adminReply != existing.adminReply || updatedAt > existing.updatedAt)
                        dao.updateSupportTicket(
                            existing.copy(
                                status = incomingStatus,
                                requestType = reqType.ifBlank { existing.requestType },
                                relatedVenueName = venueName.ifBlank { existing.relatedVenueName },
                                adminReply = adminReply.ifBlank { existing.adminReply },
                                repliedByAdmin = repliedBy.ifBlank { existing.repliedByAdmin },
                                syncedToControl = true,
                                updatedAt = maxOf(existing.updatedAt, updatedAt)
                            )
                        )
                    } else {
                        shouldNotifyReply = adminReply.isNotBlank()
                        dao.insertSupportTicket(
                            SupportTicketEntity(
                                externalId = externalId,
                                senderName = v.getAsString(SupportCols.SENDER_NAME).orEmpty(),
                                senderPhone = v.getAsString(SupportCols.SENDER_PHONE).orEmpty(),
                                category = v.getAsString(SupportCols.CATEGORY).orEmpty(),
                                requestType = reqType,
                                relatedVenueName = venueName,
                                subject = v.getAsString(SupportCols.SUBJECT).orEmpty(),
                                message = v.getAsString(SupportCols.MESSAGE).orEmpty(),
                                status = incomingStatus,
                                adminReply = adminReply,
                                repliedByAdmin = repliedBy,
                                syncedToControl = true,
                                createdAt = v.getAsLong(SupportCols.CREATED_AT) ?: System.currentTimeMillis(),
                                updatedAt = updatedAt
                            )
                        )
                    }
                }
                if (shouldNotifyReply) {
                    DoriNotificationHelper.showSupportMessageNotification(
                        context = ctx,
                        title = "💬 رد جديد من الدعم الفني",
                        body = if (repliedBy.isNotBlank()) "$repliedBy: $adminReply" else adminReply,
                        idSeed = kotlin.math.abs(externalId.hashCode() % 3000) + 3100
                    )
                }
                Uri.withAppendedPath(DoriBridgeContract.USER_SUPPORT_URI, externalId)
            }

            CODE_SUPPORT_MESSAGES -> {
                val extMsgId = v.getAsString(SupportMessageCols.EXTERNAL_MESSAGE_ID)?.takeIf { it.isNotBlank() }
                    ?: return null
                val ticketExtId = v.getAsString(SupportMessageCols.TICKET_EXTERNAL_ID)?.takeIf { it.isNotBlank() }
                    ?: return null
                val isFromAdmin = (v.getAsInteger(SupportMessageCols.IS_FROM_ADMIN) ?: 0) == 1
                val senderName = v.getAsString(SupportMessageCols.SENDER_NAME).orEmpty()
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
                if (insertedNew && isFromAdmin && body.isNotBlank()) {
                    DoriNotificationHelper.showSupportMessageNotification(
                        context = ctx,
                        title = "💬 رسالة جديدة من الدعم الفني",
                        body = if (senderName.isNotBlank()) "$senderName: $body" else body,
                        idSeed = kotlin.math.abs(extMsgId.hashCode() % 3000) + 3150
                    )
                }
                Uri.withAppendedPath(DoriBridgeContract.USER_SUPPORT_MESSAGES_URI, extMsgId)
            }

            CODE_DECISIONS -> {
                val ticketCode = v.getAsString(BookingCols.TICKET_CODE)?.takeIf { it.isNotBlank() }
                    ?: return null
                val approvalStatus = v.getAsString(BookingCols.APPROVAL_STATUS).orEmpty()
                    .ifBlank { BookingApprovalStatus.PENDING.name }
                val handledBy = v.getAsString(BookingCols.HANDLED_BY_PROVIDER).orEmpty()
                val note = v.getAsString(BookingCols.APPROVAL_NOTE).orEmpty()
                val status = v.getAsString(BookingCols.STATUS).orEmpty()
                val stage = v.getAsString(BookingCols.STAGE).orEmpty()
                val peopleAhead = v.getAsInteger(BookingCols.PEOPLE_AHEAD) ?: -1
                val estimatedMinutes = v.getAsInteger(BookingCols.ESTIMATED_MINUTES) ?: -1

                var shouldNotifyDecision = false
                val found = runBlocking {
                    val b = dao.getBookingByTicketCode(ticketCode)
                    if (b != null) {
                        shouldNotifyDecision = b.approvalStatus != approvalStatus &&
                            (approvalStatus == BookingApprovalStatus.ACCEPTED.name ||
                                approvalStatus == BookingApprovalStatus.REJECTED.name)
                        val nextStatus = status.takeIf { it.isNotBlank() } ?: b.status
                        dao.updateBooking(
                            b.copy(
                                approvalStatus = approvalStatus,
                                handledByProvider = handledBy.ifBlank { b.handledByProvider },
                                approvalNote = note.ifBlank { b.approvalNote },
                                status = nextStatus,
                                stage = stage.takeIf { it.isNotBlank() } ?: b.stage,
                                peopleAhead = if (peopleAhead >= 0) peopleAhead else b.peopleAhead,
                                estimatedMinutes = if (estimatedMinutes >= 0) estimatedMinutes else b.estimatedMinutes,
                                completedAt = if (nextStatus != BookingStatus.ACTIVE.name && b.completedAt == null) {
                                    System.currentTimeMillis()
                                } else {
                                    b.completedAt
                                }
                            )
                        )
                        true
                    } else {
                        false
                    }
                }
                if (found) {
                    if (shouldNotifyDecision) {
                        DoriNotificationHelper.showTicketDecisionNotification(
                            context = ctx,
                            ticketCode = ticketCode,
                            accepted = approvalStatus == BookingApprovalStatus.ACCEPTED.name,
                            providerName = handledBy.ifBlank { "مقدم الخدمة" }
                        )
                    }
                    Uri.withAppendedPath(DoriBridgeContract.USER_DECISIONS_URI, ticketCode)
                } else {
                    null
                }
            }

            CODE_CONTROL_CONFIG -> {
                val payload = DoriLocalBridgeClient.controlStateFromContentValues(v)
                runBlocking {
                    UserPreferencesDataStore(ctx).applySyncedControlStateFromBridge(
                        controlConfig = payload.controlConfig,
                        controlRevision = payload.controlRevision,
                        queueResetRevision = payload.queueResetRevision,
                        loyaltyRevision = payload.loyaltyRevision,
                        loyaltyPointsFromControl = payload.loyaltyPoints
                    )
                }
                Uri.withAppendedPath(DoriBridgeContract.USER_CONFIG_URI, payload.controlRevision.toString())
            }

            else -> null
        }
    }

    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int {
        val ctx = context ?: return 0
        val dao = DoriDatabase.getInstance(ctx).doriDao()
        val prefs = UserPreferencesDataStore(ctx)
        val repo = DoriRepository(dao, prefs)

        return when (matcher.match(uri)) {
            CODE_VENUES -> {
                val venues = values.mapNotNull { DoriLocalBridgeClient.venueFromContentValues(it) }
                val looksReset = venues.isNotEmpty() && venues.all {
                    it.nextTicketNumber == 1 && it.currentServingNumber == 0 && it.waitingCount == 0
                }
                runBlocking {
                    repo.mergeSyncedVenuesFromControl(venues, queueResetChanged = looksReset)
                }
                venues.size
            }

            CODE_PROVIDERS -> {
                val providers = values.mapNotNull { DoriLocalBridgeClient.providerFromContentValues(it) }
                runBlocking {
                    repo.mergeSyncedProvidersFromControl(providers)
                }
                providers.size
            }

            else -> super.bulkInsert(uri, values)
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val ctx = context ?: return 0
        val dao = DoriDatabase.getInstance(ctx).doriDao()
        return when (matcher.match(uri)) {
            CODE_VENUES -> {
                runBlocking { dao.deleteAllVenues() }
                1
            }

            CODE_PROVIDERS -> {
                runBlocking { dao.deleteAllProviders() }
                1
            }

            else -> 0
        }
    }

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
