#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?target source directory required}"

bash dolbomon/ci/restore-v045.sh "$SRC"

base64 -d dolbomon/patches/v0.4.6-color-fidelity.patch.gz.b64 | gzip -d > /tmp/v046.patch
(cd "$SRC" && patch -p1 < /tmp/v046.patch)

grep -q 'versionCode 23' "$SRC/app/build.gradle"
grep -q 'versionName "0.4.6"' "$SRC/app/build.gradle"
grep -q '#087F87' "$SRC/app/src/main/res/values/colors.xml"
grep -q 'elevationOverlayEnabled">false' "$SRC/app/src/main/res/values/themes.xml"
grep -q 'Color.argb(70, 48, 56, 60)' "$SRC/app/src/main/java/com/easternwood/dolbomon/Ui.java"
