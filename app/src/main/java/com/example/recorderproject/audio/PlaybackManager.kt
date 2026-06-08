package com.example.recorderproject.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.model.RecordFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns audio playback (a single MediaPlayer, its lifecycle, position tracking, speed/loop/volume
 * with persistence) and the A/B-compare feature that reuses the same player. Extracted from
 * RecorderViewModel (issue #13). Android-coupled, so verified by compile + the app's existing
 * suite + device smoke rather than unit tests.
 *
 * @param onError forwards a user-facing message to the ViewModel's general _errorMessage flow.
 */
class PlaybackManager(
    private val app: Context,
    private val settings: SettingsDataStore,
    private val scope: CoroutineScope,
    private val isHydrated: () -> Boolean,
    private val onError: (String) -> Unit,
) {
    private val mediaPlayer = MediaPlayer()
    private var positionUpdateJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPlaybackPosition = MutableStateFlow(0)
    val currentPlaybackPosition: StateFlow<Int> = _currentPlaybackPosition

    private val _playbackDuration = MutableStateFlow(0)
    val playbackDuration: StateFlow<Int> = _playbackDuration

    private val _selectedFile = MutableStateFlow<RecordFile?>(null)
    val selectedFile: StateFlow<RecordFile?> = _selectedFile

    private val _isPlayerReady = MutableStateFlow(false)
    val isPlayerReady: StateFlow<Boolean> = _isPlayerReady

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _playbackLoop = MutableStateFlow(false)
    val playbackLoop: StateFlow<Boolean> = _playbackLoop

    private val _playbackVolume = MutableStateFlow(1f)
    val playbackVolume: StateFlow<Float> = _playbackVolume

    // A/B compare: two takes; plays A or B on demand using the same MediaPlayer.
    private val _abFiles = MutableStateFlow<Pair<RecordFile, RecordFile>?>(null)
    val abFiles: StateFlow<Pair<RecordFile, RecordFile>?> = _abFiles

    private val _abPlayingSlot = MutableStateFlow(0) // 0=idle, 1=A playing, 2=B playing
    val abPlayingSlot: StateFlow<Int> = _abPlayingSlot

    private val _abCompareOpen = MutableStateFlow(false)
    val abCompareOpen: StateFlow<Boolean> = _abCompareOpen

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
                // Auto-start on select so tapping a row plays immediately.
                try {
                    mp.start()
                    _isPlaying.value = true
                    startPositionUpdates()
                } catch (_: Exception) {}
            }
            // Surface async prepare/playback failures instead of hanging silently.
            mediaPlayer.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra for ${file.name}")
                _isPlayerReady.value = false
                _isPlaying.value = false
                positionUpdateJob?.cancel()
                onError("Can't play ${file.name} (code $what/$extra)")
                true
            }
            if (file.path.startsWith("content://")) {
                mediaPlayer.setDataSource(app, Uri.parse(file.path))
            } else {
                mediaPlayer.setDataSource(file.path)
            }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare playback: ${e.message}", e)
            onError("Failed to prepare playback: ${e.message}")
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

    fun closePlayer() {
        try { mediaPlayer.reset() } catch (_: Exception) {}
        _isPlaying.value = false
        _selectedFile.value = null
        _currentPlaybackPosition.value = 0
        positionUpdateJob?.cancel()
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        try {
            val params = mediaPlayer.playbackParams
            params.speed = speed
            mediaPlayer.playbackParams = params
        } catch (_: Exception) {}
        if (isHydrated()) scope.launch { settings.setPlaybackSpeed(speed) }
    }

    fun toggleLoop() {
        _playbackLoop.value = !_playbackLoop.value
        mediaPlayer.isLooping = _playbackLoop.value
        if (isHydrated()) scope.launch { settings.setPlaybackLoop(_playbackLoop.value) }
    }

    fun setPlaybackVolume(v: Float) {
        val vv = v.coerceIn(0f, 1f)
        _playbackVolume.value = vv
        mediaPlayer.setVolume(vv, vv)
        if (isHydrated()) scope.launch { settings.setPlaybackVolume(vv) }
    }

    fun openAbCompare(a: RecordFile, b: RecordFile) {
        _abFiles.value = a to b
        _abCompareOpen.value = true
        _abPlayingSlot.value = 0
    }

    fun closeAbCompare() {
        _abCompareOpen.value = false
        _abFiles.value = null
        _abPlayingSlot.value = 0
        try { mediaPlayer.reset() } catch (_: Exception) {}
        _isPlaying.value = false
    }

    /** Play slot A (1) or B (2). Stops any current playback then plays the requested file. */
    fun abPlay(slot: Int) {
        val pair = _abFiles.value ?: return
        val file = if (slot == 1) pair.first else pair.second
        _abPlayingSlot.value = slot
        try {
            mediaPlayer.reset()
            if (file.path.startsWith("content://")) {
                mediaPlayer.setDataSource(app, Uri.parse(file.path))
            } else {
                mediaPlayer.setDataSource(file.path)
            }
            mediaPlayer.setOnCompletionListener {
                _abPlayingSlot.value = 0
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e(TAG, "abPlay failed: ${e.message}", e)
        }
    }

    fun abStop() {
        try { mediaPlayer.reset() } catch (_: Exception) {}
        _isPlaying.value = false
        _abPlayingSlot.value = 0
    }

    /** Hydrate persisted speed/loop/volume. Does not persist. */
    fun applySnapshot(speed: Float, loop: Boolean, volume: Float) {
        _playbackSpeed.value = speed
        _playbackLoop.value = loop
        _playbackVolume.value = volume
    }

    /** Release the MediaPlayer (call from ViewModel.onCleared). */
    fun release() {
        positionUpdateJob?.cancel()
        mediaPlayer.release()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (_isPlaying.value && mediaPlayer.isPlaying) {
                _currentPlaybackPosition.value = mediaPlayer.currentPosition
                delay(100)
            }
        }
    }

    companion object { private const val TAG = "PlaybackManager" }
}
