#!/usr/bin/env bash
# NetPilot — OpenVPN core vendoring helper (PHASE 2 of docs/OPENVPN_CORE.md).
#
# Fetches the OpenVPN 3 C++ protocol core SOURCES for the :openvpn-core
# module. v2.2.1 correction: the previously referenced
# github.com/openvpn/openvpn3-android DOES NOT EXIST (404 — verified via the
# GitHub API; that failure is what broke the first CI probe). The real
# upstream core is github.com/openvpn/openvpn3 (C++, ships its own CMake,
# default branch master). The Android UI that builds it is
# github.com/schwabe/ics-openvpn ("OpenVPN for Android", AGPL) whose
# main/src/main/cpp/CMakeLists.txt is the reference integration.
#
# Runs on a developer machine or in CI — NEVER commit the fetched tree
# blindly: review the pinned commit, keep licenses intact, and let the CI
# NDK job BUILD it (no binaries in git — standing project rule).
#
# Usage:
#   scripts/vendor-openvpn-core.sh <output-dir> [ref]
#
# Source of truth: https://github.com/openvpn/openvpn3 (AGPL-3.0 — the
# LICENSES/ folder travels with the sources).
set -euo pipefail

OUT="${1:?usage: vendor-openvpn-core.sh <output-dir> [ref]}"
REF="${2:-master}"
REPO="https://github.com/openvpn/openvpn3.git"

mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "[vendor] cloning $REPO @ $REF (shallow)"
git clone --depth 1 --branch "$REF" "$REPO" "$TMP/src" 2>&1 | sed 's/^/[vendor] /'

CORE="$TMP/src"
if [ ! -f "$CORE/CMakeLists.txt" ]; then
  echo "[vendor] FATAL: core checkout has no CMakeLists.txt — wrong repo/ref?" >&2
  exit 1
fi

echo "[vendor] copying sources (source-only) into $OUT"
# The whole tree is source; strip VCS metadata and CI-only bits.
if command -v rsync > /dev/null 2>&1; then
  rsync -a --exclude '.git' --exclude '.github' "$CORE/" "$OUT/"
else
  (cd "$CORE" && tar -cf - --exclude=.git --exclude=.github .) | tar -xf - -C "$OUT"
fi

# Licenses travel with the code (AGPL-3.0 family; keep every file intact).
if [ -d "$OUT/LICENSES" ]; then
  echo "[vendor] LICENSES/ kept ($(find "$OUT/LICENSES" -type f | wc -l) files)"
fi
cat > "$OUT/LICENSE-OPENVPN-CORE.txt" <<'EOF'
OpenVPN 3 C++ core (vendored source for NetPilot's :openvpn-core module)
Upstream: https://github.com/openvpn/openvpn3
License: AGPL-3.0 (see LICENSES/ and LICENSE.md shipped alongside).
This tree is vendored AS SOURCE; binaries are never committed (NetPilot
policy). Built at CI time by the NDK/CMake job. "OpenVPN" is a registered
trademark of OpenVPN (Inc.).
EOF

echo "[vendor] done: $(find "$OUT" -type f | wc -l) files, $(du -sh "$OUT" | cut -f1)"
echo "[vendor] next: configure with the Android NDK toolchain — the core's"
echo "[vendor] own CMake needs DEP_DIR with asio (+ mbedtls/lz4/fmt); see"
echo "[vendor] .github/workflows/openvpn-core.yml for the working recipe."
