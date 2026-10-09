package com.focal.android

/**
 * Camera / screen / audio **hosting** is limited to the mobile (phone) app.
 * TV and desktop builds are receive-only clients for the Focal protocol.
 */
object FocalRoles {
    val canHostStreams: Boolean
        get() = BuildConfig.IS_STREAM_SENDER
}
