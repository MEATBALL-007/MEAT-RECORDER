# Quick Settings bottom sheet — design

**Date:** 2026-06-08
**Branch:** feat/loudness-delivery
**Problem:** Frequently-used settings (live DSP toggles, quality/format, theme, save/cloud)
live only on the full Settings page. Reaching one is gear-tap → scroll → hunt → toggle.
Too many taps for things the user changes often.

## Goal

Give one-tap access to the most-used settings via a bottom sheet, without removing or
duplicating the full Settings page. Keep the home screen visually unchanged except for one
new icon.

## Non-goals

- No customizable / user-pinned chip strip (considered, rejected as heavier — needs an
  editing flow). The sheet's contents are a fixed curated set.
- No relocation or removal of anything from the existing `SettingsScreenV2`. The full page
  stays the source of truth; the sheet is a fast shortcut to a subset.
- No new persisted state. Every control reflects existing ViewModel / MainActivity state.

## User-facing behavior

### Home top bar
A new "sliders" icon is added immediately left of the existing gear icon in the orange top
bar (`MeatRecHome`):

```
MeatRec PRO            [≡ Quick]  [⚙ gear]
```

- Tapping **Quick** opens the Quick Settings `ModalBottomSheet`.
- Tapping **gear** opens the full Settings page (unchanged).
- The Quick icon is **available during recording too**. Controls that cannot change
  mid-take (Quality preset) are shown **disabled** while `isRecording` is true. Live DSP
  toggles apply immediately whether idle or recording.

### Sheet contents (top → bottom)

1. **Header row** — "QUICK SETTINGS" title + a "Full Settings ›" text button that closes
   the sheet and opens `SettingsScreenV2`.
2. **Live processing** — six toggles in a compact 2-column grid:
   AGC, Hi-pass (rumble cut), Anti-clip, Compressor, Stereo widener, VAD.
3. **Quality** — chip row bound to `RecordingQuality` (`setQuality`). Disabled while
   recording.
4. **Theme** — horizontal swatch row of all 10 `AppTheme` dots; tap applies live and
   persists (same path as Settings).
5. **Storage** — a "Save → <folder>" row that launches the folder picker, and a Cloud
   backup toggle (`toggleCloudBackup`, triggering Drive sign-in when first enabled).

## Architecture

### New component
`ui/components/QuickSettingsSheet.kt` — a stateless `ModalBottomSheet` composable. Takes
the current values + lambdas it needs; owns no business logic. Mirrors the existing sheet
pattern already used in the codebase (`AudioSourcePicker`, `EQPresetPicker`,
`ProUpgradeSheet`, etc. all use `rememberModalBottomSheetState` + `ModalBottomSheet`).

Internal sub-composables reuse the visual language already in `SettingsScreenV2`
(ToggleRow / ChipRow / theme swatch) and `MeatRecHome` (MeatOrange / MeatYellow). To avoid
duplication, the small `ToggleRow` and chip styling are factored so both the sheet and
Settings can share them, OR the sheet defines its own compact variants if sharing would
distort the existing Settings layout. Decision deferred to the plan; prefer a shared
`ui/components` helper if it drops in cleanly.

### State ownership & plumbing

- **DSP toggles, quality, cloud backup** — already on `RecorderViewModel`
  (`agcOn`, `hiPassOn`, `antiClipOn`, `compressorOn`, `stereoWidenerOn`, `vadOn`, `quality`,
  `cloudBackupOn`, `isDriveSignedIn`, and their toggle/set methods). `RecorderApp` already
  holds the `viewModel`, so it can read/collect these directly.
- **Theme (`appTheme`) + its persistence** and **save-location picker** + **Drive sign-in**
  are owned by `MainActivity`. These must be passed down into `RecorderApp`:
  - `theme: AppTheme`
  - `onChangeTheme: (AppTheme) -> Unit`  (already defined inline in MainActivity for Settings)
  - `onSignInDrive: () -> Unit`
  - `onOpenFullSettings: () -> Unit`  (just `settingsOpen = true`; `onOpenSettings` already exists and can be reused)
  - `onSelectSaveLocation` already passed to `RecorderApp`.

### Hosting the sheet

The sheet is hosted inside **`RecorderApp`** (not MainActivity), because RecorderApp already
holds `viewModel` and `onSelectSaveLocation`, and is where `MeatRecHome` lives.

- `RecorderApp` gains a `var quickSettingsOpen by remember { mutableStateOf(false) }`.
- `MeatRecHome` gains one new param `onOpenQuickSettings: () -> Unit` and renders the new
  Quick icon in the top bar that calls it.
- When `quickSettingsOpen`, RecorderApp renders `QuickSettingsSheet(...)`, collecting the
  VM flows and forwarding the MainActivity-owned lambdas (theme, save, cloud, full-settings).

### Data flow (theme change example)

```
QuickSettingsSheet swatch tap
  -> onChangeTheme(theme)         [lambda from RecorderApp param]
  -> MainActivity: appTheme = it; persist to prefs("app_theme")
  -> RecorderProjectTheme recomposes with new AppTheme (animated)
```

DSP toggle example:
```
QuickSettingsSheet toggle
  -> viewModel.toggleAgc()        [direct, RecorderApp already has viewModel]
  -> StateFlow agcOn flips -> sheet recomposes checked state
```

## Error handling / edge cases

- **Recording active**: Quality chips disabled (greyed, non-clickable) when `isRecording`.
  DSP toggles remain enabled. No format mutation mid-take.
- **Cloud backup with no Drive account**: toggling on while `!isDriveSignedIn` calls
  `onSignInDrive()` (same logic already in `SettingsScreenV2`'s cloud row).
- **No save folder yet**: the Save row shows a placeholder ("Default app folder") and the
  picker still works — identical to the existing `SaveLocationRow`.
- **Sheet dismissal**: swipe-down or scrim tap sets `quickSettingsOpen = false`
  (standard `ModalBottomSheet` `onDismissRequest`).

## Testing

- This is presentation wiring over existing, already-tested ViewModel logic; no new business
  logic is introduced, so no new unit tests are strictly required.
- Manual verification checklist (run the app):
  1. Quick icon appears left of gear; opens the sheet.
  2. Each DSP toggle flips VM state and reflects it (open full Settings, confirm match).
  3. Quality chips change format when idle; are disabled while recording.
  4. Theme swatch applies instantly and survives an app restart.
  5. Save row opens the folder picker; chosen folder reflected.
  6. Cloud toggle prompts Drive sign-in when first enabled.
  7. "Full Settings ›" closes the sheet and opens the full page.
  8. Sheet opens and functions while a recording is in progress.

## Files touched

- **New:** `app/src/main/java/com/example/recorderproject/ui/components/QuickSettingsSheet.kt`
- **Edit:** `ui/MeatRecHome.kt` — add Quick icon + `onOpenQuickSettings` param.
- **Edit:** `ui/RecorderApp.kt` — host sheet state, new params, render `QuickSettingsSheet`.
- **Edit:** `MainActivity.kt` — pass `theme`, `onChangeTheme`, `onSignInDrive`,
  `onOpenFullSettings` into `RecorderApp`.
- Possibly **Edit:** a shared `ui/components` toggle/chip helper if extraction is clean.
