#!/usr/bin/env bash
# NetPilot — OpenVPN core vendoring helper (PHASE 2 of docs/OPENVPN_CORE.md).
#
# Fetches the OpenVPN protocol core SOURCES for the :openvpn-core module.
# Runs on a developer machine or in CI — NEVER commit the fetched tree
# blindly: review the pinned commit, keep licenses intact, and let the CI
# NDK job BUILD it (no binaries in git — standing project rule).
#
# Usage:
#   scripts/vendor-openvpn-core.sh <output-dir> [ref]
#
# Source of truth: https://github.com/openvpn/openvpn3-android (AGPL-3.0)
set -euo pipefail

OUT="${1:?usage: vendor-openvpn-core.sh <output-dir> [ref]}"
REF="${2:-master}"
REPO="https://github.com/openvpn/openvpn3-android.git"

mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "[vendor] cloning $REPO @ $REF (shallow)"
git clone --depth 1 --branch "$REF" "$REPO" "$TMP/src"

echo "[vendor] copying core sources (source-only)"
mkdir -p "$OUT/core"
cp -r "$TMP/src/core/." "$OUT/core/"

cat > "$OUT/LICENSE-OPENVPN-CORE.txt" <<'NOTE'
NetPilot vendors the OpenVPN3 C++ core from openvpn3-android (AGPL-3.0).
See core/ for the full sources and copyright headers. This component is
built by CI into the :openvpn-core module; no prebuilt binaries are
accepted into the NetPilot repository.
NOTE

echo "[vendor] done: $OUT"
echo "[vendor] NEXT: create :openvpn-core (CMake + NDK r27, ABIs:"
echo "[vendor]   arm64-v8a armeabi-v7a x86 x86_64) and wire"
echo "[vendor]   OvpnCoreChannel into core.vpn.VpnDataChannel.factory"
echo "[vendor] LICENSE: add AGPL-3.0 entry to LicenseCatalog (shipped=true)."
