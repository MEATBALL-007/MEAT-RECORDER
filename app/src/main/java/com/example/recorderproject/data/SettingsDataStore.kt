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
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * DataStore-backed persistence for every recorder/EQ/playback setting that the
 * user can change. Wiped wholesale by `RecorderViewModel.resetFactory()`.
 *
 * Companion SharedPreferences in `MainActivity` (`MODE_PRIVATE`) and
 * `RecorderViewModel` (`meatrec_ui`) hold app-level state (onboarding, theme,
 * custom presets, sort/filter) and are NOT touched by reset.
 */
class SettingsDataStore(private val context: Context) {

    // ----- Recording -----
    private val recorderModeKey = stringPreferencesKey("recorder_mode")
    private val audioSourceNameKey = stringPreferencesKey("audio_source_name")
    private val micSourceLabelKey = stringPreferencesKey("mic_source_label")
    private val inputGainDbKey = floatPreferencesKey("input_gain_db")
    private val noiseReductionKey = booleanPreferencesKey("noise_reduction")
    private val sampleRateKey = intPreferencesKey("sample_rate_hz")
    private val bitDepthKey = intPreferencesKey("bit_depth")
    private val channelCountKey = intPreferencesKey("channel_count")
    private val countdownSecKey = intPreferencesKey("countdown_sec")
    private val maxDurationMinKey = intPreferencesKey("max_duration_min")
    private val autoStopMinKey = intPreferencesKey("auto_stop_min")
    private val qualityPresetKey = stringPreferencesKey("quality_preset")
    private val sceneNameKey = stringPreferencesKey("scene_name")

    // ----- Live toggles -----
    private val liveNoiseGateKey = booleanPreferencesKey("live_noise_gate")
    private val agcKey = booleanPreferencesKey("agc")
    private val hiPassKey = booleanPreferencesKey("hi_pass")
    private val antiClipKey = booleanPreferencesKey("anti_clip")
    private val compressorKey = booleanPreferencesKey("compressor")
    private val stereoWidenerKey = booleanPreferencesKey("stereo_widener")
    private val vadKey = booleanPreferencesKey("vad")
    private val liveEqEnabledKey = booleanPreferencesKey("live_eq_enabled")
    private val liveEqBandGainsKey = stringPreferencesKey("live_eq_band_gains")

    // ----- EQ editor -----
    private val currentEqChainJsonKey = stringPreferencesKey("current_eq_chain_json")
    private val eqModeKey = stringPreferencesKey("eq_mode")
    private val eqViewModeKey = stringPreferencesKey("eq_view_mode")
    private val eqApplySaveModeKey = stringPreferencesKey("eq_apply_save_mode")
    private val eqBypassedKey = booleanPreferencesKey("eq_bypassed")

    // ----- Playback -----
    private val playbackSpeedKey = floatPreferencesKey("playback_speed")
    private val playbackLoopKey = booleanPreferencesKey("playback_loop")
    private val playbackVolumeKey = floatPreferencesKey("playback_volume")

    // ----- Misc -----
    private val saveDirectoryUriKey = stringPreferencesKey("save_directory_uri")
    private val groupByScene = booleanPreferencesKey("group_by_scene")
    private val lockScreenControlsKey = booleanPreferencesKey("lockscreen_controls")
    private val cloudBackupKey = booleanPreferencesKey("cloud_backup")
    private val activeRecordingPathKey = stringPreferencesKey("active_recording_path")

    /** Read every key at once and return a snapshot. */
    suspend fun snapshot(): SettingsSnapshot {
        val p = context.dataStore.data.first()
        return SettingsSnapshot(
            recorderMode = p[recorderModeKey] ?: Defaults.RECORDER_MODE,
            audioSourceName = p[audioSourceNameKey] ?: Defaults.AUDIO_SOURCE_NAME,
            micSourceLabel = p[micSourceLabelKey] ?: Defaults.MIC_SOURCE_LABEL,
            inputGainDb = p[inputGainDbKey] ?: Defaults.INPUT_GAIN_DB,
            noiseReduction = p[noiseReductionKey] ?: Defaults.NOISE_REDUCTION,
            sampleRate = p[sampleRateKey] ?: Defaults.SAMPLE_RATE_HZ,
            bitDepth = p[bitDepthKey] ?: Defaults.BIT_DEPTH,
            channelCount = p[channelCountKey] ?: Defaults.CHANNEL_COUNT,
            countdownSec = p[countdownSecKey] ?: Defaults.COUNTDOWN_SEC,
            maxDurationMin = p[maxDurationMinKey] ?: Defaults.MAX_DURATION_MIN,
            autoStopMin = p[autoStopMinKey] ?: Defaults.AUTO_STOP_MIN,
            qualityPreset = p[qualityPresetKey] ?: Defaults.QUALITY_PRESET,
            sceneName = p[sceneNameKey] ?: Defaults.SCENE_NAME,
            liveNoiseGate = p[liveNoiseGateKey] ?: Defaults.LIVE_NOISE_GATE,
            agc = p[agcKey] ?: Defaults.AGC,
            hiPass = p[hiPassKey] ?: Defaults.HI_PASS,
            antiClip = p[antiClipKey] ?: Defaults.ANTI_CLIP,
            compressor = p[compressorKey] ?: Defaults.COMPRESSOR,
            stereoWidener = p[stereoWidenerKey] ?: Defaults.STEREO_WIDENER,
            vad = p[vadKey] ?: Defaults.VAD,
            liveEqEnabled = p[liveEqEnabledKey] ?: Defaults.LIVE_EQ_ENABLED,
            liveEqBandGains = parseCsvFloats(p[liveEqBandGainsKey], expected = 6),
            currentEqChainJson = p[currentEqChainJsonKey] ?: Defaults.CURRENT_EQ_CHAIN_JSON,
            eqMode = p[eqModeKey] ?: Defaults.EQ_MODE,
            eqViewMode = p[eqViewModeKey] ?: Defaults.EQ_VIEW_MODE,
            eqApplySaveMode = p[eqApplySaveModeKey] ?: Defaults.EQ_APPLY_SAVE_MODE,
            eqBypassed = p[eqBypassedKey] ?: Defaults.EQ_BYPASSED,
            playbackSpeed = p[playbackSpeedKey] ?: Defaults.PLAYBACK_SPEED,
            playbackLoop = p[playbackLoopKey] ?: Defaults.PLAYBACK_LOOP,
            playbackVolume = p[playbackVolumeKey] ?: Defaults.PLAYBACK_VOLUME,
            saveDirectoryUri = p[saveDirectoryUriKey], // null when absent
            groupByScene = p[groupByScene] ?: Defaults.GROUP_BY_SCENE,
            lockScreenControls = p[lockScreenControlsKey] ?: Defaults.LOCKSCREEN_CONTROLS,
            cloudBackup = p[cloudBackupKey] ?: Defaults.CLOUD_BACKUP,
        )
    }

    /** Wipe every key — used by Reset Factory. */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    // ----- Setters -----
    suspend fun setRecorderMode(v: String) = context.dataStore.edit { it[recorderModeKey] = v }
    suspend fun setAudioSourceName(v: String) = context.dataStore.edit { it[audioSourceNameKey] = v }
    suspend fun setMicSourceLabel(v: String) = context.dataStore.edit { it[micSourceLabelKey] = v }
    suspend fun setInputGainDb(v: Float) = context.dataStore.edit { it[inputGainDbKey] = v }
    suspend fun setNoiseReduction(v: Boolean) = context.dataStore.edit { it[noiseReductionKey] = v }
    suspend fun setSampleRate(v: Int) = context.dataStore.edit { it[sampleRateKey] = v }
    suspend fun setBitDepth(v: Int) = context.dataStore.edit { it[bitDepthKey] = v }
    suspend fun setChannelCount(v: Int) = context.dataStore.edit { it[channelCountKey] = v }
    suspend fun setCountdownSec(v: Int) = context.dataStore.edit { it[countdownSecKey] = v }
    suspend fun setMaxDurationMin(v: Int) = context.dataStore.edit { it[maxDurationMinKey] = v }
    suspend fun setAutoStopMin(v: Int) = context.dataStore.edit { it[autoStopMinKey] = v }
    suspend fun setQualityPreset(v: String) = context.dataStore.edit { it[qualityPresetKey] = v }
    suspend fun setSceneName(v: String) = context.dataStore.edit { it[sceneNameKey] = v }

    suspend fun setLiveNoiseGate(v: Boolean) = context.dataStore.edit { it[liveNoiseGateKey] = v }
    suspend fun setAgc(v: Boolean) = context.dataStore.edit { it[agcKey] = v }
    suspend fun setHiPass(v: Boolean) = context.dataStore.edit { it[hiPassKey] = v }
    suspend fun setAntiClip(v: Boolean) = context.dataStore.edit { it[antiClipKey] = v }
    suspend fun setCompressor(v: Boolean) = context.dataStore.edit { it[compressorKey] = v }
    suspend fun setStereoWidener(v: Boolean) = context.dataStore.edit { it[stereoWidenerKey] = v }
    suspend fun setVad(v: Boolean) = context.dataStore.edit { it[vadKey] = v }
    suspend fun setLiveEqEnabled(v: Boolean) = context.dataStore.edit { it[liveEqEnabledKey] = v }
    suspend fun setLiveEqBandGains(v: FloatArray) = context.dataStore.edit {
        it[liveEqBandGainsKey] = v.joinToString(",")
    }

    suspend fun setCurrentEqChainJson(v: String) = context.dataStore.edit { it[currentEqChainJsonKey] = v }
    suspend fun setEqMode(v: String) = context.dataStore.edit { it[eqModeKey] = v }
    suspend fun setEqViewMode(v: String) = context.dataStore.edit { it[eqViewModeKey] = v }
    suspend fun setEqApplySaveMode(v: String) = context.dataStore.edit { it[eqApplySaveModeKey] = v }
    suspend fun setEqBypassed(v: Boolean) = context.dataStore.edit { it[eqBypassedKey] = v }

    suspend fun setPlaybackSpeed(v: Float) = context.dataStore.edit { it[playbackSpeedKey] = v }
    suspend fun setPlaybackLoop(v: Boolean) = context.dataStore.edit { it[playbackLoopKey] = v }
    suspend fun setPlaybackVolume(v: Float) = context.dataStore.edit { it[playbackVolumeKey] = v }

    suspend fun setSaveDirectoryUri(v: String?) = context.dataStore.edit {
        if (v == null) it.remove(saveDirectoryUriKey) else it[saveDirectoryUriKey] = v
    }
    suspend fun setGroupByScene(v: Boolean) = context.dataStore.edit { it[groupByScene] = v }
    suspend fun setLockScreenControls(v: Boolean) = context.dataStore.edit { it[lockScreenControlsKey] = v }
    suspend fun setCloudBackup(v: Boolean) = context.dataStore.edit { it[cloudBackupKey] = v }

    /** Path of the currently-active recording, or null if none. Set on start, cleared on stop. */
    suspend fun setActiveRecordingPath(path: String?) {
        context.dataStore.edit {
            if (path == null) it.remove(activeRecordingPathKey)
            else it[activeRecordingPathKey] = path
        }
    }

    suspend fun getActiveRecordingPath(): String? {
        return context.dataStore.data.first()[activeRecordingPathKey]
    }

    private fun parseCsvFloats(csv: String?, expected: Int): FloatArray {
        if (csv.isNullOrBlank()) return FloatArray(expected)
        val parts = csv.split(",")
        if (parts.size != expected) return FloatArray(expected)
        return FloatArray(expected) { i -> parts[i].toFloatOrNull() ?: 0f }
    }
}
