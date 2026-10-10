package com.focal.android.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom focus ring for Android TV D-Pad navigation.
 * Renders a glowing LocalSend vibrant blue border when an item is selected by the remote control.
 */
fun Modifier.tvFocusable(
    shape: Shape = RoundedCornerShape(16.dp),
    focusedBorderWidth: Dp = 3.dp,
    unfocusedBorderWidth: Dp = 0.dp,
    focusedBorderColor: Color? = null,
    unfocusedBorderColor: Color = Color.Transparent,
    interactionSource: InteractionSource? = null
): Modifier = composed {
    val source = interactionSource as? MutableInteractionSource
        ?: remember { MutableInteractionSource() }
    val isFocused by source.collectIsFocusedAsState()

    val targetColor = if (isFocused) {
        focusedBorderColor ?: MaterialTheme.colorScheme.primary
    } else {
        unfocusedBorderColor
    }

    val borderColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 150),
        label = "tvFocusBorderColor"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) focusedBorderWidth else unfocusedBorderWidth,
        animationSpec = tween(durationMillis = 150),
        label = "tvFocusBorderWidth"
    )

    this
        .focusable(interactionSource = source)
        .border(width = borderWidth, color = borderColor, shape = shape)
}
