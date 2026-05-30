package com.example.recorderproject.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultsTest {
    @Test
    fun `recording defaults match spec`() {
        assertEquals("CUSTOM", Defaults.RECORDER_MODE)
        assertEquals("Unprocessed", Defaults.AUDIO_SOURCE_NAME)
        assertEquals("Unprocessed", Defaults.MIC_SOURCE_LABEL)
        assertEquals(0f, Defaults.INPUT_GAIN_DB)
        assertEquals(false, Defaults.NOISE_REDUCTION)
        assertEquals(48000, Defaults.SAMPLE_RATE_HZ)
        assertEquals(32, Defaults.BIT_DEPTH)   // 32-bit float capture is the documented default
        assertEquals(1, Defaults.CHANNEL_COUNT)
        assertEquals(0, Defaults.COUNTDOWN_SEC)
        assertEquals(0, Defaults.MAX_DURATION_MIN)
        assertEquals(0, Defaults.AUTO_STOP_MIN)
        assertEquals("Scene 1", Defaults.SCENE_NAME)
    }

    @Test
    fun `live toggles default off`() {
        assertEquals(false, Defaults.LIVE_NOISE_GATE)
        assertEquals(false, Defaults.AGC)
        assertEquals(false, Defaults.HI_PASS)
        assertEquals(false, Defaults.ANTI_CLIP)
        assertEquals(false, Defaults.COMPRESSOR)
        assertEquals(false, Defaults.STEREO_WIDENER)
        assertEquals(false, Defaults.VAD)
        assertEquals(false, Defaults.LIVE_EQ_ENABLED)
        assertEquals(6, Defaults.LIVE_EQ_BAND_GAINS.size)
        Defaults.LIVE_EQ_BAND_GAINS.forEach { assertEquals(0f, it) }
    }

    @Test
    fun `playback defaults match spec`() {
        assertEquals(1f, Defaults.PLAYBACK_SPEED)
        assertEquals(false, Defaults.PLAYBACK_LOOP)
        assertEquals(1f, Defaults.PLAYBACK_VOLUME)
    }

    @Test
    fun `misc defaults match spec`() {
        assertNull(Defaults.SAVE_DIRECTORY_URI)
        assertEquals(false, Defaults.GROUP_BY_SCENE)
        assertEquals(true, Defaults.LOCKSCREEN_CONTROLS)
        assertEquals(false, Defaults.CLOUD_BACKUP)
    }

    @Test
    fun `eq defaults match spec`() {
        assertEquals("PARAMETRIC", Defaults.EQ_MODE)
        assertEquals("TWO_D", Defaults.EQ_VIEW_MODE)
        assertEquals("BOTH", Defaults.EQ_APPLY_SAVE_MODE)
        assertEquals(false, Defaults.EQ_BYPASSED)
    }

    @Test fun defaults_include_loudness_target_podcast() {
        assertEquals("PODCAST", Defaults.defaultLoudnessTarget)
        assertEquals(-16f, Defaults.customLoudnessLufs, 0.001f)
        assertEquals(-1f, Defaults.customLoudnessTpCeiling, 0.001f)
    }
}
