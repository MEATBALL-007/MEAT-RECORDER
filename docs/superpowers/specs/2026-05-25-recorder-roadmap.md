# Recorder app · 100-feature roadmap

**Date:** 2026-05-25
**Companion to:** `2026-05-25-live-graph-eq-design.md` (Phase 1)

This document captures the full feature scope for the Recorder app (100 features + USB-C external mic support) phased across 7 milestones. Each phase is its own brainstorm → spec → implementation cycle. Nothing in the catalog is dropped — only sequenced.

Phase 1 (Live Graph EQ + 2D/3D view) has its own design doc; this file tracks Phases 2–7.

---

## Phase 1 · Live Graph EQ + 2D/3D view *(this session)*

**Scope:** see `2026-05-25-live-graph-eq-design.md`.

**Features delivered (≈ 25 of 100):**
8-band parametric chain · 10 plugin-emulation presets · curve handles · sliders sheet · enable/copy/paste/reset · A/B snapshot · undo/redo (10 levels) · bypass master · spectrum behind curve · 2D ↔ 3D view toggle · 3D spectrogram landscape · noise-cut tap-to-notch · noise-cut freeform draw · noise-cut auto-detect · save mode (BOTH/EQ-only/Original-only) · sidecar JSON persistence · debug-shell entry button · clip indicator · render progress · smooth motion (band springs, mode cross-fade, preset morph, trail fade, Apply ring) · brand palette (orange/yellow/blue-grey).

---

## Phase 2 · EQ depth + power features

**Goal:** round out the EQ to pro-tool quality.

**Features (≈ 18):**
- Per-band solo (hear only this band's contribution)
- Per-band mute (bypass without losing settings)
- Per-band lock (prevent accidental edits)
- History timeline scrubber (drag through edit history)
- Gain compensation (auto-match output level to bypass)
- Spectrum peak-hold (freeze peaks over time)
- Spectrum smoothing (1/3 oct, 1/6 oct)
- Phase response view (toggle from magnitude)
- Group delay view (toggle from magnitude)
- Curve zoom (pinch for dB range) + pan (two-finger drag for freq range)
- Cursor freq readout (touch-and-hold)
- Hum-detect (50/60 Hz comb removal as one tap)
- Resonance sweep tool (drag high-Q peak to find resonances by ear)
- Sibilance band auto-highlight (4–8 kHz for de-ess)
- Save custom preset
- Preset morph slider (0–100% between two presets)
- Import / export preset JSON
- Random preset generator
- Match EQ (load reference WAV, fit chain to it)
- Time-selection EQ (apply to a region of the WAV)

**New files (likely):** `EQHistoryScrubber.kt`, `EQDeEssWizard.kt`, `EQResonanceSweep.kt`, `EQPresetMatchFitter.kt`, `EQTimeSelection.kt`.

---

## Phase 3 · Real RecorderApp + VM40 / USB-C mic

**Goal:** rebuild the main screen and unlock external mic input.

**Features (≈ 13):**
- Real `RecorderApp` main screen — waveform, take counter, file list, scene/notes inputs, playback controls (replaces current debug shell)
- Sort + search file list (6 modes: name, date, scene, size, duration, EQ-applied)
- File card overflow menu — rename · share · info · delete · open in another app
- Sample-rate selector (44.1 / 48 / 96 kHz, mid-session warning)
- Bit-depth selector (16 / 24-bit)
- Audio source picker (Mic / Camcorder / Voice Recognition / Unprocessed)
- **USB-C external mic support (VM40 + UAC-compliant devices)**: detect attached devices via `BroadcastReceiver` (`ACTION_USB_DEVICE_ATTACHED` / `_DETACHED`), enumerate `AudioDeviceInfo` for USB devices, surface in source picker, route `AudioRecord.setPreferredDevice()`, auto-switch on plug/unplug, persist last-used device, capture device name into `RecordFile.inputDeviceName`
- VU meter on main screen
- Recording time limit (auto-stop)
- Playback speed (0.5×–2×)
- Loop playback (repeat selection)
- Notes / tags UI surface (data already in ViewModel)
- Open EQ from file list item (replaces Phase 1's debug-shell entry button)

**Spec scope:** main-screen architecture, EQ-from-file-list navigation, USB device routing layer, audio-source-picker abstractions.

---

## Phase 4 · Production / broadcast tooling

**Goal:** field-recorder-grade metadata and workflow.

**Features (≈ 12):**
- Splash screen with liquid-blob entry animation
- Mode selector screen (Field / Podcast / Music / Voice Memo)
- Multi-take recording (auto-numbered takes per scene)
- Production slate dialog (scene/take/roll/camera, optional clapper sync)
- LTC timecode generation (SMPTE)
- Pre-roll buffer (5 s circular buffer; record button captures the 5 s before)
- WAV cue markers (tap during record drops marker, saved into WAV)
- iXML metadata block (broadcast standard)
- LUFS metering during record
- Scene slicer (auto-split long recording by silence detection)
- Audio transcription (speech-to-text → `TranscriptScreen`)
- Trim screen (crop start/end via existing `WavTrimmer`)
- Sync points for video (drop sync flash + audio click; `RecordFile.syncPointMs` exists)

**Recovered classes that come back:** `SplashScreen`, `LiquidBlob`/`LiquidSplashEffect`, `ModeSelectorScreen`, `MultiTakeScreen`, `SlateDialog`, `LtcGenerator`, `PreRollBuffer`, `WavCueWriter`, `IxmlWriter`, `LufsProcessor`, `SceneSlicer`, `AudioTranscriber`, `TranscriptScreen`, `TrimScreen`, `TimecodeDisplay`, `ProductionSlate` (model), `CuePoint` (model), `Transcript` (model).

---

## Phase 5 · Dedicated visualization screens

**Goal:** rebuild full-screen visualizers (the integrated 3D mode on the EQ screen ships in Phase 1; these are the full-screen versions).

**Features (≈ 5):**
- `SpectrogramScreen` + `SpectrogramView` — full-screen 2D heatmap (time × freq, color = magnitude)
- `HarmonicPortraitScreen` + `HarmonicPortraitView` — visualization of harmonic structure (chromagram or similar)
- `WaterfallView` — full-screen 3D scrolling spectrogram
- `PitchView` — live pitch + nearest note readout, uses recovered `PitchDetector`
- Live frequency analyzer overlay (during recording)

---

## Phase 6 · Power-user + external integrations

**Goal:** workflow features and integrations.

**Features (≈ 18):**
- Bluetooth monitoring (BT SCO state in UI; uses recovered `BtScoState`)
- Location tagging (GPS coords per recording)
- Environment tag (auto-detect indoor/outdoor/vehicle from noise floor)
- Theme switching (light/dark/system; uses recovered `ThemeDataStore` shape)
- Fan mode card (cooling for long sessions — battery + thermal)
- Cloud backup (Drive / Dropbox / iCloud — **explicit upload, not background sync**; lesson from 2026-05-25 eviction)
- Batch operations (apply same EQ / preset across multiple files)
- Star / favorite recordings
- Lock file (prevent edits; `RecordFile.isLocked` exists)
- Share to other apps (Android share sheet)
- MP3 / FLAC / AAC export (format conversion on share)
- Network remote control (web UI; uses recovered `RecorderRemoteServer`)
- Onboarding tour (first-launch overlay)
- Help bubbles
- Reduce-motion / high-contrast accessibility settings
- Project-level default EQ chain (auto-apply after each recording)
- Auto-noise-reduction toggle (already plumbed via recovered `NoiseReductionProcessor`)
- Manual noise reduction with graph editor (same UI pattern as EQ)

---

## Phase 7 · Advanced DSP + future

**Goal:** features that need solid foundation underneath.

**Features (≈ 10):**
- Pitch shift / time stretch (offline; uses recovered `PitchShifter`)
- Stereo widening (M/S processing on mono → faux-stereo)
- Convolution reverb / room profile (uses recovered `RoomProfile`)
- Master limiter (brick-wall on output stage)
- Linear-phase EQ mode (FIR convolution; alternate to biquad)
- Background WorkManager render (long-file renders as Worker with notification)
- Wear OS companion control (remote start/stop, basic levels)
- Voice command record ("Hey Recorder, start scene 2 take 1")
- Crash-safe WAV writing (header re-finalized every chunk)
- M/S processing per band in EQ
- Channel link per band (L/R independence)
- Listen mode (solo a notch — hear what you're cutting)
- Snap-to-musical-notes (bands snap to A=440 equal temperament)
- Curve PNG export
- LUFS / correlation meters in EQ screen

---

## Cross-cutting principles

- **No iCloud-style background sync** for the codebase or recordings; the 2026-05-25 eviction was caused by Desktop+Documents iCloud sync. Cloud uploads in Phase 6 are explicit user actions only.
- **All-Kotlin DSP** unless a measured performance wall forces JNI/NDK. Biquad / FFT / WAV I/O all live in pure JVM code.
- **Stable model APIs.** Once a model (`RecordFile`, `EQBand`, `EQChain`, `EQPreset`) is shipped in a phase, subsequent phases add fields with defaults; never break existing serialized state.
- **Reduce-motion accessibility from Phase 1.** Animations are first-class but must be disable-able for users who need it.
- **Brand palette** (orange `#FA4616`, yellow `#FFC72C`, blue-grey `#7B8189`) is established in Phase 1 and used everywhere from then on. Existing MEATrec legacy maroon/gold are kept in `Color.kt` but not used in new UI unless explicitly redesigned.

## Out-of-scope (not in any phase)

- Multi-track / DAW-style timeline editing
- VST / AU plugin hosting
- MIDI control surfaces
- Multiplayer / collaborative recording
- Real-time streaming / broadcast publishing

These would each be their own app and aren't on this roadmap.

## Phase ordering rationale

EQ first (Phase 1) because it's the user's original ask and is self-contained.

EQ-depth (Phase 2) before recorder rebuild (Phase 3) because the EQ has the most direct user-facing leverage and Phase 1 already lands the user on a real, useful screen.

Phase 3 lands the real recorder UX **and** USB-C mic together because they share the audio-source picker and main-screen plumbing.

Phase 4 is production tooling — bigger scope, multiple recovered classes, depends on the real main screen.

Phase 5 (visualization screens) is small but depends on having recordings to visualize from the file list (Phase 3).

Phase 6 is breadth (workflow + integrations) — most items are independent, can be parallelized in implementation.

Phase 7 is the deep DSP frontier — best done last when the surface is stable.

User can request a reordering at any time. The brand palette and motion design from Phase 1 are cross-cutting commitments that hold regardless.
