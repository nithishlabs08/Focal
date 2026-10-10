import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:media_kit_video/media_kit_video.dart';

import '../focl/focl_types.dart';
import '../models/discovered_sender.dart';
import '../theme/focal_layout.dart';
import 'widgets/device_badge.dart';

/// Full-pane receive UI with in-app live video (cast-style).
class ReceiveSessionView extends StatelessWidget {
  const ReceiveSessionView({
    super.key,
    required this.sender,
    required this.connecting,
    required this.errorMessage,
    required this.stats,
    required this.outputPath,
    required this.onStop,
    this.videoController,
    this.recordingToFile = false,
    this.isMuted = false,
    this.onToggleMute,
  });

  final DiscoveredSender sender;
  final bool connecting;
  final String? errorMessage;
  final FoclReceiveStats? stats;
  final String? outputPath;
  final VoidCallback onStop;
  final VideoController? videoController;
  final bool recordingToFile;
  final bool isMuted;
  final VoidCallback? onToggleMute;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final hasVideo = videoController != null;

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: FocalLayout.horizontalPadding,
        vertical: 8,
      ),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      sender.name,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    DeviceBadge(label: sender.endpoint),
                  ],
                ),
              ),
              if (onToggleMute != null)
                IconButton(
                  tooltip: isMuted ? 'Unmute' : 'Mute',
                  onPressed: onToggleMute,
                  icon: Icon(
                    isMuted ? Icons.volume_off_rounded : Icons.volume_up_rounded,
                  ),
                ),
              FilledButton.icon(
                onPressed: onStop,
                style: FilledButton.styleFrom(
                  backgroundColor: cs.error,
                  foregroundColor: cs.onError,
                ),
                icon: const Icon(Icons.stop_rounded, size: 20),
                label: const Text('Stop'),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Expanded(
            child: hasVideo
                ? ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: ColoredBox(
                      color: Colors.black,
                      child: Video(
                        controller: videoController!,
                        controls: MaterialVideoControls,
                        fill: Colors.black,
                      ),
                    ),
                  )
                : Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        if (connecting)
                          const CircularProgressIndicator()
                        else
                          Icon(
                            Icons.cast_rounded,
                            size: 64,
                            color: cs.primary.withValues(alpha: 0.7),
                          ),
                        const SizedBox(height: 16),
                        Text(
                          connecting ? 'Connecting…' : 'Starting live feed…',
                          style: theme.textTheme.titleMedium,
                        ),
                        if (errorMessage != null) ...[
                          const SizedBox(height: 8),
                          Text(
                            errorMessage!,
                            textAlign: TextAlign.center,
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: cs.error,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
          ),
          const SizedBox(height: 8),
          if (stats != null && stats!.fps > 0)
            Text(
              '${stats!.fps.toStringAsFixed(1)} fps'
              '${stats!.bitrateMbps > 0 ? " · ${stats!.bitrateMbps.toStringAsFixed(2)} Mbps" : ""}'
              ' · ${(stats!.videoBytes / (1024 * 1024)).toStringAsFixed(2)} MB'
              '${stats!.latencyMs > 0 ? " · ${stats!.latencyMs} ms" : ""}',
              style: theme.textTheme.labelLarge?.copyWith(
                fontFamily: 'monospace',
              ),
            ),
          if (recordingToFile && outputPath != null) ...[
            const SizedBox(height: 6),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.save_alt_rounded, size: 16, color: cs.primary),
                const SizedBox(width: 6),
                Flexible(
                  child: Text(
                    'Also saving to $outputPath',
                    style: theme.textTheme.bodySmall?.copyWith(
                      fontFamily: 'monospace',
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                TextButton(
                  onPressed: () {
                    Clipboard.setData(ClipboardData(text: outputPath!));
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Path copied'),
                        behavior: SnackBarBehavior.floating,
                        width: 280,
                      ),
                    );
                  },
                  child: const Text('Copy'),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
