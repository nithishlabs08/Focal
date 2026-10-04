package com.focal.android.server

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.focal.android.MainActivity
import com.focal.android.R
import com.focal.android.data.model.CameraConflictState
import com.focal.android.data.model.HostConnectionMode
import com.focal.android.data.model.OutputProfile
import com.focal.android.data.model.StreamCodec
import com.focal.android.data.model.StreamMode
import com.focal.android.media.AudioCaptureListener
import com.focal.android.media.AudioController
import com.focal.android.media.CameraCapturePipeline
import com.focal.android.media.DeviceCapabilities
import com.focal.android.media.RecoveryManager
import com.focal.android.media.VideoEncoder
import com.focal.android.media.VideoEncoderListener
import com.focal.android.transport.FocalDiscoveryManager
import com.focal.android.transport.PairingManager
import com.focal.android.transport.TransportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WebcamStreamService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private var transportManager: TransportManager? = null
    private var videoEncoder: VideoEncoder? = null
    private var audioController: AudioController? = null
    private var recoveryManager: RecoveryManager? = null

    companion object {
        const val CHANNEL_ID = "focal_webcam_stream_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.focal.android.server.ACTION_START"
        const val ACTION_STOP = "com.focal.android.server.ACTION_STOP"
        const val ACTION_UPDATE_CONFIG = "com.focal.android.server.ACTION_UPDATE_CONFIG"

        const val EXTRA_PORT = "com.focal.android.server.EXTRA_PORT"
        const val EXTRA_PAIRING_PIN = "com.focal.android.server.EXTRA_PAIRING_PIN"
        const val EXTRA_CONNECTION_MODE = "com.focal.android.server.EXTRA_CONNECTION_MODE"
        const val EXTRA_STREAM_MODE = "com.focal.android.server.EXTRA_STREAM_MODE"
        const val EXTRA_WIDTH = "com.focal.android.server.EXTRA_WIDTH"
        const val EXTRA_HEIGHT = "com.focal.android.server.EXTRA_HEIGHT"
        const val EXTRA_FPS = "com.focal.android.server.EXTRA_FPS"
        const val EXTRA_BITRATE = "com.focal.android.server.EXTRA_BITRATE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isAudioActive = MutableStateFlow(false)
        val isAudioActive: StateFlow<Boolean> = _isAudioActive.asStateFlow()

        private val _startupError = MutableStateFlow<String?>(null)
        val startupError: StateFlow<String?> = _startupError.asStateFlow()

        private val _conflictState = MutableStateFlow(CameraConflictState.NORMAL)
        val conflictState: StateFlow<CameraConflictState> = _conflictState.asStateFlow()

        private val _currentFps = MutableStateFlow(30.0f)
        val currentFps: StateFlow<Float> = _currentFps.asStateFlow()

        private val _currentBitrateMbps = MutableStateFlow(4.8f)
        val currentBitrateMbps: StateFlow<Float> = _currentBitrateMbps.asStateFlow()

        private val _connectedClients = MutableStateFlow(0)
        val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()

        var currentPairingPin: String
            get() = PairingManager.currentPin
            set(value) {
                PairingManager.setPin(value)
            }

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

        fun markStreamReadyForTesting() {
            _isRunning.value = true
        }

        fun resetStateForTesting() {
            _isRunning.value = false
            _isAudioActive.value = false
            _startupError.value = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _startupError.value = null

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
        val rawBitrate = intent?.getFloatExtra(EXTRA_BITRATE, 4.8f) ?: 4.8f

        // Check camera permission first
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            _startupError.value = "Camera permission not granted"
            _isRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }

        // Check microphone permission before audio capture
        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val audioRequested = streamMode == StreamMode.VIDEO_AND_AUDIO && hasMicPermission

        // Clamped configuration against hardware encoder honoring requested bitrate
        val clamped = DeviceCapabilities.clampConfiguration(rawWidth, rawHeight, rawFps, rawBitrate)

        // Avoid leaking resources on repeated starts by stopping existing session first
        if (_isRunning.value) {
            stopStreaming()
        }

        startForegroundWithNotification(
            clamped.width,
            clamped.height,
            clamped.fps,
            connectionMode,
            audioEnabled = audioRequested,
            isReady = false
        )

        val pipelineInitialized = initAndStartPipeline(
            connectionMode = connectionMode,
            streamMode = streamMode,
            hasMicPermission = hasMicPermission,
            width = clamped.width,
            height = clamped.height,
            fps = clamped.fps,
            bitrateMbps = clamped.bitrateMbps
        )

        if (pipelineInitialized) {
            return START_STICKY
        } else {
            _isRunning.value = false
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun initAndStartPipeline(
        connectionMode: HostConnectionMode,
        streamMode: StreamMode,
        hasMicPermission: Boolean,
        width: Int,
        height: Int,
        fps: Int,
        bitrateMbps: Float
    ): Boolean {
        try {
            // 1. Initialize Transport Manager
            val tm = TransportManager(
                pairingPinProvider = { PairingManager.currentPin },
                pinValidator = { PairingManager.isPinValid(it) }
            )
            transportManager = tm
            tm.start(connectionMode)

            if (!tm.isAnyRunning()) {
                _startupError.value = "Failed to bind network transport"
                return false
            }

            // Register mDNS auto-discovery on local Wi-Fi
            if (connectionMode == HostConnectionMode.WIFI) {
                FocalDiscoveryManager.registerService(applicationContext, port = 8080, pin = PairingManager.currentPin)
            }

            // 2. Initialize Video Encoder
            val encoderListener = object : VideoEncoderListener {
                override fun onEncodedNal(codec: StreamCodec, isKeyframe: Boolean, isConfig: Boolean, timestampUs: Long, data: ByteArray) {
                    transportManager?.broadcastVideoNal(codec, isKeyframe, isConfig, timestampUs, data)
                }

                override fun onEncoderError(throwable: Throwable) {
                    recoveryManager?.onEncoderFailure(throwable)
                }
            }

            val encoder = VideoEncoder(
                width = width,
                height = height,
                fps = fps,
                bitrateMbps = bitrateMbps,
                listener = encoderListener
            )
            videoEncoder = encoder
            val encoderStarted = encoder.start()
            if (!encoderStarted) {
                _startupError.value = "Failed to initialize video encoder"
                return false
            }

            // Connect camera capture pipeline to the H.264 encoder's input Surface
            CameraCapturePipeline.setEncoderSurface(encoder.inputSurface, width, height)

            // 3. Initialize Audio Controller (ONLY if streamMode == VIDEO_AND_AUDIO and permission is granted)
            val audioListener = object : AudioCaptureListener {
                override fun onAudioData(pcmBytes: ByteArray, timestampUs: Long) {
                    transportManager?.broadcastAudio(pcmBytes, timestampUs)
                }
            }
            audioController = AudioController(audioListener)
            if (streamMode == StreamMode.VIDEO_AND_AUDIO && hasMicPermission) {
                val audioStarted = audioController?.startRecording(hasPermission = true, streamMode = streamMode) == true
                _isAudioActive.value = audioStarted
            } else {
                _isAudioActive.value = false
            }

            // 4. Initialize Recovery Manager
            recoveryManager = RecoveryManager(
                onRetryCamera = {
                    CameraCapturePipeline.rebind(applicationContext)
                },
                onReinitEncoder = {
                    videoEncoder?.stop()
                    val safeConfig = DeviceCapabilities.clampConfiguration(1280, 720, 30, 2.4f)
                    val fallbackEncoder = VideoEncoder(
                        safeConfig.width,
                        safeConfig.height,
                        safeConfig.fps,
                        safeConfig.bitrateMbps,
                        encoderListener
                    )
                    val started = fallbackEncoder.start()
                    if (started) {
                        videoEncoder = fallbackEncoder
                        CameraCapturePipeline.setEncoderSurface(fallbackEncoder.inputSurface, safeConfig.width, safeConfig.height)
                        CameraCapturePipeline.rebind(applicationContext)
                        true
                    } else {
                        _startupError.value = "Fallback encoder initialization failed"
                        false
                    }
                }
            )

            // 5. Bind camera capture session to service lifecycle
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            CameraCapturePipeline.bindSessionCamera(
                context = applicationContext,
                lifecycleOwner = this,
                isFront = false,
                width = width,
                height = height,
                onFirstFrame = {
                    // Actual readiness: Transport, encoder, camera bound AND first live frame reached encoder
                    _isRunning.value = true
                    updateNotification(
                        width = width,
                        height = height,
                        fps = fps,
                        mode = connectionMode,
                        audioEnabled = _isAudioActive.value,
                        isReady = true
                    )
                },
                onError = { err ->
                    _startupError.value = "Camera binding failed: ${err.message}"
                    stopStreaming()
                    stopSelf()
                }
            )

            return true
        } catch (t: Throwable) {
            _startupError.value = t.message ?: "Startup failed"
            return false
        }
    }

    private fun stopStreaming() {
        _isRunning.value = false
        _isAudioActive.value = false

        FocalDiscoveryManager.unregisterService()

        CameraCapturePipeline.unbind()
        CameraCapturePipeline.setEncoderSurface(null)

        audioController?.stopRecording()
        audioController = null

        videoEncoder?.stop()
        videoEncoder = null

        transportManager?.stop()
        transportManager = null

        recoveryManager?.clearState()
        recoveryManager = null

        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
    }

    override fun onDestroy() {
        stopStreaming()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
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
        mode: HostConnectionMode,
        audioEnabled: Boolean,
        isReady: Boolean
    ) {
        val notification = buildNotification(width, height, fps, mode, audioEnabled, isReady)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (audioEnabled) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        width: Int,
        height: Int,
        fps: Int,
        mode: HostConnectionMode,
        audioEnabled: Boolean,
        isReady: Boolean
    ) {
        val notification = buildNotification(width, height, fps, mode, audioEnabled, isReady)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        width: Int,
        height: Int,
        fps: Int,
        mode: HostConnectionMode,
        audioEnabled: Boolean,
        isReady: Boolean
    ): android.app.Notification {
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

        val title = if (isReady) "Focal Webcam Active" else "Focal Webcam Starting…"
        val statusText = if (isReady) {
            val audioTag = if (audioEnabled) " + 48kHz PCM" else ""
            "Hardware H.264 • ${width}x$height @ $fps FPS$audioTag • ${mode.displayName}"
        } else {
            "Connecting camera & hardware encoder…"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Stream", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
