package com.focal.android.transport

import com.focal.android.data.model.StreamCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLServerSocketFactory
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class WifiTransport(
    override val port: Int = 8080,
    private val pairingPinProvider: () -> String = { PairingManager.currentPin },
    private val pinValidator: ((String) -> Boolean)? = null
) : StreamTransport {

    override val name: String = "Wi-Fi Local Transport"

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val clientPool = Executors.newCachedThreadPool().asCoroutineDispatcher()

    private val activeClients = ConcurrentHashMap<String, AuthenticatedClient>()
    private val clientCounter = AtomicInteger(0)
    private var clientListener: TransportClientListener? = null

    @Volatile
    override var isRunning: Boolean = false
        private set

    override val activeClientsCount: Int
        get() = activeClients.size

    data class AuthenticatedClient(
        val id: String,
        val socket: Socket,
        val outputStream: OutputStream,
        @Volatile var isAuthenticated: Boolean,
        val isRawPacketProtocol: Boolean
    )

    override fun start() {
        start(null)
    }

    fun start(tlsServerSocketFactory: SSLServerSocketFactory?) {
        if (isRunning) return
        try {
            serverSocket = if (tlsServerSocketFactory != null) {
                tlsServerSocketFactory.createServerSocket(port)
            } else {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
            }
            isRunning = true

            serverJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        launch(clientPool) {
                            handleIncomingConnection(socket)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (_: Exception) {
            isRunning = false
        }
    }

    override fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        serverJob?.cancel()

        activeClients.values.forEach { client ->
            try {
                client.socket.close()
            } catch (_: Exception) {
            }
        }
        activeClients.clear()
    }

    override fun setClientListener(listener: TransportClientListener?) {
        this.clientListener = listener
    }

    override fun broadcastPacket(packet: StreamPacket) {
        if (!isRunning || activeClients.isEmpty()) return

        val encodedPacket = StreamPacket.encode(packet)
        val deadClients = mutableListOf<String>()

        activeClients.forEach { (clientId, client) ->
            if (client.isAuthenticated) {
                try {
                    synchronized(client.outputStream) {
                        if (client.isRawPacketProtocol) {
                            client.outputStream.write(encodedPacket)
                        } else if (packet.codec == StreamCodec.H264 && packet.type == PacketType.VIDEO_NAL) {
                            // Raw H.264 Annex-B stream for HTTP /stream.h264 clients
                            client.outputStream.write(packet.payload)
                        }
                        client.outputStream.flush()
                    }
                } catch (_: Exception) {
                    deadClients.add(clientId)
                }
            }
        }

        deadClients.forEach { clientId ->
            activeClients.remove(clientId)?.let { client ->
                try { client.socket.close() } catch (_: Exception) {}
                clientListener?.onClientDisconnected(clientId)
            }
        }
    }

    private fun isPinMatching(candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        return if (pinValidator != null) {
            pinValidator.invoke(candidate)
        } else {
            candidate == pairingPinProvider()
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        val clientId = "wifi_client_${clientCounter.incrementAndGet()}"
        val remoteAddress = socket.inetAddress?.hostAddress ?: "unknown"
        clientListener?.onClientConnected(clientId, remoteAddress)

        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 15000

            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))

            val initialLine = reader.readLine() ?: run {
                socket.close()
                return
            }

            // Case A: Focal Desktop Bridge Binary Connection with Pairing PIN
            if (initialLine.startsWith("AUTH ")) {
                val candidatePin = initialLine.substringAfter("AUTH ").trim()
                if (isPinMatching(candidatePin)) {
                    outputStream.write("AUTH_OK ENC1\n".toByteArray(StandardCharsets.UTF_8))
                    outputStream.flush()

                    val client = AuthenticatedClient(clientId, socket, outputStream, isAuthenticated = true, isRawPacketProtocol = true)
                    activeClients[clientId] = client
                    clientListener?.onClientAuthenticated(clientId)

                    // Keep-alive loop reading heartbeat
                    // Read keep-alive lines only; do not write text responses on this socket —
                    // FOCL binary packets share the same OutputStream and interleaved PONG
                    // lines break the TV client's packet parser.
                    while (isRunning && !socket.isClosed && socket.isConnected) {
                        val line = reader.readLine() ?: break
                        if (line == "PING") {
                            // Consumed; client may use FOCL HEARTBEAT for latency later.
                        }
                    }
                } else {
                    outputStream.write("AUTH_ERR Invalid PIN\n".toByteArray(StandardCharsets.UTF_8))
                    outputStream.flush()
                    clientListener?.onAuthChallengeFailed(clientId)
                    socket.close()
                }
                return
            }

            // Case B: HTTP Request (stream.h264, status, etc.)
            val parts = initialLine.split(" ")
            if (parts.size >= 2) {
                val fullPath = parts[1]
                val path = fullPath.substringBefore("?")

                // Read all HTTP headers
                val headers = mutableMapOf<String, String>()
                var headerLine = reader.readLine()
                while (!headerLine.isNullOrEmpty()) {
                    val colonIdx = headerLine.indexOf(':')
                    if (colonIdx > 0) {
                        val k = headerLine.substring(0, colonIdx).trim().lowercase()
                        val v = headerLine.substring(colonIdx + 1).trim()
                        headers[k] = v
                    }
                    headerLine = reader.readLine()
                }

                // Authentication MUST come from request headers or binary handshake only.
                // Do NOT accept the pairing code in URL query parameters.
                val headerPin = headers["x-focal-pin"] ?: headers["authorization"]?.removePrefix("Bearer ")?.trim()
                val isAuthenticated = isPinMatching(headerPin)

                when {
                    path == "/stream.h264" || path == "/video.h264" || path == "/live" -> {
                        if (!isAuthenticated) {
                            val unauthorized = "HTTP/1.1 401 Unauthorized\r\n" +
                                    "Content-Type: text/plain; charset=utf-8\r\n" +
                                    "WWW-Authenticate: Bearer realm=\"Focal Webcam\"\r\n" +
                                    "Connection: close\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n\r\n" +
                                    "401 Unauthorized: Valid pairing PIN required via 'X-Focal-Pin' header or 'Authorization: Bearer <pin>'. Query parameter authentication is disabled for security.\n"
                            outputStream.write(unauthorized.toByteArray(StandardCharsets.UTF_8))
                            outputStream.flush()
                            clientListener?.onAuthChallengeFailed(clientId)
                            socket.close()
                            return
                        }

                        val responseHeader = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: video/h264\r\n" +
                                "Connection: close\r\n" +
                                "Access-Control-Allow-Origin: *\r\n\r\n"
                        outputStream.write(responseHeader.toByteArray(StandardCharsets.US_ASCII))
                        outputStream.flush()

                        val client = AuthenticatedClient(clientId, socket, outputStream, isAuthenticated = true, isRawPacketProtocol = false)
                        activeClients[clientId] = client
                        clientListener?.onClientAuthenticated(clientId)

                        // Hold connection until client disconnects
                        while (isRunning && !socket.isClosed && socket.isConnected) {
                            Thread.sleep(1000)
                        }
                    }

                    path == "/status" -> {
                        val json = """{"status":"online","transport":"wifi","clients":${activeClients.size},"primaryCodec":"H264","requiresAuth":true}"""
                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${json.length}\r\nAccess-Control-Allow-Origin: *\r\n\r\n$json"
                        outputStream.write(resp.toByteArray(StandardCharsets.UTF_8))
                        outputStream.flush()
                        socket.close()
                    }

                    else -> {
                        val html = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nAccess-Control-Allow-Origin: *\r\n\r\nFocal Local Webcam Ready\nAuthentication required for /stream.h264"
                        outputStream.write(html.toByteArray(StandardCharsets.UTF_8))
                        outputStream.flush()
                        socket.close()
                    }
                }
            }
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        } finally {
            activeClients.remove(clientId)
            clientListener?.onClientDisconnected(clientId)
        }
    }
}
