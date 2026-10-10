import 'package:flutter/material.dart';

/// Matches Android Focal / LocalSend palette (`ui/theme/Color.kt`).
class FocalColors {
  static const primary = Color(0xFF006A60);
  static const primaryDark = Color(0xFF82D5C8);
  static const primaryContainerLight = Color(0xFFCCE8E2);
  static const onPrimaryContainerLight = Color(0xFF004D45);
  static const primaryContainerDark = Color(0xFF1A3D38);
  static const onPrimaryContainerDark = Color(0xFFB8EDE4);

  static const backgroundLight = Color(0xFFF4FBF8);
  static const surfaceLight = Color(0xFFF8FAFC);
  static const cardLight = Color(0xFFFFFFFF);
  static const onSurfaceLight = Color(0xFF0F172A);
  static const onSurfaceVariantLight = Color(0xFF64748B);
  static const outlineLight = Color(0xFFE2E8F0);
  static const containerLowLight = Color(0xFFF1F5F9);
  static const containerLight = Color(0xFFE2E8F0);

  static const backgroundDark = Color(0xFF0E1513);
  static const surfaceDark = Color(0xFF11151C);
  static const cardDark = Color(0xFF1A202C);
  static const onSurfaceDark = Color(0xFFF1F5F9);
  static const onSurfaceVariantDark = Color(0xFF94A3B8);
  static const outlineDark = Color(0xFF334155);
  static const containerLowDark = Color(0xFF151B26);
  static const containerDark = Color(0xFF1E2638);
}

class FocalTheme {
  static ThemeData light() => _base(_lightScheme, FocalColors.backgroundLight);

  static ThemeData dark() => _base(_darkScheme, FocalColors.backgroundDark);

  static ColorScheme get _lightScheme => const ColorScheme(
        brightness: Brightness.light,
        primary: FocalColors.primary,
        onPrimary: Colors.white,
        primaryContainer: FocalColors.primaryContainerLight,
        onPrimaryContainer: FocalColors.onPrimaryContainerLight,
        secondary: Color(0xFF475569),
        onSecondary: Colors.white,
        secondaryContainer: Color(0xFFE2E8F0),
        onSecondaryContainer: Color(0xFF334155),
        tertiary: Color(0xFF10B981),
        onTertiary: Colors.white,
        tertiaryContainer: Color(0xFFD1FAE5),
        onTertiaryContainer: Color(0xFF065F46),
        error: Color(0xFFEF4444),
        onError: Colors.white,
        errorContainer: Color(0xFFFEE2E2),
        onErrorContainer: Color(0xFF991B1B),
        surface: FocalColors.surfaceLight,
        onSurface: FocalColors.onSurfaceLight,
        onSurfaceVariant: FocalColors.onSurfaceVariantLight,
        outline: FocalColors.outlineLight,
        outlineVariant: FocalColors.containerLight,
        shadow: Colors.black,
        scrim: Colors.black,
        inverseSurface: FocalColors.cardDark,
        onInverseSurface: FocalColors.onSurfaceDark,
        inversePrimary: FocalColors.primaryDark,
        surfaceTint: FocalColors.primary,
        surfaceContainerHighest: Color(0xFF94A3B8),
        surfaceContainerHigh: Color(0xFFCBD5E1),
        surfaceContainer: FocalColors.containerLight,
        surfaceContainerLow: FocalColors.containerLowLight,
        surfaceContainerLowest: FocalColors.cardLight,
      );

  static ColorScheme get _darkScheme => const ColorScheme(
        brightness: Brightness.dark,
        primary: FocalColors.primaryDark,
        onPrimary: Color(0xFF00332E),
        primaryContainer: FocalColors.primaryContainerDark,
        onPrimaryContainer: FocalColors.onPrimaryContainerDark,
        secondary: Color(0xFF94A3B8),
        onSecondary: Color(0xFF1E293B),
        secondaryContainer: Color(0xFF2D3748),
        onSecondaryContainer: Color(0xFFE2E8F0),
        tertiary: Color(0xFF10B981),
        onTertiary: Color(0xFF064E3B),
        tertiaryContainer: Color(0xFF065F46),
        onTertiaryContainer: Color(0xFFD1FAE5),
        error: Color(0xFFF87171),
        onError: Color(0xFF450A0A),
        errorContainer: Color(0xFF7F1D1D),
        onErrorContainer: Color(0xFFFECACA),
        surface: FocalColors.surfaceDark,
        onSurface: FocalColors.onSurfaceDark,
        onSurfaceVariant: FocalColors.onSurfaceVariantDark,
        outline: FocalColors.outlineDark,
        outlineVariant: Color(0xFF263045),
        shadow: Colors.black,
        scrim: Colors.black,
        inverseSurface: FocalColors.cardLight,
        onInverseSurface: FocalColors.onSurfaceLight,
        inversePrimary: FocalColors.primary,
        surfaceTint: FocalColors.primaryDark,
        surfaceContainerHighest: Color(0xFF2E3A52),
        surfaceContainerHigh: Color(0xFF263045),
        surfaceContainer: FocalColors.containerDark,
        surfaceContainerLow: FocalColors.cardDark,
        surfaceContainerLowest: FocalColors.containerLowDark,
      );

  static ThemeData _base(ColorScheme scheme, Color scaffoldBg) {
    final text = TextTheme(
      headlineMedium: TextStyle(
        fontSize: 28,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.6,
        height: 1.2,
        color: scheme.onSurface,
      ),
      headlineSmall: TextStyle(
        fontSize: 22,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.4,
        height: 1.25,
        color: scheme.onSurface,
      ),
      titleLarge: TextStyle(
        fontSize: 18,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.3,
        color: scheme.onSurface,
      ),
      titleMedium: TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w600,
        letterSpacing: -0.2,
        color: scheme.onSurface,
      ),
      bodyLarge: TextStyle(
        fontSize: 15,
        height: 1.5,
        color: scheme.onSurface,
      ),
      bodyMedium: TextStyle(
        fontSize: 14,
        height: 1.45,
        color: scheme.onSurface,
      ),
      bodySmall: TextStyle(
        fontSize: 13,
        height: 1.4,
        color: scheme.onSurfaceVariant,
      ),
      labelLarge: TextStyle(
        fontSize: 13,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.1,
        color: scheme.onSurfaceVariant,
      ),
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: scaffoldBg,
      textTheme: text,
      visualDensity: VisualDensity.standard,
      splashFactory: InkSparkle.splashFactory,
      dividerTheme: DividerThemeData(
        color: scheme.outline.withValues(alpha: 0.65),
        thickness: 1,
        space: 1,
      ),
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {
          TargetPlatform.linux: FadeUpwardsPageTransitionsBuilder(),
          TargetPlatform.windows: FadeUpwardsPageTransitionsBuilder(),
          TargetPlatform.macOS: FadeUpwardsPageTransitionsBuilder(),
        },
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: scheme.surfaceContainerLow,
        indicatorColor: scheme.primaryContainer,
        indicatorShape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        selectedIconTheme: IconThemeData(color: scheme.primary, size: 24),
        unselectedIconTheme: IconThemeData(
          color: scheme.onSurfaceVariant,
          size: 24,
        ),
        selectedLabelTextStyle: TextStyle(
          color: scheme.primary,
          fontWeight: FontWeight.w600,
          fontSize: 12,
        ),
        unselectedLabelTextStyle: TextStyle(
          color: scheme.onSurfaceVariant,
          fontWeight: FontWeight.w500,
          fontSize: 12,
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: ButtonStyle(
          shape: WidgetStatePropertyAll(
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: scheme.surfaceContainerLowest,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(FocalRadii.card),
          side: BorderSide(color: scheme.outline.withValues(alpha: 0.55)),
        ),
        clipBehavior: Clip.antiAlias,
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: scheme.surfaceContainerLowest,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        insetPadding: const EdgeInsets.symmetric(horizontal: 40, vertical: 32),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(FocalRadii.button),
          ),
          elevation: 0,
          textStyle: const TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(FocalRadii.button),
          ),
          side: BorderSide(color: scheme.outline),
          foregroundColor: scheme.onSurface,
          textStyle: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: scheme.surfaceContainerLow,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(FocalRadii.button),
          borderSide: BorderSide(color: scheme.outline),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(FocalRadii.button),
          borderSide: BorderSide(color: scheme.outline),
        ),
      ),
      listTileTheme: ListTileThemeData(
        iconColor: scheme.primary,
        contentPadding: const EdgeInsets.symmetric(horizontal: 4),
      ),
    );
  }
}

/// Shared corner radii (LocalSend desktop cards ~16px).
class FocalRadii {
  static const double card = 16;
  static const double button = 14;
  static const double tile = 16;
}
