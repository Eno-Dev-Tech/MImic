# BLA build integration

MImic uses **bla-agents 3.6.1**, MIT, as a build-time dependency only. The exact
dependency graph and npm integrity digests are locked under `tools/bla`. Node
24.19.0 is the tested local/CI version. No Node, Babel or BLA runtime is packaged
in the Android app. The existing Android package and signing certificate remain.

Official resources reviewed: [source](https://github.com/qybaihe/bla),
[website](https://bla.tools), [npm](https://www.npmjs.com/package/bla-agents).
The reviewed upstream checkout was `dd9e72f53ad97e3eb228523ad25295029107bb63`.
The npm package integrity is pinned in package-lock.json. Lifecycle scripts are
disabled during dependency installation. Re-run tests before changing any pin.

## Actual protection and limitations

The only enabled BLA method is `protectAndroidDexBuffer` with **stealth** intensity.
It appends inert decoy text and recomputes DEX file_size, SHA-1 and Adler32.
Despite the upstream field name `decoyClassesInjected`, these are descriptor
strings in a trailer, not newly defined Android classes. The original string,
type and class tables and all method bodies remain unchanged. The adapter proves
that byte preservation rather than trusting the marker or score alone.

This is removable analysis noise. It adds no method encryption, runtime integrity
enforcement, or demonstrated JADX resistance beyond R8. R8 is the source of the
release code optimization, shrinking and obfuscation. No token/cost amplification
estimate is reported as a measured security benefit.

Whole-APK hardening is deliberately not used. Upstream's broad archive shield has
additional JNI/manifest/decoy behavior and does not supply the required Android
alignment/signing pipeline. Source weaving, aggressive exhaustion, JNI decoys,
runtime traps, manifest modification and other unvalidated options are disabled.

## Production path and enforcement

1. Gradle builds original Kotlin/Java sources and runs R8 with optimization enabled.
   AGP 9.4's `.keep` source set supplies narrowly scoped reflection/optional API rules.
2. The AGP public `SingleArtifact.APK` transform invokes `protect<Variant>Apks` for
   **every release APK** in both editions. There is no skip-BLA Gradle property.
3. The adapter inventories contiguous `classes.dex`, `classes2.dex`, etc., including
   tenth and later entries. Duplicate/hidden/missing/noncanonical DEX members fail.
   Supported standalone DEX versions are 035/037/038/039/040; v041 is refused.
4. Each DEX is protected independently, checked with independent SHA-1/Adler32
   validation, BLA inspection, SDK dexdump checksum and full decoder passes.
5. Resources, AndroidManifest.xml and native libraries retain their exact contents.
   An embedded JSON receipt records every transformed DEX hash and limitations.
6. SDK zipalign uses four-byte ZIP alignment and 16 KiB uncompressed native alignment.
   Production APKs remain unsigned until `scripts/release.sh` stages them.
7. The release script signs locally from the ignored keystore.properties. Passwords
   travel only through child-process environment variables, never command arguments
   or report files. Apksigner must match certificate SHA-256
   `C03C51075A49A049387185C775626074368AA9F4220EB5BA0E74CC4A4F88087D`.
8. Release publication requires both unit suites, pipeline refusal tests, Android
   instrumentation, lint, final signed APK emulator/upgrade checks and security gate.
   The emulator receipt is bound to the exact signed APK hashes, so stale results
   cannot authorize a changed APK. Publishing uploads only explicitly staged assets.

R8-only intermediates exist under normal AGP build folders and
`build/bla/private-comparison`. They and `app/build/outputs/mapping` are **private**.
Never upload those APKs, decompiled source, mapping files, keys, or credentials.
The final Gradle APK artifact is under `app/build/outputs/bla/<variant>`; only the
release script's verified signed `dist` assets may be published.

CI installs pinned Node/dependencies, runs refusal tests and builds both protected
unsigned release variants. It uploads JSON evidence only, without APKs or private
R8 mappings. The original production key stays on the maintainer's machine.

## Emulator and upgrade verification

Use the existing emulator/ADB from the terminal. `android_regression.py` builds
debug/test APKs, verifies their signer, saves its own fresh emulator snapshot, runs
instrumentation directly, restores that snapshot and checks the original install
record. It never invokes connected-test cleanup, uninstalls or clears app data.
The optional live catalogue test is explicitly excluded from the offline suite.

`verify_emulator.py` requires a previous signed MImic installation on the first
run. It installs the final x86_64 and universal APKs with `adb install -r`, checks
firstInstallTime and the active theme, runs ART compilation, six-tab navigation,
three cold launches and PSS measurement, and tests local/background/locked-screen
playback and the media notification. The existing local track `Artwork Red` is
the default fixture; set `MIMIC_SMOKE_TRACK` for another known local track.

The runner also hashes the installed standalone base APK after each install and
after the recovery reboot. Matching version metadata alone cannot authorize a
different installed artifact. UI actions use the enabled accessibility control's
hit area (including selected-tab reselect areas), rather than shifted icon/text
coordinates. Non-actionable text and disabled controls are refused. Failed
recovery runs retain incomplete checks, PNG/XML, logcat and window/activity state;
these diagnostic records never count as successful release receipts.

On this Windows host, a headless 37.2.12 emulator run exited during snapshot
restoration. The existing windowed Pixel_10a with 4 GiB RAM and SwiftShader passed
an independent owned snapshot save/restore probe. `-no-metrics` and
`-crash-report-mode never` avoid startup reporting prompts without sending crash
reports. Preserve the original snapshots and require a successful restore; an
emulator/runtime failure still blocks release.

Keep a restored snapshot's RAM backing files while its emulator is running.
Eager cleanup was followed by delayed QEMU exits on this host; the runners now
retain their named baseline snapshots (recorded in receipts/logs). Clean these
test snapshots only after the emulator stops, preserving the owner's and
previous-release baselines. Reboot checks use Android's power service and require
a changed kernel boot ID plus completed startup; a hung ADB client or an already
set `sys.boot_completed` property is insufficient. The driver explicitly dismisses
the non-secure emulator keyguard, collapses the shade and requires MImic window
focus before app actions, including its own popup windows.

A same-version repeat can reuse upgrade evidence only for identical final APK
hashes and the same original install record. If source/build changes invalidate
that receipt, deliberately restore the saved previous-release baseline snapshot;
do not downgrade, uninstall or manufacture an upgrade pass.

Physical ARM devices, real account logins, live Navidrome and native ffmpeg on the
16 KiB Pixel are not covered by this local gate. Explicitly report these limits.
After publication, restore the previous-release snapshot and verify the actual
Beta updater's detect/download/install flow before calling OTA verified.

## Agent installation review

The upstream skill bundle's six regular Markdown files were inspected outside
agent-discovery roots before setup. Verdict: **REVIEW** for mutable `@latest`
fallbacks and broad source-weaving/exclusion guidance. The owner's explicit exact
version and clean-source/no-bypass rules override those recipes. Project AGENTS.md
forbids them. No shell hooks, bundled executables or upload instructions were in
that skill bundle; this is a scoped review, not an audit of every BLA engine.

Pinned setup configured Codex and VS Code while preserving their other MCP
entries and backing up their configuration. `bla doctor --verify-handshake`
reported healthy / local MCP handshake verified. GUI client reload/attachment is
separate from that local handshake and from APK protection.

## Measurements

Private comparison tools and reports live under `build/bla/private-comparison`.
`compare_protection.py` records ordinary R8 vs BLA APK sizes and representative
JADX 1.5.7 decompilation hashes/errors. Never publish the comparison APK or R8 map.
Final measurements and release/OTA status are recorded in the development report
after validation; an unfinished test is not a pass.
