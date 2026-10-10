# Focal Desktop (Flutter)

**Receive-only** cross-platform viewer for Focal phone senders (Linux, Windows, macOS).  
Uses the same **FOCL** protocol as the Android **Receive** tab (live in-app video, not file-first).

## Setup (first time)

Install [Flutter](https://docs.flutter.dev/get-started/install) 3.24+ with desktop enabled:

```bash
flutter doctor
flutter config --enable-linux-desktop
flutter config --enable-windows-desktop
flutter config --enable-macos-desktop
```

Generate platform runners (once):

```bash
cd apps/focal_desktop
flutter create . --platforms=linux,windows,macos --project-name focal_desktop
flutter pub get
```

## Run

```bash
cd apps/focal_desktop
flutter run -d linux    # or windows / macos
```

## Build release

```bash
flutter build linux --release
# Binary: build/linux/x64/release/bundle/focal_desktop
```

## Usage

1. Start streaming on the **Focal mobile** app (camera or screen) on the same Wi‑Fi.
2. Open **Focal Desktop** → pick the sender or **Connect by IP**.
3. Enter the **4-digit PIN** from the phone.
4. Video plays **live in the app** (cast-style). Audio plays when the sender includes it (`aplay` on Linux, `ffplay` elsewhere if installed).

Optional **Settings → Also save stream to file** writes `~/focal_capture.h264` for VLC while you watch.

Optional **TLS** uses port **8443** (match the phone). Plain FOCL uses **8080**.

Focal is **not** Miracast/AirPlay — it is paired app-to-app streaming over your LAN.

## App icon (taskbar / window / installers)

Icons are generated from [`../../branding/favicon.svg`](../../branding/favicon.svg):

```bash
./scripts/generate_desktop_icons.sh
```

Updates Windows `.ico`, macOS `AppIcon.appiconset`, and Linux `runner/resources/` PNGs + Freedesktop hicolor tree.

## Packaging

- `.tar.gz` — release bundle from `build/linux/x64/release/bundle/`
- `.deb` / `.rpm` — see `packaging/build-deb-rpm.sh` and CI

## Layout

```text
lib/
  focl/           # FOCL session, bridge, PCM player
  discovery/      # mDNS `_focal._tcp`
  ui/             # Desktop receive UI
```

Android hosting stays in the Kotlin mobile app; desktop can also **Send** (share this PC’s screen) from the Send tab.
