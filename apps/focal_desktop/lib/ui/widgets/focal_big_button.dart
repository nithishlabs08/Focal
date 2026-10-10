import 'package:flutter/material.dart';

import '../../theme/focal_layout.dart';
import '../../theme/focal_theme.dart';

/// LocalSend-style action tile (filled or outline).
class FocalBigButton extends StatelessWidget {
  const FocalBigButton({
    super.key,
    required this.label,
    required this.icon,
    required this.onPressed,
    this.filled = true,
    this.minWidth = FocalLayout.bigButtonMinWidth,
  });

  final String label;
  final IconData icon;
  final VoidCallback? onPressed;
  final bool filled;
  final double minWidth;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    final child = Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(icon, size: 28, color: filled ? cs.onPrimary : cs.primary),
        const SizedBox(height: 10),
        Text(
          label,
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: filled ? cs.onPrimary : cs.onSurface,
          ),
        ),
      ],
    );

    return ConstrainedBox(
      constraints: BoxConstraints(minWidth: minWidth, minHeight: 108),
      child: filled
          ? FilledButton(
              onPressed: onPressed,
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(FocalRadii.card),
                ),
              ),
              child: child,
            )
          : OutlinedButton(
              onPressed: onPressed,
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
                backgroundColor: cs.surfaceContainerLowest,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(FocalRadii.card),
                ),
              ),
              child: child,
            ),
    );
  }
}
