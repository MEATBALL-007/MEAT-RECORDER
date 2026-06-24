package com.example.recorderproject.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import android.widget.Toast
import com.example.recorderproject.model.RecordFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Populates the [FileLibrary] from disk/SAF on launch and recovers crash-interrupted takes.
 * Extracted from RecorderViewModel (issue #13 step 14). Wraps the pure [RecordingScanner] with
 * FileLibrary integration + launch-time recovery (PR1 periodic finalize leaves a playable WAV;
 * the active-recording marker + isRecording flag let us surface it after a crash).
 */
class LibraryScanner(
    private val app: Context,
    private val settings: SettingsDataStore,
    private val fileLibrary: FileLibrary,
    private val saveLocation: SaveLocation,
) {
    private val scanner = RecordingScanner(app)

    /**
     * Populate the library from internal storage on launch. Detects NR (`_nr.wav`) and EQ
     * (`_eq.json` sidecar) flags. If both `foo.wav` and `foo_nr.wav` exist, only the NR
     * version shows — the user-facing artifact. Also used as the stop-error rescan net.
     */
    fun scanDisk() {
        val added = scanner.scanDisk(fileLibrary.current().map { it.path }.toSet())
        if (added.isNotEmpty()) {
            fileLibrary.addAll(added)
            Log.i(TAG, "scanDisk: added ${added.size} files from disk")
        }
    }

    /**
     * Like [scanDisk] but for a SAF (folder-picker) save location. Those recordings are
     * content:// documents the internal-storage scan never sees — without this they vanish from
     * the list after a restart though the files are intact. Best-effort; never throws.
     */
    suspend fun scanSaf() = withContext(Dispatchers.IO) {
        val uri = saveLocation.uri.value ?: return@withContext
        val added = scanner.scanSaf(uri, fileLibrary.current().map { it.path }.toSet())
        if (added.isNotEmpty()) {
            fileLibrary.addAll(added)
            Log.i(TAG, "scanSaf: added ${added.size} files from SAF folder")
        }
    }

    /**
     * Recover a take interrupted by a crash/kill in a previous session: surface the
     * still-playable active WAV in the list, then clear the active-path + isRecording markers.
     * Shows Main-thread toasts, so call from a Main-dispatched coroutine.
     */
    suspend fun recoverCrashedTake() {
        val activePath = settings.getActiveRecordingPath()
        if (activePath != null) {
            val recoveredFile = java.io.File(activePath)
            if (recoveredFile.exists() && recoveredFile.length() > 44L) {
                // Build a RecordFile entry — best-effort metadata.
                val durationSeconds = try {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(activePath)
                    val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    mmr.release()
                    (ms / 1000L).toInt()
                } catch (_: Exception) { 0 }
                val recovered = RecordFile(
                    id = java.util.UUID.randomUUID().toString(),
                    name = recoveredFile.name,
                    path = recoveredFile.absolutePath,
                    durationSeconds = durationSeconds,
                    sceneName = "Recovered",
                )
                fileLibrary.add(recovered)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Recovered take from previous session: ${recoveredFile.name}",
                        Toast.LENGTH_LONG).show()
                }
            }
            // Clear the marker either way — we've handled it (or the file doesn't exist).
            try { settings.setActiveRecordingPath(null) } catch (_: Exception) {}
        }
        // Detect zombie recording: previous session was recording when destroyed.
        val wasRecording = try { settings.getIsRecording() } catch (_: Exception) { false }
        if (wasRecording) {
            try { settings.setIsRecording(false) } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "Previous recording recovered — check Recordings list", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object { private const val TAG = "LibraryScanner" }
}
