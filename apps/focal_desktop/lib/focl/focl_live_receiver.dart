import 'dart:async';

import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';

import 'focl_annexb_bridge.dart';
import 'focl_types.dart';
import 'focl_file_recorder.dart';
import 'focl_pcm_player.dart';
import 'focl_receive_session.dart';

/// Live receive: one FOCL session → localhost Annex-B bridge → in-app video + PCM audio.
class FocalLiveReceiver {
  FoclReceiveSession? _session;
  FoclAnnexBBridge? _bridge;
  FoclFileRecorder? _recorder;
  FoclPcmPlayer? _pcmPlayer;
  Player? _player;
  VideoController? _videoController;
  StreamSubscription<FoclVideoChunk>? _videoBridgeSub;
  StreamSubscription<bool>? _playingSub;

  VideoController? get videoController => _videoController;

  bool get hasInAppVideo => _videoController != null;

  /// True when optional file recording is active.
  bool get isRecordingToFile => _recorder != null;

  bool get isMuted => _pcmPlayer?.isMuted ?? false;

  void setMuted(bool muted) {
    _pcmPlayer?.setMuted(muted);
  }

  bool toggleMute() {
    return _pcmPlayer?.toggleMute() ?? false;
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

    final session = FoclReceiveSession();
    _session = session;

    final bridge = FoclAnnexBBridge();
    await bridge.start();
    _bridge = bridge;

    _videoBridgeSub = session.videoStream.listen((chunk) {
      bridge.writeNal(chunk.payload);
    });

    if (saveToFile && outputH264Path != null) {
      final recorder = FoclFileRecorder();
      _recorder = recorder;
      await recorder.start(
        videoStream: session.videoStream,
        outputH264Path: outputH264Path,
      );
    }

    final pcm = FoclPcmPlayer();
    _pcmPlayer = pcm;
    await pcm.start(
      pcmStream: session.audioStream.map((c) => c.payload),
    );

    _player = Player(configuration: const PlayerConfiguration(title: 'Focal'));
    _videoController = VideoController(_player!);

    await _player!.open(
      Media(
        bridge.streamUrl,
        httpHeaders: bridge.playbackHeaders,
      ),
    );

    _playingSub = _player!.stream.playing.listen(onPlaybackState ?? (_) {});

    await session.connect(
      host: host,
      port: port,
      pin: pin,
      useTls: useTls,
      onStats: onStats,
    );
  }

  Future<void> disconnect() async {
    await _videoBridgeSub?.cancel();
    _videoBridgeSub = null;
    await _playingSub?.cancel();
    _playingSub = null;
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
  }
}
