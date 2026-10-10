import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../../theme/focal_theme.dart';

/// Canonical Focal mark from `assets/focal_mark.svg` (same geometry as `branding/favicon.svg`).
class FocalLogo extends StatelessWidget {
  const FocalLogo({
    super.key,
    required this.size,
    this.showTile = true,
    this.accentColor,
    this.tileColor,
  });

  final double size;
  final bool showTile;
  final Color? accentColor;
  final Color? tileColor;

  static const _markAsset = 'assets/focal_mark.svg';

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final accent = accentColor ?? FocalColors.primary;
    final tile = tileColor ??
        (isDark ? cs.surfaceContainerHigh : FocalColors.surfaceLight);
    final radius = size * 14 / 64;
    final pad = size * 0.14;

    return SizedBox(
      width: size,
      height: size,
      child: DecoratedBox(
        decoration: showTile
            ? BoxDecoration(
                color: tile,
                borderRadius: BorderRadius.circular(radius),
                border: Border.all(
                  color: cs.outlineVariant.withValues(alpha: isDark ? 0.4 : 0.22),
                ),
              )
            : const BoxDecoration(),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(radius),
          child: Padding(
            padding: EdgeInsets.all(pad),
            child: SvgPicture.asset(
              _markAsset,
              fit: BoxFit.contain,
              colorFilter: ColorFilter.mode(accent, BlendMode.srcIn),
              semanticsLabel: 'Focal',
            ),
          ),
        ),
      ),
    );
  }
}
