# LoudnessManager extraction (RecorderViewModel decomposition, step 1)

**Date:** 2026-06-08
**Issue:** #13 — `RecorderViewModel` is a 3,144-line god object; maintainability risk.
**Status:** Approved design, pending implementation plan.

## Problem

`RecorderViewModel` (3,144 lines) mixes a dozen responsibilities — recording control, audio input config, EQ/DSP, playback, file management, monitoring, settings, cloud, and loudness/delivery. It has near-zero direct test coverage (two small tests), so any change carries regression risk.

This is the **first** of several behavior-preserving extractions. It establishes the template the rest of the decomposition will reuse: move a cohesive cluster into a collaborator class (matching the existing `AudioRecorderManager` / `NoiseReductionProcessor` pattern in `audio/`), and have the ViewModel delegate to it while keeping its public API identical so the UI and tests are unaffected.

## Scope

**This cluster only: loudness/delivery.** The other seven clusters (recording control, EQ editor, playback, file library, monitoring, settings hydration, cloud) are explicitly out of scope for this step. Each will get its own spec → plan → implementation cycle.

## Key finding (de-risks the work)

`renderSemaphore` (`Semaphore(permits = 2)`) is used **only** by delivery rendering — its declaration (line 536) and the two render sites (lines 1914, 2031). EQ's `onEQApply` / `onEQApplySaf` do not use it. So it is purely a delivery concern and moves entirely into `LoudnessManager` with no shared-resource entanglement. (The earlier structural map's claim that it was shared with EQ was incorrect.)

## Members being moved

From `RecorderViewModel.kt`:
- State: `_loudnessTarget` / `loudnessTarget` (525-526), `_isRenderingDelivery` / `isRenderingDelivery` (529-530), `_lastDeliveryResult` / `lastDeliveryResult` (533-534), `renderSemaphore` (536).
- The auto-render block inside `stopRecording` (1908-1942), which already branches local vs SAF.
- `renderDeliverySaf` (2027-2065, added in #11).
- `setSessionLoudnessTarget` (2803-2805), `saveAsDefaultLoudnessTarget` (2815-2823).
- The loudness lines of `applySnapshot` (2795-2799).

Staying in the ViewModel:
- `rebindDeliveryResult` (2014-2018) — it mutates the ViewModel-owned `_recordFiles`, so it stays and is passed to the manager as a callback.

## Design

### `LoudnessManager`

New file `app/src/main/java/com/example/recorderproject/audio/LoudnessManager.kt`.

```kotlin
class LoudnessManager(
    private val app: Context,
    private val settings: SettingsDataStore,
    private val scope: CoroutineScope,                                 // viewModelScope
    private val saveDirectoryUri: () -> Uri?,                          // current SAF tree, or null
    private val onDeliveryResult: (srcPath: String, dstPath: String, result: DeliveryResult) -> Unit,
) {
    val target: StateFlow<LoudnessTarget>
    val isRendering: StateFlow<Boolean>
    val lastResult: StateFlow<DeliveryResult?>

    /** Hydrate the session target from persisted settings. Does not persist. */
    fun applySnapshot(key: String, customLufs: Float, customTp: Float)

    /** Change the active session target. Does not persist. */
    fun setSessionTarget(t: LoudnessTarget)

    /** Persist as default and update the session target. */
    fun saveAsDefault(t: LoudnessTarget)

    /** Render delivery for a just-finalized recording if a target is active. */
    fun renderFor(finalFile: RecordFile)
}
```

Internals (moved verbatim except for the state/callback references):
- Owns `private val renderSemaphore = Semaphore(permits = 2)`.
- `renderFor`: if `target.value is LoudnessTarget.Off` → return; else if `finalFile.path.startsWith("content://")` → `renderSaf`; else `renderLocal`.
- `renderLocal(finalFile, target)`: the body from `stopRecording` lines 1913-1941 (semaphore, `DeliveryRenderer.render` to a sibling `_delivery.wav`, write `_delivery.json`, `_lastResult` update, `onDeliveryResult(src, dst, result)` instead of the inline `rebindDeliveryResult`).
- `renderSaf(finalFile, target)`: the body of `renderDeliverySaf` (uses `app.contentResolver`, `DocumentFile.fromTreeUri(app, saveDirectoryUri())`, `SafAudioBridge`, then `onDeliveryResult(finalFile.path, deliveryDoc.uri.toString(), result)`).
- `saveAsDefault`: `scope.launch { ... settings.setDefaultLoudnessTarget/...Lufs/...TpCeiling }`.

### ViewModel changes

- Construct `loudness` **before** the delegating vals (declared near the other collaborators, e.g. by `noiseProcessor`), so init order is correct:
  ```kotlin
  private val loudness = LoudnessManager(
      app = app,
      settings = settings,
      scope = viewModelScope,
      saveDirectoryUri = { _saveDirectoryUri.value },
      onDeliveryResult = ::rebindDeliveryResult,
  )
  ```
  The constructor lambda and method reference defer-resolve `_saveDirectoryUri` and `rebindDeliveryResult` (declared later in the file) — legal because their bodies execute only when `renderFor` is later called.
- Replace the four state declarations (525-536) with delegations:
  ```kotlin
  val loudnessTarget: StateFlow<LoudnessTarget> = loudness.target
  val isRenderingDelivery: StateFlow<Boolean> = loudness.isRendering
  val lastDeliveryResult: StateFlow<DeliveryResult?> = loudness.lastResult
  ```
- Replace `setSessionLoudnessTarget` / `saveAsDefaultLoudnessTarget` bodies with `loudness.setSessionTarget(t)` / `loudness.saveAsDefault(t)`.
- Replace the `stopRecording` auto-render block (1908-1942) with `loudness.renderFor(finalFile)`.
- Replace the loudness lines of `applySnapshot` (2795-2799) with `loudness.applySnapshot(s.defaultLoudnessTarget, s.customLoudnessLufs, s.customLoudnessTpCeiling)`.
- Delete the now-moved `renderDeliverySaf` private function.
- Keep `rebindDeliveryResult`.

### Net effect

~110 lines leave the ViewModel; `LoudnessManager` is a ~150-line single-responsibility unit. The ViewModel's public surface is unchanged, so no UI or test file changes.

## Testing

This is a behavior-preserving move, not new logic.
- `LoudnessTarget.encode/decode` persistence logic is already covered by `RecorderViewModelLoudnessTest` (model-level) and stays green unchanged.
- `renderFor` / `renderLocal` / `renderSaf` are Android- and file-coupled (Context, DocumentFile, ContentResolver) and are not JVM-unit-testable in this project (no Robolectric). No artificial tests will be added for the move.
- Safety net: identical public API + full existing unit suite (87 tests) staying green + clean compile.

### Device smoke (acceptance)

1. Set a loudness target, record locally → `<name>_delivery.wav` and `<name>_delivery.json` appear; the delivery result shows in the UI.
2. Set a target, record into a SAF folder → `_delivery.wav` + `_delivery.json` appear in the folder; result shows.
3. Set target to Off → no delivery files produced.
4. "Save as default" target → persists across an app restart (target restored on launch).

## Out of scope

- Extraction of the other seven clusters — future steps of #13.
- Any behavior change to loudness/delivery — this is a pure move.
- Adding Robolectric or instrumented tests for the render paths.
