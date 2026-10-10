import 'dart:async';
import 'dart:io';

/// Plays FOCL PCM (48 kHz mono s16le) via a subprocess pipe when available.
class FoclPcmPlayer {
  Process? _process;
  IOSink? _stdin;
  StreamSubscription<List<int>>? _sub;
  var _muted = false;
  var _started = false;

  bool get isMuted => _muted;

  Future<void> start({required Stream<List<int>> pcmStream}) async {
    await stop();
    _started = await _spawnPlayer();
    if (!_started) return;

    _sub = pcmStream.listen(
      (data) {
        if (_muted || data.isEmpty) return;
        try {
          _stdin?.add(data);
        } catch (_) {}
      },
      onError: (_) {},
    );
  }

  Future<bool> _spawnPlayer() async {
    try {
      if (Platform.isLinux) {
        _process = await Process.start(
          'aplay',
          ['-f', 'S16_LE', '-r', '48000', '-c', '1', '-q'],
          mode: ProcessStartMode.normal,
        );
      } else if (Platform.isMacOS) {
        _process = await Process.start(
          'ffplay',
          [
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
          ],
        );
      } else if (Platform.isWindows) {
        _process = await Process.start(
          'ffplay',
          [
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
          ],
          runInShell: true,
        );
      } else {
        return false;
      }
      _stdin = _process!.stdin;
      return true;
    } catch (_) {
      _process = null;
      _stdin = null;
      return false;
    }
  }

  void setMuted(bool mute) {
    _muted = mute;
  }

  bool toggleMute() {
    _muted = !_muted;
    return _muted;
  }

  Future<void> stop() async {
    await _sub?.cancel();
    _sub = null;
    try {
      await _stdin?.flush();
      await _stdin?.close();
    } catch (_) {}
    _stdin = null;
    _process?.kill(ProcessSignal.sigterm);
    _process = null;
    _started = false;
  }
}
