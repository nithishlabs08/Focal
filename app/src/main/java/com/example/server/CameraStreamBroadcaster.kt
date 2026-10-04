package com.example.server

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object CameraStreamBroadcaster {

    private val listeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()

    // Minimal valid JPEG fallback (SOI + EOI markers) for headless JVM environments
    private val MINIMAL_JPEG = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00.toByte(), 0x10.toByte(),
        0x4A.toByte(), 0x46.toByte(), 0x49.toByte(), 0x46.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x48.toByte(), 0x00.toByte(), 0x48.toByte(),
        0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xD9.toByte()
    )

    @Volatile
    private var latestFrame: ByteArray? = null

    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()

    private val _streamFps = MutableStateFlow(30.0f)
    val streamFps: StateFlow<Float> = _streamFps.asStateFlow()

    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()

    fun pushFrame(jpegBytes: ByteArray) {
        latestFrame = jpegBytes
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsTimestamp >= 1000) {
            val elapsedSec = (now - lastFpsTimestamp) / 1000.0f
            _streamFps.value = (frameCount / elapsedSec).coerceAtLeast(1.0f)
            frameCount = 0
            lastFpsTimestamp = now
        }

        // Broadcast to all active MJPEG HTTP client connections
        listeners.forEach { listener ->
            try {
                listener(jpegBytes)
            } catch (_: Exception) {
            }
        }
    }

    fun getLatestFrame(): ByteArray {
        val frame = latestFrame
        return frame ?: generateFallbackFrame("Focal Ready to Stream")
    }

    fun addFrameListener(listener: (ByteArray) -> Unit) {
        listeners.add(listener)
    }

    fun removeFrameListener(listener: (ByteArray) -> Unit) {
        listeners.remove(listener)
    }

    fun setClientCount(count: Int) {
        _connectedClients.value = count.coerceAtLeast(0)
    }

    fun generateFallbackFrame(statusText: String): ByteArray {
        return try {
            val width = 1280
            val height = 720
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)

            // Dark studio background with gradient effect
            val bgPaint = Paint().apply { color = Color.rgb(18, 22, 28) }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Center reticle
            val reticlePaint = Paint().apply {
                color = Color.rgb(0, 83, 195)
                style = Paint.Style.STROKE
                strokeWidth = 3f
                isAntiAlias = true
            }
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawCircle(cx, cy, 90f, reticlePaint)
            canvas.drawCircle(cx, cy, 140f, reticlePaint.apply { color = Color.argb(100, 255, 255, 255) })

            // Crosshairs
            canvas.drawLine(cx - 160f, cy, cx + 160f, cy, reticlePaint)
            canvas.drawLine(cx, cy - 160f, cx, cy + 160f, reticlePaint)

            // Text paint
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 38f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("FOCAL • 100% LOCAL WEBCAM", cx, 100f, textPaint)

            val subTextPaint = Paint().apply {
                color = Color.rgb(116, 219, 153)
                textSize = 28f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(statusText, cx, 150f, subTextPaint)

            // Timestamp
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            val timePaint = Paint().apply {
                color = Color.rgb(176, 198, 255)
                textSize = 24f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Live Frame: ${timeFormat.format(Date())}", cx, height - 70f, timePaint)

            val streamInfoPaint = Paint().apply {
                color = Color.GRAY
                textSize = 20f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("1080p Ultra-Low Latency • Hardware MJPEG • Linux PipeWire Ready", cx, height - 35f, streamInfoPaint)

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val bytes = stream.toByteArray()
            latestFrame = bytes
            bytes
        } catch (_: Throwable) {
            latestFrame = MINIMAL_JPEG
            MINIMAL_JPEG
        }
    }
}
