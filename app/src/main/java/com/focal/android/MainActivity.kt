package com.focal.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focal.android.data.model.AppDestination
import com.focal.android.data.model.MainTab
import com.focal.android.ui.components.FocalBottomNav
import com.focal.android.ui.components.FocalTopBar
import com.focal.android.ui.components.ProfilePickerBottomSheet
import com.focal.android.ui.components.SecurityBottomSheet
import com.focal.android.ui.components.SensorPickerBottomSheet
import com.focal.android.ui.screens.ActiveStreamScreen
import com.focal.android.ui.screens.ConnectScreen
import com.focal.android.ui.screens.HelpScreen
import com.focal.android.ui.screens.OnboardingScreen
import com.focal.android.ui.screens.PermissionsScreen
import com.focal.android.ui.screens.SettingsScreen
import com.focal.android.ui.screens.StreamReadyScreen
import com.focal.android.ui.theme.FocalTheme
import com.focal.android.ui.viewmodel.FocalViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocalTheme {
                FocalApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocalApp(
    viewModel: FocalViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.updateContext(context)
    }

    val securitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sensorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Handle Android system back presses cleanly
    BackHandler(enabled = uiState.destination != AppDestination.ONBOARDING) {
        when (uiState.destination) {
            AppDestination.ACTIVE_STREAM -> {
                viewModel.stopStreaming(context)
            }
            AppDestination.MAIN_STREAM -> {
                if (uiState.selectedTab != MainTab.STREAM) {
                    viewModel.selectTab(MainTab.STREAM)
                } else {
                    viewModel.navigateTo(AppDestination.PERMISSIONS)
                }
            }
            AppDestination.PERMISSIONS -> {
                viewModel.navigateTo(AppDestination.ONBOARDING)
            }
            AppDestination.ONBOARDING -> {
                // Exit app
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (uiState.destination == AppDestination.MAIN_STREAM) {
                val title = when (uiState.selectedTab) {
                    MainTab.STREAM -> "Focal"
                    MainTab.CONNECT -> "Pairing & Network"
                    MainTab.SETTINGS -> "Preferences"
                    MainTab.HELP -> "Help & Guides"
                }
                val subtitle = when (uiState.selectedTab) {
                    MainTab.STREAM -> "Stream Ready"
                    MainTab.CONNECT -> "Linux Host Bridge"
                    MainTab.SETTINGS -> "Zero Telemetry"
                    MainTab.HELP -> "v4l2loopback Docs"
                }
                FocalTopBar(
                    title = title,
                    subtitle = subtitle,
                    showBack = uiState.selectedTab != MainTab.STREAM,
                    onBackClick = { viewModel.selectTab(MainTab.STREAM) },
                    showControls = true,
                    onControlsClick = { viewModel.setShowQuickControls(true) },
                    onProfileClick = { viewModel.setShowSecurityModal(true) }
                )
            }
        },
        bottomBar = {
            if (uiState.destination == AppDestination.MAIN_STREAM) {
                FocalBottomNav(
                    selectedTab = uiState.selectedTab,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.destination) {
                AppDestination.ONBOARDING -> {
                    OnboardingScreen(
                        onGetStartedClick = { viewModel.navigateTo(AppDestination.PERMISSIONS) },
                        onPrivacyDetailsClick = { viewModel.setShowSecurityModal(true) }
                    )
                }

                AppDestination.PERMISSIONS -> {
                    PermissionsScreen(
                        onBackClick = { viewModel.navigateTo(AppDestination.ONBOARDING) },
                        onContinueClick = { viewModel.navigateTo(AppDestination.MAIN_STREAM) },
                        onPermissionsUpdated = { camera, mic ->
                            viewModel.setCameraPermissionGranted(camera)
                            viewModel.setMicPermissionGranted(mic)
                        }
                    )
                }

                AppDestination.MAIN_STREAM -> {
                    when (uiState.selectedTab) {
                        MainTab.STREAM -> {
                            StreamReadyScreen(
                                uiState = uiState,
                                onStartStreamClick = { viewModel.startStreaming(context) },
                                onFlipCamera = { viewModel.flipCamera() },
                                onAutoRotate = { viewModel.rotate90() },
                                onCycleExposure = { viewModel.cycleExposure() },
                                onCycleFlashMode = { viewModel.cycleFlashMode() },
                                onSelectFlashMode = { viewModel.setFlashMode(it) },
                                onToggleGrid = { viewModel.toggleGridOverlay() },
                                onStreamModeChanged = { viewModel.setStreamMode(it) },
                                onConnectionModeChanged = { viewModel.setConnectionMode(it) },
                                onOpenSensorPicker = { viewModel.setShowSensorPicker(true) },
                                onOpenProfilePicker = { viewModel.setShowProfilePicker(true) },
                                onOpenQuickControls = { viewModel.selectTab(MainTab.SETTINGS) },
                                onOpenPairingCode = { viewModel.selectTab(MainTab.CONNECT) },
                                onToggleFullscreen = { viewModel.toggleFullscreenViewfinder() }
                            )
                        }

                        MainTab.CONNECT -> {
                            ConnectScreen(
                                uiState = uiState,
                                onSelectHost = { viewModel.selectHost(it) },
                                onConnectionModeChanged = { viewModel.setConnectionMode(it) },
                                onRegenerateCode = { viewModel.regeneratePairingCode() }
                            )
                        }

                        MainTab.SETTINGS -> {
                            SettingsScreen(
                                uiState = uiState,
                                onResolutionChanged = { viewModel.setStreamResolution(it) },
                                onFpsChanged = { viewModel.setStreamFps(it) },
                                onProfileSelected = { viewModel.selectProfile(it) }
                            )
                        }

                        MainTab.HELP -> {
                            HelpScreen()
                        }
                    }
                }

                AppDestination.ACTIVE_STREAM -> {
                    ActiveStreamScreen(
                        uiState = uiState,
                        onBackClick = { viewModel.stopStreaming(context) },
                        onStopStreamClick = { viewModel.stopStreaming(context) },
                        onFlipCamera = { viewModel.flipCamera() },
                        onRotate90 = { viewModel.rotate90() },
                        onToggleMute = { viewModel.toggleMute() },
                        onToggleTorch = { viewModel.toggleTorch() },
                        onCycleFlashMode = { viewModel.cycleFlashMode() },
                        onSelectFlashMode = { viewModel.setFlashMode(it) },
                        onToggleGrid = { viewModel.toggleGridOverlay() },
                        onToggleOledScreenOff = { viewModel.setOledScreenOff(it) }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheets
    if (uiState.showSecurityModal) {
        SecurityBottomSheet(
            sheetState = securitySheetState,
            onDismissRequest = { viewModel.setShowSecurityModal(false) }
        )
    }

    if (uiState.showSensorPicker) {
        SensorPickerBottomSheet(
            sensors = uiState.availableSensors,
            selectedSensor = uiState.selectedSensor,
            onSensorSelected = { viewModel.selectSensor(it) },
            sheetState = sensorSheetState,
            onDismissRequest = { viewModel.setShowSensorPicker(false) }
        )
    }

    if (uiState.showProfilePicker) {
        ProfilePickerBottomSheet(
            profiles = uiState.availableProfiles,
            selectedProfile = uiState.selectedProfile,
            onProfileSelected = { viewModel.selectProfile(it) },
            sheetState = profileSheetState,
            onDismissRequest = { viewModel.setShowProfilePicker(false) }
        )
    }
}
