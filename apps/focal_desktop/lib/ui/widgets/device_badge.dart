import 'package:flutter/material.dart';

/// Small pill badge (LocalSend-style IP / model chip).
class DeviceBadge extends StatelessWidget {
  const DeviceBadge({
    super.key,
    required this.label,
  });

  final String label;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: cs.secondaryContainer,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: cs.onSecondaryContainer,
          fontFamily: 'monospace',
        ),
      ),
    );
  }
}
