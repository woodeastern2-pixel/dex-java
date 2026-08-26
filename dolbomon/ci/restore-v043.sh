#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?target source directory required}"

bash dolbomon/ci/restore-v042.sh "$SRC"

base64 -d dolbomon/patches/v0.4.3-share-thumb-depth.patch.gz.b64 | gzip -d > /tmp/v043.patch
(cd "$SRC" && patch -p1 < /tmp/v043.patch)

grep -q 'versionCode 20' "$SRC/app/build.gradle"
grep -q 'versionName "0.4.3"' "$SRC/app/build.gradle"
grep -q 'share_text_clipboard_fallback' "$SRC/app/src/main/res/values/strings.xml"
grep -q 'renderPhotoThumbnails' "$SRC/app/src/main/java/com/easternwood/dolbomon/RecordActivity.java"
grep -q 'attachStreams' "$SRC/app/src/main/java/com/easternwood/dolbomon/ShareHelper.java"
