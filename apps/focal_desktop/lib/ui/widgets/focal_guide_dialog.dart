import 'package:flutter/material.dart';

import '../../theme/focal_typography.dart';

Future<void> showReceiveGuide(BuildContext context) {
  return showDialog<void>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(
        'How to receive',
        style: FocalType.sectionTitleStyle(Theme.of(ctx).colorScheme.onSurface),
      ),
      content: const SingleChildScrollView(
        child: Text(
          '1. On your phone, open Focal → Send and start Camera, Screen, or Audio.\n'
          '2. Wait for your phone under Nearby devices on this tab.\n'
          '3. Tap it and enter the 4-digit PIN from the phone.\n'
          '4. Video plays live in this app over FOCL (same idea as Receive on your phone).\n'
          'Optional: Settings → also save to ~/focal_capture.h264 for VLC/archive.\n\n'
          'Same Wi‑Fi only. Turn off VPN or allow local/LAN traffic in your VPN app.\n'
          'Use Connect by IP if the phone does not appear.',
          style: TextStyle(fontSize: FocalType.body, height: 1.5),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx),
          child: const Text('Got it'),
        ),
      ],
    ),
  );
}

Future<void> showReceiveDetails(
  BuildContext context, {
  required String deviceName,
  required String lanLine,
  required bool useTls,
}) {
  return showDialog<void>(
    context: context,
    builder: (ctx) {
      final cs = Theme.of(ctx).colorScheme;
      return AlertDialog(
        title: Text(
          'Receive details',
          style: FocalType.sectionTitleStyle(cs.onSurface),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Device',
              style: FocalType.sectionSubtitleStyle(cs.onSurfaceVariant),
            ),
            SelectableText(deviceName),
            const SizedBox(height: 12),
            Text(
              'Network',
              style: FocalType.sectionSubtitleStyle(cs.onSurfaceVariant),
            ),
            SelectableText(lanLine),
            const SizedBox(height: 12),
            Text(
              'Playback',
              style: FocalType.sectionSubtitleStyle(cs.onSurfaceVariant),
            ),
            const Text(
              'Live video and audio in the Focal window (FOCL over LAN).',
              style: TextStyle(fontSize: FocalType.body, height: 1.4),
            ),
            if (useTls) ...[
              const SizedBox(height: 12),
              Text(
                'TLS',
                style: FocalType.sectionSubtitleStyle(cs.primary),
              ),
              Text(
                'Encrypted FOCL on port 8443 (match phone Settings).',
                style: FocalType.sectionSubtitleStyle(cs.onSurfaceVariant),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Close'),
          ),
        ],
      );
    },
  );
}

Future<void> showSendGuide(BuildContext context) {
  return showDialog<void>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(
        'How to send',
        style: FocalType.sectionTitleStyle(Theme.of(ctx).colorScheme.onSurface),
      ),
      content: const SingleChildScrollView(
        child: Text(
          'From this computer\n'
          '• Tap Share screen to broadcast your desktop.\n'
          '• On Wayland, pick a monitor or window in the system dialog.\n'
          '• Viewers use Receive on phone, TV, or another PC and enter your PIN.\n\n'
          'From your phone\n'
          '• Focal → Send → Camera, Screen, or Audio → Start.\n'
          '• On a PC, open Receive, pick your phone, and enter its PIN.\n\n'
          'Install ffmpeg for laptop screen share on Linux.\n'
          'VPNs often block LAN streaming — disable VPN or enable split tunneling.',
          style: TextStyle(fontSize: FocalType.body, height: 1.5),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx),
          child: const Text('Got it'),
        ),
      ],
    ),
  );
}
