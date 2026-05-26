package com.example.recorderproject.model

/**
 * Pre-record input level snapshot, derived from the AudioMonitor PCM loop.
 *
 * - [rmsDb] is the smoothed input loudness in dBFS, range -60..0 (floor at -60).
 * - [peakDb] is the peak-hold value in dBFS, decays toward [rmsDb] over time.
 * - [clipped] flips true when the peak exceeded -0.1 dBFS in the last ~2 seconds.
 */
data class MonitorLevel(
    val rmsDb: Float,
    val peakDb: Float,
    val clipped: Boolean,
) {
    companion object {
        val Silent = MonitorLevel(rmsDb = -60f, peakDb = -60f, clipped = false)
    }
}

/**
 * Result of applying an audio event or decay tick. Carries the new [level] and the
 * scheduled clip-clear timestamp (epoch ms) so the ViewModel can hold the deadline
 * across calls without keeping state inside the pure functions.
 */
data class LevelUpdate(val level: MonitorLevel, val clipUntilMs: Long)

/** Clip threshold: a peak above this triggers the clip flag. */
const val CLIP_THRESHOLD_DB = -0.1f

/** How long the clip flag stays lit after a clip event, in ms. */
const val CLIP_HOLD_MS = 2_000L

/** Default peak-hold decay per tick, in dB. */
const val PEAK_DECAY_DB_PER_TICK = 1f

/**
 * Apply a fresh audio buffer's RMS/peak readings. Peak is held against the current
 * peak (whichever is louder wins); clip is set if [newPeakDb] crosses the threshold,
 * and the clip window is extended.
 */
fun applyAudioSample(
    curr: MonitorLevel,
    newRmsDb: Float,
    newPeakDb: Float,
    nowMs: Long,
    clipUntilMs: Long,
): LevelUpdate {
    val heldPeak = maxOf(newPeakDb, curr.peakDb)
    val isNewClip = newPeakDb > CLIP_THRESHOLD_DB
    val nextClipUntilMs = if (isNewClip) nowMs + CLIP_HOLD_MS else clipUntilMs
    val isClipped = isNewClip || nowMs < clipUntilMs
    return LevelUpdate(
        level = MonitorLevel(rmsDb = newRmsDb, peakDb = heldPeak, clipped = isClipped),
        clipUntilMs = nextClipUntilMs,
    )
}

/**
 * Apply a periodic decay tick (no new audio). Peak decays by [peakDecayDb] toward
 * [MonitorLevel.rmsDb] but never below it. The clip flag clears once [nowMs] has
 * passed the scheduled [clipUntilMs] deadline.
 */
fun applyDecayTick(
    curr: MonitorLevel,
    nowMs: Long,
    clipUntilMs: Long,
    peakDecayDb: Float = PEAK_DECAY_DB_PER_TICK,
): LevelUpdate {
    val decayed = (curr.peakDb - peakDecayDb).coerceAtLeast(curr.rmsDb)
    val isClipped = nowMs < clipUntilMs
    return LevelUpdate(
        level = MonitorLevel(rmsDb = curr.rmsDb, peakDb = decayed, clipped = isClipped),
        clipUntilMs = clipUntilMs,
    )
}
