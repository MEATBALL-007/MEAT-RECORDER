package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EQChainTest {

    @Test fun `empty chain has 8 disabled default bands`() {
        val chain = EQChain.empty()
        assertEquals(8, chain.bands.size)
        assertTrue(chain.bands.all { !it.enabled })
        assertEquals((1..8).toList(), chain.bands.map { it.id })
    }

    @Test fun `withBand replaces matching id`() {
        val chain = EQChain.empty()
        val updated = chain.bands[2].copy(gainDb = 6f, enabled = true)
        val newChain = chain.withBand(updated)
        assertEquals(6f, newChain.bands[2].gainDb, 0.001f)
        assertTrue(newChain.bands[2].enabled)
        assertFalse(newChain.bands[0].enabled)
    }

    @Test fun `withAddedBand fills first disabled slot`() {
        val chain = EQChain.empty()
        val newBand = EQBand(id = 99, type = EQBandType.BELL, frequencyHz = 440f, gainDb = 3f, q = 1.4f, enabled = true)
        val result = chain.withAddedBand(newBand)
        assertNotNull(result)
        assertTrue(result!!.bands[0].enabled)
        assertEquals(440f, result.bands[0].frequencyHz, 0.001f)
        assertEquals(1, result.bands[0].id)
    }

    @Test fun `withAddedBand returns null when 8 enabled`() {
        var chain = EQChain.empty()
        for (i in 1..8) {
            chain = chain.withBand(chain.bands[i - 1].copy(enabled = true))
        }
        val newBand = EQBand(id = 0, type = EQBandType.BELL, frequencyHz = 1000f, gainDb = 0f, q = 1f)
        assertNull(chain.withAddedBand(newBand))
    }
}
