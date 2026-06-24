# Quick Settings Bottom Sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-tap "Quick Settings" bottom sheet, opened from a new sliders icon in the home top bar, surfacing the most-used controls (live DSP toggles, quality, theme, save/cloud) without touching the full Settings page.

**Architecture:** A new stateless `QuickSettingsSheet` `ModalBottomSheet` takes plain values + lambdas. `MeatRecHome` gains a sliders icon that opens it. `RecorderApp` hosts the sheet's open/close state, collects the relevant `RecorderViewModel` flows, and forwards `MainActivity`-owned lambdas (theme, save-location, Drive sign-in, full-settings). No new persisted state and no business logic — every control re-presents existing, already-tested state.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 (`ModalBottomSheet`), existing custom Canvas icon library (`IconLine.kt`).

---

## Working location & build notes

- This project has **two git-synced copies**: `/Users/meatball_mac/RECORDER_PROJECT` (session default) and `/Volumes/Meatball/RECORDER_PROJECT` (exFAT external). Do all implementation in **one** copy. After it's done, sync the other via `git fetch <other-path> feat/loudness-delivery` + cherry-pick/reset — **never** a blind file copy (it would clobber each machine's `local.properties` and could undo commits). See `docs/CROSS_PLATFORM_DRIVE.md`.
- Build commands below use `./gradlew`. From the external drive the build output auto-redirects to `~/.meatrec-build/` (APK at `~/.meatrec-build/app/outputs/apk/debug/app-debug.apk`). Both copies build identically; `assembleDebug` currently completes in ~7s incremental.
- These are UI-only changes with no new logic, so verification is **compile + manual checklist**, not unit tests (matches the spec's "no new business logic" note).

## File structure

- **Create:** `app/src/main/java/com/example/recorderproject/ui/components/QuickSettingsSheet.kt` — the stateless sheet + its private compact sub-composables (toggle pill, chip row, theme swatch row).
- **Modify:** `app/src/main/java/com/example/recorderproject/ui/components/IconLine.kt` — add `IconLineSliders`.
- **Modify:** `app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt` — add `onOpenQuickSettings` param + render the sliders icon in the top bar.
- **Modify:** `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt` — host sheet state, new params, collect flows, render `QuickSettingsSheet`.
- **Modify:** `app/src/main/java/com/example/recorderproject/MainActivity.kt` — pass `theme`, `onChangeTheme`, `onSignInDrive`, `onOpenFullSettings` into `RecorderApp`.

---

### Task 1: Add the `IconLineSliders` custom icon

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/components/IconLine.kt` (insert after `IconLineSettings`, before `IconLineMenu` at line ~187)

- [ ] **Step 1: Add the icon composable**

Insert this after the `IconLineSettings` function (it closes at line 186). It matches the file's conventions (square Canvas, `StrokeBase * density`, round caps) — three horizontal rails with a knob on each (the classic "tune/sliders" glyph):

```kotlin
@Composable
fun IconLineSliders(tint: Color, size: Dp = DefaultSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = StrokeBase * density * 1.2f
        val x0 = w * 0.18f; val x1 = w * 0.82f
        val rows = listOf(h * 0.28f to w * 0.62f, h * 0.50f to w * 0.38f, h * 0.72f to w * 0.70f)
        val knobR = w * 0.07f
        for ((y, knobX) in rows) {
            drawLine(tint, Offset(x0, y), Offset(x1, y), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(tint, knobR, Offset(knobX, y))
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/IconLine.kt
git commit -m "feat: add IconLineSliders icon for Quick Settings"
```

---

### Task 2: Create the stateless `QuickSettingsSheet` composable

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/QuickSettingsSheet.kt`

- [ ] **Step 1: Write the full file**

The sheet is stateless: it takes plain values + lambdas (no ViewModel), so it stays self-contained and previewable. Private helpers (`QuickToggle`, `QualityChipRow`, `ThemeSwatchRow`, `StorageRows`) keep the change isolated to this file rather than disturbing `SettingsScreenV2`'s private composables.

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordingQuality
import com.example.recorderproject.ui.theme.AppTheme
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Quick Settings bottom sheet — one-tap access to the most-used settings without
 * opening the full Settings page. Stateless: every value/lambda is supplied by the
 * caller (RecorderApp). Mirrors the app's other ModalBottomSheets (AudioSourcePicker,
 * EQPresetPicker). The full Settings page stays the source of truth; "Full Settings ›"
 * jumps there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsSheet(
    agcOn: Boolean,
    onToggleAgc: () -> Unit,
    hiPassOn: Boolean,
    onToggleHiPass: () -> Unit,
    antiClipOn: Boolean,
    onToggleAntiClip: () -> Unit,
    compressorOn: Boolean,
    onToggleCompressor: () -> Unit,
    stereoWidenerOn: Boolean,
    onToggleStereoWidener: () -> Unit,
    vadOn: Boolean,
    onToggleVad: () -> Unit,
    quality: RecordingQuality,
    onChangeQuality: (RecordingQuality) -> Unit,
    isRecording: Boolean,
    currentTheme: AppTheme,
    onChangeTheme: (AppTheme) -> Unit,
    saveLocationLabel: String,
    onPickSaveLocation: () -> Unit,
    cloudBackupOn: Boolean,
    onToggleCloudBackup: () -> Unit,
    onOpenFullSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RecorderCharcoal,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "QUICK SETTINGS",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    "Full Settings ›",
                    color = RecorderOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenFullSettings)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            SheetSectionLabel("LIVE PROCESSING")
            // 6 toggles in a 2-column grid
            val toggles = listOf(
                Triple("AGC", agcOn, onToggleAgc),
                Triple("Hi-pass", hiPassOn, onToggleHiPass),
                Triple("Anti-clip", antiClipOn, onToggleAntiClip),
                Triple("Compressor", compressorOn, onToggleCompressor),
                Triple("Widener", stereoWidenerOn, onToggleStereoWidener),
                Triple("VAD", vadOn, onToggleVad),
            )
            toggles.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { (label, value, onToggle) ->
                        QuickToggle(label = label, value = value, onToggle = onToggle, modifier = Modifier.weight(1f))
                    }
                    if (rowItems.size == 1) Box(Modifier.weight(1f))
                }
            }

            SheetSectionLabel("QUALITY")
            QualityChipRow(current = quality, enabled = !isRecording, onChange = onChangeQuality)
            if (isRecording) {
                Text(
                    "Quality can't change while recording",
                    color = RecorderBlueGrey,
                    fontSize = 11.sp,
                )
            }

            SheetSectionLabel("THEME")
            ThemeSwatchRow(current = currentTheme, onChange = onChangeTheme)

            SheetSectionLabel("STORAGE")
            StorageRows(
                saveLocationLabel = saveLocationLabel,
                onPickSaveLocation = onPickSaveLocation,
                cloudBackupOn = cloudBackupOn,
                onToggleCloudBackup = onToggleCloudBackup,
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(RecorderYellow),
        )
        Text(text, color = Color.White, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickToggle(label: String, value: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Switch(
            checked = value,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RecorderOrange,
                uncheckedThumbColor = RecorderBlueGrey,
                uncheckedTrackColor = Color(0xFF0C0C10),
            ),
        )
    }
}

@Composable
private fun QualityChipRow(current: RecordingQuality, enabled: Boolean, onChange: (RecordingQuality) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0C0C10))
            .padding(3.dp)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RecordingQuality.entries.forEach { q ->
            val active = q == current
            Text(
                q.displayName,
                color = if (active) Color.White else RecorderBlueGrey,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) RecorderOrange else Color.Transparent)
                    .then(if (enabled) Modifier.clickable { onChange(q) } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ThemeSwatchRow(current: AppTheme, onChange: (AppTheme) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTheme.entries.forEach { t ->
            val active = t == current
            Box(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) RecorderOrange.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onChange(t) }
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                ColorDot(
                    background = t.background,
                    surface = t.surface,
                    surfaceElevated = t.surfaceElevated,
                    size = 34.dp,
                )
            }
        }
    }
}

@Composable
private fun StorageRows(
    saveLocationLabel: String,
    onPickSaveLocation: () -> Unit,
    cloudBackupOn: Boolean,
    onToggleCloudBackup: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RecorderCharcoalCard)
                .clickable(onClick = onPickSaveLocation)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Save location", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(saveLocationLabel, color = RecorderBlueGrey, fontSize = 11.sp)
            }
            Text("Change", color = RecorderOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RecorderCharcoalCard)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Cloud backup", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (cloudBackupOn) "Backing up to Google Drive" else "Tap to back up to Google Drive",
                    color = RecorderBlueGrey,
                    fontSize = 11.sp,
                )
            }
            Switch(
                checked = cloudBackupOn,
                onCheckedChange = { onToggleCloudBackup() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = RecorderOrange,
                    uncheckedThumbColor = RecorderBlueGrey,
                    uncheckedTrackColor = Color(0xFF0C0C10),
                ),
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. (If `RecorderCharcoal` is unresolved, confirm it exists in `ui/theme/` — it is imported by `SettingsScreenV2.kt`.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/QuickSettingsSheet.kt
git commit -m "feat: add stateless QuickSettingsSheet composable"
```

---

### Task 3: Add the sliders icon to the home top bar

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt` (param list ~line 100; top bar gear at lines 290-297)

- [ ] **Step 1: Add the `onOpenQuickSettings` parameter**

In the `MeatRecHome(...)` signature, immediately after the existing `onOpenSettings: () -> Unit,` line (line 100), add:

```kotlin
    onOpenQuickSettings: () -> Unit = {},
```

(Default `{}` keeps existing call sites compiling until Task 4 wires it.)

- [ ] **Step 2: Render the sliders icon left of the gear**

Replace the existing gear `Box` (lines 290-297):

```kotlin
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                IconLineSettings(tint = Color.White, size = 26.dp)
            }
```

with a Row holding the new Quick icon then the gear:

```kotlin
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onOpenQuickSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    com.example.recorderproject.ui.components.IconLineSliders(tint = Color.White, size = 24.dp)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    IconLineSettings(tint = Color.White, size = 26.dp)
                }
            }
```

(`IconLineSettings` is already imported via `com.example.recorderproject.ui.components.IconLineSettings`; the fully-qualified `IconLineSliders` reference avoids touching the import block.)

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt
git commit -m "feat: add Quick Settings sliders icon to home top bar"
```

---

### Task 4: Host the sheet in `RecorderApp` and wire it to the ViewModel

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt` (signature lines 88-96; `MeatRecHome(...)` call; end of composable before the trailing dialogs)

- [ ] **Step 1: Add new params to `RecorderApp` (with defaults so MainActivity still compiles)**

Change the signature (lines 88-96) to add four params. The new full signature:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderApp(
    viewModel: RecorderViewModel,
    onStartRecording: () -> Unit,
    onSelectSaveLocation: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenEQOnLast: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenPresets: () -> Unit = {},
    theme: com.example.recorderproject.ui.theme.AppTheme = com.example.recorderproject.ui.theme.AppTheme.Default,
    onChangeTheme: (com.example.recorderproject.ui.theme.AppTheme) -> Unit = {},
    onSignInDrive: () -> Unit = {},
    onOpenFullSettings: () -> Unit = {},
) {
```

- [ ] **Step 2: Add the sheet open-state and collect the sheet's flows**

Right after the existing `val channelCount by viewModel.channelCount.collectAsStateWithLifecycle()` line (line 161), add:

```kotlin
    var quickSettingsOpen by remember { mutableStateOf(false) }
    val agcOn by viewModel.agcOn.collectAsStateWithLifecycle()
    val hiPassOn by viewModel.hiPassOn.collectAsStateWithLifecycle()
    val antiClipOn by viewModel.antiClipOn.collectAsStateWithLifecycle()
    val compressorOn by viewModel.compressorOn.collectAsStateWithLifecycle()
    val stereoWidenerOn by viewModel.stereoWidenerOn.collectAsStateWithLifecycle()
    val vadOnQs by viewModel.vadOn.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val cloudBackupOn by viewModel.cloudBackupOn.collectAsStateWithLifecycle()
    val isDriveSignedIn by viewModel.isDriveSignedIn.collectAsStateWithLifecycle()
```

(`vadOnQs` avoids colliding with the `vadOn` already passed inline into `MeatRecHome` at line 243; that inline call reads the flow directly, so there is no existing local named `vadOn`. The distinct name is defensive and self-documenting.)

- [ ] **Step 3: Pass `onOpenQuickSettings` into `MeatRecHome`**

In the `MeatRecHome(...)` call, immediately after `onOpenSettings = onOpenSettings,` (line 184), add:

```kotlin
        onOpenQuickSettings = { quickSettingsOpen = true },
```

- [ ] **Step 4: Render the sheet**

Immediately after the closing `)` of the `MeatRecHome(...)` call (line 267, before the `// L2: Bottom mini player` block), add:

```kotlin
    if (quickSettingsOpen) {
        com.example.recorderproject.ui.components.QuickSettingsSheet(
            agcOn = agcOn,
            onToggleAgc = { viewModel.toggleAgc() },
            hiPassOn = hiPassOn,
            onToggleHiPass = { viewModel.toggleHiPass() },
            antiClipOn = antiClipOn,
            onToggleAntiClip = { viewModel.toggleAntiClip() },
            compressorOn = compressorOn,
            onToggleCompressor = { viewModel.toggleCompressor() },
            stereoWidenerOn = stereoWidenerOn,
            onToggleStereoWidener = { viewModel.toggleStereoWidener() },
            vadOn = vadOnQs,
            onToggleVad = { viewModel.toggleVad() },
            quality = quality,
            onChangeQuality = { viewModel.setQuality(it) },
            isRecording = isRecording,
            currentTheme = theme,
            onChangeTheme = onChangeTheme,
            saveLocationLabel = saveDirectoryUri?.toString() ?: "Default app folder",
            onPickSaveLocation = onSelectSaveLocation,
            cloudBackupOn = cloudBackupOn,
            onToggleCloudBackup = {
                viewModel.toggleCloudBackup()
                if (!cloudBackupOn && !isDriveSignedIn) onSignInDrive()
            },
            onOpenFullSettings = { quickSettingsOpen = false; onOpenFullSettings() },
            onDismiss = { quickSettingsOpen = false },
        )
    }
```

(`saveDirectoryUri` and `isRecording` are already collected near the top of `RecorderApp`; the cloud-toggle's sign-in trigger mirrors the logic already in `SettingsScreenV2.kt:237-240`.)

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/RecorderApp.kt
git commit -m "feat: host QuickSettingsSheet in RecorderApp, wired to ViewModel"
```

---

### Task 5: Pass theme / save / cloud / full-settings lambdas from MainActivity

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt` (the `else -> RecorderApp(...)` call, lines 268-287)

- [ ] **Step 1: Add the four arguments to the `RecorderApp(...)` call**

In the `else -> RecorderApp(` block, after the existing `onOpenPresets = { ... },` argument (ends line 286), add:

```kotlin
                            theme = appTheme,
                            onChangeTheme = {
                                appTheme = it
                                getPreferences(MODE_PRIVATE).edit()
                                    .putString("app_theme", it.displayName).apply()
                            },
                            onSignInDrive = { signInToGoogleDrive() },
                            onOpenFullSettings = { settingsOpen = true },
```

(`appTheme` is the `var` declared at MainActivity.kt:115; the `onChangeTheme` body is identical to the one already used for `SettingsScreenV2` at lines 185-189, so theme changes from the sheet persist the same way. `signInToGoogleDrive()` is the same method `SettingsScreenV2` uses.)

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/MainActivity.kt
git commit -m "feat: wire MainActivity theme/save/cloud lambdas into Quick Settings"
```

---

### Task 6: Full build + manual verification

**Files:** none (verification only)

- [ ] **Step 1: Full debug build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`; APK at `~/.meatrec-build/app/outputs/apk/debug/app-debug.apk` (external drive) or `app/build/outputs/...` (internal disk).

- [ ] **Step 2: Manual checklist (install on device/emulator, open the app)**

Verify each:
1. A sliders icon appears immediately left of the gear in the orange top bar; tapping it opens the bottom sheet.
2. Each of the 6 DSP toggles (AGC, Hi-pass, Anti-clip, Compressor, Widener, VAD) flips and, when you open full Settings, the matching toggle there shows the same state.
3. Quality chips change the preset when idle; while a recording is in progress the Quality row is dimmed and non-interactive, with the "can't change while recording" note.
4. Tapping a theme swatch applies the theme instantly behind the sheet; relaunch the app and confirm the theme persisted.
5. The "Save location" row opens the folder picker; the chosen folder is reflected in the row label next time the sheet opens.
6. Toggling Cloud backup on while not signed in launches the Google Drive sign-in flow.
7. "Full Settings ›" closes the sheet and opens the full Settings page.
8. Open the sheet **while recording** — it opens and the DSP toggles still work live.
9. Swipe the sheet down / tap the scrim — it dismisses.

- [ ] **Step 3: Commit any fixes, then finalize**

If the checklist surfaced fixes, commit them. Otherwise the feature is complete.

```bash
git commit --allow-empty -m "test: verify Quick Settings sheet end-to-end"
```

- [ ] **Step 4: Sync the other copy**

Per the working-location note, bring the commits to the other copy via git (from the OTHER copy: `git fetch <this-copy-path> feat/loudness-delivery && git reset --hard FETCH_HEAD` if it has no unique commits, else cherry-pick). Do **not** blind-copy.

---

## Self-review notes

- **Spec coverage:** Sliders icon (Task 1, 3) ✓; sheet with 6 DSP toggles + Quality + theme swatch + save/cloud + Full Settings link (Task 2) ✓; available during recording with Quality disabled (Task 2 `isRecording`/`enabled`, Task 4 passes `isRecording`) ✓; theme/save/cloud plumbing from MainActivity (Task 4 params, Task 5 args) ✓; full Settings untouched ✓; no new persisted state ✓.
- **Type consistency:** `QuickSettingsSheet` param names (`onToggleAgc`, `onChangeQuality`, `currentTheme`, `saveLocationLabel`, `onOpenFullSettings`, `onDismiss`) are identical between the definition (Task 2) and the call site (Task 4). `IconLineSliders(tint, size)` signature matches between Task 1 and Task 3. VM methods (`toggleAgc`, `toggleHiPass`, `toggleAntiClip`, `toggleCompressor`, `toggleStereoWidener`, `toggleVad`, `setQuality`, `toggleCloudBackup`) and flows (`agcOn`, `hiPassOn`, `antiClipOn`, `compressorOn`, `stereoWidenerOn`, `vadOn`, `quality`, `cloudBackupOn`, `isDriveSignedIn`) all verified to exist in `RecorderViewModel.kt`.
- **No placeholders:** every step shows the exact code/command.
