# Settings Persistence & Factory Reset — Design

**Date:** 2026-05-27
**Status:** Draft (pending user review)
**Scope:** Make every setting/toggle in `RecorderViewModel` survive app close/reopen, and add a "Reset to factory defaults" button in `MeatRecSettings`.

---

## Goal

Today, almost every setting the user touches in MEATrec lives only in `MutableStateFlow`s on `RecorderViewModel` and is gone the moment the process dies. The user wants two things:

1. **Persistence** — settings/toggles stay the same across close/reopen.
2. **Reset Factory** — a button in the Settings screen that returns those settings to declared defaults, while preserving recorded files, custom EQ presets, onboarding state, and theme.

Out of scope: persisting transient UI position (last selected file, which screen was open, search query). Recordings themselves already live on disk and aren't affected.

---

## Storage layout

Two backing stores, each with a clear role.

### A. `SettingsDataStore` — wiped on Reset Factory

The existing `SettingsDataStore` (DataStore-backed, file: `app_settings.preferences_pb`) is expanded to cover every in-memory recorder/EQ/playback setting. New keys, with their declared defaults:

| Group | Key | Type | Default |
|-------|-----|------|---------|
| Recording | `recorder_mode` | String (enum name) | `CUSTOM` |
| Recording | `audio_source_name` | String | `"Microphone"` |
| Recording | `mic_source_label` | String | `"Microphone"` |
| Recording | `input_gain_db` | Float | `0f` |
| Recording | `noise_reduction` | Bool | `true` |
| Recording | `sample_rate_hz` | Int | `48000` |
| Recording | `bit_depth` | Int | `16` |
| Recording | `channel_count` | Int | `1` |
| Recording | `countdown_sec` | Int | `0` |
| Recording | `max_duration_min` | Int | `0` |
| Recording | `auto_stop_min` | Int | `0` |
| Recording | `quality_preset` | String (enum name) | `Default` |
| Recording | `scene_name` | String | `"Scene 1"` |
| Live toggles | `live_noise_gate` | Bool | `false` |
| Live toggles | `agc` | Bool | `false` |
| Live toggles | `hi_pass` | Bool | `false` |
| Live toggles | `anti_clip` | Bool | `false` |
| Live toggles | `compressor` | Bool | `false` |
| Live toggles | `stereo_widener` | Bool | `false` |
| Live toggles | `vad` | Bool | `false` |
| Live toggles | `live_eq_enabled` | Bool | `false` |
| Live toggles | `live_eq_band_gains` | String (6 floats, comma-joined) | `"0,0,0,0,0,0"` |
| EQ editor | `current_eq_chain_json` | String | `EQChain.empty()` as JSON |
| EQ editor | `eq_mode` | String (enum name) | `PARAMETRIC` |
| EQ editor | `eq_view_mode` | String (enum name) | `TWO_D` |
| EQ editor | `eq_apply_save_mode` | String (enum name) | `BOTH` |
| EQ editor | `eq_bypassed` | Bool | `false` |
| Playback | `playback_speed` | Float | `1f` |
| Playback | `playback_loop` | Bool | `false` |
| Playback | `playback_volume` | Float | `1f` |
| Misc | `save_directory_uri` | String? | `null` |
| Misc | `group_by_scene` | Bool | `false` |
| Misc | `lockscreen_controls` | Bool | `true` |
| Misc | `cloud_backup` | Bool | `false` |

EQ chain serialization reuses the existing `EQChainJson.toJsonString` / `fromJsonString`.

### B. `SharedPreferences` — preserved across Reset Factory

These keep working exactly as they do today. Reset Factory does **not** touch them.

- **`getPreferences(MODE_PRIVATE)`** (used by `MainActivity`): `onboarding_done`, `mode_chosen`, `app_theme`, `custom_presets`, `active_preset`
- **`meatrec_ui`** (used by `RecorderViewModel`): `sort_order`, `file_filter`

Rationale: these are app-level state (intro shown, theme chosen, user-saved presets, UI sort prefs). Wiping them on Reset Factory would re-trigger onboarding and lose user-named presets, which the user explicitly didn't want.

---

## Hydration & write-through

### Hydration on `RecorderViewModel.init`

A single coroutine reads each persisted value with `dataStore.data.first()` and assigns it into the matching `MutableStateFlow`. While hydration is running, a `private val hydrated = MutableStateFlow(false)` flag is `false`. Each persistence-writing setter checks this flag and skips the DataStore write until hydration completes — this prevents the "hydrate writes back the value we just read" loop.

```kotlin
init {
    viewModelScope.launch {
        val snapshot = settings.snapshot() // reads all keys in one .first()
        applySnapshot(snapshot)
        hydrated.value = true
        rewireRecorderFromState()
    }
}
```

### Write-through

Every existing setter (`updateInputGainDb`, `setQuality`, `toggleAgc`, etc.) keeps its current in-memory behavior, then appends:

```kotlin
if (hydrated.value) viewModelScope.launch { settings.setX(value) }
```

For toggles that flip a boolean (the common case), that's one extra line per function.

### Debounced setters

`liveEqBandGains` and `inputGainDb` can fire many times per second (slider drags, +/- buttons). The UI updates instantly via the `StateFlow`, but the disk write is routed through a `MutableSharedFlow` with `.debounce(150.ms).collect { settings.setX(it) }`. This keeps DataStore writes bounded and avoids thrashing the preferences file.

### Re-firing recorder side effects

Several toggles aren't pure StateFlows — they also push state into `AudioRecorderManager` (`setAgc`, `setHiPass`, `setLiveNoiseGate`, `setInputGain`, `setLiveEqChain`). After hydration, a `rewireRecorderFromState()` helper re-calls those so the native recorder picks up the persisted state on first record.

### Things that are intentionally NOT persisted

- **`monitor_enabled`** — opening the mic for live monitoring is an audio-capture side effect, not a preference. Persisting it would either (a) auto-open the mic on launch (surprising, battery-costly), or (b) show the toggle as ON while the mic is actually OFF (misleading). Solution: always start at `false`; user re-enables explicitly.
- **`is_recording`, `is_paused`, `is_playing`** — pure runtime state.
- **`liveCueCount`, `currentWaveform`, `liveSpectrum`, `spectrumHistory`, `livePitchHz`, `eqRenderProgress`** — derived/streaming runtime data.
- **`selectedFile`, `eqOpen`, `menuOpen`, `statsOpen`, `trimFile`, `portraitFile`, `multiTakeOpen`, `transcriptFile`, `pitchShiftFile`, `roomProfilerOpen`, `sceneSliceFile`, `designPickerOpen`** — UI position / open screens. Out of scope per user (settings only, not UI position).
- **`scheduledStartMs`, `recentIds`, `_transcripts`, `eqSnapshot`, `eqHistory`, `eqRedo`, `eqSourceFile`, `eqSourceSpectrum`, `eqSelectedBandId`, `ghostTakeFile`, `errorMessage`, `needsPermission`, `searchQuery`, `selectedFileIds`** — session-only.

### SAF URI permission

Persisting `saveDirectoryUri` as a string isn't enough on Android — `takePersistableUriPermission` is per-process. Two places need to call it:

1. **`setSaveDirectoryUri`** — the original grant path. Call `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ|WRITE)` immediately so the grant becomes persistable.
2. **Hydration** — after reading the persisted URI, call `takePersistableUriPermission` again. If it throws `SecurityException` (user revoked it via system settings), null out the flow and log a warning.

---

## Factory reset behavior

### `RecorderViewModel.resetFactory()`

Single entry point. Order matters:

1. `dataStore.edit { it.clear() }` — wipes all DataStore-backed keys.
2. `applyDefaults()` — central helper that reassigns every in-memory `StateFlow` to its declared default. Defaults live in exactly one place (a `Defaults` object) so they can't drift between hydration fallback and reset.
3. `rewireRecorderFromState()` — push defaults into `AudioRecorderManager`: `setAgc(false)`, `setHiPass(false)`, `setLiveNoiseGate(false, …)`, `setInputGain(1f)`, `setLiveEqChain(null, sampleRate)`.
4. If monitor was running, stop it — reuse the off-branch of `toggleMonitor` (`audioMonitor.stop()` + SCO teardown + decay-job cancel + listener clear).
5. Clear EQ undo/redo history (`eqHistory.clear(); eqRedo.clear()`).
6. Toast: `"Settings reset to defaults"`.

### Explicitly NOT touched

- `MODE_PRIVATE` SharedPrefs: `onboarding_done`, `mode_chosen`, `app_theme`, `custom_presets`, `active_preset`.
- `meatrec_ui` SharedPrefs: `sort_order`, `file_filter`.
- Recorded WAV files on disk + their `_eq.json` / `_nr.wav` sidecars.
- The in-memory `_recordFiles` list (still reflects what's on disk).
- `CustomPresetStore` (user-named EQ presets).

---

## UI

### Placement in `MeatRecSettings.kt`

New section at the bottom of the scrollable body, after AUDIO QUALITY. Matches the existing card style.

```
SECTION HEADER:  "RESET"

┌───────────────────────────────────────────────┐
│ Reset to factory defaults                  →  │
│ Wipes all settings. Recordings & presets safe │
└───────────────────────────────────────────────┘
```

Card: `Color(0xFF161616)` background, `RoundedCornerShape(12.dp)`, 14.dp padding. Title white SemiBold, subtitle white-55%. No orange/red emphasis on the row itself — destructive emphasis lives only on the confirm button inside the dialog.

### Confirm dialog

Material3 `AlertDialog`, themed dark:

```
Reset all settings?

Recordings, custom presets, and your theme
won't be touched.

           [ Cancel ]    [ Reset ]
```

Cancel: neutral text button. Reset: orange (`MeatOrange`) filled button.

### State flow

User taps row → dialog opens → Reset → `viewModel.resetFactory()` → Toast → dialog closes → user stays on the Settings screen and immediately sees defaults reflected (sample-rate pill jumps to 48 kHz, mode row updates) because the UI observes the same `StateFlow`s that `resetFactory()` writes to.

---

## File-level changes

- **`data/SettingsDataStore.kt`** — expand: add ~30 new keys, flows, suspend setters; add `snapshot()` helper returning a `SettingsSnapshot` data class; add `clear()` wrapper. Update the file-top TODO.
- **`RecorderViewModel.kt`** — add `private val settings = SettingsDataStore(application)`, `Defaults` object, `hydrated` flag, `init { hydrate }`, `applyDefaults()`, `rewireRecorderFromState()`, `resetFactory()`. Append write-through to every existing setter. Add debounced flows for `inputGainDb` and `liveEqBandGains`.
- **`ui/MeatRecSettings.kt`** — add RESET section + confirm dialog. New parameter `onResetFactory: () -> Unit` (or just call `viewModel.resetFactory()` directly inside the composable, since the screen already takes `viewModel`).
- **`MainActivity.kt`** — for `setSaveDirectoryUri`, ensure `takePersistableUriPermission` is called on grant (currently the SAF launcher just calls `viewModel.setSaveDirectoryUri(it)` without taking the persistable grant).

No new dependencies — `androidx.datastore:datastore-preferences` is already in the build.

---

## Testing notes

- **Manual:** change every setting, force-stop the app, reopen → all settings restored.
- **Manual:** tap Reset → confirm → every setting visibly resets, recordings still present, theme unchanged, no re-onboarding.
- **Unit:** `SettingsDataStoreTest` — write each key, read back equals what was written; `clear()` removes them all. `RecorderViewModelTest` — `applyDefaults()` produces the declared defaults; `resetFactory()` after mutation returns every observable flow to its default.
- **Edge:** SAF URI grant revoked between sessions — hydration handles the `SecurityException`, save directory is null afterward, recording falls back to app-private storage.

---

## Open questions resolved

- **Theme on reset?** No — preserved (stays in `MODE_PRIVATE` SharedPrefs).
- **Sort/filter on reset?** No — preserved (stays in `meatrec_ui` SharedPrefs).
- **Recordings on reset?** No — never touched.
- **Monitor auto-resume on launch?** No — persist the preference but require explicit user tap to start capture.
