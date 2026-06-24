# SAF Default Save Location (First-Record Onboarding) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prompt the user to pick a user-visible, uninstall-safe SAF folder the first time they tap Record, with an "app storage" escape hatch, without disrupting existing recordings or automated capture.

**Architecture:** A pure gate rule (`SaveLocationOnboarding.shouldPrompt`) decides whether to interrupt the record flow. `MainActivity.requestRecordingPermissions()` — reached only from the user-initiated record button — consults it and, if needed, shows an `AlertDialog` that either launches the existing SAF folder picker or marks app storage as chosen. After a folder pick, the record flow resumes automatically. VAD and scheduled auto-start call `viewModel.startRecording()` directly and are unaffected.

**Tech Stack:** Kotlin, Android (`androidx.appcompat.app.AlertDialog`, `ActivityResultContracts.OpenDocumentTree`, `SharedPreferences`, `DocumentsContract`), JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-08-saf-default-storage-design.md`

---

## File Structure

- **Create** `app/src/main/java/com/example/recorderproject/SaveLocationOnboarding.kt` — the pure gate rule (root package so `MainActivity` needs no import). Single responsibility: decide whether to prompt.
- **Create** `app/src/test/java/com/example/recorderproject/SaveLocationOnboardingTest.kt` — JVM unit tests for the rule.
- **Modify** `app/src/main/java/com/example/recorderproject/MainActivity.kt` — gate in `requestRecordingPermissions()` (line ~323), new `showSaveLocationDialog()`, `pendingRecordAfterFolderPick` field, `directoryLauncher` continue-after-pick (line ~65), Music pre-seed in `selectSaveDirectory()` (line ~390).

---

## Task 1: SaveLocationOnboarding gate rule (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/SaveLocationOnboarding.kt`
- Test: `app/src/test/java/com/example/recorderproject/SaveLocationOnboardingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/recorderproject/SaveLocationOnboardingTest.kt`:

```kotlin
package com.example.recorderproject

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveLocationOnboardingTest {

    @Test fun `prompts when no folder chosen and not yet asked`() {
        assertTrue(SaveLocationOnboarding.shouldPrompt(hasFolder = false, alreadyPrompted = false))
    }

    @Test fun `does not prompt once a folder is chosen`() {
        assertFalse(SaveLocationOnboarding.shouldPrompt(hasFolder = true, alreadyPrompted = false))
    }

    @Test fun `does not prompt again after the user was already asked`() {
        assertFalse(SaveLocationOnboarding.shouldPrompt(hasFolder = false, alreadyPrompted = true))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.SaveLocationOnboardingTest"`
Expected: FAIL — `Unresolved reference 'SaveLocationOnboarding'`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/example/recorderproject/SaveLocationOnboarding.kt`:

```kotlin
package com.example.recorderproject

/**
 * Decides whether to interrupt the record flow to ask the user where recordings
 * should be saved. Kept free of Android types so it is unit-testable on the JVM.
 */
object SaveLocationOnboarding {
    /** Prompt for a save folder only when none is chosen and the user hasn't been asked yet. */
    fun shouldPrompt(hasFolder: Boolean, alreadyPrompted: Boolean): Boolean =
        !hasFolder && !alreadyPrompted
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.SaveLocationOnboardingTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/SaveLocationOnboarding.kt \
        app/src/test/java/com/example/recorderproject/SaveLocationOnboardingTest.kt
git commit -m "feat: SaveLocationOnboarding gate rule for first-record save-folder prompt (#10)"
```

---

## Task 2: Wire the gate, dialog, and continue-after-pick into MainActivity

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt:65-69` (directoryLauncher)
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt:323-330` (requestRecordingPermissions)
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt:390-392` (selectSaveDirectory)

This task is Android glue (Activity, AlertDialog, ActivityResult launcher, SharedPreferences) that cannot run in JVM unit tests here (no Robolectric). It is verified by compilation and the device walkthrough at the end.

- [ ] **Step 1: Add the `pendingRecordAfterFolderPick` field**

In `MainActivity`, just above the `directoryLauncher` declaration (line ~65), add:

```kotlin
    // True while we're waiting for the folder-picker result mid-onboarding, so the record
    // flow can resume automatically once a folder is chosen.
    private var pendingRecordAfterFolderPick = false
```

- [ ] **Step 2: Resume recording after a folder pick**

Replace the existing `directoryLauncher` (lines ~65-69):

```kotlin
    private val directoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.setSaveDirectoryUri(it) }
    }
```

with:

```kotlin
    private val directoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.setSaveDirectoryUri(it) }
        if (pendingRecordAfterFolderPick) {
            pendingRecordAfterFolderPick = false
            // Re-enter the record flow; the gate now passes (a folder is set) and we
            // fall through to permissions + recording. If the user cancelled the picker
            // (uri == null) we do nothing — they tap Record again, now bound for app storage.
            if (uri != null) requestRecordingPermissions()
        }
    }
```

- [ ] **Step 3: Add the save-location gate to `requestRecordingPermissions()`**

Replace `requestRecordingPermissions()` (lines ~323-330):

```kotlin
    private fun requestRecordingPermissions() {
        val prefs = getPreferences(MODE_PRIVATE)
        if (!prefs.getBoolean("location_disclosure_shown", false)) {
            showLocationDisclosureDialog()
            return
        }
        proceedWithRecordingPermissions()
    }
```

with:

```kotlin
    private fun requestRecordingPermissions() {
        val prefs = getPreferences(MODE_PRIVATE)
        if (SaveLocationOnboarding.shouldPrompt(
                hasFolder = viewModel.saveDirectoryUri.value != null,
                alreadyPrompted = prefs.getBoolean("save_location_prompted", false),
            )
        ) {
            showSaveLocationDialog()
            return
        }
        if (!prefs.getBoolean("location_disclosure_shown", false)) {
            showLocationDisclosureDialog()
            return
        }
        proceedWithRecordingPermissions()
    }

    private fun showSaveLocationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Where should recordings be saved?")
            .setMessage(
                "Pick a folder you'll find in your Files app and that stays even if you " +
                "uninstall MEAT REC — or keep using private app storage."
            )
            .setCancelable(false)
            .setPositiveButton("Choose folder") { _, _ ->
                getPreferences(MODE_PRIVATE).edit()
                    .putBoolean("save_location_prompted", true).apply()
                pendingRecordAfterFolderPick = true
                selectSaveDirectory()
            }
            .setNegativeButton("Use app storage") { _, _ ->
                getPreferences(MODE_PRIVATE).edit()
                    .putBoolean("save_location_prompted", true).apply()
                // Gate now passes; re-enter to continue to the location-disclosure / record flow.
                requestRecordingPermissions()
            }
            .show()
    }
```

- [ ] **Step 4: Pre-seed the picker to the Music folder**

Replace `selectSaveDirectory()` (lines ~390-392):

```kotlin
    private fun selectSaveDirectory() {
        directoryLauncher.launch(null)
    }
```

with:

```kotlin
    private fun selectSaveDirectory() {
        // Best-effort initial location hint (ignored by some OEM pickers).
        val initial: android.net.Uri? = try {
            android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:Music"
            )
        } catch (_: Exception) { null }
        directoryLauncher.launch(initial)
    }
```

- [ ] **Step 5: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all unit tests pass (no regressions; the new 3 from Task 1 included).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/MainActivity.kt
git commit -m "feat: first-record SAF folder onboarding with app-storage fallback (#10)"
```

---

## Device Walkthrough (manual acceptance — run after Task 2)

Install on a device/emulator and verify:

1. Fresh install → tap Record → the save-location dialog appears.
2. "Choose folder" → pick a folder → recording proceeds; the file appears in that folder in the Files app.
3. Fresh install (clear app data) → tap Record → "Use app storage" → records to app storage; dialog does not reappear on later taps.
4. After either choice, subsequent record taps show no dialog.
5. With no folder chosen, trigger VAD or a scheduled start → recording begins with no dialog (automated capture uninterrupted).
6. After choosing a folder, existing app-storage recordings are still listed in the library.
7. Tap "Choose folder" then back out of the picker → no recording starts; tapping Record again goes straight to app storage.

---

## Self-Review

**Spec coverage:**
- Gate in record-only path → Task 2 Step 3 (and `SaveLocationOnboarding` rule, Task 1). ✓
- Dialog with "Choose folder" / "Use app storage" → Task 2 Step 3 (`showSaveLocationDialog`). ✓
- `save_location_prompted` in `MODE_PRIVATE` SharedPreferences → Task 2 Step 3 (both buttons). ✓
- Continue-after-pick via `pendingRecordAfterFolderPick` → Task 2 Steps 1-2. ✓
- Picker cancel → no auto-record → Task 2 Step 2. ✓
- Music pre-seed (best-effort) → Task 2 Step 4. ✓
- Library lists both (no change) → no task needed; existing dual scan. ✓
- VAD/scheduled unaffected → guaranteed by gating in `requestRecordingPermissions()` only; covered by walkthrough #5. ✓
- Pure rule TDD → Task 1. ✓

**Placeholder scan:** none — all steps contain complete code and exact commands.

**Type consistency:** `SaveLocationOnboarding.shouldPrompt(hasFolder, alreadyPrompted)` defined in Task 1 matches the call in Task 2 Step 3. `pendingRecordAfterFolderPick` declared in Step 1, set in Step 3, consumed in Step 2. `selectSaveDirectory()` modified in Step 4 and called in Step 3 — consistent signature (no args).
