# AGENTS.md — Focal

Instructions for AI coding agents (Cursor, CI bots, etc.). **Read this file first** before changing code. **Update this file** when architecture, workflows, or product scope change.

## Product scope (current planning)

| Component | Role | Status |
|-----------|------|--------|
| **Android `mobile` flavor** | Send (camera, screen, audio) + Receive | Primary |
| **`apps/focal_desktop`** | Flutter receive-only (Linux / Windows / macOS) | Active |
| **`desktop/focl_receiver.py`** | Minimal Python FOCL CLI receiver | Legacy / reference |
| **Android `tv` flavor** | TV receive + screen/audio host | **Frozen** — do not expand unless user asks |

- **Camera hosting** is **mobile-only** (`FocalRoles.canHostCameraStream`).
- **Screen/audio hosting** uses `WebcamStreamService` on flavors that allow it (`FocalRoles.canHostScreenOrAudioStream`).
- Receivers: mobile Receive tab, Flutter desktop, Python CLI; shared **FOCL** protocol.

Protocol and roles: [`docs/PROTOCOL.md`](docs/PROTOCOL.md).

## Repository layout

```text
Focal/
  AGENTS.md                 # This file — keep current
  app/                      # Android (Gradle), flavors: mobile, tv
  apps/focal_desktop/       # Flutter desktop receiver
  desktop/                  # Python CLI receiver
  docs/                     # PROTOCOL.md, etc.
  scripts/                  # e.g. generate_lan_keystore.sh, bootstrap_focal_desktop.sh
  branding/                 # Icons / SVG source
  .github/workflows/        # Android CI, flutter-desktop.yml
```

Marketing site is **separate** (`focal-web` / `focal.github.io`) — not this repo unless user says otherwise.

## How agents should work

1. **Execute, don’t only suggest** — run builds, tests, and scripts in the real environment when possible.
2. **Minimal diffs** — fix the requested problem; don’t refactor unrelated code or add speculative features.
3. **Match existing style** — naming, packages, Compose/Material patterns in Android; Dart structure in `apps/focal_desktop`.
4. **No secrets in git** — never commit keystores, `.env`, or PINs; warn if user asks to commit credentials.
5. **Git** — create commits or push **only when the user explicitly asks**. Never run `git config`. No force-push to `main`. Use `gh` for GitHub PRs/issues when requested.
6. **CI** — after push, user may want `gh run watch`; Android workflow uses Gradle 9.x, JDK 21, `testMobileDebugUnitTest` (and TV tests if still in workflow).

## Android (`app/`)

- **Namespace:** `com.focal.android`
- **Entry:** `MainActivity` (mobile), `tv.ui.TvActivity` (tv flavor)
- **Streaming:** `WebcamStreamService`, `StreamSessionController`, `transport/*`, `media/*`
- **Receive UI (mobile):** `ui/receive/MobileReceiveScreen.kt` + shared `tv/client/*` FOCL client
- **Device settings:** `settings/FocalDevicePreferences.kt` (name, theme)
- **Discovery (host):** `transport/FocalDiscoveryManager.kt` — register mDNS **while streaming**, not idle on app open
- **Discovery (receive):** `tv/discovery/TvDiscoveryManager.kt` — filter local sender (service name + local IPs)

Build (from repo root, when `gradlew` or system `gradle` is available):

```bash
gradle testMobileDebugUnitTest
gradle assembleMobileRelease
```

## Flutter desktop (`apps/focal_desktop/`)

- **Receive-only** — FOCL in `lib/focl/`, mDNS in `lib/discovery/`, UI in `lib/ui/`
- Local Flutter SDK (this machine): `~/flutter` on `PATH` via `~/.bashrc`
- First-time / platform runners: `./scripts/bootstrap_focal_desktop.sh`

```bash
cd apps/focal_desktop
flutter pub get
flutter analyze && flutter test
flutter run -d linux
flutter build linux --release
```

CI: Single unified workflow `.github/workflows/build.yml` builds Android APKs plus Linux `.deb`/`.rpm` (job `desktop-linux`) and publishes one GitHub Release on `main`.

## Shared protocol rules

- mDNS: `_focal._tcp.`
- Auth: `AUTH <pin>\n` → `AUTH_OK ENC1`
- Key: `SHA-256("FOCL:" + pin)`; AES-GCM payloads (see `FocalSessionCrypto.kt` / `lib/focl/focl_crypto.dart`)
- Keep Android, Flutter, and Python CLI behavior aligned; update `docs/PROTOCOL.md` when wire format changes.

## UI / brand

- LocalSend-inspired greens: primary `#006A60` (light), `#82D5C8` accents (see `ui/theme/Color.kt`, launcher `values-night/colors.xml`).

## Testing

- Android unit tests: `app/src/test/` (use ephemeral ports where binding is tested)
- Flutter: `apps/focal_desktop/test/`
- After substantive changes, run relevant tests before claiming done.

## When finishing a task

- Summarize what changed and how to verify (device steps or commands).
- Note if something was **not** run (e.g. no Gradle wrapper on machine).
- If scope shifted (e.g. user deprioritized TV), reflect it here in **Product scope**.

## Changelog (agents)

| Date | Note |
|------|------|
| 2026-10-09 | Initial AGENTS.md: mobile + Flutter desktop focus; TV frozen; FOCL + CI pointers |
| 2026-10-10 | Mobile UI: LocalSend-style bottom nav (Receive / Send / Settings); 4-digit pairing PIN |
