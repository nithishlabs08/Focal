package com.focal.android.ui.receive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared receive-playback state for PiP and background viewing (read from MainActivity). */
object ReceivePlaybackCoordinator {
    var isReceivingStream: Boolean by mutableStateOf(false)
}
