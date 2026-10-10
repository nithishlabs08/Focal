import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:cryptography/cryptography.dart';

import '../discovery/focal_host_discovery.dart';
import 'focl_crypto.dart';
import 'focl_packet_writer.dart';
import 'linux_portal_screencast_session.dart';
import 'linux_screen_capture.dart';

class FoclHostState {
  const FoclHostState({
    required this.isHosting,
    required this.pin,
    required this.port,
    required this.serviceName,
    this.viewerCount = 0,
    this.statusMessage = '',
    this.screenActive = false,
  });

  final bool isHosting;
  final String pin;
  final int port;
  final String serviceName;
  final int viewerCount;
  final String statusMessage;
  final bool screenActive;

  static const idle = FoclHostState(
    isHosting: false,
    pin: '',
    port: 8080,
    serviceName: '',
  );
}

typedef FoclHostStateCallback = void Function(FoclHostState state);

typedef FoclHostStartResult = ({
  bool success,
  bool cancelled,
  String? error,
});

/// FOCL sender on desktop (screen via ffmpeg on Linux when available).
class FoclHost {
  FoclHost();

  static const defaultPort = 8080;

  final _discovery = FocalHostDiscovery();
  ServerSocket? _server;
  final _clients = <_HostedClient>[];
  Process? _ffmpeg;
  LinuxPortalScreencastSession? _portalSession;
  Timer? _heartbeatTimer;
  SecretKey? _sessionKey;
  final List<List<int>> _cachedConfigNals = [];
  List<int>? _cachedKeyframeNal;
  var _pin = '';
  var _serviceName = '';
  var _running = false;
  FoclHostStateCallback? onStateChanged;

  FoclHostState get state => FoclHostState(
        isHosting: _running,
        pin: _pin,
        port: defaultPort,
        serviceName: _serviceName,
        viewerCount: _clients.length,
        statusMessage: _statusMessage,
        screenActive: _ffmpeg != null,
      );

  var _statusMessage = '';

  Future<FoclHostStartResult> start({required String displayName}) async {
    if (_running) {
      return (success: true, cancelled: false, error: null);
    }

    LinuxScreenCapturePlan? capturePlan;
    if (Platform.isLinux) {
      if (!await LinuxScreenCapture.ffmpegAvailable()) {
        return (
          success: false,
          cancelled: false,
          error:
              'ffmpeg is required to share your screen. Install it (e.g. sudo apt install ffmpeg) and try again.',
        );
      }
      final prepared = await LinuxScreenCapture.prepare();
      if (prepared.cancelled) {
        return (success: false, cancelled: true, error: null);
      }
      if (prepared.errorMessage != null) {
        return (success: false, cancelled: false, error: prepared.errorMessage);
      }
      capturePlan = prepared.plan;
    }

    _pin = _randomPin();
    _sessionKey = await FoclCrypto.deriveKey(_pin);
    _serviceName = _mDnsName(displayName);

    try {
      _server = await ServerSocket.bind(InternetAddress.anyIPv4, defaultPort);
    } on SocketException {
      return (
        success: false,
        cancelled: false,
        error:
            'Port $defaultPort is already in use. Stop other sharing or close Focal and try again.',
      );
    }
    _running = true;
    _statusMessage = 'Waiting for viewers…';
    _emit();

    _server!.listen(_onConnection);

    await _discovery.start(
      serviceName: _serviceName,
      port: defaultPort,
      pin: _pin,
    );
    if (!_discovery.isPublishing) {
      _statusMessage =
          'Hosting on port $defaultPort — use your phone’s “Connect by IP” if this PC does not appear in Nearby.';
      _emit();
    }

    _heartbeatTimer = Timer.periodic(const Duration(seconds: 2), (_) {
      unawaited(_broadcastHeartbeat());
    });

    if (Platform.isLinux) {
      await _startLinuxScreenCapture(capturePlan!);
    } else {
      _statusMessage =
          'Hosting on this port — screen capture is Linux-only for now; viewers can still pair.';
      _emit();
    }
    return (success: true, cancelled: false, error: null);
  }

  Future<void> stop() async {
    _running = false;
    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;
    _ffmpeg?.kill();
    _ffmpeg = null;
    await _portalSession?.close();
    _portalSession = null;
    _cachedConfigNals.clear();
    _cachedKeyframeNal = null;
    await _discovery.stop();
    for (final c in _clients) {
      await c.socket.close();
    }
    _clients.clear();
    await _server?.close();
    _server = null;
    _pin = '';
    _serviceName = '';
    _statusMessage = '';
    _emit();
  }

  void _emit() => onStateChanged?.call(state);

  Future<void> _onConnection(Socket socket) async {
    if (!_running) {
      await socket.close();
      return;
    }
    try {
      socket.setOption(SocketOption.tcpNoDelay, true);
      final lineCompleter = Completer<String>();
      final buffer = <int>[];
      late final StreamSubscription<List<int>> sub;
      sub = socket.listen((data) {
        buffer.addAll(data);
        final newline = buffer.indexOf(10);
        if (newline >= 0 && !lineCompleter.isCompleted) {
          final line = utf8.decode(buffer.sublist(0, newline)).trim();
          lineCompleter.complete(line);
        }
      });

      final line = await lineCompleter.future.timeout(
        const Duration(seconds: 15),
        onTimeout: () => '',
      );

      if (!line.startsWith('AUTH ')) {
        await socket.close();
        await sub.cancel();
        return;
      }
      final candidate = line.substring(5).trim();
      if (candidate != _pin) {
        socket.write('AUTH_ERR invalid_pin\n');
        await socket.flush();
        await socket.close();
        await sub.cancel();
        return;
      }
      socket.write('AUTH_OK ENC1\n');
      await socket.flush();
      final client = _HostedClient(socket, sub);
      _clients.add(client);
      _statusMessage = 'Viewer connected';
      _emit();
      unawaited(_sendStartupNals(client));
    } catch (_) {
      await socket.close();
    }
  }

  Future<void> _broadcastHeartbeat() async {
    final key = _sessionKey;
    if (key == null || _clients.isEmpty) return;
    final payload = await FoclCrypto.encrypt(key, [0]);
    final packet = FoclPacketWriter.encode(
      codec: FoclPacketWriter.codecH264,
      typeCode: FoclPacketWriter.typeHeartbeat,
      flags: FoclPacketWriter.flagEncrypted,
      timestampUs: DateTime.now().microsecondsSinceEpoch,
      payload: payload,
    );
    await _broadcast(packet);
  }

  Future<void> _broadcastVideo(
    List<int> nal,
    {required bool keyframe, required bool config}
  ) async {
    final key = _sessionKey;
    if (key == null || _clients.isEmpty) return;
    var flags = FoclPacketWriter.flagEncrypted;
    if (keyframe) flags |= FoclPacketWriter.flagKeyframe;
    if (config) flags |= FoclPacketWriter.flagConfig;
    final payload = await FoclCrypto.encrypt(key, nal);
    final packet = FoclPacketWriter.encode(
      codec: FoclPacketWriter.codecH264,
      typeCode: FoclPacketWriter.typeVideoNal,
      flags: flags,
      timestampUs: DateTime.now().microsecondsSinceEpoch,
      payload: payload,
    );
    await _broadcast(packet);
  }

  Future<void> _broadcast(List<int> packet) async {
    final dead = <_HostedClient>[];
    for (final c in _clients) {
      try {
        c.socket.add(packet);
        await c.socket.flush();
      } catch (_) {
        dead.add(c);
      }
    }
    for (final d in dead) {
      _clients.remove(d);
      await d.socket.close();
    }
    if (dead.isNotEmpty) _emit();
  }

  Future<void> _startLinuxScreenCapture(LinuxScreenCapturePlan plan) async {
    _portalSession = plan.portalSession;

    _ffmpeg = await Process.start(
      'ffmpeg',
      [
        '-loglevel',
        'error',
        ...plan.ffmpegInputArgs,
        '-c:v',
        'libx264',
        '-preset',
        'ultrafast',
        '-tune',
        'zerolatency',
        '-pix_fmt',
        'yuv420p',
        '-g',
        '30',
        '-f',
        'h264',
        '-',
      ],
      environment: {
        ...Platform.environment,
        if (plan.portalSession != null)
          'PIPEWIRE_NODE': '${plan.portalSession!.pipeWireNodeId}',
      },
    );
    _statusMessage = plan.statusLabel;
    _emit();

    final acc = <int>[];
    final stderrAcc = <int>[];
    _ffmpeg!.stdout.listen((chunk) async {
      acc.addAll(chunk);
      while (true) {
        final nal = _takeAnnexBNal(acc);
        if (nal == null) break;
        final type = nal.length > 4 ? nal[4] & 0x1f : 0;
        final isConfig = type == 7 || type == 8;
        final isKeyframe = type == 5;
        if (isConfig) {
          _cachedConfigNals.add(List<int>.from(nal));
        } else if (isKeyframe) {
          _cachedKeyframeNal = List<int>.from(nal);
        }
        await _broadcastVideo(nal, keyframe: isKeyframe, config: isConfig);
      }
    });
    _ffmpeg!.stderr.listen((chunk) {
      stderrAcc.addAll(chunk);
      if (stderrAcc.length > 4096) {
        stderrAcc.removeRange(0, stderrAcc.length - 2048);
      }
    });
    unawaited(_ffmpeg!.exitCode.then((code) async {
      if (!_running || code == 0) return;
      final err = utf8.decode(stderrAcc, allowMalformed: true).trim();
      _statusMessage = err.isNotEmpty
          ? 'Screen capture stopped: $err'
          : 'Screen capture stopped (ffmpeg exit $code).';
      _emit();
      await _portalSession?.close();
      _portalSession = null;
    }));
  }

  Future<void> _sendStartupNals(_HostedClient client) async {
    final key = _sessionKey;
    if (key == null) return;
    for (final nal in _cachedConfigNals) {
      await _sendVideoToClient(
        client,
        nal,
        keyframe: false,
        config: true,
      );
    }
    final idr = _cachedKeyframeNal;
    if (idr != null) {
      await _sendVideoToClient(client, idr, keyframe: true, config: false);
    }
  }

  Future<void> _sendVideoToClient(
    _HostedClient client,
    List<int> nal, {
    required bool keyframe,
    required bool config,
  }) async {
    final key = _sessionKey;
    if (key == null) return;
    var flags = FoclPacketWriter.flagEncrypted;
    if (keyframe) flags |= FoclPacketWriter.flagKeyframe;
    if (config) flags |= FoclPacketWriter.flagConfig;
    final payload = await FoclCrypto.encrypt(key, nal);
    final packet = FoclPacketWriter.encode(
      codec: FoclPacketWriter.codecH264,
      typeCode: FoclPacketWriter.typeVideoNal,
      flags: flags,
      timestampUs: DateTime.now().microsecondsSinceEpoch,
      payload: payload,
    );
    try {
      client.socket.add(packet);
      await client.socket.flush();
    } catch (_) {}
  }

  static List<int>? _takeAnnexBNal(List<int> buffer) {
    int start = -1;
    for (var i = 0; i < buffer.length - 3; i++) {
      if (buffer[i] == 0 &&
          buffer[i + 1] == 0 &&
          buffer[i + 2] == 1) {
        start = i;
        break;
      }
      if (i < buffer.length - 4 &&
          buffer[i] == 0 &&
          buffer[i + 1] == 0 &&
          buffer[i + 2] == 0 &&
          buffer[i + 3] == 1) {
        start = i;
        break;
      }
    }
    if (start < 0) return null;
    if (start > 0) buffer.removeRange(0, start);

    var next = -1;
    for (var i = 4; i < buffer.length - 3; i++) {
      if (buffer[i] == 0 && buffer[i + 1] == 0 && buffer[i + 2] == 1) {
        next = i;
        break;
      }
      if (i < buffer.length - 4 &&
          buffer[i] == 0 &&
          buffer[i + 1] == 0 &&
          buffer[i + 2] == 0 &&
          buffer[i + 3] == 1) {
        next = i;
        break;
      }
    }
    if (next < 0) return null;
    final nal = buffer.sublist(0, next);
    buffer.removeRange(0, next);
    return nal;
  }

  static String _randomPin() {
    final r = Random.secure();
    return (100000 + r.nextInt(900000)).toString();
  }

  static String _mDnsName(String displayName) {
    var clean = displayName
        .trim()
        .replaceAll(' ', '-')
        .replaceAll(RegExp(r'[^A-Za-z0-9\-_]'), '');
    if (clean.length > 40) {
      clean = clean.substring(0, 40);
    }
    final base = clean.isEmpty ? 'Desktop' : clean;
    return 'Focal-$base';
  }
}

class _HostedClient {
  _HostedClient(this.socket, this.subscription);
  final Socket socket;
  final StreamSubscription<List<int>> subscription;
}
