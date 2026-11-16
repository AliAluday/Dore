package com.example.data.model

enum class NinevehDistrict(
    val displayName: String,
    val code: String,
    val areas: List<String>
) {
    ALL_NINEVEH(
        displayName = "كل مناطق نينوى",
        code = "NIN",
        areas = emptyList()
    ),
    MOSUL_LEFT(
        displayName = "الموصل - الأيسر",
        code = "MSL-L",
        areas = listOf(
            "حي الزهور", "المجموعة الثقافية", "المهندسين", "حي المثنى",
            "القاهرة", "الزهراء", "الحدباء", "حي الجامعة", "الغابات",
            "الفيصلية", "النبي يونس", "البكر", "التحرير", "الكرامة",
            "الجزائر", "الصديق", "العربي", "البلديات", "القادسية",
            "الكفاءات", "السلام", "السماح", "الرشيدية"
        )
    ),
    MOSUL_RIGHT(
        displayName = "الموصل - الأيمن",
        code = "MSL-R",
        areas = listOf(
            "الدواسة", "المنصور", "اليرموك", "المأمون", "موصل الجديدة",
            "باب الطوب", "باب لكش", "النجفي", "الزنجيلي", "الشفاء",
            "وادي حجر", "تل الرمان", "رجم حديد", "العريبي", "مشيرفة",
            "حي العامل", "17 تموز", "بادوش", "حليلة"
        )
    ),
    TAL_AFAR(
        displayName = "تلعفر",
        code = "TLF",
        areas = listOf(
            "مركز تلعفر", "العياضية", "المحلبية", "القادسية تلعفر",
            "الخضراء", "حسن كوي", "الجزيرة", "ربيعة", "زمار", "بردية"
        )
    ),
    HAMDANIYA(
        displayName = "الحمدانية (بغديدا)",
        code = "HMD",
        areas = listOf(
            "قره قوش (بغديدا)", "برطلة", "كرمليس", "النمرود",
            "الخازر", "وردك", "ترجلة", "قره تبة"
        )
    ),
    TEL_KEPE(
        displayName = "تلكيف",
        code = "TLK",
        areas = listOf(
            "مركز تلكيف", "ألقوش", "باطنايا", "تللسقف",
            "وانة", "الشريخان", "فايدة", "القوسيات"
        )
    ),
    BASHIQA(
        displayName = "بعشيقة وبحزاني",
        code = "BSH",
        areas = listOf(
            "مركز بعشيقة", "بحزاني", "الفاضلية", "خورساباد",
            "جبل مقلوب", "عمر قابجي"
        )
    ),
    SINJAR(
        displayName = "سنجار",
        code = "SNJ",
        areas = listOf(
            "مركز سنجار", "سنوني", "القيروان", "القحطانية (تل عزير)",
            "تل بنات", "خانصور"
        )
    ),
    SHEIKHAN(
        displayName = "الشيخان",
        code = "SHK",
        areas = listOf(
            "عين سفني", "باعذرة", "مهد", "لالش", "شيخان"
        )
    ),
    BAJ(
        displayName = "البعاج",
        code = "BAJ",
        areas = listOf(
            "مركز البعاج", "القحطانية البعاج", "البليع", "الجرن"
        )
    ),
    QAYYARAH(
        displayName = "القيارة وحمام العليل",
        code = "QYR",
        areas = listOf(
            "القيارة", "حمام العليل", "الشورة", "الحود", "الحاج علي"
        )
    ),
    HATRA_MAKHMOUR(
        displayName = "الحضر ومخمور",
        code = "HTR",
        areas = listOf(
            "الحضر", "تل عبطة", "مخمور", "الكوير", "قراج"
        )
    );

    companion object {
        val selectableDistricts = entries.filter { it != ALL_NINEVEH }
        fun fromName(name: String): NinevehDistrict =
            entries.find { it.displayName == name || it.name == name } ?: ALL_NINEVEH
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
    }
}

enum class MedicalSpecialty(val displayName: String) {
    ALL("كل التخصصات"),
    DENTAL("طب الأسنان"),
    INTERNAL("باطنية وقلب"),
    EYES("طب العيون"),
    PEDIATRICS("طب الأطفال"),
    DERMATOLOGY("جلدية وليزر"),
    ORTHOPEDICS("عظام ومفاصل")
}

enum class DoctorVisitType(val displayName: String, val priceMultiplier: Double) {
    NEW_CHECKUP("كشف جديد", 1.0),
    FOLLOW_UP("مراجعة", 0.5),
    CONSULTATION("استشارة سريعة", 0.75)
}

enum class DoctorTimePeriod(val displayName: String, val rangeText: String) {
    MORNING("الفترة الصباحية", "09:00 ص - 01:30 م"),
    EVENING("الفترة المسائية", "04:00 م - 10:00 م")
}

enum class DoctorUrgency(val displayName: String) {
    NORMAL("اعتيادي"),
    URGENT("حالة مستعجلة")
}

object FieldBookingDuration {
    const val MIN_HOURS = 1.5
    const val MAX_HOURS = 3.0
    const val STEP_HOURS = 0.5
    val validSteps = listOf(1.5, 2.0, 2.5, 3.0)

    fun clamp(hours: Double): Double {
        val snapped = kotlin.math.round(hours * 2.0) / 2.0
        return snapped.coerceIn(MIN_HOURS, MAX_HOURS)
    }

    fun formatArabic(hours: Double): String = when (clamp(hours)) {
        1.5 -> "ساعة ونصف (1.5 ساعة)"
        2.0 -> "ساعتان (2.0 ساعة)"
        2.5 -> "ساعتان ونصف (2.5 ساعة)"
        3.0 -> "3 ساعات (الحد الأقصى)"
        else -> "$hours ساعة"
    }

    fun shortLabel(hours: Double): String = when (clamp(hours)) {
        1.5 -> "1.5 ساعة"
        2.0 -> "ساعتان"
        2.5 -> "2.5 ساعة"
        3.0 -> "3 ساعات"
        else -> "$hours س"
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
    GOLD("ذهبية", 100, 250, "خصم 10% لدى شركاء نينوى وتنبيه مسبق مضاعف"),
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
    val selectedDistrict: NinevehDistrict = NinevehDistrict.ALL_NINEVEH,
    val isDarkMode: Boolean = true,
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
