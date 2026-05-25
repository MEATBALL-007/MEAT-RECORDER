package com.example.recorderproject.audio

import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType

/**
 * Mains-hum comb removal. Produces a chain of narrow notches at the chosen mains frequency
 * and its first 4 harmonics. Used by the one-tap "Hum Detect" button in noise-cut mode.
 */
object EQHumDetect {

    /** @param mainsHz 50 or 60 (Europe / US). */
    fun combNotches(mainsHz: Float = 60f, harmonics: Int = 4, q: Float = 12f): List<EQBand> =
        (1..harmonics).map { harm ->
            EQBand(
                id = harm,
                type = EQBandType.NOTCH,
                frequencyHz = (mainsHz * harm).coerceAtMost(20_000f),
                gainDb = 0f,
                q = q,
                enabled = true,
            )
        }
}
