package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.NinevehDistrict
import com.example.data.model.UserAppControlConfig
import com.example.data.model.UserProfileSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dori_nineveh_prefs_v2")

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val SELECTED_DISTRICT = stringPreferencesKey("selected_district")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_PHONE = stringPreferencesKey("user_phone")
        val USER_AGE = stringPreferencesKey("user_age")
        val OPTIONAL_NOTE = stringPreferencesKey("optional_note")
        val LOYALTY_POINTS = intPreferencesKey("loyalty_points")
        val MUTE_SOUND = booleanPreferencesKey("mute_sound")
        val MUTE_VIBRATION = booleanPreferencesKey("mute_vibration")
        val ACTIVE_ADMIN_ID = longPreferencesKey("active_admin_id")
        val ACTIVE_PROVIDER_ID = longPreferencesKey("active_provider_id")

        // User Edition Control Panel Settings
        val CTRL_ALLOW_BOOKINGS = booleanPreferencesKey("ctrl_allow_bookings")
        val CTRL_FASTEST_TURN = booleanPreferencesKey("ctrl_fastest_turn")
        val CTRL_QR_CODE = booleanPreferencesKey("ctrl_qr_code")
        val CTRL_WHATSAPP = booleanPreferencesKey("ctrl_whatsapp")
        val CTRL_REVIEWS = booleanPreferencesKey("ctrl_reviews")
        val CTRL_LOYALTY = booleanPreferencesKey("ctrl_loyalty")
        val CTRL_OPTIONAL_FIELDS = booleanPreferencesKey("ctrl_optional_fields")
        val CTRL_LIVE_ALERTS = booleanPreferencesKey("ctrl_live_alerts")
        val CTRL_POINTS_PER_BOOKING = intPreferencesKey("ctrl_points_per_booking")
        val CTRL_DISABLED_CATEGORIES = stringSetPreferencesKey("ctrl_disabled_categories")
        val CTRL_BROADCAST_MSG = stringPreferencesKey("ctrl_broadcast_msg")
    }

    val userPreferences: Flow<UserProfileSettings> = context.dataStore.data.map { prefs ->
        UserProfileSettings(
            selectedDistrict = NinevehDistrict.fromName(
                prefs[Keys.SELECTED_DISTRICT] ?: NinevehDistrict.ALL_NINEVEH.displayName
            ),
            isDarkMode = prefs[Keys.IS_DARK_MODE] ?: true,
            userName = prefs[Keys.USER_NAME] ?: "",
            userPhone = prefs[Keys.USER_PHONE] ?: "",
            userAge = prefs[Keys.USER_AGE] ?: "",
            optionalNote = prefs[Keys.OPTIONAL_NOTE] ?: "",
            loyaltyPoints = prefs[Keys.LOYALTY_POINTS] ?: 60,
            muteSound = prefs[Keys.MUTE_SOUND] ?: false,
            muteVibration = prefs[Keys.MUTE_VIBRATION] ?: false,
            activeAdminId = prefs[Keys.ACTIVE_ADMIN_ID] ?: 1L,
            activeProviderId = prefs[Keys.ACTIVE_PROVIDER_ID] ?: 0L,
            controlConfig = UserAppControlConfig(
                allowNewBookings = prefs[Keys.CTRL_ALLOW_BOOKINGS] ?: true,
                enableFastestTurn = prefs[Keys.CTRL_FASTEST_TURN] ?: true,
                enableQrCode = prefs[Keys.CTRL_QR_CODE] ?: true,
                enableWhatsAppShare = prefs[Keys.CTRL_WHATSAPP] ?: true,
                enableReviews = prefs[Keys.CTRL_REVIEWS] ?: true,
                enableLoyalty = prefs[Keys.CTRL_LOYALTY] ?: true,
                enableOptionalFields = prefs[Keys.CTRL_OPTIONAL_FIELDS] ?: true,
                enableLiveAlerts = prefs[Keys.CTRL_LIVE_ALERTS] ?: true,
                pointsPerBooking = prefs[Keys.CTRL_POINTS_PER_BOOKING] ?: 10,
                disabledCategories = prefs[Keys.CTRL_DISABLED_CATEGORIES] ?: emptySet(),
                broadcastMessage = prefs[Keys.CTRL_BROADCAST_MSG] ?: ""
            )
        )
    }

    suspend fun setSelectedDistrict(district: NinevehDistrict) {
        context.dataStore.edit { it[Keys.SELECTED_DISTRICT] = district.displayName }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_DARK_MODE] = enabled }
    }

    suspend fun updateProfile(
        name: String,
        phone: String,
        district: NinevehDistrict,
        age: String = "",
        optionalNote: String = ""
    ) {
        context.dataStore.edit {
            it[Keys.USER_NAME] = name.trim()
            it[Keys.USER_PHONE] = phone.trim()
            it[Keys.SELECTED_DISTRICT] = district.displayName
            it[Keys.USER_AGE] = age.trim()
            it[Keys.OPTIONAL_NOTE] = optionalNote.trim()
        }
    }

    suspend fun addLoyaltyPoints(delta: Int) {
        context.dataStore.edit {
            val current = it[Keys.LOYALTY_POINTS] ?: 60
            it[Keys.LOYALTY_POINTS] = (current + delta).coerceAtLeast(0)
        }
    }

    suspend fun setLoyaltyPoints(points: Int) {
        context.dataStore.edit {
            it[Keys.LOYALTY_POINTS] = points.coerceAtLeast(0)
        }
    }

    suspend fun setMuteSound(muted: Boolean) {
        context.dataStore.edit { it[Keys.MUTE_SOUND] = muted }
    }

    suspend fun setMuteVibration(muted: Boolean) {
        context.dataStore.edit { it[Keys.MUTE_VIBRATION] = muted }
    }

    suspend fun setActiveAdminId(adminId: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_ADMIN_ID] = adminId }
    }

    suspend fun setActiveProviderId(providerId: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_PROVIDER_ID] = providerId }
    }

    suspend fun saveControlConfig(config: UserAppControlConfig) {
        context.dataStore.edit {
            it[Keys.CTRL_ALLOW_BOOKINGS] = config.allowNewBookings
            it[Keys.CTRL_FASTEST_TURN] = config.enableFastestTurn
            it[Keys.CTRL_QR_CODE] = config.enableQrCode
            it[Keys.CTRL_WHATSAPP] = config.enableWhatsAppShare
            it[Keys.CTRL_REVIEWS] = config.enableReviews
            it[Keys.CTRL_LOYALTY] = config.enableLoyalty
            it[Keys.CTRL_OPTIONAL_FIELDS] = config.enableOptionalFields
            it[Keys.CTRL_LIVE_ALERTS] = config.enableLiveAlerts
            it[Keys.CTRL_POINTS_PER_BOOKING] = config.pointsPerBooking.coerceIn(0, 100)
            it[Keys.CTRL_DISABLED_CATEGORIES] = config.disabledCategories
            it[Keys.CTRL_BROADCAST_MSG] = config.broadcastMessage.trim()
        }
    }

    suspend fun applySyncedSettings(settings: UserProfileSettings) {
        context.dataStore.edit {
            it[Keys.SELECTED_DISTRICT] = settings.selectedDistrict.displayName
            it[Keys.IS_DARK_MODE] = settings.isDarkMode
            it[Keys.USER_NAME] = settings.userName
            it[Keys.USER_PHONE] = settings.userPhone
            it[Keys.USER_AGE] = settings.userAge
            it[Keys.OPTIONAL_NOTE] = settings.optionalNote
            it[Keys.LOYALTY_POINTS] = settings.loyaltyPoints.coerceAtLeast(0)
            it[Keys.MUTE_SOUND] = settings.muteSound
            it[Keys.MUTE_VIBRATION] = settings.muteVibration
            val cfg = settings.controlConfig
            it[Keys.CTRL_ALLOW_BOOKINGS] = cfg.allowNewBookings
            it[Keys.CTRL_FASTEST_TURN] = cfg.enableFastestTurn
            it[Keys.CTRL_QR_CODE] = cfg.enableQrCode
            it[Keys.CTRL_WHATSAPP] = cfg.enableWhatsAppShare
            it[Keys.CTRL_REVIEWS] = cfg.enableReviews
            it[Keys.CTRL_LOYALTY] = cfg.enableLoyalty
            it[Keys.CTRL_OPTIONAL_FIELDS] = cfg.enableOptionalFields
            it[Keys.CTRL_LIVE_ALERTS] = cfg.enableLiveAlerts
            it[Keys.CTRL_POINTS_PER_BOOKING] = cfg.pointsPerBooking.coerceIn(0, 100)
            it[Keys.CTRL_DISABLED_CATEGORIES] = cfg.disabledCategories
            it[Keys.CTRL_BROADCAST_MSG] = cfg.broadcastMessage.trim()
        }
    }
}

