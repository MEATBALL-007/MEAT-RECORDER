package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class BiquadCoeffsTest {

    private val sr = 48_000f

    /** Magnitude (dB) of a biquad at a given frequency, measured via sine-sweep peak. */
    private fun magnitudeDb(b: Biquad, fHz: Float): Double {
        val omega = 2.0 * PI * fHz / sr
        b.reset()
        repeat(1024) { n -> b.process(sin(omega * n)) }
        var peak = 0.0
        for (n in 1024 until 1024 + 4096) {
            val y = b.process(sin(omega * n))
            if (kotlin.math.abs(y) > peak) peak = kotlin.math.abs(y)
        }
        return 20.0 * log10(peak.coerceAtLeast(1e-12))
    }

    @Test fun `bell at 1kHz with +6dB gives roughly +6dB at center`() {
        val band = EQBand(id = 1, type = EQBandType.BELL, frequencyHz = 1000f, gainDb = 6f, q = 1.4f)
        val b = BiquadCoeffs.forBand(band, sr)
        val gain = magnitudeDb(b, 1000f)
        assertEquals(6.0, gain, 0.6)
    }

    @Test fun `notch at 1kHz attenuates the center frequency strongly`() {
        val band = EQBand(id = 1, type = EQBandType.NOTCH, frequencyHz = 1000f, gainDb = 0f, q = 10f)
        val b = BiquadCoeffs.forBand(band, sr)
        val gain = magnitudeDb(b, 1000f)
        assertTrue("Notch attenuated $gain dB", gain < -20.0)
    }

    @Test fun `low pass at 1kHz passes 100Hz and rolls off at 10kHz`() {
        val band = EQBand(id = 1, type = EQBandType.LOW_PASS, frequencyHz = 1000f, gainDb = 0f, q = 0.707f)
        val b = BiquadCoeffs.forBand(band, sr)
        assertTrue("100 Hz should pass", magnitudeDb(b, 100f) > -3.0)
        assertTrue("10 kHz should roll off", magnitudeDb(b, 10_000f) < -15.0)
    }

    @Test fun `high pass at 1kHz blocks 100Hz`() {
        val band = EQBand(id = 1, type = EQBandType.HIGH_PASS, frequencyHz = 1000f, gainDb = 0f, q = 0.707f)
        val b = BiquadCoeffs.forBand(band, sr)
        assertTrue("100 Hz should be blocked", magnitudeDb(b, 100f) < -15.0)
        assertTrue("10 kHz should pass", magnitudeDb(b, 10_000f) > -3.0)
    }

    @Test fun `low cut is alias of high pass`() {
        val band1 = EQBand(id = 1, type = EQBandType.LOW_CUT, frequencyHz = 800f, gainDb = 0f, q = 0.707f)
        val band2 = band1.copy(type = EQBandType.HIGH_PASS)
        val b1 = BiquadCoeffs.forBand(band1, sr)
        val b2 = BiquadCoeffs.forBand(band2, sr)
        b1.reset(); b2.reset()
        for (n in 0 until 256) {
            val x = sin(2.0 * PI * 1234.0 * n / sr)
            assertEquals(b2.process(x), b1.process(x), 1e-9)
        }
    }

    @Test fun `low shelf at 80Hz with +6dB lifts the low end`() {
        val band = EQBand(id = 1, type = EQBandType.LOW_SHELF, frequencyHz = 80f, gainDb = 6f, q = 0.707f)
        val b = BiquadCoeffs.forBand(band, sr)
        val low = magnitudeDb(b, 40f)
        val high = magnitudeDb(b, 5_000f)
        assertTrue("Low lift low=$low high=$high", low > 4.0)
        assertTrue("High near 0dB", kotlin.math.abs(high) < 1.0)
    }

    @Test fun `tilt with positive gain lifts highs and cuts lows`() {
        val band = EQBand(id = 1, type = EQBandType.TILT, frequencyHz = 500f, gainDb = 4f, q = 0.5f)
        val chain = BiquadCoeffs.cascadeForBand(band, sr)

        fun cascadeGainDb(fHz: Float): Double {
            chain.forEach { it.reset() }
            val omega = 2.0 * PI * fHz / sr
            repeat(1024) { n ->
                var y = sin(omega * n)
                chain.forEach { y = it.process(y) }
            }
            var peak = 0.0
            for (n in 1024 until 1024 + 4096) {
                var y = sin(omega * n)
                chain.forEach { y = it.process(y) }
                if (kotlin.math.abs(y) > peak) peak = kotlin.math.abs(y)
            }
            return 20.0 * log10(peak.coerceAtLeast(1e-12))
        }
        assertTrue("Lows cut", cascadeGainDb(80f) < -2.0)
        assertTrue("Highs lifted", cascadeGainDb(8000f) > 2.0)
    }
}
