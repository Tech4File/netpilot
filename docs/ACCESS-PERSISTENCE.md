# Access persistence — TV restarts, adb grants and Shizuku

NetPilot changes the system **Private DNS** setting through a one-time access
grant. This document states exactly what survives a TV power cycle — verified
against Android's documented behavior and public project docs (FindMyDevice,
Shizuku) — and what NetPilot does in every case (v2.2.0 AccessGuard).

## The three pieces, and what survives

| Piece | What it is | Survives power off/on? | Survives app update? | Lost only by |
|---|---|---|---|---|
| **ADB grant** (`pm grant WRITE_SECURE_SETTINGS`) | Package-manager permission record | ✅ Yes | ✅ Yes (same signature) | Uninstall · Clear data · Factory reset |
| **Private DNS setting** (`private_dns_mode` + `private_dns_specifier`) | System secure setting | ✅ Yes | ✅ Yes | Settings change · Factory reset |
| **Shizuku service** | A user-space process started via wireless debugging/adb | ❌ **Dies on every power cycle** | n/a | Any restart (non-root) |

### What this means on a TV

1. **Recommended setup (ADB grant): set once, done forever.**
   After `adb shell pm grant app.netpilot android.permission.WRITE_SECURE_SETTINGS`,
   a TV restart / power off / power on needs **nothing**. The grant is kept by
   the package manager; the Private DNS setting is kept by the OS. NetPilot
   does not need to be opened at all — DNS keeps protecting.

2. **Shizuku setup: one tap after each restart.**
   Shizuku is a process; the system kills it at power-off. After power-on:
   open Shizuku → **Start** (pairing is remembered, so this is one
   interaction). Until then, NetPilot actions that need elevated access
   detect the dead server and say so.

3. **NetPilot's own protection state needs no re-login ever.** There is no
   account, no session, no daemon. When the TV boots, nothing of NetPilot's
   runs until the user opens the app (or the optional boot reconnect fires
   once) — zero-dormancy by design.

## The AccessGuard (implemented v2.2.0)

Every app open, Dashboard evaluates `AccessGuard.evaluate(...)`:

| Situation | Status | What the user sees |
|---|---|---|
| ADB grant present | `READY` | Normal UI |
| No grant, Shizuku up + permitted | `SHIZUKU_READY` | Normal UI |
| Neither, but access worked before (or setup was completed) | `REGRANT` | **The permission guide re-appears automatically** with the "Access lost — re-grant needed" wording (same dialog as first launch, different intro) |
| Brand-new install | `ONBOARD` | First-launch permission guide (unchanged) |

Details that keep this honest:

- The prompt fires **once per app open** — no modal nag loop; the dashboard
  setup card and the (i) button remain the always-visible recovery paths.
- The moment any access path works, `accessHeld` is recorded, so a later
  loss is detected as REGRANT (not silent).
- Emulator-like devices are excluded from the auto-prompt so instrumented
  CI tests start from a clean window (unchanged policy).
- DNS apply failures (`SecurityException` paths in Private DNS screens) also
  route to the same guide — belt and suspenders.

## Recovery commands (unchanged)

```
adb shell pm grant app.netpilot android.permission.WRITE_SECURE_SETTINGS
```

Shizuku: install from the store → pair once → **Start** (repeat after every
restart; "Start on boot" exists only for rooted devices).
