package com.example.recorderproject.billing

/**
 * Catalogue of features locked behind MEAT REC Pro. Used to label the paywall and to
 * decide whether tapping a control should run the feature or open the upgrade sheet.
 *
 * Free tier (NOT listed here) covers: recording up to 15 min per take, up to 48 kHz / 16-bit,
 * mono + stereo, built-in mic + basic source picker, playback, basic trim, rename,
 * share, file list/search/sort, and the first 3 themes.
 */
enum class ProFeature(val title: String, val blurb: String) {
    HIGH_RES_AUDIO("Studio quality", "96 kHz and 24 / 32-bit float recording"),
    EXTERNAL_MIC("External mics", "USB-C, Bluetooth & wired boom mic routing"),
    FULL_EQ("Parametric EQ", "Full visual EQ with unlimited bands"),
    LIVE_DSP("Live processing", "Noise gate, AGC, compressor, stereo widener"),
    PITCH_SHIFT("Pitch shift", "Re-pitch a take without changing its length"),
    LOUDNESS_DELIVERY("Loudness delivery", "Render to broadcast LUFS targets with true-peak limiting"),
    PRE_ROLL_VAD("Smart capture", "Pre-roll buffer, voice-activated record & slate tone"),
    TRANSCRIPTION("Transcribe", "Turn recordings into text on-device"),
    CLOUD_BACKUP("Cloud backup", "Auto-upload takes to your Google Drive"),
    ANALYSIS_TOOLS("Analysis suite", "Spectrogram, harmonic portrait, room profiler, A/B & multi-take"),
    ALL_THEMES("All themes", "Unlock all 10 appearance themes"),
    RECORDING_LIMIT("Unlimited recording", "Free recordings are limited to 15 min — go unlimited with Pro");

    companion object {
        /** Free users are capped to these. */
        const val FREE_MAX_SAMPLE_RATE = 48_000
        const val FREE_MAX_BIT_DEPTH = 16

        /** Theme indices [0..2] are free; the rest require Pro. */
        const val FREE_THEME_COUNT = 3

        /** Free recording length cap in seconds (15 minutes). */
        const val FREE_RECORDING_LIMIT_SECONDS = 15 * 60

        /** Warn the user when this many seconds remain before the limit. */
        const val FREE_RECORDING_WARN_SECONDS = 2 * 60
    }
}
