# SAF folder as default save location (first-record onboarding)

**Date:** 2026-06-08
**Issue:** #10 — Private App Storage is sandboxed: recordings are invisible to the user in file managers and lost on uninstall.
**Status:** Approved design, pending implementation plan.

## Problem

New recordings default to `getExternalFilesDir(DIRECTORY_MUSIC)/Recordings/` (see `AudioRecorderManager`). That location is app-private scoped storage: it does not appear in the system Files app or other file managers, and the OS deletes it when the app is uninstalled. Users can lose every recording on an uninstall without warning, and cannot find their files outside the app.

The app already has a Storage Access Framework (SAF) path — folder picker, persistable URI grant, recording-to-folder, dual library scan, and (as of issue #11) full EQ/NR/loudness support on `content://` recordings. SAF folders are user-visible and survive uninstall. They are simply not the default, and nothing guides the user to choose one.

## Goal

Guide the user to pick a user-visible, uninstall-safe SAF folder the first time they record, while preserving the ability to keep using app storage. Do not disrupt existing recordings or auto-capture paths (VAD, scheduled start).

## Decisions

- **Mechanism:** SAF folder (reuse the existing picker / grant / scan / DSP stack). Not MediaStore (net-new), not app-storage-plus-export (doesn't fix the core problem).
- **Onboarding trigger:** first **record-button tap** with no folder chosen — ties the ask to the moment it matters. Not a first-launch screen, not a passive banner.
- **Escape hatch:** the prompt offers "Use app storage"; app storage remains a fully supported choice.
- **Existing files:** leave app-storage recordings where they are and keep listing them (the dual scan already does this). No bulk migration.

## Design

### 1. The gate

Add a save-location gate at the top of `MainActivity.requestRecordingPermissions()`, mirroring the existing `location_disclosure_shown` gate immediately below it:

```
if (!prefs.getBoolean("save_location_prompted", false) && viewModel.saveDirectoryUri.value == null) {
    showSaveLocationDialog()
    return
}
// existing: location-disclosure gate, then proceedWithRecordingPermissions()
```

`requestRecordingPermissions()` is reached **only** from the user-initiated record button (`onStartRecording = { requestRecordingPermissions() }`). VAD and scheduled auto-start call `viewModel.startRecording()` directly and bypass this method, so automated capture never triggers the onboarding dialog.

The `save_location_prompted` flag lives in the same `SharedPreferences` (`MODE_PRIVATE`) as `onboarding_done` and `location_disclosure_shown`, for consistency. The chosen folder URI itself continues to live in DataStore (`save_directory_uri`) via the ViewModel; this flag only records that the user has been asked once.

### 2. The dialog

`showSaveLocationDialog()` mirrors the structure of `showLocationDisclosureDialog()` (an `androidx.appcompat.app.AlertDialog`):

- **Title:** "Where should recordings be saved?"
- **Message:** "Pick a folder you'll find in your Files app and that stays even if you uninstall MEAT REC — or keep using private app storage."
- **Positive — "Choose folder":** set `save_location_prompted = true`; set `pendingRecordAfterFolderPick = true`; launch the existing `directoryLauncher`, pre-seeded to the Music directory via `EXTRA_INITIAL_URI` (best-effort; ignored on some OEMs).
- **Negative — "Use app storage":** set `save_location_prompted = true`; re-enter `requestRecordingPermissions()` (the gate now passes, flowing to the location-disclosure gate and then recording).

### 3. Continue-after-pick

The `directoryLauncher` callback already calls `viewModel.setSaveDirectoryUri(uri)` (which takes the persistable grant). Extend it:

```
uri?.let { viewModel.setSaveDirectoryUri(it) }
if (pendingRecordAfterFolderPick) {
    pendingRecordAfterFolderPick = false
    if (uri != null) requestRecordingPermissions()   // re-enter; gate now passes -> records into the folder
}
```

If the user cancels the picker (`uri == null`), we do not auto-record. The prompted flag is already set, so the next record tap flows straight to the app-storage path.

`pendingRecordAfterFolderPick` is a plain `MainActivity` field (the picker and the record flow are both Activity-scoped).

### 4. Library listing

No change. `scanRecordingsFromDisk()` and `scanSafRecordings()` already run together, so app-storage and SAF recordings are both listed. "Leave them, list both" is the current behavior.

### 5. Pure logic extracted for tests

The gate rule is the only non-Android-glue logic. Extract it:

```kotlin
object SaveLocationOnboarding {
    /** Prompt for a save folder only when none is chosen and the user hasn't been asked yet. */
    fun shouldPrompt(hasFolder: Boolean, alreadyPrompted: Boolean): Boolean =
        !hasFolder && !alreadyPrompted
}
```

`MainActivity` calls `SaveLocationOnboarding.shouldPrompt(viewModel.saveDirectoryUri.value != null, prefs.getBoolean("save_location_prompted", false))`.

## Testing

- **Unit (JVM, TDD):** `SaveLocationOnboarding.shouldPrompt` — prompt when no folder and not prompted; suppress when a folder exists; suppress when already prompted. (~3 tests.)
- **Not unit-testable here (no Robolectric):** the dialog, the `ActivityResult` launcher round-trip, `SharedPreferences`, and the continue-after-pick coordination. Verified by compilation and a device walkthrough.

### Device walkthrough (acceptance)

1. Fresh install → tap Record → save-location dialog appears.
2. "Choose folder" → pick a folder → recording proceeds and the file lands in that folder (visible in Files).
3. Fresh install → tap Record → "Use app storage" → records to app storage; dialog never reappears.
4. After either choice, subsequent record taps do not show the dialog.
5. VAD / scheduled start with no folder chosen records without showing the dialog (no interruption of automated capture).
6. Existing app-storage recordings remain listed after a folder is chosen.

## Out of scope

- Rewording the "No save folder — tap to set" banner — issue #9.
- Persisting notes/tags/scene across reinstalls (metadata store) — separate concern.
- Bulk migration of existing app-storage recordings into the chosen folder — explicitly declined ("leave them, list both").
