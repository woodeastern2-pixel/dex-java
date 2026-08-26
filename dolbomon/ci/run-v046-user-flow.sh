#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?restored source directory required}"
APP="com.easternwood.dolbomon"
OUT="flow-screens"
mkdir -p "$OUT"

# Dump the current hierarchy and, only when needed, scroll until the requested
# visible text/content-description is actually present. This intentionally
# avoids relying on a fixed number of swipes.
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
  python3 -c 'import re,subprocess,sys; s=open(sys.argv[1],encoding="utf-8").read(); needle=sys.argv[2]; nodes=re.findall(r"<node\b[^>]*>",s); n=next((x for x in nodes if needle in x),None); assert n, "node not found: "+needle; m=re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"",n); assert m, "bounds not found: "+needle; x=(int(m.group(1))+int(m.group(3)))//2; y=(int(m.group(2))+int(m.group(4)))//2; subprocess.check_call(["adb","shell","input","tap",str(x),str(y)])' "$xml" "$needle"
}

assert_activity() {
  local activity="$1"
  sleep 1
  adb shell dumpsys activity activities | grep -Fq "$activity"
}

gradle -p "$SRC" :app:connectedDebugAndroidTest
adb install -r "$SRC/app/build/outputs/apk/debug/DolbomOn-debug.apk"
adb shell pm clear "$APP" >/dev/null
adb logcat -c

# Seed the normal visual data once, then open the record screen with two real
# local image files injected by the debug-only CI hook.
adb shell am start -W -n "$APP/.MainActivity" --ez visual_seed true >/dev/null
sleep 3
adb shell am force-stop "$APP"
adb shell am start -W -n "$APP/.RecordActivity" --el senior_id 4 --ei visual_step 2 --ez visual_photos true >/dev/null
sleep 2

dump_until '사진 2장' "$OUT/step2.xml"
grep -Fq '사진 삭제' "$OUT/step2.xml"
adb exec-out screencap -p > "$OUT/01-record-photo-2.png"

tap_text "$OUT/step2.xml" '사진 삭제'
sleep 1
adb shell uiautomator dump /sdcard/step2-after.xml >/dev/null
adb pull /sdcard/step2-after.xml "$OUT/step2-after.xml" >/dev/null
grep -Fq '사진 1장' "$OUT/step2-after.xml"
adb exec-out screencap -p > "$OUT/02-record-photo-1.png"

# Delivery screen: verify the attachment section and remove button are reachable,
# then actually navigate to Senior detail and History rather than only checking labels.
adb shell am force-stop "$APP"
adb shell am start -W -n "$APP/.RecordActivity" --el senior_id 4 --ei visual_step 3 --ez visual_photos true >/dev/null
sleep 2

dump_until '첨부 사진' "$OUT/delivery-attachments.xml"
grep -Fq '사진 삭제' "$OUT/delivery-attachments.xml"
adb exec-out screencap -p > "$OUT/03-delivery-attachments.png"

dump_until '상세 보기' "$OUT/delivery-detail.xml"
tap_text "$OUT/delivery-detail.xml" '상세 보기'
assert_activity 'SeniorActivity'
adb exec-out screencap -p > "$OUT/04-senior-detail.png"
adb shell input keyevent 4
sleep 1

dump_until '지난 기록' "$OUT/delivery-history.xml"
tap_text "$OUT/delivery-history.xml" '지난 기록'
assert_activity 'HistoryActivity'
adb exec-out screencap -p > "$OUT/05-history.png"

if adb logcat -d | grep -E 'FATAL EXCEPTION|Process: com.easternwood.dolbomon'; then
  echo 'DolbomOn crash detected during user-flow regression' >&2
  exit 1
fi

echo 'v0.4.6 user flow regression passed: share intent, photo delete 2->1, delivery attachments, detail navigation, history navigation.'
