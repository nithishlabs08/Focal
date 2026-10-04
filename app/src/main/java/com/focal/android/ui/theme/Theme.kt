package com.focal.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FocalDarkColorScheme = darkColorScheme(
    primary = LocalSendBlueDark,
    onPrimary = Color(0xFF0B132B),
    primaryContainer = LocalSendBlueDarkContainer,
    onPrimaryContainer = LocalSendBlueOnContainerDark,
    secondary = LocalSendSecondaryDark,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = LocalSendSecondaryContainerDark,
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = LocalSendSuccess,
    onTertiary = Color(0xFF064E3B),
    tertiaryContainer = Color(0xFF065F46),
    onTertiaryContainer = LocalSendSuccessContainer,
    background = LocalSendDarkBackground,
    onBackground = LocalSendDarkOnSurface,
    surface = LocalSendDarkSurface,
    onSurface = LocalSendDarkOnSurface,
    surfaceVariant = LocalSendDarkContainer,
    onSurfaceVariant = LocalSendDarkOnSurfaceVariant,
    surfaceContainerLowest = LocalSendDarkContainerLow,
    surfaceContainerLow = LocalSendDarkCard,
    surfaceContainer = LocalSendDarkContainer,
    surfaceContainerHigh = LocalSendDarkContainerHigh,
    surfaceContainerHighest = LocalSendDarkContainerHighest,
    outline = LocalSendDarkOutline,
    outlineVariant = LocalSendDarkContainerHigh,
    error = LocalSendErrorDark,
    onError = Color(0xFF450A0A)
)

private val FocalLightColorScheme = lightColorScheme(
    primary = LocalSendBlue,
    onPrimary = Color.White,
    primaryContainer = LocalSendBlueLightContainer,
    onPrimaryContainer = LocalSendBlueOnContainerLight,
    secondary = LocalSendSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = LocalSendSecondaryContainerLight,
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = LocalSendSuccess,
    onTertiary = Color.White,
    tertiaryContainer = LocalSendSuccessContainer,
    onTertiaryContainer = Color(0xFF065F46),
    background = LocalSendLightBackground,
    onBackground = LocalSendLightOnSurface,
    surface = LocalSendLightSurface,
    onSurface = LocalSendLightOnSurface,
    surfaceVariant = LocalSendLightContainer,
    onSurfaceVariant = LocalSendLightOnSurfaceVariant,
    surfaceContainerLowest = LocalSendLightCard,
    surfaceContainerLow = LocalSendLightContainerLow,
    surfaceContainer = LocalSendLightContainer,
    surfaceContainerHigh = LocalSendLightContainerHigh,
    surfaceContainerHighest = LocalSendLightContainerHighest,
    outline = LocalSendLightOutline,
    outlineVariant = LocalSendLightContainer,
    error = LocalSendError,
    onError = Color.White
)

@Composable
fun FocalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) FocalDarkColorScheme else FocalLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    FocalTheme(darkTheme = darkTheme, content = content)
}
