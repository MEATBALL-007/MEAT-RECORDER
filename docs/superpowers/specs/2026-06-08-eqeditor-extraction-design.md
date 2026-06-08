# EqEditor extraction (RecorderViewModel decomposition, step 4)

**Date:** 2026-06-08
**Issue:** #13 — `RecorderViewModel` god-object decomposition. Steps 1–3 (`LoudnessManager`, `RecordingScanner`, `PlaybackManager`) done; VM is at ~2,754 lines.
**Status:** Approved design, pending implementation plan.

## Problem

`RecorderViewModel` holds the entire EQ feature: ~13 StateFlows, ~25 `onEQ*` functions, two `ArrayDeque` undo/redo stacks, the apply/render pipeline (local + SAF), source-spectrum analysis, and persistence. It is the largest and most coupled remaining cluster: the EQ chain is also pushed to the recorder in real time ("live EQ") and to the monitor, and is written as a sidecar on recording stop.

## Scope decision

Extract the **offline EQ editor** (everything driven by the EQ screen: chain editing, undo/redo, presets, hum/auto/curve/tap-notch, open/close + spectrum, apply/render, save-mode, persistence). The **live-EQ-during-recording** pieces stay in the ViewModel — they belong to the recording pipeline (push to `recorder`/`audioMonitor`, read `_sampleRate`/`_isRecording`) and will be handled when `RecordingController` is extracted. The editor exposes `currentEQChain` so the VM's live-EQ/recording/monitor code can read it.

## Coupling findings

- `onEQBandChanged` and `onEQPresetSelected` push the chain to the recorder when live-EQ is on (`if (_liveEqEnabled.value) recorder.setLiveEqChain(chain, _sampleRate.value.toFloat())`).
- `onEQApply`/`onEQApplySaf` update `_recordFiles` (set `hasEQ = true`) and `onEQApplySaf` reads `_saveDirectoryUri`.
- `onEQOpen` gates on `requirePro(ProFeature.FULL_EQ)` (the only Pro gate in the cluster), reads/writes the `_eq.json` sidecar, and computes a source spectrum via `SpectrumAnalyzer`.
- `onEQExportCurvePng` reads `_sampleRate` (for `CurveBitmapExport.exportToGallery`).
- No `_errorMessage` use in the cluster (failures surface via Toast).
- `_eqBypassed` (StateFlow) appears vestigial — never written after init and not read in `ui/`; moved verbatim, not removed, to preserve behavior.

## Design

### `EqHistory` — pure undo/redo (unit-tested)

New file `app/src/main/java/com/example/recorderproject/audio/EqHistory.kt`. No Android types.

```kotlin
class EqHistory(private val cap: Int = 10) {
    /** Record the pre-change state; clears the redo stack; evicts oldest past [cap]. */
    fun push(current: EQChain)
    /** Return the state to restore (or null if none); pushes [current] onto the redo stack. */
    fun undo(current: EQChain): EQChain?
    /** Return the state to restore (or null if none); pushes [current] onto the undo stack. */
    fun redo(current: EQChain): EQChain?
    fun clear()
}
```

Reproduces the current mechanics exactly: `push` = `addLast` + cap-evict `removeFirst` + `redo.clear()`; `undo` = `history.removeLastOrNull()` then `redo.addLast(current)`; `redo` = `redo.removeLastOrNull()` then `history.addLast(current)`.

### `EqEditor` — offline editor (Android collaborator)

New file `app/src/main/java/com/example/recorderproject/audio/EqEditor.kt`.

```kotlin
class EqEditor(
    private val app: Context,
    private val settings: SettingsDataStore,
    private val scope: CoroutineScope,
    private val isHydrated: () -> Boolean,
    private val requirePro: (ProFeature) -> Boolean,
    private val sampleRate: () -> Int,
    private val liveChainSink: (EQChain) -> Unit,
    private val markFileHasEq: (id: String) -> Unit,
    private val saveDirectoryUri: () -> Uri?,
) {
    // State (moved from the VM)
    val currentEQChain: StateFlow<EQChain>
    val eqMode: StateFlow<EQEditMode>
    val eqViewMode: StateFlow<EQViewMode>
    val eqSelectedBandId: StateFlow<Int?>
    val eqSnapshot: StateFlow<EQChain?>
    val eqApplySaveMode: StateFlow<ApplySaveMode>
    val eqRenderProgress: StateFlow<Float>
    val eqSourceFile: StateFlow<RecordFile?>
    val eqSourceSpectrum: StateFlow<StaticSpectrum?>
    val eqBypassed: StateFlow<Boolean>
    val eqOpen: StateFlow<Boolean>

    // All onEQ* functions move here verbatim, with these substitutions:
    //  - inline `if (_liveEqEnabled.value) recorder.setLiveEqChain(...)`  → liveChainSink(currentChain)
    //  - inline `_recordFiles.value = ...copy(hasEQ=true)`                → markFileHasEq(src.id)
    //  - `_saveDirectoryUri.value`                                        → saveDirectoryUri()
    //  - `requirePro(...)` / `_sampleRate.value`                          → injected lambdas
    //  - `hydrated.value` / `viewModelScope`                              → isHydrated() / scope
    //  - undo/redo stacks                                                 → EqHistory
    fun onEQOpen(file); onEQClose(); onEQBandChanged(band); onEQModeToggle(mode)
    fun onEQViewModeToggle(mode); onEQSelectBand(id?); onEQUndo(); onEQRedo(); onEQABToggle()
    fun onEQResetAll(); onEQPresetSelected(preset); onEQToggleBypass(); onEQToggleGainCompensation()
    fun onEQHumDetect(mainsHz); onEQRandomPreset(); onEQSaveAsCustomPreset(name); onEQExportCurvePng()
    fun onEQNoiseAutoDetect(); onEQAcceptSuggestion(band); onEQRejectSuggestion(band)
    fun onEQDrawCurve(curve); onEQTapNotch(hz); onEQSaveModeChange(mode); onEQApply()
    fun applySnapshot(chainJson: String, eqMode: String, eqViewMode: String, eqApplySaveMode: String, bypassed: Boolean)
    fun clearHistory()
    // private: onEQApplySaf, persistCurrentEqChain, pushEqHistory (via EqHistory)
}
```

### ViewModel changes (public surface unchanged)

- Declare `private val eqEditor = EqEditor(...)` after `settings`/`hydrated` (and after `recorder`/`audioMonitor` exist, since the `liveChainSink` lambda references `recorder`; lambdas defer-resolve so textual order only needs the field initialized before first use — placed before the `init` block because hydration calls `eqEditor.applySnapshot`).
- Replace the 11 EQ StateFlow declarations with delegating vals (same names).
- Replace the ~25 `onEQ*` functions with one-line delegations.
- Implement the seams:
  - `liveChainSink = { if (_liveEqEnabled.value) recorder.setLiveEqChain(it, _sampleRate.value.toFloat()) }`
  - `markFileHasEq = { id -> _recordFiles.value = _recordFiles.value.map { if (it.id == id) it.copy(hasEQ = true) else it } }`
  - `saveDirectoryUri = { _saveDirectoryUri.value }`, `requirePro = ::requirePro`, `sampleRate = { _sampleRate.value }`, `isHydrated = { hydrated.value }`.
- Live-EQ stays: `_liveEqEnabled`, `_liveEqBandGains`, `toggleLiveEq`, `setLiveEqBand` keep reading/writing their own state; where they previously read `_currentEQChain.value` they now read `eqEditor.currentEQChain.value`.
- Recording start's `recorder.setLiveEqChain(_currentEQChain.value, ...)`, the stop sidecar write, and `audioMonitor.setChain(_currentEQChain.value)` now read `eqEditor.currentEQChain.value`.
- `applySnapshot`: keep the two live-EQ lines (`_liveEqEnabled`, `_liveEqBandGains`); replace the EQ-chain/mode lines with `eqEditor.applySnapshot(s.currentEqChainJson, s.eqMode, s.eqViewMode, s.eqApplySaveMode, s.eqBypassed)`.
- `resetFactory`'s `eqHistory.clear(); eqRedo.clear()` → `eqEditor.clearHistory()`.
- Remove the moved EQ state, functions, and the `eqHistory`/`eqRedo` fields from the VM.

### Net effect

~250 lines move out; `EqEditor` ~230 lines, `EqHistory` ~40 lines (tested). Public surface unchanged → no UI/test file changes.

## Testing

- **`EqHistoryTest` (JVM, TDD):** push then undo restores the pushed state; redo after undo restores; a new push clears redo; cap eviction keeps only the last `cap` states; undo/redo on empty stacks return null.
- **`EqEditor`:** Android/I/O-coupled (Context, file I/O, `EQProcessor`, `SafAudioBridge`, `SpectrumAnalyzer`, `CurveBitmapExport`, settings) — not JVM-testable here. Verified by clean compile + the full existing suite (98 tests) staying green.

### Device smoke (acceptance)

1. Open EQ on a local recording → existing `_eq.json` loads; source spectrum renders.
2. Edit a band, undo, redo; A/B snapshot toggle; reset-all; pick a preset; hum-detect; noise auto-detect accept/reject; draw curve; tap-notch — all behave as before.
3. Apply (BOTH) → `_eq.wav` + `_eq.json` written, EQ badge appears; Apply (EQ_ONLY) replaces in place; both work for a SAF-folder recording.
4. With live-EQ enabled during recording, editing a band still applies in real time, and the take gets its EQ sidecar.
5. Export curve PNG works.
6. EQ chain, mode, view mode, save mode, and bypass are restored after an app restart.

## Out of scope

- Live-EQ-during-recording (`toggleLiveEq`, `setLiveEqBand`, recording-start push, stop sidecar) — deferred to the RecordingController step.
- Removing the vestigial `_eqBypassed` flow — preserved as-is.
- Any behavior change to EQ — pure move.
- Robolectric/instrumented tests for the editor.
