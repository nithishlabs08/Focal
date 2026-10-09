package com.focal.android

/**
 * Flavor capabilities for stream **hosting**.
 *
 * Discovery, transport, pairing, crypto, and receive playback live in shared `main` code and run on
 * every flavor. Only the **camera** capture path is limited to the mobile (phone) app; screen and
 * audio hosting use the same [com.focal.android.server.WebcamStreamService] stack on all flavors.
 */
object FocalRoles {
    val canHostCameraStream: Boolean
        get() = BuildConfig.CAN_HOST_CAMERA_STREAM

    val canHostScreenOrAudioStream: Boolean
        get() = BuildConfig.CAN_HOST_SCREEN_AUDIO_STREAM
}
