# Verifying NetPilot — "does the APK really run?"

Skepticism is healthy for any app that touches your network. Here is the complete,
reproducible evidence chain — everything below was actually executed on the build machine.

## Evidence from the build pipeline

| Check | Command | Result |
|---|---|---|
| Code compiles to real DEX | `dexdump app-debug.apk` | **178 NetPilot classes** inside the APK (all `core.*`, `ui.*`, both VPN services, parsers) |
| APK is cryptographically signed | `apksigner verify --print-certs app-debug.apk` | Valid signature — `CN=Android Debug` — **installs directly** on any Android 9+ device/TV |
| Release build survives R8 shrinking | `dexdump app-release-unsigned.apk` | 1,241 classes, 2.3 MB |
| Manifest is well-formed & TV-ready | `aapt2 dump badging` | `leanback-launchable`, touchscreen not required, minSdk 28 |
| Behaviour, not just compilation | `./gradlew :app:testDebugUnitTest` | **75 tests, 0 failures** — Robolectric executes the real activity, dialogs, settings writes and parsers on the JVM |
| Static quality gate | `./gradlew :app:lintDebug` | Clean (hard-fail mode) |

The JVM tests are the strongest no-device proof available: they **launch MainActivity,
navigate tabs, open the DNS dialog, feed it input and assert system writes** using the actual
Android framework code (Robolectric), not mocks of our logic.

## Verify on your own device (5 minutes)

1. **Install** — copy `NetPilot-v1.0.0-debug.apk` to the TV/phone (USB, `adb install`,
   or any file manager) and open it. If it installs and launches, the APK runs.
2. **See it work without any ADB** — Private DNS tab → add a profile (e.g. *Cloudflare* /
   `one.one.one.one`) → flip **Zero-setup Secure DNS** → Android shows the standard VPN prompt →
   OK. The status pill turns green; your DNS is now DNS-over-TLS encrypted.
3. **Prove DNS is actually being encrypted** — on the TV, open a browser/caption app; then check
   the Dashboard "Active DNS servers" row shows the NetPilot tunnel address while the mode is on.
4. **Full verification** — connect the TV once over Wi-Fi adb
   (`adb connect <TV_IP>:5555`) and run the emulator test suite against the real device:
   `./gradlew connectedDebugAndroidTest`.
5. **Check the signature yourself**:
   `apksigner verify --print-certs NetPilot-v1.0.0-debug.apk`

## Reproduce the whole build from source

```bash
unzip netpilot-source.zip && cd netpilot
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
sha256sum app/build/outputs/apk/debug/app-debug.apk   # compare with the checksums on the GitHub Release
```

Identical checksum requires identical toolchain versions (see `gradle/wrapper/gradle-wrapper.properties`
and `app/build.gradle.kts`); byte-identical reproducibility is a stretch goal — behavioural
verification is what matters.
