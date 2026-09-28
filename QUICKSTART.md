# ⚡ Quickstart — unzip → run → push

## 0. What's in the package
`netpilot-source.zip` is **the complete project** — it already includes everything:
source code, all docs, banners/icons, CI workflows, **and the prebuilt APKs** in `releases/`.
`netpilot-apks.zip` is just the installable APKs extracted for convenience.

## 1. Unzip & build locally (Linux / macOS / WSL)

```bash
unzip netpilot-source.zip && cd netpilot
chmod +x gradlew                 # ← important once, after unzipping (zip can drop the +x bit)
./gradlew :app:testDebugUnitTest # 71 tests — expect "BUILD SUCCESSFUL"
./gradlew :app:assembleDebug     # → app/build/outputs/apk/debug/app-debug.apk
```
Requirements: JDK 17 only. Android SDK is fetched automatically by Gradle on first run
(accept licenses once with `./gradlew --refresh-keys` not needed — the build prompts,
or pre-accept: `yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses`).

Windows: use `gradlew.bat` instead of `./gradlew`.

## 2. Push to GitHub (open source)

```bash
cd netpilot
git init -b main
git add -A
git update-index --chmod=+x gradlew      # preserve the executable bit in git
git commit -m "NetPilot 1.1.0 — Private DNS & VPN for Android TV"
git remote add origin https://github.com/<you>/netpilot.git
git push -u origin main
```

The moment you push, GitHub Actions runs automatically (free on public repos):
- **CI**: unit tests → Lint → APK build → **instrumented tests on emulators for Android 9/10, 14 and 15**
  (the Android 14 & 15 legs grant the secure-settings permission over adb and exercise real system writes)
- **Security**: secret scanning, dependency review, hardening assertions, CodeQL
- **Release**: push a tag (`git tag v1.1.0 && git push --tags`) → verification gate → signed APK + AAB + checksums
  (add the 4 keystore secrets from `docs/RELEASE.md` for signed releases; without them you get unsigned artifacts only)

## 3. The APKs users can install today
| File in `releases/` | Use |
|---|---|
| `NetPilot-v1.1.0-debug.apk` | **Installable right now** (debug-signed). Sideload on TV/phone |
| `NetPilot-v1.1.0-release-unsigned.apk` | For testing; must be signed with your keystore before distribution |
| `NetPilot-v1.1.0-release.aab` | Play Store upload (Play delivers per-device automatically) |

**Architecture note:** NetPilot contains **zero native (C/C++) code** — one APK runs on
*every* device architecture (arm64-v8a, armeabi-v7a, x86_64, x86, RISC-V) and every density.
There is nothing to "pick the right build" — universal by construction.

## 4. Verify nothing is missing
`PROJECT_MANIFEST.txt` lists every tracked file. After unzipping:
```bash
diff <(unzip -l netpilot-source.zip) PROJECT_MANIFEST.txt  # or just count:
find . -type f | wc -l
```
