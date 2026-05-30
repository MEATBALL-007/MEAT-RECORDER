package com.example.recorderproject.data

/**
 * Immutable snapshot of every persisted setting, as read from [SettingsDataStore].
 * Returned by `SettingsDataStore.snapshot()` and applied to in-memory state by
 * `RecorderViewModel.applySnapshot()` during hydration.
 *
 * Default values come from [Defaults] so a snapshot built from a missing/empty
 * DataStore is identical to the post-`resetFactory()` state.
 */
data class SettingsSnapshot(
    // Recording
    val recorderMode: String = Defaults.RECORDER_MODE,
    val audioSourceName: String = Defaults.AUDIO_SOURCE_NAME,
    val micSourceLabel: String = Defaults.MIC_SOURCE_LABEL,
    val inputGainDb: Float = Defaults.INPUT_GAIN_DB,
    val noiseReduction: Boolean = Defaults.NOISE_REDUCTION,
    val sampleRate: Int = Defaults.SAMPLE_RATE_HZ,
    val bitDepth: Int = Defaults.BIT_DEPTH,
    val channelCount: Int = Defaults.CHANNEL_COUNT,
    val countdownSec: Int = Defaults.COUNTDOWN_SEC,
    val maxDurationMin: Int = Defaults.MAX_DURATION_MIN,
    val autoStopMin: Int = Defaults.AUTO_STOP_MIN,
    val qualityPreset: String = Defaults.QUALITY_PRESET,
    val sceneName: String = Defaults.SCENE_NAME,
    // Live toggles
    val liveNoiseGate: Boolean = Defaults.LIVE_NOISE_GATE,
    val agc: Boolean = Defaults.AGC,
    val hiPass: Boolean = Defaults.HI_PASS,
    val antiClip: Boolean = Defaults.ANTI_CLIP,
    val compressor: Boolean = Defaults.COMPRESSOR,
    val stereoWidener: Boolean = Defaults.STEREO_WIDENER,
    val vad: Boolean = Defaults.VAD,
    val liveEqEnabled: Boolean = Defaults.LIVE_EQ_ENABLED,
    val liveEqBandGains: FloatArray = Defaults.LIVE_EQ_BAND_GAINS,
    // EQ editor
    val currentEqChainJson: String = Defaults.CURRENT_EQ_CHAIN_JSON,
    val eqMode: String = Defaults.EQ_MODE,
    val eqViewMode: String = Defaults.EQ_VIEW_MODE,
    val eqApplySaveMode: String = Defaults.EQ_APPLY_SAVE_MODE,
    val eqBypassed: Boolean = Defaults.EQ_BYPASSED,
    // Playback
    val playbackSpeed: Float = Defaults.PLAYBACK_SPEED,
    val playbackLoop: Boolean = Defaults.PLAYBACK_LOOP,
    val playbackVolume: Float = Defaults.PLAYBACK_VOLUME,
    // Misc
    val saveDirectoryUri: String? = Defaults.SAVE_DIRECTORY_URI,
    val groupByScene: Boolean = Defaults.GROUP_BY_SCENE,
    val lockScreenControls: Boolean = Defaults.LOCKSCREEN_CONTROLS,
    val cloudBackup: Boolean = Defaults.CLOUD_BACKUP,
    val preRollEnabled: Boolean = Defaults.PRE_ROLL_ENABLED,
    val cloudBackupUri: String? = null,
    // Loudness / delivery
    val defaultLoudnessTarget: String = Defaults.defaultLoudnessTarget,
    val customLoudnessLufs: Float = Defaults.customLoudnessLufs,
    val customLoudnessTpCeiling: Float = Defaults.customLoudnessTpCeiling,
)
