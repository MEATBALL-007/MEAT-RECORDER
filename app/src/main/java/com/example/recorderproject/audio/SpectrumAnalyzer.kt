package com.example.recorderproject.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max

/** Static spectrum result for the EQ curve view to overlay. */
data class StaticSpectrum(
    val magnitudeDb: FloatArray,
    val minFreqHz: Float,
    val maxFreqHz: Float,
) {
    fun frequencyForBin(idx: Int): Double {
        val t = idx.toDouble() / (magnitudeDb.size - 1)
        return exp(ln(minFreqHz.toDouble()) + t * (ln(maxFreqHz.toDouble()) - ln(minFreqHz.toDouble())))
    }
}

object SpectrumAnalyzer {

    private const val FFT_SIZE = 4096
    private const val HOP = FFT_SIZE / 2

    fun analyzeFile(file: File, bins: Int = 256, minFreqHz: Float = 20f, maxFreqHz: Float = 20_000f): StaticSpectrum {
        val header = WavIo.readHeader(file)
        val sr = header.sampleRate
        val reader = WavIo.openReader(file)
        val buf = FloatArray(FFT_SIZE * header.channels)
        val fftMagAcc = DoubleArray(FFT_SIZE / 2)
        var nWindows = 0

        val window = DoubleArray(FFT_SIZE) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1)) }
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)

        var leftover = FloatArray(0)
        while (true) {
            val n = reader.readBlock(buf)
            if (n <= 0) break
            val monoLen = n / header.channels
            val mono = FloatArray(monoLen) { i ->
                var sum = 0f
                for (ch in 0 until header.channels) sum += buf[i * header.channels + ch]
                sum / header.channels
            }
            val combined = leftover + mono
            var pos = 0
            while (pos + FFT_SIZE <= combined.size) {
                for (i in 0 until FFT_SIZE) {
                    real[i] = combined[pos + i].toDouble() * window[i]
                    imag[i] = 0.0
                }
                fftInPlace(real, imag)
                for (k in 0 until FFT_SIZE / 2) {
                    val mag = kotlin.math.sqrt(real[k] * real[k] + imag[k] * imag[k])
                    fftMagAcc[k] += mag
                }
                nWindows++
                pos += HOP
            }
            leftover = if (pos < combined.size) combined.copyOfRange(pos, combined.size) else FloatArray(0)
        }
        reader.close()

        if (nWindows == 0) {
            return StaticSpectrum(FloatArray(bins) { -120f }, minFreqHz, maxFreqHz)
        }
        for (k in fftMagAcc.indices) fftMagAcc[k] /= nWindows

        val out = FloatArray(bins)
        for (i in 0 until bins) {
            val t = i.toDouble() / (bins - 1)
            val freq = exp(ln(minFreqHz.toDouble()) + t * (ln(maxFreqHz.toDouble()) - ln(minFreqHz.toDouble())))
            val binIdx = (freq * FFT_SIZE / sr).toInt().coerceIn(0, FFT_SIZE / 2 - 1)
            val mag = fftMagAcc[binIdx]
            out[i] = (20.0 * log10(max(mag, 1e-12))).toFloat()
        }
        return StaticSpectrum(out, minFreqHz, maxFreqHz)
    }

    /** Iterative in-place Cooley-Tukey FFT (radix-2). n must be a power of 2. */
    private fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wlenR = cos(ang); val wlenI = kotlin.math.sin(ang)
            var i = 0
            while (i < n) {
                var wR = 1.0; var wI = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]; val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * wR - imag[i + k + len / 2] * wI
                    val vI = real[i + k + len / 2] * wI + imag[i + k + len / 2] * wR
                    real[i + k] = uR + vR; imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR; imag[i + k + len / 2] = uI - vI
                    val nwR = wR * wlenR - wI * wlenI
                    val nwI = wR * wlenI + wI * wlenR
                    wR = nwR; wI = nwI
                }
                i += len
            }
            len = len shl 1
        }
    }
}
