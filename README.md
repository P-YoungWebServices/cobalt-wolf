# Blue Wolf Player

Blue Wolf Player is an open-source Android Jellyfin client that combines a
media player with a server admin dashboard, plus Seerr/Jellyseerr request
support for all users. See `docs/SPEC.md` for the full product spec.

## Features (planned)

- Browse and play movies, series, music, books, live TV and photos
- Native video player with subtitle/audio track switching, PiP, and SyncPlay
- High-end playback: 4K, HDR10, HDR10+ and Dolby Vision direct play, lossless
  audio passthrough, local FFmpeg decoding, styled ASS/SSA subtitles
- Server admin dashboard: sessions, user/device management, scheduled tasks,
  live logs, libraries, multi-server support
- Seerr/Jellyseerr requests: browse and request content, plus an admin
  approval queue
- Android phone, tablet, Android TV and Fire TV layouts (one codebase)

**Blue Wolf Player is a third-party app and is not affiliated with or
endorsed by the Jellyfin project.**

## Stack

- Kotlin, Kotlin Multiplatform (shared module for networking, models and
  business logic)
- Jetpack Compose for phone/tablet and TV UI
- Media3/ExoPlayer playback with an FFmpeg decoder extension
- Official Jellyfin Kotlin SDK, Ktor for Seerr
- MVVM with coroutines/Flow, Koin or Hilt for DI, Room or SQLDelight for
  local storage

## High-end playback

The player detects what the device, its display and its audio output can do
each time playback starts, and picks the best path they support:

- **Video:** H.264, HEVC, AV1 and VP9 up to the size the device decoders take
  (4K where available); HDR10, HDR10+, HLG and Dolby Vision (profiles 5, 7
  and 8) when both the decoder and the display support them. Dolby Vision the
  device cannot show falls back to its HDR10/HLG/SDR base layer, HDR10+ to
  HDR10, and HDR on an SDR display is tone-mapped by the server.
- **Audio:** Dolby Digital, Dolby Digital Plus (including Atmos), Dolby TrueHD
  (including Atmos), DTS and DTS-HD MA (including DTS:X) are passed through
  untouched when the HDMI receiver or TV reports the format; otherwise they are
  decoded to PCM on the device.
- **Fallback order:** direct play (device decoders, passthrough), then local
  software decoding with FFmpeg, then a server transcode. A decoder that fails
  mid-playback moves down the same order and resumes where it stopped.
- **Display matching (TV):** the display mode is switched to the content's
  frame rate, and HDR output is only requested while HDR is playing.
- **Subtitles:** ASS/SSA rendered by libass with full styling (fonts,
  positioning, animation), PGS and SRT.
- **Playback settings** (Home > Playback) lists what was detected and what
  will be used per format, and has manual overrides. A **stats overlay** in the
  player shows codec, bitrate, HDR type, audio output (passthrough or PCM),
  sample rate, channels and dropped frames.

### Testing playback from adb

Debug builds add two exported entry points, so devices without a TV layout yet
(Fire TV) and files that are not on a server can be tested. Release builds do
not contain them.

```sh
APP=com.pyoungws.bluewolfplayer
# Play a file or URL directly (no server involved; the last fallback level is not available)
adb shell am start -n $APP/.player.DebugPlayerActivity -d http://192.168.1.10:8089/sample.mkv --ez stats true
# Play a library item of the signed-in user (all three fallback levels)
adb shell am start -n $APP/.player.DebugPlayerActivity --es item_id <jellyfin item id>
# Open the Playback settings screen
adb shell am start -n $APP/.ui.settings.DebugPlaybackSettingsActivity
adb logcat -s BlueWolfPlayback   # detected capabilities, fallbacks, periodic stats
```

Optional extras override the saved Playback settings first: `--es decoding
Auto|PreferLocal|AlwaysTranscode`, `--es audio_output Auto|PcmOnly`, `--es hdr
Auto|SdrOnly`, `--ez dolby_vision true|false`, `--ez styled_subtitles
true|false`, `--ez stats true|false`. `--es audio_mime audio/vnd.dts` selects
an audio track by type, `--es subtitle_mime application/pgs` (or `text/x-ssa`,
`application/x-subrip`) turns on the first subtitle track of that type and `--ez over_lock_screen true` plays on a locked
device.

## Licensing

Blue Wolf Player is licensed under [GPL v3](LICENSE).

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
