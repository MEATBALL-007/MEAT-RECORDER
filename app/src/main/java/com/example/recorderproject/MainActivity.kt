package com.example.recorderproject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recorderproject.ui.EQScreen
import com.example.recorderproject.ui.RecorderApp
import com.example.recorderproject.ui.theme.RecorderProjectTheme
import android.widget.Toast

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<RecorderViewModel>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val storageGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        if (audioGranted && storageGranted) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            viewModel.onPermissionGranted()
            viewModel.startRecording()
        } else {
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_LONG).show()
            viewModel.onPermissionDenied()
        }
    }

    private val directoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.setSaveDirectoryUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecorderProjectTheme {
                val eqOpen by viewModel.eqOpen.collectAsStateWithLifecycle()
                if (eqOpen) {
                    EQScreen(
                        viewModel = viewModel,
                        onBack = { /* viewModel.onEQClose() already toggles eqOpen=false */ },
                    )
                } else {
                    RecorderApp(
                        viewModel = viewModel,
                        onStartRecording = { requestRecordingPermissions() },
                        onSelectSaveLocation = { selectSaveDirectory() },
                        onRequestPermission = { requestRecordingPermissions() },
                        onOpenEQOnLast = {
                            val last = viewModel.recordFiles.value.lastOrNull()
                            if (last != null) {
                                viewModel.onEQOpen(last)
                            } else {
                                Toast.makeText(this, "No recordings yet — record something first", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun requestRecordingPermissions() {
        val audioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        val storagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        android.util.Log.d("MainActivity", "Audio permission: $audioPermission (GRANTED=${PackageManager.PERMISSION_GRANTED})")
        android.util.Log.d("MainActivity", "Storage permission: $storagePermission (GRANTED=${PackageManager.PERMISSION_GRANTED})")

        if (audioPermission == PackageManager.PERMISSION_GRANTED &&
            storagePermission == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Starting recording...", Toast.LENGTH_SHORT).show()
            viewModel.startRecording()
        } else {
            Toast.makeText(this, "Requesting permissions...", Toast.LENGTH_SHORT).show()
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun selectSaveDirectory() {
        directoryLauncher.launch(null)
    }
}
