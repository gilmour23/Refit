#!/usr/bin/env bash
set -euo pipefail

if ! command -v java >/dev/null 2>&1; then
  echo "Java 17 is required." >&2
  exit 1
fi

echo "Building Re:Fit hardened internal APK..."
./gradlew --no-daemon :app:assembleInternal

APK="app/build/outputs/apk/internal/app-internal.apk"
test -f "$APK"

if command -v sha256sum >/dev/null 2>&1; then
  echo
  echo "APK: $APK"
  sha256sum "$APK"
fi
