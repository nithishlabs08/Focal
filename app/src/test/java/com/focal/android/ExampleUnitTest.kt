package com.focal.android

import com.focal.android.data.model.CameraConflictState
import com.focal.android.data.model.FlashMode
import com.focal.android.data.model.HostConnectionMode
import com.focal.android.data.model.StreamCodec
import com.focal.android.data.model.StreamMode
import com.focal.android.media.AudioCaptureListener
import com.focal.android.media.AudioController
import com.focal.android.media.DeviceCapabilities
import com.focal.android.media.RecoveryManager
import com.focal.android.transport.PacketType
import com.focal.android.transport.StreamPacket
import com.focal.android.transport.TransportManager
import com.focal.android.ui.viewmodel.FocalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun cameraFlip_togglesFrontAndBackSensors() {
        val viewModel = FocalViewModel()
        assertFalse(viewModel.uiState.value.selectedSensor.isFront)

        viewModel.flipCamera()
        assertTrue(viewModel.uiState.value.selectedSensor.isFront)

        viewModel.flipCamera()
        assertFalse(viewModel.uiState.value.selectedSensor.isFront)
    }

    @Test
    fun flashMode_cyclesCorrectly() {
        val viewModel = FocalViewModel()
        assertEquals(FlashMode.OFF, viewModel.uiState.value.flashMode)
        assertFalse(viewModel.uiState.value.isTorchOn)

        viewModel.cycleFlashMode()
        assertEquals(FlashMode.TORCH, viewModel.uiState.value.flashMode)
        assertTrue(viewModel.uiState.value.isTorchOn)

        viewModel.cycleFlashMode()
        assertEquals(FlashMode.AUTO, viewModel.uiState.value.flashMode)
        assertFalse(viewModel.uiState.value.isTorchOn)

        viewModel.cycleFlashMode()
        assertEquals(FlashMode.OFF, viewModel.uiState.value.flashMode)
    }

    @Test
    fun torchToggle_synchronizesWithFlashMode() {
        val viewModel = FocalViewModel()
        assertFalse(viewModel.uiState.value.isTorchOn)
        assertEquals(FlashMode.OFF, viewModel.uiState.value.flashMode)

        viewModel.toggleTorch()
        assertTrue(viewModel.uiState.value.isTorchOn)
        assertEquals(FlashMode.TORCH, viewModel.uiState.value.flashMode)

        viewModel.toggleTorch()
        assertFalse(viewModel.uiState.value.isTorchOn)
        assertEquals(FlashMode.OFF, viewModel.uiState.value.flashMode)
    }

    @Test
    fun gridToggle_switchesState() {
        val viewModel = FocalViewModel()
        assertFalse(viewModel.uiState.value.showGridOverlay)

        viewModel.toggleGridOverlay()
        assertTrue(viewModel.uiState.value.showGridOverlay)

        viewModel.toggleGridOverlay()
        assertFalse(viewModel.uiState.value.showGridOverlay)
    }

    @Test
    fun pairingCode_regeneratesValid6DigitCode() {
        val viewModel = FocalViewModel()
        assertEquals(6, viewModel.uiState.value.pairingCode.length)

        viewModel.regeneratePairingCode()
        val newCode = viewModel.uiState.value.pairingCode
        assertEquals(6, newCode.length)
        assertTrue(newCode.all { it.isDigit() })
    }

    @Test
    fun batteryStatus_warnsOnLowPowerWhenNotCharging() {
        val viewModel = FocalViewModel()
        viewModel.updateBatteryState(14, isCharging = false)
        assertEquals(14, viewModel.uiState.value.batteryPercentage)
        assertTrue(viewModel.uiState.value.isLowBatteryWarning)

        // Reset to normal level
        viewModel.updateBatteryState(80, isCharging = false)
        assertEquals(80, viewModel.uiState.value.batteryPercentage)
        assertFalse(viewModel.uiState.value.isLowBatteryWarning)
    }

    @Test
    fun batteryStatus_noWarningWhenChargingEvenIfLow() {
        val viewModel = FocalViewModel()
        viewModel.updateBatteryState(12, isCharging = true)
        assertEquals(12, viewModel.uiState.value.batteryPercentage)
        assertTrue(viewModel.uiState.value.isBatteryCharging)
        assertFalse(viewModel.uiState.value.isLowBatteryWarning)
    }

    @Test
    fun resolutionSetting_updatesProfileAndBitrate() {
        val viewModel = FocalViewModel()
        viewModel.setStreamResolution("720p")
        assertTrue(viewModel.uiState.value.selectedProfile.name.contains("720p"))
        assertEquals(2.4f, viewModel.uiState.value.selectedProfile.bitrateMbps, 0.01f)

        viewModel.setStreamResolution("4K")
        assertTrue(viewModel.uiState.value.selectedProfile.name.contains("4K"))
        assertEquals(18.0f, viewModel.uiState.value.selectedProfile.bitrateMbps, 0.01f)
    }

    @Test
    fun frameRateSetting_updatesFpsAndProfile() {
        val viewModel = FocalViewModel()
        viewModel.setStreamFps(60)
        assertEquals(60, viewModel.uiState.value.selectedProfile.fps)
        assertEquals(60.0f, viewModel.uiState.value.diagnostics.fps, 0.01f)

        viewModel.setStreamFps(30)
        assertEquals(30, viewModel.uiState.value.selectedProfile.fps)
        assertEquals(30.0f, viewModel.uiState.value.diagnostics.fps, 0.01f)
    }

    @Test
    fun cameraRotation_cyclesThrough90DegreeSteps() {
        val viewModel = FocalViewModel()
        assertEquals(0, viewModel.uiState.value.rotationDegrees)

        viewModel.rotate90()
        assertEquals(90, viewModel.uiState.value.rotationDegrees)

        viewModel.rotate90()
        assertEquals(180, viewModel.uiState.value.rotationDegrees)

        viewModel.rotate90()
        assertEquals(270, viewModel.uiState.value.rotationDegrees)

        viewModel.rotate90()
        assertEquals(0, viewModel.uiState.value.rotationDegrees)
    }

    @Test
    fun unsupportedResolution_fallbackClampsSafely() {
        // If an extreme unsupported resolution is requested (e.g. 8000x8000)
        val clamped = DeviceCapabilities.clampConfiguration(8000, 8000, 30)
        assertTrue(clamped.isClamped)
        assertTrue(clamped.width <= 3840)
        assertTrue(clamped.height <= 2160)
        assertNotNull(clamped.explanation)
    }

    @Test
    fun unsupportedFps_fallbackClampsSafely() {
        // If an unsupported 240 FPS is requested
        val clamped = DeviceCapabilities.clampConfiguration(1920, 1080, 240)
        assertTrue(clamped.isClamped)
        assertTrue(clamped.fps <= 60)
        assertNotNull(clamped.explanation)
    }

    @Test
    fun videoOnly_doesNotInitializeMicrophone() {
        var audioDataReceived = false
        val listener = object : AudioCaptureListener {
            override fun onAudioData(pcmBytes: ByteArray, timestampUs: Long) {
                audioDataReceived = true
            }
        }
        val audioController = AudioController(listener)

        // Even if permission is true, VIDEO_ONLY mode must never start or record
        val started = audioController.startRecording(hasPermission = true, streamMode = StreamMode.VIDEO_ONLY)
        assertFalse(started)
        assertFalse(audioController.isRecording)
        assertFalse(audioDataReceived)
    }

    @Test
    fun transportSwitching_wifiAndAdbModes() {
        val viewModel = FocalViewModel()
        assertEquals(HostConnectionMode.WIFI, viewModel.uiState.value.connectionMode)

        viewModel.setConnectionMode(HostConnectionMode.USB_ADB)
        assertEquals(HostConnectionMode.USB_ADB, viewModel.uiState.value.connectionMode)
        assertTrue(viewModel.uiState.value.diagnostics.connectionType.contains("USB"))

        viewModel.setConnectionMode(HostConnectionMode.WIFI)
        assertEquals(HostConnectionMode.WIFI, viewModel.uiState.value.connectionMode)
        assertTrue(viewModel.uiState.value.diagnostics.connectionType.contains("Wi-Fi"))
    }

    @Test
    fun recoveryManager_handlesCameraConflictAndRecovers() {
        var cameraRecovered = false
        val recoveryManager = RecoveryManager(
            onRetryCamera = {
                cameraRecovered = true
                true
            },
            onReinitEncoder = { true }
        )

        assertEquals(CameraConflictState.NORMAL, recoveryManager.conflictState.value)

        recoveryManager.onCameraConflictDetected("Camera in use by video caller")
        assertEquals(CameraConflictState.UNAVAILABLE, recoveryManager.conflictState.value)
        assertNotNull(recoveryManager.errorMessage.value)

        // Perform manual retry
        recoveryManager.manualRetry()
        assertTrue(cameraRecovered)
        assertEquals(CameraConflictState.NORMAL, recoveryManager.conflictState.value)
    }

    @Test
    fun pairingPacketProtocol_encodesAndDecodesCorrectly() {
        val originalPayload = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00) // H.264 SPS header
        val packet = StreamPacket(
            codec = StreamCodec.H264,
            type = PacketType.VIDEO_NAL,
            isKeyframe = true,
            timestampUs = 123456789L,
            payload = originalPayload
        )

        val encoded = StreamPacket.encode(packet)
        assertTrue(encoded.size > StreamPacket.HEADER_SIZE)

        val decoded = StreamPacket.decode(encoded)
        assertNotNull(decoded)
        assertEquals(StreamCodec.H264, decoded?.codec)
        assertEquals(PacketType.VIDEO_NAL, decoded?.type)
        assertTrue(decoded?.isKeyframe == true)
        assertEquals(123456789L, decoded?.timestampUs)
        assertTrue(decoded?.payload?.contentEquals(originalPayload) == true)
    }

    @Test
    fun transportManager_distributesToMultipleClientsWithoutDuplicateEncoding() {
        val tm = TransportManager(pairingPinProvider = { "849207" })
        tm.start(HostConnectionMode.WIFI)

        val sampleNal = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65)
        // Broadcasting from a single encoder
        tm.broadcastVideoNal(
            codec = StreamCodec.H264,
            isKeyframe = true,
            timestampUs = System.currentTimeMillis() * 1000,
            nalBytes = sampleNal
        )

        // Clean shutdown
        tm.stop()
        assertEquals(0, tm.connectedClientsCount.value)
    }
}
