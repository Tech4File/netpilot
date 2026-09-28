# Release Runbook

Releases are **fully automated** — no local git, no tag commands needed.

## Cutting a release (2 minutes, from the browser)

1. Make sure `main` is green (CI + Security workflows on the latest commit).
2. GitHub → **Actions → Release → Run workflow**.
3. Choose the **version bump**:
   | Choice | From `1.1.2` | Use when |
   |---|---|---|
   | `auto-patch` (default) | `1.1.3` | bug fixes only |
   | `auto-minor` | `1.2.0` | new features, backwards-compatible |
   | `auto-major` | `2.0.0` | big changes / breaking UI or behaviour |
   | `manual` | whatever you type (e.g. `2.0.1`) | special cases |
4. Click **Run**. The workflow then, automatically:
   - runs the verification gate (all unit tests + lint) — failures abort everything;
   - reads the **latest release tag** and derives the next version (never allows a
     version that is not strictly newer — it refuses and fails loudly);
   - bumps `versionName` + increments `versionCode` in `app/build.gradle.kts`,
     commits and pushes that to the default branch (`[skip ci]`);
   - creates and pushes the `vX.Y.Z` tag;
   - builds the **release APK and AAB**, generates **SHA-256 checksums** and a
     **source zip**;
   - **publishes the GitHub Release** with all files attached (signed when
     secrets exist, otherwise clearly labelled *UNSIGNED*).

A tag push (`git tag v1.2.0 && git push origin v1.2.0`) still works and does
exactly the same thing.

## Where the version is visible after installing

The APK is built *after* the version bump, so the installed app shows exactly
the release version:
- left navigation rail footer: `v1.2.0`
- **Settings → About**: app name, version name and version code
- Android system: Settings → Apps → NetPilot → version

`versionCode` increments with every release so Android treats each new APK as
an in-place update (no uninstall needed).

## One-time signing setup (2 clicks — no computer tools needed)

Without secrets the workflow still publishes releases (unsigned APK/AAB,
labelled as such). To sign, run the bootstrap workflow **once**:

1. GitHub → **Actions → "Signing setup (one-time)" → Run workflow → Run**.
   It generates a keystore + a strong random password on GitHub's runner and
   writes all four secrets automatically (`ANDROID_KEYSTORE_B64`,
   `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`).
   The password is masked in every log — you never need to know it.
2. Download the `netpilot-release-keystore-BACKUP` artifact from that run,
   store it safely (USB + private cloud), then delete the artifact.
   *If the repository is ever deleted, its secrets die with it — the backup
   keystore is the only way to keep shipping updates that install over the
   released builds.*
3. Now every **Release** run signs automatically (label flips to
   "✅ Signed release build").

Manual alternative (if you prefer your own key): create a keystore with
`keytool -genkeypair -v -keystore netpilot-release.jks -storetype PKCS12
-alias netpilot -keyalg RSA -keysize 4096 -validity 10000` and add the four
secrets by hand (Settings → Secrets and variables → Actions), then delete
`.github/workflows/signing-setup.yml`.

> ⚠️ Android signature rule: a signed APK cannot install **over** an
> unsigned/debug-signed one — uninstall the old app first when switching.
> Afterwards keep the keystore forever: updates must always carry the same
> signature.

## Repository settings worth checking (once)

- Settings → Actions → General → **Workflow permissions**: allow
  *Read and write permissions* (the Release workflow pushes the version bump
  commit and the tag; the workflow also declares this itself, but if the
  repo forces read-only, publishing fails with 403).
- Nothing else is required — `GITHUB_TOKEN` is used automatically.
