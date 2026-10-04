package com.example.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log

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

    fun queryH264EncoderCapabilities(): MediaCodecInfo.CodecCapabilities? {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder) {
                    for (type in info.supportedTypes) {
                        if (type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                            return info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                        }
                    }
                }
            }
            null
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
     * Dynamically verifies requested width, height, and fps against hardware encoder
     * and returns the closest safe configuration with zero crash.
     */
    fun clampConfiguration(
        requestedWidth: Int,
        requestedHeight: Int,
        requestedFps: Int
    ): ClampedConfig {
        var targetW = requestedWidth
        var targetH = requestedHeight
        var targetFps = requestedFps
        var clamped = false
        var explanation: String? = null

        val caps = queryH264EncoderCapabilities()
        val videoCaps = caps?.videoCapabilities

        // 1. Verify resolution
        if (videoCaps != null) {
            if (!videoCaps.isSizeSupported(targetW, targetH)) {
                clamped = true
                if (videoCaps.isSizeSupported(1920, 1080)) {
                    targetW = 1920
                    targetH = 1080
                    explanation = "Hardware encoder does not support $requestedWidth×$requestedHeight. Clamped to 1080p."
                } else {
                    targetW = 1280
                    targetH = 720
                    explanation = "Hardware encoder clamped resolution to 720p for compatibility."
                }
            }

            // 2. Verify frame rate
            if (!videoCaps.areSizeAndRateSupported(targetW, targetH, targetFps.toDouble())) {
                clamped = true
                targetFps = 30
                explanation = (explanation?.let { "$it " } ?: "") + "Target $requestedFps FPS unsupported at $targetW×$targetH. Fallback to 30 FPS."
            }
        } else {
            // Baseline fallback for headless environments: support up to 4K and 60 FPS
            if (targetW > 3840 || targetH > 2160) {
                targetW = 1920
                targetH = 1080
                clamped = true
                explanation = "Hardware encoder clamped resolution to 1080p."
            }
            if (targetFps > 60) {
                targetFps = 30
                clamped = true
                explanation = (explanation?.let { "$it " } ?: "") + "Target $requestedFps FPS unsupported. Fallback to 30 FPS."
            }
        }

        val recommendedBitrate = calculateBitrate(targetW, targetH, targetFps)

        return ClampedConfig(
            width = targetW,
            height = targetH,
            fps = targetFps,
            bitrateMbps = recommendedBitrate,
            isClamped = clamped,
            explanation = explanation
        )
    }

    fun calculateBitrate(width: Int, height: Int, fps: Int): Float {
        return when {
            width >= 3840 || height >= 2160 -> if (fps >= 60) 25.0f else 18.0f
            width >= 1920 || height >= 1080 -> if (fps >= 60) 8.5f else 4.8f
            else -> if (fps >= 60) 4.2f else 2.4f
        }
    }
}
