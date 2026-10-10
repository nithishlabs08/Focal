package com.focal.android.tv.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.focal.android.tv.model.TvRootMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.focal.android.tv.client.TvAudioPlayer
import com.focal.android.tv.client.TvClientState
import com.focal.android.tv.client.TvStreamClient
import com.focal.android.tv.client.TvVideoDecoder
import com.focal.android.tv.discovery.TvDiscoveryManager
import com.focal.android.tv.model.DiscoveredCamera
import com.focal.android.ui.receive.ReceivePlaybackCoordinator
import com.focal.android.util.NetworkUtils

@Composable
fun TvMainScreen(
    discoveryManager: TvDiscoveryManager,
    streamClient: TvStreamClient,
    videoDecoder: TvVideoDecoder,
    audioPlayer: TvAudioPlayer,
    isPipMode: Boolean,
    onEnterPip: () -> Unit,
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
    var showManualIpDialog by remember { mutableStateOf(false) }
    var rootMode by rememberSaveable { mutableStateOf(TvRootMode.RECEIVE) }
    val appContext = LocalContext.current.applicationContext
    val vpnBlocksLan = remember(appContext) {
        NetworkUtils.isVpnLikelyBlockingLan(appContext)
    }

    LaunchedEffect(rootMode) {
        if (rootMode == TvRootMode.RECEIVE) {
            discoveryManager.startDiscovery()
        } else {
            discoveryManager.stopDiscovery()
            streamClient.disconnect()
        }
    }

    DisposableEffect(Unit) {
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
            modifier = modifier
        )
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            TvRootModePicker(
                selected = rootMode,
                onSelected = { rootMode = it },
                enabled = connectionState != TvClientState.CONNECTING &&
                    connectionState != TvClientState.AUTHENTICATING &&
                    connectionState != TvClientState.RECONNECTING
            )

            when (rootMode) {
                TvRootMode.SHARE -> TvHostScreen(modifier = Modifier.fillMaxSize())
                TvRootMode.RECEIVE -> TvReceiveBrowseContent(
                    modifier = Modifier.fillMaxSize(),
                    isScanning = isScanning,
                    discoveryError = discoveryError,
                    connectionState = connectionState,
                    errorMessage = errorMessage,
                    discoveredCameras = discoveredCameras,
                    onConnectByIp = { showManualIpDialog = true },
                    onRefreshDiscovery = {
                        discoveryManager.stopDiscovery()
                        discoveryManager.startDiscovery()
                    },
                    onCameraSelected = { selectedCameraForPin = it },
                    vpnBlocksLan = vpnBlocksLan
                )
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
                streamClient.connect(cam, pin, appContext)
            },
            onDismiss = { selectedCameraForPin = null }
        )
    }

    if (showManualIpDialog) {
        ManualIpDialog(
            onAdd = { ip, port ->
                discoveryManager.addManualCamera(name = "Manual sender ($ip)", host = ip, port = port)
                showManualIpDialog = false
            },
            onDismiss = { showManualIpDialog = false }
        )
    }
}

@Composable
private fun TvReceiveBrowseContent(
    isScanning: Boolean,
    discoveryError: String?,
    connectionState: TvClientState,
    errorMessage: String?,
    discoveredCameras: List<DiscoveredCamera>,
    onConnectByIp: () -> Unit,
    onRefreshDiscovery: () -> Unit,
    onCameraSelected: (DiscoveredCamera) -> Unit,
    vpnBlocksLan: Boolean = false,
    modifier: Modifier = Modifier
) {
    TvBrowseScaffold(
        modifier = modifier,
        header = {
            TvResponsiveHeader(
                branding = { TvBrowseBranding() },
                actions = {
                    TvBrowseToolbar(
                        isScanning = isScanning,
                        onConnectByIp = onConnectByIp,
                        onRefresh = onRefreshDiscovery
                    )
                }
            )
        }
    ) {
        TvBrowseStatusSection(
            discoveryError = discoveryError,
            connectionState = connectionState,
            errorMessage = errorMessage,
            vpnBlocksLan = vpnBlocksLan
        )

        Text(
            text = "AVAILABLE SENDERS ON WI‑FI",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        TvSenderGrid(
            cameras = discoveredCameras,
            onCameraSelected = onCameraSelected,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun TvBrowseBranding() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "Focal TV",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Watch a Focal sender on your big screen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TvBrowseToolbar(
    isScanning: Boolean,
    onConnectByIp: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isScanning) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Scanning…",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Button(
            onClick = onConnectByIp,
            modifier = Modifier
                .height(44.dp)
                .tvFocusable(shape = RoundedCornerShape(22.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(22.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Connect by IP", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(44.dp)
                .tvFocusable(shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh discovery",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TvBrowseStatusSection(
    discoveryError: String?,
    connectionState: TvClientState,
    errorMessage: String?,
    vpnBlocksLan: Boolean = false
) {
    if (vpnBlocksLan) {
        TvStatusBanner(
            text = "VPN is on — local streaming may fail. Turn off VPN or allow LAN access in your VPN app."
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    if (discoveryError != null) {
        TvStatusBanner(text = discoveryError)
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (connectionState == TvClientState.AUTH_FAILED && errorMessage != null) {
        TvStatusBanner(text = "Pairing failed: $errorMessage")
        Spacer(modifier = Modifier.height(12.dp))
    } else if (errorMessage != null && connectionState != TvClientState.AUTH_FAILED) {
        TvStatusBanner(text = "Connection error: $errorMessage")
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (connectionState == TvClientState.CONNECTING ||
        connectionState == TvClientState.AUTHENTICATING ||
        connectionState == TvClientState.RECONNECTING
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = when (connectionState) {
                        TvClientState.AUTHENTICATING -> "Entering pairing PIN on sender…"
                        TvClientState.RECONNECTING -> "Reconnecting to sender…"
                        else -> "Connecting to sender…"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TvStatusBanner(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(14.dp)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TvSenderGrid(
    cameras: List<DiscoveredCamera>,
    onCameraSelected: (DiscoveredCamera) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cameras.isEmpty()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No senders found",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Open Focal on your phone, start streaming, and stay on the same Wi‑Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = TvLayout.senderCardMinWidth),
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(cameras, key = { it.id }) { camera ->
                TvDeviceCard(
                    camera = camera,
                    onClick = { onCameraSelected(camera) }
                )
            }
        }
    }
}

@Composable
private fun ManualIpDialog(
    onAdd: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var ip by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("8080") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.width(480.dp)
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = "Connect by IP address",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP address (e.g. 192.168.1.50)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusable(shape = RoundedCornerShape(12.dp)),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusable(shape = RoundedCornerShape(12.dp)),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.tvFocusable(shape = RoundedCornerShape(20.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (ip.isNotBlank()) {
                                onAdd(ip.trim(), portText.toIntOrNull() ?: 8080)
                            }
                        },
                        modifier = Modifier.tvFocusable(shape = RoundedCornerShape(20.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Add sender", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
