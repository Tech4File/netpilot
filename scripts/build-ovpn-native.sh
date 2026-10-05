#!/usr/bin/env bash
# NetPilot — build the embedded OpenVPN engine (libovpncore.so) for Android.
#
# Runs on GitHub CI runners (7 GB RAM) or a capable dev machine. Produces
# openvpn-core/src/main/jniLibs/<abi>/libovpncore.so which the Release job
# packages into the APK (binaries are NEVER committed — standing rule).
#
# Usage: scripts/build-ovpn-native.sh <abi> [<abi>...]
#   ABIs: arm64-v8a armeabi-v7a x86 x86_64
#
# Env: ANDROID_HOME (SDK; NDK resolved via $ANDROID_HOME/ndk/<latest>).
#
# Recipe (proven by the v2.2.1/v2.3.0 CI probes and the official client):
#   asio (headers) + OpenSSL (USE_OPENSSL, the flavor the official Android
#   client actually ships) + lz4 + fmt, cross-built static into a prefix;
#   the core compiled as a single TU (client/ovpncli.cpp) with our JNI.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${OVPN_BUILD_DIR:-$ROOT/openvpn-core/.native-build}"
PREFIX="$WORK/prefix"
CORE_DIR="$WORK/openvpn3"
JNILIBS="$ROOT/openvpn-core/src/main/jniLibs"

ABIS=("$@")
if [ ${#ABIS[@]} -eq 0 ]; then
  echo "usage: $0 <abi> [<abi>...] (arm64-v8a armeabi-v7a x86 x86_64)" >&2
  exit 1
fi

echo "[ovpn] workspace: $WORK"
mkdir -p "$WORK" "$JNILIBS"

# ---- 1. fetch sources (idempotent, pinned) ---------------------------------
fetch() { # fetch <dir> <url> <ref>
  if [ ! -d "$1" ]; then
    git clone --depth 1 --branch "$3" "$2" "$1"
  fi
}
fetch "$WORK/asio"        "https://github.com/chriskohlhoff/asio.git" "master"
fetch "$WORK/openssl-src" "https://github.com/openssl/openssl.git"    "openssl-3.0.15"
fetch "$WORK/lz4-src"     "https://github.com/lz4/lz4.git"            "v1.10.0"
fetch "$WORK/fmt-src"     "https://github.com/fmtlib/fmt.git"         "11.1.1"
if [ ! -d "$CORE_DIR" ]; then
  bash "$ROOT/scripts/vendor-openvpn-core.sh" "$CORE_DIR"
fi

# ---- 2. pick the NDK --------------------------------------------------------
NDK="${ANDROID_NDK_HOME:-}"
if [ -z "$NDK" ] && [ -n "${ANDROID_HOME:-}" ]; then
  NDK=$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort | tail -1 || true)
fi
[ -n "$NDK" ] || { echo "FATAL: NDK not found (set ANDROID_NDK_HOME)" >&2; exit 1; }
echo "[ovpn] NDK: $NDK"
export PATH="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"

# ---- 3. cross-build deps once per ABI into per-ABI prefixes ----------------
for ABI in "${ABIS[@]}"; do
  echo "[ovpn] ===== ABI $ABI ====="
  P="$WORK/prefix-$ABI"
  rm -rf "$P"; mkdir -p "$P"

  case "$ABI" in
    arm64-v8a)    OSSL_TARGET=android-arm64;  TRIPLE=aarch64-linux-android ;;
    armeabi-v7a)  OSSL_TARGET=android-arm;    TRIPLE=armv7a-linux-androideabi ;;
    x86)          OSSL_TARGET=android-x86;    TRIPLE=i686-linux-android ;;
    x86_64)       OSSL_TARGET=android-x86_64; TRIPLE=x86_64-linux-android ;;
    *) echo "FATAL: unknown ABI $ABI" >&2; exit 1 ;;
  esac

  # OpenSSL (static)
  if [ ! -f "$P/lib/libssl.a" ]; then
    echo "[ovpn] openssl ($OSSL_TARGET)"
    (cd "$WORK/openssl-src" \
      && make clean >/dev/null 2>&1 || true \
      && ANDROID_NDK_ROOT="$NDK" ./Configure "$OSSL_TARGET" \
           -D__ANDROID_API__=28 no-shared no-tests no-docs no-engine \
           --prefix="$P" \
      && make -j"$(nproc)" build_libs > /dev/null \
      && make install_sw > /dev/null)
  fi

  # lz4 (static, CMake)
  if [ ! -f "$P/lib/liblz4.a" ]; then
    echo "[ovpn] lz4"
    cmake -S "$WORK/lz4-src/build/cmake" -B "$WORK/lz4-$ABI" -G Ninja \
      -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-28 \
      -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF \
      -DCMAKE_INSTALL_PREFIX="$P" > /dev/null
    cmake --build "$WORK/lz4-$ABI" > /dev/null
    cmake --install "$WORK/lz4-$ABI" > /dev/null
  fi

  # fmt (static, CMake) — the core's CMake tooling expects it present.
  if [ ! -f "$P/lib/libfmt.a" ]; then
    echo "[ovpn] fmt"
    cmake -S "$WORK/fmt-src" -B "$WORK/fmt-$ABI" -G Ninja \
      -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-28 \
      -DCMAKE_BUILD_TYPE=Release -DFMT_TEST=OFF -DBUILD_SHARED_LIBS=OFF \
      -DFMT_HEADER_ONLY=ON -DCMAKE_INSTALL_PREFIX="$P" > /dev/null
    cmake --build "$WORK/fmt-$ABI" > /dev/null
    cmake --install "$WORK/fmt-$ABI" > /dev/null
  fi

  # ---- 4. the engine -----------------------------------------------------
  echo "[ovpn] core + JNI ($ABI)"
  cmake -S "$ROOT/openvpn-core/src/main/cpp" -B "$WORK/core-$ABI" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-28 \
    -DCMAKE_BUILD_TYPE=Release \
    -DOVPN_CORE_DIR="$CORE_DIR" \
    -DDEP_PREFIX="$P"
  cmake --build "$WORK/core-$ABI"

  OUT="$JNILIBS/$ABI"
  mkdir -p "$OUT"
  cp "$WORK/core-$ABI/libovpncore.so" "$OUT/"
  echo "[ovpn] -> $OUT/libovpncore.so ($(du -h "$OUT/libovpncore.so" | cut -f1))"
done

echo "[ovpn] DONE: $(find "$JNILIBS" -name '*.so' | wc -l) libraries"
