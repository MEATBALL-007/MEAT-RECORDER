package com.example.recorderproject.audio

import kotlin.math.abs
import kotlin.math.log10

/**
 * BS.1770 true-peak detector via 4× polyphase upsampling.
 *
 * 12-tap windowed-sinc per-phase filter (Lanczos), enough to lift the
 * inter-sample-peak detection above ~0.5 dBTP at the worst-case 1/4-sample
 * offset for content up to ~22 kHz @ 48 kHz input.
 */
class TruePeakDetector(sampleRate: Float, private val channels: Int = 1) {

    private val taps = COEFFS.maxOf { it.size }
    private val history = Array(channels) { FloatArray(taps) }
    private var histPos = 0
    private var peakLinear = 0f

    val peakDbTP: Float
        get() = if (peakLinear <= 0f) Float.NEGATIVE_INFINITY
                else 20f * log10(peakLinear)

    fun feed(interleaved: FloatArray) {
        val frames = interleaved.size / channels
        for (frame in 0 until frames) {
            for (ch in 0 until channels) {
                val x = interleaved[frame * channels + ch]
                history[ch][histPos] = x
                for (phase in COEFFS.indices) {
                    var acc = 0f
                    val c = COEFFS[phase]
                    for (i in c.indices) {
                        val idx = (histPos - i + taps) % taps
                        acc += c[i] * history[ch][idx]
                    }
                    val a = abs(acc)
                    if (a > peakLinear) peakLinear = a
                }
            }
            histPos = (histPos + 1) % taps
        }
    }

    fun reset() {
        for (ch in 0 until channels) history[ch].fill(0f)
        histPos = 0
        peakLinear = 0f
    }

    companion object {
        private val COEFFS = arrayOf(
            // phase 0 (identity)
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f),
            // phase 1 (t = 0.25)
            floatArrayOf(
                -0.0023f, 0.0086f, -0.0220f, 0.0470f, -0.0941f, 0.3134f,
                 0.8801f, -0.1483f, 0.0727f, -0.0395f, 0.0203f, -0.0079f
            ),
            // phase 2 (t = 0.5)
            floatArrayOf(
                -0.0040f, 0.0136f, -0.0322f, 0.0644f, -0.1213f, 0.6035f,
                 0.6035f, -0.1213f, 0.0644f, -0.0322f, 0.0136f, -0.0040f
            ),
            // phase 3 (t = 0.75)
            floatArrayOf(
                -0.0079f, 0.0203f, -0.0395f, 0.0727f, -0.1483f, 0.8801f,
                 0.3134f, -0.0941f, 0.0470f, -0.0220f, 0.0086f, -0.0023f
            ),
        )
    }
}
