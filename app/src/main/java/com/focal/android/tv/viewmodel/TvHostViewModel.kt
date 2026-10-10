package com.focal.android.tv.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focal.android.FocalRoles
import com.focal.android.data.model.HostConnectionMode
import com.focal.android.data.model.OutputProfile
import com.focal.android.data.model.StreamMode
import com.focal.android.data.model.StreamSource
import com.focal.android.media.CameraCapturePipeline
import com.focal.android.media.DeviceCapabilities
import com.focal.android.server.CameraStreamBroadcaster
import com.focal.android.server.WebcamStreamService
import com.focal.android.stream.StreamSessionController
import com.focal.android.transport.PairingManager
import com.focal.android.util.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TvHostUiState(
    val streamSource: StreamSource = StreamSource.SCREEN,
    val streamMode: StreamMode = StreamMode.VIDEO_AND_AUDIO,
    val isStreaming: Boolean = false,
    val deviceIp: String = "",
    val pairingPin: String = "",
    val connectedClients: Int = 0,
    val errorMessage: String? = null,
    val isMicPermissionGranted: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val selectedProfile: OutputProfile = OutputProfile(
        "1080p_30",
        "1080p • 30 FPS",
        "1920x1080",
        30,
        4.8f,
        "Standard"
    )
)

class TvHostViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TvHostUiState())
    val uiState: StateFlow<TvHostUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    val availableSources: List<StreamSource> = StreamSource.entries.filter { source ->
        when (source) {
            StreamSource.CAMERA -> FocalRoles.canHostCameraStream
            StreamSource.SCREEN,
            StreamSource.AUDIO_ONLY -> FocalRoles.canHostScreenOrAudioStream
        }
    }

    init {
        val profiles = DeviceCapabilities.detectSupportedProfiles()
        val profile = profiles.firstOrNull() ?: _uiState.value.selectedProfile
        _uiState.update {
            it.copy(
                pairingPin = PairingManager.currentPin,
                selectedProfile = profile,
                streamSource = availableSources.firstOrNull() ?: StreamSource.SCREEN
            )
        }

        viewModelScope.launch {
            WebcamStreamService.isRunning.collect { running ->
                _uiState.update { it.copy(isStreaming = running, errorMessage = if (running) null else it.errorMessage) }
                if (running) {
                    startTimer()
                } else {
                    timerJob?.cancel()
                    timerJob = null
                    _uiState.update { it.copy(elapsedSeconds = 0L) }
                }
            }
        }

        viewModelScope.launch {
            WebcamStreamService.startupError.collect { msg ->
                if (msg != null) {
                    _uiState.update { it.copy(errorMessage = msg, isStreaming = false) }
                }
            }
        }

        viewModelScope.launch {
            CameraStreamBroadcaster.connectedClients.collect { clients ->
                _uiState.update { it.copy(connectedClients = clients) }
            }
        }
    }

    fun refreshFromContext(context: Context) {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val ip = NetworkUtils.getLocalIpAddress(context)
        _uiState.update {
            it.copy(
                deviceIp = ip,
                isMicPermissionGranted = hasMic,
                pairingPin = PairingManager.currentPin
            )
        }
    }

    fun setMicPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(isMicPermissionGranted = granted) }
    }

    fun setStreamSource(source: StreamSource) {
        if (_uiState.value.isStreaming || !availableSources.contains(source)) return
        _uiState.update { it.copy(streamSource = source) }
    }

    fun setStreamMode(mode: StreamMode) {
        if (_uiState.value.isStreaming) return
        _uiState.update { it.copy(streamMode = mode) }
    }

    fun startScreenStream(
        context: Context,
        mediaProjectionResultCode: Int,
        mediaProjectionResultData: Intent
    ) {
        if (!FocalRoles.canHostScreenOrAudioStream) return
        val pin = PairingManager.generateNewPin()
        val profile = clampProfile(_uiState.value.selectedProfile)
        CameraCapturePipeline.unbind()
        StreamSessionController.startScreenStream(
            context = context,
            mediaProjectionResultCode = mediaProjectionResultCode,
            mediaProjectionResultData = mediaProjectionResultData,
            connectionMode = HostConnectionMode.WIFI,
            streamMode = _uiState.value.streamMode,
            pairingPin = pin,
            profile = profile
        )
        _uiState.update { it.copy(pairingPin = pin, errorMessage = null) }
        startTimer()
    }

    fun startAudioStream(context: Context) {
        if (!FocalRoles.canHostScreenOrAudioStream) return
        val pin = PairingManager.generateNewPin()
        StreamSessionController.startAudioOnlyStream(
            context = context,
            connectionMode = HostConnectionMode.WIFI,
            pairingPin = pin
        )
        _uiState.update { it.copy(pairingPin = pin, errorMessage = null) }
        startTimer()
    }

    fun stopStream(context: Context) {
        StreamSessionController.stopStream(context)
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(elapsedSeconds = 0L) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun clampProfile(profile: OutputProfile): OutputProfile {
        val parts = profile.resolution.split("x", " ")
        val w = parts.getOrNull(0)?.toIntOrNull() ?: 1920
        val h = parts.getOrNull(1)?.toIntOrNull() ?: 1080
        val clamped = DeviceCapabilities.clampConfiguration(w, h, profile.fps, profile.bitrateMbps)
        return profile.copy(fps = clamped.fps, bitrateMbps = clamped.bitrateMbps)
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }
}
