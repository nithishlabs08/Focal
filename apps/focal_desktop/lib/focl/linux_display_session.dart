import 'dart:io';

/// Linux display server hints for choosing a screen-capture backend.
class LinuxDisplaySession {
  const LinuxDisplaySession._(this.kind);

  final LinuxDisplayKind kind;

  static LinuxDisplaySession detect() {
    final session = Platform.environment['XDG_SESSION_TYPE']?.toLowerCase();
    if (session == 'wayland') {
      return const LinuxDisplaySession._(LinuxDisplayKind.wayland);
    }
    if (session == 'x11') {
      return const LinuxDisplaySession._(LinuxDisplayKind.x11);
    }
    final display = Platform.environment['DISPLAY'];
    if (display != null && display.isNotEmpty) {
      return const LinuxDisplaySession._(LinuxDisplayKind.x11);
    }
    return const LinuxDisplaySession._(LinuxDisplayKind.unknown);
  }

  bool get isWayland => kind == LinuxDisplayKind.wayland;
  bool get isX11 => kind == LinuxDisplayKind.x11;
}

enum LinuxDisplayKind { wayland, x11, unknown }
