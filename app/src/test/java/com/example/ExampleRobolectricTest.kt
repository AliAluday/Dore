package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SeedData
import com.example.data.model.FieldBookingDuration
import com.example.data.model.LoyaltyTier
import com.example.data.model.MedicalSpecialty
import com.example.data.model.NinevehDistrict
import com.example.data.model.ServiceCategory
import com.example.util.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import com.example.data.model.UserProfileSettings
import org.junit.Assert.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("دوري", appName)
    }

    @Test
    fun `seed data covers all 11 nineveh districts and all 5 categories`() {
        val venues = SeedData.initialVenues()
        val districts = venues.map { it.city }.toSet()
        val categories = venues.map { it.category }.toSet()

        assertEquals(NinevehDistrict.selectableDistricts.map { it.displayName }.toSet(), districts)
        assertEquals(ServiceCategory.fiveCategories.map { it.displayName }.toSet(), categories)
        assertTrue(NinevehDistrict.selectableDistricts.all { it.areas.isNotEmpty() })
    }

    @Test
    fun `every company queue starts cleanly at ticket 1 and above`() {
        val venues = SeedData.initialVenues()
        val itemsByVenue = SeedData.initialQueueItems(venues).groupBy { it.venueId }

        venues.forEach { venue ->
            assertTrue("Venue ${venue.id} nextTicketNumber must be >= 1", venue.nextTicketNumber >= 1)
            assertTrue("Venue ${venue.id} serving must be 0 or 1", venue.currentServingNumber in 0..1)
            val items = itemsByVenue[venue.id].orEmpty().sortedBy { it.ticketNumber }
            if (items.isNotEmpty()) {
                assertEquals(1, items.first().ticketNumber)
                items.forEachIndexed { idx, item ->
                    assertEquals(idx + 1, item.ticketNumber)
                }
                assertEquals(items.size + 1, venue.nextTicketNumber)
                assertEquals(items.size, venue.queueAheadCount)
            } else {
                assertEquals(1, venue.nextTicketNumber)
                assertEquals(0, venue.queueAheadCount)
            }
            assertEquals(venue.queueAheadCount * venue.avgMinutesPerPerson, venue.estimatedWaitMinutes)
        }
    }

    @Test
    fun `mandatory user profile validation rejects empty or invalid details`() {
        val defaultProfile = UserProfileSettings()
        assertFalse(defaultProfile.hasMandatoryInfo)
        assertFalse(UserProfileSettings.isValidFullName(""))
        assertFalse(UserProfileSettings.isValidFullName("أب"))
        assertTrue(UserProfileSettings.isValidFullName("علي حسين"))

        assertFalse(UserProfileSettings.isValidPhone(""))
        assertFalse(UserProfileSettings.isValidPhone("077012"))
        assertTrue(UserProfileSettings.isValidPhone("07701234567"))
        assertTrue(UserProfileSettings.isValidPhone("+9647701234567"))

        val completeProfile = UserProfileSettings(
            userName = "علي حسين الجبوري",
            userPhone = "07701234567"
        )
        assertTrue(completeProfile.hasMandatoryInfo)
    }

    @Test
    fun `field booking duration strictly enforces 1_5h min to 3_0h max in 0_5h steps`() {
        assertEquals(1.5, FieldBookingDuration.MIN_HOURS, 0.001)
        assertEquals(3.0, FieldBookingDuration.MAX_HOURS, 0.001)
        assertEquals(0.5, FieldBookingDuration.STEP_HOURS, 0.001)
        assertEquals(listOf(1.5, 2.0, 2.5, 3.0), FieldBookingDuration.validSteps)
        assertEquals(1.5, FieldBookingDuration.clamp(1.0), 0.001)
        assertEquals(3.0, FieldBookingDuration.clamp(4.0), 0.001)
        assertEquals(2.5, FieldBookingDuration.clamp(2.5), 0.001)
    }

    @Test
    fun `clinics cover all medical specialties`() {
        val clinicSpecialties = SeedData.initialVenues()
            .filter { it.category == ServiceCategory.CLINICS.displayName }
            .map { it.specialty }
            .toSet()

        val expected = MedicalSpecialty.entries
            .filter { it != MedicalSpecialty.ALL }
            .map { it.displayName }
            .toSet()

        assertEquals(expected, clinicSpecialties)
    }

    @Test
    fun `loyalty tiers advance accurately across silver gold diamond`() {
        assertEquals(LoyaltyTier.SILVER, LoyaltyTier.fromPoints(0))
        assertEquals(LoyaltyTier.SILVER, LoyaltyTier.fromPoints(90))
        assertEquals(LoyaltyTier.GOLD, LoyaltyTier.fromPoints(100))
        assertEquals(LoyaltyTier.GOLD, LoyaltyTier.fromPoints(240))
        assertEquals(LoyaltyTier.DIAMOND, LoyaltyTier.fromPoints(250))
    }

    @Test
    fun `qr code generator outputs real scannable ISO QR readable by standard QRCodeReader`() {
        val sampleBooking = com.example.data.local.BookingEntity(
            ticketCode = "NIN-1-1",
            queueNumber = 1,
            venueId = 1,
            venueName = "عيادة الحدباء",
            venueCity = "الموصل - الأيسر",
            venueCategory = "عيادات",
            venuePhone = "+9647701112233",
            customerName = "علي حسين الجبوري",
            customerPhone = "07701234567",
            selectedOption = "فحص وتشخيص",
            priceDinar = 20000,
            peopleAhead = 0,
            estimatedMinutes = 0
        )
        val payload = QrCodeGenerator.buildTicketPayload(sampleBooking)
        val matrix = QrCodeGenerator.generateMatrix(payload)
        assertTrue(matrix.isNotEmpty())
        assertEquals(matrix.size, matrix[0].size)

        // Render matrix scaled 4x into ARGB pixels and decode with standard ZXing QRCodeReader
        val scale = 4
        val dim = matrix.size * scale
        val pixels = IntArray(dim * dim) { idx ->
            val r = (idx / dim) / scale
            val c = (idx % dim) / scale
            if (matrix[r][c]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val source = com.google.zxing.RGBLuminanceSource(dim, dim, pixels)
        val bitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
        val decoded = com.google.zxing.qrcode.QRCodeReader().decode(
            bitmap,
            mapOf(com.google.zxing.DecodeHintType.CHARACTER_SET to "UTF-8")
        )
        assertEquals(payload, decoded.text)
    }

    @Test
    fun `same-day qr payload encodes fresh customer details independently`() {
        val firstGuest = com.example.data.local.BookingEntity(
            ticketCode = "NIN-2-1",
            queueNumber = 1,
            venueId = 2,
            venueName = "ورشة الحدباء",
            venueCity = "الموصل - الأيسر",
            venueCategory = "صيانة",
            venuePhone = "+9647702223344",
            customerName = "أحمد محمود",
            customerPhone = "07701112233",
            selectedOption = "فحص كمبيوتر",
            priceDinar = 25000
        )
        val secondGuest = firstGuest.copy(
            ticketCode = "NIN-2-2",
            queueNumber = 2,
            customerName = "زينب كريم",
            customerPhone = "07509998877"
        )
        val payload1 = QrCodeGenerator.buildTicketPayload(firstGuest)
        val payload2 = QrCodeGenerator.buildTicketPayload(secondGuest)
        assertTrue(payload1.contains("أحمد محمود"))
        assertTrue(payload2.contains("زينب كريم"))
        assertFalse(payload2.contains("أحمد محمود"))
    }

    @Test
    fun `user app control config defaults enable all core user edition features`() {
        val config = com.example.data.model.UserAppControlConfig()
        assertTrue(config.allowNewBookings)
        assertTrue(config.enableFastestTurn)
        assertTrue(config.enableQrCode)
        assertTrue(config.enableWhatsAppShare)
        assertTrue(config.enableReviews)
        assertTrue(config.enableLoyalty)
        assertTrue(config.enableOptionalFields)
        assertTrue(config.enableLiveAlerts)
        assertEquals(10, config.pointsPerBooking)
        assertTrue(config.disabledCategories.isEmpty())
        assertEquals("", config.broadcastMessage)
        assertTrue(ServiceCategory.fiveCategories.all { it.displayName !in config.disabledCategories })
    }

    @Test
    fun `control panel commands enforce booking lockouts, custom venues, loyalty points, and queue reset`() =
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = androidx.room.Room.inMemoryDatabaseBuilder(
                context,
                com.example.data.local.DoriDatabase::class.java
            ).allowMainThreadQueries().build()
            try {
                val prefs = com.example.data.local.UserPreferencesDataStore(context)
                val repo = com.example.data.repository.DoriRepository(db.doriDao(), prefs)
                repo.ensureSeeded()
                repo.resetControlConfig()

                // 1. Booking allowed by default, optional info preserved
                val firstResult = repo.bookQueueTicket(
                    venueId = 3,
                    customerName = "علي حسين الجبوري",
                    customerPhone = "07701234567",
                    selectedOption = "تنظيف بشرة",
                    optionalInfo = "حساسية خفيفة"
                )
                assertTrue(firstResult.isSuccess)
                val created = firstResult.getOrThrow()
                assertEquals("حساسية خفيفة", created.optionalInfo)
                assertEquals(1, created.queueNumber)

                // 2. Disable bookings and verify rejection
                repo.updateControlConfig { it.copy(allowNewBookings = false) }
                val blockedResult = repo.bookQueueTicket(
                    venueId = 3,
                    customerName = "مصطفى طارق",
                    customerPhone = "07709998877",
                    selectedOption = "تنظيف بشرة"
                )
                assertTrue(blockedResult.isFailure)

                // 3. Re-enable bookings, disable optional fields, custom loyalty points = 25
                repo.updateControlConfig {
                    it.copy(
                        allowNewBookings = true,
                        enableOptionalFields = false,
                        enableLoyalty = true,
                        pointsPerBooking = 25
                    )
                }
                val secondResult = repo.bookQueueTicket(
                    venueId = 5,
                    customerName = "سارة العبيدي",
                    customerPhone = "07701112233",
                    selectedOption = "تحليل دم شامل",
                    optionalInfo = "يجب إخفاؤه"
                )
                assertTrue(secondResult.isSuccess)
                assertEquals("", secondResult.getOrThrow().optionalInfo)

                val earned = repo.completeBookingWithOptionalReview(
                    bookingId = secondResult.getOrThrow().id,
                    rating = 5,
                    comment = "ممتاز"
                )
                assertEquals(25, earned)

                // 4. Add custom venue & verify deletion
                val addedVenue = repo.addCustomVenue(
                    name = "عيادة النور التجريبية",
                    district = NinevehDistrict.MOSUL_LEFT,
                    area = "حي الزهور",
                    category = ServiceCategory.CLINICS,
                    specialty = MedicalSpecialty.DENTAL.displayName,
                    priceDinar = 15000,
                    avgMinutes = 6,
                    phone = "07701234567"
                )
                assertTrue(addedVenue.isSuccess)
                val customId = addedVenue.getOrThrow().id
                assertTrue(db.doriDao().getVenueById(customId) != null)
                repo.deleteVenue(customId)
                assertTrue(db.doriDao().getVenueById(customId) == null)

                // 5. Reset all queues to #1
                repo.resetAllQueuesToOne()
                val resetVenue = db.doriDao().getVenueById(1)!!
                assertEquals(1, resetVenue.nextTicketNumber)
                assertEquals(0, resetVenue.currentServingNumber)
                assertEquals(0, resetVenue.waitingCount)
            } finally {
                db.close()
            }
        }

    @Test
    fun `support tickets, admin permissions, and provider ticket acceptance workflow work end-to-end`() =
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = androidx.room.Room.inMemoryDatabaseBuilder(
                context,
                com.example.data.local.DoriDatabase::class.java
            ).allowMainThreadQueries().build()
            try {
                val prefs = com.example.data.local.UserPreferencesDataStore(context)
                val repo = com.example.data.repository.DoriRepository(db.doriDao(), prefs)
                repo.ensureSeeded()

                // 1. Seeded Admins & Permission Checks
                val seededAdmins = db.doriDao().getAllAdminsOnce()
                assertTrue(seededAdmins.size >= 2)
                val superAdmin = seededAdmins.first { it.isSuperAdmin }
                assertTrue(
                    com.example.data.model.AdminPermissionType.entries.all {
                        superAdmin.hasPermission(it)
                    }
                )
                // Super Admin cannot be deleted
                assertTrue(repo.deleteAdminAccount(superAdmin.id).isFailure)

                // Add restricted Admin & verify granular permissions
                val addedAdmin = repo.addAdminAccount(
                    name = "حسين العزاوي",
                    roleTitle = "موظف دعم",
                    phone = "07705556677",
                    canManageFeatures = false,
                    canManageVenues = false,
                    canManageQueues = false,
                    canReplySupport = true,
                    canManageProviders = false,
                    canManageAdmins = false,
                    canRunCommands = false
                ).getOrThrow()
                assertTrue(addedAdmin.hasPermission(com.example.data.model.AdminPermissionType.REPLY_SUPPORT))
                assertFalse(addedAdmin.hasPermission(com.example.data.model.AdminPermissionType.MANAGE_ADMINS))
                assertFalse(addedAdmin.hasPermission(com.example.data.model.AdminPermissionType.RUN_COMMANDS))
                assertTrue(repo.deleteAdminAccount(addedAdmin.id).isSuccess)

                // 2. Support Ticket Creation, Delivery & Admin Reply
                val ticket = repo.createSupportTicket(
                    senderName = "محمد خالد",
                    senderPhone = "07701234567",
                    category = com.example.data.model.SupportCategory.BOOKING_ISSUE,
                    subject = "استفسار عن الحجز",
                    message = "تأخرت التذكرة قليلاً في تحديث الدور"
                ).getOrThrow()
                assertEquals(com.example.data.model.SupportTicketStatus.SENT.name, ticket.status)
                repo.markSupportTicketDelivered(ticket.id)
                assertEquals(
                    com.example.data.model.SupportTicketStatus.DELIVERED.name,
                    db.doriDao().getSupportTicketById(ticket.id)!!.status
                )
                val replied = repo.replyToSupportTicket(
                    ticketId = ticket.id,
                    replyText = "تم فحص الطابور وتحديث دورك فوراً",
                    adminName = "أحمد الموصلي"
                ).getOrThrow()
                assertEquals(com.example.data.model.SupportTicketStatus.REPLIED.name, replied.status)
                assertEquals("تم فحص الطابور وتحديث دورك فوراً", replied.adminReply)

                // 3. Service Providers & Ticket Acceptance / Rejection
                val seededProviders = db.doriDao().getAllProvidersOnce()
                assertTrue(seededProviders.size >= 4)
                val doctor = repo.addServiceProvider(
                    name = "د. علياء النعيمي",
                    role = com.example.data.model.ProviderRole.DOCTOR,
                    customTitle = "طبيب اختصاص",
                    phone = "07708889900",
                    venueId = 1,
                    canAcceptTickets = true,
                    canCallNext = true,
                    canPauseVenue = false
                ).getOrThrow()
                assertEquals("طبيب اختصاص", doctor.customTitle)

                val booking1 = repo.bookQueueTicket(
                    venueId = 1,
                    customerName = "عمر سعد",
                    customerPhone = "07701112233",
                    selectedOption = "فحص وتشخيص"
                ).getOrThrow()
                assertEquals(com.example.data.model.BookingApprovalStatus.PENDING.name, booking1.approvalStatus)

                val accepted = repo.acceptBookingTicket(
                    bookingId = booking1.id,
                    actorName = "${doctor.name} (${doctor.customTitle})",
                    note = "حياك الله بعيادتنا",
                    providerId = doctor.id
                )!!
                assertEquals(com.example.data.model.BookingApprovalStatus.ACCEPTED.name, accepted.approvalStatus)
                assertEquals(1, db.doriDao().getProviderById(doctor.id)!!.acceptedCount)

                val booking2 = repo.bookQueueTicket(
                    venueId = 1,
                    customerName = "ياسر نوري",
                    customerPhone = "07704445566",
                    selectedOption = "متابعة"
                ).getOrThrow()
                val rejected = repo.rejectBookingTicket(
                    bookingId = booking2.id,
                    actorName = "${doctor.name} (${doctor.customTitle})",
                    note = "الجدول ممتلئ اليوم"
                )!!
                assertEquals(com.example.data.model.BookingApprovalStatus.REJECTED.name, rejected.approvalStatus)
                assertEquals(com.example.data.model.BookingStatus.CANCELLED.name, rejected.status)
            } finally {
                db.close()
            }
        }
}
