package com.example.recorderproject.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Phase 7 — brick-wall master limiter.
 *
 * Look-ahead-free limiter using fast attack / slower release envelope follower.
 * Designed for the output stage of the EQ render pipeline.
 */
class MasterLimiter(
    sampleRate: Float,
    private val ceilingDb: Float = -0.3f,
    attackMs: Float = 1.0f,
    releaseMs: Float = 80f,
) {
    private val ceiling = exp(ln(10.0) * ceilingDb / 20.0).toFloat()
    private val attackCoef = exp(-1.0 / (sampleRate * attackMs / 1000.0)).toFloat()
    private val releaseCoef = exp(-1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()
    private var envelope = 0f

    fun process(x: Float): Float {
        val abs = abs(x)
        val target = if (abs > ceiling) ceiling / abs else 1f
        // Asymmetric one-pole envelope: snappy attack, slower release
        envelope = if (target < envelope) {
            target + (envelope - target) * attackCoef
        } else {
            target + (envelope - target) * releaseCoef
        }
        return x * envelope
    }
}
