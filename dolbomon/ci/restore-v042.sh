#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?target source directory required}"

bash dolbomon/ci/restore-v041.sh "$SRC"

base64 -d dolbomon/patches/v0.4.2-surface-polish.patch.gz.b64 | gzip -d > /tmp/v042.patch
(cd "$SRC" && patch -p1 < /tmp/v042.patch)

# Apply the already-approved launcher icon asset without regenerating artwork.
cp dolbomon/assets/v0.4.2-icon-source.b64 "$SRC/app/icon-source.b64"

grep -q 'versionCode 19' "$SRC/app/build.gradle"
grep -q 'versionName "0.4.2"' "$SRC/app/build.gradle"
