import 'package:flutter/material.dart';

import '../../models/discovered_sender.dart';
import '../../theme/focal_layout.dart';
import 'device_list_tile.dart';
import 'device_placeholder_tile.dart';
import 'focal_entrance.dart';

/// One- or two-column device list (LocalSend desktop grid).
class FocalDeviceGrid extends StatelessWidget {
  const FocalDeviceGrid({
    super.key,
    required this.senders,
    required this.senderLabel,
    required this.onSelect,
    this.emptyHint,
  });

  final List<DiscoveredSender> senders;
  final String Function(DiscoveredSender) senderLabel;
  final void Function(DiscoveredSender) onSelect;
  final String? emptyHint;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final twoCol = constraints.maxWidth >= FocalLayout.twoColumnMinWidth;
        final gap = FocalLayout.cardGap;

        if (senders.isEmpty) {
          final placeholders = [
            const DevicePlaceholderTile(phaseOffset: 0),
            const DevicePlaceholderTile(phaseOffset: 1),
          ];
          if (!twoCol) {
            return Column(
              children: [
                for (var i = 0; i < placeholders.length; i++) ...[
                  if (i > 0) SizedBox(height: gap),
                  placeholders[i],
                ],
                if (emptyHint != null) ...[
                  const SizedBox(height: FocalLayout.blockGap),
                  _EmptyHint(text: emptyHint!),
                ],
              ],
            );
          }
          final tileWidth = (constraints.maxWidth - gap) / 2;
          return Column(
            children: [
              Row(
                children: [
                  SizedBox(width: tileWidth, child: placeholders[0]),
                  SizedBox(width: gap),
                  SizedBox(width: tileWidth, child: placeholders[1]),
                ],
              ),
              if (emptyHint != null) ...[
                const SizedBox(height: FocalLayout.blockGap),
                _EmptyHint(text: emptyHint!),
              ],
            ],
          );
        }

        if (!twoCol) {
          return Column(
            children: [
              for (var i = 0; i < senders.length; i++)
                Padding(
                  padding: EdgeInsets.only(bottom: i < senders.length - 1 ? gap : 0),
                  child: FocalEntrance(
                    key: ValueKey(senders[i].id),
                    index: i,
                    child: DeviceListTile(
                      sender: senders[i],
                      displayName: senderLabel(senders[i]),
                      onTap: () => onSelect(senders[i]),
                    ),
                  ),
                ),
            ],
          );
        }

        final tileWidth = (constraints.maxWidth - gap) / 2;
        final rows = <Widget>[];
        for (var i = 0; i < senders.length; i += 2) {
          rows.add(
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(
                  width: tileWidth,
                  child: FocalEntrance(
                    key: ValueKey(senders[i].id),
                    index: i,
                    child: DeviceListTile(
                      sender: senders[i],
                      displayName: senderLabel(senders[i]),
                      onTap: () => onSelect(senders[i]),
                    ),
                  ),
                ),
                SizedBox(width: gap),
                if (i + 1 < senders.length)
                  SizedBox(
                    width: tileWidth,
                    child: FocalEntrance(
                      key: ValueKey(senders[i + 1].id),
                      index: i + 1,
                      child: DeviceListTile(
                        sender: senders[i + 1],
                        displayName: senderLabel(senders[i + 1]),
                        onTap: () => onSelect(senders[i + 1]),
                      ),
                    ),
                  )
                else
                  SizedBox(width: tileWidth),
              ],
            ),
          );
          if (i + 2 < senders.length) {
            rows.add(SizedBox(height: gap));
          }
        }
        return Column(children: rows);
      },
    );
  }
}

class _EmptyHint extends StatelessWidget {
  const _EmptyHint({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Text(
      text,
      textAlign: TextAlign.center,
      style: theme.textTheme.bodyMedium?.copyWith(
        color: theme.colorScheme.onSurfaceVariant,
        height: 1.45,
      ),
    );
  }
}
