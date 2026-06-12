# Customizable Workspace & Pro Markers — Design

**Date:** 2026-06-12
**App:** MEATrec (Android, Kotlin / Jetpack Compose)
**Status:** Approved design — ready for implementation planning

## Summary

Two related features for the Record screen:

1. **Pro marker system** — a consistent, always-visible "★ PRO" badge on every
   pro-gated control, so free users can see at a glance what requires an upgrade.
   Today gating is enforced only *on tap* (a paywall appears) with no visual cue.
2. **Customizable workspace (A + C)** — let users show/hide and reorder the
   in-recording quick controls, with the resulting layout saved per
   `RecorderMode`.

The Pro marker (Feature 2) is built **first** because the Customize editor
(Feature 1) reuses it.

Both features build on existing infrastructure:
- `EntitlementStore.isPro` (StateFlow, SharedPreferences-backed) →
  `RecorderViewModel.isPro`.
- `RecorderViewModel.requirePro(feature: ProFeature)` and `openPaywall(feature)`.
- `ProFeature` enum and the `ProUpgradeSheet` paywall (feature → initial slide).
- `RecorderMode` enum (FILM / INTERVIEW / MUSIC / AMBIENCE / CUSTOM).
- `SettingsDataStore` (wipe-safe DataStore) for preference persistence.

## Decisions (from brainstorming)

| Decision | Choice |
| --- | --- |
| Workspace direction | **A + C**: show/hide + reorder editor, layouts saved per mode |
| Workspace gating | **Free, but Pro unlocks more** (see Free vs Pro below) |
| Customize scope | **In-recording quick controls only** — Settings card, record button, meters, gain, take/scene bumpers stay fixed |
| Pro marker style | **"★ PRO" pill** (reuses the existing top-bar badge), on **every** pro-only control |

---

## Feature 2 — Pro marker system

### Components

- **`ProBadge` composable** (`ui/components/ProBadge.kt`, new): the "★ PRO" yellow
  pill. Extracted from the inline pill currently at `MeatRecHome.kt:266-284` so
  the top bar and the controls share a single definition.
- **`Modifier.proLocked(feature, isPro, onUpgrade)`** (or a small `ProLockable`
  wrapper composable): when `!isPro`, overlays/append the `ProBadge` and routes
  taps to `onUpgrade(feature)` (→ `openPaywall`) instead of the control's own
  action. When `isPro`, it is a no-op and the control behaves normally.
- **Control → `ProFeature` registry:** a single mapping table so each control
  knows its feature, and the paywall opens to the correct slide.

### Controls that receive the badge (free users)

| Control | ProFeature |
| --- | --- |
| Live EQ, NR Gate, Edit EQ | DSP / EQ feature(s) |
| VAD, Pre-roll, Slate tone | `PRE_ROLL_VAD` |
| Loudness target chip + loudness meter | `LOUDNESS_DELIVERY` |
| 96 kHz sample rate, 24-bit / 32-float depth | `HIGH_RES_AUDIO` |
| External mic selection | `EXTERNAL_MIC` |
| Room profile + analysis tools | `ANALYSIS_TOOLS` |
| Cloud backup | `CLOUD_BACKUP` |
| Pitch shift | `PITCH_SHIFT` |
| Transcription | `TRANSCRIPTION` |
| Extra themes (beyond free 3) | `ALL_THEMES` |

(Exact `ProFeature` names to be confirmed against the enum during planning.)

### Behavior

- Badge shows for free users only; disappears and the control goes live after
  upgrade.
- Tap still opens the existing paywall via `openPaywall(feature)`.
- No change to `requirePro()` logic — this layer only makes the existing gate
  *visible*.

---

## Feature 1 — Customizable workspace (A + C)

### Model

- **`QuickControl` registry:** a stable enum of the customizable in-recording
  controls — `id`, `label`, `icon`, optional `proFeature`. Defines the universe
  of controls that can be shown/hidden/reordered.
- **`WorkspaceLayout`:** an ordered list of control ids, each with a `visible`
  flag.

### Customize editor

- New sheet/screen, reached from a **"✎ Customize layout"** button on the Record
  page.
- A drag-to-reorder list with a visibility toggle per row.
- Pro controls render with the Feature 2 badge here too. A free user **can**
  place a pro control in their layout — it simply shows gated on the record
  screen.
- **Reset to default** (per mode) — free.

### Render

- `RecordPage` reads the active layout and renders the quick controls in that
  order, omitting hidden ones.
- Not customizable (per scope decision): the Recording Settings card, live
  meters, input gain dial, take/scene bumpers, and the record button.

---

## Free vs Pro split

Maps cleanly onto A + C:

- **Free → the "A" part:** full show/hide + reorder, but **one shared layout**
  across all modes.
- **Pro → the "C" part:** **independent per-mode layouts** (Film / Interview /
  Music / Ambience each remember their own). Gated by a **new
  `ProFeature.CUSTOM_WORKSPACE`** with its own paywall slide.

Free users get real customization; the per-mode behavior is the upsell.

---

## Persistence

- Layouts stored in `SettingsDataStore` (wipe-safe DataStore):
  - one key for the free **global** layout, and
  - a **per-mode map** for Pro.
- Serialized as JSON (ids + visibility + order), **versioned** so that when the
  `QuickControl` registry gains a control later, existing layouts merge it in at
  a sane default position rather than dropping or breaking.
- The control → `ProFeature` map and the `QuickControl` registry live in code,
  not in storage.

---

## Testing

- **Unit:** layout serialize/deserialize; default-merge when the registry gains
  a control; `proLocked` gate routing (free → paywall, pro → action).
- **Manual (emulator):** drag/reorder, show/hide, per-mode layout swap, badge
  visibility toggling with entitlement state.

## Out of scope (YAGNI)

- Customizing the Settings card rows.
- Dragging meters / record button / gain.
- Cloud-syncing or sharing layouts between users/devices.
- Unlimited named layouts, import/export (possible future Pro extras).
- Animations beyond basic drag feedback.

## Build order

1. Feature 2 — `ProBadge`, `proLocked`, control→`ProFeature` registry; apply to
   all pro controls.
2. Feature 1 — `QuickControl` registry, `WorkspaceLayout`, persistence, Customize
   editor, `RecordPage` render, free/Pro split + `CUSTOM_WORKSPACE` paywall slide.
