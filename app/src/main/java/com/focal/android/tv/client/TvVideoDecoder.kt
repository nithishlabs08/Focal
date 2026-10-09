package com.focal.android.tv.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hardware-accelerated H.264 video decoder with Surface rendering for Android TV.
 * Also includes JPEG bitmap frame decoding fallback.
 */
class TvVideoDecoder(
    private var surface: Surface? = null,
    private var surfaceHolder: SurfaceHolder? = null
) {

    private var mediaCodec: MediaCodec? = null
    private var isDecoderConfigured = false
    private val isRunning = AtomicBoolean(false)

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val renderMatrix = Matrix()

    companion object {
        private const val TAG = "TvVideoDecoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
    }

    fun setSurface(newSurface: Surface?, holder: SurfaceHolder? = null) {
        surface = newSurface
        surfaceHolder = holder
        if (newSurface == null) {
            stop()
        } else if (isRunning.get() && !isDecoderConfigured) {
            initH264Decoder(1920, 1080)
        }
    }

    fun start(width: Int = 1920, height: Int = 1080) {
        if (isRunning.getAndSet(true)) return
        initH264Decoder(width, height)
    }

    /** Starts decoding once a [Surface] is available (TV player attaches surface after connect). */
    fun startIfSurfaceReady(width: Int = 1920, height: Int = 1080) {
        if (surface == null) return
        if (isRunning.get()) {
            if (!isDecoderConfigured) initH264Decoder(width, height)
            return
        }
        start(width, height)
    }

    private fun initH264Decoder(width: Int, height: Int) {
        val targetSurface = surface ?: return
        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
            }

            val codec = MediaCodec.createDecoderByType(MIME_TYPE)
            codec.configure(format, targetSurface, null, 0)
            codec.start()
            mediaCodec = codec
            isDecoderConfigured = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaCodec H.264 decoder", e)
            isDecoderConfigured = false
        }
    }

    fun feedH264Nal(
        nalBytes: ByteArray,
        isKeyframe: Boolean,
        timestampUs: Long,
        isConfig: Boolean = false
    ) {
        if (!isRunning.get()) {
            startIfSurfaceReady()
        }
        if (!isRunning.get()) return
        val codec = mediaCodec ?: return
        try {
            val inputIndex = codec.dequeueInputBuffer(10000L)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
                inputBuffer.clear()
                inputBuffer.put(nalBytes)

                val flags = when {
                    isConfig -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    isKeyframe -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                    else -> 0
                }
                codec.queueInputBuffer(inputIndex, 0, nalBytes.size, timestampUs, flags)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            while (outputIndex >= 0) {
                // Render true = render directly to the hardware surface
                codec.releaseOutputBuffer(outputIndex, true)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error decoding H.264 NAL", e)
        }
    }

    fun feedJpegFrame(jpegBytes: ByteArray) {
        val holder = surfaceHolder ?: return
        try {
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return
            val canvas = holder.lockCanvas() ?: return
            try {
                val viewW = canvas.width.toFloat()
                val viewH = canvas.height.toFloat()
                val bmpW = bitmap.width.toFloat()
                val bmpH = bitmap.height.toFloat()

                val scale = minOf(viewW / bmpW, viewH / bmpH)
                val dx = (viewW - bmpW * scale) / 2f
                val dy = (viewH - bmpH * scale) / 2f

                renderMatrix.reset()
                renderMatrix.postScale(scale, scale)
                renderMatrix.postTranslate(dx, dy)

                canvas.drawColor(android.graphics.Color.BLACK)
                canvas.drawBitmap(bitmap, renderMatrix, bitmapPaint)
            } finally {
                holder.unlockCanvasAndPost(canvas)
                bitmap.recycle()
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        isRunning.set(false)
        isDecoderConfigured = false
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {
        } finally {
            mediaCodec = null
        }
    }
}
