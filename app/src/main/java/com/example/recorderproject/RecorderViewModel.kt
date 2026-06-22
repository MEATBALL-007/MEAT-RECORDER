package com.example.recorderproject

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recorderproject.audio.AudioRecorderManager
import com.example.recorderproject.audio.TranscriptionEngine
import com.example.recorderproject.audio.VoiceActivityDetector
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.data.Defaults
import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.data.SettingsSnapshot
import com.example.recorderproject.model.ApplySaveMode
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQEditMode
import com.example.recorderproject.model.EQViewMode
import com.example.recorderproject.model.MonitorLevel
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.model.RecordingQuality
import com.example.recorderproject.model.SortOrder
import com.example.recorderproject.model.DeliveryResult
import com.example.recorderproject.model.LoudnessTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import android.widget.Toast
import java.io.File

class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "RecorderViewModel"
    private val app = application
    private val recorder = AudioRecorderManager(application.applicationContext)
    private val settings = SettingsDataStore(application)

    // ---- Monetization: MEAT REC Pro one-time unlock ----
    val entitlements = com.example.recorderproject.billing.EntitlementStore(application.applicationContext)
    val billing = com.example.recorderproject.billing.BillingManager(application.applicationContext, entitlements)
    /** True when the user owns Pro (or debug-forced). Gate Pro features on this. */
    val isPro: StateFlow<Boolean> = entitlements.isPro

    /** When a free user taps a locked feature, this holds it so the UI shows the paywall. Null = closed. */
    private val _paywallFeature = MutableStateFlow<com.example.recorderproject.billing.ProFeature?>(null)
    val paywallFeature: StateFlow<com.example.recorderproject.billing.ProFeature?> = _paywallFeature
    fun openPaywall(feature: com.example.recorderproject.billing.ProFeature) { _paywallFeature.value = feature }
    fun closePaywall() { _paywallFeature.value = null }

    /**
     * Gate a Pro feature. Returns true and runs nothing extra if the user is Pro;
     * otherwise opens the paywall for [feature] and returns false. Callers should
     * `if (!requirePro(X)) return` before doing the gated work.
     */
    fun requirePro(feature: com.example.recorderproject.billing.ProFeature): Boolean {
        if (isPro.value) return true
        openPaywall(feature)
        return false
    }
    private val voiceActivityDetector = VoiceActivityDetector(
        context = app.applicationContext,
        thresholdDb = -38f,
        triggerWindowMs = 250,
        cooldownMs = 3000,
    )
    private val transcriptionEngine = TranscriptionEngine(app.applicationContext)
    // Cloud backup (Drive + SAF folder) → CloudBackup (issue #13 step 10).
    private val cloudBackup = com.example.recorderproject.data.CloudBackup(
        app = app,
        scope = viewModelScope,
        settings = settings,
        isHydrated = { hydrated.value },
        requirePro = ::requirePro,
    )
    val isDriveSignedIn: StateFlow<Boolean> = cloudBackup.isDriveSignedIn
    fun refreshDriveSignInState() = cloudBackup.refreshSignInState()
    val cloudBackupOn: StateFlow<Boolean> = cloudBackup.cloudBackupOn
    fun toggleCloudBackup() = cloudBackup.toggle()
    val cloudBackupUri: StateFlow<android.net.Uri?> = cloudBackup.cloudBackupUri
    fun setCloudBackupUri(uri: android.net.Uri?) = cloudBackup.setUri(uri)
    fun backupToDrive(filePath: String) = cloudBackup.backupToDrive(filePath)

    // ---- Feature #1: Pre-roll buffer ----
    private val preRollBuffer = com.example.recorderproject.audio.PreRollBuffer(
        sampleRate = 48_000, seconds = 5, channels = 1,
    )
    private val preRollCapture = com.example.recorderproject.audio.PreRollCapture(
        buffer = preRollBuffer,
        sampleRate = 48_000,
    )

    // Recording controls (DSP toggles / live-EQ / VAD / pre-roll / slate / countdown) →
    // RecordingController (issue #13 step 7d, clean half). The start/stop lifecycle stays in
    // the VM for now. currentChain/sampleRate/onStartRecording defer-resolve (lambdas).
    // RecordingController (recording engine: controls + lifecycle) is declared LOWER, after all
    // its collaborator dependencies (audioFocus/timers/naming/monitorManager/fileLibrary/…).
    // See the "Recording engine" section below.

    // ---- Feature #2: Stop broadcast receiver (notification Stop button) ----
    private val stopBroadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == com.example.recorderproject.audio.RecordingForegroundService.ACTION_STOP_RECORDING) {
                Log.i(TAG, "Stop broadcast received — stopping recording")
                if (recordingController.isRecording.value) {
                    recordingController.stopRecording()
                }
            }
        }
    }
    private var stopReceiverRegistered = false
    // Library scanning extracted into RecordingScanner (issue #13). Declared before the init
    // block (which calls the scan functions), so it is initialized in time. Only needs `app`.
    private val scanner = com.example.recorderproject.data.RecordingScanner(app)
    private val hydrated = MutableStateFlow(false)

    // Audio-input configuration extracted into AudioInputConfig (issue #13 step 6). Owns the
    // sample rate / bit depth / channel count / input gain / audio source / mic label / quality
    // StateFlows + the USB/BT device detector. Declared before eqEditor (which reads
    // audioConfig.sampleRate). Pushes config to `recorder` (5 setters).
    private val audioConfig = com.example.recorderproject.audio.AudioInputConfig(
        app = app,
        scope = viewModelScope,
        settings = settings,
        isHydrated = { hydrated.value },
        requirePro = ::requirePro,
        recorder = recorder,
    )
    val externalInputDevices: StateFlow<List<com.example.recorderproject.audio.UsbAudioDetector.UsbDevice>> = audioConfig.externalInputDevices

    // Offline EQ editor extracted into EqEditor (issue #13 step 4). Declared before the init
    // block (hydration calls eqEditor.applySnapshot). Constructor lambdas defer-resolve
    // recorder / _liveEqEnabled / audioConfig / _recordFiles / _saveDirectoryUri (legal — they
    // run only on later EQ calls).
    private val eqEditor = com.example.recorderproject.audio.EqEditor(
        app = app,
        settings = settings,
        scope = viewModelScope,
        isHydrated = { hydrated.value },
        requirePro = ::requirePro,
        sampleRate = { audioConfig.sampleRate.value },
        markFileHasEq = { id -> fileLibrary.updateById(id) { it.copy(hasEQ = true) } },
        saveDirectoryUri = { _saveDirectoryUri.value },
    )

    // Playback + A/B compare extracted into PlaybackManager (issue #13). Declared before the
    // init block (hydration's applySnapshot delegates here). The constructor lambdas
    // defer-resolve hydrated / _errorMessage (declared later) — legal, they run only on
    // later playback calls.
    private val playback = com.example.recorderproject.audio.PlaybackManager(
        app = app,
        settings = settings,
        scope = viewModelScope,
        isHydrated = { hydrated.value },
        onError = { _errorMessage.value = it },
    )

    init {
        audioConfig.start()
        billing.start()
        viewModelScope.launch {
            try {
                val snapshot = settings.snapshot()
                applySnapshot(snapshot)
                // Re-claim SAF URI grant if we have one persisted
                snapshot.saveDirectoryUri?.let { uriStr ->
                    try {
                        val uri = Uri.parse(uriStr)
                        app.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Persisted SAF URI no longer granted, clearing: ${e.message}")
                        _saveDirectoryUri.value = null
                        settings.setSaveDirectoryUri(null)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                app,
                                "⚠️ Save folder permission expired — recordings will be saved to App Storage. Go to Settings → Save Location to re-select your folder.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
                // Recovery: if a previous session died while recording, the WAV is now
                // playable (PR1 periodic finalize) — surface it in the recordings list.
                val activePath = settings.getActiveRecordingPath()
                if (activePath != null) {
                    val recoveredFile = java.io.File(activePath)
                    if (recoveredFile.exists() && recoveredFile.length() > 44L) {
                        // Build a RecordFile entry — best-effort metadata
                        val durationSeconds = try {
                            val mmr = android.media.MediaMetadataRetriever()
                            mmr.setDataSource(activePath)
                            val ms = mmr.extractMetadata(
                                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                            )?.toLongOrNull() ?: 0L
                            mmr.release()
                            (ms / 1000L).toInt()
                        } catch (_: Exception) { 0 }
                        val recovered = RecordFile(
                            id = java.util.UUID.randomUUID().toString(),
                            name = recoveredFile.name,
                            path = recoveredFile.absolutePath,
                            durationSeconds = durationSeconds,
                            sceneName = "Recovered",
                        )
                        fileLibrary.add(recovered)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(app, "Recovered take from previous session: ${recoveredFile.name}",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                    // Clear the marker either way — we've handled it (or the file doesn't exist)
                    try { settings.setActiveRecordingPath(null) } catch (_: Exception) {}
                }
                // Detect zombie recording: previous session was recording when destroyed.
                val wasRecording = try { settings.getIsRecording() } catch (_: Exception) { false }
                if (wasRecording) {
                    // The take is recoverable via PR2 logic; clear the flag here.
                    // The user can review the recovered file in the recordings list.
                    try { settings.setIsRecording(false) } catch (_: Exception) {}
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "Previous recording recovered — check Recordings list", Toast.LENGTH_LONG).show()
                    }
                }
                // Populate _recordFiles from disk on launch — without this, the user can
                // only see files created in the current session.
                scanRecordingsFromDisk()
                // Also scan the SAF (folder-picker) save location, if one is set —
                // otherwise recordings saved there disappear from the list after an app
                // restart, since scanRecordingsFromDisk() only reads internal storage.
                scanSafRecordings()
                rewireRecorderFromState()
                // Update default file name to next take for the loaded scene
                refreshAutoFileName()
            } catch (e: Exception) {
                Log.e(TAG, "Settings hydration failed: ${e.message}", e)
            } finally {
                hydrated.value = true
                // Register stop-recording broadcast (used by notification Stop button)
                if (!stopReceiverRegistered) {
                    val filter = android.content.IntentFilter(
                        com.example.recorderproject.audio.RecordingForegroundService.ACTION_STOP_RECORDING
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        app.registerReceiver(stopBroadcastReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        @Suppress("UnspecifiedRegisterReceiverFlag")
                        app.registerReceiver(stopBroadcastReceiver, filter)
                    }
                    stopReceiverRegistered = true
                }
            }
        }
    }

    // Recordings list + sort/search/filter/selection → FileLibrary (issue #13 step 8).
    private val prefs = application.getSharedPreferences("meatrec_ui", 0)
    private val fileLibrary = com.example.recorderproject.data.FileLibrary(viewModelScope, prefs)
    val recordFiles: StateFlow<List<RecordFile>> = fileLibrary.recordFiles
    val sortOrder: StateFlow<SortOrder> = fileLibrary.sortOrder
    val sortedRecordFiles: StateFlow<List<RecordFile>> = fileLibrary.sortedRecordFiles
    val visibleRecordFiles: StateFlow<List<RecordFile>> = fileLibrary.visibleRecordFiles
    val searchQuery: StateFlow<String> = fileLibrary.searchQuery
    val fileFilter: StateFlow<com.example.recorderproject.model.FileFilter> = fileLibrary.fileFilter
    val selectedFileIds: StateFlow<Set<String>> = fileLibrary.selectedFileIds

    fun setSortOrder(order: SortOrder) = fileLibrary.setSortOrder(order)
    fun setSearchQuery(q: String) = fileLibrary.setSearchQuery(q)
    fun setFileFilter(f: com.example.recorderproject.model.FileFilter) = fileLibrary.setFileFilter(f)
    fun toggleFileSelection(id: String) = fileLibrary.toggleFileSelection(id)
    fun clearSelection() = fileLibrary.clearSelection()
    fun selectAll() = fileLibrary.selectAll()

    fun deleteSelected() {
        val ids = fileLibrary.selectedFileIds.value
        if (ids.isEmpty()) return
        fileLibrary.current().filter { it.id in ids }.forEach { deleteRecording(it) }
        fileLibrary.clearSelection()
    }

    // All recording controls + lifecycle delegations live in the "Recording engine" section below.

    // G8: Quality presets — delegated to AudioInputConfig (issue #13 step 6).
    val quality: StateFlow<com.example.recorderproject.model.RecordingQuality> = audioConfig.quality
    fun setQuality(q: com.example.recorderproject.model.RecordingQuality) = audioConfig.setQuality(q)

    // Recording timers (schedule / auto-stop / free-tier limit) → RecordingTimers (#13 step 7b).
    // onLimitReached defer-resolves stopRecording/openPaywall (declared later — legal).
    private val timers = com.example.recorderproject.audio.RecordingTimers(
        app = app,
        scope = viewModelScope,
        settings = settings,
        isHydrated = { hydrated.value },
        isRecording = { recordingController.isRecording.value },
        isPro = { isPro.value },
        onLimitReached = {
            recordingController.stopRecording()
            openPaywall(com.example.recorderproject.billing.ProFeature.RECORDING_LIMIT)
        },
    )

    // G12: Recording schedule — start at a given absolute time (epoch ms). 0 = off.
    val scheduledStartMs: StateFlow<Long> = timers.scheduledStartMs
    fun setScheduledStart(epochMs: Long, onFire: () -> Unit) = timers.setScheduledStart(epochMs, onFire)
    fun cancelSchedule() = timers.cancelSchedule()

    // G13: VAD (voice-activity detection) — auto-start recording when input rises.
    // G14: Tag list per file (lightweight — stored alongside RecordFile.tags string).
    fun addTagToFile(file: RecordFile, tag: String) {
        if (tag.isBlank()) return
        val existing = file.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (existing.contains(tag)) return
        existing += tag
        val merged = existing.joinToString(",")
        fileLibrary.updateById(file.id) { it.copy(tags = merged) }
    }
    fun removeTagFromFile(file: RecordFile, tag: String) {
        val existing = file.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != tag }
        val merged = existing.joinToString(",")
        fileLibrary.updateById(file.id) { it.copy(tags = merged) }
    }

    // G15: Auto-name from scene + date/time when filename is left blank.
    fun autoNameForNextTake(): String = naming.autoNameForNextTake()

    // Live meters (waveform/spectrum/pitch/LUFS/true-peak/raw-peak) + pendingLocationTag →
    // RecordingController (recording engine, below).

    // D: loudness/delivery extracted into LoudnessManager (issue #13). The constructor lambda
    // and method reference defer-resolve _saveDirectoryUri / rebindDeliveryResult (declared later)
    // — legal because their bodies execute only when renderFor() is later called.
    private val loudness = com.example.recorderproject.audio.LoudnessManager(
        app = app,
        settings = settings,
        scope = viewModelScope,
        saveDirectoryUri = { _saveDirectoryUri.value },
        onDeliveryResult = ::rebindDeliveryResult,
    )
    val loudnessTarget: StateFlow<LoudnessTarget> = loudness.target
    val isRenderingDelivery: StateFlow<Boolean> = loudness.isRendering
    val lastDeliveryResult: StateFlow<DeliveryResult?> = loudness.lastResult

    // phaseCorrelation + spectrumHistory → RecordingController (recording engine, below).

    // Take/scene/file naming → TakeNaming (#13 step 7c).
    private val naming = com.example.recorderproject.audio.TakeNaming(
        app = app,
        scope = viewModelScope,
        settings = settings,
        isHydrated = { hydrated.value },
    )
    val fileName: StateFlow<String> = naming.fileName
    val sceneName: StateFlow<String> = naming.sceneName
    val notes: StateFlow<String> = naming.notes

    // Noise reduction → NoiseReduction (issue #13 step 12). Declared before recordingController
    // (which reads enabled + calls process at stop time) and recorderModeManager (NR default).
    private val noiseReduction = com.example.recorderproject.audio.NoiseReduction(
        app = app,
        scope = viewModelScope,
        settings = settings,
        isHydrated = { hydrated.value },
        fileLibrary = fileLibrary,
        saveDirectoryUri = { _saveDirectoryUri.value },
    )
    val noiseReductionEnabled: StateFlow<Boolean> = noiseReduction.enabled
    fun toggleNoiseReduction(enabled: Boolean) = noiseReduction.setEnabled(enabled)
    fun applyNoiseReduce(file: RecordFile) = noiseReduction.applyTo(file)

    // Audio-input config delegated to AudioInputConfig (issue #13 step 6).
    val sampleRate: StateFlow<Int> = audioConfig.sampleRate
    val bitDepth: StateFlow<Int> = audioConfig.bitDepth
    fun updateBitDepth(v: Int) = audioConfig.updateBitDepth(v)

    val channelCount: StateFlow<Int> = audioConfig.channelCount
    fun updateChannelCount(v: Int) = audioConfig.updateChannelCount(v)

    /** Pre-record countdown in seconds (0 = off). */

    // Input gain (Phase 3): linear multiplier applied before EQ in the recording loop.
    // Delegated to AudioInputConfig (issue #13 step 6).
    val inputGainDb: StateFlow<Float> = audioConfig.inputGainDb
    fun updateInputGainDb(db: Float) = audioConfig.updateInputGainDb(db)

    // ------------- Phase 7: Live monitoring (Bluetooth earphone / wired) -------------
    // Delegated to MonitorManager (issue #13 step 5). currentChain reads the offline EQ
    // chain (defer-resolves eqEditor, declared earlier); onMessage routes toasts.
    private val monitorManager = com.example.recorderproject.audio.MonitorManager(
        app = app,
        scope = viewModelScope,
        currentChain = { eqEditor.currentEQChain.value },
        onMessage = { msg -> Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() },
    )

    // ------------- PR3: Audio focus + headphone-unplug handling -------------
    // Delegated to AudioFocusController (issue #13 step 7a). onFocusLost defer-resolves
    // stopRecording (declared later — legal, runs only on a later focus-loss event).
    private val audioFocus = com.example.recorderproject.audio.AudioFocusController(
        app = app,
        isRecording = { recordingController.isRecording.value },
        onFocusLost = { recordingController.stopRecording() },
    )

    // Monitoring state delegated to MonitorManager (issue #13 step 5).
    val monitorEnabled: StateFlow<Boolean> = monitorManager.enabled
    val monitorLevel: StateFlow<MonitorLevel> = monitorManager.level

    // ============ Recording engine (controls + lifecycle) → RecordingController ============
    // Declared here, AFTER all its collaborator dependencies (issue #13 steps 7 + 9). Explicit
    // type so eqEditor's liveChainSink (which forward-references recordingController.liveEqEnabled)
    // resolves without recursive type inference. Cloud/Drive backup, the general error flow, the
    // save-dir, and the recovery rescan stay in the VM via seams.
    private val recordingController: com.example.recorderproject.audio.RecordingController =
        com.example.recorderproject.audio.RecordingController(
            app = app,
            scope = viewModelScope,
            recorder = recorder,
            settings = settings,
            isHydrated = { hydrated.value },
            requirePro = ::requirePro,
            audioConfig = audioConfig,
            eqEditor = eqEditor,
            naming = naming,
            fileLibrary = fileLibrary,
            loudness = loudness,
            monitorManager = monitorManager,
            audioFocus = audioFocus,
            timers = timers,
            voiceActivityDetector = voiceActivityDetector,
            preRollCapture = preRollCapture,
            preRollBuffer = preRollBuffer,
            noiseReduction = noiseReduction,
            hydrated = hydrated,
            saveDirectoryUri = { _saveDirectoryUri.value },
            onError = { _errorMessage.value = it },
            onTakeSaved = cloudBackup::onTakeSaved,
            rescanFromDisk = ::scanRecordingsFromDisk,
        )

    // Lifecycle delegations.
    val isRecording: StateFlow<Boolean> = recordingController.isRecording
    val isPaused: StateFlow<Boolean> = recordingController.isPaused
    fun togglePause() = recordingController.togglePause()
    val currentWaveform: StateFlow<List<Float>> = recordingController.currentWaveform
    val liveSpectrum: StateFlow<FloatArray> = recordingController.liveSpectrum
    val livePitchHz: StateFlow<Float> = recordingController.livePitchHz
    val liveLufs: StateFlow<Float> = recordingController.liveLufs
    val liveTpDbTp: StateFlow<Float> = recordingController.liveTpDbTp
    val liveRawPeakDbfs: StateFlow<Float> = recordingController.liveRawPeakDbfs
    val phaseCorrelation: StateFlow<Float> = recordingController.phaseCorrelation
    val spectrumHistory: StateFlow<List<FloatArray>> = recordingController.spectrumHistory
    val liveCueCount: StateFlow<Int> = recordingController.liveCueCount
    fun dropCueMarker(label: String = "") = recordingController.dropCueMarker(label)
    fun startRecording() = recordingController.startRecording()
    fun stopRecording() = recordingController.stopRecording()

    // Control delegations.
    val preRollEnabled: StateFlow<Boolean> = recordingController.preRollEnabled
    fun togglePreRoll() = recordingController.togglePreRoll()
    val liveNoiseGateOn: StateFlow<Boolean> = recordingController.liveNoiseGateOn
    fun toggleLiveNoiseGate() = recordingController.toggleLiveNoiseGate()
    val agcOn: StateFlow<Boolean> = recordingController.agcOn
    fun toggleAgc() = recordingController.toggleAgc()
    val hiPassOn: StateFlow<Boolean> = recordingController.hiPassOn
    fun toggleHiPass() = recordingController.toggleHiPass()
    val antiClipOn: StateFlow<Boolean> = recordingController.antiClipOn
    fun toggleAntiClip() = recordingController.toggleAntiClip()
    val compressorOn: StateFlow<Boolean> = recordingController.compressorOn
    fun toggleCompressor() = recordingController.toggleCompressor()
    val stereoWidenerOn: StateFlow<Boolean> = recordingController.stereoWidenerOn
    fun toggleStereoWidener() = recordingController.toggleStereoWidener()
    val vadOn: StateFlow<Boolean> = recordingController.vadOn
    fun toggleVad() = recordingController.toggleVad()
    val countdownSeconds: StateFlow<Int> = recordingController.countdownSeconds
    fun updateCountdownSeconds(v: Int) = recordingController.updateCountdownSeconds(v)
    val maxDurationMinutes: StateFlow<Int> = recordingController.maxDurationMinutes
    fun updateMaxDurationMinutes(v: Int) = recordingController.updateMaxDurationMinutes(v)
    val liveEqEnabled: StateFlow<Boolean> = recordingController.liveEqEnabled
    fun toggleLiveEq() = recordingController.toggleLiveEq()
    val liveEqBandGains: StateFlow<FloatArray> = recordingController.liveEqBandGains
    fun setLiveEqBand(band: Int, gainDb: Float) = recordingController.setLiveEqBand(band, gainDb)
    fun fireSlateTone() = recordingController.fireSlateTone()

    init {
        // Runs after recordingController is initialized (second init block, declared below it).
        recordingController.start()
        // Wire EQ edits → recorder during live-EQ recording.
        eqEditor.liveChainSink = { chain ->
            if (recordingController.liveEqEnabled.value) {
                recorder.setLiveEqChain(chain, audioConfig.sampleRate.value.toFloat())
            }
        }
    }

    // Mic-source label delegated to AudioInputConfig (issue #13 step 6).
    val micSourceLabel: StateFlow<String> = audioConfig.micSourceLabel

    fun toggleMonitor() = monitorManager.toggle()

    fun setMicSource(label: String) = audioConfig.setMicSource(label)

    /** Select a specific hardware input device (USB, BT, wired headset). */
    fun selectInputDevice(device: com.example.recorderproject.audio.UsbAudioDetector.UsbDevice) =
        audioConfig.selectInputDevice(device)

    /** Clear hardware device preference — fall back to OS default for chosen AudioSource. */
    fun clearInputDevice() = audioConfig.clearInputDevice()

    // Save directory
    private val _saveDirectoryUri = MutableStateFlow<Uri?>(null)
    val saveDirectoryUri: StateFlow<Uri?> = _saveDirectoryUri

    // A/B compare state — delegated to PlaybackManager (issue #13).
    val abFiles: StateFlow<Pair<com.example.recorderproject.model.RecordFile, com.example.recorderproject.model.RecordFile>?> = playback.abFiles
    val abPlayingSlot: StateFlow<Int> = playback.abPlayingSlot
    val abCompareOpen: StateFlow<Boolean> = playback.abCompareOpen

    fun openAbCompare(a: com.example.recorderproject.model.RecordFile, b: com.example.recorderproject.model.RecordFile) =
        playback.openAbCompare(a, b)

    fun closeAbCompare() = playback.closeAbCompare()

    /** Play slot A or B. Stops any current playback then plays the requested file. */
    fun abPlay(slot: Int) = playback.abPlay(slot)

    fun abStop() = playback.abStop()

    /** Open A/B compare with the two currently selected files. Requires exactly 2. */
    fun openAbCompareFromSelection() {
        if (!requirePro(com.example.recorderproject.billing.ProFeature.ANALYSIS_TOOLS)) return
        val ids = fileLibrary.selectedFileIds.value
        if (ids.size != 2) {
            Toast.makeText(app, "Select exactly 2 files to compare", Toast.LENGTH_SHORT).show()
            return
        }
        val files = fileLibrary.current().filter { it.id in ids }
        if (files.size != 2) return
        openAbCompare(files[0], files[1])
        clearSelection()
    }

    // Playback states — delegated to PlaybackManager (issue #13).
    val isPlaying: StateFlow<Boolean> = playback.isPlaying
    val currentPlaybackPosition: StateFlow<Int> = playback.currentPlaybackPosition
    val playbackDuration: StateFlow<Int> = playback.playbackDuration
    val selectedFile: StateFlow<RecordFile?> = playback.selectedFile

    // Audio source delegated to AudioInputConfig (issue #13 step 6).
    val audioSource: StateFlow<Int> = audioConfig.audioSource
    val audioSourceName: StateFlow<String> = audioConfig.audioSourceName

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _needsPermission = MutableStateFlow(false)
    val needsPermission: StateFlow<Boolean> = _needsPermission

    val isPlayerReady: StateFlow<Boolean> = playback.isPlayerReady

    // ------------- EQ state — delegated to EqEditor (issue #13 step 4) -------------
    val currentEQChain: StateFlow<EQChain> = eqEditor.currentEQChain
    val eqMode: StateFlow<EQEditMode> = eqEditor.eqMode
    val eqViewMode: StateFlow<EQViewMode> = eqEditor.eqViewMode
    val eqSelectedBandId: StateFlow<Int?> = eqEditor.eqSelectedBandId
    val eqSnapshot: StateFlow<EQChain?> = eqEditor.eqSnapshot
    val eqApplySaveMode: StateFlow<ApplySaveMode> = eqEditor.eqApplySaveMode
    val eqRenderProgress: StateFlow<Float> = eqEditor.eqRenderProgress
    val eqSourceFile: StateFlow<RecordFile?> = eqEditor.eqSourceFile
    val eqSourceSpectrum: StateFlow<StaticSpectrum?> = eqEditor.eqSourceSpectrum
    val eqBypassed: StateFlow<Boolean> = eqEditor.eqBypassed
    /** True while the EQ screen should be shown — MainActivity observes for nav. */
    val eqOpen: StateFlow<Boolean> = eqEditor.eqOpen

    /** Phase C: which file's harmonic portrait is currently open (null = none). */
    private val _portraitFile = MutableStateFlow<RecordFile?>(null)
    val portraitFile: StateFlow<RecordFile?> = _portraitFile
    fun openPortrait(file: RecordFile) {
        if (!requirePro(com.example.recorderproject.billing.ProFeature.ANALYSIS_TOOLS)) return
        _portraitFile.value = file
    }
    fun closePortrait() { _portraitFile.value = null }

    /** Phase D: ghost-take file overlaid on next recording (null = no ghost). */
    private val _ghostTakeFile = MutableStateFlow<RecordFile?>(null)
    val ghostTakeFile: StateFlow<RecordFile?> = _ghostTakeFile
    fun setGhostTake(file: RecordFile?) { _ghostTakeFile.value = file }

    /** Phase D: is the multi-take comparison screen open? */
    private val _multiTakeOpen = MutableStateFlow(false)
    val multiTakeOpen: StateFlow<Boolean> = _multiTakeOpen
    fun openMultiTake() {
        if (!requirePro(com.example.recorderproject.billing.ProFeature.ANALYSIS_TOOLS)) return
        _multiTakeOpen.value = true
    }
    fun closeMultiTake() { _multiTakeOpen.value = false }

    /** G4: menu screen open? */
    private val _menuOpen = MutableStateFlow(false)
    val menuOpen: StateFlow<Boolean> = _menuOpen
    fun openMenu() { _menuOpen.value = true }
    fun closeMenu() { _menuOpen.value = false }

    /** G16: stats screen open? */
    private val _statsOpen = MutableStateFlow(false)
    val statsOpen: StateFlow<Boolean> = _statsOpen
    fun openStats() { _statsOpen.value = true }
    fun closeStats() { _statsOpen.value = false }

    /** Design picker (temporary): swipe-through mockups of 4 design directions. */
    private val _designPickerOpen = MutableStateFlow(false)
    val designPickerOpen: StateFlow<Boolean> = _designPickerOpen
    fun openDesignPicker() { _designPickerOpen.value = true }
    fun closeDesignPicker() { _designPickerOpen.value = false }


    // N2: recording mode + preset application → RecorderModeManager (issue #13 step 11).
    private val recorderModeManager = com.example.recorderproject.audio.RecorderModeManager(
        scope = viewModelScope,
        settings = settings,
        isHydrated = { hydrated.value },
        audioConfig = audioConfig,
        setNoiseReduction = { noiseReduction.setEnabledInMemory(it) },
    )
    val recorderMode: StateFlow<com.example.recorderproject.model.RecorderMode> = recorderModeManager.recorderMode

    // ── Customizable workspace ──────────────────────────────────────────
    val workspace = com.example.recorderproject.workspace.WorkspaceManager(
        settings = settings,
        scope = viewModelScope,
        isPro = isPro,
        mode = recorderMode,
    )
    val workspaceLayout: StateFlow<com.example.recorderproject.model.WorkspaceLayout> = workspace.layout

    private val _customizeOpen = MutableStateFlow(false)
    val customizeOpen: StateFlow<Boolean> = _customizeOpen
    fun openCustomize() { _customizeOpen.value = true }
    fun closeCustomize() { _customizeOpen.value = false }

    fun saveWorkspace(layout: com.example.recorderproject.model.WorkspaceLayout) = workspace.save(layout)
    fun resetWorkspace() = workspace.resetToDefault()

    fun selectRecorderMode(mode: com.example.recorderproject.model.RecorderMode) =
        recorderModeManager.select(mode)

    /** G17: trim editor open for which file (null = none). */
    private val _trimFile = MutableStateFlow<RecordFile?>(null)
    val trimFile: StateFlow<RecordFile?> = _trimFile
    fun openTrim(file: RecordFile) { _trimFile.value = file }
    fun closeTrim() { _trimFile.value = null }

    fun trimFile(file: com.example.recorderproject.model.RecordFile, inMs: Long, outMs: Long): java.io.File? {
        return try {
            val result = com.example.recorderproject.audio.WavTrimmer.trimToFile(
                srcPath = file.path,
                inMs = inMs,
                outMs = outMs,
                sampleRate = file.sampleRate,
                bitDepth = file.bitDepth,
                channelCount = file.channelCount,
            )
            if (result != null) {
                val trimRecord = file.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    name = result.name,
                    path = result.absolutePath,
                    durationSeconds = ((outMs - inMs) / 1000L).toInt().coerceAtLeast(1),
                )
                fileLibrary.add(trimRecord)
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Trim failed: ${e.message}", e)
            null
        }
    }


    // Cloud backup (toggle / URI / Drive / onTakeSaved) → CloudBackup (declared near the top).

    /** G21: device health snapshot — battery % + remaining storage MB. Computed on demand. */
    fun snapshotHealth(context: android.content.Context): Pair<Int, Long> {
        val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
        val battery = try { bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (_: Exception) { -1 }
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val freeMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024L * 1024L)
        return battery to freeMb
    }

    // G22: Pomodoro / auto-stop timer + free-tier limit → RecordingTimers (#13 step 7b).
    val autoStopMinutes: StateFlow<Int> = timers.autoStopMinutes
    fun setAutoStopMinutes(m: Int) = timers.setAutoStopMinutes(m)
    /** Called by start-recording flow to arm the timer. */
    fun armAutoStop(onFire: () -> Unit) = timers.armAutoStop(onFire)
    fun cancelAutoStop() = timers.cancelAutoStop()

    /** G23: which files were viewed/played recently (FIFO, capped 10). */
    private val _recentIds = MutableStateFlow<List<String>>(emptyList())
    val recentIds: StateFlow<List<String>> = _recentIds
    fun touchRecent(id: String) {
        val current = _recentIds.value.toMutableList()
        current.remove(id)
        current.add(0, id)
        _recentIds.value = current.take(10)
    }

    /** G24: group by sceneName? (folders proxy). */
    private val _groupByScene = MutableStateFlow(false)
    val groupByScene: StateFlow<Boolean> = _groupByScene
    fun toggleGroupByScene() {
        _groupByScene.value = !_groupByScene.value
        if (hydrated.value) viewModelScope.launch { settings.setGroupByScene(_groupByScene.value) }
    }

    /** G25: lockscreen control intent — placeholder flag for future foreground-service work. */
    private val _lockScreenControlsOn = MutableStateFlow(true)
    val lockScreenControlsOn: StateFlow<Boolean> = _lockScreenControlsOn
    fun toggleLockScreenControls() {
        _lockScreenControlsOn.value = !_lockScreenControlsOn.value
        if (hydrated.value) viewModelScope.launch { settings.setLockScreenControls(_lockScreenControlsOn.value) }
    }

    /** Phase D: which file's transcript view is open (null = none). */
    private val _transcriptFile = MutableStateFlow<RecordFile?>(null)
    val transcriptFile: StateFlow<RecordFile?> = _transcriptFile
    fun openTranscript(file: RecordFile) { _transcriptFile.value = file }
    fun closeTranscript() { _transcriptFile.value = null }

    /** Phase E: which file's pitch-shift dialog is open (null = none). */
    private val _pitchShiftFile = MutableStateFlow<RecordFile?>(null)
    val pitchShiftFile: StateFlow<RecordFile?> = _pitchShiftFile
    fun openPitchShift(file: RecordFile) {
        if (!requirePro(com.example.recorderproject.billing.ProFeature.PITCH_SHIFT)) return
        _pitchShiftFile.value = file
    }
    fun closePitchShift() { _pitchShiftFile.value = null }

    /** Phase E: room profiler screen open? */
    private val _roomProfilerOpen = MutableStateFlow(false)
    val roomProfilerOpen: StateFlow<Boolean> = _roomProfilerOpen
    fun openRoomProfiler() {
        if (!requirePro(com.example.recorderproject.billing.ProFeature.ANALYSIS_TOOLS)) return
        _roomProfilerOpen.value = true
    }
    fun closeRoomProfiler() { _roomProfilerOpen.value = false }

    /** Phase E: which file's scene-slicer screen is open (null = none). */
    private val _sceneSliceFile = MutableStateFlow<RecordFile?>(null)
    val sceneSliceFile: StateFlow<RecordFile?> = _sceneSliceFile
    fun openSceneSlicer(file: RecordFile) { _sceneSliceFile.value = file }
    fun closeSceneSlicer() { _sceneSliceFile.value = null }

    /**
     * Phase E: Run PitchShifter on [file] at [semitones]. Output saved alongside
     * original with `_pitch+N` suffix. Returns the resulting File or null on error.
     */
    /**
     * F3: Detect sync point in [file] (first onset, ms from start). Stores result
     * on the in-memory RecordFile as `syncPointMs` so future UI can show a badge
     * or jump-to-onset action.
     */
    fun detectSyncPoint(file: RecordFile): Long? {
        val ms = com.example.recorderproject.audio.SyncDetector.detect(file.path) ?: return null
        fileLibrary.updateById(file.id) { it.copy(syncPointMs = ms) }
        return ms
    }

    fun shiftFilePitch(file: RecordFile, semitones: Float): java.io.File? {
        if (file.path.startsWith("content://")) return null
        return try {
            val src = java.io.File(file.path)
            if (!src.exists()) return null
            val suffix = "_pitch${if (semitones >= 0) "+" else ""}${"%.1f".format(semitones)}"
            val out = java.io.File(src.parentFile, "${src.nameWithoutExtension}$suffix.wav")
            com.example.recorderproject.audio.PitchShifter.shift(src.absolutePath, out.absolutePath, semitones)
            // Best-effort: add the new file to the in-memory list so the user sees it.
            val newRecord = file.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = out.name,
                path = out.absolutePath,
            )
            fileLibrary.add(newRecord)
            out
        } catch (e: Exception) {
            Log.e(TAG, "Pitch shift failed: ${e.message}", e)
            null
        }
    }

    /**
     * Phase D: transcribed text per file (keyed by RecordFile.id).
     * Populated by [requestTranscribe] when an STT engine is hooked up.
     * Until then, requestTranscribe seeds a placeholder so the UI has something to show.
     */
    private val _transcripts = MutableStateFlow<Map<String, String>>(emptyMap())
    val transcripts: StateFlow<Map<String, String>> = _transcripts

    private val _transcribeProgress = MutableStateFlow<Map<String, String>>(emptyMap())
    val transcribeProgress: StateFlow<Map<String, String>> = _transcribeProgress

    fun requestTranscribe(file: RecordFile) {
        if (!requirePro(com.example.recorderproject.billing.ProFeature.TRANSCRIPTION)) return
        if (file.path.startsWith("content://")) {
            Toast.makeText(app, "Transcription requires a local file path, not SAF URI", Toast.LENGTH_LONG).show()
            return
        }
        _transcribeProgress.value = _transcribeProgress.value + (file.id to "Transcribing…")
        transcriptionEngine.transcribe(
            filePath = file.path,
            durationSeconds = file.durationSeconds,
            scope = viewModelScope,
            onProgress = { msg ->
                _transcribeProgress.value = _transcribeProgress.value + (file.id to msg)
            },
            onResult = { text ->
                _transcripts.value = _transcripts.value + (file.id to text)
                _transcribeProgress.value = _transcribeProgress.value - file.id
            },
            onError = { err ->
                _transcribeProgress.value = _transcribeProgress.value - file.id
                Toast.makeText(app, err, Toast.LENGTH_LONG).show()
            },
        )
    }


    fun onPermissionDenied() {
        Log.d(TAG, "Permission denied")
        _needsPermission.value = true
        _errorMessage.value = "Audio recording permission is required. Please grant permission to record audio."
        Toast.makeText(app, "Permission denied. Please allow audio recording.", Toast.LENGTH_LONG).show()
    }

    fun onPermissionGranted() {
        Log.d(TAG, "Permission granted")
        _needsPermission.value = false
        _errorMessage.value = null
    }

    // ---- Feature #3: Auto-naming scene_T## ----

    /**
     * Compute the next take number for [sceneName] by scanning the recordings dir
     * for files matching "<scene>_T<NN>.wav" pattern. Returns 1 if none exist.
     *
     * Sanitizes scene name the same way `validateRecordingData` does so the comparison
     * matches what actually lands on disk.
     */
    // Take/scene/file naming → TakeNaming (#13 step 7c).
    private fun refreshAutoFileName() = naming.refreshAutoFileName()
    fun updateFileName(value: String) = naming.updateFileName(value)
    fun updateSceneName(value: String) = naming.updateSceneName(value)
    fun bumpTake(delta: Int = 1) = naming.bumpTake(delta)
    fun bumpSubscene(deltaTenths: Int = 1) = naming.bumpSubscene(deltaTenths)
    fun bumpScene(delta: Int) = naming.bumpScene(delta)
    fun updateNotes(value: String) = naming.updateNotes(value)

    fun updateSampleRate(value: Int) = audioConfig.updateSampleRate(value)

    fun updateAudioSource(name: String) = audioConfig.updateAudioSource(name)

    fun setSaveDirectoryUri(uri: Uri) {
        _saveDirectoryUri.value = uri
        // Take persistable grant so it survives process death
        try {
            app.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not take persistable SAF permission: ${e.message}")
        }
        if (hydrated.value) viewModelScope.launch { settings.setSaveDirectoryUri(uri.toString()) }
    }

    fun deleteRecording(file: RecordFile) {
        try {
            if (!file.path.startsWith("content://")) {
                val srcFile = File(file.path)
                if (srcFile.exists()) srcFile.delete()
                // Also delete companion files (NR / EQ render / sidecar)
                val parent = srcFile.parentFile
                val base = srcFile.nameWithoutExtension
                parent?.listFiles { f -> f.name.startsWith(base) }?.forEach { it.delete() }
            }
            fileLibrary.removeById(file.id)
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed: ${e.message}", e)
        }
    }

    private fun rebindDeliveryResult(srcPath: String, dstPath: String, r: DeliveryResult) {
        fileLibrary.update { list ->
            list.map { f ->
                if (f.path == srcPath) f.copy(deliveryPath = dstPath, deliveryResult = r) else f
            }
        }
    }

    // Cue markers (liveCueCount / pendingCues / dropCueMarker) → RecordingController (below).

    fun toggleStarRecording(file: RecordFile) {
        fileLibrary.updateById(file.id) { it.copy(starred = !it.starred) }
    }

    fun toggleLockRecording(file: RecordFile) {
        fileLibrary.updateById(file.id) { it.copy(isLocked = !it.isLocked) }
    }

    /**
     * Rename a recording on disk and update the in-memory list. Returns true on success.
     * If [newName] doesn't end with `.wav`, the extension is preserved from the original.
     */
    fun renameRecording(file: RecordFile, newName: String): Boolean {
        if (newName.isBlank()) return false
        if (file.path.startsWith("content://")) {
            // SAF-backed files can't be renamed via java.io.File — skip the disk side but
            // still update the in-memory label so the UI reflects the new name.
            fileLibrary.updateById(file.id) { it.copy(name = newName) }
            return true
        }
        return try {
            val srcFile = File(file.path)
            if (!srcFile.exists()) return false
            val ext = srcFile.extension.ifBlank { "wav" }
            val finalName = if (newName.endsWith(".$ext")) newName else "$newName.$ext"
            val target = File(srcFile.parentFile, finalName)
            if (target.exists()) return false
            if (!srcFile.renameTo(target)) return false
            fileLibrary.updateById(file.id) { it.copy(name = finalName, path = target.absolutePath) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Rename failed: ${e.message}", e)
            false
        }
    }

    fun shareRecording(file: RecordFile) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "audio/wav"
                val uri = if (file.path.startsWith("content://")) {
                    android.net.Uri.parse(file.path)
                } else {
                    androidx.core.content.FileProvider.getUriForFile(
                        app, app.packageName + ".fileprovider", File(file.path),
                    )
                }
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(intent, "Share recording").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Share failed: ${e.message}", e)
            Toast.makeText(app, "Share unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    // Playback functions — delegated to PlaybackManager (issue #13).
    fun selectFile(file: RecordFile) = playback.selectFile(file)

    fun playPause() = playback.playPause()

    fun stopPlayback() = playback.stopPlayback()

    fun seekTo(position: Int) = playback.seekTo(position)

    fun closePlayer() = playback.closePlayer()

    // Playback speed / loop / volume — delegated to PlaybackManager (issue #13).
    val playbackSpeed: StateFlow<Float> = playback.playbackSpeed
    fun setPlaybackSpeed(speed: Float) = playback.setPlaybackSpeed(speed)

    val playbackLoop: StateFlow<Boolean> = playback.playbackLoop
    fun toggleLoop() = playback.toggleLoop()

    val playbackVolume: StateFlow<Float> = playback.playbackVolume
    fun setPlaybackVolume(v: Float) = playback.setPlaybackVolume(v)

    // ============= EQ actions — delegated to EqEditor (issue #13 step 4) =============
    fun onEQOpen(file: RecordFile) = eqEditor.onEQOpen(file)
    fun onEQClose() = eqEditor.onEQClose()
    fun onEQBandChanged(updated: com.example.recorderproject.model.EQBand) = eqEditor.onEQBandChanged(updated)
    fun onEQModeToggle(mode: EQEditMode) = eqEditor.onEQModeToggle(mode)
    fun onEQViewModeToggle(mode: EQViewMode) = eqEditor.onEQViewModeToggle(mode)
    fun onEQSelectBand(id: Int?) = eqEditor.onEQSelectBand(id)
    fun onEQUndo() = eqEditor.onEQUndo()
    fun onEQRedo() = eqEditor.onEQRedo()
    fun onEQABToggle() = eqEditor.onEQABToggle()
    fun onEQResetAll() = eqEditor.onEQResetAll()
    fun onEQPresetSelected(preset: com.example.recorderproject.model.EQPreset) = eqEditor.onEQPresetSelected(preset)
    fun onEQToggleBypass() = eqEditor.onEQToggleBypass()
    fun onEQToggleGainCompensation() = eqEditor.onEQToggleGainCompensation()
    fun onEQHumDetect(mainsHz: Float = 60f) = eqEditor.onEQHumDetect(mainsHz)
    fun onEQRandomPreset() = eqEditor.onEQRandomPreset()
    fun onEQSaveAsCustomPreset(name: String) = eqEditor.onEQSaveAsCustomPreset(name)
    fun onEQExportCurvePng() = eqEditor.onEQExportCurvePng()
    fun onEQNoiseAutoDetect() = eqEditor.onEQNoiseAutoDetect()
    fun onEQAcceptSuggestion(band: com.example.recorderproject.model.EQBand) = eqEditor.onEQAcceptSuggestion(band)
    fun onEQRejectSuggestion(band: com.example.recorderproject.model.EQBand) = eqEditor.onEQRejectSuggestion(band)
    fun onEQDrawCurve(targetDbCurve: FloatArray) = eqEditor.onEQDrawCurve(targetDbCurve)
    fun onEQTapNotch(frequencyHz: Float) = eqEditor.onEQTapNotch(frequencyHz)
    fun onEQSaveModeChange(mode: ApplySaveMode) = eqEditor.onEQSaveModeChange(mode)
    fun onEQApply() = eqEditor.onEQApply()

    /**
     * Push current in-memory recorder-related state into `AudioRecorderManager`.
     * Called after hydration (to restore native state on next record) and after
     * `resetFactory()` (to push default values into the native recorder).
     *
     * The monitor toggle is intentionally NOT auto-resumed here — `monitor_enabled`
     * is not persisted (see spec § Things NOT persisted).
     */
    private fun rewireRecorderFromState() {
        // Input-config slice (gain + bit depth + channel count) → AudioInputConfig.
        audioConfig.rewireRecorder()
        // Recording-DSP slice (gate / AGC / hi-pass / anti-clip) → RecordingController.
        recordingController.rewireRecorder()
        // Live EQ chain is pushed on demand in startRecording(); no need here.
    }

    /**
     * Reassign every in-memory persisted-setting StateFlow to its declared default.
     * Used by both `applySnapshot(SettingsSnapshot())` and `resetFactory()`.
     *
     * NOTE: This sets the StateFlows only — it does NOT push state into
     * `AudioRecorderManager`. Call `rewireRecorderFromState()` after this if you
     * need the native recorder to pick up the new state.
     */
    private fun applyDefaults() = applySnapshot(SettingsSnapshot())

    /** Apply a SettingsSnapshot to the in-memory StateFlows. */
    private fun applySnapshot(s: SettingsSnapshot) {
        recorderModeManager.applySnapshot(s.recorderMode)
        // Input-config slice (audio source, mic label, gain, sample/bit/channel, quality).
        audioConfig.applySnapshot(s)
        noiseReduction.setEnabledInMemory(s.noiseReduction)
        timers.applySnapshot(s.autoStopMin)
        naming.applySnapshot(s.sceneName)
        // Recording-controls slice (DSP toggles, live-EQ, VAD, pre-roll, countdown/max-duration).
        recordingController.applySnapshot(s)

        eqEditor.applySnapshot(s.currentEqChainJson, s.eqMode, s.eqViewMode, s.eqApplySaveMode, s.eqBypassed)

        playback.applySnapshot(s.playbackSpeed, s.playbackLoop, s.playbackVolume)

        _saveDirectoryUri.value = s.saveDirectoryUri?.let { Uri.parse(it) }
        _groupByScene.value = s.groupByScene
        _lockScreenControlsOn.value = s.lockScreenControls
        cloudBackup.applySnapshot(s.cloudBackup, s.cloudBackupUri)

        loudness.applySnapshot(
            s.defaultLoudnessTarget,
            s.customLoudnessLufs,
            s.customLoudnessTpCeiling,
        )
    }

    /** Change the active session target. Does not persist. */
    fun setSessionLoudnessTarget(t: LoudnessTarget) = loudness.setSessionTarget(t)

    /** Persist as default + update session target. */
    fun saveAsDefaultLoudnessTarget(t: LoudnessTarget) = loudness.saveAsDefault(t)

    /**
     * Reset every persisted setting to its declared default. Does NOT touch:
     *  - SharedPreferences (onboarding_done, mode_chosen, app_theme, custom_presets,
     *    active_preset, sort_order, file_filter)
     *  - Recorded WAV files or their sidecars
     *  - CustomPresetStore (user-named EQ presets)
     *  - The in-memory _recordFiles list
     */
    fun resetFactory() {
        if (recordingController.isRecording.value) {
            Toast.makeText(app, "Stop recording before resetting", Toast.LENGTH_LONG).show()
            return
        }
        viewModelScope.launch {
            try {
                settings.clear()
                // Tear down monitor if running — opening the mic is a session-local
                // side effect that should not persist past reset.
                monitorManager.stop()
                applyDefaults()
                rewireRecorderFromState()
                // Clear EQ undo/redo so post-reset history doesn't reference old chains
                eqEditor.clearHistory()
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "resetFactory failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Reset failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * On launch, populate _recordFiles from the recordings directory. Detects
     * NR (filename ends with _nr.wav) and EQ (matching _eq.json sidecar) flags
     * so the filter chips behave as expected.
     *
     * If both foo.wav and foo_nr.wav exist, only foo_nr.wav is shown — the NR
     * version is the user-facing artifact, matching what stopRecording() adds
     * to the list when NR is enabled.
     */
    private fun scanRecordingsFromDisk() {
        val added = scanner.scanDisk(fileLibrary.current().map { it.path }.toSet())
        if (added.isNotEmpty()) {
            fileLibrary.addAll(added)
            Log.i(TAG, "scanRecordingsFromDisk: added ${added.size} files from disk")
        }
    }

    /**
     * Like [scanRecordingsFromDisk] but for a SAF (folder-picker) save location. Recordings
     * saved to a chosen folder are stored as content:// documents, which the internal-storage
     * scan never sees — so without this they vanish from the list after an app restart even
     * though the files are perfectly intact. Best-effort and fully guarded; never throws.
     */
    private suspend fun scanSafRecordings() = withContext(Dispatchers.IO) {
        val uri = _saveDirectoryUri.value ?: return@withContext
        val added = scanner.scanSaf(uri, fileLibrary.current().map { it.path }.toSet())
        if (added.isNotEmpty()) {
            fileLibrary.addAll(added)
            Log.i(TAG, "scanSafRecordings: added ${added.size} files from SAF folder")
        }
    }

    /**
     * Export a CSV sound report of all recordings to a temp file and fire a share intent.
     * Columns: Scene, Take, FileName, Duration(s), HasNR, HasEQ, Starred, Locked, Tags, Notes.
     */
    fun exportSoundReport() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = fileLibrary.current()
                val sb = StringBuilder()
                sb.appendLine("Scene,Take,FileName,Duration(s),HasNR,HasEQ,Starred,Locked,Tags,Notes,Integrated,TP_dBTP,LRA,Target_Result")
                for (f in files) {
                    val takeMatch = Regex("_T(\\d+)").find(f.name)
                    val takeNum = takeMatch?.groupValues?.get(1) ?: ""
                    fun esc(s: String) = "\"${s.replace("\"", "\"\"")}\""
                    val dr = f.deliveryResult
                    val integrated = dr?.integratedLufs?.let { "%.1f".format(it) } ?: ""
                    val tp         = dr?.truePeakDbtp?.let { "%.1f".format(it) } ?: ""
                    val lra        = dr?.lra?.let { "%.1f".format(it) } ?: ""
                    val result = when {
                        dr == null            -> "N/A"
                        dr.targetLufs == null -> "N/A"
                        dr.passed             -> "PASS"
                        else                  -> "FAIL"
                    }
                    sb.append(esc(f.sceneName)).append(',')
                        .append(takeNum).append(',')
                        .append(esc(f.name)).append(',')
                        .append(f.durationSeconds).append(',')
                        .append(if (f.hasNoiseReduction) "Y" else "N").append(',')
                        .append(if (f.hasEQ) "Y" else "N").append(',')
                        .append(if (f.starred) "Y" else "N").append(',')
                        .append(if (f.isLocked) "Y" else "N").append(',')
                        .append(esc(f.tags)).append(',')
                        .append(esc(f.notes)).append(',')
                        .append(integrated).append(',')
                        .append(tp).append(',')
                        .append(lra).append(',')
                        .append(result)
                        .append('\n')
                }
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
                    .format(java.util.Date())
                val outFile = java.io.File(app.cacheDir, "sound_report_$ts.csv")
                outFile.writeText(sb.toString())

                withContext(Dispatchers.Main) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        app, app.packageName + ".fileprovider", outFile,
                    )
                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Sound Report $ts")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = android.content.Intent.createChooser(share, "Share sound report").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    app.startActivity(chooser)
                    Toast.makeText(app, "Sound report exported: ${files.size} takes", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sound report export failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { preRollCapture.stop() } catch (_: Exception) {}
        if (stopReceiverRegistered) {
            try { app.unregisterReceiver(stopBroadcastReceiver) } catch (_: Exception) {}
            stopReceiverRegistered = false
        }
        try { monitorManager.release() } catch (_: Exception) {}
        audioFocus.unregisterBecomingNoisy()
        try { audioConfig.stop() } catch (_: Exception) {}
        try { voiceActivityDetector.stop() } catch (_: Exception) {}
        try { billing.stop() } catch (_: Exception) {}
        audioFocus.abandonFocus()
        playback.release()
    }
}
