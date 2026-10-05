package com.focal.android.tv.model

/**
 * Represents a Focal camera streamer discovered on the local Wi-Fi or Ethernet network.
 */
data class DiscoveredCamera(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 8080,
    val lastSeenMs: Long = System.currentTimeMillis()
) {
    val streamUrl: String
        get() = "http://$host:$port/stream.h264"

    val mjpegUrl: String
        get() = "http://$host:$port/stream.mjpg"
}
