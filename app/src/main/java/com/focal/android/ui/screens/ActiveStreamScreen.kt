package com.focal.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.LaptopChromebook
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focal.android.data.model.FlashMode
import com.focal.android.ui.components.CameraViewfinder
import com.focal.android.ui.components.FocalTopBar
import com.focal.android.ui.viewmodel.FocalUiState

@Composable
fun ActiveStreamScreen(
    uiState: FocalUiState,
    onBackClick: () -> Unit,
    onStopStreamClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onRotate90: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleTorch: () -> Unit,
    onCycleFlashMode: () -> Unit = {},
    onSelectFlashMode: (FlashMode) -> Unit = {},
    onToggleGrid: () -> Unit = {},
    onToggleOledScreenOff: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showStopConfirmation by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streamPulse"
    )

    // Formatted elapsed timer: hh:mm:ss
    val hrs = uiState.streamElapsedSeconds / 3600
    val mins = (uiState.streamElapsedSeconds % 3600) / 60
    val secs = uiState.streamElapsedSeconds % 60
    val timerText = "%02d:%02d:%02d".format(hrs, mins, secs)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Header Top Bar
            FocalTopBar(
                title = "Active Stream",
                subtitle = "Focal Linux Bridge",
                showBack = true,
                onBackClick = onBackClick,
                showControls = false
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status & Session Banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Webcam Running badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF08834B).copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF006739).copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WEBCAM RUNNING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF006739),
                                letterSpacing = 1.sp
                            )
                        }

                        // Elapsed timer badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = timerText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LaptopChromebook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Client: ${uiState.selectedHost.name}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF90F8B3).copy(alpha = 0.3f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = uiState.selectedHost.bridgeDriver,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF00522C)
                            )
                        }
                    }
                }

                // Live Streaming Preview Card
                CameraViewfinder(
                    hasCameraPermission = uiState.isCameraPermissionGranted,
                    isFrontCamera = uiState.selectedSensor.isFront,
                    rotationDegrees = uiState.rotationDegrees,
                    isLiveStreaming = true,
                    isTorchOn = uiState.isTorchOn,
                    flashMode = uiState.flashMode,
                    showGridOverlay = uiState.showGridOverlay,
                    batteryPercentage = uiState.batteryPercentage,
                    isBatteryCharging = uiState.isBatteryCharging,
                    badgeTitle = "Rear Main (1x • 24mm f/1.8)",
                    profileInfo = "${uiState.selectedProfile.name} • H.264 HW",
                    streamUrl = uiState.streamUrl,
                    onToggleTorch = onToggleTorch,
                    onCycleFlashMode = onCycleFlashMode,
                    onSelectFlashMode = onSelectFlashMode,
                    onToggleGrid = onToggleGrid,
                    onFlipCamera = onFlipCamera,
                    modifier = Modifier.fillMaxWidth()
                )

                // Runtime Quick Controls Ribbon (Unique non-duplicated controls)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StreamRibbonButton(
                        icon = Icons.Default.BarChart,
                        label = "${uiState.diagnostics.fps.toInt()} FPS",
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { },
                        modifier = Modifier.weight(1f),
                        testTag = "ribbon_metrics"
                    )

                    StreamRibbonButton(
                        icon = Icons.Default.ScreenRotation,
                        label = "Rotate 90°",
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onRotate90,
                        modifier = Modifier.weight(1f),
                        testTag = "ribbon_rotate"
                    )

                    StreamRibbonButton(
                        icon = if (uiState.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (uiState.isAudioMuted) "Audio Muted" else "Mic Live",
                        iconTint = if (uiState.isAudioMuted) MaterialTheme.colorScheme.secondary else Color(0xFF006739),
                        onClick = onToggleMute,
                        modifier = Modifier.weight(1f),
                        testTag = "ribbon_toggle_mute"
                    )

                    StreamRibbonButton(
                        icon = Icons.Default.ModeNight,
                        label = "Screen Off",
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { onToggleOledScreenOff(true) },
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        testTag = "ribbon_screen_off"
                    )
                }

                // Background Service Persistent System Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Background Service Engaged",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Stream continues if you switch apps, lock screen, or turn off the display.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            lineHeight = 17.sp
                        )
                    }
                }

                // Metrics & Diagnostics M3 Tile Group
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Stream Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF08834B).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFF006739),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Lossless",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF006739)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Row 1: Connection Latency
                    DiagnosticRow(
                        icon = Icons.Default.Wifi,
                        title = uiState.diagnostics.connectionType,
                        subtitle = "${uiState.diagnostics.latencyMs} ms roundtrip latency",
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.height(18.dp)
                            ) {
                                Box(modifier = Modifier.width(4.dp).height(6.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF006739)))
                                Box(modifier = Modifier.width(4.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF006739)))
                                Box(modifier = Modifier.width(4.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF006739)))
                                Box(modifier = Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF006739)))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 2: Video Pipeline
                    DiagnosticRow(
                        icon = Icons.Default.Videocam,
                        title = "${uiState.selectedProfile.resolution.split(" ")[0]} • ${uiState.diagnostics.fps} FPS",
                        subtitle = "${uiState.diagnostics.codec} (${uiState.diagnostics.bitrateMbps} Mbps CBR)",
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = uiState.diagnostics.hardwareAccel,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 3: Audio State
                    DiagnosticRow(
                        icon = if (uiState.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        title = if (uiState.isAudioMuted) "Audio: Off" else "Audio: Active",
                        subtitle = if (uiState.isAudioMuted) "Mic idle & hardware locked" else "48 kHz PCM Passthrough",
                        iconBg = Color(0xFF08834B).copy(alpha = 0.15f),
                        iconTint = Color(0xFF006739),
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF08834B).copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF006739),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.isAudioMuted) "Isolated" else "Streaming",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF006739)
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 4: Device Health
                    DiagnosticRow(
                        icon = Icons.Default.Devices,
                        title = "Battery ${uiState.diagnostics.batteryPct}% • Temp ${uiState.diagnostics.temperatureC}°C",
                        subtitle = "Optimal • ${uiState.diagnostics.droppedFrames} dropped frames",
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF006739),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Primary Destructive CTA: Stop Webcam
                Button(
                    onClick = { showStopConfirmation = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("stop_webcam_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stop webcam",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // OLED Power Save Blackout Overlay
        AnimatedVisibility(
            visible = uiState.isOledScreenOffActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { onToggleOledScreenOff(false) }
                    .testTag("oled_blackout_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EnergySavingsLeaf,
                        contentDescription = null,
                        tint = Color(0xFF4A4D54),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "OLED POWER SAVE ACTIVE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color(0xFF6E717A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Webcam is still broadcasting",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFB0B3BD),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1E2024))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Tap anywhere to awaken screen",
                            fontSize = 13.sp,
                            color = Color(0xFF888B96)
                        )
                    }
                }
            }
        }

        // Stop confirmation dialog
        if (showStopConfirmation) {
            AlertDialog(
                onDismissRequest = { showStopConfirmation = false },
                title = { Text("Stop Webcam Stream?") },
                text = { Text("Disconnect and stop active camera stream to ${uiState.selectedHost.name}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showStopConfirmation = false
                            onStopStreamClick()
                        }
                    ) {
                        Text("Stop", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStopConfirmation = false }) {
                        Text("Keep Streaming")
                    }
                }
            )
        }
    }
}

@Composable
private fun StreamRibbonButton(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    testTag: String = ""
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DiagnosticRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingContent: @Composable () -> Unit,
    iconBg: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        trailingContent()
    }
}
