# PlaybackManager Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the MediaPlayer playback subsystem + A/B compare out of `RecorderViewModel` into a `PlaybackManager` collaborator, with the ViewModel delegating so its public API stays identical.

**Architecture:** Task 1 adds `PlaybackManager` (owns the `MediaPlayer`, position-update job, playback + A/B state, controls with persistence); it compiles standalone, unused. Task 2 rewires the ViewModel in one compile-coherent change: add the `playback` field, replace all playback state declarations and function bodies with delegations, delete the moved private helpers, and route the general error flow through an `onError` callback. Behavior-preserving move of pure Android glue — regression guard is clean compile + the full existing unit suite (98) staying green.

**Tech Stack:** Kotlin, Android (`MediaPlayer`, `Context`), kotlinx.coroutines (`StateFlow`, `Job`), JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-08-playbackmanager-extraction-design.md`

---

## File Structure

- **Create** `app/src/main/java/com/example/recorderproject/audio/PlaybackManager.kt` — owns playback + A/B state, the `MediaPlayer`, the position-update job, and speed/loop/volume with persistence. One responsibility.
- **Modify** `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` — add the `playback` field (before the `init` block, since hydration delegates to it); replace 11 state declarations and 12 functions with delegations; delete `preparePlayback`/`startPositionUpdates`/`mediaPlayer`/`positionUpdateJob`; route `_errorMessage` via `onError`; update `applySnapshot` and `onCleared`; drop the now-unused `MediaPlayer` import. Keep `openAbCompareFromSelection` (resolver over file-library state).

---

## Task 1: Add PlaybackManager (standalone, unused)

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/PlaybackManager.kt`

- [ ] **Step 1: Create the file**

```kotlin
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
```

- [ ] **Step 2: Compile (the class is unused; build stays green)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/PlaybackManager.kt
git commit -m "feat: add PlaybackManager collaborator (#13 step 3, not yet wired)"
```

---

## Task 2: Rewire RecorderViewModel to delegate

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

Apply all edits (Steps 1–12), then verify in Step 13. The edits are interdependent — the build only goes green after all are applied. Each is a unique find/replace.

- [ ] **Step 1: Remove the `mediaPlayer` field** (it moves into the manager)

FIND:
```kotlin
    private val mediaPlayer = MediaPlayer()
    private val settings = SettingsDataStore(application)
```
REPLACE:
```kotlin
    private val settings = SettingsDataStore(application)
```

- [ ] **Step 2: Add the `playback` field (before the init block that uses it via hydration)**

FIND:
```kotlin
    private val hydrated = MutableStateFlow(false)
```
REPLACE:
```kotlin
    private val hydrated = MutableStateFlow(false)

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
```

- [ ] **Step 3: Delegate the A/B state declarations**

FIND:
```kotlin
    // A/B compare: two takes selected from the list. Playback plays A, then B, in sequence.
    private val _abFiles = MutableStateFlow<Pair<com.example.recorderproject.model.RecordFile, com.example.recorderproject.model.RecordFile>?>(null)
    val abFiles: StateFlow<Pair<com.example.recorderproject.model.RecordFile, com.example.recorderproject.model.RecordFile>?> = _abFiles

    private val _abPlayingSlot = MutableStateFlow(0) // 0=idle, 1=A playing, 2=B playing
    val abPlayingSlot: StateFlow<Int> = _abPlayingSlot

    private val _abCompareOpen = MutableStateFlow(false)
    val abCompareOpen: StateFlow<Boolean> = _abCompareOpen
```
REPLACE:
```kotlin
    // A/B compare state — delegated to PlaybackManager (issue #13).
    val abFiles = playback.abFiles
    val abPlayingSlot = playback.abPlayingSlot
    val abCompareOpen = playback.abCompareOpen
```

- [ ] **Step 4: Delegate the A/B functions (keep `openAbCompareFromSelection`)**

FIND:
```kotlin
    fun openAbCompare(a: com.example.recorderproject.model.RecordFile, b: com.example.recorderproject.model.RecordFile) {
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

    /**
     * Play slot A or B. Stops any current playback then plays the requested file.
     */
    fun abPlay(slot: Int) {
        val pair = _abFiles.value ?: return
        val file = if (slot == 1) pair.first else pair.second
        _abPlayingSlot.value = slot
        try {
            mediaPlayer.reset()
            if (file.path.startsWith("content://")) {
                mediaPlayer.setDataSource(app, android.net.Uri.parse(file.path))
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
```
REPLACE:
```kotlin
    fun openAbCompare(a: com.example.recorderproject.model.RecordFile, b: com.example.recorderproject.model.RecordFile) =
        playback.openAbCompare(a, b)

    fun closeAbCompare() = playback.closeAbCompare()

    /** Play slot A or B. Stops any current playback then plays the requested file. */
    fun abPlay(slot: Int) = playback.abPlay(slot)

    fun abStop() = playback.abStop()
```

- [ ] **Step 5: Delegate the core playback state declarations**

FIND:
```kotlin
    // Playback states
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPlaybackPosition = MutableStateFlow(0)
    val currentPlaybackPosition: StateFlow<Int> = _currentPlaybackPosition

    private val _playbackDuration = MutableStateFlow(0)
    val playbackDuration: StateFlow<Int> = _playbackDuration

    private val _selectedFile = MutableStateFlow<RecordFile?>(null)
    val selectedFile: StateFlow<RecordFile?> = _selectedFile
```
REPLACE:
```kotlin
    // Playback states — delegated to PlaybackManager (issue #13).
    val isPlaying = playback.isPlaying
    val currentPlaybackPosition = playback.currentPlaybackPosition
    val playbackDuration = playback.playbackDuration
    val selectedFile = playback.selectedFile
```

- [ ] **Step 6: Remove `positionUpdateJob`, delegate `isPlayerReady`**

FIND:
```kotlin
    private var positionUpdateJob: Job? = null

    private val _isPlayerReady = MutableStateFlow(false)
    val isPlayerReady: StateFlow<Boolean> = _isPlayerReady
```
REPLACE:
```kotlin
    val isPlayerReady = playback.isPlayerReady
```

- [ ] **Step 7: Delegate the playback functions, delete `preparePlayback`**

FIND (from the `// Playback functions` comment through the end of `closePlayer`):
```kotlin
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
                // G1: auto-start on selectFile so tapping a row plays immediately
                try {
                    mp.start()
                    _isPlaying.value = true
                    startPositionUpdates()
                } catch (_: Exception) {}
            }
            // Surface async prepare/playback failures instead of hanging silently.
            // (e.g. some devices can't decode 24-bit WAV via MediaPlayer — the file is
            // still valid on disk, the user just gets told rather than a dead Play button.)
            mediaPlayer.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra for ${file.name}")
                _isPlayerReady.value = false
                _isPlaying.value = false
                positionUpdateJob?.cancel()
                _errorMessage.value = "Can't play ${file.name} (code $what/$extra)"
                true
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

    fun closePlayer() {
        try { mediaPlayer.reset() } catch (_: Exception) {}
        _isPlaying.value = false
        _selectedFile.value = null
        _currentPlaybackPosition.value = 0
        positionUpdateJob?.cancel()
    }
```
REPLACE:
```kotlin
    // Playback functions — delegated to PlaybackManager (issue #13).
    fun selectFile(file: RecordFile) = playback.selectFile(file)

    fun playPause() = playback.playPause()

    fun stopPlayback() = playback.stopPlayback()

    fun seekTo(position: Int) = playback.seekTo(position)

    fun closePlayer() = playback.closePlayer()
```

- [ ] **Step 8: Delegate speed/loop/volume**

FIND:
```kotlin
    // G5: playback speed
    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed
    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        try {
            val params = mediaPlayer.playbackParams
            params.speed = speed
            mediaPlayer.playbackParams = params
        } catch (_: Exception) {}
        if (hydrated.value) viewModelScope.launch { settings.setPlaybackSpeed(speed) }
    }

    // G6: loop playback
    private val _playbackLoop = MutableStateFlow(false)
    val playbackLoop: StateFlow<Boolean> = _playbackLoop
    fun toggleLoop() {
        _playbackLoop.value = !_playbackLoop.value
        mediaPlayer.isLooping = _playbackLoop.value
        if (hydrated.value) viewModelScope.launch { settings.setPlaybackLoop(_playbackLoop.value) }
    }

    // G7: playback volume (0..1)
    private val _playbackVolume = MutableStateFlow(1f)
    val playbackVolume: StateFlow<Float> = _playbackVolume
    fun setPlaybackVolume(v: Float) {
        val vv = v.coerceIn(0f, 1f)
        _playbackVolume.value = vv
        mediaPlayer.setVolume(vv, vv)
        if (hydrated.value) viewModelScope.launch { settings.setPlaybackVolume(vv) }
    }
```
REPLACE:
```kotlin
    // G5/G6/G7: playback speed / loop / volume — delegated to PlaybackManager (issue #13).
    val playbackSpeed = playback.playbackSpeed
    fun setPlaybackSpeed(speed: Float) = playback.setPlaybackSpeed(speed)

    val playbackLoop = playback.playbackLoop
    fun toggleLoop() = playback.toggleLoop()

    val playbackVolume = playback.playbackVolume
    fun setPlaybackVolume(v: Float) = playback.setPlaybackVolume(v)
```

- [ ] **Step 9: Delete `startPositionUpdates` (moved to the manager)**

FIND:
```kotlin
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (_isPlaying.value && mediaPlayer.isPlaying) {
                _currentPlaybackPosition.value = mediaPlayer.currentPosition
                delay(100)
            }
        }
    }
```
REPLACE with nothing (delete the whole function).

- [ ] **Step 10: Delegate playback hydration in `applySnapshot`**

FIND:
```kotlin
        _playbackSpeed.value = s.playbackSpeed
        _playbackLoop.value = s.playbackLoop
        _playbackVolume.value = s.playbackVolume
```
REPLACE:
```kotlin
        playback.applySnapshot(s.playbackSpeed, s.playbackLoop, s.playbackVolume)
```

- [ ] **Step 11: Release via the manager in `onCleared`**

FIND:
```kotlin
        abandonAudioFocus()
        mediaPlayer.release()
```
REPLACE:
```kotlin
        abandonAudioFocus()
        playback.release()
```

- [ ] **Step 12: Remove the now-unused `MediaPlayer` import**

FIND:
```kotlin
import android.media.MediaPlayer
```
REPLACE with nothing (delete the line). (`MediaPlayer` is now only used inside `PlaybackManager`.)

- [ ] **Step 13: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass (98 total, unchanged).

If compilation fails with "unresolved reference `_isPlaying`/`_selectedFile`/`mediaPlayer`/`positionUpdateJob`/`_playbackSpeed`/etc.", a delegation was missed — find the remaining reference and convert it. If it fails on an unused import other than MediaPlayer, only then remove that exact import line. If a reference you cannot explain remains, report BLOCKED with the error.

- [ ] **Step 14: Confirm suite count and zero failures**

Run:
```bash
total=0; fail=0; err=0; for f in app/build/test-results/testDebugUnitTest/*.xml; do t=$(grep -oE 'tests="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); fl=$(grep -oE 'failures="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); e=$(grep -oE 'errors="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); total=$((total+t)); fail=$((fail+fl)); err=$((err+e)); done; echo "tests: $total failures: $fail errors: $err"
```
Expected: `tests: 98 failures: 0 errors: 0`.

- [ ] **Step 15: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "refactor: delegate playback + A/B compare to PlaybackManager (#13 step 3)"
```

---

## Device Walkthrough (manual acceptance — run after Task 2)

1. Tap a recording → it auto-plays; play/pause toggles; seek works; position updates while playing.
2. Speed (0.5×–2×), loop toggle, and volume all affect playback.
3. Close player resets state; selecting another file plays it.
4. A/B compare: open from a two-file selection, play A, play B, stop; close resets.
5. A bad/undecodable file surfaces an error message (not a hung Play button).
6. Speed, loop, and volume are restored after an app restart (persistence intact).

---

## Self-Review

**Spec coverage:**
- `PlaybackManager` interface (11 StateFlows + 12 functions + applySnapshot + release + onError) → Task 1. ✓
- Owns `mediaPlayer` + `positionUpdateJob`; `preparePlayback`/`startPositionUpdates` private → Task 1. ✓
- Persistence via `isHydrated()` + `scope.launch { settings... }` → Task 1 (setPlaybackSpeed/toggleLoop/setPlaybackVolume). ✓
- `onError` bridges `_errorMessage` (stays in VM) → Task 1 (preparePlayback) + Task 2 Step 2 (`onError = { _errorMessage.value = it }`). ✓
- VM delegates all 11 states + 12 functions, public API unchanged → Task 2 Steps 3–8. ✓
- `openAbCompareFromSelection` stays in VM → not edited (only `openAbCompare` it calls is now a delegation). ✓
- `applySnapshot` + `onCleared` updated → Task 2 Steps 10–11. ✓
- `mediaPlayer`/`positionUpdateJob` fields + `preparePlayback`/`startPositionUpdates` removed from VM → Task 2 Steps 1, 6, 7, 9. ✓
- `playback` declared before init block (init order) → Task 2 Step 2 (after `hydrated`, before `init`). ✓
- Testing = compile + suite green (no artificial tests) → Task 2 Steps 13–14. ✓
- Scope = playback only; `_recordFiles`/selection stay → untouched. ✓

**Placeholder scan:** none — all edits show complete before/after code; the Step 13 "if it fails" guidance is concrete recovery, not a placeholder.

**Type consistency:** `PlaybackManager(app, settings, scope, isHydrated, onError)` (Task 1) matches the call in Task 2 Step 2. All delegated property names (`isPlaying`, `currentPlaybackPosition`, `playbackDuration`, `selectedFile`, `isPlayerReady`, `playbackSpeed`, `playbackLoop`, `playbackVolume`, `abFiles`, `abPlayingSlot`, `abCompareOpen`) and method names (`selectFile`, `playPause`, `stopPlayback`, `seekTo`, `closePlayer`, `setPlaybackSpeed`, `toggleLoop`, `setPlaybackVolume`, `openAbCompare`, `closeAbCompare`, `abPlay`, `abStop`, `applySnapshot`, `release`) are identical between the manager (Task 1) and the VM delegations (Task 2). `abFiles` type `Pair<RecordFile, RecordFile>?` matches the VM's prior `Pair<...RecordFile, ...RecordFile>?` (same type, manager imports `RecordFile`).
