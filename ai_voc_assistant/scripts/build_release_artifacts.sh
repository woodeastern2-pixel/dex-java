#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT_DIR/release_artifacts"
LOCAL_FLUTTER="/workspaces/dex-java/.tools/flutter/bin/flutter"
ARM64_APK_NAME="ai_voc_assistant-arm64-v8a-release.apk"
LATEST_APK_NAME="ai_voc_assistant-latest.apk"

mkdir -p "$OUT_DIR"

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
cp "$APK_OUT_DIR/app-release.apk" "$OUT_DIR/$ARM64_APK_NAME"
cp "$APK_OUT_DIR/app-release.apk" "$OUT_DIR/$LATEST_APK_NAME"

echo "[4/6] Skipping demo data (not needed for production)"

echo "[5/6] Windows EXE build note"
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
echo "Stable local APK path: $OUT_DIR/$LATEST_APK_NAME"
echo "Stable GitHub download URL: https://github.com/woodeastern2-pixel/dex-java/raw/main/ai_voc_assistant/release_artifacts/$LATEST_APK_NAME"
