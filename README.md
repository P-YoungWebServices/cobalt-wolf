# Cobalt Wolf

Cobalt Wolf is an open-source Android client for [Jellyfin](https://jellyfin.org)
that combines a full-featured media player with a server admin dashboard and a
media-management toolkit — one app for watching your library, running your
server, and managing your downloads. It runs on Android phones, tablets,
Android TV and Fire TV from a single codebase, with a touch UI on handhelds
and a dedicated D-pad UI on televisions.

**Cobalt Wolf is a third-party app and is not affiliated with or endorsed by
the Jellyfin project.**

## Features

### Watching

- Browse movies, series, music, live TV, home videos and mixed libraries,
  with list/card views, card sizes and image-type choices
- Continue Watching, Next Up, Recently Added, suggestions and upcoming rows,
  plus a global Favorites page
- Detail pages for movies, episodes, series, seasons, people, artists and
  albums with cast & crew, chapters, media info and related items
- Full playback control: audio and subtitle track pickers, version/media
  source selection, playback speed, repeat modes, Play Next, Add to Queue,
  Play All From Here, shuffle, resume and play-from-start
- Playlists and collections: create, reorder and manage items; multi-select
  mode, long-press context menus, share/copy stream URL, media deletion and
  administrator metadata tools
- Downloads for offline playback on phone and tablet
- SyncPlay "Watch together" groups
- Search across the library and Seerr at once, with filter chips for type,
  genre, rating, runtime, year, play state, quality and features
- Live TV: channel browsing, playback, EPG guide and recording management
- Music: audio playback with a now-playing experience, music mixes and
  synced lyrics

### High-end playback

The player detects the device's decoders, display and audio output each time
playback starts, then picks the best supported path — and falls back
gracefully instead of failing:

- **Video:** H.264, HEVC, AV1 and VP9 up to the device's decoder limits (4K
  where available); HDR10, HDR10+, HLG and Dolby Vision (profiles 5, 7 and 8)
  when both decoder and display support them. Unsupported Dolby Vision falls
  back to its HDR10/HLG/SDR base layer, HDR10+ to HDR10, and HDR on an SDR
  display is tone-mapped by the server.
- **Audio:** Dolby Digital, Digital Plus (incl. Atmos), TrueHD (incl. Atmos),
  DTS and DTS-HD MA (incl. DTS:X) passthrough when the HDMI sink reports the
  format; otherwise decoded to PCM on-device. Night mode / loudness
  normalization is available for late-night listening.
- **Fallback order:** direct play → local FFmpeg software decoding → server
  transcode (with a configurable codec preference and codec-level caps).
  A decoder that fails mid-playback moves down the same order and resumes
  where it stopped.
- **Display matching (TV):** output frame rate switches to match the content,
  and HDR is only signalled while HDR content plays.
- **Subtitles:** styled ASS/SSA via libass, PGS and SRT, with per-track delay
  offset for external text subtitles.
- A **stats overlay** shows codec, bitrate, HDR type, audio output
  (passthrough or PCM), sample rate, channels and dropped frames, and a
  Playback settings page lists what was detected with manual overrides.
  Gesture controls (brightness/volume/seek) and trickplay seek thumbnails
  round out the player.

### Server admin dashboard

Available to Jellyfin administrators (and a reduced view for everyone):

- Active sessions with per-user activity and playback control, plus a
  home-screen widget listing live sessions
- User and device management, scheduled tasks, live server logs, libraries
- Plugin management and server settings pages
- Activity log and API key administration
- Multi-server support: save profiles and switch accounts without signing out

### Requests, calendar and media management

- **Seerr/Jellyseerr:** browse trending and popular titles, request movies and
  series, track your own requests — admins get the approval queue
- **Calendar:** upcoming episodes and releases from Radarr/Sonarr in list,
  week and month views
- **Media manager (admin):** Radarr/Sonarr integration — browse, edit, monitor
  seasons and episodes, rename files, interactive release search + grab,
  manual import and delete
- **qBittorrent:** live transfer monitor with pause/resume and speed-limit
  toggling
- **Bazarr and Prowlarr** integration for subtitle and indexer management

### Android TV and Fire TV

A dedicated 10-foot UI built with Compose for TV activates automatically on
television devices:

- Persistent top-bar navigation (Home, Search, Seerr, Calendar, More) with a
  D-pad focus model throughout — autofocus, focus restoration and
  scroll-to-top behaviour tuned for remotes
- Detail pages with a backdrop masthead and an icon action bar mirroring the
  phone layout
- Context menus on cards (menu/long-press): play, mark watched, favourite,
  queue and more — scoped to the item under focus
- Playlist reordering, queue view and album track lists designed for the
  remote
- Android TV home-screen channels for Continue Watching and Recently Added
  where the platform supports them
- Phone admin screens are reachable via D-pad on TV as pushed routes

## Requirements

- Jellyfin server **12.0 or newer**
- Android **7.0 (API 24)** or newer; a TV build activates automatically on
  Android TV / Fire TV devices
- To build: JDK 17+, Android SDK platform 37

## Building

```sh
./gradlew assembleDebug   # debug APK in app/build/outputs/apk/debug/
./gradlew assembleRelease # release APK in app/build/outputs/apk/release/
./gradlew test            # shared (JVM + Android host) and app unit tests
```

Release builds are signed when `keystore.properties` exists in the repo
root — copy `keystore.properties.example`, fill in the keystore path and
passwords, and keep the file out of version control (it is gitignored).
Without it `assembleRelease` produces an unsigned APK.

Debug builds can be pre-seeded with a test server and service credentials:
copy `dev.credentials.properties` (see `AGENTS.md` for the keys) into the
repo root — it is gitignored and never read into release builds.

## Testing playback from adb

Debug builds add two exported entry points, so devices without a TV layout
yet (Fire TV) and files that are not on a server can be tested. Release
builds do not contain them.

```sh
APP=com.pyoungws.cobaltwolf
# Play a file or URL directly (no server involved; the last fallback level is not available)
adb shell am start -n $APP/.player.DebugPlayerActivity -d http://192.168.1.10:8089/sample.mkv --ez stats true
# Play a library item of the signed-in user (all three fallback levels)
adb shell am start -n $APP/.player.DebugPlayerActivity --es item_id <jellyfin item id>
# Open the Playback settings screen
adb shell am start -n $APP/.ui.settings.DebugPlaybackSettingsActivity
adb logcat -s CobaltWolfPlayback   # detected capabilities, fallbacks, periodic stats
```

Optional extras override the saved Playback settings first:
`--es decoding Auto|PreferLocal|AlwaysTranscode`,
`--es audio_output Auto|PcmOnly`, `--es hdr Auto|SdrOnly`,
`--ez dolby_vision true|false`, `--ez styled_subtitles true|false`,
`--ez stats true|false`. `--es audio_mime audio/vnd.dts` selects an audio
track by type, `--es subtitle_mime application/pgs` (or `text/x-ssa`,
`application/x-subrip`) turns on the first subtitle track of that type and
`--ez over_lock_screen true` plays on a locked device.

## Stack

- Kotlin, with a Kotlin Multiplatform `shared` module for networking, models
  and business logic
- Jetpack Compose (phone/tablet) and Compose for TV, Material 3
- Media3/ExoPlayer with an FFmpeg decoder extension and libass subtitles
- Official Jellyfin Kotlin SDK; Ktor for Seerr/arr integrations
- MVVM with coroutines/Flow, Koin DI, Coil 3

See `SPEC.md` for the full product spec and `AGENTS.md` for
contributing conventions.

## Licensing

Cobalt Wolf is licensed under [GPL v3](LICENSE).

### FFmpeg

Software decoding uses [FFmpeg](https://ffmpeg.org), which is licensed under
the **GNU Lesser General Public License (LGPL) version 3 or later** in the
build this app ships. The build is configured with `--enable-version3` and
**without** `--enable-gpl` and `--enable-nonfree`, so it contains none of
FFmpeg's GPL-only or non-free parts (the libraries report "LGPL version 3 or
later"). Only decoders are enabled.

- FFmpeg is shipped as separate shared libraries (`libavcodec.so`,
  `libavutil.so`, `libswresample.so`, `libswscale.so`) that the app loads
  dynamically, so they can be replaced with a modified build, as the LGPL
  requires.
- The binaries come from [NextLib](https://github.com/anilbeesetti/nextlib)
  (`io.github.anilbeesetti:nextlib-media3ext`, GPL v3), which also provides the
  Media3 renderers around them. Its `ffmpeg/setup.sh` at the tag matching the
  version in `gradle/libs.versions.toml` is the complete build script: FFmpeg
  9.0.1 source from ffmpeg.org with the exact configure flags, plus
  [dav1d](https://code.videolan.org/videolan/dav1d) (AV1 decoder,
  BSD 2-Clause) and [Mbed TLS](https://github.com/Mbed-TLS/mbedtls)
  (Apache 2.0) linked into `libavcodec.so`. To use your own FFmpeg, build with
  that script and replace the four libraries.
- FFmpeg is a trademark of Fabrice Bellard. This project is not affiliated
  with the FFmpeg project.

### Other third-party components

- [Jellyfin Kotlin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin): LGPL v3
- [libass-android](https://github.com/peerless2012/libass-android) (MIT),
  bundling [libass](https://github.com/libass/libass) (ISC), FreeType (FTL),
  FriBidi (LGPL v2.1+) and HarfBuzz (MIT)
- AndroidX including Media3, Kotlin, kotlinx, Koin, Coil: Apache 2.0
