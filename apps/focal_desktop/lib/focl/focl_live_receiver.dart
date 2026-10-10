import 'dart:async';

import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';

import 'focl_annexb_bridge.dart';
import 'focl_file_recorder.dart';
import 'focl_pcm_player.dart';
import 'focl_receive_session.dart';
import 'focl_types.dart';

enum FocalReceivePhase {
  idle,
  connecting,
  waitingKeyframe,
  streaming,
  reconnecting,
  error,
}

/// Live receive: one FOCL session → localhost Annex-B bridge → in-app video + PCM.
class FocalLiveReceiver {
  static const _maxReconnectAttempts = 5;
  static const _reconnectDelay = Duration(milliseconds: 2500);

  FoclReceiveSession? _session;
  FoclAnnexBBridge? _bridge;
  FoclFileRecorder? _recorder;
  FoclPcmPlayer? _pcmPlayer;
  Player? _player;
  VideoController? _videoController;
  StreamSubscription<FoclVideoChunk>? _videoBridgeSub;
  StreamSubscription<bool>? _playingSub;
  StreamSubscription<String>? _errorSub;

  var _phase = FocalReceivePhase.idle;
  var _userStopped = false;
  var _playerOpened = false;
  var _sawKeyframe = false;
  String? _lastError;

  String? _host;
  int? _port;
  String? _pin;
  bool _useTls = false;
  bool _saveToFile = false;
  String? _outputH264Path;
  void Function(bool playing)? _onPlaybackState;
  FoclStatsCallback? _onStats;
  void Function(FocalReceivePhase phase)? onPhaseChanged;

  VideoController? get videoController => _videoController;

  bool get hasInAppVideo => _videoController != null;

  bool get isRecordingToFile => _recorder != null;

  bool get isMuted => _pcmPlayer?.isMuted ?? false;

  bool get audioAvailable => _pcmPlayer?.isAvailable ?? false;

  FocalReceivePhase get phase => _phase;

  String? get lastError => _lastError;

  String? get recordingPath =>
      isRecordingToFile ? _outputH264Path : null;

  void setMuted(bool muted) {
    _pcmPlayer?.setMuted(muted);
  }

  bool toggleMute() {
    return _pcmPlayer?.toggleMute() ?? false;
  }

  void _setPhase(FocalReceivePhase next) {
    if (_phase == next) return;
    _phase = next;
    onPhaseChanged?.call(next);
  }

  Future<void> connect({
    required String host,
    required int port,
    required String pin,
    required bool useTls,
    required bool saveToFile,
    String? outputH264Path,
    void Function(bool playing)? onPlaybackState,
    FoclStatsCallback? onStats,
  }) async {
    await disconnect();
    _userStopped = false;
    _host = host;
    _port = port;
    _pin = pin;
    _useTls = useTls;
    _saveToFile = saveToFile;
    _outputH264Path = outputH264Path;
    _onPlaybackState = onPlaybackState;
    _onStats = onStats;
    _lastError = null;

    await _startSession(isReconnect: false);
  }

  Future<void> _startSession({required bool isReconnect}) async {
    final host = _host;
    final port = _port;
    final pin = _pin;
    if (host == null || port == null || pin == null) {
      throw StateError('connect() required before session start');
    }

    _setPhase(isReconnect ? FocalReceivePhase.reconnecting : FocalReceivePhase.connecting);
    _playerOpened = false;
    _sawKeyframe = false;

    final session = FoclReceiveSession();
    _session = session;

    final bridge = FoclAnnexBBridge();
    await bridge.start();
    _bridge = bridge;

    _videoBridgeSub = session.videoStream.listen(_onVideoChunk);

    if (_saveToFile && _outputH264Path != null) {
      final recorder = FoclFileRecorder();
      _recorder = recorder;
      await recorder.start(
        videoStream: session.videoStream,
        outputH264Path: _outputH264Path!,
      );
    }

    final pcm = FoclPcmPlayer();
    _pcmPlayer = pcm;
    await pcm.start(
      pcmStream: session.audioStream.map((c) => c.payload),
    );

    _player = Player(
      configuration: const PlayerConfiguration(
        title: 'Focal',
        bufferSize: 64 * 1024,
      ),
    );
    _videoController = VideoController(_player!);
    _playingSub = _player!.stream.playing.listen((playing) {
      _onPlaybackState?.call(playing);
      if (playing) {
        _setPhase(FocalReceivePhase.streaming);
      }
    });
    _errorSub = _player!.stream.error.listen((err) {
      if (err.isEmpty) return;
      _lastError = err;
    });

    // Low-latency preferences for live Annex-B over HTTP.
    try {
      final native = _player!.platform;
      // Best-effort; ignore if platform does not support setProperty.
      // ignore: avoid_dynamic_calls
      await (native as dynamic).setProperty('cache', 'no');
      // ignore: avoid_dynamic_calls
      await (native as dynamic).setProperty('demuxer-max-bytes', '512KiB');
      // ignore: avoid_dynamic_calls
      await (native as dynamic).setProperty('demuxer-readahead-secs', '0.2');
      // ignore: avoid_dynamic_calls
      await (native as dynamic).setProperty('framedrop', 'vo');
    } catch (_) {}

    try {
      await session.connect(
        host: host,
        port: port,
        pin: pin,
        useTls: _useTls,
        onStats: _onStats,
      );
      _setPhase(FocalReceivePhase.waitingKeyframe);
      // Player opens after first keyframe (see _onVideoChunk).
      unawaited(_watchSessionEnd(session));
    } catch (e) {
      _lastError = e.toString();
      _setPhase(FocalReceivePhase.error);
      await _tearDownPipeline(keepCallbacks: true);
      rethrow;
    }
  }

  Future<void> _watchSessionEnd(FoclReceiveSession session) async {
    try {
      await session.done;
    } catch (_) {}
    if (_userStopped || _session != session) return;
    if (_phase == FocalReceivePhase.error) return;
    await _scheduleReconnect();
  }

  void _onVideoChunk(FoclVideoChunk chunk) {
    final bridge = _bridge;
    if (bridge == null) return;

    if (chunk.isKeyframe) _sawKeyframe = true;

    bridge.writeNal(chunk.payload);

    if (!_playerOpened && _sawKeyframe) {
      _playerOpened = true;
      unawaited(_openPlayer());
    }
  }

  Future<void> _openPlayer() async {
    final player = _player;
    final bridge = _bridge;
    if (player == null || bridge == null || _userStopped) return;
    try {
      await player.open(
        Media(
          bridge.streamUrl,
          httpHeaders: bridge.playbackHeaders,
        ),
        play: true,
      );
    } catch (e) {
      _lastError = e.toString();
      _setPhase(FocalReceivePhase.error);
    }
  }

  Future<void> _scheduleReconnect() async {
    if (_userStopped) return;
    final host = _host;
    final port = _port;
    final pin = _pin;
    if (host == null || port == null || pin == null) return;

    for (var attempt = 1; attempt <= _maxReconnectAttempts; attempt++) {
      if (_userStopped) return;
      _setPhase(FocalReceivePhase.reconnecting);
      _lastError = 'Reconnecting ($attempt/$_maxReconnectAttempts)…';
      await _tearDownPipeline(keepCallbacks: true);
      await Future<void>.delayed(_reconnectDelay);
      if (_userStopped) return;
      try {
        await _startSession(isReconnect: true);
        return;
      } on FoclAuthException catch (e) {
        _lastError = e.message;
        _setPhase(FocalReceivePhase.error);
        return;
      } catch (e) {
        _lastError = e.toString();
      }
    }
    _setPhase(FocalReceivePhase.error);
    _lastError ??= 'Could not reconnect to sender';
  }

  Future<void> _tearDownPipeline({required bool keepCallbacks}) async {
    await _videoBridgeSub?.cancel();
    _videoBridgeSub = null;
    await _playingSub?.cancel();
    _playingSub = null;
    await _errorSub?.cancel();
    _errorSub = null;
    await _session?.disconnect();
    _session = null;
    await _recorder?.stop();
    _recorder = null;
    await _pcmPlayer?.stop();
    _pcmPlayer = null;
    await _bridge?.stop();
    _bridge = null;
    await _player?.dispose();
    _player = null;
    _videoController = null;
    _playerOpened = false;
    if (!keepCallbacks) {
      _onPlaybackState = null;
      _onStats = null;
      _host = null;
      _port = null;
      _pin = null;
    }
  }

  Future<void> disconnect() async {
    _userStopped = true;
    await _tearDownPipeline(keepCallbacks: false);
    _setPhase(FocalReceivePhase.idle);
    _lastError = null;
  }
}
