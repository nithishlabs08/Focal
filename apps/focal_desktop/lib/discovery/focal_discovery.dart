import 'dart:async';
import 'dart:io';

import 'package:multicast_dns/multicast_dns.dart';

import '../models/discovered_sender.dart';
import '../util/local_network_info.dart';

/// LAN discovery for `_focal._tcp` (see [FocalDiscoveryManager] on Android).
class FocalDiscovery {
  static const serviceType = '_focal._tcp.local';

  final MDnsClient _client = MDnsClient();
  final _senders = <String, DiscoveredSender>{};
  final _controller = StreamController<List<DiscoveredSender>>.broadcast();
  Set<String> _localIpv4 = {};

  Stream<List<DiscoveredSender>> get senders => _controller.stream;
  bool _running = false;

  Future<void> start() async {
    if (_running) return;
    _running = true;
    final network = await LocalNetworkInfo.load();
    _localIpv4 = network.ipv4Addresses.toSet();
    await _client.start();
    unawaited(_scanLoop());
  }

  Future<void> stop() async {
    _running = false;
    _client.stop();
  }

  Future<void> restart() async {
    await stop();
    _senders.clear();
    _emit();
    await start();
  }

  void addManual({
    required String name,
    required String host,
    int port = 8080,
  }) {
    _upsert(
      DiscoveredSender(
        id: senderEndpointId(host, port),
        name: name,
        host: host,
        port: port,
        isManual: true,
      ),
    );
  }

  void _upsert(DiscoveredSender sender) {
    final key = senderEndpointId(sender.host, sender.port);
    // Drop stale rows that pointed at the same phone/PC under another mDNS name.
    for (final id in _senders.keys.toList()) {
      final existing = _senders[id]!;
      if (existing.host == sender.host &&
          existing.port == sender.port &&
          id != key) {
        _senders.remove(id);
      }
    }
    _senders[key] = DiscoveredSender(
      id: key,
      name: sender.name,
      host: sender.host,
      port: sender.port,
      isManual: sender.isManual,
    );
    _emit();
  }

  Future<void> _scanLoop() async {
    while (_running) {
      try {
        await for (final PtrResourceRecord ptr in _client.lookup<PtrResourceRecord>(
          ResourceRecordQuery.serverPointer(serviceType),
        )) {
          if (!_running) break;
          final serviceName = ptr.domainName;
          var host = '';
          var port = 8080;

          var srvTarget = '';
          await for (final SrvResourceRecord srv in _client.lookup<SrvResourceRecord>(
            ResourceRecordQuery.service(serviceName),
          )) {
            port = srv.port;
            srvTarget = srv.target;
            await for (final IPAddressResourceRecord ip in _client.lookup<IPAddressResourceRecord>(
              ResourceRecordQuery.addressIPv4(srvTarget),
            )) {
              host = ip.address.address;
            }
          }

          if (host.isEmpty && srvTarget.isNotEmpty) {
            host = await _resolveHostIpv4(srvTarget);
          }

          if (host.isEmpty) continue;
          if (_localIpv4.contains(host)) continue;

          var displayName = serviceName
              .replaceAll('.$serviceType', '')
              .replaceAll('.local', '');
          if (displayName.startsWith('Focal-')) {
            displayName = displayName.substring(6).replaceAll('-', ' ');
          } else {
            displayName = displayName.replaceAll('-', ' ');
          }
          if (displayName.isEmpty) displayName = 'Focal sender';

          _upsert(
            DiscoveredSender(
              id: senderEndpointId(host, port),
              name: displayName,
              host: host,
              port: port,
            ),
          );
        }
      } catch (_) {
        // mDNS may fail on some networks; manual IP still works.
      }
      await Future<void>.delayed(const Duration(seconds: 4));
    }
  }

  void _emit() {
    final list = _senders.values.toList()
      ..sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    _controller.add(list);
  }

  static Future<String> _resolveHostIpv4(String mDnsTarget) async {
    var name = mDnsTarget;
    if (name.endsWith('.')) {
      name = name.substring(0, name.length - 1);
    }
    if (name.endsWith('.local')) {
      name = name.substring(0, name.length - 6);
    }
    try {
      final results = await InternetAddress.lookup(name, type: InternetAddressType.IPv4);
      if (results.isNotEmpty) return results.first.address;
    } catch (_) {}
    return '';
  }
}
