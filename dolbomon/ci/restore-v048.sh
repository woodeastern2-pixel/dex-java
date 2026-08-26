#!/usr/bin/env bash
set -euo pipefail
DEST="${1:-$PWD/dolbomon-v048-src}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

bash "$ROOT/dolbomon/ci/restore-v047.sh" "$DEST"
PATCH_FILE="$(mktemp)"
trap 'rm -f "$PATCH_FILE"' EXIT
base64 -d "$ROOT/dolbomon/patches/v0.4.8-ux-polish.patch.gz.b64" | gzip -dc > "$PATCH_FILE"
git -C "$DEST" apply --check "$PATCH_FILE"
git -C "$DEST" apply "$PATCH_FILE"

grep -q 'versionCode 25' "$DEST/app/build.gradle"
grep -q 'versionName "0.4.8"' "$DEST/app/build.gradle"
grep -q 'view_senior_detail_move' "$DEST/app/src/main/res/values/strings.xml"
grep -q 'HorizontalScrollView choiceScroll' "$DEST/app/src/main/java/com/easternwood/dolbomon/RecordActivity.java"
grep -q 'Ui.dp(this, 118)' "$DEST/app/src/main/java/com/easternwood/dolbomon/MainActivity.java"
echo "DolbomOn v0.4.8 restored at $DEST"
