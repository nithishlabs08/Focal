import 'dart:async';
import 'dart:io';

import 'package:media_kit/media_kit.dart';

import 'focl_pcm_wav_bridge.dart';

/// Plays FOCL PCM (48 kHz mono s16le).
/// Prefers in-process media_kit via a localhost WAV bridge; falls back to aplay/ffplay.
class FoclPcmPlayer {
  FoclPcmWavBridge? _wavBridge;
  Player? _player;
  Process? _process;
  IOSink? _stdin;
  StreamSubscription<List<int>>? _sub;
  var _muted = false;
  var _available = false;

  bool get isMuted => _muted;

  bool get isAvailable => _available;

  Future<void> start({required Stream<List<int>> pcmStream}) async {
    await stop();

    _available = await _startMediaKit(pcmStream);
    if (!_available) {
      _available = await _startSubprocess(pcmStream);
    }
  }

  Future<bool> _startMediaKit(Stream<List<int>> pcmStream) async {
    try {
      final bridge = FoclPcmWavBridge();
      await bridge.start();
      _wavBridge = bridge;

      final player = Player(
        configuration: const PlayerConfiguration(
          title: 'Focal Audio',
          bufferSize: 32 * 1024,
        ),
      );
      _player = player;

      await player.open(
        Media(bridge.streamUrl),
        play: true,
      );
      await player.setVolume(100);

      _sub = pcmStream.listen(
        (data) {
          if (_muted || data.isEmpty) return;
          bridge.writePcm(data);
        },
        onError: (_) {},
      );
      return true;
    } catch (_) {
      await _tearDownMediaKit();
      return false;
    }
  }

  Future<bool> _startSubprocess(Stream<List<int>> pcmStream) async {
    try {
      final proc = await _spawnSubprocess();
      if (proc == null) return false;
      _process = proc;
      _stdin = proc.stdin;
      _sub = pcmStream.listen(
        (data) {
          if (_muted || data.isEmpty) return;
          try {
            _stdin?.add(data);
          } catch (_) {}
        },
        onError: (_) {},
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<Process?> _spawnSubprocess() async {
    final candidates = <Future<Process> Function()>[];
    if (Platform.isLinux) {
      candidates.addAll([
        () => Process.start(
              'aplay',
              ['-f', 'S16_LE', '-r', '48000', '-c', '1', '-q'],
            ),
        () => Process.start(
              'pw-play',
              ['--rate=48000', '--channels=1', '--format=s16', '-'],
            ),
        () => Process.start(
              'paplay',
              ['--rate=48000', '--channels=1', '--format=s16le', '--raw'],
            ),
        () => Process.start('ffplay', _ffplayArgs),
      ]);
    } else if (Platform.isMacOS || Platform.isWindows) {
      candidates.add(
        () => Process.start(
          'ffplay',
          _ffplayArgs,
          runInShell: Platform.isWindows,
        ),
      );
    }

    for (final start in candidates) {
      try {
        return await start();
      } catch (_) {}
    }
    return null;
  }

  static const _ffplayArgs = [
    '-nodisp',
    '-autoexit',
    '-loglevel',
    'quiet',
    '-f',
    's16le',
    '-ar',
    '48000',
    '-ac',
    '1',
    '-i',
    'pipe:0',
  ];

  void setMuted(bool mute) {
    _muted = mute;
    unawaited(_player?.setVolume(mute ? 0 : 100) ?? Future.value());
  }

  bool toggleMute() {
    setMuted(!_muted);
    return _muted;
  }

  Future<void> _tearDownMediaKit() async {
    await _player?.dispose();
    _player = null;
    await _wavBridge?.stop();
    _wavBridge = null;
  }

  Future<void> stop() async {
    await _sub?.cancel();
    _sub = null;
    await _tearDownMediaKit();
    try {
      await _stdin?.flush();
      await _stdin?.close();
    } catch (_) {}
    _stdin = null;
    _process?.kill(ProcessSignal.sigterm);
    _process = null;
    _available = false;
  }
}
