#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?target source directory required}"

bash dolbomon/ci/restore-v041.sh "$SRC"

base64 -d dolbomon/patches/v0.4.2-surface-polish.patch.gz.b64 | gzip -d > /tmp/v042.patch
(cd "$SRC" && patch -p1 < /tmp/v042.patch)

# Keep fractional dp values used by the softer v0.4.2 elevation treatment compile-safe.
python3 - "$SRC/app/src/main/java/com/easternwood/dolbomon/Ui.java" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
needle = '''    public static int dp(Context c, int value) {\n        return Math.round(value * c.getResources().getDisplayMetrics().density);\n    }\n'''
overload = '''\n    public static int dp(Context c, float value) {\n        return Math.round(value * c.getResources().getDisplayMetrics().density);\n    }\n'''
if 'dp(Context c, float value)' not in s:
    if needle not in s:
        raise SystemExit('Ui.dp helper anchor not found')
    s = s.replace(needle, needle + overload, 1)
p.write_text(s)
PY

# Apply the already-approved launcher icon asset without regenerating artwork.
cp dolbomon/assets/v0.4.2-icon-source.b64 "$SRC/app/icon-source.b64"

grep -q 'versionCode 19' "$SRC/app/build.gradle"
grep -q 'versionName "0.4.2"' "$SRC/app/build.gradle"
