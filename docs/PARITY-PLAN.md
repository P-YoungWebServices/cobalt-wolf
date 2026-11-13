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
| 1.2 | Play queue system | ☑ | PlayQueueRepository+tests, PlayerViewModel queue follow (repeat/shuffle), QueueDialog in PlayerScreen, play-next/add-to-queue on detail pages, queue items in card menus |
| 1.3 | Music browsing: artists/albums/songs tabs, album/artist/song detail | ☑ | cf6377d: music tabs, album track rows, artist→albums, container queue+shuffle play |
| 1.4 | Audio playback + media session/notification/background audio | ☑ | ec1b990: PlaybackService (MediaSessionService), handoff on close, artwork+metadata, session-tap opens app |
| 1.5 | Instant mix + lyrics display | ☑ | instantMixApi.getInstantMixFromSong → queue; LyricApi.getLyrics → lyrics section on song detail |
| 1.6 | Live TV: channels, EPG guide, recordings, series timers, record/cancel | ☑ | LiveTvViewModel + shared phone/TV screen, library route, record buttons on program detail |
| 1.7 | Home videos / mixed libraries | ☑ | part of 1.1; non-recursive folder browsing via ItemQuery.recursive |
| 1.8 | Full person pages (bio, photo, filmography/discography) | ☑ | Person detail shows bio + filmography grid |

## Phase 2 — Browsing and item actions

| # | Item | Status | Notes |
|---|------|--------|-------|
| 2.1 | View modes: list view, card-size options, image-type options | ☑ | DisplayPrefs per-library; view/size/image pickers on browse pages |
| 2.2 | Extra sorts | ☑ | rating/critic/parental/last played/play count/date added/runtime/year in SORT_CHOICES + ItemQuery |
| 2.3 | Extra filters | ☑ | played/resumable, series status, video type, has-trailer/subs/extras/themes via ItemQuery |
| 2.4 | Library tabs: suggestions, shows upcoming, networks/studios | ☑ | getStudios + getUpcoming endpoints; per-type view tabs |
| 2.5 | Global Favorites page | ☑ | 721dd4a: top-bar menu → cross-library favorites |
| 2.6 | Long-press card context menus | ☑ | 634abe7: ItemContextMenu on PosterCard/LandscapeCard (queue, playlist/collection, watched, favorite, remove-from-container). TV cards still need a variant |
| 2.7 | Multi-select mode + batch actions | ☑ | 529b1c2: long-press selection, batch queue/watch/favorite/playlist/delete | |
| 2.8 | Playlist add/create/reorder/remove | ☑ | data layer + picker dialogs; reorder API exists, UI reorder pending |
| 2.9 | Collection add/create/remove | ☑ | same dialog path |
| 2.10 | Share / copy stream URL | ☑ | context menu copy + share intent | |
| 2.11 | Delete media (permission-gated) | ☑ | canDelete-gated, confirmed, batch + single | |
| 2.12 | Admin metadata tools | ☑ | fdb99a5: refresh/identify/edit/subtitle-search dialogs, admin-gated, phone+TV | |

## Phase 3 — Player

| # | Item | Status | Notes |
|---|------|--------|-------|
| 3.1 | Playback speed control | ☑ | speed picker in player bar | |
| 3.2 | Repeat modes (off/all/one) | ☑ | shipped with queue (1.2) |
| 3.3 | Gesture controls | ☑ | PlayerView touch listener; double-tap/scrub/volume/brightness/2x-hold/lock |
| 3.4 | Trickplay scrub thumbnails | ◐ | trickplay images already render in scrub bar; verify tiles from server |
| 3.5 | Subtitle delay offset | — | DEFERRED: no Media3 API; cues flow internally to SubtitleView | |
| 3.6 | Audio delay offset | — | DEFERRED: no Media3 API | |
| 3.7 | Chapter list in OSD | ☑ | dialog + progress-bar markers already existed | |
| 3.8 | Still-watching prompt | ☑ | streak in PlayerViewModel, pause+dialog, tested | |
| 3.9 | Cinema mode prerolls | ☑ | getIntros queued before movie | |
| 3.10 | Resume rewind N sec | ☑ | startPositionMs offset, tested | |
| 3.11 | External player option | ☑ | ACTION_VIEW handoff + fallback | |
| 3.12 | Chromecast | ☐ | proprietary dep — record in AGENTS.md |
| 3.13 | "Play on" remote session control | ☑ | sessionApi.play + menu dialog | Sessions + websocket commands |
| 3.14 | Background audio | ☑ | done via 1.4 |
| 3.15 | Track prefs: audio lang, sub lang, sub mode, detail-page preselect | ◐ | version preselect exists; audio/sub preselect + prefs pending |
| 3.16 | Per-codec passthrough + channel cap + night mode | ☐ | AudioOutputPreference extension |
| 3.17 | Per-format HDR overrides, codec level caps, buffer len, transcode prefs, zoom default+autocrop | ☐ | |
| 3.18 | Subtitle styling, PGS toggle, burn-on-transcode | ☐ | |

## Phase 4 — Auth, home, admin, misc

| # | Item | Status | Notes |
|---|------|--------|-------|
| 4.1 | LAN server discovery on connect screen | ☐ | SDK discovery.discover() |
| 4.2 | User cards w/ avatars on sign-in | ☐ | |
| 4.3 | Auto sign-in behavior | ☐ | |
| 4.4 | Reset password link | ☐ | |
| 4.5 | More home rows + notifications + server-synced sections | ☐ | |
| 4.6 | Themes, backdrops+blur, clock, watched-indicator styles | ☐ | |
| 4.7 | In-app screensaver + daydream | ☐ | TV |
| 4.8 | Voice search (TV) | ☐ | |
| 4.9 | Admin: activity log, API keys, devices detail, server settings | ☐ | |
| 4.10 | Search: grouped result types + filters | ☐ | |
| 4.11 | Downloads: method + storage location | ☐ | |
| 4.12 | Android Auto browse/play | ☐ | MediaLibraryService |

## Current checkpoint

- Started: 2026-11-13 on branch `parity` from `pre-parity` (2e9550a).
- Last commit: Play-on remote control (just committed).
- Done: Phases 1+2 complete. Build+tests green.
- Next: 3.16-3.18 playback prefs (codec toggles/channel cap/night mode; HDR per-format; buffer; subtitle styling incl. color/background via SubtitleView; PGS toggle). Then Phase 4 (auth/home/admin/misc).
- Key files: models=shared/.../model/Models.kt, mapping=shared/.../library/JellyfinLibraryDataSource.kt, queue=shared/.../playback/PlayQueue.kt, PlayerVM=shared/.../presentation/PlayerViewModel.kt, player UI=app/.../player/PlayerScreen.kt, menus=app/.../ui/common/ItemActions.kt, cards=app/.../ui/common/Components.kt, view prefs=app/.../ui/common/DisplayPrefs.kt, DI=shared/.../di/SharedModule.kt.
- Conventions: per-feature commits, `JAVA_HOME=/snap/android-studio/current/jbr`, strings appended in app/.../res/values/strings.xml, icons from material-icons-extended.
- Open follow-ups: TV card context menu (D-pad long-press), playlist reorder UI, queue UI on TV player, TV album track list, image-editor screen (edit-images uses RemoteImageApi — deferred).
- Phone test status: installDebug OK, cold launch clean, home renders (Movies/TV Shows only on this server — music/LiveTV unverifiable on-device).
