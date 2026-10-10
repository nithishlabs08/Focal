import 'package:flutter/material.dart';

import '../../theme/focal_theme.dart';

/// Compact inline hint row (LAN / security), LocalSend-style accent strip.
class FocalInfoBanner extends StatelessWidget {
  const FocalInfoBanner({
    super.key,
    required this.message,
    this.icon = Icons.wifi_rounded,
    this.trailing,
  });

  final String message;
  final IconData icon;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: cs.primaryContainer.withValues(alpha: 0.45),
        borderRadius: BorderRadius.circular(FocalRadii.card),
        border: Border.all(color: cs.primary.withValues(alpha: 0.2)),
      ),
      child: Row(
        children: [
          Icon(icon, size: 18, color: cs.primary),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              style: theme.textTheme.labelLarge?.copyWith(
                color: cs.onPrimaryContainer,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          if (trailing != null) trailing!,
        ],
      ),
    );
  }
}
