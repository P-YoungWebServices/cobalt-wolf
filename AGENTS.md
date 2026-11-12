# AGENTS.md

Conventions and rules for contributing to Cobalt Wolf.

## Language and architecture

- **Kotlin only.** No Java sources.
- **MVVM** architecture with a repository layer.
- **Kotlin coroutines and Flow** for all asynchronous work. No callbacks or
  RxJava.
- UI is Jetpack Compose (phone/tablet) and Compose for TV.
- Follow `docs/SPEC.md` for the product spec and milestone plan.

## Process

- **Small, focused pull requests.** One concern per PR; don't bundle unrelated
  changes.
- **Unit tests for all business logic.** ViewModels, repositories,
  use cases and any logic that can be exercised without Android framework
  dependencies should have tests. UI code may rely on instrumented tests
  later, but keep business logic testable in plain JUnit.
- Run `./gradlew test` before submitting a PR.

## Licensing (important)

- The project is licensed under **GPL v3** (see LICENSE).
- **All code must be written originally.** Reading other projects to learn
  how they solve a problem is fine, but never copy their code. If code is
  adapted from another source, note the source in a comment and confirm its
  license is compatible with GPL v3. GPL v2-only code is **not** compatible.
- **Every new dependency's license must be checked for GPL v3 compatibility**
  before adding it. Prefer well-established libraries; record any
  attribution requirements (e.g. FFmpeg LGPL linking rules) in this file or
  the relevant module's README.
- Do not reproduce JellyWatch's code, branding, icons or artwork. Reproduce
  capabilities only.
- Keep the Jellyfin disclaimer in README/distribution materials: Cobalt Wolf is a third-party app, not affiliated with or endorsed by the
  Jellyfin project.

## Build / verify

- `./gradlew test` and `./gradlew assembleDebug` are the standard
  verification commands (CI runs `./gradlew test assembleDebug`).
- `./gradlew test` runs the shared tests on both the JVM target
  (`:shared:jvmTest`) and the Android host target
  (`:shared:testAndroidHostTest`), plus `:app:testDebugUnitTest`.
- Requires JDK 17+ (`JAVA_HOME`) and an Android SDK with platform 37
  (`local.properties` `sdk.dir=...` or `ANDROID_HOME`). Locally, Android
  Studio's bundled JBR works: `JAVA_HOME=/snap/android-studio/current/jbr`.

## Local test credentials

- `dev.credentials.properties` (repo root, gitignored) holds test credentials:
  Jellyfin `server`/`username`/`password`, `firestick.adb`, `seerr.server`,
  `syncplay.username`/`syncplay.password`, `radarr.url`/`radarr.apikey`,
  `sonarr.url`/`sonarr.apikey`, `qbittorrent.url`/`username`/`password`,
  `bazarr.url`/`bazarr.apikey`, `prowlarr.url`/`prowlarr.apikey`.
  `app/build.gradle.kts` reads it at build time into debug-only `BuildConfig`
  fields; `app/src/debug/.../DevSeed.kt` consumes them to seed service settings
  and sign into Jellyfin + Seerr on every debug start so test installs are
  usable immediately. The release stub (`app/src/release/.../DevSeed.kt`) is a
  no-op. Release builds contain no credentials.
- **Never commit it, copy its values into code, tests, docs, logs or PR text, or
  print the password.** Read values from the file in shell commands only.
- If the file is missing or still has placeholder values, ask the user to fill it
  in rather than guessing credentials.

## Test devices

- **The owner's phone may be used for on-device testing** whenever it is
  attached over USB: a Nokia G50 (Android 13, arm64, HDR10/HLG 720p panel, no
  HDMI output). Check with `adb devices`; `adb` is at
  `~/Android/Sdk/platform-tools/adb` (not on `PATH`). Installing debug builds,
  pushing sample media to `/sdcard/Android/data/<app id>/files/` or
  `/data/local/tmp`, launching activities and reading logcat are all fine. Do
  not touch other apps or user data on it, and do not factory-reset, unlock or
  change system settings.
- A Fire Stick is tested over network adb. Its address is the `firestick.adb`
  key in `dev.credentials.properties`; if it is empty, ask the owner to fill it
  in. Connect with `adb connect <address>` and target it with `adb -s <address>`.
- With more than one device attached, always pass `adb -s <serial>`.

## Testing playback on a device

- Debug builds export `.player.DebugPlayerActivity` (plays `-d <uri>` or
  `--es item_id <id>`) and `.ui.settings.DebugPlaybackSettingsActivity`; see
  README "Testing playback from adb" for the extras. Everything of interest is
  logged under the `CobaltWolfPlayback` tag: detected capabilities, fallbacks and
  a `stats` line (codec, decoder, HDR, audio output, dropped frames) 5 s into
  playback and every 15 s after.
- Files pushed with adb into the app's storage are not readable by the app on
  Android 11+. Serve samples over HTTP with Range support instead and, for a
  USB device, `adb reverse tcp:<port> tcp:<port>`.
- Free samples: https://repo.jellyfin.org/test-videos/ (H.264, HEVC, AV1,
  HDR10, Dolby Vision P5/P8). The owner's Jellyfin library has real 4K HDR10,
  Dolby Vision, TrueHD Atmos, DTS-HD MA, PGS and ASS items; find them through
  the API with the credentials file and play them by item id.

## Project layout

- `shared/` (Kotlin Multiplatform: `android` + `jvm` targets): models,
  Jellyfin SDK data sources, repositories, ViewModels and the Koin
  `sharedModule`. `commonTest` holds the unit tests. `androidMain` holds
  `KeystoreSessionStore` (Android Keystore AES-GCM token storage),
  `KeystoreServerProfilesStore` (the saved server/account profiles behind the
  multi-server switcher; same Keystore AES-GCM scheme via `KeystoreCipher`,
  `server_profiles` prefs, excluded from backups) and `androidPlatformModule`
  (creates the `Jellyfin` instance). `AuthRepository.profiles` exposes the
  profiles; `switchTo` swaps the active session without a sign-out and
  `removeProfile`/`signOut`/`invalidateSession` drop profiles — the
  `ServersViewModel`-backed switcher is `ui/servers` (phone) and the Servers
  entry in the TV "More" menu. `MediaItem.streams` carries `MediaStreamInfo`
  (video/audio stream details mapped from the SDK's `MediaStream`); detail
  pages render `videoQualityLabel`/`audioProfileLabel` summaries, and
  `LibraryType.MusicVideos`/`MediaKind.MusicVideo` make music-video
  libraries browsable like any other.
- `app/`: Android app (`com.pyoungws.bluewolfplayer`, minSdk 24), Jetpack
  Compose + Material 3 UI only; no business logic. `player/` holds the Media3
  glue (`PlayerEngine`, stats collector, display mode controller).
- `app/ui/tv/`: the Android TV / Fire TV experience (Compose for TV, always
  dark — `BlueWolfTheme` forces dark and never uses dynamic device colours;
  tv-material3's `LocalContentColor` is provided from `TvColors` so text is
  light on the navy palette). `MainActivity` picks it at runtime via
  `UiModeManager` when `isTelevision()` (`DeviceInfo.kt`) is true.
  The persistent top bar holds the wolf icon plus Home, Search, Seerr,
  Calendar and a "More" overflow menu (My requests, Servers, Settings,
  Server dashboard for admins, Sign out). Watch together is not a nav
  item — it lives on detail pages. Downloads and service credentials are
  admin-only pushed routes (`Route.Downloads`/`Route.ArrSettings`) opened
  from the dashboard; pushed routes reuse `Route`/`decodeRoute` from
  `ui/navigation`, including the phone admin screens which work on TV via
  D-pad. Back pops pushed routes first, then returns to the Home section
  before letting the app exit. `TvTextField` keeps text fields read-only
  until focused + centre/enter pressed, then enables the IME — use it
  instead of `OutlinedTextField`/`TextField` on TV screens. TV screens
  reuse the shared ViewModels; lazy lists are the standard
  `LazyColumn`/`LazyRow`/`LazyVerticalGrid` (the `TvLazy*` components were
  removed in tv-foundation 1.0.0). `ui/tv/channels/` publishes Android TV
  home screen channels (Continue Watching, Recently Added) via
  androidx.tvprovider when `supportsHomeScreenChannels()` — skipped where
  the TV provider does not exist (Fire TV). Programs deep-link
  `bluewolf://item/<id>` into the item detail screen. The TV banner is
  `res/drawable-xhdpi/tv_banner.png` (320x180, original wolf artwork +
  wordmark). The phone top bar mirrors the same structure: Search,
  Requests, More (Servers, Calendar, Playback, Dashboard, Sign out).
- Player on TV: `PlayerScreen` gives the D-pad a focus target and focuses
  play/pause when controls show; `PlayerActivity.onKeyDown` maps remote
  transport keys (play/pause, fast-forward, rewind, stop). Frame rate matching
  (`DisplayModeController`) already applies on TV via `capabilities.isTv`.
  The overlay shows the item title top-left, icon buttons top-right (white
  ring + fill on focus), and an "Ends at" wall-clock time + skip button
  bottom-right. Next-episode goes through the controller's own next button:
  a `ForwardingPlayer` advertises `COMMAND_SEEK_TO_NEXT` once
  `PlayerViewModel.nextItem` resolves and routes `seekToNext()` to
  `playNext`; episodes also auto-advance on `STATE_ENDED` (not in SyncPlay
  groups). `PlaybackRepository.nextEpisode`/`mediaSegments` feed both —
  segments come from the Jellyfin `MediaSegments` API (intro/outro), and a
  visible segment auto-focuses the TV skip button. PiP is disabled on TV
  (`supportsPip` excludes `isTelevision()` — Fire OS declares the feature
  but Home→PiP is wrong there).
- Matroska seeking: ffmpeg-remuxed MKVs only reference their Cues from a
  second SeekHead at EOF, which `MatroskaExtractor` never follows, so the
  stream reports `SeekMap.Unseekable` and every seek control dies (rw/ff,
  scrub, `seekTo` lands at 0). `probeMkvCueIndex` (shared/playback/
  `MkvSeekIndex.kt`) parses the file over HTTP Range requests — head
  SeekHead, chased trailing SeekHeads, then the Cues element — during
  `getPlaybackSource`; `PlaybackSource.cuePoints` carries the index and
  `PlayerEngine` wraps the Matroska extractors (`withMkvCueIndex` in
  `player/MkvCueSeekMap.kt`) so an `Unseekable` map is swapped for
  `MkvCueSeekMap` built from the real cue index. Never re-enable
  `FLAG_DISABLE_SEEK_FOR_CUES` — it also disables the whole seek path.
  `playbackHttpClient()` (OkHttp, in the data source binding) does the
  range fetches; the SDK's `ApiClient.request` cannot send headers.
- Playback decisions live in `shared/.../playback`: `Capabilities.kt` (what was
  detected), `PlaybackSelection.kt` (best path per codec/HDR format and the
  fallback order), `DeviceProfiles.kt` (what the server is told),
  `DisplayModeMatcher.kt`. `androidMain` holds `AndroidCapabilityDetector` and
  the settings store.
- Admin features live in `shared/.../admin` (data source, `AdminRepository`,
  dashboard/session models) with `AdminDashboardViewModel` in `presentation`
  and the screen in `app/.../ui/admin`. `navDestinations()`/`canNavigateTo()`
  in `AdminModels.kt` are the single source of truth for admin visibility:
  the role comes from `UserProfile.isAdministrator` (the Jellyfin user policy),
  and admin routes stay hidden and unreachable for everyone else. The
  dashboard itself is open to everyone — `AdminDashboardViewModel` resolves
  the user policy and viewers get the Servers/Playback cards and recently
  added media without the sessions poll, service cards or management section.
  The Seerr
  request approval queue (`NavDestination.RequestQueue`) is the exception:
  its data goes through `SeerrRepository`, its screen is
  `AdminRequestsScreen` + `AdminRequestsViewModel`. Local notifications for
  request events come from `RequestWatcher` (shared, polls while signed in)
  feeding `RequestNotifier` (app module, posts via NotificationCompat).
  `MANAGEMENT_DESTINATIONS` (in `AdminModels.kt`) also includes
  `Downloads` and `Services` (the Radarr/Sonarr/qBittorrent credentials
  screen) — both are admin-only even though their underlying screens are
  general. The dashboard leads with "Active sessions" and "Downloads"
  action cards; sessions live on their own page (`Route.Sessions`,
  `AdminSessionsScreen`, gated by `NavDestination.Sessions`).
- `app/widget/` holds the Android home-screen widget (`SessionsWidget`, a
  Glance `GlanceAppWidget`) listing the server's active sessions via
  `AdminRepository.sessions()`; session→row mapping lives in `SessionRow.kt`
  and is unit-tested in `app/src/test`. The widget reads repositories through
  the Koin `GlobalContext` started by `BlueWolfApplication`.
- `shared/.../arrs/` is the Radarr/Sonarr integration: `ArrSettingsRepository`
  (per-service URL + API key), `KtorArrDataSource` (`X-Api-Key` header,
  `/api/v3/calendar` + `system/status`), `ArrRepository` (merges both
  calendars, isolates per-service failures). API keys are encrypted at rest by
  `SharedPreferencesArrSettingsStore` via `KeystoreCipher`. `CalendarViewModel`
  and `ArrSettingsViewModel` drive `app/.../ui/calendar/` (phone) and
  `ui/tv/TvCalendarScreen.kt`. The calendar has List/Week/Month modes:
  `CalendarViewModel.show(mode, anchor)` fetches the range for the visible
  period (`CalendarPeriod` in `presentation` holds the Monday-first week and
  month-grid date math, tested in `CalendarPeriodTest`); `ArrRepository.calendar`
  takes an optional explicit range. On TV, `Modifier.tvListScroll` (in
  `ui/common/TvScroll.kt`) makes a LazyColumn D-pad-scrollable when its items
  have no focusable children — apply it to read-only lists (system info,
  libraries, logs, playback settings) since plain LazyColumn can't scroll on
  TV without focusable items. `shared/.../seerr/` is the
  Seerr/Jellyseerr integration: `SeerrRepository` (`authState` —
  `Checking`/`SignedIn`/`SignedOut`/`NotConfigured` — backed by
  `SeerrCookieStorage` + `KeystoreSeerrSessionStore`) and
  `KtorSeerrDataSource`. In `SharedModule` every data source constructs its
  own HTTP client inside its binding — do NOT register the `*HttpClient`
  functions as bare `single { }`s producing `HttpClient`, or Koin hands the
  last one to every consumer (this once made Seerr lose its cookie storage).
  `SearchViewModel` runs Jellyfin and Seerr queries concurrently and waits
  for `authState` to resolve before deciding; phone and TV search show
  "On this server" / "From Seerr" sections on one page.
  `shared/.../net/` holds helpers shared by the
  REST integrations (`normalizeServerUrl`, `Throwable.toLoadError`). The
  admin-only media manager builds on the same connection: `ArrMediaDataSource` /
  `KtorArrMediaDataSource` (edits fetch-and-PUT the raw resource so unmodelled
  fields survive, `POST /api/v3/command` for searches/renames/imports, release
  search + grab, manual import), `ArrMediaRepository`, `MediaManagerViewModel`
  (browse movies/series) and `MediaDetailViewModel` (edit form, season/episode
  monitoring, rename preview, interactive search + grab, manual import, delete
  with a deleteFiles choice). The UI is `app/.../ui/arrmedia/` behind
  `NavDestination.MediaManager` — viewers never reach it (`AdminAccessTest`).
- `shared/.../qbittorrent/` is the qBittorrent monitor: URL + username +
  password settings (password encrypted via `KeystoreCipher`), SID-cookie auth
  (`QBittorrentCookieStorage` + `KeystoreQBittorrentSessionStore`), automatic
  re-login when the cookie expires, and `DownloadsViewModel` polling
  `torrents/info` + `transfer/info` every 3 s while the screen is open.
  Torrents can be paused/resumed (`torrents/start|stop`, falling back to
  `resume|pause` on pre-v5 qBittorrent) and the alternative speed limits
  toggled (`transfer/speedLimitsMode` + `toggleSpeedLimitsMode`) from the
  downloads screens. The
  phone screen is `ui/downloads/`; on TV it is the pushed
  `Route.Downloads` from the dashboard (admin-only). The credentials form
  is `ui/calendar/ArrSettingsScreen.kt` on phone and
  `ui/tv/TvServiceSettings.kt` on TV.
- `shared/.../bazarr/` and `shared/.../prowlarr/` are the Bazarr and Prowlarr
  integrations. Both are `ArrService` members (`managesMedia == false`, so
  they reuse `ArrServiceSettings` + the encrypted API-key store but stay out
  of the calendar/media-manager paths). Bazarr uses `/api` and its wanted
  endpoints answer a `{data,total}` envelope; the wanted screen
  (`ui/bazarr`, `NavDestination.Bazarr`) can search providers and download a
  subtitle via `/api/providers/movies|episodes`. Prowlarr uses `/api/v1`;
  its screen (`ui/prowlarr`, `NavDestination.Prowlarr`) merges indexers,
  stats, auto-disable status and health into `ProwlarrOverview`, and the
  indexer endpoints (`indexer/{id}` fetch + `indexer/test`,
  `indexer/testall`) power per-indexer retest and test-all. A failed test
  answers 400 with validation errors in the body, which `testIndexer`
  parses — `expectSuccess` is disabled on that one request. Both screens
  get dashboard cards with live badge counts (`AdminDashboardViewModel`).
- SyncPlay ("watch together") lives in `shared/.../syncplay`
  (`SyncPlayDataSource` over the SDK's `syncPlayApi` + `apiClient.webSocket`,
  `SyncPlayRepository`, models, `SyncPlayEchoGate`) with
  `SyncPlayViewModel` in `presentation`. The repository is a singleton that
  outlives screens: `membership` tracks the group, `playerCommands` drives any
  attached player, and the `local*` functions report this device's transport
  back to the group. `PlayerScreen` applies `viewModel.commands` and reports
  local pause/seek/buffering; group screens are `ui/syncplay` (phone) and
  `TvWatchTogetherScreen` (TV). There is no nav entry — media detail pages
  carry a "Watch together" button that opens `Route.SyncPlay(itemId)`; a
  queued `itemId` is played for the group via
  `SyncPlayRepository.playInGroup` after join/create. In unit tests inject a
  `backgroundScope` into the repository and pump with `runCurrent()` —
  `advanceUntilIdle()` does not reach background-scope resumptions.
- Versions live in `gradle/libs.versions.toml`.

## Dependencies and licenses

All checked for GPL v3 compatibility:

- Jellyfin Kotlin SDK (`org.jellyfin.sdk`), LGPL-3.0. Keep attribution.
  Requires Jellyfin server 12.0+ (see `docs/SPEC.md`).
- Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx.datetime: Apache-2.0
- Ktor client (core, OkHttp engine, content negotiation, kotlinx-json; mock for
  tests): Apache-2.0. Used for the Seerr/Jellyseerr REST API in `shared/.../seerr`.
- AndroidX (Compose, Material 3, Material Icons Extended, Activity, Lifecycle, Core): Apache-2.0
- Koin: Apache-2.0
- AndroidX Media3 (ExoPlayer, HLS, UI): Apache-2.0
- Coil 3 (image loading): Apache-2.0
- AndroidX TV (`androidx.tv:tv-material`, `androidx.tv:tv-foundation`): Apache-2.0
- AndroidX TV Provider (`androidx.tvprovider:tvprovider`): Apache-2.0. Used for
  the Android TV home screen channels only; never invoked on devices without
  the TV provider (Fire TV).
- AndroidX Glance (`androidx.glance:glance-appwidget`): Apache-2.0. Used for
  the home-screen sessions widget in `app/widget`.
- NextLib `nextlib-media3ext` (FFmpeg audio/video renderers for Media3):
  GPL-3.0. Bundles FFmpeg as shared libraries built **LGPL v3+** (no
  `--enable-gpl`/`--enable-nonfree`), with dav1d (BSD-2-Clause) and Mbed TLS
  (Apache-2.0). Its version is tied to the Media3 version
  (`<media3>-<nextlib>`); bump both together. Before bumping, check the
  binaries still say LGPL: `strings libavutil.so | grep license`. The README
  "Licensing" section carries the FFmpeg notice and must stay accurate.
- libass-android `ass-media` (ASS/SSA rendering for Media3): MIT; bundles
  libass (ISC), FreeType (FTL), FriBidi (LGPL-2.1+), HarfBuzz (MIT). It reaches
  into Media3's Matroska extractor by reflection, so retest ASS subtitles after
  any Media3 bump.

App icon (`app/src/main/res/drawable/ic_launcher_*.xml`, `mipmap-*`): original
artwork made for this project (low-poly wolf head), GPL v3 with the code. No
third-party images. The vector drawables are the source; the `mipmap-*dpi` PNGs
are rendered from the same shapes for API 24–25 launchers.
