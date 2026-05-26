package com.example.recorderproject.model

/**
 * G8: Quality presets — bundle sample rate + bit depth + channels into a single
 * named profile so the user can pick "Voice" vs "Music" without micro-tuning.
 */
enum class RecordingQuality(
    val displayName: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val channelCount: Int,
    val description: String,
) {
    VOICE("Voice", 16_000, 16, 1, "Compact · ideal for dialogue"),
    PODCAST("Podcast", 48_000, 16, 1, "Studio voice · most compatible"),
    MUSIC("Music", 48_000, 24, 2, "Stereo · 24-bit headroom"),
    HIRES("Hi-Res", 96_000, 24, 2, "96 kHz · pro tracking"),
    ;

    companion object {
        val Default = PODCAST

        /** Best-effort guess of the active preset from current settings. */
        fun bestMatch(sr: Int, bd: Int, ch: Int): RecordingQuality? =
            entries.firstOrNull { it.sampleRate == sr && it.bitDepth == bd && it.channelCount == ch }
    }
}
