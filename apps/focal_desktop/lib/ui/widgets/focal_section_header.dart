import 'package:flutter/material.dart';

import '../../theme/focal_layout.dart';

/// Section title with optional trailing icon actions (LocalSend send tab toolbar).
class FocalSectionHeader extends StatelessWidget {
  const FocalSectionHeader({
    super.key,
    required this.title,
    this.actions = const [],
    this.subtitle,
  });

  final String title;
  final String? subtitle;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;

    return Padding(
      padding: const EdgeInsets.only(bottom: FocalLayout.cardGap),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: theme.textTheme.titleLarge,
                ),
              ),
              for (var i = 0; i < actions.length; i++) ...[
                if (i > 0) const SizedBox(width: 4),
                actions[i],
              ],
            ],
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 6),
            Text(
              subtitle!,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: cs.onSurfaceVariant,
                height: 1.45,
              ),
            ),
          ],
        ],
      ),
    );
  }
}
