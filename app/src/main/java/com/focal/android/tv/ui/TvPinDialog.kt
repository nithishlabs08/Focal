package com.focal.android.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.focal.android.tv.model.DiscoveredCamera

@Composable
fun TvPinDialog(
    camera: DiscoveredCamera,
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier.width(460.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Connect to ${camera.name}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Enter the 6-digit PIN shown on your phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 6-digit boxes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 6) {
                        val digit = pin.getOrNull(i)?.toString() ?: ""
                        val isCurrent = i == pin.length
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = digit,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // D-Pad On-Screen Keypad for TV Remote
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "DEL")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (row in keys) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (key in row) {
                                KeypadButton(
                                    label = key,
                                    onClick = {
                                        when (key) {
                                            "DEL" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            "C" -> pin = ""
                                            else -> if (pin.length < 6) pin += key
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .tvFocusable(shape = RoundedCornerShape(24.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurface)
                    }

                    Button(
                        onClick = {
                            if (pin.length >= 4) {
                                onConnect(pin)
                            } else {
                                errorMessage = "PIN must be at least 4 digits"
                            }
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .tvFocusable(shape = RoundedCornerShape(24.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(text = "Connect", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .tvFocusable(shape = RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (label == "DEL") {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Delete",
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
