package com.focal.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focal.android.settings.AppThemeMode
import com.focal.android.settings.FocalDevicePreferences

@Composable
fun DeviceSettingsForm(
    deviceName: String,
    themeMode: AppThemeMode,
    onSaveDeviceName: (String) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    showSaveButton: Boolean = true
) {
    var nameDraft by remember(deviceName) { mutableStateOf(deviceName) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Device & appearance",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Other devices see this name on the network (like LocalSend).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = nameDraft,
            onValueChange = { nameDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Device name") },
            placeholder = { Text(FocalDevicePreferences.defaultDeviceName()) },
            singleLine = true
        )
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleSmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeSelected(mode) },
                    label = {
                        Text(
                            when (mode) {
                                AppThemeMode.SYSTEM -> "System"
                                AppThemeMode.LIGHT -> "Light"
                                AppThemeMode.DARK -> "Dark"
                            }
                        )
                    }
                )
            }
        }
        if (showSaveButton) {
            Button(
                onClick = { onSaveDeviceName(nameDraft.trim()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save device name")
            }
        }
    }
}
