package com.focal.android.transport

import com.focal.android.data.model.StreamCodec
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
 * [5]      Codec ID:
 *            1: H264 (Annex-B NAL)
 *            2: HEVC (H.265)
 *            3: MJPEG
 *            4: PCM (Linear PCM 16-bit 48kHz Mono)
 *            5: AAC (AAC-LC)
 * [6]      Packet Type (1: VIDEO_NAL, 2: AUDIO_RAW, 3: METADATA, 4: AUTH_CHALLENGE, 5: AUTH_RESPONSE, 6: HEARTBEAT)
 * [7]      Flags:
 *            Bit 0 (0x01): Keyframe (IDR frame)
 *            Bit 1 (0x02): Codec Configuration (SPS/PPS header for H.264/HEVC)
 * [8..15]  Timestamp in microseconds (Long, Big Endian)
 * [16..19] Payload length in bytes (Int, Big Endian)
 * [20..N]  Payload data bytes
 *
 * Audio Format Specification for AUDIO_RAW + PCM:
 *   - Encoding: Signed 16-bit Linear PCM (ENCODING_PCM_16BIT)
 *   - Byte order: Little-endian (standard Android AudioRecord)
 *   - Channel configuration: 1 channel (Mono)
 *   - Sample rate: 48,000 Hz
 *   - Frame size: 2 bytes per sample
 */
data class StreamPacket(
    val codec: StreamCodec,
    val type: PacketType,
    val isKeyframe: Boolean = false,
    val isConfig: Boolean = false,
    val timestampUs: Long = 0L,
    val payload: ByteArray
) {
    // Backwards-compatible constructor for 5-argument calls without isConfig
    constructor(
        codec: StreamCodec,
        type: PacketType,
        isKeyframe: Boolean,
        timestampUs: Long,
        payload: ByteArray
    ) : this(codec, type, isKeyframe, false, timestampUs, payload)

    companion object {
        val MAGIC = byteArrayOf('F'.code.toByte(), 'O'.code.toByte(), 'C'.code.toByte(), 'L'.code.toByte())
        const val HEADER_SIZE = 20

        const val FLAG_KEYFRAME: Byte = 0x01
        const val FLAG_CONFIG: Byte = 0x02

        fun encode(packet: StreamPacket): ByteArray {
            val totalSize = HEADER_SIZE + packet.payload.size
            val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

            buffer.put(MAGIC)
            buffer.put(1.toByte()) // Version 1
            val codecByte: Byte = when (packet.codec) {
                StreamCodec.H264 -> 1
                StreamCodec.HEVC -> 2
                StreamCodec.MJPEG -> 3
                StreamCodec.PCM -> 4
                StreamCodec.AAC -> 5
            }
            buffer.put(codecByte)
            buffer.put(packet.type.code)

            var flags: Byte = 0
            if (packet.isKeyframe) flags = (flags.toInt() or FLAG_KEYFRAME.toInt()).toByte()
            if (packet.isConfig) flags = (flags.toInt() or FLAG_CONFIG.toInt()).toByte()
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
                4 -> StreamCodec.PCM
                5 -> StreamCodec.AAC
                else -> StreamCodec.H264
            }

            val typeByte = buffer.get()
            val type = PacketType.fromCode(typeByte)

            val flags = buffer.get()
            val isKeyframe = (flags.toInt() and FLAG_KEYFRAME.toInt()) != 0
            val isConfig = (flags.toInt() and FLAG_CONFIG.toInt()) != 0

            val timestampUs = buffer.getLong()
            val payloadLen = buffer.getInt()

            if (payloadLen < 0 || payloadLen > buffer.remaining()) {
                return null
            }

            val payload = ByteArray(payloadLen)
            buffer.get(payload)

            return StreamPacket(codec, type, isKeyframe, isConfig, timestampUs, payload)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StreamPacket
        if (codec != other.codec) return false
        if (type != other.type) return false
        if (isKeyframe != other.isKeyframe) return false
        if (isConfig != other.isConfig) return false
        if (timestampUs != other.timestampUs) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = codec.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + isKeyframe.hashCode()
        result = 31 * result + isConfig.hashCode()
        result = 31 * result + timestampUs.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
