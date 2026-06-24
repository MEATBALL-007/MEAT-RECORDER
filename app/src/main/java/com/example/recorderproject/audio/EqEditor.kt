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
    private val markFileHasEq: (id: String) -> Unit,
    private val saveDirectoryUri: () -> Uri?,
) {
    /**
     * Notified with the new chain whenever the user edits the EQ, so the recorder can pick up
     * real-time edits while live-EQ recording. Set by the ViewModel after construction (it
     * forwards into RecordingController, which would otherwise be a forward reference). No-op
     * until wired.
     */
    var liveChainSink: (EQChain) -> Unit = {}

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
