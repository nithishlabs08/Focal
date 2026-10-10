import 'package:flutter/material.dart';

/// Desktop tab typography (LocalSend-like scale, explicit sizes).
abstract final class FocalType {
  static const double appTitle = 32;
  static const double deviceName = 48;
  static const double deviceMeta = 22;
  static const double sectionTitle = 17;
  static const double sectionSubtitle = 14;
  static const double body = 14;
  static const double caption = 12;

  static TextStyle deviceNameStyle(Color color) => TextStyle(
        fontSize: deviceName,
        fontWeight: FontWeight.w600,
        letterSpacing: -1.1,
        height: 1.1,
        color: color,
      );

  static TextStyle deviceMetaStyle(Color color) => TextStyle(
        fontSize: deviceMeta,
        fontWeight: FontWeight.w500,
        letterSpacing: -0.2,
        height: 1.2,
        color: color,
      );

  static TextStyle sectionTitleStyle(Color color) => TextStyle(
        fontSize: sectionTitle,
        fontWeight: FontWeight.w600,
        letterSpacing: -0.25,
        height: 1.25,
        color: color,
      );

  static TextStyle sectionSubtitleStyle(Color color) => TextStyle(
        fontSize: sectionSubtitle,
        fontWeight: FontWeight.w500,
        height: 1.45,
        color: color,
      );
}
