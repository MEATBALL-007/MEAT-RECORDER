package com.example.recorderproject.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorLevelTest {

    // ----- applyAudioSample -----

    @Test fun `audio sample with new peak above current peak raises peak`() {
        val curr = MonitorLevel(rmsDb = -30f, peakDb = -20f, clipped = false)
        val out = applyAudioSample(curr, newRmsDb = -25f, newPeakDb = -10f, nowMs = 1_000L, clipUntilMs = 0L)
        assertEquals(-25f, out.level.rmsDb, 0.001f)
        assertEquals(-10f, out.level.peakDb, 0.001f)
        assertFalse(out.level.clipped)
        assertEquals(0L, out.clipUntilMs)
    }

    @Test fun `audio sample with new peak below current peak holds the higher peak`() {
        val curr = MonitorLevel(rmsDb = -30f, peakDb = -10f, clipped = false)
        val out = applyAudioSample(curr, newRmsDb = -35f, newPeakDb = -20f, nowMs = 1_000L, clipUntilMs = 0L)
        assertEquals(-35f, out.level.rmsDb, 0.001f)
        assertEquals(-10f, out.level.peakDb, 0.001f) // held
    }

    @Test fun `audio sample above clip threshold sets clipped and schedules clip-clear 2s out`() {
        val curr = MonitorLevel.Silent
        val out = applyAudioSample(curr, newRmsDb = -3f, newPeakDb = 0.5f, nowMs = 10_000L, clipUntilMs = 0L)
        assertTrue(out.level.clipped)
        assertEquals(12_000L, out.clipUntilMs)
    }

    @Test fun `audio sample below clip threshold preserves an unexpired clip window`() {
        val curr = MonitorLevel(rmsDb = -20f, peakDb = -5f, clipped = true)
        val out = applyAudioSample(curr, newRmsDb = -20f, newPeakDb = -5f, nowMs = 10_500L, clipUntilMs = 12_000L)
        assertTrue(out.level.clipped) // 10.5s < 12s, still clipped
        assertEquals(12_000L, out.clipUntilMs)
    }

    @Test fun `audio sample below clip threshold after window expiry clears clipped`() {
        val curr = MonitorLevel(rmsDb = -20f, peakDb = -5f, clipped = true)
        val out = applyAudioSample(curr, newRmsDb = -20f, newPeakDb = -5f, nowMs = 13_000L, clipUntilMs = 12_000L)
        assertFalse(out.level.clipped) // 13s > 12s, expired
    }

    // ----- applyDecayTick -----

    @Test fun `decay tick reduces peak by default 1dB`() {
        val curr = MonitorLevel(rmsDb = -30f, peakDb = -10f, clipped = false)
        val out = applyDecayTick(curr, nowMs = 1_000L, clipUntilMs = 0L)
        assertEquals(-11f, out.level.peakDb, 0.001f)
        assertEquals(-30f, out.level.rmsDb, 0.001f) // RMS unchanged by decay
    }

    @Test fun `decay tick floor at rmsDb`() {
        val curr = MonitorLevel(rmsDb = -30f, peakDb = -29.5f, clipped = false)
        val out = applyDecayTick(curr, nowMs = 1_000L, clipUntilMs = 0L)
        assertEquals(-30f, out.level.peakDb, 0.001f) // floored, not -30.5
    }

    @Test fun `decay tick clears clipped after window expiry`() {
        val curr = MonitorLevel(rmsDb = -20f, peakDb = -5f, clipped = true)
        val out = applyDecayTick(curr, nowMs = 13_000L, clipUntilMs = 12_000L)
        assertFalse(out.level.clipped)
    }

    @Test fun `decay tick preserves clipped within window`() {
        val curr = MonitorLevel(rmsDb = -20f, peakDb = -5f, clipped = true)
        val out = applyDecayTick(curr, nowMs = 10_500L, clipUntilMs = 12_000L)
        assertTrue(out.level.clipped)
    }
}
