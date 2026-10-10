import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../theme/focal_layout.dart';
import '../../util/local_network_info.dart';

/// Shown on **Receive** — this PC’s friendly name + network context.
class ThisComputerCard extends StatefulWidget {
  const ThisComputerCard({
    super.key,
    required this.displayName,
  });

  final String displayName;

  @override
  State<ThisComputerCard> createState() => _ThisComputerCardState();
}

class _ThisComputerCardState extends State<ThisComputerCard> {
  LocalNetworkInfo? _network;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final info = await LocalNetworkInfo.load();
    if (mounted) {
      setState(() {
        _network = info;
        _loading = false;
      });
    }
  }

  Future<void> _copy(String label, String value) async {
    await Clipboard.setData(ClipboardData(text: value));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Copied $label'),
        behavior: SnackBarBehavior.floating,
        width: 360,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final network = _network;

    return Padding(
      padding: const EdgeInsets.all(FocalLayout.cardPadding),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.computer_rounded, color: cs.primary, size: 22),
              const SizedBox(width: 10),
              Text(
                'This computer',
                style: theme.textTheme.titleMedium,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            widget.displayName,
            style: theme.textTheme.headlineSmall,
          ),
          if (network != null && network.hostname != widget.displayName) ...[
            const SizedBox(height: 4),
            Text(
              'System name: ${network.hostname}',
              style: theme.textTheme.bodySmall,
            ),
          ],
          const SizedBox(height: 10),
          Text(
            'Rename in Settings. Phones discover you when this PC is sending.',
            style: theme.textTheme.bodySmall?.copyWith(height: 1.4),
          ),
          const SizedBox(height: 14),
          if (_loading)
            Text(
              'Detecting network…',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: cs.onSurfaceVariant,
              ),
            )
          else
            _InfoRow(
              label: 'LAN',
              value: network == null || network.ipv4Addresses.isEmpty
                  ? 'No IPv4 on Wi‑Fi / Ethernet'
                  : network.ipv4Addresses.join('  ·  '),
              onCopy: network == null || network.ipv4Addresses.isEmpty
                  ? null
                  : () => _copy('IP address', network.primaryIpv4),
            ),
        ],
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({
    required this.label,
    required this.value,
    this.onCopy,
  });

  final String label;
  final String value;
  final VoidCallback? onCopy;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 40,
          child: Text(
            label,
            style: theme.textTheme.labelLarge?.copyWith(
              color: cs.onSurfaceVariant,
            ),
          ),
        ),
        Expanded(
          child: SelectableText(
            value,
            style: theme.textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.w600,
              height: 1.35,
            ),
          ),
        ),
        if (onCopy != null)
          IconButton(
            tooltip: 'Copy',
            visualDensity: VisualDensity.compact,
            onPressed: onCopy,
            icon: const Icon(Icons.copy_rounded, size: 20),
          ),
      ],
    );
  }
}
