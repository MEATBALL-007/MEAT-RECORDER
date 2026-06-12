# Customizable Workspace & Pro Markers — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a consistent "★ PRO" marker to pro-gated controls, and let users show/hide + reorder the in-recording quick controls with layouts saved per recording mode (free = one shared layout, Pro = per-mode layouts).

**Architecture:** Phase A builds a reusable `ProBadge` + `ProLock` wrapper and adds a `CUSTOM_WORKSPACE` Pro tier. Phase B adds a `QuickControl` registry, a pure `WorkspaceLayout` model with `org.json` persistence, a thin `WorkspaceManager` that resolves free/Pro + mode, a registry-driven render in `RecordingActiveSection`, and a `WorkspaceCustomizeSheet` editor. Pure logic is unit-tested (JUnit + org.json); Compose UI is verified on an emulator (this repo has no Compose UI-test deps).

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX DataStore (Preferences), `org.json`, JUnit 4. Gradle test command: `./gradlew app:test`. Build: `./gradlew :app:assembleDebug`.

**Key decision (deviation from mockup):** the Customize editor reorders with **▲ / ▼ move buttons**, not drag-and-drop. Reason: no reorderable Compose library is present, drag gesture code is fragile to specify; move-buttons make reordering a pure, unit-testable list operation. UX still allows full reordering.

**Convention:** end every commit message with the trailer:
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## File Structure

**Phase A — Pro markers**
- Create `app/src/main/java/com/example/recorderproject/ui/components/ProBadge.kt` — the "★ PRO" pill (one definition, reused).
- Create `app/src/main/java/com/example/recorderproject/ui/components/ProLock.kt` — wrapper that badges + intercepts taps → paywall for free users.
- Modify `app/src/main/java/com/example/recorderproject/billing/ProFeature.kt` — add `CUSTOM_WORKSPACE`.
- Modify `app/src/main/java/com/example/recorderproject/ui/components/ProUpgradeSheet.kt` — add slide + `initialSlide()` mapping.

**Phase B — Customizable workspace**
- Create `app/src/main/java/com/example/recorderproject/model/QuickControl.kt` — registry enum (id, label, proFeature).
- Create `app/src/main/java/com/example/recorderproject/model/WorkspaceLayout.kt` — model + pure ops.
- Create `app/src/main/java/com/example/recorderproject/model/WorkspaceLayoutJson.kt` — org.json round-trip + merge.
- Create `app/src/main/java/com/example/recorderproject/workspace/WorkspaceManager.kt` — resolve free/Pro + mode; persist.
- Create `app/src/main/java/com/example/recorderproject/ui/components/WorkspaceCustomizeSheet.kt` — the editor.
- Modify `app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt` — workspace keys + flows/setters.
- Modify `app/src/main/java/com/example/recorderproject/ui/components/RecordingActiveSection.kt` — registry-driven chip render.
- Modify `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` — wire `WorkspaceManager`, customize-sheet state, feature-aware paywall.
- Modify `app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt` + `ui/RecordPage.kt` + `RecorderApp` (in `MainActivity.kt`/host) — thread layout, customize entry, feature-aware upgrade, host the sheet.

**Tests**
- Create `app/src/test/java/com/example/recorderproject/WorkspaceLayoutTest.kt`
- Create `app/src/test/java/com/example/recorderproject/WorkspaceLayoutJsonTest.kt`
- Create `app/src/test/java/com/example/recorderproject/QuickControlTest.kt`

---

# PHASE A — Pro marker system

### Task A1: `ProBadge` composable

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/ProBadge.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MeatYellow = Color(0xFFFFC72C)

/**
 * The "★ PRO" pill — single shared definition for the top bar and any pro-gated
 * control. Visible to free users only; callers decide when to show it.
 */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text(
        text = "★ PRO",
        color = Color(0xFF0C0C10),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MeatYellow)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF101014)
@Composable
private fun ProBadgePreview() {
    ProBadge()
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/ProBadge.kt
git commit -m "feat: add reusable ProBadge pill composable

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task A2: `ProLock` wrapper composable

Wraps any control. When `feature != null && !isPro`, it overlays a `ProBadge` and a transparent tap-catcher that routes taps to `onUpgrade(feature)` (the control's own action never fires). When unlocked (Pro, or `feature == null`), it's a transparent pass-through.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/ProLock.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recorderproject.billing.ProFeature

/**
 * Marks [content] as a Pro feature for free users: shows a [ProBadge] in the
 * top-end corner and intercepts taps to open the paywall for [feature] instead
 * of running the control's own click. For Pro users (or when [feature] is null)
 * it renders [content] untouched.
 */
@Composable
fun ProLock(
    feature: ProFeature?,
    isPro: Boolean,
    onUpgrade: (ProFeature) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val locked = feature != null && !isPro
    Box(modifier = modifier) {
        content()
        if (locked) {
            // Transparent tap-catcher covering the control → paywall.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onUpgrade(feature!!) },
            )
            ProBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/ProLock.kt
git commit -m "feat: add ProLock wrapper (badge + paywall tap-catch for free users)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task A3: Add `CUSTOM_WORKSPACE` Pro tier + paywall slide

The `ProFeature.initialSlide()` `when` in `ProUpgradeSheet.kt` is exhaustive (no `else`), so adding the enum value will not compile until the mapping and a slide are added — do all three edits together.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/billing/ProFeature.kt:11-38`
- Modify: `app/src/main/java/com/example/recorderproject/ui/components/ProUpgradeSheet.kt` (slides list ~75-118, `initialSlide()` ~120-132, add a `draw` fn)

- [ ] **Step 1: Add the enum value**

In `ProFeature.kt`, add this line immediately after the `RECORDING_LIMIT(...)` entry (keep the trailing `;` on the final entry):

```kotlin
    RECORDING_LIMIT("Unlimited recording", "Free recordings are limited to 5 min — go unlimited with Pro"),
    CUSTOM_WORKSPACE("Custom workspace", "Save a different control layout for each recording mode");
```

- [ ] **Step 2: Add the paywall slide**

In `ProUpgradeSheet.kt`, append this `ProSlide` as the last element of the `proSlides` list (after the "Unlimited Recording" slide, line ~117):

```kotlin
    ProSlide(
        title = "Custom Workspace",
        blurb = "Save a different control layout for each recording mode",
        accentColor = SlideBlue,
        draw = { drawWorkspaceVisual() },
    ),
```

- [ ] **Step 3: Map the feature to the new slide**

In `ProUpgradeSheet.kt`, add this branch to `initialSlide()` (the new slide is at index 7):

```kotlin
    ProFeature.RECORDING_LIMIT               -> 6
    ProFeature.CUSTOM_WORKSPACE              -> 7
```

- [ ] **Step 4: Add the slide's Canvas visual**

In `ProUpgradeSheet.kt`, add this function next to the other `private fun DrawScope.draw*Visual()` functions (e.g. after `drawUnlimitedRecordingVisual`):

```kotlin
private fun DrawScope.drawWorkspaceVisual() {
    // Three stacked "control chips" with a reorder caret — evokes a movable layout.
    val rowH = size.height * 0.16f
    val gap = size.height * 0.07f
    val w = size.width * 0.56f
    val x = (size.width - w) / 2f
    var y = size.height * 0.22f
    val colors = listOf(SlideBlue, RecorderYellow, SlideGreen)
    colors.forEach { c ->
        drawRoundRect(
            color = c.copy(alpha = 0.75f),
            topLeft = Offset(x, y),
            size = Size(w, rowH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(rowH / 2f, rowH / 2f),
        )
        // Up/down caret on the right edge of each row.
        val cx = x + w - rowH * 0.6f
        drawLine(Color.White, Offset(cx, y + rowH * 0.35f), Offset(cx + rowH * 0.25f, y + rowH * 0.2f), 2.dp.toPx())
        drawLine(Color.White, Offset(cx + rowH * 0.5f, y + rowH * 0.2f), Offset(cx + rowH * 0.75f, y + rowH * 0.35f), 2.dp.toPx())
        y += rowH + gap
    }
}
```

(Imports `Offset`, `Size`, `Color`, `dp`, and `DrawScope` are already present in `ProUpgradeSheet.kt`.)

- [ ] **Step 5: Build to verify exhaustiveness + compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `initialSlide()` was missed, Kotlin reports a non-exhaustive `when` here.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/billing/ProFeature.kt app/src/main/java/com/example/recorderproject/ui/components/ProUpgradeSheet.kt
git commit -m "feat: add CUSTOM_WORKSPACE pro tier and paywall slide

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

# PHASE B — Customizable workspace

### Task B1: `QuickControl` registry enum

The universe of customizable in-recording controls. Order here is the default layout order. `proFeature` (nullable) drives the badge. Feature mapping confirmed against `ProFeature.kt`: Slate/Pre-roll/VAD → `PRE_ROLL_VAD`; Live EQ/Edit EQ → `FULL_EQ`; NR Gate → `LIVE_DSP`.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/model/QuickControl.kt`
- Test: `app/src/test/java/com/example/recorderproject/QuickControlTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.recorderproject

import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.model.QuickControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickControlTest {
    @Test fun ids_are_unique() {
        val ids = QuickControl.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun fromId_roundtrips_every_entry() {
        for (c in QuickControl.entries) {
            assertEquals(c, QuickControl.fromId(c.id))
        }
    }

    @Test fun fromId_unknown_is_null() {
        assertNull(QuickControl.fromId("nope"))
    }

    @Test fun free_controls_have_no_feature() {
        assertNull(QuickControl.PAUSE.proFeature)
        assertNull(QuickControl.DROP_CUE.proFeature)
    }

    @Test fun vad_is_gated_on_pre_roll_vad() {
        assertEquals(ProFeature.PRE_ROLL_VAD, QuickControl.VAD.proFeature)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.example.recorderproject.QuickControlTest"`
Expected: FAIL — `QuickControl` unresolved.

- [ ] **Step 3: Create the enum**

```kotlin
package com.example.recorderproject.model

import com.example.recorderproject.billing.ProFeature

/**
 * The in-recording quick controls a user can show/hide and reorder. Declaration
 * order is the default layout order. [proFeature] (nullable) marks pro-gated
 * controls so the UI can badge and gate them.
 */
enum class QuickControl(
    val id: String,
    val label: String,
    val proFeature: ProFeature?,
) {
    PAUSE("pause", "Pause", null),
    DROP_CUE("drop_cue", "Drop cue", null),
    SLATE("slate", "Slate", ProFeature.PRE_ROLL_VAD),
    LIVE_EQ("live_eq", "Live EQ", ProFeature.FULL_EQ),
    NR_GATE("nr_gate", "NR Gate", ProFeature.LIVE_DSP),
    EDIT_EQ("edit_eq", "Edit EQ", ProFeature.FULL_EQ),
    PRE_ROLL("pre_roll", "Pre-roll 5s", ProFeature.PRE_ROLL_VAD),
    VAD("vad", "VAD", ProFeature.PRE_ROLL_VAD);

    companion object {
        fun fromId(id: String): QuickControl? = entries.firstOrNull { it.id == id }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:test --tests "com.example.recorderproject.QuickControlTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/model/QuickControl.kt app/src/test/java/com/example/recorderproject/QuickControlTest.kt
git commit -m "feat: add QuickControl registry enum + tests

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task B2: `WorkspaceLayout` model + pure reorder/visibility ops

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/model/WorkspaceLayout.kt`
- Test: `app/src/test/java/com/example/recorderproject/WorkspaceLayoutTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.recorderproject

import com.example.recorderproject.model.QuickControl
import com.example.recorderproject.model.WorkspaceLayout
import com.example.recorderproject.model.WorkspaceItem
import com.example.recorderproject.model.moveUp
import com.example.recorderproject.model.moveDown
import com.example.recorderproject.model.toggleVisible
import com.example.recorderproject.model.mergedWithDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLayoutTest {
    @Test fun default_has_all_controls_visible_in_declaration_order() {
        val d = WorkspaceLayout.DEFAULT
        assertEquals(QuickControl.entries.toList(), d.items.map { it.control })
        assertTrue(d.items.all { it.visible })
    }

    @Test fun visibleControls_filters_hidden() {
        val l = WorkspaceLayout.DEFAULT.toggleVisible(0) // hide first
        assertTrue(QuickControl.entries[0] !in l.visibleControls())
        assertEquals(QuickControl.entries.size - 1, l.visibleControls().size)
    }

    @Test fun moveDown_then_moveUp_is_identity() {
        val start = WorkspaceLayout.DEFAULT
        val moved = start.moveDown(0).moveUp(1)
        assertEquals(start.items, moved.items)
    }

    @Test fun moveUp_at_top_is_noop() {
        val start = WorkspaceLayout.DEFAULT
        assertEquals(start.items, start.moveUp(0).items)
    }

    @Test fun moveDown_at_bottom_is_noop() {
        val start = WorkspaceLayout.DEFAULT
        val last = start.items.lastIndex
        assertEquals(start.items, start.moveDown(last).items)
    }

    @Test fun toggleVisible_flips_one_item() {
        val l = WorkspaceLayout.DEFAULT.toggleVisible(2)
        assertEquals(false, l.items[2].visible)
        assertEquals(true, l.toggleVisible(2).items[2].visible)
    }

    @Test fun mergedWithDefaults_appends_missing_and_drops_unknown() {
        // A layout missing VAD entirely; VAD should be appended (visible) at the end.
        val partial = WorkspaceLayout(
            WorkspaceLayout.DEFAULT.items.filter { it.control != QuickControl.VAD }
        )
        val merged = partial.mergedWithDefaults()
        assertEquals(QuickControl.entries.size, merged.items.size)
        assertEquals(QuickControl.VAD, merged.items.last().control)
        assertTrue(merged.items.last().visible)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.example.recorderproject.WorkspaceLayoutTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Create the model + ops**

```kotlin
package com.example.recorderproject.model

/** One row in a workspace layout: a control and whether it is shown. */
data class WorkspaceItem(val control: QuickControl, val visible: Boolean)

/** Ordered, show/hide-able set of quick controls for the recording screen. */
data class WorkspaceLayout(val items: List<WorkspaceItem>) {
    /** Controls to render, in order, skipping hidden ones. */
    fun visibleControls(): List<QuickControl> = items.filter { it.visible }.map { it.control }

    companion object {
        /** Every control, in declaration order, all visible. */
        val DEFAULT = WorkspaceLayout(QuickControl.entries.map { WorkspaceItem(it, true) })
    }
}

fun WorkspaceLayout.moveUp(index: Int): WorkspaceLayout {
    if (index <= 0 || index >= items.size) return this
    val list = items.toMutableList()
    val tmp = list[index - 1]; list[index - 1] = list[index]; list[index] = tmp
    return WorkspaceLayout(list)
}

fun WorkspaceLayout.moveDown(index: Int): WorkspaceLayout {
    if (index < 0 || index >= items.lastIndex) return this
    val list = items.toMutableList()
    val tmp = list[index + 1]; list[index + 1] = list[index]; list[index] = tmp
    return WorkspaceLayout(list)
}

fun WorkspaceLayout.toggleVisible(index: Int): WorkspaceLayout {
    if (index < 0 || index >= items.size) return this
    val list = items.toMutableList()
    list[index] = list[index].copy(visible = !list[index].visible)
    return WorkspaceLayout(list)
}

/**
 * Reconciles a (possibly stale) layout with the current [QuickControl] registry:
 * drops items whose control no longer exists (handled at parse time) and appends
 * any registry controls missing from this layout, visible, at the end. Keeps
 * existing order/visibility for known controls.
 */
fun WorkspaceLayout.mergedWithDefaults(): WorkspaceLayout {
    val present = items.map { it.control }.toSet()
    val missing = QuickControl.entries.filter { it !in present }.map { WorkspaceItem(it, true) }
    return WorkspaceLayout(items + missing)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:test --tests "com.example.recorderproject.WorkspaceLayoutTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/model/WorkspaceLayout.kt app/src/test/java/com/example/recorderproject/WorkspaceLayoutTest.kt
git commit -m "feat: add WorkspaceLayout model + pure reorder/visibility ops + tests

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task B3: `WorkspaceLayoutJson` (org.json round-trip)

Follows the existing `EQChainJson` pattern (org.json, schema version, returns `null` on bad input). `fromJsonString` skips unknown ids and runs `mergedWithDefaults()` so loading never drops new controls.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/model/WorkspaceLayoutJson.kt`
- Test: `app/src/test/java/com/example/recorderproject/WorkspaceLayoutJsonTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.recorderproject

import com.example.recorderproject.model.QuickControl
import com.example.recorderproject.model.WorkspaceLayout
import com.example.recorderproject.model.WorkspaceLayoutJson
import com.example.recorderproject.model.toggleVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceLayoutJsonTest {
    @Test fun round_trips_a_custom_layout() {
        val original = WorkspaceLayout.DEFAULT.toggleVisible(1).moveDownCompat(0)
        val json = WorkspaceLayoutJson.toJsonString(original)
        val parsed = WorkspaceLayoutJson.fromJsonString(json)
        assertEquals(original.items, parsed!!.items)
    }

    @Test fun bad_json_returns_null() {
        assertNull(WorkspaceLayoutJson.fromJsonString("not json"))
    }

    @Test fun unknown_ids_are_skipped_and_missing_appended() {
        // Only one valid control + one bogus id → parse keeps the valid one and
        // appends the rest of the registry via mergedWithDefaults.
        val json = """{"schema":1,"items":[{"id":"vad","visible":false},{"id":"bogus","visible":true}]}"""
        val parsed = WorkspaceLayoutJson.fromJsonString(json)!!
        assertEquals(QuickControl.VAD, parsed.items.first().control)
        assertEquals(false, parsed.items.first().visible)
        assertEquals(QuickControl.entries.size, parsed.items.size) // bogus dropped, rest merged
    }
}

// Helper to keep the test independent of import ordering.
private fun WorkspaceLayout.moveDownCompat(i: Int) =
    com.example.recorderproject.model.moveDown(this, i)
```

Note: the test references a `moveDown(layout, index)` free-function overload to avoid import friction. Provide it in Step 3 alongside the existing extension (thin wrapper).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.example.recorderproject.WorkspaceLayoutJsonTest"`
Expected: FAIL — `WorkspaceLayoutJson` unresolved.

- [ ] **Step 3: Create the serializer (and the wrapper overload)**

```kotlin
package com.example.recorderproject.model

import org.json.JSONArray
import org.json.JSONObject

/** Wrapper overload used by tests/callers that prefer function-call form. */
fun moveDown(layout: WorkspaceLayout, index: Int): WorkspaceLayout = layout.moveDown(index)

/**
 * JSON persistence for [WorkspaceLayout], mirroring EQChainJson: org.json,
 * schema-versioned, null on malformed input. On load, unknown control ids are
 * skipped and any registry controls missing from the file are appended via
 * [mergedWithDefaults].
 */
object WorkspaceLayoutJson {
    private const val SCHEMA_VERSION = 1

    fun toJsonString(layout: WorkspaceLayout): String {
        val root = JSONObject()
        root.put("schema", SCHEMA_VERSION)
        val arr = JSONArray()
        for (item in layout.items) {
            arr.put(JSONObject().apply {
                put("id", item.control.id)
                put("visible", item.visible)
            })
        }
        root.put("items", arr)
        return root.toString()
    }

    fun fromJsonString(json: String): WorkspaceLayout? = try {
        val root = JSONObject(json)
        val arr = root.getJSONArray("items")
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val control = QuickControl.fromId(o.getString("id")) ?: return@mapNotNull null
            WorkspaceItem(control, o.optBoolean("visible", true))
        }
        WorkspaceLayout(items).mergedWithDefaults()
    } catch (e: Exception) {
        null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:test --tests "com.example.recorderproject.WorkspaceLayoutJsonTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/model/WorkspaceLayoutJson.kt app/src/test/java/com/example/recorderproject/WorkspaceLayoutJsonTest.kt
git commit -m "feat: add WorkspaceLayoutJson org.json persistence + tests

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task B4: DataStore keys + flows/setters for layouts

Adds dedicated, semantic accessors (not routed through the big `snapshot()` aggregate, to keep this change isolated). Free layout = one global key; Pro = one key per mode.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt`

- [ ] **Step 1: Add imports if missing**

Ensure these are imported in `SettingsDataStore.kt` (the file already uses `stringPreferencesKey`, `edit`, `map`; add `RecorderMode` and `Flow` if absent):

```kotlin
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.recorderproject.model.RecorderMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
```

- [ ] **Step 2: Add keys + accessors**

Add inside the `SettingsDataStore` class (near the other `private val *Key` declarations and `setX` functions):

```kotlin
// ── Workspace layouts ───────────────────────────────────────────────────
private val workspaceGlobalKey = stringPreferencesKey("workspace_layout_global")
private fun workspaceModeKey(modeName: String) =
    stringPreferencesKey("workspace_layout_mode_${modeName.lowercase()}")

/** Free-tier single shared layout (null until first saved). */
fun globalWorkspaceJson(): Flow<String?> =
    context.dataStore.data.map { it[workspaceGlobalKey] }

suspend fun setGlobalWorkspaceJson(json: String) =
    context.dataStore.edit { it[workspaceGlobalKey] = json }

/** Pro-tier per-mode layout (null until first saved for that mode). */
fun modeWorkspaceJson(mode: RecorderMode): Flow<String?> =
    context.dataStore.data.map { it[workspaceModeKey(mode.name)] }

suspend fun setModeWorkspaceJson(mode: RecorderMode, json: String) =
    context.dataStore.edit { it[workspaceModeKey(mode.name)] = json }
```

(If the existing `dataStore` accessor differs from `context.dataStore`, match the file's existing pattern — the other setters in this file show the exact receiver to use.)

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt
git commit -m "feat: add workspace layout datastore keys (global + per-mode)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task B5: `WorkspaceManager` (resolve free/Pro + mode; persist)

Thin glue: exposes a `StateFlow<WorkspaceLayout>` that reads the global layout for free users or the active-mode layout for Pro users, and saves to the matching key. Logic is intentionally minimal; correctness of the parts it composes is covered by B2/B3 unit tests, and the wiring is verified on the emulator in B7–B9.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/workspace/WorkspaceManager.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.recorderproject.workspace

import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.model.RecorderMode
import com.example.recorderproject.model.WorkspaceLayout
import com.example.recorderproject.model.WorkspaceLayoutJson
import com.example.recorderproject.model.mergedWithDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Resolves and persists the recording workspace layout.
 * Free users share one global layout; Pro users get an independent layout per
 * [RecorderMode]. The active layout is always merged with the current registry
 * so newly added controls appear automatically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceManager(
    private val settings: SettingsDataStore,
    private val scope: CoroutineScope,
    private val isPro: StateFlow<Boolean>,
    private val mode: StateFlow<RecorderMode>,
) {
    val layout: StateFlow<WorkspaceLayout> =
        combine(isPro, mode) { pro, m -> pro to m }
            .flatMapLatest { (pro, m) ->
                val source = if (pro) settings.modeWorkspaceJson(m) else settings.globalWorkspaceJson()
                source.map { json ->
                    json?.let { WorkspaceLayoutJson.fromJsonString(it) }?.mergedWithDefaults()
                        ?: WorkspaceLayout.DEFAULT
                }
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), WorkspaceLayout.DEFAULT)

    /** Persist [newLayout] to the appropriate store for the current entitlement. */
    fun save(newLayout: WorkspaceLayout) {
        scope.launch {
            val json = WorkspaceLayoutJson.toJsonString(newLayout)
            if (isPro.value) settings.setModeWorkspaceJson(mode.value, json)
            else settings.setGlobalWorkspaceJson(json)
        }
    }

    fun resetToDefault() = save(WorkspaceLayout.DEFAULT)
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/workspace/WorkspaceManager.kt
git commit -m "feat: add WorkspaceManager (free global vs pro per-mode layout)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task B6: Wire `WorkspaceManager` + customize state into `RecorderViewModel`

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

**Context:** the VM already exposes `isPro: StateFlow<Boolean>` (line 68), `openPaywall(feature)` (line 73), and `settings` + a recorder-mode `StateFlow`. Confirm the exact field names by reading the file: the SettingsDataStore instance and the `StateFlow<RecorderMode>` (the design notes call it `recorderMode` / `_recorderMode`). Use those exact names below.

- [ ] **Step 1: Add the manager + customize-sheet state**

Add near the other collaborator declarations (after `requirePro`, ~line 85), substituting the real field names for `settings` and `recorderMode`:

```kotlin
    // ── Customizable workspace ──────────────────────────────────────────
    val workspace = com.example.recorderproject.workspace.WorkspaceManager(
        settings = settings,            // existing SettingsDataStore instance
        scope = viewModelScope,
        isPro = isPro,
        mode = recorderMode,            // existing StateFlow<RecorderMode>
    )
    val workspaceLayout: StateFlow<com.example.recorderproject.model.WorkspaceLayout> = workspace.layout

    private val _customizeOpen = MutableStateFlow(false)
    val customizeOpen: StateFlow<Boolean> = _customizeOpen
    fun openCustomize() { _customizeOpen.value = true }
    fun closeCustomize() { _customizeOpen.value = false }

    fun saveWorkspace(layout: com.example.recorderproject.model.WorkspaceLayout) = workspace.save(layout)
    fun resetWorkspace() = workspace.resetToDefault()
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `settings`/`recorderMode` names differ, the compiler names the unresolved symbol — fix to match.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat: wire WorkspaceManager + customize state into RecorderViewModel

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task B7: Registry-driven chip render in `RecordingActiveSection`

Replace the three hardcoded chip `Row`s (lines 138-204) with a block that renders `layout.visibleControls()` in order, chunked into rows of three, wrapping pro controls in `ProLock`. Leaves everything above line 138 and below line 204 unchanged.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/components/RecordingActiveSection.kt`

- [ ] **Step 1: Add new params to `RecordingActiveSection`**

In the parameter list (after `channelCount: Int = 1,`, line 112), add:

```kotlin
    channelCount: Int = 1,
    layout: com.example.recorderproject.model.WorkspaceLayout = com.example.recorderproject.model.WorkspaceLayout.DEFAULT,
    isPro: Boolean = true,
    onUpgrade: (com.example.recorderproject.billing.ProFeature) -> Unit = {},
    modifier: Modifier = Modifier,
```

(The existing `modifier: Modifier = Modifier,` line is already present — do not duplicate it; insert the three new params before it.)

- [ ] **Step 2: Replace the three chip rows (lines 138-204) with the dynamic block**

Delete the block that starts at the `// Quick action row` comment (line 138) and ends at the close of the third `Row {...}` (line 204), and replace it with:

```kotlin
        // Quick controls — order & visibility come from the saved workspace layout.
        layout.visibleControls().chunked(3).forEach { rowControls ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowControls.forEach { control ->
                    ProLock(
                        feature = control.proFeature,
                        isPro = isPro,
                        onUpgrade = onUpgrade,
                        modifier = Modifier.weight(1f),
                    ) {
                        QuickControlChip(
                            control = control,
                            isPaused = isPaused,
                            cueCount = cueCount,
                            liveEqOn = liveEqOn,
                            liveNoiseGateOn = liveNoiseGateOn,
                            preRollOn = preRollOn,
                            vadOn = vadOn,
                            onTogglePause = onTogglePause,
                            onDropCue = onDropCue,
                            onSlateTone = onSlateTone,
                            onToggleLiveEq = onToggleLiveEq,
                            onToggleLiveNoiseGate = onToggleLiveNoiseGate,
                            onOpenEqEditor = onOpenEqEditor,
                            onTogglePreRoll = onTogglePreRoll,
                            onToggleVad = onToggleVad,
                        )
                    }
                }
                // Pad short rows so chips keep equal width.
                repeat(3 - rowControls.size) { Spacer(Modifier.weight(1f)) }
            }
        }
```

- [ ] **Step 3: Add the `QuickControlChip` dispatcher composable**

Add this private composable to the same file (next to `QuickActionChip` / `ActiveChip`). It maps each control to the existing chip style; `ProLock` already supplies the `Modifier.weight(1f)`, so the chip fills its slot with `Modifier.fillMaxWidth()`:

```kotlin
@Composable
private fun QuickControlChip(
    control: com.example.recorderproject.model.QuickControl,
    isPaused: Boolean,
    cueCount: Int,
    liveEqOn: Boolean,
    liveNoiseGateOn: Boolean,
    preRollOn: Boolean,
    vadOn: Boolean,
    onTogglePause: () -> Unit,
    onDropCue: () -> Unit,
    onSlateTone: () -> Unit,
    onToggleLiveEq: () -> Unit,
    onToggleLiveNoiseGate: () -> Unit,
    onOpenEqEditor: () -> Unit,
    onTogglePreRoll: () -> Unit,
    onToggleVad: () -> Unit,
) {
    val m = Modifier.fillMaxWidth()
    when (control) {
        com.example.recorderproject.model.QuickControl.PAUSE ->
            QuickActionChip(if (isPaused) "Resume" else "Pause", if (isPaused) "▶" else "❚❚", onTogglePause, m)
        com.example.recorderproject.model.QuickControl.DROP_CUE ->
            QuickActionChip(if (cueCount == 0) "Drop cue" else "Cue · $cueCount", "◆", onDropCue, m)
        com.example.recorderproject.model.QuickControl.SLATE ->
            QuickActionChip("Slate", "♪", onSlateTone, m)
        com.example.recorderproject.model.QuickControl.LIVE_EQ ->
            ActiveChip(if (liveEqOn) "Live EQ ON" else "Live EQ", liveEqOn, onToggleLiveEq, m)
        com.example.recorderproject.model.QuickControl.NR_GATE ->
            ActiveChip(if (liveNoiseGateOn) "NR Gate ON" else "NR Gate", liveNoiseGateOn, onToggleLiveNoiseGate, m)
        com.example.recorderproject.model.QuickControl.EDIT_EQ ->
            QuickActionChip("Edit EQ", "→", onOpenEqEditor, m)
        com.example.recorderproject.model.QuickControl.PRE_ROLL ->
            ActiveChip(if (preRollOn) "Pre-roll 5s ON" else "Pre-roll 5s", preRollOn, onTogglePreRoll, m)
        com.example.recorderproject.model.QuickControl.VAD ->
            ActiveChip(if (vadOn) "VAD ON" else "VAD", vadOn, onToggleVad, m)
    }
}
```

(`QuickActionChip(label, icon, onClick, modifier)` and `ActiveChip(label, active, onClick, modifier)` already exist in this file with these positional signatures. `Spacer` is already imported.)

- [ ] **Step 4: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/RecordingActiveSection.kt
git commit -m "refactor: render quick controls from workspace layout + pro-lock

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task B8: `WorkspaceCustomizeSheet` editor

A `ModalBottomSheet` (same component as `ProUpgradeSheet`) listing every `QuickControl` with an eye toggle and ▲/▼ move buttons. Holds a local working copy; **Save** commits it, **Reset** restores the default. Free users see the per-mode upsell row that opens the `CUSTOM_WORKSPACE` paywall.

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/components/WorkspaceCustomizeSheet.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.model.WorkspaceLayout
import com.example.recorderproject.model.moveDown
import com.example.recorderproject.model.moveUp
import com.example.recorderproject.model.toggleVisible

private val MeatYellow = Color(0xFFFFC72C)
private val CardBg = Color(0xFF161616)
private val Charcoal = Color(0xFF101014)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceCustomizeSheet(
    layout: WorkspaceLayout,
    isPro: Boolean,
    modeName: String,
    onSave: (WorkspaceLayout) -> Unit,
    onReset: () -> Unit,
    onUpgrade: (ProFeature) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var working by remember { mutableStateOf(layout) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Charcoal, dragHandle = null) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Customize layout", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(
                if (isPro) "Saved to $modeName mode" else "Shared across all modes",
                color = MeatYellow, fontSize = 12.sp,
            )

            working.items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(item.control.label, color = if (item.visible) Color.White else Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (item.control.proFeature != null && !isPro) ProBadge()
                    Text("", modifier = Modifier.weight(1f))
                    // Eye toggle
                    Text(if (item.visible) "👁" else "🚫", fontSize = 16.sp, modifier = Modifier.clickable { working = working.toggleVisible(index) })
                    // Move up / down
                    Text("▲", color = if (index == 0) Color.White.copy(alpha = 0.25f) else MeatYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { working = working.moveUp(index) })
                    Text("▼", color = if (index == working.items.lastIndex) Color.White.copy(alpha = 0.25f) else MeatYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { working = working.moveDown(index) })
                }
            }

            // Free → per-mode upsell.
            if (!isPro) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).clickable { onUpgrade(ProFeature.CUSTOM_WORKSPACE) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProBadge()
                    Text("Save a separate layout per mode", color = Color.White, fontSize = 13.sp)
                }
            }

            Button(
                onClick = { onSave(working); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA4616)),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Done", color = Color.White, fontWeight = FontWeight.Bold) }

            TextButton(
                onClick = { working = WorkspaceLayout.DEFAULT; onReset() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Reset to default", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) }
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/WorkspaceCustomizeSheet.kt
git commit -m "feat: add WorkspaceCustomizeSheet editor (show/hide + move + upsell)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task B9: Thread layout + customize entry through the UI and host the sheet

Wire the new state from VM → `RecorderApp` → `MeatRecHome` → `RecordPage` → `RecordingActiveSection`, add the "✎ Customize layout" entry, a feature-aware upgrade callback, and host `WorkspaceCustomizeSheet` next to the paywall.

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt` (params ~79-170; pager call ~326-388)
- Modify: `app/src/main/java/com/example/recorderproject/ui/RecordPage.kt` (params; `RecordingActiveSection` call ~178-210)
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt` (the `RecorderApp` composable that builds `MeatRecHome` and hosts `ProUpgradeSheet` ~324-332)

- [ ] **Step 1: Add params to `MeatRecHome`**

After `onTapUpgrade: () -> Unit = {},` (line 168) add:

```kotlin
    onTapUpgrade: () -> Unit = {},
    onUpgradeFeature: (com.example.recorderproject.billing.ProFeature) -> Unit = {},
    workspaceLayout: com.example.recorderproject.model.WorkspaceLayout = com.example.recorderproject.model.WorkspaceLayout.DEFAULT,
    onOpenCustomize: () -> Unit = {},
```

- [ ] **Step 2: Pass them into `RecordPage`**

In the `RecordPage(...)` call (the `0 -> RecordPage(` block, ~line 326), add these arguments alongside the existing ones:

```kotlin
                        workspaceLayout = workspaceLayout,
                        isProUser = isPro,
                        onUpgradeFeature = onUpgradeFeature,
                        onOpenCustomize = onOpenCustomize,
```

- [ ] **Step 3: Add matching params to `RecordPage` and forward to `RecordingActiveSection`**

In `RecordPage.kt`, add to its parameter list:

```kotlin
    workspaceLayout: com.example.recorderproject.model.WorkspaceLayout = com.example.recorderproject.model.WorkspaceLayout.DEFAULT,
    isProUser: Boolean = true,
    onUpgradeFeature: (com.example.recorderproject.billing.ProFeature) -> Unit = {},
    onOpenCustomize: () -> Unit = {},
```

In the `RecordingActiveSection(...)` call (~line 178), add:

```kotlin
    channelCount = channelCount,
    layout = workspaceLayout,
    isPro = isProUser,
    onUpgrade = onUpgradeFeature,
```

Add a "✎ Customize layout" entry below the record button. Find where `RecordingActiveSection` is placed in `RecordPage` and add, just after it (use a plain clickable `Text` to avoid depending on private chip composables in this file):

```kotlin
    Text(
        "✎ Customize layout",
        color = Color(0xFFFFC72C),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenCustomize)
            .padding(vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
```

(Ensure `clickable`, `Text`, `Color`, `sp`, `FontWeight`, `padding`, `fillMaxWidth` are imported in `RecordPage.kt`; add any that are missing.)

- [ ] **Step 4: Wire from `RecorderApp` (in `MainActivity.kt`) and host the sheet**

Where `RecorderApp` collects VM state and builds `MeatRecHome`, collect the new flows and pass the new args:

```kotlin
val workspaceLayout by viewModel.workspaceLayout.collectAsStateWithLifecycle()
val customizeOpen by viewModel.customizeOpen.collectAsStateWithLifecycle()
val currentMode by viewModel.recorderMode.collectAsStateWithLifecycle()   // existing flow
```

Add to the `MeatRecHome(...)` call:

```kotlin
    onUpgradeFeature = { feature -> viewModel.openPaywall(feature) },
    workspaceLayout = workspaceLayout,
    onOpenCustomize = { viewModel.openCustomize() },
```

Next to the existing paywall host (`if (paywallFeature != null) { ProUpgradeSheet(...) }`, ~line 324), add:

```kotlin
if (customizeOpen) {
    com.example.recorderproject.ui.components.WorkspaceCustomizeSheet(
        layout = workspaceLayout,
        isPro = isPro,                       // existing collected isPro state
        modeName = currentMode.displayName,
        onSave = { viewModel.saveWorkspace(it) },
        onReset = { viewModel.resetWorkspace() },
        onUpgrade = { feature -> viewModel.openPaywall(feature) },
        onDismiss = { viewModel.closeCustomize() },
    )
}
```

(Match `collectAsStateWithLifecycle` to whatever collection helper this file already uses, e.g. `collectAsState`.)

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/MeatRecHome.kt app/src/main/java/com/example/recorderproject/ui/RecordPage.kt app/src/main/java/com/example/recorderproject/MainActivity.kt
git commit -m "feat: wire customizable workspace through UI + host customize sheet

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task B10: Manual verification on emulator

Use the **verify** skill / an Android emulator (`emulator -avd Medium_Phone`; build+install `app/build/outputs/apk/debug/app-debug.apk`; package `com.meatrec.recorder.debug`). Screencap may be black under swiftshader — use `adb shell uiautomator dump` as ground truth.

- [ ] **Step 1: Pro markers visible (free user)**

With a free entitlement (use the debug-force-pro OFF state), confirm pro-gated quick controls (Slate, Live EQ, NR Gate, Edit EQ, Pre-roll, VAD) show the "★ PRO" badge and that tapping one opens the paywall to the correct slide. Pause/Drop cue have no badge and work normally.

- [ ] **Step 2: Customize editor — show/hide + reorder**

Open "✎ Customize layout", hide a control, move one up/down, tap Done. Confirm the Record screen reflects the new order/visibility. Reopen and confirm it persisted (kill + relaunch app).

- [ ] **Step 3: Free vs Pro layout scope**

As free user, confirm the layout is shared across modes (switch mode chip → same layout) and the "Save a separate layout per mode" row opens the `CUSTOM_WORKSPACE` paywall. Enable debug-force-pro, set different layouts in two modes, switch modes, confirm each mode shows its own layout.

- [ ] **Step 4: Record the evidence**

Capture uiautomator dumps / screenshots for each step and note the verdict (PASS/FAIL) per the verify skill's report format. No commit (verification only).

---

## Self-Review

**Spec coverage:**
- Pro marker system → Tasks A1–A3, applied in B7 (quick controls). ✅
- "★ PRO" pill style, hidden for Pro, paywall on tap → A1/A2, verified B10-1. ✅
- Customizable workspace A+C (show/hide + reorder, per-mode) → B1–B9. ✅
- Scope = in-recording quick controls only → `QuickControl` enum (B1) is exactly those eight; Settings card/meters/gain untouched. ✅
- Free = one shared layout, Pro = per-mode + `CUSTOM_WORKSPACE` gate → B4 (keys), B5 (resolution), A3 + B8 (paywall trigger). ✅
- Persistence in DataStore, versioned JSON, merge new controls → B3 (`schema`, `mergedWithDefaults`), B4. ✅
- Testing (serialize/deserialize, default-merge, gate routing) → B1/B2/B3 unit tests; gate routing + UI via B10 manual verify (no Compose UI-test deps in repo). ✅
- Reset to default (free) → B5 `resetToDefault`, B8 Reset button. ✅

**Deviation flagged:** reorder uses ▲/▼ buttons, not drag (no reorder lib); documented at top.

**Scope note:** the spec's Feature-2 table lists pro controls beyond the quick controls (Sample Rate/Bit Depth pills, External mic, Cloud backup, Pitch, Transcription, extra Themes) that live in other screens not read for this plan. This plan applies the marker to the **in-recording quick controls** (the primary surface). Extending `ProLock`/`ProBadge` to those other sites is mechanical (wrap each gated control, pass its `ProFeature`) and should be a short follow-up plan once those files are mapped — called out here so it is not mistaken as covered.

**Placeholder scan:** no TBD/TODO; every code step shows complete code. The only "match the existing name" notes (B4 `dataStore` receiver, B6 `settings`/`recorderMode`, B9 collection helper) are field-name confirmations against the real file, with the compiler as the check — not missing logic.

**Type consistency:** `WorkspaceLayout`/`WorkspaceItem`/`QuickControl`/`moveUp`/`moveDown`/`toggleVisible`/`mergedWithDefaults`/`WorkspaceLayoutJson.{toJsonString,fromJsonString}`/`WorkspaceManager.{layout,save,resetToDefault}` are used consistently across tasks. `ProLock(feature,isPro,onUpgrade,modifier,content)` and `ProBadge(modifier)` signatures match every call site.
