package com.example.recorderproject.model

// TODO: original definition lost 2026-05-25. Placeholder values for typical recording modes.
enum class RecordingMode(val displayName: String) {
    FIELD("Field"),
    STUDIO("Studio"),
    INTERVIEW("Interview"),
    MUSIC("Music")
}

data class ModeSettings(
    val mode: RecordingMode = RecordingMode.FIELD,
    val sampleRate: Int = 48000,
    val bitDepth: Int = 16,
    val channelCount: Int = 1,
    val noiseReduction: Boolean = true,
    val inputGainLinear: Float = 1.0f,
    val countdownSec: Int = 0,
    val maxDurationSec: Int = -1
)
