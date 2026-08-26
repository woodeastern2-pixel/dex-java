#!/usr/bin/env bash
set -euo pipefail
DEST="${1:-$PWD/dolbomon-v050-src}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

bash "$ROOT/dolbomon/ci/restore-v049.sh" "$DEST"
PATCH_FILE="$(mktemp)"
trap 'rm -f "$PATCH_FILE"' EXIT

base64 -d "$ROOT/dolbomon/patches/v0.5.0-final-polish.patch.gz.b64" | gzip -dc > "$PATCH_FILE"
git -C "$DEST" apply --check "$PATCH_FILE"
git -C "$DEST" apply "$PATCH_FILE"

grep -q 'versionCode 27' "$DEST/app/build.gradle"
grep -q 'versionName "0.5.0"' "$DEST/app/build.gradle"
grep -q 'recordLp.topMargin = Ui.dp(this, newDate ? 3 : 1)' "$DEST/app/src/main/java/com/easternwood/dolbomon/HistoryActivity.java"
grep -q 'cardLp.topMargin = Ui.dp(this, 1)' "$DEST/app/src/main/java/com/easternwood/dolbomon/MainActivity.java"
grep -q 'int gap = Ui.dp(this, 0.5f)' "$DEST/app/src/main/java/com/easternwood/dolbomon/SeniorActivity.java"
grep -q 'moreOptionsButton(getString(R.string.more_activities))' "$DEST/app/src/main/java/com/easternwood/dolbomon/RecordActivity.java"
grep -q '더 많은 활동 보기' "$DEST/app/src/main/res/values/strings.xml"
echo "DolbomOn v0.5.0 restored at $DEST"
