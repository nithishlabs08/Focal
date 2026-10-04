package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import com.example.media.CameraCapturePipeline
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LaptopChromebook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.model.FlashMode
import com.example.server.CameraStreamBroadcaster
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.ByteArrayOutputStream

const val STREAM_READY_IMAGE_URL = "https://lh3.googleusercontent.com/aida-public/AB6AXuC30EDOpgVZhPjdxSB7gXlgMpEiQonG-xtOGGb5lq1qS783bqvsS8pEyDOKnSgjvITkiu6ywhHlMgcQmVZBEyzOmufLSGYKKicPLl7MPHIyJhoWijS-XNW9O-ss7HL0BmsiLisr9d6foDGo9RiDrmSNKrH0A2jLNtL1TNtS_OwgTA3x5v6zwrhXeHEENVdC0xGWRTo2LvD_wVWIymp31Eh-7OhUurXwYji39ftjYsyFsZhY859YEkv96Q"
const val ACTIVE_STREAM_IMAGE_URL = "https://lh3.googleusercontent.com/aida-public/AB6AXuBgYXTgd-DC4RYVFgJ8C8TcYvBWxi5f050U6XG1bxGn7pvEL3XOolJV87Bkx5onyTxeX2GIZSp-pU6XuQYqx6YVcA86QY_lNq5W3ffB1NILhpyLN2WadwWA7IT16zM_Oh9LD-_QYNKq54Hmg0GSP5MxGLt21pDUKX7fzp2TTjzN-2UWmS1eN3oQ5C2MW93yadUJJ3HhQ2DQb38Ji3kzcjHeb49J9z4WPkgQ7YZ3xYZfm7LeqZzwd9DAqQ"

@Composable
fun CameraViewfinder(
    hasCameraPermission: Boolean,
    isFrontCamera: Boolean,
    rotationDegrees: Int,
    isLiveStreaming: Boolean = false,
    flashMode: FlashMode = FlashMode.OFF,
    showGridOverlay: Boolean = true,
    batteryPercentage: Int = 82,
    isBatteryCharging: Boolean = false,
    badgeTitle: String = "Rear Main (1x • 24mm f/1.8)",
    profileInfo: String = "1080p • 30 FPS • H.264 HW",
    streamUrl: String = "http://192.168.1.142:8080/stream.h264",
    isTorchOn: Boolean = false,
    onToggleTorch: () -> Unit = {},
    onCycleFlashMode: () -> Unit = {},
    onSelectFlashMode: (FlashMode) -> Unit = {},
    onFlipCamera: () -> Unit = {},
    onToggleGrid: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onSimulateBattery: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isCameraBound by remember { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var showFlashMenu by remember { mutableStateOf(false) }
    var showBatteryDetails by remember { mutableStateOf(false) }
    var isControlPanelExpanded by remember { mutableStateOf(true) }

    val connectedClients by CameraStreamBroadcaster.connectedClients.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Hardware torch control hook
    LaunchedEffect(flashMode, isTorchOn, activeCamera) {
        try {
            val shouldEnableTorch = flashMode == FlashMode.TORCH || (flashMode == FlashMode.OFF && isTorchOn)
            activeCamera?.cameraControl?.enableTorch(shouldEnableTorch)
        } catch (_: Exception) {
        }
    }

    // Fallback frame generator loop for emulator environments
    LaunchedEffect(hasCameraPermission, isCameraBound) {
        if (!hasCameraPermission || !isCameraBound) {
            while (isActive) {
                CameraStreamBroadcaster.generateFallbackFrame("Focal 100% Local Stream")
                delay(33) // ~30 fps
            }
        }
    }

    LaunchedEffect(isFrontCamera) {
        if (hasCameraPermission && isCameraBound) {
            CameraCapturePipeline.rebind(context)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (isLiveStreaming) 4f / 3f else 16f / 10f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF2D3037))
            .testTag("camera_viewfinder_container"),
        contentAlignment = Alignment.Center
    ) {
        val scaleX = if (isFrontCamera) -1f else 1f

        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    CameraCapturePipeline.bindCamera(
                        context = ctx,
                        lifecycleOwner = lifecycleOwner,
                        surfaceProvider = previewView.surfaceProvider,
                        isFront = isFrontCamera
                    ) { cam ->
                        activeCamera = cam
                        isCameraBound = cam != null
                    }
                    previewView
                },
                update = {
                    // Update preview state if required
                },
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scaleX, 1f)
                    .rotate(rotationDegrees.toFloat())
            )
        }

        // Hotlinked fallback or backdrop image
        if (!hasCameraPermission || !isCameraBound) {
            AsyncImage(
                model = if (isLiveStreaming) ACTIVE_STREAM_IMAGE_URL else STREAM_READY_IMAGE_URL,
                contentDescription = "Camera Viewfinder Feed",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scaleX, 1f)
                    .rotate(rotationDegrees.toFloat())
            )
        }

        // Vignette Gradient Overlays
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        // 3x3 Grid Framing Overlay
        if (showGridOverlay) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val strokeColor = Color.White.copy(alpha = 0.22f)
                val strokeWidth = 1.dp.toPx()

                // Vertical grid lines
                drawLine(strokeColor, Offset(width / 3f, 0f), Offset(width / 3f, height), strokeWidth)
                drawLine(strokeColor, Offset(width * 2f / 3f, 0f), Offset(width * 2f / 3f, height), strokeWidth)

                // Horizontal grid lines
                drawLine(strokeColor, Offset(0f, height / 3f), Offset(width, height / 3f), strokeWidth)
                drawLine(strokeColor, Offset(0f, height * 2f / 3f), Offset(width, height * 2f / 3f), strokeWidth)
            }
        }

        // Center reticle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        // ==========================================
        // LOW BATTERY WARNING BANNER (On Camera Preview)
        // ==========================================
        val isLowPower = batteryPercentage < 20 && !isBatteryCharging
        AnimatedVisibility(
            visible = isLowPower,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 46.dp, start = 12.dp, end = 68.dp)
                .testTag("low_battery_warning_banner")
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFBA1A1A).copy(alpha = 0.94f))
                    .border(1.dp, Color(0xFFFFB4AB).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = "Low Power Warning",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LOW POWER ($batteryPercentage%) • INTERRUPT RISK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Connect USB/charger: stream may cut out abruptly",
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.92f),
                        lineHeight = 11.sp
                    )
                }
            }
        }

        // Top Status Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left sensor badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2D3037).copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                if (isLiveStreaming) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFBA1A1A).copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE • FEED 0",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = Color(0xFF90F8B3),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeTitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEEF0FA)
                    )
                }
            }

            // Right connected desktop badge or Live Feed indicator
            if (isLiveStreaming && connectedClients > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF006739).copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LaptopChromebook,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$connectedClients desktop",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else if (!isLiveStreaming) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF2D3037).copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFBA1A1A))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE FEED READY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEEF0FA)
                    )
                }
            }
        }

        // ==========================================
        // CAMERA CONTROL PANEL OVERLAY (Top Floating Dock)
        // ==========================================
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 12.dp)
                .testTag("control_panel_overlay")
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Main Control Panel Capsule
                AnimatedVisibility(
                    visible = isControlPanelExpanded,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF181C24).copy(alpha = 0.88f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Flip Front / Back Camera Action
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFrontCamera) Color(0xFF1E6BEB)
                                    else Color.White.copy(alpha = 0.12f)
                                )
                                .clickable {
                                    onFlipCamera()
                                    Toast.makeText(
                                        context,
                                        if (!isFrontCamera) "Switched to Front Lens" else "Switched to Rear Main",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .testTag("control_panel_flip_camera"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Toggle Front / Back Camera",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 2. Flash / Torch Mode Action
                        val flashIcon = when (flashMode) {
                            FlashMode.OFF -> Icons.Default.FlashOff
                            FlashMode.TORCH -> Icons.Default.FlashOn
                            FlashMode.AUTO -> Icons.Default.FlashAuto
                        }
                        val flashBg = when (flashMode) {
                            FlashMode.OFF -> Color.White.copy(alpha = 0.12f)
                            FlashMode.TORCH -> Color(0xFF0053C3)
                            FlashMode.AUTO -> Color(0xFF08834B)
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(flashBg)
                                .clickable {
                                    showFlashMenu = !showFlashMenu
                                    showBatteryDetails = false
                                }
                                .testTag("control_panel_flash_mode"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = flashIcon,
                                contentDescription = "Switch Flash Mode",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 3. Grid Toggle Button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showGridOverlay) Color.White.copy(alpha = 0.12f)
                                    else Color.Black.copy(alpha = 0.3f)
                                )
                                .clickable { onToggleGrid() }
                                .testTag("control_panel_grid_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (showGridOverlay) Icons.Default.GridOn else Icons.Default.GridOff,
                                contentDescription = "Toggle Grid",
                                tint = if (showGridOverlay) Color.White else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 4. BATTERY LEVEL INDICATOR & POWER WARNING
                        val isCriticallyLow = batteryPercentage <= 10 && !isBatteryCharging
                        val batteryBg = when {
                            isBatteryCharging -> Color(0xFF006739)
                            isCriticallyLow -> Color(0xFFBA1A1A)
                            isLowPower -> Color(0xFFD97706)
                            else -> Color.White.copy(alpha = 0.12f)
                        }
                        val batteryIcon = when {
                            isBatteryCharging -> Icons.Default.BatteryChargingFull
                            isLowPower -> Icons.Default.BatteryAlert
                            batteryPercentage > 80 -> Icons.Default.BatteryFull
                            batteryPercentage > 50 -> Icons.Default.Battery5Bar
                            batteryPercentage > 25 -> Icons.Default.Battery3Bar
                            else -> Icons.Default.Battery1Bar
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(batteryBg)
                                .clickable {
                                    showBatteryDetails = !showBatteryDetails
                                    showFlashMenu = false
                                }
                                .testTag("control_panel_battery_indicator"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = batteryIcon,
                                    contentDescription = "Battery level $batteryPercentage%",
                                    tint = if (isLowPower || isBatteryCharging) Color.White else Color(0xFFEEF0FA),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "$batteryPercentage%",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLowPower || isBatteryCharging) Color.White else Color(0xFFEEF0FA)
                                )
                            }
                        }
                    }
                }

                // Sub-Menu: Flash Mode Selector Popup
                AnimatedVisibility(
                    visible = showFlashMenu && isControlPanelExpanded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF181C24).copy(alpha = 0.95f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FlashOptionItem(
                            icon = Icons.Default.FlashOff,
                            label = "Off",
                            isSelected = flashMode == FlashMode.OFF,
                            onClick = {
                                onSelectFlashMode(FlashMode.OFF)
                                showFlashMenu = false
                                Toast.makeText(context, "Flash: OFF", Toast.LENGTH_SHORT).show()
                            },
                            testTag = "flash_opt_off"
                        )
                        FlashOptionItem(
                            icon = Icons.Default.FlashOn,
                            label = "Torch",
                            isSelected = flashMode == FlashMode.TORCH,
                            onClick = {
                                onSelectFlashMode(FlashMode.TORCH)
                                showFlashMenu = false
                                Toast.makeText(context, "Torch: Active", Toast.LENGTH_SHORT).show()
                            },
                            testTag = "flash_opt_torch"
                        )
                        FlashOptionItem(
                            icon = Icons.Default.FlashAuto,
                            label = "Auto",
                            isSelected = flashMode == FlashMode.AUTO,
                            onClick = {
                                onSelectFlashMode(FlashMode.AUTO)
                                showFlashMenu = false
                                Toast.makeText(context, "Flash: Auto", Toast.LENGTH_SHORT).show()
                            },
                            testTag = "flash_opt_auto"
                        )
                    }
                }

                // Sub-Menu: Battery Details & Power Health Dialog
                AnimatedVisibility(
                    visible = showBatteryDetails && isControlPanelExpanded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .width(210.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF181C24).copy(alpha = 0.96f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                            .testTag("battery_details_panel"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Battery Power",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "$batteryPercentage%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLowPower) Color(0xFFFFB4AB) else Color(0xFF90F8B3)
                            )
                        }

                        Text(
                            text = if (isBatteryCharging) "Status: Fast Charging (USB)"
                            else if (isLowPower) "Status: Critical Low Power • Connect charger"
                            else "Status: Discharging • Normal (~${batteryPercentage / 18 + 1}h left)",
                            fontSize = 11.sp,
                            color = if (isLowPower) Color(0xFFFFB4AB) else Color(0xFFC4C6D0),
                            lineHeight = 14.sp
                        )

                        // Testing toggle button
                        if (onSimulateBattery != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { onSimulateBattery(14) },
                                    modifier = Modifier.weight(1f).height(28.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A))
                                ) {
                                    Text("Test 14%", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { onSimulateBattery(85) },
                                    modifier = Modifier.weight(1f).height(28.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006739))
                                ) {
                                    Text("Reset 85%", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Center bottom stream URL pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Stream URL", streamUrl))
                    Toast.makeText(context, "Stream URL copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = streamUrl,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFB0C6FF),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy URL",
                tint = Color(0xFFB0C6FF),
                modifier = Modifier.size(12.dp)
            )
        }

        // Bottom Controls / Profile overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2D3037).copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color(0xFFB0C6FF),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = profileInfo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFEEF0FA)
                )
            }

            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2D3037).copy(alpha = 0.85f))
                    .testTag("viewfinder_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = "Fullscreen Viewfinder",
                    tint = Color(0xFFEEF0FA),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FlashOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF0053C3) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = Color.White
        )
    }
}
