package com.example.data.model

enum class IraqiCity(val displayName: String, val code: String) {
    BAGHDAD("بغداد", "BGW"),
    MOSUL("الموصل", "OSM"),
    ERBIL("أربيل", "EBL"),
    BASRA("البصرة", "BSR"),
    NAJAF("النجف", "NJF");

    companion object {
        fun fromName(name: String): IraqiCity =
            entries.find { it.displayName == name || it.name == name } ?: BAGHDAD
    }
}

enum class ServiceCategory(val displayName: String) {
    ALL("الكل"),
    CLINICS("عيادات"),
    MAINTENANCE("صيانة"),
    BEAUTY("تجميل"),
    FIELDS("ملاعب"),
    LABS("مختبرات");

    companion object {
        val fiveCategories = listOf(CLINICS, MAINTENANCE, BEAUTY, FIELDS, LABS)
        fun fromName(name: String): ServiceCategory =
            entries.find { it.displayName == name || it.name == name } ?: CLINICS
    }
}

enum class SortOption(val displayName: String) {
    HIGHEST_RATED("الأعلى تقييمًا"),
    SHORTEST_WAIT("الأقل انتظارًا"),
    SERVICE_PRICE("حسب الخدمة والسعر")
}

enum class TicketStage(val stepIndex: Int, val label: String) {
    MONITORED(0, "تمت المراقبة"),
    APPROACHING(1, "اقترب دورك"),
    YOUR_TURN(2, "حان دورك")
}

enum class BookingStatus(val label: String) {
    ACTIVE("نشط"),
    COMPLETED("مكتمل"),
    CANCELLED("ملغى")
}

enum class QueueItemStatus(val label: String) {
    WAITING("بالانتظار"),
    SERVING("قيد الخدمة"),
    DONE("مكتمل"),
    SKIPPED("تم التخطي"),
    NO_SHOW("توقف عن الحضور")
}

enum class LoyaltyTier(
    val title: String,
    val minPoints: Int,
    val nextTierPoints: Int?,
    val perkDescription: String
) {
    SILVER("فضية", 0, 100, "أولوية إشعارات فورية وتذكرة رقمية ذكية"),
    GOLD("ذهبية", 100, 250, "خصم 10% لدى الشركاء وتنبيه مسبق مضاعف"),
    DIAMOND("ماسية", 250, null, "مسار VIP سريع وأولوية الحجز في أوقات الذروة");

    companion object {
        fun fromPoints(points: Int): LoyaltyTier = when {
            points >= 250 -> DIAMOND
            points >= 100 -> GOLD
            else -> SILVER
        }
    }
}

data class UserProfileSettings(
    val selectedCity: IraqiCity = IraqiCity.BAGHDAD,
    val isDarkMode: Boolean = false,
    val userName: String = "علي الحديدي",
    val userPhone: String = "07701234567",
    val loyaltyPoints: Int = 60,
    val muteSound: Boolean = false,
    val muteVibration: Boolean = false
)

data class LiveTurnAlert(
    val bookingId: Long,
    val ticketCode: String,
    val venueName: String,
    val stage: TicketStage,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
