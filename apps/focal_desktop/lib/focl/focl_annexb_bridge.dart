import 'dart:async';
import 'dart:io';

/// Localhost HTTP server that mirrors phone `GET /stream.h264` for media_kit.
class FoclAnnexBBridge {
  HttpServer? _server;
  final Set<Socket> _streamSockets = {};
  String _localPin = 'focal';
  String? _streamUrl;

  String get streamUrl {
    final url = _streamUrl;
    if (url == null) {
      throw StateError('FoclAnnexBBridge not started');
    }
    return url;
  }

  String get localPin => _localPin;

  Map<String, String> get playbackHeaders => {'X-Focal-Pin': _localPin};

  Future<void> start({String? localPin}) async {
    await stop();
    _localPin = localPin ?? 'focal${DateTime.now().millisecondsSinceEpoch % 10000}';
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    _server = server;
    _streamUrl = 'http://127.0.0.1:${server.port}/stream.h264';

    server.listen((request) {
      unawaited(_handle(request));
    });
  }

  Future<void> _handle(HttpRequest request) async {
    final path = request.uri.path;
    if (path != '/stream.h264' && path != '/video.h264' && path != '/live') {
      request.response.statusCode = 404;
      await request.response.close();
      return;
    }

    final headerPin = request.headers.value('x-focal-pin') ??
        request.headers.value('authorization')?.replaceFirst(RegExp(r'^Bearer\s+', caseSensitive: false), '');
    if (headerPin != _localPin) {
      request.response.statusCode = 401;
      await request.response.close();
      return;
    }

    request.response.statusCode = 200;
    request.response.headers.set('Content-Type', 'video/h264');
    request.response.headers.set('Access-Control-Allow-Origin', '*');
    final socket = await request.response.detachSocket();
    _streamSockets.add(socket);
    socket.done.whenComplete(() => _streamSockets.remove(socket));
  }

  void writeNal(List<int> bytes) {
    for (final socket in _streamSockets.toList()) {
      try {
        socket.add(bytes);
      } catch (_) {
        _streamSockets.remove(socket);
      }
    }
  }

  Future<void> stop() async {
    for (final socket in _streamSockets.toList()) {
      try {
        await socket.close();
      } catch (_) {}
    }
    _streamSockets.clear();
    await _server?.close(force: true);
    _server = null;
    _streamUrl = null;
  }
}
