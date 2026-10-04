package com.example.transport

import com.example.data.model.HostConnectionMode
import com.example.data.model.StreamCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class TransportManager(
    private val pairingPinProvider: () -> String,
    val wifiPort: Int = 8080,
    val adbPort: Int = 8082
) {
    private val wifiTransport = WifiTransport(wifiPort, pairingPinProvider)
    private val adbTransport = AdbTransport(adbPort, pairingPinProvider)

    private val _connectedClientsCount = MutableStateFlow(0)
    val connectedClientsCount: StateFlow<Int> = _connectedClientsCount.asStateFlow()

    private val _measuredLatencyMs = MutableStateFlow(8)
    val measuredLatencyMs: StateFlow<Int> = _measuredLatencyMs.asStateFlow()

    private val _currentBitrateMbps = MutableStateFlow(4.8f)
    val currentBitrateMbps: StateFlow<Float> = _currentBitrateMbps.asStateFlow()

    private val bytesSentCounter = AtomicLong(0)
    private var lastBitrateTimestamp = System.currentTimeMillis()

    private val clientListener = object : TransportClientListener {
        override fun onClientConnected(clientId: String, address: String) {
            updateTotalClientCount()
        }

        override fun onClientAuthenticated(clientId: String) {
            updateTotalClientCount()
        }

        override fun onClientDisconnected(clientId: String) {
            updateTotalClientCount()
        }

        override fun onAuthChallengeFailed(clientId: String) {
            updateTotalClientCount()
        }
    }

    init {
        wifiTransport.setClientListener(clientListener)
        adbTransport.setClientListener(clientListener)
    }

    fun start(mode: HostConnectionMode) {
        when (mode) {
            HostConnectionMode.WIFI -> {
                wifiTransport.start()
            }
            HostConnectionMode.USB_ADB -> {
                adbTransport.start()
                // Keep wifi transport available for local endpoints
                wifiTransport.start()
            }
        }
        updateTotalClientCount()
    }

    fun stop() {
        wifiTransport.stop()
        adbTransport.stop()
        _connectedClientsCount.value = 0
    }

    fun isAnyRunning(): Boolean = wifiTransport.isRunning || adbTransport.isRunning

    fun broadcastVideoNal(
        codec: StreamCodec,
        isKeyframe: Boolean,
        isConfig: Boolean = false,
        timestampUs: Long,
        nalBytes: ByteArray
    ) {
        val packet = StreamPacket(
            codec = codec,
            type = PacketType.VIDEO_NAL,
            isKeyframe = isKeyframe,
            isConfig = isConfig,
            timestampUs = timestampUs,
            payload = nalBytes
        )
        broadcast(packet)
    }

    fun broadcastVideoNal(
        codec: StreamCodec,
        isKeyframe: Boolean,
        timestampUs: Long,
        nalBytes: ByteArray
    ) {
        broadcastVideoNal(codec, isKeyframe, false, timestampUs, nalBytes)
    }

    fun broadcastAudio(
        pcmOrAacBytes: ByteArray,
        timestampUs: Long
    ) {
        val packet = StreamPacket(
            codec = StreamCodec.PCM,
            type = PacketType.AUDIO_RAW,
            isKeyframe = false,
            isConfig = false,
            timestampUs = timestampUs,
            payload = pcmOrAacBytes
        )
        broadcast(packet)
    }

    fun broadcast(packet: StreamPacket) {
        wifiTransport.broadcastPacket(packet)
        adbTransport.broadcastPacket(packet)

        val totalBytes = bytesSentCounter.addAndGet(packet.payload.size.toLong())
        val now = System.currentTimeMillis()
        val elapsed = now - lastBitrateTimestamp
        if (elapsed >= 1000) {
            val mbps = (totalBytes * 8f) / (elapsed * 1000f)
            _currentBitrateMbps.value = (mbps * 10).toInt() / 10f
            bytesSentCounter.set(0)
            lastBitrateTimestamp = now
        }
    }

    fun updateMeasuredLatency(latencyMs: Int) {
        _measuredLatencyMs.value = latencyMs.coerceAtLeast(1)
    }

    private fun updateTotalClientCount() {
        _connectedClientsCount.value = wifiTransport.activeClientsCount + adbTransport.activeClientsCount
    }
}
