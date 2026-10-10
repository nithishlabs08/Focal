import 'dart:io';

import 'avahi_mdns_publisher.dart';

/// Publishes `_focal._tcp` while this computer is hosting (Avahi D-Bus or CLI fallback).
class FocalHostDiscovery {
  final AvahiMdnsPublisher _avahi = AvahiMdnsPublisher();
  Process? _cliProcess;
  var _published = false;

  bool get isPublishing => _published;

  Future<void> start({
    required String serviceName,
    required int port,
    required String pin,
  }) async {
    await stop();
    if (!Platform.isLinux) return;

    final viaDBus = await _avahi.start(
      serviceName: serviceName,
      port: port,
      pin: pin,
    );
    if (viaDBus) {
      _published = true;
      return;
    }

    if (!await _hasAvahiCli()) return;

    _cliProcess = await Process.start(
      'avahi-publish-service',
      [
        serviceName,
        '_focal._tcp',
        '$port',
        'pin=$pin',
        'enc=focl-aes-gcm',
        'sources=screen',
        'version=1.1',
      ],
      mode: ProcessStartMode.detachedWithStdio,
    );
    _published = true;
  }

  Future<void> stop() async {
    await _avahi.stop();
    _cliProcess?.kill();
    _cliProcess = null;
    _published = false;
  }

  static Future<bool> _hasAvahiCli() async {
    final r = await Process.run('which', ['avahi-publish-service']);
    return r.exitCode == 0;
  }
}
