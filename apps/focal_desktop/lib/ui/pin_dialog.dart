import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class ManualConnectResult {
  ManualConnectResult({
    required this.name,
    required this.host,
    required this.port,
  });

  final String name;
  final String host;
  final int port;
}

Future<String?> showPinDialog(BuildContext context, String senderName) {
  final controller = TextEditingController();
  return showDialog<String>(
    context: context,
    builder: (context) {
      final theme = Theme.of(context);
      final cs = theme.colorScheme;
      return Dialog(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(28, 28, 28, 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    CircleAvatar(
                      backgroundColor: cs.primaryContainer,
                      child: Icon(Icons.pin_rounded, color: cs.primary),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Pairing PIN',
                            style: theme.textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          Text(
                            senderName,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: cs.onSurfaceVariant,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),
                TextField(
                  controller: controller,
                  keyboardType: TextInputType.number,
                  maxLength: 4,
                  textAlign: TextAlign.center,
                  style: theme.textTheme.headlineSmall?.copyWith(
                    letterSpacing: 8,
                    fontWeight: FontWeight.w600,
                  ),
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  decoration: const InputDecoration(
                    hintText: '••••',
                    helperText: 'Shown on the phone while streaming',
                  ),
                  autofocus: true,
                  onSubmitted: (_) =>
                      Navigator.pop(context, controller.text.trim()),
                ),
                const SizedBox(height: 20),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('Cancel'),
                    ),
                    const SizedBox(width: 8),
                    FilledButton(
                      onPressed: () =>
                          Navigator.pop(context, controller.text.trim()),
                      child: const Text('Connect'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      );
    },
  );
}

Future<ManualConnectResult?> showManualConnectDialog(BuildContext context) {
  final hostController = TextEditingController();
  final portController = TextEditingController(text: '8080');
  return showDialog<ManualConnectResult>(
    context: context,
    builder: (context) {
      final theme = Theme.of(context);
      return Dialog(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(28, 28, 28, 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'Connect to sender by IP',
                  style: theme.textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  'Enter your phone’s LAN address when it does not appear in the list.',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 20),
                TextField(
                  controller: hostController,
                  decoration: const InputDecoration(
                    labelText: 'Sender IP address',
                    hintText: '192.168.1.50',
                    prefixIcon: Icon(Icons.dns_outlined),
                  ),
                  autofocus: true,
                ),
                const SizedBox(height: 14),
                TextField(
                  controller: portController,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: 'Port',
                    prefixIcon: Icon(Icons.numbers_rounded),
                  ),
                ),
                const SizedBox(height: 24),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('Cancel'),
                    ),
                    const SizedBox(width: 8),
                    FilledButton(
                      onPressed: () {
                        final host = hostController.text.trim();
                        if (host.isEmpty) return;
                        final port =
                            int.tryParse(portController.text.trim()) ?? 8080;
                        Navigator.pop(
                          context,
                          ManualConnectResult(
                            name: 'Manual ($host)',
                            host: host,
                            port: port,
                          ),
                        );
                      },
                      child: const Text('Continue'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      );
    },
  );
}
