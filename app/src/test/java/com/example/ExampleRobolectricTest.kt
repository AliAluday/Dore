package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SeedData
import com.example.data.model.IraqiCity
import com.example.data.model.LoyaltyTier
import com.example.data.model.ServiceCategory
import com.example.util.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    fun `seed data covers all 5 iraqi cities and 5 categories`() {
        val venues = SeedData.initialVenues()
        val cities = venues.map { it.city }.toSet()
        val categories = venues.map { it.category }.toSet()

        assertEquals(IraqiCity.entries.map { it.displayName }.toSet(), cities)
        assertEquals(ServiceCategory.fiveCategories.map { it.displayName }.toSet(), categories)
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
        val m1 = QrCodeGenerator.generateMatrix("DORI|DR-1-15|1|07701234567")
        val m2 = QrCodeGenerator.generateMatrix("DORI|DR-1-15|1|07701234567")
        assertEquals(25, m1.size)
        assertEquals(25, m1[0].size)
        // Top-left finder corners should be dark
        assertTrue(m1[0][0])
        assertTrue(m1[6][6])
        assertTrue(m1[3][3])
        // Deterministic
        for (r in 0 until 25) {
            for (c in 0 until 25) {
                assertEquals(m1[r][c], m2[r][c])
            }
        }
    }
}
