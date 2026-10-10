import 'package:flutter/material.dart';

import '../../theme/focal_typography.dart';
import 'focal_entrance.dart';
import 'focal_logo_hero.dart';

/// Animated identity block (Receive / Send tabs).
class FocalTabHero extends StatelessWidget {
  const FocalTabHero({
    super.key,
    required this.deviceName,
    this.metaLine,
    this.metaLineStyle,
    this.extraBelowMeta,
    this.minHeight = 300,
  });

  final String deviceName;
  final String? metaLine;
  final TextStyle? metaLineStyle;
  final Widget? extraBelowMeta;
  final double minHeight;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    final compact = minHeight < 240;
    final logoSize = compact ? 64.0 : 80.0;

    return SizedBox(
      height: minHeight,
      width: double.infinity,
      child: FittedBox(
        fit: BoxFit.scaleDown,
        alignment: Alignment.center,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            FocalEntrance(
              index: 0,
              child: FocalLogoHero(
                logoSize: logoSize,
                deviceName: '',
                showDeviceName: false,
              ),
            ),
            FocalEntrance(
              index: 1,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Text(
                  deviceName,
                  textAlign: TextAlign.center,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: FocalType.deviceNameStyle(cs.onSurface),
                ),
              ),
            ),
            if (metaLine != null && metaLine!.isNotEmpty) ...[
              SizedBox(height: compact ? 6 : 10),
              FocalEntrance(
                index: 2,
                child: Text(
                  metaLine!,
                  textAlign: TextAlign.center,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: metaLineStyle ??
                      FocalType.deviceMetaStyle(cs.onSurfaceVariant),
                ),
              ),
            ],
            if (extraBelowMeta != null) ...[
              SizedBox(height: compact ? 4 : 8),
              FocalEntrance(index: 3, child: extraBelowMeta!),
            ],
          ],
        ),
      ),
    );
  }
}
