package com.example.recorderproject.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * BS.1770-4 K-weighted loudness meter.
 *
 * Exposes momentary (400 ms), short-term (3 s), and integrated (full history)
 * LUFS plus max-momentary and max-short-term.
 *
 * K-weighting is a two-stage IIR:
 *   1) High-shelf at 1.681 kHz, +4 dB    (treble boost — "head-related" weighting)
 *   2) High-pass at  38 Hz                (revised RLB curve)
 *
 * Channels are summed: BS.1770 weights L=R=1.0 for the two front channels;
 * mono is treated as a single channel (per the spec).
 */
class LufsProcessor(private val sampleRate: Float, val channels: Int = 1) {

    // ---- K-weighting coefficients (computed once per sample rate) ----
    private val preFilter: Array<Biquad>  // shelf, one per channel
    private val rlbFilter: Array<Biquad>  // high-pass, one per channel

    // ---- Block accumulation: BS.1770-4 uses 400 ms blocks at 75% overlap ----
    private val blockSamples = (sampleRate * 0.4f).toInt()           // 400 ms
    private val hopSamples = blockSamples / 4                         // 100 ms hop
    private val ringSize = blockSamples
    private val ring = Array(channels) { FloatArray(ringSize) }
    private var ringPos = 0
    private var samplesSinceHop = 0
    private var totalSamples = 0L

    // ---- Block-loudness history for gating & windows ----
    // Each block-loudness value is in LUFS. Stored unbounded (typical session: a few hours fits).
    private val blockLoudness = ArrayList<Float>()

    var momentaryLufs: Float = -70f; private set
    var shortTermLufs: Float = -70f; private set
    var integratedLufs: Float = -70f; private set
    var momentaryMaxLufs: Float = -70f; private set
    var shortTermMaxLufs: Float = -70f; private set

    init {
        // High-shelf (pre-filter), Q=0.707, fc=1681.974 Hz, gain=+3.999843 dB.
        val (b0, b1, b2, a1, a2) = highShelf(1681.974f, 3.999843f, sampleRate)
        preFilter = Array(channels) { Biquad(b0, b1, b2, a1, a2) }

        // High-pass (RLB), Q=0.5, fc=38.135 Hz.
        val (hb0, hb1, hb2, ha1, ha2) = highPass(38.135f, 0.5f, sampleRate)
        rlbFilter = Array(channels) { Biquad(hb0, hb1, hb2, ha1, ha2) }
    }

    /**
     * Feed interleaved samples. Length must be a multiple of [channels].
     */
    fun process(interleaved: FloatArray) {
        val n = interleaved.size / channels
        for (frame in 0 until n) {
            for (ch in 0 until channels) {
                val raw = interleaved[frame * channels + ch]
                val k = rlbFilter[ch].process(preFilter[ch].process(raw.toDouble())).toFloat()
                ring[ch][ringPos] = k
            }
            ringPos = (ringPos + 1) % ringSize
            totalSamples++
            samplesSinceHop++

            if (samplesSinceHop >= hopSamples && totalSamples >= blockSamples) {
                samplesSinceHop = 0
                emitBlock()
            }
        }
        // If no blocks have been emitted yet (short signal), keep live window values at floor.
        updateLiveWindows()
    }

    private fun emitBlock() {
        // Compute mean-square over the last blockSamples frames across all channels.
        var sumSq = 0.0
        for (ch in 0 until channels) {
            val r = ring[ch]
            for (i in r.indices) sumSq += r[i] * r[i]
        }
        // BS.1770: mean square summed across channels, divided only by blockSamples (not channels).
        val mean = sumSq / blockSamples
        val l = (-0.691 + 10.0 * log10(max(mean, 1e-12))).toFloat()
        blockLoudness.add(l)
        momentaryLufs = l
        if (l > momentaryMaxLufs) momentaryMaxLufs = l
        updateShortTerm()
        updateIntegrated()
    }

    private fun updateLiveWindows() {
        if (blockLoudness.isEmpty()) {
            momentaryLufs = -70f
            shortTermLufs = -70f
        }
    }

    private fun updateShortTerm() {
        // Short-term = mean-square over the last 3 s = 30 hops (30 × 100 ms).
        val window = 30
        if (blockLoudness.size < window) {
            shortTermLufs = blockLoudness.last()
        } else {
            // Convert last `window` block-loudness values to mean-square, then back to LU.
            var ms = 0.0
            for (i in (blockLoudness.size - window) until blockLoudness.size) {
                ms += Math.pow(10.0, (blockLoudness[i] + 0.691) / 10.0)
            }
            ms /= window
            shortTermLufs = (-0.691 + 10.0 * log10(max(ms, 1e-12))).toFloat()
        }
        if (shortTermLufs > shortTermMaxLufs) shortTermMaxLufs = shortTermLufs
    }

    private fun updateIntegrated() {
        // BS.1770-4 gating: absolute gate at -70 LUFS, then relative gate at (ungated_integrated - 10).
        if (blockLoudness.isEmpty()) { integratedLufs = -70f; return }
        val abovesAbs = blockLoudness.filter { it >= -70f }
        if (abovesAbs.isEmpty()) { integratedLufs = -70f; return }
        val ungated = meanSquareLufs(abovesAbs)
        val relGate = ungated - 10f
        val abovesRel = abovesAbs.filter { it >= relGate }
        if (abovesRel.isEmpty()) { integratedLufs = ungated; return }
        integratedLufs = meanSquareLufs(abovesRel)
    }

    private fun meanSquareLufs(blocks: List<Float>): Float {
        var ms = 0.0
        for (l in blocks) ms += Math.pow(10.0, (l + 0.691) / 10.0)
        ms /= blocks.size
        return (-0.691 + 10.0 * log10(max(ms, 1e-12))).toFloat()
    }

    fun reset() {
        for (ch in 0 until channels) {
            ring[ch].fill(0f)
            preFilter[ch].reset()
            rlbFilter[ch].reset()
        }
        ringPos = 0
        samplesSinceHop = 0
        totalSamples = 0
        blockLoudness.clear()
        momentaryLufs = -70f
        shortTermLufs = -70f
        integratedLufs = -70f
        momentaryMaxLufs = -70f
        shortTermMaxLufs = -70f
    }

    // ---- Coefficient generators (RBJ cookbook adapted for BS.1770-4 spec values) ----

    private fun highShelf(fc: Float, gainDb: Float, sr: Float): DoubleArray {
        val A = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * PI * fc / sr
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / 2.0 * sqrt((A + 1.0 / A) * (1.0 / 0.707 - 1.0) + 2.0)
        val b0 = A * ((A + 1) + (A - 1) * cosW + 2 * sqrt(A) * alpha)
        val b1 = -2 * A * ((A - 1) + (A + 1) * cosW)
        val b2 = A * ((A + 1) + (A - 1) * cosW - 2 * sqrt(A) * alpha)
        val a0 = (A + 1) - (A - 1) * cosW + 2 * sqrt(A) * alpha
        val a1 = 2 * ((A - 1) - (A + 1) * cosW)
        val a2 = (A + 1) - (A - 1) * cosW - 2 * sqrt(A) * alpha
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun highPass(fc: Float, q: Float, sr: Float): DoubleArray {
        val w0 = 2.0 * PI * fc / sr
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / (2.0 * q)
        val b0 = (1 + cosW) / 2.0
        val b1 = -(1 + cosW)
        val b2 = (1 + cosW) / 2.0
        val a0 = 1 + alpha
        val a1 = -2 * cosW
        val a2 = 1 - alpha
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
}

// Destructure helper for the coefficient arrays above.
private operator fun DoubleArray.component1() = this[0]
private operator fun DoubleArray.component2() = this[1]
private operator fun DoubleArray.component3() = this[2]
private operator fun DoubleArray.component4() = this[3]
private operator fun DoubleArray.component5() = this[4]
