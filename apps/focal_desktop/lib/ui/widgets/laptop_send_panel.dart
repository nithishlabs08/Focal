import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../focl/focl_host.dart';
import '../../theme/focal_layout.dart';
import 'device_badge.dart';

/// Share screen from **this computer** (FOCL host).
class LaptopSendPanel extends StatelessWidget {
  const LaptopSendPanel({
    super.key,
    required this.state,
    required this.deviceDisplayName,
    required this.onStart,
    required this.onStop,
  });

  final FoclHostState state;
  final String deviceDisplayName;
  final VoidCallback onStart;
  final VoidCallback onStop;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final hosting = state.isHosting;

    return Padding(
      padding: const EdgeInsets.all(FocalLayout.cardPadding),
      child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.laptop_mac_rounded, color: cs.primary),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Send from this computer',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                if (hosting)
                  Chip(
                    label: const Text('Live'),
                    backgroundColor: cs.primaryContainer,
                    visualDensity: VisualDensity.compact,
                  ),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              'Share your desktop to Focal on your phone or TV (Receive tab). '
              '${Platform.isLinux ? 'On Wayland you pick a monitor or window in the system dialog; needs ffmpeg with PipeWire. On X11 the whole display is captured.' : 'Screen capture: Linux first; pairing works on all desktops.'}',
              style: theme.textTheme.bodySmall?.copyWith(
                color: cs.onSurfaceVariant,
                height: 1.4,
              ),
            ),
            const SizedBox(height: 16),
            if (hosting) ...[
              Text(
                'Pairing PIN',
                style: theme.textTheme.labelLarge?.copyWith(
                  color: cs.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 6),
              Row(
                children: [
                  Text(
                    state.pin,
                    style: theme.textTheme.headlineMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                      letterSpacing: 6,
                      fontFamily: 'monospace',
                    ),
                  ),
                  IconButton(
                    tooltip: 'Copy PIN',
                    onPressed: () {
                      Clipboard.setData(ClipboardData(text: state.pin));
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('PIN copied'),
                          behavior: SnackBarBehavior.floating,
                          width: 240,
                        ),
                      );
                    },
                    icon: const Icon(Icons.copy_rounded, size: 20),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              DeviceBadge(label: '${state.serviceName} · port ${state.port}'),
              if (state.statusMessage.isNotEmpty) ...[
                const SizedBox(height: 12),
                Text(
                  state.statusMessage,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: cs.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
              if (state.viewerCount > 0)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    '${state.viewerCount} viewer(s) connected',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: cs.onSurfaceVariant,
                    ),
                  ),
                ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: onStop,
                  style: FilledButton.styleFrom(
                    backgroundColor: cs.error,
                    foregroundColor: cs.onError,
                    minimumSize: const Size.fromHeight(48),
                  ),
                  icon: const Icon(Icons.stop_rounded),
                  label: const Text('Stop sharing'),
                ),
              ),
            ] else
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: onStart,
                  icon: const Icon(Icons.screen_share_rounded),
                  label: const Text('Share screen'),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(48),
                  ),
                ),
              ),
          ],
      ),
    );
  }
}
