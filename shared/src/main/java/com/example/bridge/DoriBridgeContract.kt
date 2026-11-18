package com.example.bridge

import android.net.Uri

object DoriBridgeContract {
    const val USER_PACKAGE = "com.aistudio.dori.qrxmzk"
    const val CONTROL_PACKAGE = "com.aistudio.dori.control.qrxmzk"

    const val USER_AUTHORITY = "com.aistudio.dori.user.bridge"
    const val CONTROL_AUTHORITY = "com.aistudio.dori.control.bridge"

    const val PATH_SUPPORT_TICKETS = "support_tickets"
    const val PATH_BOOKINGS = "bookings"
    const val PATH_BOOKING_DECISIONS = "booking_decisions"

    val CONTROL_SUPPORT_URI: Uri = Uri.parse("content://$CONTROL_AUTHORITY/$PATH_SUPPORT_TICKETS")
    val CONTROL_BOOKINGS_URI: Uri = Uri.parse("content://$CONTROL_AUTHORITY/$PATH_BOOKINGS")
    val CONTROL_DECISIONS_URI: Uri = Uri.parse("content://$CONTROL_AUTHORITY/$PATH_BOOKING_DECISIONS")

    val USER_SUPPORT_URI: Uri = Uri.parse("content://$USER_AUTHORITY/$PATH_SUPPORT_TICKETS")
    val USER_BOOKINGS_URI: Uri = Uri.parse("content://$USER_AUTHORITY/$PATH_BOOKINGS")
    val USER_DECISIONS_URI: Uri = Uri.parse("content://$USER_AUTHORITY/$PATH_BOOKING_DECISIONS")

    object SupportCols {
        const val EXTERNAL_ID = "external_id"
        const val SENDER_NAME = "sender_name"
        const val SENDER_PHONE = "sender_phone"
        const val CATEGORY = "category"
        const val SUBJECT = "subject"
        const val MESSAGE = "message"
        const val STATUS = "status"
        const val ADMIN_REPLY = "admin_reply"
        const val REPLIED_BY_ADMIN = "replied_by_admin"
        const val SYNCED_TO_CONTROL = "synced_to_control"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }

    object BookingCols {
        const val TICKET_CODE = "ticket_code"
        const val QUEUE_NUMBER = "queue_number"
        const val VENUE_ID = "venue_id"
        const val VENUE_NAME = "venue_name"
        const val VENUE_CITY = "venue_city"
        const val VENUE_CATEGORY = "venue_category"
        const val VENUE_PHONE = "venue_phone"
        const val CUSTOMER_NAME = "customer_name"
        const val CUSTOMER_PHONE = "customer_phone"
        const val SELECTED_OPTION = "selected_option"
        const val NOTES = "notes"
        const val OPTIONAL_INFO = "optional_info"
        const val PRICE_DINAR = "price_dinar"
        const val STATUS = "status"
        const val STAGE = "stage"
        const val APPROVAL_STATUS = "approval_status"
        const val HANDLED_BY_PROVIDER = "handled_by_provider"
        const val APPROVAL_NOTE = "approval_note"
        const val PEOPLE_AHEAD = "people_ahead"
        const val ESTIMATED_MINUTES = "estimated_minutes"
        const val CREATED_AT = "created_at"
    }
}
