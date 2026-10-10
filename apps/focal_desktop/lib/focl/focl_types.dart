class FoclReceiveStats {
  const FoclReceiveStats({
    required this.videoBytes,
    required this.audioBytes,
    required this.fps,
  });

  final int videoBytes;
  final int audioBytes;
  final double fps;
}

typedef FoclStatsCallback = void Function(FoclReceiveStats stats);

class FoclAuthException implements Exception {
  FoclAuthException(this.message);
  final String message;
  @override
  String toString() => 'FoclAuthException: $message';
}
