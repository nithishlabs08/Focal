import 'package:flutter/material.dart';

import '../../theme/focal_layout.dart';
import '../../theme/focal_theme.dart';

/// Bottom inset card (LocalSend “Quick Save” bar style).
class FocalBottomPanel extends StatelessWidget {
  const FocalBottomPanel({
    super.key,
    required this.title,
    required this.child,
  });

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;

    return Card(
      margin: EdgeInsets.zero,
      color: cs.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(FocalRadii.card),
        side: BorderSide(color: cs.outline.withValues(alpha: 0.45)),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          FocalLayout.cardPadding,
          14,
          FocalLayout.cardPadding,
          14,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              title,
              style: theme.textTheme.titleMedium,
            ),
            const SizedBox(height: 12),
            child,
          ],
        ),
      ),
    );
  }
}
