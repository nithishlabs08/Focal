import 'package:flutter/material.dart';

import '../../theme/focal_theme.dart';

/// Shimmer placeholder while scanning for senders.
class DevicePlaceholderTile extends StatefulWidget {
  const DevicePlaceholderTile({super.key, this.phaseOffset = 0});

  /// Stagger pulse between stacked placeholders (0–1).
  final double phaseOffset;

  @override
  State<DevicePlaceholderTile> createState() => _DevicePlaceholderTileState();
}

class _DevicePlaceholderTileState extends State<DevicePlaceholderTile>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulse;

  @override
  void initState() {
    super.initState();
    _pulse = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
      value: widget.phaseOffset.clamp(0.0, 1.0),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return AnimatedBuilder(
      animation: _pulse,
      builder: (context, child) {
        final t = 0.22 + _pulse.value * 0.18;
        return Opacity(opacity: t, child: child);
      },
      child: Container(
        height: 88,
        decoration: BoxDecoration(
          color: cs.surfaceContainerLow,
          borderRadius: BorderRadius.circular(FocalRadii.tile),
          border: Border.all(color: cs.outline.withValues(alpha: 0.55)),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Row(
          children: [
            _shimmerBox(cs, 52, 52, 16),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _shimmerBox(cs, 14, 140, 4),
                  const SizedBox(height: 8),
                  _shimmerBox(cs, 10, 90, 4),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _shimmerBox(ColorScheme cs, double h, double w, double radius) {
    return AnimatedBuilder(
      animation: _pulse,
      builder: (context, _) {
        final alpha = 0.06 + _pulse.value * 0.08;
        return Container(
          height: h,
          width: w,
          decoration: BoxDecoration(
            color: cs.onSurface.withValues(alpha: alpha),
            borderRadius: BorderRadius.circular(radius),
          ),
        );
      },
    );
  }
}
