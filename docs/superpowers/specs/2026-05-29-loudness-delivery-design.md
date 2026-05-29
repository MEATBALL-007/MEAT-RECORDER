# Loudness + Delivery (Target-aware recording + auto-render on stop)

**Date:** 2026-05-29
**Phase:** 4 (Production / Broadcast depth) — picks up Phase 4's "LUFS meter UI surface" PENDING item and expands it.
**Companion to:** `2026-05-25-recorder-roadmap.md` (master roadmap).

## Goal

Give the recordist real-time visual feedback about loudness (LUFS) against a chosen delivery target and, on stop, automatically produce a delivery-ready file normalized to that target with true-peak ceiling enforced. The original recording is never altered.

## User flow

### Before recording

A `LoudnessTargetChip` lives in the recording header alongside the existing Monitor / LiveEQ / MicSource chips. Default state: `Target: Podcast –16`. Tap opens a bottom-sheet picker:

| Target    | LUFS | TP ceiling | Used for                        |
|-----------|------|------------|---------------------------------|
| Streaming | –14  | –1 dBTP    | YouTube, Spotify, Apple Music   |
| Podcast   | –16  | –1 dBTP    | Apple Podcasts, Spotify spoken  |
| Broadcast | –23  | –1 dBTP    | EBU R128, BBC                   |
| Custom    | user | user       | manual (sliders)                |
| Off       | —    | —          | no target, no auto-render       |

Tap-and-select changes the **session target** only (the target used by the next take). The current default is shown with a small dot. To change the saved default, long-press the chip → "Save as default" appears in the picker sheet. This avoids the surprise of an experimental one-time pick becoming sticky forever.

### During recording

The "LUFS" tile in `RecordingActiveSection` becomes a vertical-bar meter with a horizontal target line and three colored zones (green ≤ ±1 LU, yellow ≤ ±3 LU, red > ±3 LU). Below the bar: `M` (momentary), `S` (short-term), `I` (integrated) numeric readouts plus a `TP` indicator that lights when true-peak crosses –3 dBTP.

### When recording stops

If target ≠ `Off`, MEATrec runs the offline delivery renderer on `Dispatchers.IO`. The record button morphs into a progress ring; a snackbar at the bottom reports the result:

> Rendered –16 LUFS · –1 dBTP · PASS

`name.wav` (original, untouched) and `name_delivery.wav` (target-matched) both appear in the file list. Delivery file gets a small `–16` badge.

### File list

Original and delivery group as one expandable row. Tap-and-hold on the delivery file → "Re-render with different target…".

### Sound report

Existing `exportSoundReport()` CSV gains four columns: `Integrated` (LUFS), `TP_dBTP`, `LRA` (LU), `Target_Result` — `PASS` when within ±0.5 LU of target and TP under ceiling, `FAIL` when delivery exists but did not meet target, `N/A` when target was `Off` or no delivery file exists.

## Architecture

### New files

**Audio layer**
- `audio/TruePeakDetector.kt` — ITU-R BS.1770 true-peak (4× polyphase oversample + per-channel peak hold). Stream API: `feed(samples)` / `peakDbTP`.
- `audio/LoudnessRangeMeter.kt` — BS.1770 LRA: short-term LUFS histogram, 10th–95th percentile, –20 LU relative gate.
- `audio/DeliveryRenderer.kt` — orchestrates the two-pass offline render.

**Model**
- `model/LoudnessTarget.kt` — sealed class (`Off`, `Streaming`, `Podcast`, `Broadcast`, `Custom(lufs, tpDbtp)`).
- `model/DeliveryResult.kt` — measurement + render outcome.

**UI**
- `ui/components/LoudnessMeterBar.kt` — vertical bar meter with target line, zones, M/S/I, TP indicator.
- `ui/components/LoudnessTargetChip.kt` — recording-header chip + bottom-sheet picker.

### Modified files

- `audio/AudioRecorderManager.kt` — extend existing per-sample hook to also feed `TruePeakDetector`. No format / path changes.
- `RecorderViewModel.kt` — add `loudnessTarget: StateFlow<LoudnessTarget>`, `liveTpDbTp: StateFlow<Float>`, `isRenderingDelivery: StateFlow<Boolean>`. On stop, if target ≠ `Off`, kick off `DeliveryRenderer.render(file, target)` on `Dispatchers.IO`; emit snackbar on result. Extend `exportSoundReport()` with the four new columns.
- `data/SettingsDataStore.kt` — persist `defaultLoudnessTarget` + `customLoudnessLufs` + `customLoudnessTpCeiling`.
- `model/RecordFile.kt` — add `deliveryPath: String?`, `deliveryResult: DeliveryResult?`.
- `ui/RecorderApp.kt` — insert `LoudnessTargetChip` next to existing feature chips.
- `ui/components/RecordingActiveSection.kt` — swap LUFS tile for `LoudnessMeterBar`.
- `ui/components/RecordingsListCard.kt` — group original + delivery into an expandable row; render `–16` badge.

### Untouched

Recording path, sample-rate / bit-depth handling, EQ chain, cue / slate / iXML writers, monitor path, USB routing, existing animations, brand palette.

## Data model

### `LoudnessTarget` (sealed class)

```kotlin
sealed class LoudnessTarget {
    object Off                                           : LoudnessTarget()
    object Streaming  /* -14 LUFS, -1 dBTP */            : LoudnessTarget()
    object Podcast    /* -16 LUFS, -1 dBTP */            : LoudnessTarget()
    object Broadcast  /* -23 LUFS, -1 dBTP */            : LoudnessTarget()
    data class Custom(val lufs: Float, val tpDbtp: Float): LoudnessTarget()

    val displayName: String       // "Podcast –16"
    val targetLufs: Float?        // null when Off
    val tpCeilingDbtp: Float?     // null when Off
    companion object { val DEFAULT = Podcast }
}
```

Single source of truth for "what loudness/TP a target means." UI and renderer both read from this — no duplicated constants.

### `DeliveryResult` (data class)

```kotlin
data class DeliveryResult(
    val integratedLufs: Float,      // BS.1770-4 integrated, gated
    val shortTermMaxLufs: Float,    // max short-term LUFS over file
    val momentaryMaxLufs: Float,    // max momentary LUFS
    val truePeakDbtp: Float,        // BS.1770 TP, dBTP
    val lra: Float,                 // loudness range, LU
    val appliedGainDb: Float,       // gain applied in render
    val targetLufs: Float?,         // copy of target at render time
    val tpCeilingDbtp: Float?,
    val passed: Boolean,            // within ±0.5 LU and TP under ceiling
    val deliveryFile: String,
    val renderedAt: Long            // epoch ms
)
```

### Persistence — `SettingsDataStore` additions

```kotlin
val defaultLoudnessTargetKey   = stringPreferencesKey("default_loudness_target")
val customLoudnessLufsKey      = floatPreferencesKey("custom_loudness_lufs")
val customLoudnessTpCeilingKey = floatPreferencesKey("custom_loudness_tp_ceiling")
```

Stored target is one of `OFF | STREAMING | PODCAST | BROADCAST | CUSTOM`. Defaults: `PODCAST`, `-16f`, `-1f`. Picked up by the existing hydrate-on-init path in `RecorderViewModel`.

### Sidecar JSON

`<name>_delivery.json` lives next to the delivery WAV and serializes the `DeliveryResult` fields. Read by the library scanner so `RecordFile.deliveryResult` survives cold starts. No DB — matches today's `_eq.json` sidecar pattern.

### Library scan

For each `<name>.wav`: if `<name>_delivery.wav` exists, mark as delivery sibling (not standalone row) and parse `<name>_delivery.json` into `deliveryResult`. Orphan delivery files (no sibling) become standalone rows so nothing disappears.

## DSP pipeline

### Triggers

Render starts when recording stops if `target ≠ Off`. Runs on `Dispatchers.IO`, never blocks the UI thread.

### Inputs / outputs

| | Spec |
|--|--|
| Input  | `name.wav` — any supported format (16/24-bit PCM, 32-bit float, mono/stereo, 44.1/48/96 kHz) |
| Output | `name_delivery.wav` — always **24-bit PCM**, same sample rate and channel count as source |
| Sidecar | `name_delivery.json` — `DeliveryResult` serialized |
| Original | untouched (invariant) |

**Why 24-bit PCM output:** universally accepted by DAWs, streaming platforms, broadcast pipelines; >144 dB dynamic range, well below any audible quantization noise after limiting.

### Pass 1 — measure

Stream the source once, chunk size 8192 samples. Each chunk feeds:

- `LufsProcessor` → integrated, short-term max, momentary max LUFS
- `TruePeakDetector` → max true-peak dBTP across the file
- `LoudnessRangeMeter` → LRA

End of file → emit `Measurement(integratedLufs, tpDbtp, lra, shortTermMax, momentaryMax)`.

### Gain calculation

```kotlin
val rawGainDb   = target.lufs - measurement.integratedLufs
val postTpDbtp  = measurement.tpDbtp + rawGainDb
val tpHeadroom  = target.tpCeiling - postTpDbtp        // negative => limiter will engage
val appliedGain = rawGainDb                            // apply the full gain
val needsLimit  = tpHeadroom < 0f
```

The full target gain is always applied. If that would push true-peak past the ceiling, the limiter handles the overshoot — this matches every reference normalizer (Fab Filter Pro-L 2, iZotope RX, Adobe Audition Match Loudness). Falling short of target to avoid limiting would be silently wrong.

### Pass 2 — render

Open source for read; open `name_delivery.wav` for write (24-bit PCM). For each 8192-sample chunk:

1. Apply `appliedGain` (linear scalar from dB).
2. If `needsLimit`: pass through existing `MasterLimiter` with `ceiling = target.tpCeilingDbtp`, attack 1.5 ms, release 50 ms (brick-wall ISR).
3. Float → 24-bit PCM, write.

After write: re-run `TruePeakDetector` over the delivery file to confirm post-limit TP for the report (cheap second walk of output).

### Pass / fail

```
passed = |measured_delivery_integrated - target.lufs| ≤ 0.5 LU
       AND delivery_tp_dbtp ≤ target.tpCeiling + 0.1 dB tolerance
```

### Edge cases

- **Silent / near-silent files** (integrated < –60 LUFS): skip render, `passed = false`, snackbar "Too quiet to normalize — re-record louder." Avoids amplifying noise by 30+ dB.
- **Sub-window files** (< 0.4 s): skip render silently, no error UI.
- **Unsupported source format** (future entry point): `Result.failure(UnsupportedFormatException)`; snackbar names the format. Not reachable for native recordings.

### Why no single-pass / real-time normalize

Integrated LUFS is a function of the whole file. You cannot know the correct gain until the entire file has been measured. Single-pass renormalization drifts on any file longer than a few seconds and is a known anti-pattern.

### Cancellation

Render runs in a `Job` on the VM scope. User leaving the screen does not cancel. User starting a new recording does not cancel previous render. Explicit "Cancel render" tap on the snackbar stops the job and deletes the partial `_delivery.wav`.

### Performance budget

~2–3× real-time on a mid-range phone for 48 kHz stereo. A 5-minute take renders in ~1–2 s. For takes >60 s, the snackbar upgrades to a determinate progress notification.

## Live meter UI

### Layout (text mockup)

```
┌──────────────────────────┐
│                          │  ← top:  +12 LU above target (red)
│                          │
│ ── ── ── ── ── ── ── ── ─│  ← target line, orange #FA4616, 2 dp
│ ▓▓▓▓▓▓▓▓▓ ← current S    │
│ ▓▓▓▓▓▓▓▓▓                │  ← green: ±1 LU around target
│ ▓▓▓▓▓▓▓▓▓                │  ← yellow: ±1 to ±3 LU
│ ▓▓▓▓▓▓▓▓▓                │  ← red: beyond ±3 LU
│                          │  ← bottom: –12 LU below target
│ M –17.1   S –16.4   I –15.9│
│ TP –0.8 dBTP             │
└──────────────────────────┘
```

### Bar semantics

- Tracks **short-term LUFS** (3 s window). Short-term is the right thing to watch — momentary jumps too fast to react to; integrated lags too far to be useful in real time.
- Scale: **target ± 12 LU**. Fixed range; no auto-zoom. Users build muscle memory.
- Fill color matches the zone the current short-term value sits in.
- Target line always orange `#FA4616`, 2 dp.
- Dotted integrated tick draws as a thin horizontal mark — gives long-term truth without claiming the main fill.

### Numeric readouts

| Label | Window  | Update rate | Notes                              |
|-------|---------|-------------|------------------------------------|
| M     | 400 ms  | 10 Hz       | momentary LUFS, gated              |
| S     | 3 s     | 10 Hz       | short-term LUFS, gated             |
| I     | full    | 1 Hz        | integrated LUFS, gated at –70      |
| TP    | sample  | 10 Hz       | white normally; yellow ≥ –3 dBTP; red ≥ –0.1 dBTP |

All four use the existing brand mono number style already used for the recording timer.

### When target is `Off`

Bar draws but with no target line and no zones — fill is brand blue-grey `#7B8189` throughout. Just M / S / I / TP readouts. Matches today's "informational only" behavior.

### Animation

- Bar fill height uses the existing 50 ms spring from `RecordingMeterBar`. No new animation primitive.
- Zone color cross-fades over 150 ms when value crosses a boundary — avoids strobing on the edge.
- Reduce-motion (already in `SettingsScreenV2`): color snaps, height updates without spring.

### Tile size & placement

Replaces today's one-row LUFS text tile in `RecordingActiveSection.kt`. Tile grows to fixed 120 dp height; other tiles (RMS, Pitch, Spectrum thumbnail) lay out around it via the existing `FlowRow`. Landscape: tile keeps 120 dp height, bar widens, readouts move to the right side.

### Target chip

`LoudnessTargetChip` renders as a small pill — brand orange when target is set, blue-grey when `Off`. Text: `–16 PODCAST` / `–14 STREAM` / `–23 BCAST` / `CUSTOM` / `OFF`. Tap → bottom sheet with the five options; Custom row reveals two sliders:

| Slider     | Range          | Step  | Default |
|------------|----------------|-------|---------|
| LUFS       | –30 to –9 LUFS | 0.5   | –16     |
| TP ceiling | –3 to 0 dBTP   | 0.1   | –1      |

Live preview text below the sliders: "Aim for –17.5 LUFS, peaks stay below –1.5 dBTP."

### Stop / render feedback

Record button completes its existing 600 ms stop animation, then morphs into a 24 dp progress ring at the same position for the render. No modal. Snackbar at bottom — message picked by outcome:

| Outcome                              | Snackbar message                                       |
|--------------------------------------|--------------------------------------------------------|
| PASS                                 | `Rendered –16 LUFS · –1 dBTP · PASS`                   |
| FAIL — measured too quiet (< –60)    | `Too quiet to normalize — re-record louder.`           |
| FAIL — gain undershoot post-limit    | `Couldn't reach –16 LUFS without clipping — try lower target or recording quieter.` |
| FAIL — render error (disk, perm)     | `Delivery render failed — keeping original only.`      |
| Off                                  | (no snackbar — silent like today)                       |

## Error handling

| Scenario | Behavior |
|---|---|
| Render fails mid-write (disk full, perm revoked, source vanished) | Partial `_delivery.wav` deleted; `_delivery.json` never written; snackbar "Delivery render failed — keeping original only." Original untouched (invariant). |
| Source is unsupported format | `Result.failure(UnsupportedFormatException)`; snackbar names format. Not reachable for native recordings. |
| Source < 0.4 s | Skip render silently. |
| Source < –60 LUFS (near-silent) | Skip render; `passed = false`; snackbar "Too quiet to normalize — re-record louder." |
| Delivery TP > ceiling + 0.1 dB (limiter undershoot) | Write file; log non-fatal warning; `passed = false`. Limiter tuning issue, observable later. |
| `defaultLoudnessTarget` key missing (first launch of this version) | Default to `Podcast`. DataStore handles missing keys; no migration code. |
| Process death mid-render | In-flight render lost; next library scan cleans `_delivery.wav` without `_delivery.json` sibling. |
| VM scope cancellation | `Dispatchers.IO + SupervisorJob` so render crash does not tear down recording. |
| Concurrent renders | Each render in its own `Dispatchers.IO` coroutine, 2-slot semaphore, third queues. Snackbar order tells the story. No queue UI. |

## Testing

### JVM unit tests

Project already has a green JVM suite — all new tests live there.

| Test | What it verifies |
|---|---|
| `LufsProcessorTest` (existing) | unchanged — relies on existing BS.1770-4 reference correctness |
| `TruePeakDetectorTest` (new) | Sine sweeps at 44.1 / 48 / 96 kHz; peak dBTP within ±0.1 dB of analytical truth. BS.1770-4 Annex 2 §6.1 ISP test vector → ≥ 0.7 dBTP where naive sample-peak misses. |
| `LoudnessRangeMeterTest` (new) | BS.1770 LRA reference tracks; within ±0.5 LU. |
| `LoudnessTargetTest` (new) | DataStore round-trip; `Custom(lufs, tp)` survives encode/decode; `DEFAULT == Podcast`. |
| `DeliveryRendererTest` (new) | (a) –24 LUFS / –6 dBTP synth → Podcast: gain ≈ +8 dB, post-integrated ≈ –16 ± 0.5 LU, post-TP ≤ –1 dBTP, `passed = true`. (b) –6 LUFS / –1 dBTP loud synth → Broadcast: gain ≈ –17 dB, no limiter engagement, integrated ≈ –23. (c) 0 dBFS square wave → Streaming: limiter engages, post-TP ≤ –1 dBTP + 0.1, no clipped bytes. (d) Silent (–80 LUFS): skip render, no output, `passed = false`. (e) 200 ms file: skip render, no output, no exception. |
| `RecorderViewModelTest` (extend) | On stop with target set: `isRenderingDelivery` flips true→false; `RecordFile.deliveryResult` populates. On stop with target `Off`: no render queued. |
| `SettingsDataStoreTest` (extend) | Round-trip `defaultLoudnessTarget = Custom(-19f, -2f)`. |

### Manual / device verification (post-implementation checklist)

1. Record 30 s speech at comfortable volume → live bar tracks short-term, integrated readout settles.
2. Stop → snackbar "Rendered –16 LUFS · PASS" within ~3 s; delivery file appears with `–16` badge.
3. Open both files in an external loudness tool (Auphonic dryrun or equivalent) → reported integrated LUFS matches `DeliveryResult.integratedLufs` within ±0.3 LU.
4. Change target to `Streaming` mid-session; re-record; verify chip persists across app restart.
5. Hot mic → ISP overshoot → limiter engages, delivery TP ≤ –1 dBTP, original stays clipping-free.
6. Toggle reduce-motion → bar transitions snap.
7. Storage <100 MB free → render-fail snackbar; original recording survives.

## Out of scope (deferred — "Full suite" path)

- Real-time auto-leveler / mic-gain ride
- Per-platform spec library beyond the four targets (Spotify, Apple Podcasts, YouTube, BBC, EBU R128 dedicated rows)
- Embedded BWAV/iXML loudness metadata (would extend `IxmlWriter`)
- Batch re-render of past files (would extend `RecordingsToolbar`)

## Open questions

None at design time. Brought to user during brainstorm and resolved:

- Scope = "Show + auto-fix on save" (Option 2)
- Output format = 24-bit PCM
- Default target = Podcast –16
- Gain rule = always apply full target gain; let limiter handle TP overshoot
- Render trigger = automatic on stop (no extra tap)
