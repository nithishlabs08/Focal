package com.focal.android.ui.components

import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focal.android.data.model.FlashMode
import com.focal.android.media.CameraCapturePipeline
import com.focal.android.ui.theme.LocalSendDarkBackground
import com.focal.android.server.CameraStreamBroadcaster
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun CameraViewfinder(
    hasCameraPermission: Boolean,
    isFrontCamera: Boolean,
    rotationDegrees: Int,
    isLiveStreaming: Boolean = false,
    showGridOverlay: Boolean = false,
    onRequestPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isCameraBound by remember { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    // Fallback frame generator loop for headless/test environments
    LaunchedEffect(hasCameraPermission, isCameraBound) {
        if (!hasCameraPermission || !isCameraBound) {
            while (isActive) {
                CameraStreamBroadcaster.generateFallbackFrame("Focal Local Stream")
                delay(33) // ~30 fps
            }
        }
    }

    LaunchedEffect(isFrontCamera) {
        if (hasCameraPermission && isCameraBound) {
            CameraCapturePipeline.rebind(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CameraCapturePipeline.detachPreviewSurfaceProvider()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(LocalSendDarkBackground)
            .testTag("camera_viewfinder_container"),
        contentAlignment = Alignment.Center
    ) {
        val scaleX = if (isFrontCamera) -1f else 1f

        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    if (isLiveStreaming) {
                        CameraCapturePipeline.attachPreviewSurfaceProvider(previewView.surfaceProvider)
                        isCameraBound = true
                    } else {
                        CameraCapturePipeline.bindCamera(
                            context = ctx,
                            lifecycleOwner = lifecycleOwner,
                            surfaceProvider = previewView.surfaceProvider,
                            isFront = isFrontCamera
                        ) { cam ->
                            activeCamera = cam
                            isCameraBound = cam != null
                        }
                    }
                    previewView
                },
                update = { previewView ->
                    if (isLiveStreaming) {
                        CameraCapturePipeline.attachPreviewSurfaceProvider(previewView.surfaceProvider)
                        isCameraBound = true
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scaleX, 1f)
                    .rotate(rotationDegrees.toFloat())
            )

            // 3x3 Grid Framing Overlay
            if (showGridOverlay) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val strokeColor = Color.White.copy(alpha = 0.25f)
                    val strokeWidth = 1.dp.toPx()

                    drawLine(strokeColor, Offset(width / 3f, 0f), Offset(width / 3f, height), strokeWidth)
                    drawLine(strokeColor, Offset(width * 2f / 3f, 0f), Offset(width * 2f / 3f, height), strokeWidth)
                    drawLine(strokeColor, Offset(0f, height / 3f), Offset(width, height / 3f), strokeWidth)
                    drawLine(strokeColor, Offset(0f, height * 2f / 3f), Offset(width, height * 2f / 3f), strokeWidth)
                }
            }

            // Minimal Live indicator in top corner
            if (isLiveStreaming) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        } else {
            // In-preview permission request card
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Camera access required",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Grant camera permission to preview and stream",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text("Grant Permission", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Camera control strip positioned cleanly UNDER the preview.
 */
@Composable
fun CameraControlBar(
    isFrontCamera: Boolean,
    flashMode: FlashMode,
    supportsFlash: Boolean,
    showGrid: Boolean,
    rotationDegrees: Int,
    exposureCompensation: Float,
    onFlipCamera: () -> Unit,
    onCycleFlashMode: () -> Unit,
    onToggleGrid: () -> Unit,
    onRotate90: () -> Unit,
    onCycleExposure: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Flip Camera
        ControlBarItem(
            icon = Icons.Default.Cameraswitch,
            label = if (isFrontCamera) "Front" else "Back",
            isActive = isFrontCamera,
            onClick = onFlipCamera,
            testTag = "control_flip_camera"
        )

        // 2. Flash / Torch
        if (supportsFlash) {
            val flashIcon = when (flashMode) {
                FlashMode.OFF -> Icons.Default.FlashOff
                FlashMode.TORCH -> Icons.Default.FlashOn
                FlashMode.AUTO -> Icons.Default.FlashAuto
            }
            val flashLabel = when (flashMode) {
                FlashMode.OFF -> "Flash Off"
                FlashMode.TORCH -> "Torch"
                FlashMode.AUTO -> "Auto"
            }
            ControlBarItem(
                icon = flashIcon,
                label = flashLabel,
                isActive = flashMode != FlashMode.OFF,
                onClick = onCycleFlashMode,
                testTag = "control_flash_mode"
            )
        }

        // 3. Grid Lines
        ControlBarItem(
            icon = if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
            label = if (showGrid) "Grid On" else "Grid Off",
            isActive = showGrid,
            onClick = onToggleGrid,
            testTag = "control_grid_toggle"
        )

        // 4. Rotate 90
        ControlBarItem(
            icon = Icons.Default.ScreenRotation,
            label = if (rotationDegrees == 0) "0°" else "${rotationDegrees}°",
            isActive = rotationDegrees != 0,
            onClick = onRotate90,
            testTag = "control_rotate"
        )

        // 5. Exposure (EV)
        val evLabel = if (exposureCompensation == 0.0f) "EV 0" else "EV %+1.1f".format(exposureCompensation)
        ControlBarItem(
            icon = Icons.Default.BrightnessMedium,
            label = evLabel,
            isActive = exposureCompensation != 0.0f,
            onClick = onCycleExposure,
            testTag = "control_exposure"
        )
    }
}

@Composable
private fun ControlBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
