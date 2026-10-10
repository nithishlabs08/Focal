package com.focal.android.media

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import kotlin.math.min

/**
 * Captures the device display via [MediaProjection] into the encoder [Surface].
 */
object ScreenCapturePipeline {

    private const val TAG = "ScreenCapturePipeline"

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var projectionCallback: MediaProjection.Callback? = null

    fun start(
        context: Context,
        resultCode: Int,
        resultData: Intent,
        encoderSurface: Surface,
        width: Int,
        height: Int,
        onStopped: () -> Unit = {}
    ): Boolean {
        stop()

        if (!encoderSurface.isValid) {
            Log.e(TAG, "Encoder surface is not valid")
            return false
        }

        return try {
            val projectionManager =
                context.applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                Log.e(TAG, "getMediaProjection returned null")
                return false
            }

            val metrics = DisplayMetrics()
            val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val density = metrics.densityDpi

            val captureWidth = min(width, metrics.widthPixels).coerceAtLeast(320)
            val captureHeight = min(height, metrics.heightPixels).coerceAtLeast(240)

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    onStopped()
                    stop()
                }
            }
            projection.registerCallback(callback, Handler(Looper.getMainLooper()))
            projectionCallback = callback
            mediaProjection = projection

            virtualDisplay = projection.createVirtualDisplay(
                "FocalScreenCapture",
                captureWidth,
                captureHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                encoderSurface,
                null,
                Handler(Looper.getMainLooper())
            )
            virtualDisplay != null
        } catch (t: Throwable) {
            Log.e(TAG, "Screen capture failed", t)
            stop()
            false
        }
    }

    fun stop() {
        virtualDisplay?.release()
        virtualDisplay = null
        projectionCallback?.let { cb ->
            try {
                mediaProjection?.unregisterCallback(cb)
            } catch (_: Exception) {
            }
        }
        projectionCallback = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
    }
}
