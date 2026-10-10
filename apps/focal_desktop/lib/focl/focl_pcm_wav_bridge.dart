import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

/// Localhost continuous WAV (PCM s16le 48kHz mono) for media_kit audio playback.
class FoclPcmWavBridge {
  HttpServer? _server;
  final Set<Socket> _clients = {};
  String? _streamUrl;

  String get streamUrl {
    final url = _streamUrl;
    if (url == null) throw StateError('FoclPcmWavBridge not started');
    return url;
  }

  Future<void> start() async {
    await stop();
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    _server = server;
    _streamUrl = 'http://127.0.0.1:${server.port}/stream.wav';
    server.listen((request) {
      unawaited(_handle(request));
    });
  }

  Future<void> _handle(HttpRequest request) async {
    if (request.uri.path != '/stream.wav' && request.uri.path != '/live.wav') {
      request.response.statusCode = 404;
      await request.response.close();
      return;
    }

    request.response.statusCode = 200;
    request.response.headers.set('Content-Type', 'audio/wav');
    request.response.headers.set('Cache-Control', 'no-cache, no-store');
    request.response.headers.set('Access-Control-Allow-Origin', '*');
    final socket = await request.response.detachSocket();
    try {
      socket.add(_wavHeader());
    } catch (_) {
      try {
        await socket.close();
      } catch (_) {}
      return;
    }
    _clients.add(socket);
    socket.done.whenComplete(() => _clients.remove(socket));
  }

  /// Minimal RIFF/WAVE header with "unknown" data size (0xFFFFFFFF).
  static List<int> _wavHeader({
    int sampleRate = 48000,
    int channels = 1,
    int bitsPerSample = 16,
  }) {
    final byteRate = sampleRate * channels * bitsPerSample ~/ 8;
    final blockAlign = channels * bitsPerSample ~/ 8;
    final data = ByteData(44);
    void ascii(int offset, String s) {
      for (var i = 0; i < s.length; i++) {
        data.setUint8(offset + i, s.codeUnitAt(i));
      }
    }

    ascii(0, 'RIFF');
    data.setUint32(4, 0xFFFFFFFF, Endian.little);
    ascii(8, 'WAVE');
    ascii(12, 'fmt ');
    data.setUint32(16, 16, Endian.little); // PCM chunk size
    data.setUint16(20, 1, Endian.little); // PCM
    data.setUint16(22, channels, Endian.little);
    data.setUint32(24, sampleRate, Endian.little);
    data.setUint32(28, byteRate, Endian.little);
    data.setUint16(32, blockAlign, Endian.little);
    data.setUint16(34, bitsPerSample, Endian.little);
    ascii(36, 'data');
    data.setUint32(40, 0xFFFFFFFF, Endian.little);
    return data.buffer.asUint8List();
  }

  void writePcm(List<int> bytes) {
    if (bytes.isEmpty || _clients.isEmpty) return;
    for (final socket in _clients.toList()) {
      try {
        socket.add(bytes);
      } catch (_) {
        _clients.remove(socket);
      }
    }
  }

  Future<void> stop() async {
    for (final socket in _clients.toList()) {
      try {
        await socket.close();
      } catch (_) {}
    }
    _clients.clear();
    await _server?.close(force: true);
    _server = null;
    _streamUrl = null;
  }
}
