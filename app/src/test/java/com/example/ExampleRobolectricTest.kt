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
}
