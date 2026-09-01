#!/usr/bin/env bash
set -euo pipefail
SRC="${1:?restored source directory required}"
APP="com.easternwood.dolbomon"
OUT="v051-final-screens"
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
  test -s "$OUT/$file"
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
  test -s "$OUT/$file"
}

start_capture_scrolled_extra() {
  local activity="$1"
  local expected="$2"
  local file="$3"
  shift 3
  adb shell am force-stop "$APP"
  adb shell am start -W -n "$APP/.$activity" "$@" >/dev/null
  wait_text_scroll "$expected" "$OUT/${file%.png}.xml"
  adb shell input swipe 540 1740 540 980 360
  sleep 0.5
  adb exec-out screencap -p > "$OUT/$file"
  test -s "$OUT/$file"
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
  test -s "$OUT/$file"
}

gradle -p "$SRC" :app:connectedDebugAndroidTest

adb install -r "$SRC/app/build/outputs/apk/debug/DolbomOn-debug.apk"
adb shell pm clear "$APP" >/dev/null
adb logcat -c

adb shell am start -W -n "$APP/.MainActivity" --ez visual_seed true >/dev/null
wait_text '돌봄온' "$OUT/01-home.xml"
grep -Eq 'text="[0-9]+(명|건)"' "$OUT/01-home.xml"
sleep 0.5
adb exec-out screencap -p > "$OUT/01-home.png"
test -s "$OUT/01-home.png"

start_capture SeniorActivity '오늘 기록 하기' '02-detail.png' --el senior_id 4
start_capture RecordActivity '오늘 기본 기록' '03-record-today.png' --el senior_id 4 --ei visual_step 1
for text in '오늘 일정' '대화·교류' '생활 지원' '하루 분위기' '주요 활동' '공유 포인트'; do
  grep -Fq "$text" "$OUT/03-record-today.xml"
done
if grep -Eq 'text="[^"]*(식사|수분 섭취|컨디션|수면|배변|통증|상처|발열|병원|복약)[^"]*"' "$OUT/03-record-today.xml"; then
  echo 'Health-facing copy rendered on v0.5.1 basic record screen' >&2
  exit 1
fi
adb shell input swipe 960 720 430 720 350 || true
sleep 0.4

start_capture RecordActivity '더 많은 활동 보기' '04-record-additional.png' --el senior_id 4 --ei visual_step 2 --ez visual_photos true
adb shell input swipe 540 1740 540 980 360
wait_text '더 많은 전달사항 보기' "$OUT/04-record-additional-policy-check.xml"
for text in '전달사항' '여벌 옷' '개인용품' '더 많은 전달사항 보기'; do
  grep -Fq "$text" "$OUT/04-record-additional-policy-check.xml"
done
if grep -Eq 'text="[^"]*(통증|상처|발열|병원|복약|기저귀|영양식)[^"]*"' "$OUT/04-record-additional-policy-check.xml"; then
  echo 'Health-facing copy rendered on v0.5.1 additional record screen' >&2
  exit 1
fi

start_capture_scrolled_extra RecordActivity '사진 2장' '04b-record-additional-photos.png' --el senior_id 4 --ei visual_step 2 --ez visual_photos true
start_capture_scrolled RecordActivity '어르신 상세보기로 이동' '05-delivery.png' --el senior_id 4 --ei visual_step 3 --ez visual_photos true
start_capture HistoryActivity '일일 기록 및 전달' '06-history.png' --el senior_id 4
start_capture_rendered MessageCenterActivity '07-message-center.png'
start_capture SettingsActivity '설정' '08-settings.png'

if adb logcat -d | grep -E 'FATAL EXCEPTION|Process: com.easternwood.dolbomon'; then
  echo 'DolbomOn crash detected during v0.5.1 final screen capture' >&2
  exit 1
fi

echo 'v0.5.1 final validation passed: daily-communication UI, neutral sharing flow, no health-facing selection copy, share regression, and rendered screens.'
