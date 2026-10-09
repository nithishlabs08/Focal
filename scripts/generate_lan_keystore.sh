#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/focal_lan.jks"
mkdir -p "$(dirname "$OUT")"
if ! command -v keytool >/dev/null; then
  echo "keytool not found; install JDK or run Android CI (generates keystore before assemble)." >&2
  exit 1
fi
keytool -genkeypair -v \
  -keystore "$OUT" \
  -alias focal \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -storepass focallan \
  -keypass focallan \
  -dname "CN=Focal LAN,O=Focal,C=US"
echo "Wrote $OUT"
