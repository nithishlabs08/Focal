package com.focal.android.transport

import android.content.Context
import com.focal.android.data.model.HostConnectionMode
import com.focal.android.data.model.StreamCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.SecretKey
import java.util.concurrent.atomic.AtomicLong

class TransportManager(
    private val pairingPinProvider: () -> String = { PairingManager.currentPin },
    private val pinValidator: ((String) -> Boolean)? = { PairingManager.isPinValid(it) },
    val wifiPort: Int = 8080,
    val adbPort: Int = 8082
) {
    private val wifiTransport = WifiTransport(wifiPort, pairingPinProvider, pinValidator)
    private val tlsWifiTransport = WifiTransport(FocalLanTls.DEFAULT_TLS_PORT, pairingPinProvider, pinValidator)
    private val adbTransport = AdbTransport(adbPort, pairingPinProvider, pinValidator)

    var tlsCertificateFingerprint: String? = null
        private set

    private val _connectedClientsCount = MutableStateFlow(0)
    val connectedClientsCount: StateFlow<Int> = _connectedClientsCount.asStateFlow()

    private val _measuredLatencyMs = MutableStateFlow(8)
    val measuredLatencyMs: StateFlow<Int> = _measuredLatencyMs.asStateFlow()

    private val _currentBitrateMbps = MutableStateFlow(4.8f)
    val currentBitrateMbps: StateFlow<Float> = _currentBitrateMbps.asStateFlow()

    private val bytesSentCounter = AtomicLong(0)
    private var lastBitrateTimestamp = System.currentTimeMillis()

    /** When true, FOCL payloads are AES-GCM encrypted with a key derived from the pairing PIN. */
    var encryptFoclPayloads: Boolean = true

    @Volatile
    private var sessionKey: SecretKey? = null

    fun refreshSessionKey() {
        sessionKey = FocalSessionCrypto.deriveKey(pairingPinProvider())
    }

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
        tlsWifiTransport.setClientListener(clientListener)
        adbTransport.setClientListener(clientListener)
    }

    fun start(mode: HostConnectionMode, appContext: Context? = null) {
        when (mode) {
            HostConnectionMode.WIFI -> {
                wifiTransport.start()
                startTlsIfAvailable(appContext)
            }
            HostConnectionMode.USB_ADB -> {
                adbTransport.start()
                wifiTransport.start()
                startTlsIfAvailable(appContext)
            }
        }
        updateTotalClientCount()
    }

    private fun startTlsIfAvailable(appContext: Context?) {
        if (appContext == null) return
        val material = runCatching { FocalLanTls.loadServerMaterial(appContext) }.getOrNull()
        if (material != null) {
            tlsCertificateFingerprint = material.certificateFingerprintSha256Base64
            tlsWifiTransport.start(FocalLanTls.serverSocketFactory(material))
        }
    }

    fun stop() {
        wifiTransport.stop()
        tlsWifiTransport.stop()
        adbTransport.stop()
        wifiTransport.setClientListener(null)
        tlsWifiTransport.setClientListener(null)
        adbTransport.setClientListener(null)
        tlsCertificateFingerprint = null
        _connectedClientsCount.value = 0
    }

    fun isAnyRunning(): Boolean =
        wifiTransport.isRunning || tlsWifiTransport.isRunning || adbTransport.isRunning

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
        val outbound = maybeEncrypt(packet)
        wifiTransport.broadcastPacket(outbound)
        tlsWifiTransport.broadcastPacket(outbound)
        adbTransport.broadcastPacket(outbound)

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
        _connectedClientsCount.value =
            wifiTransport.activeClientsCount + tlsWifiTransport.activeClientsCount + adbTransport.activeClientsCount
    }

    fun broadcastHeartbeat(timestampUs: Long = System.nanoTime() / 1000) {
        broadcast(
            StreamPacket(
                codec = StreamCodec.PCM,
                type = PacketType.HEARTBEAT,
                timestampUs = timestampUs,
                payload = byteArrayOf()
            )
        )
    }

    private fun maybeEncrypt(packet: StreamPacket): StreamPacket {
        if (!encryptFoclPayloads || packet.type == PacketType.HEARTBEAT || packet.isEncrypted) {
            return packet
        }
        val key = sessionKey ?: FocalSessionCrypto.deriveKey(pairingPinProvider()).also { sessionKey = it }
        val encrypted = FocalSessionCrypto.encrypt(key, packet.payload)
        return StreamPacket(
            codec = packet.codec,
            type = packet.type,
            isKeyframe = packet.isKeyframe,
            isConfig = packet.isConfig,
            isEncrypted = true,
            timestampUs = packet.timestampUs,
            payload = encrypted
        )
    }
}
