#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT_DIR/release_artifacts"
ANDROID_OUT_DIR="$OUT_DIR/android"
LOCAL_FLUTTER="/workspaces/dex-java/.tools/flutter/bin/flutter"
LATEST_APK_NAME="ai_voc_assistant-latest-release.apk"
LATEST_APK_ZIP_NAME="ai_voc_assistant-latest-release.zip"

mkdir -p "$OUT_DIR"
mkdir -p "$ANDROID_OUT_DIR"

if [[ -x "$LOCAL_FLUTTER" ]]; then
  FLUTTER="$LOCAL_FLUTTER"
elif command -v flutter >/dev/null 2>&1; then
  FLUTTER="$(command -v flutter)"
else
  echo "Flutter is not installed in this environment."
  echo "Install Flutter or place it at /workspaces/dex-java/.tools/flutter."
  exit 1
fi

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/workspaces/dex-java/android-sdk}"

echo "[1/6] Checking Flutter toolchain..."
"$FLUTTER" --version

cd "$ROOT_DIR"

echo "[2/6] Resolving dependencies"
if [[ -f "$ROOT_DIR/.dart_tool/package_config.json" ]]; then
  echo "Dependencies already resolved; skipping pub get"
else
  "$FLUTTER" pub get
fi

echo "[3/6] Building Android APK"
"$FLUTTER" build apk --release --target-platform android-arm64 --no-pub
APK_OUT_DIR="$ROOT_DIR/build/app/outputs/flutter-apk"
# Keep a single stable APK path and always overwrite it.
cp -f "$APK_OUT_DIR/app-release.apk" "$ANDROID_OUT_DIR/$LATEST_APK_NAME"
sha256sum "$ANDROID_OUT_DIR/$LATEST_APK_NAME" > "$ANDROID_OUT_DIR/$LATEST_APK_NAME.sha256"

echo "[4/6] Packaging Android APK zip"
cd "$ANDROID_OUT_DIR"
rm -f "$LATEST_APK_ZIP_NAME"
zip -q -9 "$LATEST_APK_ZIP_NAME" "$LATEST_APK_NAME"
sha256sum "$LATEST_APK_ZIP_NAME" > "$LATEST_APK_ZIP_NAME.sha256"
cd "$ROOT_DIR"

# Remove all other APK/ZIP files so only the latest stable Android artifacts remain.
find "$ROOT_DIR" -type f -name "*.apk" \
  ! -path "$ANDROID_OUT_DIR/$LATEST_APK_NAME" -delete
find "$ANDROID_OUT_DIR" -type f -name "*.apk.sha256" \
  ! -name "$LATEST_APK_NAME.sha256" -delete
find "$ANDROID_OUT_DIR" -type f -name "*.zip" \
  ! -name "$LATEST_APK_ZIP_NAME" -delete
find "$ANDROID_OUT_DIR" -type f -name "*.zip.sha256" \
  ! -name "$LATEST_APK_ZIP_NAME.sha256" -delete

echo "[5/6] Skipping demo data (not needed for production)"

echo "[6/6] Windows EXE build note"
if [[ "$(uname -s)" == "Linux" ]]; then
  echo "Windows EXE cannot be produced on Linux host directly."
  echo "Use .github/workflows/build-windows-exe.yml on a windows-latest runner."
else
  "$FLUTTER" build windows --release
  WIN_SRC="$ROOT_DIR/build/windows/x64/runner/Release"
  WIN_DST="$OUT_DIR/windows-release"
  rm -rf "$WIN_DST"
  mkdir -p "$WIN_DST"
  cp -R "$WIN_SRC"/* "$WIN_DST"/
fi

echo "Done. Artifacts are in: $OUT_DIR"
echo "Stable local APK path: $ANDROID_OUT_DIR/$LATEST_APK_NAME"
echo "Stable local APK zip path: $ANDROID_OUT_DIR/$LATEST_APK_ZIP_NAME"
echo "Stable GitHub download URL: https://github.com/woodeastern2-pixel/dex-java/raw/main/ai_voc_assistant/release_artifacts/android/$LATEST_APK_NAME"
