import 'package:dbus/dbus.dart';

/// Keeps the xdg-desktop-portal ScreenCast session alive while ffmpeg reads PipeWire.
class LinuxPortalScreencastSession {
  LinuxPortalScreencastSession({
    required DBusClient client,
    required DBusObjectPath sessionPath,
    required this.pipeWireNodeId,
  })  : _client = client,
        _sessionPath = sessionPath;

  final DBusClient _client;
  final DBusObjectPath _sessionPath;
  final int pipeWireNodeId;

  static const _portalName = 'org.freedesktop.portal.Desktop';
  static const _sessionIface = 'org.freedesktop.portal.Session';

  Future<void> close() async {
    try {
      final session = DBusRemoteObject(
        _client,
        name: _portalName,
        path: _sessionPath,
      );
      await session.callMethod(_sessionIface, 'Close', []);
    } catch (_) {}
    try {
      await _client.close();
    } catch (_) {}
  }
}
