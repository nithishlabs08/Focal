import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

/// Localhost HTTP server that mirrors phone `GET /stream.h264` for media_kit.
///
/// Buffers Annex-B NALs until a client attaches so the start of the stream
/// (SPS/PPS/IDR) is not dropped before media_kit connects.
class FoclAnnexBBridge {
  static const _maxPrerollBytes = 4 * 1024 * 1024;

  HttpServer? _server;
  final Set<Socket> _streamSockets = {};
  final BytesBuilder _preroll = BytesBuilder(copy: false);
  final _clientAttached = StreamController<void>.broadcast();
  Completer<void>? _firstClient;
  String _localPin = 'focal';
  String? _streamUrl;
  var _prerollBytes = 0;

  String get streamUrl {
    final url = _streamUrl;
    if (url == null) {
      throw StateError('FoclAnnexBBridge not started');
    }
    return url;
  }

  String get localPin => _localPin;

  Map<String, String> get playbackHeaders => {'X-Focal-Pin': _localPin};

  bool get hasClient => _streamSockets.isNotEmpty;

  /// Completes when the first HTTP client attaches (or immediately if already attached).
  Future<void> waitForClient({Duration timeout = const Duration(seconds: 8)}) {
    if (hasClient) return Future.value();
    _firstClient ??= Completer<void>();
    return _firstClient!.future.timeout(timeout);
  }

  Future<void> start({String? localPin}) async {
    await stop();
    _localPin = localPin ?? 'focal${DateTime.now().millisecondsSinceEpoch % 10000}';
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    _server = server;
    _streamUrl = 'http://127.0.0.1:${server.port}/stream.h264';
    _firstClient = Completer<void>();

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
        request.headers
            .value('authorization')
            ?.replaceFirst(RegExp(r'^Bearer\s+', caseSensitive: false), '');
    if (headerPin != _localPin) {
      request.response.statusCode = 401;
      await request.response.close();
      return;
    }

    request.response.statusCode = 200;
    request.response.headers.set('Content-Type', 'video/h264');
    request.response.headers.set('Cache-Control', 'no-cache, no-store');
    request.response.headers.set('Access-Control-Allow-Origin', '*');
    final socket = await request.response.detachSocket();

    // Flush buffered start (config + keyframe) before live writes.
    final buffered = _preroll.takeBytes();
    _prerollBytes = 0;
    if (buffered.isNotEmpty) {
      try {
        socket.add(buffered);
      } catch (_) {
        try {
          await socket.close();
        } catch (_) {}
        return;
      }
    }

    _streamSockets.add(socket);
    if (_firstClient != null && !_firstClient!.isCompleted) {
      _firstClient!.complete();
    }
    if (!_clientAttached.isClosed) {
      _clientAttached.add(null);
    }
    socket.done.whenComplete(() => _streamSockets.remove(socket));
  }

  void writeNal(List<int> bytes) {
    if (bytes.isEmpty) return;
    if (_streamSockets.isEmpty) {
      _appendPreroll(bytes);
      return;
    }
    for (final socket in _streamSockets.toList()) {
      try {
        socket.add(bytes);
      } catch (_) {
        _streamSockets.remove(socket);
      }
    }
  }

  void _appendPreroll(List<int> bytes) {
    if (_prerollBytes + bytes.length > _maxPrerollBytes) {
      // Drop oldest by resetting — better to wait for next IDR than OOM.
      _preroll.clear();
      _prerollBytes = 0;
    }
    _preroll.add(bytes);
    _prerollBytes += bytes.length;
  }

  Future<void> stop() async {
    for (final socket in _streamSockets.toList()) {
      try {
        await socket.close();
      } catch (_) {}
    }
    _streamSockets.clear();
    _preroll.clear();
    _prerollBytes = 0;
    if (_firstClient != null && !_firstClient!.isCompleted) {
      _firstClient!.complete();
    }
    _firstClient = null;
    await _server?.close(force: true);
    _server = null;
    _streamUrl = null;
  }
}
