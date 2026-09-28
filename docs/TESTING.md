# NetPilot Testing Strategy

Bugs are caught **before** any APK is built. The CI order is: static analysis → unit tests →
lint → build → instrumented tests on emulators.

## The pyramid

### 1. JVM unit tests — `./gradlew :app:testDebugUnitTest` (75 tests)
Pure logic, milliseconds-fast, run on every compile in CI *before* packaging:

| Suite | Covers |
|---|---|
| `HostnameValidatorTest` | RFC-style hostname rules: IPs rejected, labels ≤63, length ≤253, normalization, schemes/port garbage |
| `DnsModeTest` | `off/opportunistic/hostname` mapping, fallbacks, encryption-state flag |
| `OvpnConfigParserTest` | directive parsing, inline PEM blocks, comments, multi-remote, garbage input |
| `DnsMessageTest` | RFC 1035 wire format: query bytes golden, QNAME encode/parse, **compression-pointer cycles rejected**, SERVFAIL synthesis |
| `DotFramingTest` | RFC 7766 two-byte DoT framing: round-trip, EOF, implausible lengths, truncation |
| `PacketOpsTest` | RFC 1071 checksum (worked example + self-verify), DNS query parse, reply build with swapped addresses/ports, fragmented/malformed packet rejection |
| `DnsProfileRepositoryTest` | CRUD, **single-active invariant**, active-deleted edge cases, export/import dedupe, corrupt-storage survival |
| `VpnProfileRepositoryTest` | CRUD, config round-trip, last-connected tracking, **secrets never exported** |
| `PrivateDnsManagerTest` (Robolectric) | permission gating, strict/automatic/off writes, replacement = single-active |
| `ProfileBackupTest` | bundle round-trip, dedupe, secret-free exports |
| `MainActivityRoboTest` (Robolectric) | activity launch, tab switching, **profile dialog validation E2E on the JVM** (rejects IP, accepts + normalizes valid host) |
| `ThemeModeTest`, `LicenseCatalogTest` | prefs enum, attribution integrity |
| `UiCompatibilityTest` | **form-factor matrix**: classic TV 960x540dp canvas (rail shown, bottom bar hidden), 4K TV 1920x1080dp canvas (overscan-safe paddings), smallest phone 360x640dp (all screens render & navigate) |

Robolectric simulates Android (SDK 33) on the JVM: real `Settings.Global` shadow storage, real
dialog inflation — no emulator needed.

### 2. Static gates
- `lintDebug` with `abortOnError=true` (NewApi, hardcoded text, missing density, …)
- CodeQL security analysis (security-extended)
- Gitleaks + dependency review + hardening assertions (see docs/SECURITY.md)

### 3. Instrumented tests — emulator (API 29 + API 34 matrix)
`./gradlew connectedDebugAndroidTest` via `android-emulator-runner` (matrix: **API 28, 29, 34, 35**):

| Suite | Covers |
|---|---|
| `AppLaunchTest` | launch, tab navigation (Espresso clicks), **D-pad focus walking** (`KEYCODE_DPAD_DOWN` moves focus between quick cards), rail/bottom-bar visibility per device type |
| `DnsSystemApplyTest` | repository → `Settings.Global` end-to-end with the real permission (CI grants it over adb on the API-34 leg): strict write, provider replacement, disable, ContentObserver notification |
| `VpnProfileStoreTest` | profile CRUD on-device, `PlatformVpnController.isSupported()` gate vs runtime API level, realistic .ovpn parse |

The API-29 leg runs **without** the permission to prove the degraded UX path (view + setup guide,
no crashes); the API-34 leg runs **with** the grant to prove real system writes.

### 4. Local runs
```bash
./gradlew :app:testDebugUnitTest            # JVM + Robolectric
./gradlew :app:lintDebug                    # static gates
./gradlew connectedDebugAndroidTest         # emulator/device required
```

Test reports land in `app/build/reports/` and are uploaded as CI artifacts on every run.
