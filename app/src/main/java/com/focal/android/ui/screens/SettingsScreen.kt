package com.focal.android.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focal.android.data.model.OutputProfile
import com.focal.android.ui.viewmodel.FocalUiState

@Composable
fun SettingsScreen(
    uiState: FocalUiState,
    onResolutionChanged: (String) -> Unit = {},
    onFpsChanged: (Int) -> Unit = {},
    onProfileSelected: (OutputProfile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var lowLatencyMode by remember { mutableStateOf(true) }
    var hardwareEncode by remember { mutableStateOf(true) }
    var keepScreenAwake by remember { mutableStateOf(true) }
    var autoOledPowerSave by remember { mutableStateOf(true) }

    val currentResolutionLabel = when {
        uiState.selectedProfile.name.contains("720p", ignoreCase = true) -> "720p"
        uiState.selectedProfile.name.contains("4K", ignoreCase = true) -> "4K"
        else -> "1080p"
    }
    val currentFps = uiState.selectedProfile.fps

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==========================================
        // 1. STREAMING RESOLUTION & FRAME RATE CARD
        // ==========================================
        SettingsCard(
            title = "Streaming Quality & Bandwidth",
            subtitle = "Optimize resolution & framerate for your local Wi-Fi or USB connection",
            icon = Icons.Default.Tune
        ) {
            // Resolution Selection Section
            Text(
                text = "STREAMING RESOLUTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResolutionOptionTile(
                    title = "720p HD",
                    resolutionText = "1280 × 720 px",
                    bandwidthText = "~2.4 Mbps • Low Latency (Best for 2.4 GHz Wi-Fi)",
                    isSelected = currentResolutionLabel == "720p",
                    badge = "Max Battery & Range",
                    onClick = {
                        onResolutionChanged("720p")
                        Toast.makeText(context, "Resolution set to 720p HD", Toast.LENGTH_SHORT).show()
                    },
                    testTag = "settings_res_720p"
                )

                ResolutionOptionTile(
                    title = "1080p Full HD",
                    resolutionText = "1920 × 1080 px",
                    bandwidthText = "~4.8 Mbps • Balanced (Recommended for 5 GHz Wi-Fi)",
                    isSelected = currentResolutionLabel == "1080p",
                    badge = "Default Standard",
                    onClick = {
                        onResolutionChanged("1080p")
                        Toast.makeText(context, "Resolution set to 1080p Full HD", Toast.LENGTH_SHORT).show()
                    },
                    testTag = "settings_res_1080p"
                )

                ResolutionOptionTile(
                    title = "4K Ultra HD (where supported)",
                    resolutionText = "3840 × 2160 px",
                    bandwidthText = "~18.0 Mbps • Studio Fidelity (Recommended for USB 3.0)",
                    isSelected = currentResolutionLabel == "4K",
                    badge = "Studio Grade",
                    onClick = {
                        onResolutionChanged("4K")
                        Toast.makeText(context, "Resolution set to 4K Ultra HD", Toast.LENGTH_SHORT).show()
                    },
                    testTag = "settings_res_4k"
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Frame Rate (FPS) Section
            Text(
                text = "TARGET FRAME RATE (FPS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FpsOptionTile(
                    fps = 30,
                    label = "30 FPS",
                    description = "Smooth standard • Lower CPU & thermal load",
                    isSelected = currentFps == 30,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onFpsChanged(30)
                        Toast.makeText(context, "Frame rate set to 30 FPS", Toast.LENGTH_SHORT).show()
                    },
                    testTag = "settings_fps_30"
                )

                FpsOptionTile(
                    fps = 60,
                    label = "60 FPS (where supported)",
                    description = "Ultra-fluid motion • Higher network throughput",
                    isSelected = currentFps == 60,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onFpsChanged(60)
                        Toast.makeText(context, "Frame rate set to 60 FPS", Toast.LENGTH_SHORT).show()
                    },
                    testTag = "settings_fps_60"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Local Network Performance Impact Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lan,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Active Stream Profile",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${uiState.selectedProfile.bitrateMbps} Mbps target",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "${uiState.selectedProfile.name} • Hardware H.264 / MJPEG socket on :${uiState.serverPort}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val networkRecommendation = when {
                        currentResolutionLabel == "4K" -> "⚠️ Requires high-speed USB 3.0 connection or gigabit Wi-Fi"
                        currentFps == 60 -> "Optimal with 5 GHz Wi-Fi (ac/ax) or USB link"
                        else -> "Compatible with standard 2.4 GHz & 5 GHz Wi-Fi"
                    }

                    Text(
                        text = networkRecommendation,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (currentResolutionLabel == "4K") Color(0xFFD97706) else Color(0xFF006739)
                    )
                }
            }
        }

        // ==========================================
        // 2. VIDEO ENGINE & ACCELERATION
        // ==========================================
        SettingsCard(
            title = "Hardware Encoder & Latency",
            subtitle = "Android MediaCodec & pipeline tuning",
            icon = Icons.Default.Videocam
        ) {
            SettingsToggleRow(
                title = "Hardware H.264 Acceleration",
                subtitle = "Uses on-device MediaCodec hardware encoder for ultra-low CPU load",
                checked = hardwareEncode,
                onCheckedChange = { hardwareEncode = it },
                testTag = "setting_toggle_hw_encode"
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsToggleRow(
                title = "Low-Latency Mode",
                subtitle = "Optimizes hardware encoder buffering for low-latency transmission",
                checked = lowLatencyMode,
                onCheckedChange = { lowLatencyMode = it },
                testTag = "setting_toggle_low_latency"
            )
        }

        // ==========================================
        // 3. POWER & THERMAL MANAGEMENT
        // ==========================================
        SettingsCard(
            title = "Power & Thermal Management",
            subtitle = "Prevent throttling during extended webcam sessions",
            icon = Icons.Default.ModeNight
        ) {
            SettingsToggleRow(
                title = "OLED Blackout Dimming",
                subtitle = "Turns off display pixels while webcam remains broadcasting",
                checked = autoOledPowerSave,
                onCheckedChange = { autoOledPowerSave = it },
                testTag = "setting_toggle_oled_dim"
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsToggleRow(
                title = "Keep Active in Background",
                subtitle = "Foreground service keeps streaming if screen is locked or switching apps",
                checked = keepScreenAwake,
                onCheckedChange = { keepScreenAwake = it },
                testTag = "setting_toggle_keep_awake"
            )
        }

        // ==========================================
        // 4. PRIVACY & ZERO TELEMETRY
        // ==========================================
        SettingsCard(
            title = "Privacy & Local Network Link",
            subtitle = "Local-only / cloud-free operation guarantee",
            icon = Icons.Default.Security
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF08834B).copy(alpha = 0.12f))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF006739),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "100% Local • Zero Telemetry Guaranteed",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF006739)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Focal is strictly local-only and cloud-free, with zero remote telemetry. Video frames and audio streams are piped only through your local Wi-Fi or USB link directly to your computer.",
                    fontSize = 12.sp,
                    color = Color(0xFF00522C),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun ResolutionOptionTile(
    title: String,
    resolutionText: String,
    bandwidthText: String,
    isSelected: Boolean,
    badge: String,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "resBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else Color.Transparent,
        label = "resBorder"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = resolutionText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = bandwidthText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FpsOptionTile(
    fps: Int,
    label: String,
    description: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "fpsBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else Color.Transparent,
        label = "fpsBorder"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = if (subtitle != null) 4.dp else 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        content()
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
