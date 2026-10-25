package com.roger.circuitcaller.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val voiceEnabled: Boolean = true,
    val countdownEnabled: Boolean = true,
    val beepsEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val halfwayCallout: Boolean = false,
    val ttsVoiceName: String = "",
    val speechRate: Float = 1.0f,
    val prepSeconds: Int = 3
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val VOICE = booleanPreferencesKey("voice_enabled")
        val COUNTDOWN = booleanPreferencesKey("countdown_enabled")
        val BEEPS = booleanPreferencesKey("beeps_enabled")
        val VIBRATE = booleanPreferencesKey("vibrate_enabled")
        val SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HALFWAY = booleanPreferencesKey("halfway_callout")
        val VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val RATE = floatPreferencesKey("speech_rate")
        val PREP = intPreferencesKey("prep_seconds")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            voiceEnabled = p[Keys.VOICE] ?: true,
            countdownEnabled = p[Keys.COUNTDOWN] ?: true,
            beepsEnabled = p[Keys.BEEPS] ?: true,
            vibrateEnabled = p[Keys.VIBRATE] ?: true,
            keepScreenOn = p[Keys.SCREEN_ON] ?: true,
            halfwayCallout = p[Keys.HALFWAY] ?: false,
            ttsVoiceName = p[Keys.VOICE_NAME] ?: "",
            speechRate = p[Keys.RATE] ?: 1.0f,
            prepSeconds = p[Keys.PREP] ?: 3
        )
    }

    suspend fun setVoiceEnabled(v: Boolean) = edit { it[Keys.VOICE] = v }
    suspend fun setCountdownEnabled(v: Boolean) = edit { it[Keys.COUNTDOWN] = v }
    suspend fun setBeepsEnabled(v: Boolean) = edit { it[Keys.BEEPS] = v }
    suspend fun setVibrateEnabled(v: Boolean) = edit { it[Keys.VIBRATE] = v }
    suspend fun setKeepScreenOn(v: Boolean) = edit { it[Keys.SCREEN_ON] = v }
    suspend fun setHalfwayCallout(v: Boolean) = edit { it[Keys.HALFWAY] = v }
    suspend fun setTtsVoiceName(v: String) = edit { it[Keys.VOICE_NAME] = v }
    suspend fun setSpeechRate(v: Float) = edit { it[Keys.RATE] = v }
    suspend fun setPrepSeconds(v: Int) = edit { it[Keys.PREP] = v }

    suspend fun applyAll(s: AppSettings) = edit { p ->
        p[Keys.VOICE] = s.voiceEnabled
        p[Keys.COUNTDOWN] = s.countdownEnabled
        p[Keys.BEEPS] = s.beepsEnabled
        p[Keys.VIBRATE] = s.vibrateEnabled
        p[Keys.SCREEN_ON] = s.keepScreenOn
        p[Keys.HALFWAY] = s.halfwayCallout
        p[Keys.VOICE_NAME] = s.ttsVoiceName
        p[Keys.RATE] = s.speechRate
        p[Keys.PREP] = s.prepSeconds
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
