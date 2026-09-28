# NetPilot Security Model

## Threat model (what this app defends against)

| Threat | Mitigation |
|---|---|
| ISP / LAN snooping of DNS lookups | System-wide DNS-over-TLS via Private DNS (strict mode) |
| Malicious app silently changing system DNS | Android keeps `WRITE_SECURE_SETTINGS` protected; the grant is explicit and one-time |
| Phishing a user into installing a fake "settings helper" | NetPilot is fully open source; the ADB command it requests is shown verbatim and grants *only* secure-settings write |
| Traffic correlation on hostile networks | Optional user-controlled VPN (IKEv2 platform or OpenVPN profiles) |
| "I can't use ADB" leading users to root or sketchy helper apps | Zero-setup Secure DNS: first-party DoT tunnel behind the standard VpnService consent — one remote tap, no ADB |
| Data exfiltration by the app itself | App performs zero network I/O of its own; enforced by code review + `network_security_config` (cleartext banned) + no permissions beyond `INTERNET`/`ACCESS_NETWORK_STATE`/`FOREGROUND_SERVICE*` |
| Profile theft via cloud backup | Profile storage excluded from backup/device-transfer; VPN secrets never exported |

## Permissions — minimal by design

| Permission | Why | How obtained |
|---|---|---|
| `INTERNET` | DNS resolution display, user's VPN tunnels | install-time |
| `ACCESS_NETWORK_STATE` | dashboard network facts, VPN state | install-time |
| `WRITE_SECURE_SETTINGS` | write Private DNS keys | **ADB grant, one-time** (`settings`-protected signature/system permission — Play Store cannot grant it) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` | OS requirements for an active VPN service | install-time / runtime (13+) |
| `BIND_VPN_SERVICE` | held by the *system*, not the app | manifest service guard |

## Zero-setup Secure DNS (VPN mode) — security notes
- One first-party TLS session (RFC 7858) to the provider selected in a DNS profile; certificate is
  hostname-validated exactly like Android Private DNS (IP literals are rejected by validation).
- Provider hostname is bootstrap-resolved **before** the tunnel opens, so the DoT connection can
  never recurse into the tunnel.
- The TUN routes a single /32 (the DNS address) — no general traffic capture, no filtering, no logs.
- Same-app exclusivity: starting it stops the OpenVPN transport (Android allows one VPN per app).

## Key storage facts
- Profiles: app-private `SharedPreferences` (JSON), excluded from backup *and* device transfer.
- VPN passwords/PSKs: same private storage, never logged, never exported (export file is metadata-only).
- Certificates: stored as normalized PEM inside the private profile store.
- No `android:debuggable`, no `allowBackup` of profile files, no cleartext — all three are
  asserted by CI (`security.yml → hardening`).

## Dependency policy (the "98% first-party" rule)

| Layer | Origin | In APK? |
|---|---|---|
| DNS engine (settings + validation + repos) | **first-party** (`core.dns`) | yes |
| OpenVPN config parser, CA parser | **first-party** (`core.vpn`) | yes |
| TV components, palettes, navigation | **first-party** (`ui.components`) | yes |
| UI plumbing (AndroidX, Material 3, Kotlin stdlib) | Google first-party Android libraries | yes |
| VPN protocol engine (IKEv2/IPsec) | Android OS itself | OS |
| OpenVPN packet engine | pluggable seam (`VpnDataChannel`) — **not bundled** | no |
| Build & test tooling (Gradle, AGP, JUnit, Robolectric, Espresso) | standard OSS DevOps toolchain | no — never ships |

Rationale: a VPN/DNS tool's crypto-critical paths must be either the platform itself (IKEv2) or
code you can read in ten minutes (everything in `core.*`). The OpenVPN data channel is the one
piece where a third-party engine would be justified — it stays behind an interface so an audited
module can be added without changing app code, and nothing unreviewed ships silently.

## CI security gates
1. **CodeQL** (`java-kotlin`, security-extended) on every push/PR
2. **Gitleaks** across full git history
3. **Dependency review** — fails on high-severity advisories, denies GPL-3/AGPL-3
4. **Hardening assertions** — cleartext off, debuggable off, backup exclusions present
5. **Lint** with `abortOnError` (NewApi violations fail the build)

## Reporting
Open a GitHub issue for anything suspicious. For a vulnerability, mark it private via
GitHub's "Report a vulnerability" on the Security tab.
