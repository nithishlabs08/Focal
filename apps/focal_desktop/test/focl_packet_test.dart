import 'package:focal_desktop/focl/focl_packet.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('parses a minimal FOCL frame', () {
    final payload = [0x00, 0x01, 0x02];
    final header = <int>[
      0x46, 0x4f, 0x43, 0x4c, // FOCL
      1,
      1,
      FoclFrame.typeVideoNal,
      0,
      0, 0, 0, 0, 0, 0, 0, 1,
      0, 0, 0, payload.length,
    ];
    final reader = FoclPacketReader();
    reader.add([...header, ...payload]);
    final frames = reader.takeFrames();
    expect(frames.length, 1);
    expect(frames.first.typeCode, FoclFrame.typeVideoNal);
    expect(frames.first.payload, payload);
  });
}
