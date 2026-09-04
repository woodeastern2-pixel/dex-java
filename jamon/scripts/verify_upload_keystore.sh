#!/usr/bin/env bash
set -euo pipefail

expected_sha1='12905E469CD13F6640428B1177F9C197677B4676'
expected_sha256='335A8CD6A3DAFBA46A487B2013F1B217A04BEAE3FD97B352F8D73CA1E3DB9CAF'

: "${JAMON_KEYSTORE_PATH:?Set JAMON_KEYSTORE_PATH}"
: "${JAMON_KEYSTORE_PASSWORD:?Set JAMON_KEYSTORE_PASSWORD}"
: "${JAMON_KEY_ALIAS:?Set JAMON_KEY_ALIAS}"

if [ ! -f "$JAMON_KEYSTORE_PATH" ]; then
  echo "Keystore not found: $JAMON_KEYSTORE_PATH" >&2
  exit 2
fi

certificate=$(keytool -list -v \
  -keystore "$JAMON_KEYSTORE_PATH" \
  -storepass "$JAMON_KEYSTORE_PASSWORD" \
  -alias "$JAMON_KEY_ALIAS")

actual_sha1=$(printf '%s\n' "$certificate" | awk -F'SHA1: ' '/SHA1:/{gsub(":", "", $2); print toupper($2); exit}')
actual_sha256=$(printf '%s\n' "$certificate" | awk -F'SHA256: ' '/SHA256:/{gsub(":", "", $2); print toupper($2); exit}')

if [ "$actual_sha1" != "$expected_sha1" ] || [ "$actual_sha256" != "$expected_sha256" ]; then
  echo "Refusing release: Jamon upload certificate does not match Google Play." >&2
  echo "SHA-1:   $actual_sha1" >&2
  echo "SHA-256: $actual_sha256" >&2
  exit 3
fi

echo "Jamon Play upload certificate verified."
