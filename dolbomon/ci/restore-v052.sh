#!/usr/bin/env bash
set -euo pipefail
DEST="${1:-$PWD/dolbomon-v052-src}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

bash "$ROOT/dolbomon/ci/restore-v051.sh" "$DEST"
python3 "$ROOT/dolbomon/ci/apply-v052-release.py" "$DEST"

grep -q 'versionCode 29' "$DEST/app/build.gradle"
grep -q 'versionName "0.5.2"' "$DEST/app/build.gradle"
grep -q 'ca-app-pub-9360550840761530~7315527975' "$DEST/app/build.gradle"
grep -q 'ca-app-pub-9360550840761530/9750119626' "$DEST/app/build.gradle"
grep -q 'ca-app-pub-3940256099942544~3347511713' "$DEST/app/build.gradle"
grep -q 'ca-app-pub-3940256099942544/9214589741' "$DEST/app/build.gradle"
grep -q 'if (!info.canRequestAds()) return;' "$DEST/app/src/main/java/com/easternwood/dolbomon/AdsManager.java"

echo "DolbomOn v0.5.2 restored at $DEST"
