# Development progress — 2026-11-27

## Verified starting state
- Clean `security/bla-integration` at `785af7b`; no Cline changes found.
- Version is `4.8.1-beta.3` (400800136). GitHub currently lists beta.1 as the newest prerelease; beta.3 is unpublished. Repository remains PUBLIC.
- Existing source implements Felt themes, six navigation styles, stable-ID theme hiding, recovery and pinned BLA 3.6.1 release processing. Runtime verification is pending this session.
- Existing emulator has beta.3 with firstInstallTime `2026-10-17 19:29:42`. Previous-version baseline snapshots remain available.

## Current task
2026-11-28 continuation: rebuild and repeat the complete gate after resolving post-reboot input targeting and strengthening installed-byte verification.

## Remaining tasks
- Fresh unit, instrumentation, lint and protected release build checks.
- Final signed APK recovery, playback, navigation, restart/reboot/reinstall and upgrade evidence; screenshots and accessibility captures.
- Review Theme Studio recovery access and preference behavior.
- Security gate and private comparison measurements.
- Update development report, review and commit completed changes.
- Present readiness evidence before approval; no push or publication authorized yet.
- Live official beta.3 repair/OTA tests require publication and are pending approval.

## Implementation milestone
- Theme Studio More menu: Restore All Themes (selection preserved), confirmed Reset Appearance Settings, Recovery & Repair shortcut.
- Theme Studio now shares the existing navigation picker and interactive preview with Home & Navigation. Theme and navigation choices remain independent.
- Appearance reset restores hidden/default themes and appearance keys in one DataStore edit; retains other settings. ViewModel pauses playback and cancels the sleep timer.
- Added isolated DataStore regression for a hidden default theme and preservation of audio, Home visibility, history and unknown personal keys.
- Final APK recovery runner now exercises the new menu and navigation previews; saves per-run fresh PNG/XML captures, including failures.
- Final-APK failure retained in `build/bla/resume-final-release-dry-run.log` and `build/bla/screenshots/20261127-205607-79381acd/`: Sliding Pill was visually selected after restart, but the flat chip did not expose `selected` to accessibility. Added RadioButton role/selected semantics and an isolated Compose regression; kept the release assertion intact.
- The same fresh screenshot showed dark system-bar icons on dark Felt Orange. System-bar icon contrast now follows every active theme's light/dark setting.
- Saved current owner emulator snapshot `mimic_resume_owner_20261127`, then deliberately restored `mimic_hidden_beta2_20261127`; verified beta.2 / 400800135 and original firstInstallTime. No APK downgrade/uninstall/data clear.

## Modified files
- `DEVELOPMENT_PROGRESS.md` (created).
- `app/src/main/java/com/example/mimic/{data/SettingsRepository.kt,player/PlayerViewModel.kt,ui/ThemeStudio.kt,ui/HomeCustomize.kt,ui/MimicApp.kt}`.
- `app/src/androidTest/java/com/example/mimic/ThemeRecoveryTest.kt`.
- `tools/bla/verify_recovery.py`, `CHANGELOG.md`, `docs/THEME_RECOVERY.md`.
- `tools/bla/apk_pipeline.py`: final verification now requires the new Studio actions and six-style preview/destination evidence.
- `docs/MImic-Development-Report.txt`.
- `app/src/main/java/com/example/mimic/MainActivity.kt`, `ui/PlayerLayoutStudio.kt` and `app/src/androidTest/java/com/example/mimic/ThemeRemovalControlTest.kt`.
- `scripts/release.sh`: runs both debug and release lint in its mandatory dry-run/publish path.
- `docs/RELEASE_READINESS_4.8.1-beta.3.md`: candid readiness report, pending final receipts.

## Results and blockers
- Prior logs show recovery validation failed at `Missing visible UI target: Remove`; a later focused test passed removal three times but failed snapshot identity restoration. These are historical failures, not current passes.
- Baseline dry run: Python pipeline tests, both unit suites, 17 direct Android regression tests and release lint passed. Emulator baseline restored. Stopped before final testing to complete source fixes; these results do not validate the changed source.
- Implemented explicit Theme Studio restore/reset/recovery actions and shared navigation picker/preview. Appearance reset now uses one allowlisted DataStore transaction, pauses playback and cancels the sleep timer.
- Added isolated appearance-reset preservation regression and final-APK Theme Studio recovery checks. Successful screenshots now retain fresh XML evidence alongside PNGs.
- Final-source unit suites: Full 369 (3 skipped), Standard 245 (1 skipped), zero failures/errors. Kotlin + instrumentation compilation and debug lint passed. Direct Android suite: 18 tests passed in 48.71 seconds; saved beta.2 baseline restored. Release lint/protected build/final APK checks in progress.
- Release lint passed. Full R8 build and BLA transform passed; Standard R8 build and private matching-output JADX comparison are in progress. No final signed-APK success is claimed yet.
- Both protected production builds passed (5m43s). All five staged APKs passed package/version/signature/alignment/protection checks; final emulator gate is running on their exact hashes.
- That candidate passed x86_64/Standard/Full installation, ART compilation, theme preservation, six-tab navigation and Standard/Full local/background/lock-screen playback. Recovery FAILED at the missing accessibility selection state. It is not release-ready; source fixes require fresh builds and receipts.
- Replacement-source unit suites passed (369 Full / 245 Standard, four skips, zero failures). All 19 direct Android regressions passed in 45.797 seconds, including the new accessibility test; beta.2 snapshot restored. Both lint variants and protected rebuild are running in `build/bla/resume-accessibility-release-dry-run.log`.
- Replacement debug/release lint and both protected builds passed (4m17s). Fresh original-key staging, private comparison and exact-hash final emulator validation are running. Earlier candidate evidence remains explicitly rejected for release.
- Replacement final-APK gate passed all six motion styles, previews, cold-launch preference persistence and 36 real destination selections. Active removal and restart/reinstall/reboot checks progressed, then `updates()` failed with `Missing recovery UI: App Updates`; fresh failure evidence shows the Android launcher. No complete receipt was issued. Evidence: `build/bla/screenshots/20261127-211517-406cb377/` and `resume-accessibility-release-dry-run.log`.
- Added failure logcat/activity/window diagnostics and explicit incomplete-check records before snapshot restoration. Targeted removal/reinstall/reboot/Updates reproduction is running under its own snapshot; independent security gate is also running.
- Standalone security gate PASS on 2026-11-28: secrets, 345-dependency OSV inventory (23 reviewed advisories, zero blocking), both merged manifests and all five pinned APK signers. This does not override the incomplete recovery gate or authorize publication.
- All six raw emulator frame reports were retained. They show 62–73% janky frames on this Pixel emulator; functional navigation passes do not establish smooth physical-device animation. No smoothness claim is made.
- Targeted reproduction retained logcat/activity/window evidence: no crash/ANR; app stayed on Home after a label-centre tap. The input probe verified the installed APK hash after reboot, reproduced the failed label tap, then successfully opened Settings by tapping its actual clickable accessibility parent. Evidence: `build/bla/resume-input-probe.log` and `build/bla/screenshots/postboot-input-20261128-081148/`.
- Driver now taps the nearest enabled clickable control, refuses non-actionable/disabled labels, and retains all existing navigation assertions. Added refusal tests; 17 Python tests pass.
- System-bar appearance is reapplied on window focus as well as composition, addressing the post-reboot contrast reset seen in actual screenshots.
- Emulator gate now compares installed base-APK bytes against each signed candidate after install and reboot; version/firstInstallTime alone cannot authorize an artifact.
- Latest source passed both unit suites and debug/release lint (post-reboot preflight: 3m18s). Python refusal/evidence suite now has 17 passing tests.
- Emulator exited during a subsequent baseline restore. Restarted the existing Pixel_10a AVD from the named beta.2 snapshot with its saved 4 GiB RAM / SwiftShader configuration, no snapshot overwrite; verified 400800135 and original firstInstallTime. Logs retained in `build/bla/resume-emulator-restart*.log`.
- Full mandatory dry run restarted as `build/bla/resume-postboot-release-dry-run.log`. No final release receipt or publication is authorized until it passes.
- That run's 19 instrumentation tests passed, but its headless emulator exited during snapshot restoration; no successful regression receipt was issued. Windows fault evidence: QEMU / ucrtbase.dll / 0xc0000409. Retained failed snapshot `mimic_regression_5dbfa849808e`.
- Restored the original windowed SwiftShader configuration. Startup was initially paused by crash-report consent; documented `-no-metrics -crash-report-mode never` kept reporting local and removed that prompt. Fresh owned snapshot save/restore then PASS, with the beta.2 install record preserved (`resume-local-snapshot-probe.log`).
- Full pipeline now runs in the validated windowed configuration: `build/bla/resume-windowed-release-dry-run.log`. No gate was disabled, excluded or bypassed.
- Current-source 19 Android regressions and baseline restore PASS in that run; both lint variants and protected builds PASS (5m13s). Five fresh signed artifacts passed host verification. Current universal SHA-256: `1f74e5ab21689aa17a8cadc3646cb3b3c5ea2d9a5d5718cfe4a9cc8e6c7f88d5`. Exact-hash emulator gate is running.
- Fresh matching-output private JADX comparison remains identical for both representative classes; unsigned ZIP/receipt size delta is 7,442 bytes. No added resistance demonstrated.
- Latest windowed final run again passed all six motion styles and cold-launch persistence, plus active-theme fallback. It then failed after reboot with Android NotificationShade focused, not MImic. Incomplete checks, PNG/XML and full focus/logcat evidence are retained in `build/bla/screenshots/20261128-083623-b0fb6505/`; restore returned OK. No complete receipt issued.
- Driver now explicitly wakes/dismisses the emulator's non-secure keyguard, collapses the shade and validates MImic window focus before tapping/scrolling; app actions cannot target other packages. Foreground refusal tests added (21 Python tests PASS).
- Investigating delayed QEMU exits after deleting a just-restored snapshot: production runners now retain restored baseline RAM files while the emulator is running, rather than deleting a possible live backing file. No verification check is removed. A retained-snapshot stability/reboot/focus probe is running before the final repeat.
- Retained snapshot remained alive for all 45 seconds. The host `adb reboot` client then stalled without proving a fresh guest boot; that probe was not counted as a reboot pass.
- Android's documented `svc power reboot --no-wait` subsequently produced distinct kernel boot IDs, completed startup and a correctly focused/unlocked Settings screen, with the original install record intact. Evidence: `resume-real-reboot-proof.json` and `resume-real-reboot-probe.log`.
- Reboot admission now requires distinct valid kernel UUIDs and completed startup; old completed-boot state and transport errors alone cannot pass. Python evidence/refusal suite: 25 PASS. Current full run: `resume-verified-reboot-release-dry-run.log`; its 19 Android regressions and baseline restore passed, with restored snapshot retained.
- Fresh private JADX 1.5.7 comparison: MainActivity and MimicApplication decompilations identical between matching R8 and R8+BLA outputs, same two decompiler errors for each. Unsigned output size delta 7,429 bytes (includes ZIP/receipt effects). No added JADX resistance demonstrated.
- BLA limitation: stealth DEX text trailer only; removable noise, no method encryption or demonstrated additional JADX resistance.
- Release status: NOT READY / UNPUBLISHED.

- Resumed the staged 4.8.1-beta.3 release without rebuilding APKs. Prior recovery failed at Classic selection because a clipped row's unbounded centre fell beneath the mini-player; visible-ancestor intersection now bounds the touch target. The first targeted probe stopped at the app's Compose popup focus label (`Pop-Up Window`); the driver now accepts that label only with MImic as focused app. The targeted retry passed immediate Classic selection and cold-launch persistence and restored its owned snapshot. Evidence: `build/bla/resume-classic-probe-retry.log`.
- Python driver/evidence tests: 28 PASS. Final standalone security gate: PASS (345 dependencies, 23 reviewed advisories, zero blocking; secrets, both manifests, all five pinned signers). Evidence: `build/security/gate.json` and `build/bla/resume-final-security.log`.
- Deliberately restored saved beta.2 hidden-theme baseline (400800135, original firstInstallTime) for final exact-hash validation. Running `build/bla/resume-final-exact-emulator.log`; no complete receipt claimed yet.
- Added explicit `scripts/release.sh --publish --staged` to reuse already built APKs only with matching source/exact-hash emulator receipt, checksum verification, full artifact verification and the security gate. No BLA transform or production protection requirement changed.

2026-11-28 final prepublication gates: PASS. No APK rebuild in this continuation. Five hashes match staged protection report. Exact-hash emulator receipt passed all recovery flags, all six styles, 36 style/destination selections, Classic persistence, Standard/Full playback, ART, same-version reinstall, real reboot and beta.2-to-beta.3 upgrade (400800135 to 400800136), preserving firstInstallTime 2026-10-17 19:29:42. Thirty themes removed individually; final visible theme protected. Offline restore, both reset flows, visible downloads/favorites and Studio recovery shortcut passed. Security gate PASS. Original owner snapshot restored; Plasma Glow and install record preserved; installed final universal hash 1f74e5ab21689aa17a8cadc3646cb3b3c5ea2d9a5d5718cfe4a9cc8e6c7f88d5. Evidence: dist/emulator-verification.json; build/bla/resume-final-exact-emulator.log; build/bla/resume-owner-restored.json. Publication and live official OTA/repair follow this committed checkpoint. No additional BLA/JADX resistance demonstrated.
