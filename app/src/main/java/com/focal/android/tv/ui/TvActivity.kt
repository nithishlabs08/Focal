package com.focal.android.tv.ui

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.focal.android.tv.client.TvAudioPlayer
import com.focal.android.tv.client.TvClientState
import com.focal.android.tv.client.TvStreamClient
import com.focal.android.tv.client.TvVideoDecoder
import com.focal.android.tv.discovery.TvDiscoveryManager
import com.focal.android.ui.receive.ReceivePlaybackCoordinator
import com.focal.android.ui.theme.FocalTheme

/**
 * Dedicated Android TV launcher Activity for the 'tv' product flavor.
 * Designed for 10-foot TV viewing, D-Pad remote navigation, and native Picture-in-Picture (PiP).
 */
class TvActivity : ComponentActivity() {

    private lateinit var discoveryManager: TvDiscoveryManager
    private lateinit var videoDecoder: TvVideoDecoder
    private lateinit var audioPlayer: TvAudioPlayer
    private lateinit var streamClient: TvStreamClient

    private var isPipMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        discoveryManager = TvDiscoveryManager(applicationContext)
        videoDecoder = TvVideoDecoder()
        audioPlayer = TvAudioPlayer()
        streamClient = TvStreamClient(videoDecoder, audioPlayer)

        setContent {
            FocalTheme(darkTheme = true) {
                TvMainScreen(
                    discoveryManager = discoveryManager,
                    streamClient = streamClient,
                    videoDecoder = videoDecoder,
                    audioPlayer = audioPlayer,
                    isPipMode = isPipMode,
                    onEnterPip = { enterPipMode() }
                )
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (ReceivePlaybackCoordinator.isReceivingStream ||
            streamClient.connectionState.value == TvClientState.STREAMING
        ) {
            enterPipMode()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && streamClient.connectionState.value == TvClientState.STREAMING) {
            streamClient.disconnect()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        discoveryManager.stopDiscovery()
        streamClient.disconnect()
    }
}
