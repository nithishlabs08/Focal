package com.focal.android.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// LocalSend Design System - Modern Slate Palette with Vibrant Blue Accent
// =========================================================================

// Primary accent (focal-web / LocalSend green)
val LocalSendBlue = Color(0xFF006A60)
val LocalSendBlueDark = Color(0xFF82D5C8)
val LocalSendBlueLightContainer = Color(0xFFCCE8E2)
val LocalSendBlueDarkContainer = Color(0xFF1A3D38)
val LocalSendBlueOnContainerLight = Color(0xFF004D45)
val LocalSendBlueOnContainerDark = Color(0xFFB8EDE4)

// Secondary (Slate Neutral Accent)
val LocalSendSecondaryLight = Color(0xFF475569)
val LocalSendSecondaryDark = Color(0xFF94A3B8)
val LocalSendSecondaryContainerLight = Color(0xFFE2E8F0)
val LocalSendSecondaryContainerDark = Color(0xFF2D3748)

// Semantic Accents
val LocalSendSuccess = Color(0xFF10B981)              // Clean Emerald
val LocalSendSuccessContainer = Color(0xFFD1FAE5)
val LocalSendWarning = Color(0xFFF59E0B)              // Warm Amber
val LocalSendError = Color(0xFFEF4444)                // Coral Red
val LocalSendErrorDark = Color(0xFFF87171)

// Dark Theme Surfaces (LocalSend's Deep Charcoal-Slate)
val LocalSendDarkBackground = Color(0xFF0E1513)
val LocalSendDarkSurface = Color(0xFF11151C)
val LocalSendDarkCard = Color(0xFF1A202C)             // Clean card surface
val LocalSendDarkContainerLow = Color(0xFF151B26)
val LocalSendDarkContainer = Color(0xFF1E2638)        // Container
val LocalSendDarkContainerHigh = Color(0xFF263045)    // Interactive pill / button
val LocalSendDarkContainerHighest = Color(0xFF2E3A52) // Borders / toggles
val LocalSendDarkOnSurface = Color(0xFFF1F5F9)        // Crisp text
val LocalSendDarkOnSurfaceVariant = Color(0xFF94A3B8) // Muted slate text
val LocalSendDarkOutline = Color(0xFF334155)

// Light Theme Surfaces (LocalSend's Clean Off-White Slate)
val LocalSendLightBackground = Color(0xFFF4FBF8)
val LocalSendLightSurface = Color(0xFFF8FAFC)
val LocalSendLightCard = Color(0xFFFFFFFF)            // Pure white card
val LocalSendLightContainerLow = Color(0xFFF1F5F9)
val LocalSendLightContainer = Color(0xFFE2E8F0)
val LocalSendLightContainerHigh = Color(0xFFCBD5E1)
val LocalSendLightContainerHighest = Color(0xFF94A3B8)
val LocalSendLightOnSurface = Color(0xFF0F172A)
val LocalSendLightOnSurfaceVariant = Color(0xFF64748B)
val LocalSendLightOutline = Color(0xFFE2E8F0)

// Backwards-compatible aliases
val FocalPrimary = LocalSendBlue
val FocalOnPrimary = Color(0xFFFFFFFF)
val FocalPrimaryContainer = LocalSendBlueLightContainer
val FocalOnPrimaryContainer = LocalSendBlueOnContainerLight
val FocalPrimaryFixed = LocalSendBlueLightContainer
val FocalOnPrimaryFixed = LocalSendBlueOnContainerLight
val FocalOnPrimaryFixedVariant = LocalSendBlue

val FocalSecondary = LocalSendSecondaryLight
val FocalOnSecondary = Color(0xFFFFFFFF)
val FocalSecondaryContainer = LocalSendSecondaryContainerLight
val FocalOnSecondaryContainer = Color(0xFF0F172A)
val FocalSecondaryFixed = LocalSendSecondaryContainerLight
val FocalOnSecondaryFixed = Color(0xFF0F172A)

val FocalTertiary = LocalSendSuccess
val FocalOnTertiary = Color(0xFFFFFFFF)
val FocalTertiaryContainer = LocalSendSuccessContainer
val FocalOnTertiaryContainer = Color(0xFF065F46)
val FocalTertiaryFixed = LocalSendSuccessContainer
val FocalOnTertiaryFixed = Color(0xFF065F46)

val FocalBackground = LocalSendLightBackground
val FocalOnBackground = LocalSendLightOnSurface
val FocalSurface = LocalSendLightSurface
val FocalOnSurface = LocalSendLightOnSurface
val FocalSurfaceVariant = LocalSendLightContainer
val FocalOnSurfaceVariant = LocalSendLightOnSurfaceVariant

val FocalSurfaceContainerLowest = LocalSendLightCard
val FocalSurfaceContainerLow = LocalSendLightContainerLow
val FocalSurfaceContainer = LocalSendLightContainer
val FocalSurfaceContainerHigh = LocalSendLightContainerHigh
val FocalSurfaceContainerHighest = LocalSendLightContainerHighest

val FocalError = LocalSendError
val FocalOnError = Color(0xFFFFFFFF)
val FocalErrorContainer = Color(0xFFFEE2E2)
val FocalOnErrorContainer = Color(0xFF991B1B)

val FocalOutline = LocalSendLightOutline
val FocalOutlineVariant = LocalSendLightContainer
val FocalInverseSurface = LocalSendDarkCard
val FocalInverseOnSurface = LocalSendDarkOnSurface
