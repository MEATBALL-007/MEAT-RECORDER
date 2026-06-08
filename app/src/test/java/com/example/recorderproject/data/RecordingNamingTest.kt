package com.example.recorderproject.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingNamingTest {

    @Test fun `baseOf strips the extension`() {
        assertEquals("REC_001", RecordingNaming.baseOf("REC_001.wav"))
        assertEquals("REC_001", RecordingNaming.baseOf("REC_001"))
    }

    @Test fun `hasNr detects the noise-reduction suffix case-insensitively`() {
        assertTrue(RecordingNaming.hasNr("take1_nr"))
        assertTrue(RecordingNaming.hasNr("take1_NR"))
        assertFalse(RecordingNaming.hasNr("take1"))
    }

    @Test fun `isDeliverySibling detects the delivery suffix`() {
        assertTrue(RecordingNaming.isDeliverySibling("take1_delivery"))
        assertFalse(RecordingNaming.isDeliverySibling("take1"))
    }

    @Test fun `nrShadowedBases lists originals that have an nr twin`() {
        assertEquals(setOf("take1"), RecordingNaming.nrShadowedBases(listOf("take1", "take1_nr", "take2")))
    }

    @Test fun `nrShadowedBases is empty when no nr twins exist`() {
        assertEquals(emptySet<String>(), RecordingNaming.nrShadowedBases(listOf("take1", "take2")))
    }

    @Test fun `isHidden hides delivery siblings`() {
        assertTrue(RecordingNaming.isHidden("take1_delivery", emptySet()))
    }

    @Test fun `isHidden hides an original shadowed by its nr twin`() {
        assertTrue(RecordingNaming.isHidden("take1", setOf("take1")))
    }

    @Test fun `isHidden keeps the nr file itself and unrelated files`() {
        assertFalse(RecordingNaming.isHidden("take1_nr", setOf("take1")))
        assertFalse(RecordingNaming.isHidden("take2", setOf("take1")))
    }

    @Test fun `sidecar name builders match the on-disk convention`() {
        assertEquals("take1_eq.json", RecordingNaming.eqSidecarName("take1"))
        assertEquals("take1_delivery.wav", RecordingNaming.deliveryWavName("take1"))
        assertEquals("take1_delivery.json", RecordingNaming.deliveryJsonName("take1"))
    }

    @Test fun `isOrphanDelivery is true only for a delivery with no original and no sidecar`() {
        assertTrue(RecordingNaming.isOrphanDelivery("take1_delivery", originalBases = emptySet(), hasSidecarJson = false))
        assertFalse(RecordingNaming.isOrphanDelivery("take1_delivery", originalBases = setOf("take1"), hasSidecarJson = false))
        assertFalse(RecordingNaming.isOrphanDelivery("take1_delivery", originalBases = emptySet(), hasSidecarJson = true))
        assertFalse(RecordingNaming.isOrphanDelivery("take1", originalBases = emptySet(), hasSidecarJson = false))
    }
}
