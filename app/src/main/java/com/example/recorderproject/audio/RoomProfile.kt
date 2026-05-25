package com.example.recorderproject.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.*

data class RoomProfile(
    val noiseFloorDb: Float,
    val rt60Ms: Float,
    val dominantFreqHz: Float,
    val qualityStars: Int
)

class RoomProfiler(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun analyze(durationMs: Long = 2000L): RoomProfile? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return null

        val sampleRate = 48000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
            )
        } catch (_: Exception) { return null }

        val totalSamples = (sampleRate * durationMs / 1000).toInt()
        val captured = ShortArray(totalSamples)
        var pos = 0

        try {
            recorder.startRecording()
        } catch (e: Exception) {
            recorder.release(); return null
        }
        val buf = ShortArray(bufferSize)
        try {
            while (pos < totalSamples) {
                val read = recorder.read(buf, 0, minOf(bufferSize, totalSamples - pos))
                if (read <= 0) break
                System.arraycopy(buf, 0, captured, pos, read)
                pos += read
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()
        }

        // TODO: Original analysis (RMS noise floor, RT60 estimation, dominant freq via FFT,
        // quality stars rubric) was lost in 2026-05-25 iCloud eviction. Stubbed to return null
        // until rebuilt. captured: ShortArray length = sampleRate * durationMs / 1000.
        return null
    }
}

