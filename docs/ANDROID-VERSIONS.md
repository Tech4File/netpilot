# Android version compatibility — Android 9 → latest, and the next release

Policy: **one APK, Android 9 (API 28) floor, every feature carries its own
API gate.** Nothing is enabled that the device cannot run; nothing forces a
newer device to use an old path. New Android releases must be adoptable
without breaking older devices — the checklist below is the contract.

## Central gates

`core/platform/Sdk.kt` holds the named constants (`Sdk.P … Sdk.CURRENT_COMPILE`).
New code gates through it, so scanning for API-conditional behavior is one
`grep -r "Sdk\." app/src`. Lint's `NewApi` check is the second net.

## Feature → API floor map

| Feature | Floor | Gate used |
|---|---|---|
| Everything | API 28 (Android 9) | minSdk |
| Tile subtitle | 29 | runtime check in both tile services |
| Platform IKEv2/IPsec VPN | 30 (needs Android 11 APIs) | `VpnType.defaultFor(SDK_INT)` + hints |
| POST_NOTIFICATIONS runtime prompt | 33 | request-once code |
| Tile `startActivityAndCollapse(PendingIntent)` | 34 | runtime check (Intent fallback 28–33) |
| Edge-to-edge enforcement | 35 | handled (window insets via `applyWindowInsets`) |
| Embedded WireGuard engine | 28+ (in-process, no OS VPN API version deps) | — |

## What already survives restarts/updates (no action needed)

- Same-signature install-over keeps profiles, prefs, the WRITE_SECURE_SETTINGS
  grant and the VPN consent — verified in the field (v2.0.8 → v2.0.9 → …).
- Cross-signature install is rejected by Android ("App not installed") —
  release-signed updates only; debug builds are device-test only.

## New-Android-release checklist (do this every year)

1. **Bump compileSdk** (and targetSdk after a behavior audit) in
   `app/build.gradle.kts`; add the new constant to `core/platform/Sdk.kt`.
2. **Read the platform behavior changes** for the new API level and check
   each item against this list of our touchpoints:
   - *Foreground services*: our only FGS is the VPN path (exempt VPN class) +
     `specialUse` type for the secure-DNS tunnel.
   - *Tile APIs*: both TileServices are boundaryless; if the bind contract
     changes, adapt in `core/tiles/` only.
   - *Package visibility*: `<queries>` blocks cover Shizuku, engine apps and
     launchable apps (per-app VPN picker).
   - *Settings writes*: WRITE_SECURE_SETTINGS grant model unchanged since 28;
     if a new Android splits Private DNS into another surface, the change is
     confined to `core/dns/PrivateDnsManager.kt`.
   - *Back gesture/predictive back*: we use OnBackPressedCallback (opt-in
     ready; no forced migration).
3. **Run the local gate** (compile → unit → lint → assembleDebug →
   androidTest compile → minified release) — the same commands as CI, so CI
   minutes are only spent on a tree that already passed.
4. **Release ladder unchanged**: version bump in source triggers the Release
   workflow; instrumented API-29 gate runs before publish.
5. **No API-level `when` without a default**: every version branch has an
   else/fallback so an unknown future API value degrades gracefully.

## Why this keeps old devices safe

Every gate is `>=`: raising compileSdk never enables a code path on devices
below its floor, and fallbacks target the oldest behavior. A new Android
version can only ADD a branch, never rewrite one for old devices.
