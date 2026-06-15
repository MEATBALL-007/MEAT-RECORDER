# MonitorManager extraction (RecorderViewModel decomposition, step 5)

**Date:** 2026-06-15
**Issue:** #13 — `RecorderViewModel` god-object decomposition. Steps 1–4 (`LoudnessManager`, `RecordingScanner`/`RecordingNaming`, `PlaybackManager`, `EqEditor`/`EqHistory`) done; VM is at ~2,402 lines.
**Status:** Proposed design, pending approval + implementation plan.

## Problem

`RecorderViewModel` owns the entire Phase-7 live monitoring feature: the `AudioMonitor` engine instance, two StateFlows (`monitorEnabled`, `monitorLevel`), the clip-hold + peak-decay bookkeeping (`monitorClipUntilMs`, `monitorDecayJob`), the per-buffer callback `onMonitorPcm`, and the ~80-line `toggleMonitor` (external-output safeguard → chain wiring → level listener → decay coroutine → Bluetooth SCO start/stop → toasts).

The monitor **teardown** sequence is duplicated **three times** verbatim — `toggleMonitor` off-branch, the `startRecordingInternal` echo safeguard, and `resetFactory` — plus a partial fourth in `onCleared`. Each copy stops the engine, cancels the decay job, clears the listener, resets clip state + `_monitorLevel`, and stops BT SCO. This duplication is the strongest signal the cluster wants to be one object with one idempotent `stop()`.

## Scope decision

Extract **everything that drives or reflects live pre-record monitoring** into a `MonitorManager` Android collaborator under `audio/`, matching the prior steps' template (VM instantiates it and re-exposes its StateFlows/functions under the SAME public names so UI + tests need no changes).

**Stays in the ViewModel:**
- `_liveEqEnabled` / `toggleLiveEq` / `_liveEqBandGains` / `setLiveEqBand` — these are the *recording-path* live EQ, not monitoring; they belong to the future `RecordingController` step. (The monitor only *reads* the current EQ chain when it starts.)
- Audio-focus / becoming-noisy handling (`requestAudioFocus`, `abandonAudioFocus`, `focusListener`, `becomingNoisyReceiver`) — these are recording-lifecycle, not monitoring. Left for `RecordingController`.
- The decision *to* tear down the monitor before recording / on reset stays at the call site; only the teardown mechanics move (call becomes `monitor.stop()`).

## Coupling findings

- `toggleMonitor` reads `eqEditor.currentEQChain.value` to seed `audioMonitor.setChain(...)` on start → inject as `currentChain: () -> EQChain`.
- The decay coroutine runs on `viewModelScope` → inject `scope: CoroutineScope`.
- External-output detection + Bluetooth SCO start/stop use the application `AudioManager` → inject `app: Context`.
- All user feedback is via `Toast` (`"Monitor on"`, `"Monitor off"`, the headphones-required warning, and the recording echo-safeguard message). Per the established pattern (`PlaybackManager.onError`), route these through an injected `onMessage: (String) -> Unit` rather than toasting from a non-UI class.
- `onMonitorPcm` + the decay tick use the already-pure `applyAudioSample` / `applyDecayTick` (`model/MonitorLevel.kt`) — these move with the manager unchanged; **no new pure logic to TDD** (that layer was extracted earlier and has unit coverage).
- `_monitorEnabled.value` is read as a guard in `startRecordingInternal` and `resetFactory`. After extraction, `stop()` is made idempotent and returns `Boolean` (was-running) so the echo-safeguard can conditionally show its toast; reset just calls `stop()`.
- `System.currentTimeMillis()` stays inside the manager (same as it is in the VM today) — the *pure* helpers already take `nowMs` as a parameter, so they remain testable.

## Design

### `MonitorManager` — live monitoring (Android collaborator)

New file `app/src/main/java/com/example/recorderproject/audio/MonitorManager.kt`.

```kotlin
class MonitorManager(
    private val app: Context,
    private val scope: CoroutineScope,
    private val currentChain: () -> EQChain,   // reads eqEditor.currentEQChain.value
    private val onMessage: (String) -> Unit,   // routes user-facing toasts to the VM
) {
    private val audioMonitor = AudioMonitor()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    private val _level = MutableStateFlow(MonitorLevel.Silent)
    val level: StateFlow<MonitorLevel> = _level

    private var clipUntilMs: Long = 0L
    private var decayJob: Job? = null

    /** Toggle monitoring on/off. On-start performs the external-output safeguard
     *  (refuses + messages if no headphones/BT/USB out) and Bluetooth-SCO setup. */
    fun toggle()

    /** Idempotent teardown: stop engine + decay job, clear listener, reset clip/level,
     *  stop BT SCO, set enabled=false. Returns true if monitoring was running.
     *  Replaces the three duplicated teardown blocks. Does NOT emit a message —
     *  callers decide whether/what to toast. */
    fun stop(): Boolean

    /** Release the engine (call from ViewModel.onCleared). */
    fun release()

    private fun onPcm(rmsDb: Float, peakDb: Float)  // applyAudioSample
}
```

### ViewModel after extraction

- Field: `private val monitorManager = MonitorManager(app, viewModelScope, { eqEditor.currentEQChain.value }, { msg -> Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() })`. Declared **after** `eqEditor` (its `currentChain` lambda defer-resolves `eqEditor`, but declaring after keeps init order obvious).
- Re-expose under identical names: `val monitorEnabled: StateFlow<Boolean> = monitorManager.enabled`, `val monitorLevel: StateFlow<MonitorLevel> = monitorManager.level`.
- `fun toggleMonitor() = monitorManager.toggle()`.
- `startRecordingInternal` echo safeguard → `if (monitorManager.stop()) onMessage-equivalent Toast "Monitor stopped to prevent echo while recording"`.
- `resetFactory` → `monitorManager.stop()` (no toast, as today).
- `onCleared` → `monitorManager.release()`.
- Delete: `audioMonitor` field, `_monitorEnabled`/`_monitorLevel`, `monitorClipUntilMs`, `monitorDecayJob`, `onMonitorPcm`, the `toggleMonitor` body, and the three duplicated teardown blocks.

## Behavior preservation

- The headphones-required safeguard, the "Monitor on/off" toasts, the echo-safeguard toast, BT SCO start/stop, clip-hold and peak-decay timing, and the auto-stop-before-record / stop-on-reset behaviors are all preserved exactly. The only intentional change is **de-duplication** of the teardown into one method (same observable effect).

## Verification

Per the decomposition template: clean compile + the full existing unit suite green + device smoke (toggle monitor with/without headphones, confirm meter moves and stops on record). No new unit tests required — the pure level math already has coverage; the manager is Android-coupled (AudioRecord/AudioTrack/AudioManager), no Robolectric in this project.
