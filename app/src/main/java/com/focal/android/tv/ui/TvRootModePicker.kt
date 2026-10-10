package com.focal.android.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focal.android.FocalRoles
import com.focal.android.tv.model.TvRootMode

@Composable
fun TvRootModePicker(
    selected: TvRootMode,
    onSelected: (TvRootMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val modes = buildList {
        add(TvRootMode.RECEIVE)
        if (FocalRoles.canHostScreenOrAudioStream) {
            add(TvRootMode.SHARE)
        }
    }
    if (modes.size <= 1) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TvLayout.screenHorizontalPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        modes.forEach { mode ->
            val isSelected = selected == mode
            Button(
                onClick = { if (enabled) onSelected(mode) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .tvFocusable(shape = RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
            ) {
                Text(
                    text = mode.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
