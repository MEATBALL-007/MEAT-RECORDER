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

    // RECORD_AUDIO is gated by the recording flow before start() is ever called; the
    // try/catch below is the safety net for the case where it somehow isn't (the
    // AudioRecord constructor can throw SecurityException when permission is missing).
    @SuppressLint("MissingPermission")
    private fun run() {
        val channelInMask = AudioFormat.CHANNEL_IN_MONO
        val channelOutMask = AudioFormat.CHANNEL_OUT_MONO
        val format = AudioFormat.ENCODING_PCM_16BIT
        val minIn = AudioRecord.getMinBufferSize(sampleRate, channelInMask, format).coerceAtLeast(256)
        val minOut = AudioTrack.getMinBufferSize(sampleRate, channelOutMask, format).coerceAtLeast(256)
        val bufSamples = maxOf(minIn, minOut) / 2 // shorts

        // Build the input/output devices up front. A rejected RECORD_AUDIO permission, an
        // unsupported format, or a busy mic can make these throw — catch it here so the
        // monitor thread fails quietly instead of taking down the app with an uncaught
        // exception. Anything created before the throw is released in the catch.
        var record: AudioRecord? = null
        var track: AudioTrack? = null
        try {
            // AudioRecord: use Builder + LOW_LATENCY performance mode (API 28+).
            // Pre-API-28 falls back to the legacy constructor with minimum buffer.
            record = if (android.os.Build.VERSION.SDK_INT >= 28) {
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
            track = AudioTrack.Builder()
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
        } catch (e: Exception) {
            Log.e(tag, "Monitor failed to initialise audio devices: ${e.message}", e)
            running = false
            try { record?.release() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            return
        }

        // Both devices are non-null here (the catch above returns on any failure).
        val rec: AudioRecord = record!!
        val trk: AudioTrack = track!!

        // Bail if either device failed to initialise (e.g. mic held by another app).
        if (rec.state != AudioRecord.STATE_INITIALIZED ||
            trk.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(tag, "Monitor devices not initialised (record=${rec.state} track=${trk.state})")
            running = false
            try { rec.release() } catch (_: Exception) {}
            try { trk.release() } catch (_: Exception) {}
            return
        }

        // Log effective latency so we can confirm low-latency path engaged
        Log.i(tag, "Monitor started: minIn=${minIn}B minOut=${minOut}B " +
            "trackPerfMode=${if (android.os.Build.VERSION.SDK_INT >= 26) trk.performanceMode else "n/a"} " +
            "(0=NONE, 1=POWER_SAVING, 2=LOW_LATENCY)")

        try {
            rec.startRecording()
            trk.play()

            val buf = ShortArray(bufSamples)
            while (running) {
                val read = rec.read(buf, 0, buf.size)
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

                trk.write(buf, 0, read)

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
            try { rec.stop(); rec.release() } catch (_: Exception) {}
            try { trk.stop(); trk.release() } catch (_: Exception) {}
        }
    }
}
