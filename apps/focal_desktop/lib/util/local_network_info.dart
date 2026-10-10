import 'dart:io';

import 'lan_network.dart';

class LocalNetworkInfo {
  const LocalNetworkInfo({
    required this.hostname,
    required this.ipv4Addresses,
  });

  final String hostname;
  final List<String> ipv4Addresses;

  String get primaryIpv4 =>
      ipv4Addresses.isNotEmpty ? ipv4Addresses.first : '—';

  static Future<LocalNetworkInfo> load() async {
    final hostname = Platform.localHostname;
    final sorted = await LanNetwork.lanIpv4Addresses();
    return LocalNetworkInfo(hostname: hostname, ipv4Addresses: sorted);
  }
}
