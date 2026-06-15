package com.example.recorderproject.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.MonitorLevel
import com.example.recorderproject.model.applyAudioSample
import com.example.recorderproject.model.applyDecayTick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns Phase-7 live pre-record monitoring: the [AudioMonitor] engine, its enable/level state,
 * the clip-hold + peak-decay bookkeeping, and the toggle flow (external-output safeguard →
 * chain wiring → level listener → decay tick → Bluetooth SCO). Extracted from
 * RecorderViewModel (issue #13 step 5). Android-coupled, so verified by compile + the app's
 * existing suite + device smoke rather than unit tests; the pure level math lives in
 * model/MonitorLevel.kt and is unit-tested separately.
 *
 * @param currentChain reads the current offline EQ chain to seed the monitor on start.
 * @param onMessage forwards a user-facing message (the VM turns it into a Toast).
 */
class MonitorManager(
    private val app: Context,
    private val scope: CoroutineScope,
    private val currentChain: () -> EQChain,
    private val onMessage: (String) -> Unit,
) {
    private val audioMonitor = AudioMonitor()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    private val _level = MutableStateFlow(MonitorLevel.Silent)
    val level: StateFlow<MonitorLevel> = _level

    private var clipUntilMs: Long = 0L
    private var decayJob: Job? = null

    /** Called from AudioMonitor's audio thread once per PCM buffer. */
    private fun onPcm(rmsDb: Float, peakDb: Float) {
        val now = System.currentTimeMillis()
        val update = applyAudioSample(
            curr = _level.value,
            newRmsDb = rmsDb,
            newPeakDb = peakDb,
            nowMs = now,
            clipUntilMs = clipUntilMs,
        )
        clipUntilMs = update.clipUntilMs
        _level.value = update.level
    }

    /**
     * Toggle monitoring on/off. On-start refuses (and messages) when no external output is
     * connected — monitoring through the phone speaker would feed back into the mic.
     */
    @Suppress("DEPRECATION") // legacy Bluetooth SCO APIs — no pre-API-31 replacement
    fun toggle() {
        val on = !_enabled.value
        if (on) {
            // Safeguard: refuse to start monitor when no headphones/earphones/BT/USB output
            // is connected. Without an external output, the monitor plays through the phone
            // speaker, which feeds back into the mic and ruins the recording.
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasExternalOutput = devices.any { d ->
                val t = d.type
                t == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                t == AudioDeviceInfo.TYPE_USB_HEADSET ||
                t == AudioDeviceInfo.TYPE_USB_DEVICE ||
                t == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    t == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    t == AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
            if (!hasExternalOutput) {
                onMessage("Plug in or connect headphones to use monitor — would cause echo through speaker")
                // Leave _enabled at its current value (false)
                return
            }
            audioMonitor.setChain(currentChain())
            // Wire level callback BEFORE start so the very first buffer is observed
            audioMonitor.setLevelListener(::onPcm)
            audioMonitor.start()
            // Peak-hold decay tick — every 50 ms, decay peakDb by 1 toward rmsDb
            decayJob?.cancel()
            decayJob = scope.launch {
                while (true) {
                    delay(50)
                    val now = System.currentTimeMillis()
                    val update = applyDecayTick(
                        curr = _level.value,
                        nowMs = now,
                        clipUntilMs = clipUntilMs,
                    )
                    clipUntilMs = update.clipUntilMs
                    _level.value = update.level
                }
            }
            // Also start Bluetooth SCO for BT earphone monitoring
            try {
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
            } catch (e: Exception) {
                Log.w(TAG, "Bluetooth SCO start failed: ${e.message}")
            }
            _enabled.value = true
            onMessage("Monitor on")
        } else {
            stop()
            onMessage("Monitor off")
        }
    }

    /**
     * Idempotent teardown: stop the engine + decay job, clear the listener, reset clip/level,
     * and stop Bluetooth SCO. Returns true if monitoring was running. Replaces the teardown
     * blocks that were previously duplicated at the toggle-off, start-recording and
     * factory-reset sites. Does NOT emit a message — callers decide whether/what to show.
     */
    @Suppress("DEPRECATION") // legacy Bluetooth SCO APIs — no pre-API-31 replacement
    fun stop(): Boolean {
        val wasRunning = _enabled.value
        audioMonitor.stop()
        // Cancel decay coroutine, clear listener (after stop so the run-loop has exited)
        decayJob?.cancel()
        decayJob = null
        audioMonitor.setLevelListener(null)
        clipUntilMs = 0L
        _level.value = MonitorLevel.Silent
        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isBluetoothScoOn = false
            am.stopBluetoothSco()
        } catch (_: Exception) {}
        _enabled.value = false
        return wasRunning
    }

    /** Release the engine (call from ViewModel.onCleared). */
    fun release() {
        try { audioMonitor.stop() } catch (_: Exception) {}
    }

    companion object { private const val TAG = "MonitorManager" }
}
