package com.example.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.CameraConflictState
import com.example.data.model.HostConnectionMode
import com.example.data.model.OutputProfile
import com.example.data.model.StreamCodec
import com.example.data.model.StreamMode
import com.example.media.AudioCaptureListener
import com.example.media.AudioController
import com.example.media.DeviceCapabilities
import com.example.media.RecoveryManager
import com.example.media.VideoEncoder
import com.example.media.VideoEncoderListener
import com.example.transport.TransportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WebcamStreamService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main)

    private var transportManager: TransportManager? = null
    private var videoEncoder: VideoEncoder? = null
    private var audioController: AudioController? = null
    private var recoveryManager: RecoveryManager? = null

    companion object {
        const val CHANNEL_ID = "focal_webcam_stream_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.example.server.ACTION_START"
        const val ACTION_STOP = "com.example.server.ACTION_STOP"
        const val ACTION_UPDATE_CONFIG = "com.example.server.ACTION_UPDATE_CONFIG"

        const val EXTRA_PORT = "com.example.server.EXTRA_PORT"
        const val EXTRA_PAIRING_PIN = "com.example.server.EXTRA_PAIRING_PIN"
        const val EXTRA_CONNECTION_MODE = "com.example.server.EXTRA_CONNECTION_MODE"
        const val EXTRA_STREAM_MODE = "com.example.server.EXTRA_STREAM_MODE"
        const val EXTRA_WIDTH = "com.example.server.EXTRA_WIDTH"
        const val EXTRA_HEIGHT = "com.example.server.EXTRA_HEIGHT"
        const val EXTRA_FPS = "com.example.server.EXTRA_FPS"
        const val EXTRA_BITRATE = "com.example.server.EXTRA_BITRATE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _conflictState = MutableStateFlow(CameraConflictState.NORMAL)
        val conflictState: StateFlow<CameraConflictState> = _conflictState.asStateFlow()

        private val _currentFps = MutableStateFlow(30.0f)
        val currentFps: StateFlow<Float> = _currentFps.asStateFlow()

        private val _currentBitrateMbps = MutableStateFlow(4.8f)
        val currentBitrateMbps: StateFlow<Float> = _currentBitrateMbps.asStateFlow()

        private val _connectedClients = MutableStateFlow(0)
        val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()

        var currentPairingPin: String = "849207"

        fun start(
            context: Context,
            pairingPin: String = currentPairingPin,
            connectionMode: HostConnectionMode = HostConnectionMode.WIFI,
            streamMode: StreamMode = StreamMode.VIDEO_ONLY,
            profile: OutputProfile? = null
        ) {
            currentPairingPin = pairingPin
            val intent = Intent(context, WebcamStreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PAIRING_PIN, pairingPin)
                putExtra(EXTRA_CONNECTION_MODE, connectionMode.name)
                putExtra(EXTRA_STREAM_MODE, streamMode.name)
                if (profile != null) {
                    val parts = profile.resolution.split("x")
                    if (parts.size == 2) {
                        putExtra(EXTRA_WIDTH, parts[0].toIntOrNull() ?: 1920)
                        putExtra(EXTRA_HEIGHT, parts[1].toIntOrNull() ?: 1080)
                    }
                    putExtra(EXTRA_FPS, profile.fps)
                    putExtra(EXTRA_BITRATE, profile.bitrateMbps)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WebcamStreamService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }

        val pairingPin = intent?.getStringExtra(EXTRA_PAIRING_PIN) ?: currentPairingPin
        currentPairingPin = pairingPin

        val modeStr = intent?.getStringExtra(EXTRA_CONNECTION_MODE) ?: HostConnectionMode.WIFI.name
        val connectionMode = try { HostConnectionMode.valueOf(modeStr) } catch (_: Exception) { HostConnectionMode.WIFI }

        val streamModeStr = intent?.getStringExtra(EXTRA_STREAM_MODE) ?: StreamMode.VIDEO_ONLY.name
        val streamMode = try { StreamMode.valueOf(streamModeStr) } catch (_: Exception) { StreamMode.VIDEO_ONLY }

        val rawWidth = intent?.getIntExtra(EXTRA_WIDTH, 1920) ?: 1920
        val rawHeight = intent?.getIntExtra(EXTRA_HEIGHT, 1080) ?: 1080
        val rawFps = intent?.getIntExtra(EXTRA_FPS, 30) ?: 30

        // Clamped configuration against hardware encoder
        val clamped = DeviceCapabilities.clampConfiguration(rawWidth, rawHeight, rawFps)

        startForegroundWithNotification(clamped.width, clamped.height, clamped.fps, connectionMode)
        initAndStartPipeline(pairingPin, connectionMode, streamMode, clamped.width, clamped.height, clamped.fps, clamped.bitrateMbps)

        _isRunning.value = true
        return START_STICKY
    }

    private fun initAndStartPipeline(
        pairingPin: String,
        connectionMode: HostConnectionMode,
        streamMode: StreamMode,
        width: Int,
        height: Int,
        fps: Int,
        bitrateMbps: Float
    ) {
        // 1. Initialize Transport Manager
        val tm = TransportManager(pairingPinProvider = { currentPairingPin })
        transportManager = tm
        tm.start(connectionMode)

        // 2. Initialize Video Encoder
        val encoderListener = object : VideoEncoderListener {
            override fun onEncodedNal(codec: StreamCodec, isKeyframe: Boolean, timestampUs: Long, data: ByteArray) {
                transportManager?.broadcastVideoNal(codec, isKeyframe, timestampUs, data)
                // Also mirror to legacy broadcaster for UI viewfinder preview
                CameraStreamBroadcaster.pushFrame(data)
            }

            override fun onEncoderError(throwable: Throwable) {
                recoveryManager?.onEncoderFailure(throwable)
            }
        }

        videoEncoder = VideoEncoder(
            width = width,
            height = height,
            fps = fps,
            bitrateMbps = bitrateMbps,
            listener = encoderListener
        ).apply {
            start()
        }

        // 3. Initialize Audio Controller (ONLY if streamMode == VIDEO_AND_AUDIO)
        val audioListener = object : AudioCaptureListener {
            override fun onAudioData(pcmBytes: ByteArray, timestampUs: Long) {
                transportManager?.broadcastAudio(pcmBytes, timestampUs)
            }
        }
        audioController = AudioController(audioListener)
        if (streamMode == StreamMode.VIDEO_AND_AUDIO) {
            audioController?.startRecording(hasPermission = true, streamMode = streamMode)
        }

        // 4. Initialize Recovery Manager
        recoveryManager = RecoveryManager(
            onRetryCamera = {
                // Retry binding camera surface to encoder
                true
            },
            onReinitEncoder = {
                videoEncoder?.stop()
                val safeConfig = DeviceCapabilities.clampConfiguration(1280, 720, 30)
                videoEncoder = VideoEncoder(safeConfig.width, safeConfig.height, safeConfig.fps, safeConfig.bitrateMbps, encoderListener).apply {
                    start()
                }
                true
            }
        )
    }

    private fun stopStreaming() {
        _isRunning.value = false
        audioController?.stopRecording()
        audioController = null

        videoEncoder?.stop()
        videoEncoder = null

        transportManager?.stop()
        transportManager = null

        recoveryManager?.clearState()
        recoveryManager = null
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focal Webcam Stream",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active hardware H.264 webcam streaming session"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification(
        width: Int,
        height: Int,
        fps: Int,
        mode: HostConnectionMode
    ) {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, WebcamStreamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focal Webcam Active")
            .setContentText("Hardware H.264 • ${width}x$height @ $fps FPS • ${mode.displayName}")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Stream", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
