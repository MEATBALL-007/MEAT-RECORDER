# LoudnessManager Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the loudness/delivery cluster out of `RecorderViewModel` into a focused `LoudnessManager` collaborator, with the ViewModel delegating so its public API stays identical.

**Architecture:** Behavior-preserving extraction. Task 1 adds `LoudnessManager` (compiles standalone, unused). Task 2 rewires the ViewModel to delegate and deletes the moved members in one compile-coherent change. This is a structural move with no new JVM-testable logic, so each task's regression guard is **clean compile + the full existing unit suite (87 tests) staying green**, not red-green-refactor. The model-level `LoudnessTarget.encode/decode` tests already cover the persistence logic and must stay green untouched.

**Tech Stack:** Kotlin, Android (`Context`, `DocumentFile`, `ContentResolver`), kotlinx.coroutines (`StateFlow`, `Semaphore`), JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-08-loudnessmanager-extraction-design.md`

---

## File Structure

- **Create** `app/src/main/java/com/example/recorderproject/audio/LoudnessManager.kt` — owns loudness/delivery state (`target`, `isRendering`, `lastResult`), target setters with persistence, and local+SAF delivery rendering. One responsibility; matches the existing `audio/` collaborator pattern.
- **Modify** `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` — instantiate `LoudnessManager`, delegate the three StateFlows and two setters, call `loudness.renderFor(finalFile)` from `stopRecording`, call `loudness.applySnapshot(...)` from `applySnapshot`, delete the moved `renderDeliverySaf`, drop now-unused imports. Keep `rebindDeliveryResult` (mutates the VM-owned `_recordFiles`; passed in as a callback).

---

## Task 1: Add LoudnessManager (standalone, unused)

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/LoudnessManager.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.recorderproject.audio

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.model.DeliveryResult
import com.example.recorderproject.model.LoudnessTarget
import com.example.recorderproject.model.RecordFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/**
 * Owns the loudness/delivery target and the post-recording delivery render (local + SAF).
 * Extracted from RecorderViewModel (issue #13). Android-coupled (Context, DocumentFile),
 * so verified by compile + the app's existing suite + device smoke rather than unit tests.
 */
class LoudnessManager(
    private val app: Context,
    private val settings: SettingsDataStore,
    private val scope: CoroutineScope,
    private val saveDirectoryUri: () -> Uri?,
    private val onDeliveryResult: (srcPath: String, dstPath: String, result: DeliveryResult) -> Unit,
) {
    private val _target = MutableStateFlow<LoudnessTarget>(LoudnessTarget.DEFAULT)
    val target: StateFlow<LoudnessTarget> = _target

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering

    private val _lastResult = MutableStateFlow<DeliveryResult?>(null)
    val lastResult: StateFlow<DeliveryResult?> = _lastResult

    private val renderSemaphore = Semaphore(permits = 2)

    /** Hydrate the session target from persisted settings. Does not persist. */
    fun applySnapshot(key: String, customLufs: Float, customTp: Float) {
        _target.value = LoudnessTarget.decode(key, customLufs, customTp)
    }

    /** Change the active session target. Does not persist. */
    fun setSessionTarget(t: LoudnessTarget) {
        _target.value = t
    }

    /** Persist as default and update the session target. */
    fun saveAsDefault(t: LoudnessTarget) {
        _target.value = t
        scope.launch {
            val (key, lufs, tp) = LoudnessTarget.encode(t)
            settings.setDefaultLoudnessTarget(key)
            settings.setCustomLoudnessLufs(lufs)
            settings.setCustomLoudnessTpCeiling(tp)
        }
    }

    /** Render delivery for a just-finalized recording if a target is active. */
    fun renderFor(finalFile: RecordFile) {
        val t = _target.value
        if (t is LoudnessTarget.Off) return
        if (finalFile.path.startsWith("content://")) renderSaf(finalFile, t) else renderLocal(finalFile, t)
    }

    private fun renderLocal(finalFile: RecordFile, target: LoudnessTarget) {
        scope.launch(Dispatchers.IO) {
            renderSemaphore.withPermit {
                _isRendering.value = true
                try {
                    val src = File(finalFile.path)
                    val dst = File(src.parentFile, src.nameWithoutExtension + "_delivery.wav")
                    val result = runCatching { DeliveryRenderer.render(src, dst, target) }.getOrNull()
                    if (result != null) {
                        File(src.parentFile, src.nameWithoutExtension + "_delivery.json")
                            .writeText(DeliveryResult.toJson(result))
                        _lastResult.value = result
                        onDeliveryResult(src.absolutePath, dst.absolutePath, result)
                    } else {
                        _lastResult.value = null
                        if (dst.exists() && dst.length() < 100) dst.delete()
                    }
                } finally {
                    _isRendering.value = false
                }
            }
        }
    }

    private fun renderSaf(finalFile: RecordFile, target: LoudnessTarget) {
        val srcUri = Uri.parse(finalFile.path)
        val base = if (finalFile.name.contains('.')) finalFile.name.substringBeforeLast('.') else finalFile.name
        scope.launch(Dispatchers.IO) {
            renderSemaphore.withPermit {
                val tree = saveDirectoryUri()?.let { DocumentFile.fromTreeUri(app, it) } ?: return@withPermit
                val deliveryDoc = tree.createFile("audio/wav", "${base}_delivery.wav") ?: return@withPermit
                _isRendering.value = true
                try {
                    var result: DeliveryResult? = null
                    runCatching {
                        SafAudioBridge.processViaTemp(
                            cacheDir = app.cacheDir,
                            openInput = { app.contentResolver.openInputStream(srcUri) ?: error("cannot open source") },
                            openOutput = { app.contentResolver.openOutputStream(deliveryDoc.uri) ?: error("cannot open output") },
                            process = { s, d -> result = DeliveryRenderer.render(s, d, target) },
                        )
                    }
                    val r = result
                    if (r != null) {
                        tree.createFile("application/json", "${base}_delivery.json")?.let { jsonDoc ->
                            app.contentResolver.openOutputStream(jsonDoc.uri)?.use { out ->
                                out.write(DeliveryResult.toJson(r).toByteArray())
                            }
                        }
                        _lastResult.value = r
                        onDeliveryResult(finalFile.path, deliveryDoc.uri.toString(), r)
                    } else {
                        // render produced nothing usable (e.g. too short) — drop the empty doc.
                        _lastResult.value = null
                        runCatching { deliveryDoc.delete() }
                    }
                } finally {
                    _isRendering.value = false
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compile (the new class is unused; build stays green)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/LoudnessManager.kt
git commit -m "feat: add LoudnessManager collaborator (#13 step 1, not yet wired)"
```

---

## Task 2: Rewire RecorderViewModel to delegate, delete moved members

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` (imports line 33/39/40; state block 524-536; `stopRecording` block 1908-1942; `renderDeliverySaf` 2021-2066; setters 2802-2823; `applySnapshot` 2795-2799)

All edits below are interdependent — the build is only green after all of them. Apply Steps 1-6, then verify in Step 7.

- [ ] **Step 1: Replace the loudness state block with the manager + delegations**

Find (lines ~524-536):

```kotlin
    // D: current loudness delivery target (session-level)
    private val _loudnessTarget = MutableStateFlow<LoudnessTarget>(LoudnessTarget.DEFAULT)
    val loudnessTarget: StateFlow<LoudnessTarget> = _loudnessTarget

    // D: in-progress delivery render
    private val _isRenderingDelivery = MutableStateFlow(false)
    val isRenderingDelivery: StateFlow<Boolean> = _isRenderingDelivery

    // D: rolling render result for snackbar / UI
    private val _lastDeliveryResult = MutableStateFlow<DeliveryResult?>(null)
    val lastDeliveryResult: StateFlow<DeliveryResult?> = _lastDeliveryResult

    private val renderSemaphore = Semaphore(permits = 2)
```

Replace with:

```kotlin
    // D: loudness/delivery extracted into LoudnessManager (issue #13). The constructor lambda
    // and method reference defer-resolve _saveDirectoryUri / rebindDeliveryResult (declared later)
    // — legal because their bodies execute only when renderFor() is later called.
    private val loudness = com.example.recorderproject.audio.LoudnessManager(
        app = app,
        settings = settings,
        scope = viewModelScope,
        saveDirectoryUri = { _saveDirectoryUri.value },
        onDeliveryResult = ::rebindDeliveryResult,
    )
    val loudnessTarget: StateFlow<LoudnessTarget> = loudness.target
    val isRenderingDelivery: StateFlow<Boolean> = loudness.isRendering
    val lastDeliveryResult: StateFlow<DeliveryResult?> = loudness.lastResult
```

- [ ] **Step 2: Collapse the stopRecording auto-render block**

Find (lines ~1908-1942):

```kotlin
                // D: kick off delivery render if a target is active
                val deliveryTarget = _loudnessTarget.value
                if (deliveryTarget !is LoudnessTarget.Off && finalFile.path.startsWith("content://")) {
                    renderDeliverySaf(finalFile, deliveryTarget)
                } else if (deliveryTarget !is LoudnessTarget.Off) {
                    viewModelScope.launch(Dispatchers.IO) {
                        renderSemaphore.withPermit {
                            _isRenderingDelivery.value = true
                            try {
                                val src = java.io.File(finalFile.path)
                                val dst = java.io.File(
                                    src.parentFile,
                                    src.nameWithoutExtension + "_delivery.wav"
                                )
                                val result = runCatching {
                                    DeliveryRenderer.render(src, dst, deliveryTarget)
                                }.getOrNull()

                                if (result != null) {
                                    java.io.File(
                                        src.parentFile,
                                        src.nameWithoutExtension + "_delivery.json"
                                    ).writeText(DeliveryResult.toJson(result))
                                    _lastDeliveryResult.value = result
                                    rebindDeliveryResult(src.absolutePath, dst.absolutePath, result)
                                } else {
                                    _lastDeliveryResult.value = null
                                    if (dst.exists() && dst.length() < 100) dst.delete()
                                }
                            } finally {
                                _isRenderingDelivery.value = false
                            }
                        }
                    }
                }
```

Replace with:

```kotlin
                // D: kick off delivery render if a target is active
                loudness.renderFor(finalFile)
```

- [ ] **Step 3: Delegate the two target setters**

Find (lines ~2802-2823):

```kotlin
    /** Change the active session target. Does not persist. */
    fun setSessionLoudnessTarget(t: LoudnessTarget) {
        _loudnessTarget.value = t
    }

    /**
     * Persist as default + update session target.
     *
     * NOTE: SettingsDataStore exposes individual setters (not bulk apply).
     * If the setter names don't exist yet, call the closest equivalent — e.g.
     * settings.setDefaultLoudnessTarget(...), settings.setCustomLoudnessLufs(...),
     * settings.setCustomLoudnessTpCeiling(...). These were added in Task B3.
     */
    fun saveAsDefaultLoudnessTarget(t: LoudnessTarget) {
        _loudnessTarget.value = t
        viewModelScope.launch {
            val (key, lufs, tp) = LoudnessTarget.encode(t)
            settings.setDefaultLoudnessTarget(key)
            settings.setCustomLoudnessLufs(lufs)
            settings.setCustomLoudnessTpCeiling(tp)
        }
    }
```

Replace with:

```kotlin
    /** Change the active session target. Does not persist. */
    fun setSessionLoudnessTarget(t: LoudnessTarget) = loudness.setSessionTarget(t)

    /** Persist as default + update session target. */
    fun saveAsDefaultLoudnessTarget(t: LoudnessTarget) = loudness.saveAsDefault(t)
```

- [ ] **Step 4: Delegate loudness hydration in applySnapshot**

Find (lines ~2795-2799):

```kotlin
        _loudnessTarget.value = LoudnessTarget.decode(
            s.defaultLoudnessTarget,
            s.customLoudnessLufs,
            s.customLoudnessTpCeiling,
        )
```

Replace with:

```kotlin
        loudness.applySnapshot(
            s.defaultLoudnessTarget,
            s.customLoudnessLufs,
            s.customLoudnessTpCeiling,
        )
```

- [ ] **Step 5: Delete the now-moved renderDeliverySaf**

Find and delete entirely (lines ~2021-2066 — the blank line, doc comment, and function; leave `rebindDeliveryResult` directly above it intact):

```kotlin

    /**
     * Loudness/delivery render for a SAF (content://) recording. Bridges the document to a
     * cache temp, runs [DeliveryRenderer], and writes sibling `_delivery.wav` + `_delivery.json`
     * documents into the folder — the SAF counterpart of the local delivery render in stopRecording.
     */
    private fun renderDeliverySaf(finalFile: RecordFile, target: LoudnessTarget) {
        val srcUri = android.net.Uri.parse(finalFile.path)
        val base = if (finalFile.name.contains('.')) finalFile.name.substringBeforeLast('.') else finalFile.name
        viewModelScope.launch(Dispatchers.IO) {
            renderSemaphore.withPermit {
                val tree = _saveDirectoryUri.value?.let {
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(app, it)
                } ?: return@withPermit
                val deliveryDoc = tree.createFile("audio/wav", "${base}_delivery.wav") ?: return@withPermit
                _isRenderingDelivery.value = true
                try {
                    var result: DeliveryResult? = null
                    runCatching {
                        com.example.recorderproject.audio.SafAudioBridge.processViaTemp(
                            cacheDir = app.cacheDir,
                            openInput = { app.contentResolver.openInputStream(srcUri) ?: error("cannot open source") },
                            openOutput = { app.contentResolver.openOutputStream(deliveryDoc.uri) ?: error("cannot open output") },
                            process = { s, d -> result = DeliveryRenderer.render(s, d, target) },
                        )
                    }
                    val r = result
                    if (r != null) {
                        tree.createFile("application/json", "${base}_delivery.json")?.let { jsonDoc ->
                            app.contentResolver.openOutputStream(jsonDoc.uri)?.use { out ->
                                out.write(DeliveryResult.toJson(r).toByteArray())
                            }
                        }
                        _lastDeliveryResult.value = r
                        rebindDeliveryResult(finalFile.path, deliveryDoc.uri.toString(), r)
                    } else {
                        // render produced nothing usable (e.g. too short) — drop the empty doc.
                        _lastDeliveryResult.value = null
                        runCatching { deliveryDoc.delete() }
                    }
                } finally {
                    _isRenderingDelivery.value = false
                }
            }
        }
    }
```

- [ ] **Step 6: Remove the now-unused imports**

These three imports were only referenced by the moved code (`DeliveryRenderer.render`, `Semaphore`, `withPermit`). Delete each line:

```kotlin
import com.example.recorderproject.audio.DeliveryRenderer
```
```kotlin
import kotlinx.coroutines.sync.Semaphore
```
```kotlin
import kotlinx.coroutines.sync.withPermit
```

(`LoudnessTarget` stays — used by the setter signatures; `DeliveryResult` stays — used by `rebindDeliveryResult` and `scanRecordingsFromDisk`; `Dispatchers` stays — used elsewhere.)

- [ ] **Step 7: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all unit tests pass (still 87, including the unchanged `RecorderViewModelLoudnessTest`). If compilation fails on a "still used" import, restore that one import and re-run.

- [ ] **Step 8: Confirm the suite count and zero failures**

Run:
```bash
total=0; fail=0; err=0; for f in app/build/test-results/testDebugUnitTest/*.xml; do t=$(grep -oE 'tests="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); fl=$(grep -oE 'failures="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); e=$(grep -oE 'errors="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); total=$((total+t)); fail=$((fail+fl)); err=$((err+e)); done; echo "tests: $total failures: $fail errors: $err"
```
Expected: `tests: 87 failures: 0 errors: 0`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "refactor: delegate loudness/delivery to LoudnessManager (#13 step 1)"
```

---

## Device Walkthrough (manual acceptance — run after Task 2)

1. Set a loudness target, record to app storage → `<name>_delivery.wav` + `<name>_delivery.json` appear; delivery result shows in the UI.
2. Set a target, record into a SAF folder → `_delivery.wav` + `_delivery.json` appear in that folder; result shows.
3. Set target to Off → record → no delivery files produced.
4. "Save as default" a target → restart the app → the target is restored on launch.

---

## Self-Review

**Spec coverage:**
- `LoudnessManager` interface (target/isRendering/lastResult + applySnapshot/setSessionTarget/saveAsDefault/renderFor) → Task 1. ✓
- Owns its own `Semaphore(2)`; `renderLocal` + `renderSaf` moved → Task 1. ✓
- `rebindDeliveryResult` stays in VM, passed as `onDeliveryResult` → Task 2 Step 1 (`::rebindDeliveryResult`); not deleted. ✓
- Delegating vals / setter delegations → Task 2 Steps 1, 3. ✓
- `stopRecording` block → `loudness.renderFor(finalFile)` → Task 2 Step 2. ✓
- `applySnapshot` loudness lines → `loudness.applySnapshot(...)` → Task 2 Step 4. ✓
- Delete `renderDeliverySaf` → Task 2 Step 5. ✓
- `loudness` declared before delegating vals; lambdas defer-resolve → Task 2 Step 1 (declaration replaces the old block at the same location, above the vals). ✓
- Testing = compile + suite green (no artificial tests) → Tasks' verify steps + Task 2 Steps 7-8. ✓
- Scope = this cluster only → no other clusters touched. ✓

**Placeholder scan:** none — all code is complete; no TBD/TODO; the only "if it fails" guidance (Step 7) is a concrete recovery instruction, not a placeholder.

**Type consistency:** `LoudnessManager(app, settings, scope, saveDirectoryUri, onDeliveryResult)` constructor (Task 1) matches the call site (Task 2 Step 1). `target`/`isRendering`/`lastResult` property names match the delegations. `setSessionTarget`/`saveAsDefault`/`applySnapshot`/`renderFor` method names match all call sites. `onDeliveryResult(srcPath, dstPath, result)` signature matches `rebindDeliveryResult(srcPath, dstPath, r)`.
