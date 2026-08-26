#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?restored source directory required}"
APP="com.easternwood.dolbomon"
OUT="v047-final-screens"
mkdir -p "$OUT"

wait_text() {
  local needle="$1"
  local out="$2"
  local i
  for i in $(seq 1 12); do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null || true
    adb pull /sdcard/window.xml "$out" >/dev/null || true
    if grep -Fq "$needle" "$out" 2>/dev/null; then return 0; fi
    sleep 0.5
  done
  echo "Expected UI text not rendered: $needle" >&2
  return 1
}

wait_text_scroll() {
  local needle="$1"
  local out="$2"
  local i
  for i in $(seq 1 12); do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null || true
    adb pull /sdcard/window.xml "$out" >/dev/null || true
    if grep -Fq "$needle" "$out" 2>/dev/null; then return 0; fi
    adb shell input swipe 540 1740 540 980 360
    sleep 0.5
  done
  echo "Expected UI text not rendered after scrolling: $needle" >&2
  return 1
}

start_capture() {
  local activity="$1"
  local expected="$2"
  local file="$3"
  shift 3
  adb shell am force-stop "$APP"
  adb shell am start -W -n "$APP/.$activity" "$@" >/dev/null
  wait_text "$expected" "$OUT/${file%.png}.xml"
  sleep 0.5
  adb exec-out screencap -p > "$OUT/$file"
}

start_capture_scrolled() {
  local activity="$1"
  local expected="$2"
  local file="$3"
  shift 3
  adb shell am force-stop "$APP"
  adb shell am start -W -n "$APP/.$activity" "$@" >/dev/null
  wait_text_scroll "$expected" "$OUT/${file%.png}.xml"
  sleep 0.5
  adb exec-out screencap -p > "$OUT/$file"
}

start_capture_rendered() {
  local activity="$1"
  local file="$2"
  shift 2
  adb shell am force-stop "$APP"
  adb shell am start -W -n "$APP/.$activity" "$@" >/dev/null
  sleep 2
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml "$OUT/${file%.png}.xml" >/dev/null
  grep -Fq "$APP" "$OUT/${file%.png}.xml"
  adb exec-out screencap -p > "$OUT/$file"
}

# Revalidate photo+body share intents on the same API 35 device used for final screenshots.
gradle -p "$SRC" :app:connectedDebugAndroidTest

adb install -r "$SRC/app/build/outputs/apk/debug/DolbomOn-debug.apk"
adb shell pm clear "$APP" >/dev/null
adb logcat -c

# Seed Korean demo data through the normal home entry.
adb shell am start -W -n "$APP/.MainActivity" --ez visual_seed true >/dev/null
wait_text '돌봄온' "$OUT/01-home.xml"
sleep 0.5
adb exec-out screencap -p > "$OUT/01-home.png"

start_capture SeniorActivity '오늘 기록 하기' '02-detail.png' --el senior_id 4
start_capture RecordActivity '오늘 상태' '03-record-today.png' --el senior_id 4 --ei visual_step 1
start_capture_scrolled RecordActivity '사진 2장' '04-record-additional.png' --el senior_id 4 --ei visual_step 2 --ez visual_photos true
start_capture_scrolled RecordActivity '상세 보기' '05-delivery.png' --el senior_id 4 --ei visual_step 3 --ez visual_photos true
start_capture HistoryActivity '일일 기록 및 전달' '06-history.png' --el senior_id 4
start_capture_rendered MessageCenterActivity '07-message-center.png'
start_capture SettingsActivity '설정' '08-settings.png'

if adb logcat -d | grep -E 'FATAL EXCEPTION|Process: com.easternwood.dolbomon'; then
  echo 'DolbomOn crash detected during final screen capture' >&2
  exit 1
fi

echo 'v0.4.7 final validation passed: share regression + 8 rendered screens.'
