package com.example.recorderproject.audio

import com.example.recorderproject.model.EQChain
import java.io.File
import kotlin.math.tanh

object EQProcessor {

    private const val BLOCK_FRAMES = 8192

    /**
     * Render src through the EQ chain to dst.
     * @param progress callback in [0f, 1f]; called approximately once per block plus 1f at the very end.
     */
    fun process(
        src: File,
        dst: File,
        chain: EQChain,
        clipProtection: Boolean = true,
        progress: (Float) -> Unit,
    ) {
        val reader = WavIo.openReader(src)
        val header = reader.header
        val channels = header.channels.coerceAtMost(2)
        val sr = header.sampleRate.toFloat()

        val activeBands = if (chain.bypassed) emptyList()
                          else chain.bands.filter { it.enabled && !it.muted }
        val cascadesPerChannel: List<List<Biquad>> = (0 until channels).map {
            activeBands.flatMap { BiquadCoeffs.cascadeForBand(it, sr) }
        }

        val readBuf = FloatArray(BLOCK_FRAMES * header.channels)
        val writeBuf = FloatArray(BLOCK_FRAMES * header.channels)
        val outSamples = ArrayList<FloatArray>()
        var totalFramesProcessed = 0L
        val totalFrames = header.totalFrames.coerceAtLeast(1)

        while (true) {
            val n = reader.readBlock(readBuf)
            if (n <= 0) break
            val frames = n / header.channels
            for (frame in 0 until frames) {
                for (ch in 0 until channels) {
                    var x = readBuf[frame * header.channels + ch].toDouble()
                    val cascade = cascadesPerChannel[ch]
                    for (biq in cascade) x = biq.process(x)
                    if (clipProtection && (x > 0.999 || x < -0.999)) x = tanh(x)
                    writeBuf[frame * header.channels + ch] = x.toFloat()
                }
                for (ch in channels until header.channels) {
                    writeBuf[frame * header.channels + ch] = readBuf[frame * header.channels + ch]
                }
            }
            outSamples.add(writeBuf.copyOf(n))
            totalFramesProcessed += frames
            progress((totalFramesProcessed.toFloat() / totalFrames).coerceIn(0f, 0.99f))
        }
        reader.close()

        val total = outSamples.sumOf { it.size }
        val flat = FloatArray(total)
        var pos = 0
        for (chunk in outSamples) {
            System.arraycopy(chunk, 0, flat, pos, chunk.size)
            pos += chunk.size
        }
        WavIo.write(dst, flat, header.channels, header.sampleRate, header.bitDepth)
        progress(1f)
    }
}
