# NetPilot Architecture

```
┌──────────────────────────── MainActivity (single host) ────────────────────────────┐
│  TV: NavRailView (left rail)                    Phone/Tablet: BottomNavigationView │
│                                                                                     │
│  ┌ DashboardFragment ┐  ┌ PrivateDnsFragment ┐  ┌ VpnFragment ┐  ┌ SettingsFragment┐│
│  │ hero status       │  │ master switch     │  │ master switch│  │ theme          ││
│  │ quick cards       │  │ mode segments     │  │ profile list │  │ security       ││
│  │ setup card        │  │ profile list      │  │ add / import │  │ export/import  ││
│  │ advisory card     │  │ add / edit        │  │ connect/stop │  │ privacy        ││
│  │ network facts     │  │ delete            │  │              │  │ licenses/about ││
│  └───────────────────┘  └───────────────────┘  └──────────────┘  └────────────────┘│
└─────────────────────────────────────────────────────────────────────────────────────┘
            │                                               │
            ▼                                               ▼
┌── core.dns ──────────────────┐              ┌── core.vpn ─────────────────────┐
│ PrivateDnsManager            │              │ PlatformVpnController (IKEv2)   │
│  Settings.Global r/w + obs   │              │ NetPilotVpnService (OpenVPN)    │
│ HostnameValidator            │              │ OvpnConfigParser (first-party)  │
│ SecureDnsVpnService (DoT tunnel)│
│ PacketOps · DnsMessage · DotClient (first-party) │
│ DnsProfileRepository         │              │ CaCertParser (first-party)      │
└──────────┬───────────────────┘              │ VpnProfileRepository            │
           │                                  │ VpnStatusMonitor (callback)     │
           │                                  └──────────┬──────────────────────┘
           ▼                                             ▼
┌── core.model ─────┐   ┌── core.profiles ──────┐   ┌── core.network ──────────┐
│ DnsMode/Profile   │   │ ProfileBackup (SAF)   │   │ NetworkInfoProvider      │
│ VpnType/Profile   │   └───────────────────────┘   └──────────────────────────┘
│ ThemeMode         │
└───────────────────┘   ┌── core.prefs ──┐   ┌── core.ui ─┐   ┌── ui.components ────┐
                        │ AppPreferences │   │ TvUi       │   │ FocusCardView       │
                        └────────────────┘   └────────────┘   │ NavRailView,        │
                                                              │ StatusPill,         │
                                                              │ SegmentedToggle,    │
                                                              │ SettingRow,         │
                                                              │ EmptyStateView      │
                                                              └─────────────────────┘
```

## Key decisions

### 1. Private DNS via Settings.Global
The TV Settings UI is missing on most devices; the framework keys are not.
`PrivateDnsManager` reads/writes `private_dns_mode` + `private_dns_specifier` behind a
`WRITE_SECURE_SETTINGS` grant (ADB, once, root-free) and exposes a `ContentObserver` so all
screens re-render when anything (including the OS or another app) changes DNS.

**Single-active invariant:** the system stores exactly one specifier. The repository stores the
"active profile id" separately; every activation path funnels through
`setActive(id) → applyProfile(hostname)`, so activating profile B can never leave A active.
Covered by unit tests (`DnsProfileRepositoryTest`, `PrivateDnsManagerTest`) and, on emulators
with the grant, by instrumented tests against the real Settings table.

### 2. VPN: platform-first, engine-seamed
- **IKEv2/IPsec (API 30+)**: `VpnManager` + `Ikev2VpnProfile`. The OS runs the protocol, shows the
  standard consent dialog and the key-icon connection UI. Zero third-party code. Note: the platform
  API requires a CA certificate for username/password (EAP) auth — PSK needs none; the UI and
  `CaCertParser` (first-party PEM/DER parser) handle cert import.
- **OpenVPN**: parsing/validation/UX are first-party. The packet engine sits behind the
  `VpnDataChannel` interface; the shipped APK explains rather than fakes a connection when no
  engine module is present (deliberate security posture — see docs/SECURITY.md).
- **One VPN system-wide** is an Android guarantee; the UI treats "connect" as "replace".
- **Secure DNS VPN mode** (`SecureDnsVpnService`): /32 capture TUN + first-party `DnsMessage`
  codec, `DotClient` (RFC 7858, hostname-validated TLS) and `PacketOps` (checksummed IPv4/UDP
  replies). Zero ADB — the protection path for users without a computer.

### 3. One activity, two navigation shells
`MainActivity` detects TV (`FEATURE_LEANBACK` / `values-television`) and shows either the left
`NavRailView` or the Material bottom bar. Fragments are shared; back behavior (Back → Dashboard →
exit; Back focuses the rail on TV) is centralized in one `OnBackPressedCallback`.

### 4. State flows are event-driven
- DNS: `ContentObserver` on the two settings keys
- VPN: single `NetworkCallback` filtered to `TRANSPORT_VPN`
- No polling loops, no services at idle, no receivers at rest → negligible battery impact.
  The only long-lived process is the OS-managed VPN itself, when the user turns it on.

### 5. First-party share (the 98% rule)
Every line under `core.*` and `ui.components` — settings access, validators, JSON repositories,
.ovpn parser, X.509 helper, TV components — is written in this repo. Third-party code is limited
to Google's own AndroidX/Material/Kotlin (UI plumbing any Android app needs) and is documented
in-app. See docs/SECURITY.md for the exact table.

### 6. Storage & privacy
Profiles live in two `SharedPreferences` files (`netpilot_profiles`, `netpilot_settings`) as JSON,
both **excluded from platform backup/transfer** via `backup_rules.xml` + `data_extraction_rules.xml`.
Export/import goes through the Storage Access Framework; VPN secrets are never exported.
