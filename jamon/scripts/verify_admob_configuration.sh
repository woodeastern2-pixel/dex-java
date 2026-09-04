#!/usr/bin/env bash
set -euo pipefail

project_dir=$(cd "$(dirname "$0")/.." && pwd)
build_file="$project_dir/app/build.gradle"
manifest_file="$project_dir/app/src/main/AndroidManifest.xml"
source_dir="$project_dir/app/src/main/java"

required_values=(
  'ca-app-pub-9360550840761530~2835130981'
  'ca-app-pub-9360550840761530/6726274534'
  'ca-app-pub-9360550840761530/1522049310'
  'ca-app-pub-3940256099942544~3347511713'
  'ca-app-pub-3940256099942544/6300978111'
  'ca-app-pub-3940256099942544/5224354917'
)

for value in "${required_values[@]}"; do
  if ! rg -Fq "$value" "$build_file"; then
    echo "Missing required AdMob identifier: $value" >&2
    exit 2
  fi
done

rg -Fq 'com.google.android.gms.ads.APPLICATION_ID' "$manifest_file"
rg -Fq 'android.permission.INTERNET' "$manifest_file"
rg -Fq 'android.permission.ACCESS_NETWORK_STATE' "$manifest_file"
rg -Fq "buildConfigField 'boolean', 'USING_TEST_ADS', 'true'" "$build_file"
rg -Fq "buildConfigField 'boolean', 'USING_TEST_ADS', 'false'" "$build_file"

if rg -q 'com\.android\.billingclient|BillingClient|queryPurchasesAsync' "$project_dir/app"; then
  echo 'Billing code or dependency found in a no-Billing release.' >&2
  exit 3
fi

if rg -q 'Prefs\.setPro\(' "$source_dir"; then
  echo 'A manual Pro bypass remains in application source.' >&2
  exit 4
fi

rg -Fq '24L * 60L * 60L * 1000L' \
  "$source_dir/com/easternwood/sleeproutine/ProAccessPolicy.java"

echo 'Jamon AdMob and 24-hour Pro configuration verified.'
