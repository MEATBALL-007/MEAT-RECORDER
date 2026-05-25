package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * RBJ Audio EQ Cookbook biquad coefficient generator.
 * Reference: http://shepazu.github.io/Audio-EQ-Cookbook/audio-eq-cookbook.html
 *
 * Filter-type mapping:
 *   LOW_PASS == HIGH_CUT (LPF math; both names communicate same filter)
 *   HIGH_PASS == LOW_CUT (HPF math)
 *   TILT = composite (low-shelf with -gain) + (high-shelf with +gain) at the same freq
 */
object BiquadCoeffs {

    /** Single biquad for the band (TILT returns just the low-shelf half — use cascadeForBand for TILT). */
    fun forBand(band: EQBand, sampleRate: Float): Biquad {
        if (band.type == EQBandType.TILT) return cascadeForBand(band, sampleRate).first()
        return singleBiquad(band, band.type, sampleRate)
    }

    /** Biquad cascade for a band (length 1 except TILT which is 2). */
    fun cascadeForBand(band: EQBand, sampleRate: Float): List<Biquad> {
        if (band.type != EQBandType.TILT) return listOf(singleBiquad(band, band.type, sampleRate))
        val lowHalf = singleBiquad(band.copy(gainDb = -band.gainDb), EQBandType.LOW_SHELF, sampleRate)
        val highHalf = singleBiquad(band, EQBandType.HIGH_SHELF, sampleRate)
        return listOf(lowHalf, highHalf)
    }

    private fun singleBiquad(band: EQBand, forcedType: EQBandType, sampleRate: Float): Biquad {
        val f0 = band.frequencyHz.toDouble().coerceIn(20.0, (sampleRate / 2.0) - 100.0)
        val q = band.q.toDouble().coerceAtLeast(0.05)
        val gainDb = band.gainDb.toDouble()
        val omega = 2.0 * PI * f0 / sampleRate
        val sinW = sin(omega)
        val cosW = cos(omega)
        val alpha = sinW / (2.0 * q)
        val a = 10.0.pow(gainDb / 40.0)

        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1Coef: Double; val a2Coef: Double

        when (forcedType) {
            EQBandType.BELL -> {
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosW
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1Coef = -2.0 * cosW
                a2Coef = 1.0 - alpha / a
            }
            EQBandType.LOW_SHELF -> {
                val sqrtA = sqrt(a)
                b0 = a * ((a + 1) - (a - 1) * cosW + 2 * sqrtA * alpha)
                b1 = 2 * a * ((a - 1) - (a + 1) * cosW)
                b2 = a * ((a + 1) - (a - 1) * cosW - 2 * sqrtA * alpha)
                a0 = (a + 1) + (a - 1) * cosW + 2 * sqrtA * alpha
                a1Coef = -2 * ((a - 1) + (a + 1) * cosW)
                a2Coef = (a + 1) + (a - 1) * cosW - 2 * sqrtA * alpha
            }
            EQBandType.HIGH_SHELF -> {
                val sqrtA = sqrt(a)
                b0 = a * ((a + 1) + (a - 1) * cosW + 2 * sqrtA * alpha)
                b1 = -2 * a * ((a - 1) + (a + 1) * cosW)
                b2 = a * ((a + 1) + (a - 1) * cosW - 2 * sqrtA * alpha)
                a0 = (a + 1) - (a - 1) * cosW + 2 * sqrtA * alpha
                a1Coef = 2 * ((a - 1) - (a + 1) * cosW)
                a2Coef = (a + 1) - (a - 1) * cosW - 2 * sqrtA * alpha
            }
            EQBandType.LOW_PASS, EQBandType.HIGH_CUT -> {
                b0 = (1.0 - cosW) / 2.0
                b1 = 1.0 - cosW
                b2 = (1.0 - cosW) / 2.0
                a0 = 1.0 + alpha
                a1Coef = -2.0 * cosW
                a2Coef = 1.0 - alpha
            }
            EQBandType.HIGH_PASS, EQBandType.LOW_CUT -> {
                b0 = (1.0 + cosW) / 2.0
                b1 = -(1.0 + cosW)
                b2 = (1.0 + cosW) / 2.0
                a0 = 1.0 + alpha
                a1Coef = -2.0 * cosW
                a2Coef = 1.0 - alpha
            }
            EQBandType.NOTCH -> {
                b0 = 1.0
                b1 = -2.0 * cosW
                b2 = 1.0
                a0 = 1.0 + alpha
                a1Coef = -2.0 * cosW
                a2Coef = 1.0 - alpha
            }
            EQBandType.BAND_PASS -> {
                b0 = alpha
                b1 = 0.0
                b2 = -alpha
                a0 = 1.0 + alpha
                a1Coef = -2.0 * cosW
                a2Coef = 1.0 - alpha
            }
            EQBandType.TILT -> error("TILT handled via cascadeForBand")
        }

        return Biquad(
            b0 = b0 / a0,
            b1 = b1 / a0,
            b2 = b2 / a0,
            a1 = a1Coef / a0,
            a2 = a2Coef / a0,
        )
    }
}
