#!/usr/bin/env bash
set -euo pipefail
DEST="${1:-$PWD/dolbomon-v051-src}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

bash "$ROOT/dolbomon/ci/restore-v050.sh" "$DEST"

APPLY="$(mktemp)"
trap 'rm -f "$APPLY"' EXIT
cat \
  "$ROOT/dolbomon/ci/apply-v051-play-policy.py.part00" \
  "$ROOT/dolbomon/ci/apply-v051-play-policy.py.part01" \
  "$ROOT/dolbomon/ci/apply-v051-play-policy.py.part02" \
  "$ROOT/dolbomon/ci/apply-v051-play-policy.py.part03" \
  "$ROOT/dolbomon/ci/apply-v051-play-policy.py.part04" > "$APPLY"
python3 "$APPLY" "$DEST"

# Android string resources require literal ampersands to be XML-escaped.
# Keep already-valid entities intact while fixing translated copy such as
# "Conversation & interaction" and "Trò chuyện & tương tác".
python3 - "$DEST" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]) / "app/src/main/res"
entity = re.compile(r"&(?!#\d+;|#x[0-9A-Fa-f]+;|[A-Za-z][A-Za-z0-9]+;)")
for path in root.glob("values*/strings.xml"):
    text = path.read_text(encoding="utf-8")
    fixed = entity.sub("&amp;", text)
    if fixed != text:
        path.write_text(fixed, encoding="utf-8")
PY

grep -q 'versionCode 28' "$DEST/app/build.gradle"
grep -q 'versionName "0.5.1"' "$DEST/app/build.gradle"
grep -q '>오늘 기본 기록<' "$DEST/app/src/main/res/values/strings.xml"
grep -q '>더 많은 전달사항 보기<' "$DEST/app/src/main/res/values/strings.xml"
grep -q 'String\[\] codes = {"none", "schedule", "contact", "supplies", "belongings", "visit", "request", "memo", "other"};' "$DEST/app/src/main/java/com/easternwood/dolbomon/RecordActivity.java"
! grep -q 'activityLevelGroup' "$DEST/app/src/main/java/com/easternwood/dolbomon/RecordActivity.java"

if grep -Eq '>([^<]*(식사|수분 섭취|컨디션|수면|배변|통증|상처|발열|병원|복약|영양식|기저귀)[^<]*)<' "$DEST/app/src/main/res/values/strings.xml"; then
  echo 'Legacy health-facing Korean copy remains in visible strings' >&2
  exit 1
fi

if grep -Eq '"(walk|exercise|pain|wound|fever|hospital|medication|diaper|nutrition)"' \
  "$DEST/app/src/main/java/com/easternwood/dolbomon/RecordActivity.java" \
  "$DEST/app/src/main/java/com/easternwood/dolbomon/SentenceGenerator.java" \
  "$DEST/app/src/main/java/com/easternwood/dolbomon/SupplyFormatter.java" \
  "$DEST/app/src/main/java/com/easternwood/dolbomon/HistoryActivity.java"; then
  echo 'Legacy health-facing runtime codes remain in v0.5.1 UI/generator paths' >&2
  exit 1
fi

echo "DolbomOn v0.5.1 restored at $DEST"
