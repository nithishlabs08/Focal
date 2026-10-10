import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'focal_logo.dart';

/// Large centered mark with dashed ring (LocalSend receive page).
class FocalLogoHero extends StatelessWidget {
  const FocalLogoHero({
    super.key,
    this.logoSize = 88,
    required this.deviceName,
    this.subtitle,
    this.showDeviceName = true,
  });

  final double logoSize;
  final String deviceName;
  final String? subtitle;
  final bool showDeviceName;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final ring = logoSize * 1.42;

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SizedBox(
          width: ring,
          height: ring,
          child: Stack(
            alignment: Alignment.center,
            children: [
              CustomPaint(
                size: Size(ring, ring),
                painter: _DashedRingPainter(
                  color: cs.primary.withValues(alpha: 0.55),
                  strokeWidth: 2.2,
                ),
              ),
              FocalLogo(
                size: logoSize,
                showTile: true,
                accentColor: cs.primary,
                tileColor: cs.surfaceContainerHigh,
              ),
            ],
          ),
        ),
        if (showDeviceName && deviceName.isNotEmpty) ...[
          const SizedBox(height: 22),
          Text(
            deviceName,
            textAlign: TextAlign.center,
            style: theme.textTheme.headlineSmall?.copyWith(
              fontWeight: FontWeight.w700,
              letterSpacing: -0.4,
            ),
          ),
        ],
        if (subtitle != null && subtitle!.isNotEmpty) ...[
          const SizedBox(height: 6),
          Text(
            subtitle!,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: cs.onSurfaceVariant,
            ),
          ),
        ],
      ],
    );
  }
}

class _DashedRingPainter extends CustomPainter {
  _DashedRingPainter({required this.color, required this.strokeWidth});

  final Color color;
  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth;
    final r = size.shortestSide / 2 - strokeWidth;
    final center = Offset(size.width / 2, size.height / 2);
    const dash = 10.0;
    const gap = 7.0;
    final circumference = 2 * math.pi * r;
    final count = (circumference / (dash + gap)).floor();
    for (var i = 0; i < count; i++) {
      final start = (i * (dash + gap)) / r;
      final sweep = dash / r;
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: r),
        start,
        sweep,
        false,
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _DashedRingPainter oldDelegate) =>
      oldDelegate.color != color || oldDelegate.strokeWidth != strokeWidth;
}
