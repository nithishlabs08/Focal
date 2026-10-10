package com.focal.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focal.android.settings.AppThemeMode
import com.focal.android.settings.FocalDevicePreferences
import com.focal.android.ui.components.DeviceSettingsForm

@Composable
fun MobileSettingsScreen(
    deviceName: String,
    themeMode: AppThemeMode,
    deviceIp: String,
    onSaveDeviceName: (String) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Name, theme, and network info for this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        DeviceSettingsForm(
            deviceName = deviceName.ifBlank { FocalDevicePreferences.defaultDeviceName() },
            themeMode = themeMode,
            onSaveDeviceName = onSaveDeviceName,
            onThemeModeSelected = onThemeModeSelected,
            showSaveButton = true
        )
        if (deviceIp.isNotBlank()) {
            Text(
                text = "LAN address",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = deviceIp,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Shown to viewers when you host a stream on Wi‑Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
