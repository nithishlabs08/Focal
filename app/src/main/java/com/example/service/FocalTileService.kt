package com.example.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.server.WebcamStreamService

@RequiresApi(Build.VERSION_CODES.N)
class FocalTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isStreaming = WebcamStreamService.isRunning.value

        if (isStreaming) {
            // Stop the webcam stream
            WebcamStreamService.stop(this)
            updateTileState(false)
        } else {
            // Check Camera permission before starting
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (hasCameraPermission) {
                // Start with user's saved configuration
                WebcamStreamService.start(this)
                updateTileState(true)
            } else {
                // Must not silently request permissions that were never granted.
                // Open MainActivity so user can grant permissions.
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(launchIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(launchIntent)
                }
            }
        }
    }

    private fun updateTileState(forceRunning: Boolean? = null) {
        val tile = qsTile ?: return
        val isRunning = forceRunning ?: WebcamStreamService.isRunning.value

        if (isRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Focal Webcam"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Running"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Focal Webcam"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Ready"
            }
        }
        tile.updateTile()
    }
}
