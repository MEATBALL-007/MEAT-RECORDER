package com.example.recorderproject.audio

import com.example.recorderproject.model.DeliveryResult
import com.example.recorderproject.model.LoudnessTarget
import java.io.File
import kotlin.math.abs
import kotlin.math.pow

/**
 * Two-pass offline delivery renderer.
 *
 *  Pass 1 — measure source loudness, true-peak, and LRA.
 *  Pass 2 — apply target gain + (if needed) MasterLimiter ceiling, write delivery WAV.
 *  Then  — re-measure delivery TP for the report and return DeliveryResult.
 *
 * Output: 24-bit PCM, same SR + channels as source.
 *
 * Returns `null` (no work, no file) when:
 *   - target is Off
 *   - source is shorter than 400 ms (momentary window)
 *   - source integrates below -60 LUFS (near-silent)
 */
object DeliveryRenderer {

    private const val CHUNK_FRAMES = 8192
    private const val MIN_FRAMES_SECONDS = 0.4f
    private const val SILENCE_FLOOR_LUFS = -60f

    fun render(
        source: File,
        destination: File,
        target: LoudnessTarget,
    ): DeliveryResult? {
        if (target is LoudnessTarget.Off) return null
        val targetLufs = target.targetLufs ?: return null
        val tpCeiling = target.tpCeilingDbtp ?: return null

        val hdr = WavIo.readHeader(source)
        if (hdr.totalFrames < hdr.sampleRate * MIN_FRAMES_SECONDS) return null

        // ---------- Pass 1 — measure ----------
        val pass1Lufs = LufsProcessor(hdr.sampleRate.toFloat(), hdr.channels)
        val pass1Tp   = TruePeakDetector(hdr.sampleRate.toFloat(), hdr.channels)
        val pass1Lra  = LoudnessRangeMeter(hdr.sampleRate.toFloat(), hdr.channels)

        run {
            val r = WavIo.openReader(source)
            try {
                val buf = FloatArray(CHUNK_FRAMES * hdr.channels)
                while (true) {
                    val n = r.readBlock(buf)
                    if (n <= 0) break
                    val slice = if (n == buf.size) buf else buf.copyOf(n)
                    pass1Lufs.process(slice)
                    pass1Tp.feed(slice)
                    pass1Lra.process(slice)
                }
            } finally { r.close() }
        }

        val measuredI = pass1Lufs.integratedLufs
        if (measuredI < SILENCE_FLOOR_LUFS) return null

        val rawGainDb   = targetLufs - measuredI
        val postTpDbtp  = pass1Tp.peakDbTP + rawGainDb
        val needsLimit  = (postTpDbtp - tpCeiling) > 0f
        val gainLinear  = 10f.pow(rawGainDb / 20f)

        // ---------- Pass 2 — render ----------
        val limiter = if (needsLimit) {
            MasterLimiter(hdr.sampleRate.toFloat(), ceilingDb = tpCeiling, attackMs = 1.5f, releaseMs = 50f)
        } else null

        run {
            val r = WavIo.openReader(source)
            val w = WavIo.openWriter(destination, hdr.channels, hdr.sampleRate, bitDepth = 24)
            try {
                val buf = FloatArray(CHUNK_FRAMES * hdr.channels)
                val out = FloatArray(CHUNK_FRAMES * hdr.channels)
                while (true) {
                    val n = r.readBlock(buf)
                    if (n <= 0) break
                    for (i in 0 until n) {
                        val gained = buf[i] * gainLinear
                        out[i] = limiter?.process(gained) ?: gained
                    }
                    w.writeBlock(out, n)
                }
            } finally { r.close(); w.close() }
        }

        // ---------- Post-measure delivery TP for the report ----------
        val verifyTp = TruePeakDetector(hdr.sampleRate.toFloat(), hdr.channels)
        run {
            val r = WavIo.openReader(destination)
            try {
                val buf = FloatArray(CHUNK_FRAMES * hdr.channels)
                while (true) {
                    val n = r.readBlock(buf)
                    if (n <= 0) break
                    verifyTp.feed(if (n == buf.size) buf else buf.copyOf(n))
                }
            } finally { r.close() }
        }

        val deliveryI = measuredI + rawGainDb
        val passed = abs(deliveryI - targetLufs) <= 0.5f
                  && verifyTp.peakDbTP <= tpCeiling + 0.1f

        return DeliveryResult(
            integratedLufs   = deliveryI,
            shortTermMaxLufs = pass1Lufs.shortTermMaxLufs + rawGainDb,
            momentaryMaxLufs = pass1Lufs.momentaryMaxLufs + rawGainDb,
            truePeakDbtp     = verifyTp.peakDbTP,
            lra              = pass1Lra.lra,
            appliedGainDb    = rawGainDb,
            targetLufs       = targetLufs,
            tpCeilingDbtp    = tpCeiling,
            passed           = passed,
            deliveryFile     = destination.absolutePath,
            renderedAt       = System.currentTimeMillis(),
        )
    }
}
