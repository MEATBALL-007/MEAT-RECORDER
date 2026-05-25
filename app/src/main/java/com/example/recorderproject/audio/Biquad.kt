package com.example.recorderproject.audio

/**
 * Direct-form II transposed biquad filter.
 *
 * Difference equation:
 *   y[n] = b0*x[n] + s1[n-1]
 *   s1[n] = b1*x[n] - a1*y[n] + s2[n-1]
 *   s2[n] = b2*x[n] - a2*y[n]
 *
 * All coefficients pre-normalized by a0 (caller divides).
 */
class Biquad(
    private var b0: Double,
    private var b1: Double,
    private var b2: Double,
    private var a1: Double,
    private var a2: Double,
) {
    private var s1 = 0.0
    private var s2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + s1
        s1 = b1 * x - a1 * y + s2
        s2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        s1 = 0.0
        s2 = 0.0
    }

    fun setCoefficients(b0: Double, b1: Double, b2: Double, a1: Double, a2: Double) {
        this.b0 = b0; this.b1 = b1; this.b2 = b2; this.a1 = a1; this.a2 = a2
    }

    /** Returns [b0, b1, b2, a1, a2] for closed-form frequency response calculations. */
    fun coeffs(): DoubleArray = doubleArrayOf(b0, b1, b2, a1, a2)
}
