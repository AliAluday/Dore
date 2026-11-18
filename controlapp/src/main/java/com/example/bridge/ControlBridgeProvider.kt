package com.example.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.example.bridge.DoriBridgeContract.BookingCols
import com.example.bridge.DoriBridgeContract.SupportCols
import com.example.data.local.BookingEntity
import com.example.data.local.DoriDatabase
import com.example.data.local.MerchantQueueItemEntity
import com.example.data.local.SupportTicketEntity
import com.example.data.model.BookingApprovalStatus
import com.example.data.model.QueueItemStatus
import com.example.data.model.SupportTicketStatus
import com.example.util.DoriNotificationHelper
import kotlinx.coroutines.runBlocking

class ControlBridgeProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(DoriBridgeContract.CONTROL_AUTHORITY, DoriBridgeContract.PATH_SUPPORT_TICKETS, CODE_SUPPORT)
        addURI(DoriBridgeContract.CONTROL_AUTHORITY, DoriBridgeContract.PATH_BOOKINGS, CODE_BOOKINGS)
        addURI(DoriBridgeContract.CONTROL_AUTHORITY, DoriBridgeContract.PATH_BOOKING_DECISIONS, CODE_DECISIONS)
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

            CODE_DECISIONS -> {
                val cols = arrayOf(
                    BookingCols.TICKET_CODE,
                    BookingCols.APPROVAL_STATUS,
                    BookingCols.HANDLED_BY_PROVIDER,
                    BookingCols.APPROVAL_NOTE
                )
                MatrixCursor(cols).apply {
                    val bookings = runBlocking { dao.getAllBookingsOnce() }
                    bookings.forEach { b ->
                        addRow(
                            arrayOf<Any?>(
                                b.ticketCode,
                                b.approvalStatus,
                                b.handledByProvider,
                                b.approvalNote
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
                val subject = v.getAsString(SupportCols.SUBJECT).orEmpty().ifBlank { "رسالة دعم فني" }
                val message = v.getAsString(SupportCols.MESSAGE).orEmpty()
                val now = System.currentTimeMillis()

                runBlocking {
                    val existing = dao.getSupportTicketByExternalId(externalId)
                    if (existing != null) {
                        dao.updateSupportTicket(
                            existing.copy(
                                senderName = senderName,
                                senderPhone = senderPhone,
                                category = category,
                                subject = subject,
                                message = message,
                                syncedToControl = true,
                                updatedAt = now
                            )
                        )
                    } else {
                        dao.insertSupportTicket(
                            SupportTicketEntity(
                                externalId = externalId,
                                senderName = senderName,
                                senderPhone = senderPhone,
                                category = category,
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

                DoriNotificationHelper.showSupportMessageNotification(
                    context = ctx,
                    title = "📩 رسالة دعم فني جديدة من $senderName",
                    body = "$subject — $message",
                    idSeed = kotlin.math.abs(externalId.hashCode() % 3000) + 3500
                )
                Uri.withAppendedPath(DoriBridgeContract.CONTROL_SUPPORT_URI, externalId)
            }

            CODE_BOOKINGS -> {
                val ticketCode = v.getAsString(BookingCols.TICKET_CODE)?.takeIf { it.isNotBlank() }
                    ?: return null
                val venueId = v.getAsInteger(BookingCols.VENUE_ID) ?: 1
                val queueNumber = v.getAsInteger(BookingCols.QUEUE_NUMBER) ?: 1
                val customerName = v.getAsString(BookingCols.CUSTOMER_NAME).orEmpty()
                val customerPhone = v.getAsString(BookingCols.CUSTOMER_PHONE).orEmpty()
                val selectedOption = v.getAsString(BookingCols.SELECTED_OPTION).orEmpty()

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
                                peopleAhead = v.getAsInteger(BookingCols.PEOPLE_AHEAD) ?: 0,
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
                                status = QueueItemStatus.WAITING.name,
                                bookingId = bId
                            )
                        )
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
    }
}
