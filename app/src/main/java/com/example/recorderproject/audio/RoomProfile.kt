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

        if (pos < 2048) return null
        val actual = if (pos < totalSamples) captured.copyOf(pos) else captured
        return analyzeBuffer(actual, sampleRate)
    }

    /**
     * Pure analysis of a captured PCM mono buffer — exposed so it can be unit-tested
     * without needing a real microphone.
     */
    fun analyzeBuffer(captured: ShortArray, sampleRate: Int): RoomProfile {
        // ---- Noise floor: bottom 10% RMS of overlapping 50ms windows.
        val winSize = (sampleRate * 0.05f).toInt().coerceAtLeast(256)
        val rmsWindows = mutableListOf<Float>()
        var i = 0
        while (i + winSize <= captured.size) {
            var sumSq = 0.0
            for (j in 0 until winSize) {
                val s = captured[i + j].toDouble() / 32768.0
                sumSq += s * s
            }
            val rms = sqrt(sumSq / winSize).toFloat()
            rmsWindows.add(rms)
            i += winSize / 2
        }
        rmsWindows.sort()
        val floorRms = if (rmsWindows.isNotEmpty())
            rmsWindows.take(maxOf(1, rmsWindows.size / 10)).average().toFloat()
        else 1e-6f
        val noiseFloorDb = (20.0 * log10(maxOf(floorRms, 1e-6f).toDouble())).toFloat()

        // ---- Dominant frequency: bin-peak from first FFT frame.
        val spectrum = FFTAnalyzer.frameSpectrum(captured, 0, minOf(FFTAnalyzer.FFT_SIZE, captured.size), sampleRate)
        var peakIdx = 0
        var peakVal = 0f
        for (b in spectrum.indices) {
            if (spectrum[b] > peakVal) { peakVal = spectrum[b]; peakIdx = b }
        }
        // log-bands: 20 Hz → nyquist; convert band index back to representative Hz.
        val logMin = log10(20.0)
        val logMax = log10((sampleRate / 2.0).coerceAtLeast(21.0))
        val frac = peakIdx.toDouble() / FFTAnalyzer.LOG_BANDS
        val dominantFreqHz = 10.0.pow(logMin + (logMax - logMin) * (frac + 0.5 / FFTAnalyzer.LOG_BANDS)).toFloat()

        // ---- RT60 estimate: rough decay time of upper-90th percentile RMS down to noise floor.
        // Find the peak window, then walk forward until RMS drops below floor + 6 dB.
        val peakRms = rmsWindows.maxOrNull() ?: floorRms
        val targetRms = floorRms * 2f  // ~6dB above floor
        var decayWindowCount = 0
        // (Walk back over rmsWindows from the peak position toward end — but we sorted in place above;
        // recompute non-sorted RMS for this part.)
        val rmsRaw = mutableListOf<Float>()
        var i2 = 0
        while (i2 + winSize <= captured.size) {
            var sumSq = 0.0
            for (j in 0 until winSize) {
                val s = captured[i2 + j].toDouble() / 32768.0
                sumSq += s * s
            }
            rmsRaw.add(sqrt(sumSq / winSize).toFloat())
            i2 += winSize / 2
        }
        val peakRawIdx = rmsRaw.indexOf(rmsRaw.maxOrNull() ?: peakRms)
        if (peakRawIdx >= 0) {
            var k = peakRawIdx
            while (k < rmsRaw.size && rmsRaw[k] > targetRms) {
                decayWindowCount++
                k++
            }
        }
        // Each window step is winSize/2 samples → seconds.
        val rt60Ms = (decayWindowCount * (winSize / 2f) / sampleRate * 1000f)

        // ---- Quality stars 1..5: combine noise floor + RT60 + peak headroom.
        val noiseScore = when {
            noiseFloorDb < -55f -> 2
            noiseFloorDb < -45f -> 1
            else -> 0
        }
        val rtScore = when {
            rt60Ms < 300f -> 2
            rt60Ms < 700f -> 1
            else -> 0
        }
        val peakDb = (20.0 * log10(maxOf(peakRms, 1e-6f).toDouble())).toFloat()
        val headroomScore = if (peakDb < -6f) 1 else 0
        val qualityStars = (1 + noiseScore + rtScore + headroomScore).coerceIn(1, 5)

        return RoomProfile(
            noiseFloorDb = noiseFloorDb,
            rt60Ms = rt60Ms,
            dominantFreqHz = dominantFreqHz,
            qualityStars = qualityStars,
        )
    }
}

