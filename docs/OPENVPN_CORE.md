# OpenVPN inside NetPilot — engineering plan & status

**Status (v2.3.0): the embedded engine is IMPLEMENTED and CI-built.**
OpenVPN profiles are fully managed in-app (import, parse, validation,
secrets, profiles, status). Connection paths:

| Path | How it connects | Since |
|---|---|---|
| **Embedded engine** (implemented, v2.3.0) | The official OpenVPN 3 C++ core (`github.com/openvpn/openvpn3`, AGPL-3.0) compiled by CI into `libovpncore.so` behind `app.netpilot.openvpn.core` (`OvpnCoreEngine` → app-side `OvpnCoreChannel : VpnDataChannel`). NetPilotVpnService builds the TUN, the fd is injected via `tun_builder_establish`, `socket_protect` routes through `VpnService.protect`, and "connected" means the core's own CONNECTED event (bounded wait). Ships in release APKs whose CI ran the native job; builds without the library fall back honestly. | v2.3.0 |
| **Engine bridge** (fallback, still shipped) | NetPilot drives the official open-source **OpenVPN for Android** app through its documented external control API. Used when a build has no native library (quality builds) or while the engine is device-validated. | v2.0.6 |

What "really connects" means here: no state is faked anywhere. The tile/UI
say connected only after the core emits CONNECTED; `open()` returns false on
config rejection (`eval_config` error) or on the bounded CONNECTED timeout,
and the service tears down. First end-to-end validation happens on the user's
device with a real server (the bridge remains until then, per the standing
rule that the bridge goes only when the core is device-proven).

## Why the core is not a quick patch

The OpenVPN protocol core is a large native C/C++ codebase (TLS data
channel, ciphers, NCP, replay protection) that runs inside the
privileged VPN path. Embedding it means: vendoring C sources, an NDK
build for 4 ABIs, a JNI bridge, and **AGPL-3.0** licensing for that
component (disclosed in-app). Doing that half-way would ship a fake
toggle — unacceptable.

## Phase 2 — embedded core, concrete steps

1. **Vendor sources (source-only, never binaries):** run
   `scripts/vendor-openvpn-core.sh openvpn-core/src <pinned-ref>` —
   it fetches the **OpenVPN 3 C++ core** (AGPL-3.0) and drops a
   license note; the CI NDK job builds them.
   *Upstream reality (verified via the GitHub API, v2.2.1):*
   - `github.com/openvpn/openvpn3` — the actual C++ core; ships its own
     CMake (`CMakeLists.txt`, `cmake/findcoredeps.cmake`, target
     `ovpncli`), default branch `master`. THIS is what we vendor.
   - `github.com/openvpn/openvpn3-android` — DOES NOT EXIST (404). The
     first CI probe failed exactly here; do not reference it again.
   - `github.com/schwabe/ics-openvpn` — "OpenVPN for Android" (AGPL),
     builds the core via `main/src/main/cpp/CMakeLists.txt` — our
     reference integration.
   - The core's CMake needs `DEP_DIR` (default `<core>/../deps`) with
     `asio/asio/` headers, plus mbedtls (USE_MBEDTLS), lz4 and fmt
     found via `CMAKE_PREFIX_PATH` / `PKG_CONFIG_PATH` — all
     cross-built in the probe workflow before configuring the core.
2. **Module:** new `:openvpn-core` Android library; CMake + NDK r27;
   ABIs arm64-v8a, armeabi-v7a, x86, x86_64; minSdk 28.
3. **JNI bridge:** implement the existing `core.vpn.VpnDataChannel`
   factory seam — `OvpnCoreChannel : VpnDataChannel` — so NetPilot's
   service plumbing (consent, TUN, FGS, notification, teardown,
   ownership flags) is reused verbatim. The parser feeding it is
   already first-party (`OvpnConfigParser`).
4. **CI:** the NDK build runs on GitHub runners (7 GB RAM): job pins
   NDK + CMake, builds all ABIs, uploads `.so` artifacts to the release
   job — binaries are NEVER committed (standing rule).
5. **License:** AGPL-3.0 disclosure added to Settings → Open-source
   licenses (`shipped = true`) the moment the core ships.
6. **Acceptance:** connect a real OpenVPN server on Android 9, 10 and
   11+ TVs/emulators; unit tests for the JNI contract; CodeQL clean;
   APK size budget documented in the release notes.

Until phase 2 lands, WireGuard (embedded, one app) and the OpenVPN
engine bridge keep every supported device working.

## Probe (v2.2.0)

`openvpn-core.yml` (workflow_dispatch only — zero CI minutes until triggered)
vendors the sources on GitHub infra and attempts a CMake configure+build for
arm64-v8a with NDK r27, uploading the logs as an artifact. Iterate on the
`:openvpn-core` module CMake against those logs; the bridge stays until a
real tunnel connects.

## Probe history (facts, not claims)

- **Probe 1 (v2.2.0): FAILED in the vendor step** — cloned the
  nonexistent `openvpn/openvpn3-android` (404 → git credential prompt).
  No compilation was attempted; nothing was learned about the toolchain.
- **Probe 2 (v2.2.1): rewritten** — vendors `openvpn/openvpn3` (verified
  reachable; the vendor script was executed end-to-end in the dev
  sandbox: 786 files, 12 MB source, licenses intact), then cross-builds
  asio/mbedtls/lz4/fmt for arm64-v8a with NDK r27 and configures the
  core's own CMake with `-DUSE_MBEDTLS=ON`, building the `ovpncli`
  client library. Every step is continue-on-error: the uploaded logs are
  the input for the next iteration. The bridge stays until a real
  tunnel connects.
