import 'package:flutter/material.dart';

import '../../theme/focal_motion.dart';

/// Cross-fade + directional slide when switching main panes.
class FocalPageSwitcher extends StatelessWidget {
  const FocalPageSwitcher({
    super.key,
    required this.transitionKey,
    required this.slideForward,
    required this.child,
  });

  final Object transitionKey;
  final bool slideForward;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: FocalMotion.page,
      switchInCurve: FocalMotion.emphasizedDecelerate,
      switchOutCurve: FocalMotion.emphasizedAccelerate,
      layoutBuilder: (current, previous) {
        return Stack(
          alignment: Alignment.topCenter,
          fit: StackFit.expand,
          children: [
            ...previous,
            if (current != null) current,
          ],
        );
      },
      transitionBuilder: (widget, animation) {
        final slide = slideForward ? 0.035 : -0.035;
        final offset = Tween<Offset>(
          begin: Offset(slide, 0.02),
          end: Offset.zero,
        ).animate(animation);
        return FadeTransition(
          opacity: animation,
          child: SlideTransition(position: offset, child: widget),
        );
      },
      child: KeyedSubtree(
        key: ValueKey(transitionKey),
        child: child,
      ),
    );
  }
}
