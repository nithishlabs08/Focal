#!/usr/bin/env bash
# Build focal-desktop .deb and .rpm from Flutter Linux release.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
cd "$ROOT"

APP_ID="com.focal.desktop"
PKG_NAME="focal-desktop"
VERSION="$(grep '^version:' pubspec.yaml | awk '{print $2}' | cut -d+ -f1)"
DEB_ARCH="amd64"
RPM_ARCH="x86_64"
BUNDLE="$ROOT/build/linux/x64/release/bundle"
DIST="$ROOT/build/dist"
STAGE="$DIST/stage"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

if [[ "${SKIP_FLUTTER_BUILD:-0}" != "1" ]]; then
  echo "==> Flutter Linux release"
  need flutter
  flutter pub get
  flutter build linux --release
else
  echo "==> SKIP_FLUTTER_BUILD=1 (using existing bundle)"
fi

if [[ ! -x "$BUNDLE/focal_desktop" ]]; then
  echo "Missing release binary: $BUNDLE/focal_desktop" >&2
  exit 1
fi

rm -rf "$DIST"
mkdir -p "$STAGE/opt/focal-desktop"
cp -a "$BUNDLE/." "$STAGE/opt/focal-desktop/"

mkdir -p "$STAGE/usr/share/applications"
cp "$ROOT/linux/com.focal.desktop" "$STAGE/usr/share/applications/${APP_ID}.desktop"
sed -i "s|^Exec=.*|Exec=/opt/focal-desktop/focal_desktop %U|" \
  "$STAGE/usr/share/applications/${APP_ID}.desktop"

ICON_PNG="$DIST/icon.png"
if [[ -f "$REPO/branding/favicon.svg" ]] && command -v rsvg-convert >/dev/null 2>&1; then
  rsvg-convert -w 128 -h 128 "$REPO/branding/favicon.svg" -o "$ICON_PNG"
elif [[ -f "$REPO/branding/favicon.svg" ]] && command -v convert >/dev/null 2>&1; then
  convert -background none "$REPO/branding/favicon.svg" -resize 128x128 "$ICON_PNG"
fi
if [[ -f "$ICON_PNG" ]]; then
  mkdir -p "$STAGE/usr/share/icons/hicolor/128x128/apps"
  cp "$ICON_PNG" "$STAGE/usr/share/icons/hicolor/128x128/apps/${APP_ID}.png"
  mkdir -p "$STAGE/usr/share/pixmaps"
  cp "$ICON_PNG" "$STAGE/usr/share/pixmaps/${APP_ID}.png"
fi

postinst() {
  cat <<'EOF'
#!/bin/sh
set -e
if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database -q /usr/share/applications || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
  gtk-update-icon-cache -q -f -t /usr/share/icons/hicolor || true
fi
EOF
}

echo "==> Building .deb"
DEB_ROOT="$DIST/deb-root"
mkdir -p "$DEB_ROOT/DEBIAN"
cp -a "$STAGE/opt" "$DEB_ROOT/"
cp -a "$STAGE/usr" "$DEB_ROOT/"
cat > "$DEB_ROOT/DEBIAN/control" <<EOF
Package: ${PKG_NAME}
Version: ${VERSION}
Section: net
Priority: optional
Architecture: ${DEB_ARCH}
Maintainer: Focal
Depends: libgtk-3-0, libblkid1, liblzma5, libsecret-1-0
Recommends: ffmpeg, avahi-daemon
Description: Focal Desktop — LAN stream receiver
 Receive Focal streams on your computer over Wi-Fi (FOCL).
EOF
postinst > "$DEB_ROOT/DEBIAN/postinst"
chmod 755 "$DEB_ROOT/DEBIAN/postinst"
need dpkg-deb
DEB_FILE="$DIST/${PKG_NAME}_${VERSION}_${DEB_ARCH}.deb"
dpkg-deb --root-owner-group --build "$DEB_ROOT" "$DEB_FILE"
echo "Created $DEB_FILE"

echo "==> Building .rpm"
if command -v fpm >/dev/null 2>&1; then
  RPM_FILE="$DIST/${PKG_NAME}-${VERSION}-1.${RPM_ARCH}.rpm"
  fpm -s dir -t rpm -n "$PKG_NAME" -v "$VERSION" -a "$RPM_ARCH" \
    --depends gtk3 --depends libsecret \
    --rpm-dist "$(rpm --eval '%{?dist}' 2>/dev/null || echo el8)" \
    -C "$STAGE" -p "$RPM_FILE" \
    opt usr
  echo "Created $RPM_FILE"
elif command -v rpmbuild >/dev/null 2>&1; then
  RPM_TOP="$DIST/rpm"
  rm -rf "$RPM_TOP"
  mkdir -p "$RPM_TOP/BUILD" "$RPM_TOP/RPMS/${RPM_ARCH}" "$RPM_TOP/SOURCES" "$RPM_TOP/SPECS" "$RPM_TOP/SRPMS"
  cp -a "$STAGE" "$RPM_TOP/SOURCES/stage"
  SPEC="$RPM_TOP/SPECS/${PKG_NAME}.spec"
  cat > "$SPEC" <<EOF
Name:           ${PKG_NAME}
Version:        ${VERSION}
Release:        1%{?dist}
Summary:        Focal Desktop LAN stream receiver
License:        Proprietary
BuildArch:      ${RPM_ARCH}
AutoReqProv:    no
Requires:       gtk3, libsecret
Recommends:     ffmpeg

%description
Receive Focal camera, screen, and audio streams on your computer.

%install
rm -rf %{buildroot}
mkdir -p %{buildroot}
cp -a %{_sourcedir}/stage/opt %{buildroot}/
cp -a %{_sourcedir}/stage/usr %{buildroot}/

%files
/opt/focal-desktop
/usr/share/applications/${APP_ID}.desktop
/usr/share/icons
/usr/share/pixmaps

%post
if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database -q %{_datadir}/applications || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
  gtk-update-icon-cache -q -f -t %{_datadir}/icons/hicolor || true
fi
EOF
  rpmbuild -bb --define "_topdir $RPM_TOP" "$SPEC"
  find "$RPM_TOP/RPMS" -name "*.rpm" -exec cp -v {} "$DIST/" \;
else
  echo "Skip RPM: install fpm (gem install fpm) or rpmbuild (sudo apt install rpm)" >&2
fi

if [[ "${GITHUB_ACTIONS:-}" == "true" ]] && ! compgen -G "$DIST/*.rpm" >/dev/null; then
  echo "RPM package was not produced (install rpm or fpm in CI)." >&2
  exit 1
fi

echo "Packages in $DIST:"
ls -la "$DIST"/*.deb "$DIST"/*.rpm 2>/dev/null || ls -la "$DIST"
