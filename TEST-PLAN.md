# Cobalt Wolf — Full Product Test Plan

Test run: **2026-11-14** · Build: signed debug (release-signed, DevSeed-enabled, includes menu fix `86db56a`)
Devices: Fire TV Stick (Fire OS, network adb) + Nokia G50 phone (Android 13, USB adb)
Server: owner's live Jellyfin + Servarr stack · Users: **Darr** (admin) + **Poppy** (viewer, passwordless)

Status legend: ⬜ pending · ✅ pass · ❌ fail · ⚠️ partial/note

## A. Build & packaging

| # | Test | Result | Notes |
|---|------|--------|-------|
| A1 | `./gradlew test` — all unit tests pass | ✅ | EXIT:0 |
| A2 | `assembleDebug` + `assembleRelease` compile clean | ✅ | both built this session |
| A3 | Release APK signed with release cert, credential scan clean | ✅ | verified during v0.1.4 make-release |
| A4 | Installer scripts pass `bash -n` | ✅ | install.sh, bundle.sh, build-iso.sh, ocms-firstboot.sh, lib/*.sh |
| A5 | `dist/` artifacts exist; ISO contains `/ocms/bundle/cobalt-wolf.apk`; SHA256SUMS matches | ✅ | rebuilt for v0.1.4 — ISO 4.9 GB, offline tarball 1.8 GB, online 36 KB; bundled apk SHA c977a867… (== published v0.1.4 asset), all sums verified |

## B. First run & authentication (TV)

| # | Test | Result | Notes |
|---|------|--------|-------|
| B1 | Server-connect screen focuses server field first | ⚠️ | not exercised this run — server remembered, sign-out lands on user picker; connect screen verified on phone sweep |
| B2 | "Who's watching" focuses first user tile | ✅ | picker shows Darr/Poppy tiles, focus on first tile |
| B3 | Credentials form focuses correct first field | ✅ | Poppy tile → Password field focused |
| B4 | Sign-in reaches home screen | ✅ | Poppy empty-password → home; admin via DevSeed reseed |

## C. Home & top navigation (TV, admin)

| # | Test | Result | Notes |
|---|------|--------|-------|
| C1 | Home loads Libraries + content rows | ✅ | Libraries + Continue Watching/Next up/Recently added rows |
| C2 | Top bar (Home/Search/Seerr/Calendar/More) reachable via D-pad | ✅ | all five sections opened via D-pad nav |
| C3 | Card context menu (MENU key) opens item menu | ✅ | Play/View details/Watch together/Mark watched/Favorite, first item focused |

## D. Browsing (TV, admin)

| # | Test | Result | Notes |
|---|------|--------|-------|
| D1 | Movies grid loads items | ✅ | full poster grid |
| D2 | Deep-scroll → open item → BACK restores grid position | ✅ | scroll + focus both restored after fix — `restoreFocusId` (rememberSaveable) refocuses the opened card in TvLibraryScreen/TvItemBrowseScreen; verified on device |
| D3 | TV Shows grid loads | ✅ | incl. unwatched-episode badges |
| D4 | Music Videos library browsable | ✅ | artist/title cards load |

## E. Search (TV)

| # | Test | Result | Notes |
|---|------|--------|-------|
| E1 | Search opens with field focused | ✅ | EditText focused, IME opens on center |
| E2 | Typed query returns "On this server" results | ✅ | "wizard" → Wizard of Oz |
| E3 | "From Seerr" section returns remote results | ✅ | section header reads "Seerr"; remote results incl. In-library badge |

## F. Detail pages (TV, admin)

| # | Test | Result | Notes |
|---|------|--------|-------|
| F1 | Movie detail opens, Play/Resume focused | ✅ | Wizard of Oz: meta/badges, action row focused (Resume) |
| F2 | Audio/Subtitles buttons present when streams exist | ✅ | "Audio: default" + "Subtitles: default" buttons |
| F3 | More menu opens with first item focused | ✅ | "Play from beginning" focused (fix `86db56a` verified) |
| F4 | Menu navigates full list; edges trapped; BACK dismisses; focus restores | ✅ | trapped on last item (Manage) after 8×DOWN; LEFT/RIGHT can't escape; BACK → focus back on Resume |
| F5 | Favorite toggle from menu works | ✅ | heart icon toggles filled/outline |
| F6 | Episode MENU key opens episode context menu | ✅ | S1:E1 card → View details/Play/Favorite…, first item focused |
| F7 | Person click opens filmography page | ✅ | Liev Schreiber → person page (photo, bio, action buttons) |
| F8 | BoxSet/collection detail renders without stream buttons | ✅ | Ali G Collection + Star Trek: ARC — Play/Shuffle/Contents section |
| F9 | Season/episode rows present on series page | ✅ | "Seasons" chips (Season 1) + "Episodes" landscape cards (S1:E1–E4, watched badges) |
| F10 | Episode detail page shows sibling sections | ✅ | Cyberpunk S1:E3: overview, Chapters, Cast & crew, Seasons chips (S1/S2), Episodes row — same layout as series page; no Media info row on TV |
| F11 | UP from actions row to top bar scrolls page to top | ✅ | page was mid-scroll on actions row; UP → topbar focus + list animated back to masthead (fix `LaunchedEffect(columnFocused…)`); DOWN re-enters on Play |

## G. Playback (TV)

| # | Test | Result | Notes |
|---|------|--------|-------|
| G1 | Play starts playback | ✅ | S1:E2 via D-pad, from beginning |
| G2 | `CobaltWolfPlayback` stats line logged (codec/decoder) | ✅ | DirectPlay hevc 2160x1080, eac3 hw, displayHz 23.976 rate-match |
| G3 | Pause + resume via remote | ✅ | MEDIA_PLAY_PAUSE froze position; PLAY resumed |
| G4 | Seek forward works | ✅ | MEDIA_FAST_FORWARD jumped ~30s |
| G5 | BACK returns to detail page | ✅ | exited player cleanly to app hierarchy (37-min playback also verified — no sleep, KEEP_SCREEN_ON held) |

## H. Seerr integration (TV)

| # | Test | Result | Notes |
|---|------|--------|-------|
| H1 | Seerr tab loads signed-in (Jellyfin-cred auto-login) | ✅ | signed in, no password prompt |
| H2 | Discover/browse content renders | ✅ | Trending + Movies/Series with request badges |
| H3 | Seerr detail page opens from a result | ✅ | Avengers: Doomsday w/ Requested status |

## I. Calendar (TV)

| # | Test | Result | Notes |
|---|------|--------|-------|
| I1 | Calendar shows upcoming items from Sonarr/Radarr | ✅ | List/Week/Month modes, real Radarr+Sonarr entries with Downloaded status |

## J. Admin & services (TV)

| # | Test | Result | Notes |
|---|------|--------|-------|
| J1 | Server dashboard opens, sessions visible | ✅ | Active sessions (12), Downloads, Bazarr (312 wanted), Prowlarr (11 indexers), Manage server entries; Sessions page lists live clients (Fire TV/Nokia G50/Seerr) |
| J2 | Services: Radarr/Sonarr/Bazarr/Prowlarr/qBit all Saved+Connected | ✅ | Services screen: Radarr+Sonarr URLs + masked API keys, Save/Test connection; Bazarr/Prowlarr/qBit live on dashboard cards |
| J3 | Downloads screen lists torrents | ✅ | ↓0 ↑0 B/s, Turtle mode on, "No torrents", Services shortcut |

## K. Normal user — Poppy (TV)

| # | Test | Result | Notes |
|---|------|--------|-------|
| K1 | Can sign in as Poppy (passwordless) | ✅ | tile → empty-password submit → home |
| K2 | Viewer dashboard: no sessions/service cards/management section | ✅ | Servers/Playback/Recently added/About only |
| K3 | Downloads + Services routes unreachable/hidden | ✅ | no entry points anywhere in viewer UI |
| K4 | Request/media-manager admin destinations absent from More menu | ✅ | More menu = My requests/Dashboard/About/Sign out (no Notifications bell) |
| K5 | Browse + detail pages still work for viewer | ✅ | TV Shows grid + series detail + Seerr (manual empty-pw sign-in → Discover w/ badges) |

## L. System (TV)

| # | Test | Result | Notes |
|---|------|--------|-------|
| L1 | Sign out returns to login flow | ✅ | confirm dialog → "Who's watching" picker |
| L2 | Relaunch re-seeds (debug) and lands on home | ✅ | force-stop+launch resumed last session (Poppy) to home |
| L3 | BACK from Home exits cleanly | ✅ | exits to Fire TV launcher |
| L4 | Subtitle default = forced English or off | ✅ | Dashboard → Playback settings shows "Forced only" + lang "eng" after install over stored old default (resolveSubtitlePreference migration) |

## P. Phone UI (Nokia G50)

| # | Test | Result | Notes |
|---|------|--------|-------|
| P1 | App launches on phone, sign-in or DevSeed → home | ✅ | DevSeed auto-signed in as admin |
| P2 | Home feed + bottom/top nav renders | ✅ | Libraries, Continue Watching, Next up, Recently added rows; top bar Search/Seerr/Calendar/Bell/More |
| P3 | Library grid opens, scrolls | ✅ | Movies grid, poster grid, filter chips (All/Genres/Letters/Suggested) |
| P4 | Item detail page: play, audio/sub, options menu | ✅ | Backrooms detail: rating/codec badges, genres, cast & crew, ⋮ menu (Watch together/Download/Trailers/Manage) |
| P5 | Playback starts + controls work | ✅ | Backrooms: hevc HW decode 1280x690, AC3 via ffmpeg, position 4→45s, 0 dropped; rotated landscape |
| P6 | Search returns results | ✅ | "wizard" → "On this server" (Wizard of Oz 1939) + "Seerr" section (In library badge); filter chips |
| P7 | Seerr + Calendar tabs work | ✅ | Seerr Discover signed in (Trending/Movies/Series w/ request badges); Calendar Upcoming List/Week/Month w/ real Radarr+Sonarr entries |
| P8 | More menu: requests/servers/settings/dashboard present (admin) | ✅ | Dashboard: Active sessions(11)/Downloads/Bazarr/Prowlarr/Servers/Playback cards + Manage server (Request approvals, Media manager, Users, Devices, Libraries, Tasks, Logs, Activity, API keys, System, Plugins, Server settings, Services); Sessions page lists live sessions w/ progress; Downloads shows qBittorrent transfer info; My requests lists statuses |
| P9 | Viewer (Poppy): admin entries hidden | ✅ | Signed in via public tile w/ empty password. Dashboard reduced → Servers/Playback/Recently added/About only; no sessions/Downloads/Bazarr/Prowlarr/Manage server/Users; no Notifications bell in top bar; Seerr Discover works (Jellyfin-auth sign-in, season-picker request UI) |

## Results summary

**A. Build & packaging:** 5/5 pass.

**P. Phone (Nokia G50):** 9/9 pass — admin + Poppy both verified.

**B–L. TV (Fire Stick):** complete — admin + Poppy verified. 1 partial (B1 not exercised, server remembered), 1 issue found & fixed this run (D2 focus restore).

### Known issues
- **Fire Stick sleeps during playback (reported by user):** the device goes to sleep while viewing. **Fix queued: implement + verify immediately after the next Fire Stick test sweep completes — do not fix before then.**
  - **Phone checked 2026-11-14 — NOT affected:** `PlayerActivity` already sets `FLAG_KEEP_SCREEN_ON` on its window, and phone playback runs in that activity. Verified live: `screen_off_timeout` dropped to 30s mid-playback, screen stayed `mWakefulness=Awake`, position advanced through the window (29s→44s), no sleep. Phone timeout restored to 1800s.
  - Fire Stick still needs an on-device look — **user reports sleep at ~28 min** (≈ a 30-min display timeout). TV playback launches `PlayerActivity` too, so the flag is *also* set on the stick — meaning Fire OS is likely sleeping on its own power path (Fire OS has a known inactivity sleep that ignores `FLAG_KEEP_SCREEN_ON`).
  - **When the stick is free, verify before fixing:** (a) `dumpsys window` / `dumpsys power` during playback to confirm the flag is on the window and what sleep driver fired; (b) the stick's sleep/screensaver setting.
  - **Candidate fixes if the flag is proven insufficient:** `playerView.keepScreenOn = true` on the video surface, or a `PowerManager` wake lock held while `isPlaying`.
  - **30-min live test 2026-11-14 (Fire Stick, S1:E2 "I Want My Halo Back", played from beginning via D-pad): did NOT reproduce.** 35 consecutive 60s samples all `mWakefulness=Awake`, `PlayerActivity` foreground throughout, `fl=KEEP_SCREEN_ON` confirmed on the window, DirectPlay 4K HEVC + EAC3 hw-decode, 0 dropped, position ran 0→2206s (~37 min) past the reported ~28 min mark with no sleep event. `screen_off_timeout=300000` (5 min) on the stick — the flag held regardless.
  - **Likely explanations for the user's observed sleep:** (a) an older build before the flag existed, (b) playback via a path that leaves `PlayerActivity` (external-player handoff, trailer `ACTION_VIEW`), or (c) the TV's own auto-sleep/HDMI-CEC timer rather than the stick. If it recurs, capture `dumpsys power` + the stick's sleep settings at the moment it happens.
  - **Verdict after the FS sweep: no fix applied — nothing to fix.** The keep-screen-on path was exercised end-to-end (D-pad start, 37 min, past the reported mark) and held. The app already does the right thing on this device/build.

### Notes / findings
- **Issue found & fixed this sweep — TV grid focus restore (was D2 ⚠️):** after deep-scroll → open → BACK, `LazyVerticalGrid` restored scroll but focus fell to the top bar. Fix: `restoreFocusId` (rememberSaveable) records the opened card's id, the matching card gets a `FocusRequester` (new optional param on `TvPosterCard`), and a `LaunchedEffect` retries `requestFocus` until attached. Applied to `TvLibraryScreen` and `TvItemBrowseScreen` (same grid pattern). Verified on device: BACK refocuses the opened card.
- **Transient observation (not reproduced):** once, the More dropdown stayed rendered over the dashboard after selecting an item; retry dismissed correctly (`menuOpen=false` fires on every item). Watching it — likely a one-off popup/animation race.
- **Seerr request submitted during test:** "Heart of the Beast" movie — auto-approved (admin has auto-approve). Safe to leave or decline in Seerr.
- **Phone scroll-restore verified:** Movies grid position preserved after opening a detail and pressing BACK (same fix as TV).
- **Poppy passwordless confirmed:** her tile opens a password form but empty-password submit signs in — for both Jellyfin and the Jellyfin-backed Seerr login. (API's `haspw:true` refers to the field existing, not a required value.)
- **Phone login UX note:** Android's autofill/"Save to Proton Pass" prompt appears over the app right after a successful sign-in on a device with a password manager — a one-time system prompt, not an app bug, but it briefly obscures the transition to home.
- **Admin user ≠ public tile:** the credentials-file admin (`badted86`, non-public) must use "Enter a username"; the Darr/Poppy tiles are Jellyfin public users only.
