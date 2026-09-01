#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?target source directory required}"
rm -rf "$SRC"
mkdir -p "$SRC"
cat dolbomon/source.tar.gz.b64.part-* | base64 -d | tar -xz -C "$SRC"
echo 'android.sourceset.disallowProvider=false' >> "$SRC/gradle.properties"
for spec in \
  'v0.2.0-commercial:p2' \
  'v0.2.1-settings:p3' \
  'v0.2.2-korean-records:p2' \
  'v0.2.3-ui-fixes:p2' \
  'v0.3.0-redesign:p1' \
  'v0.3.1b-final-ui:p1' \
  'v0.3.2-record-ux:p1'; do
  name=${spec%%:*}; strip=${spec##*:}
  cat dolbomon/patches/${name}.patch.gz.b64.part-* | base64 -d | gzip -d > "/tmp/${name}.patch"
  (cd "$SRC" && patch -${strip} < "/tmp/${name}.patch")
done
cat dolbomon/patches/v0.3.3-stability-ui.patch.gz.b64.part-* | base64 -d | gzip -d > /tmp/v033.patch
(cd "$SRC" && patch -p1 < /tmp/v033.patch)
base64 -d dolbomon/patches/v0.3.3-scrollbar-crashfix.patch.gz.b64 | gzip -d > /tmp/v033-scroll.patch
(cd "$SRC" && patch -p1 < /tmp/v033-scroll.patch)
cat dolbomon/patches/v0.3.4-exact-redesign.patch.gz.b64.part-* | base64 -d | gzip -d > /tmp/v034.patch
(cd "$SRC" && patch -p3 < /tmp/v034.patch)
base64 -d dolbomon/patches/v0.3.4-system-insets.patch.gz.b64 | gzip -d > /tmp/v034-insets.patch
(cd "$SRC" && patch -p1 < /tmp/v034-insets.patch)
: > /tmp/v035.patch.gz
for p in dolbomon/patches/v0.3.5b-visual-match.patch.gz.b64.part-*; do base64 -d "$p" >> /tmp/v035.patch.gz; done
gzip -d < /tmp/v035.patch.gz > /tmp/v035.patch
(cd "$SRC" && patch -p1 < /tmp/v035.patch)
for v in v0.3.6-visual-polish v0.3.7-record-flow v0.3.8-nav-spacing v0.3.9-message-center-polish v0.4.0-design-alignment v0.4.1-final-design; do
  base64 -d "dolbomon/patches/${v}.patch.gz.b64" | gzip -d > "/tmp/${v}.patch"
  (cd "$SRC" && patch -p1 < "/tmp/${v}.patch")
done

# Keep generated Android string resources valid even when translated copy contains a bare '&'.
python3 - "$SRC" <<'PY'
from pathlib import Path
import re
import sys

res = Path(sys.argv[1]) / "app/src/main/res"
bare_amp = re.compile(r"&(?!#\d+;|#x[0-9A-Fa-f]+;|[A-Za-z][A-Za-z0-9]+;)")
for path in res.glob("values*/strings.xml"):
    text = path.read_text(encoding="utf-8")
    fixed = bare_amp.sub("&amp;", text)
    if fixed != text:
        path.write_text(fixed, encoding="utf-8")
PY

grep -q 'versionCode 18' "$SRC/app/build.gradle"
grep -q 'versionName "0.4.1"' "$SRC/app/build.gradle"
