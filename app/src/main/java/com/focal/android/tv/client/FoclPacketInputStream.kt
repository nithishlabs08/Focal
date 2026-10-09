package com.focal.android.tv.client

import com.focal.android.transport.StreamPacket
import java.io.InputStream

/**
 * Reads FOCL [StreamPacket] frames from a TCP stream that may contain discarded
 * stray non-binary lines (legacy keep-alive text).
 */
internal class FoclPacketInputStream(private val input: InputStream) {

    private val headerScratch = ByteArray(StreamPacket.HEADER_SIZE)

    fun readNextPayload(): FoclFrame? {
        while (true) {
            if (!readFully(headerScratch, 0, 4)) return null
            if (isFoclMagic(headerScratch)) {
                if (!readFully(headerScratch, 4, StreamPacket.HEADER_SIZE - 4)) return null
                return parseHeaderAndPayload(headerScratch)
            }
            if (!discardLineStartingWith(headerScratch)) return null
        }
    }

    private fun parseHeaderAndPayload(header: ByteArray): FoclFrame? {
        val version = header[4]
        val codecByte = header[5]
        val typeCode = header[6]
        val flags = header[7]
        val timestampUs = readLongBe(header, 8)
        val payloadLen = readIntBe(header, 16)

        if (payloadLen < 0 || payloadLen > 5_000_000) return null

        val payload = ByteArray(payloadLen)
        if (!readFully(payload, 0, payloadLen)) return null

        return FoclFrame(version, codecByte, typeCode, flags, timestampUs, payload)
    }

    private fun discardLineStartingWith(prefix: ByteArray): Boolean {
        var sawNewline = false
        for (i in 1 until prefix.size) {
            val b = prefix[i]
            if (b == '\n'.code.toByte()) {
                sawNewline = true
                break
            }
        }
        if (sawNewline) return true

        while (true) {
            val b = input.read()
            if (b < 0) return false
            if (b == '\n'.code) return true
        }
    }

    private fun isFoclMagic(bytes: ByteArray): Boolean {
        return bytes[0] == 'F'.code.toByte() &&
            bytes[1] == 'O'.code.toByte() &&
            bytes[2] == 'C'.code.toByte() &&
            bytes[3] == 'L'.code.toByte()
    }

    private fun readFully(buffer: ByteArray, offset: Int, length: Int): Boolean {
        var bytesRead = 0
        while (bytesRead < length) {
            val count = input.read(buffer, offset + bytesRead, length - bytesRead)
            if (count < 0) return false
            bytesRead += count
        }
        return true
    }

    private fun readLongBe(header: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (header[offset + i].toLong() and 0xFFL)
        }
        return value
    }

    private fun readIntBe(header: ByteArray, offset: Int): Int {
        return ((header[offset].toInt() and 0xFF) shl 24) or
            ((header[offset + 1].toInt() and 0xFF) shl 16) or
            ((header[offset + 2].toInt() and 0xFF) shl 8) or
            (header[offset + 3].toInt() and 0xFF)
    }
}

internal data class FoclFrame(
    val version: Byte,
    val codecByte: Byte,
    val typeCode: Byte,
    val flags: Byte,
    val timestampUs: Long,
    val payload: ByteArray
)
