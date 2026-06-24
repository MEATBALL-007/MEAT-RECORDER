package com.example.recorderproject.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.network.GoogleDriveUploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns cloud backup of finished takes — Google-Drive auto-upload and the SAF "cloud folder"
 * copy — plus the enable toggle, the persisted folder URI, and Drive sign-in state. Extracted
 * from RecorderViewModel (issue #13 step 10). Android/network-coupled (Drive, SAF, Toast), so
 * verified by compile + device smoke rather than unit tests.
 *
 * @param requirePro gate: turning cloud backup ON is a Pro feature.
 */
class CloudBackup(
    private val app: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsDataStore,
    private val isHydrated: () -> Boolean,
    private val requirePro: (ProFeature) -> Boolean,
) {
    private val driveUploader = GoogleDriveUploader(app.applicationContext)

    private val _isDriveSignedIn = MutableStateFlow(driveUploader.isSignedIn())
    val isDriveSignedIn: StateFlow<Boolean> = _isDriveSignedIn

    fun refreshSignInState() {
        _isDriveSignedIn.value = driveUploader.isSignedIn()
    }

    /** G20: cloud backup toggle. Turning it on prompts for a folder if none is set. */
    private val _cloudBackupOn = MutableStateFlow(false)
    val cloudBackupOn: StateFlow<Boolean> = _cloudBackupOn
    fun toggle() {
        // Turning cloud backup ON requires Pro; turning it off is always allowed.
        if (!_cloudBackupOn.value && !requirePro(ProFeature.CLOUD_BACKUP)) return
        _cloudBackupOn.value = !_cloudBackupOn.value
        if (_cloudBackupOn.value && _cloudBackupUri.value == null) {
            Toast.makeText(app, "Pick a cloud folder in Settings → Cloud Backup Folder", Toast.LENGTH_LONG).show()
        }
        if (isHydrated()) scope.launch { settings.setCloudBackup(_cloudBackupOn.value) }
    }

    /** L.2: Persisted SAF URI for the cloud backup folder. */
    private val _cloudBackupUri = MutableStateFlow<Uri?>(null)
    val cloudBackupUri: StateFlow<Uri?> = _cloudBackupUri

    fun setUri(uri: Uri?) {
        _cloudBackupUri.value = uri
        if (uri != null) {
            try {
                app.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Cloud backup URI grant failed: ${e.message}")
            }
        }
        if (isHydrated()) scope.launch { settings.setCloudBackupUri(uri?.toString()) }
    }

    /** Upload a finished file to Google Drive (no-op unless enabled + signed in). */
    fun backupToDrive(filePath: String) {
        if (!_cloudBackupOn.value) return
        if (!driveUploader.isSignedIn()) return
        scope.launch {
            val file = java.io.File(filePath)
            if (!file.exists()) return@launch
            val id = driveUploader.upload(file)
            if (id != null) {
                Toast.makeText(app, "Backed up: ${file.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(app, "Drive backup failed — check internet connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Called when a take is finalized (RecordingController.onTakeSaved seam): Drive auto-upload
     * + SAF "cloud folder" copy. [capturedFilePath] is the local WAV path captured before
     * recorder.stop() cleared it.
     */
    fun onTakeSaved(finalFile: RecordFile, capturedFilePath: String?) {
        // Google Drive backup — auto-upload to Drive if signed in and enabled.
        capturedFilePath?.let { backupToDrive(it) }
        // L.2: Cloud backup — copy the WAV to the chosen SAF folder.
        val cloudUri = _cloudBackupUri.value
        if (_cloudBackupOn.value && cloudUri != null && !finalFile.path.startsWith("content://")) {
            scope.launch(Dispatchers.IO) {
                try {
                    val srcFile = java.io.File(finalFile.path)
                    val tree = DocumentFile.fromTreeUri(app, cloudUri)
                    val target = tree?.createFile("audio/wav", srcFile.name)
                    if (target != null) {
                        app.contentResolver.openOutputStream(target.uri).use { out ->
                            srcFile.inputStream().use { it.copyTo(out!!) }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(app, "Backed up to cloud folder", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Cloud backup copy failed: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "Cloud backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /** Hydrate persisted enable flag + folder URI from a snapshot (in-memory only). */
    fun applySnapshot(cloudBackup: Boolean, cloudBackupUri: String?) {
        _cloudBackupOn.value = cloudBackup
        _cloudBackupUri.value = cloudBackupUri?.let { Uri.parse(it) }
    }

    companion object { private const val TAG = "CloudBackup" }
}
