package com.example.recorderproject.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Energy-based Voice Activity Detector.
 * Calls [onVoiceDetected] when RMS energy exceeds [thresholdDb] dBFS
 * for [triggerWindowMs] continuous milliseconds.
 * After triggering, waits [cooldownMs] before triggering again.
 */
class VoiceActivityDetector(
    private val context: Context,
    private val thresholdDb: Float = -38f,
    private val triggerWindowMs: Int = 250,
    private val cooldownMs: Int = 3000,
) {
    private var job: Job? = null
    private val threshold = 10.0.pow(thresholdDb / 20.0).toFloat()

    var onVoiceDetected: (() -> Unit)? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { runVad() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runVad() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(1024)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) return

        try {
            recorder.startRecording()
            val buf = ShortArray(bufferSize / 2)
            var aboveMs = 0
            var lastTriggerMs = 0L

            while (coroutineContext.isActive) {
                val read = recorder.read(buf, 0, buf.size)
                if (read <= 0) continue
                var sum = 0.0
                for (i in 0 until read) sum += (buf[i] / 32768.0) * (buf[i] / 32768.0)
                val rms = sqrt(sum / read).toFloat()
                val chunkMs = (read * 1000) / sampleRate

                if (rms >= threshold) {
                    aboveMs += chunkMs
                    if (aboveMs >= triggerWindowMs) {
                        val now = System.currentTimeMillis()
                        if (now - lastTriggerMs >= cooldownMs) {
                            lastTriggerMs = now
                            withContext(Dispatchers.Main) { onVoiceDetected?.invoke() }
                        }
                        aboveMs = 0
                    }
                } else {
                    aboveMs = (aboveMs - chunkMs).coerceAtLeast(0)
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }

    private fun Double.pow(exp: Double): Double = Math.pow(this, exp)
    private fun Float.pow(exp: Float): Float = Math.pow(toDouble(), exp.toDouble()).toFloat()
}
