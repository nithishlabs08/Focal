import 'dart:convert';

import 'package:dbus/dbus.dart';

/// Publishes `_focal._tcp` via Avahi on the system bus (no `avahi-publish-service` binary).
class AvahiMdnsPublisher {
  static const _avahiBus = 'org.freedesktop.Avahi';
  static final _serverPath = DBusObjectPath('/');

  DBusClient? _client;
  DBusRemoteObject? _entryGroup;
  bool get isPublishing => _entryGroup != null;

  Future<bool> start({
    required String serviceName,
    required int port,
    required String pin,
  }) async {
    await stop();
    try {
      final client = DBusClient.system();
      _client = client;
      final server = DBusRemoteObject(
        client,
        name: _avahiBus,
        path: _serverPath,
      );

      final groupReply = await server.callMethod(
        'org.freedesktop.Avahi.Server',
        'EntryGroupNew',
        [],
        replySignature: DBusSignature('o'),
      );
      final groupPath = groupReply.returnValues.first.asObjectPath();
      _entryGroup = DBusRemoteObject(
        client,
        name: _avahiBus,
        path: groupPath,
      );

      final txt = DBusArray(
        DBusSignature('ay'),
        [
          DBusArray.byte(utf8.encode('pin=$pin')),
          DBusArray.byte(utf8.encode('enc=focl-aes-gcm')),
          DBusArray.byte(utf8.encode('sources=screen')),
          DBusArray.byte(utf8.encode('version=1.1')),
        ],
      );

      await _entryGroup!.callMethod(
        'org.freedesktop.Avahi.EntryGroup',
        'AddService',
        [
          const DBusInt32(-1), // IF_UNSPEC
          const DBusUint32(0), // PROTO_UNSPEC
          const DBusUint32(0), // publish flags
          DBusString(serviceName),
          const DBusString('_focal._tcp'),
          const DBusString(''),
          const DBusString(''),
          DBusUint16(port),
          txt,
        ],
      );

      await _entryGroup!.callMethod(
        'org.freedesktop.Avahi.EntryGroup',
        'Commit',
        [],
      );
      return true;
    } on DBusServiceUnknownException {
      await stop();
      return false;
    } catch (_) {
      await stop();
      return false;
    }
  }

  Future<void> stop() async {
    final group = _entryGroup;
    final client = _client;
    _entryGroup = null;
    _client = null;
    if (group != null) {
      try {
        await group.callMethod(
          'org.freedesktop.Avahi.EntryGroup',
          'Free',
          [],
        );
      } catch (_) {}
    }
    if (client != null) {
      try {
        await client.close();
      } catch (_) {}
    }
  }
}
