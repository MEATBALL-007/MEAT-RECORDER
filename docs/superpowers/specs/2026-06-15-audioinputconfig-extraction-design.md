# AudioInputConfig extraction (RecorderViewModel decomposition, step 6)

**Date:** 2026-06-15
**Issue:** #13 — `RecorderViewModel` god-object decomposition. Steps 1–5 (`LoudnessManager`, `RecordingScanner`/`RecordingNaming`, `PlaybackManager`, `EqEditor`/`EqHistory`, `MonitorManager`) done; VM is at ~2,279 lines.
**Status:** Proposed design, pending approval + implementation plan.

## Problem

The audio-input configuration is spread across the VM: sample rate, bit depth, channel count, input gain, audio source (MIC/CAMCORDER/etc.), mic-source label, the quality preset, and external-device (USB/BT/wired) routing via `UsbAudioDetector`. It is ~8 StateFlows + 9 functions + the `gainDbPersist` debounce flow + the detector lifecycle.

## Scope decision

Extract into an `AudioInputConfig` Android collaborator under `audio/`, matching the prior steps' template (VM instantiates it, re-exposes its StateFlows/functions under the SAME public names).

**Moves into `AudioInputConfig`:**
- State: `sampleRate`, `bitDepth`, `channelCount`, `inputGainDb`, `audioSource`, `audioSourceName`, `micSourceLabel`, `quality`, plus `externalInputDevices` (from the detector).
- The `UsbAudioDetector` instance (`inputDeviceDetector`) + its `start()`/`stop()` lifecycle.
- The `gainDbPersist` MutableSharedFlow + its debounced persistence collector.
- Functions: `updateSampleRate`, `updateBitDepth`, `updateChannelCount`, `updateInputGainDb`, `setQuality`, `setMicSource`, `updateAudioSource`, `selectInputDevice`, `clearInputDevice`.
- Pro gating that lives inside these (`HIGH_RES_AUDIO` for >48 kHz / >16-bit; `EXTERNAL_MIC` for device selection).

**Stays in the VM (other clusters):**
- `selectRecorderMode` stays (it's recorder-mode + noise-reduction logic) but delegates its sample/bit/channel slice to `audioConfig.applyQualityValues(...)`.
- `applySnapshot` / `applyDefaults` / `rewireRecorderFromState` stay (they hydrate/rewire *all* clusters) but delegate their audio-input slice to `audioConfig.applySnapshot(s)` / `audioConfig.rewireRecorder()`.
- `recorder.setAudioSource(...)` at record start stays in the recording flow (reads `audioConfig.audioSource.value`).

## Coupling findings

- **`sampleRate` is read in ~9 places** beyond its own setter: the `EqEditor` ctor lambda (`sampleRate = { _sampleRate.value }`), `liveChainSink`, `toggleLiveEq`, the recording start + validation + byte-rate calc, file metadata capture, and `selectRecorderMode`. All become `audioConfig.sampleRate.value`. The `EqEditor` ctor lambda defers resolution, so declaration order is legal, but `audioConfig` will be declared **before** `eqEditor` for clarity (it already sits near the current `inputDeviceDetector` at the top).
- **Recorder push surface (5 setters):** `setBitDepth`, `setChannelCount`, `setInputGain`, `setAudioSource`, `setPreferredDevice`. `sampleRate` is NOT a setter — it's a `recorder.start(...)` parameter, so the recording flow keeps reading `audioConfig.sampleRate.value` at start time. **Design choice:** inject the `recorder` (`AudioRecorderManager`) reference into `AudioInputConfig` (it is a peer collaborator; injecting it is cleaner than threading 5 sink lambdas). This is a deliberate, small deviation from the "VM wraps recorder in lambdas" pattern used by `EqEditor`, justified by the setter count.
- **`gainDbPersist`** is a debounced hot-path persistence flow (slider writes coalesced to ≤1/150 ms). It and its collector move into `AudioInputConfig` (collector launched in `start()` on the injected `scope`). `eqBandGainsPersist` stays in the VM (live-EQ, recording-path).
- **`setQuality`** sets `_quality` + sample/bit/channel + persists all four → moves wholesale.
- **`selectRecorderMode`** (stays in VM) sets sample/bit/channel/noiseReduction from `mode.*`. Its sample/bit/channel slice delegates to a new `audioConfig.applyQualityValues(sampleRate, bitDepth, channelCount)` (sets the three flows + persists when hydrated). NoiseReduction + recorderMode stay in the VM. (Persistence moves from one combined coroutine to the collaborator's own launch — same DataStore writes, observably identical.)
- **`applySnapshot`** sets the 8 config fields interleaved with non-config ones (recorderMode, sceneName, noiseReduction, gate/agc/hipass/anticlip, countdown, durations). The audio-input slice moves to `audioConfig.applySnapshot(s)`; the VM keeps the rest. `audioConfig.applySnapshot` does the `AUDIO_SOURCES` name→id lookup it already does today.
- **`rewireRecorderFromState`** pushes inputGain + bitDepth + channelCount (audio-input slice) plus gate/agc/hipass/anticlip (other). The audio-input slice moves to `audioConfig.rewireRecorder()`.
- No `_errorMessage` use in the cluster; Pro gating returns silently via `requirePro`.

## Design

### `AudioInputConfig` — input configuration (Android collaborator)

New file `app/src/main/java/com/example/recorderproject/audio/AudioInputConfig.kt`.

```kotlin
class AudioInputConfig(
    app: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsDataStore,
    private val isHydrated: () -> Boolean,
    private val requirePro: (ProFeature) -> Boolean,
    private val recorder: AudioRecorderManager,
) {
    private val detector = UsbAudioDetector(app)
    val externalInputDevices: StateFlow<List<UsbAudioDetector.UsbDevice>> = detector.devices

    // 8 config StateFlows (same defaults/public names as the VM today)
    val sampleRate: StateFlow<Int>          // default 48000
    val bitDepth: StateFlow<Int>            // default 16
    val channelCount: StateFlow<Int>        // default 1
    val inputGainDb: StateFlow<Float>       // default 0f
    val audioSource: StateFlow<Int>         // default 1 (MIC)
    val audioSourceName: StateFlow<String>  // default "Microphone"
    val micSourceLabel: StateFlow<String>   // default "Microphone"
    val quality: StateFlow<RecordingQuality>

    // setters (moved verbatim, incl. Pro gates + persistence + recorder pushes)
    fun updateSampleRate(value: Int)
    fun updateBitDepth(v: Int)
    fun updateChannelCount(v: Int)
    fun updateInputGainDb(db: Float)
    fun setQuality(q: RecordingQuality)
    fun setMicSource(label: String)
    fun updateAudioSource(name: String)
    fun selectInputDevice(device: UsbAudioDetector.UsbDevice)
    fun clearInputDevice()

    // shared-method seams
    fun applyQualityValues(sampleRate: Int, bitDepth: Int, channelCount: Int) // for selectRecorderMode
    fun applySnapshot(s: SettingsSnapshot)   // hydrate the input-config slice
    fun rewireRecorder()                     // push inputGain + bitDepth + channelCount

    fun start()  // detector.start() + launch gainDbPersist debounce collector
    fun stop()   // detector.stop()  (from onCleared)
}
```

### ViewModel after extraction

- Field (replaces `inputDeviceDetector`, near the top so it precedes `eqEditor`): `private val audioConfig = AudioInputConfig(app, viewModelScope, settings, { hydrated.value }, ::requirePro, recorder)`.
- Re-expose under identical names: `externalInputDevices`, `sampleRate`, `bitDepth`, `channelCount`, `inputGainDb`, `audioSource`, `audioSourceName`, `micSourceLabel`, `quality`.
- Delegate the 9 functions: `fun updateSampleRate(v) = audioConfig.updateSampleRate(v)`, etc.
- `EqEditor` ctor lambda → `sampleRate = { audioConfig.sampleRate.value }`; update the other `_sampleRate`/`_bitDepth`/`_channelCount`/`_audioSource` reads to `audioConfig.*.value`.
- `selectRecorderMode` → `audioConfig.applyQualityValues(mode.sampleRate, mode.bitDepth, mode.channelCount)` for its quality slice.
- `applySnapshot` → `audioConfig.applySnapshot(s)` for its slice; `rewireRecorderFromState` → `audioConfig.rewireRecorder()`.
- `init` → `audioConfig.start()` (replaces `inputDeviceDetector.start()` + the `gainDbPersist` collector launch).
- `onCleared` → `audioConfig.stop()` (replaces `inputDeviceDetector.stop()`).
- Delete the moved state, functions, and the `gainDbPersist` field + collector.

## Behavior preservation

All Pro gates, clamps (`bitDepth` free cap, `channelCount` 1..2, `inputGainDb` −12..24, the dB→linear conversion), persistence keys, the BT-mic VOICE_COMMUNICATION switch, and the detector lifecycle are preserved exactly. Persistence coroutine grouping changes in `selectRecorderMode`/`setQuality` (collaborator launches its own) — same DataStore writes, observably identical.

## Verification

Per the decomposition template: clean compile + full existing unit suite (120) green + device smoke (change sample rate/bit depth, toggle quality preset, plug a USB/BT mic and select it, confirm recording uses the chosen config). No new unit tests (Android-coupled; no Robolectric).
