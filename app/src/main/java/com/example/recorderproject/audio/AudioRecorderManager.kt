package com.example.recorderproject.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.RecordFile
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import android.util.Log
import java.util.Locale

class AudioRecorderManager(private val context: Context) {
    private val TAG = "AudioRecorderManager"
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var recorderFile: File? = null
    private var outputStream: BufferedOutputStream? = null
    private var totalBytesWritten = 0
    private var sampleRate = 48000
    private var isUsingSAF = false
    @Volatile private var isRecordingActive = false
    private var documentUri: Uri? = null
    private var contentResolver = context.contentResolver
    private var targetName = ""
    private var audioSource = MediaRecorder.AudioSource.DEFAULT

    companion object {
        val AUDIO_SOURCES = listOf(
            "Default" to MediaRecorder.AudioSource.DEFAULT,
            "Microphone" to MediaRecorder.AudioSource.MIC,
            "Voice Communication" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            "Camcorder" to MediaRecorder.AudioSource.CAMCORDER,
            "Voice Recognition" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
            "Remote Submix" to MediaRecorder.AudioSource.REMOTE_SUBMIX
        )
    }

    fun setAudioSource(source: Int) {
        audioSource = source
    }

    // ------------- Real-time EQ during recording (Phase 7) -------------
    //
    // When set, every PCM frame read from AudioRecord is run through this biquad cascade
    // BEFORE it lands in the WAV file. Set during recording for true real-time EQ.
    @Volatile private var liveEqBiquads: List<Biquad> = emptyList()
    @Volatile private var liveEqActive = false

    /** Input gain multiplier applied before EQ (1.0 = unity). Clipped softly at ±1 after. */
    @Volatile private var inputGain: Float = 1.0f
    fun setInputGain(v: Float) { inputGain = v.coerceIn(0.0f, 8.0f) }

    /** Push a new EQ chain into the recording loop. Pass null to disable. */
    // F13: Live noise gate — drops samples whose RMS is below threshold while recording.
    // The gate is RMS-windowed (4096 samples) so it doesn't chop syllables mid-word.
    @Volatile private var liveNoiseGateOn: Boolean = false
    @Volatile private var liveNoiseGateThreshold: Float = 0.005f // ~-46 dBFS

    fun setLiveNoiseGate(enabled: Boolean, thresholdDb: Float = -46f) {
        liveNoiseGateOn = enabled
        // dB → linear 0..1
        liveNoiseGateThreshold = Math.pow(10.0, (thresholdDb / 20.0).toDouble()).toFloat()
    }

    // F2: Live pause — when true, the recording loop skips writePcmData (timer keeps going).
    @Volatile private var paused: Boolean = false
    fun setPaused(p: Boolean) { paused = p }

    // M1/M2: Live spectrum + pitch listeners — called once every N PCM reads
    // so the rate is ~10-20 Hz and visualization doesn't melt the CPU.
    @Volatile private var spectrumListener: ((FloatArray) -> Unit)? = null
    @Volatile private var pitchListener: ((Float) -> Unit)? = null
    private var dspFrameCounter: Int = 0
    fun setSpectrumListener(cb: ((FloatArray) -> Unit)?) { spectrumListener = cb }
    fun setPitchListener(cb: ((Float) -> Unit)?) { pitchListener = cb }

    // G9: AGC — track running peak; slowly bring it toward target.
    @Volatile private var agcOn: Boolean = false
    @Volatile private var agcGain: Float = 1f
    private val agcTargetRms = 0.18f
    fun setAgc(enabled: Boolean) { agcOn = enabled; if (!enabled) agcGain = 1f }

    // G10: Hi-pass filter (rumble removal) — single-pole 80 Hz cutoff. Skipped when off.
    @Volatile private var hiPassOn: Boolean = false
    @Volatile private var hiPassY1: Double = 0.0
    @Volatile private var hiPassX1: Double = 0.0
    fun setHiPass(enabled: Boolean) { hiPassOn = enabled }

    // G11: Anti-clipping auto-attenuator — when peak hits ≥0.98, drop gain by 1 dB.
    @Volatile private var antiClipOn: Boolean = false
    @Volatile private var antiClipGain: Float = 1f
    fun setAntiClip(enabled: Boolean) { antiClipOn = enabled; if (!enabled) antiClipGain = 1f }

    fun setLiveEqChain(chain: EQChain?, sr: Float) {
        if (chain == null) {
            liveEqActive = false
            liveEqBiquads = emptyList()
            return
        }
        val active = if (chain.bypassed) emptyList() else chain.bands.filter { it.enabled && !it.muted }
        liveEqBiquads = active.flatMap { BiquadCoeffs.cascadeForBand(it, sr) }
        liveEqActive = liveEqBiquads.isNotEmpty()
    }

    /** True iff a chain with at least one active band is currently being applied. */
    fun isLiveEqActive(): Boolean = liveEqActive

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start(fileName: String, sampleRate: Int = 48000, saveDirectoryUri: Uri? = null, onAudioFrame: (List<Float>) -> Unit) {
        Log.d(TAG, "start() called with sampleRate=$sampleRate, audioSource=$audioSource")
        if (!hasRecordAudioPermission()) {
            val msg = "Audio recording permission is required"
            Log.e(TAG, msg)
            throw SecurityException(msg)
        }

        this.sampleRate = sampleRate
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)
        Log.d(TAG, "bufferSize=$bufferSize")

        targetName = fileName.takeIf { it.isNotBlank() }?.replace("[^A-Za-z0-9_.-]".toRegex(), "_") ?: "recording.wav"
        if (!targetName.endsWith(".wav", ignoreCase = true)) targetName += ".wav"
        Log.d(TAG, "targetName=$targetName")

        if (saveDirectoryUri != null) {
            Log.d(TAG, "Using SAF (Storage Access Framework)")
            isUsingSAF = true
            val treeDocument = DocumentFile.fromTreeUri(context, saveDirectoryUri)
                ?: throw IOException("Cannot access selected directory")
            val document = treeDocument.createFile("audio/wav", targetName)
                ?: throw IOException("Failed to create file '$targetName' in selected directory")
            documentUri = document.uri
            val stream = contentResolver.openOutputStream(document.uri)
                ?: throw IOException("Failed to open output stream for '$targetName'")
            outputStream = BufferedOutputStream(stream)
        } else {
            Log.d(TAG, "Using internal storage")
            isUsingSAF = false
            val recordingsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings")
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
                Log.d(TAG, "Created recordings directory: ${recordingsDir.absolutePath}")
            }
            recorderFile = File(recordingsDir, targetName)
            Log.d(TAG, "Recording to file: ${recorderFile!!.absolutePath}")
            outputStream = BufferedOutputStream(recorderFile!!.outputStream())
        }

        writeWavHeader(outputStream!!, 0, 0, sampleRate.toLong(), 1, 2L * sampleRate)

        try {
            recorder = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            Log.d(TAG, "AudioRecord created successfully, state: ${recorder?.state}")
            recorder?.startRecording()
            Log.d(TAG, "Recording started, recordingState: ${recorder?.recordingState}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create/start AudioRecord: ${e.message}", e)
            throw e
        }

        totalBytesWritten = 0
        isRecordingActive = true

        recordingThread = Thread {
            Log.d(TAG, "Recording thread started")
            val audioBuffer = ShortArray(bufferSize)
            var readCount = 0
            while (isRecordingActive && recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    val read = recorder?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    readCount++
                    if (read > 0) {
                        // Input gain + Real-time EQ: gain first, then biquad cascade, soft-clip.
                        val gain = inputGain
                        val biquads = liveEqBiquads
                        val eqOn = liveEqActive && biquads.isNotEmpty()
                        val gainOn = kotlin.math.abs(gain - 1.0f) > 0.01f
                        // G9/G10/G11: extra DSP stages — only enter inner loop if any toggle is on.
                        val needsExtras = eqOn || gainOn || agcOn || hiPassOn || antiClipOn
                        if (needsExtras) {
                            // Hi-pass coefficient: ~80 Hz @ 48k → a = exp(-2π * 80 / 48000)
                            val hpAlpha = 0.98955  // ≈ matches 80 Hz cutoff at 48k
                            var peak = 0.0
                            var sumSq = 0.0
                            for (i in 0 until read) {
                                var x = audioBuffer[i].toDouble() / Short.MAX_VALUE
                                if (gainOn) x *= gain
                                if (hiPassOn) {
                                    // y[n] = α·(y[n-1] + x[n] − x[n-1])
                                    val y = hpAlpha * (hiPassY1 + x - hiPassX1)
                                    hiPassX1 = x
                                    hiPassY1 = y
                                    x = y
                                }
                                if (eqOn) for (b in biquads) x = b.process(x)
                                if (agcOn) x *= agcGain
                                if (antiClipOn) x *= antiClipGain
                                if (x > 0.999 || x < -0.999) x = kotlin.math.tanh(x)
                                if (kotlin.math.abs(x) > peak) peak = kotlin.math.abs(x)
                                sumSq += x * x
                                audioBuffer[i] = (x.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                            }
                            // AGC slew: ease toward target every buffer
                            if (agcOn) {
                                val rms = kotlin.math.sqrt(sumSq / read).toFloat()
                                if (rms > 1e-5f) {
                                    val targetGain = (agcTargetRms / rms).coerceIn(0.25f, 4f)
                                    agcGain = (agcGain * 0.95f + targetGain * 0.05f).coerceIn(0.25f, 4f)
                                }
                            }
                            // Anti-clip: nudge gain down when peak nears clip; recover slowly.
                            if (antiClipOn) {
                                antiClipGain = if (peak >= 0.98) {
                                    (antiClipGain * 0.891f).coerceAtLeast(0.1f) // -1 dB
                                } else {
                                    (antiClipGain * 1.001f).coerceAtMost(1f) // creep back
                                }
                            }
                        }
                        // F13: Live noise gate — zero out samples in buffers whose RMS
                        // sits below the gate threshold (background-only stretches).
                        if (liveNoiseGateOn) {
                            var sumSq = 0.0
                            for (i in 0 until read) {
                                val s = audioBuffer[i].toDouble() / Short.MAX_VALUE
                                sumSq += s * s
                            }
                            val rms = kotlin.math.sqrt(sumSq / read).toFloat()
                            if (rms < liveNoiseGateThreshold) {
                                for (i in 0 until read) audioBuffer[i] = 0
                            }
                        }
                        // F2: Live pause — skip writing while paused (still drain the mic
                        // so the AudioRecord buffer doesn't overflow).
                        if (paused) {
                            val levels = List(read) { 0f }
                            onAudioFrame(levels)
                            continue
                        }
                        writePcmData(audioBuffer, read)
                        val levels = audioBuffer.take(read).map { it / 32768f }
                        onAudioFrame(levels)

                        // M1/M2: Every 4 frames (~40-80 ms), compute FFT + pitch.
                        // Listeners are best-effort and run on the recording thread.
                        dspFrameCounter++
                        if (dspFrameCounter % 4 == 0) {
                            val sl = spectrumListener
                            val pl = pitchListener
                            if (sl != null && read >= FFTAnalyzer.FFT_SIZE) {
                                try {
                                    val bands = FFTAnalyzer.frameSpectrum(
                                        audioBuffer, 0, FFTAnalyzer.FFT_SIZE, sampleRate
                                    )
                                    sl(bands)
                                } catch (_: Exception) { /* swallow — never break the loop */ }
                            }
                            if (pl != null && read >= 2048) {
                                try {
                                    val result = PitchDetector.detect(audioBuffer, 0, read, sampleRate)
                                    if (result.confidence > 0.30f) pl(result.frequencyHz)
                                } catch (_: Exception) {}
                            }
                        }
                        if (readCount % 10 == 0) {
                            Log.d(TAG, "Read: $read samples, total written: $totalBytesWritten bytes")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in recording thread: ${e.message}", e)
                    break
                }
            }
            Log.d(TAG, "Recording thread ended after $readCount reads")
        }
        recordingThread?.start()
    }

    fun stop(sceneName: String, notes: String): RecordFile {
        isRecordingActive = false
        recorder?.stop()
        recorder?.release()
        recorder = null

        recordingThread?.join(2000)
        recordingThread = null

        outputStream?.flush()
        outputStream?.close()
        outputStream = null

        val durationSeconds = if (sampleRate > 0) {
            totalBytesWritten / (sampleRate * 2)
        } else {
            0
        }

        return if (isUsingSAF) {
            val uri = documentUri
            if (uri != null) {
                try {
                    contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).channel.use { channel ->
                            channel.position(4)
                            channel.write(ByteBuffer.wrap(intToLittleEndian(totalBytesWritten + 36)))
                            channel.position(40)
                            channel.write(ByteBuffer.wrap(intToLittleEndian(totalBytesWritten)))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update SAF WAV header: ${e.message}", e)
                }
            }
            RecordFile(
                id = System.currentTimeMillis().toString(),
                name = targetName,
                path = uri?.toString() ?: "",
                durationSeconds = durationSeconds,
                sceneName = sceneName,
                notes = notes
            )
        } else {
            val file = recorderFile ?: throw IllegalStateException("Recording file not initialized")
            updateWavHeader(file, totalBytesWritten.toLong())
            RecordFile(
                id = System.currentTimeMillis().toString(),
                name = file.name,
                path = file.absolutePath,
                durationSeconds = durationSeconds,
                sceneName = sceneName,
                notes = notes
            )
        }
    }

    private fun writePcmData(buffer: ShortArray, read: Int) {
        val byteBuffer = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until read) {
            byteBuffer.putShort(buffer[i])
        }
        outputStream?.write(byteBuffer.array())
        totalBytesWritten += read * 2
    }

    private fun writeWavHeader(output: BufferedOutputStream, totalAudioLen: Long, totalDataLen: Long, longSampleRate: Long, channels: Int, byteRate: Long) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt((totalDataLen + 36).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(longSampleRate.toInt())
        header.putInt(byteRate.toInt())
        header.putShort((channels * 16 / 8).toShort())
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(totalAudioLen.toInt())
        output.write(header.array())
    }

    private fun updateWavHeader(file: File, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(intToLittleEndian(totalDataLen.toInt()))
            raf.seek(40)
            raf.write(intToLittleEndian(totalAudioLen.toInt()))
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    fun autoRenameFile(filePath: String, sceneName: String, notes: String): String {
        if (isUsingSAF) return filePath
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) return filePath
            val analysis = analyzeAudioFile(file)
            val suggestedName = buildSuggestedFileName(analysis, sceneName, notes)
            if (suggestedName == file.name) return filePath
            val newFile = createUniqueFile(file.parentFile, suggestedName)
            if (file.renameTo(newFile)) newFile.absolutePath else filePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-rename file: ${e.message}", e)
            filePath
        }
    }

    private data class AudioAnalysisResult(
        val averageLevel: Float,
        val durationSeconds: Int
    )

    private fun analyzeAudioFile(file: File): AudioAnalysisResult {
        FileInputStream(file).use { inputStream ->
            inputStream.skip(44)
            val buffer = ByteArray(4096)
            var totalSamples = 0L
            var sumSquares = 0.0
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                for (i in 0 until bytesRead step 2) {
                    if (i + 1 >= bytesRead) break
                    val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                    val normalized = sample.toDouble() / 32768.0
                    sumSquares += normalized * normalized
                    totalSamples++
                }
            }
            val averageLevel = if (totalSamples > 0) sqrt(sumSquares / totalSamples).toFloat() else 0f
            val durationSeconds = if (sampleRate > 0) {
                ((file.length() - 44) / 2 / sampleRate).toInt().coerceAtLeast(0)
            } else 0
            return AudioAnalysisResult(averageLevel, durationSeconds)
        }
    }

    private fun buildSuggestedFileName(result: AudioAnalysisResult, sceneName: String, notes: String): String {
        val parts = mutableListOf<String>()
        val sceneTag = sanitizeTag(sceneName)
        if (sceneTag.isNotBlank()) parts.add(sceneTag)

        val noteTags = extractKeywordTags(notes)
        parts.addAll(noteTags.take(2))

        when {
            result.averageLevel < 0.05f -> parts.add("quiet")
            result.averageLevel < 0.2f -> parts.add("soft")
            result.averageLevel < 0.5f -> parts.add("normal")
            else -> parts.add("loud")
        }

        parts.add(when {
            result.durationSeconds < 5 -> "short"
            result.durationSeconds < 20 -> "medium"
            else -> "long"
        })

        if (notes.contains("dialog", ignoreCase = true) || notes.contains("speech", ignoreCase = true) || notes.contains("interview", ignoreCase = true)) {
            parts.add("voice")
        } else {
            parts.add("audio")
        }

        val base = parts.filter { it.isNotBlank() }.joinToString("_")
        return sanitizeFileName("${base}_").takeIf { it.isNotBlank() }?.let { "$it${result.durationSeconds}s.wav" } ?: "recording_${result.durationSeconds}s.wav"
    }

    private fun sanitizeTag(value: String): String {
        return value
            .trim()
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9]+".toRegex(), "_")
            .trim('_')
    }

    private fun extractKeywordTags(notes: String): List<String> {
        val lower = notes.lowercase(Locale.ROOT)
        val tags = mutableListOf<String>()
        if (lower.contains("interview")) tags.add("interview")
        if (lower.contains("dialog")) tags.add("dialog")
        if (lower.contains("voice")) tags.add("voice")
        if (lower.contains("action")) tags.add("action")
        if (lower.contains("scene")) tags.add("scene")
        if (lower.contains("ambient") || lower.contains("ambience")) tags.add("ambient")
        return tags.ifEmpty { listOf("auto") }
    }

    private fun sanitizeFileName(value: String): String {
        return value
            .trim()
            .replace("[^A-Za-z0-9_.-]".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .trim('_')
    }

    private fun createUniqueFile(directory: File?, baseName: String): File {
        val parent = directory ?: File(context.filesDir, "Recordings")
        var file = File(parent, baseName)
        var index = 1
        while (file.exists()) {
            val nameWithoutExtension = file.nameWithoutExtension
            val extension = file.extension.ifEmpty { "wav" }
            file = File(parent, "${nameWithoutExtension}_$index.$extension")
            index++
        }
        return file
    }
}
