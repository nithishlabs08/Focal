import 'package:flutter/material.dart';

import '../../models/discovered_sender.dart';
import '../../theme/focal_layout.dart';
import '../../theme/focal_theme.dart';
import 'device_badge.dart';

/// LocalSend-inspired device row: rounded card, icon, name, IP badge, chevron.
class DeviceListTile extends StatefulWidget {
  const DeviceListTile({
    super.key,
    required this.sender,
    required this.displayName,
    required this.onTap,
    this.enabled = true,
  });

  final DiscoveredSender sender;
  final String displayName;
  final VoidCallback? onTap;
  final bool enabled;

  @override
  State<DeviceListTile> createState() => _DeviceListTileState();
}

class _DeviceListTileState extends State<DeviceListTile> {
  bool _hover = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final elevated = _hover && widget.enabled;
    final hasAlias = widget.displayName != widget.sender.name;

    return MouseRegion(
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() => _hover = false),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        curve: Curves.easeOutCubic,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(FocalRadii.tile),
          color: cs.surfaceContainerLowest,
          border: Border.all(
            color: elevated
                ? cs.primary.withValues(alpha: 0.45)
                : cs.outline.withValues(alpha: 0.65),
          ),
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: widget.enabled ? widget.onTap : null,
            borderRadius: BorderRadius.circular(FocalRadii.tile),
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: FocalLayout.cardPadding,
                vertical: 16,
              ),
              child: Row(
                children: [
                  Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: cs.primaryContainer.withValues(alpha: 0.55),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Icon(
                      widget.sender.isManual
                          ? Icons.lan_rounded
                          : Icons.smartphone_rounded,
                      color: cs.primary,
                      size: 28,
                    ),
                  ),
                  const SizedBox(width: FocalLayout.rowIconGap),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.displayName,
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w600,
                            letterSpacing: -0.2,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        if (hasAlias) ...[
                          const SizedBox(height: 2),
                          Text(
                            widget.sender.name,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: cs.onSurfaceVariant,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                        const SizedBox(height: 8),
                        DeviceBadge(label: widget.sender.endpoint),
                      ],
                    ),
                  ),
                  Icon(
                    Icons.chevron_right_rounded,
                    color: widget.enabled ? cs.primary : cs.outline,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
