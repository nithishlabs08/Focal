import 'package:focal_desktop/focl/focl_packet.dart';
import 'package:focal_desktop/focl/focl_receive_session.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('dispatches video and audio FOCL payloads to streams', () async {
    final session = FoclReceiveSession();
    final video = <FoclVideoChunk>[];
    final audio = <FoclAudioChunk>[];
    final videoSub = session.videoStream.listen(video.add);
    final audioSub = session.audioStream.listen(audio.add);

    session.feedFrame(
      const FoclFrame(
        typeCode: FoclFrame.typeVideoNal,
        flags: FoclReceiveSession.flagKeyframe,
        timestampUs: 1000,
        payload: [0, 0, 0, 1, 0x65],
      ),
      (stats) {
        expect(stats.fps, greaterThanOrEqualTo(0));
      },
    );
    session.feedFrame(
      const FoclFrame(
        typeCode: FoclFrame.typeAudioRaw,
        flags: 0,
        timestampUs: 2000,
        payload: [1, 2, 3, 4],
      ),
    );

    await Future<void>.delayed(const Duration(milliseconds: 50));

    expect(video.length, 1);
    expect(video.first.isKeyframe, isTrue);
    expect(video.first.payload, [0, 0, 0, 1, 0x65]);
    expect(audio.length, 1);
    expect(audio.first.payload, [1, 2, 3, 4]);

    await videoSub.cancel();
    await audioSub.cancel();
    await session.disconnect();
  });

  test('handles heartbeat frames cleanly', () async {
    final session = FoclReceiveSession();
    session.feedFrame(
      const FoclFrame(
        typeCode: FoclFrame.typeHeartbeat,
        flags: 0,
        timestampUs: 42,
        payload: [],
      ),
    );
    await Future<void>.delayed(const Duration(milliseconds: 20));
    await session.disconnect();
  });

  test('stream getters remain safe after disconnect', () async {
    final session = FoclReceiveSession();
    await session.disconnect();
    expect(session.videoStream, isNotNull);
    expect(session.audioStream, isNotNull);
  });
}
