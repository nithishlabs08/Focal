package com.focal.android.stream

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Minimal activity: only shows the system screen-capture consent dialog, then starts the service.
 */
class ScreenCaptureRequestActivity : ComponentActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            StreamSessionController.startScreenStream(
                context = this,
                mediaProjectionResultCode = result.resultCode,
                mediaProjectionResultData = result.data!!
            )
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    companion object {
        fun createLaunchIntent(context: Context): Intent =
            Intent(context, ScreenCaptureRequestActivity::class.java)
    }
}
