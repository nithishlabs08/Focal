package com.focal.android.data.model

/** Primary mode: publish a stream or watch a remote Focal sender. */
enum class FocalAppMode(val displayName: String) {
    SEND("Send"),
    RECEIVE("Receive")
}
