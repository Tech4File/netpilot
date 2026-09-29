# ⚡ Quickstart — unzip → run → push

## 0. What's in the package
`netpilot-source.zip` is **the complete project** — source code, all docs,
banners/icons and the CI workflows. Installable APKs are **not** stored in the
repo (binaries never belong in git): every push to `main` builds and publishes
them automatically to **GitHub Releases** — see section 3.

## 1. Unzip & build locally (Linux / macOS / WSL)

```bash
unzip netpilot-source.zip && cd netpilot
chmod +x gradlew                 # ← important once, after unzipping (zip can drop the +x bit)
./gradlew :app:testDebugUnitTest # 75 tests — expect "BUILD SUCCESSFUL"
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
- **Release**: fully automatic — every push to `main` publishes the version set in
  `app/build.gradle.kts` to GitHub Releases (Actions → Release → Run workflow also
  offers patch/minor/major bumps). Add the 4 keystore secrets (or run the
  "Signing setup (one-time)" workflow) for signed releases.

## 3. What a release contains (GitHub Releases page)
| File | Use |
|---|---|
| `NetPilot-vX.Y.Z.apk` | **Universal** — installs directly on TV/phone/tablet (RECOMMENDED) |
| `NetPilot-vX.Y.Z.apks` | Split set: auto-detects the device on install (bundletool/SAI) |
| `NetPilot-vX.Y.Z.apk.zip` | Fallback: all APKs + INSTALL-GUIDE.txt in one zip |
| `NetPilot-vX.Y.Z.aab` | Play Store upload (Play delivers per-device automatically) |
| `checksums-sha256.txt` | SHA-256 of every file above |

**Architecture note:** NetPilot contains **zero native (C/C++) code** — one APK runs on
*every* device architecture (arm64-v8a, armeabi-v7a, x86, x86_64) and every density.
There is nothing to "pick the right build" — universal by construction.

## 4. Verify nothing is missing
The git tree is the manifest (no stale file lists). After unzipping:
```bash
git ls-files | wc -l        # expected file count (see netpilot-verify.txt)
unzip -Z1 netpilot-source.zip | grep -v '/$' | wc -l   # must match
```
