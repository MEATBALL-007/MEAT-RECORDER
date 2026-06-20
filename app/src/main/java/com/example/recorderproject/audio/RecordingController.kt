@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.example.recorderproject.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.widget.Toast
import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.data.FileLibrary
import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.model.CuePoint
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQChainJson
import com.example.recorderproject.model.RecordFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * The recording engine: owns the live recording-DSP controls AND the start/stop/pause
 * lifecycle extracted from RecorderViewModel (issue #13 step 7d controls + step 9 lifecycle).
 *
 * It orchestrates the other collaborators (audio focus, timers, naming, file library, monitor,
 * audio config, EQ editor, loudness, VAD, pre-roll) — the ViewModel declares this AFTER all of
 * them and passes real references. A few cross-cluster tails stay in the ViewModel via seams:
 * [onError] (general error flow), [saveDirectoryUri] (save-location cluster), [onTakeSaved]
 * (Google-Drive + SAF cloud backup), and [rescanFromDisk] (recovery rescan).
 *
 * Android-coupled (AudioRecord/foreground service/Toast) so verified by compile + device smoke;
 * the JVM unit suite does not exercise this path.
 */
class RecordingController(
    private val app: Context,
    private val scope: CoroutineScope,
    private val recorder: AudioRecorderManager,
    private val settings: SettingsDataStore,
    private val isHydrated: () -> Boolean,
    private val requirePro: (ProFeature) -> Boolean,
    // Collaborators (real refs — the VM declares this controller after all of them).
    private val audioConfig: AudioInputConfig,
    private val eqEditor: EqEditor,
    private val naming: TakeNaming,
    private val fileLibrary: FileLibrary,
    private val loudness: LoudnessManager,
    private val monitorManager: MonitorManager,
    private val audioFocus: AudioFocusController,
    private val timers: RecordingTimers,
    private val voiceActivityDetector: VoiceActivityDetector,
    private val preRollCapture: PreRollCapture,
    private val preRollBuffer: PreRollBuffer,
    private val noiseProcessor: NoiseReductionProcessor,
    // ViewModel-owned seams.
    private val hydrated: StateFlow<Boolean>,
    private val saveDirectoryUri: () -> Uri?,
    private val noiseReductionEnabled: () -> Boolean,
    private val onError: (String?) -> Unit,
    private val onTakeSaved: (finalFile: RecordFile, capturedFilePath: String?) -> Unit,
    private val rescanFromDisk: () -> Unit,
) {
    // ====================== Recording state ======================

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    /** F2: paused-while-recording state. When true, the recording loop still runs but
     *  written-sample count is held — the file ends up with no data while paused. */
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused
    fun togglePause() {
        if (!_isRecording.value) return
        _isPaused.value = !_isPaused.value
        recorder.setPaused(_isPaused.value)
    }

    // Live meters — populated only while recording.
    private val _currentWaveform = MutableStateFlow<List<Float>>(emptyList())
    val currentWaveform: StateFlow<List<Float>> = _currentWaveform
    private val _liveSpectrum = MutableStateFlow(FloatArray(0))
    val liveSpectrum: StateFlow<FloatArray> = _liveSpectrum
    private val _livePitchHz = MutableStateFlow(0f)
    val livePitchHz: StateFlow<Float> = _livePitchHz
    private val _liveLufs = MutableStateFlow(-70f)
    val liveLufs: StateFlow<Float> = _liveLufs
    private val _liveTpDbTp = MutableStateFlow(Float.NEGATIVE_INFINITY)
    val liveTpDbTp: StateFlow<Float> = _liveTpDbTp
    private val _liveRawPeakDbfs = MutableStateFlow(Float.NEGATIVE_INFINITY)
    val liveRawPeakDbfs: StateFlow<Float> = _liveRawPeakDbfs
    private val _phaseCorrelation = MutableStateFlow(0f)
    val phaseCorrelation: StateFlow<Float> = _phaseCorrelation
    private val _spectrumHistory = MutableStateFlow<List<FloatArray>>(emptyList())
    val spectrumHistory: StateFlow<List<FloatArray>> = _spectrumHistory

    @Volatile private var pendingLocationTag: String? = null

    // ====================== Controls ======================

    // ---- Pre-roll ("pre-recording") buffer — Pro feature ----
    private val _preRollEnabled = MutableStateFlow(false)
    val preRollEnabled: StateFlow<Boolean> = _preRollEnabled

    fun togglePreRoll() {
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
                if (!_isRecording.value) {
                    Log.i(TAG, "VAD: voice detected — auto-starting recording")
                    startRecording()
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
        if (_liveEqEnabled.value) {
            recorder.setLiveEqChain(eqEditor.currentEQChain.value, audioConfig.sampleRate.value.toFloat())
        } else {
            recorder.setLiveEqChain(null, audioConfig.sampleRate.value.toFloat())
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
            if (_isRecording.value) {
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
                    audioConfig.sampleRate.value.toFloat(),
                )
            }
        }
    }

    /** J.3: Fire a 1 kHz, 1-second slate tone baked into the current recording. */
    fun fireSlateTone() {
        if (!_isRecording.value) {
            Toast.makeText(app, "Start recording first", Toast.LENGTH_SHORT).show()
            return
        }
        recorder.armSlateTone(1000)
        Toast.makeText(app, "Slate tone 1 kHz, 1s", Toast.LENGTH_SHORT).show()
    }

    // ====================== Cue markers ======================

    private val _liveCueCount = MutableStateFlow(0)
    val liveCueCount: StateFlow<Int> = _liveCueCount
    private val pendingCues = mutableListOf<CuePoint>()
    private var recordingStartMs = 0L

    fun dropCueMarker(label: String = "") {
        if (!_isRecording.value) return
        val tMs = System.currentTimeMillis() - recordingStartMs
        pendingCues.add(CuePoint(timeMs = tMs, label = label))
        _liveCueCount.value = pendingCues.size
    }

    // ====================== Validation ======================

    private fun validateRecordingData(): Boolean {
        val fileName = naming.fileName.value.trim()
        val sceneName = naming.sceneName.value.trim()
        if (fileName.isEmpty()) {
            onError("Please enter a file name before recording.")
            Toast.makeText(app, "Please enter a file name", Toast.LENGTH_LONG).show()
            return false
        }
        val sanitized = fileName.replace("[^A-Za-z0-9_.-]".toRegex(), "_")
        if (sanitized.isEmpty()) {
            onError("File name contains invalid characters.")
            Toast.makeText(app, "File name contains invalid characters", Toast.LENGTH_LONG).show()
            return false
        }
        if (sceneName.isEmpty()) {
            onError("Please enter a scene name before recording.")
            Toast.makeText(app, "Please enter a scene name", Toast.LENGTH_LONG).show()
            return false
        }
        if (audioConfig.sampleRate.value !in listOf(44100, 48000, 96000)) {
            onError("Sample rate is not supported.")
            Toast.makeText(app, "Sample rate is not supported", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    /**
     * Returns null if there's enough free space to safely start a recording, or a
     * human-readable error string. Rule: refuse if free space < 60s-at-current-quality.
     */
    private fun checkDiskSpaceOrError(): String? {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val bytesPerSec = audioConfig.sampleRate.value.toLong() *
                (audioConfig.bitDepth.value / 8) *
                audioConfig.channelCount.value
            val minBytes = bytesPerSec * 60L
            if (freeBytes < minBytes) {
                val freeMb = freeBytes / (1024L * 1024L)
                val minMb = minBytes / (1024L * 1024L)
                "Not enough free space (${freeMb} MB free, need at least ${minMb} MB for 60s at current quality)"
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Disk space check failed: ${e.message}")
            null
        }
    }

    // ====================== Lifecycle ======================

    fun startRecording() {
        if (!hydrated.value) {
            // Hydration races against an early Record tap. Wait up to 500ms.
            scope.launch {
                try {
                    withTimeout(500) { hydrated.filter { it }.first() }
                    startRecordingInternal()
                } catch (e: Exception) {
                    Log.w(TAG, "Hydration timed out — starting with current state")
                    startRecordingInternal()
                }
            }
            return
        }
        startRecordingInternal()
    }

    @Suppress("DEPRECATION")  // legacy Bluetooth SCO APIs — no pre-API-31 replacement
    private fun startRecordingInternal() {
        if (_vadOn.value) voiceActivityDetector.stop()
        Log.d(TAG, "startRecording() called")
        // Safeguard: if monitor is on, stop it before recording (acoustic feedback loop).
        if (monitorManager.stop()) {
            Toast.makeText(app, "Monitor stopped to prevent echo while recording", Toast.LENGTH_SHORT).show()
        }
        // If pre-roll is on, stop the capture thread so the main AudioRecord can open the mic.
        if (preRollCapture.isRunning()) {
            preRollCapture.stop()
        }
        if (_isRecording.value) {
            Log.d(TAG, "Already recording, ignoring")
            return
        }
        if (!validateRecordingData()) return
        checkDiskSpaceOrError()?.let { msg ->
            onError(msg)
            Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
            return
        }
        // Feature I: countdown beep before recording
        val countdown = _countdownSeconds.value
        if (countdown > 0) {
            scope.launch {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                    for (i in countdown downTo 1) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(app, "Recording in ${i}…", Toast.LENGTH_SHORT).show()
                        }
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                        delay(1000L)
                    }
                    tg.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Countdown beep failed: ${e.message}")
                }
                startRecordingNow()
            }
            return
        }
        startRecordingNow()
    }

    private fun startRecordingNow() {
        _isRecording.value = true
        scope.launch { try { settings.setIsRecording(true) } catch (_: Exception) {} }
        audioFocus.requestFocus() // Best-effort — don't block recording if denied
        audioFocus.registerBecomingNoisy()
        onError(null)
        recorder.setAudioSource(audioConfig.audioSource.value)
        // Phase 7: real-time EQ during recording — push current chain if Live EQ is on.
        if (_liveEqEnabled.value) {
            recorder.setLiveEqChain(eqEditor.currentEQChain.value, audioConfig.sampleRate.value.toFloat())
        } else {
            recorder.setLiveEqChain(null, audioConfig.sampleRate.value.toFloat())
        }
        recordingStartMs = System.currentTimeMillis()
        // H: snapshot device location for metadata (null if permission denied / no fix)
        pendingLocationTag = LocationCapture.snapshot(app)
        Log.d(TAG, "Set audio source to: ${audioConfig.audioSource.value}, location=${pendingLocationTag ?: "n/a"}")
        try {
            Log.d(TAG, "Calling recorder.start()")
            recorder.start(
                fileName = naming.fileName.value,
                sampleRate = audioConfig.sampleRate.value,
                saveDirectoryUri = saveDirectoryUri(),
            ) { level ->
                _currentWaveform.value = level
            }
            // Q2: start foreground service so recording survives screen-off
            try {
                val intent = Intent(app, RecordingForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start foreground service: ${e.message}")
            }
            // M1/M2: wire live spectrum + pitch listeners
            recorder.setSpectrumListener { bands ->
                _liveSpectrum.value = bands
                val hist = _spectrumHistory.value.toMutableList()
                hist.add(bands)
                if (hist.size > 80) hist.removeAt(0) // ~80 frames rolling window
                _spectrumHistory.value = hist
            }
            recorder.setPitchListener { hz -> _livePitchHz.value = hz }
            recorder.setLufsListener { lufs -> _liveLufs.value = lufs }
            recorder.setTruePeakListener { tp -> _liveTpDbTp.value = tp }
            recorder.setRawPeakListener { db -> _liveRawPeakDbfs.value = db }
            recorder.setPhaseListener { c -> _phaseCorrelation.value = c }
            recorder.setErrorListener { err ->
                scope.launch(Dispatchers.Main) {
                    val msg = when (err) {
                        is SecurityException -> "Recording stopped: save folder permission was revoked"
                        is java.io.IOException -> "Recording stopped: disk write failed (${err.message})"
                        else -> "Recording stopped due to error: ${err.message}"
                    }
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                    onError(msg)
                    if (_isRecording.value) {
                        try { stopRecording() } catch (_: Exception) {}
                    }
                }
            }
            Log.d(TAG, "Recording started successfully")
            Toast.makeText(app, "Recording started", Toast.LENGTH_SHORT).show()
            timers.startFreeTierLimit()
            // Persist active-take path so a crash-then-relaunch can recover it
            scope.launch {
                try {
                    val activePath = recorder.currentFilePath()
                    if (activePath != null) settings.setActiveRecordingPath(activePath)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not persist active recording path: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            onError("Failed to start recording: ${e.message}")
            _isRecording.value = false
            Toast.makeText(app, "Recording failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun stopRecording() {
        Log.d(TAG, "stopRecording() called")
        if (!_isRecording.value) {
            Log.d(TAG, "Not recording, ignoring")
            return
        }
        timers.cancelFreeTierLimit()
        audioFocus.abandonFocus()
        audioFocus.unregisterBecomingNoisy()

        // M1/M2: clear listeners + reset live state
        recorder.setSpectrumListener(null)
        recorder.setPitchListener(null)
        recorder.setLufsListener(null)
        recorder.setTruePeakListener(null)
        recorder.setRawPeakListener(null)
        recorder.setPhaseListener(null)
        recorder.setErrorListener(null)
        _liveSpectrum.value = FloatArray(0)
        _livePitchHz.value = 0f
        _liveLufs.value = -70f
        _liveTpDbTp.value = Float.NEGATIVE_INFINITY
        _liveRawPeakDbfs.value = Float.NEGATIVE_INFINITY
        _phaseCorrelation.value = 0f
        _spectrumHistory.value = emptyList()

        // Q2: stop foreground service
        try {
            app.stopService(Intent(app, RecordingForegroundService::class.java))
        } catch (_: Exception) {}

        val capturedScene = naming.sceneName.value
        val capturedNotes = naming.notes.value
        // Capture path before stop() clears it — used for Drive backup after finalize
        val capturedFilePath = recorder.currentFilePath()

        scope.launch {
            try {
                Log.d(TAG, "Calling recorder.stop()")
                val recordedFile = withContext(Dispatchers.IO) {
                    recorder.stop(sceneName = capturedScene, notes = capturedNotes)
                }
                Log.d(TAG, "Recorder stopped, file: ${recordedFile.name}")
                val renamedPath = withContext(Dispatchers.IO) {
                    recorder.autoRenameFile(recordedFile.path, capturedScene, capturedNotes)
                }
                val wasRenamed = renamedPath != recordedFile.path
                val renamedFile = if (wasRenamed) {
                    val newFile = File(renamedPath)
                    recordedFile.copy(name = newFile.name, path = newFile.absolutePath)
                } else {
                    recordedFile
                }.copy(locationTag = pendingLocationTag ?: recordedFile.locationTag)

                // Single toast: merge auto-rename + save-location info.
                if (saveDirectoryUri() == null) {
                    val msg = if (wasRenamed)
                        "✅ Auto-named & saved to App Storage: ${renamedFile.name}\nTip: Set a Save Location in Settings to choose your folder."
                    else
                        "✅ Saved to App Storage: ${renamedFile.name}\nTip: Set a Save Location in Settings to choose your folder."
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                } else if (wasRenamed) {
                    Toast.makeText(app, "Auto-named file: ${renamedFile.name}", Toast.LENGTH_SHORT).show()
                }

                // Surface the take in the list NOW — the WAV is on disk and playable. NR / EQ
                // below can be slow or throw; adding here (then swapping via provisionalId)
                // ensures a post-processing failure can't make the take silently vanish.
                val provisionalId = renamedFile.id
                fileLibrary.add(renamedFile)
                _isRecording.value = false
                try { settings.setIsRecording(false) } catch (_: Exception) {}
                _currentWaveform.value = emptyList()
                if (_vadOn.value) {
                    voiceActivityDetector.onVoiceDetected = {
                        if (!_isRecording.value) startRecording()
                    }
                    voiceActivityDetector.start(scope)
                }

                val nrFile = if (noiseReductionEnabled()) {
                    Log.d(TAG, "Applying noise reduction")
                    withContext(Dispatchers.IO) { noiseProcessor.process(renamedFile) }
                } else {
                    renamedFile
                }

                // Phase 7: real-time Live EQ — chain was baked into the PCM. Mark hasEQ + write
                // the sidecar JSON so re-opening shows the chain.
                val finalFile = if (_liveEqEnabled.value && recorder.isLiveEqActive() &&
                    !nrFile.path.startsWith("content://")
                ) {
                    try {
                        val srcFile = File(nrFile.path)
                        File(srcFile.parentFile, srcFile.nameWithoutExtension + "_eq.json")
                            .writeText(EQChainJson.toJsonString(eqEditor.currentEQChain.value))
                        nrFile.copy(hasEQ = true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Live EQ sidecar write failed: ${e.message}", e)
                        nrFile.copy(hasEQ = true)
                    }
                } else nrFile

                // Recorder no longer needs the chain after the take
                recorder.setLiveEqChain(null, audioConfig.sampleRate.value.toFloat())

                // Swap the provisional entry for the fully-processed final file.
                fileLibrary.update { it.filterNot { f -> f.id == provisionalId } + finalFile }

                // D: kick off delivery render if a target is active
                loudness.renderFor(finalFile)

                scope.launch {
                    try { settings.setActiveRecordingPath(null) } catch (_: Exception) {}
                }
                // If pre-roll was enabled, restart the capture thread for the next take.
                if (_preRollEnabled.value) {
                    preRollCapture.start()
                }
                // Auto-bump take number for next take in the same scene
                naming.refreshAutoFileName()
                Log.d(TAG, "Recording stopped successfully")
                Toast.makeText(app, "Recording saved: ${finalFile.name}", Toast.LENGTH_SHORT).show()
                // Google-Drive + SAF cloud backup stay in the ViewModel (cloud cluster).
                onTakeSaved(finalFile, capturedFilePath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording: ${e.message}", e)
                onError("Failed to stop recording: ${e.message}")
                _isRecording.value = false
                try { settings.setIsRecording(false) } catch (_: Exception) {}
                _currentWaveform.value = emptyList()
                // Safety net: the WAV was crash-safe-finalized to disk; rescan so the take
                // still appears in the list rather than silently lost.
                try { rescanFromDisk() } catch (_: Exception) {}
                Toast.makeText(app, "Failed to save recording: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ====================== Snapshot / rewire / lifecycle plumbing ======================

    /** Push the recording-DSP slice (gate/AGC/hi-pass/anti-clip) onto the recorder. */
    fun rewireRecorder() {
        recorder.setLiveNoiseGate(_liveNoiseGateOn.value, thresholdDb = -46f)
        recorder.setAgc(_agcOn.value)
        recorder.setHiPass(_hiPassOn.value)
        recorder.setAntiClip(_antiClipOn.value)
    }

    /** Hydrate the controls slice from a snapshot (in-memory) + apply the pre-roll side-effect. */
    fun applySnapshot(s: com.example.recorderproject.data.SettingsSnapshot) {
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
