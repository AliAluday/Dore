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
    fun `qr code generator builds deterministic 25x25 matrix with finder patterns`() {
        val m1 = QrCodeGenerator.generateMatrix("DORI|NIN-1-15|1|07701234567")
        val m2 = QrCodeGenerator.generateMatrix("DORI|NIN-1-15|1|07701234567")
        assertEquals(25, m1.size)
        assertEquals(25, m1[0].size)
        assertTrue(m1[0][0])
        assertTrue(m1[6][6])
        assertTrue(m1[3][3])
        for (r in 0 until 25) {
            for (c in 0 until 25) {
                assertEquals(m1[r][c], m2[r][c])
            }
        }
    }
}
