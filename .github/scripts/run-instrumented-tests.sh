#!/usr/bin/env bash
# CI: install the app, optionally grant WRITE_SECURE_SETTINGS, run instrumented tests.
#
# Why an external file: multiline shell control flow inside the emulator-runner
# `script:` input was truncated to its first line on the runners
# ("/usr/bin/sh: Syntax error: end of file unexpected (expecting \"fi\")").
# A single-line `bash path/to/script` call is immune, versionable and locally testable.
#
# Reliability model: freshly provisioned emulators (especially the API 35
# image on its cold first boot) flake at a low but real rate — install or
# test-run fails with no app-side cause. connectDebugAndroidTest and the
# test gradle run each get ONE retry. Real failures fail twice in a row and
# still exit 1 — retries never mask an honest red.
set -Eeuo pipefail

APP_ID="app.netpilot"
PERMISSION="android.permission.WRITE_SECURE_SETTINGS"
PKG_DUMP="/tmp/netpilot-pkg-dump.txt"

# On any failure: leave diagnostics in the workspace (uploaded as CI artifacts).
cleanup() {
  rc=$?
  if [ $rc -ne 0 ]; then
    echo "::error::instrumented-test script failed (stage above). Dumping diagnostics..."
    {
      echo "=== device state ==="
      adb devices -l || true
      echo "=== api level ==="
      adb shell getprop ro.build.version.sdk || true
      echo "=== last 4000 logcat lines ==="
      adb logcat -d -t 4000 || true
      echo "=== package dump (tail) ==="
      [ -f "$PKG_DUMP" ] && tail -60 "$PKG_DUMP" || true
    } > failed-diagnostics.txt 2>&1
    echo "Diagnostics written to failed-diagnostics.txt"
  fi
}
trap cleanup EXIT

# run_with_retry N LABEL cmd... — run once, on failure warn and retry once more.
run_with_retry() {
  local tries="$1" label="$2"; shift 2
  local attempt=1
  while [ "$attempt" -le "$tries" ]; do
    echo "==> [$label] attempt $attempt/$tries"
    if "$@"; then
      return 0
    fi
    echo "::warning::[$label] attempt $attempt failed"
    attempt=$((attempt + 1))
    sleep 5
  done
  echo "::error::[$label] failed after $tries attempts"
  return 1
}

echo "==> Devices visible to adb:"
adb devices -l
echo "==> API level: $(adb shell getprop ro.build.version.sdk || echo '?')"

echo "==> Installing debug APK..."
run_with_retry 2 "installDebug" ./gradlew :app:installDebug --stacktrace --console=plain

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
    # One retry: a just-booted emulator occasionally drops the first pm call.
    sleep 3
    adb shell pm grant "$APP_ID" "$PERMISSION" || true
    sleep 1
    adb shell dumpsys package "$APP_ID" > "$PKG_DUMP" 2>/dev/null || true
  fi
  if ! grep -q "WRITE_SECURE_SETTINGS.*granted=true" "$PKG_DUMP"; then
    echo "::error::WRITE_SECURE_SETTINGS grant did not take effect on $APP_ID"
    echo "--- grant-related dumpsys lines (also in failed-diagnostics.txt):"
    grep "WRITE_SECURE_SETTINGS" "$PKG_DUMP" || echo "(none found)"
    exit 1
  fi
  echo "==> Grant verified."
else
  echo "==> Skipping WRITE_SECURE_SETTINGS grant (ungranted leg: system-write tests will skip)"
fi

echo "==> Running instrumented tests..."
# Two attempts: fresh-emulator flakes are real; deterministic failures fail
# twice and still fail the build.
if ! ./gradlew connectedDebugAndroidTest --stacktrace --console=plain; then
  echo "::warning::first instrumented run failed — retrying once on the same emulator"
  ./gradlew connectedDebugAndroidTest --stacktrace --console=plain
fi
echo "==> Instrumented tests PASSED."
