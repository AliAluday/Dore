package com.example.bridge

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.bridge.DoriBridgeContract.BookingCols
import com.example.bridge.DoriBridgeContract.SupportCols
import com.example.data.local.BookingEntity
import com.example.data.local.SupportTicketEntity
import com.example.data.model.BookingApprovalStatus
import com.example.data.model.SupportTicketStatus

data class BookingDecisionSnapshot(
    val ticketCode: String,
    val approvalStatus: String,
    val handledByProvider: String,
    val approvalNote: String
)

object DoriLocalBridgeClient {

    fun supportToContentValues(ticket: SupportTicketEntity): ContentValues = ContentValues().apply {
        put(SupportCols.EXTERNAL_ID, ticket.externalId)
        put(SupportCols.SENDER_NAME, ticket.senderName)
        put(SupportCols.SENDER_PHONE, ticket.senderPhone)
        put(SupportCols.CATEGORY, ticket.category)
        put(SupportCols.SUBJECT, ticket.subject)
        put(SupportCols.MESSAGE, ticket.message)
        put(SupportCols.STATUS, ticket.status)
        put(SupportCols.ADMIN_REPLY, ticket.adminReply)
        put(SupportCols.REPLIED_BY_ADMIN, ticket.repliedByAdmin)
        put(SupportCols.SYNCED_TO_CONTROL, if (ticket.syncedToControl) 1 else 0)
        put(SupportCols.CREATED_AT, ticket.createdAt)
        put(SupportCols.UPDATED_AT, ticket.updatedAt)
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
        approvalNote: String
    ): Boolean {
        return runCatching {
            val values = ContentValues().apply {
                put(BookingCols.TICKET_CODE, ticketCode)
                put(BookingCols.APPROVAL_STATUS, approvalStatus)
                put(BookingCols.HANDLED_BY_PROVIDER, handledByProvider)
                put(BookingCols.APPROVAL_NOTE, approvalNote)
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
                                    approvalNote = cursor.getStringOrEmpty(BookingCols.APPROVAL_NOTE)
                                )
                            )
                        }
                    }
                }
            list
        }.getOrDefault(emptyList())
    }

    private fun readSupportTicketFromCursor(c: Cursor): SupportTicketEntity = SupportTicketEntity(
        externalId = c.getStringOrEmpty(SupportCols.EXTERNAL_ID),
        senderName = c.getStringOrEmpty(SupportCols.SENDER_NAME),
        senderPhone = c.getStringOrEmpty(SupportCols.SENDER_PHONE),
        category = c.getStringOrEmpty(SupportCols.CATEGORY),
        subject = c.getStringOrEmpty(SupportCols.SUBJECT),
        message = c.getStringOrEmpty(SupportCols.MESSAGE),
        status = c.getStringOrEmpty(SupportCols.STATUS),
        adminReply = c.getStringOrEmpty(SupportCols.ADMIN_REPLY),
        repliedByAdmin = c.getStringOrEmpty(SupportCols.REPLIED_BY_ADMIN),
        syncedToControl = c.getIntOrDefault(SupportCols.SYNCED_TO_CONTROL, 0) == 1,
        createdAt = c.getLongOrDefault(SupportCols.CREATED_AT, System.currentTimeMillis()),
        updatedAt = c.getLongOrDefault(SupportCols.UPDATED_AT, System.currentTimeMillis())
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
}
