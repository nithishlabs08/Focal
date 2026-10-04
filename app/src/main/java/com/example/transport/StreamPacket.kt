package com.example.transport

import com.example.data.model.StreamCodec
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class PacketType(val code: Byte) {
    VIDEO_NAL(1),
    AUDIO_RAW(2),
    METADATA(3),
    AUTH_CHALLENGE(4),
    AUTH_RESPONSE(5),
    HEARTBEAT(6);

    companion object {
        fun fromCode(code: Byte): PacketType = entries.find { it.code == code } ?: METADATA
    }
}

/**
 * Binary stream packet protocol for low-latency transmission:
 * [0..3]   Magic bytes 'FOCL' (0x46, 0x4F, 0x43, 0x4C)
 * [4]      Protocol Version (1)
 * [5]      Codec ID (1: H264, 2: HEVC, 3: MJPEG)
 * [6]      Packet Type (1: Video, 2: Audio, etc.)
 * [7]      Flags (0x01: Keyframe, 0x02: Config SPS/PPS)
 * [8..15]  Timestamp in microseconds (Long, Big Endian)
 * [16..19] Payload length (Int, Big Endian)
 * [20..N]  Payload data bytes
 */
data class StreamPacket(
    val codec: StreamCodec,
    val type: PacketType,
    val isKeyframe: Boolean,
    val timestampUs: Long,
    val payload: ByteArray
) {
    companion object {
        val MAGIC = byteArrayOf('F'.code.toByte(), 'O'.code.toByte(), 'C'.code.toByte(), 'L'.code.toByte())
        const val HEADER_SIZE = 20

        fun encode(packet: StreamPacket): ByteArray {
            val totalSize = HEADER_SIZE + packet.payload.size
            val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

            buffer.put(MAGIC)
            buffer.put(1.toByte()) // Version
            val codecByte: Byte = when (packet.codec) {
                StreamCodec.H264 -> 1
                StreamCodec.HEVC -> 2
                StreamCodec.MJPEG -> 3
            }
            buffer.put(codecByte)
            buffer.put(packet.type.code)
            val flags: Byte = if (packet.isKeyframe) 1 else 0
            buffer.put(flags)
            buffer.putLong(packet.timestampUs)
            buffer.putInt(packet.payload.size)
            buffer.put(packet.payload)

            return buffer.array()
        }

        fun decode(data: ByteArray, offset: Int = 0, length: Int = data.size): StreamPacket? {
            if (length < HEADER_SIZE) return null
            val buffer = ByteBuffer.wrap(data, offset, length).order(ByteOrder.BIG_ENDIAN)

            val magic0 = buffer.get()
            val magic1 = buffer.get()
            val magic2 = buffer.get()
            val magic3 = buffer.get()

            if (magic0 != MAGIC[0] || magic1 != MAGIC[1] || magic2 != MAGIC[2] || magic3 != MAGIC[3]) {
                return null
            }

            val version = buffer.get()
            if (version.toInt() != 1) return null

            val codecByte = buffer.get()
            val codec = when (codecByte.toInt()) {
                1 -> StreamCodec.H264
                2 -> StreamCodec.HEVC
                3 -> StreamCodec.MJPEG
                else -> StreamCodec.H264
            }

            val typeByte = buffer.get()
            val type = PacketType.fromCode(typeByte)

            val flags = buffer.get()
            val isKeyframe = (flags.toInt() and 1) != 0

            val timestampUs = buffer.getLong()
            val payloadLen = buffer.getInt()

            if (payloadLen < 0 || payloadLen > buffer.remaining()) {
                return null
            }

            val payload = ByteArray(payloadLen)
            buffer.get(payload)

            return StreamPacket(codec, type, isKeyframe, timestampUs, payload)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StreamPacket
        if (codec != other.codec) return false
        if (type != other.type) return false
        if (isKeyframe != other.isKeyframe) return false
        if (timestampUs != other.timestampUs) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = codec.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + isKeyframe.hashCode()
        result = 31 * result + timestampUs.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
