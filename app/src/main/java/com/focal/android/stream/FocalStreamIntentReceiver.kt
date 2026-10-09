package com.focal.android.stream

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focal.android.data.model.HostConnectionMode
import com.focal.android.data.model.StreamMode

/**
 * Starts/stops streams via `adb shell am broadcast` without opening the main UI.
 */
class FocalStreamIntentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val appContext = context.applicationContext
        when (intent.action) {
            StreamSessionController.ACTION_START_CAMERA_STREAM -> {
                val mode = intent.getStringExtra(StreamSessionController.EXTRA_CONNECTION_MODE)
                    ?.let { runCatching { HostConnectionMode.valueOf(it) }.getOrNull() }
                    ?: HostConnectionMode.WIFI
                val streamMode = intent.getStringExtra(StreamSessionController.EXTRA_STREAM_MODE)
                    ?.let { runCatching { StreamMode.valueOf(it) }.getOrNull() }
                    ?: StreamMode.VIDEO_ONLY
                val pin = intent.getStringExtra(StreamSessionController.EXTRA_PAIRING_PIN)
                StreamSessionController.startCameraStream(appContext, mode, streamMode, pin)
            }
            StreamSessionController.ACTION_START_AUDIO_STREAM -> {
                val pin = intent.getStringExtra(StreamSessionController.EXTRA_PAIRING_PIN)
                StreamSessionController.startAudioOnlyStream(appContext, pairingPin = pin)
            }
            StreamSessionController.ACTION_START_SCREEN_STREAM -> {
                val launch = Intent(appContext, ScreenCaptureRequestActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(launch)
            }
            StreamSessionController.ACTION_STOP_STREAM -> {
                StreamSessionController.stopStream(appContext)
            }
        }
    }
}
