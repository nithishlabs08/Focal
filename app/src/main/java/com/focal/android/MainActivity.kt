package com.focal.android

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focal.android.data.model.HostConnectionMode
import com.focal.android.data.model.StreamMode
import com.focal.android.ui.components.CameraControlBar
import com.focal.android.ui.components.CameraViewfinder
import com.focal.android.ui.components.FocalTopBar
import com.focal.android.ui.components.ProfilePickerBottomSheet
import com.focal.android.ui.components.SensorPickerBottomSheet
import com.focal.android.ui.theme.FocalOnPrimaryFixed
import com.focal.android.ui.theme.FocalPrimaryFixed
import com.focal.android.ui.theme.FocalTheme
import com.focal.android.ui.viewmodel.FocalViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocalTheme {
                FocalApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocalApp(
    viewModel: FocalViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Permission Request Launcher (Camera & Microphone)
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        viewModel.setCameraPermissionGranted(cameraGranted)
        viewModel.setMicPermissionGranted(micGranted)
        viewModel.updateContext(context)
    }

    LaunchedEffect(Unit) {
        viewModel.updateContext(context)
        if (!uiState.isCameraPermissionGranted) {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    val sensorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Back handler: stop streaming if active, else finish
    BackHandler(enabled = uiState.isStreaming) {
        viewModel.stopStreaming(context)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Formatted stream duration: hh:mm:ss
    val hrs = uiState.streamElapsedSeconds / 3600
    val mins = (uiState.streamElapsedSeconds % 3600) / 60
    val secs = uiState.streamElapsedSeconds % 60
    val streamDurationText = "%02d:%02d:%02d".format(hrs, mins, secs)

    // Effective Stream URL
    val effectiveStreamUrl = if (uiState.connectionMode == HostConnectionMode.USB_ADB) {
        "http://localhost:${uiState.serverPort}/stream.h264"
    } else if (uiState.deviceIp.isNotBlank()) {
        "http://${uiState.deviceIp}:${uiState.serverPort}/stream.h264"
    } else {
        "http://localhost:${uiState.serverPort}/stream.h264"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            FocalTopBar(
                title = "Focal",
                subtitle = if (uiState.isStreaming) "Webcam Live • $streamDurationText" else "Webcam Ready",
                showBack = false,
                deviceIp = uiState.deviceIp
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ==========================================
            // 1. Camera Viewfinder (16:9 Clean Preview)
            // ==========================================
            CameraViewfinder(
                hasCameraPermission = uiState.isCameraPermissionGranted,
                isFrontCamera = uiState.selectedSensor.isFront,
                rotationDegrees = uiState.rotationDegrees,
                isLiveStreaming = uiState.isStreaming,
                showGridOverlay = uiState.showGridOverlay,
                onRequestPermission = {
                    permissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ==========================================
            // 2. Camera Controls Bar (Under Preview)
            // ==========================================
            CameraControlBar(
                isFrontCamera = uiState.selectedSensor.isFront,
                flashMode = uiState.flashMode,
                supportsFlash = uiState.selectedSensor.supportsTorch,
                showGrid = uiState.showGridOverlay,
                rotationDegrees = uiState.rotationDegrees,
                exposureCompensation = uiState.exposureCompensation,
                onFlipCamera = { viewModel.flipCamera() },
                onCycleFlashMode = { viewModel.cycleFlashMode() },
                onToggleGrid = { viewModel.toggleGridOverlay() },
                onRotate90 = { viewModel.rotate90() },
                onCycleExposure = { viewModel.cycleExposure() },
                modifier = Modifier.fillMaxWidth()
            )

            // ==========================================
            // 3. Main Action Button: Start / Stop Stream
            // ==========================================
            Button(
                onClick = {
                    if (!uiState.isCameraPermissionGranted) {
                        permissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                    } else if (uiState.isStreaming) {
                        viewModel.stopStreaming(context)
                    } else {
                        viewModel.startStreaming(context)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("main_stream_action_button"),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isStreaming) "Stop Webcam Stream" else "Start Webcam Stream",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ==========================================
            // 4. Live Diagnostics Card (When Streaming)
            // ==========================================
            if (uiState.isStreaming) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE STREAM ACTIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = streamDurationText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiagnosticMetric(
                            label = "FPS",
                            value = if (uiState.diagnostics.fps > 0f) "%.1f".format(uiState.diagnostics.fps) else "${uiState.selectedProfile.fps}"
                        )
                        DiagnosticMetric(
                            label = "Bitrate",
                            value = "${uiState.selectedProfile.bitrateMbps} Mbps"
                        )
                        DiagnosticMetric(
                            label = "Clients",
                            value = "${uiState.connectedClientsCount}"
                        )
                        DiagnosticMetric(
                            label = "Codec",
                            value = "H.264 HW"
                        )
                    }
                }
            }

            // ==========================================
            // 5. Connection & Desktop Stream URL Card
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp)
            ) {
                Text(
                    text = "CONNECTION & DESKTOP URL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Wi-Fi vs USB Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(3.dp)
                ) {
                    val isWifi = uiState.connectionMode == HostConnectionMode.WIFI
                    val wifiBg by animateColorAsState(
                        targetValue = if (isWifi) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "wifiBg"
                    )
                    val wifiColor by animateColorAsState(
                        targetValue = if (isWifi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "wifiColor"
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(wifiBg)
                            .clickable { viewModel.setConnectionMode(HostConnectionMode.WIFI) }
                            .testTag("conn_mode_wifi"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Wifi, contentDescription = null, tint = wifiColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Wi-Fi (LAN)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = wifiColor)
                    }

                    val isUsb = uiState.connectionMode == HostConnectionMode.USB_ADB
                    val usbBg by animateColorAsState(
                        targetValue = if (isUsb) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "usbBg"
                    )
                    val usbColor by animateColorAsState(
                        targetValue = if (isUsb) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "usbColor"
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(usbBg)
                            .clickable { viewModel.setConnectionMode(HostConnectionMode.USB_ADB) }
                            .testTag("conn_mode_usb"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Usb, contentDescription = null, tint = usbColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "USB / ADB", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = usbColor)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stream URL Box with Copy Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.connectionMode == HostConnectionMode.USB_ADB) "USB Port Forward URL" else "Network Stream URL",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = effectiveStreamUrl,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Focal Stream URL", effectiveStreamUrl))
                            Toast.makeText(context, "Stream URL copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy URL",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Pairing PIN Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pairing PIN: ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = uiState.pairingCode,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.regeneratePairingCode()
                            Toast.makeText(context, "Pairing PIN regenerated", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate PIN",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // mDNS Auto-Discovery Status Badge (when in Wi-Fi mode)
                if (uiState.connectionMode == HostConnectionMode.WIFI) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (uiState.isDiscoveryActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isDiscoveryActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (uiState.isDiscoveryActive) "mDNS Auto-Discovery Active" else "mDNS Auto-Discovery Broadcast",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (uiState.isDiscoveryActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Service: ${uiState.discoveryServiceName ?: "Focal"} • _focal._tcp",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 6. Camera & Video Settings Card
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp)
            ) {
                Text(
                    text = "HARDWARE & SETTINGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Camera Lens Selector Tile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { viewModel.setShowSensorPicker(true) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("selector_camera_sensor"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(FocalPrimaryFixed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = null,
                                tint = FocalOnPrimaryFixed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Camera Lens",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${uiState.selectedSensor.name} (${uiState.selectedSensor.resolutionLabel})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stream Output Profile Selector Tile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { viewModel.setShowProfilePicker(true) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("selector_output_profile"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HighQuality,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Output Profile",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${uiState.selectedProfile.name} • ${uiState.selectedProfile.bitrateMbps} Mbps",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Microphone Audio Switch Tile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Microphone Audio",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.streamMode == StreamMode.VIDEO_AND_AUDIO) "Stream audio with video" else "Video only (zero audio transmission)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = uiState.streamMode == StreamMode.VIDEO_AND_AUDIO,
                        onCheckedChange = { enabled ->
                            viewModel.setStreamMode(if (enabled) StreamMode.VIDEO_AND_AUDIO else StreamMode.VIDEO_ONLY)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    }

    // Modal Bottom Sheets for Lens & Profile selection
    if (uiState.showSensorPicker) {
        SensorPickerBottomSheet(
            sensors = uiState.availableSensors,
            selectedSensor = uiState.selectedSensor,
            onSensorSelected = { viewModel.selectSensor(it) },
            sheetState = sensorSheetState,
            onDismissRequest = { viewModel.setShowSensorPicker(false) }
        )
    }

    if (uiState.showProfilePicker) {
        ProfilePickerBottomSheet(
            profiles = uiState.availableProfiles,
            selectedProfile = uiState.selectedProfile,
            onProfileSelected = { viewModel.selectProfile(it) },
            sheetState = profileSheetState,
            onDismissRequest = { viewModel.setShowProfilePicker(false) }
        )
    }
}

@Composable
private fun DiagnosticMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
