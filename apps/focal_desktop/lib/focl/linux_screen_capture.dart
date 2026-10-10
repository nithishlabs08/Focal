import 'dart:io';

import 'linux_display_session.dart';
import 'linux_portal_screencast.dart';
import 'linux_portal_screencast_session.dart';

/// Result of preparing Linux desktop capture before FOCL hosting starts.
class LinuxScreenCapturePlan {
  const LinuxScreenCapturePlan._({
    required this.ffmpegInputArgs,
    required this.statusLabel,
    this.portalSession,
  });

  final List<String> ffmpegInputArgs;
  final String statusLabel;
  final LinuxPortalScreencastSession? portalSession;
}

class LinuxScreenCapturePrepareResult {
  const LinuxScreenCapturePrepareResult._({
    this.plan,
    this.cancelled = false,
    this.errorMessage,
  });

  final LinuxScreenCapturePlan? plan;
  final bool cancelled;
  final String? errorMessage;

  bool get ok => plan != null;
}

class LinuxScreenCapture {
  /// Wayland: portal picker. X11: full display. Returns error text on failure.
  static Future<LinuxScreenCapturePrepareResult> prepare() async {
    final session = LinuxDisplaySession.detect();
    if (session.isWayland) {
      return _prepareWayland();
    }
    if (session.isX11) {
      return LinuxScreenCapturePrepareResult._(
        plan: _x11Plan(),
      );
    }
    return const LinuxScreenCapturePrepareResult._(
      errorMessage:
          'Could not detect X11 or Wayland. Screen share needs a graphical session.',
    );
  }

  static Future<LinuxScreenCapturePrepareResult> _prepareWayland() async {
    try {
      final portal = LinuxPortalScreencast();
      final picked = await portal.requestCapture();
      if (picked == null) {
        return const LinuxScreenCapturePrepareResult._(cancelled: true);
      }
      return LinuxScreenCapturePrepareResult._(
        plan: LinuxScreenCapturePlan._(
          ffmpegInputArgs: [
            '-thread_queue_size',
            '512',
            '-f',
            'pipewire',
            '-framerate',
            '15',
            '-i',
            '${picked.pipeWireNodeId}',
          ],
          statusLabel: 'Sharing selected screen',
          portalSession: picked.session,
        ),
      );
    } on LinuxPortalScreencastException catch (e) {
      return LinuxScreenCapturePrepareResult._(errorMessage: e.message);
    } catch (e) {
      return LinuxScreenCapturePrepareResult._(
        errorMessage: 'Screen picker failed: $e',
      );
    }
  }

  static LinuxScreenCapturePlan _x11Plan() {
    final display = Platform.environment['DISPLAY'] ?? ':0';
    return LinuxScreenCapturePlan._(
      ffmpegInputArgs: [
        '-f',
        'x11grab',
        '-framerate',
        '15',
        '-i',
        display,
      ],
      statusLabel:
          'Sharing display $display (X11 — use Wayland for the system picker)',
    );
  }

  static Future<bool> ffmpegAvailable() async {
    final which = await Process.run('which', ['ffmpeg']);
    return which.exitCode == 0;
  }
}
