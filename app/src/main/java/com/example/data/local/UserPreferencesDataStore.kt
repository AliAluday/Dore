package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.NinevehDistrict
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
            muteVibration = prefs[Keys.MUTE_VIBRATION] ?: false
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

    suspend fun ensureMandatorySaved(name: String, phone: String) {
        context.dataStore.edit {
            if (it[Keys.USER_NAME].isNullOrBlank()) {
                it[Keys.USER_NAME] = name.trim()
            }
            if (it[Keys.USER_PHONE].isNullOrBlank()) {
                it[Keys.USER_PHONE] = phone.trim()
            }
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
