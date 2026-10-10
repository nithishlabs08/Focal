package com.focal.android.ui.receive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focal.android.tv.client.TvAudioPlayer
import com.focal.android.tv.client.TvClientState
import com.focal.android.tv.client.TvStreamClient
import com.focal.android.tv.client.TvVideoDecoder
import com.focal.android.tv.discovery.TvDiscoveryManager
import com.focal.android.tv.model.DiscoveredCamera
import com.focal.android.tv.ui.TvPinDialog
import com.focal.android.tv.ui.TvPlayerView
import com.focal.android.ui.components.FocalPinDialog
import com.focal.android.ui.components.MobileSenderCard
import com.focal.android.transport.PairingManager
import com.focal.android.util.NetworkUtils

@Composable
fun MobileReceiveScreen(
    discoveryManager: TvDiscoveryManager,
    streamClient: TvStreamClient,
    videoDecoder: TvVideoDecoder,
    audioPlayer: TvAudioPlayer,
    deviceDisplayName: String,
    deviceIp: String,
    isPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    useTvPinDialog: Boolean = false,
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
    val appContext = LocalContext.current.applicationContext
    val vpnBlocksLan = remember(appContext) {
        NetworkUtils.isVpnLikelyBlockingLan(appContext)
    }

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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ReceiveHero(
                deviceName = deviceDisplayName,
                lanLine = if (deviceIp.isNotBlank()) "#$deviceIp" else "Offline"
            )
        }
        if (vpnBlocksLan) {
            item { InfoBanner("VPN is on — turn it off or allow LAN traffic in your VPN app.") }
        }
        discoveryError?.let { msg -> item { InfoBanner(msg, isError = true) } }
        errorMessage?.let { msg -> item { InfoBanner(msg, isError = true) } }
        if (connectionState == TvClientState.CONNECTING ||
            connectionState == TvClientState.AUTHENTICATING ||
            connectionState == TvClientState.RECONNECTING
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                    }
                    IconButton(
                        onClick = {
                            discoveryManager.stopDiscovery()
                            discoveryManager.startDiscovery()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Refresh nearby devices"
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { showManualIp = !showManualIp }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showManualIp) "Hide manual connection" else "Connect by IP")
            }
        }
        if (showManualIp) {
            item {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    label = { Text("IP address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = manualPort,
                    onValueChange = { manualPort = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                TextButton(
                    onClick = {
                        if (manualIp.isNotBlank()) {
                            discoveryManager.addManualCamera(
                                name = manualIp.trim(),
                                host = manualIp.trim(),
                                port = manualPort.toIntOrNull() ?: 8080
                            )
                        }
                    }
                ) {
                    Text("Add sender")
                }
            }
        }
        if (discoveredCameras.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No senders yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Start streaming on another device (Focal → Send), same Wi‑Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(discoveredCameras, key = { it.id }) { camera ->
                MobileSenderCard(camera = camera, onClick = { selectedCameraForPin = camera })
            }
        }
    }

    selectedCameraForPin?.let { camera ->
        val pinTitle = "Connect to ${camera.name}"
        val pinSubtitle = "Enter the ${PairingManager.PIN_LENGTH}-digit PIN on the sender"
        if (useTvPinDialog) {
            TvPinDialog(
                camera = camera,
                onConnect = { pin ->
                    activeStreamingCamera = camera
                    selectedCameraForPin = null
                    streamClient.connect(camera, pin, appContext)
                },
                onDismiss = { selectedCameraForPin = null }
            )
        } else {
            FocalPinDialog(
                title = pinTitle,
                subtitle = pinSubtitle,
                onConnect = { pin ->
                    activeStreamingCamera = camera
                    selectedCameraForPin = null
                    streamClient.connect(camera, pin, appContext)
                },
                onDismiss = { selectedCameraForPin = null }
            )
        }
    }
}

@Composable
private fun ReceiveHero(deviceName: String, lanLine: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = deviceName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = lanLine,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Receive",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun InfoBanner(message: String, isError: Boolean = false) {
    val colors = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    }
    val onColors = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = message,
        color = onColors,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors)
            .padding(12.dp)
    )
}
