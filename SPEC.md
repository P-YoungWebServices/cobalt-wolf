# Cobalt Wolf: Product Spec

The product spec for Cobalt Wolf. Save it in the repo as docs/SPEC.md; the Devin prompts are in a separate document.

## 1. Product summary

Cobalt Wolf is a single Jellyfin client that combines two things: a media player (watching your library on phone, tablet and TV) and a server admin dashboard (monitoring and managing the server). It also lets every user browse and request movies and series through Seerr/Jellyseerr directly inside the app, while admins approve or decline those requests.

**Goal:** match the capabilities of JellyWatch Player (TV) and JellyWatch Admin, with all features free and no paywalls.

**Platforms:** Android phone, Android tablet, Android TV and Fire TV for v1. iOS later.

**Servers supported:** Jellyfin 12.0 or newer (the minimum supported by the official Jellyfin Kotlin SDK 1.9.x). Emby is not supported.

## 2. Users and roles

- **Viewer:** any signed-in Jellyfin user. Can browse, play, search, and make requests.
- **Admin:** a Jellyfin user with administrator rights. Sees everything a viewer sees, plus the admin tabs and the request approval queue.

The app detects the role at sign-in from the Jellyfin user policy and shows or hides admin screens accordingly. One app, one codebase.

## 3. Feature list

### Viewing (parity with JellyWatch Player)

- Browse movies, series, music, books, live TV and photos
- Native video player with subtitle and audio track switching
- Continue Watching and Recently Added rows
- Android TV home screen channels for Continue Watching and Recently Added (on devices that support them; Fire TV may not)
- Match frame rate (24p, 25p, 30p) on TV
- SyncPlay (watch together in real time)
- Search across the library
- D-pad friendly 10-foot layout for TV; touch layout for phone and tablet
- Picture-in-Picture on phone and tablet
- Quick Connect and username/password sign-in

* High-end playback: direct play of 4K, HDR10, HDR10+ and Dolby Vision; H.264, HEVC, AV1 and VP9; lossless audio passthrough (Dolby TrueHD and Atmos, DTS-HD MA, DTS:X, Dolby Digital Plus); local FFmpeg decoding when the device cannot decode a format; server transcoding only as a last resort
* Advanced subtitles (ASS/SSA with full styling) and a stats overlay showing codec, bitrate, HDR type, audio output (passthrough or PCM) and dropped frames

- Best-for-the-device playback: detect the device's decoders, HDR support, audio passthrough and connected display or receiver at runtime, then automatically choose the highest-quality path that device can handle; fall back gracefully instead of failing, and let the user override the choice in settings

### Requests (open to all users)

- Search and browse Seerr/Jellyseerr content from inside the app
- Request a movie or series (with season selection for shows)
- See the status of your own requests (pending, approved, available, declined)
- Notifications when a request is approved or becomes available
- Sign in to Seerr using the same Jellyfin account

### Admin (parity with JellyWatch Admin)

- Dashboard with real-time active sessions, plus recently added
- Session details: who is watching, what, progress, codecs, transcoding info
- Send a message to an active session
- User management: view, create, edit, delete
- Device management
- Library details
- Scheduled tasks: start, stop, monitor
- Live server logs with filters
- System info and update status
- Multi-server support (connect to and switch between several servers)
- Request approval queue: approve or decline Seerr requests
- Radarr and Sonarr upcoming calendars
- qBittorrent monitoring and control
- Home screen widget with active sessions
- Push or local notifications for new sessions, new devices, new content and requests
- Customizable dashboard (reorder and toggle sections)

* Radarr and Sonarr media editing: change monitored status, quality profile, root folder and tags on movies and series; rename and delete media files; run automatic and interactive release searches; manual import of files

## 4. Technical approach

- **Language:** Kotlin throughout
- **Structure:** Kotlin Multiplatform with a shared module for networking, models and business logic. Android app module on top. An iOS module can be added later using Compose Multiplatform.
- **UI:** Jetpack Compose for phone and tablet; Compose for TV for the Android TV and Fire TV layout
- **Playback:** Media3 / ExoPlayer
- **Jellyfin access:** the official Jellyfin Kotlin SDK
- **Seerr access:** REST API client in the shared module (Ktor)
- **Architecture:** MVVM with a repository layer; dependency injection with Koin or Hilt; Kotlin coroutines and Flow
- **Storage:** secure storage for tokens; local database (Room or SQLDelight) for server profiles and caching
- **Admin detection:** read the administrator flag from the signed-in Jellyfin user policy

* **Playback engine:** Media3/ExoPlayer with an FFmpeg decoder extension and a three-level fallback: direct play, then local FFmpeg decode, then server transcoding. Audio passthrough uses encoded audio output when the device and receiver support it, with display frame rate and HDR mode matching on TV

## 5. Legal and licensing notes

- Reproduce capabilities, not JellyWatch's code, branding, icons or artwork. Use an original name, logo and visual design.
- The official Jellyfin Android TV client is GPL. If you reuse any of its code, Cobalt Wolf must also be open source under a compatible license. If you only use the Jellyfin SDK and write your own UI, check the SDK license (it is open source) and keep attribution.
- Include the standard disclaimer: Cobalt Wolf is a third-party app and is not affiliated with or endorsed by the Jellyfin project.

* Cobalt Wolf will be released as open source. FFmpeg is LGPL (or GPL, depending on build options): use an LGPL build, keep attribution, and follow its linking rules. The project license is GPL v3, with all code written fresh: read other clients to learn how they handle playback, but do not copy their code unless its license is confirmed compatible (GPL v2-only code is not)

## 6. Milestones

1. **M1:** Project scaffold, server connection and sign-in
2. **M2:** Library browsing and playback, followed by high-end playback (Prompt 2b: 4K, HDR, passthrough, best-for-the-device selection)
3. **M3:** Seerr connection and user requests
4. **M4:** Admin dashboard and sessions
5. **M5:** Admin management (users, devices, tasks, logs, libraries)
6. **M6:** Request approval queue and notifications
7. **M7:** Android TV layout, home screen channels, frame rate matching, SyncPlay
8. **M8:** Multi-server, widgets, Radarr/Sonarr/qBittorrent integrations, followed by Radarr and Sonarr media editing (Prompt 9)
9. **M9:** Polish, testing, release prep
10. **Later:** iOS

## 7. Open decisions

- App name (chosen and checked: Cobalt Wolf), logo and package name
- Emby support: decided against, Jellyfin only
- Decided: GPL v3. Revisit only if you later choose to copy code from a GPL v2-only project
- Where it will be distributed (Google Play, Amazon Appstore, direct APK)
