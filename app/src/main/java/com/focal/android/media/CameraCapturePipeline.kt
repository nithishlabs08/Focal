package com.focal.android.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.focal.android.server.CameraStreamBroadcaster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Camera capture pipeline that connects live camera frames to:
 * 1. The H.264 video encoder's input Surface for hardware-accelerated video streaming.
 * 2. Optional camera preview (via PreviewView surface provider) for on-device display.
 * 3. The JPEG/MJPEG broadcaster (via compressed JPEG frames when subscribers are present).
 *
 * Runs as part of the streaming session lifecycle (decoupled from Compose UI), allowing
 * streaming to continue in the background and start directly from Quick Settings.
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
    private var previewUseCase: Preview? = null
    private var isFrontCamera: Boolean = false

    private var analysisExecutor: ExecutorService? = null

    @Volatile
    private var hasDeliveredFirstFrame: Boolean = false
    private var onFirstFrameCallback: (() -> Unit)? = null

    // Frame rate & output metrics tracking
    private val _actualFps = MutableStateFlow(0.0f)
    val actualFps: StateFlow<Float> = _actualFps.asStateFlow()

    private val _actualResolution = MutableStateFlow("1920x1080")
    val actualResolution: StateFlow<String> = _actualResolution.asStateFlow()

    private var frameCounter: Int = 0
    private var lastFpsCalculationTime: Long = 0L

    // Reusable graphics objects to avoid per-frame allocations
    private val renderMatrix = Matrix()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun setEncoderSurface(surface: Surface?, width: Int = 1920, height: Int = 1080) {
        encoderSurface = surface
        encoderWidth = width
        encoderHeight = height
        _actualResolution.value = "${width}x${height}"
        if (surface == null) {
            hasDeliveredFirstFrame = false
        }
    }

    fun getEncoderSurface(): Surface? = encoderSurface

    /**
     * Attaches the UI PreviewView surface provider if available.
     * Can be dynamically attached/detached without disrupting encoder streaming.
     */
    fun attachPreviewSurfaceProvider(surfaceProvider: Preview.SurfaceProvider?) {
        currentPreviewSurfaceProvider = surfaceProvider
        previewUseCase?.setSurfaceProvider(surfaceProvider)
    }

    fun detachPreviewSurfaceProvider() {
        currentPreviewSurfaceProvider = null
        previewUseCase?.setSurfaceProvider(null)
    }

    /**
     * Binds camera capture to the streaming session lifecycle (typically WebcamStreamService).
     * Does not require a CameraViewfinder or UI PreviewView to be active.
     */
    fun bindSessionCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        isFront: Boolean,
        width: Int = encoderWidth,
        height: Int = encoderHeight,
        onFirstFrame: (() -> Unit)? = null,
        onBound: ((Camera?) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        currentLifecycleOwner = lifecycleOwner
        isFrontCamera = isFront
        encoderWidth = width
        encoderHeight = height
        _actualResolution.value = "${width}x${height}"
        hasDeliveredFirstFrame = false
        onFirstFrameCallback = onFirstFrame

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val camera = doBind(provider, lifecycleOwner, currentPreviewSurfaceProvider, isFront)
                activeCamera = camera
                onBound?.invoke(camera)
            } catch (t: Throwable) {
                onError?.invoke(t)
                onBound?.invoke(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Legacy/preview binding for when service is not running and user is on preview screen.
     */
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

        // Preview use case (can be bound with or without an active surface provider)
        val preview = Preview.Builder().build()
        if (surfaceProvider != null) {
            preview.setSurfaceProvider(surfaceProvider)
        }
        previewUseCase = preview
        useCases.add(preview)

        if (analysisExecutor == null || analysisExecutor?.isShutdown == true) {
            analysisExecutor = Executors.newSingleThreadExecutor()
        }

        // Configure ImageAnalysis for the selected output resolution
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(encoderWidth, encoderHeight),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(resolutionSelector)
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
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // 1. Deliver frame to hardware H.264 encoder input surface
            val surface = encoderSurface
            if (surface != null && surface.isValid) {
                val rendered = renderBitmapToSurface(bitmap, surface, rotationDegrees)
                if (rendered && !hasDeliveredFirstFrame) {
                    hasDeliveredFirstFrame = true
                    onFirstFrameCallback?.invoke()
                }
            }

            // 2. Deliver JPEG frame to JPEG/MJPEG broadcaster only if there are active subscribers
            if (CameraStreamBroadcaster.connectedClients.value > 0) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                CameraStreamBroadcaster.pushFrame(stream.toByteArray())
            }

            // Track actual FPS
            frameCounter++
            val now = System.currentTimeMillis()
            if (lastFpsCalculationTime == 0L) {
                lastFpsCalculationTime = now
            } else if (now - lastFpsCalculationTime >= 1000L) {
                val elapsedSec = (now - lastFpsCalculationTime) / 1000.0f
                _actualFps.value = frameCounter / elapsedSec
                frameCounter = 0
                lastFpsCalculationTime = now
            }
        } catch (_: Throwable) {
        } finally {
            imageProxy.close()
        }
    }

    fun renderBitmapToSurface(bitmap: Bitmap, surface: Surface, rotationDegrees: Int = 0): Boolean {
        if (!surface.isValid) return false
        var canvas: Canvas? = null
        try {
            canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                surface.lockHardwareCanvas()
            } else {
                surface.lockCanvas(null)
            }
            if (canvas != null) {
                val canvasWidth = canvas.width.toFloat()
                val canvasHeight = canvas.height.toFloat()

                synchronized(renderMatrix) {
                    renderMatrix.reset()
                    // Center origin on bitmap
                    renderMatrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)

                    // Apply frame rotation
                    if (rotationDegrees != 0) {
                        renderMatrix.postRotate(rotationDegrees.toFloat())
                    }

                    // Account for rotated dimensions to preserve aspect ratio
                    val (orientedW, orientedH) = if (rotationDegrees == 90 || rotationDegrees == 270) {
                        bitmap.height.toFloat() to bitmap.width.toFloat()
                    } else {
                        bitmap.width.toFloat() to bitmap.height.toFloat()
                    }

                    // Center-crop / fill scale factor
                    val scale = maxOf(canvasWidth / orientedW, canvasHeight / orientedH)
                    renderMatrix.postScale(scale, scale)

                    // Translate back to canvas center
                    renderMatrix.postTranslate(canvasWidth / 2f, canvasHeight / 2f)

                    canvas.drawBitmap(bitmap, renderMatrix, bitmapPaint)
                }
                return true
            }
            return false
        } catch (_: Throwable) {
            // Handle surface/render failures gracefully without crashing
            return false
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
        previewUseCase?.setSurfaceProvider(null)
        previewUseCase = null
        currentLifecycleOwner = null
        currentPreviewSurfaceProvider = null
        encoderSurface = null
        hasDeliveredFirstFrame = false
        onFirstFrameCallback = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
        _actualFps.value = 0.0f
    }
}
