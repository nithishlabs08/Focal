package com.focal.android.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.focal.android.MainActivity
import com.focal.android.server.WebcamStreamService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class FocalTileService : TileService() {

    private var observeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
        observeJob?.cancel()
        observeJob = scope.launch {
            WebcamStreamService.isRunning.collect { running ->
                updateTileState(running)
            }
        }
    }

    override fun onStopListening() {
        observeJob?.cancel()
        observeJob = null
        super.onStopListening()
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
                // Start stream session; tile state stays INACTIVE until transport, encoder,
                // and camera capture have started and the first live frame reaches the encoder.
                WebcamStreamService.start(this)
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
