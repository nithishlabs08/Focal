package com.focal.android.tv.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.focal.android.tv.client.TvAudioPlayer
import com.focal.android.tv.client.TvVideoDecoder
import com.focal.android.tv.model.DiscoveredCamera
import kotlinx.coroutines.delay

@Composable
fun TvPlayerView(
    camera: DiscoveredCamera,
    fps: Float,
    bitrateMbps: Float,
    latencyMs: Int,
    videoDecoder: TvVideoDecoder,
    audioPlayer: TvAudioPlayer,
    isPipMode: Boolean,
    onEnterPip: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showHud by remember { mutableStateOf(!isPipMode) }
    var isMuted by remember { mutableStateOf(audioPlayer.isMuted) }
    var isFitMode by remember { mutableStateOf(true) }

    LaunchedEffect(showHud, isPipMode) {
        if (showHud && !isPipMode) {
            delay(4000)
            showHud = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isPipMode) showHud = !showHud
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            videoDecoder.setSurface(holder.surface, holder)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            videoDecoder.setSurface(holder.surface, holder)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            videoDecoder.setSurface(null, null)
                        }
                    })
                }
            },
            modifier = if (isFitMode) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            } else {
                Modifier.fillMaxSize()
            }
        )

        if (!isPipMode) {
            AnimatedVisibility(
                visible = showHud,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = TvLayout.screenHorizontalPadding, vertical = TvLayout.screenVerticalPadding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "LIVE • ${camera.name}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item { MetricPill(label = "FPS", value = "%.1f".format(fps)) }
                            item { MetricPill(label = "Bitrate", value = "%.1f Mbps".format(bitrateMbps)) }
                            if (latencyMs > 0) {
                                item { MetricPill(label = "Latency", value = "${latencyMs}ms") }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                PlayerControlButton(
                                    onClick = { isMuted = audioPlayer.toggleMute() },
                                    containerColor = if (isMuted) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (isMuted) {
                                                Icons.AutoMirrored.Filled.VolumeMute
                                            } else {
                                                Icons.AutoMirrored.Filled.VolumeUp
                                            },
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    label = if (isMuted) "Muted" else "Audio on"
                                )
                            }
                            item {
                                PlayerControlButton(
                                    onClick = { isFitMode = !isFitMode },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    label = if (isFitMode) "16:9 fit" else "Fill screen"
                                )
                            }
                            item {
                                PlayerControlButton(
                                    onClick = onEnterPip,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.PictureInPicture,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    label = "Picture in picture"
                                )
                            }
                        }

                        Button(
                            onClick = onDisconnect,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .tvFocusable(shape = RoundedCornerShape(26.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Disconnect",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlButton(
    onClick: () -> Unit,
    containerColor: Color,
    icon: @Composable () -> Unit,
    label: String
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .tvFocusable(shape = RoundedCornerShape(24.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$label: ", fontSize = 12.sp, color = Color.LightGray)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.White
        )
    }
}
