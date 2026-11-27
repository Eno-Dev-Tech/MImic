# Theme visibility and app recovery

Theme Studio hides a theme only after its minus control's confirmation. The
48dp control has a small minus glyph and a theme-specific accessible label.
`removed_theme_ids` is a local DataStore string set keyed by stable IDs, independent
of names, translations or ordering. The complete built-in/custom catalog stays
intact. New theme IDs are visible by default. Removal atomically chooses a visible
fallback for the active theme and rejects the last visible theme. A damaged imported
all-hidden set still resolves a safe visible theme.

Settings > App Updates > **Recovery & Repair** offers:

- **Restore Removed Themes** removes only the hidden-ID preference. It works offline
  and preserves the active theme, custom theme definitions and all personal data.
- **Repair / Reinstall Latest Version** explicitly searches the selected official
  release channel for the newest compatible protected release at or above the
  installed version. It respects edition, ABI and minimum Android API. A same-version
  reinstall is permitted only in this flow. Normal updates remain strictly newer.
  The APK must match SHA256SUMS, the official protection report and its embedded
  DEX hashes, package/version and pinned/installed signing identity. The report
  describes the host-validated BLA transform; the client does not rerun BLA or R8.
  Android asks for installation approval. Cached retries are verified again.
- **Reset App Settings** confirms its scope, pauses playback, cancels the sleep
  timer and removes an explicit list of appearance, audio, display, scanning and
  updater preferences. It restores hidden themes. Search/listening history,
  credentials/accounts, server connections, custom themes, queues, playlists,
  favorites, downloads and music files stay intact. No caches are deleted.

Downloads use unique targets and temporary `.part` files. Cancellation, incomplete
responses and transfer failures remove partial files; an unsuccessful transfer
never becomes an installable cached APK. A failed/cancelled Android installer can
be retried after verifying the retained APK again. No recovery path uninstalls,
clears Android application data or silently downgrades.

Pure tests exercise visibility/fallback/new IDs, repair admission and report/DEX
matching, transfer truncation/oversize/interruption/cancellation and retry. Isolated
Android DataStore tests exercise persistence and reset preservation; Compose tests
exercise the accessible minus control. Final APK/emulator evidence and actual
GitHub repair status belong in the development report; these tests alone do not
claim a live installer or streaming/account test.
