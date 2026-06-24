package com.example.recorderproject.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the user's chosen SAF "save folder" — the persisted tree URI and its persistable
 * permission grant. Extracted from RecorderViewModel (issue #13 step 13). Several collaborators
 * (EqEditor, LoudnessManager, NoiseReduction, RecordingController) read [uri] via a seam to
 * decide where to write; null means recordings go to private app storage.
 */
class SaveLocation(
    private val app: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsDataStore,
    private val isHydrated: () -> Boolean,
) {
    private val _uri = MutableStateFlow<Uri?>(null)
    val uri: StateFlow<Uri?> = _uri

    /** User picked a folder: take the persistable grant so it survives process death, persist. */
    fun set(uri: Uri) {
        _uri.value = uri
        try {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not take persistable SAF permission: ${e.message}")
        }
        if (isHydrated()) scope.launch { settings.setSaveDirectoryUri(uri.toString()) }
    }

    /** Hydrate the persisted URI from a snapshot (in-memory only; no grant re-claim). */
    fun applySnapshot(uriStr: String?) {
        _uri.value = uriStr?.let { Uri.parse(it) }
    }

    /**
     * On launch, re-claim the persistable grant for the hydrated URI. If the grant is gone
     * (folder deleted / permission revoked), clear it and inform the user so recordings fall
     * back to app storage rather than failing silently. Call from the hydration coroutine.
     */
    suspend fun reclaimPersistedGrant() {
        val uri = _uri.value ?: return
        try {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Persisted SAF URI no longer granted, clearing: ${e.message}")
            _uri.value = null
            settings.setSaveDirectoryUri(null)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    app,
                    "⚠️ Save folder permission expired — recordings will be saved to App Storage. Go to Settings → Save Location to re-select your folder.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    companion object { private const val TAG = "SaveLocation" }
}
