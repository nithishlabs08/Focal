import 'dart:async';

import 'package:dbus/dbus.dart';

import 'linux_portal_screencast_session.dart';

/// PipeWire node from [org.freedesktop.portal.ScreenCast] after the user picks a source.
class LinuxPortalScreencastResult {
  const LinuxPortalScreencastResult({
    required this.pipeWireNodeId,
    required this.session,
  });

  final int pipeWireNodeId;
  final LinuxPortalScreencastSession session;
}

class LinuxPortalScreencastException implements Exception {
  LinuxPortalScreencastException(this.message);
  final String message;
  @override
  String toString() => message;
}

/// xdg-desktop-portal ScreenCast (monitor/window picker on Wayland).
class LinuxPortalScreencast {
  static const _portalName = 'org.freedesktop.portal.Desktop';
  static final _portalPath = DBusObjectPath('/org/freedesktop/portal/desktop');
  static const _screenCast = 'org.freedesktop.portal.ScreenCast';
  static const _request = 'org.freedesktop.portal.Request';

  /// Shows the system screen/window picker. Returns `null` if the user cancels.
  Future<LinuxPortalScreencastResult?> requestCapture() async {
    final client = DBusClient.session();
    try {
      final portal = DBusRemoteObject(
        client,
        name: _portalName,
        path: _portalPath,
      );
      await portal.introspect();

      final createReq = _token('req');
      final sessionHandleToken = _token('sh');
      final create = await _portalRequest(
        client: client,
        handleToken: createReq,
        call: () => portal.callMethod(
          _screenCast,
          'CreateSession',
          [
            DBusDict.stringVariant({
              'session_handle_token': DBusString(sessionHandleToken),
              'handle_token': DBusString(createReq),
            }),
          ],
          replySignature: DBusSignature('o'),
        ),
      );
      if (create.response != 0) {
        throw LinuxPortalScreencastException(_portalError('CreateSession', create.response));
      }

      final sessionPath = create.results['session_handle']?.asString();
      if (sessionPath == null || sessionPath.isEmpty) {
        throw LinuxPortalScreencastException('Portal did not return a session handle.');
      }
      final sessionHandle = DBusObjectPath(sessionPath);

      final selectReq = _token('req');
      final select = await _portalRequest(
        client: client,
        handleToken: selectReq,
        call: () => portal.callMethod(
          _screenCast,
          'SelectSources',
          [
            sessionHandle,
            DBusDict.stringVariant({
              'handle_token': DBusString(selectReq),
              'types': const DBusUint32(3), // monitor | window
              'multiple': const DBusBoolean(false),
            }),
          ],
          replySignature: DBusSignature('o'),
        ),
      );
      if (select.response != 0) {
        throw LinuxPortalScreencastException(_portalError('SelectSources', select.response));
      }

      final startReq = _token('req');
      final start = await _portalRequest(
        client: client,
        handleToken: startReq,
        call: () => portal.callMethod(
          _screenCast,
          'Start',
          [
            sessionHandle,
            const DBusString(''),
            DBusDict.stringVariant({
              'handle_token': DBusString(startReq),
            }),
          ],
          replySignature: DBusSignature('o'),
        ),
      );
      if (start.response != 0) {
        if (start.response == 1) {
          await client.close();
          return null;
        }
        throw LinuxPortalScreencastException(_portalError('Start', start.response));
      }

      final nodeId = _pipeWireNodeIdFromResults(start.results);
      if (nodeId == null) {
        await client.close();
        throw LinuxPortalScreencastException(
          'Portal started but no PipeWire stream was returned.',
        );
      }
      return LinuxPortalScreencastResult(
        pipeWireNodeId: nodeId,
        session: LinuxPortalScreencastSession(
          client: client,
          sessionPath: sessionHandle,
          pipeWireNodeId: nodeId,
        ),
      );
    } on DBusServiceUnknownException {
      await client.close();
      throw LinuxPortalScreencastException(
        'xdg-desktop-portal is not running. Install xdg-desktop-portal and '
        'xdg-desktop-portal-gnome (or -kde).',
      );
    } on DBusMethodResponseException catch (e) {
      await client.close();
      throw LinuxPortalScreencastException('Portal D-Bus error: $e');
    } on TimeoutException {
      await client.close();
      throw LinuxPortalScreencastException(
        'Screen picker timed out. Try again or check portal permissions.',
      );
    } on LinuxPortalScreencastException {
      await client.close();
      rethrow;
    } catch (_) {
      await client.close();
      rethrow;
    }
  }

  static String _portalError(String step, int code) {
    switch (code) {
      case 1:
        return '$step was cancelled.';
      case 2:
        return '$step failed (portal error $code).';
      default:
        return '$step failed (response $code).';
    }
  }

  static int? _pipeWireNodeIdFromResults(Map<String, DBusValue> results) {
    final streams = results['streams'];
    if (streams == null) return null;
    final array = streams.asArray();
    if (array.isEmpty) return null;

    for (final entry in array) {
      if (entry is DBusStruct && entry.children.isNotEmpty) {
        return entry.children.first.asUint32();
      }
    }
    return null;
  }

  static String _token(String prefix) {
    return '${prefix}_${DateTime.now().microsecondsSinceEpoch}';
  }

  static Future<_PortalResponse> _portalRequest({
    required DBusClient client,
    required String handleToken,
    required Future<DBusMethodSuccessResponse> Function() call,
  }) async {
    var sender = client.uniqueName;
    if (sender.isEmpty) {
      throw LinuxPortalScreencastException('DBus session not connected.');
    }
    if (sender.startsWith(':')) sender = sender.substring(1);
    sender = sender.replaceAll('.', '_');
    final requestPath = DBusObjectPath(
      '/org/freedesktop/portal/desktop/request/$sender/$handleToken',
    );

    final stream = DBusSignalStream(
      client,
      sender: _portalName,
      path: requestPath,
      interface: _request,
      name: 'Response',
    );

    final responseFuture = stream.first;
    await call();

    final signal = await responseFuture.timeout(
      const Duration(minutes: 5),
      onTimeout: () => throw TimeoutException('portal request'),
    );

    final response = signal.values[0].asUint32();
    final results = signal.values[1].asStringVariantDict();
    return _PortalResponse(response: response, results: results);
  }
}

class _PortalResponse {
  const _PortalResponse({required this.response, required this.results});

  final int response;
  final Map<String, DBusValue> results;
}
