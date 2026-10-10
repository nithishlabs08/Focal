import 'package:flutter/material.dart';

/// Minimal tap target (LocalSend `CustomIconButton`).
class FocalIconButton extends StatelessWidget {
  const FocalIconButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.tooltip,
  });

  final IconData icon;
  final VoidCallback? onPressed;
  final String? tooltip;

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: tooltip,
      onPressed: onPressed,
      visualDensity: VisualDensity.compact,
      icon: Icon(icon),
    );
  }
}
