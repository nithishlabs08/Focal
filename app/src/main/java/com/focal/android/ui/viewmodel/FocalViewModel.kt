package com.focal.android.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focal.android.data.model.AppDestination
import com.focal.android.data.model.CameraConflictState
import com.focal.android.data.model.CameraSensor
import com.focal.android.data.model.FlashMode
import com.focal.android.data.model.HostConnectionMode
import com.focal.android.data.model.MainTab
import com.focal.android.data.model.OutputProfile
import com.focal.android.data.model.StreamCodec
import com.focal.android.data.model.StreamDiagnostics
import com.focal.android.data.model.StreamMode
import com.focal.android.data.model.StreamSource
import com.focal.android.stream.StreamSessionController
import com.focal.android.media.CameraCapturePipeline
import com.focal.android.media.DeviceCapabilities
import com.focal.android.server.CameraStreamBroadcaster
import com.focal.android.server.WebcamStreamService
import com.focal.android.transport.FocalDiscoveryManager
import com.focal.android.transport.PairingManager
import com.focal.android.settings.AppThemeMode
import com.focal.android.settings.FocalDevicePreferences
import com.focal.android.util.NetworkUtils
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
    val streamSource: StreamSource = StreamSource.CAMERA,
    val connectionMode: HostConnectionMode = HostConnectionMode.WIFI,
    val selectedSensor: CameraSensor = CameraSensor("0", "Back Main Camera", "1080p FHD", "", "", isFront = false, supportsTorch = true),
    val selectedProfile: OutputProfile = OutputProfile("1080p_30", "1080p • 30 FPS", "1920x1080", 30, 4.8f, "Standard", StreamCodec.H264),
    val availableSensors: List<CameraSensor> = emptyList(),
    val availableProfiles: List<OutputProfile> = emptyList(),
    val isStreaming: Boolean = false,
    val streamElapsedSeconds: Long = 0L,
    val isTorchOn: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF,
    val showGridOverlay: Boolean = false,
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
    val showQuickControls: Boolean = false,
    val deviceIp: String = "",
    val serverPort: Int = 8080,
    val connectedClientsCount: Int = 0,
    val streamUrl: String = "",
    val pairingCode: String = "",
    val batteryPercentage: Int = -1,
    val isBatteryCharging: Boolean = false,
    val isLowBatteryWarning: Boolean = false,
    val dismissedLowBatteryWarning: Boolean = false,
    val isClampedFallback: Boolean = false,
    val clampNotice: String? = null,
    val isDiscoveryActive: Boolean = false,
    val discoveryServiceName: String? = null,
    val cameraConflictState: CameraConflictState = CameraConflictState.NORMAL,
    val cameraConflictMessage: String? = null,
    val deviceDisplayName: String = "",
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val showDeviceSettings: Boolean = false,
    val vpnMayBlockLocalStreaming: Boolean = false
)

class FocalViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FocalUiState())
    val uiState: StateFlow<FocalUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var appContext: Context? = null
    private var devicePreferencesSubscribed = false

    init {
        val localIp = NetworkUtils.getLocalIpAddress()
        val hwLabel = DeviceCapabilities.getHardwareAccelLabel()
        val initialProfiles = DeviceCapabilities.detectSupportedProfiles()
        val initialPin = PairingManager.currentPin

        _uiState.update {
            it.copy(
                deviceIp = localIp,
                streamUrl = if (localIp.isNotBlank()) "http://$localIp:8080/stream.h264" else "",
                pairingCode = initialPin,
                availableProfiles = initialProfiles,
                selectedProfile = initialProfiles.firstOrNull() ?: it.selectedProfile,
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

        // Connection mode status update loop
        viewModelScope.launch {
            while (isActive) {
                delay(2000)
                if (_uiState.value.isStreaming) {
                    val current = _uiState.value.diagnostics
                    _uiState.update { state ->
                        state.copy(
                            diagnostics = current.copy(
                                connectionType = if (state.connectionMode == HostConnectionMode.WIFI) "Wi-Fi (Local Network)" else "USB / ADB Port Forwarding"
                            )
                        )
                    }
                }
            }
        }
    }

    fun updateContext(context: Context) {
        appContext = context.applicationContext
        if (!devicePreferencesSubscribed) {
            devicePreferencesSubscribed = true
            viewModelScope.launch {
                FocalDevicePreferences.deviceNameFlow(context).collect { name ->
                    _uiState.update { it.copy(deviceDisplayName = name) }
                }
            }
            viewModelScope.launch {
                FocalDevicePreferences.themeModeFlow(context).collect { mode ->
                    _uiState.update { it.copy(themeMode = mode) }
                }
            }
        }
        val realIp = NetworkUtils.getLocalIpAddress(context)
        updateBattery(context)
        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hwLabel = DeviceCapabilities.getHardwareAccelLabel()

        val detectedCameras = DeviceCapabilities.detectAvailableCameras(context)
        val detectedProfiles = DeviceCapabilities.detectSupportedProfiles()

        _uiState.update { state ->
            val activeSensor = detectedCameras.find { it.id == state.selectedSensor.id } ?: detectedCameras.firstOrNull() ?: state.selectedSensor
            val activeProfile = detectedProfiles.find { it.id == state.selectedProfile.id } ?: detectedProfiles.firstOrNull() ?: state.selectedProfile
            state.copy(
                deviceIp = realIp,
                streamUrl = if (realIp.isNotBlank()) "http://$realIp:8080/stream.h264" else "",
                pairingCode = PairingManager.currentPin,
                isCameraPermissionGranted = hasCam,
                isMicPermissionGranted = hasMic,
                availableSensors = detectedCameras,
                selectedSensor = activeSensor,
                availableProfiles = detectedProfiles,
                selectedProfile = activeProfile,
                diagnostics = state.diagnostics.copy(hardwareAccel = hwLabel),
                isDiscoveryActive = FocalDiscoveryManager.isRegistered,
                discoveryServiceName = FocalDiscoveryManager.registeredServiceName,
                vpnMayBlockLocalStreaming = NetworkUtils.isVpnLikelyBlockingLan(context)
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

    fun updateBatteryState(pct: Int, isCharging: Boolean) {
        _uiState.update {
            it.copy(
                batteryPercentage = pct,
                isBatteryCharging = isCharging,
                isLowBatteryWarning = pct < 20 && !isCharging,
                diagnostics = it.diagnostics.copy(batteryPct = pct)
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

    fun setStreamSource(source: StreamSource) {
        if (_uiState.value.isStreaming) return
        _uiState.update { it.copy(streamSource = source) }
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
        if (mode == HostConnectionMode.WIFI && _uiState.value.isStreaming) {
            appContext?.let { FocalDiscoveryManager.registerService(it, port = 8080, pin = PairingManager.currentPin) }
        } else if (mode != HostConnectionMode.WIFI) {
            FocalDiscoveryManager.unregisterService()
        }
        _uiState.update {
            it.copy(
                connectionMode = mode,
                isDiscoveryActive = if (mode == HostConnectionMode.WIFI) FocalDiscoveryManager.isRegistered else false,
                discoveryServiceName = if (mode == HostConnectionMode.WIFI) FocalDiscoveryManager.registeredServiceName else null,
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
        val target = _uiState.value.availableProfiles.find { it.name.contains(resolutionLabel, ignoreCase = true) }
            ?: when {
                resolutionLabel.contains("720p", ignoreCase = true) -> OutputProfile("720p_30", "720p HD • 30 FPS", "1280x720", 30, 2.4f, "Low Latency")
                resolutionLabel.contains("4K", ignoreCase = true) -> OutputProfile("4k_30", "4K Ultra HD • 30 FPS", "3840x2160", 30, 18.0f, "Studio")
                else -> OutputProfile("1080p_30", "1080p Full HD • 30 FPS", "1920x1080", 30, 4.8f, "Standard")
            }
        selectProfile(target)
    }

    fun setStreamFps(fps: Int) {
        val current = _uiState.value.selectedProfile
        val target = _uiState.value.availableProfiles.find { it.fps == fps }
            ?: current.copy(fps = fps)
        selectProfile(target)
    }

    fun flipCamera(context: Context) {
        val nextIsFront = !_uiState.value.selectedSensor.isFront
        _uiState.update { state ->
            val nextSensor = if (nextIsFront) {
                state.availableSensors.firstOrNull { it.isFront }
                    ?: state.selectedSensor.copy(isFront = true, name = "Front Camera")
            } else {
                state.availableSensors.firstOrNull { !it.isFront }
                    ?: state.selectedSensor.copy(isFront = false, name = "Back Main Camera")
            }
            state.copy(selectedSensor = nextSensor)
        }
        CameraCapturePipeline.switchCamera(context, _uiState.value.selectedSensor.isFront)
    }

    fun setShowDeviceSettings(show: Boolean) {
        _uiState.update { it.copy(showDeviceSettings = show) }
    }

    fun saveDeviceName(context: Context, name: String) {
        viewModelScope.launch {
            FocalDevicePreferences.setDeviceName(context, name)
            if (_uiState.value.isStreaming && _uiState.value.connectionMode == HostConnectionMode.WIFI) {
                FocalDiscoveryManager.unregisterService()
                FocalDiscoveryManager.registerService(context.applicationContext, port = 8080, pin = PairingManager.currentPin)
                _uiState.update {
                    it.copy(
                        isDiscoveryActive = FocalDiscoveryManager.isRegistered,
                        discoveryServiceName = FocalDiscoveryManager.registeredServiceName
                    )
                }
            }
        }
    }

    fun saveThemeMode(context: Context, mode: AppThemeMode) {
        viewModelScope.launch {
            FocalDevicePreferences.setThemeMode(context, mode)
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

    fun startStreaming(
        context: Context? = null,
        mediaProjectionResultCode: Int = 0,
        mediaProjectionResultData: Intent? = null
    ) {
        val currentProfile = _uiState.value.selectedProfile
        val parts = currentProfile.resolution.split("x", " ")
        val rawWidth = parts.getOrNull(0)?.toIntOrNull() ?: 1920
        val rawHeight = parts.getOrNull(1)?.toIntOrNull() ?: 1080
        val rawFps = currentProfile.fps
        val rawBitrate = currentProfile.bitrateMbps

        val clamped = DeviceCapabilities.clampConfiguration(rawWidth, rawHeight, rawFps, rawBitrate)

        val streamPin = PairingManager.generateNewPin()

        if (context != null) {
            try {
                when (_uiState.value.streamSource) {
                    StreamSource.SCREEN -> {
                        if (mediaProjectionResultData == null) {
                            return
                        }
                        CameraCapturePipeline.unbind()
                        StreamSessionController.startScreenStream(
                            context = context,
                            mediaProjectionResultCode = mediaProjectionResultCode,
                            mediaProjectionResultData = mediaProjectionResultData,
                            connectionMode = _uiState.value.connectionMode,
                            streamMode = _uiState.value.streamMode,
                            pairingPin = streamPin,
                            profile = currentProfile.copy(
                                fps = clamped.fps,
                                bitrateMbps = clamped.bitrateMbps
                            )
                        )
                    }
                    StreamSource.AUDIO_ONLY -> {
                        StreamSessionController.startAudioOnlyStream(
                            context = context,
                            connectionMode = _uiState.value.connectionMode,
                            pairingPin = streamPin
                        )
                    }
                    StreamSource.CAMERA -> {
                        StreamSessionController.startCameraStream(
                            context = context,
                            connectionMode = _uiState.value.connectionMode,
                            streamMode = _uiState.value.streamMode,
                            pairingPin = streamPin,
                            profile = currentProfile.copy(fps = clamped.fps, bitrateMbps = clamped.bitrateMbps),
                            useFrontCamera = _uiState.value.selectedSensor.isFront
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
        _uiState.update {
            it.copy(
                pairingCode = streamPin,
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

    fun setShowQuickControls(show: Boolean) {
        _uiState.update { it.copy(showQuickControls = show) }
    }

    fun regeneratePairingCode() {
        val newCode = PairingManager.generateNewPin()
        WebcamStreamService.currentPairingPin = newCode
        if (_uiState.value.connectionMode == HostConnectionMode.WIFI) {
            appContext?.let { FocalDiscoveryManager.registerService(it, port = 8080, pin = newCode) }
        }
        _uiState.update {
            it.copy(
                pairingCode = newCode,
                streamUrl = if (it.deviceIp.isNotBlank()) "http://${it.deviceIp}:8080/stream.h264" else "",
                isDiscoveryActive = FocalDiscoveryManager.isRegistered,
                discoveryServiceName = FocalDiscoveryManager.registeredServiceName
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        FocalDiscoveryManager.unregisterService()
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
