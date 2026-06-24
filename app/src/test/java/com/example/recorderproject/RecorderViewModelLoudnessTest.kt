package com.example.recorderproject

import com.example.recorderproject.model.LoudnessTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderViewModelLoudnessTest {

    @Test fun encode_decode_handles_all_targets() {
        val all = listOf(
            LoudnessTarget.Off,
            LoudnessTarget.Streaming,
            LoudnessTarget.Podcast,
            LoudnessTarget.Broadcast,
            LoudnessTarget.Custom(-19f, -2f),
        )
        for (t in all) {
            val (k, l, tp) = LoudnessTarget.encode(t)
            assertEquals(t, LoudnessTarget.decode(k, l, tp))
        }
    }

    @Test fun decode_unknown_key_returns_default() {
        assertEquals(LoudnessTarget.DEFAULT, LoudnessTarget.decode("BOGUS", 0f, 0f))
    }

    @Test fun off_target_has_null_lufs() {
        assertTrue(LoudnessTarget.Off.targetLufs == null)
    }
}
