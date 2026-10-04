package com.focal.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import com.focal.android.data.model.CameraConflictState
import com.focal.android.data.model.HostConnectionMode
import com.focal.android.data.model.StreamCodec
import com.focal.android.data.model.StreamMode
import com.focal.android.media.AudioCaptureListener
import com.focal.android.media.AudioController
import com.focal.android.media.CameraCapturePipeline
import com.focal.android.media.DeviceCapabilities
import com.focal.android.media.RecoveryManager
import com.focal.android.server.CameraStreamBroadcaster
import com.focal.android.server.WebcamStreamService
import com.focal.android.transport.FocalDiscoveryManager
import com.focal.android.transport.PacketType
import com.focal.android.transport.PairingManager
import com.focal.android.transport.StreamPacket
import com.focal.android.transport.TransportClientListener
import com.focal.android.transport.TransportManager
import com.focal.android.transport.WifiTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StreamingArchitectureTest {

    // ==========================================
    // Priority 5 & 7: Packet Format & Protocol Tests
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
    fun packetFormat_distinguishesVideoAndAudioPackets() {
        val videoPacket = StreamPacket(
            codec = StreamCodec.H264,
            type = PacketType.VIDEO_NAL,
            isKeyframe = true,
            payload = byteArrayOf(1, 2, 3)
        )
        val audioPacket = StreamPacket(
            codec = StreamCodec.PCM,
            type = PacketType.AUDIO_RAW,
            payload = byteArrayOf(4, 5, 6)
        )

        val encodedVideo = StreamPacket.encode(videoPacket)
        val encodedAudio = StreamPacket.encode(audioPacket)

        assertNotEquals(encodedVideo[5], encodedAudio[5]) // Codec IDs differ (1 vs 4)
        assertNotEquals(encodedVideo[6], encodedAudio[6]) // Packet types differ (VIDEO_NAL vs AUDIO_RAW)
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
    // Priority 2 & 4: Media Separation Tests
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
    // Priority 2: Service Readiness & Startup Failure Tests
    // ==========================================

    @Test
    fun service_initialStateIsNotRunningUntilReadinessEstablished() {
        WebcamStreamService.resetStateForTesting()
        assertFalse(WebcamStreamService.isRunning.value)
        assertFalse(WebcamStreamService.isAudioActive.value)
        assertNull(WebcamStreamService.startupError.value)
    }

    @Test
    fun service_marksReadyOnlyWhenTriggered() {
        WebcamStreamService.resetStateForTesting()
        assertFalse(WebcamStreamService.isRunning.value)

        WebcamStreamService.markStreamReadyForTesting()
        assertTrue(WebcamStreamService.isRunning.value)

        WebcamStreamService.resetStateForTesting()
        assertFalse(WebcamStreamService.isRunning.value)
    }

    // ==========================================
    // Priority 1 & 3: Camera Capture Pipeline Independence & Canvas Relay Tests
    // ==========================================

    @Test
    fun cameraCapturePipeline_managesEncoderSurfaceSafely() {
        assertNull(CameraCapturePipeline.getEncoderSurface())

        CameraCapturePipeline.setEncoderSurface(null, 1280, 720)
        assertEquals(1280, CameraCapturePipeline.encoderWidth)
        assertEquals(720, CameraCapturePipeline.encoderHeight)

        CameraCapturePipeline.unbind()
        assertNull(CameraCapturePipeline.getEncoderSurface())
    }

    @Test
    fun cameraCapturePipeline_handlesRotationAndAspectRatioSafely() {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)

        val surfaceTexture = android.graphics.SurfaceTexture(0)
        val fakeSurface = Surface(surfaceTexture)
        val rendered = CameraCapturePipeline.renderBitmapToSurface(bitmap, fakeSurface, rotationDegrees = 90)
        fakeSurface.release()
        surfaceTexture.release()
        bitmap.recycle()
    }

    @Test
    fun cameraCapturePipeline_attachAndDetachPreviewIndependentOfEncoder() {
        CameraCapturePipeline.setEncoderSurface(null, 1920, 1080)
        CameraCapturePipeline.attachPreviewSurfaceProvider(null)
        CameraCapturePipeline.detachPreviewSurfaceProvider()

        // Detaching preview does not unbind the configured encoder dimensions
        assertEquals(1920, CameraCapturePipeline.encoderWidth)
        assertEquals(1080, CameraCapturePipeline.encoderHeight)
        CameraCapturePipeline.unbind()
    }

    // ==========================================
    // Priority 3 & 6: Recovery and Shutdown Tests
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
    fun transportManager_stopsAndCleansUpAllTransports() {
        val tm = TransportManager(pairingPinProvider = { "123456" }, wifiPort = 18095, adbPort = 18096)
        tm.start(HostConnectionMode.WIFI)
        assertTrue(tm.isAnyRunning())

        tm.stop()
        assertFalse(tm.isAnyRunning())
        assertEquals(0, tm.connectedClientsCount.value)
    }

    // ==========================================
    // Priority 6 & 7: Device Capabilities & Bitrate Tests
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
    fun deviceCapabilities_honorsRequestedBitrateWhenSupported() {
        val requestedBitrate = 8.5f
        val clamped = DeviceCapabilities.clampConfiguration(
            requestedWidth = 1920,
            requestedHeight = 1080,
            requestedFps = 60,
            requestedBitrateMbps = requestedBitrate
        )
        assertEquals(requestedBitrate, clamped.bitrateMbps, 0.01f)
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
    // Priority 5: Hardened Wi-Fi Pairing Tests
    // ==========================================

    @Test
    fun pairingManager_validatesSixDigitCodeAndExpiration() {
        PairingManager.setPin("654321", validityMs = 60000)
        assertEquals("654321", PairingManager.currentPin)
        assertTrue(PairingManager.isPinValid("654321"))
        assertFalse(PairingManager.isPinValid("000000"))
        assertFalse(PairingManager.isPinValid(null))
        assertFalse(PairingManager.isPinValid(""))

        // Expired pin
        PairingManager.setPin("654321", validityMs = -1000)
        assertFalse(PairingManager.isPinValid("654321"))

        // Regenerate new pin
        val generated = PairingManager.generateNewPin(validityMs = 60000)
        assertEquals(6, generated.length)
        assertTrue(PairingManager.isPinValid(generated))
    }

    @Test
    fun wifiTransport_rejectsUnauthenticatedHttpStreamAccess() {
        val testPort = 18090
        val transport = WifiTransport(port = testPort, pairingPinProvider = { "987654" })
        val challengeLatch = java.util.concurrent.CountDownLatch(1)

        transport.setClientListener(object : TransportClientListener {
            override fun onClientConnected(clientId: String, address: String) {}
            override fun onClientAuthenticated(clientId: String) {}
            override fun onClientDisconnected(clientId: String) {}
            override fun onAuthChallengeFailed(clientId: String) {
                challengeLatch.countDown()
            }
        })

        transport.start()
        assertTrue(transport.isRunning)

        try {
            val socket = Socket("127.0.0.1", testPort)
            val out = socket.getOutputStream()
            out.write("GET /stream.h264 HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            out.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val responseLine = reader.readLine()
            assertNotNull(responseLine)
            assertTrue("Expected 401 Unauthorized but got: $responseLine", responseLine.contains("401 Unauthorized"))
            assertTrue(challengeLatch.await(5, java.util.concurrent.TimeUnit.SECONDS))
            socket.close()
        } finally {
            transport.stop()
            assertFalse(transport.isRunning)
        }
    }

    @Test
    fun wifiTransport_rejectsQueryParameterPinAuthentication() {
        val testPort = 18091
        // Even with the correct PIN in query string, URL query params MUST NOT be accepted
        val transport = WifiTransport(port = testPort, pairingPinProvider = { "123456" })

        transport.start()
        assertTrue(transport.isRunning)

        try {
            val socket = Socket("127.0.0.1", testPort)
            val out = socket.getOutputStream()
            out.write("GET /stream.h264?pin=123456 HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            out.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val responseLine = reader.readLine()
            assertNotNull(responseLine)
            assertTrue("Query parameter pin must be rejected with 401: $responseLine", responseLine.contains("401 Unauthorized"))
            socket.close()
        } finally {
            transport.stop()
            assertFalse(transport.isRunning)
        }
    }

    @Test
    fun wifiTransport_acceptsHeaderPinAuthentication() {
        val testPort = 18092
        val transport = WifiTransport(port = testPort, pairingPinProvider = { "555888" })
        val authLatch = java.util.concurrent.CountDownLatch(1)

        transport.setClientListener(object : TransportClientListener {
            override fun onClientConnected(clientId: String, address: String) {}
            override fun onClientAuthenticated(clientId: String) {
                authLatch.countDown()
            }
            override fun onClientDisconnected(clientId: String) {}
            override fun onAuthChallengeFailed(clientId: String) {}
        })

        transport.start()
        assertTrue(transport.isRunning)

        try {
            val socket = Socket("127.0.0.1", testPort)
            val out = socket.getOutputStream()
            out.write("GET /stream.h264 HTTP/1.1\r\nHost: localhost\r\nX-Focal-Pin: 555888\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            out.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val responseLine = reader.readLine()
            assertNotNull(responseLine)
            assertTrue("Expected 200 OK but got: $responseLine", responseLine.contains("200 OK"))
            assertTrue("Authentication callback should fire", authLatch.await(5, java.util.concurrent.TimeUnit.SECONDS))
            socket.close()
        } finally {
            transport.stop()
            assertFalse(transport.isRunning)
        }
    }

    @Test
    fun wifiTransport_acceptsBearerAuthorizationHeader() {
        val testPort = 18093
        val transport = WifiTransport(port = testPort, pairingPinProvider = { "777999" })
        val authLatch = java.util.concurrent.CountDownLatch(1)

        transport.setClientListener(object : TransportClientListener {
            override fun onClientConnected(clientId: String, address: String) {}
            override fun onClientAuthenticated(clientId: String) {
                authLatch.countDown()
            }
            override fun onClientDisconnected(clientId: String) {}
            override fun onAuthChallengeFailed(clientId: String) {}
        })

        transport.start()
        assertTrue(transport.isRunning)

        try {
            val socket = Socket("127.0.0.1", testPort)
            val out = socket.getOutputStream()
            out.write("GET /stream.h264 HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer 777999\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            out.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val responseLine = reader.readLine()
            assertNotNull(responseLine)
            assertTrue("Expected 200 OK but got: $responseLine", responseLine.contains("200 OK"))
            assertTrue("Authentication callback should fire", authLatch.await(5, java.util.concurrent.TimeUnit.SECONDS))
            socket.close()
        } finally {
            transport.stop()
            assertFalse(transport.isRunning)
        }
    }

    // ==========================================
    // Priority 8: mDNS Auto-Discovery Tests
    // ==========================================

    @Test
    fun focalDiscoveryManager_serviceTypeAndLifecycle() {
        assertEquals("_focal._tcp.", FocalDiscoveryManager.SERVICE_TYPE)
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Test registering and unregistering safely
        FocalDiscoveryManager.registerService(context, port = 8080, pin = "654321")
        FocalDiscoveryManager.unregisterService()
        assertFalse(FocalDiscoveryManager.isRegistered)
        assertNull(FocalDiscoveryManager.registeredServiceName)
    }
}
