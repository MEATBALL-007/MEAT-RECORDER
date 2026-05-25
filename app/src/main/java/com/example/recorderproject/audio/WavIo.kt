package com.example.recorderproject.audio

import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavIo {

    data class Header(
        val channels: Int,
        val sampleRate: Int,
        val bitDepth: Int,
        val totalFrames: Long,
        val dataOffset: Long,
        val dataSize: Long,
    )

    fun readHeader(file: File): Header {
        FileInputStream(file).use { fis ->
            val buf = ByteArray(12)
            val read = fis.read(buf)
            require(read >= 12) { "WAV file truncated (RIFF header missing)" }
            require(buf.sliceArray(0..3).toString(Charsets.US_ASCII) == "RIFF") { "Not a RIFF file" }
            require(buf.sliceArray(8..11).toString(Charsets.US_ASCII) == "WAVE") { "Not a WAVE file" }
        }
        java.io.RandomAccessFile(file, "r").use { raf ->
            var pos = 12L
            var channels = 0; var sampleRate = 0; var bitDepth = 0
            while (pos < raf.length()) {
                raf.seek(pos)
                val idBytes = ByteArray(4); raf.read(idBytes)
                val sizeBytes = ByteArray(4); raf.read(sizeBytes)
                val chunkSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
                val id = idBytes.toString(Charsets.US_ASCII)
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(chunkSize); raf.read(fmt)
                        val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        bb.short // audio format (1 = PCM)
                        channels = bb.short.toInt()
                        sampleRate = bb.int
                        bb.int // byte rate
                        bb.short // block align
                        bitDepth = bb.short.toInt()
                    }
                    "data" -> {
                        val dataOffset = pos + 8
                        val dataSize = chunkSize.toLong()
                        val bytesPerFrame = channels * (bitDepth / 8)
                        require(bytesPerFrame > 0) { "fmt chunk must precede data chunk" }
                        return Header(
                            channels = channels,
                            sampleRate = sampleRate,
                            bitDepth = bitDepth,
                            totalFrames = dataSize / bytesPerFrame,
                            dataOffset = dataOffset,
                            dataSize = dataSize,
                        )
                    }
                }
                pos += 8 + chunkSize + (chunkSize and 1)
            }
            error("data chunk not found")
        }
    }

    fun readAllSamples(file: File): FloatArray {
        val header = readHeader(file)
        val reader = StreamReader(file, header)
        val out = FloatArray(header.totalFrames.toInt() * header.channels)
        var written = 0
        val block = FloatArray(8192)
        while (true) {
            val read = reader.readBlock(block)
            if (read <= 0) break
            System.arraycopy(block, 0, out, written, read)
            written += read
        }
        reader.close()
        return out
    }

    fun openReader(file: File): StreamReader = StreamReader(file, readHeader(file))

    class StreamReader internal constructor(file: File, val header: Header) {
        private val raf = java.io.RandomAccessFile(file, "r")
        private var framePos = 0L
        private val bytesPerSample = header.bitDepth / 8
        private val bytesPerFrame = bytesPerSample * header.channels

        init { raf.seek(header.dataOffset) }

        fun readBlock(buf: FloatArray): Int {
            val framesAvail = (header.totalFrames - framePos)
            if (framesAvail <= 0) return 0
            val framesToRead = minOf(buf.size / header.channels, framesAvail.toInt())
            val byteCount = framesToRead * bytesPerFrame
            val bytes = ByteArray(byteCount)
            val readBytes = raf.read(bytes)
            if (readBytes <= 0) return 0
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val samplesRead = readBytes / bytesPerSample
            for (i in 0 until samplesRead) {
                buf[i] = when (header.bitDepth) {
                    16 -> bb.short.toFloat() / Short.MAX_VALUE.toFloat()
                    24 -> {
                        val b0 = bb.get().toInt() and 0xFF
                        val b1 = bb.get().toInt() and 0xFF
                        val b2 = bb.get().toInt()
                        val s = (b2 shl 16) or (b1 shl 8) or b0
                        val signed = if (s and 0x800000 != 0) s or 0xFF000000.toInt() else s
                        signed.toFloat() / 8388608f
                    }
                    32 -> bb.int.toFloat() / Int.MAX_VALUE.toFloat()
                    else -> error("Unsupported bit depth ${header.bitDepth}")
                }
            }
            framePos += samplesRead / header.channels
            return samplesRead
        }

        fun close() = raf.close()
    }

    fun write(
        file: File,
        interleavedSamples: FloatArray,
        channels: Int,
        sampleRate: Int,
        bitDepth: Int,
    ) {
        require(bitDepth in listOf(16, 24, 32)) { "Unsupported bit depth $bitDepth" }
        require(channels in 1..2) { "Unsupported channel count $channels" }
        val bytesPerSample = bitDepth / 8
        val dataSize = interleavedSamples.size * bytesPerSample
        FileOutputStream(file).use { fos ->
            val out = DataOutputStream(fos)
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.writeInt(Integer.reverseBytes(36 + dataSize))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.writeInt(Integer.reverseBytes(16))
            out.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
            out.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
            out.writeInt(Integer.reverseBytes(sampleRate))
            out.writeInt(Integer.reverseBytes(sampleRate * channels * bytesPerSample))
            out.writeShort(java.lang.Short.reverseBytes((channels * bytesPerSample).toShort()).toInt())
            out.writeShort(java.lang.Short.reverseBytes(bitDepth.toShort()).toInt())
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.writeInt(Integer.reverseBytes(dataSize))
            val bb = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            when (bitDepth) {
                16 -> for (s in interleavedSamples) {
                    val v = (s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                    bb.putShort(v)
                }
                24 -> for (s in interleavedSamples) {
                    val v = (s.coerceIn(-1f, 1f) * 8_388_607f).toInt()
                    bb.put((v and 0xFF).toByte())
                    bb.put(((v shr 8) and 0xFF).toByte())
                    bb.put(((v shr 16) and 0xFF).toByte())
                }
                32 -> for (s in interleavedSamples) {
                    val v = (s.coerceIn(-1f, 1f).toDouble() * Int.MAX_VALUE).toInt()
                    bb.putInt(v)
                }
            }
            out.write(bb.array())
        }
    }
}
