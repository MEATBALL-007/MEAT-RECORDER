package com.example.recorderproject.data

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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

// TODO: Original implementation lost in 2026-05-25 iCloud eviction.
// Was wider — also persisted ModeSettings, AppTheme, and recording mode.
// Re-add those flows once ModeSettings / RecordingMode / AppTheme are rebuilt.
class SettingsDataStore(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_name")
    private val sampleRateKey = intPreferencesKey("sample_rate_hz")
    private val bitDepthKey = intPreferencesKey("bit_depth")
    private val channelKey = intPreferencesKey("channel_count")
    private val noiseRedKey = booleanPreferencesKey("noise_reduction")
    private val maxDurKey = intPreferencesKey("max_duration_sec")
    private val countdownKey = intPreferencesKey("countdown_sec")
    private val gainKey = floatPreferencesKey("input_gain_linear")

    val theme: Flow<String> = context.dataStore.data.map { it[themeKey] ?: "KMUTT" }
    val sampleRate: Flow<Int> = context.dataStore.data.map { it[sampleRateKey] ?: 48000 }
    val bitDepth: Flow<Int> = context.dataStore.data.map { it[bitDepthKey] ?: 16 }
    val channelCount: Flow<Int> = context.dataStore.data.map { it[channelKey] ?: 1 }
    val noiseReduction: Flow<Boolean> = context.dataStore.data.map { it[noiseRedKey] ?: true }
    val maxDurationSec: Flow<Int> = context.dataStore.data.map { it[maxDurKey] ?: -1 }
    val countdownSec: Flow<Int> = context.dataStore.data.map { it[countdownKey] ?: 0 }
    val inputGain: Flow<Float> = context.dataStore.data.map { it[gainKey] ?: 1.0f }

    suspend fun setTheme(v: String) = context.dataStore.edit { it[themeKey] = v }
    suspend fun setSampleRate(v: Int) = context.dataStore.edit { it[sampleRateKey] = v }
    suspend fun setBitDepth(v: Int) = context.dataStore.edit { it[bitDepthKey] = v }
    suspend fun setChannelCount(v: Int) = context.dataStore.edit { it[channelKey] = v }
    suspend fun setNoiseReduction(v: Boolean) = context.dataStore.edit { it[noiseRedKey] = v }
    suspend fun setMaxDurationSec(v: Int) = context.dataStore.edit { it[maxDurKey] = v }
    suspend fun setCountdownSec(v: Int) = context.dataStore.edit { it[countdownKey] = v }
    suspend fun setInputGain(v: Float) = context.dataStore.edit { it[gainKey] = v }
}
