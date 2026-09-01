#!/usr/bin/env bash
set -euo pipefail

SLUG=${1:?usage: find-and-build.sh <slug>}
WORKSPACE=${GITHUB_WORKSPACE:-$(pwd)}
OWNER=woodeastern2-pixel
SEARCH_ROOT="$WORKSPACE"
EXTERNAL_ROOT="$RUNNER_TEMP/easternwood-repositories"
REPORT_DIR="$WORKSPACE/feature-capture-build/$SLUG"
mkdir -p "$REPORT_DIR" "$EXTERNAL_ROOT"

log() { printf '[capture-build:%s] %s\n' "$SLUG" "$*" | tee -a "$REPORT_DIR/build-harness.log"; }

restore_dolbomon() {
  local src="$RUNNER_TEMP/dolbomon-feature-src"
  rm -rf "$src" && mkdir -p "$src"
  cat "$WORKSPACE"/dolbomon/source.tar.gz.b64.part-* | base64 -d | tar -xz -C "$src"
  echo 'android.sourceset.disallowProvider=false' >> "$src/gradle.properties"
  for spec in \
    'v0.2.0-commercial:p2' \
    'v0.2.1-settings:p3' \
    'v0.2.2-korean-records:p2' \
    'v0.2.3-ui-fixes:p2' \
    'v0.3.0-redesign:p1' \
    'v0.3.1b-final-ui:p1' \
    'v0.3.2-record-ux:p1'; do
    local name=${spec%%:*}
    local strip=${spec##*:}
    cat "$WORKSPACE/dolbomon/patches/${name}.patch.gz.b64.part-"* | base64 -d | gzip -d > "$RUNNER_TEMP/${name}.patch"
    (cd "$src" && patch -"$strip" < "$RUNNER_TEMP/${name}.patch")
  done
  cat "$WORKSPACE"/dolbomon/patches/v0.3.3-stability-ui.patch.gz.b64.part-* | base64 -d | gzip -d > "$RUNNER_TEMP/v033.patch"
  (cd "$src" && patch -p1 < "$RUNNER_TEMP/v033.patch")
  base64 -d "$WORKSPACE/dolbomon/patches/v0.3.3-scrollbar-crashfix.patch.gz.b64" | gzip -d > "$RUNNER_TEMP/v033-scroll.patch"
  (cd "$src" && patch -p1 < "$RUNNER_TEMP/v033-scroll.patch")
  printf '%s\n' "$src"
}

discover_project() {
  python3 - "$1" "$SLUG" <<'PY'
import os, re, sys
from pathlib import Path
base=Path(sys.argv[1])
slug=sys.argv[2]
patterns={
 'nfc':[r'com\.easternwood\.[^\s"\']*nfc',r'nfc.?reader',r'nfc.?리더',r'nfc리다',r'near.?field'],
 'signpdf':[r'com\.easternwood\.signpdf',r'\bsignpdf\b',r'sign.?pdf'],
 'whereisit':[r'com\.easternwood\.whereisit',r'com\.whereisit\.app',r'whereisit',r'내.?물건.?어디'],
 'dolbomon':[r'com\.easternwood\.dolbomon',r'dolbomon',r'돌봄온'],
 'veilpic':[r'com\.easternwood\.veilpic',r'veilpic',r'veil.?pic',r'베일픽'],
 'ireumon':[r'com\.easternwood\.ireumgil',r'com\.ireumgil',r'ireumgil',r'ireumon',r'이름온',r'이름길'],
 'jamon':[r'com\.easternwood\.sleeproutine',r'sleeproutine',r'jamon',r'잠온',r'sleep.?routine'],
}[slug]
package_bonus={
 'nfc':['com.easternwood.nfc','com.easternwood.nfcreader'],
 'signpdf':['com.easternwood.signpdf'],
 'whereisit':['com.easternwood.whereisit','com.whereisit.app'],
 'dolbomon':['com.easternwood.dolbomon'],
 'veilpic':['com.easternwood.veilpic'],
 'ireumon':['com.easternwood.ireumgil','com.ireumgil'],
 'jamon':['com.easternwood.sleeproutine'],
}[slug]
roots=set()
for name in ('settings.gradle','settings.gradle.kts','pubspec.yaml'):
    for p in base.rglob(name):
        s=str(p)
        if any(x in s for x in ('/.git/','/node_modules/','/.github/capture-runner/','/build/','/.gradle/')):
            continue
        root=p.parent
        if name=='settings.gradle' or name=='settings.gradle.kts':
            if root.name=='android' and (root.parent/'pubspec.yaml').exists(): root=root.parent
        roots.add(root)

def read_text(root):
    chunks=[]
    wanted=['settings.gradle','settings.gradle.kts','build.gradle','build.gradle.kts','pubspec.yaml',
            'app/build.gradle','app/build.gradle.kts','android/app/build.gradle','android/app/build.gradle.kts',
            'app/src/main/AndroidManifest.xml','android/app/src/main/AndroidManifest.xml',
            'app/src/main/res/values/strings.xml','android/app/src/main/res/values/strings.xml']
    for rel in wanted:
        p=root/rel
        if p.is_file():
            try: chunks.append(p.read_text(errors='ignore')[:400000])
            except Exception: pass
    chunks.append(root.name)
    return '\n'.join(chunks).lower()

scored=[]
for root in roots:
    text=read_text(root)
    score=0
    for pkg in package_bonus:
        if pkg.lower() in text: score+=100
    for i,pat in enumerate(patterns):
        if re.search(pat,text,re.I): score+=40-i
    n=root.name.lower()
    if slug in n: score+=25
    if slug=='whereisit' and 'whereisit' in n: score+=40
    if slug=='ireumon' and ('ireum' in n or 'name' in n): score+=25
    if slug=='jamon' and ('sleep' in n or 'jam' in n): score+=25
    if slug=='veilpic' and ('veil' in n or 'backcame' in n): score+=25
    if slug=='nfc' and 'nfc' in n: score+=35
    if score: scored.append((score,len(str(root)),str(root)))
scored.sort(key=lambda x:(-x[0],x[1]))
if scored:
    print(scored[0][2])
    print('SCORE='+str(scored[0][0]),file=sys.stderr)
PY
}

clone_owner_repositories() {
  log "Searching public repositories owned by $OWNER"
  local list="$REPORT_DIR/repositories.tsv"
  gh api --paginate "users/$OWNER/repos?per_page=100&type=owner&sort=updated" \
    --jq '.[] | [.name,.clone_url] | @tsv' > "$list"
  while IFS=$'\t' read -r name url; do
    [ -n "$name" ] || continue
    case "$name" in
      dex-java|easternwood-studio-web) continue ;;
    esac
    local dst="$EXTERNAL_ROOT/$name"
    [ -d "$dst/.git" ] && continue
    log "Cloning $name"
    timeout 90 git clone --depth 1 --filter=blob:limit=25m "$url" "$dst" >>"$REPORT_DIR/clone.log" 2>&1 || true
  done < "$list"
}

PROJECT_DIR=""
if [ "$SLUG" = dolbomon ]; then
  PROJECT_DIR=$(restore_dolbomon)
else
  PROJECT_DIR=$(discover_project "$SEARCH_ROOT" 2>>"$REPORT_DIR/discovery.log" || true)
fi

if [ -z "$PROJECT_DIR" ] || [ ! -d "$PROJECT_DIR" ]; then
  clone_owner_repositories
  PROJECT_DIR=$(discover_project "$EXTERNAL_ROOT" 2>>"$REPORT_DIR/discovery.log" || true)
fi

if [ -z "$PROJECT_DIR" ] || [ ! -d "$PROJECT_DIR" ]; then
  log "ERROR: project source was not found"
  find "$WORKSPACE" -maxdepth 3 -type f \( -name settings.gradle -o -name settings.gradle.kts -o -name pubspec.yaml \) \
    | sort > "$REPORT_DIR/available-projects.txt" || true
  exit 21
fi

log "Selected project: $PROJECT_DIR"
printf 'slug=%s\nproject=%s\n' "$SLUG" "$PROJECT_DIR" > "$REPORT_DIR/discovery.properties"

# Capture-only compatibility repairs are applied only to the ephemeral Actions checkout.
if [ "$SLUG" = whereisit ] && [ -f "$PROJECT_DIR/app/src/main/res/layout/activity_main.xml" ]; then
  sed -i 's/app:textColor=/android:textColor=/g' "$PROJECT_DIR/app/src/main/res/layout/activity_main.xml"
fi
if [ "$SLUG" = ireumon ] && [ -f "$PROJECT_DIR/app/src/main/java/com/ireumgil/ui/HanjaSearchDialog.java" ]; then
  python3 - "$PROJECT_DIR/app/src/main/java/com/ireumgil/ui/HanjaSearchDialog.java" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]); s=p.read_text()
s=s.replace('private final HanjaRepository repository = new HanjaRepository();','private HanjaRepository repository;')
s=s.replace('public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {\n        View v =',
            'public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {\n        repository = new HanjaRepository(requireContext());\n        View v =')
p.write_text(s)
PY
fi

install_gradle() {
  local version=$1
  local home="$RUNNER_TEMP/gradle-$version"
  if [ ! -x "$home/bin/gradle" ]; then
    log "Installing Gradle $version"
    curl -fsSL --retry 4 "https://services.gradle.org/distributions/gradle-$version-bin.zip" -o "$RUNNER_TEMP/gradle-$version.zip"
    unzip -q -o "$RUNNER_TEMP/gradle-$version.zip" -d "$RUNNER_TEMP"
  fi
  printf '%s\n' "$home/bin/gradle"
}

build_native() {
  local root=$1
  local cmd=''
  if [ -x "$root/gradlew" ]; then
    cmd="$root/gradlew"
  elif [ -x "$WORKSPACE/gradlew" ] && [ "$root" = "$WORKSPACE" ]; then
    cmd="$WORKSPACE/gradlew"
  else
    local files="$root/build.gradle $root/build.gradle.kts $root/app/build.gradle $root/app/build.gradle.kts"
    local agp
    agp=$(cat $files 2>/dev/null | grep -Eo "com.android.application[^0-9]*[0-9]+\.[0-9]+(\.[0-9]+)?" | grep -Eo '[0-9]+\.[0-9]+(\.[0-9]+)?' | head -n1 || true)
    local gv=8.7
    case "$agp" in
      9.*) gv=9.5.0 ;;
      8.9*|8.10*|8.11*) gv=8.11.1 ;;
      8.7*|8.8*) gv=8.9 ;;
      *)
        case "$SLUG" in signpdf|dolbomon) gv=9.5.0 ;; esac
        ;;
    esac
    cmd=$(install_gradle "$gv")
  fi
  chmod +x "$cmd" || true
  log "Building native Android project with $cmd"
  "$cmd" -p "$root" :app:assembleDebug --stacktrace --no-daemon 2>&1 | tee "$REPORT_DIR/gradle-build.log"
}

build_flutter() {
  local root=$1
  log "Building Flutter project"
  (cd "$root" && flutter pub get && flutter build apk --debug) 2>&1 | tee "$REPORT_DIR/flutter-build.log"
}

if [ -f "$PROJECT_DIR/pubspec.yaml" ]; then
  build_flutter "$PROJECT_DIR"
else
  build_native "$PROJECT_DIR"
fi

APK_PATH=$(find "$PROJECT_DIR" -type f -path '*/build/outputs/*' -name '*.apk' \
  ! -name '*androidTest*' ! -name '*unaligned*' | grep -E 'debug|app-debug' | head -n1 || true)
if [ -z "$APK_PATH" ]; then
  APK_PATH=$(find "$PROJECT_DIR" -type f -name '*.apk' ! -name '*androidTest*' | head -n1 || true)
fi
if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
  log "ERROR: build completed without an APK"
  exit 22
fi

AAPT=$(find "$ANDROID_HOME/build-tools" -type f -name aapt | sort -V | tail -n1)
TARGET_PACKAGE=$($AAPT dump badging "$APK_PATH" | sed -n "s/package: name='\([^']*\)'.*/\1/p" | head -n1)
if [ -z "$TARGET_PACKAGE" ]; then
  log "ERROR: could not read application package from APK"
  exit 23
fi

cp "$APK_PATH" "$REPORT_DIR/${SLUG}-debug.apk"
{
  printf 'PROJECT_DIR=%s\n' "$PROJECT_DIR"
  printf 'APK_PATH=%s\n' "$APK_PATH"
  printf 'TARGET_PACKAGE=%s\n' "$TARGET_PACKAGE"
} >> "$GITHUB_ENV"
{
  printf 'apk=%s\n' "$APK_PATH"
  printf 'package=%s\n' "$TARGET_PACKAGE"
} >> "$REPORT_DIR/discovery.properties"
log "APK=$APK_PATH"
log "PACKAGE=$TARGET_PACKAGE"
