#!/usr/bin/env bash
set -euo pipefail

SLUG=${1:?slug required}
TARGET_PACKAGE=${2:?target package required}
APK_PATH=${3:?apk path required}
RUNNER_DIR=${4:?runner artifact directory required}
ASSET_DIR=${5:?sample asset directory required}
OUT="$GITHUB_WORKSPACE/feature-scenario-screens/$SLUG"
mkdir -p "$OUT"

log() { printf '[scenario:%s] %s\n' "$SLUG" "$*" | tee -a "$OUT/host-scenario.log"; }

adb wait-for-device
adb shell wm size 1080x2400
adb shell wm density 420
adb shell settings put system font_scale 1.0 || true
adb shell settings put system system_locales ko-KR || true
adb shell pm clear "$TARGET_PACKAGE" >/dev/null 2>&1 || true

log "Installing actual app APK: $APK_PATH"
adb install -r -t "$APK_PATH"

for permission in \
  android.permission.CAMERA \
  android.permission.READ_MEDIA_IMAGES \
  android.permission.READ_MEDIA_VIDEO \
  android.permission.READ_EXTERNAL_STORAGE \
  android.permission.WRITE_EXTERNAL_STORAGE \
  android.permission.POST_NOTIFICATIONS; do
  adb shell pm grant "$TARGET_PACKAGE" "$permission" >/dev/null 2>&1 || true
done

adb shell mkdir -p /sdcard/Download
if [ -f "$ASSET_DIR/sample-contract.pdf" ]; then
  adb push "$ASSET_DIR/sample-contract.pdf" /sdcard/Download/sample-contract.pdf >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/sample-contract.pdf >/dev/null || true
fi
if [ -f "$ASSET_DIR/sample-private-info.png" ]; then
  adb push "$ASSET_DIR/sample-private-info.png" /sdcard/Download/sample-private-info.png >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/sample-private-info.png >/dev/null || true
fi

RUNNER_APK=$(find "$RUNNER_DIR" -type f -name '*debug.apk' ! -name '*androidTest*' | head -n1)
TEST_APK=$(find "$RUNNER_DIR" -type f -name '*androidTest*.apk' | head -n1)
test -f "$RUNNER_APK"
test -f "$TEST_APK"
adb install -r -t "$RUNNER_APK" >/dev/null
adb install -r -t "$TEST_APK" >/dev/null
adb shell pm clear com.easternwood.capture.runner >/dev/null 2>&1 || true
adb logcat -c

log "Executing real UI scenario against $TARGET_PACKAGE"
set +e
adb shell am instrument -w -r \
  -e slug "$SLUG" \
  -e targetPackage "$TARGET_PACKAGE" \
  com.easternwood.capture.runner.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$OUT/instrumentation.txt"
INSTRUMENT_STATUS=${PIPESTATUS[0]}
set -e

REMOTE="/sdcard/Android/data/com.easternwood.capture.runner/files/screens/$SLUG"
adb pull "$REMOTE/." "$OUT/" >/dev/null 2>&1 || true

# Always preserve a final, indisputable framebuffer capture and diagnostics.
adb exec-out screencap -p > "$OUT/zz-final-framebuffer.png" || true
adb shell uiautomator dump /sdcard/final-window.xml >/dev/null 2>&1 || true
adb pull /sdcard/final-window.xml "$OUT/zz-final-window.xml" >/dev/null 2>&1 || true
adb shell dumpsys activity activities > "$OUT/activity-dump.txt" || true
adb shell dumpsys window > "$OUT/window-dump.txt" || true
adb logcat -d > "$OUT/logcat.txt" || true

PNG_COUNT=$(find "$OUT" -maxdepth 1 -type f -name '*.png' -size +10k | wc -l | tr -d ' ')
{
  printf 'slug=%s\n' "$SLUG"
  printf 'targetPackage=%s\n' "$TARGET_PACKAGE"
  printf 'apk=%s\n' "$APK_PATH"
  printf 'api=35\nprofile=pixel_6\nresolution=1080x2400\n'
  printf 'instrumentStatus=%s\n' "$INSTRUMENT_STATUS"
  printf 'pngCount=%s\n' "$PNG_COUNT"
  printf 'captureType=actual-app-running-on-android-emulator\n'
  if [ "$SLUG" = nfc ]; then printf 'nfcInput=synthetic-ndef-test-payload\n'; fi
} > "$OUT/host-metadata.properties"

log "Captured PNG files: $PNG_COUNT"
if [ "$PNG_COUNT" -lt 2 ]; then
  log "ERROR: scenario produced too few screenshots"
  exit 31
fi
if [ "$INSTRUMENT_STATUS" -ne 0 ]; then
  log "Instrumentation reported status $INSTRUMENT_STATUS; screenshots and diagnostics were retained"
  exit "$INSTRUMENT_STATUS"
fi
