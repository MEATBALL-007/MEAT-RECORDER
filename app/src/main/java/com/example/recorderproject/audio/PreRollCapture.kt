package com.example.recorderproject.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.concurrent.thread

/**
 * Always-on mic capture that feeds a [PreRollBuffer] in the background.
 * On record start, the buffer is drained and the take's WAV is prepended.
 *
 * Caller is responsible for stop()-ing this BEFORE the main recording AudioRecord
 * is opened — two AudioRecord clients on the same mic conflict on Android.
 */
class PreRollCapture(
    private val buffer: PreRollBuffer,
    private val sampleRate: Int = 48_000,
    private val audioSource: Int = MediaRecorder.AudioSource.UNPROCESSED,
) {
    private val tag = "PreRollCapture"
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // caller layer holds RECORD_AUDIO permission
    fun start() {
        if (running) return
        running = true
        thread = thread(name = "PreRollCapture") {
            val channelMask = AudioFormat.CHANNEL_IN_MONO
            val format = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelMask, format)
                .coerceAtLeast(2048)
            val record = try {
                AudioRecord(audioSource, sampleRate, channelMask, format, minBuf)
            } catch (e: Exception) {
                Log.e(tag, "AudioRecord init failed: ${e.message}", e)
                running = false
                return@thread
            }
            try {
                record.startRecording()
                val shortBuf = ShortArray(2048)
                val floatBuf = FloatArray(2048)
                while (running) {
                    val n = record.read(shortBuf, 0, shortBuf.size)
                    if (n > 0) {
                        for (i in 0 until n) floatBuf[i] = shortBuf[i] / 32768f
                        buffer.write(floatBuf.copyOfRange(0, n))
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Pre-roll loop crashed: ${e.message}", e)
            } finally {
                try { record.stop(); record.release() } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
    }

    fun isRunning(): Boolean = running
}
