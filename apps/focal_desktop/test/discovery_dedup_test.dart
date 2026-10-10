import 'package:focal_desktop/models/discovered_sender.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('senderEndpointId is stable per host and port', () {
    expect(senderEndpointId('192.168.1.10', 8080), '192.168.1.10:8080');
  });
}
