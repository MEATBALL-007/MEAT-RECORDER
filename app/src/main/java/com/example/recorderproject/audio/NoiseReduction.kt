package com.example.recorderproject.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.example.recorderproject.data.FileLibrary
import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.model.RecordFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the noise-reduction processor, the enable toggle, and post-processing a recorded file
 * (local + SAF). Extracted from RecorderViewModel (issue #13 step 12).
 *
 * [enabled] is read by RecordingController at stop time to decide whether to run NR on the take;
 * [process] is the raw (blocking) call it uses. [applyTo] is the UI-triggered re-process of an
 * existing library entry, swapping it in place via [fileLibrary].
 *
 * @param saveDirectoryUri the SAF folder URI (owned by the VM) — needed for the SAF NR path.
 */
class NoiseReduction(
    private val app: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsDataStore,
    private val isHydrated: () -> Boolean,
    private val fileLibrary: FileLibrary,
    private val saveDirectoryUri: () -> Uri?,
) {
    private val processor = NoiseReductionProcessor()

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled

    /** Toggle from the UI — sets + persists. */
    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (isHydrated()) scope.launch { settings.setNoiseReduction(enabled) }
    }

    /** Set the flag in memory only (snapshot hydration + recorder-mode preset application). */
    fun setEnabledInMemory(enabled: Boolean) {
        _enabled.value = enabled
    }

    /** Raw (blocking) processing of a freshly-recorded file — called at stop time. */
    fun process(file: RecordFile): RecordFile = processor.process(file)

    /** UI-triggered: re-process an existing library entry and swap it in place. */
    fun applyTo(file: RecordFile) {
        if (file.path.startsWith("content://")) {
            applyToSaf(file)
            return
        }
        scope.launch {
            val updated = withContext(Dispatchers.IO) { processor.process(file) }
            fileLibrary.updateById(file.id) { updated }
        }
    }

    /**
     * Noise reduction for SAF (content://) sources. Bridges the document to a cache temp, runs
     * [NoiseReductionProcessor.processFile], and writes a sibling `_nr.wav` into the folder —
     * then swaps the list entry for the NR version, mirroring the local flow.
     */
    private fun applyToSaf(file: RecordFile) {
        val srcUri = Uri.parse(file.path)
        val base = if (file.name.contains('.')) file.name.substringBeforeLast('.') else file.name
        scope.launch(Dispatchers.IO) {
            try {
                val tree = saveDirectoryUri()?.let { DocumentFile.fromTreeUri(app, it) }
                    ?: throw IllegalStateException("Save folder unavailable")
                val nrDoc = tree.createFile("audio/wav", "${base}_nr.wav")
                    ?: throw IllegalStateException("Could not create NR file in folder")
                SafAudioBridge.processViaTemp(
                    cacheDir = app.cacheDir,
                    openInput = { app.contentResolver.openInputStream(srcUri) ?: error("cannot open source") },
                    openOutput = { app.contentResolver.openOutputStream(nrDoc.uri) ?: error("cannot open output") },
                    process = { s, d -> processor.processFile(s, d) },
                )
                val updated = file.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    name = nrDoc.name ?: "${base}_nr.wav",
                    path = nrDoc.uri.toString(),
                    hasNoiseReduction = true,
                )
                fileLibrary.updateById(file.id) { updated }
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Noise reduction applied", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "NR (SAF) failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Noise reduction failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object { private const val TAG = "NoiseReduction" }
}
