#!/usr/bin/env bash
set -euo pipefail
DEST="${1:-$PWD/dolbomon-v049-src}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

bash "$ROOT/dolbomon/ci/restore-v048.sh" "$DEST"
PATCH_FILE="$(mktemp)"
trap 'rm -f "$PATCH_FILE"' EXIT

base64 -d "$ROOT/dolbomon/patches/v0.4.9-layout-polish.patch.gz.b64" | gzip -dc > "$PATCH_FILE"
git -C "$DEST" apply --check "$PATCH_FILE"
git -C "$DEST" apply "$PATCH_FILE"

grep -q 'versionCode 26' "$DEST/app/build.gradle"
grep -q 'versionName "0.4.9"' "$DEST/app/build.gradle"
grep -q 'Ui.dp(this, 142)' "$DEST/app/src/main/java/com/easternwood/dolbomon/MainActivity.java"
grep -q 'recordLp.topMargin = Ui.dp(this, newDate ? 5 : 2)' "$DEST/app/src/main/java/com/easternwood/dolbomon/HistoryActivity.java"
grep -q 'Ui.dp(this, 120)' "$DEST/app/src/main/java/com/easternwood/dolbomon/SeniorActivity.java"
grep -q 'MaterialButton detailMove = Ui.secondaryButton' "$DEST/app/src/main/java/com/easternwood/dolbomon/RecordActivity.java"
grep -q 'activityBox.setPadding' "$DEST/app/src/main/java/com/easternwood/dolbomon/RecordActivity.java"
echo "DolbomOn v0.4.9 restored at $DEST"
