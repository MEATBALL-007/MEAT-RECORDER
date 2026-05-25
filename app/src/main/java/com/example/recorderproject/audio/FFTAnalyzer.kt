package com.example.recorderproject.audio

import java.io.File
import java.io.FileInputStream
import kotlin.math.*

object FFTAnalyzer {
    const val FFT_SIZE = 1024
    const val LOG_BANDS = 32
    private const val MIN_HZ = 20.0
    private const val MAX_HZ = 20000.0

    private val hannWindow = FloatArray(FFT_SIZE) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
    }

    fun frameSpectrum(samples: ShortArray, offset: Int, count: Int, sampleRate: Int): FloatArray {
        val fftData = FloatArray(FFT_SIZE * 2)
        val len = minOf(FFT_SIZE, count)
        for (i in 0 until len) {
            fftData[i * 2] = (samples[offset + i] / 32768f) * hannWindow[i]
        }
        fftInPlace(fftData, FFT_SIZE)
        val mags = FloatArray(FFT_SIZE / 2)
        for (i in 0 until FFT_SIZE / 2) {
            val re = fftData[i * 2]; val im = fftData[i * 2 + 1]
            mags[i] = sqrt(re * re + im * im)
        }
        return toLogBands(mags, sampleRate)
    }

    fun toLogBands(magnitudes: FloatArray, sampleRate: Int): FloatArray {
        val result = FloatArray(LOG_BANDS)
        val nyquist = sampleRate / 2.0
        val logMin = log10(MIN_HZ)
        val logMax = log10(nyquist.coerceAtLeast(MIN_HZ + 1.0))
        for (b in 0 until LOG_BANDS) {
            val fLow  = 10.0.pow(logMin + (logMax - logMin) * b.toDouble() / LOG_BANDS)
            val fHigh = 10.0.pow(logMin + (logMax - logMin) * (b + 1).toDouble() / LOG_BANDS)
            val binLow  = (fLow  / nyquist * magnitudes.size).toInt().coerceIn(0, magnitudes.size - 1)
            val binHigh = (fHigh / nyquist * magnitudes.size).toInt().coerceIn(binLow, magnitudes.size - 1)
            var sum = 0f; var count = 0
            for (bin in binLow..binHigh) { sum += magnitudes[bin]; count++ }
            result[b] = if (count > 0) sum / count else 0f
        }
        return result
    }

    fun computeSpectrogramFromFile(path: String, sampleRate: Int, maxFrames: Int = 400): Array<FloatArray> {
        val file = File(path)
        if (!file.exists()) return emptyArray()
        val audioDataBytes = (file.length() - 44).coerceAtLeast(0)
        val totalSamples = (audioDataBytes / 2).toInt()  // assumes 16-bit LE
        if (totalSamples < FFT_SIZE) return emptyArray()
        val step = (totalSamples / maxFrames).coerceAtLeast(FFT_SIZE / 2)
        val frames = mutableListOf<FloatArray>()
        val frameBytes = ByteArray(FFT_SIZE * 2)
        val shorts = ShortArray(FFT_SIZE)
        try {
            FileInputStream(file).use { fis ->
                fis.skip(44)
                var filePos = 0
                while (filePos + FFT_SIZE <= totalSamples && frames.size < maxFrames) {
                    var read = 0
                    while (read < frameBytes.size) {
                        val n = fis.read(frameBytes, read, frameBytes.size - read)
                        if (n < 0) break
                        read += n
                    }
                    if (read < FFT_SIZE * 2) break
                    for (i in 0 until FFT_SIZE) {
                        shorts[i] = ((frameBytes[i * 2 + 1].toInt() shl 8) or (frameBytes[i * 2].toInt() and 0xFF)).toShort()
                    }
                    frames.add(frameSpectrum(shorts, 0, FFT_SIZE, sampleRate))
                    filePos += step
                    var toSkip = (step - FFT_SIZE).toLong() * 2
                    while (toSkip > 0) { val s = fis.skip(toSkip); if (s <= 0) break; toSkip -= s }
                }
            }
        } catch (_: Exception) {}
        return frames.toTypedArray()
    }

    fun readWavSamples(file: File): ShortArray {
        return try {
            FileInputStream(file).use { fis ->
                fis.skip(44)
                val bytes = fis.readBytes()
                if (bytes.size < 2) return ShortArray(0)
                ShortArray(bytes.size / 2) { i ->
                    ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort()
                }
            }
        } catch (_: Exception) { ShortArray(0) }
    }

    // Cooley-Tukey radix-2 FFT in-place (interleaved real/imag)
    private fun fftInPlace(data: FloatArray, n: Int) {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = data[i * 2]; data[i * 2] = data[j * 2]; data[j * 2] = t
                t = data[i * 2 + 1]; data[i * 2 + 1] = data[j * 2 + 1]; data[j * 2 + 1] = t
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val ang = -2.0 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cRe = 1f; var cIm = 0f
                for (k in 0 until half) {
                    val uRe = data[(i+k)*2];      val uIm = data[(i+k)*2+1]
                    val vRe = data[(i+k+half)*2]; val vIm = data[(i+k+half)*2+1]
                    val tvRe = cRe*vRe - cIm*vIm; val tvIm = cRe*vIm + cIm*vRe
                    data[(i+k)*2]          = uRe + tvRe; data[(i+k)*2+1]          = uIm + tvIm
                    data[(i+k+half)*2]     = uRe - tvRe; data[(i+k+half)*2+1]     = uIm - tvIm
                    val nRe = cRe*wRe - cIm*wIm; cIm = cRe*wIm + cIm*wRe; cRe = nRe
                }
                i += len
            }
            len *= 2
        }
    }
}
