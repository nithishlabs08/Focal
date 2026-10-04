package com.example.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.example.data.model.StreamCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface VideoEncoderListener {
    fun onEncodedNal(codec: StreamCodec, isKeyframe: Boolean, isConfig: Boolean, timestampUs: Long, data: ByteArray)
    fun onEncoderError(throwable: Throwable)
}

class VideoEncoder(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val bitrateMbps: Float = 4.8f,
    private val listener: VideoEncoderListener
) {
    private val TAG = "VideoEncoder"

    private var codec: MediaCodec? = null
    var inputSurface: Surface? = null
        private set

    private var drainJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    var isRunning = false
        private set

    private var spsPpsHeader: ByteArray? = null

    fun start(): Boolean {
        if (isRunning) return true
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (bitrateMbps * 1_000_000).toInt())
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe interval for rapid sync
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        setInteger(MediaFormat.KEY_LATENCY, 0)
                        setInteger(MediaFormat.KEY_PRIORITY, 0)
                    } catch (_: Throwable) {}
                }
            }

            // Prefer hardware AVC encoder when available
            val bestEncoderInfo = DeviceCapabilities.findBestAvcEncoder()
            codec = if (bestEncoderInfo != null) {
                try {
                    MediaCodec.createByCodecName(bestEncoderInfo.name)
                } catch (_: Throwable) {
                    MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                }
            } else {
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            }.apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }

            isRunning = true
            startDrainLoop()
            return true
        } catch (e: Throwable) {
            stop()
            listener.onEncoderError(e)
            return false
        }
    }

    fun stop() {
        isRunning = false
        drainJob?.cancel()
        drainJob = null

        try {
            codec?.stop()
        } catch (_: Throwable) {}

        try {
            codec?.release()
        } catch (_: Throwable) {}
        codec = null

        try {
            inputSurface?.release()
        } catch (_: Throwable) {}
        inputSurface = null
    }

    private fun startDrainLoop() {
        drainJob = scope.launch {
            val bufferInfo = MediaCodec.BufferInfo()
            val localCodec = codec ?: return@launch

            while (isActive && isRunning) {
                try {
                    val outputBufferIndex = localCodec.dequeueOutputBuffer(bufferInfo, 10_000) // 10ms timeout
                    if (outputBufferIndex >= 0) {
                        val outputBuffer = localCodec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val outData = ByteArray(bufferInfo.size)
                            outputBuffer.get(outData)

                            val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            val isKeyframe = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                            if (isCodecConfig) {
                                spsPpsHeader = outData
                                listener.onEncodedNal(
                                    codec = StreamCodec.H264,
                                    isKeyframe = false,
                                    isConfig = true,
                                    timestampUs = bufferInfo.presentationTimeUs,
                                    data = outData
                                )
                            } else {
                                val (nalToSend, hasConfig) = if (isKeyframe && spsPpsHeader != null) {
                                    // Prepend SPS/PPS to IDR keyframe so new connecting clients can decode immediately
                                    (spsPpsHeader!! + outData) to true
                                } else {
                                    outData to false
                                }
                                listener.onEncodedNal(
                                    codec = StreamCodec.H264,
                                    isKeyframe = isKeyframe,
                                    isConfig = hasConfig,
                                    timestampUs = bufferInfo.presentationTimeUs,
                                    data = nalToSend
                                )
                            }
                        }
                        localCodec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                } catch (e: Throwable) {
                    if (isRunning) {
                        listener.onEncoderError(e)
                    }
                    break
                }
            }
        }
    }
}
