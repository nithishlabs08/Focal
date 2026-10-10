#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/apps/focal_desktop"

if ! command -v flutter >/dev/null 2>&1; then
  echo "Flutter SDK not found. Install from https://docs.flutter.dev/get-started/install"
  exit 1
fi

cd "$APP"
flutter create . --platforms=linux,windows,macos --project-name focal_desktop
flutter pub get
echo "Done. Run: cd apps/focal_desktop && flutter run -d linux"
