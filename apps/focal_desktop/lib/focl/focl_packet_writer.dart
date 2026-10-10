import 'dart:typed_data';

/// Encodes FOCL [StreamPacket] frames (matches Android `StreamPacket.encode`).
class FoclPacketWriter {
  static const codecH264 = 1;
  static const typeVideoNal = 1;
  static const typeHeartbeat = 6;
  static const flagKeyframe = 0x01;
  static const flagConfig = 0x02;
  static const flagEncrypted = 0x04;

  static Uint8List encode({
    required int codec,
    required int typeCode,
    required int flags,
    required int timestampUs,
    required List<int> payload,
  }) {
    final out = Uint8List(20 + payload.length);
    final view = ByteData.sublistView(out);
    out[0] = 0x46;
    out[1] = 0x4f;
    out[2] = 0x43;
    out[3] = 0x4c;
    out[4] = 1;
    out[5] = codec;
    out[6] = typeCode;
    out[7] = flags;
    view.setUint64(8, timestampUs, Endian.big);
    view.setUint32(16, payload.length, Endian.big);
    out.setRange(20, out.length, payload);
    return out;
  }
}
