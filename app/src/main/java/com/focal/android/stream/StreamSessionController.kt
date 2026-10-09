package com.focal.android.stream

import android.content.Context
import android.content.Intent
import com.focal.android.data.model.HostConnectionMode
import com.focal.android.data.model.OutputProfile
import com.focal.android.data.model.StreamMode
import com.focal.android.data.model.StreamSource
import com.focal.android.FocalRoles
import com.focal.android.server.WebcamStreamService
import com.focal.android.transport.FocalDiscoveryManager
import com.focal.android.transport.PairingManager

/**
 * Non-UI entry point for starting and stopping Focal streams (intents, tiles, automation).
 */
object StreamSessionController {

    const val ACTION_START_CAMERA_STREAM = "com.focal.android.action.START_CAMERA_STREAM"
    const val ACTION_START_AUDIO_STREAM = "com.focal.android.action.START_AUDIO_STREAM"
    const val ACTION_START_SCREEN_STREAM = "com.focal.android.action.START_SCREEN_STREAM"
    const val ACTION_STOP_STREAM = "com.focal.android.action.STOP_STREAM"

    const val EXTRA_CONNECTION_MODE = "connection_mode"
    const val EXTRA_STREAM_MODE = "stream_mode"
    const val EXTRA_PAIRING_PIN = "pairing_pin"

    fun startCameraStream(
        context: Context,
        connectionMode: HostConnectionMode = HostConnectionMode.WIFI,
        streamMode: StreamMode = StreamMode.VIDEO_ONLY,
        pairingPin: String? = null,
        profile: OutputProfile? = null
    ) {
        if (!FocalRoles.canHostCameraStream) return
        val pin = pairingPin ?: PairingManager.generateNewPin()
        WebcamStreamService.start(
            context = context,
            pairingPin = pin,
            connectionMode = connectionMode,
            streamMode = streamMode,
            streamSource = StreamSource.CAMERA,
            profile = profile
        )
        registerDiscoveryIfWifi(context, connectionMode, pin)
    }

    fun startAudioOnlyStream(
        context: Context,
        connectionMode: HostConnectionMode = HostConnectionMode.WIFI,
        pairingPin: String? = null
    ) {
        if (!FocalRoles.canHostScreenOrAudioStream) return
        val pin = pairingPin ?: PairingManager.generateNewPin()
        WebcamStreamService.start(
            context = context,
            pairingPin = pin,
            connectionMode = connectionMode,
            streamMode = StreamMode.VIDEO_AND_AUDIO,
            streamSource = StreamSource.AUDIO_ONLY
        )
        registerDiscoveryIfWifi(context, connectionMode, pin)
    }

    fun startScreenStream(
        context: Context,
        mediaProjectionResultCode: Int,
        mediaProjectionResultData: Intent,
        connectionMode: HostConnectionMode = HostConnectionMode.WIFI,
        streamMode: StreamMode = StreamMode.VIDEO_ONLY,
        pairingPin: String? = null
    ) {
        if (!FocalRoles.canHostScreenOrAudioStream) return
        val pin = pairingPin ?: PairingManager.generateNewPin()
        WebcamStreamService.start(
            context = context,
            pairingPin = pin,
            connectionMode = connectionMode,
            streamMode = streamMode,
            streamSource = StreamSource.SCREEN,
            mediaProjectionResultCode = mediaProjectionResultCode,
            mediaProjectionResultData = mediaProjectionResultData
        )
        registerDiscoveryIfWifi(context, connectionMode, pin)
    }

    fun stopStream(context: Context) {
        WebcamStreamService.stop(context)
        FocalDiscoveryManager.unregisterService()
    }

    private fun registerDiscoveryIfWifi(context: Context, mode: HostConnectionMode, pin: String) {
        if (mode == HostConnectionMode.WIFI) {
            FocalDiscoveryManager.registerService(context.applicationContext, port = 8080, pin = pin)
        }
    }
}
