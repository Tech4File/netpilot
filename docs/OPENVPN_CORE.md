# OpenVPN inside NetPilot — engineering plan & status

**Status (v2.0.8):** OpenVPN profiles are fully managed in-app (import,
parse, validation, secrets, profiles, status). Connection paths today:

| Path | How it connects | Since |
|---|---|---|
| **Engine bridge** (shipped) | NetPilot drives the official open-source **OpenVPN for Android** app through its documented external control API (`de.blinkt.openvpn.api.ConnectVPN` / `DisconnectVPN`, FileProvider `.ovpn` hand-off). Works on Android 9/10/11+. | v2.0.6 |
| **Embedded core** (this plan) | The OpenVPN protocol core compiled INTO NetPilot — one app, nothing else to install. | phase 2 (below) |

An OpenVPN toggle that did not really tunnel would be a lie, and NetPilot
never lies about connection state. The core is therefore built properly,
in phases, with the heavy native build running on GitHub CI runners.

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
   it fetches the `openvpn3-android` core sources (AGPL-3.0) and drops a
   license note; the CI NDK job builds them.
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
