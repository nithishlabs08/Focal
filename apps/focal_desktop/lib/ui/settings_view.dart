import 'package:flutter/material.dart';

import '../theme/focal_layout.dart';
import 'focal_responsive.dart';
import 'widgets/focal_entrance.dart';

const _hPad = 15.0;

class SettingsView extends StatelessWidget {
  const SettingsView({
    super.key,
    required this.deviceDisplayName,
    required this.onRenameDevice,
    required this.useTls,
    required this.tlsLocked,
    required this.onTlsChanged,
    required this.saveRecordingToFile,
    required this.onSaveRecordingChanged,
  });

  final String deviceDisplayName;
  final VoidCallback onRenameDevice;
  final bool useTls;
  final bool tlsLocked;
  final ValueChanged<bool> onTlsChanged;
  final bool saveRecordingToFile;
  final ValueChanged<bool> onSaveRecordingChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;

    return FocalResponsiveList(
      padding: const EdgeInsets.symmetric(horizontal: _hPad, vertical: 40),
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 8),
          child: Text(
            'Settings',
            style: theme.textTheme.titleLarge,
            textAlign: TextAlign.center,
          ),
        ),
        const SizedBox(height: 28),
        _sectionTitle(context, 'General'),
        FocalEntrance(
          index: 0,
          child: Card(
            child: ListTile(
              contentPadding: const EdgeInsets.symmetric(
                horizontal: FocalLayout.cardPadding,
                vertical: 8,
              ),
              leading: CircleAvatar(
                backgroundColor: cs.primaryContainer,
                child: Icon(Icons.computer_rounded, color: cs.primary),
              ),
              title: const Text('Device name'),
              subtitle: Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(deviceDisplayName),
              ),
              trailing: IconButton(
                tooltip: 'Rename',
                onPressed: onRenameDevice,
                icon: const Icon(Icons.edit_outlined),
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),
        _sectionTitle(context, 'Receive'),
        FocalEntrance(
          index: 1,
          child: Card(
            child: SwitchListTile(
              contentPadding: const EdgeInsets.symmetric(
                horizontal: FocalLayout.cardPadding,
                vertical: 4,
              ),
              title: const Text('Also save stream to file'),
              subtitle: const Padding(
                padding: EdgeInsets.only(top: 4),
                child: Text(
                  'Off by default — video plays live in the app (like Focal on your phone). '
                  'Enable to keep ~/focal_capture.h264 for VLC.',
                ),
              ),
              value: saveRecordingToFile,
              onChanged: tlsLocked ? null : onSaveRecordingChanged,
            ),
          ),
        ),
        const SizedBox(height: 12),
        _sectionTitle(context, 'Network'),
        FocalEntrance(
          index: 2,
          child: Card(
            child: SwitchListTile(
              contentPadding: const EdgeInsets.symmetric(
                horizontal: FocalLayout.cardPadding,
                vertical: 4,
              ),
              title: const Text('Encrypted connection (TLS)'),
              subtitle: const Padding(
                padding: EdgeInsets.only(top: 4),
                child: Text(
                  'Use TLS port 8443 when connecting to a sender. Must match the phone.',
                ),
              ),
              value: useTls,
              onChanged: tlsLocked ? null : onTlsChanged,
            ),
          ),
        ),
        if (tlsLocked) ...[
          const SizedBox(height: 8),
          Text(
            'Stop the current session to change TLS.',
            style: theme.textTheme.bodySmall,
          ),
        ],
        const SizedBox(height: 24),
        _sectionTitle(context, 'About'),
        Text(
          'Focal is not Miracast — it is paired app-to-app live streaming over your LAN '
          '(FOCL). Desktop Receive decodes FOCL in-process (same idea as Receive on your phone). '
          'The phone’s HTTP /stream.h264 URL is only for external tools like VLC.\n\n'
          'Focal Desktop can also share this computer’s screen on your LAN.',
          style: theme.textTheme.bodyMedium?.copyWith(
            color: cs.onSurfaceVariant,
            height: 1.5,
          ),
        ),
        const SizedBox(height: 12),
        Text(
          'Version 1.1.0',
          style: theme.textTheme.labelLarge?.copyWith(color: cs.primary),
        ),
        const SizedBox(height: 40),
      ],
    );
  }

  Widget _sectionTitle(BuildContext context, String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 8, bottom: 8, top: 8),
      child: Text(
        title,
        style: Theme.of(context).textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w700,
            ),
      ),
    );
  }
}
