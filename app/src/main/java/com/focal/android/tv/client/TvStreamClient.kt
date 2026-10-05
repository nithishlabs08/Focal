package com.focal.android.tv.client

import android.util.Log
import com.focal.android.data.model.StreamCodec
import com.focal.android.transport.PacketType
import com.focal.android.transport.StreamPacket
import com.focal.android.tv.model.DiscoveredCamera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

enum class TvClientState {
    IDLE,
    CONNECTING,
    AUTHENTICATING,
    STREAMING,
    AUTH_FAILED,
    ERROR,
    DISCONNECTED
}

/**
 * Network client that connects to a Focal camera streamer, handles PIN authentication,
 * reads binary StreamPackets (H.264 and PCM audio), and computes live FPS/bitrate metrics.
 */
class TvStreamClient(
    private val videoDecoder: TvVideoDecoder,
    private val audioPlayer: TvAudioPlayer
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var clientJob: Job? = null
    private var pingJob: Job? = null

    private var activeSocket: Socket? = null
    private val isRunning = AtomicBoolean(false)

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
    }

    fun connect(camera: DiscoveredCamera, pin: String) {
        disconnect()
        isRunning.set(true)
        _connectionState.value = TvClientState.CONNECTING
        _errorMessage.value = null

        clientJob = scope.launch {
            try {
                val socket = Socket()
                activeSocket = socket
                socket.tcpNoDelay = true
                socket.soTimeout = 12000
                socket.connect(InetSocketAddress(camera.host, camera.port), CONNECT_TIMEOUT_MS)

                val output = socket.getOutputStream()
                val rawInput = BufferedInputStream(socket.getInputStream())

                _connectionState.value = TvClientState.AUTHENTICATING

                // 1. Send AUTH handshake
                val authMsg = "AUTH $pin\n".toByteArray(StandardCharsets.UTF_8)
                output.write(authMsg)
                output.flush()

                // Read line response for auth
                val responseLine = readLineFromStream(rawInput)
                if (responseLine == null || !responseLine.startsWith("AUTH_OK")) {
                    _connectionState.value = TvClientState.AUTH_FAILED
                    _errorMessage.value = "Invalid pairing PIN"
                    disconnect()
                    return@launch
                }

                // Auth success
                _connectionState.value = TvClientState.STREAMING
                videoDecoder.start()
                audioPlayer.start()

                // Start ping loop for latency tracking
                startPingLoop(output)

                // 2. Read binary StreamPacket loop
                readPacketStream(rawInput)

            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Connection error", e)
                    _connectionState.value = TvClientState.ERROR
                    _errorMessage.value = e.localizedMessage ?: "Failed to connect to camera"
                }
            } finally {
                disconnect()
            }
        }
    }

    private fun readPacketStream(input: InputStream) {
        val headerBuffer = ByteArray(StreamPacket.HEADER_SIZE)

        while (isActive && isRunning.get()) {
            // Read 20-byte header
            if (!readFully(input, headerBuffer, 0, StreamPacket.HEADER_SIZE)) break

            // Verify Magic 'FOCL'
            if (headerBuffer[0] != 'F'.code.toByte() ||
                headerBuffer[1] != 'O'.code.toByte() ||
                headerBuffer[2] != 'C'.code.toByte() ||
                headerBuffer[3] != 'L'.code.toByte()
            ) {
                // If not magic, possibly plain text line or heartbeat response
                continue
            }

            val buf = ByteBuffer.wrap(headerBuffer).order(ByteOrder.BIG_ENDIAN)
            buf.position(4)
            val version = buf.get()
            val codecByte = buf.get()
            val typeCode = buf.get()
            val flags = buf.get()
            val timestampUs = buf.long
            val payloadLen = buf.int

            if (payloadLen < 0 || payloadLen > 5_000_000) {
                // Invalid payload size, drop connection to avoid memory crash
                break
            }

            val payload = ByteArray(payloadLen)
            if (!readFully(input, payload, 0, payloadLen)) break

            // Update stats
            frameCount++
            bytesAccumulator += payloadLen + StreamPacket.HEADER_SIZE
            val now = System.currentTimeMillis()
            val elapsed = now - lastMetricsCalcTime
            if (elapsed >= 1000) {
                _currentFps.value = (frameCount * 1000f) / elapsed
                _currentBitrateMbps.value = (bytesAccumulator * 8f) / (elapsed * 1000f)
                frameCount = 0
                bytesAccumulator = 0L
                lastMetricsCalcTime = now
            }

            val packetType = PacketType.fromCode(typeCode)
            val isKeyframe = (flags.toInt() and StreamPacket.FLAG_KEYFRAME.toInt()) != 0

            when (packetType) {
                PacketType.VIDEO_NAL -> {
                    videoDecoder.feedH264Nal(payload, isKeyframe, timestampUs)
                }
                PacketType.AUDIO_RAW -> {
                    audioPlayer.playPcm(payload)
                }
                PacketType.HEARTBEAT -> {
                    // Heartbeat ack
                }
                else -> {
                    // Metadata or auth packet
                }
            }
        }
    }

    private fun startPingLoop(output: OutputStream) {
        pingJob = scope.launch {
            val pingBytes = "PING\n".toByteArray(StandardCharsets.UTF_8)
            while (isActive && isRunning.get()) {
                kotlinx.coroutines.delay(3000)
                try {
                    val start = System.currentTimeMillis()
                    output.write(pingBytes)
                    output.flush()
                    _latencyMs.value = (System.currentTimeMillis() - start).toInt().coerceAtLeast(4)
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Boolean {
        var bytesRead = 0
        while (bytesRead < length) {
            val count = input.read(buffer, offset + bytesRead, length - bytesRead)
            if (count < 0) return false
            bytesRead += count
        }
        return true
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
        isRunning.set(false)
        pingJob?.cancel()
        clientJob?.cancel()
        pingJob = null
        clientJob = null

        try {
            activeSocket?.close()
        } catch (_: Exception) {
        } finally {
            activeSocket = null
        }

        videoDecoder.stop()
        audioPlayer.stop()

        _currentFps.value = 0f
        _currentBitrateMbps.value = 0f
        if (_connectionState.value == TvClientState.STREAMING || _connectionState.value == TvClientState.CONNECTING) {
            _connectionState.value = TvClientState.DISCONNECTED
        }
    }
}
