package com.example.data.model

data class CameraSensor(
    val id: String,
    val name: String,
    val resolutionLabel: String,
    val focalLength: String,
    val aperture: String,
    val isFront: Boolean = false,
    val maxZoom: Float = 5.0f,
    val supportsTorch: Boolean = true
)

data class OutputProfile(
    val id: String,
    val name: String,
    val resolution: String,
    val fps: Int,
    val bitrateMbps: Float,
    val badge: String? = null,
    val codec: StreamCodec = StreamCodec.H264
)

enum class StreamCodec(val identifier: String, val displayName: String) {
    H264("H264", "Hardware H.264 (AVC)"),
    HEVC("HEVC", "Hardware H.265 (HEVC)"),
    MJPEG("MJPEG", "MJPEG (Compatibility Fallback)"),
    PCM("PCM", "Linear PCM 16-bit 48kHz Mono"),
    AAC("AAC", "AAC-LC")
}

enum class QualityPreset(val displayName: String, val width: Int, val height: Int, val fps: Int, val defaultBitrateMbps: Float) {
    LOW("Low (720p30)", 1280, 720, 30, 2.4f),
    BALANCED("Balanced (1080p30)", 1920, 1080, 30, 4.8f),
    HIGH("High (1080p60)", 1920, 1080, 60, 8.5f),
    MAXIMUM("Maximum (Device Best)", 3840, 2160, 60, 18.0f)
}

enum class StreamMode {
    VIDEO_ONLY,
    VIDEO_AND_AUDIO
}

enum class FlashMode {
    OFF,
    TORCH,
    AUTO
}

enum class HostConnectionMode(val displayName: String, val description: String) {
    WIFI("Wi-Fi", "Local network connection via Wi-Fi"),
    USB_ADB("USB / ADB", "Zero-jitter low-latency link via ADB port forwarding")
}

enum class CameraConflictState {
    NORMAL,
    UNAVAILABLE,
    RECOVERING
}

data class HostDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val os: String,
    val bridgeDriver: String,
    val isConnected: Boolean
)

data class StreamDiagnostics(
    val latencyMs: Int = 12,
    val fps: Float = 30.0f,
    val bitrateMbps: Float = 4.8f,
    val codec: String = "Hardware H.264",
    val hardwareAccel: String = "MediaCodec HW",
    val isAudioActive: Boolean = false,
    val batteryPct: Int = 82,
    val temperatureC: Int = 34,
    val droppedFrames: Int = 0,
    val connectionType: String = "Wi-Fi (Local Network)",
    val isClampedFallback: Boolean = false,
    val clampNotice: String? = null
)

enum class AppDestination {
    ONBOARDING,
    PERMISSIONS,
    MAIN_STREAM,
    ACTIVE_STREAM
}

enum class MainTab {
    STREAM,
    CONNECT,
    SETTINGS,
    HELP
}
