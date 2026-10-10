import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:focal_desktop/focl/focl_annexb_bridge.dart';
import 'package:flutter_test/flutter_test.dart';

Future<List<int>> _rawGetStream(String url, {required String pin}) async {
  final uri = Uri.parse(url);
  final socket = await Socket.connect(uri.host, uri.port);
  socket.add(
    utf8.encode(
      'GET ${uri.path} HTTP/1.1\r\n'
      'Host: ${uri.host}\r\n'
      'X-Focal-Pin: $pin\r\n'
      'Connection: close\r\n'
      '\r\n',
    ),
  );
  await socket.flush();

  final builder = BytesBuilder(copy: false);
  await for (final chunk in socket) {
    builder.add(chunk);
    final data = builder.toBytes();
    final headerEnd = _indexOfHeaderEnd(data);
    if (headerEnd >= 0) {
      final body = data.sublist(headerEnd);
      socket.destroy();
      return body;
    }
  }
  return builder.takeBytes();
}

int _indexOfHeaderEnd(List<int> data) {
  for (var i = 0; i < data.length - 3; i++) {
    if (data[i] == 13 &&
        data[i + 1] == 10 &&
        data[i + 2] == 13 &&
        data[i + 3] == 10) {
      return i + 4;
    }
  }
  return -1;
}

Future<int> _statusCode(String url, {required String pin}) async {
  final uri = Uri.parse(url);
  final socket = await Socket.connect(uri.host, uri.port);
  socket.add(
    utf8.encode(
      'GET ${uri.path} HTTP/1.1\r\n'
      'Host: ${uri.host}\r\n'
      'X-Focal-Pin: $pin\r\n'
      '\r\n',
    ),
  );
  await socket.flush();
  final first = await socket.first;
  socket.destroy();
  final line = utf8.decode(first).split('\r\n').first;
  return int.parse(line.split(' ')[1]);
}

void main() {
  test('prerolls NALs until HTTP client connects', () async {
    final bridge = FoclAnnexBBridge();
    await bridge.start(localPin: '1234');

    bridge.writeNal([0, 0, 0, 1, 0x67]);
    bridge.writeNal([0, 0, 0, 1, 0x65]);
    expect(bridge.hasClient, isFalse);

    final bodyFuture = _rawGetStream(bridge.streamUrl, pin: '1234');
    await bridge.waitForClient(timeout: const Duration(seconds: 2));
    expect(bridge.hasClient, isTrue);

    final body = await bodyFuture.timeout(const Duration(seconds: 2));
    expect(body, containsAllInOrder([0, 0, 0, 1, 0x67]));
    expect(body, containsAllInOrder([0, 0, 0, 1, 0x65]));

    await bridge.stop();
  });

  test('rejects wrong pin', () async {
    final bridge = FoclAnnexBBridge();
    await bridge.start(localPin: '9999');
    final status = await _statusCode(bridge.streamUrl, pin: '0000');
    expect(status, 401);
    await bridge.stop();
  });
}
