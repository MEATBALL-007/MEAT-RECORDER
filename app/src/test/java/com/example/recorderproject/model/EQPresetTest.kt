package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EQPresetTest {

    @Test fun `there are exactly 10 presets`() {
        assertEquals(10, EQPresets.ALL.size)
    }

    @Test fun `every preset name is unique`() {
        val names = EQPresets.ALL.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun `Flat preset has all bands disabled`() {
        val flat = EQPresets.ALL.first { it.name == "Flat" }
        assertTrue(flat.bands.all { !it.enabled })
    }

    @Test fun `every preset has 8 bands and parameters in range`() {
        for (preset in EQPresets.ALL) {
            assertEquals("Preset ${preset.name} should have 8 bands", 8, preset.bands.size)
            for (b in preset.bands) {
                assertTrue("${preset.name}: freq ${b.frequencyHz} out of range",
                    b.frequencyHz in EQBand.MIN_FREQ_HZ..EQBand.MAX_FREQ_HZ)
                assertTrue("${preset.name}: gain ${b.gainDb} out of range",
                    b.gainDb in EQBand.MIN_GAIN_DB..EQBand.MAX_GAIN_DB)
                assertTrue("${preset.name}: Q ${b.q} out of range",
                    b.q in EQBand.MIN_Q..EQBand.MAX_Q)
            }
        }
    }

    @Test fun `expected preset names ship`() {
        val expected = setOf(
            "Flat", "Air Lift", "Vintage Console", "Pultec Smooth", "API Punch",
            "Massive Low", "Neve Warmth", "Broadcast Voice", "De-Ess", "Master Bus",
        )
        assertEquals(expected, EQPresets.ALL.map { it.name }.toSet())
    }
}
