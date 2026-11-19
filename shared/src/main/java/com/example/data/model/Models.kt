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

enum class BookingApprovalStatus(val label: String) {
    PENDING("بانتظار القبول"),
    ACCEPTED("مقبولة"),
    REJECTED("مرفوضة");

    companion object {
        fun fromName(value: String): BookingApprovalStatus =
            entries.find { it.name == value } ?: PENDING
    }
}

enum class SupportRequestType(val displayName: String) {
    CHAT("محادثة مباشرة"),
    COMPLAINT("طلب شكوى");

    companion object {
        fun fromName(value: String): SupportRequestType =
            entries.find { it.name == value || it.displayName == value } ?: CHAT
    }
}

enum class SupportCategory(val displayName: String) {
    COMPLAINT("تقديم شكوى"),
    BOOKING_ISSUE("مشكلة حجز"),
    TECHNICAL("عطل تقني"),
    VENUE_REPORT("بلاغ عن مركز"),
    SUGGESTION("اقتراح وتحسين"),
    GENERAL("استفسار عام");

    companion object {
        fun fromName(value: String): SupportCategory =
            entries.find { it.name == value || it.displayName == value } ?: GENERAL
    }
}

enum class SupportTicketStatus(val label: String) {
    SENT("مرسلة"),
    DELIVERED("تم الاستلام"),
    IN_REVIEW("قيد المراجعة"),
    REPLIED("تم الرد"),
    CLOSED("مغلقة");

    companion object {
        fun fromName(value: String): SupportTicketStatus =
            entries.find { it.name == value } ?: SENT
    }
}

enum class ProviderRole(val displayName: String) {
    DOCTOR("طبيب"),
    FIELD_OWNER("صاحب ملعب"),
    LAB_SPECIALIST("أخصائي مختبر"),
    WORKSHOP_OWNER("صاحب ورشة"),
    BEAUTY_EXPERT("خبير تجميل"),
    MERCHANT("تاجر / مقدم خدمة");

    companion object {
        fun fromName(value: String): ProviderRole =
            entries.find { it.name == value || it.displayName == value } ?: MERCHANT
    }
}

enum class AdminPermissionType(val label: String) {
    MANAGE_FEATURES("إدارة المميزات والإعلانات"),
    MANAGE_VENUES("إدارة المراكز والأسعار"),
    MANAGE_QUEUES("إدارة الطوابير وقبول التذاكر"),
    REPLY_SUPPORT("الرد على رسائل الدعم"),
    MANAGE_PROVIDERS("إدارة الأطباء والتجار"),
    MANAGE_ADMINS("إدارة الأدمن والصلاحيات"),
    RUN_COMMANDS("تنفيذ الأوامر السريعة والتصفير")
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

data class UserAppControlConfig(
    val allowNewBookings: Boolean = true,
    val enableFastestTurn: Boolean = true,
    val enableQrCode: Boolean = true,
    val enableWhatsAppShare: Boolean = true,
    val enableReviews: Boolean = true,
    val enableLoyalty: Boolean = true,
    val enableOptionalFields: Boolean = true,
    val enableLiveAlerts: Boolean = true,
    val pointsPerBooking: Int = 10,
    val disabledCategories: Set<String> = emptySet(),
    val broadcastMessage: String = ""
)

data class UserProfileSettings(
    val selectedDistrict: NinevehDistrict = NinevehDistrict.ALL_NINEVEH,
    val isDarkMode: Boolean = true,
    val userName: String = "",
    val userPhone: String = "",
    val userAge: String = "",
    val optionalNote: String = "",
    val loyaltyPoints: Int = 0,
    val muteSound: Boolean = false,
    val muteVibration: Boolean = false,
    val favoriteVenueIds: Set<Int> = emptySet(),
    val activeAdminId: Long = 1L,
    val activeProviderId: Long = 0L,
    val controlConfig: UserAppControlConfig = UserAppControlConfig()
) {
    val hasMandatoryInfo: Boolean
        get() = isValidFullName(userName) && isValidPhone(userPhone)

    companion object {
        fun normalizeDigits(input: String): String = buildString(input.length) {
            input.forEach { ch ->
                when (ch) {
                    in '٠'..'٩' -> append(('0'.code + (ch.code - '٠'.code)).toChar())
                    in '۰'..'۹' -> append(('0'.code + (ch.code - '۰'.code)).toChar())
                    else -> append(ch)
                }
            }
        }

        fun isValidFullName(name: String): Boolean = name.trim().length >= 3

        fun isValidPhone(phone: String): Boolean {
            val digits = normalizeDigits(phone).filter { it.isDigit() }
            return digits.length in 10..14
        }
    }
}

data class LiveTurnAlert(
    val bookingId: Long,
    val ticketCode: String,
    val venueName: String,
    val stage: TicketStage,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
