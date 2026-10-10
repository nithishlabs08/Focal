import 'dart:convert';
import 'dart:io';

/// LocalSend-style labels for this PC and remembered sender names.
class FocalDesktopPreferences {
  FocalDesktopPreferences({
    required this.deviceDisplayName,
    this.saveRecordingToFile = false,
    Map<String, String>? senderAliases,
  }) : senderAliases = senderAliases ?? {};

  String deviceDisplayName;
  bool saveRecordingToFile;
  final Map<String, String> senderAliases;

  static String defaultDeviceName() {
    final host = Platform.localHostname;
    return host.isNotEmpty ? host : 'My computer';
  }

  static File _prefsFile() {
    final home = Platform.environment['HOME'] ??
        Platform.environment['USERPROFILE'] ??
        Directory.current.path;
    final dir = Directory('$home/.config/focal');
    if (!dir.existsSync()) {
      dir.createSync(recursive: true);
    }
    return File('${dir.path}/desktop_preferences.json');
  }

  static Future<FocalDesktopPreferences> load() async {
    try {
      final file = _prefsFile();
      if (!file.existsSync()) {
        return FocalDesktopPreferences(
          deviceDisplayName: defaultDeviceName(),
        );
      }
      final map = jsonDecode(await file.readAsString()) as Map<String, dynamic>;
      final aliases = <String, String>{};
      final raw = map['sender_aliases'];
      if (raw is Map) {
        for (final e in raw.entries) {
          if (e.key is String && e.value is String) {
            aliases[e.key as String] = (e.value as String).trim();
          }
        }
      }
      final name = (map['device_display_name'] as String?)?.trim();
      final saveFile = map['save_recording_to_file'] == true;
      return FocalDesktopPreferences(
        deviceDisplayName:
            (name != null && name.isNotEmpty) ? name : defaultDeviceName(),
        saveRecordingToFile: saveFile,
        senderAliases: aliases,
      );
    } catch (_) {
      return FocalDesktopPreferences(
        deviceDisplayName: defaultDeviceName(),
      );
    }
  }

  Future<void> save() async {
    final file = _prefsFile();
    final payload = {
      'device_display_name': deviceDisplayName.trim().isEmpty
          ? defaultDeviceName()
          : deviceDisplayName.trim(),
      'sender_aliases': senderAliases,
      'save_recording_to_file': saveRecordingToFile,
    };
    await file.writeAsString(const JsonEncoder.withIndent('  ').convert(payload));
  }

  String labelForSender(String senderId, String discoveredName) {
    final alias = senderAliases[senderId]?.trim();
    if (alias != null && alias.isNotEmpty) {
      return alias;
    }
    return discoveredName;
  }

  Future<void> setSaveRecordingToFile(bool value) async {
    saveRecordingToFile = value;
    await save();
  }

  Future<void> setDeviceName(String name) async {
    deviceDisplayName =
        name.trim().isEmpty ? defaultDeviceName() : name.trim();
    await save();
  }

  Future<void> setSenderAlias(String senderId, String? alias) async {
    final trimmed = alias?.trim();
    if (trimmed == null || trimmed.isEmpty) {
      senderAliases.remove(senderId);
    } else {
      senderAliases[senderId] = trimmed;
    }
    await save();
  }
}
