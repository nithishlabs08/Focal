import 'package:flutter/animation.dart';

/// Shared motion timing (LocalSend-style snappy desktop).
abstract final class FocalMotion {
  static const Duration page = Duration(milliseconds: 320);
  static const Duration medium = Duration(milliseconds: 260);
  static const Duration fast = Duration(milliseconds: 180);
  static const Duration staggerStep = Duration(milliseconds: 45);

  static const Curve emphasizedDecelerate = Curves.easeOutCubic;
  static const Curve emphasizedAccelerate = Curves.easeInCubic;
  static const Curve standard = Curves.easeInOutCubic;
}
