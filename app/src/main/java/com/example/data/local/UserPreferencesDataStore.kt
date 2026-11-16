package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.IraqiCity
import com.example.data.model.UserProfileSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dori_prefs")

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val SELECTED_CITY = stringPreferencesKey("selected_city")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_PHONE = stringPreferencesKey("user_phone")
        val LOYALTY_POINTS = intPreferencesKey("loyalty_points")
        val MUTE_SOUND = booleanPreferencesKey("mute_sound")
        val MUTE_VIBRATION = booleanPreferencesKey("mute_vibration")
    }

    val userPreferences: Flow<UserProfileSettings> = context.dataStore.data.map { prefs ->
        UserProfileSettings(
            selectedCity = IraqiCity.fromName(prefs[Keys.SELECTED_CITY] ?: IraqiCity.BAGHDAD.displayName),
            isDarkMode = prefs[Keys.IS_DARK_MODE] ?: false,
            userName = prefs[Keys.USER_NAME] ?: "علي الحديدي",
            userPhone = prefs[Keys.USER_PHONE] ?: "07701234567",
            loyaltyPoints = prefs[Keys.LOYALTY_POINTS] ?: 60,
            muteSound = prefs[Keys.MUTE_SOUND] ?: false,
            muteVibration = prefs[Keys.MUTE_VIBRATION] ?: false
        )
    }

    suspend fun setSelectedCity(city: IraqiCity) {
        context.dataStore.edit { it[Keys.SELECTED_CITY] = city.displayName }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_DARK_MODE] = enabled }
    }

    suspend fun updateProfile(name: String, phone: String, city: IraqiCity) {
        context.dataStore.edit {
            it[Keys.USER_NAME] = name.ifBlank { "علي الحديدي" }
            it[Keys.USER_PHONE] = phone.ifBlank { "07701234567" }
            it[Keys.SELECTED_CITY] = city.displayName
        }
    }

    suspend fun addLoyaltyPoints(delta: Int) {
        context.dataStore.edit {
            val current = it[Keys.LOYALTY_POINTS] ?: 60
            it[Keys.LOYALTY_POINTS] = (current + delta).coerceAtLeast(0)
        }
    }

    suspend fun setMuteSound(muted: Boolean) {
        context.dataStore.edit { it[Keys.MUTE_SOUND] = muted }
    }

    suspend fun setMuteVibration(muted: Boolean) {
        context.dataStore.edit { it[Keys.MUTE_VIBRATION] = muted }
    }
}
