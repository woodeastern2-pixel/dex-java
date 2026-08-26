#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?restored source directory required}"
APP="com.easternwood.dolbomon"
OUT="flow-screens"
mkdir -p "$OUT"

dump_until() {
  local needle="$1"
  local out="$2"
  local i
  for i in $(seq 0 10); do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null
    adb pull /sdcard/window.xml "$out" >/dev/null
    if grep -Fq "$needle" "$out"; then
      return 0
    fi
    adb shell input swipe 540 1760 540 1040 360
    sleep 0.5
  done
  echo "UI text not found after scrolling: $needle" >&2
  return 1
}

tap_text() {
  local xml="$1"
  local needle="$2"
  python3 -c 'import re,subprocess,sys,xml.etree.ElementTree as ET; root=ET.parse(sys.argv[1]).getroot(); needle=sys.argv[2]; parent={c:p for p in root.iter() for c in p}; target=next((n for n in root.iter("node") if needle in (n.attrib.get("text","")+" "+n.attrib.get("content-desc",""))),None); assert target is not None, "node not found: "+needle; clickable=target; 
while clickable is not None and clickable.attrib.get("clickable")!="true": clickable=parent.get(clickable); 
assert clickable is not None, "clickable ancestor not found: "+needle; b=clickable.attrib.get("bounds",""); m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",b); assert m, "bounds not found: "+needle; x=(int(m.group(1))+int(m.group(3)))//2; y=(int(m.group(2))+int(m.group(4)))//2; print("tap",needle,"at",x,y,"class",clickable.attrib.get("class")); subprocess.check_call(["adb","shell","input","tap",str(x),str(y)])' "$xml" "$needle"
}

assert_resumed_activity() {
  local activity="$1"
  local current=""
  local i
  for i in $(seq 1 12); do
    current="$(adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -n 1 || true)"
    echo "foreground check [$i]: $current"
    if [[ "$current" == *"$activity"* ]]; then
      return 0
    fi
    sleep 0.5
  done
  echo "Expected resumed activity $activity, got: $current" >&2
  adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|ActivityRecord' | head -n 30 >&2 || true
  return 1
}

gradle -p "$SRC" :app:connectedDebugAndroidTest
adb install -r "$SRC/app/build/outputs/apk/debug/DolbomOn-debug.apk"
adb shell pm clear "$APP" >/dev/null
adb logcat -c

adb shell am start -W -n "$APP/.MainActivity" --ez visual_seed true >/dev/null
sleep 3
adb shell am force-stop "$APP"
adb shell am start -W -n "$APP/.RecordActivity" --el senior_id 4 --ei visual_step 2 --ez visual_photos true >/dev/null
sleep 2

# Wait until the actual delete control is exposed, not merely the photo-count
# label at the edge of the viewport.
dump_until '사진 삭제' "$OUT/step2.xml"
grep -Fq '사진 2장' "$OUT/step2.xml"
adb exec-out screencap -p > "$OUT/01-record-photo-2.png"

tap_text "$OUT/step2.xml" '사진 삭제'
sleep 1
# Rewind slightly so the updated count is reliably inside the viewport, then
# verify the observable transition from 2 photos to 1 photo.
adb shell input swipe 540 980 540 1720 360
sleep 0.5
dump_until '사진 1장' "$OUT/step2-after.xml"
adb exec-out screencap -p > "$OUT/02-record-photo-1.png"

adb shell am force-stop "$APP"
adb shell am start -W -n "$APP/.RecordActivity" --el senior_id 4 --ei visual_step 3 --ez visual_photos true >/dev/null
sleep 2

dump_until '첨부 사진' "$OUT/delivery-attachments.xml"
dump_until '사진 삭제' "$OUT/delivery-attachments.xml"
adb exec-out screencap -p > "$OUT/03-delivery-attachments.png"

dump_until '상세 보기' "$OUT/delivery-detail.xml"
tap_text "$OUT/delivery-detail.xml" '상세 보기'
assert_resumed_activity 'SeniorActivity'
dump_until '오늘 기록 하기' "$OUT/senior-detail.xml"
adb exec-out screencap -p > "$OUT/04-senior-detail.png"
adb shell input keyevent 4
assert_resumed_activity 'RecordActivity'

dump_until '지난 기록' "$OUT/delivery-history.xml"
tap_text "$OUT/delivery-history.xml" '지난 기록'
assert_resumed_activity 'HistoryActivity'
dump_until '일일 기록 및 전달' "$OUT/history-rendered.xml"
adb exec-out screencap -p > "$OUT/05-history.png"

if adb logcat -d | grep -E 'FATAL EXCEPTION|Process: com.easternwood.dolbomon'; then
  echo 'DolbomOn crash detected during user-flow regression' >&2
  exit 1
fi

echo 'v0.4.6 user flow regression passed: share intent, photo delete 2->1, delivery attachments, rendered Senior detail, rendered History.'
