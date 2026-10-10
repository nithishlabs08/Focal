import 'package:flutter/material.dart';

import '../../theme/focal_motion.dart';

/// Fade + slight slide when a widget enters the tree (lists, cards).
class FocalEntrance extends StatefulWidget {
  const FocalEntrance({
    super.key,
    required this.child,
    this.index = 0,
    this.axis = Axis.vertical,
  });

  final Widget child;
  final int index;
  final Axis axis;

  @override
  State<FocalEntrance> createState() => _FocalEntranceState();
}

class _FocalEntranceState extends State<FocalEntrance>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _opacity;
  late final Animation<Offset> _offset;

  @override
  void initState() {
    super.initState();
    final delayMs = FocalMotion.staggerStep.inMilliseconds * widget.index;
    _controller = AnimationController(
      vsync: this,
      duration: FocalMotion.medium + Duration(milliseconds: delayMs),
    );
    final interval = delayMs == 0
        ? const Interval(0, 1, curve: FocalMotion.emphasizedDecelerate)
        : Interval(
            delayMs / (FocalMotion.medium.inMilliseconds + delayMs),
            1,
            curve: FocalMotion.emphasizedDecelerate,
          );
    _opacity = CurvedAnimation(parent: _controller, curve: interval);
    final begin = widget.axis == Axis.vertical
        ? const Offset(0, 0.06)
        : const Offset(0.04, 0);
    _offset = Tween<Offset>(begin: begin, end: Offset.zero).animate(_opacity);
    _controller.forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: _opacity,
      child: SlideTransition(
        position: _offset,
        child: widget.child,
      ),
    );
  }
}
