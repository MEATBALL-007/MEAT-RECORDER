package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EQChainJsonTest {

    @Test fun `round trip preserves every field`() {
        val original = EQChain.empty().withBand(
            EQBand(id = 3, type = EQBandType.NOTCH, frequencyHz = 1234.5f, gainDb = -3f, q = 8f,
                enabled = true, soloed = false, muted = true, locked = true)
        ).copy(bypassed = true, gainCompensation = true)

        val json = EQChainJson.toJsonString(original)
        val restored = EQChainJson.fromJsonString(json)
        assertEquals(original, restored)
    }

    @Test fun `corrupt JSON returns null`() {
        assertNull(EQChainJson.fromJsonString("not json"))
        assertNull(EQChainJson.fromJsonString("{\"bands\": [\"oops\"]}"))
    }
}
