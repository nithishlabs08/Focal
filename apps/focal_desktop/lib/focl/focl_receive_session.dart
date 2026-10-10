import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:cryptography/cryptography.dart';
import 'package:flutter/foundation.dart';

import 'focl_crypto.dart';
import 'focl_types.dart';
import 'focl_packet.dart';

class FoclVideoChunk {
  const FoclVideoChunk({
    required this.payload,
    required this.isKeyframe,
    required this.isConfig,
    required this.timestampUs,
  });

  final List<int> payload;
  final bool isKeyframe;
  final bool isConfig;
  final int timestampUs;
}

class FoclAudioChunk {
  const FoclAudioChunk({required this.payload});

  final List<int> payload;
}

/// FOCL TCP session: auth, decrypt, fan-out video/audio streams (no disk).
class FoclReceiveSession {
  static const flagKeyframe = 0x01;
  static const flagConfig = 0x02;

  FoclReceiveSession() {
    _openStreamControllers();
  }

  Socket? _socket;
  StreamSubscription<List<int>>? _sub;
  SecretKey? _sessionKey;
  final FoclPacketReader _reader = FoclPacketReader();
  StreamController<FoclVideoChunk>? _videoController;
  StreamController<FoclAudioChunk>? _audioController;
  final _pendingDecrypt = <Future<void>>[];
  final _authBuffer = <int>[];
  var _authed = false;
  Completer<void>? _authCompleter;

  var _videoBytes = 0;
  var _audioBytes = 0;
  var _lastBytes = 0;
  var _frameCount = 0;
  var _lastFpsTick = DateTime.now();
  var _fps = 0.0;
  var _bitrateMbps = 0.0;
  var _latencyMs = 0;

  Stream<FoclVideoChunk> get videoStream {
    final c = _videoController;
    if (c == null) {
      return const Stream.empty();
    }
    return c.stream;
  }

  Stream<FoclAudioChunk> get audioStream {
    final c = _audioController;
    if (c == null) {
      return const Stream.empty();
    }
    return c.stream;
  }

  bool get isConnected => _socket != null && _authed;

  Future<void> connect({
    required String host,
    required int port,
    required String pin,
    bool useTls = false,
    FoclStatsCallback? onStats,
  }) async {
    await _closeTransport();
    if (_videoController == null || _videoController!.isClosed) {
      _openStreamControllers();
    }
    _sessionKey = await FoclCrypto.deriveKey(pin);
    _authed = false;
    _authBuffer.clear();
    _authCompleter = Completer<void>();

    final socket = await Socket.connect(host, port, timeout: const Duration(seconds: 8));
    Socket active = socket;
    if (useTls) {
      active = await SecureSocket.secure(
        socket,
        host: host,
        onBadCertificate: (_) => true,
      );
    }
    _socket = active;

    active.write('AUTH $pin\n');

    _sub = active.listen(
      (data) {
        if (!_authed) {
          _consumeAuthBytes(data, onStats);
        } else {
          _reader.add(data);
          for (final frame in _reader.takeFrames()) {
            _handleFrame(frame, onStats);
          }
        }
      },
      onDone: () async {
        await _flushDecrypt();
      },
      onError: (_) {
        if (_authCompleter != null && !_authCompleter!.isCompleted) {
          _authCompleter!.completeError(FoclAuthException('socket error'));
        }
      },
      cancelOnError: true,
    );

    await _authCompleter!.future.timeout(
      const Duration(seconds: 12),
      onTimeout: () {
        throw FoclAuthException('auth timeout');
      },
    );
  }

  @visibleForTesting
  void feedFrame(FoclFrame frame, [FoclStatsCallback? onStats]) {
    _handleFrame(frame, onStats);
  }

  void _consumeAuthBytes(List<int> data, FoclStatsCallback? onStats) {
    _authBuffer.addAll(data);
    final newline = _authBuffer.indexOf(10);
    if (newline < 0) return;

    final line = utf8.decode(_authBuffer.sublist(0, newline)).trim();
    final rest = _authBuffer.sublist(newline + 1);
    _authBuffer.clear();

    if (!line.startsWith('AUTH_OK')) {
      _authCompleter?.completeError(FoclAuthException(line));
      return;
    }

    _authed = true;
    _authCompleter?.complete();
    if (rest.isNotEmpty) {
      _reader.add(rest);
      for (final frame in _reader.takeFrames()) {
        _handleFrame(frame, onStats);
      }
    }
  }

  void _handleFrame(FoclFrame frame, FoclStatsCallback? onStats) {
    final payload = frame.payload;
    if ((frame.flags & FoclFrame.flagEncrypted) != 0) {
      final key = _sessionKey;
      if (key == null) return;
      _pendingDecrypt.add(
        FoclCrypto.decrypt(key, payload).then((clear) {
          if (clear != null) {
            _dispatchPayload(
              frame.typeCode,
              frame.flags,
              frame.timestampUs,
              clear,
              onStats,
            );
          }
        }),
      );
      return;
    }
    _dispatchPayload(frame.typeCode, frame.flags, frame.timestampUs, payload, onStats);
  }

  void _dispatchPayload(
    int typeCode,
    int flags,
    int timestampUs,
    List<int> payload,
    FoclStatsCallback? onStats,
  ) {
    if (typeCode == FoclFrame.typeVideoNal) {
      _videoBytes += payload.length;
      _frameCount++;
      final videoOut = _videoController;
      if (videoOut != null && !videoOut.isClosed) {
        videoOut.add(
          FoclVideoChunk(
            payload: payload,
            isKeyframe: (flags & flagKeyframe) != 0,
            isConfig: (flags & flagConfig) != 0,
            timestampUs: timestampUs,
          ),
        );
      }
      _emitStatsIfDue(onStats);
    } else if (typeCode == FoclFrame.typeAudioRaw) {
      _audioBytes += payload.length;
      final audioOut = _audioController;
      if (audioOut != null && !audioOut.isClosed) {
        audioOut.add(FoclAudioChunk(payload: payload));
      }
      _emitStatsIfDue(onStats);
    } else if (typeCode == FoclFrame.typeHeartbeat) {
      _latencyMs = timestampUs.clamp(0, 5000);
      _emitStatsIfDue(onStats);
    }
  }

  void _emitStatsIfDue(FoclStatsCallback? onStats) {
    final now = DateTime.now();
    final elapsed = now.difference(_lastFpsTick).inMilliseconds;
    if (elapsed < 1000) return;
    _fps = _frameCount * 1000 / elapsed;
    final totalBytes = _videoBytes + _audioBytes;
    final bytesDelta = totalBytes - _lastBytes;
    _bitrateMbps = (bytesDelta * 8) / (elapsed * 1000);
    _lastBytes = totalBytes;
    _frameCount = 0;
    _lastFpsTick = now;
    onStats?.call(
      FoclReceiveStats(
        videoBytes: _videoBytes,
        audioBytes: _audioBytes,
        fps: _fps,
        bitrateMbps: _bitrateMbps,
        latencyMs: _latencyMs,
      ),
    );
  }

  Future<void> _flushDecrypt() async {
    if (_pendingDecrypt.isNotEmpty) {
      await Future.wait(_pendingDecrypt);
      _pendingDecrypt.clear();
    }
  }

  void _openStreamControllers() {
    _videoController = StreamController<FoclVideoChunk>.broadcast();
    _audioController = StreamController<FoclAudioChunk>.broadcast();
  }

  Future<void> _closeTransport() async {
    await _sub?.cancel();
    _sub = null;
    await _flushDecrypt();
    try {
      await _socket?.close();
    } catch (_) {}
    _socket = null;
    _videoBytes = 0;
    _audioBytes = 0;
    _lastBytes = 0;
    _fps = 0;
    _bitrateMbps = 0;
    _latencyMs = 0;
    _frameCount = 0;
    _authed = false;
    _authCompleter = null;
  }

  Future<void> disconnect() async {
    await _closeTransport();
    final videoOut = _videoController;
    if (videoOut != null && !videoOut.isClosed) {
      await videoOut.close();
    }
    _videoController = null;
    final audioOut = _audioController;
    if (audioOut != null && !audioOut.isClosed) {
      await audioOut.close();
    }
    _audioController = null;
  }
}
