package com.example.recorderproject.model

import androidx.compose.ui.graphics.Color

/**
 * The 5 recording modes from the 22 May APK fan-card selector.
 *
 * Each mode bundles:
 *   - displayName for the card label
 *   - accent color (rendered as the card tint + icon halo)
 *   - sample rate / bit depth / channels / NR preset
 *   - shortHelp shown in onboarding tooltips
 *
 * `Custom` lets the user dial in their own combo; the others snap to sensible
 * defaults for that genre.
 */
enum class RecorderMode(
    val displayName: String,
    val accent: Color,
    val sampleRate: Int,
    val bitDepth: Int,
    val channelCount: Int,
    val noiseReduction: Boolean,
    val shortHelp: String,
) {
    FILM(
        displayName = "FILM",
        accent = Color(0xFFEA4F30),
        sampleRate = 48_000,
        bitDepth = 24,
        channelCount = 2,
        noiseReduction = false,
        shortHelp = "Production sound · 48 kHz / 24-bit stereo · NR off",
    ),
    INTERVIEW(
        displayName = "INTERVIEW",
        accent = Color(0xFFE9C04F),
        sampleRate = 48_000,
        bitDepth = 16,
        channelCount = 1,
        noiseReduction = true,
        shortHelp = "Dialogue · 48 kHz / 16-bit mono · NR on",
    ),
    MUSIC(
        displayName = "MUSIC",
        accent = Color(0xFF1F84C7),
        sampleRate = 96_000,
        bitDepth = 24,
        channelCount = 2,
        noiseReduction = false,
        shortHelp = "Hi-Res capture · 96 kHz / 24-bit stereo · NR off",
    ),
    AMBIENCE(
        displayName = "AMBIENCE",
        accent = Color(0xFF3DC399),
        sampleRate = 48_000,
        bitDepth = 24,
        channelCount = 2,
        noiseReduction = false,
        shortHelp = "Field room tone · 48 kHz / 24-bit stereo · NR off",
    ),
    CUSTOM(
        displayName = "CUSTOM",
        accent = Color(0xFF7B8189),
        sampleRate = 48_000,
        bitDepth = 16,
        channelCount = 1,
        noiseReduction = true,
        shortHelp = "Your settings — fully editable",
    ),
    ;

    companion object {
        val Default = INTERVIEW
        fun fromName(name: String?): RecorderMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Default
    }
}
