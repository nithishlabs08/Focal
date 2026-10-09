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
import android.view.Surface
import android.view.WindowManager

/**
 * Captures the device display via [MediaProjection] into the encoder [Surface].
 */
object ScreenCapturePipeline {

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

        val projectionManager =
            context.applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData) ?: return false

        val metrics = DisplayMetrics()
        val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val density = metrics.densityDpi

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
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            encoderSurface,
            null,
            null
        )
        return virtualDisplay != null
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
        mediaProjection?.stop()
        mediaProjection = null
    }
}
