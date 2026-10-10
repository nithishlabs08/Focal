/// Stable list identity — one row per streaming endpoint (not per mDNS instance name).
String senderEndpointId(String host, int port) => '$host:$port';

class DiscoveredSender {
  const DiscoveredSender({
    required this.id,
    required this.name,
    required this.host,
    required this.port,
    this.isManual = false,
  });

  final String id;
  final String name;
  final String host;
  final int port;
  final bool isManual;

  String get endpoint => '$host:$port';

  /// Plain FOCL uses mDNS SRV port (8080). TLS uses 8443 unless manually overridden.
  int connectPort({required bool useTls}) => useTls ? 8443 : port;

  DiscoveredSender copyWith({String? name}) {
    return DiscoveredSender(
      id: id,
      name: name ?? this.name,
      host: host,
      port: port,
      isManual: isManual,
    );
  }
}
