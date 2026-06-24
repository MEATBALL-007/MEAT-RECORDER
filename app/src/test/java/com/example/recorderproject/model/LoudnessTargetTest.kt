package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessTargetTest {

    @Test fun default_is_off_with_null_targets() {
        val t = LoudnessTarget.DEFAULT
        assertTrue(t is LoudnessTarget.Off)
        assertNull(t.targetLufs)
        assertNull(t.tpCeilingDbtp)
    }

    @Test fun off_has_null_target_and_ceiling() {
        val t: LoudnessTarget = LoudnessTarget.Off
        assertNull(t.targetLufs)
        assertNull(t.tpCeilingDbtp)
    }

    @Test fun custom_carries_user_values() {
        val t = LoudnessTarget.Custom(lufs = -19.5f, tpDbtp = -1.5f)
        assertEquals(-19.5f, t.targetLufs)
        assertEquals(-1.5f, t.tpCeilingDbtp)
    }

    @Test fun display_names_match_spec() {
        assertEquals("Streaming –14",  LoudnessTarget.Streaming.displayName)
        assertEquals("Podcast –16",    LoudnessTarget.Podcast.displayName)
        assertEquals("Broadcast –23",  LoudnessTarget.Broadcast.displayName)
        assertEquals("Off",            LoudnessTarget.Off.displayName)
        assertEquals("Custom –19.5",   LoudnessTarget.Custom(-19.5f, -1f).displayName)
    }

    @Test fun encode_decode_round_trip() {
        val cases = listOf(
            LoudnessTarget.Off,
            LoudnessTarget.Streaming,
            LoudnessTarget.Podcast,
            LoudnessTarget.Broadcast,
            LoudnessTarget.Custom(-21f, -2.5f),
        )
        for (t in cases) {
            val (key, lufs, tp) = LoudnessTarget.encode(t)
            val decoded = LoudnessTarget.decode(key, lufs, tp)
            assertEquals(t, decoded)
        }
    }
}
