# MonitorManager Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the Phase-7 live monitoring cluster out of `RecorderViewModel` into a `MonitorManager` collaborator, with the ViewModel delegating so its public API stays identical. Collapse the three duplicated teardown blocks into one idempotent `stop()`.

**Architecture:** Task 1 creates `MonitorManager` (the monitoring logic moved verbatim, cross-links as injected callbacks); it compiles standalone, unused. Task 2 rewires the ViewModel: add the `monitorManager` field, delegate `monitorEnabled`/`monitorLevel` + `toggleMonitor`, replace the 3 teardown sites + `onCleared` with `monitorManager.stop()`/`release()`, delete the moved code. Behavior-preserving; regression guard is clean compile + the full existing unit suite staying green. No new pure logic (the level math in `model/MonitorLevel.kt` is already unit-tested and moves unchanged).

**Tech Stack:** Kotlin, Android, kotlinx.coroutines.

**Spec:** `docs/superpowers/specs/2026-06-15-monitormanager-extraction-design.md`

---

## File Structure

- **Create** `app/src/main/java/com/example/recorderproject/audio/MonitorManager.kt` — live monitoring (engine instance, `enabled`/`level` StateFlows, clip/decay state, `onPcm`, `toggle`, idempotent `stop`, `release`), with cross-links via injected callbacks.
- **Modify** `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` — add `monitorManager` field + seams; delegate `monitorEnabled`/`monitorLevel`/`toggleMonitor`; replace the 3 teardown blocks (`toggleMonitor`-off already moves, plus `startRecordingInternal` + `resetFactory`) with `monitorManager.stop()`; `onCleared` → `monitorManager.release()`; delete moved code (`audioMonitor`, `_monitorEnabled`, `_monitorLevel`, `monitorClipUntilMs`, `monitorDecayJob`, `onMonitorPcm`).

---

## Task 1: MonitorManager collaborator (not yet wired)

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/MonitorManager.kt`

- [ ] **Step 1: Move the monitoring logic verbatim into the collaborator.**
  - Constructor: `(app: Context, scope: CoroutineScope, currentChain: () -> EQChain, onMessage: (String) -> Unit)`.
  - Own `private val audioMonitor = AudioMonitor()`, `_enabled`/`enabled`, `_level`/`level`, `clipUntilMs`, `decayJob`.
  - `private fun onPcm(rmsDb, peakDb)` — copy of `onMonitorPcm` (uses `applyAudioSample`, `System.currentTimeMillis()`).
  - `fun toggle()` — copy of `toggleMonitor` body: external-output safeguard (uses `app` AudioManager; on fail → `onMessage(...)` + return), `audioMonitor.setChain(currentChain())`, `setLevelListener(::onPcm)`, `start()`, decay coroutine on `scope` (uses `applyDecayTick`), BT SCO start, `onMessage("Monitor on")`; off-branch calls `stop()` then `onMessage("Monitor off")`; finally `_enabled.value = on`. (Off-branch teardown is `stop()`, not inline.)
  - `fun stop(): Boolean` — capture `wasRunning = _enabled.value`; `audioMonitor.stop()`; `decayJob?.cancel(); decayJob = null`; `audioMonitor.setLevelListener(null)`; `clipUntilMs = 0L`; `_level.value = MonitorLevel.Silent`; stop BT SCO (try/catch); `_enabled.value = false`; return `wasRunning`. No `onMessage`.
  - `fun release()` — `audioMonitor.stop()` (mirror current `onCleared`).
  - Keep `@Suppress("DEPRECATION")` on the BT SCO calls and `@SuppressLint`/permission notes as in the VM.

- [ ] **Step 2: Confirm it compiles standalone** (unused). `./gradlew compileDebugKotlin` (or full `assembleDebug`).

## Task 2: Delegate the ViewModel to MonitorManager

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

- [ ] **Step 1: Add the field + delegating StateFlows.**
  - After `eqEditor`: `private val monitorManager = MonitorManager(app, viewModelScope, { eqEditor.currentEQChain.value }, { msg -> Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() })`.
  - Replace `_monitorEnabled`/`monitorEnabled` and `_monitorLevel`/`monitorLevel` declarations with `val monitorEnabled = monitorManager.enabled` / `val monitorLevel = monitorManager.level`.

- [ ] **Step 2: Delegate `toggleMonitor`.** `fun toggleMonitor() = monitorManager.toggle()`. Delete `onMonitorPcm`, `monitorClipUntilMs`, `monitorDecayJob`, `audioMonitor`.

- [ ] **Step 3: Replace the record-time + reset teardown sites.**
  - `startRecordingInternal`: replace the inline teardown with `if (monitorManager.stop()) Toast.makeText(app, "Monitor stopped to prevent echo while recording", Toast.LENGTH_SHORT).show()`.
  - `resetFactory`: replace the inline teardown with `monitorManager.stop()` (no toast, matching today).

- [ ] **Step 4: `onCleared`.** Replace `audioMonitor.stop()` with `monitorManager.release()`.

- [ ] **Step 5: Compile + full unit suite.** `./gradlew assembleDebug testDebugUnitTest`. Expect green (no behavior change; suite count unchanged).

---

## Verification

- Clean compile (`assembleDebug`).
- Full existing unit suite green (`testDebugUnitTest`).
- Device smoke: toggle monitor with no headphones (refusal toast), with headphones (meter moves), start recording while monitor on (auto-stops + echo toast), factory reset while monitor on (stops).

## Commits (match prior steps' rhythm)

1. `docs: design spec for MonitorManager extraction (#13 step 5)`
2. `docs: implementation plan for MonitorManager extraction (#13 step 5)`
3. `feat: add MonitorManager collaborator (#13 step 5, not yet wired)`
4. `refactor: delegate live monitoring to MonitorManager (#13 step 5)`
