import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:media_kit_video/media_kit_video.dart';

import '../focl/focl_live_receiver.dart';
import '../focl/focl_types.dart';
import '../models/discovered_sender.dart';
import '../theme/focal_layout.dart';
import 'widgets/device_badge.dart';

/// Full-pane receive UI with cast-style chrome (auto-hide).
class ReceiveSessionView extends StatefulWidget {
  const ReceiveSessionView({
    super.key,
    required this.sender,
    required this.phase,
    required this.errorMessage,
    required this.stats,
    required this.outputPath,
    required this.onStop,
    this.videoController,
    this.recordingToFile = false,
    this.isMuted = false,
    this.audioAvailable = true,
    this.onToggleMute,
  });

  final DiscoveredSender sender;
  final FocalReceivePhase phase;
  final String? errorMessage;
  final FoclReceiveStats? stats;
  final String? outputPath;
  final VoidCallback onStop;
  final VideoController? videoController;
  final bool recordingToFile;
  final bool isMuted;
  final bool audioAvailable;
  final VoidCallback? onToggleMute;

  @override
  State<ReceiveSessionView> createState() => _ReceiveSessionViewState();
}

class _ReceiveSessionViewState extends State<ReceiveSessionView> {
  var _showChrome = true;
  Timer? _hideTimer;

  @override
  void initState() {
    super.initState();
    _bumpChrome();
  }

  @override
  void didUpdateWidget(covariant ReceiveSessionView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.phase != widget.phase ||
        oldWidget.errorMessage != widget.errorMessage) {
      _bumpChrome();
    }
  }

  @override
  void dispose() {
    _hideTimer?.cancel();
    super.dispose();
  }

  void _bumpChrome() {
    _hideTimer?.cancel();
    setState(() => _showChrome = true);
    if (widget.phase == FocalReceivePhase.streaming &&
        widget.errorMessage == null) {
      _hideTimer = Timer(const Duration(seconds: 4), () {
        if (mounted) setState(() => _showChrome = false);
      });
    }
  }

  String get _statusLabel {
    switch (widget.phase) {
      case FocalReceivePhase.connecting:
        return 'Connecting…';
      case FocalReceivePhase.waitingKeyframe:
        return 'Waiting for video keyframe…';
      case FocalReceivePhase.reconnecting:
        return widget.errorMessage ?? 'Reconnecting…';
      case FocalReceivePhase.error:
        return widget.errorMessage ?? 'Stream error';
      case FocalReceivePhase.streaming:
        return 'Live';
      case FocalReceivePhase.idle:
        return 'Starting…';
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final hasVideo = widget.videoController != null;
    final connecting = widget.phase == FocalReceivePhase.connecting ||
        widget.phase == FocalReceivePhase.waitingKeyframe ||
        widget.phase == FocalReceivePhase.reconnecting;

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: FocalLayout.horizontalPadding,
        vertical: 8,
      ),
      child: Column(
        children: [
          AnimatedOpacity(
            opacity: _showChrome ? 1 : 0,
            duration: const Duration(milliseconds: 200),
            child: IgnorePointer(
              ignoring: !_showChrome,
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.sender.name,
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w700,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        DeviceBadge(label: widget.sender.endpoint),
                      ],
                    ),
                  ),
                  if (widget.onToggleMute != null)
                    IconButton(
                      tooltip: !widget.audioAvailable
                          ? 'Audio unavailable'
                          : (widget.isMuted ? 'Unmute' : 'Mute'),
                      onPressed:
                          widget.audioAvailable ? widget.onToggleMute : null,
                      icon: Icon(
                        !widget.audioAvailable
                            ? Icons.volume_off_outlined
                            : (widget.isMuted
                                ? Icons.volume_off_rounded
                                : Icons.volume_up_rounded),
                      ),
                    ),
                  FilledButton.icon(
                    onPressed: widget.onStop,
                    style: FilledButton.styleFrom(
                      backgroundColor: cs.error,
                      foregroundColor: cs.onError,
                    ),
                    icon: const Icon(Icons.stop_rounded, size: 20),
                    label: const Text('Stop'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: _bumpChrome,
              child: hasVideo
                  ? ClipRRect(
                      borderRadius: BorderRadius.circular(12),
                      child: Stack(
                        fit: StackFit.expand,
                        children: [
                          ColoredBox(
                            color: Colors.black,
                            child: Video(
                              controller: widget.videoController!,
                              controls: NoVideoControls,
                              fill: Colors.black,
                            ),
                          ),
                          if (connecting ||
                              widget.phase == FocalReceivePhase.error)
                            ColoredBox(
                              color: Colors.black54,
                              child: Center(
                                child: _StatusBlock(
                                  connecting: connecting,
                                  label: _statusLabel,
                                  error: widget.phase == FocalReceivePhase.error
                                      ? widget.errorMessage
                                      : null,
                                ),
                              ),
                            ),
                        ],
                      ),
                    )
                  : Center(
                      child: _StatusBlock(
                        connecting: connecting,
                        label: _statusLabel,
                        error: widget.errorMessage,
                      ),
                    ),
            ),
          ),
          AnimatedOpacity(
            opacity: _showChrome ? 1 : 0,
            duration: const Duration(milliseconds: 200),
            child: Column(
              children: [
                const SizedBox(height: 8),
                if (widget.stats != null && widget.stats!.fps > 0)
                  Text(
                    '${widget.stats!.fps.toStringAsFixed(1)} fps'
                    '${widget.stats!.bitrateMbps > 0 ? " · ${widget.stats!.bitrateMbps.toStringAsFixed(2)} Mbps" : ""}'
                    ' · ${(widget.stats!.videoBytes / (1024 * 1024)).toStringAsFixed(2)} MB'
                    '${widget.stats!.latencyMs > 0 ? " · ${widget.stats!.latencyMs} ms" : ""}',
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontFamily: 'monospace',
                    ),
                  ),
                if (widget.recordingToFile && widget.outputPath != null) ...[
                  const SizedBox(height: 6),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.save_alt_rounded, size: 16, color: cs.primary),
                      const SizedBox(width: 6),
                      Flexible(
                        child: Text(
                          'Also saving to ${widget.outputPath}',
                          style: theme.textTheme.bodySmall?.copyWith(
                            fontFamily: 'monospace',
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      TextButton(
                        onPressed: () {
                          Clipboard.setData(
                            ClipboardData(text: widget.outputPath!),
                          );
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
                if (!widget.audioAvailable &&
                    widget.phase == FocalReceivePhase.streaming) ...[
                  const SizedBox(height: 4),
                  Text(
                    'Audio unavailable on this system',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: cs.onSurfaceVariant,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _StatusBlock extends StatelessWidget {
  const _StatusBlock({
    required this.connecting,
    required this.label,
    this.error,
  });

  final bool connecting;
  final String label;
  final String? error;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (connecting)
          const CircularProgressIndicator()
        else
          Icon(
            error != null ? Icons.error_outline_rounded : Icons.cast_rounded,
            size: 64,
            color: (error != null ? cs.error : cs.primary).withValues(alpha: 0.8),
          ),
        const SizedBox(height: 16),
        Text(label, style: theme.textTheme.titleMedium),
        if (error != null && connecting == false) ...[
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Text(
              error!,
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyMedium?.copyWith(color: cs.error),
            ),
          ),
        ],
      ],
    );
  }
}
