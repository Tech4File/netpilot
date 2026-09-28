# Release Runbook

## One-time setup (repo maintainer)

1. **Create a release keystore** (keep it private, back it up — losing it ends updateability):
   ```bash
   keytool -genkeypair -v -keystore netpilot-release.jks -storetype PKCS12 \
     -alias netpilot -keyalg RSA -keysize 4096 -validity 10000
   ```
2. **Add repository secrets** (Settings → Secrets and variables → Actions):
   | Secret | Value |
   |---|---|
   | `ANDROID_KEYSTORE_B64` | `base64 -w0 netpilot-release.jks` |
   | `ANDROID_KEYSTORE_PASSWORD` | keystore password |
   | `ANDROID_KEY_ALIAS` | `netpilot` |
   | `ANDROID_KEY_PASSWORD` | key password |

## Cutting a release

```bash
# 1. Ensure main is green (CI + Security workflows)
# 2. Bump versionCode/versionName in app/build.gradle.kts
# 3. Tag and push
git tag -a v1.0.0 -m "NetPilot 1.0.0"
git push origin main --tags
```

The **Release** workflow then:
1. Runs the verification gate (all unit tests + lint) — failures abort everything
2. Decodes the keystore from secrets to `.ci-release-keystore.jks` (git-ignored)
3. Builds `assembleRelease` + `bundleRelease` with R8 and resource shrinking
4. Publishes a GitHub Release with the signed APK, AAB, and SHA-256 checksums
5. Changelog is generated from commit history since the previous tag

**Fail-safe:** if any signing secret is missing, the workflow still produces an
*unsigned* artifact for testing but **refuses to publish a release**.

## Verifying artifacts
```bash
sha256sum -c checksums-sha256.txt
apksigner verify --print-certs app-release.apk        # signer fingerprint
aapt2 dump badging app-release.apk | head             # package/version/SDKs
```

## Install on Android TV
```bash
adb connect <TV_IP>:5555
adb install -r app-release.apk
adb shell pm grant app.netpilot android.permission.WRITE_SECURE_SETTINGS
```
