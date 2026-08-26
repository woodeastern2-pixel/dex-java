#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?target source directory required}"

bash dolbomon/ci/restore-v043.sh "$SRC"

base64 -d dolbomon/patches/v0.4.4-final-ui-fix.patch.gz.b64 | gzip -d > /tmp/v044.patch
(cd "$SRC" && patch -p1 < /tmp/v044.patch)

grep -q 'versionCode 21' "$SRC/app/build.gradle"
grep -q 'versionName "0.4.4"' "$SRC/app/build.gradle"
grep -q 'Ui.dp(this, 116)' "$SRC/app/src/main/java/com/easternwood/dolbomon/SeniorActivity.java"
grep -q 'applySystemBarAppearance' "$SRC/app/src/main/java/com/easternwood/dolbomon/BaseActivity.java"
