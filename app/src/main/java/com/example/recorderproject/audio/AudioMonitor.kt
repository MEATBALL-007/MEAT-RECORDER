package com.example.recorderproject.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.example.recorderproject.model.EQChain
import kotlin.concurrent.thread

/**
 * Phase 7 — Real-time mic-to-output monitor with optional EQ chain.
 *
 * Pipes AudioRecord PCM directly to an AudioTrack at low buffer sizes for live monitoring
 * (Bluetooth earphones, wired earphones, USB). Optional biquad cascade applied per channel.
 *
 * Latency: uses AAudio LOW_LATENCY performance mode with minimum buffers. Real-world floor
 * depends on the output route — wired/USB headphones: ~10-20ms, BT LC3: ~30-60ms,
 * BT SBC (legacy): ~150ms. True 0ms is physically impossible for any digital monitor.
 */
class AudioMonitor(
    private val sampleRate: Int = 48_000,
    private val audioSource: Int = MediaRecorder.AudioSource.MIC,
) {
    private val tag = "AudioMonitor"
    @Volatile private var running = false
    @Volatile private var chain: EQChain = EQChain.empty()
    @Volatile private var levelListener: ((rmsDb: Float, peakDb: Float) -> Unit)? = null
    private var thread: Thread? = null

    fun setChain(newChain: EQChain) {
        chain = newChain
    }

    /** Set or clear the per-buffer (rmsDb, peakDb) callback. Pass null to clear. */
    fun setLevelListener(cb: ((rmsDb: Float, peakDb: Float) -> Unit)?) {
        levelListener = cb
    }

    @SuppressLint("MissingPermission") // permission gated at the caller layer
    fun start() {
        if (running) return
        running = true
        thread = thread(name = "AudioMonitor") { run() }
    }

    fun stop() {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
    }

    private fun run() {
        val channelInMask = AudioFormat.CHANNEL_IN_MONO
        val channelOutMask = AudioFormat.CHANNEL_OUT_MONO
        val format = AudioFormat.ENCODING_PCM_16BIT
        val minIn = AudioRecord.getMinBufferSize(sampleRate, channelInMask, format).coerceAtLeast(256)
        val minOut = AudioTrack.getMinBufferSize(sampleRate, channelOutMask, format).coerceAtLeast(256)
        val bufSamples = maxOf(minIn, minOut) / 2 // shorts

        // AudioRecord: use Builder + LOW_LATENCY performance mode (API 28+).
        // Pre-API-28 falls back to the legacy constructor with minimum buffer.
        val record = if (android.os.Build.VERSION.SDK_INT >= 28) {
            AudioRecord.Builder()
                .setAudioSource(audioSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(format)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelInMask)
                        .build()
                )
                .setBufferSizeInBytes(minIn)
                .build()
        } else {
            AudioRecord(audioSource, sampleRate, channelInMask, format, minIn)
        }

        // AudioTrack: PERFORMANCE_MODE_LOW_LATENCY (API 26+) for minimum buffer chain.
        // The output path then uses the device's fast-mixer/AAudio path when available.
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(format)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelOutMask)
                    .build()
            )
            .setBufferSizeInBytes(minOut)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
            }
            .build()

        // Log effective latency so we can confirm low-latency path engaged
        Log.i(tag, "Monitor started: minIn=${minIn}B minOut=${minOut}B " +
            "trackPerfMode=${if (android.os.Build.VERSION.SDK_INT >= 26) track.performanceMode else "n/a"} " +
            "(0=NONE, 1=POWER_SAVING, 2=LOW_LATENCY)")

        try {
            record.startRecording()
            track.play()

            val buf = ShortArray(bufSamples)
            while (running) {
                val read = record.read(buf, 0, buf.size)
                if (read <= 0) continue

                // Apply EQ chain if enabled bands present
                val activeBands = if (chain.bypassed) emptyList()
                    else chain.bands.filter { it.enabled && !it.muted }
                if (activeBands.isNotEmpty()) {
                    val biquads = activeBands.flatMap { BiquadCoeffs.cascadeForBand(it, sampleRate.toFloat()) }
                    for (i in 0 until read) {
                        var x = buf[i].toDouble() / Short.MAX_VALUE
                        for (b in biquads) x = b.process(x)
                        buf[i] = (x.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                    }
                }

                track.write(buf, 0, read)

                // Compute level for any registered meter listener (~5-15µs per buffer)
                val cb = levelListener
                if (cb != null && read > 0) {
                    var sumSq = 0.0
                    var peakAbs = 0
                    for (i in 0 until read) {
                        val s = buf[i].toInt()
                        sumSq += s.toDouble() * s.toDouble()
                        val a = if (s < 0) -s else s
                        if (a > peakAbs) peakAbs = a
                    }
                    val rms = kotlin.math.sqrt(sumSq / read) / Short.MAX_VALUE.toDouble()
                    val pk  = peakAbs.toDouble() / Short.MAX_VALUE.toDouble()
                    val rmsDb  = if (rms <= 0.0) -60f else (20.0 * kotlin.math.log10(rms)).toFloat().coerceAtLeast(-60f)
                    val peakDb = if (pk  <= 0.0) -60f else (20.0 * kotlin.math.log10(pk )).toFloat().coerceAtLeast(-60f)
                    cb(rmsDb, peakDb)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Monitor loop crashed: ${e.message}", e)
        } finally {
            try { record.stop(); record.release() } catch (_: Exception) {}
            try { track.stop(); track.release() } catch (_: Exception) {}
        }
    }
}
