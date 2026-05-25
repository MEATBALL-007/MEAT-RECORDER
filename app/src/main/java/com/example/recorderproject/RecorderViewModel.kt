package com.example.recorderproject

import android.app.Application
import android.net.Uri
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recorderproject.audio.AudioRecorderManager
import com.example.recorderproject.audio.NoiseReductionProcessor
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

    override fun onCleared() {
        super.onCleared()
        mediaPlayer.release()
    }
}
