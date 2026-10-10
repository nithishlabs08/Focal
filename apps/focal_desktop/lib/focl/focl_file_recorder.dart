import 'dart:async';
import 'dart:io';

import 'focl_receive_session.dart';

/// Optional tee: writes FOCL video NAL payloads to a raw H.264 file.
class FoclFileRecorder {
  StreamSubscription<FoclVideoChunk>? _sub;
  IOSink? _sink;

  Future<void> start({
    required Stream<FoclVideoChunk> videoStream,
    required String outputH264Path,
  }) async {
    await stop();
    final file = File(outputH264Path);
    _sink = file.openWrite(mode: FileMode.writeOnly);
    _sub = videoStream.listen(
      (chunk) => _sink?.add(chunk.payload),
      onError: (_) {},
      onDone: () async {
        await _sink?.flush();
        await _sink?.close();
        _sink = null;
      },
    );
  }

  Future<void> stop() async {
    await _sub?.cancel();
    _sub = null;
    try {
      await _sink?.flush();
      await _sink?.close();
    } catch (_) {}
    _sink = null;
  }
}
