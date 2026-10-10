import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Idle / live illustration: laptop streaming to phone & TV on the LAN.
class FocalStreamPlaceholder extends StatefulWidget {
  const FocalStreamPlaceholder({
    super.key,
    this.active = false,
    this.height = 132,
  });

  /// Brighter, faster motion while screen share is live.
  final bool active;
  final double height;

  @override
  State<FocalStreamPlaceholder> createState() => _FocalStreamPlaceholderState();
}

class _FocalStreamPlaceholderState extends State<FocalStreamPlaceholder>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: widget.active
          ? const Duration(milliseconds: 2200)
          : const Duration(milliseconds: 3200),
    )..repeat();
  }

  @override
  void didUpdateWidget(FocalStreamPlaceholder oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.active != widget.active) {
      _controller.duration = widget.active
          ? const Duration(milliseconds: 2200)
          : const Duration(milliseconds: 3200);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return CustomPaint(
          foregroundPainter: _StreamPathsPainter(
            progress: _controller.value,
            color: cs.primary,
            active: widget.active,
          ),
          child: child,
        );
      },
      child: SizedBox(
        height: widget.height,
        child: Row(
          children: [
            Expanded(
              child: Align(
                alignment: Alignment.centerRight,
                child: _DeviceIcon(
                  icon: Icons.laptop_mac_rounded,
                  size: 52,
                  color: cs.primary,
                  glow: widget.active,
                ),
              ),
            ),
            const SizedBox(width: 8),
            const Expanded(
              flex: 2,
              child: SizedBox.shrink(),
            ),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _DeviceIcon(
                    icon: Icons.smartphone_rounded,
                    size: 34,
                    color: cs.primary.withValues(alpha: 0.9),
                    glow: widget.active,
                  ),
                  const SizedBox(height: 14),
                  _DeviceIcon(
                    icon: Icons.tv_rounded,
                    size: 38,
                    color: cs.primary.withValues(alpha: 0.85),
                    glow: widget.active,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DeviceIcon extends StatelessWidget {
  const _DeviceIcon({
    required this.icon,
    required this.size,
    required this.color,
    required this.glow,
  });

  final IconData icon;
  final double size;
  final Color color;
  final bool glow;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        boxShadow: glow
            ? [
                BoxShadow(
                  color: color.withValues(alpha: 0.35),
                  blurRadius: 16,
                  spreadRadius: 1,
                ),
              ]
            : null,
      ),
      child: Icon(icon, size: size, color: color),
    );
  }
}

class _StreamPathsPainter extends CustomPainter {
  _StreamPathsPainter({
    required this.progress,
    required this.color,
    required this.active,
  });

  final double progress;
  final Color color;
  final bool active;

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    final start = Offset(w * 0.22, h * 0.5);
    final phoneEnd = Offset(w * 0.78, h * 0.32);
    final tvEnd = Offset(w * 0.78, h * 0.72);

    _drawStream(canvas, start, phoneEnd, phase: 0);
    _drawStream(canvas, start, tvEnd, phase: 0.45);
  }

  void _drawStream(Canvas canvas, Offset from, Offset to, {required double phase}) {
    final control = Offset((from.dx + to.dx) / 2, from.dy);
    final path = Path()
      ..moveTo(from.dx, from.dy)
      ..quadraticBezierTo(control.dx, control.dy, to.dx, to.dy);

    final trackAlpha = active ? 0.28 : 0.16;
    final track = Paint()
      ..color = color.withValues(alpha: trackAlpha)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round;

    canvas.drawPath(path, track);

    final dash = Paint()
      ..color = color.withValues(alpha: active ? 0.45 : 0.28)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round;

    for (var i = 0; i < 4; i++) {
      final t0 = ((progress + phase + i * 0.22) % 1.0);
      final t1 = math.min(t0 + 0.08, 1.0);
      if (t1 <= t0) continue;
      final seg = _extractPathSegment(path, t0, t1);
      canvas.drawPath(seg, dash);
    }

    final headT = (progress + phase) % 1.0;
    final head = _pointOnQuadratic(from, control, to, headT);
    final headRadius = active ? 5.5 : 4.5;
    canvas.drawCircle(
      head,
      headRadius,
      Paint()..color = color.withValues(alpha: active ? 0.95 : 0.75),
    );
    canvas.drawCircle(
      head,
      headRadius + 4,
      Paint()..color = color.withValues(alpha: active ? 0.25 : 0.12),
    );
  }

  static Offset _pointOnQuadratic(Offset a, Offset b, Offset c, double t) {
    final u = 1 - t;
    return Offset(
      u * u * a.dx + 2 * u * t * b.dx + t * t * c.dx,
      u * u * a.dy + 2 * u * t * b.dy + t * t * c.dy,
    );
  }

  static Path _extractPathSegment(Path path, double t0, double t1) {
    final metrics = path.computeMetrics().first;
    final len = metrics.length;
    return metrics.extractPath(len * t0, len * t1);
  }

  @override
  bool shouldRepaint(covariant _StreamPathsPainter oldDelegate) =>
      oldDelegate.progress != progress ||
      oldDelegate.color != color ||
      oldDelegate.active != active;
}
