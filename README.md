# Blue Wolf Player

Blue Wolf Player is an open-source Android Jellyfin client that combines a
media player with a server admin dashboard, plus Seerr/Jellyseerr request
support for all users. See `docs/SPEC.md` for the full product spec.

## Features (planned)

- Browse and play movies, series, music, books, live TV and photos
- Native video player with subtitle/audio track switching, PiP, and SyncPlay
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

Licensed under [GPL v3](LICENSE).
