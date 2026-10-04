package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FocalDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6F),
    primaryContainer = Color(0xFF00419D),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFB7C8DC),
    onSecondary = Color(0xFF213242),
    secondaryContainer = Color(0xFF384859),
    onSecondaryContainer = Color(0xFFD2E4F9),
    tertiary = Color(0xFF74DB99),
    onTertiary = Color(0xFF00391D),
    tertiaryContainer = Color(0xFF00522C),
    onTertiaryContainer = Color(0xFF90F8B3),
    background = Color(0xFF10131A),
    onBackground = Color(0xFFE0E2EB),
    surface = Color(0xFF10131A),
    onSurface = Color(0xFFE0E2EB),
    surfaceVariant = Color(0xFF424654),
    onSurfaceVariant = Color(0xFFC2C6D7),
    surfaceContainer = Color(0xFF1C2028),
    surfaceContainerLow = Color(0xFF181C22),
    surfaceContainerLowest = Color(0xFF0B0E14),
    surfaceContainerHigh = Color(0xFF262A33),
    surfaceContainerHighest = Color(0xFF31353E),
    outline = Color(0xFF8C91A0),
    outlineVariant = Color(0xFF424654),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val FocalLightColorScheme = lightColorScheme(
    primary = FocalPrimary,
    onPrimary = FocalOnPrimary,
    primaryContainer = FocalPrimaryContainer,
    onPrimaryContainer = FocalOnPrimaryContainer,
    secondary = FocalSecondary,
    onSecondary = FocalOnSecondary,
    secondaryContainer = FocalSecondaryContainer,
    onSecondaryContainer = FocalOnSecondaryContainer,
    tertiary = FocalTertiary,
    onTertiary = FocalOnTertiary,
    tertiaryContainer = FocalTertiaryContainer,
    onTertiaryContainer = FocalOnTertiaryContainer,
    background = FocalBackground,
    onBackground = FocalOnBackground,
    surface = FocalSurface,
    onSurface = FocalOnSurface,
    surfaceVariant = FocalSurfaceVariant,
    onSurfaceVariant = FocalOnSurfaceVariant,
    surfaceContainer = FocalSurfaceContainer,
    surfaceContainerLow = FocalSurfaceContainerLow,
    surfaceContainerLowest = FocalSurfaceContainerLowest,
    surfaceContainerHigh = FocalSurfaceContainerHigh,
    surfaceContainerHighest = FocalSurfaceContainerHighest,
    outline = FocalOutline,
    outlineVariant = FocalOutlineVariant,
    error = FocalError,
    onError = FocalOnError,
    errorContainer = FocalErrorContainer,
    onErrorContainer = FocalOnErrorContainer
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

// Alias for template backwards compatibility if needed
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    FocalTheme(darkTheme = darkTheme, content = content)
}
