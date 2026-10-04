package com.example.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppDestination
import com.example.data.model.CameraConflictState
import com.example.data.model.CameraSensor
import com.example.data.model.FlashMode
import com.example.data.model.HostConnectionMode
import com.example.data.model.HostDevice
import com.example.data.model.MainTab
import com.example.data.model.OutputProfile
import com.example.data.model.StreamCodec
import com.example.data.model.StreamDiagnostics
import com.example.data.model.StreamMode
import com.example.media.CameraCapturePipeline
import com.example.media.DeviceCapabilities
import com.example.server.CameraStreamBroadcaster
import com.example.server.WebcamStreamService
import com.example.transport.PairingManager
import com.example.util.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FocalUiState(
    val destination: AppDestination = AppDestination.MAIN_STREAM,
    val selectedTab: MainTab = MainTab.STREAM,
    val streamMode: StreamMode = StreamMode.VIDEO_ONLY,
    val connectionMode: HostConnectionMode = HostConnectionMode.WIFI,
    val selectedSensor: CameraSensor = DefaultSensors.first(),
    val selectedProfile: OutputProfile = DefaultProfiles.first(),
    val availableSensors: List<CameraSensor> = DefaultSensors,
    val availableProfiles: List<OutputProfile> = DefaultProfiles,
    val availableHosts: List<HostDevice> = DefaultHosts,
    val selectedHost: HostDevice = DefaultHosts.first(),
    val isStreaming: Boolean = false,
    val streamElapsedSeconds: Long = 0L,
    val isTorchOn: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF,
    val showGridOverlay: Boolean = true,
    val isAudioMuted: Boolean = true,
    val rotationDegrees: Int = 0,
    val exposureCompensation: Float = 0.0f,
    val isOledScreenOffActive: Boolean = false,
    val isFullscreenViewfinder: Boolean = false,
    val isCameraPermissionGranted: Boolean = false,
    val isMicPermissionGranted: Boolean = false,
    val diagnostics: StreamDiagnostics = StreamDiagnostics(),
    val showSecurityModal: Boolean = false,
    val showSensorPicker: Boolean = false,
    val showProfilePicker: Boolean = false,
    val showHostPicker: Boolean = false,
    val showQuickControls: Boolean = false,
    val deviceIp: String = "192.168.1.142",
    val serverPort: Int = 8080,
    val connectedClientsCount: Int = 0,
    val streamUrl: String = "http://192.168.1.142:8080/stream.h264",
    val pairingCode: String = "849207",
    val batteryPercentage: Int = 82,
    val isBatteryCharging: Boolean = false,
    val isLowBatteryWarning: Boolean = false,
    val dismissedLowBatteryWarning: Boolean = false,
    val isClampedFallback: Boolean = false,
    val clampNotice: String? = null,
    val cameraConflictState: CameraConflictState = CameraConflictState.NORMAL,
    val cameraConflictMessage: String? = null
)

val DefaultSensors = listOf(
    CameraSensor(
        id = "rear_main",
        name = "Rear Main (4K)",
        resolutionLabel = "3840 x 2160 (4K)",
        focalLength = "24mm eq.",
        aperture = "f/1.8"
    ),
    CameraSensor(
        id = "rear_ultrawide",
        name = "Ultra Wide (0.5x)",
        resolutionLabel = "1920 x 1080 (FHD)",
        focalLength = "13mm eq.",
        aperture = "f/2.2"
    ),
    CameraSensor(
        id = "rear_telephoto",
        name = "Telephoto (3x)",
        resolutionLabel = "3840 x 2160 (4K)",
        focalLength = "72mm eq.",
        aperture = "f/2.4"
    ),
    CameraSensor(
        id = "front_selfie",
        name = "Front Lens (FHD)",
        resolutionLabel = "1920 x 1080 (FHD)",
        focalLength = "20mm eq.",
        aperture = "f/2.0",
        isFront = true
    )
)

val DefaultProfiles = listOf(
    OutputProfile("1080p_30", "1080p • 30 FPS", "1920x1080", 30, 4.8f, "Recommended Standard", StreamCodec.H264),
    OutputProfile("1080p_60", "1080p • 60 FPS", "1920x1080", 60, 8.5f, "Fluid Motion", StreamCodec.H264),
    OutputProfile("720p_30", "720p • 30 FPS", "1280x720", 30, 2.4f, "Low Latency (2.4GHz)", StreamCodec.H264),
    OutputProfile("720p_60", "720p • 60 FPS", "1280x720", 60, 4.2f, "High Motion", StreamCodec.H264),
    OutputProfile("4k_30", "4K • 30 FPS (where supported)", "3840x2160", 30, 18.0f, "Studio Rig", StreamCodec.H264)
)

val DefaultHosts = listOf(
    HostDevice(
        id = "host_fedora",
        name = "Fedora 40 Workstation",
        ipAddress = "192.168.1.105",
        os = "Fedora Linux 40 (Silverblue)",
        bridgeDriver = "PipeWire v4l2loopback",
        isConnected = true
    ),
    HostDevice(
        id = "host_ubuntu",
        name = "Ubuntu Studio 24.04",
        ipAddress = "192.168.1.180",
        os = "Ubuntu Linux 24.04 LTS",
        bridgeDriver = "v4l2loopback /dev/video2",
        isConnected = false
    ),
    HostDevice(
        id = "host_arch",
        name = "Arch Linux Rig",
        ipAddress = "192.168.1.210",
        os = "Arch Linux (Kernel 6.10)",
        bridgeDriver = "PipeWire camera portal",
        isConnected = false
    )
)

class FocalViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FocalUiState())
    val uiState: StateFlow<FocalUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        val localIp = NetworkUtils.getLocalIpAddress()
        val hwLabel = DeviceCapabilities.getHardwareAccelLabel()
        _uiState.update {
            it.copy(
                deviceIp = localIp,
                streamUrl = "http://$localIp:8080/stream.h264",
                pairingCode = PairingManager.currentPin,
                diagnostics = it.diagnostics.copy(hardwareAccel = hwLabel)
            )
        }

        // Sync with foreground service state (e.g. if started from Quick Settings Tile)
        viewModelScope.launch {
            WebcamStreamService.isRunning.collect { running ->
                _uiState.update { state ->
                    state.copy(
                        isStreaming = running,
                        destination = if (running && state.destination == AppDestination.MAIN_STREAM) {
                            AppDestination.ACTIVE_STREAM
                        } else if (!running && state.destination == AppDestination.ACTIVE_STREAM) {
                            AppDestination.MAIN_STREAM
                        } else {
                            state.destination
                        }
                    )
                }
            }
        }

        // Sync real audio active state from service
        viewModelScope.launch {
            WebcamStreamService.isAudioActive.collect { audioActive ->
                _uiState.update { state ->
                    state.copy(
                        diagnostics = state.diagnostics.copy(isAudioActive = audioActive)
                    )
                }
            }
        }

        // Sync startup failures from service
        viewModelScope.launch {
            WebcamStreamService.startupError.collect { errorMsg ->
                if (errorMsg != null) {
                    _uiState.update { state ->
                        state.copy(
                            isStreaming = false,
                            destination = AppDestination.MAIN_STREAM,
                            clampNotice = errorMsg
                        )
                    }
                }
            }
        }

        // Collect broadcaster real client count
        viewModelScope.launch {
            CameraStreamBroadcaster.connectedClients.collect { clients ->
                _uiState.update { state ->
                    state.copy(
                        connectedClientsCount = clients,
                        diagnostics = state.diagnostics.copy(
                            droppedFrames = if (clients > 0) 0 else state.diagnostics.droppedFrames
                        )
                    )
                }
            }
        }

        // Collect actual pipeline FPS from camera-to-encoder pipeline
        viewModelScope.launch {
            CameraCapturePipeline.actualFps.collect { fps ->
                if (_uiState.value.isStreaming && fps > 0f) {
                    _uiState.update { state ->
                        state.copy(
                            diagnostics = state.diagnostics.copy(fps = fps)
                        )
                    }
                }
            }
        }

        // Live stream metrics jitter/update loop
        viewModelScope.launch {
            while (isActive) {
                delay(2000)
                if (_uiState.value.isStreaming) {
                    val current = _uiState.value.diagnostics
                    val jitterLatency = if (_uiState.value.connectionMode == HostConnectionMode.USB_ADB) (4..7).random() else (8..14).random()
                    _uiState.update { state ->
                        state.copy(
                            diagnostics = current.copy(
                                latencyMs = jitterLatency,
                                connectionType = if (state.connectionMode == HostConnectionMode.WIFI) "Wi-Fi (Local Network)" else "USB / ADB Port Forwarding"
                            )
                        )
                    }
                }
            }
        }
    }

    fun updateContext(context: Context) {
        val realIp = NetworkUtils.getLocalIpAddress(context)
        updateBattery(context)
        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hwLabel = DeviceCapabilities.getHardwareAccelLabel()

        _uiState.update {
            it.copy(
                deviceIp = realIp,
                streamUrl = "http://$realIp:8080/stream.h264",
                pairingCode = PairingManager.currentPin,
                isCameraPermissionGranted = hasCam,
                isMicPermissionGranted = hasMic,
                diagnostics = it.diagnostics.copy(hardwareAccel = hwLabel)
            )
        }
    }

    fun updateBattery(context: Context) {
        try {
            val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, iFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            if (level >= 0 && scale > 0) {
                val pct = (level * 100) / scale
                _uiState.update {
                    it.copy(
                        batteryPercentage = pct,
                        isBatteryCharging = isCharging,
                        isLowBatteryWarning = pct < 20 && !isCharging,
                        diagnostics = it.diagnostics.copy(batteryPct = pct)
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun setSimulatedBattery(pct: Int, isCharging: Boolean = false) {
        _uiState.update {
            it.copy(
                batteryPercentage = pct,
                isBatteryCharging = isCharging,
                isLowBatteryWarning = pct < 20 && !isCharging,
                diagnostics = it.diagnostics.copy(batteryPct = pct),
                dismissedLowBatteryWarning = false
            )
        }
    }

    fun dismissLowBatteryWarning() {
        _uiState.update { it.copy(dismissedLowBatteryWarning = true) }
    }

    fun navigateTo(destination: AppDestination) {
        _uiState.update { it.copy(destination = destination) }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setStreamMode(mode: StreamMode) {
        _uiState.update {
            it.copy(
                streamMode = mode,
                isAudioMuted = mode == StreamMode.VIDEO_ONLY,
                diagnostics = it.diagnostics.copy(isAudioActive = mode == StreamMode.VIDEO_AND_AUDIO)
            )
        }
    }

    fun setConnectionMode(mode: HostConnectionMode) {
        _uiState.update {
            it.copy(
                connectionMode = mode,
                diagnostics = it.diagnostics.copy(
                    connectionType = if (mode == HostConnectionMode.WIFI) "Wi-Fi (Local Network)" else "USB / ADB Port Forwarding"
                )
            )
        }
    }

    fun selectSensor(sensor: CameraSensor) {
        _uiState.update { it.copy(selectedSensor = sensor, showSensorPicker = false) }
    }

    fun selectProfile(profile: OutputProfile) {
        val parts = profile.resolution.split("x", " ")
        val w = parts.getOrNull(0)?.toIntOrNull() ?: 1920
        val h = parts.getOrNull(1)?.toIntOrNull() ?: 1080
        val clamped = DeviceCapabilities.clampConfiguration(w, h, profile.fps)

        val updatedProfile = profile.copy(
            fps = clamped.fps,
            bitrateMbps = clamped.bitrateMbps
        )

        _uiState.update {
            it.copy(
                selectedProfile = updatedProfile,
                showProfilePicker = false,
                isClampedFallback = clamped.isClamped,
                clampNotice = clamped.explanation,
                diagnostics = it.diagnostics.copy(
                    bitrateMbps = clamped.bitrateMbps,
                    fps = clamped.fps.toFloat(),
                    isClampedFallback = clamped.isClamped,
                    clampNotice = clamped.explanation
                )
            )
        }
    }

    fun setStreamResolution(resolutionLabel: String) {
        _uiState.update { state ->
            val currentFps = state.selectedProfile.fps
            val targetW = when (resolutionLabel) {
                "720p" -> 1280
                "4K" -> 3840
                else -> 1920
            }
            val targetH = when (resolutionLabel) {
                "720p" -> 720
                "4K" -> 2160
                else -> 1080
            }

            val clamped = DeviceCapabilities.clampConfiguration(targetW, targetH, currentFps)

            val updatedProfile = state.selectedProfile.copy(
                resolution = "${clamped.width}x${clamped.height}",
                fps = clamped.fps,
                name = "$resolutionLabel @ ${clamped.fps} FPS",
                bitrateMbps = clamped.bitrateMbps
            )

            state.copy(
                selectedProfile = updatedProfile,
                isClampedFallback = clamped.isClamped,
                clampNotice = clamped.explanation,
                diagnostics = state.diagnostics.copy(
                    bitrateMbps = clamped.bitrateMbps,
                    fps = clamped.fps.toFloat(),
                    isClampedFallback = clamped.isClamped,
                    clampNotice = clamped.explanation
                )
            )
        }
    }

    fun setStreamFps(fps: Int) {
        _uiState.update { state ->
            val currentRes = when {
                state.selectedProfile.name.contains("720p", ignoreCase = true) -> "720p"
                state.selectedProfile.name.contains("4K", ignoreCase = true) -> "4K"
                else -> "1080p"
            }
            val targetW = if (currentRes == "720p") 1280 else if (currentRes == "4K") 3840 else 1920
            val targetH = if (currentRes == "720p") 720 else if (currentRes == "4K") 2160 else 1080

            val clamped = DeviceCapabilities.clampConfiguration(targetW, targetH, fps)

            val updatedProfile = state.selectedProfile.copy(
                resolution = "${clamped.width}x${clamped.height}",
                fps = clamped.fps,
                name = "$currentRes @ ${clamped.fps} FPS",
                bitrateMbps = clamped.bitrateMbps
            )

            state.copy(
                selectedProfile = updatedProfile,
                isClampedFallback = clamped.isClamped,
                clampNotice = clamped.explanation,
                diagnostics = state.diagnostics.copy(
                    fps = clamped.fps.toFloat(),
                    bitrateMbps = clamped.bitrateMbps,
                    isClampedFallback = clamped.isClamped,
                    clampNotice = clamped.explanation
                )
            )
        }
    }

    fun selectHost(host: HostDevice) {
        _uiState.update { it.copy(selectedHost = host, showHostPicker = false) }
    }

    fun flipCamera() {
        _uiState.update { state ->
            val nextSensor = if (state.selectedSensor.isFront) {
                state.availableSensors.first { !it.isFront }
            } else {
                state.availableSensors.first { it.isFront }
            }
            state.copy(selectedSensor = nextSensor)
        }
    }

    fun rotate90() {
        _uiState.update {
            it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360)
        }
    }

    fun cycleExposure() {
        _uiState.update {
            val nextEv = when (it.exposureCompensation) {
                -1.0f -> -0.5f
                -0.5f -> 0.0f
                0.0f -> 0.5f
                0.5f -> 1.0f
                else -> -1.0f
            }
            it.copy(exposureCompensation = nextEv)
        }
    }

    fun cycleFlashMode() {
        _uiState.update {
            val nextMode = when (it.flashMode) {
                FlashMode.OFF -> FlashMode.TORCH
                FlashMode.TORCH -> FlashMode.AUTO
                FlashMode.AUTO -> FlashMode.OFF
            }
            it.copy(
                flashMode = nextMode,
                isTorchOn = nextMode == FlashMode.TORCH
            )
        }
    }

    fun setFlashMode(mode: FlashMode) {
        _uiState.update {
            it.copy(
                flashMode = mode,
                isTorchOn = mode == FlashMode.TORCH
            )
        }
    }

    fun toggleTorch() {
        _uiState.update {
            val nextTorch = !it.isTorchOn
            it.copy(
                isTorchOn = nextTorch,
                flashMode = if (nextTorch) FlashMode.TORCH else FlashMode.OFF
            )
        }
    }

    fun setOledScreenOff(active: Boolean) {
        _uiState.update { it.copy(isOledScreenOffActive = active) }
    }

    fun toggleGridOverlay() {
        _uiState.update { it.copy(showGridOverlay = !it.showGridOverlay) }
    }

    fun toggleMute() {
        _uiState.update {
            val nextMuted = !it.isAudioMuted
            it.copy(
                isAudioMuted = nextMuted,
                streamMode = if (nextMuted) StreamMode.VIDEO_ONLY else StreamMode.VIDEO_AND_AUDIO,
                diagnostics = it.diagnostics.copy(isAudioActive = !nextMuted)
            )
        }
    }

    fun startStreaming(context: Context? = null) {
        val currentProfile = _uiState.value.selectedProfile
        val parts = currentProfile.resolution.split("x", " ")
        val rawWidth = parts.getOrNull(0)?.toIntOrNull() ?: 1920
        val rawHeight = parts.getOrNull(1)?.toIntOrNull() ?: 1080
        val rawFps = currentProfile.fps
        val rawBitrate = currentProfile.bitrateMbps

        val clamped = DeviceCapabilities.clampConfiguration(rawWidth, rawHeight, rawFps, rawBitrate)

        if (context != null) {
            try {
                WebcamStreamService.start(
                    context = context,
                    pairingPin = _uiState.value.pairingCode,
                    connectionMode = _uiState.value.connectionMode,
                    streamMode = _uiState.value.streamMode,
                    profile = currentProfile.copy(fps = clamped.fps, bitrateMbps = clamped.bitrateMbps)
                )
            } catch (_: Exception) {
            }
        }
        _uiState.update {
            it.copy(
                isStreaming = false, // Becomes true when WebcamStreamService.isRunning emits true
                streamElapsedSeconds = 0L,
                destination = AppDestination.ACTIVE_STREAM,
                isClampedFallback = clamped.isClamped,
                clampNotice = clamped.explanation,
                diagnostics = it.diagnostics.copy(
                    fps = clamped.fps.toFloat(),
                    bitrateMbps = clamped.bitrateMbps,
                    isClampedFallback = clamped.isClamped,
                    clampNotice = clamped.explanation
                )
            )
        }
        startTimer()
    }

    fun stopStreaming(context: Context? = null) {
        if (context != null) {
            try {
                WebcamStreamService.stop(context)
            } catch (_: Exception) {
            }
        }
        _uiState.update {
            it.copy(
                isStreaming = false,
                streamElapsedSeconds = 0L,
                destination = AppDestination.MAIN_STREAM
            )
        }
        timerJob?.cancel()
        timerJob = null
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(streamElapsedSeconds = it.streamElapsedSeconds + 1) }
            }
        }
    }

    fun toggleOledScreenOff(active: Boolean) {
        _uiState.update { it.copy(isOledScreenOffActive = active) }
    }

    fun toggleFullscreenViewfinder() {
        _uiState.update { it.copy(isFullscreenViewfinder = !it.isFullscreenViewfinder) }
    }

    fun setCameraPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(isCameraPermissionGranted = granted) }
    }

    fun setMicPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(isMicPermissionGranted = granted) }
    }

    fun setShowSecurityModal(show: Boolean) {
        _uiState.update { it.copy(showSecurityModal = show) }
    }

    fun setShowSensorPicker(show: Boolean) {
        _uiState.update { it.copy(showSensorPicker = show) }
    }

    fun setShowProfilePicker(show: Boolean) {
        _uiState.update { it.copy(showProfilePicker = show) }
    }

    fun setShowHostPicker(show: Boolean) {
        _uiState.update { it.copy(showHostPicker = show) }
    }

    fun setShowQuickControls(show: Boolean) {
        _uiState.update { it.copy(showQuickControls = show) }
    }

    fun regeneratePairingCode() {
        val newCode = PairingManager.generateNewPin()
        WebcamStreamService.currentPairingPin = newCode
        _uiState.update {
            it.copy(
                pairingCode = newCode,
                streamUrl = "http://${it.deviceIp}:8080/stream.h264"
            )
        }
    }

    fun onCameraConflict(reason: String) {
        _uiState.update {
            it.copy(
                cameraConflictState = CameraConflictState.UNAVAILABLE,
                cameraConflictMessage = "Camera temporarily unavailable ($reason). Retrying..."
            )
        }
    }

    fun retryCameraConflict() {
        _uiState.update {
            it.copy(
                cameraConflictState = CameraConflictState.NORMAL,
                cameraConflictMessage = null
            )
        }
    }
}
