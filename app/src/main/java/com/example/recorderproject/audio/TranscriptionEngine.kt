package com.example.recorderproject.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TranscriptionEngine(private val context: Context) {
    private val TAG = "TranscriptionEngine"

    fun transcribe(
        filePath: String,
        durationSeconds: Int,
        scope: CoroutineScope,
        onProgress: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("Speech recognition not available on this device")
                return@launch
            }
            onProgress("Preparing transcription…")
            try {
                val result = transcribeSuspend(filePath, durationSeconds)
                onResult(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed: ${e.message}", e)
                onError(e.message ?: "Transcription failed")
            }
        }
    }

    private suspend fun transcribeSuspend(
        filePath: String,
        durationSeconds: Int,
    ): String = suspendCancellableCoroutine { cont ->
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var mediaPlayer: MediaPlayer? = null

        fun cleanup() {
            try { mediaPlayer?.stop() } catch (_: Exception) {}
            try { mediaPlayer?.release() } catch (_: Exception) {}
            mediaPlayer = null
            try { recognizer.destroy() } catch (_: Exception) {}
            audioManager.mode = AudioManager.MODE_NORMAL
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = texts?.joinToString(" ") ?: ""
                cleanup()
                if (cont.isActive) cont.resume(text.ifBlank { "(No speech detected)" })
            }
            override fun onError(error: Int) {
                cleanup()
                if (cont.isActive) cont.resumeWithException(Exception(errorMessage(error)))
            }
            override fun onPartialResults(partial: Bundle?) {}
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        try {
            mediaPlayer = MediaPlayer().apply {
                @Suppress("DEPRECATION")
                setAudioStreamType(AudioManager.STREAM_VOICE_CALL)
                setDataSource(filePath)
                prepare()
            }
        } catch (e: Exception) {
            cleanup()
            if (cont.isActive) cont.resumeWithException(Exception("Cannot read audio file: ${e.message}"))
            return@suspendCancellableCoroutine
        }

        val listenMs = (durationSeconds * 1000L + 3000L).coerceAtLeast(5000L)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, listenMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, listenMs)
        }

        recognizer.startListening(intent)
        mediaPlayer!!.start()

        cont.invokeOnCancellation { cleanup() }
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio error — is another app using the microphone?"
        SpeechRecognizer.ERROR_CLIENT -> "Client error — please try again"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing RECORD_AUDIO permission"
        SpeechRecognizer.ERROR_NETWORK -> "No internet — go online or check offline speech is available"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout — try again"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized in this recording"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy — wait and retry"
        SpeechRecognizer.ERROR_SERVER -> "Server error — try again"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Recording too short or too quiet for transcription"
        else -> "Speech recognition error (code $code)"
    }
}
