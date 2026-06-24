# RecordingController extraction (RecorderViewModel decomposition, step 7)

**Date:** 2026-06-16
**Issue:** #13 — `RecorderViewModel` god-object decomposition. Steps 1–6 done (`LoudnessManager`, `RecordingScanner`/`RecordingNaming`, `PlaybackManager`, `EqEditor`/`EqHistory`, `MonitorManager`, `AudioInputConfig`); VM is at ~2,196 lines.
**Status:** Proposed design, pending approval.

## Problem

The recording lifecycle is the last and largest cluster — "the one that touches everything." Inventory of the recording surface in the VM today:

- **~18 state fields:** `_isRecording`, `_isPaused`; live meters `_currentWaveform`/`_liveSpectrum`/`_livePitchHz`/`_liveLufs`/`_liveTpDbTp`/`_liveRawPeakDbfs`/`_phaseCorrelation`/`_spectrumHistory`; DSP toggles `_liveNoiseGateOn`/`_agcOn`/`_hiPassOn`/`_antiClipOn`/`_compressorOn`/`_stereoWidenerOn`; `_liveEqEnabled`/`_liveEqBandGains`; `_vadOn`; `_preRollEnabled`; naming `_fileName`/`_sceneName`/`_notes`; timers `scheduleJob`/`autoStopJob`/`recordingLimitJob` + `_scheduledStartMs`/`_maxDurationMinutes`/`_autoStopMinutes`/`_countdownSeconds`; audio focus `audioFocusRequest`/`focusListener`/`becomingNoisyReceiver`/`becomingNoisyRegistered`; cue `_liveCueCount`/`pendingCues`/`recordingStartMs`; `pendingLocationTag`.
- **20+ functions:** `startRecording`/`startRecordingInternal`/`startRecordingNow`/`stopRecording`/`togglePause`; the six DSP toggles; `toggleLiveEq`/`setLiveEqBand`; `toggleVad`; `togglePreRoll`; the timers (`setScheduledStart`/`cancelSchedule`/`armAutoStop`/`cancelAutoStop`/`setAutoStopMinutes`/`startFreeTierLimitTimer`/`cancelFreeTierLimitTimer`); naming (`computeNextTakeNumber`/`refreshAutoFileName`/`bumpTake`/`bumpScene`/`bumpSubscene`/`autoNameForNextTake`/`updateFileName`/`updateSceneName`/`updateNotes`); `validateRecordingData`/`checkDiskSpaceOrError`; `requestAudioFocus`/`abandonAudioFocus`; `dropCueMarker`; `fireSlateTone`.
- **27 distinct `recorder.*` calls**, and reads/writes across **8 collaborators** (eqEditor, audioConfig, loudness, monitorManager, recorder, settings, voiceActivityDetector, preRollCapture) + scanner, billing/`requirePro`, `_recordFiles`, `_saveDirectoryUri`.

A single behavior-preserving move would be a ~700+ line diff with delicate listener-wiring and init-order hazards. **Too risky in one shot.**

## Scope decision: decompose RecordingController into sub-steps

Peel the **self-contained, low-coupling** pieces first, shrinking the core before extracting it. Each sub-step is its own spec→(plan)→collaborator→delegate→verify cycle with the usual guard (compile + 120 unit tests green). Ordered lowest-coupling first:

### Step 7a — `AudioFocusController` (smallest, cleanest; explicitly left behind by step 5)
Owns: `audioFocusRequest`, `requestAudioFocus()`, `abandonAudioFocus()`, the `focusListener` (force-stops recording on `AUDIOFOCUS_LOSS`/`_TRANSIENT`), and the `becomingNoisyReceiver` (+ `becomingNoisyRegistered`) that warns on headphone unplug mid-record.
Seams: `app: Context`, `isRecording: () -> Boolean`, `onFocusLost: () -> Unit` (→ VM `stopRecording`), `onBecomingNoisy: () -> Unit` (→ VM toast). Register/unregister helpers called from `startRecordingNow`/`stopRecording`/`onCleared`.
~70 lines. No coupling to other collaborators. **Recommended first.**

### Step 7b — `RecordingTimers`
Owns: `scheduleJob`/`_scheduledStartMs` + `setScheduledStart`/`cancelSchedule`; `autoStopJob`/`_autoStopMinutes` + `armAutoStop`/`cancelAutoStop`/`setAutoStopMinutes`; `recordingLimitJob` + `startFreeTierLimitTimer`/`cancelFreeTierLimitTimer`.
Seams: `scope`, `settings`+`isHydrated` (persist auto-stop), `isPro: () -> Boolean` (free-tier timer), `onLimitReached: () -> Unit` (→ stopRecording), `onMessage` (toasts). Note: persisted `_autoStopMinutes`/`_maxDurationMinutes`/`_countdownSeconds` are simple ints — decide per-field whether they ride along or stay as recording-config in 7d.

### Step 7c — `TakeNaming`
Owns: `_fileName`/`_sceneName`/`_notes` + `computeNextTakeNumber`/`refreshAutoFileName`/`bump{Take,Scene,Subscene}`/`autoNameForNextTake`/`updateFileName`/`updateSceneName`/`updateNotes`. Builds on the already-pure `RecordingNaming` (step 2) + `scanner` for take-number scan.
Seams: `scanner`, `settings`+`isHydrated`.

### Step 7d — `RecordingController` (core engine, the remainder)
Owns: `_isRecording`/`_isPaused`, the 8 live-meter flows, the six DSP toggles, `toggleLiveEq`/`setLiveEqBand` + `_liveEqEnabled`/`_liveEqBandGains`, `_vadOn`/`toggleVad`, `_preRollEnabled`/`togglePreRoll`, cue markers, `fireSlateTone`, `validateRecordingData`/`checkDiskSpaceOrError`, and `startRecording`/`startRecordingInternal`/`startRecordingNow`/`stopRecording`/`togglePause` with all listener wiring + foreground service.
Composes 7a/7b/7c + injects eqEditor/audioConfig/loudness/monitorManager/recorder/settings/VAD/preRoll seams. `rewireRecorderFromState`'s DSP slice moves here; `applySnapshot`'s recording slice delegates here.

## Why this order

7a/7b/7c are independently testable and each removes a chunk of the start/stop methods' noise, so by the time 7d runs, `startRecordingNow`/`stopRecording` read as orchestration over named collaborators rather than 200-line procedures. If we stop after any sub-step, the VM is still smaller and fully working.

## This document covers Step 7a in detail

### `AudioFocusController` — audio focus + output-route handling (Android collaborator)

New file `app/src/main/java/com/example/recorderproject/audio/AudioFocusController.kt`.

```kotlin
class AudioFocusController(
    private val app: Context,
    private val isRecording: () -> Boolean,
    private val onFocusLost: () -> Unit,      // → VM stopRecording()
    private val onBecomingNoisy: () -> Unit,  // → VM toast (route changed)
) {
    private var audioFocusRequest: AudioFocusRequest? = null
    private var becomingNoisyRegistered = false
    private val focusListener = OnAudioFocusChangeListener { change -> ... if (isRecording()) onFocusLost() }
    private val becomingNoisyReceiver = object : BroadcastReceiver() { ... if (isRecording()) onBecomingNoisy() }

    fun requestFocus(): Boolean          // moved verbatim (API-O split)
    fun abandonFocus()                   // moved verbatim
    fun registerBecomingNoisy()          // from startRecordingNow()
    fun unregisterBecomingNoisy()        // from stopRecording()/onCleared()
}
```

### ViewModel after 7a
- Field: `private val audioFocus = AudioFocusController(app, { _isRecording.value }, { stopRecording() }, { Toast... "Headphones unplugged…" })`.
- `startRecordingNow` → `audioFocus.requestFocus()` + `audioFocus.registerBecomingNoisy()`.
- `stopRecording` → `audioFocus.abandonFocus()` + `audioFocus.unregisterBecomingNoisy()`.
- `onCleared` → `audioFocus.unregisterBecomingNoisy()`.
- Delete `audioFocusRequest`, `focusListener`, `requestAudioFocus`, `abandonAudioFocus`, `becomingNoisyReceiver`, `becomingNoisyRegistered`, and the inline register/unregister blocks.
- The current `focusListener` Toast ("Recording stopped: another app took audio focus") moves into the controller (it has `app`); the force-stop routes via `onFocusLost`.

## Verification (per sub-step)

Clean compile + full existing unit suite (120) green + device smoke. For 7a: start recording, trigger focus loss (play another app's audio) → recording stops with toast; unplug headphones mid-record → warning toast, recording continues.
