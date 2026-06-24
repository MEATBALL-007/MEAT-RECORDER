# PlaybackManager extraction (RecorderViewModel decomposition, step 3)

**Date:** 2026-06-08
**Issue:** #13 — `RecorderViewModel` god-object decomposition. Steps 1 (`LoudnessManager`) and 2 (`RecordingScanner`) are done; VM is at ~2,900 lines.
**Status:** Approved design, pending implementation plan.

## Problem

`RecorderViewModel` owns the entire audio playback subsystem: a `MediaPlayer`, its lifecycle (prepare/play/pause/seek/close), a position-update coroutine, speed/loop/volume controls with persistence, and the A/B-compare feature (which reuses the same `MediaPlayer`). That is ~110 lines of one clearly separable responsibility.

This is step 3 of the decomposition, reusing the established template: move the cluster into a collaborator class, have the ViewModel delegate, keep the public API identical so the UI and tests are unaffected.

## Coupling findings (de-risk)

- `_selectedFile`, `_isPlaying`, and `mediaPlayer` are read/written **only** within the playback + A/B cluster — no external ViewModel readers. (`_selectedFileIds` is the separate multi-select state for the file library and is unrelated.)
- Recording does **not** touch playback (no `mediaPlayer`/`_isPlaying` access in the recording path).
- The only outward ties are: persistence (`settings` + the `hydrated` gate), `mediaPlayer.release()` in `onCleared`, `openAbCompareFromSelection()` reading `_selectedFileIds` + `_recordFiles` to resolve two files, and `preparePlayback()` writing the general `_errorMessage` flow (which stays in the ViewModel) on prepare/decode failure — bridged via an `onError` callback.

## Scope

Playback + A/B compare only. `_recordFiles` and multi-select (`_selectedFileIds`) stay in the ViewModel. `openAbCompareFromSelection()` stays in the ViewModel as a thin resolver.

## Design

New file `app/src/main/java/com/example/recorderproject/audio/PlaybackManager.kt` (the `audio/` package, alongside `AudioRecorderManager`/`AudioMonitor`; it owns a `MediaPlayer`).

### `PlaybackManager`

```kotlin
class PlaybackManager(
    private val app: Context,
    private val settings: SettingsDataStore,
    private val scope: CoroutineScope,       // viewModelScope
    private val isHydrated: () -> Boolean,    // gates persistence writes
    private val onError: (String) -> Unit,    // forwards to the VM's _errorMessage flow
) {
    // Playback state
    val isPlaying: StateFlow<Boolean>
    val currentPlaybackPosition: StateFlow<Int>
    val playbackDuration: StateFlow<Int>
    val selectedFile: StateFlow<RecordFile?>
    val isPlayerReady: StateFlow<Boolean>
    val playbackSpeed: StateFlow<Float>
    val playbackLoop: StateFlow<Boolean>
    val playbackVolume: StateFlow<Float>
    // A/B-compare state
    val abFiles: StateFlow<Pair<RecordFile, RecordFile>?>
    val abPlayingSlot: StateFlow<Int>
    val abCompareOpen: StateFlow<Boolean>

    fun selectFile(file: RecordFile)
    fun playPause()
    fun stopPlayback()
    fun seekTo(position: Int)
    fun closePlayer()
    fun setPlaybackSpeed(speed: Float)
    fun toggleLoop()
    fun setPlaybackVolume(v: Float)
    fun openAbCompare(a: RecordFile, b: RecordFile)
    fun closeAbCompare()
    fun abPlay(slot: Int)
    fun abStop()
    fun applySnapshot(speed: Float, loop: Boolean, volume: Float)  // hydration; no persist
    fun release()                                                  // onCleared
}
```

Internals (moved verbatim except for state/scope/persistence references):
- Owns `private val mediaPlayer = MediaPlayer()` and `private var positionUpdateJob: Job?`.
- `preparePlayback(file)` and `startPositionUpdates()` become private members of the manager.
- Persistence writes use `if (isHydrated()) scope.launch { settings.setPlaybackSpeed/Loop/Volume(...) }`, exactly mirroring the current `hydrated.value` gating.
- `selectFile` calls `preparePlayback` (auto-start on prepared, as today).
- `preparePlayback` reports prepare/decode failures via `onError(...)` instead of writing `_errorMessage` directly.
- `release()` performs the current `onCleared` `mediaPlayer.release()`.

### ViewModel changes (public surface unchanged)

- Declare `private val playback = PlaybackManager(app, settings, viewModelScope, { hydrated.value }, { _errorMessage.value = it })` after `settings`/`hydrated` and before first use.
- Replace the 11 state declarations with delegations:
  ```kotlin
  val isPlaying = playback.isPlaying
  val currentPlaybackPosition = playback.currentPlaybackPosition
  val playbackDuration = playback.playbackDuration
  val selectedFile = playback.selectedFile
  val isPlayerReady = playback.isPlayerReady
  val playbackSpeed = playback.playbackSpeed
  val playbackLoop = playback.playbackLoop
  val playbackVolume = playback.playbackVolume
  val abFiles = playback.abFiles
  val abPlayingSlot = playback.abPlayingSlot
  val abCompareOpen = playback.abCompareOpen
  ```
- Replace the 12 function bodies with one-line delegations (`fun playPause() = playback.playPause()`, etc.).
- Keep `openAbCompareFromSelection()` in the ViewModel: it resolves two files from `_selectedFileIds` + `_recordFiles` and calls `playback.openAbCompare(a, b)`.
- `applySnapshot` lines (currently `_playbackSpeed/_playbackLoop/_playbackVolume = s....`) become `playback.applySnapshot(s.playbackSpeed, s.playbackLoop, s.playbackVolume)`.
- `onCleared`'s `mediaPlayer.release()` becomes `playback.release()`.
- Remove the `mediaPlayer` and `positionUpdateJob` fields from the ViewModel (moved into the manager).

### Net effect

~110 lines move out; `PlaybackManager` is a ~160-line single-responsibility unit. The ViewModel's public surface is unchanged, so no UI or test file changes.

## Testing

Behavior-preserving move of pure Android glue (`MediaPlayer`, coroutines, `settings`). Not JVM-unit-testable in this project (no Robolectric). Safety net: identical public API + the full existing unit suite (98 tests) staying green + clean compile.

### Device smoke (acceptance)

1. Tap a recording → it auto-plays; play/pause toggles; seek works; position updates.
2. Speed (0.5×–2×), loop toggle, and volume all affect playback.
3. Close player resets state; selecting another file plays it.
4. A/B compare: open from a two-file selection, play A, play B, stop; close resets.
5. Speed, loop, and volume are restored after an app restart (persistence intact).

## Out of scope

- Moving `_recordFiles` / multi-select ownership — a later FileLibrary-state step.
- Any behavior change to playback or A/B — this is a pure move.
- Adding Robolectric or instrumented tests for the player.
