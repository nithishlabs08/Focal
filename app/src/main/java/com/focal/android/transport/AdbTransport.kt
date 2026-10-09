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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class AdbTransport(
    override val port: Int = 8082,
    private val pairingPinProvider: () -> String = { PairingManager.currentPin },
    private val pinValidator: ((String) -> Boolean)? = null
) : StreamTransport {

    override val name: String = "USB / ADB Loopback Transport"

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val clientPool = Executors.newCachedThreadPool().asCoroutineDispatcher()

    private val activeClients = ConcurrentHashMap<String, AdbClient>()
    private val clientCounter = AtomicInteger(0)
    private var clientListener: TransportClientListener? = null

    @Volatile
    override var isRunning: Boolean = false
        private set

    override val activeClientsCount: Int
        get() = activeClients.size

    data class AdbClient(
        val id: String,
        val socket: Socket,
        val outputStream: OutputStream,
        @Volatile var isAuthenticated: Boolean
    )

    override fun start() {
        if (isRunning) return
        try {
            // Bind strictly to 127.0.0.1 (loopback) to prevent external LAN access over the ADB port
            serverSocket = ServerSocket(port, 10, InetAddress.getByName("127.0.0.1")).apply {
                reuseAddress = true
            }
            isRunning = true

            serverJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        launch(clientPool) {
                            handleAdbClient(socket)
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
                        client.outputStream.write(encodedPacket)
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

    private fun handleAdbClient(socket: Socket) {
        val clientId = "adb_client_${clientCounter.incrementAndGet()}"
        clientListener?.onClientConnected(clientId, "127.0.0.1 (ADB)")

        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 15000

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val outputStream = socket.getOutputStream()

            val authLine = reader.readLine() ?: run {
                socket.close()
                return
            }

            if (authLine.startsWith("AUTH ")) {
                val candidatePin = authLine.substringAfter("AUTH ").trim()
                val isValid = if (pinValidator != null) {
                    pinValidator.invoke(candidatePin)
                } else {
                    candidatePin == pairingPinProvider()
                }
                if (isValid) {
                    outputStream.write("AUTH_OK ENC1\n".toByteArray(StandardCharsets.UTF_8))
                    outputStream.flush()

                    val client = AdbClient(clientId, socket, outputStream, isAuthenticated = true)
                    activeClients[clientId] = client
                    clientListener?.onClientAuthenticated(clientId)

                    while (isRunning && !socket.isClosed && socket.isConnected) {
                        reader.readLine() ?: break
                    }
                } else {
                    outputStream.write("AUTH_ERR Invalid PIN\n".toByteArray(StandardCharsets.UTF_8))
                    outputStream.flush()
                    clientListener?.onAuthChallengeFailed(clientId)
                    socket.close()
                }
            } else {
                // If connecting without auth header, close
                socket.close()
            }
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        } finally {
            activeClients.remove(clientId)
            clientListener?.onClientDisconnected(clientId)
        }
    }
}
