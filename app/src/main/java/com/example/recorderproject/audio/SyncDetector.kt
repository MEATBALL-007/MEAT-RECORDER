package com.example.recorderproject.audio

import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Sync-point detector — finds the first significant audio onset in a recording.
 *
 * Used by [com.example.recorderproject.ui.components.RecordingFileList]'s
 * "Detect sync" menu item to mark the moment a take actually begins (e.g. the
 * first clap, the first downbeat, the first word). Stored on the RecordFile as
 * `syncPointMs` and shown as a badge.
 *
 * Algorithm (simple but effective for studio takes):
 *  1. Compute RMS in overlapping 20 ms windows
 *  2. Find global peak window
 *  3. Walk backward from the peak until RMS drops below threshold (peak * 0.20)
 *  4. Return that earlier window's start time as the sync point
 *
 * Robust to: silent intros, ambient noise floor, clap-track style cues.
 * Not robust to: gradual fade-in, dialogue with no transient.
 */
object SyncDetector {

    /** Returns the sync point in milliseconds, or null if none could be confidently found. */
    fun detect(path: String, sampleRate: Int = 48_000): Long? {
        if (path.startsWith("content://")) return null
        val f = File(path)
        if (!f.exists()) return null
        val samples = try { FFTAnalyzer.readWavSamples(f) } catch (_: Exception) { return null }
        if (samples.size < sampleRate / 2) return null
        return detectInBuffer(samples, sampleRate)
    }

    /**
     * Pure-buffer variant — accepts an already-loaded ShortArray. Exposed so it can
     * be unit-tested without a WAV file.
     */
    fun detectInBuffer(samples: ShortArray, sampleRate: Int = 48_000): Long? {
        val winSize = max(256, sampleRate / 50) // ~20 ms
        val hop = winSize / 2
        val nWindows = (samples.size - winSize) / hop + 1
        if (nWindows < 4) return null

        // RMS per window
        val rms = FloatArray(nWindows)
        var peakIdx = 0
        var peakVal = 0f
        for (w in 0 until nWindows) {
            val off = w * hop
            var sumSq = 0.0
            for (j in 0 until winSize) {
                val s = samples[off + j].toDouble() / 32_768.0
                sumSq += s * s
            }
            rms[w] = kotlin.math.sqrt(sumSq / winSize).toFloat()
            if (rms[w] > peakVal) {
                peakVal = rms[w]
                peakIdx = w
            }
        }

        // Floor: avg of bottom 10% RMS values (background noise estimate)
        val sorted = rms.sortedArray()
        val floor = sorted.take(max(1, sorted.size / 10)).average().toFloat()
        // Require peak to be at least 6 dB above noise floor for a confident detection.
        if (peakVal < floor * 2f) return null

        // Walk backward from peak until RMS drops below max(peakVal*0.20, floor*1.5)
        val onsetThreshold = max(peakVal * 0.20f, floor * 1.5f)
        var idx = peakIdx
        while (idx > 0 && rms[idx] > onsetThreshold) idx--

        // Don't go below floor-ish either — clamp at 0
        val onsetSamples = idx.toLong() * hop
        return (onsetSamples * 1_000L) / sampleRate
    }
}
