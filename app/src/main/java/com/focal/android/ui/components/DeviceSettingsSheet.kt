package com.focal.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focal.android.settings.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsSheet(
    visible: Boolean,
    deviceName: String,
    themeMode: AppThemeMode,
    onDismiss: () -> Unit,
    onSaveDeviceName: (String) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nameDraft by remember(deviceName) { mutableStateOf(deviceName) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        DeviceSettingsForm(
            deviceName = nameDraft,
            themeMode = themeMode,
            onSaveDeviceName = {
                onSaveDeviceName(it)
                onDismiss()
            },
            onThemeModeSelected = onThemeModeSelected,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            showSaveButton = true
        )
    }
}
