package com.focal.android.media

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.SurfaceHolder
import com.focal.android.data.model.CameraSensor
import com.focal.android.data.model.OutputProfile
import com.focal.android.data.model.StreamCodec

data class ClampedConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateMbps: Float,
    val isClamped: Boolean,
    val explanation: String? = null
)

object DeviceCapabilities {

    private const val TAG = "DeviceCapabilities"

    // Safe baseline profiles guaranteed on standard Android 7.0+ (API 24+)
    val STANDARD_RESOLUTIONS = listOf(
        Pair(1280, 720),
        Pair(1920, 1080),
        Pair(3840, 2160)
    )

    /**
     * Determines whether a given MediaCodecInfo is a true hardware-accelerated codec.
     * Uses API 29+ isHardwareAccelerated if available, or vendor naming conventions as fallback.
     */
    fun isHardwareAccelerated(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !name.startsWith("omx.google.") &&
            !name.startsWith("c2.android.") &&
            !name.startsWith("omx.ffmpeg.") &&
            !name.contains("soft") &&
            !name.contains("sw")
        }
    }

    /**
     * Finds the best AVC (H.264) encoder available on the device, preferring
     * hardware-accelerated codecs over software implementations.
     */
    fun findBestAvcEncoder(): MediaCodecInfo? {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            var softwareFallback: MediaCodecInfo? = null
            for (info in codecList.codecInfos) {
                if (info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }) {
                    if (isHardwareAccelerated(info)) {
                        return info
                    } else if (softwareFallback == null) {
                        softwareFallback = info
                    }
                }
            }
            softwareFallback
        } catch (_: Throwable) {
            null
        }
    }

    fun isHardwareAvcSupported(): Boolean {
        val encoder = findBestAvcEncoder() ?: return false
        return isHardwareAccelerated(encoder)
    }

    fun getHardwareAccelLabel(): String {
        val encoder = findBestAvcEncoder() ?: return "Software Fallback"
        return if (isHardwareAccelerated(encoder)) {
            "MediaCodec HW (${encoder.name})"
        } else {
            "Software Fallback (${encoder.name})"
        }
    }

    fun queryH264EncoderCapabilities(): MediaCodecInfo.CodecCapabilities? {
        return try {
            val encoder = findBestAvcEncoder()
            encoder?.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
        } catch (_: Throwable) {
            null
        }
    }

    fun isResolutionSupported(width: Int, height: Int): Boolean {
        val caps = queryH264EncoderCapabilities() ?: return width <= 1920 && height <= 1080
        val videoCaps = caps.videoCapabilities ?: return width <= 1920 && height <= 1080
        return try {
            videoCaps.isSizeSupported(width, height)
        } catch (_: Throwable) {
            width <= 1920 && height <= 1080
        }
    }

    fun isFpsSupported(width: Int, height: Int, fps: Int): Boolean {
        val caps = queryH264EncoderCapabilities() ?: return fps <= 30
        val videoCaps = caps.videoCapabilities ?: return fps <= 30
        return try {
            videoCaps.areSizeAndRateSupported(width, height, fps.toDouble())
        } catch (_: Throwable) {
            fps <= 30
        }
    }

    /**
     * Dynamically verifies requested width, height, and fps against device encoder capabilities
     * and returns the closest safe configuration without assuming a software encoder is hardware accelerated.
     * Honors the selected bitrate when supported by the device encoder.
     */
    fun clampConfiguration(
        requestedWidth: Int,
        requestedHeight: Int,
        requestedFps: Int,
        requestedBitrateMbps: Float? = null
    ): ClampedConfig {
        var targetW = requestedWidth
        var targetH = requestedHeight
        var targetFps = requestedFps
        var clamped = false
        var explanation: String? = null

        val encoder = findBestAvcEncoder()
        val isHw = encoder != null && isHardwareAccelerated(encoder)
        val caps = queryH264EncoderCapabilities()
        val videoCaps = caps?.videoCapabilities

        val encoderTypePrefix = if (isHw) "Hardware" else "Software"

        // 1. Verify resolution
        if (videoCaps != null) {
            if (!videoCaps.isSizeSupported(targetW, targetH)) {
                clamped = true
                if (videoCaps.isSizeSupported(1920, 1080)) {
                    targetW = 1920
                    targetH = 1080
                    explanation = "$encoderTypePrefix encoder does not support $requestedWidth×$requestedHeight. Clamped to 1080p."
                } else if (videoCaps.isSizeSupported(1280, 720)) {
                    targetW = 1280
                    targetH = 720
                    explanation = "$encoderTypePrefix encoder clamped resolution to 720p for compatibility."
                } else {
                    val maxW = try { videoCaps.supportedWidths.upper } catch (_: Throwable) { 1920 }
                    val maxH = try { videoCaps.supportedHeights.upper } catch (_: Throwable) { 1080 }
                    targetW = minOf(targetW, maxW)
                    targetH = minOf(targetH, maxH)
                    explanation = "$encoderTypePrefix encoder clamped resolution to ${targetW}x${targetH}."
                }
            }

            // 2. Verify frame rate
            if (!videoCaps.areSizeAndRateSupported(targetW, targetH, targetFps.toDouble())) {
                clamped = true
                val maxFps = try {
                    videoCaps.getSupportedFrameRatesFor(targetW, targetH).upper.toInt()
                } catch (_: Throwable) {
                    30
                }
                targetFps = minOf(targetFps, maxFps).coerceAtLeast(1)
                if (targetFps > 30 && maxFps >= 30) {
                    targetFps = 30
                }
                explanation = (explanation?.let { "$it " } ?: "") + "Target $requestedFps FPS unsupported at $targetW×$targetH. Fallback to $targetFps FPS."
            }
        } else {
            // Baseline fallback for headless environments
            if (targetW > 3840 || targetH > 2160) {
                targetW = 1920
                targetH = 1080
                clamped = true
                explanation = "$encoderTypePrefix encoder clamped resolution to 1080p."
            }
            if (targetFps > 60) {
                targetFps = 30
                clamped = true
                explanation = (explanation?.let { "$it " } ?: "") + "Target $requestedFps FPS unsupported. Fallback to 30 FPS."
            }
        }

        val recommendedBitrate = calculateBitrate(targetW, targetH, targetFps)
        val finalBitrate: Float
        if (requestedBitrateMbps != null && requestedBitrateMbps > 0f) {
            val bitrateRange = videoCaps?.bitrateRange
            if (bitrateRange != null) {
                val requestedBps = (requestedBitrateMbps * 1_000_000).toInt()
                if (bitrateRange.contains(requestedBps)) {
                    finalBitrate = requestedBitrateMbps
                } else {
                    val clampedBps = bitrateRange.clamp(requestedBps)
                    finalBitrate = clampedBps / 1_000_000f
                    clamped = true
                    explanation = (explanation?.let { "$it " } ?: "") + "Bitrate ${requestedBitrateMbps} Mbps outside encoder range. Clamped to ${finalBitrate} Mbps."
                }
            } else {
                finalBitrate = requestedBitrateMbps.coerceIn(0.5f, 50.0f)
            }
        } else {
            finalBitrate = recommendedBitrate
        }

        return ClampedConfig(
            width = targetW,
            height = targetH,
            fps = targetFps,
            bitrateMbps = finalBitrate,
            isClamped = clamped,
            explanation = explanation
        )
    }

    fun clampConfiguration(
        requestedWidth: Int,
        requestedHeight: Int,
        requestedFps: Int
    ): ClampedConfig = clampConfiguration(requestedWidth, requestedHeight, requestedFps, null)

    fun calculateBitrate(width: Int, height: Int, fps: Int): Float {
        return when {
            width >= 3840 || height >= 2160 -> if (fps >= 60) 25.0f else 18.0f
            width >= 1920 || height >= 1080 -> if (fps >= 60) 8.5f else 4.8f
            else -> if (fps >= 60) 4.2f else 2.4f
        }
    }

    /**
     * Auto-detects real physical camera sensors available on the installed device.
     */
    fun detectAvailableCameras(context: Context): List<CameraSensor> {
        val result = mutableListOf<CameraSensor>()
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager != null) {
                for (id in cameraManager.cameraIdList) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    val isFront = facing == CameraCharacteristics.LENS_FACING_FRONT
                    val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

                    val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val sizes = map?.getOutputSizes(SurfaceHolder::class.java) ?: emptyArray()
                    val maxW = sizes.maxOfOrNull { it.width } ?: 1920
                    val maxH = sizes.maxOfOrNull { it.height } ?: 1080
                    val resLabel = if (maxW >= 3840 || maxH >= 2160) "4K UHD" else if (maxW >= 1920 || maxH >= 1080) "1080p FHD" else "720p HD"

                    val name = if (isFront) "Front Camera" else if (id == "0") "Back Main Camera" else "Back Camera ($id)"
                    result.add(
                        CameraSensor(
                            id = id,
                            name = name,
                            resolutionLabel = resLabel,
                            focalLength = "",
                            aperture = "",
                            isFront = isFront,
                            supportsTorch = hasFlash
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        }
        if (result.isEmpty()) {
            result.add(CameraSensor("0", "Back Camera", "1080p FHD", "", "", isFront = false, supportsTorch = true))
            result.add(CameraSensor("1", "Front Camera", "1080p FHD", "", "", isFront = true, supportsTorch = false))
        }
        return result
    }

    /**
     * Auto-detects supported output profiles by cross-referencing camera and MediaCodec capabilities.
     */
    fun detectSupportedProfiles(): List<OutputProfile> {
        val profiles = mutableListOf<OutputProfile>()

        // 1. 4K 30 FPS (if hardware encoder supports 3840x2160)
        if (isResolutionSupported(3840, 2160)) {
            val clamped = clampConfiguration(3840, 2160, 30)
            if (!clamped.isClamped || (clamped.width == 3840 && clamped.height == 2160)) {
                profiles.add(
                    OutputProfile(
                        id = "4k_30",
                        name = "4K • 30 FPS",
                        resolution = "3840x2160",
                        fps = 30,
                        bitrateMbps = clamped.bitrateMbps,
                        badge = "Ultra HD",
                        codec = StreamCodec.H264
                    )
                )
            }
        }

        // 2. 1080p 60 FPS (if 60fps is supported at 1080p)
        if (isResolutionSupported(1920, 1080) && isFpsSupported(1920, 1080, 60)) {
            val clamped = clampConfiguration(1920, 1080, 60)
            if (clamped.fps >= 60) {
                profiles.add(
                    OutputProfile(
                        id = "1080p_60",
                        name = "1080p • 60 FPS",
                        resolution = "1920x1080",
                        fps = 60,
                        bitrateMbps = clamped.bitrateMbps,
                        badge = "Fluid 60",
                        codec = StreamCodec.H264
                    )
                )
            }
        }

        // 3. 1080p 30 FPS (Standard)
        if (isResolutionSupported(1920, 1080)) {
            val clamped = clampConfiguration(1920, 1080, 30)
            profiles.add(
                OutputProfile(
                    id = "1080p_30",
                    name = "1080p • 30 FPS",
                    resolution = "1920x1080",
                    fps = clamped.fps,
                    bitrateMbps = clamped.bitrateMbps,
                    badge = "Standard",
                    codec = StreamCodec.H264
                )
            )
        }

        // 4. 720p 60 FPS (if 60fps is supported at 720p)
        if (isResolutionSupported(1280, 720) && isFpsSupported(1280, 720, 60)) {
            val clamped = clampConfiguration(1280, 720, 60)
            if (clamped.fps >= 60) {
                profiles.add(
                    OutputProfile(
                        id = "720p_60",
                        name = "720p • 60 FPS",
                        resolution = "1280x720",
                        fps = 60,
                        bitrateMbps = clamped.bitrateMbps,
                        badge = "Fluid HD",
                        codec = StreamCodec.H264
                    )
                )
            }
        }

        // 5. 720p 30 FPS (Always available baseline)
        val clamped720p = clampConfiguration(1280, 720, 30)
        profiles.add(
            OutputProfile(
                id = "720p_30",
                name = "720p • 30 FPS",
                resolution = "1280x720",
                fps = clamped720p.fps,
                bitrateMbps = clamped720p.bitrateMbps,
                badge = "Low Bandwidth",
                codec = StreamCodec.H264
            )
        )

        return profiles
    }
}
