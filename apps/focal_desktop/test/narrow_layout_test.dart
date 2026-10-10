import 'package:focal_desktop/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('no exception with extended navigation rail', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1280, 800));
    await tester.pumpWidget(
      const TickerMode(enabled: false, child: FocalDesktopApp()),
    );
    await tester.pump();
    expect(tester.takeException(), isNull);
    await tester.binding.setSurfaceSize(null);
  });

  testWidgets('no layout exception at narrow width', (tester) async {
    await tester.binding.setSurfaceSize(const Size(640, 720));
    await tester.pumpWidget(
      const TickerMode(enabled: false, child: FocalDesktopApp()),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(tester.takeException(), isNull);

    await tester.tap(find.text('Send'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(tester.takeException(), isNull);

    await tester.binding.setSurfaceSize(null);
  });
}
