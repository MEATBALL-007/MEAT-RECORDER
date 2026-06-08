package com.example.recorderproject.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Bridges a stream-based audio source (e.g. a SAF `content://` document) through a
 * `File`-based DSP processor such as [EQProcessor] or [NoiseReductionProcessor].
 *
 * The DSP pipeline only knows how to read/write `java.io.File`s, but SAF folders hand
 * us [InputStream]/[OutputStream] instead. This materializes the source to a temp file
 * under [cacheDir], runs the processor, publishes the result back to the sink, and always
 * cleans up the temp files — even if the processor throws.
 *
 * Android types stay out of here on purpose so the round-trip is unit-testable on the JVM;
 * the ViewModel supplies the `contentResolver.openInputStream` / `openOutputStream` lambdas.
 */
object SafAudioBridge {

    fun processViaTemp(
        cacheDir: File,
        openInput: () -> InputStream,
        openOutput: () -> OutputStream,
        process: (src: File, dst: File) -> Unit,
    ) {
        val srcTemp = File.createTempFile("saf_src_", ".wav", cacheDir)
        val dstTemp = File.createTempFile("saf_dst_", ".wav", cacheDir)
        try {
            openInput().use { input ->
                FileOutputStream(srcTemp).use { out -> input.copyTo(out) }
            }
            process(srcTemp, dstTemp)
            openOutput().use { sink ->
                FileInputStream(dstTemp).use { result -> result.copyTo(sink) }
            }
        } finally {
            srcTemp.delete()
            dstTemp.delete()
        }
    }
}
