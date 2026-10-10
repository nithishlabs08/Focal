import 'package:flutter/material.dart';

import '../models/discovered_sender.dart';
import '../theme/focal_typography.dart';
import '../util/lan_network.dart';
import '../util/local_network_info.dart';
import 'focal_responsive.dart';
import 'widgets/focal_entrance.dart';
import 'widgets/focal_guide_dialog.dart';
import 'widgets/focal_icon_button.dart';
import 'widgets/focal_nearby_devices_section.dart';
import 'widgets/focal_tab_hero.dart';

class ReceiveTabView extends StatefulWidget {
  const ReceiveTabView({
    super.key,
    required this.senders,
    required this.useTls,
    required this.deviceDisplayName,
    required this.onManualConnect,
    required this.onRefresh,
    required this.onSelect,
    required this.senderLabel,
  });

  final List<DiscoveredSender> senders;
  final bool useTls;
  final String deviceDisplayName;
  final VoidCallback onManualConnect;
  final VoidCallback onRefresh;
  final void Function(DiscoveredSender) onSelect;
  final String Function(DiscoveredSender) senderLabel;

  @override
  State<ReceiveTabView> createState() => _ReceiveTabViewState();
}

class _ReceiveTabViewState extends State<ReceiveTabView> {
  String _lanLine = '';
  bool _vpnMayBlockLan = false;

  @override
  void initState() {
    super.initState();
    _loadNetwork();
  }

  Future<void> _loadNetwork() async {
    final info = await LocalNetworkInfo.load();
    final vpn = await LanNetwork.vpnMayBlockLan();
    if (!mounted) return;
    final ip = info.primaryIpv4;
    setState(() {
      _lanLine = ip.isNotEmpty && ip != '—' ? '#$ip' : 'Offline';
      _vpnMayBlockLan = vpn;
    });
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Stack(
      children: [
        FocalResponsiveList(
          padding: const EdgeInsets.fromLTRB(15, 56, 15, 20),
          desktopPadding: const EdgeInsets.fromLTRB(10, 56, 10, 20),
          children: [
            FocalTabHero(
              deviceName: widget.deviceDisplayName,
              metaLine: _lanLine,
              extraBelowMeta: widget.useTls
                  ? Text(
                      'TLS receive enabled',
                      style: FocalType.sectionSubtitleStyle(cs.primary),
                    )
                  : null,
            ),
            if (_vpnMayBlockLan)
              FocalEntrance(
                index: 2,
                child: Card(
                  color: cs.errorContainer.withValues(alpha: 0.35),
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(
                      'VPN detected — local streaming often fails until you turn off the VPN or allow LAN/local network access in your VPN app.',
                      style: FocalType.sectionSubtitleStyle(cs.error),
                    ),
                  ),
                ),
              ),
            if (_vpnMayBlockLan) const SizedBox(height: 12),
            FocalEntrance(
              index: 3,
              child: Center(
                child: OutlinedButton.icon(
                  onPressed: widget.onManualConnect,
                  icon: const Icon(Icons.lan_rounded),
                  label: const Text(
                    'Connect by IP',
                    style: TextStyle(fontSize: FocalType.sectionSubtitle),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 20),
            FocalEntrance(
              index: 4,
              child: FocalNearbyDevicesSection(
                title: 'Nearby devices',
                titleStyle: FocalType.sectionTitleStyle(cs.onSurface),
                senders: widget.senders,
                senderLabel: widget.senderLabel,
                onSelect: widget.onSelect,
                onRefresh: widget.onRefresh,
                onManualConnect: widget.onManualConnect,
                emptyHint:
                    'Start streaming on your phone (Focal → Send), same Wi‑Fi.',
              ),
            ),
            const SizedBox(height: 40),
          ],
        ),
        Positioned(
          top: 8,
          right: 8,
          child: Material(
            color: cs.surface.withValues(alpha: 0.92),
            elevation: 0,
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  FocalIconButton(
                    tooltip: 'Refresh',
                    icon: Icons.refresh_rounded,
                    onPressed: widget.onRefresh,
                  ),
                  FocalIconButton(
                    tooltip: 'Guide',
                    icon: Icons.help_outline_rounded,
                    onPressed: () => showReceiveGuide(context),
                  ),
                  FocalIconButton(
                    tooltip: 'Details',
                    icon: Icons.info_outline_rounded,
                    onPressed: () => showReceiveDetails(
                      context,
                      deviceName: widget.deviceDisplayName,
                      lanLine: _lanLine,
                      useTls: widget.useTls,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}
