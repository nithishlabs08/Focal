package com.focal.android.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.focal.android.data.model.StreamMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface AudioCaptureListener {
    fun onAudioData(pcmBytes: ByteArray, timestampUs: Long)
}

class AudioController(
    private val listener: AudioCaptureListener
) {
    private val TAG = "AudioController"

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRecording = false
        private set

    /**
     * Critical requirement:
     * Video-only mode MUST NOT access or initialize the microphone.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(hasPermission: Boolean, streamMode: StreamMode): Boolean {
        // Enforce zero microphone access for video-only mode
        if (streamMode == StreamMode.VIDEO_ONLY) {
            stopRecording()
            return false
        }

        if (!hasPermission) {
            return false
        }

        if (isRecording) return true

        val sampleRate = 48000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) return false
        val bufferSize = minBufferSize * 2

        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }

            record.startRecording()
            audioRecord = record
            isRecording = true

            recordJob = scope.launch {
                val readBuffer = ByteArray(2048)
                while (isActive && isRecording) {
                    val bytesRead = record.read(readBuffer, 0, readBuffer.size)
                    if (bytesRead > 0) {
                        val timestampUs = System.nanoTime() / 1000
                        val dataToSend = readBuffer.copyOf(bytesRead)
                        listener.onAudioData(dataToSend, timestampUs)
                    }
                }
            }

            true
        } catch (_: Throwable) {
            isRecording = false
            false
        }
    }

    fun stopRecording() {
        isRecording = false
        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.stop()
        } catch (_: Throwable) {}

        try {
            audioRecord?.release()
        } catch (_: Throwable) {}
        audioRecord = null
    }
}
