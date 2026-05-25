package com.example.recorderproject.audio

import com.example.recorderproject.model.RecordFile
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Phase 4 — iXML metadata writer.
 * iXML is the broadcast/film standard for embedded WAV metadata: scene/take/roll/circled/notes.
 * This writes a minimal valid iXML chunk into an existing WAV.
 */
object IxmlWriter {

    fun appendIxml(file: File, record: RecordFile) {
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<BWFXML>")
            append("<IXML_VERSION>2.10</IXML_VERSION>")
            append("<PROJECT>MEATrec</PROJECT>")
            append("<SCENE>").append(escape(record.sceneName)).append("</SCENE>")
            append("<TAKE>").append(parseTakeNumber(record.name)).append("</TAKE>")
            append("<NOTE>").append(escape(record.notes)).append("</NOTE>")
            record.tags.takeIf { it.isNotBlank() }?.let {
                append("<TAG>").append(escape(it)).append("</TAG>")
            }
            record.locationTag?.let { append("<LOCATION>").append(escape(it)).append("</LOCATION>") }
            append("</BWFXML>")
        }
        val payload = xml.toByteArray(Charsets.UTF_8)
        val chunkSize = payload.size
        val padded = if (chunkSize and 1 == 1) payload + 0.toByte() else payload

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            header.put("iXML".toByteArray(Charsets.US_ASCII))
            header.putInt(chunkSize)
            raf.write(header.array())
            raf.write(padded)
            // Update RIFF size
            val newRiffSize = (raf.length() - 8).toInt()
            raf.seek(4)
            raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(newRiffSize).array())
        }
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun parseTakeNumber(filename: String): String {
        val m = Regex("_take(\\d+)", RegexOption.IGNORE_CASE).find(filename)
        return m?.groupValues?.get(1) ?: "1"
    }
}
