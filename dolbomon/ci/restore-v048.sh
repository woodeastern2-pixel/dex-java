#!/usr/bin/env bash
set -euo pipefail
DEST="${1:-$PWD/dolbomon-v048-src}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

bash "$ROOT/dolbomon/ci/restore-v047.sh" "$DEST"
PATCH_FILE="$(mktemp)"
PATCH_B64="$(mktemp)"
trap 'rm -f "$PATCH_FILE" "$PATCH_B64"' EXIT

# Use the immutable, already-validated v0.4.8 UX patch from the first successful v0.4.8 build.
# The local copy is intentionally not consumed here so a partially-written patch can never poison release reconstruction.
curl -fsSL "https://raw.githubusercontent.com/woodeastern2-pixel/dex-java/9289d8b279c9fbb37cf6191d6f0df421bee28868/dolbomon/patches/v0.4.8-ux-polish.patch.gz.b64" -o "$PATCH_B64"
base64 -d "$PATCH_B64" | gzip -dc > "$PATCH_FILE"
git -C "$DEST" apply --check "$PATCH_FILE"
git -C "$DEST" apply "$PATCH_FILE"

# Final visual correction found during rendered-screen inspection: keep icon, count and metric label visible together.
sed -i 's/Ui\.dp(this, 118)/Ui.dp(this, 138)/' "$DEST/app/src/main/java/com/easternwood/dolbomon/MainActivity.java"

grep -q 'versionCode 25' "$DEST/app/build.gradle"
grep -q 'versionName "0.4.8"' "$DEST/app/build.gradle"
grep -q 'view_senior_detail_move' "$DEST/app/src/main/res/values/strings.xml"
grep -q 'HorizontalScrollView choiceScroll' "$DEST/app/src/main/java/com/easternwood/dolbomon/RecordActivity.java"
grep -q 'Ui.dp(this, 138)' "$DEST/app/src/main/java/com/easternwood/dolbomon/MainActivity.java"
echo "DolbomOn v0.4.8 restored at $DEST"
