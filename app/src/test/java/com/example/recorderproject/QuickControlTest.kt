package com.example.recorderproject

import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.model.QuickControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickControlTest {
    @Test fun ids_are_unique() {
        val ids = QuickControl.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun fromId_roundtrips_every_entry() {
        for (c in QuickControl.entries) {
            assertEquals(c, QuickControl.fromId(c.id))
        }
    }

    @Test fun fromId_unknown_is_null() {
        assertNull(QuickControl.fromId("nope"))
    }

    @Test fun free_controls_have_no_feature() {
        assertNull(QuickControl.PAUSE.proFeature)
        assertNull(QuickControl.DROP_CUE.proFeature)
    }

    @Test fun vad_is_gated_on_pre_roll_vad() {
        assertEquals(ProFeature.PRE_ROLL_VAD, QuickControl.VAD.proFeature)
    }
}
