package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryResultTest {

    private val sample = DeliveryResult(
        integratedLufs = -16.1f,
        shortTermMaxLufs = -14.2f,
        momentaryMaxLufs = -11.8f,
        truePeakDbtp = -1.1f,
        lra = 7.3f,
        appliedGainDb = 5.4f,
        targetLufs = -16f,
        tpCeilingDbtp = -1f,
        passed = true,
        deliveryFile = "/sdcard/Recordings/scene_T01_delivery.wav",
        renderedAt = 1_716_000_000_000L,
    )

    @Test fun round_trip_json() {
        val json = DeliveryResult.toJson(sample)
        val parsed = DeliveryResult.fromJson(json)
        assertEquals(sample, parsed)
    }

    @Test fun off_target_round_trip() {
        val off = sample.copy(targetLufs = null, tpCeilingDbtp = null, passed = false)
        val parsed = DeliveryResult.fromJson(DeliveryResult.toJson(off))
        assertNull(parsed.targetLufs)
        assertNull(parsed.tpCeilingDbtp)
    }
}
