package com.focal.android.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MjpegHttpServer(
    val port: Int = 8080
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val clientPool = Executors.newCachedThreadPool().asCoroutineDispatcher()

    private val activeClients = ConcurrentHashMap<String, Socket>()
    private val clientCounter = AtomicInteger(0)

    @Volatile
    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        try {
            serverSocket = ServerSocket(port).apply {
                reuseAddress = true
            }
            isRunning = true

            serverJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        launch(clientPool) {
                            handleClient(socket)
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

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        serverJob?.cancel()

        activeClients.values.forEach { socket ->
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
        activeClients.clear()
        CameraStreamBroadcaster.setClientCount(0)
    }

    private fun handleClient(socket: Socket) {
        val clientId = "client_${clientCounter.incrementAndGet()}"
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 15000

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            // Drain headers
            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) {
                headerLine = reader.readLine()
            }

            val output = socket.getOutputStream()

            when {
                path == "/stream.mjpg" || path == "/video" || path == "/stream" -> {
                    serveMjpegStream(socket, clientId, output)
                }

                path == "/snapshot" || path == "/frame.jpg" -> {
                    serveSnapshot(output)
                    socket.close()
                }

                path == "/status" -> {
                    serveJsonStatus(output)
                    socket.close()
                }

                else -> {
                    serveHtmlDashboard(output)
                    socket.close()
                }
            }
        } catch (_: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        } finally {
            activeClients.remove(clientId)
            CameraStreamBroadcaster.setClientCount(activeClients.size)
        }
    }

    private fun serveMjpegStream(socket: Socket, clientId: String, output: OutputStream) {
        activeClients[clientId] = socket
        CameraStreamBroadcaster.setClientCount(activeClients.size)

        val header = "HTTP/1.1 200 OK\r\n" +
                "Server: Focal-Webcam/1.0\r\n" +
                "Connection: close\r\n" +
                "Cache-Control: no-cache, no-store, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                "Pragma: no-cache\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=--focalframe\r\n\r\n"

        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        output.flush()

        val lock = Object()
        var nextFrameToSend: ByteArray? = CameraStreamBroadcaster.getLatestFrame()

        val listener: (ByteArray) -> Unit = { frame ->
            synchronized(lock) {
                nextFrameToSend = frame
                lock.notifyAll()
            }
        }

        CameraStreamBroadcaster.addFrameListener(listener)

        try {
            while (isRunning && !socket.isClosed && socket.isConnected) {
                val frame: ByteArray
                synchronized(lock) {
                    while (nextFrameToSend == null && isRunning && !socket.isClosed) {
                        lock.wait(500)
                    }
                    frame = nextFrameToSend ?: CameraStreamBroadcaster.getLatestFrame()
                    nextFrameToSend = null
                }

                val frameHeader = "--focalframe\r\n" +
                        "Content-Type: image/jpeg\r\n" +
                        "Content-Length: ${frame.size}\r\n\r\n"

                output.write(frameHeader.toByteArray(StandardCharsets.US_ASCII))
                output.write(frame)
                output.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
                output.flush()
            }
        } catch (_: Exception) {
        } finally {
            CameraStreamBroadcaster.removeFrameListener(listener)
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun serveSnapshot(output: OutputStream) {
        val frame = CameraStreamBroadcaster.getLatestFrame()
        val header = "HTTP/1.1 200 OK\r\n" +
                "Server: Focal-Webcam/1.0\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Content-Length: ${frame.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        output.write(frame)
        output.flush()
    }

    private fun serveJsonStatus(output: OutputStream) {
        val fps = CameraStreamBroadcaster.streamFps.value
        val clients = activeClients.size
        val json = """
            {
                "status": "online",
                "app": "Focal Local Webcam",
                "port": $port,
                "clients": $clients,
                "fps": $fps,
                "lossless": true,
                "codec": "MJPEG Hardware"
            }
        """.trimIndent()

        val bodyBytes = json.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Server: Focal-Webcam/1.0\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        output.write(bodyBytes)
        output.flush()
    }

    private fun serveHtmlDashboard(output: OutputStream) {
        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Focal — Local Webcam Stream</title>
  <style>
    body {
      margin: 0;
      background: #0f1218;
      color: #e2e5ec;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 24px;
    }
    header {
      width: 100%;
      max-width: 960px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }
    .badge {
      background: rgba(0, 103, 57, 0.2);
      color: #74db99;
      border: 1px solid #006739;
      padding: 4px 12px;
      border-radius: 9999px;
      font-size: 13px;
      font-weight: 600;
    }
    .stream-box {
      width: 100%;
      max-width: 960px;
      background: #181c24;
      border-radius: 16px;
      overflow: hidden;
      box-shadow: 0 8px 32px rgba(0,0,0,0.5);
      position: relative;
    }
    .stream-box img {
      width: 100%;
      height: auto;
      display: block;
      background: #000;
    }
    .terminal-box {
      width: 100%;
      max-width: 960px;
      background: #181c24;
      border-radius: 12px;
      padding: 16px 20px;
      margin-top: 20px;
      border: 1px solid #2d3340;
    }
    code {
      display: block;
      background: #0b0d12;
      color: #a5d6ff;
      padding: 12px;
      border-radius: 8px;
      font-family: monospace;
      font-size: 13px;
      overflow-x: auto;
      margin-top: 8px;
    }
  </style>
</head>
<body>
  <header>
    <h2>📷 Focal — Live Desktop Receiver</h2>
    <span class="badge">● 100% Local Stream</span>
  </header>
  <div class="stream-box">
    <img src="/stream.mjpg" alt="Focal Live Camera Feed">
  </div>
  <div class="terminal-box">
    <h3>Linux Virtual Webcam (v4l2loopback)</h3>
    <p>Pipe this stream directly into your virtual camera device for Google Meet, Zoom, OBS, and Discord:</p>
    <code>ffmpeg -i http://localhost:$port/stream.mjpg -vcodec rawvideo -pix_fmt yuv420p -f v4l2 /dev/video2</code>
  </div>
</body>
</html>
        """.trimIndent()

        val bodyBytes = html.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Server: Focal-Webcam/1.0\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        output.write(bodyBytes)
        output.flush()
    }
}
