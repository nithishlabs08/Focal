package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FlashMode
import com.example.data.model.HostConnectionMode
import com.example.data.model.StreamMode
import com.example.ui.components.CameraViewfinder
import com.example.ui.theme.FocalOnPrimaryFixed
import com.example.ui.theme.FocalOnSecondaryFixed
import com.example.ui.theme.FocalPrimaryFixed
import com.example.ui.theme.FocalSecondaryFixed
import com.example.ui.viewmodel.FocalUiState

@Composable
fun StreamReadyScreen(
    uiState: FocalUiState,
    onStartStreamClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onAutoRotate: () -> Unit,
    onCycleExposure: () -> Unit,
    onCycleFlashMode: () -> Unit = {},
    onSelectFlashMode: (FlashMode) -> Unit = {},
    onToggleGrid: () -> Unit = {},
    onStreamModeChanged: (StreamMode) -> Unit,
    onConnectionModeChanged: (HostConnectionMode) -> Unit,
    onOpenSensorPicker: () -> Unit,
    onOpenProfilePicker: () -> Unit,
    onOpenQuickControls: () -> Unit,
    onOpenPairingCode: () -> Unit = {},
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 80.dp)
    ) {
        // Status Row (Ready to stream & LAN IP)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ready to stream pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF08834B).copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF006739).copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ready to stream",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF006739)
                )
            }

            // LAN IP badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lan,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = uiState.deviceIp,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Camera conflict notice
        if (uiState.cameraConflictState == com.example.data.model.CameraConflictState.UNAVAILABLE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFBA1A1A).copy(alpha = 0.9f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Camera Unavailable",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.cameraConflictMessage ?: "Camera temporarily unavailable. Retrying automatically...",
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Hardware capability fallback notice
        if (uiState.isClampedFallback && uiState.clampNotice != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFD97706).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFFD97706).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Hardware adjustment",
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.clampNotice ?: "",
                    fontSize = 11.sp,
                    color = Color(0xFFD97706),
                    lineHeight = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Live Viewfinder Card
        CameraViewfinder(
            hasCameraPermission = uiState.isCameraPermissionGranted,
            isFrontCamera = uiState.selectedSensor.isFront,
            rotationDegrees = uiState.rotationDegrees,
            isLiveStreaming = false,
            flashMode = uiState.flashMode,
            showGridOverlay = uiState.showGridOverlay,
            batteryPercentage = uiState.batteryPercentage,
            isBatteryCharging = uiState.isBatteryCharging,
            badgeTitle = "${uiState.selectedSensor.name} (1x • ${uiState.selectedSensor.focalLength} ${uiState.selectedSensor.aperture})",
            profileInfo = "${uiState.selectedProfile.name} • H.264 HW",
            streamUrl = uiState.streamUrl,
            isTorchOn = uiState.isTorchOn,
            onFlipCamera = onFlipCamera,
            onCycleFlashMode = onCycleFlashMode,
            onSelectFlashMode = onSelectFlashMode,
            onToggleGrid = onToggleGrid,
            onToggleFullscreen = onToggleFullscreen,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Controls Horizontal Scroll Pill Row (Unique non-duplicated controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Lens Switcher Pill (Non-duplicated: opens optical sensor switcher)
            QuickActionPill(
                icon = Icons.Default.Camera,
                text = uiState.selectedSensor.name,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onOpenSensorPicker,
                testTag = "quick_pill_sensor_picker"
            )

            // Auto Rotate Pill
            QuickActionPill(
                icon = Icons.Default.ScreenRotation,
                text = if (uiState.rotationDegrees == 0) "Auto Rotate" else "Rotate ${uiState.rotationDegrees}°",
                iconTint = MaterialTheme.colorScheme.secondary,
                onClick = onAutoRotate,
                testTag = "quick_pill_auto_rotate"
            )

            // EV Compensation Pill
            val evText = if (uiState.exposureCompensation == 0.0f) "EV 0.0" else "EV %+1.1f".format(uiState.exposureCompensation)
            QuickActionPill(
                icon = Icons.Default.BrightnessMedium,
                text = evText,
                iconTint = MaterialTheme.colorScheme.secondary,
                onClick = onCycleExposure,
                testTag = "quick_pill_ev"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Stream Mode Selector Card
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
                Text(
                    text = "STREAM MODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF006739),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (uiState.streamMode == StreamMode.VIDEO_ONLY) "Mic Off (Zero Leak)" else "Mic Active",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF006739)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Two-segment toggle pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(4.dp)
            ) {
                val isVideoOnly = uiState.streamMode == StreamMode.VIDEO_ONLY
                val tab1Bg by animateColorAsState(
                    targetValue = if (isVideoOnly) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
                    label = "tab1Bg"
                )
                val tab1Color by animateColorAsState(
                    targetValue = if (isVideoOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tab1Color"
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(tab1Bg)
                        .clickable { onStreamModeChanged(StreamMode.VIDEO_ONLY) }
                        .testTag("stream_mode_video_only"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = tab1Color,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Video only",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tab1Color
                    )
                }

                val isVideoAndAudio = uiState.streamMode == StreamMode.VIDEO_AND_AUDIO
                val tab2Bg by animateColorAsState(
                    targetValue = if (isVideoAndAudio) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
                    label = "tab2Bg"
                )
                val tab2Color by animateColorAsState(
                    targetValue = if (isVideoAndAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tab2Color"
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(tab2Bg)
                        .clickable { onStreamModeChanged(StreamMode.VIDEO_AND_AUDIO) }
                        .testTag("stream_mode_video_audio"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = tab2Color,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Video + Audio",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tab2Color
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sensor and Profile Selection Row (2 Cards)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Camera Sensor Card
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onOpenSensorPicker() }
                    .padding(14.dp)
                    .testTag("selector_camera_sensor"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(FocalPrimaryFixed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = null,
                            tint = FocalOnPrimaryFixed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Camera Sensor",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = uiState.selectedSensor.name,
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

            // Output Profile Card
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onOpenProfilePicker() }
                    .padding(14.dp)
                    .testTag("selector_output_profile"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = FocalOnSecondaryFixed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Output Profile",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.selectedProfile.badge != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF08834B).copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = uiState.selectedProfile.badge,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF006739)
                                    )
                                }
                            }
                        }
                        Text(
                            text = uiState.selectedProfile.name,
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
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Host Connection Card
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
                Text(
                    text = "HOST CONNECTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = uiState.selectedHost.name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Two-segment toggle pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(4.dp)
            ) {
                val isWifi = uiState.connectionMode == HostConnectionMode.WIFI
                val conn1Bg by animateColorAsState(
                    targetValue = if (isWifi) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "conn1Bg"
                )
                val conn1Color by animateColorAsState(
                    targetValue = if (isWifi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "conn1Color"
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(conn1Bg)
                        .clickable { onConnectionModeChanged(HostConnectionMode.WIFI) }
                        .testTag("conn_mode_wifi"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = conn1Color,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Wi-Fi",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = conn1Color
                    )
                }

                val isUsb = uiState.connectionMode == HostConnectionMode.USB_ADB
                val conn2Bg by animateColorAsState(
                    targetValue = if (isUsb) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "conn2Bg"
                )
                val conn2Color by animateColorAsState(
                    targetValue = if (isUsb) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "conn2Color"
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(conn2Bg)
                        .clickable { onConnectionModeChanged(HostConnectionMode.USB_ADB) }
                        .testTag("conn_mode_usb"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = conn2Color,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "USB / ADB",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = conn2Color
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Zero-Interruption Streaming Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenLockPortrait,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Zero-Interruption Streaming",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FocalOnSecondaryFixed
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Stream stays alive in background. You can lock your phone screen or reply to messages anytime without dropping frames.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Primary Action Button: Start Webcam Stream
        Button(
            onClick = onStartStreamClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_webcam_stream_button"),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Start Webcam Stream",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Buttons Row (Quick Controls & Pairing QR)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenQuickControls,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("stream_quick_controls_btn"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Quick Controls",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = onOpenPairingCode,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("stream_pairing_code_btn"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Pairing Code",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun QuickActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconTint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
