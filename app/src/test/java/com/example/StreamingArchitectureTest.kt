package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CameraConflictState
import com.example.data.model.HostConnectionMode
import com.example.data.model.StreamCodec
import com.example.data.model.StreamMode
import com.example.media.AudioCaptureListener
import com.example.media.AudioController
import com.example.media.CameraCapturePipeline
import com.example.media.DeviceCapabilities
import com.example.media.RecoveryManager
import com.example.server.CameraStreamBroadcaster
import com.example.server.WebcamStreamService
import com.example.transport.PacketType
import com.example.transport.StreamPacket
import com.example.transport.TransportClientListener
import com.example.transport.TransportManager
import com.example.transport.WifiTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StreamingArchitectureTest {

    // ==========================================
    // Priority 5: Packet Format Tests
    // ==========================================

    @Test
    fun packetFormat_videoKeyframeFlagEncoding() {
        val payload = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x01)
        val packet = StreamPacket(
            codec = StreamCodec.H264,
            type = PacketType.VIDEO_NAL,
            isKeyframe = true,
            isConfig = false,
            timestampUs = 1000000L,
            payload = payload
        )

        val encoded = StreamPacket.encode(packet)
        assertEquals('F'.code.toByte(), encoded[0])
        assertEquals('O'.code.toByte(), encoded[1])
        assertEquals('C'.code.toByte(), encoded[2])
        assertEquals('L'.code.toByte(), encoded[3])
        assertEquals(1.toByte(), encoded[4]) // Version
        assertEquals(1.toByte(), encoded[5]) // H264 Codec ID
        assertEquals(PacketType.VIDEO_NAL.code, encoded[6])
        assertEquals(StreamPacket.FLAG_KEYFRAME, encoded[7]) // Flag 0x01: Keyframe

        val decoded = StreamPacket.decode(encoded)
        assertNotNull(decoded)
        assertEquals(StreamCodec.H264, decoded?.codec)
        assertEquals(PacketType.VIDEO_NAL, decoded?.type)
        assertTrue(decoded?.isKeyframe == true)
        assertFalse(decoded?.isConfig == true)
        assertEquals(1000000L, decoded?.timestampUs)
        assertTrue(decoded?.payload?.contentEquals(payload) == true)
    }

    @Test
    fun packetFormat_videoCodecConfigFlagEncoding() {
        val spsPpsPayload = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00) // SPS
        val packet = StreamPacket(
            codec = StreamCodec.H264,
            type = PacketType.VIDEO_NAL,
            isKeyframe = false,
            isConfig = true,
            timestampUs = 2000000L,
            payload = spsPpsPayload
        )

        val encoded = StreamPacket.encode(packet)
        assertEquals(StreamPacket.FLAG_CONFIG, encoded[7]) // Flag 0x02: Config SPS/PPS

        val decoded = StreamPacket.decode(encoded)
        assertNotNull(decoded)
        assertFalse(decoded?.isKeyframe == true)
        assertTrue(decoded?.isConfig == true)
        assertEquals(StreamCodec.H264, decoded?.codec)
        assertTrue(decoded?.payload?.contentEquals(spsPpsPayload) == true)
    }

    @Test
    fun packetFormat_videoKeyframeWithConfigFlags() {
        val payload = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67, 0x00, 0x00, 0x00, 0x01, 0x65)
        val packet = StreamPacket(
            codec = StreamCodec.H264,
            type = PacketType.VIDEO_NAL,
            isKeyframe = true,
            isConfig = true,
            timestampUs = 3000000L,
            payload = payload
        )

        val encoded = StreamPacket.encode(packet)
        val expectedFlags = (StreamPacket.FLAG_KEYFRAME.toInt() or StreamPacket.FLAG_CONFIG.toInt()).toByte()
        assertEquals(expectedFlags, encoded[7]) // Flag 0x03: Both Keyframe and Config

        val decoded = StreamPacket.decode(encoded)
        assertNotNull(decoded)
        assertTrue(decoded?.isKeyframe == true)
        assertTrue(decoded?.isConfig == true)
    }

    @Test
    fun packetFormat_audioRawPcmCodecEncoding() {
        val pcmPayload = ByteArray(1920) { it.toByte() } // 16-bit PCM 48kHz mono samples
        val packet = StreamPacket(
            codec = StreamCodec.PCM,
            type = PacketType.AUDIO_RAW,
            isKeyframe = false,
            isConfig = false,
            timestampUs = 4000000L,
            payload = pcmPayload
        )

        val encoded = StreamPacket.encode(packet)
        assertEquals(4.toByte(), encoded[5]) // PCM Codec ID = 4
        assertEquals(PacketType.AUDIO_RAW.code, encoded[6])
        assertEquals(0.toByte(), encoded[7]) // No keyframe/config flags for raw PCM

        val decoded = StreamPacket.decode(encoded)
        assertNotNull(decoded)
        assertEquals(StreamCodec.PCM, decoded?.codec)
        assertEquals(PacketType.AUDIO_RAW, decoded?.type)
        assertFalse(decoded?.isKeyframe == true)
        assertFalse(decoded?.isConfig == true)
        assertEquals(4000000L, decoded?.timestampUs)
        assertTrue(decoded?.payload?.contentEquals(pcmPayload) == true)
    }

    @Test
    fun packetFormat_rejectsMalformedOrTruncatedPacket() {
        // Less than header size
        val truncated = byteArrayOf('F'.code.toByte(), 'O'.code.toByte())
        assertNull(StreamPacket.decode(truncated))

        // Invalid magic bytes
        val invalidMagic = ByteArray(25)
        assertNull(StreamPacket.decode(invalidMagic))
    }

    // ==========================================
    // Priority 2: Broadcaster Format Integrity Tests
    // ==========================================

    @Test
    fun broadcaster_rejectsH264NalBytesAndPreservesValidJpeg() {
        // Valid JPEG begins with 0xFF, 0xD8
        val validJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        CameraStreamBroadcaster.pushFrame(validJpeg)
        assertTrue(CameraStreamBroadcaster.getLatestFrame().contentEquals(validJpeg))

        // Raw H.264 NAL starts with 0x00, 0x00, 0x00, 0x01 - must be rejected!
        val h264Nal = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x08, 0x09)
        CameraStreamBroadcaster.pushFrame(h264Nal)

        // The broadcaster must retain the valid JPEG, NOT the H.264 NAL
        val currentFrame = CameraStreamBroadcaster.getLatestFrame()
        assertEquals(0xFF.toByte(), currentFrame[0])
        assertEquals(0xD8.toByte(), currentFrame[1])
        assertFalse(currentFrame.contentEquals(h264Nal))
    }

    // ==========================================
    // Priority 4: Video-Only & Audio Permission Tests
    // ==========================================

    @Test
    fun audioController_neverRecordsInVideoOnlyModeEvenWithPermission() {
        var callbackReceived = false
        val listener = object : AudioCaptureListener {
            override fun onAudioData(pcmBytes: ByteArray, timestampUs: Long) {
                callbackReceived = true
            }
        }
        val controller = AudioController(listener)

        // When streamMode is VIDEO_ONLY, must strictly return false and not record
        val startedWithPermission = controller.startRecording(hasPermission = true, streamMode = StreamMode.VIDEO_ONLY)
        assertFalse(startedWithPermission)
        assertFalse(controller.isRecording)
        assertFalse(callbackReceived)

        val startedWithoutPermission = controller.startRecording(hasPermission = false, streamMode = StreamMode.VIDEO_ONLY)
        assertFalse(startedWithoutPermission)
        assertFalse(controller.isRecording)
        assertFalse(callbackReceived)
    }

    @Test
    fun audioController_requiresPermissionForVideoAndAudioMode() {
        val listener = object : AudioCaptureListener {
            override fun onAudioData(pcmBytes: ByteArray, timestampUs: Long) {}
        }
        val controller = AudioController(listener)

        // When permission is false, VIDEO_AND_AUDIO must reject audio capture
        val started = controller.startRecording(hasPermission = false, streamMode = StreamMode.VIDEO_AND_AUDIO)
        assertFalse(started)
        assertFalse(controller.isRecording)
    }

    // ==========================================
    // Priority 3: Startup Failure & Clean Teardown Tests
    // ==========================================

    @Test
    fun recoveryManager_cameraConflictTriggersRetryAndRebinds() {
        var cameraRebound = false
        var encoderReinitialized = false

        val recoveryManager = RecoveryManager(
            onRetryCamera = {
                cameraRebound = true
                true
            },
            onReinitEncoder = {
                encoderReinitialized = true
                true
            }
        )

        assertEquals(CameraConflictState.NORMAL, recoveryManager.conflictState.value)

        // Camera conflict
        recoveryManager.onCameraConflictDetected("Camera taken by incoming phone call")
        assertEquals(CameraConflictState.UNAVAILABLE, recoveryManager.conflictState.value)
        assertNotNull(recoveryManager.errorMessage.value)

        // Manual retry
        val recovered = recoveryManager.manualRetry()
        assertTrue(recovered)
        assertTrue(cameraRebound)
        assertEquals(CameraConflictState.NORMAL, recoveryManager.conflictState.value)
        assertNull(recoveryManager.errorMessage.value)
    }

    @Test
    fun cameraCapturePipeline_managesEncoderSurfaceSafely() {
        assertNull(CameraCapturePipeline.getEncoderSurface())

        // Setting encoder surface updates dimensions
        CameraCapturePipeline.setEncoderSurface(null, 1280, 720)
        assertEquals(1280, CameraCapturePipeline.encoderWidth)
        assertEquals(720, CameraCapturePipeline.encoderHeight)

        CameraCapturePipeline.unbind()
        assertNull(CameraCapturePipeline.getEncoderSurface())
    }

    // ==========================================
    // Priority 6: Device Capabilities & Encoder Selection Tests
    // ==========================================

    @Test
    fun deviceCapabilities_detectsHardwareOrSoftwareAccurately() {
        val label = DeviceCapabilities.getHardwareAccelLabel()
        assertNotNull(label)
        assertTrue(label.isNotEmpty())

        val clamped = DeviceCapabilities.clampConfiguration(1920, 1080, 30)
        assertEquals(1920, clamped.width)
        assertEquals(1080, clamped.height)
        assertEquals(30, clamped.fps)
    }

    @Test
    fun deviceCapabilities_clampsUnsupportedResolutionSafely() {
        val clamped = DeviceCapabilities.clampConfiguration(10000, 10000, 30)
        assertTrue(clamped.isClamped)
        assertTrue(clamped.width <= 3840)
        assertTrue(clamped.height <= 2160)
        assertNotNull(clamped.explanation)
    }

    @Test
    fun deviceCapabilities_clampsUnsupportedFpsSafely() {
        val clamped = DeviceCapabilities.clampConfiguration(1920, 1080, 360)
        assertTrue(clamped.isClamped)
        assertTrue(clamped.fps <= 60)
        assertNotNull(clamped.explanation)
    }

    // ==========================================
    // Priority 7: Wi-Fi Authentication Tests
    // ==========================================

    @Test
    fun wifiTransport_rejectsUnauthenticatedHttpStreamAccess() {
        val testPort = 18090
        val transport = WifiTransport(port = testPort, pairingPinProvider = { "987654" })
        var authFailedReported = false

        transport.setClientListener(object : TransportClientListener {
            override fun onClientConnected(clientId: String, address: String) {}
            override fun onClientAuthenticated(clientId: String) {}
            override fun onClientDisconnected(clientId: String) {}
            override fun onAuthChallengeFailed(clientId: String) {
                authFailedReported = true
            }
        })

        transport.start()
        assertTrue(transport.isRunning)

        try {
            // Client attempts to stream without providing the PIN
            val socket = Socket("127.0.0.1", testPort)
            val out = socket.getOutputStream()
            out.write("GET /stream.h264 HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            out.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val responseLine = reader.readLine()
            assertNotNull(responseLine)
            assertTrue("Expected 401 Unauthorized but got: $responseLine", responseLine.contains("401 Unauthorized"))
            socket.close()
        } finally {
            transport.stop()
            assertFalse(transport.isRunning)
        }
    }

    @Test
    fun wifiTransport_acceptsAuthenticatedHttpStreamAccessWithQueryPin() {
        val testPort = 18091
        val transport = WifiTransport(port = testPort, pairingPinProvider = { "123456" })
        var authenticatedReported = false

        transport.setClientListener(object : TransportClientListener {
            override fun onClientConnected(clientId: String, address: String) {}
            override fun onClientAuthenticated(clientId: String) {
                authenticatedReported = true
            }
            override fun onClientDisconnected(clientId: String) {}
            override fun onAuthChallengeFailed(clientId: String) {}
        })

        transport.start()
        assertTrue(transport.isRunning)

        try {
            // Client supplies ?pin=123456
            val socket = Socket("127.0.0.1", testPort)
            val out = socket.getOutputStream()
            out.write("GET /stream.h264?pin=123456 HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            out.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val responseLine = reader.readLine()
            assertNotNull(responseLine)
            assertTrue("Expected 200 OK but got: $responseLine", responseLine.contains("200 OK"))
            socket.close()
        } finally {
            transport.stop()
            assertFalse(transport.isRunning)
        }
    }
}
