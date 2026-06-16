@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.example.recorderproject.audio

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.data.SettingsSnapshot
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import com.example.recorderproject.model.EQChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Owns the recording **controls** (not yet the start/stop lifecycle) extracted from
 * RecorderViewModel (issue #13 step 7d, clean half): the live recording-DSP toggles
 * (noise gate / AGC / hi-pass / anti-clip / compressor / stereo widener), live-EQ
 * (enable + per-band gains), VAD, pre-roll, slate tone, and the countdown / max-duration
 * config. Pushes to the injected [recorder]; persists via [settings].
 *
 * The start/stop/pause lifecycle, live meters, and cue markers stay in the ViewModel for now
 * — they are fused to the file-library/cloud clusters and will fold in once those are extracted.
 *
 * @param isRecording reads the VM's current recording state.
 * @param onStartRecording invoked when VAD detects voice while idle (→ VM startRecording()).
 * @param currentChain reads the offline EQ chain (for live-EQ enable).
 * @param sampleRate current sample rate in Hz (for live-EQ chain push).
 */
class RecordingController(
    private val app: Context,
    private val scope: CoroutineScope,
    private val recorder: AudioRecorderManager,
    private val settings: SettingsDataStore,
    private val isHydrated: () -> Boolean,
    private val requirePro: (ProFeature) -> Boolean,
    private val isRecording: () -> Boolean,
    private val onStartRecording: () -> Unit,
    private val currentChain: () -> EQChain,
    private val sampleRate: () -> Int,
    private val voiceActivityDetector: VoiceActivityDetector,
    private val preRollCapture: PreRollCapture,
    private val preRollBuffer: PreRollBuffer,
) {
    // ---- Pre-roll ("pre-recording") buffer — Pro feature ----
    private val _preRollEnabled = MutableStateFlow(false)
    val preRollEnabled: StateFlow<Boolean> = _preRollEnabled

    fun togglePreRoll() {
        // Pre-roll is a Pro feature — gate enabling, allow turning off.
        if (!_preRollEnabled.value && !requirePro(ProFeature.PRE_ROLL_VAD)) return
        val on = !_preRollEnabled.value
        _preRollEnabled.value = on
        if (on) {
            preRollCapture.start()
            recorder.setPreRollBuffer(preRollBuffer)
            Toast.makeText(app, "Pre-roll on — last 5s captured to next take", Toast.LENGTH_SHORT).show()
        } else {
            preRollCapture.stop()
            preRollBuffer.clear()
            recorder.setPreRollBuffer(null)
            Toast.makeText(app, "Pre-roll off", Toast.LENGTH_SHORT).show()
        }
        if (isHydrated()) scope.launch { settings.setPreRollEnabled(on) }
    }

    // G2: Live noise gate while recording.
    private val _liveNoiseGateOn = MutableStateFlow(false)
    val liveNoiseGateOn: StateFlow<Boolean> = _liveNoiseGateOn
    fun toggleLiveNoiseGate() {
        _liveNoiseGateOn.value = !_liveNoiseGateOn.value
        recorder.setLiveNoiseGate(_liveNoiseGateOn.value, thresholdDb = -46f)
        if (isHydrated()) scope.launch { settings.setLiveNoiseGate(_liveNoiseGateOn.value) }
    }

    // G9: Auto Gain Control toggle.
    private val _agcOn = MutableStateFlow(false)
    val agcOn: StateFlow<Boolean> = _agcOn
    fun toggleAgc() {
        _agcOn.value = !_agcOn.value
        recorder.setAgc(_agcOn.value)
        if (isHydrated()) scope.launch { settings.setAgc(_agcOn.value) }
    }

    // G10: Hi-pass (rumble removal) toggle.
    private val _hiPassOn = MutableStateFlow(false)
    val hiPassOn: StateFlow<Boolean> = _hiPassOn
    fun toggleHiPass() {
        _hiPassOn.value = !_hiPassOn.value
        recorder.setHiPass(_hiPassOn.value)
        if (isHydrated()) scope.launch { settings.setHiPass(_hiPassOn.value) }
    }

    // G11: Anti-clipping auto-attenuator toggle.
    private val _antiClipOn = MutableStateFlow(false)
    val antiClipOn: StateFlow<Boolean> = _antiClipOn
    fun toggleAntiClip() {
        _antiClipOn.value = !_antiClipOn.value
        recorder.setAntiClip(_antiClipOn.value)
        if (isHydrated()) scope.launch { settings.setAntiClip(_antiClipOn.value) }
    }

    // G18: compressor live during record (uses existing MasterLimiter — toggle only).
    private val _compressorOn = MutableStateFlow(false)
    val compressorOn: StateFlow<Boolean> = _compressorOn
    fun toggleCompressor() {
        _compressorOn.value = !_compressorOn.value
        if (isHydrated()) scope.launch { settings.setCompressor(_compressorOn.value) }
    }

    // G19: stereo widener live (only matters when channelCount=2).
    private val _stereoWidenerOn = MutableStateFlow(false)
    val stereoWidenerOn: StateFlow<Boolean> = _stereoWidenerOn
    fun toggleStereoWidener() {
        _stereoWidenerOn.value = !_stereoWidenerOn.value
        if (isHydrated()) scope.launch { settings.setStereoWidener(_stereoWidenerOn.value) }
    }

    // VAD ("voice-activated record") — Smart-capture Pro bundle.
    private val _vadOn = MutableStateFlow(false)
    val vadOn: StateFlow<Boolean> = _vadOn
    fun toggleVad() {
        if (!_vadOn.value && !requirePro(ProFeature.PRE_ROLL_VAD)) return
        val on = !_vadOn.value
        _vadOn.value = on
        if (isHydrated()) scope.launch { settings.setVad(on) }
        if (on) {
            voiceActivityDetector.onVoiceDetected = {
                if (!isRecording()) {
                    Log.i(TAG, "VAD: voice detected — auto-starting recording")
                    onStartRecording()
                }
            }
            voiceActivityDetector.start(scope)
            Toast.makeText(app, "VAD on — recording will start when voice is detected", Toast.LENGTH_SHORT).show()
        } else {
            voiceActivityDetector.stop()
            voiceActivityDetector.onVoiceDetected = null
            Toast.makeText(app, "VAD off", Toast.LENGTH_SHORT).show()
        }
    }

    /** Pre-record countdown in seconds (0 = off). */
    private val _countdownSeconds = MutableStateFlow(0)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds
    fun updateCountdownSeconds(v: Int) {
        _countdownSeconds.value = v.coerceAtLeast(0)
        if (isHydrated()) scope.launch { settings.setCountdownSec(_countdownSeconds.value) }
    }

    /** Auto-stop after this many minutes of recording (0 = off). */
    private val _maxDurationMinutes = MutableStateFlow(0)
    val maxDurationMinutes: StateFlow<Int> = _maxDurationMinutes
    fun updateMaxDurationMinutes(v: Int) {
        _maxDurationMinutes.value = v.coerceAtLeast(0)
        if (isHydrated()) scope.launch { settings.setMaxDurationMin(_maxDurationMinutes.value) }
    }

    // Live EQ (real-time during recording).
    private val _liveEqEnabled = MutableStateFlow(false)
    val liveEqEnabled: StateFlow<Boolean> = _liveEqEnabled
    fun toggleLiveEq() {
        _liveEqEnabled.value = !_liveEqEnabled.value
        // Push the current chain into the recorder right now — if we're mid-recording, EQ takes
        // effect on the very next PCM buffer the AudioRecord loop reads.
        if (_liveEqEnabled.value) {
            recorder.setLiveEqChain(currentChain(), sampleRate().toFloat())
        } else {
            recorder.setLiveEqChain(null, sampleRate().toFloat())
        }
        Toast.makeText(app,
            if (_liveEqEnabled.value) "Live EQ on — applied in real time to recording"
            else "Live EQ off",
            Toast.LENGTH_SHORT).show()
        if (isHydrated()) scope.launch { settings.setLiveEqEnabled(_liveEqEnabled.value) }
    }

    /** Q1: live EQ band gains (6 bands: 60/200/500/1k/3k/10k Hz) in dB. */
    private val _liveEqBandGains = MutableStateFlow(FloatArray(6))
    val liveEqBandGains: StateFlow<FloatArray> = _liveEqBandGains
    private val eqBandGainsPersist = MutableSharedFlow<FloatArray>(extraBufferCapacity = 64)

    fun setLiveEqBand(band: Int, gainDb: Float) {
        val arr = _liveEqBandGains.value.copyOf()
        if (band in arr.indices) {
            arr[band] = gainDb.coerceIn(-12f, 12f)
            _liveEqBandGains.value = arr
            if (isHydrated()) eqBandGainsPersist.tryEmit(arr.copyOf())
            // Build an EQChain and push to recorder if recording.
            if (isRecording()) {
                val freqs = floatArrayOf(60f, 200f, 500f, 1000f, 3000f, 10000f)
                val bands = arr.mapIndexed { i, g ->
                    EQBand(
                        id = i + 1,
                        type = EQBandType.BELL,
                        frequencyHz = freqs[i],
                        gainDb = g,
                        q = 1.0f,
                        enabled = kotlin.math.abs(g) > 0.05f,
                    )
                }
                recorder.setLiveEqChain(
                    EQChain(bands = bands, bypassed = false),
                    sampleRate().toFloat(),
                )
            }
        }
    }

    /** J.3: Fire a 1 kHz, 1-second slate tone baked into the current recording. */
    fun fireSlateTone() {
        if (!isRecording()) {
            Toast.makeText(app, "Start recording first", Toast.LENGTH_SHORT).show()
            return
        }
        recorder.armSlateTone(1000)
        Toast.makeText(app, "Slate tone 1 kHz, 1s", Toast.LENGTH_SHORT).show()
    }

    /** Push the recording-DSP slice (gate/AGC/hi-pass/anti-clip) onto the recorder. */
    fun rewireRecorder() {
        recorder.setLiveNoiseGate(_liveNoiseGateOn.value, thresholdDb = -46f)
        recorder.setAgc(_agcOn.value)
        recorder.setHiPass(_hiPassOn.value)
        recorder.setAntiClip(_antiClipOn.value)
    }

    /** Hydrate the controls slice from a snapshot (in-memory) + apply the pre-roll side-effect. */
    fun applySnapshot(s: SettingsSnapshot) {
        _countdownSeconds.value = s.countdownSec
        _maxDurationMinutes.value = s.maxDurationMin
        _liveNoiseGateOn.value = s.liveNoiseGate
        _agcOn.value = s.agc
        _hiPassOn.value = s.hiPass
        _antiClipOn.value = s.antiClip
        _compressorOn.value = s.compressor
        _stereoWidenerOn.value = s.stereoWidener
        _vadOn.value = s.vad
        _liveEqEnabled.value = s.liveEqEnabled
        _liveEqBandGains.value = s.liveEqBandGains.copyOf()
        _preRollEnabled.value = s.preRollEnabled
        if (s.preRollEnabled) {
            preRollCapture.start()
            recorder.setPreRollBuffer(preRollBuffer)
        } else {
            preRollCapture.stop()
        }
    }

    /** Launch the debounced live-EQ-band-gains persistence collector. */
    fun start() {
        scope.launch {
            eqBandGainsPersist
                .debounce(150)
                .collect { settings.setLiveEqBandGains(it) }
        }
    }

    companion object { private const val TAG = "RecordingController" }
}
