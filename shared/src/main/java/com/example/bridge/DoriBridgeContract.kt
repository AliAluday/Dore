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
    const val PATH_CONTROL_CONFIG = "control_config"
    const val PATH_VENUES = "venues"
    const val PATH_PROVIDERS = "providers"

    val CONTROL_SUPPORT_URI: Uri = Uri.parse("content://$CONTROL_AUTHORITY/$PATH_SUPPORT_TICKETS")
    val CONTROL_BOOKINGS_URI: Uri = Uri.parse("content://$CONTROL_AUTHORITY/$PATH_BOOKINGS")
    val CONTROL_DECISIONS_URI: Uri = Uri.parse("content://$CONTROL_AUTHORITY/$PATH_BOOKING_DECISIONS")
    val CONTROL_CONFIG_URI: Uri = Uri.parse("content://$CONTROL_AUTHORITY/$PATH_CONTROL_CONFIG")
    val CONTROL_VENUES_URI: Uri = Uri.parse("content://$CONTROL_AUTHORITY/$PATH_VENUES")
    val CONTROL_PROVIDERS_URI: Uri = Uri.parse("content://$CONTROL_AUTHORITY/$PATH_PROVIDERS")

    val USER_SUPPORT_URI: Uri = Uri.parse("content://$USER_AUTHORITY/$PATH_SUPPORT_TICKETS")
    val USER_BOOKINGS_URI: Uri = Uri.parse("content://$USER_AUTHORITY/$PATH_BOOKINGS")
    val USER_DECISIONS_URI: Uri = Uri.parse("content://$USER_AUTHORITY/$PATH_BOOKING_DECISIONS")
    val USER_CONFIG_URI: Uri = Uri.parse("content://$USER_AUTHORITY/$PATH_CONTROL_CONFIG")
    val USER_VENUES_URI: Uri = Uri.parse("content://$USER_AUTHORITY/$PATH_VENUES")
    val USER_PROVIDERS_URI: Uri = Uri.parse("content://$USER_AUTHORITY/$PATH_PROVIDERS")

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

    object ControlConfigCols {
        const val ALLOW_NEW_BOOKINGS = "allow_new_bookings"
        const val ENABLE_FASTEST_TURN = "enable_fastest_turn"
        const val ENABLE_QR_CODE = "enable_qr_code"
        const val ENABLE_WHATSAPP_SHARE = "enable_whatsapp_share"
        const val ENABLE_REVIEWS = "enable_reviews"
        const val ENABLE_LOYALTY = "enable_loyalty"
        const val ENABLE_OPTIONAL_FIELDS = "enable_optional_fields"
        const val ENABLE_LIVE_ALERTS = "enable_live_alerts"
        const val POINTS_PER_BOOKING = "points_per_booking"
        const val DISABLED_CATEGORIES_CSV = "disabled_categories_csv"
        const val BROADCAST_MESSAGE = "broadcast_message"
        const val CONTROL_REVISION = "control_revision"
        const val QUEUE_RESET_REVISION = "queue_reset_revision"
        const val LOYALTY_REVISION = "loyalty_revision"
        const val LOYALTY_POINTS = "loyalty_points"
    }

    object VenueCols {
        const val ID = "id"
        const val NAME = "name"
        const val CITY = "city"
        const val CATEGORY = "category"
        const val SPECIALTY = "specialty"
        const val AREA = "area"
        const val PRICE_DINAR = "price_dinar"
        const val RATING = "rating"
        const val REVIEW_COUNT = "review_count"
        const val WORKING_HOURS = "working_hours"
        const val PHONE = "phone"
        const val WHATSAPP = "whatsapp"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        const val DESCRIPTION = "description"
        const val CUSTOM_OPTIONS_CSV = "custom_options_csv"
        const val IS_OPEN = "is_open"
        const val IS_PAUSED = "is_paused"
        const val IS_ACCEPTING_BOOKINGS = "is_accepting_bookings"
        const val CURRENT_SERVING_NUMBER = "current_serving_number"
        const val NEXT_TICKET_NUMBER = "next_ticket_number"
        const val SERVED_TODAY_COUNT = "served_today_count"
        const val AVG_MINUTES_PER_PERSON = "avg_minutes_per_person"
        const val WAITING_COUNT = "waiting_count"
    }

    object ProviderCols {
        const val ID = "id"
        const val NAME = "name"
        const val PROVIDER_ROLE = "provider_role"
        const val CUSTOM_TITLE = "custom_title"
        const val PHONE = "phone"
        const val VENUE_ID = "venue_id"
        const val VENUE_NAME = "venue_name"
        const val CAN_ACCEPT_TICKETS = "can_accept_tickets"
        const val CAN_CALL_NEXT = "can_call_next"
        const val CAN_PAUSE_VENUE = "can_pause_venue"
        const val IS_ACTIVE = "is_active"
        const val ACCEPTED_COUNT = "accepted_count"
        const val CREATED_AT = "created_at"
    }
}
