package com.example.recorderproject.model

/**
 * A single band in an EQ chain.
 *
 * @param id stable identifier 1..8 used as UI key
 * @param frequencyHz center frequency in Hz, range 20f..20000f (log-mapped in UI)
 * @param gainDb gain in dB, range -24f..+24f; ignored for PASS/CUT/NOTCH/BAND_PASS
 * @param q resonance / bandwidth, range 0.1f..18f
 */
data class EQBand(
    val id: Int,
    val type: EQBandType,
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float,
    val enabled: Boolean = true,
    val soloed: Boolean = false,
    val muted: Boolean = false,
    val locked: Boolean = false,
) {
    companion object {
        const val MAX_BANDS = 8
        const val MIN_FREQ_HZ = 20f
        const val MAX_FREQ_HZ = 20000f
        const val MIN_GAIN_DB = -24f
        const val MAX_GAIN_DB = 24f
        const val MIN_Q = 0.1f
        const val MAX_Q = 18f

        /** Default log-spaced bell at the given slot, disabled. */
        fun defaultForSlot(id: Int): EQBand {
            val freqs = floatArrayOf(60f, 150f, 320f, 700f, 1500f, 3200f, 7000f, 12000f)
            val f = freqs.getOrElse(id - 1) { 1000f }
            return EQBand(
                id = id,
                type = EQBandType.BELL,
                frequencyHz = f,
                gainDb = 0f,
                q = 1.0f,
                enabled = false,
            )
        }
    }
}
