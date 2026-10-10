import 'dart:typed_data';

class FoclFrame {
  const FoclFrame({
    required this.typeCode,
    required this.flags,
    required this.timestampUs,
    required this.payload,
  });

  final int typeCode;
  final int flags;
  final int timestampUs;
  final List<int> payload;

  static const magic = [0x46, 0x4f, 0x43, 0x4c]; // FOCL
  static const headerSize = 20;
  static const flagEncrypted = 0x04;
  static const typeVideoNal = 1;
  static const typeAudioRaw = 2;
  static const typeHeartbeat = 6;
}

/// Reads FOCL frames from a TCP byte stream (skips HTTP/text lines like the Python CLI).
class FoclPacketReader {
  final BytesBuilder _buffer = BytesBuilder(copy: false);

  void add(List<int> bytes) {
    _buffer.add(bytes);
    _drain();
  }

  final List<FoclFrame> _pending = [];

  List<FoclFrame> takeFrames() {
    final out = List<FoclFrame>.from(_pending);
    _pending.clear();
    return out;
  }

  void _drain() {
    while (true) {
      final data = _buffer.toBytes();
      final frameStart = _indexOfMagic(data);
      if (frameStart < 0) {
        _buffer.clear();
        if (data.isNotEmpty) {
          _buffer.add(data);
        }
        return;
      }
      if (frameStart > 0) {
        _buffer.clear();
        _buffer.add(data.sublist(frameStart));
        continue;
      }
      if (data.length < FoclFrame.headerSize) return;

      final flags = data[7];
      final typeCode = data[6];
      final timestampUs = _readUint64Be(data, 8);
      final payloadLen = _readUint32Be(data, 16);
      final total = FoclFrame.headerSize + payloadLen;
      if (data.length < total) return;

      final payload = data.sublist(FoclFrame.headerSize, total);
      _pending.add(
        FoclFrame(
          typeCode: typeCode,
          flags: flags,
          timestampUs: timestampUs,
          payload: payload,
        ),
      );

      _buffer.clear();
      if (data.length > total) {
        _buffer.add(data.sublist(total));
      }
      // Continue parsing if more data buffered.
      if (_buffer.length == 0) return;
    }
  }

  int _indexOfMagic(List<int> data) {
    for (var i = 0; i <= data.length - 4; i++) {
      if (data[i] == 0x46 &&
          data[i + 1] == 0x4f &&
          data[i + 2] == 0x43 &&
          data[i + 3] == 0x4c) {
        return i;
      }
    }
    return -1;
  }

  static int _readUint32Be(List<int> data, int offset) {
    return (data[offset] << 24) |
        (data[offset + 1] << 16) |
        (data[offset + 2] << 8) |
        data[offset + 3];
  }

  static int _readUint64Be(List<int> data, int offset) {
    var value = 0;
    for (var i = 0; i < 8; i++) {
      value = (value << 8) | data[offset + i];
    }
    return value;
  }
}
