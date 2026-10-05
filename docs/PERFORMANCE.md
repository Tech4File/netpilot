# NetPilot performance & battery model

NetPilot is built so that **"off" means gone**: no services, no listeners,
no polling, no wake locks, no schedulers — nothing runs unless the user
turned a protection ON.

## What runs, when

| Feature | While ON | While OFF |
|---|---|---|
| **Private DNS (strict/automatic)** | *Nothing* — it is a **system setting** (`Settings.Global`); the OS enforces it with zero NetPilot processes. Battery cost ≈ 0 beyond the platform's own DoT. | nothing |
| **Secure DNS (VPN-mode tunnel)** | `SecureDnsVpnService` foreground service + 4-thread query pool (in-memory only) | service stopped (`stopSelf` + foreground removal) |
| **VPN — WireGuard (embedded)** | the engine's VPN service while the tunnel is up | native tunnel closed, service stopped by the engine |
| **VPN — IKEv2 (platform, 11+)** | the **OS daemon** owns the tunnel — no NetPilot process at all | nothing |
| **VPN — OpenVPN (engine bridge)** | the engine app's own service | NetPilot runs nothing |
| **Status notification** | hosted by whichever component is alive (`VpnStatusService` only while DNS-strict or a NetPilot VPN session is active) | service **self-stops** the moment its reason disappears — including when the toggle happens in Android's own settings (in-process `ContentObserver` self-check) |
| **VPN state listener** (`VpnStatusMonitor`) | registered **only while the app UI is in the foreground** (registered in `onResume`, unregistered in `onPause`) | unregistered — queries still work on demand |
| **DNS setting observers** | registered only by visible screens (`onResume` → `onPause`) | unregistered |
| **Quick Settings tile** | bound by the system **only while the shade shows it / it is tapped** | nothing — TileServices are boundaryless by design |
| **JobScheduler / AlarmManager / WorkManager / wake locks** | none anywhere in the app | none |

## Android hibernation compatibility

When every protection is off (or is a pure system setting), the app holds
**no** components: no foreground services, no bound services, no registered
callbacks, no receivers with pending work. The process is immediately
cacheable, so Android 12+ "hibernation"/app-freezing and aggressive battery
management apply to NetPilot exactly as the OS intends — nothing fights it.

## Memory & size

- Screens use ViewBinding + RecyclerView (recycled rows); no image bitmaps —
  all vector drawables.
- The single heavyweight component is the embedded WireGuard engine
  (`wg-go`, loaded lazily on first WireGuard use only); R8 minify +
  resource shrinking are on for releases, and the per-ABI APK ladder keeps
  device downloads at one native ABI each (~17 MB) instead of the
  universal (~25 MB).
- No third-party analytics/ads/Firebase — the dep tree is AndroidX +
  Material + the two disclosed engines (Shizuku, WireGuard).

## Roadmap tiles (Quick Settings)

- Shipped: **NetPilot DNS** tile (one-tap strict DNS toggle; opens the app
  when the grant is missing or no profile is active).
- Planned: **VPN tile** (connect/stop last-used tunnel) once the embedded
  OpenVPN core lands — the tile itself is trivial; the work is the engine.

## v2.2.0 additions

- `BootReconnectReceiver`: fires once per power-on, only if the user enabled
  "Reconnect VPN after restart" (default OFF) and a tunnel was actually up at
  shutdown. One decision, at most one connect or one notification, then exit —
  no scheduler, no retry, no listeners. Zero-dormancy holds.
- Both QS tiles remain boundaryless (bound only while the shade is visible).
- App shortcuts are static XML — the system resolves them from the manifest,
  nothing runs.
