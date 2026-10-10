import 'package:flutter/material.dart';

import '../focl/focl_host.dart';
import '../theme/focal_layout.dart';
import '../theme/focal_typography.dart';
import 'focal_responsive.dart';
import 'widgets/focal_big_button.dart';
import 'widgets/focal_entrance.dart';
import 'widgets/focal_guide_dialog.dart';
import 'widgets/focal_icon_button.dart';
import 'widgets/laptop_send_panel.dart';
import 'widgets/focal_stream_placeholder.dart';
import 'widgets/focal_tab_hero.dart';

const _hPad = 15.0;

class SendTabView extends StatelessWidget {
  const SendTabView({
    super.key,
    required this.hostState,
    required this.deviceDisplayName,
    required this.onStartLaptopShare,
    required this.onStopLaptopShare,
  });

  final FoclHostState hostState;
  final String deviceDisplayName;
  final VoidCallback onStartLaptopShare;
  final VoidCallback onStopLaptopShare;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final sizing = FocalSizing.of(context);
    final buttonWidth =
        sizing.isDesktop ? FocalLayout.bigButtonMinWidth : 160.0;
    final hosting = hostState.isHosting;

    final statusLine = hosting
        ? 'Live · PIN ${hostState.pin}'
        : 'Ready to share';

    return Stack(
      children: [
        FocalResponsiveList(
          padding: const EdgeInsets.symmetric(horizontal: _hPad, vertical: 20),
          children: [
            FocalTabHero(
              deviceName: deviceDisplayName,
              metaLine: statusLine,
              minHeight: 200,
              metaLineStyle: FocalType.deviceMetaStyle(
                hosting ? cs.primary : cs.onSurfaceVariant,
              ),
            ),
            FocalEntrance(
              index: 2,
              child: FocalStreamPlaceholder(active: hosting),
            ),
            const SizedBox(height: 8),
            FocalEntrance(
              index: 3,
              child: Center(
                child: hosting
                    ? ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 520),
                        child: Card(
                          child: LaptopSendPanel(
                            state: hostState,
                            deviceDisplayName: deviceDisplayName,
                            onStart: onStartLaptopShare,
                            onStop: onStopLaptopShare,
                          ),
                        ),
                      )
                    : FocalBigButton(
                        filled: false,
                        label: 'Share screen',
                        icon: Icons.screen_share_rounded,
                        onPressed: onStartLaptopShare,
                        minWidth: buttonWidth,
                      ),
              ),
            ),
          ],
        ),
        Align(
          alignment: Alignment.topRight,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: FocalIconButton(
              tooltip: 'Guide',
              icon: Icons.help_outline_rounded,
              onPressed: () => showSendGuide(context),
            ),
          ),
        ),
      ],
    );
  }
}
