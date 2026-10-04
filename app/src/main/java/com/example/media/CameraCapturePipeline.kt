package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.server.CameraStreamBroadcaster
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Camera capture pipeline that connects live camera frames to:
 * 1. The H.264 video encoder's input Surface for hardware-accelerated video streaming.
 * 2. The camera preview (via PreviewView surface provider) for on-device display.
 * 3. The JPEG/MJPEG broadcaster (via compressed JPEG frames).
 */
object CameraCapturePipeline {

    @Volatile
    private var encoderSurface: Surface? = null

    @Volatile
    var encoderWidth: Int = 1920
        private set

    @Volatile
    var encoderHeight: Int = 1080
        private set

    private var activeCamera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentPreviewSurfaceProvider: Preview.SurfaceProvider? = null
    private var isFrontCamera: Boolean = false

    private var analysisExecutor: ExecutorService? = null

    fun setEncoderSurface(surface: Surface?, width: Int = 1920, height: Int = 1080) {
        encoderSurface = surface
        encoderWidth = width
        encoderHeight = height
    }

    fun getEncoderSurface(): Surface? = encoderSurface

    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider?,
        isFront: Boolean,
        onBound: ((Camera?) -> Unit)? = null
    ) {
        currentLifecycleOwner = lifecycleOwner
        currentPreviewSurfaceProvider = surfaceProvider
        isFrontCamera = isFront

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val camera = doBind(provider, lifecycleOwner, surfaceProvider, isFront)
                activeCamera = camera
                onBound?.invoke(camera)
            } catch (_: Throwable) {
                onBound?.invoke(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun rebind(context: Context): Boolean {
        val provider = cameraProvider ?: return false
        val lifecycleOwner = currentLifecycleOwner ?: return false
        return try {
            val camera = doBind(provider, lifecycleOwner, currentPreviewSurfaceProvider, isFrontCamera)
            activeCamera = camera
            camera != null
        } catch (_: Throwable) {
            false
        }
    }

    private fun doBind(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider?,
        isFront: Boolean
    ): Camera? {
        provider.unbindAll()

        val cameraSelector = if (isFront) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val useCases = mutableListOf<androidx.camera.core.UseCase>()

        if (surfaceProvider != null) {
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(surfaceProvider)
            useCases.add(preview)
        }

        if (analysisExecutor == null || analysisExecutor?.isShutdown == true) {
            analysisExecutor = Executors.newSingleThreadExecutor()
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalysis.setAnalyzer(analysisExecutor!!) { imageProxy ->
            processCameraFrame(imageProxy)
        }
        useCases.add(imageAnalysis)

        return provider.bindToLifecycle(lifecycleOwner, cameraSelector, *useCases.toTypedArray())
    }

    fun processCameraFrame(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()

            // 1. Deliver frame to hardware H.264 encoder input surface
            val surface = encoderSurface
            if (surface != null && surface.isValid) {
                renderBitmapToSurface(bitmap, surface)
            }

            // 2. Deliver JPEG frame to JPEG/MJPEG broadcaster
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            CameraStreamBroadcaster.pushFrame(stream.toByteArray())
        } catch (_: Throwable) {
        } finally {
            imageProxy.close()
        }
    }

    fun renderBitmapToSurface(bitmap: Bitmap, surface: Surface) {
        if (!surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                surface.lockHardwareCanvas()
            } else {
                surface.lockCanvas(null)
            }
            if (canvas != null) {
                val destRect = Rect(0, 0, canvas.width, canvas.height)
                canvas.drawBitmap(bitmap, null, destRect, null)
            }
        } catch (_: Throwable) {
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun unbind() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Throwable) {}
        activeCamera = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }
}
