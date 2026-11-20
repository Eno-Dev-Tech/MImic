# Third-party notices

MImic is licensed under the GNU General Public License v3.0 (see `LICENSE`). This file lists the
code it incorporates from other projects and the libraries it ships, with their licenses.

## Code copied and modified from BitChord (GPL-3.0) — MImic 4.4

BitChord — https://github.com/kushagrasinghx/BitChord — Copyright (C) BitChord contributors,
licensed under the GNU General Public License v3.0.

The following files under `app/src/full/java/com/example/mimic/online/` are copies of BitChord
source files, modified for MImic (package renamed from `com.music.bitchord` to
`com.example.mimic.online`, trimmed, and adapted to MImic's player and storage). Each file starts
with a notice naming its original path.

| MImic file (online/…) | BitChord original | Modification |
|---|---|---|
| data/innertube/Innertube.kt, InnertubeParser.kt, PlayerClient.kt, UploadedTracks.kt | shared/…/data/innertube/ | package rename |
| data/innertube/StreamResolver.kt | shared/…/data/innertube/StreamResolver.kt | package rename; stats overlay removed; `ensureNewPipe()` added |
| data/innertube/InnerTubeXResolver.kt | shared/…/data/innertube/InnerTubeXResolver.kt | package rename |
| data/innertube/potoken/* | shared/…/potoken/*, app/…/potoken/PoTokenWebView.kt | package rename, MImic BuildConfig |
| data/YtMusicRepository.kt, LikeState.kt, LocaleTags.kt, Http.kt, TrackLog.kt, DebugLog.kt, MonotonicClock.kt | shared/…/data/ | package rename; WebDAV interceptor removed from Http; TrackLog read-back replaced by `recent()` |
| data/model/Models.kt, ArtistCredits.kt, PlaylistOrder.kt; data/sources/StreamFormat.kt, TrackMatcher.kt | shared/…/data/model, sources | package rename |
| playback/ChunkedDataSource.kt | app/…/playback/ChunkedDataSource.kt | package rename |
| download/Downloader.kt, MediaTagger.kt, Mp4Tagger.kt, Mp4Boxes.kt, Mp4Sidx.kt, WebmTagger.kt, FlacTagger.kt | app/…/download/ | package rename; lyrics embedding removed; `readNBytes` replaced (API 24) |
| auth/AuthStore.kt, AccountSessions.kt, DataSyncId.kt, EncryptedPrefs.kt, WebSession.kt, YtMusicLoginScreen.kt | app/…/auth/, shared/…/auth/ | package rename |
| `app/src/full/java/org/schabi/newpipe/extractor/utils/Utils.java` | BitChord's patched copy of NewPipeExtractor v0.26.3 Utils (GPL-3.0) | unchanged |

MImic's own glue (`OnlineMusic.kt`, `OnlineDownloadWorker.kt`, `OnlineQuality.kt`,
`OnlineDiagnostics.kt`, `YouTubeSearch.kt`, `YouTubeMusicAccount.kt`, `OnlineSettings.kt`, and
everything under `app/src/main/`) is MImic's code under GPL-3.0.

## Libraries added in 4.4

| Library | License | Edition | Why |
|---|---|---|---|
| InnerTubeX (com.github.MetrolistGroup.innertubex) v0.7.0 | GPL-3.0 (from its POM) | full | YouTube stream extraction (client catalogue, cipher, PoTokens) |
| NewPipeExtractor (TeamNewPipe) v0.26.3 | GPL-3.0 | full | stream-resolution fallback, YouTube search fallback |
| nanojson (TeamNewPipe) | MIT / Apache-2.0 | full | NewPipeExtractor dependency |
| Mozilla Rhino 1.8.1 | MPL-2.0 | full | NewPipe's player-JS evaluation |
| quickjs-kt-android 1.0.14 (dokar3) | Apache-2.0 | full | InnerTubeX's JS solver |
| jsoup 1.22.2 | MIT | full | NewPipe dependency |
| protobuf-javalite 4.35.0 | BSD-3-Clause | full | NewPipe dependency |
| findbugs jsr305 3.0.2 | BSD-3-Clause | full | annotations (NewPipe) |
| Ktor client 3.5.2, kotlinx-serialization-json 1.11.0 | Apache-2.0 | full | BitChord's Innertube HTTP / JSON |
| androidx.security:security-crypto 1.1.0-alpha06 | Apache-2.0 | full | encrypted YouTube Music session storage |
| AndroidX Credentials 1.3.0, Google ID (googleid) 1.1.1 | Apache-2.0 | both | Sign in with Google |
| Firebase Auth / Firestore (BoM 33.7.0) | Apache-2.0 SDK; uses Google Play services | both | MImic account and sync |
| desugar_jdk_libs 2.1.5 | GPL-2.0 with Classpath Exception | both | java.time on Android 7 |

Notes:
- Firebase and Credential Manager rely on Google Play services, which is proprietary. MImic uses
  it only as a system component for sign-in; the account is optional and MImic works without it.
- At runtime InnerTubeX downloads a player-configuration file from the public `ZemerTeam/zemer-cipher`
  GitHub repository, and YouTube's own player JavaScript; neither is bundled in the APK.
- LRCLIB (https://lrclib.net) is queried for lyrics; lyrics remain the property of their rights
  holders and are only cached on the phone.
- Earlier libraries (youtubedl-android GPL-3.0, Media3, Room, Coil, OkHttp, kyant backdrop, …)
  are listed in `docs/DEPENDENCIES.md`.

## Listen Together and lyrics code from BitChord (GPL-3.0) — MImic 4.5

Same origin and license as above. Each file below carries a header naming its BitChord origin.

| MImic file | BitChord original | Modification |
|---|---|---|
| `server/party/**` (Go) | `backend/` (clock, codes, config, hub, party, presence, protocol, main.go, tests) | module path, branding, `mimic://` invite deep link, host `endParty`, `voteSkip` (majority of connected devices), new tests `mimic_test.go` |
| `app/src/main/java/com/example/mimic/party/ListenTogether.kt` | `data/listentogether/ListenTogether.kt` | OkHttp WebSocket instead of Ktor, one user-chosen server, identity from the MImic account or a nickname, membership kept across restarts, end party, skip votes |
| `app/src/main/java/com/example/mimic/party/PartyModels.kt` | `shared/.../listentogether/PartyModels.kt`, `ServerClock.kt` | org.json instead of kotlinx-serialization; skip-vote fields |
| `app/src/main/java/com/example/mimic/party/PartyInvite.kt` | `data/listentogether/JamInviteLink.kt` | MImic link shapes |
| `app/src/main/java/com/example/mimic/player/PartySync.kt` | `playback/PartySync.kt`, `PartyPersonalQueueStash.kt` | resolution to each device's own source, MImic song ids, sync status, errors sit a song out |
| `app/src/main/java/com/example/mimic/data/lyrics/LyricsQuery.kt` | `shared/.../lyrics/LyricsQuery.kt` (credit/packaging patterns) | extended (artist cleanup, version markers, "Artist - Title" readings) |

The party server's only dependency: **gorilla/websocket 1.5.3** — BSD-2-Clause.

## Services used (4.5)

- **LRCLIB** (https://lrclib.net) — free community lyrics database with an open API. MImic sends
  only title, artist, album and length, identifies itself in the User-Agent as LRCLIB asks, and
  credits LRCLIB on every lyrics screen that shows its lyrics. BitChord's other lyrics providers
  (unofficial Musixmatch / Genius / KuGou / Megalobiz endpoints) were deliberately not ported.

## App icon (4.5)

The glass waveform disc icon (4.5.0-beta.4; earlier the brushed-metal "E", the "Glossy Neon Glass E Emblem" and the neon-glass "E") is the owner's image (`docs/icon/source/mimic-wave-disc-source.png`);
`tools/icon/build_e_icon.py` only scales and centers it for Android's icon formats.

## Distribution (GPL-3.0)

If you give the APK to anyone else, the GPL requires that they can get the complete corresponding
source code (this repository at the matching tag), under the GPL, with these notices kept.

## Musify adaptations — private integration, 2026-11-20

Source: https://github.com/gokadzev/Musify, commit
5f6a5e7b497bc2bccd0985f731db1afe6dcdda9a (master inspected locally).
Copyright (C) 2026 Valeri Gokadze. GNU GPL version 3 or, where stated in the
upstream source header, any later version. Full upstream license is bundled as
app/src/main/assets/Musify-GPL-3.txt; the project LICENSE remains GPL-3.0.

Adapted files:
- player/SponsorSegments.kt: _fetchSkipSegments from lib/services/common_services.dart;
  ported to Kotlin with millisecond precision, validated and merged ranges.
- data/PlaylistCsv.kt: CSV parser/header detection from
  lib/screens/import_spotify_playlist_page.dart; bounded and validated, matches only
  existing native-library recordings with explicit review of misses/ambiguities.
- player/SponsorBlock.kt, ui/settings/PlaylistImportScreen.kt and calendar recaps
  are native MImic implementations of compatible features. No Flutter runtime added.

SponsorBlock: uses data from https://sponsor.ajay.app under CC BY-NC-SA 4.0,
https://creativecommons.org/licenses/by-nc-sa/4.0/ . Data is obtained at runtime
only after opt-in, stored in an evictable cache and not bundled with the APK.
Attribution appears beside the setting. No data submissions or voting are performed.
This private build is for the owner's noncommercial personal use.

GPL-3 section 2 permits private modification and running without public source
publication. If the combined app is conveyed to others, GPL sections 4–6 require
notices, licensing and Corresponding Source through a compliant delivery method;
this does not inherently require a public GitHub repository. Musify's README summary
contains restrictions broader than the standard GPL text (including commercial
redistribution); the actual LICENSE is the standard GPL. Obtain clarification before
any future distribution. Private use does not waive unrelated service/content rights.
Nothing from this integration was pushed, published or uploaded.

## Home discovery additions — 2026-11-20
DiscoveryMapper.rankRelated adapts Musify lib/services/common_services.dart _getRecommendationsFromSeedSongs (Copyright (C) 2026 Valeri Gokadze, GPL-3.0-or-later). It uses bounded native YouTube metadata calls and excludes seeds/duplicates. Country discovery, feed state, UI and caching are MImic code. Public YouTube extraction uses the existing bundled NewPipeExtractor (GPL-3.0); no new binary dependency or account/API key is required. Public album-query results remain labelled as playlists with their actual IDs/publishers; recent uploads are not claimed to be original release dates. Existing notices and the bundled Musify GPL text are retained.
