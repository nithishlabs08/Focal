package com.focal.android.tv.ui

import android.Manifest
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focal.android.FocalRoles
import com.focal.android.data.model.StreamMode
import com.focal.android.data.model.StreamSource
import com.focal.android.tv.viewmodel.TvHostViewModel

@Composable
fun TvHostScreen(
    modifier: Modifier = Modifier,
    hostViewModel: TvHostViewModel = viewModel()
) {
    if (!FocalRoles.canHostScreenOrAudioStream) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Screen and audio sharing is not available on this build.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val context = LocalContext.current
    val uiState by hostViewModel.uiState.collectAsState()

    val projectionManager = remember {
        context.getSystemService(MediaProjectionManager::class.java)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hostViewModel.setMicPermissionGranted(granted)
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            hostViewModel.startScreenStream(context, result.resultCode, result.data)
        } else {
            Toast.makeText(context, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        hostViewModel.refreshFromContext(context)
        if (!uiState.isMicPermissionGranted) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    TvBrowseScaffold(
        modifier = modifier,
        header = {
            TvResponsiveHeader(
                branding = {
                    Column {
                        Text(
                            text = "Share from this TV",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Stream your TV screen or microphone to another Focal receiver",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = { }
            )
        }
    ) {
        if (uiState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(14.dp)
            ) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uiState.isStreaming) {
            TvHostLivePanel(
                uiState = uiState,
                onStop = { hostViewModel.stopStream(context) }
            )
        } else {
            Text(
                text = "WHAT TO SHARE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                hostViewModel.availableSources.forEach { source ->
                    val selected = uiState.streamSource == source
                    val icon = when (source) {
                        StreamSource.SCREEN -> Icons.Default.ScreenShare
                        StreamSource.AUDIO_ONLY -> Icons.Default.Mic
                        StreamSource.CAMERA -> Icons.Default.Videocam
                    }
                    Button(
                        onClick = { hostViewModel.setStreamSource(source) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .tvFocusable(shape = RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = source.displayName,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (uiState.streamSource == StreamSource.SCREEN) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include TV microphone",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Adds audio to the screen stream",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.streamMode == StreamMode.VIDEO_AND_AUDIO,
                        onCheckedChange = { enabled ->
                            hostViewModel.setStreamMode(
                                if (enabled) StreamMode.VIDEO_AND_AUDIO else StreamMode.VIDEO_ONLY
                            )
                        },
                        enabled = uiState.isMicPermissionGranted,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    when (uiState.streamSource) {
                        StreamSource.SCREEN -> {
                            val mgr = projectionManager
                            if (mgr == null) {
                                Toast.makeText(
                                    context,
                                    "Screen capture is not available on this device",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (
                                uiState.streamMode == StreamMode.VIDEO_AND_AUDIO &&
                                !uiState.isMicPermissionGranted
                            ) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
                            }
                        }
                        StreamSource.AUDIO_ONLY -> {
                            if (!uiState.isMicPermissionGranted) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                hostViewModel.startAudioStream(context)
                            }
                        }
                        StreamSource.CAMERA -> { /* not offered on TV */ }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .tvFocusable(shape = RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = when (uiState.streamSource) {
                        StreamSource.SCREEN -> "Start screen stream"
                        StreamSource.AUDIO_ONLY -> "Start audio stream"
                        StreamSource.CAMERA -> "Start stream"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Other devices on Wi‑Fi will see this TV in Watch → Available senders while streaming.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TvHostLivePanel(
    uiState: com.focal.android.tv.viewmodel.TvHostUiState,
    onStop: () -> Unit
) {
    val hrs = uiState.elapsedSeconds / 3600
    val mins = (uiState.elapsedSeconds % 3600) / 60
    val secs = uiState.elapsedSeconds % 60
    val duration = "%02d:%02d:%02d".format(hrs, mins, secs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "LIVE • ${uiState.streamSource.displayName} • $duration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TvHostInfoBlock(label = "TV address", value = uiState.deviceIp.ifBlank { "—" })
            TvHostInfoBlock(label = "Pairing PIN", value = uiState.pairingPin)
            TvHostInfoBlock(label = "Viewers", value = uiState.connectedClients.toString())
        }

        Text(
            text = "Open Focal on another device → Watch → select this TV → enter the PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .tvFocusable(shape = RoundedCornerShape(26.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(26.dp)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop sharing", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TvHostInfoBlock(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
