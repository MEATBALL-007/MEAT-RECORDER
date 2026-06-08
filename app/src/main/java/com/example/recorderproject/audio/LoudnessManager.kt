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
