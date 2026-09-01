#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?target source directory required}"

bash dolbomon/ci/restore-v044.sh "$SRC"

base64 -d dolbomon/patches/v0.4.5-depth-surface.patch.gz.b64 | gzip -d > /tmp/v045.patch
(cd "$SRC" && patch -p1 < /tmp/v045.patch)

grep -q 'versionCode 22' "$SRC/app/build.gradle"
grep -q 'versionName "0.4.5"' "$SRC/app/build.gradle"
grep -q '#F2F6F6' "$SRC/app/src/main/res/values/colors.xml"
grep -q 'Color.argb(92, 22, 58, 61)' "$SRC/app/src/main/java/com/easternwood/dolbomon/Ui.java"
