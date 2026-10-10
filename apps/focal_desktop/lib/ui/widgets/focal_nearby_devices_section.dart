import 'package:flutter/material.dart';

import '../../models/discovered_sender.dart';
import 'device_placeholder_tile.dart';
import 'focal_device_grid.dart';
import 'focal_icon_button.dart';

const _hPad = 15.0;

/// LocalSend send tab: title row + icon toolbar + device list.
class FocalNearbyDevicesSection extends StatelessWidget {
  const FocalNearbyDevicesSection({
    super.key,
    required this.title,
    this.titleStyle,
    required this.senders,
    required this.senderLabel,
    required this.onSelect,
    required this.onRefresh,
    this.onManualConnect,
    this.emptyHint,
  });

  final String title;
  final TextStyle? titleStyle;
  final List<DiscoveredSender> senders;
  final String Function(DiscoveredSender) senderLabel;
  final void Function(DiscoveredSender) onSelect;
  final VoidCallback onRefresh;
  final VoidCallback? onManualConnect;
  final String? emptyHint;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            const SizedBox(width: _hPad),
            Flexible(
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 10),
                child: Text(
                  title,
                  style: titleStyle ?? theme.textTheme.titleMedium,
                ),
              ),
            ),
            FocalIconButton(
              tooltip: 'Refresh',
              icon: Icons.sync_rounded,
              onPressed: onRefresh,
            ),
            if (onManualConnect != null)
              FocalIconButton(
                tooltip: 'Connect by IP',
                icon: Icons.ads_click_rounded,
                onPressed: onManualConnect,
              ),
            const SizedBox(width: 4),
          ],
        ),
        if (senders.isEmpty)
          const Padding(
            padding: EdgeInsets.only(
              bottom: 10,
              left: _hPad,
              right: _hPad,
            ),
            child: Opacity(
              opacity: 0.35,
              child: DevicePlaceholderTile(),
            ),
          )
        else
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: _hPad),
            child: FocalDeviceGrid(
              senders: senders,
              senderLabel: senderLabel,
              onSelect: onSelect,
              emptyHint: null,
            ),
          ),
        if (senders.isEmpty && emptyHint != null) ...[
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: _hPad),
            child: Text(
              emptyHint!,
              textAlign: TextAlign.center,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
        const SizedBox(height: 10),
      ],
    );
  }
}
