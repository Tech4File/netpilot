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
PKG_DUMP="/tmp/netpilot-pkg-dump.txt"

# On any failure: leave diagnostics in the workspace (uploaded as CI artifacts).
cleanup() {
  rc=$?
  if [ $rc -ne 0 ]; then
    echo "::error::instrumented-test script failed (stage above). Dumping diagnostics..."
    adb devices -l > failed-diagnostics.txt 2>&1 || true
    adb logcat -d -t 4000 >> failed-diagnostics.txt 2>&1 || true
    [ -f "$PKG_DUMP" ] && tail -60 "$PKG_DUMP" >> failed-diagnostics.txt || true
    echo "Diagnostics written to failed-diagnostics.txt"
  fi
}
trap cleanup EXIT

echo "==> Devices visible to adb:"
adb devices -l

echo "==> Installing debug APK..."
./gradlew :app:installDebug --stacktrace --console=plain

if [[ "${GRANT_SECURE_SETTINGS:-false}" == "true" ]]; then
  echo "==> Granting WRITE_SECURE_SETTINGS..."
  adb shell pm grant "$APP_ID" "$PERMISSION"
  sleep 1
  # NOTE: dump the full package info to a FILE, then grep the file.
  # (Piping adb straight into `grep -q` + `set -o pipefail` lets grep close the
  # pipe early -> adb dies with SIGPIPE -> the guard false-negatives even on a
  # successful grant. Learned the hard way; don't "simplify" this back.)
  adb shell dumpsys package "$APP_ID" > "$PKG_DUMP" 2>/dev/null || true
  if ! grep -q "WRITE_SECURE_SETTINGS.*granted=true" "$PKG_DUMP"; then
    echo "::error::WRITE_SECURE_SETTINGS grant did not take effect on $APP_ID"
    exit 1
  fi
  echo "==> Grant verified."
else
  echo "==> Skipping WRITE_SECURE_SETTINGS grant (ungranted leg: system-write tests will skip)"
fi

echo "==> Running instrumented tests..."
./gradlew connectedDebugAndroidTest --stacktrace --console=plain
