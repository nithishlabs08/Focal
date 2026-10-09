package com.focal.android.ui.receive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focal.android.tv.client.TvAudioPlayer
import com.focal.android.tv.client.TvClientState
import com.focal.android.tv.client.TvStreamClient
import com.focal.android.tv.client.TvVideoDecoder
import com.focal.android.tv.discovery.TvDiscoveryManager
import com.focal.android.tv.model.DiscoveredCamera
import com.focal.android.tv.ui.TvDeviceCard
import com.focal.android.tv.ui.TvPinDialog
import com.focal.android.tv.ui.TvPlayerView

@Composable
fun MobileReceiveScreen(
    discoveryManager: TvDiscoveryManager,
    streamClient: TvStreamClient,
    videoDecoder: TvVideoDecoder,
    audioPlayer: TvAudioPlayer,
    isPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val discoveredCameras by discoveryManager.discoveredCameras.collectAsState()
    val isScanning by discoveryManager.isScanning.collectAsState()
    val discoveryError by discoveryManager.discoveryError.collectAsState()

    val connectionState by streamClient.connectionState.collectAsState()
    val fps by streamClient.currentFps.collectAsState()
    val bitrate by streamClient.currentBitrateMbps.collectAsState()
    val latency by streamClient.latencyMs.collectAsState()
    val errorMessage by streamClient.errorMessage.collectAsState()

    var selectedCameraForPin by remember { mutableStateOf<DiscoveredCamera?>(null) }
    var activeStreamingCamera by remember { mutableStateOf<DiscoveredCamera?>(null) }
    var showManualIp by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("8080") }

    DisposableEffect(Unit) {
        discoveryManager.startDiscovery()
        onDispose {
            discoveryManager.stopDiscovery()
            streamClient.disconnect()
        }
    }

    LaunchedEffect(connectionState) {
        ReceivePlaybackCoordinator.isReceivingStream = connectionState == TvClientState.STREAMING
        if (connectionState == TvClientState.AUTH_FAILED) {
            activeStreamingCamera?.let { selectedCameraForPin = it }
            activeStreamingCamera = null
        }
    }

    if (connectionState == TvClientState.STREAMING && activeStreamingCamera != null) {
        TvPlayerView(
            camera = activeStreamingCamera!!,
            fps = fps,
            bitrateMbps = bitrate,
            latencyMs = latency,
            videoDecoder = videoDecoder,
            audioPlayer = audioPlayer,
            isPipMode = isPipMode,
            onEnterPip = onEnterPip,
            onDisconnect = {
                streamClient.disconnect()
                activeStreamingCamera = null
            },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Receive stream",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Discover senders on the same Wi‑Fi or connect by IP. While watching, tap Picture in Picture or leave the app to keep video on screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isScanning) {
            RowScanning()
            Spacer(modifier = Modifier.height(8.dp))
        }
        discoveryError?.let {
            ErrorBanner(it)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (errorMessage != null) {
            ErrorBanner(errorMessage!!)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (connectionState == TvClientState.CONNECTING ||
            connectionState == TvClientState.AUTHENTICATING ||
            connectionState == TvClientState.RECONNECTING
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        TextButton(onClick = { showManualIp = !showManualIp }) {
            Text(if (showManualIp) "Hide manual IP" else "Connect by IP")
        }
        if (showManualIp) {
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                label = { Text("IP address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = manualPort,
                onValueChange = { manualPort = it },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            TextButton(
                onClick = {
                    if (manualIp.isNotBlank()) {
                        discoveryManager.addManualCamera(
                            name = "Manual (${manualIp.trim()})",
                            host = manualIp.trim(),
                            port = manualPort.toIntOrNull() ?: 8080
                        )
                    }
                }
            ) {
                Text("Add device")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (discoveredCameras.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No senders found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(discoveredCameras, key = { it.id }) { camera ->
                    TvDeviceCard(camera = camera, onClick = { selectedCameraForPin = camera })
                }
            }
        }
    }

    if (selectedCameraForPin != null) {
        TvPinDialog(
            camera = selectedCameraForPin!!,
            onConnect = { pin ->
                val cam = selectedCameraForPin!!
                activeStreamingCamera = cam
                selectedCameraForPin = null
                streamClient.connect(cam, pin)
            },
            onDismiss = { selectedCameraForPin = null }
        )
    }
}

@Composable
private fun RowScanning() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text("Scanning…", fontSize = 12.sp)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp)
    ) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
    }
}
