# Parity Implementation Plan

Working document tracking the official-client parity build-out. Update status
as each item lands. Everything runs on the `parity` branch, one commit per
feature; `pre-parity` tag marks the restore point. Never pushed.

Reference sources live in `~/jellyfin-reference/` (jellyfin-web v12.4,
jellyfin-android v2.7.3, jellyfin-androidtv v0.20.0 — all GPL v2,
capabilities only, no code reuse).

Status: ☐ pending · ◐ in progress · ☑ done · ⊘ skipped · ⚠ done-untested

## Phase 1 — Foundations: media types + play queue

| # | Item | Status | Notes |
|---|------|--------|-------|
| 1.1 | Extend models: MediaKind + LibraryType | ☑ | commit 4a6e3d6; Song/MusicAlbum/MusicArtist/TvChannel/Program/Recording/Folder/Person; Music/LiveTv/HomeVideos/Mixed; canDelete/canDownload/ProgramInfo |
| 1.2 | Play queue system (queue repo, play next/add to queue/play all from here, queue UI, shuffle) | ☐ | Foundation for music + C1 |
| 1.3 | Music browsing: artists/albums/songs/genres tabs, album/artist/song detail pages | ☐ | |
| 1.4 | Audio playback engine + now-playing screen (phone + TV) | ☐ | Media3 audio, media session, notification |
| 1.5 | Instant mix + lyrics display | ☐ | instantMix API, lyrics API |
| 1.6 | Live TV: channels + playback | ☐ | TvChannel items |
| 1.7 | Live TV: EPG guide grid | ☐ | |
| 1.8 | Live TV: recordings, scheduled, series timers, record/cancel actions | ☐ | |
| 1.9 | Home videos / mixed libraries | ☐ | |
| 1.10 | Full person pages (bio, photo, filmography/discography) | ☐ | replaces plain grid |

## Phase 2 — Browsing and item actions

| # | Item | Status | Notes |
|---|------|--------|-------|
| 2.1 | View modes: list/table view, card-size options, image-type options | ☐ | |
| 2.2 | Extra sorts: parental rating, critic, last played, play count, date episode added, runtime, year | ☐ | ItemBrowseScreen SORT_CHOICES |
| 2.3 | Extra filters: played/resumable, series status, video type, has-trailer/subs/extras/themes | ☐ | ItemQuery extension |
| 2.4 | Library tabs: per-type suggestions, shows upcoming, networks/studios | ☐ | |
| 2.5 | Global Favorites page | ☐ | |
| 2.6 | Long-press card context menus | ☐ | phone + TV variants |
| 2.7 | Multi-select mode + batch actions | ☐ | |
| 2.8 | Playlist add/create/edit/reorder/remove | ☐ | |
| 2.9 | Collection add/create/remove | ☐ | |
| 2.10 | Share / copy stream URL | ☐ | |
| 2.11 | Delete media (permission-gated) | ☐ | |
| 2.12 | Admin metadata tools (edit, refresh, identify, images, subtitle download) | ☐ | inside app |

## Phase 3 — Player

| # | Item | Status | Notes |
|---|------|--------|-------|
| 3.1 | Playback speed control | ☐ | |
| 3.2 | Repeat modes (off/all/one) | ☐ | needs queue for 'all' |
| 3.3 | Gesture controls (double-tap, swipe seek, brightness/volume, press-hold speed, lock) | ☐ | phone |
| 3.4 | Trickplay scrub thumbnails | ☐ | trickplay API + tiles |
| 3.5 | Subtitle delay offset | ☐ | |
| 3.6 | Audio delay offset | ☐ | |
| 3.7 | Chapter list in OSD | ☐ | |
| 3.8 | Still-watching prompt | ☐ | episode-count inactivity |
| 3.9 | Cinema mode prerolls | ☐ | trailers before movie |
| 3.10 | Resume rewind N sec | ☐ | setting + offset on resume |
| 3.11 | External player option (VLC/MX/mpv) | ☐ | intent handoff |
| 3.12 | Chromecast (Cast SDK, Play Services only) | ☐ | proprietary dep — record in AGENTS.md |
| 3.13 | "Play on" remote session control | ☐ | Sessions + websocket commands |
| 3.14 | Background audio | ☐ | part of 1.4 media session |
| 3.15 | Track prefs: audio lang, sub lang, sub mode, detail-page preselect | ☐ | |
| 3.16 | Per-codec passthrough toggles + channel cap + night mode | ☐ | AudioOutputPreference extension |
| 3.17 | Per-format HDR overrides, codec level caps, buffer len, transcode prefs, zoom default+autocrop | ☐ | |
| 3.18 | Subtitle styling (color/weight/bg/stroke/position/font), PGS toggle, burn-on-transcode | ☐ | |

## Phase 4 — Auth, home, admin, misc

| # | Item | Status | Notes |
|---|------|--------|-------|
| 4.1 | LAN server discovery on connect screen | ☐ | SDK discovery.discover() broadcast |
| 4.2 | User cards w/ avatars on sign-in | ☐ | public users list |
| 4.3 | Auto sign-in behavior (last/specific/ask) | ☐ | |
| 4.4 | Reset password link | ☐ | ForgotPassword API |
| 4.5 | More home rows (live TV, resume audio, now playing) + notifications row + server-synced sections | ☐ | DisplayPreferences server storage |
| 4.6 | Themes, backdrops+blur, clock options, watched-indicator styles | ☐ | |
| 4.7 | In-app screensaver + daydream | ☐ | TV |
| 4.8 | Voice search (TV) | ☐ | |
| 4.9 | Admin: activity log, API keys, devices detail, server settings pages | ☐ | |
| 4.10 | Search: grouped result types + filters | ☐ | |
| 4.11 | Downloads: method + storage location | ☐ | |
| 4.12 | Android Auto browse/play | ☐ | MediaLibraryService |

## Current checkpoint

- Started: 2026-11-13 on branch `parity` from `pre-parity` (2e9550a).
- Working on: 1.2 play queue system.
- Next: 1.3 music browsing.
- Done: 1.1 (models). Compile clean, MediaMappingTest green.
- Phone test status: not yet run.
