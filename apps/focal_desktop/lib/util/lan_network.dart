import 'dart:io';

/// Heuristics for LAN streaming (VPN/tunnel interfaces often break mDNS and TCP to 192.168.x.x).
class LanNetwork {
  static const _vpnInterfaceMarkers = [
    'tun',
    'tap',
    'wg',
    'ppp',
    'vpn',
    'utun',
    'nordlynx',
    'tailscale',
  ];

  static bool isVpnInterfaceName(String name) {
    final n = name.toLowerCase();
    return _vpnInterfaceMarkers.any((m) => n.startsWith(m) || n.contains(m));
  }

  static bool isPrivateIpv4(String host) {
    final parts = host.split('.');
    if (parts.length != 4) return false;
    final octets = parts.map(int.tryParse).toList();
    if (octets.any((o) => o == null || o < 0 || o > 255)) return false;
    final a = octets[0]!;
    final b = octets[1]!;
    if (a == 10) return true;
    if (a == 172 && b >= 16 && b <= 31) return true;
    if (a == 192 && b == 168) return true;
    if (a == 169 && b == 254) return true;
    return false;
  }

  /// LAN IPs from non-tunnel interfaces (for mDNS self-filter and display).
  static Future<List<String>> lanIpv4Addresses() async {
    final ips = <String>{};
    try {
      final interfaces = await NetworkInterface.list(
        type: InternetAddressType.IPv4,
        includeLinkLocal: false,
      );
      for (final iface in interfaces) {
        if (isVpnInterfaceName(iface.name)) continue;
        for (final addr in iface.addresses) {
          if (!addr.isLoopback && isPrivateIpv4(addr.address)) {
            ips.add(addr.address);
          }
        }
      }
    } catch (_) {}
    final sorted = ips.toList()..sort();
    return sorted;
  }

  /// True when we only see private IPs on tunnel-like interfaces (common with VPN).
  static Future<bool> vpnMayBlockLan() async {
    try {
      final interfaces = await NetworkInterface.list(
        type: InternetAddressType.IPv4,
        includeLinkLocal: false,
      );
      var hasVpnIface = false;
      var hasPlainLan = false;
      for (final iface in interfaces) {
        final vpn = isVpnInterfaceName(iface.name);
        for (final addr in iface.addresses) {
          if (addr.isLoopback || !isPrivateIpv4(addr.address)) continue;
          if (vpn) {
            hasVpnIface = true;
          } else {
            hasPlainLan = true;
          }
        }
      }
      return hasVpnIface && hasPlainLan;
    } catch (_) {
      return false;
    }
  }
}
