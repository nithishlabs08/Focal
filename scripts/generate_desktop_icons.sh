#!/usr/bin/env bash
# Regenerate Flutter desktop launcher icons from branding/favicon.svg
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/apps/focal_desktop"
SVG="$ROOT/branding/favicon.svg"
DESKTOP_SVG="$APP/assets/focal_icon.svg"

if [[ ! -f "$SVG" ]]; then
  echo "Missing $SVG" >&2
  exit 1
fi
# Keep in-app / packaging SVG in sync with branding source.
cp "$SVG" "$DESKTOP_SVG"

if ! command -v rsvg-convert >/dev/null 2>&1; then
  echo "Install librsvg2-bin (rsvg-convert)." >&2
  exit 1
fi

MAC_SET="$APP/macos/Runner/Assets.xcassets/AppIcon.appiconset"
WIN_ICO="$APP/windows/runner/resources/app_icon.ico"
LINUX_RES="$APP/linux/runner/resources"
mkdir -p "$MAC_SET" "$APP/windows/runner/resources" "$LINUX_RES"

echo "→ macOS AppIcon.appiconset"
for size in 16 32 64 128 256 512 1024; do
  rsvg-convert -w "$size" -h "$size" "$SVG" -o "$MAC_SET/app_icon_${size}.png"
done

echo "→ Windows app_icon.ico"
TMP="$(mktemp -d)"
for size in 16 32 48 64 128 256; do
  rsvg-convert -w "$size" -h "$size" "$SVG" -o "$TMP/focal_${size}.png"
done
if command -v magick >/dev/null 2>&1; then
  magick "$TMP/focal_256.png" "$TMP/focal_128.png" "$TMP/focal_64.png" \
    "$TMP/focal_48.png" "$TMP/focal_32.png" "$TMP/focal_16.png" "$WIN_ICO"
elif command -v convert >/dev/null 2>&1; then
  convert "$TMP/focal_256.png" "$TMP/focal_128.png" "$TMP/focal_64.png" \
    "$TMP/focal_48.png" "$TMP/focal_32.png" "$TMP/focal_16.png" "$WIN_ICO"
else
  echo "ImageMagick required for .ico (apt install imagemagick)." >&2
  exit 1
fi
rm -rf "$TMP"

echo "→ Linux bundle icon (256px PNG)"
rsvg-convert -w 256 -h 256 "$SVG" -o "$LINUX_RES/app_icon.png"
rsvg-convert -w 512 -h 512 "$SVG" -o "$LINUX_RES/app_icon_512.png"

echo "→ Freedesktop hicolor (for .desktop menu install)"
HICOLOR="$LINUX_RES/hicolor"
rm -rf "$HICOLOR"
for size in 16 32 48 64 128 256 512; do
  dir="$HICOLOR/${size}x${size}/apps"
  mkdir -p "$dir"
  rsvg-convert -w "$size" -h "$size" "$SVG" -o "$dir/com.focal.desktop.png"
done

echo "Done. Icons updated under apps/focal_desktop/"
