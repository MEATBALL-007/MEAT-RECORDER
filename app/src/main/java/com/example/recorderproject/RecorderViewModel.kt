package com.example.recorderproject

import android.app.Application
import android.net.Uri
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recorderproject.audio.AudioRecorderManager
import com.example.recorderproject.audio.NoiseReductionProcessor
import com.example.recorderproject.audio.StaticSpectrum
import com.example.recorderproject.model.ApplySaveMode
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQEditMode
import com.example.recorderproject.model.EQViewMode
import com.example.recorderproject.model.RecordFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import android.widget.Toast
import java.io.File

class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "RecorderViewModel"
    private val app = application
    private val recorder = AudioRecorderManager(application.applicationContext)
    private val noiseProcessor = NoiseReductionProcessor()
    private val mediaPlayer = MediaPlayer()

    private val _recordFiles = MutableStateFlow<List<RecordFile>>(emptyList())
    val recordFiles: StateFlow<List<RecordFile>> = _recordFiles

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _currentWaveform = MutableStateFlow<List<Float>>(emptyList())
    val currentWaveform: StateFlow<List<Float>> = _currentWaveform

    private val _fileName = MutableStateFlow("scene1_take1.wav")
    val fileName: StateFlow<String> = _fileName

    private val _sceneName = MutableStateFlow("Scene 1")
    val sceneName: StateFlow<String> = _sceneName

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes

    private val _noiseReductionEnabled = MutableStateFlow(true)
    val noiseReductionEnabled: StateFlow<Boolean> = _noiseReductionEnabled

    private val _sampleRate = MutableStateFlow(48000)
    val sampleRate: StateFlow<Int> = _sampleRate

    private val _bitDepth = MutableStateFlow(16)
    val bitDepth: StateFlow<Int> = _bitDepth

    fun updateBitDepth(v: Int) { _bitDepth.value = v }

    // Save directory
    private val _saveDirectoryUri = MutableStateFlow<Uri?>(null)
    val saveDirectoryUri: StateFlow<Uri?> = _saveDirectoryUri

    // Playback states
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPlaybackPosition = MutableStateFlow(0)
    val currentPlaybackPosition: StateFlow<Int> = _currentPlaybackPosition

    private val _playbackDuration = MutableStateFlow(0)
    val playbackDuration: StateFlow<Int> = _playbackDuration

    private val _selectedFile = MutableStateFlow<RecordFile?>(null)
    val selectedFile: StateFlow<RecordFile?> = _selectedFile

    private val _audioSource = MutableStateFlow(1) // Default MIC
    val audioSource: StateFlow<Int> = _audioSource

    private val _audioSourceName = MutableStateFlow("Microphone")
    val audioSourceName: StateFlow<String> = _audioSourceName

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _needsPermission = MutableStateFlow(false)
    val needsPermission: StateFlow<Boolean> = _needsPermission

    private var positionUpdateJob: Job? = null

    private val _isPlayerReady = MutableStateFlow(false)
    val isPlayerReady: StateFlow<Boolean> = _isPlayerReady

    // ------------- EQ state (Phase 1) -------------

    private val _currentEQChain = MutableStateFlow(EQChain.empty())
    val currentEQChain: StateFlow<EQChain> = _currentEQChain

    private val _eqMode = MutableStateFlow(EQEditMode.PARAMETRIC)
    val eqMode: StateFlow<EQEditMode> = _eqMode

    private val _eqViewMode = MutableStateFlow(EQViewMode.TWO_D)
    val eqViewMode: StateFlow<EQViewMode> = _eqViewMode

    private val _eqSelectedBandId = MutableStateFlow<Int?>(null)
    val eqSelectedBandId: StateFlow<Int?> = _eqSelectedBandId

    private val _eqSnapshot = MutableStateFlow<EQChain?>(null)
    val eqSnapshot: StateFlow<EQChain?> = _eqSnapshot

    private val _eqApplySaveMode = MutableStateFlow(ApplySaveMode.BOTH)
    val eqApplySaveMode: StateFlow<ApplySaveMode> = _eqApplySaveMode

    /** -1f = idle, 0..1 = rendering, exactly 1f shows checkmark briefly. */
    private val _eqRenderProgress = MutableStateFlow(-1f)
    val eqRenderProgress: StateFlow<Float> = _eqRenderProgress

    private val _eqSourceFile = MutableStateFlow<RecordFile?>(null)
    val eqSourceFile: StateFlow<RecordFile?> = _eqSourceFile

    private val _eqSourceSpectrum = MutableStateFlow<StaticSpectrum?>(null)
    val eqSourceSpectrum: StateFlow<StaticSpectrum?> = _eqSourceSpectrum

    private val _eqBypassed = MutableStateFlow(false)
    val eqBypassed: StateFlow<Boolean> = _eqBypassed

    /** True while the EQ screen should be shown — MainActivity observes for nav. */
    private val _eqOpen = MutableStateFlow(false)
    val eqOpen: StateFlow<Boolean> = _eqOpen

    /** Undo / redo stacks for the chain. Capped at EQ_HISTORY_CAP. */
    private val eqHistory = ArrayDeque<EQChain>()
    private val eqRedo = ArrayDeque<EQChain>()
    private val EQ_HISTORY_CAP = 10

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

    fun updateFileName(value: String) {
        _fileName.value = value
    }

    fun updateSceneName(value: String) {
        _sceneName.value = value
    }

    fun updateNotes(value: String) {
        _notes.value = value
    }

    fun toggleNoiseReduction(enabled: Boolean) {
        _noiseReductionEnabled.value = enabled
    }

    fun updateSampleRate(value: Int) {
        _sampleRate.value = value
    }

    fun updateAudioSource(name: String) {
        val source = AudioRecorderManager.AUDIO_SOURCES.find { it.first == name }
        if (source != null) {
            _audioSource.value = source.second
            _audioSourceName.value = source.first
        }
    }

    fun setSaveDirectoryUri(uri: Uri) {
        _saveDirectoryUri.value = uri
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

        if (_sampleRate.value !in listOf(44100, 48000, 96000)) {
            _errorMessage.value = "Sample rate is not supported."
            Toast.makeText(app, "Sample rate is not supported", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    fun startRecording() {
        Log.d(TAG, "startRecording() called")
        if (_isRecording.value) {
            Log.d(TAG, "Already recording, ignoring")
            return
        }

        if (!validateRecordingData()) {
            return
        }

        _isRecording.value = true
        _errorMessage.value = null
        recorder.setAudioSource(_audioSource.value)
        Log.d(TAG, "Set audio source to: ${_audioSource.value}")
        try {
            Log.d(TAG, "Calling recorder.start()")
            recorder.start(
                fileName = _fileName.value,
                sampleRate = _sampleRate.value,
                saveDirectoryUri = _saveDirectoryUri.value
            ) { level ->
                _currentWaveform.value = level
            }
            Log.d(TAG, "Recording started successfully")
            Toast.makeText(app, "Recording started", Toast.LENGTH_SHORT).show()
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
        val capturedScene = _sceneName.value
        val capturedNotes = _notes.value

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
                val renamedFile = if (renamedPath != recordedFile.path) {
                    val newFile = File(renamedPath)
                    Toast.makeText(app, "Auto-named file: ${newFile.name}", Toast.LENGTH_SHORT).show()
                    recordedFile.copy(name = newFile.name, path = newFile.absolutePath)
                } else {
                    recordedFile
                }

                val finalFile = if (_noiseReductionEnabled.value) {
                    Log.d(TAG, "Applying noise reduction")
                    withContext(Dispatchers.IO) { noiseProcessor.process(renamedFile) }
                } else {
                    renamedFile
                }

                _recordFiles.value = _recordFiles.value + finalFile
                _isRecording.value = false
                _currentWaveform.value = emptyList()
                Log.d(TAG, "Recording stopped successfully")
                Toast.makeText(app, "Recording saved: ${finalFile.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording: ${e.message}", e)
                _errorMessage.value = "Failed to stop recording: ${e.message}"
                _isRecording.value = false
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

    fun toggleLockRecording(file: RecordFile) {
        _recordFiles.value = _recordFiles.value.map {
            if (it.id == file.id) it.copy(isLocked = !it.isLocked) else it
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
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { noiseProcessor.process(file) }
            _recordFiles.value = _recordFiles.value.map {
                if (it.id == file.id) updated else it
            }
        }
    }

    // Playback functions
    fun selectFile(file: RecordFile) {
        _selectedFile.value = file
        preparePlayback(file)
    }

    private fun preparePlayback(file: RecordFile) {
        _isPlayerReady.value = false
        try {
            mediaPlayer.reset()
            mediaPlayer.setOnCompletionListener {
                positionUpdateJob?.cancel()
                _isPlaying.value = false
                _currentPlaybackPosition.value = 0
            }
            mediaPlayer.setOnPreparedListener { mp ->
                _playbackDuration.value = mp.duration
                _currentPlaybackPosition.value = 0
                _isPlayerReady.value = true
            }
            if (file.path.startsWith("content://")) {
                mediaPlayer.setDataSource(app, android.net.Uri.parse(file.path))
            } else {
                mediaPlayer.setDataSource(file.path)
            }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare playback: ${e.message}", e)
            _errorMessage.value = "Failed to prepare playback: ${e.message}"
        }
    }

    fun playPause() {
        if (!_isPlayerReady.value) return
        if (_isPlaying.value) {
            mediaPlayer.pause()
            _isPlaying.value = false
            positionUpdateJob?.cancel()
        } else {
            mediaPlayer.start()
            _isPlaying.value = true
            startPositionUpdates()
        }
    }

    fun stopPlayback() {
        positionUpdateJob?.cancel()
        _isPlaying.value = false
        _currentPlaybackPosition.value = 0
        // preparePlayback calls reset() (valid from any state) and re-prepares async
        _selectedFile.value?.let { preparePlayback(it) }
    }

    fun seekTo(position: Int) {
        mediaPlayer.seekTo(position)
        _currentPlaybackPosition.value = position
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (_isPlaying.value && mediaPlayer.isPlaying) {
                _currentPlaybackPosition.value = mediaPlayer.currentPosition
                delay(100)
            }
        }
    }

    // ============= EQ actions (Phase 1) =============

    private fun pushEqHistory(chain: EQChain) {
        eqHistory.addLast(chain)
        if (eqHistory.size > EQ_HISTORY_CAP) eqHistory.removeFirst()
        eqRedo.clear()
    }

    fun onEQOpen(file: RecordFile) {
        _eqSourceFile.value = file
        val srcPath = file.path
        if (!srcPath.startsWith("content://")) {
            val sidecar = File(srcPath.replace(Regex("\\.wav$", RegexOption.IGNORE_CASE), "_eq.json"))
            _currentEQChain.value = if (sidecar.exists()) {
                com.example.recorderproject.model.EQChainJson.fromJsonString(sidecar.readText())
                    ?: EQChain.empty()
            } else EQChain.empty()
        } else {
            _currentEQChain.value = EQChain.empty()
        }
        eqHistory.clear(); eqRedo.clear()
        _eqOpen.value = true

        viewModelScope.launch(Dispatchers.IO) {
            if (!srcPath.startsWith("content://")) {
                try {
                    val spec = com.example.recorderproject.audio.SpectrumAnalyzer
                        .analyzeFile(File(srcPath), bins = 256)
                    _eqSourceSpectrum.value = spec
                } catch (e: Exception) {
                    Log.e(TAG, "Spectrum compute failed: ${e.message}", e)
                    _eqSourceSpectrum.value = null
                }
            } else {
                _eqSourceSpectrum.value = null
            }
        }
    }

    fun onEQClose() {
        _eqSourceFile.value?.let { file ->
            if (!file.path.startsWith("content://")) {
                val sidecar = File(file.path.replace(Regex("\\.wav$", RegexOption.IGNORE_CASE), "_eq.json"))
                try {
                    sidecar.writeText(com.example.recorderproject.model.EQChainJson.toJsonString(_currentEQChain.value))
                } catch (e: Exception) {
                    Log.w(TAG, "Sidecar autosave failed: ${e.message}")
                }
            }
        }
        _eqOpen.value = false
        _eqSourceSpectrum.value = null
        _eqRenderProgress.value = -1f
    }

    fun onEQBandChanged(updated: com.example.recorderproject.model.EQBand) {
        pushEqHistory(_currentEQChain.value)
        _currentEQChain.value = _currentEQChain.value.withBand(updated)
    }

    fun onEQModeToggle(mode: EQEditMode) { _eqMode.value = mode }
    fun onEQViewModeToggle(mode: EQViewMode) { _eqViewMode.value = mode }
    fun onEQSelectBand(id: Int?) { _eqSelectedBandId.value = id }

    fun onEQUndo() {
        val prev = eqHistory.removeLastOrNull() ?: return
        eqRedo.addLast(_currentEQChain.value)
        _currentEQChain.value = prev
    }

    fun onEQRedo() {
        val next = eqRedo.removeLastOrNull() ?: return
        eqHistory.addLast(_currentEQChain.value)
        _currentEQChain.value = next
    }

    fun onEQABToggle() {
        val snap = _eqSnapshot.value
        if (snap == null) {
            _eqSnapshot.value = _currentEQChain.value
        } else {
            val current = _currentEQChain.value
            _currentEQChain.value = snap
            _eqSnapshot.value = current
        }
    }

    fun onEQResetAll() {
        pushEqHistory(_currentEQChain.value)
        _currentEQChain.value = EQChain.empty()
    }

    fun onEQPresetSelected(preset: com.example.recorderproject.model.EQPreset) {
        pushEqHistory(_currentEQChain.value)
        _currentEQChain.value = EQChain(bands = preset.bands)
    }

    fun onEQToggleBypass() {
        _currentEQChain.value = _currentEQChain.value.copy(bypassed = !_currentEQChain.value.bypassed)
    }

    fun onEQToggleGainCompensation() {
        _currentEQChain.value = _currentEQChain.value.copy(gainCompensation = !_currentEQChain.value.gainCompensation)
    }

    fun onEQHumDetect(mainsHz: Float = 60f) {
        pushEqHistory(_currentEQChain.value)
        val combNotches = com.example.recorderproject.audio.EQHumDetect.combNotches(mainsHz)
        // Place into the chain — fill from band 1 onward, overwriting disabled slots
        var chain = EQChain.empty()
        for ((idx, notch) in combNotches.withIndex()) {
            if (idx >= 8) break
            chain = chain.withBand(notch.copy(id = idx + 1))
        }
        _currentEQChain.value = chain
        Toast.makeText(app, "Placed ${combNotches.size}-notch hum comb at ${mainsHz.toInt()} Hz", Toast.LENGTH_SHORT).show()
    }

    fun onEQRandomPreset() {
        pushEqHistory(_currentEQChain.value)
        _currentEQChain.value = com.example.recorderproject.model.EQRandomPreset.generate()
    }

    fun onEQSaveAsCustomPreset(name: String) {
        val store = com.example.recorderproject.data.CustomPresetStore(app)
        store.save(name, _currentEQChain.value)
        Toast.makeText(app, "Saved preset: $name", Toast.LENGTH_SHORT).show()
    }

    fun onEQExportCurvePng() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = com.example.recorderproject.ui.components.CurveBitmapExport
                    .exportToGallery(app, _currentEQChain.value, _sampleRate.value.toFloat())
                withContext(Dispatchers.Main) {
                    if (path != null) Toast.makeText(app, "Curve PNG saved", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(app, "PNG save failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Curve PNG export failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "PNG export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun onEQNoiseAutoDetect() {
        val spec = _eqSourceSpectrum.value ?: return
        val suggestions = com.example.recorderproject.audio.EQAutoDetect.proposeNotches(spec, maxBands = 4)
        _currentEQChain.value = _currentEQChain.value.copy(noiseCutSuggestions = suggestions)
        if (suggestions.isEmpty()) {
            Toast.makeText(app, "Spectrum is clean — no peaks detected", Toast.LENGTH_SHORT).show()
        }
    }

    fun onEQAcceptSuggestion(band: com.example.recorderproject.model.EQBand) {
        val current = _currentEQChain.value
        val added = current.withAddedBand(band) ?: run {
            Toast.makeText(app, "8-band limit reached — disable a band first", Toast.LENGTH_SHORT).show()
            return
        }
        pushEqHistory(current)
        _currentEQChain.value = added.copy(
            noiseCutSuggestions = current.noiseCutSuggestions.filter { it.id != band.id }
        )
    }

    fun onEQRejectSuggestion(band: com.example.recorderproject.model.EQBand) {
        _currentEQChain.value = _currentEQChain.value.copy(
            noiseCutSuggestions = _currentEQChain.value.noiseCutSuggestions.filter { it.id != band.id }
        )
    }

    fun onEQDrawCurve(targetDbCurve: FloatArray) {
        val bands = com.example.recorderproject.audio.EQCurveFitter
            .fitToCurve(targetDbCurve, 20f, 20_000f, maxBands = 6)
        if (bands.isEmpty()) return
        pushEqHistory(_currentEQChain.value)
        val padded = bands + (bands.size + 1..8).map { com.example.recorderproject.model.EQBand.defaultForSlot(it) }
        _currentEQChain.value = EQChain(bands = padded.take(8))
    }

    fun onEQTapNotch(frequencyHz: Float) {
        val newBand = com.example.recorderproject.model.EQBand(
            id = 0,
            type = com.example.recorderproject.model.EQBandType.NOTCH,
            frequencyHz = frequencyHz,
            gainDb = 0f,
            q = 8f,
            enabled = true,
        )
        val added = _currentEQChain.value.withAddedBand(newBand) ?: run {
            Toast.makeText(app, "8-band limit reached — disable a band first", Toast.LENGTH_SHORT).show()
            return
        }
        pushEqHistory(_currentEQChain.value)
        _currentEQChain.value = added
    }

    fun onEQSaveModeChange(mode: ApplySaveMode) { _eqApplySaveMode.value = mode }

    fun onEQApply() {
        val src = _eqSourceFile.value ?: return
        val mode = _eqApplySaveMode.value
        if (mode == ApplySaveMode.ORIGINAL_ONLY) {
            _currentEQChain.value = EQChain.empty()
            onEQClose()
            return
        }
        if (src.path.startsWith("content://")) {
            Toast.makeText(app, "SAF (content://) sources not supported for Apply yet — save to a local folder", Toast.LENGTH_LONG).show()
            return
        }
        val srcFile = File(src.path)
        val eqFile = File(srcFile.parentFile, srcFile.nameWithoutExtension + "_eq.wav")
        val chain = _currentEQChain.value
        viewModelScope.launch(Dispatchers.IO) {
            _eqRenderProgress.value = 0f
            try {
                com.example.recorderproject.audio.EQProcessor.process(srcFile, eqFile, chain) { p ->
                    _eqRenderProgress.value = p
                }
                when (mode) {
                    ApplySaveMode.BOTH -> {
                        File(srcFile.parentFile, srcFile.nameWithoutExtension + "_eq.json")
                            .writeText(com.example.recorderproject.model.EQChainJson.toJsonString(chain))
                        _recordFiles.value = _recordFiles.value.map {
                            if (it.id == src.id) it.copy(hasEQ = true) else it
                        }
                    }
                    ApplySaveMode.EQ_ONLY -> {
                        val tmpRename = File(srcFile.parentFile, srcFile.name + ".replacing")
                        srcFile.renameTo(tmpRename)
                        if (eqFile.renameTo(srcFile)) {
                            tmpRename.delete()
                        } else {
                            tmpRename.renameTo(srcFile)
                            eqFile.delete()
                            throw RuntimeException("Atomic rename failed")
                        }
                        _recordFiles.value = _recordFiles.value.map {
                            if (it.id == src.id) it.copy(hasEQ = true) else it
                        }
                    }
                    ApplySaveMode.ORIGINAL_ONLY -> Unit
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "EQ applied", Toast.LENGTH_SHORT).show()
                    delay(600)
                    _eqRenderProgress.value = -1f
                    onEQClose()
                }
            } catch (e: Exception) {
                Log.e(TAG, "EQ render failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "EQ render failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                _eqRenderProgress.value = -1f
                eqFile.delete()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer.release()
    }
}
