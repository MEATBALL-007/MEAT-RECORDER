package com.example.recorderproject.model

enum class RecordingMode(val displayName: String) {
    FIELD("Field"),
    STUDIO("Studio"),
    INTERVIEW("Interview"),
    MUSIC("Music")
}

/**
 * Per-mode recording preset.
 *
 * Shape restored from the 2026-05-22 APK pulled off the user's S24 Ultra during the
 * APK-recovery pass on 2026-05-26 (decompiled via jadx; see `recovery/INVENTORY.md`).
 * Typical usage in the old build was a `Map<RecordingMode, ModeSettings>` persisted to
 * SettingsDataStore — re-add those flows once the mode-selector flow is rewired.
 */
data class ModeSettings(
    val sampleRate: Int = 48_000,
    val bitDepth: Int = 16,
    val channelCount: Int = 1,
    val noiseReduction: Boolean = true,
    /** Pre-roll countdown before recording starts. 0 = no countdown. */
    val countdownSeconds: Int = 0,
    /** Auto-stop after N minutes. 0 = no limit. */
    val autoStopMinutes: Int = 0,
)
