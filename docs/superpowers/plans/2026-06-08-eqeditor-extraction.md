# EqEditor Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the offline EQ editor out of `RecorderViewModel` into an `EqEditor` collaborator backed by a pure, unit-tested `EqHistory`, with the ViewModel delegating so its public API stays identical.

**Architecture:** Task 1 TDDs the pure `EqHistory` undo/redo. Task 2 creates `EqEditor` (the full offline EQ logic moved verbatim, with cross-links expressed as injected callbacks); it compiles standalone, unused. Task 3 rewires the ViewModel: add the `eqEditor` field + seams, delegate the 11 EQ StateFlows and ~24 functions, delete the moved code, update the live-EQ/recording/monitor reads to `eqEditor.currentEQChain`, and split `applySnapshot`. Behavior-preserving; regression guard is clean compile + the full existing suite (98) staying green.

**Tech Stack:** Kotlin, Android, kotlinx.coroutines, JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-08-eqeditor-extraction-design.md`

---

## File Structure

- **Create** `app/src/main/java/com/example/recorderproject/audio/EqHistory.kt` — pure undo/redo stacks for `EQChain`. Unit-tested.
- **Create** `app/src/test/java/com/example/recorderproject/audio/EqHistoryTest.kt` — JVM tests.
- **Create** `app/src/main/java/com/example/recorderproject/audio/EqEditor.kt` — offline EQ editor (state + all `onEQ*` + apply/render + persistence), delegating cross-links to injected callbacks.
- **Modify** `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` — add `eqEditor` field + seams; delegate EQ state + functions; delete moved code (`eqHistory`/`eqRedo`/`EQ_HISTORY_CAP`, the EQ-actions block, `persistCurrentEqChain`); update the 4 live-EQ/monitor/recording chain reads + `applySnapshot` + `resetFactory`. Keep live-EQ (`_liveEqEnabled`, `_liveEqBandGains`, `toggleLiveEq`, `setLiveEqBand`).

---

## Task 1: EqHistory (pure undo/redo, TDD)

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/EqHistory.kt`
- Test: `app/src/test/java/com/example/recorderproject/audio/EqHistoryTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/recorderproject/audio/EqHistoryTest.kt`:

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.EQChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EqHistoryTest {

    // Three structurally-distinct chains (EQChain is a data class → value equality).
    private val a = EQChain.empty()
    private val b = EQChain.empty().copy(bypassed = true)
    private val c = EQChain.empty().copy(gainCompensation = true)

    @Test fun `undo returns the pushed pre-change state`() {
        val h = EqHistory()
        h.push(a)
        assertEquals(a, h.undo(b))
    }

    @Test fun `redo after undo restores the undone state`() {
        val h = EqHistory()
        h.push(a)
        val restored = h.undo(b)!!   // returns a; b moved to redo
        assertEquals(b, h.redo(restored))
    }

    @Test fun `a new push clears the redo stack`() {
        val h = EqHistory()
        h.push(a)
        h.undo(b)        // redo now holds b
        h.push(c)        // clears redo
        assertNull(h.redo(a))
    }

    @Test fun `undo on empty history returns null`() {
        assertNull(EqHistory().undo(a))
    }

    @Test fun `redo on empty stack returns null`() {
        assertNull(EqHistory().redo(a))
    }

    @Test fun `cap keeps only the most recent states`() {
        val h = EqHistory(cap = 2)
        h.push(a); h.push(b); h.push(c)   // 'a' evicted; undo stack = [b, c]
        assertEquals(c, h.undo(EQChain.empty()))
        assertEquals(b, h.undo(EQChain.empty()))
        assertNull(h.undo(EQChain.empty()))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.EqHistoryTest"`
Expected: FAIL — `Unresolved reference 'EqHistory'`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/recorderproject/audio/EqHistory.kt`:

```kotlin
package com.example.recorderproject.audio

import com.example.recorderproject.model.EQChain

/**
 * Undo/redo stacks for an EQChain editor. Pure (no Android types) → unit-testable.
 * Mirrors the original ViewModel mechanics: [push] records the pre-change state and clears
 * redo; [undo]/[redo] move the "current" state between the two stacks. Capped at [cap].
 */
class EqHistory(private val cap: Int = 10) {
    private val undoStack = ArrayDeque<EQChain>()
    private val redoStack = ArrayDeque<EQChain>()

    /** Record [current] as a pre-change checkpoint; clears redo; evicts oldest past [cap]. */
    fun push(current: EQChain) {
        undoStack.addLast(current)
        if (undoStack.size > cap) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Return the state to restore (or null if none); pushes [current] onto the redo stack. */
    fun undo(current: EQChain): EQChain? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return prev
    }

    /** Return the state to restore (or null if none); pushes [current] onto the undo stack. */
    fun redo(current: EQChain): EQChain? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.audio.EqHistoryTest"`
Expected: PASS (6 tests). If `EQChain.empty().copy(bypassed = ...)` or `EQChain` equality fails to compile/behave, check `app/src/main/java/com/example/recorderproject/model/EQChain.kt` — it is used with `.copy(bypassed = ...)` / `.copy(gainCompensation = ...)` in the existing ViewModel, so those fields exist; if `gainCompensation` is named differently, use that name. Do not change EqHistory's logic.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/EqHistory.kt \
        app/src/test/java/com/example/recorderproject/audio/EqHistoryTest.kt
git commit -m "feat: EqHistory pure undo/redo for EQ editor (#13 step 4)"
```

---

## Task 2: Create EqEditor (standalone, unused)

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/EqEditor.kt`

- [ ] **Step 1: Create the file**

Create `app/src/main/java/com/example/recorderproject/audio/EqEditor.kt` with EXACTLY this content. (`EQHumDetect`, `EQAutoDetect`, `EQCurveFitter`, `SpectrumAnalyzer`, `EQProcessor`, `SafAudioBridge`, `StaticSpectrum`, `EqHistory` all live in this same `audio` package, so they are referenced without imports.)

```kotlin
package com.example.recorderproject.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.model.ApplySaveMode
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQChainJson
import com.example.recorderproject.model.EQEditMode
import com.example.recorderproject.model.EQViewMode
import com.example.recorderproject.model.RecordFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "EqEditor"

/**
 * Offline EQ editor extracted from RecorderViewModel (issue #13 step 4): chain editing,
 * undo/redo, presets, hum/auto-detect, curve fit, tap-notch, source spectrum, apply/render
 * (local + SAF), and persistence. Live-EQ-during-recording stays in the ViewModel; this
 * editor exposes [currentEQChain] for it to read, and notifies edits via [liveChainSink].
 */
class EqEditor(
    private val app: Context,
    private val settings: SettingsDataStore,
    private val scope: CoroutineScope,
    private val isHydrated: () -> Boolean,
    private val requirePro: (ProFeature) -> Boolean,
    private val sampleRate: () -> Int,
    private val liveChainSink: (EQChain) -> Unit,
    private val markFileHasEq: (id: String) -> Unit,
    private val saveDirectoryUri: () -> Uri?,
) {
    private val history = EqHistory(cap = 10)

    private val _currentEQChain = MutableStateFlow(EQChain.empty())
    val currentEQChain: StateFlow<EQChain> = _currentEQChain

    private val _eqMode = MutableStateFlow(EQEditMode.PARAMETRIC)
    val eqMode: StateFlow<EQEditMode> = _eqMode

    private val _eqViewMode = MutableStateFlow(EQViewMode.TWO_D)
    val eqViewMode: StateFlow<EQViewMode> = _eqViewMode

    private val _eqSelectedBandId = MutableStateFlow<Int?>(null)
    val eqSelectedBandId: StateFlow<Int?> = _eqSelectedBandId

    private val _eqSnapshot = MutableStateFlow<EQChain?>(null)
    val eqSnapshot: StateFlow<EQChain?> = _eqSnapshot

    private val _eqApplySaveMode = MutableStateFlow(ApplySaveMode.BOTH)
    val eqApplySaveMode: StateFlow<ApplySaveMode> = _eqApplySaveMode

    /** -1f = idle, 0..1 = rendering, exactly 1f shows checkmark briefly. */
    private val _eqRenderProgress = MutableStateFlow(-1f)
    val eqRenderProgress: StateFlow<Float> = _eqRenderProgress

    private val _eqSourceFile = MutableStateFlow<RecordFile?>(null)
    val eqSourceFile: StateFlow<RecordFile?> = _eqSourceFile

    private val _eqSourceSpectrum = MutableStateFlow<StaticSpectrum?>(null)
    val eqSourceSpectrum: StateFlow<StaticSpectrum?> = _eqSourceSpectrum

    private val _eqBypassed = MutableStateFlow(false)
    val eqBypassed: StateFlow<Boolean> = _eqBypassed

    private val _eqOpen = MutableStateFlow(false)
    val eqOpen: StateFlow<Boolean> = _eqOpen

    private fun pushEqHistory(chain: EQChain) = history.push(chain)

    fun onEQOpen(file: RecordFile) {
        if (!requirePro(ProFeature.FULL_EQ)) return
        _eqSourceFile.value = file
        val srcPath = file.path
        if (!srcPath.startsWith("content://")) {
            val sidecar = File(srcPath.replace(Regex("\\.wav$", RegexOption.IGNORE_CASE), "_eq.json"))
            _currentEQChain.value = if (sidecar.exists()) {
                EQChainJson.fromJsonString(sidecar.readText()) ?: EQChain.empty()
            } else EQChain.empty()
        } else {
            _currentEQChain.value = EQChain.empty()
        }
        history.clear()
        _eqOpen.value = true

        scope.launch(Dispatchers.IO) {
            if (!srcPath.startsWith("content://")) {
                try {
                    val spec = SpectrumAnalyzer.analyzeFile(File(srcPath), bins = 256)
                    _eqSourceSpectrum.value = spec
                } catch (e: Exception) {
                    Log.e(TAG, "Spectrum compute failed: ${e.message}", e)
                    _eqSourceSpectrum.value = null
                }
            } else {
                _eqSourceSpectrum.value = null
            }
        }
    }

    fun onEQClose() {
        _eqSourceFile.value?.let { file ->
            if (!file.path.startsWith("content://")) {
                val sidecar = File(file.path.replace(Regex("\\.wav$", RegexOption.IGNORE_CASE), "_eq.json"))
                try {
                    sidecar.writeText(EQChainJson.toJsonString(_currentEQChain.value))
                } catch (e: Exception) {
                    Log.w(TAG, "Sidecar autosave failed: ${e.message}")
                }
            }
        }
        _eqOpen.value = false
        _eqSourceSpectrum.value = null
        _eqRenderProgress.value = -1f
    }

    fun onEQBandChanged(updated: com.example.recorderproject.model.EQBand) {
        pushEqHistory(_currentEQChain.value)
        _currentEQChain.value = _currentEQChain.value.withBand(updated)
        persistCurrentEqChain()
        liveChainSink(_currentEQChain.value)
    }

    fun onEQModeToggle(mode: EQEditMode) {
        _eqMode.value = mode
        if (isHydrated()) scope.launch { settings.setEqMode(mode.name) }
    }
    fun onEQViewModeToggle(mode: EQViewMode) {
        _eqViewMode.value = mode
        if (isHydrated()) scope.launch { settings.setEqViewMode(mode.name) }
    }
    fun onEQSelectBand(id: Int?) { _eqSelectedBandId.value = id }

    fun onEQUndo() {
        val prev = history.undo(_currentEQChain.value) ?: return
        _currentEQChain.value = prev
        persistCurrentEqChain()
    }

    fun onEQRedo() {
        val next = history.redo(_currentEQChain.value) ?: return
        _currentEQChain.value = next
        persistCurrentEqChain()
    }

    fun onEQABToggle() {
        val snap = _eqSnapshot.value
        if (snap == null) {
            _eqSnapshot.value = _currentEQChain.value
        } else {
            val current = _currentEQChain.value
            _currentEQChain.value = snap
            _eqSnapshot.value = current
            persistCurrentEqChain()
        }
    }

    fun onEQResetAll() {
        pushEqHistory(_currentEQChain.value)
        _currentEQChain.value = EQChain.empty()
        persistCurrentEqChain()
    }

    fun onEQPresetSelected(preset: com.example.recorderproject.model.EQPreset) {
        pushEqHistory(_currentEQChain.value)
        _currentEQChain.value = EQChain(bands = preset.bands)
        persistCurrentEqChain()
        liveChainSink(_currentEQChain.value)
    }

    fun onEQToggleBypass() {
        _currentEQChain.value = _currentEQChain.value.copy(bypassed = !_currentEQChain.value.bypassed)
        if (isHydrated()) scope.launch {
            settings.setEqBypassed(_currentEQChain.value.bypassed)
        }
    }

    fun onEQToggleGainCompensation() {
        _currentEQChain.value = _currentEQChain.value.copy(gainCompensation = !_currentEQChain.value.gainCompensation)
    }

    fun onEQHumDetect(mainsHz: Float = 60f) {
        pushEqHistory(_currentEQChain.value)
        val combNotches = EQHumDetect.combNotches(mainsHz)
        var chain = EQChain.empty()
        for ((idx, notch) in combNotches.withIndex()) {
            if (idx >= 8) break
            chain = chain.withBand(notch.copy(id = idx + 1))
        }
        _currentEQChain.value = chain
        persistCurrentEqChain()
        Toast.makeText(app, "Placed ${combNotches.size}-notch hum comb at ${mainsHz.toInt()} Hz", Toast.LENGTH_SHORT).show()
    }

    fun onEQRandomPreset() {
        pushEqHistory(_currentEQChain.value)
        _currentEQChain.value = com.example.recorderproject.model.EQRandomPreset.generate()
        persistCurrentEqChain()
    }

    fun onEQSaveAsCustomPreset(name: String) {
        val store = com.example.recorderproject.data.CustomPresetStore(app)
        store.save(name, _currentEQChain.value)
        Toast.makeText(app, "Saved preset: $name", Toast.LENGTH_SHORT).show()
    }

    fun onEQExportCurvePng() {
        scope.launch(Dispatchers.IO) {
            try {
                val path = com.example.recorderproject.ui.components.CurveBitmapExport
                    .exportToGallery(app, _currentEQChain.value, sampleRate().toFloat())
                withContext(Dispatchers.Main) {
                    if (path != null) Toast.makeText(app, "Curve PNG saved", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(app, "PNG save failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Curve PNG export failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "PNG export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun onEQNoiseAutoDetect() {
        val spec = _eqSourceSpectrum.value ?: return
        val suggestions = EQAutoDetect.proposeNotches(spec, maxBands = 4)
        _currentEQChain.value = _currentEQChain.value.copy(noiseCutSuggestions = suggestions)
        if (suggestions.isEmpty()) {
            Toast.makeText(app, "Spectrum is clean — no peaks detected", Toast.LENGTH_SHORT).show()
        }
    }

    fun onEQAcceptSuggestion(band: com.example.recorderproject.model.EQBand) {
        val current = _currentEQChain.value
        val added = current.withAddedBand(band) ?: run {
            Toast.makeText(app, "8-band limit reached — disable a band first", Toast.LENGTH_SHORT).show()
            return
        }
        pushEqHistory(current)
        _currentEQChain.value = added.copy(
            noiseCutSuggestions = current.noiseCutSuggestions.filter { it.id != band.id }
        )
        persistCurrentEqChain()
    }

    fun onEQRejectSuggestion(band: com.example.recorderproject.model.EQBand) {
        _currentEQChain.value = _currentEQChain.value.copy(
            noiseCutSuggestions = _currentEQChain.value.noiseCutSuggestions.filter { it.id != band.id }
        )
    }

    fun onEQDrawCurve(targetDbCurve: FloatArray) {
        val bands = EQCurveFitter.fitToCurve(targetDbCurve, 20f, 20_000f, maxBands = 6)
        if (bands.isEmpty()) return
        pushEqHistory(_currentEQChain.value)
        val padded = bands + (bands.size + 1..8).map { com.example.recorderproject.model.EQBand.defaultForSlot(it) }
        _currentEQChain.value = EQChain(bands = padded.take(8))
        persistCurrentEqChain()
    }

    fun onEQTapNotch(frequencyHz: Float) {
        val newBand = com.example.recorderproject.model.EQBand(
            id = 0,
            type = com.example.recorderproject.model.EQBandType.NOTCH,
            frequencyHz = frequencyHz,
            gainDb = 0f,
            q = 8f,
            enabled = true,
        )
        val added = _currentEQChain.value.withAddedBand(newBand) ?: run {
            Toast.makeText(app, "8-band limit reached — disable a band first", Toast.LENGTH_SHORT).show()
            return
        }
        pushEqHistory(_currentEQChain.value)
        _currentEQChain.value = added
        persistCurrentEqChain()
    }

    fun onEQSaveModeChange(mode: ApplySaveMode) {
        _eqApplySaveMode.value = mode
        if (isHydrated()) scope.launch { settings.setEqApplySaveMode(mode.name) }
    }

    fun onEQApply() {
        val src = _eqSourceFile.value ?: return
        val mode = _eqApplySaveMode.value
        if (mode == ApplySaveMode.ORIGINAL_ONLY) {
            _currentEQChain.value = EQChain.empty()
            onEQClose()
            return
        }
        if (src.path.startsWith("content://")) {
            onEQApplySaf(src, _currentEQChain.value, mode)
            return
        }
        val srcFile = File(src.path)
        val eqFile = File(srcFile.parentFile, srcFile.nameWithoutExtension + "_eq.wav")
        val chain = _currentEQChain.value
        scope.launch(Dispatchers.IO) {
            _eqRenderProgress.value = 0f
            try {
                EQProcessor.process(srcFile, eqFile, chain) { p ->
                    _eqRenderProgress.value = p
                }
                when (mode) {
                    ApplySaveMode.BOTH -> {
                        File(srcFile.parentFile, srcFile.nameWithoutExtension + "_eq.json")
                            .writeText(EQChainJson.toJsonString(chain))
                        markFileHasEq(src.id)
                    }
                    ApplySaveMode.EQ_ONLY -> {
                        val tmpRename = File(srcFile.parentFile, srcFile.name + ".replacing")
                        srcFile.renameTo(tmpRename)
                        if (eqFile.renameTo(srcFile)) {
                            tmpRename.delete()
                        } else {
                            tmpRename.renameTo(srcFile)
                            eqFile.delete()
                            throw RuntimeException("Atomic rename failed")
                        }
                        markFileHasEq(src.id)
                    }
                    ApplySaveMode.ORIGINAL_ONLY -> Unit
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "EQ applied", Toast.LENGTH_SHORT).show()
                    delay(600)
                    _eqRenderProgress.value = -1f
                    onEQClose()
                }
            } catch (e: Exception) {
                Log.e(TAG, "EQ render failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "EQ render failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                _eqRenderProgress.value = -1f
                eqFile.delete()
            }
        }
    }

    private fun onEQApplySaf(src: RecordFile, chain: EQChain, mode: ApplySaveMode) {
        if (mode == ApplySaveMode.ORIGINAL_ONLY) return
        val srcUri = Uri.parse(src.path)
        val base = if (src.name.contains('.')) src.name.substringBeforeLast('.') else src.name
        val runEq: (File, File) -> Unit = { s, d ->
            EQProcessor.process(s, d, chain) { p -> _eqRenderProgress.value = p }
        }
        scope.launch(Dispatchers.IO) {
            _eqRenderProgress.value = 0f
            try {
                when (mode) {
                    ApplySaveMode.BOTH -> {
                        val tree = saveDirectoryUri()?.let {
                            DocumentFile.fromTreeUri(app, it)
                        } ?: throw IllegalStateException("Save folder unavailable")
                        val eqDoc = tree.createFile("audio/wav", "${base}_eq.wav")
                            ?: throw IllegalStateException("Could not create EQ file in folder")
                        SafAudioBridge.processViaTemp(
                            cacheDir = app.cacheDir,
                            openInput = { app.contentResolver.openInputStream(srcUri) ?: error("cannot open source") },
                            openOutput = { app.contentResolver.openOutputStream(eqDoc.uri) ?: error("cannot open output") },
                            process = runEq,
                        )
                        tree.createFile("application/json", "${base}_eq.json")?.let { jsonDoc ->
                            app.contentResolver.openOutputStream(jsonDoc.uri)?.use { out ->
                                out.write(EQChainJson.toJsonString(chain).toByteArray())
                            }
                        }
                        markFileHasEq(src.id)
                    }
                    ApplySaveMode.EQ_ONLY -> {
                        SafAudioBridge.processViaTemp(
                            cacheDir = app.cacheDir,
                            openInput = { app.contentResolver.openInputStream(srcUri) ?: error("cannot open source") },
                            openOutput = { app.contentResolver.openOutputStream(srcUri, "wt") ?: error("cannot open output") },
                            process = runEq,
                        )
                        markFileHasEq(src.id)
                    }
                    ApplySaveMode.ORIGINAL_ONLY -> Unit
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "EQ applied", Toast.LENGTH_SHORT).show()
                    delay(600)
                    _eqRenderProgress.value = -1f
                    onEQClose()
                }
            } catch (e: Exception) {
                Log.e(TAG, "EQ render (SAF) failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "EQ render failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                _eqRenderProgress.value = -1f
            }
        }
    }

    /** Hydrate EQ state from persisted settings. Does not persist. */
    fun applySnapshot(chainJson: String, eqMode: String, eqViewMode: String, eqApplySaveMode: String, bypassed: Boolean) {
        _currentEQChain.value = runCatching { EQChainJson.fromJsonString(chainJson) }.getOrNull() ?: EQChain.empty()
        _eqMode.value = runCatching { EQEditMode.valueOf(eqMode) }.getOrDefault(EQEditMode.PARAMETRIC)
        _eqViewMode.value = runCatching { EQViewMode.valueOf(eqViewMode) }.getOrDefault(EQViewMode.TWO_D)
        _eqApplySaveMode.value = runCatching { ApplySaveMode.valueOf(eqApplySaveMode) }.getOrDefault(ApplySaveMode.BOTH)
        _currentEQChain.value = _currentEQChain.value.copy(bypassed = bypassed)
    }

    fun clearHistory() = history.clear()

    private fun persistCurrentEqChain() {
        if (!isHydrated()) return
        scope.launch {
            try {
                settings.setCurrentEqChainJson(EQChainJson.toJsonString(_currentEQChain.value))
            } catch (e: Exception) {
                Log.w(TAG, "EQ chain persist failed: ${e.message}")
            }
        }
    }
}
```

- [ ] **Step 2: Compile (the class is unused; build stays green)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If a helper name differs (e.g. `EQHumDetect.combNotches`, `EQAutoDetect.proposeNotches`, `EQCurveFitter.fitToCurve`, `SpectrumAnalyzer.analyzeFile`, `EQBand.defaultForSlot`, `EQRandomPreset.generate`, `CustomPresetStore`, `CurveBitmapExport.exportToGallery`), the original ViewModel code calls them with these exact names — they are correct; if compilation fails on one, report BLOCKED with the exact error rather than guessing.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/EqEditor.kt
git commit -m "feat: add EqEditor collaborator (#13 step 4, not yet wired)"
```

---

## Task 3: Rewire RecorderViewModel to delegate

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

Apply Steps 1–9, then verify in Step 10. Edits are interdependent — compile only after all are applied.

- [ ] **Step 1: Add the `eqEditor` field + seams (before the init block)**

Find the playback field added in step 3 (it sits just after `hydrated`):

```kotlin
    private val hydrated = MutableStateFlow(false)
```

Replace with:

```kotlin
    private val hydrated = MutableStateFlow(false)

    // Offline EQ editor extracted into EqEditor (issue #13 step 4). Declared before the init
    // block (hydration calls eqEditor.applySnapshot). Constructor lambdas defer-resolve
    // recorder / _liveEqEnabled / _sampleRate / _recordFiles / _saveDirectoryUri (legal — they
    // run only on later EQ calls).
    private val eqEditor = com.example.recorderproject.audio.EqEditor(
        app = app,
        settings = settings,
        scope = viewModelScope,
        isHydrated = { hydrated.value },
        requirePro = ::requirePro,
        sampleRate = { _sampleRate.value },
        liveChainSink = { chain -> if (_liveEqEnabled.value) recorder.setLiveEqChain(chain, _sampleRate.value.toFloat()) },
        markFileHasEq = { id -> _recordFiles.value = _recordFiles.value.map { if (it.id == id) it.copy(hasEQ = true) else it } },
        saveDirectoryUri = { _saveDirectoryUri.value },
    )
```

- [ ] **Step 2: Delegate the EQ state declarations**

Find (the EQ state block):

```kotlin
    // ------------- EQ state (Phase 1) -------------

    private val _currentEQChain = MutableStateFlow(EQChain.empty())
    val currentEQChain: StateFlow<EQChain> = _currentEQChain

    private val _eqMode = MutableStateFlow(EQEditMode.PARAMETRIC)
    val eqMode: StateFlow<EQEditMode> = _eqMode

    private val _eqViewMode = MutableStateFlow(EQViewMode.TWO_D)
    val eqViewMode: StateFlow<EQViewMode> = _eqViewMode

    private val _eqSelectedBandId = MutableStateFlow<Int?>(null)
    val eqSelectedBandId: StateFlow<Int?> = _eqSelectedBandId

    private val _eqSnapshot = MutableStateFlow<EQChain?>(null)
    val eqSnapshot: StateFlow<EQChain?> = _eqSnapshot

    private val _eqApplySaveMode = MutableStateFlow(ApplySaveMode.BOTH)
    val eqApplySaveMode: StateFlow<ApplySaveMode> = _eqApplySaveMode

    /** -1f = idle, 0..1 = rendering, exactly 1f shows checkmark briefly. */
    private val _eqRenderProgress = MutableStateFlow(-1f)
    val eqRenderProgress: StateFlow<Float> = _eqRenderProgress

    private val _eqSourceFile = MutableStateFlow<RecordFile?>(null)
    val eqSourceFile: StateFlow<RecordFile?> = _eqSourceFile

    private val _eqSourceSpectrum = MutableStateFlow<StaticSpectrum?>(null)
    val eqSourceSpectrum: StateFlow<StaticSpectrum?> = _eqSourceSpectrum

    private val _eqBypassed = MutableStateFlow(false)
    val eqBypassed: StateFlow<Boolean> = _eqBypassed

    /** True while the EQ screen should be shown — MainActivity observes for nav. */
    private val _eqOpen = MutableStateFlow(false)
    val eqOpen: StateFlow<Boolean> = _eqOpen
```

Replace with:

```kotlin
    // ------------- EQ state — delegated to EqEditor (issue #13 step 4) -------------
    val currentEQChain: StateFlow<EQChain> = eqEditor.currentEQChain
    val eqMode: StateFlow<EQEditMode> = eqEditor.eqMode
    val eqViewMode: StateFlow<EQViewMode> = eqEditor.eqViewMode
    val eqSelectedBandId: StateFlow<Int?> = eqEditor.eqSelectedBandId
    val eqSnapshot: StateFlow<EQChain?> = eqEditor.eqSnapshot
    val eqApplySaveMode: StateFlow<ApplySaveMode> = eqEditor.eqApplySaveMode
    val eqRenderProgress: StateFlow<Float> = eqEditor.eqRenderProgress
    val eqSourceFile: StateFlow<RecordFile?> = eqEditor.eqSourceFile
    val eqSourceSpectrum: StateFlow<StaticSpectrum?> = eqEditor.eqSourceSpectrum
    val eqBypassed: StateFlow<Boolean> = eqEditor.eqBypassed
    /** True while the EQ screen should be shown — MainActivity observes for nav. */
    val eqOpen: StateFlow<Boolean> = eqEditor.eqOpen
```

- [ ] **Step 3: Delete the undo/redo fields**

Find:

```kotlin
    /** Undo / redo stacks for the chain. Capped at EQ_HISTORY_CAP. */
    private val eqHistory = ArrayDeque<EQChain>()
    private val eqRedo = ArrayDeque<EQChain>()
    private val EQ_HISTORY_CAP = 10
```

Replace with nothing (delete all four lines).

- [ ] **Step 4: Replace the entire EQ-actions block with delegations**

The EQ actions form one contiguous region. Replace everything from this start line:

```kotlin
    // ============= EQ actions (Phase 1) =============
```

through the closing brace of `onEQApplySaf` (the `}` immediately followed by a blank line and then the comment `    /**` + `     * Push current in-memory recorder-related state into ...`).

That entire region (the `// ============= EQ actions (Phase 1) =============` header, `pushEqHistory`, every `onEQ*` function, and the private `onEQApplySaf`) is replaced by:

```kotlin
    // ============= EQ actions — delegated to EqEditor (issue #13 step 4) =============
    fun onEQOpen(file: RecordFile) = eqEditor.onEQOpen(file)
    fun onEQClose() = eqEditor.onEQClose()
    fun onEQBandChanged(updated: com.example.recorderproject.model.EQBand) = eqEditor.onEQBandChanged(updated)
    fun onEQModeToggle(mode: EQEditMode) = eqEditor.onEQModeToggle(mode)
    fun onEQViewModeToggle(mode: EQViewMode) = eqEditor.onEQViewModeToggle(mode)
    fun onEQSelectBand(id: Int?) = eqEditor.onEQSelectBand(id)
    fun onEQUndo() = eqEditor.onEQUndo()
    fun onEQRedo() = eqEditor.onEQRedo()
    fun onEQABToggle() = eqEditor.onEQABToggle()
    fun onEQResetAll() = eqEditor.onEQResetAll()
    fun onEQPresetSelected(preset: com.example.recorderproject.model.EQPreset) = eqEditor.onEQPresetSelected(preset)
    fun onEQToggleBypass() = eqEditor.onEQToggleBypass()
    fun onEQToggleGainCompensation() = eqEditor.onEQToggleGainCompensation()
    fun onEQHumDetect(mainsHz: Float = 60f) = eqEditor.onEQHumDetect(mainsHz)
    fun onEQRandomPreset() = eqEditor.onEQRandomPreset()
    fun onEQSaveAsCustomPreset(name: String) = eqEditor.onEQSaveAsCustomPreset(name)
    fun onEQExportCurvePng() = eqEditor.onEQExportCurvePng()
    fun onEQNoiseAutoDetect() = eqEditor.onEQNoiseAutoDetect()
    fun onEQAcceptSuggestion(band: com.example.recorderproject.model.EQBand) = eqEditor.onEQAcceptSuggestion(band)
    fun onEQRejectSuggestion(band: com.example.recorderproject.model.EQBand) = eqEditor.onEQRejectSuggestion(band)
    fun onEQDrawCurve(targetDbCurve: FloatArray) = eqEditor.onEQDrawCurve(targetDbCurve)
    fun onEQTapNotch(frequencyHz: Float) = eqEditor.onEQTapNotch(frequencyHz)
    fun onEQSaveModeChange(mode: ApplySaveMode) = eqEditor.onEQSaveModeChange(mode)
    fun onEQApply() = eqEditor.onEQApply()
```

(`pushEqHistory` and `onEQApplySaf` were private and are not delegated — they now live in `EqEditor`.)

- [ ] **Step 5: Delete `persistCurrentEqChain` from the ViewModel**

Find:

```kotlin
    private fun persistCurrentEqChain() {
        if (!hydrated.value) return
        viewModelScope.launch {
            try {
                settings.setCurrentEqChainJson(
                    EQChainJson.toJsonString(_currentEQChain.value)
                )
            } catch (e: Exception) {
                Log.w(TAG, "EQ chain persist failed: ${e.message}")
            }
        }
    }
```

Replace with nothing (delete the function). (The doc comment above it actually documents `applyDefaults` below; leave that comment in place.)

- [ ] **Step 6: Point the 4 live-EQ / monitor / recording chain reads at the editor**

Edit 6a — monitor. Find:
```kotlin
            audioMonitor.setChain(_currentEQChain.value)
```
Replace:
```kotlin
            audioMonitor.setChain(eqEditor.currentEQChain.value)
```

Edit 6b — recording start (the live-EQ branch). Find:
```kotlin
        if (_liveEqEnabled.value) {
            recorder.setLiveEqChain(_currentEQChain.value, _sampleRate.value.toFloat())
        } else {
            recorder.setLiveEqChain(null, _sampleRate.value.toFloat())
```
Replace:
```kotlin
        if (_liveEqEnabled.value) {
            recorder.setLiveEqChain(eqEditor.currentEQChain.value, _sampleRate.value.toFloat())
        } else {
            recorder.setLiveEqChain(null, _sampleRate.value.toFloat())
```

Edit 6c — toggleLiveEq. Find:
```kotlin
        if (_liveEqEnabled.value) {
            recorder.setLiveEqChain(_currentEQChain.value, _sampleRate.value.toFloat())
```
Replace:
```kotlin
        if (_liveEqEnabled.value) {
            recorder.setLiveEqChain(eqEditor.currentEQChain.value, _sampleRate.value.toFloat())
```
(If this exact 2-line snippet also matches Edit 6b's region, apply 6b first; 6b's snippet is longer/unique. This 6c snippet is inside `toggleLiveEq`.)

Edit 6d — stop sidecar write. Find:
```kotlin
                        File(srcFile.parentFile, srcFile.nameWithoutExtension + "_eq.json")
                            .writeText(com.example.recorderproject.model.EQChainJson.toJsonString(_currentEQChain.value))
```
Replace:
```kotlin
                        File(srcFile.parentFile, srcFile.nameWithoutExtension + "_eq.json")
                            .writeText(com.example.recorderproject.model.EQChainJson.toJsonString(eqEditor.currentEQChain.value))
```

- [ ] **Step 7: Split EQ hydration in `applySnapshot`**

Find:
```kotlin
        _currentEQChain.value = runCatching {
            EQChainJson.fromJsonString(s.currentEqChainJson)
        }.getOrNull() ?: EQChain.empty()
        _eqMode.value = runCatching { EQEditMode.valueOf(s.eqMode) }.getOrDefault(EQEditMode.PARAMETRIC)
        _eqViewMode.value = runCatching { EQViewMode.valueOf(s.eqViewMode) }.getOrDefault(EQViewMode.TWO_D)
        _eqApplySaveMode.value = runCatching { ApplySaveMode.valueOf(s.eqApplySaveMode) }
            .getOrDefault(ApplySaveMode.BOTH)
        _currentEQChain.value = _currentEQChain.value.copy(bypassed = s.eqBypassed)
```
Replace:
```kotlin
        eqEditor.applySnapshot(s.currentEqChainJson, s.eqMode, s.eqViewMode, s.eqApplySaveMode, s.eqBypassed)
```
(Leave the `_liveEqEnabled.value = s.liveEqEnabled` and `_liveEqBandGains.value = ...` lines above it untouched — live-EQ stays in the VM.)

- [ ] **Step 8: Update `resetFactory` history clear**

Find:
```kotlin
                eqHistory.clear()
                eqRedo.clear()
```
Replace:
```kotlin
                eqEditor.clearHistory()
```

- [ ] **Step 9: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass (104 total: prior 98 + 6 new `EqHistoryTest`).

If compilation fails with "unresolved reference `_currentEQChain` / `_eqMode` / `_eqViewMode` / `_eqSelectedBandId` / `_eqSnapshot` / `_eqApplySaveMode` / `_eqRenderProgress` / `_eqSourceFile` / `_eqSourceSpectrum` / `_eqBypassed` / `_eqOpen` / `eqHistory` / `eqRedo` / `pushEqHistory` / `persistCurrentEqChain` / `EQ_HISTORY_CAP`", a moved reference was missed — locate it. Reads of `_currentEQChain.value` that legitimately remain are ONLY the four handled in Step 6 (now `eqEditor.currentEQChain.value`); any other remaining `_*` EQ reference belongs to deleted code and must be removed/redirected. Do NOT reintroduce the deleted backing fields. Imports do not need changing (EQ types are still referenced by the delegating vals/params).

- [ ] **Step 10: Confirm suite count and zero failures**

Run:
```bash
total=0; fail=0; err=0; for f in app/build/test-results/testDebugUnitTest/*.xml; do t=$(grep -oE 'tests="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); fl=$(grep -oE 'failures="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); e=$(grep -oE 'errors="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); total=$((total+t)); fail=$((fail+fl)); err=$((err+e)); done; echo "tests: $total failures: $fail errors: $err"
```
Expected: `tests: 104 failures: 0 errors: 0`.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "refactor: delegate offline EQ editor to EqEditor (#13 step 4)"
```

---

## Device Walkthrough (manual acceptance — run after Task 3)

1. Open EQ on a local recording → existing `_eq.json` loads; source spectrum renders.
2. Edit a band, undo, redo; A/B snapshot toggle; reset-all; pick a preset; hum-detect; noise auto-detect accept/reject; draw curve; tap-notch — all behave as before.
3. Apply (BOTH) → `_eq.wav` + `_eq.json` written, EQ badge appears; Apply (EQ_ONLY) replaces in place; both work for a SAF-folder recording.
4. With live-EQ enabled during recording, editing a band still applies in real time, and the take gets its EQ sidecar.
5. Export curve PNG works.
6. EQ chain, mode, view mode, save mode, and bypass restored after an app restart.

---

## Self-Review

**Spec coverage:**
- `EqHistory` pure undo/redo → Task 1. ✓
- `EqEditor` with the 9 seams, 11 StateFlows, all `onEQ*`, apply/render, persistence, applySnapshot, clearHistory → Task 2. ✓
- Substitutions (liveChainSink / markFileHasEq / saveDirectoryUri / requirePro / sampleRate / isHydrated / scope / EqHistory) → Task 2 bodies. ✓
- VM delegates state + functions; live-EQ stays → Task 3 Steps 2, 4 (+ live-EQ untouched). ✓
- `eqEditor` declared before init → Task 3 Step 1. ✓
- Cross-link reads redirected (monitor, record-start, toggleLiveEq, stop-sidecar) → Task 3 Step 6. ✓
- applySnapshot split, resetFactory clear, history fields + persist deleted → Task 3 Steps 3, 5, 7, 8. ✓
- Tests: EqHistory TDD'd; EqEditor = compile + suite green → Task 1 + Task 3 Steps 9–10. ✓
- Scope = offline editor only; live-EQ deferred → Task 3 keeps live-EQ. ✓

**Placeholder scan:** none — full code for both new files; Task 3 edits are exact find/replace except Step 4 which is a bounded contiguous-region replacement with precise start/end anchors and the complete replacement block.

**Type consistency:** `EqEditor(app, settings, scope, isHydrated, requirePro, sampleRate, liveChainSink, markFileHasEq, saveDirectoryUri)` (Task 2) matches the Task 3 Step 1 call. The 11 delegated property names + 24 delegated function signatures match between `EqEditor` (Task 2) and the VM delegations (Task 3 Steps 2, 4). `EqHistory(cap)` + `push`/`undo`/`redo`/`clear` (Task 1) match `EqEditor`'s usage (Task 2). `applySnapshot(chainJson, eqMode, eqViewMode, eqApplySaveMode, bypassed)` matches the Step 7 call. `clearHistory()` matches Step 8.
```
