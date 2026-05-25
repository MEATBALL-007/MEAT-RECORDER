# Live Graph EQ — Design

**Date:** 2026-05-25
**Phase:** 1 of 7 (see companion `2026-05-25-recorder-roadmap.md`)
**Status:** Approved design, ready for implementation plan
**Target:** Android, Kotlin + Jetpack Compose, minSdk 24, Compose BOM 2024.02.00

---

## Goal

Build an interactive 8-band parametric EQ with a live frequency-response curve. The curve redraws as the user manipulates bands; on **Apply** the EQ is rendered offline to a new WAV. A separate **Noise Cut** mode supports tap-to-notch, freeform draw, and auto-detected suggestions. Ten plugin-emulation presets ship. The save behavior is user-selectable per Apply (keep both / replace original / discard).

This is Phase 1 — the EQ-internal feature set (~25 of the 100 features in the roadmap). All other features are tracked separately in `2026-05-25-recorder-roadmap.md`.

## Brand palette

- **Primary accent — orange:** `#FA4616` (EQ response curve, primary buttons, mode-active state)
- **Secondary accent — yellow:** `#FFC72C` (band handles, active band readout, preset name)
- **Neutral — blue-grey:** `#7B8189` (grid, axis labels, dividers, inactive UI, spectrum hills)
- **Background — charcoal:** `#0C0C10` (canvas), `#161618` (cards)
- **Existing MEATrec legacy colors** (`KmuttMaroon #A31F34`, `KmuttGold #D4AF37`) remain in `Color.kt` but are not used for the EQ screen.

## 2D / 3D view modes

A toggle on the EQ screen switches the curve view between two rendering modes:

| Mode | What you see | Use case |
|---|---|---|
| **2D** (default) | Frequency × dB plane. Orange response curve, blue-grey spectrum hills behind, yellow band handles, axis labels. The conventional EQ view. | Surgical editing, precise band placement |
| **3D** | Spectrogram landscape: frequency × time × magnitude. Recent ~10 s of source audio rendered as a tilted 3D mesh in blue-grey. The orange EQ response curve hovers as a glowing ridge along the front edge, intersecting the spectrogram. Yellow handles project down onto the mesh as light columns. | Spotting noise patterns over time, seeing how the EQ cuts through the signal landscape |

The 3D mode subsumes what was originally planned as separate `SpectrogramView` and `WaterfallView` screens (Phase 5 roadmap items). Those dedicated screens still ship in Phase 5 for full-screen analysis use, but the EQ now also has its own integrated 3D preview.

**Implementation:** Compose `Canvas` with manual perspective projection. Spectrogram mesh updated on a coroutine at ~30 fps from a rolling FFT buffer. No new dependencies; all-Kotlin like the rest of the EQ pipeline. If frame rate becomes a problem on large spectrograms, swap inner loop for `AndroidView { GLSurfaceView }` with OpenGL ES — interface unchanged.

ViewModel adds `eqViewMode: StateFlow<EQViewMode>` (`TWO_D | THREE_D`), persisted to `SettingsDataStore`.

## Motion design

"Smooth rich UI animation" is a first-class requirement:

| Surface | Animation |
|---|---|
| Curve recomputation | Each band's contribution animated with Compose `Animatable`; critically-damped spring ~120 ms |
| Band handle drag | Handle follows finger directly; curve under it tweens with spring; release flashes handle gold-to-orange |
| Mode toggle (Parametric ↔ Noise Cut) | `AnimatedContent` with `slideInHorizontally + fadeIn` |
| Auto-detect suggestions appear | Dashed ghost handles enter with pulse; accepting one springs scale 0.6 → 1.0 |
| Freeform draw | Finger trail rendered with fading orange stroke (~600 ms per point); fitted bands animate into place on release |
| Preset switch | Every band's freq/gain/Q interpolates simultaneously across ~350 ms with ease-in-out cubic |
| Apply press | Button scales down to 0.96 on press, expands into a progress ring; on complete becomes checkmark; parent screen's EQ badge springs in |

Reduce-motion accessibility setting disables non-essential animations.

## Architecture

Pure-Kotlin offline render. No native code, no NDK, no Oboe dependency. Biquad math runs on JVM in `DoubleArray`. Sample-rate-aware. Stereo and mono both supported (independent biquad chains per channel). Higher sample rates (96 kHz) handled transparently — relevant for USB-C external mics (Phase 3).

Alternatives considered and rejected for Phase 1:
- **WorkManager background render** — overkill for typical phone recordings (sub-second render); revisit in Phase 7 if render time becomes a problem.
- **Native C++ via JNI / Oboe** — fastest throughput but requires NDK, JNI bindings, platform-specific build. No measured perf wall yet.
- **Android `AudioEffect.Equalizer`** — fixed bands, no parametric control, no offline render. Doesn't fit.

## Data model

### `model/EQBand.kt`

```kotlin
data class EQBand(
    val id: Int,                  // 1..8, stable for UI keys
    val type: EQBandType,         // existing enum, recovered
    val frequencyHz: Float,       // 20f..20000f, log-mapped on UI
    val gainDb: Float,            // -24f..+24f, ignored for Pass/Cut/Notch/BandPass
    val q: Float,                 // 0.1f..18f
    val enabled: Boolean = true,
    val soloed: Boolean = false,
    val muted: Boolean = false,
    val locked: Boolean = false,
)
```

### `model/EQChain.kt`

```kotlin
data class EQChain(
    val bands: List<EQBand>,                 // up to 8
    val noiseCutSuggestions: List<EQBand>,   // proposed by auto-detect, not yet accepted
    val bypassed: Boolean = false,
    val gainCompensation: Boolean = false,
)
```

### `model/EQPreset.kt`

```kotlin
data class EQPreset(
    val name: String,
    val description: String,
    val category: PresetCategory,    // VOCAL, DRUM, MASTER, REPAIR, VINTAGE, NEUTRAL
    val bands: List<EQBand>,
)

val EQ_PRESETS: List<EQPreset> = listOf(
    /* Flat */         EQPreset("Flat", "All bands disabled — true bypass for A/B reference", NEUTRAL, flatBands()),
    /* Air Lift */     EQPreset("Air Lift", "Light high-shelf opening — FabFilter Pro-Q-style sparkle", MASTER, airLiftBands()),
    /* Vintage */      EQPreset("Vintage Console", "SSL E-Channel-style mid scoop + smooth highs", VINTAGE, vintageConsoleBands()),
    /* Pultec */       EQPreset("Pultec Smooth", "Bass boost-and-cut at 100 Hz + 16 kHz silk", VINTAGE, pultecSmoothBands()),
    /* API Punch */    EQPreset("API Punch", "Drum-oriented punch — low thump + 5 kHz attack", DRUM, apiPunchBands()),
    /* Massive Low */  EQPreset("Massive Low", "Manley-Massive-Passive-style wide warm low boost", VINTAGE, massiveLowBands()),
    /* Neve */         EQPreset("Neve Warmth", "1073-style vintage musical curves", VINTAGE, neveWarmthBands()),
    /* Broadcast */    EQPreset("Broadcast Voice", "High-pass + presence boost for spoken word", VOCAL, broadcastVoiceBands()),
    /* De-Ess */       EQPreset("De-Ess", "Narrow 6 kHz notch with high Q", REPAIR, deEssBands()),
    /* Master Bus */   EQPreset("Master Bus", "Gentle mastering polish — 200 Hz dip + high shelf", MASTER, masterBusBands()),
)
```

Concrete preset values are committed in code; user can tune later via "Save custom preset."

### Save mode

```kotlin
enum class ApplySaveMode { BOTH, EQ_ONLY, ORIGINAL_ONLY }
enum class EQEditMode { PARAMETRIC, NOISE_CUT }
enum class EQViewMode { TWO_D, THREE_D }
enum class PresetCategory { VOCAL, DRUM, MASTER, REPAIR, VINTAGE, NEUTRAL }
```

Persisted to existing `SettingsDataStore`. Default `BOTH`. UI is a segmented selector above Apply; Apply button label varies:

| Mode | Apply label | Effect |
|---|---|---|
| `BOTH` | "Apply · save copy" | Render to `<base>_eq.wav` sibling, keep original, `hasEQ=true` |
| `EQ_ONLY` | "Apply · replace original" | Render to temp, atomic rename over original (with confirm) |
| `ORIGINAL_ONLY` | "Discard EQ" | No render, no write |

Pattern generalizes to other processors: noise reduction uses suffix `_nr.wav`; chained processing produces `<base>_eq_nr.wav`. The recovered `NoiseReductionProcessor` will adopt this pattern in Phase 6.

## Audio pipeline (`audio/`)

### `audio/EQProcessor.kt`

RBJ biquad cookbook implementation. Per band, `EQBandType` → `BiquadCoefficients(b0,b1,b2,a1,a2)` via `when`:

- **BELL** → peaking EQ (freq, gain, Q)
- **LOW_SHELF / HIGH_SHELF** → shelving (freq, gain, slope from Q)
- **LOW_PASS / HIGH_PASS / LOW_CUT / HIGH_CUT** → identical math; "cut" labels communicate user intent (each = −12 dB/oct; two cascaded = −24)
- **NOTCH / BAND_PASS** → classic cookbook (freq + Q only, gain ignored)
- **TILT** → composite: low-shelf @ 500 Hz with −gain + high-shelf @ 500 Hz with +gain

Cascade of `Biquad` direct-form-II-transposed objects (one per enabled band per channel). Streams the source WAV in 8192-sample blocks via `RandomAccessFile`, applies the cascade in place, writes to `<base>_eq.wav`. Pre-allocated `FloatArray` / `ShortArray` / `IntArray` buffers — zero allocation in the inner loop. Soft-saturation tanh on the output buffer if peaks would exceed full scale (clip protection toggle).

Supports 16-bit, 24-bit, and 32-bit PCM WAV. Up to 2 channels. Refuses formats outside that range with a clear error.

### `audio/SpectrumAnalyzer.kt`

Reuses recovered `FFTAnalyzer.kt`. Computes averaged log-magnitude spectrum across the whole file: 256 bins, 50% overlap, Hann window. Returns `FloatArray` for `EQCurveView` to overlay. Cached per source file (re-computed only on file change).

### `audio/EQAutoDetect.kt`

Peak-picking on the average spectrum: prominence threshold over local mean. Plus heuristics for known noise:
- 50 Hz and 60 Hz fundamentals + 4 harmonics (mains hum)
- Common A/C hum range 100–120 Hz
- Fan / handling-noise spike detection in 200–500 Hz

Returns proposed `EQBand` notches (NOTCH type, Q derived from peak width, gain capped at −18 dB).

### `audio/EQCurveFitter.kt`

For freeform-draw → biquad chain conversion. Samples the user's drawn path at 32 log-spaced frequencies; computes deviation from 0 dB; finds local extrema; assigns each extremum to a Bell band (capped at 8). User can refine individual bands after.

## UI (`ui/`, `ui/components/`)

### Files to create

- `ui/EQScreen.kt` — main composable
- `ui/components/EQCurveView.kt` — interactive curve canvas (Compose `Canvas`), dispatches to 2D or 3D renderer
- `ui/components/EQCurveView2D.kt` — 2D renderer: response curve + spectrum hills + handles
- `ui/components/EQCurveView3D.kt` — 3D renderer: spectrogram landscape with EQ curve as front-edge ridge
- `ui/components/ViewModeToggle.kt` — 2D / 3D segmented control on the curve frame
- `ui/components/EQBandSheet.kt` — per-band bottom sheet with sliders
- `ui/components/EQPresetPicker.kt` — preset browse drawer with category tabs
- `ui/components/NoiseCutPanel.kt` — Analyze button, Draw button, suggestion list
- `ui/components/SpectrumOverlay.kt` — FFT hills behind the curve
- `ui/components/SaveModeSelector.kt` — segmented control for `ApplySaveMode`
- `ui/components/ApplyButton.kt` — animated press → progress ring → checkmark

### Layout (mobile portrait, ~890 dp tall on a typical phone)

```
┌───────────────────────────────────────┐
│ TopAppBar: filename · Undo/Redo · Apply │  (orange Apply pill)
├───────────────────────────────────────┤
│ Mode segmented: [ Parametric | Noise Cut ] │
├───────────────────────────────────────┤
│                                       │
│   EQCurveView (~55% of screen)        │
│   - spectrum hills (blue-grey, 15%)  │
│   - response curve (orange, 2.5 px)  │
│   - draggable handles (yellow, #1-8) │
│   - active band readout (top-left)   │
│   - axis labels (blue-grey)          │
│                                       │
├───────────────────────────────────────┤
│ AnimatedContent(mode):                │
│   PARAMETRIC → BandChipsStrip (8 chips) │
│   NOISE_CUT → NoiseCutPanel           │
├───────────────────────────────────────┤
│ SaveModeSelector: [Both | EQ only | Original only] │
├───────────────────────────────────────┤
│ PresetBar: "Pultec Smooth"  browse → │
└───────────────────────────────────────┘
```

Tapping a band chip opens `EQBandSheet` (bottom sheet) with freq/gain/Q/type sliders for fine control. Long-press chip → context menu (solo, mute, copy, paste, reset, delete).

### ViewModel additions (`RecorderViewModel.kt`)

```kotlin
private val _currentEQChain = MutableStateFlow(EQChain.empty())
val currentEQChain: StateFlow<EQChain>

private val _eqMode = MutableStateFlow(EQEditMode.PARAMETRIC)
val eqMode: StateFlow<EQEditMode>

private val _eqSelectedBandId = MutableStateFlow<Int?>(null)
val eqSelectedBandId: StateFlow<Int?>

private val _eqSnapshot = MutableStateFlow<EQChain?>(null)
val eqSnapshot: StateFlow<EQChain?>

private val _eqApplySaveMode = MutableStateFlow(ApplySaveMode.BOTH)
val eqApplySaveMode: StateFlow<ApplySaveMode>

private val _eqViewMode = MutableStateFlow(EQViewMode.TWO_D)
val eqViewMode: StateFlow<EQViewMode>

private val _eqRenderProgress = MutableStateFlow(-1f)
val eqRenderProgress: StateFlow<Float>

private val _eqSourceFile = MutableStateFlow<RecordFile?>(null)
val eqSourceFile: StateFlow<RecordFile?>

private val _eqSourceSpectrum = MutableStateFlow<FloatArray?>(null)
val eqSourceSpectrum: StateFlow<FloatArray?>

private val eqHistory = ArrayDeque<EQChain>()    // undo stack, cap 10
private val eqRedo    = ArrayDeque<EQChain>()    // redo stack

fun onEQOpen(file: RecordFile)
fun onEQBandChanged(updated: EQBand)
fun onEQModeToggle(mode: EQEditMode)
fun onEQUndo()
fun onEQRedo()
fun onEQABToggle()
fun onEQResetAll()
fun onEQPresetSelected(preset: EQPreset)
fun onEQNoiseAutoDetect()
fun onEQAcceptSuggestion(band: EQBand)
fun onEQRejectSuggestion(band: EQBand)
fun onEQDrawCurve(points: List<Offset>)
fun onEQTapNotch(frequencyHz: Float)
fun onEQSaveModeChange(mode: ApplySaveMode)
fun onEQApply()
```

### Entry point (debug shell)

`ui/RecorderApp.kt` gets one new button under "Choose save folder":

```kotlin
OutlinedButton(
    onClick = onOpenEQOnLastRecording,
    enabled = files.isNotEmpty(),
) {
    Text("Open EQ on last recording")
}
```

`MainActivity.kt` wires it to `viewModel.onEQOpen(files.last())` and navigates to EQ screen via Compose nav or a simple `eqOpen: Boolean` state in VM.

This entry point is temporary — when Phase 3 rebuilds `RecorderApp.kt` as the real main screen, EQ will be reached by tapping a file in the recordings list (and the debug button removed).

## Data flow

1. User taps "Open EQ on last recording"
2. `vm.onEQOpen(latestFile)` — loads sidecar `<base>_eq.json` if exists, else creates default empty chain
3. VM launches coroutine on `Dispatchers.IO` → computes spectrum via `SpectrumAnalyzer` → emits `eqSourceSpectrum`
4. `EQScreen` composes, observes states
5. User edits via handles / sliders / noise-cut → `onEQBandChanged(...)` → VM pushes prior chain to `eqHistory`, updates `currentEQChain`
6. Curve view animates smoothly
7. User taps Apply → `vm.onEQApply()` dispatches by `eqApplySaveMode`:
   - `BOTH`: render `<base>_eq.wav` + sidecar JSON + flip `hasEQ`
   - `EQ_ONLY`: confirm → render temp → atomic rename → flip `hasEQ`
   - `ORIGINAL_ONLY`: no write
8. Render emits progress 0..1 to `eqRenderProgress`; UI shows ring; on complete UI shows check, navigates back after 600 ms

## Error handling

Every failure → snackbar + state recovery, never a crash:

| Failure | Behavior |
|---|---|
| Source missing / unreadable | Snackbar, back to caller |
| `content://` URI (SAF) source | Read via `ContentResolver.openInputStream`; for BOTH mode write `_eq.wav` via `DocumentFile.createFile()` in the same tree; sidecar JSON stored in the app's `filesDir` keyed by content-URI hash since SAF siblings aren't always writable; for EQ_ONLY, copy contents back with `ContentResolver.openOutputStream(uri, "wt")` (no atomic rename needed, the URI stays the same) |
| Local-path source | Sidecar JSON written next to the WAV; EQ_ONLY uses temp + atomic `renameTo()` |
| Disk full / write permission denied | Rollback `hasEQ`, delete partial, snackbar |
| Unsupported bit depth (≠ 16/24/32 PCM) | Snackbar, refuse render |
| Channels > 2 | Log warning, process channels 0–1 only |
| User backs out mid-render | Dialog "Cancel render?" → cancel coroutine, delete temp |
| Sidecar JSON corrupt | Ignore, start empty, overwrite on next save |
| Auto-detect → 0 peaks | Toast "Spectrum is clean — no peaks detected" |
| Freeform draw < 3 points | Silently ignore |
| Tap empty curve when chain already has 8 bands | Toast "8-band limit reached — disable a band first"; no new band added |
| Render coroutine throws | Catch, log, snackbar, rollback |

## Persistence

- `currentEQChain` autosaves to `<base>_eq.json` on screen exit (process-death safe) and on Apply (BOTH and EQ_ONLY modes)
- `eqApplySaveMode` persisted to existing `SettingsDataStore`
- Custom presets saved as JSON files under `<filesDir>/presets/`

## Testing

### JVM unit (no Android deps)

- `BiquadTest` — sine input, magnitude attenuation at notch freq within ±0.5 dB
- `EQProcessorTest` — apply known preset to golden input WAV, output samples match precomputed reference
- `SpectrumAnalyzerTest` — pink noise → flat shape; pure tone → single peak detection
- `EQAutoDetectTest` — 60 Hz hum injected into clean signal → detection returns notch at 60 Hz ± 2 Hz
- `EQCurveFitterTest` — feed target curve, fitted bands approximate within ±1 dB
- `EQPresetTest` — all 10 presets load; parameters in valid ranges; Flat == identity
- `EQChainSerializationTest` — JSON round-trip preserves every field
- `BiquadCascadeTest` — cascade is order-independent for non-overlapping bands

### Compose UI (instrumented)

- EQScreen renders empty chain
- Mode toggle switches panels
- Apply with no source shows error
- Save mode selector survives process death
- Band handle drag updates `currentEQChain`
- Long-press chip shows context menu

### Manual smoke

After build: record 5s sample → "Open EQ on last recording" → load `Pultec Smooth` preset → Apply with `BOTH` → confirm `<name>_eq.wav` exists and plays back through `MediaPlayer`.

## Out of scope for Phase 1 (deferred to later phases)

Tracked in `2026-05-25-recorder-roadmap.md`. Notable deferrals:

- Per-band solo/mute/lock UI (Phase 2 — data fields exist now, UI doesn't)
- History scrubber (Phase 2)
- Phase response / group delay views (Phase 2)
- Hum-detect / resonance sweep / sibilance band UI affordances (Phase 2)
- Save custom preset / preset morph / import-export JSON (Phase 2)
- Match EQ (Phase 2)
- USB-C external mic / VM40 support (Phase 3)
- Real `RecorderApp` main screen (Phase 3)
- Linear-phase mode, M/S processing, channel link per band (Phase 7)
- WorkManager background render (Phase 7)
- Curve PNG export (Phase 7)
- Voice control / Wear OS (Phase 7)

## Open questions

None blocking. Open invitations:

- Concrete preset values (the 10 plugin-emulation curves) are my best-effort defaults — user can tune via "Save custom preset" once that lands in Phase 2.
- Animation timings (120 ms spring, 350 ms preset morph, 600 ms trail fade) are starting values; tune by feel during implementation.

## Files added / modified

**New:**
- `app/src/main/java/com/example/recorderproject/model/EQBand.kt`
- `app/src/main/java/com/example/recorderproject/model/EQChain.kt`
- `app/src/main/java/com/example/recorderproject/model/EQPreset.kt`
- `app/src/main/java/com/example/recorderproject/model/ApplySaveMode.kt`
- `app/src/main/java/com/example/recorderproject/audio/EQProcessor.kt`
- `app/src/main/java/com/example/recorderproject/audio/SpectrumAnalyzer.kt`
- `app/src/main/java/com/example/recorderproject/audio/EQAutoDetect.kt`
- `app/src/main/java/com/example/recorderproject/audio/EQCurveFitter.kt`
- `app/src/main/java/com/example/recorderproject/audio/Biquad.kt`
- `app/src/main/java/com/example/recorderproject/ui/EQScreen.kt`
- `app/src/main/java/com/example/recorderproject/ui/components/EQCurveView.kt`
- `app/src/main/java/com/example/recorderproject/ui/components/EQBandSheet.kt`
- `app/src/main/java/com/example/recorderproject/ui/components/EQPresetPicker.kt`
- `app/src/main/java/com/example/recorderproject/ui/components/NoiseCutPanel.kt`
- `app/src/main/java/com/example/recorderproject/ui/components/SpectrumOverlay.kt`
- `app/src/main/java/com/example/recorderproject/ui/components/SaveModeSelector.kt`
- `app/src/main/java/com/example/recorderproject/ui/components/ApplyButton.kt`
- Unit tests under `app/src/test/java/com/example/recorderproject/`
- Instrumented tests under `app/src/androidTest/java/com/example/recorderproject/`

**Modified:**
- `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt` — add "Open EQ on last recording" button
- `app/src/main/java/com/example/recorderproject/MainActivity.kt` — wire EQ navigation
- `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` — extend with EQ state + actions
- `app/src/main/java/com/example/recorderproject/ui/theme/Color.kt` — add `RecorderOrange`, `RecorderYellow`, `RecorderBlueGrey` brand colors (existing MEATrec legacy colors retained)
- `app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt` — persist `ApplySaveMode`
