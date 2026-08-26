#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?target source directory required}"

# Final Play Store candidate: v0.4.7 / versionCode 24.
bash dolbomon/ci/restore-v046.sh "$SRC"

base64 -d dolbomon/patches/v0.4.7-detail-grid-spacing.patch.gz.b64 | gzip -d > /tmp/v047.patch
(cd "$SRC" && patch -p1 < /tmp/v047.patch)

grep -q 'versionCode 24' "$SRC/app/build.gradle"
grep -q 'versionName "0.4.7"' "$SRC/app/build.gradle"
grep -q 'firstActionsLp.topMargin = Ui.dp(this, 10);' "$SRC/app/src/main/java/com/easternwood/dolbomon/SeniorActivity.java"
grep -q 'secondLp.topMargin = Ui.dp(this, 6);' "$SRC/app/src/main/java/com/easternwood/dolbomon/SeniorActivity.java"
grep -q 'int gap = Ui.dp(this, 2);' "$SRC/app/src/main/java/com/easternwood/dolbomon/SeniorActivity.java"
