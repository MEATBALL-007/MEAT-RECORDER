package com.example.recorderproject.data

import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQChainJson

/**
 * Single source of truth for the declared default value of every persisted
 * setting in [SettingsDataStore]. Used both as the fallback when a key is
 * absent during hydration AND as the target state of `RecorderViewModel.resetFactory()`.
 *
 * Keeping these in one place prevents drift between "what gets read on first
 * launch" and "what Reset Factory restores".
 */
object Defaults {
    // Recording
    const val RECORDER_MODE: String = "CUSTOM"
    const val AUDIO_SOURCE_NAME: String = "Unprocessed"
    const val MIC_SOURCE_LABEL: String = "Unprocessed"
    const val INPUT_GAIN_DB: Float = 0f
    const val NOISE_REDUCTION: Boolean = false
    const val SAMPLE_RATE_HZ: Int = 48000
    // 32-bit float capture (native AudioRecord format → no quantization, best quality).
    // NOTE: 32-bit float WAV does NOT play back via Android MediaPlayer; in-app replay
    // will fail for these files (they're intended for editing on a computer).
    const val BIT_DEPTH: Int = 32
    const val CHANNEL_COUNT: Int = 1
    const val COUNTDOWN_SEC: Int = 0
    const val MAX_DURATION_MIN: Int = 0
    const val AUTO_STOP_MIN: Int = 0
    const val QUALITY_PRESET: String = "Default"
    const val SCENE_NAME: String = "Scene 1"

    // Live toggles
    const val LIVE_NOISE_GATE: Boolean = false
    const val AGC: Boolean = false
    const val HI_PASS: Boolean = false
    const val ANTI_CLIP: Boolean = false
    const val COMPRESSOR: Boolean = false
    const val STEREO_WIDENER: Boolean = false
    const val VAD: Boolean = false
    const val LIVE_EQ_ENABLED: Boolean = false
    val LIVE_EQ_BAND_GAINS: FloatArray get() = FloatArray(6) // fresh copy each access
    const val LIVE_EQ_BAND_GAINS_CSV: String = "0.0,0.0,0.0,0.0,0.0,0.0"

    // EQ editor
    val CURRENT_EQ_CHAIN_JSON: String get() = EQChainJson.toJsonString(EQChain.empty())
    const val EQ_MODE: String = "PARAMETRIC"
    const val EQ_VIEW_MODE: String = "TWO_D"
    const val EQ_APPLY_SAVE_MODE: String = "BOTH"
    const val EQ_BYPASSED: Boolean = false

    // Playback
    const val PLAYBACK_SPEED: Float = 1f
    const val PLAYBACK_LOOP: Boolean = false
    const val PLAYBACK_VOLUME: Float = 1f

    // Loudness / delivery
    const val defaultLoudnessTarget: String = "OFF"
    const val customLoudnessLufs: Float = -16f
    const val customLoudnessTpCeiling: Float = -1f

    // Misc
    val SAVE_DIRECTORY_URI: String? = null
    const val GROUP_BY_SCENE: Boolean = false
    const val LOCKSCREEN_CONTROLS: Boolean = true
    const val CLOUD_BACKUP: Boolean = false
    const val PRE_ROLL_ENABLED: Boolean = false
}
