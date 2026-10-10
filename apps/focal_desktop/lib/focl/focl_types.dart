class FoclReceiveStats {
  const FoclReceiveStats({
    required this.videoBytes,
    required this.audioBytes,
    required this.fps,
    this.bitrateMbps = 0.0,
    this.latencyMs = 0,
  });

  final int videoBytes;
  final int audioBytes;
  final double fps;
  final double bitrateMbps;
  final int latencyMs;
}

typedef FoclStatsCallback = void Function(FoclReceiveStats stats);

class FoclAuthException implements Exception {
  FoclAuthException(this.message);
  final String message;
  @override
  String toString() => 'FoclAuthException: $message';
}
