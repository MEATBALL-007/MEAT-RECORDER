package com.example.recorderproject.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BiquadTest {

    /** Identity coefficients (b0=1, all others 0) should pass samples through unchanged. */
    @Test fun `identity biquad is a no-op`() {
        val b = Biquad(b0 = 1.0, b1 = 0.0, b2 = 0.0, a1 = 0.0, a2 = 0.0)
        for (x in listOf(0.1, -0.5, 0.999, -0.999, 0.0)) {
            assertEquals(x, b.process(x), 1e-12)
        }
    }

    /** A unit-delay biquad (b1=1) outputs the previous input. */
    @Test fun `unit delay biquad delays by one sample`() {
        val b = Biquad(b0 = 0.0, b1 = 1.0, b2 = 0.0, a1 = 0.0, a2 = 0.0)
        assertEquals(0.0, b.process(0.7), 1e-12)
        assertEquals(0.7, b.process(0.0), 1e-12)
        assertEquals(0.0, b.process(0.3), 1e-12)
        assertEquals(0.3, b.process(0.0), 1e-12)
    }

    @Test fun `reset clears state`() {
        val b = Biquad(b0 = 0.0, b1 = 1.0, b2 = 0.0, a1 = 0.0, a2 = 0.0)
        b.process(0.5)
        b.reset()
        assertEquals(0.0, b.process(0.0), 1e-12)
    }

    @Test fun `coeffs returns the five coefficients`() {
        val b = Biquad(1.0, 2.0, 3.0, 4.0, 5.0)
        val c = b.coeffs()
        assertEquals(5, c.size)
        assertEquals(1.0, c[0], 0.0)
        assertEquals(2.0, c[1], 0.0)
        assertEquals(3.0, c[2], 0.0)
        assertEquals(4.0, c[3], 0.0)
        assertEquals(5.0, c[4], 0.0)
    }
}
