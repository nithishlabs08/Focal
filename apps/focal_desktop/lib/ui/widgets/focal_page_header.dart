import 'package:flutter/material.dart';

import '../../theme/focal_layout.dart';

class FocalPageHeader extends StatelessWidget {
  const FocalPageHeader({
    super.key,
    required this.title,
    this.subtitle,
    this.center = false,
  });

  final String title;
  final String? subtitle;
  final bool center;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final align = center ? TextAlign.center : TextAlign.start;

    return Column(
      crossAxisAlignment:
          center ? CrossAxisAlignment.center : CrossAxisAlignment.start,
      children: [
        Text(
          title,
          textAlign: align,
          style: theme.textTheme.headlineMedium,
        ),
        if (subtitle != null) ...[
          const SizedBox(height: FocalLayout.titleBottomGap),
          Text(
            subtitle!,
            textAlign: align,
            style: theme.textTheme.bodyLarge?.copyWith(
              color: cs.onSurfaceVariant,
              height: 1.5,
            ),
          ),
        ],
        const SizedBox(height: FocalLayout.subtitleBottomGap),
      ],
    );
  }
}
