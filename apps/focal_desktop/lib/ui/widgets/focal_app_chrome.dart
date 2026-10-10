import 'package:flutter/material.dart';

/// Top content bar (LocalSend: app title + trailing actions).
class FocalAppChrome extends StatelessWidget {
  const FocalAppChrome({
    super.key,
    this.actions = const [],
  });

  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        children: [
          Text(
            'Focal',
            style: theme.textTheme.titleLarge?.copyWith(
              fontWeight: FontWeight.w700,
              letterSpacing: -0.4,
            ),
          ),
          const Spacer(),
          ...actions,
        ],
      ),
    );
  }
}
