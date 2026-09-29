#!/usr/bin/env bash
set -euo pipefail

if ! command -v java >/dev/null 2>&1; then
  echo "Java 17 is required." >&2
  exit 1
fi

JAVA_MAJOR="$(java -version 2>&1 | head -n1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [[ "$JAVA_MAJOR" != "17" ]]; then
  echo "Java 17 is required. Current major version: $JAVA_MAJOR" >&2
  exit 1
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" && -f local.properties ]]; then
  SDK_ROOT="$(grep '^sdk.dir=' local.properties | head -n1 | cut -d= -f2- | sed 's/\\:/:/g; s/\\\\/\\/g')"
fi

if [[ -z "$SDK_ROOT" ]]; then
  echo "Android SDK not found. Set ANDROID_SDK_ROOT/ANDROID_HOME or configure local.properties." >&2
  exit 1
fi

AAPT="$(find "$SDK_ROOT/build-tools" -maxdepth 2 -type f -name aapt | sort -V | tail -n1)"
if [[ -z "$AAPT" || ! -x "$AAPT" ]]; then
  echo "aapt not found under Android SDK build-tools." >&2
  exit 1
fi

echo "Building Re:Fit hardened internal APK..."
./gradlew --no-daemon :app:assembleInternal

APK="app/build/outputs/apk/internal/app-internal.apk"
test -f "$APK"

echo "Verifying APK hardening..."
BADGING="$(mktemp)"
MANIFEST="$(mktemp)"
PERMISSIONS="$(mktemp)"
trap 'rm -f "$BADGING" "$MANIFEST" "$PERMISSIONS"' EXIT

"$AAPT" dump badging "$APK" > "$BADGING"
"$AAPT" dump xmltree "$APK" AndroidManifest.xml > "$MANIFEST"
"$AAPT" dump permissions "$APK" > "$PERMISSIONS"

check() {
  local name="$1"
  shift
  if "$@"; then
    echo "[PASS] $name"
  else
    echo "[FAIL] $name" >&2
    exit 1
  fi
}

check "non-debuggable" bash -c "! grep -q 'application-debuggable' '$BADGING'"
check "no Compose PreviewActivity" bash -c "! grep -q 'androidx.compose.ui.tooling.PreviewActivity' '$MANIFEST'"
check "allowBackup=false" grep -Eq "android:allowBackup.*0x0" "$MANIFEST"
check "usesCleartextTraffic=false" grep -Eq "android:usesCleartextTraffic.*0x0" "$MANIFEST"
check "no INTERNET permission" bash -c "! grep -q 'android.permission.INTERNET' '$PERMISSIONS'"

echo
echo "APK: $APK"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$APK"
elif command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$APK"
fi
