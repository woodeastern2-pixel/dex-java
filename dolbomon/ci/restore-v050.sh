#!/usr/bin/env bash
set -euo pipefail
DEST="${1:-$PWD/dolbomon-v050-src}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

bash "$ROOT/dolbomon/ci/restore-v049.sh" "$DEST"

# v0.5.0 is applied as deterministic UTF-8 text edits instead of a compressed
# patch so release reconstruction cannot be broken by a partially-written b64 file.
python3 "$ROOT/dolbomon/ci/apply-v050-final-polish.py" "$DEST"

grep -q 'versionCode 27' "$DEST/app/build.gradle"
grep -q 'versionName "0.5.0"' "$DEST/app/build.gradle"
grep -q 'recordLp.topMargin = Ui.dp(this, newDate ? 3 : 1)' "$DEST/app/src/main/java/com/easternwood/dolbomon/HistoryActivity.java"
grep -q 'cardLp.topMargin = Ui.dp(this, 1)' "$DEST/app/src/main/java/com/easternwood/dolbomon/MainActivity.java"
grep -q 'int gap = Ui.dp(this, 0.5f)' "$DEST/app/src/main/java/com/easternwood/dolbomon/SeniorActivity.java"
grep -q 'moreOptionsButton(getString(R.string.more_activities))' "$DEST/app/src/main/java/com/easternwood/dolbomon/RecordActivity.java"
grep -q '더 많은 활동 보기' "$DEST/app/src/main/res/values/strings.xml"
echo "DolbomOn v0.5.0 restored at $DEST"
