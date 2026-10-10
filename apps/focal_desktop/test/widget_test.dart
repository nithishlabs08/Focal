import 'package:focal_desktop/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('Focal desktop app loads', (WidgetTester tester) async {
    await tester.binding.setSurfaceSize(const Size(1280, 800));
    await tester.pumpWidget(
      const TickerMode(enabled: false, child: FocalDesktopApp()),
    );
    await tester.pumpAndSettle();
    expect(find.text('Nearby devices'), findsOneWidget);
    expect(find.text('Focal'), findsOneWidget);
    await tester.tap(find.text('Send').last);
    await tester.pumpAndSettle();
    expect(find.text('Share screen'), findsOneWidget);
    await tester.binding.setSurfaceSize(null);
  });
}
