@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.example.recorderproject

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recorderproject.audio.AudioRecorderManager
import com.example.recorderproject.audio.TranscriptionEngine
import com.example.recorderproject.network.GoogleDriveUploader
import com.example.recorderproject.audio.VoiceActivityDetector
import com.example.recorderproject.audio.NoiseReductionProcessor
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.data.Defaults
import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.data.SettingsSnapshot
import com.example.recorderproject.model.ApplySaveMode
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQChainJson
import com.example.recorderproject.model.EQEditMode
import com.example.recorderproject.model.EQViewMode
import com.example.recorderproject.model.MonitorLevel
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.model.RecorderMode
import com.example.recorderproject.model.RecordingQuality
import com.example.recorderproject.model.SortOrder
import com.example.recorderproject.model.DeliveryResult
import com.example.recorderproject.model.LoudnessTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import android.util.Log
import android.widget.Toast
import java.io.File

class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "RecorderViewModel"
    private val app = application
    private val recorder = AudioRecorderManager(application.applicationContext)
    private val noiseProcessor = NoiseReductionProcessor()

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
    private val driveUploader = GoogleDriveUploader(app.applicationContext)

    private val _isDriveSignedIn = MutableStateFlow(driveUploader.isSignedIn())
    val isDriveSignedIn: StateFlow<Boolean> = _isDriveSignedIn

    fun refreshDriveSignInState() {
        _isDriveSignedIn.value = driveUploader.isSignedIn()
    }

    // ---- Feature #1: Pre-roll buffer ----
    private val preRollBuffer = com.example.recorderproject.audio.PreRollBuffer(
        sampleRate = 48_000, seconds = 5, channels = 1,
    )
    private val preRollCapture = com.example.recorderproject.audio.PreRollCapture(
        buffer = preRollBuffer,
        sampleRate = 48_000,
    )

    private val _preRollEnabled = MutableStateFlow(false)
    val preRollEnabled: StateFlow<Boolean> = _preRollEnabled

    fun togglePreRoll() {
        // Pre-roll ("pre-recording") is a Pro feature — gate enabling, allow turning off.
        if (!_preRollEnabled.value &&
            !requirePro(com.example.recorderproject.billing.ProFeature.PRE_ROLL_VAD)
        ) return
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
        if (hydrated.value) viewModelScope.launch { settings.setPreRollEnabled(on) }
    }

    // ---- Feature #2: Stop broadcast receiver (notification Stop button) ----
    private val stopBroadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == com.example.recorderproject.audio.RecordingForegroundService.ACTION_STOP_RECORDING) {
                Log.i(TAG, "Stop broadcast received — stopping recording")
                if (_isRecording.value) {
                    stopRecording()
                }
            }
        }
    }
    private var stopReceiverRegistered = false
    private val settings = SettingsDataStore(application)
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
        liveChainSink = { chain -> if (_liveEqEnabled.value) recorder.setLiveEqChain(chain, audioConfig.sampleRate.value.toFloat()) },
        markFileHasEq = { id -> _recordFiles.value = _recordFiles.value.map { if (it.id == id) it.copy(hasEQ = true) else it } },
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

    private val eqBandGainsPersist = MutableSharedFlow<FloatArray>(extraBufferCapacity = 64)

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
                        _recordFiles.value = _recordFiles.value + recovered
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

        // Debounced persistence for the live-EQ band gains — UI state updates instantly,
        // disk write is coalesced to at most one per 150 ms of quiet time. (Input-gain
        // persistence moved into AudioInputConfig.)
        viewModelScope.launch {
            eqBandGainsPersist
                .debounce(150)
                .collect { settings.setLiveEqBandGains(it) }
        }
    }

    private val _recordFiles = MutableStateFlow<List<RecordFile>>(emptyList())
    val recordFiles: StateFlow<List<RecordFile>> = _recordFiles

    // Q5: persisted across sessions via SharedPreferences
    private val prefs = application.getSharedPreferences("meatrec_ui", 0)
    private val _sortOrder = MutableStateFlow(
        runCatching { SortOrder.valueOf(prefs.getString("sort_order", null) ?: "") }
            .getOrElse { SortOrder.Default }
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    /**
     * Files sorted per `sortOrder`. UI should observe this, not `recordFiles`.
     * Date sort uses insertion order as a proxy (`_recordFiles` appends on new takes);
     * `RecordFile` has no explicit timestamp field today.
     */
    val sortedRecordFiles: StateFlow<List<RecordFile>> = combine(_recordFiles, _sortOrder) { files, order ->
        applySort(files, order)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        prefs.edit().putString("sort_order", order.name).apply()
    }

    // F6+F7: search query + filter chip
    enum class FileFilter { ALL, STARRED, LOCKED, NR, EQ }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    // NOTE: the filter chip is TRANSIENT view state and is intentionally NOT restored
    // across launches. Persisting it caused a trap: a stuck non-ALL filter (e.g. "NR")
    // hid every recording, and since the filter toolbar only shows when the list is
    // non-empty, there was no way to switch back to "All" — the library looked empty
    // forever. Always start at ALL so recordings are visible by default.
    private val _fileFilter = MutableStateFlow(FileFilter.ALL)
    val fileFilter: StateFlow<FileFilter> = _fileFilter
    fun setFileFilter(f: FileFilter) {
        _fileFilter.value = f
    }

    // F8: bulk multi-select state for RECORDINGS list
    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds: StateFlow<Set<String>> = _selectedFileIds

    fun toggleFileSelection(id: String) {
        _selectedFileIds.value = _selectedFileIds.value.toMutableSet().also {
            if (!it.add(id)) it.remove(id)
        }
    }
    fun clearSelection() { _selectedFileIds.value = emptySet() }
    fun selectAll() { _selectedFileIds.value = _recordFiles.value.map { it.id }.toSet() }

    fun deleteSelected() {
        val ids = _selectedFileIds.value
        if (ids.isEmpty()) return
        _recordFiles.value.filter { it.id in ids }.forEach { deleteRecording(it) }
        _selectedFileIds.value = emptySet()
    }

    /**
     * Files filtered by [searchQuery] (case-insensitive substring of name) AND
     * [fileFilter] chip, then sorted per [sortOrder]. UI should observe this.
     */
    val visibleRecordFiles: StateFlow<List<RecordFile>> = kotlinx.coroutines.flow.combine(
        _recordFiles, _sortOrder, _searchQuery, _fileFilter,
    ) { files, order, query, filter ->
        val matched = files.filter { f ->
            val matchesQuery = query.isBlank() || f.name.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                FileFilter.ALL -> true
                FileFilter.STARRED -> f.starred
                FileFilter.LOCKED -> f.isLocked
                FileFilter.NR -> f.hasNoiseReduction
                FileFilter.EQ -> f.hasEQ
            }
            matchesQuery && matchesFilter
        }
        applySort(matched, order)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun applySort(files: List<RecordFile>, order: SortOrder): List<RecordFile> = when (order) {
        SortOrder.DATE_NEWEST    -> files.asReversed()
        SortOrder.DATE_OLDEST    -> files
        SortOrder.NAME_ASC       -> files.sortedBy { it.name.lowercase() }
        SortOrder.NAME_DESC      -> files.sortedByDescending { it.name.lowercase() }
        SortOrder.DURATION_LONG  -> files.sortedByDescending { it.durationSeconds }
        SortOrder.DURATION_SHORT -> files.sortedBy { it.durationSeconds }
    }

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

    // G2: Live noise gate while recording — toggle from the feature chips.
    private val _liveNoiseGateOn = MutableStateFlow(false)
    val liveNoiseGateOn: StateFlow<Boolean> = _liveNoiseGateOn
    fun toggleLiveNoiseGate() {
        _liveNoiseGateOn.value = !_liveNoiseGateOn.value
        recorder.setLiveNoiseGate(_liveNoiseGateOn.value, thresholdDb = -46f)
        if (hydrated.value) viewModelScope.launch { settings.setLiveNoiseGate(_liveNoiseGateOn.value) }
    }

    // G9: Auto Gain Control toggle
    private val _agcOn = MutableStateFlow(false)
    val agcOn: StateFlow<Boolean> = _agcOn
    fun toggleAgc() {
        _agcOn.value = !_agcOn.value
        recorder.setAgc(_agcOn.value)
        if (hydrated.value) viewModelScope.launch { settings.setAgc(_agcOn.value) }
    }

    // G10: Hi-pass (rumble removal) toggle
    private val _hiPassOn = MutableStateFlow(false)
    val hiPassOn: StateFlow<Boolean> = _hiPassOn
    fun toggleHiPass() {
        _hiPassOn.value = !_hiPassOn.value
        recorder.setHiPass(_hiPassOn.value)
        if (hydrated.value) viewModelScope.launch { settings.setHiPass(_hiPassOn.value) }
    }

    // G11: Anti-clipping auto-attenuator toggle
    private val _antiClipOn = MutableStateFlow(false)
    val antiClipOn: StateFlow<Boolean> = _antiClipOn
    fun toggleAntiClip() {
        _antiClipOn.value = !_antiClipOn.value
        recorder.setAntiClip(_antiClipOn.value)
        if (hydrated.value) viewModelScope.launch { settings.setAntiClip(_antiClipOn.value) }
    }

    // G8: Quality presets — delegated to AudioInputConfig (issue #13 step 6).
    val quality: StateFlow<com.example.recorderproject.model.RecordingQuality> = audioConfig.quality
    fun setQuality(q: com.example.recorderproject.model.RecordingQuality) = audioConfig.setQuality(q)

    // G12: Recording schedule — start at a given absolute time (epoch ms). 0 = off.
    private val _scheduledStartMs = MutableStateFlow(0L)
    val scheduledStartMs: StateFlow<Long> = _scheduledStartMs
    private var scheduleJob: kotlinx.coroutines.Job? = null
    fun setScheduledStart(epochMs: Long, onFire: () -> Unit) {
        _scheduledStartMs.value = epochMs
        scheduleJob?.cancel()
        if (epochMs <= 0) return
        scheduleJob = viewModelScope.launch {
            val waitMs = (epochMs - System.currentTimeMillis()).coerceAtLeast(0)
            kotlinx.coroutines.delay(waitMs)
            if (_scheduledStartMs.value == epochMs) onFire()
        }
    }
    fun cancelSchedule() { scheduleJob?.cancel(); _scheduledStartMs.value = 0L }

    // G13: VAD (voice-activity detection) — auto-start recording when input rises.
    private val _vadOn = MutableStateFlow(false)
    val vadOn: StateFlow<Boolean> = _vadOn
    fun toggleVad() {
        // VAD ("voice-activated record") is part of the Smart capture Pro bundle — gate enabling.
        if (!_vadOn.value &&
            !requirePro(com.example.recorderproject.billing.ProFeature.PRE_ROLL_VAD)
        ) return
        val on = !_vadOn.value
        _vadOn.value = on
        if (hydrated.value) viewModelScope.launch { settings.setVad(on) }
        if (on) {
            voiceActivityDetector.onVoiceDetected = {
                if (!_isRecording.value) {
                    Log.i(TAG, "VAD: voice detected — auto-starting recording")
                    startRecording()
                }
            }
            voiceActivityDetector.start(viewModelScope)
            Toast.makeText(app, "VAD on — recording will start when voice is detected", Toast.LENGTH_SHORT).show()
        } else {
            voiceActivityDetector.stop()
            voiceActivityDetector.onVoiceDetected = null
            Toast.makeText(app, "VAD off", Toast.LENGTH_SHORT).show()
        }
    }

    // G14: Tag list per file (lightweight — stored alongside RecordFile.tags string).
    fun addTagToFile(file: RecordFile, tag: String) {
        if (tag.isBlank()) return
        val existing = file.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (existing.contains(tag)) return
        existing += tag
        val merged = existing.joinToString(",")
        _recordFiles.value = _recordFiles.value.map {
            if (it.id == file.id) it.copy(tags = merged) else it
        }
    }
    fun removeTagFromFile(file: RecordFile, tag: String) {
        val existing = file.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != tag }
        val merged = existing.joinToString(",")
        _recordFiles.value = _recordFiles.value.map {
            if (it.id == file.id) it.copy(tags = merged) else it
        }
    }

    // G15: Auto-name from scene + date/time when filename is left blank.
    fun autoNameForNextTake(): String {
        val now = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
            .format(java.util.Date())
        val scene = _sceneName.value.replace("[^A-Za-z0-9_-]".toRegex(), "_").take(24)
        val base = if (scene.isNotBlank()) "${scene}_$now" else "rec_$now"
        return "$base.wav"
    }

    private val _currentWaveform = MutableStateFlow<List<Float>>(emptyList())
    val currentWaveform: StateFlow<List<Float>> = _currentWaveform

    // M1/M2: live spectrum + pitch — populated only while recording
    private val _liveSpectrum = MutableStateFlow(FloatArray(0))
    val liveSpectrum: StateFlow<FloatArray> = _liveSpectrum
    private val _livePitchHz = MutableStateFlow(0f)
    val livePitchHz: StateFlow<Float> = _livePitchHz
    // D: live LUFS meter — populated only while recording
    private val _liveLufs = MutableStateFlow(-70f)
    val liveLufs: StateFlow<Float> = _liveLufs

    // D: live true-peak — populated only while recording
    private val _liveTpDbTp = MutableStateFlow(Float.NEGATIVE_INFINITY)
    val liveTpDbTp: StateFlow<Float> = _liveTpDbTp

    // G: raw peak dBFS — pre-EQ / pre-NR sample-domain peak, populated while recording
    private val _liveRawPeakDbfs = MutableStateFlow(Float.NEGATIVE_INFINITY)
    val liveRawPeakDbfs: StateFlow<Float> = _liveRawPeakDbfs

    // H: device location snapshot, captured at recording start; copied onto the
    // finalized RecordFile so IxmlWriter emits a <LOCATION> tag.
    @Volatile private var pendingLocationTag: String? = null

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

    // K.2: Stereo phase correlation (-1..1) — populated only while recording stereo
    private val _phaseCorrelation = MutableStateFlow(0f)
    val phaseCorrelation: StateFlow<Float> = _phaseCorrelation
    // Rolling window of recent FFT frames so the SPECTRUM heatmap can scroll
    private val _spectrumHistory = MutableStateFlow<List<FloatArray>>(emptyList())
    val spectrumHistory: StateFlow<List<FloatArray>> = _spectrumHistory

    private val _fileName = MutableStateFlow("scene1_take1.wav")
    val fileName: StateFlow<String> = _fileName

    private val _sceneName = MutableStateFlow("Scene 1")
    val sceneName: StateFlow<String> = _sceneName

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes

    private val _noiseReductionEnabled = MutableStateFlow(true)
    val noiseReductionEnabled: StateFlow<Boolean> = _noiseReductionEnabled

    // Audio-input config delegated to AudioInputConfig (issue #13 step 6).
    val sampleRate: StateFlow<Int> = audioConfig.sampleRate
    val bitDepth: StateFlow<Int> = audioConfig.bitDepth
    fun updateBitDepth(v: Int) = audioConfig.updateBitDepth(v)

    val channelCount: StateFlow<Int> = audioConfig.channelCount
    fun updateChannelCount(v: Int) = audioConfig.updateChannelCount(v)

    /** Pre-record countdown in seconds (0 = off). */
    private val _countdownSeconds = MutableStateFlow(0)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds
    fun updateCountdownSeconds(v: Int) {
        _countdownSeconds.value = v.coerceAtLeast(0)
        if (hydrated.value) viewModelScope.launch { settings.setCountdownSec(_countdownSeconds.value) }
    }

    /** Auto-stop after this many minutes of recording (0 = off). */
    private val _maxDurationMinutes = MutableStateFlow(0)
    val maxDurationMinutes: StateFlow<Int> = _maxDurationMinutes
    fun updateMaxDurationMinutes(v: Int) {
        _maxDurationMinutes.value = v.coerceAtLeast(0)
        if (hydrated.value) viewModelScope.launch { settings.setMaxDurationMin(_maxDurationMinutes.value) }
    }

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
        isRecording = { _isRecording.value },
        onFocusLost = { stopRecording() },
    )

    // Monitoring state delegated to MonitorManager (issue #13 step 5).
    val monitorEnabled: StateFlow<Boolean> = monitorManager.enabled
    val monitorLevel: StateFlow<MonitorLevel> = monitorManager.level

    private val _liveEqEnabled = MutableStateFlow(false)
    val liveEqEnabled: StateFlow<Boolean> = _liveEqEnabled

    // Mic-source label delegated to AudioInputConfig (issue #13 step 6).
    val micSourceLabel: StateFlow<String> = audioConfig.micSourceLabel

    fun toggleMonitor() = monitorManager.toggle()

    fun toggleLiveEq() {
        _liveEqEnabled.value = !_liveEqEnabled.value
        // Push the current chain into the recorder right now — if we're mid-recording, EQ takes
        // effect on the very next PCM buffer the AudioRecord loop reads.
        if (_liveEqEnabled.value) {
            recorder.setLiveEqChain(eqEditor.currentEQChain.value, audioConfig.sampleRate.value.toFloat())
        } else {
            recorder.setLiveEqChain(null, audioConfig.sampleRate.value.toFloat())
        }
        Toast.makeText(app,
            if (_liveEqEnabled.value) "Live EQ on — applied in real time to recording"
            else "Live EQ off",
            Toast.LENGTH_SHORT).show()
        if (hydrated.value) viewModelScope.launch { settings.setLiveEqEnabled(_liveEqEnabled.value) }
    }

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
        val ids = _selectedFileIds.value
        if (ids.size != 2) {
            Toast.makeText(app, "Select exactly 2 files to compare", Toast.LENGTH_SHORT).show()
            return
        }
        val files = _recordFiles.value.filter { it.id in ids }
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

    /** Q1: live EQ band gains (6 bands: 60/200/500/1k/3k/10k Hz) in dB.
     *  Tapping +/− on the LiveEqBandStrip writes here; ViewModel pushes a new
     *  EQChain into the recorder if recording is active. */
    private val _liveEqBandGains = MutableStateFlow(FloatArray(6))
    val liveEqBandGains: StateFlow<FloatArray> = _liveEqBandGains
    fun setLiveEqBand(band: Int, gainDb: Float) {
        val arr = _liveEqBandGains.value.copyOf()
        if (band in arr.indices) {
            arr[band] = gainDb.coerceIn(-12f, 12f)
            _liveEqBandGains.value = arr
            if (hydrated.value) eqBandGainsPersist.tryEmit(arr.copyOf())
            // Build an EQChain and push to recorder if recording.
            if (_isRecording.value) {
                val freqs = floatArrayOf(60f, 200f, 500f, 1000f, 3000f, 10000f)
                val bands = arr.mapIndexed { i, g ->
                    com.example.recorderproject.model.EQBand(
                        id = i + 1,
                        type = com.example.recorderproject.model.EQBandType.BELL,
                        frequencyHz = freqs[i],
                        gainDb = g,
                        q = 1.0f,
                        enabled = kotlin.math.abs(g) > 0.05f,
                    )
                }
                recorder.setLiveEqChain(
                    com.example.recorderproject.model.EQChain(bands = bands, bypassed = false),
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

    /** N2: currently selected recording mode + auto-apply preset on change. */
    private val _recorderMode = MutableStateFlow(com.example.recorderproject.model.RecorderMode.Default)
    val recorderMode: StateFlow<com.example.recorderproject.model.RecorderMode> = _recorderMode

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

    fun selectRecorderMode(mode: com.example.recorderproject.model.RecorderMode) {
        _recorderMode.value = mode
        // Apply preset (except for CUSTOM — user controls those themselves). The
        // sample/bit/channel slice (incl. persistence) is delegated to AudioInputConfig.
        if (mode != com.example.recorderproject.model.RecorderMode.CUSTOM) {
            audioConfig.applyQualityValues(mode.sampleRate, mode.bitDepth, mode.channelCount)
            _noiseReductionEnabled.value = mode.noiseReduction
        }
        if (hydrated.value) viewModelScope.launch {
            settings.setRecorderMode(mode.name)
            if (mode != com.example.recorderproject.model.RecorderMode.CUSTOM) {
                settings.setNoiseReduction(_noiseReductionEnabled.value)
            }
        }
    }

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
                _recordFiles.value = _recordFiles.value + trimRecord
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Trim failed: ${e.message}", e)
            null
        }
    }

    /** G18: compressor live during record (uses existing MasterLimiter — toggle only). */
    private val _compressorOn = MutableStateFlow(false)
    val compressorOn: StateFlow<Boolean> = _compressorOn
    fun toggleCompressor() {
        _compressorOn.value = !_compressorOn.value
        if (hydrated.value) viewModelScope.launch { settings.setCompressor(_compressorOn.value) }
    }

    /** G19: stereo widener live (only matters when channelCount=2). */
    private val _stereoWidenerOn = MutableStateFlow(false)
    val stereoWidenerOn: StateFlow<Boolean> = _stereoWidenerOn
    fun toggleStereoWidener() {
        _stereoWidenerOn.value = !_stereoWidenerOn.value
        if (hydrated.value) viewModelScope.launch { settings.setStereoWidener(_stereoWidenerOn.value) }
    }

    /** G20: cloud backup toggle. Turning it on prompts for a folder if none is set. */
    private val _cloudBackupOn = MutableStateFlow(false)
    val cloudBackupOn: StateFlow<Boolean> = _cloudBackupOn
    fun toggleCloudBackup() {
        // Turning cloud backup ON requires Pro; turning it off is always allowed.
        if (!_cloudBackupOn.value && !requirePro(com.example.recorderproject.billing.ProFeature.CLOUD_BACKUP)) return
        _cloudBackupOn.value = !_cloudBackupOn.value
        if (_cloudBackupOn.value && _cloudBackupUri.value == null) {
            Toast.makeText(app, "Pick a cloud folder in Settings → Cloud Backup Folder", Toast.LENGTH_LONG).show()
        }
        if (hydrated.value) viewModelScope.launch { settings.setCloudBackup(_cloudBackupOn.value) }
    }

    fun backupToDrive(filePath: String) {
        if (!_cloudBackupOn.value) return
        if (!driveUploader.isSignedIn()) return
        viewModelScope.launch {
            val file = java.io.File(filePath)
            if (!file.exists()) return@launch
            val id = driveUploader.upload(file)
            if (id != null) {
                Toast.makeText(app, "Backed up: ${file.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(app, "Drive backup failed — check internet connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** L.2: Persisted SAF URI for the cloud backup folder. */
    private val _cloudBackupUri = MutableStateFlow<android.net.Uri?>(null)
    val cloudBackupUri: StateFlow<android.net.Uri?> = _cloudBackupUri

    fun setCloudBackupUri(uri: android.net.Uri?) {
        _cloudBackupUri.value = uri
        if (uri != null) {
            try {
                app.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Cloud backup URI grant failed: ${e.message}")
            }
        }
        if (hydrated.value) viewModelScope.launch {
            settings.setCloudBackupUri(uri?.toString())
        }
    }

    /** G21: device health snapshot — battery % + remaining storage MB. Computed on demand. */
    fun snapshotHealth(context: android.content.Context): Pair<Int, Long> {
        val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
        val battery = try { bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (_: Exception) { -1 }
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val freeMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024L * 1024L)
        return battery to freeMb
    }

    /** G22: Pomodoro / auto-stop timer that fires while recording. 0 = off. */
    private val _autoStopMinutes = MutableStateFlow(0)
    val autoStopMinutes: StateFlow<Int> = _autoStopMinutes
    private var autoStopJob: kotlinx.coroutines.Job? = null
    fun setAutoStopMinutes(m: Int) {
        _autoStopMinutes.value = m.coerceAtLeast(0)
        if (hydrated.value) viewModelScope.launch { settings.setAutoStopMin(_autoStopMinutes.value) }
    }
    /** Called by start-recording flow to arm the timer. */
    fun armAutoStop(onFire: () -> Unit) {
        autoStopJob?.cancel()
        val mins = _autoStopMinutes.value
        if (mins <= 0) return
        autoStopJob = viewModelScope.launch {
            kotlinx.coroutines.delay(mins * 60_000L)
            if (_isRecording.value) onFire()
        }
    }
    fun cancelAutoStop() { autoStopJob?.cancel() }

    private var recordingLimitJob: Job? = null

    private fun startFreeTierLimitTimer() {
        recordingLimitJob?.cancel()
        if (isPro.value) return
        val limitMs = com.example.recorderproject.billing.ProFeature.FREE_RECORDING_LIMIT_SECONDS * 1000L
        val warnMs  = (com.example.recorderproject.billing.ProFeature.FREE_RECORDING_LIMIT_SECONDS -
                       com.example.recorderproject.billing.ProFeature.FREE_RECORDING_WARN_SECONDS) * 1000L
        val warnMinutes = com.example.recorderproject.billing.ProFeature.FREE_RECORDING_WARN_SECONDS / 60
        recordingLimitJob = viewModelScope.launch {
            delay(warnMs)
            if (_isRecording.value && !isPro.value) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        app,
                        "$warnMinutes minute left — upgrade to Pro for unlimited recording",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            delay(limitMs - warnMs)
            if (_isRecording.value && !isPro.value) {
                stopRecording()
                openPaywall(com.example.recorderproject.billing.ProFeature.RECORDING_LIMIT)
            }
        }
    }

    private fun cancelFreeTierLimitTimer() {
        recordingLimitJob?.cancel()
        recordingLimitJob = null
    }

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
        _recordFiles.value = _recordFiles.value.map {
            if (it.id == file.id) it.copy(syncPointMs = ms) else it
        }
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
            _recordFiles.value = _recordFiles.value + newRecord
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
    private fun computeNextTakeNumber(sceneName: String): Int {
        val sanitized = sceneName.replace("[^A-Za-z0-9_.-]".toRegex(), "_")
        if (sanitized.isBlank()) return 1
        val dir = java.io.File(
            app.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
            "Recordings",
        )
        if (!dir.exists()) return 1
        val pattern = Regex("^${Regex.escape(sanitized)}_T(\\d+)(_nr)?\\.wav$", RegexOption.IGNORE_CASE)
        val existing = dir.listFiles()?.mapNotNull { f ->
            pattern.matchEntire(f.name)?.groupValues?.get(1)?.toIntOrNull()
        } ?: emptyList()
        return (existing.maxOrNull() ?: 0) + 1
    }

    /** Update the default file name based on current scene + next take number. */
    private fun refreshAutoFileName() {
        val scene = _sceneName.value.trim()
        if (scene.isBlank()) return
        val sanitized = scene.replace("[^A-Za-z0-9_.-]".toRegex(), "_")
        val takeNum = computeNextTakeNumber(scene)
        val newName = "${sanitized}_T${"%02d".format(takeNum)}.wav"
        _fileName.value = newName
    }

    fun updateFileName(value: String) {
        _fileName.value = value
    }

    fun updateSceneName(value: String) {
        _sceneName.value = value
        if (hydrated.value) viewModelScope.launch { settings.setSceneName(value) }
        refreshAutoFileName()
    }

    /**
     * H: Manually adjust take number by [delta].
     * Parses the trailing _T## in the current filename and adds delta (clamped to >=1).
     * Useful to skip a take (+1) or back up to overwrite (-1).
     */
    fun bumpTake(delta: Int = 1) {
        val current = _fileName.value
        val match = Regex("^(.+)_T(\\d+)(\\.wav)?$", RegexOption.IGNORE_CASE).matchEntire(current)
        if (match != null) {
            val base = match.groupValues[1]
            val n = ((match.groupValues[2].toIntOrNull() ?: 0) + delta).coerceAtLeast(1)
            val ext = match.groupValues[3].ifBlank { ".wav" }
            _fileName.value = "${base}_T${"%02d".format(n)}$ext"
        } else {
            refreshAutoFileName()
        }
    }

    /**
     * H: Manually bump scene to next decimal sub-scene (default +0.1).
     * "Scene 1" + 0.1 → "Scene 1.1" → +0.1 → "Scene 1.2" … ; take resets to 01.
     * Negative delta steps backward (clamps at .0, which collapses back to the
     * integer-only form, e.g. "Scene 1.1" - 0.1 → "Scene 1").
     */
    fun bumpSubscene(deltaTenths: Int = 1) {
        val base = _sceneName.value.trim()
        val m = Regex("^(.*?)(\\d+)(?:\\.(\\d+))?\\s*$").matchEntire(base)
        val nextScene = if (m != null) {
            val prefix = m.groupValues[1]
            val whole = m.groupValues[2]
            val frac = (m.groupValues[3].toIntOrNull() ?: 0) + deltaTenths
            when {
                frac > 0 -> "$prefix$whole.$frac"
                frac == 0 -> "$prefix$whole"
                else -> "$prefix$whole"     // can't go below 0; stay at integer form
            }
        } else if (deltaTenths > 0) {
            "$base.$deltaTenths"
        } else {
            base
        }
        _sceneName.value = nextScene
        if (hydrated.value) viewModelScope.launch { settings.setSceneName(nextScene) }
        refreshAutoFileName()
    }

    /**
     * H: Manually bump the whole-number scene (default +1).
     * "Scene 1" + 1 → "Scene 2"; "Scene 1.3" + 1 → "Scene 2" (fractional resets);
     * negative delta clamps at 1.
     */
    fun bumpScene(delta: Int) {
        val base = _sceneName.value.trim()
        val m = Regex("^(.*?)(\\d+)(?:\\.\\d+)?\\s*$").matchEntire(base)
        val nextScene = if (m != null) {
            val prefix = m.groupValues[1]
            val whole = (m.groupValues[2].toIntOrNull() ?: 1) + delta
            "$prefix${whole.coerceAtLeast(1)}"
        } else {
            "$base ${(1 + delta).coerceAtLeast(1)}"
        }
        _sceneName.value = nextScene
        if (hydrated.value) viewModelScope.launch { settings.setSceneName(nextScene) }
        refreshAutoFileName()
    }

    fun updateNotes(value: String) {
        _notes.value = value
    }

    fun toggleNoiseReduction(enabled: Boolean) {
        _noiseReductionEnabled.value = enabled
        if (hydrated.value) viewModelScope.launch { settings.setNoiseReduction(enabled) }
    }

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

    private fun validateRecordingData(): Boolean {
        val fileName = _fileName.value.trim()
        val sceneName = _sceneName.value.trim()

        if (fileName.isEmpty()) {
            _errorMessage.value = "Please enter a file name before recording."
            Toast.makeText(app, "Please enter a file name", Toast.LENGTH_LONG).show()
            return false
        }

        val sanitized = fileName.replace("[^A-Za-z0-9_.-]".toRegex(), "_")
        if (sanitized.isEmpty()) {
            _errorMessage.value = "File name contains invalid characters."
            Toast.makeText(app, "File name contains invalid characters", Toast.LENGTH_LONG).show()
            return false
        }

        if (sceneName.isEmpty()) {
            _errorMessage.value = "Please enter a scene name before recording."
            Toast.makeText(app, "Please enter a scene name", Toast.LENGTH_LONG).show()
            return false
        }

        if (audioConfig.sampleRate.value !in listOf(44100, 48000, 96000)) {
            _errorMessage.value = "Sample rate is not supported."
            Toast.makeText(app, "Sample rate is not supported", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    /**
     * Returns null if there's enough free space to safely start a recording,
     * or a human-readable error string explaining why we refuse.
     *
     * Rule: refuse if free space < bytes-needed-for-60-seconds-at-current-quality.
     * (60s is a heuristic: long enough that filling disk mid-take would be a
     * disaster, short enough that we don't block recording on devices that
     * could comfortably handle a short take.)
     */
    private fun checkDiskSpaceOrError(): String? {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
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
            null  // don't block on failure
        }
    }

    fun startRecording() {
        if (!hydrated.value) {
            // Hydration races against an early Record tap. Wait up to 500ms.
            viewModelScope.launch {
                try {
                    withTimeout(500) {
                        hydrated.filter { it }.first()
                    }
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
        // Safeguard: if monitor is on, stop it before recording. Monitor while
        // recording risks an acoustic feedback loop (speaker → mic → speaker)
        // especially on UNPROCESSED mic which has no echo cancellation.
        if (monitorManager.stop()) {
            Toast.makeText(app, "Monitor stopped to prevent echo while recording", Toast.LENGTH_SHORT).show()
        }
        // If pre-roll is on, stop the capture thread so the main AudioRecord can open the mic.
        // The buffer content has been written into preRollBuffer; AudioRecorderManager.start()
        // will drain + prepend it.
        if (preRollCapture.isRunning()) {
            preRollCapture.stop()
        }

        if (_isRecording.value) {
            Log.d(TAG, "Already recording, ignoring")
            return
        }

        if (!validateRecordingData()) {
            return
        }

        checkDiskSpaceOrError()?.let { msg ->
            _errorMessage.value = msg
            Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
            return
        }

        // Feature I: countdown beep before recording
        val countdown = _countdownSeconds.value
        if (countdown > 0) {
            viewModelScope.launch {
                try {
                    val tg = android.media.ToneGenerator(
                        android.media.AudioManager.STREAM_MUSIC, 80,
                    )
                    for (i in countdown downTo 1) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(app, "Recording in ${i}…", Toast.LENGTH_SHORT).show()
                        }
                        tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
                        kotlinx.coroutines.delay(1000L)
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
        viewModelScope.launch { try { settings.setIsRecording(true) } catch (_: Exception) {} }
        audioFocus.requestFocus() // Best-effort — don't block recording if denied
        audioFocus.registerBecomingNoisy()
        _errorMessage.value = null
        recorder.setAudioSource(audioConfig.audioSource.value)
        // Phase 7: real-time EQ during recording — push current chain if Live EQ is on.
        if (_liveEqEnabled.value) {
            recorder.setLiveEqChain(eqEditor.currentEQChain.value, audioConfig.sampleRate.value.toFloat())
        } else {
            recorder.setLiveEqChain(null, audioConfig.sampleRate.value.toFloat())
        }
        recordingStartMs = System.currentTimeMillis()
        // H: snapshot device location for metadata (null if permission denied / no fix)
        pendingLocationTag = com.example.recorderproject.audio.LocationCapture.snapshot(app)
        Log.d(TAG, "Set audio source to: ${audioConfig.audioSource.value}, location=${pendingLocationTag ?: "n/a"}")
        try {
            Log.d(TAG, "Calling recorder.start()")
            recorder.start(
                fileName = _fileName.value,
                sampleRate = audioConfig.sampleRate.value,
                saveDirectoryUri = _saveDirectoryUri.value
            ) { level ->
                _currentWaveform.value = level
            }
            // Q2: start foreground service so recording survives screen-off
            try {
                val intent = android.content.Intent(
                    app, com.example.recorderproject.audio.RecordingForegroundService::class.java,
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
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
            recorder.setPitchListener { hz ->
                _livePitchHz.value = hz
            }
            recorder.setLufsListener { lufs ->
                _liveLufs.value = lufs
            }
            recorder.setTruePeakListener { tp -> _liveTpDbTp.value = tp }
            recorder.setRawPeakListener { db -> _liveRawPeakDbfs.value = db }
            recorder.setPhaseListener { c -> _phaseCorrelation.value = c }
            recorder.setErrorListener { err ->
                viewModelScope.launch(Dispatchers.Main) {
                    val msg = when (err) {
                        is SecurityException -> "Recording stopped: save folder permission was revoked"
                        is java.io.IOException -> "Recording stopped: disk write failed (${err.message})"
                        else -> "Recording stopped due to error: ${err.message}"
                    }
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                    _errorMessage.value = msg
                    if (_isRecording.value) {
                        try { stopRecording() } catch (_: Exception) {}
                    }
                }
            }
            Log.d(TAG, "Recording started successfully")
            Toast.makeText(app, "Recording started", Toast.LENGTH_SHORT).show()
            startFreeTierLimitTimer()
            // Persist active-take path so a crash-then-relaunch can recover it
            viewModelScope.launch {
                try {
                    val activePath = recorder.currentFilePath()
                    if (activePath != null) settings.setActiveRecordingPath(activePath)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not persist active recording path: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            _errorMessage.value = "Failed to start recording: ${e.message}"
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
        cancelFreeTierLimitTimer()
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
            app.stopService(
                android.content.Intent(
                    app, com.example.recorderproject.audio.RecordingForegroundService::class.java,
                ),
            )
        } catch (_: Exception) {}

        val capturedScene = _sceneName.value
        val capturedNotes = _notes.value
        // Capture path before stop() clears it — used for Drive backup after finalize
        val capturedFilePath = recorder.currentFilePath()

        viewModelScope.launch {
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

                // Single toast: merge auto-rename + save-location info to avoid two back-to-back
                // notifications when both conditions are true (no save folder + scene name triggered rename).
                if (_saveDirectoryUri.value == null) {
                    val msg = if (wasRenamed)
                        "✅ Auto-named & saved to App Storage: ${renamedFile.name}\nTip: Set a Save Location in Settings to choose your folder."
                    else
                        "✅ Saved to App Storage: ${renamedFile.name}\nTip: Set a Save Location in Settings to choose your folder."
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                } else if (wasRenamed) {
                    Toast.makeText(app, "Auto-named file: ${renamedFile.name}", Toast.LENGTH_SHORT).show()
                }

                // Surface the take in the list NOW — the WAV is on disk and playable.
                // NR / EQ below can be slow or throw; if we waited until after them to
                // add the file, a failure there would make the recording silently vanish
                // from the list (the original bug). We add it here, then swap in the
                // fully-processed version below via [provisionalId].
                val provisionalId = renamedFile.id
                _recordFiles.value = _recordFiles.value + renamedFile
                _isRecording.value = false
                try { settings.setIsRecording(false) } catch (_: Exception) {}
                _currentWaveform.value = emptyList()
                if (_vadOn.value) {
                    voiceActivityDetector.onVoiceDetected = {
                        if (!_isRecording.value) startRecording()
                    }
                    voiceActivityDetector.start(viewModelScope)
                }

                val nrFile = if (_noiseReductionEnabled.value) {
                    Log.d(TAG, "Applying noise reduction")
                    withContext(Dispatchers.IO) { noiseProcessor.process(renamedFile) }
                } else {
                    renamedFile
                }

                // Phase 7: real-time Live EQ — the chain was baked into the PCM as it was recorded.
                // Mark the file with hasEQ + write the sidecar JSON so re-opening shows the chain.
                val finalFile = if (_liveEqEnabled.value && recorder.isLiveEqActive() &&
                    !nrFile.path.startsWith("content://")
                ) {
                    try {
                        val srcFile = File(nrFile.path)
                        File(srcFile.parentFile, srcFile.nameWithoutExtension + "_eq.json")
                            .writeText(com.example.recorderproject.model.EQChainJson.toJsonString(eqEditor.currentEQChain.value))
                        nrFile.copy(hasEQ = true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Live EQ sidecar write failed: ${e.message}", e)
                        nrFile.copy(hasEQ = true)
                    }
                } else nrFile

                // Recorder no longer needs the chain after the take
                recorder.setLiveEqChain(null, audioConfig.sampleRate.value.toFloat())

                // Swap the provisional entry for the fully-processed final file.
                // (NR assigns a new id, so filter by the provisional id, not finalFile's.)
                _recordFiles.value = _recordFiles.value.filterNot { it.id == provisionalId } + finalFile

                // D: kick off delivery render if a target is active
                loudness.renderFor(finalFile)

                viewModelScope.launch {
                    try { settings.setActiveRecordingPath(null) } catch (_: Exception) {}
                }
                // (isRecording / waveform already reset above, right after the take was
                // added to the list, so the UI updates immediately.)
                // If pre-roll was enabled, restart the capture thread for the next take.
                if (_preRollEnabled.value) {
                    preRollCapture.start()
                }
                // Auto-bump take number for next take in the same scene
                refreshAutoFileName()
                Log.d(TAG, "Recording stopped successfully")
                Toast.makeText(app, "Recording saved: ${finalFile.name}", Toast.LENGTH_SHORT).show()
                // Google Drive backup — auto-upload to Drive if signed in and enabled.
                capturedFilePath?.let { backupToDrive(it) }
                // L.2: Cloud backup — copy the WAV to the chosen SAF folder.
                val cloudUri = _cloudBackupUri.value
                if (_cloudBackupOn.value && cloudUri != null && !finalFile.path.startsWith("content://")) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val srcFile = java.io.File(finalFile.path)
                            val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(app, cloudUri)
                            val target = tree?.createFile("audio/wav", srcFile.name)
                            if (target != null) {
                                app.contentResolver.openOutputStream(target.uri).use { out ->
                                    srcFile.inputStream().use { it.copyTo(out!!) }
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(app, "Backed up to cloud folder", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Cloud backup copy failed: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(app, "Cloud backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording: ${e.message}", e)
                _errorMessage.value = "Failed to stop recording: ${e.message}"
                _isRecording.value = false
                try { settings.setIsRecording(false) } catch (_: Exception) {}
                _currentWaveform.value = emptyList()
                // Safety net: even if recorder.stop()/post-processing threw, the WAV was
                // written to disk (periodic crash-safe finalize). Rescan so the take still
                // appears in the list and can be played back rather than silently lost.
                try { scanRecordingsFromDisk() } catch (_: Exception) {}
                Toast.makeText(app, "Failed to save recording: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
            _recordFiles.value = _recordFiles.value.filter { it.id != file.id }
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed: ${e.message}", e)
        }
    }

    private fun rebindDeliveryResult(srcPath: String, dstPath: String, r: DeliveryResult) {
        _recordFiles.update { list ->
            list.map { f ->
                if (f.path == srcPath) f.copy(deliveryPath = dstPath, deliveryResult = r) else f
            }
        }
    }

    // Phase 4 — cue points dropped while recording
    private val _liveCueCount = MutableStateFlow(0)
    val liveCueCount: StateFlow<Int> = _liveCueCount

    private val pendingCues = mutableListOf<com.example.recorderproject.model.CuePoint>()
    private var recordingStartMs = 0L

    fun dropCueMarker(label: String = "") {
        if (!_isRecording.value) return
        val tMs = System.currentTimeMillis() - recordingStartMs
        pendingCues.add(com.example.recorderproject.model.CuePoint(timeMs = tMs, label = label))
        _liveCueCount.value = pendingCues.size
    }

    fun toggleStarRecording(file: RecordFile) {
        _recordFiles.value = _recordFiles.value.map {
            if (it.id == file.id) it.copy(starred = !it.starred) else it
        }
    }

    fun toggleLockRecording(file: RecordFile) {
        _recordFiles.value = _recordFiles.value.map {
            if (it.id == file.id) it.copy(isLocked = !it.isLocked) else it
        }
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
            _recordFiles.value = _recordFiles.value.map {
                if (it.id == file.id) it.copy(name = newName) else it
            }
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
            _recordFiles.value = _recordFiles.value.map {
                if (it.id == file.id) it.copy(name = finalName, path = target.absolutePath) else it
            }
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

    fun applyNoiseReduce(file: RecordFile) {
        if (file.path.startsWith("content://")) {
            applyNoiseReduceSaf(file)
            return
        }
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { noiseProcessor.process(file) }
            _recordFiles.value = _recordFiles.value.map {
                if (it.id == file.id) updated else it
            }
        }
    }

    /**
     * Noise reduction for SAF (content://) sources. Bridges the document to a cache temp,
     * runs [NoiseReductionProcessor.processFile], and writes a sibling `_nr.wav` into the
     * folder — then swaps the list entry for the NR version, mirroring the local flow.
     */
    private fun applyNoiseReduceSaf(file: RecordFile) {
        val srcUri = android.net.Uri.parse(file.path)
        val base = if (file.name.contains('.')) file.name.substringBeforeLast('.') else file.name
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tree = _saveDirectoryUri.value?.let {
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(app, it)
                } ?: throw IllegalStateException("Save folder unavailable")
                val nrDoc = tree.createFile("audio/wav", "${base}_nr.wav")
                    ?: throw IllegalStateException("Could not create NR file in folder")
                com.example.recorderproject.audio.SafAudioBridge.processViaTemp(
                    cacheDir = app.cacheDir,
                    openInput = { app.contentResolver.openInputStream(srcUri) ?: error("cannot open source") },
                    openOutput = { app.contentResolver.openOutputStream(nrDoc.uri) ?: error("cannot open output") },
                    process = { s, d -> noiseProcessor.processFile(s, d) },
                )
                val updated = file.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    name = nrDoc.name ?: "${base}_nr.wav",
                    path = nrDoc.uri.toString(),
                    hasNoiseReduction = true,
                )
                _recordFiles.value = _recordFiles.value.map { if (it.id == file.id) updated else it }
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Noise reduction applied", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "NR (SAF) failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Noise reduction failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
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
        recorder.setLiveNoiseGate(_liveNoiseGateOn.value, thresholdDb = -46f)
        recorder.setAgc(_agcOn.value)
        recorder.setHiPass(_hiPassOn.value)
        recorder.setAntiClip(_antiClipOn.value)
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
        _recorderMode.value = runCatching { RecorderMode.valueOf(s.recorderMode) }
            .getOrDefault(RecorderMode.CUSTOM)
        // Input-config slice (audio source, mic label, gain, sample/bit/channel, quality).
        audioConfig.applySnapshot(s)
        _noiseReductionEnabled.value = s.noiseReduction
        _countdownSeconds.value = s.countdownSec
        _maxDurationMinutes.value = s.maxDurationMin
        _autoStopMinutes.value = s.autoStopMin
        _sceneName.value = s.sceneName

        _liveNoiseGateOn.value = s.liveNoiseGate
        _agcOn.value = s.agc
        _hiPassOn.value = s.hiPass
        _antiClipOn.value = s.antiClip
        _compressorOn.value = s.compressor
        _stereoWidenerOn.value = s.stereoWidener
        _vadOn.value = s.vad
        _liveEqEnabled.value = s.liveEqEnabled
        _liveEqBandGains.value = s.liveEqBandGains.copyOf()

        eqEditor.applySnapshot(s.currentEqChainJson, s.eqMode, s.eqViewMode, s.eqApplySaveMode, s.eqBypassed)

        playback.applySnapshot(s.playbackSpeed, s.playbackLoop, s.playbackVolume)

        _saveDirectoryUri.value = s.saveDirectoryUri?.let { Uri.parse(it) }
        _groupByScene.value = s.groupByScene
        _lockScreenControlsOn.value = s.lockScreenControls
        _cloudBackupOn.value = s.cloudBackup
        _cloudBackupUri.value = s.cloudBackupUri?.let { android.net.Uri.parse(it) }

        _preRollEnabled.value = s.preRollEnabled
        if (s.preRollEnabled) {
            preRollCapture.start()
            recorder.setPreRollBuffer(preRollBuffer)
        } else {
            preRollCapture.stop()
        }

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
        if (_isRecording.value) {
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
        val added = scanner.scanDisk(_recordFiles.value.map { it.path }.toSet())
        if (added.isNotEmpty()) {
            _recordFiles.value = _recordFiles.value + added
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
        val added = scanner.scanSaf(uri, _recordFiles.value.map { it.path }.toSet())
        if (added.isNotEmpty()) {
            _recordFiles.value = _recordFiles.value + added
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
                val files = _recordFiles.value
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
