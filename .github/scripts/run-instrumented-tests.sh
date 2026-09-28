#!/usr/bin/env bash
# CI: install the app, optionally grant WRITE_SECURE_SETTINGS, run instrumented tests.
#
# Why an external file: multiline shell control flow inside the emulator-runner
# `script:` input was truncated to its first line on the runners
# ("/usr/bin/sh: Syntax error: end of file unexpected (expecting \"fi\")").
# A single-line `bash path/to/script` call is immune, versionable and locally testable.
set -Eeuo pipefail

APP_ID="app.netpilot"
PERMISSION="android.permission.WRITE_SECURE_SETTINGS"

echo "==> Installing debug APK..."
./gradlew :app:installDebug --stacktrace --console=plain

if [[ "${GRANT_SECURE_SETTINGS:-false}" == "true" ]]; then
  echo "==> Granting WRITE_SECURE_SETTINGS..."
  adb shell pm grant "$APP_ID" "$PERMISSION"
  # Fail loudly if the grant did not stick (some pm versions exit 0 even on
  # failure — never let the "granted" leg silently degrade into skipped tests).
  if ! adb shell dumpsys package "$APP_ID" | grep -q "WRITE_SECURE_SETTINGS.*granted=true"; then
    echo "::error::WRITE_SECURE_SETTINGS grant did not take effect on $APP_ID"
    exit 1
  fi
  echo "==> Grant verified."
else
  echo "==> Skipping WRITE_SECURE_SETTINGS grant (ungranted leg: system-write tests will skip)"
fi

echo "==> Running instrumented tests..."
./gradlew connectedDebugAndroidTest --stacktrace --console=plain
