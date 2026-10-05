package com.focal.android.tv.client

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log

/**
 * Low-latency audio player for playing the phone's 16-bit 48kHz mono PCM stream
 * through the Android TV / device speakers.
 */
class TvAudioPlayer {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    @Volatile
    var isMuted: Boolean = false
        private set

    companion object {
        private const val TAG = "TvAudioPlayer"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    fun start() {
        if (isPlaying) return
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isPlaying = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
            isPlaying = false
        }
    }

    fun playPcm(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        if (!isPlaying || isMuted || length <= 0) return
        try {
            audioTrack?.write(data, offset, length)
        } catch (_: Exception) {
        }
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        return isMuted
    }

    fun setMute(mute: Boolean) {
        isMuted = mute
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {
        } finally {
            audioTrack = null
        }
    }
}
