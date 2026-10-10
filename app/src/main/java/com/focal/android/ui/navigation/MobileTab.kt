package com.focal.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** LocalSend-style primary destinations on the phone app. */
enum class MobileTab(
    val label: String,
    val icon: ImageVector
) {
    RECEIVE("Receive", Icons.Default.Download),
    SEND("Send", Icons.AutoMirrored.Filled.Send),
    SETTINGS("Settings", Icons.Default.Settings)
}
