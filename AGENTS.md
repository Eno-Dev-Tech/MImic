# MImic development and production releases

Continue the existing Android app; preserve Felt Classic, Felt Orange, all six
navigation motions, existing themes, playback, Navidrome, downloads, lyrics,
radio, accounts, preferences, library data and the GitHub updater.
Read CLAUDE.md and docs/RELEASING.md before release work. Never discard local work.

## Mandatory production protection

- Every future production APK must run R8 optimization/obfuscation, the pinned
  BLA DEX transform, independent DEX checks, resource/native preservation checks,
  zipalign, original-key signing and final APK verification. This applies to
  themes, features, fixes and all later updates.
- BLA is pinned to **3.6.1** by tools/bla/package.json and package-lock.json.
  Use `npm ci --ignore-scripts`. Never use `@latest`, automatic upgrades, or
  automatic source weaving. BLA upgrades require review and fresh final-APK tests.
- No agent may disable, exclude, bypass or catch-and-ignore BLA verification to
  make a production build succeed. A protection/verification failure blocks
  production release. Debug builds intentionally remain ordinary debug builds.
- The validated mode is only the stealth DEX text trailer. It does not encrypt
  methods or stop JADX; report that limitation. Do not treat marker strings,
  BLA defense scores or modeled token costs as proof of effective protection.
- Keep aggressive/unverified archive, JNI, manifest, source-weaving, runtime
  traps and exhaustion options off. Never run whole-tree BLA harden/apply here.
- Source remains readable. Private R8 comparisons and mappings stay under build
  outputs; never publish them. Never publish an unprotected comparison APK.
- The release script stages only verified BLA outputs. Do not manually upload
  APKs from app/build/outputs/apk or build/bla/private-comparison.

## Signing, upgrades and testing

- Package stays `com.example.mimic`. Signing stays the existing local key with
  SHA-256 `C03C51075A49A049387185C775626074368AA9F4220EB5BA0E74CC4A4F88087D`.
  Never create a replacement key, print passwords, or upload signing configuration.
- Let versionCode derive from versionName. Never reuse or lower a published version.
- Use the existing emulator and terminal/ADB. Never open Android Studio unless
  requested. Never uninstall, clear data or downgrade the user's installation.
- Do not run Gradle connected Android tests against the owner's installation:
  its automatic cleanup uninstalls the target. Install test APKs with `adb install -r`
  and invoke instrumentation directly, or use an isolated disposable emulator.
- Final signed APK hashes must match the emulator receipt. Test update from the
  previous signed release with firstInstallTime and preferences preserved, local
  audio, background/lock-screen playback, media notification, navigation and startup.
- If repeated validation needs the previous version, use the deliberately saved
  emulator baseline snapshot; never silently fake upgrade evidence or downgrade.
- Publish only with scripts/release.sh --publish after every required check passes.
  Preserve repository visibility. Verify public downloads, signer/checksum and OTA.
- A blocker means no new release. Report exact errors and tests not performed;
  never call an unverified or unprotected APK a successful BLA release.

See docs/security/BLA_INTEGRATION.md for implementation and measurement details.

## Permanent theme recovery and repair contract

- Every catalog theme, including future presets and custom themes, must support the
  accessible 48dp minus control and confirmation. Store removals as stable IDs in
  local DataStore; never delete bundled resources or custom-theme records. New IDs
  appear by default. Prevent removal of the final visible theme and atomically
  select a visible fallback when removing the active theme.
- Settings > App Updates > Recovery & Repair must retain offline theme restoration,
  explicit repair/reinstall and safe settings reset. Restoration preserves selection.
  Reset uses an explicit preference allowlist, pauses playback and cancels its sleep
  timer, and preserves accounts, server credentials, history, custom themes, queue,
  playlists, favorites, downloads and files. Never clear Android app data or whole
  repositories to implement reset. No cache deletion is currently needed.
- Only explicit repair permits an equal versionCode. Normal updates stay strictly
  newer; every path refuses downgrade. Use official HTTPS GitHub releases, compatible
  edition/ABI/minSdk, release checksum and BLA report, embedded DEX receipt, exact
  package/version and the original pinned/installed certificate. Reverify cached
  APKs before install/retry. Android must request confirmation; never uninstall.
- Preserve removal preferences across restart, reboot, reinstall and upgrades.
  Validate these behaviors and capture gallery/recovery screenshots on the final
  protected APK. Interrupted transfers must leave no installable partial APK.
