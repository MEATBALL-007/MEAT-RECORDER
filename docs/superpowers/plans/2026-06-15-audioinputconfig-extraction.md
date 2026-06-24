# AudioInputConfig Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the audio-input configuration cluster (sample rate, bit depth, channel count, input gain, audio source, mic-source label, quality preset, USB/BT device routing) out of `RecorderViewModel` into an `AudioInputConfig` collaborator, VM delegating so its public API stays identical.

**Architecture:** Task 1 creates `AudioInputConfig` (logic moved verbatim; recorder injected; `applySnapshot`/`rewireRecorder`/`applyQualityValues` slice-seams added); it compiles standalone, unused. Task 2 rewires the VM: add the field, re-expose 9 flows + delegate 9 functions, repoint the `sampleRate`/`bitDepth`/`channelCount`/`audioSource` read sites, delegate the 3 shared-method slices, move detector + `gainDbPersist` lifecycle into `start()`/`stop()`, delete moved code. Behavior-preserving; guard is clean compile + full existing unit suite (120) green.

**Tech Stack:** Kotlin, Android, kotlinx.coroutines.

**Spec:** `docs/superpowers/specs/2026-06-15-audioinputconfig-extraction-design.md`

---

## File Structure

- **Create** `app/src/main/java/com/example/recorderproject/audio/AudioInputConfig.kt`.
- **Modify** `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`.

---

## Task 1: AudioInputConfig collaborator (not yet wired)

**Files:** Create `app/.../audio/AudioInputConfig.kt`.

- [ ] **Step 1: Constructor + state.** `(app: Context, scope, settings, isHydrated, requirePro, recorder: AudioRecorderManager)`. Own `detector = UsbAudioDetector(app)` + `externalInputDevices = detector.devices`. Declare the 8 `MutableStateFlow`s with the VM's current defaults (sampleRate 48000, bitDepth 16, channelCount 1, inputGainDb 0f, audioSource 1, audioSourceName/micSourceLabel "Microphone", quality `RecordingQuality.Default`) + public read-only aliases. Own `gainDbPersist = MutableSharedFlow<Float>(extraBufferCapacity = 64)`.
- [ ] **Step 2: Setters (verbatim).** Move `updateSampleRate`, `updateBitDepth`, `updateChannelCount`, `updateInputGainDb`, `setQuality`, `setMicSource`, `updateAudioSource`, `selectInputDevice`, `clearInputDevice` — including Pro gates (`HIGH_RES_AUDIO`, `EXTERNAL_MIC`), clamps, the dB→linear conversion, recorder pushes (`recorder.setBitDepth/…`), and persistence. `setMicSource` calls the moved `updateAudioSource`. `selectInputDevice` uses `detector.preferredDeviceById(...)`.
- [ ] **Step 3: Seam methods.**
  - `applyQualityValues(sampleRate, bitDepth, channelCount)` — set the three flows; if `isHydrated()` launch persistence of all three. (Used by `selectRecorderMode`.)
  - `applySnapshot(s)` — set audioSourceName, micSourceLabel, the `AUDIO_SOURCES` name→id lookup into audioSource, inputGainDb, sampleRate, bitDepth, channelCount, quality (`runCatching { RecordingQuality.valueOf(s.qualityPreset) }`). No recorder push.
  - `rewireRecorder()` — push inputGain (dB→linear), bitDepth, channelCount to `recorder`.
- [ ] **Step 4: Lifecycle.** `start()` — `detector.start()` + launch the `gainDbPersist.debounce(150).collect { settings.setInputGainDb(it) }` collector on `scope`. `stop()` — `detector.stop()`.
- [ ] **Step 5: Compile standalone** (unused). `./gradlew compileDebugKotlin`.

## Task 2: Delegate the ViewModel to AudioInputConfig

**Files:** Modify `RecorderViewModel.kt`.

- [ ] **Step 1: Field.** Replace the `inputDeviceDetector` field (top, before `eqEditor`) with `private val audioConfig = AudioInputConfig(app, viewModelScope, settings, { hydrated.value }, ::requirePro, recorder)`. Re-expose `externalInputDevices = audioConfig.externalInputDevices`.
- [ ] **Step 2: Re-expose flows + delegate fns.** Replace the 8 state declarations with `val sampleRate = audioConfig.sampleRate` (and bitDepth/channelCount/inputGainDb/audioSource/audioSourceName/micSourceLabel/quality). Replace the 9 setter bodies with one-line delegations.
- [ ] **Step 3: Repoint reads.** `EqEditor` ctor `sampleRate = { audioConfig.sampleRate.value }`; update `liveChainSink`/`toggleLiveEq`/recording-start/validation/byte-rate/file-metadata/`selectRecorderMode` reads of `_sampleRate`/`_bitDepth`/`_channelCount`/`_audioSource` to `audioConfig.*.value`. (grep to confirm none remain.)
- [ ] **Step 4: Shared-method slices.** `selectRecorderMode` quality slice → `audioConfig.applyQualityValues(mode.sampleRate, mode.bitDepth, mode.channelCount)` (keep recorderMode + noiseReduction in VM). `applySnapshot` input slice → `audioConfig.applySnapshot(s)`. `rewireRecorderFromState` input slice → `audioConfig.rewireRecorder()`.
- [ ] **Step 5: Lifecycle.** `init`: replace `inputDeviceDetector.start()` + the `gainDbPersist` collector launch with `audioConfig.start()`. `onCleared`: `inputDeviceDetector.stop()` → `audioConfig.stop()`. Delete the `gainDbPersist` field + collector.
- [ ] **Step 6: Compile + full unit suite.** `./gradlew compileDebugKotlin testDebugUnitTest`. Expect 120 green. grep for orphaned imports (`UsbAudioDetector` may still be referenced by type in signatures).

---

## Verification

- Clean compile (`compileDebugKotlin`).
- Full existing unit suite green (`testDebugUnitTest`, 120).
- Device smoke: change sample rate / bit depth (Pro gate fires for free tier), toggle a quality preset, switch recorder mode, plug a USB/BT mic and select it (BT → VOICE_COMMUNICATION), record and confirm the file metadata matches the chosen config.

## Commits (match prior steps' rhythm)

1. `docs: design spec for AudioInputConfig extraction (#13 step 6)`
2. `docs: implementation plan for AudioInputConfig extraction (#13 step 6)`
3. `feat: add AudioInputConfig collaborator (#13 step 6, not yet wired)`
4. `refactor: delegate audio-input config to AudioInputConfig (#13 step 6)`
