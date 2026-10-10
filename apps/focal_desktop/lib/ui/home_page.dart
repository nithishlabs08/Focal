import 'dart:io';

import 'package:flutter/material.dart';

import '../discovery/focal_discovery.dart';
import '../focl/focl_client.dart';
import '../focl/focl_live_receiver.dart';
import '../focl/focl_host.dart';
import '../focl/linux_display_session.dart';
import '../models/discovered_sender.dart';
import '../settings/focal_desktop_preferences.dart';
import 'pin_dialog.dart';
import 'receive_session_view.dart';
import 'receive_tab_view.dart';
import 'rename_dialog.dart';
import 'send_tab_view.dart';
import 'settings_view.dart';
import 'focal_responsive.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final _discovery = FocalDiscovery();
  final _receiver = FocalLiveReceiver();
  final _host = FoclHost();
  FoclHostState _hostState = FoclHostState.idle;
  List<DiscoveredSender> _senders = [];
  FoclReceiveStats? _stats;
  String? _statusMessage;
  String? _outputPath;
  bool _useTls = false;
  bool _hasError = false;
  bool _receiveMuted = false;
  FocalReceivePhase _receivePhase = FocalReceivePhase.idle;
  DiscoveredSender? _activeSender;
  int _railIndex = 0;
  late final PageController _pageController;
  FocalDesktopPreferences? _prefs;

  @override
  void initState() {
    super.initState();
    _pageController = PageController(initialPage: _railIndex);
    _discovery.start();
    _discovery.senders.listen((list) {
      if (mounted) setState(() => _senders = list);
    });
    FocalDesktopPreferences.load().then((p) {
      if (mounted) setState(() => _prefs = p);
    });
    _host.onStateChanged = (s) {
      if (mounted) setState(() => _hostState = s);
    };
    _receiver.onPhaseChanged = (phase) {
      if (!mounted) return;
      setState(() {
        _receivePhase = phase;
        if (phase == FocalReceivePhase.error) {
          _hasError = true;
          _statusMessage = _receiver.lastError;
        } else if (phase == FocalReceivePhase.streaming) {
          _hasError = false;
        } else if (phase == FocalReceivePhase.reconnecting) {
          _statusMessage = _receiver.lastError;
        }
        _outputPath = _receiver.recordingPath;
      });
    };
  }

  @override
  void dispose() {
    _pageController.dispose();
    _discovery.stop();
    _receiver.disconnect();
    _host.stop();
    super.dispose();
  }

  void _selectTab(int index) {
    if (_inSession && index != 0) return;
    setState(() => _railIndex = index);
    _pageController.jumpToPage(index);
  }

  Future<void> _startLaptopShare() async {
    if (_inSession) {
      _showError('Stop receiving before sharing from this computer.');
      return;
    }
    if (Platform.isLinux && LinuxDisplaySession.detect().isWayland && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Choose a screen or window in the system dialog…'),
          behavior: SnackBarBehavior.floating,
          duration: Duration(seconds: 4),
        ),
      );
    }
    try {
      final result = await _host.start(displayName: _deviceName);
      if (!mounted) return;
      if (result.cancelled) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Screen share cancelled'),
            behavior: SnackBarBehavior.floating,
            width: 280,
          ),
        );
      } else if (result.error != null) {
        _showError(result.error!);
      }
      setState(() => _hostState = _host.state);
    } catch (e) {
      if (mounted) _showError('$e');
    }
  }

  Future<void> _stopLaptopShare() async {
    await _host.stop();
    if (mounted) setState(() => _hostState = FoclHostState.idle);
  }

  bool get _inSession => _activeSender != null;

  String get _deviceName =>
      _prefs?.deviceDisplayName ?? FocalDesktopPreferences.defaultDeviceName();

  String _senderLabel(DiscoveredSender s) {
    return _prefs?.labelForSender(s.id, s.name) ?? s.name;
  }

  Future<void> _renameComputer() async {
    final name = await showRenameDialog(
      context,
      title: 'Rename this computer',
      label: 'Display name',
      initialValue: _deviceName,
      hint: 'Living room PC',
    );
    if (name == null) return;
    _prefs ??= await FocalDesktopPreferences.load();
    await _prefs!.setDeviceName(name);
    if (mounted) setState(() {});
  }

  Future<void> _connectTo(DiscoveredSender sender) async {
    final pin = await showPinDialog(context, _senderLabel(sender));
    if (pin == null || pin.length < 4) return;

    final saveFile = _prefs?.saveRecordingToFile ?? false;
    final home = Platform.environment['HOME'] ?? Directory.current.path;
    final out = saveFile ? '$home/focal_capture.h264' : null;

    setState(() {
      _hasError = false;
      _statusMessage = null;
      _activeSender = sender;
      _outputPath = out;
      _stats = null;
      _railIndex = 0;
      _receivePhase = FocalReceivePhase.connecting;
    });

    try {
      await _receiver.connect(
        host: sender.host,
        port: sender.connectPort(useTls: _useTls),
        pin: pin,
        useTls: _useTls,
        saveToFile: saveFile,
        outputH264Path: out,
        onPlaybackState: (playing) {
          if (mounted && playing) {
            setState(() => _receivePhase = FocalReceivePhase.streaming);
          }
        },
        onStats: (s) {
          if (mounted) setState(() => _stats = s);
        },
      );
      if (mounted) {
        setState(() {
          _outputPath = _receiver.recordingPath;
          _receivePhase = _receiver.phase;
        });
      }
    } on FoclAuthException catch (e) {
      if (mounted) {
        setState(() {
          _hasError = true;
          _statusMessage = e.message;
          _receivePhase = FocalReceivePhase.error;
          _activeSender = null;
        });
        _showError('Pairing failed: ${e.message}');
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _hasError = true;
          _statusMessage = e.toString();
          _receivePhase = FocalReceivePhase.error;
          _activeSender = null;
        });
        _showError('$e');
      }
    }
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg),
        behavior: SnackBarBehavior.floating,
        width: 420,
      ),
    );
  }

  Future<void> _stop() async {
    await _receiver.disconnect();
    setState(() {
      _activeSender = null;
      _statusMessage = null;
      _stats = null;
      _hasError = false;
      _receiveMuted = false;
      _receivePhase = FocalReceivePhase.idle;
      _outputPath = null;
    });
  }

  Future<void> _manualConnect() async {
    final result = await showManualConnectDialog(context);
    if (result == null) return;
    _discovery.addManual(
      name: result.name,
      host: result.host,
      port: result.port,
    );
    await _connectTo(
      DiscoveredSender(
        id: senderEndpointId(result.host, result.port),
        name: result.name,
        host: result.host,
        port: result.port,
        isManual: true,
      ),
    );
  }

  Widget _body() {
    if (_inSession && _activeSender != null) {
      return ReceiveSessionView(
        sender: _activeSender!.copyWith(name: _senderLabel(_activeSender!)),
        phase: _receivePhase,
        errorMessage: (_hasError ||
                _receivePhase == FocalReceivePhase.reconnecting ||
                _receivePhase == FocalReceivePhase.error)
            ? _statusMessage
            : null,
        stats: _stats,
        outputPath: _outputPath,
        videoController: _receiver.videoController,
        recordingToFile: _receiver.isRecordingToFile,
        isMuted: _receiveMuted,
        audioAvailable: _receiver.audioAvailable,
        onToggleMute: () {
          final muted = _receiver.toggleMute();
          setState(() => _receiveMuted = muted);
        },
        onStop: _stop,
      );
    }
    return PageView(
      controller: _pageController,
      physics: const NeverScrollableScrollPhysics(),
      children: [
        ReceiveTabView(
          senders: _senders,
          useTls: _useTls,
          deviceDisplayName: _deviceName,
          onManualConnect: _manualConnect,
          onRefresh: () => _discovery.restart(),
          onSelect: _connectTo,
          senderLabel: _senderLabel,
        ),
        SendTabView(
          hostState: _hostState,
          deviceDisplayName: _deviceName,
          onStartLaptopShare: _startLaptopShare,
          onStopLaptopShare: _stopLaptopShare,
        ),
        SettingsView(
          deviceDisplayName: _deviceName,
          onRenameDevice: _renameComputer,
          useTls: _useTls,
          tlsLocked: _inSession,
          onTlsChanged: (v) => setState(() => _useTls = v),
          saveRecordingToFile: _prefs?.saveRecordingToFile ?? false,
          onSaveRecordingChanged: (v) async {
            _prefs ??= await FocalDesktopPreferences.load();
            await _prefs!.setSaveRecordingToFile(v);
            if (mounted) setState(() {});
          },
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final sizing = FocalSizing.of(context);
    final extendRail = sizing.isDesktop;
    final railIndex = _inSession ? 0 : _railIndex;

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: Row(
        children: [
          if (!sizing.isMobile && !_inSession)
            NavigationRail(
              extended: extendRail,
              backgroundColor: cs.surfaceContainerLow,
              selectedIndex: railIndex,
              onDestinationSelected: _selectTab,
              leading: extendRail
                  ? Column(
                      children: [
                        const SizedBox(height: 20),
                        Text(
                          'Focal',
                          style: theme.textTheme.headlineMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 20),
                      ],
                    )
                  : null,
              labelType: extendRail
                  ? NavigationRailLabelType.none
                  : NavigationRailLabelType.all,
              destinations: const [
                NavigationRailDestination(
                  icon: Icon(Icons.wifi),
                  label: Text('Receive'),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.send),
                  label: Text('Send'),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.settings),
                  label: Text('Settings'),
                ),
              ],
            ),
          Expanded(
            child: SafeArea(
              left: sizing.isMobile,
              child: _body(),
            ),
          ),
        ],
      ),
    );
  }
}
