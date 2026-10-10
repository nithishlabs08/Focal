package com.focal.android.tv.client

import android.content.Context
import android.util.Log
import com.focal.android.transport.FocalLanNetwork
import com.focal.android.transport.FocalSessionCrypto
import com.focal.android.transport.PacketType
import com.focal.android.transport.StreamPacket
import javax.crypto.SecretKey
import com.focal.android.tv.model.DiscoveredCamera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

enum class TvClientState {
    IDLE,
    CONNECTING,
    AUTHENTICATING,
    STREAMING,
    AUTH_FAILED,
    ERROR,
    DISCONNECTED,
    RECONNECTING
}

/**
 * Network client that connects to a Focal camera streamer, handles PIN authentication,
 * reads binary StreamPackets (H.264 and PCM audio), and computes live FPS/bitrate metrics.
 */
class TvStreamClient(
    private val videoDecoder: TvVideoDecoder,
    private val audioPlayer: TvAudioPlayer,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private var clientJob: Job? = null
    private var reconnectJob: Job? = null

    private var activeSocket: Socket? = null
    private val isRunning = AtomicBoolean(false)
    private var userInitiatedDisconnect = false

    private var lastCamera: DiscoveredCamera? = null
    private var lastPin: String? = null
    private var sessionKey: SecretKey? = null

    var autoReconnectEnabled: Boolean = true
    var maxReconnectAttempts: Int = 5

    private val _connectionState = MutableStateFlow(TvClientState.IDLE)
    val connectionState: StateFlow<TvClientState> = _connectionState.asStateFlow()

    private val _currentFps = MutableStateFlow(0f)
    val currentFps: StateFlow<Float> = _currentFps.asStateFlow()

    private val _currentBitrateMbps = MutableStateFlow(0f)
    val currentBitrateMbps: StateFlow<Float> = _currentBitrateMbps.asStateFlow()

    private val _latencyMs = MutableStateFlow(0)
    val latencyMs: StateFlow<Int> = _latencyMs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var frameCount = 0
    private var bytesAccumulator = 0L
    private var lastMetricsCalcTime = System.currentTimeMillis()

    companion object {
        private const val TAG = "TvStreamClient"
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val RECONNECT_DELAY_MS = 2500L
    }

    fun connect(camera: DiscoveredCamera, pin: String, context: Context? = null) {
        reconnectJob?.cancel()
        reconnectJob = null
        userInitiatedDisconnect = false
        lastCamera = camera
        lastPin = pin
        lastContext = context?.applicationContext
        startSession(camera, pin, isReconnect = false)
    }

    private var lastContext: Context? = null

    private fun startSession(camera: DiscoveredCamera, pin: String, isReconnect: Boolean) {
        clientJob?.cancel()
        teardownTransportOnly()
        isRunning.set(true)
        _connectionState.value = if (isReconnect) TvClientState.RECONNECTING else TvClientState.CONNECTING
        _errorMessage.value = null

        clientJob = scope.launch {
            try {
                val tlsPort = camera.tlsPort
                val connectPort = tlsPort ?: camera.port
                val useTls = tlsPort != null
                val socket = FocalLanNetwork.connectTcp(
                    context = lastContext,
                    host = camera.host,
                    port = connectPort,
                    useTls = useTls,
                    timeoutMs = CONNECT_TIMEOUT_MS
                )
                activeSocket = socket
                socket.soTimeout = 12000

                val output = socket.getOutputStream()
                val rawInput = BufferedInputStream(socket.getInputStream())

                _connectionState.value = TvClientState.AUTHENTICATING

                val authMsg = "AUTH $pin\n".toByteArray(StandardCharsets.UTF_8)
                output.write(authMsg)
                output.flush()

                val responseLine = readLineFromStream(rawInput)
                if (responseLine == null || !responseLine.trim().startsWith("AUTH_OK")) {
                    _connectionState.value = TvClientState.AUTH_FAILED
                    _errorMessage.value = if (responseLine?.startsWith("AUTH_ERR") == true) {
                        "Invalid or expired pairing PIN"
                    } else {
                        "Invalid pairing PIN"
                    }
                    isRunning.set(false)
                    closeSocket()
                    return@launch
                }

                sessionKey = FocalSessionCrypto.deriveKey(pin)
                _connectionState.value = TvClientState.STREAMING
                videoDecoder.startIfSurfaceReady()
                audioPlayer.start()

                readPacketStream(rawInput)
            } catch (e: Exception) {
                if (isRunning.get() && !userInitiatedDisconnect) {
                    Log.e(TAG, "Connection error", e)
                    _connectionState.value = TvClientState.ERROR
                    _errorMessage.value = e.localizedMessage ?: "Failed to connect to camera"
                    scheduleReconnectIfNeeded()
                }
            } finally {
                if (!userInitiatedDisconnect && _connectionState.value == TvClientState.STREAMING) {
                    _connectionState.value = TvClientState.DISCONNECTED
                    _errorMessage.value = "Stream ended"
                    scheduleReconnectIfNeeded()
                }
                teardownTransportOnly()
            }
        }
    }

    private fun scheduleReconnectIfNeeded() {
        if (userInitiatedDisconnect || !autoReconnectEnabled) return
        val camera = lastCamera ?: return
        val pin = lastPin ?: return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            var attempt = 0
            while (isActive && !userInitiatedDisconnect && attempt < maxReconnectAttempts) {
                attempt++
                _connectionState.value = TvClientState.RECONNECTING
                _errorMessage.value = "Reconnecting ($attempt/$maxReconnectAttempts)…"
                delay(RECONNECT_DELAY_MS)
                if (userInitiatedDisconnect) return@launch
                startSession(camera, pin, isReconnect = true)
                clientJob?.join()
                if (_connectionState.value == TvClientState.STREAMING) return@launch
                if (_connectionState.value == TvClientState.AUTH_FAILED) return@launch
            }
            if (!userInitiatedDisconnect && _connectionState.value != TvClientState.STREAMING) {
                _connectionState.value = TvClientState.ERROR
                _errorMessage.value = "Could not reconnect to camera"
            }
        }
    }

    private fun readPacketStream(input: InputStream) {
        val foclInput = FoclPacketInputStream(input)

        while (isRunning.get()) {
            val frame = foclInput.readNextPayload() ?: break

            frameCount++
            bytesAccumulator += frame.payload.size + StreamPacket.HEADER_SIZE
            val now = System.currentTimeMillis()
            val elapsed = now - lastMetricsCalcTime
            if (elapsed >= 1000) {
                _currentFps.value = (frameCount * 1000f) / elapsed
                _currentBitrateMbps.value = (bytesAccumulator * 8f) / (elapsed * 1000f)
                frameCount = 0
                bytesAccumulator = 0L
                lastMetricsCalcTime = now
            }

            val packetType = PacketType.fromCode(frame.typeCode)
            val isKeyframe = (frame.flags.toInt() and StreamPacket.FLAG_KEYFRAME.toInt()) != 0
            val isConfig = (frame.flags.toInt() and StreamPacket.FLAG_CONFIG.toInt()) != 0
            val isEncrypted = (frame.flags.toInt() and StreamPacket.FLAG_ENCRYPTED.toInt()) != 0

            val payload = if (isEncrypted) {
                val key = sessionKey ?: continue
                FocalSessionCrypto.decrypt(key, frame.payload) ?: continue
            } else {
                frame.payload
            }

            when (packetType) {
                PacketType.VIDEO_NAL -> {
                    videoDecoder.feedH264Nal(payload, isKeyframe, frame.timestampUs, isConfig)
                }
                PacketType.AUDIO_RAW -> {
                    audioPlayer.playPcm(payload)
                }
                PacketType.HEARTBEAT -> {
                    _latencyMs.value = frame.timestampUs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
                else -> Unit
            }
        }
    }

    private fun readLineFromStream(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }

    fun disconnect() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        isRunning.set(false)
        clientJob?.cancel()
        clientJob = null
        teardownTransportOnly()
        videoDecoder.stop()
        audioPlayer.stop()
        _currentFps.value = 0f
        _currentBitrateMbps.value = 0f
        _latencyMs.value = 0
        sessionKey = null
        if (_connectionState.value != TvClientState.AUTH_FAILED) {
            _connectionState.value = TvClientState.IDLE
        }
        _errorMessage.value = null
    }

    private fun teardownTransportOnly() {
        isRunning.set(false)
        closeSocket()
    }

    private fun closeSocket() {
        try {
            activeSocket?.close()
        } catch (_: Exception) {
        } finally {
            activeSocket = null
        }
    }
}
