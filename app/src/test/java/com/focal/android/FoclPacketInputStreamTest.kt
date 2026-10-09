package com.focal.android

import com.focal.android.data.model.StreamCodec
import com.focal.android.transport.PacketType
import com.focal.android.transport.StreamPacket
import com.focal.android.tv.client.FoclPacketInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class FoclPacketInputStreamTest {

    @Test
    fun readNextPayload_skipsTextLineBeforeFoclPacket() {
        val packet = StreamPacket(
            codec = StreamCodec.H264,
            type = PacketType.VIDEO_NAL,
            isKeyframe = true,
            timestampUs = 42L,
            payload = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65)
        )
        val encoded = StreamPacket.encode(packet)
        val prefix = "PONG\n".toByteArray(StandardCharsets.UTF_8)
        val combined = prefix + encoded

        val reader = FoclPacketInputStream(ByteArrayInputStream(combined))
        val frame = reader.readNextPayload()

        assertNotNull(frame)
        assertEquals(PacketType.VIDEO_NAL.code, frame!!.typeCode)
        assertTrue(frame.payload.contentEquals(packet.payload))
    }
}
