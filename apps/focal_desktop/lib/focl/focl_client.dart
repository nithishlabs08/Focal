import 'focl_file_recorder.dart';
import 'focl_receive_session.dart';
import 'focl_types.dart';

export 'focl_types.dart';

/// Legacy FOCL client that records to disk only (CLI / tests). Prefer [FoclReceiveSession].
class FoclClient {
  FoclReceiveSession? _session;
  final FoclFileRecorder _recorder = FoclFileRecorder();

  Future<void> connect({
    required String host,
    required int port,
    required String pin,
    bool useTls = false,
    required String outputH264Path,
    FoclStatsCallback? onStats,
  }) async {
    await disconnect();
    final session = FoclReceiveSession();
    _session = session;
    await _recorder.start(
      videoStream: session.videoStream,
      outputH264Path: outputH264Path,
    );
    await session.connect(
      host: host,
      port: port,
      pin: pin,
      useTls: useTls,
      onStats: onStats,
    );
  }

  Future<void> disconnect() async {
    await _session?.disconnect();
    _session = null;
    await _recorder.stop();
  }
}
