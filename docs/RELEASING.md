# Releasing MImic

## Mandatory R8 + BLA path

See [BLA integration](security/BLA_INTEGRATION.md) and AGENTS.md. The release build
now produces R8-optimized **unsigned, BLA-processed** APKs through AGP's artifact
transform. `scripts/release.sh` aligns/verifies and signs only those outputs with
the existing key. Do not copy raw APKs out of `app/build/outputs/apk`: they are
private R8-only intermediates. Never publish the comparison APK or R8 mappings.

Install Node 24.19.0 and Python, with Android SDK build-tools 36.0.0. BLA 3.6.1 and
all npm dependencies are locked; Gradle runs npm ci --ignore-scripts. Failed
protection, integrity, signing, Android regression or emulator checks block release.
Debug builds remain ordinary debug builds. Do not add skip-BLA switches.

Keep an existing previous signed MImic release on the emulator. The local release
script runs direct Android regression tests with a temporary saved/restored
snapshot, then final signed APK update, navigation and local/background/lock-screen
playback checks. It never uninstalls or clears data. `MIMIC_SMOKE_TRACK` defaults
to the existing local `Artwork Red` fixture. Same-version repeats need an exact
matching upgrade receipt; otherwise deliberately restore the prior-release baseline
snapshot before retrying. Do not downgrade or claim an upgrade that did not happen.

Release assets additionally include protection-report.json and
emulator-verification.json. These identify the tested APK hashes and the actual
limited BLA mode. No extra JADX resistance beyond R8 is demonstrated. After
publication, test the actual Beta updater on the saved previous-release snapshot.

For recovery upgrade evidence, deliberately prepare an internal lower-version
emulator fixture using `python tools/bla/verify_recovery.py --prepare-upgrade`
before installing the final APK. Save an owner-state snapshot first. The release
gate checks hidden-theme persistence against that fixture, then uses its own
temporary snapshot for final-APK removal/last-theme/offline restore/reset/reboot
and same-version reinstall tests. It restores that snapshot and never uninstalls.
Live official GitHub repair is checked after publication; do not expose a draft
or untested APK just to make it appear in the updater. See THEME_RECOVERY.md.

The rules below still apply; signing now occurs after mandatory BLA processing.

Releases are GitHub Releases of <https://github.com/Eno-Dev-Tech/MImic>. The app's
Settings → App Updates reads them, so **every release must follow these rules** or phones won't
see (or won't accept) it.

## Rules

1. **Version** — change only `versionName` in `app/build.gradle.kts`. `versionCode` is derived:
   `X.Y.Z` → `(X·1 000 000 + Y·1 000 + Z)·100 + 99`; pre-releases `X.Y.Z-alpha.N | -beta.N | -rc.N`
   get `N`, `33+N`, `66+N` instead of 99. Every release therefore has a unique, increasing code.
   Never reuse or lower a version.
2. **Channel** — a version with a suffix (`4.1.0-beta.1`) is published as a GitHub *pre-release*
   and is only offered to the Beta channel. A plain version (`4.1.0`) is Stable.
3. **Changelog** — `CHANGELOG.md` must have a `## <versionName>` section; it becomes the release
   notes shown in the app.
4. **Signing** — only with MImic's release key (the certificate pinned as
   `RELEASE_CERT_SHA256`, SHA-256 `C0:3C:51:07:…:4F:88:08:7D`). Never generate a replacement key:
   phones would refuse the update. The key is the maintainer's debug keystore
   (`~/.android/debug.keystore`, backup in `MImic-backups/`), referenced by the gitignored
   `keystore.properties`.
5. **Assets** (created by the script):
   `MImic-vX.Y.Z-full.apk` (universal), `MImic-vX.Y.Z-full-arm64-v8a.apk`,
   `…-full-armeabi-v7a.apk`, `…-full-x86_64.apk`, `MImic-vX.Y.Z-standard.apk`, `SHA256SUMS.txt`.

## Steps

```sh
# 1. bump versionName, add the CHANGELOG section, commit (and push to main: CI runs the tests)
scripts/release.sh             # dry run: tests, lint, signed build, verification → dist/
scripts/release.sh --publish   # same, then tag vX.Y.Z, push, create the GitHub Release, verify downloads
scripts/release.sh --publish --staged # reuse completed build/tests; require matching final source/APK emulator receipt, recheck hashes/artifacts and security
```

The script refuses to publish when: tests or lint fail, `keystore.properties` is missing, any APK
isn't signed with the pinned certificate, an APK's package/versionCode/versionName is wrong, the
tree has uncommitted changes, or the tag/release already exists. After publishing it checks that
every asset is downloadable.

Then on a phone: Settings → App Updates → **Check for Updates** → Download Update → Install
Update (Android asks to confirm). Data is kept; no uninstall is ever needed.

## Why not sign in GitHub Actions?

Decision (2026-10-19): the signing key stays on the maintainer's machine. CI (`.github/workflows/ci.yml`)
runs unit tests, lint and a debug build on every push; publishing is a local, deliberate step.
To move signing into Actions later, add the keystore as encrypted secrets and a tag-triggered job
— never commit the key.
