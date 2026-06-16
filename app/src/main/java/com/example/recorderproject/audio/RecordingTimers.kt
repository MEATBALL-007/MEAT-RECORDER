package com.example.recorderproject.audio

import android.content.Context
import android.widget.Toast
import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the three recording-related coroutine timers extracted from RecorderViewModel
 * (issue #13 step 7b):
 *  - scheduled start (fire a callback at an absolute epoch time),
 *  - auto-stop / Pomodoro (stop after N minutes of recording),
 *  - free-tier recording limit (warn then stop + paywall for non-Pro users).
 *
 * Jobs run on the injected [scope] (the ViewModel's scope), so they are cancelled
 * automatically when the ViewModel clears — no explicit release needed. Android-coupled
 * (Toast), so verified by compile + the app's existing suite + device smoke.
 *
 * @param isRecording reads the VM's current recording state.
 * @param isPro reads the current Pro-entitlement state.
 * @param onLimitReached invoked when a free user hits the recording limit (→ VM stopRecording + paywall).
 */
class RecordingTimers(
    private val app: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsDataStore,
    private val isHydrated: () -> Boolean,
    private val isRecording: () -> Boolean,
    private val isPro: () -> Boolean,
    private val onLimitReached: () -> Unit,
) {
    // G12: Recording schedule — start at a given absolute time (epoch ms). 0 = off.
    private val _scheduledStartMs = MutableStateFlow(0L)
    val scheduledStartMs: StateFlow<Long> = _scheduledStartMs
    private var scheduleJob: Job? = null

    fun setScheduledStart(epochMs: Long, onFire: () -> Unit) {
        _scheduledStartMs.value = epochMs
        scheduleJob?.cancel()
        if (epochMs <= 0) return
        scheduleJob = scope.launch {
            val waitMs = (epochMs - System.currentTimeMillis()).coerceAtLeast(0)
            delay(waitMs)
            if (_scheduledStartMs.value == epochMs) onFire()
        }
    }

    fun cancelSchedule() { scheduleJob?.cancel(); _scheduledStartMs.value = 0L }

    // G22: Pomodoro / auto-stop timer that fires while recording. 0 = off.
    private val _autoStopMinutes = MutableStateFlow(0)
    val autoStopMinutes: StateFlow<Int> = _autoStopMinutes
    private var autoStopJob: Job? = null

    fun setAutoStopMinutes(m: Int) {
        _autoStopMinutes.value = m.coerceAtLeast(0)
        if (isHydrated()) scope.launch { settings.setAutoStopMin(_autoStopMinutes.value) }
    }

    /** Called by the start-recording flow to arm the timer. */
    fun armAutoStop(onFire: () -> Unit) {
        autoStopJob?.cancel()
        val mins = _autoStopMinutes.value
        if (mins <= 0) return
        autoStopJob = scope.launch {
            delay(mins * 60_000L)
            if (isRecording()) onFire()
        }
    }

    fun cancelAutoStop() { autoStopJob?.cancel() }

    private var recordingLimitJob: Job? = null

    /** Free-tier limit: warn near the cap, then stop recording + open the paywall. */
    fun startFreeTierLimit() {
        recordingLimitJob?.cancel()
        if (isPro()) return
        val limitMs = ProFeature.FREE_RECORDING_LIMIT_SECONDS * 1000L
        val warnMs  = (ProFeature.FREE_RECORDING_LIMIT_SECONDS -
                       ProFeature.FREE_RECORDING_WARN_SECONDS) * 1000L
        val warnMinutes = ProFeature.FREE_RECORDING_WARN_SECONDS / 60
        recordingLimitJob = scope.launch {
            delay(warnMs)
            if (isRecording() && !isPro()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        app,
                        "$warnMinutes minute left — upgrade to Pro for unlimited recording",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            delay(limitMs - warnMs)
            if (isRecording() && !isPro()) {
                onLimitReached()
            }
        }
    }

    fun cancelFreeTierLimit() {
        recordingLimitJob?.cancel()
        recordingLimitJob = null
    }

    /** Hydrate persisted auto-stop minutes from a snapshot (in-memory only). */
    fun applySnapshot(autoStopMin: Int) {
        _autoStopMinutes.value = autoStopMin
    }
}
