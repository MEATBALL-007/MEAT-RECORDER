package com.example.recorderproject.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/** Generate a random-but-musical EQ chain — useful for ideation / palette breaking. */
object EQRandomPreset {

    /** Returns a chain with 3-5 enabled bands at log-spaced random freqs with modest gains. */
    fun generate(seed: Long = System.currentTimeMillis()): EQChain {
        val rnd = Random(seed)
        val numEnabled = rnd.nextInt(3, 6)
        val typeChoices = listOf(EQBandType.BELL, EQBandType.LOW_SHELF, EQBandType.HIGH_SHELF, EQBandType.BELL, EQBandType.BELL)
        val bands = (1..8).map { id ->
            if (id <= numEnabled) {
                // Place log-uniformly in 60..14k
                val t = rnd.nextDouble()
                val freq = exp(ln(60.0) + t * (ln(14_000.0) - ln(60.0))).toFloat()
                EQBand(
                    id = id,
                    type = typeChoices.random(rnd),
                    frequencyHz = freq.coerceIn(EQBand.MIN_FREQ_HZ, EQBand.MAX_FREQ_HZ),
                    gainDb = (rnd.nextFloat() * 12f - 6f).coerceIn(EQBand.MIN_GAIN_DB, EQBand.MAX_GAIN_DB),
                    q = (rnd.nextFloat() * 2f + 0.5f).coerceIn(EQBand.MIN_Q, EQBand.MAX_Q),
                    enabled = true,
                )
            } else {
                EQBand.defaultForSlot(id)
            }
        }
        return EQChain(bands = bands)
    }
}
