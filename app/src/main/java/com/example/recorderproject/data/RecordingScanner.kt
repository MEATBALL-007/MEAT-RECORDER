package com.example.recorderproject.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.recorderproject.model.DeliveryResult
import com.example.recorderproject.model.RecordFile
import java.io.File
import java.util.UUID

/**
 * Reconstructs the recording library from internal app storage and/or a SAF folder, returning
 * List<RecordFile>; the caller owns the list. Android/I/O-coupled — all naming/filtering
 * decisions delegate to the pure, unit-tested [RecordingNaming].
 */
class RecordingScanner(private val app: Context) {

    /** Scan internal app storage. Also sweeps orphaned `_delivery.wav` files. */
    fun scanDisk(existingPaths: Set<String>): List<RecordFile> {
        val dir = File(app.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings")
        if (!dir.exists()) return emptyList()
        val allWavs = dir.listFiles { f -> f.isFile && f.extension.equals("wav", ignoreCase = true) }
            ?.toList() ?: return emptyList()

        val nrShadowed = RecordingNaming.nrShadowedBases(allWavs.map { it.nameWithoutExtension })

        val scanned = allWavs
            .filter { f ->
                val base = f.nameWithoutExtension
                !RecordingNaming.isHidden(base, nrShadowed) && f.absolutePath !in existingPaths
            }
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f ->
                try {
                    val base = f.nameWithoutExtension
                    val hasNr = RecordingNaming.hasNr(base)
                    val hasEq = File(f.parentFile, RecordingNaming.eqSidecarName(base)).exists()

                    val durationSeconds = try {
                        val mmr = MediaMetadataRetriever()
                        mmr.setDataSource(f.absolutePath)
                        val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        mmr.release()
                        (ms / 1000L).toInt()
                    } catch (_: Exception) { 0 }

                    val deliveryWav = File(f.parentFile, RecordingNaming.deliveryWavName(base))
                    val deliveryJson = File(f.parentFile, RecordingNaming.deliveryJsonName(base))
                    val deliveryResult = runCatching {
                        if (deliveryJson.exists()) DeliveryResult.fromJson(deliveryJson.readText()) else null
                    }.getOrNull()
                    val deliveryPath = if (deliveryWav.exists()) deliveryWav.absolutePath else null

                    RecordFile(
                        id = UUID.randomUUID().toString(),
                        name = f.name,
                        path = f.absolutePath,
                        durationSeconds = durationSeconds,
                        sceneName = "",
                        hasNoiseReduction = hasNr,
                        hasEQ = hasEq,
                        deliveryPath = deliveryPath,
                        deliveryResult = deliveryResult,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "scanDisk: skipping ${f.name}: ${e.message}")
                    null
                }
            }

        // Orphan delivery sweep: `_delivery.wav` with no original and no sidecar JSON.
        val originalBases = allWavs
            .filter { !RecordingNaming.isDeliverySibling(it.nameWithoutExtension) }
            .map { it.nameWithoutExtension }
            .toSet()
        allWavs
            .filter { f ->
                val base = f.nameWithoutExtension
                val sidecar = File(f.parentFile, "${base}.json")
                RecordingNaming.isOrphanDelivery(base, originalBases, sidecar.exists())
            }
            .forEach { runCatching { it.delete() } }

        return scanned
    }

    /** Scan a SAF (folder-picker) save location. Best-effort and fully guarded; never throws. */
    fun scanSaf(treeUri: Uri, existingPaths: Set<String>): List<RecordFile> {
        val tree = try { DocumentFile.fromTreeUri(app, treeUri) } catch (_: Exception) { null } ?: return emptyList()
        val docs = try { tree.listFiles().toList() } catch (_: Exception) { return emptyList() }

        // name -> doc, for sidecar/companion (_eq.json, _delivery.*) lookups
        val byName = docs.mapNotNull { d -> d.name?.let { it to d } }.toMap()
        val wavs = docs.filter { it.isFile && (it.name?.endsWith(".wav", ignoreCase = true) == true) }
        val nrShadowed = RecordingNaming.nrShadowedBases(wavs.mapNotNull { it.name }.map { RecordingNaming.baseOf(it) })

        return wavs
            .sortedByDescending { it.lastModified() }
            .mapNotNull { doc ->
                try {
                    val fullName = doc.name ?: return@mapNotNull null
                    val base = RecordingNaming.baseOf(fullName)
                    if (RecordingNaming.isHidden(base, nrShadowed)) return@mapNotNull null
                    val path = doc.uri.toString()
                    if (path in existingPaths) return@mapNotNull null

                    val durationSeconds = try {
                        val mmr = MediaMetadataRetriever()
                        mmr.setDataSource(app, doc.uri)
                        val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        mmr.release()
                        (ms / 1000L).toInt()
                    } catch (_: Exception) { 0 }

                    val deliveryDoc = byName[RecordingNaming.deliveryWavName(base)]
                    val deliveryResult = runCatching {
                        byName[RecordingNaming.deliveryJsonName(base)]?.let { d ->
                            app.contentResolver.openInputStream(d.uri)?.use { ins ->
                                DeliveryResult.fromJson(ins.readBytes().decodeToString())
                            }
                        }
                    }.getOrNull()

                    RecordFile(
                        id = UUID.randomUUID().toString(),
                        name = fullName,
                        path = path,
                        durationSeconds = durationSeconds,
                        sceneName = "",
                        hasNoiseReduction = RecordingNaming.hasNr(base),
                        hasEQ = byName.containsKey(RecordingNaming.eqSidecarName(base)),
                        deliveryPath = deliveryDoc?.uri?.toString(),
                        deliveryResult = deliveryResult,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "scanSaf: skipping ${doc.name}: ${e.message}")
                    null
                }
            }
    }

    companion object { private const val TAG = "RecordingScanner" }
}
