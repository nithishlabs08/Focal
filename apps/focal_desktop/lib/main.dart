import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';

import 'theme/focal_theme.dart';
import 'ui/home_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  MediaKit.ensureInitialized();
  runApp(const FocalDesktopApp());
}

class FocalDesktopApp extends StatelessWidget {
  const FocalDesktopApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Focal Desktop',
      debugShowCheckedModeBanner: false,
      theme: FocalTheme.light(),
      darkTheme: FocalTheme.dark(),
      themeMode: ThemeMode.system,
      home: const HomePage(),
    );
  }
}
