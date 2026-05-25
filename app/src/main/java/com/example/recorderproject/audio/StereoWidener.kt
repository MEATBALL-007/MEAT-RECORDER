package com.example.recorderproject.audio

/**
 * Phase 7 — mid/side stereo widening. Pure stateless math; works in-place on interleaved
 * stereo samples. Width 1.0 = unchanged, >1 = wider, <1 = narrower (0 = mono).
 */
object StereoWidener {

    fun process(interleavedStereo: FloatArray, width: Float) {
        require(interleavedStereo.size % 2 == 0) { "Stereo array must have even length" }
        val w = width.coerceIn(0f, 4f)
        var i = 0
        while (i < interleavedStereo.size) {
            val l = interleavedStereo[i]
            val r = interleavedStereo[i + 1]
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f * w
            interleavedStereo[i] = mid + side
            interleavedStereo[i + 1] = mid - side
            i += 2
        }
    }
}
