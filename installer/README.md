# OneClick Media Suite

One-shot **Ubuntu 24.04+** installer that turns a fresh machine into a
complete, optionally VPN-protected media automation stack — then optionally
sideloads the Cobalt Wolf release APK onto a Fire Stick or any Android
device via adb.

## What it installs (all Docker containers)

| Service | Image | Port | Notes |
|---|---|---|---|
| Gluetun | `qmcgaw/gluetun:v3.41.3` | — | VPN gateway + killswitch; owns the netns for the services below |
| qBittorrent | `lscr.io/linuxserver/qbittorrent:5.3.0` | 8080 | all traffic through the VPN |
| Prowlarr | `lscr.io/linuxserver/prowlarr:2.7.0` | 9696 | all traffic through the VPN |
| Sonarr | `lscr.io/linuxserver/sonarr:4.0.20` | 8989 | TV |
| Radarr | `lscr.io/linuxserver/radarr:6.4.4` | 7878 | movies |
| Bazarr | `lscr.io/linuxserver/bazarr:1.6.2` | 6767 | subtitles |
| Jellyfin | `jellyfin/jellyfin:12.4` | 8096 | media server |
| Seerr | `seerr/seerr:v3.5.1` | 5055 | request front-end |
| Homepage | `ghcr.io/gethomepage/homepage:v1.4.5` | — | dashboard with live widgets (no direct port) |
| Dashboard proxy | `nginx:1.30.6-alpine` | 3080 | basic-auth gateway in front of Homepage |
| Deunhealth | `qmcgaw/deunhealth:latest` | — | restarts VPN-side containers when Gluetun bounces (VPN mode only) |
| portforward-sync | local build | — | pushes the VPN-forwarded port into qBittorrent (AirVPN) |

Everything else (Docker Engine + Compose plugin) is installed from Docker's
official apt repository when missing.

## Quick start

```bash
cd OneClickMediaSuite
./install.sh
```

It asks for everything up front — VPN provider and credentials, paths,
timezone, Jellyfin admin account, qBittorrent credentials — then runs
unattended: Docker install → folders → compose → API wiring → credentials
summary → optional Fire Stick phase.

Re-run any time; it is idempotent:

```bash
./install.sh           # all phases (answers from the last run are reused)
./install.sh stack     # resume from a later phase
./install.sh wire      # just re-run the API wiring
./install.sh firestick # just the Fire Stick step
```

Phases: `setup` (prompts) → `docker` → `stack` → `wire` → `summary` → `firestick`.
An answers file at `OneClickMediaSuite/answers.txt` (one value per prompt, in
order) skips the interactive phase entirely.

## Three ways to install

1. **Online installer** — clone/copy this folder, run `./install.sh`, and it
   downloads everything fresh. Default and recommended.
2. **Offline installer** — run `./bundle.sh` once (docker + internet needed),
   copy the whole folder to the target, choose "Use the bundled copy" at the
   images prompt. No internet required during install.
3. **Bootable ISO** — run `./build-iso.sh` to produce
   `oneclick-mediasuite-ubuntu.iso`: a remastered Ubuntu Server installer that
   installs Ubuntu itself, then installs the stack unattended on first boot.

## Bootable ISO installer

```bash
sudo apt install xorriso curl          # build host only
./build-iso.sh                         # or: ./build-iso.sh --iso ubuntu-server.iso
```

What it does:

- Downloads the official Ubuntu Server 24.04 ISO (or uses `--iso` you supply;
  `UBUNTU_ISO_URL` overrides the default URL)
- Requires your stack answers first — it runs the same prompt set and bakes
  them into `.state/` inside the ISO (run `./install.sh` once beforehand or let
  `build-iso.sh` prompt you)
- Requires `bundle/` so first boot needs no internet — it offers to run
  `bundle.sh` for you
- Injects a NoCloud autoinstall seed: Ubuntu asks **only** for the Linux user
  and the target disk (kept interactive deliberately — picking the disk is the
  safety checkpoint), everything else is unattended
- Installs an `ocms-firstboot` systemd unit that runs `./install.sh docker`
  once on first boot — Docker, images (from the bundle), and all API wiring
  complete by themselves; the stack is up when the service finishes

Flash with `dd` or balenaEtcher/Rufus (DD mode). The ISO is ~4.8 GB — hybrid
BIOS+UEFI bootable. Progress of the first-boot install:
`journalctl -u ocms-firstboot -f`.

## Bundled / offline install

By default the installer downloads the latest container images at install time
("Download latest from the web"). To install without internet — or with a known
image set — build a bundle first:

```bash
./bundle.sh          # on any machine that has docker + internet
```

This pulls every image the stack needs, builds `portforward-sync`, and writes
`bundle/images.tar.gz` (+ `bundle/manifest.txt`). It also drops the Docker
Engine `.deb`s into `bundle/debs/` so even the Docker install can happen
offline — targeting Ubuntu **24.04 (noble)** by default; override with
`DEB_RELEASE=<codename> ./bundle.sh` for another release. Finally it fetches
the latest published **Cobalt Wolf release APK** into `bundle/cobalt-wolf.apk`
so the sideload step works offline too — point `COBALT_WOLF_APK=/path/to.apk`
at a local build to bundle that one instead.

Copy the whole `OneClickMediaSuite` folder to the target, run `./install.sh`,
and pick **Use the bundled copy** when asked about container images. The option
only appears if a bundle exists — otherwise the prompt offers just the default
download path. Bundled mode also installs Docker from `bundle/debs/` when
present (falls back to the web repo with a warning when absent).

## What "wiring" means

After `compose up`, the installer drives each app's API to:

- finish Jellyfin's startup wizard (admin user + **Movies** + **TV Shows**
  libraries) and create an API key,
- set the qBittorrent web password and create `radarr`/`tv-sonarr` categories
  (each *arr watches only its own, so downloads never cross-import),
- add qBittorrent as the download client in Sonarr/Radarr with the right
  category, plus Jellyfin notifications,
- create the `/data/media/movies` and `/data/media/tv` root folders,
- register Sonarr + Radarr as applications inside Prowlarr (indexers you add
  later sync automatically),
- connect Bazarr to Sonarr + Radarr,
- sign Seerr into Jellyfin with the admin account and register both arrs,
- write the Homepage config (tiles + live widgets) behind the admin password,
- write `credentials.txt` with every URL and API key.

The request flow then works end to end:
**Seerr → Sonarr/Radarr → Prowlarr indexers → qBittorrent (VPN) → import →
Jellyfin**, with Bazarr fetching subtitles.

> Indexer *accounts* cannot be pre-seeded generically. Open Prowlarr →
> Indexers after install; whatever you add syncs to Radarr/Sonarr on its own.
> Same for Bazarr → Settings → Providers (e.g. OpenSubtitles).

## VPN options

- **AirVPN (WireGuard)** — the default and design target. Paste the values
  from a generated config (Client Area → Config Generator → Linux →
  WireGuard). Port forwarding is supported: enable it, and the bundled
  `portforward-sync` service keeps qBittorrent's listen port in sync.
- **Custom WireGuard** — any provider's `[Interface]`/`[Peer]` values.
- **Custom OpenVPN** — path to a `.ovpn` file plus credentials; the file is
  copied into `config/gluetun/custom.ovpn`.
- **No VPN** — skips Gluetun entirely. The installer applies
  `docker-compose.novpn.yml`, which swaps the `gluetun` service for an inert
  holder container that keeps the same network identity (name, static IP,
  published ports) — so qBittorrent and Prowlarr run unchanged, just with a
  direct connection. Your real IP is exposed to peers/indexers in this mode.
  Deunhealth and port forwarding are not deployed. When running compose by
  hand in this mode, use `docker compose -f docker-compose.yml
  -f docker-compose.novpn.yml ...`.

qBittorrent and Prowlarr run inside Gluetun's network namespace
(`network_mode: service:gluetun`): if the tunnel drops they have **no route
out** — the killswitch is structural. Gluetun's firewall also drops everything
outside the tunnel except the LAN, the Docker subnet and `mediasuite`
(10.97.0.0/24, which is pinned so the VPN-side apps can still reach
Radarr/Sonarr).

### Testing without real VPN credentials

`tests/fake-vpn/` builds a local WireGuard endpoint container that NATs like
a real provider:

```bash
tests/fake-vpn/run.sh /path/to/oneclick-media-suite   # dir containing .env
```

It generates keys, starts the fake server on the `mediasuite` network and
patches `.env` so Gluetun tunnels through it. `docker exec fake-vpn wg show`
proves the handshake; `docker exec qbittorrent wget -qO- https://api.ipify.org`
proves traffic takes the tunnel.

> In a docker-in-docker test environment, run the fake endpoint on the
> **outer** Docker daemon — `wg` in inner containers hits a fuse-overlayfs
> permission quirk — and point `WIREGUARD_ENDPOINT_IP` at its outer-IP.

## Dashboard

Homepage runs behind a small nginx `auth_basic` proxy at
`http://<host>:3080` — sign in with the **Jellyfin admin credentials**. Each
tile opens the real service in a new browser tab, and widgets show live
queue/wanted/stream stats wired with the generated API keys. Homepage itself
has no published port — the proxy is the only way in.

## Fire Stick / Cobalt Wolf

> **Compatibility:** ADB sideloading only works on Fire TV devices running
> classic **Fire OS** (Fire OS 6/7, Android-based — Fire TV Stick 4K / 4K Max
> 1st gen, Fire TV Cube, etc.). The new 2024+ models on **Vega OS** (e.g.
> Fire TV Stick 4K Select) cannot sideload Android APKs at all. Check
> Settings → My Fire TV → About for the OS name.

The optional last phase prints step-by-step instructions for enabling
**ADB debugging** on the stick (Settings → My Fire TV → Developer Options;
if hidden, press SELECT ~7 times on the device name under About, then turn
on ADB debugging and install-unknown-apps, and note the IP under About →
Network). It `adb connect`s to the IP you give it, then installs the release
APK:

1. **`bundle/cobalt-wolf.apk` present** — installs that copy (works fully
   offline; `./bundle.sh` keeps it at the latest published release).
2. **Otherwise** — downloads the latest release APK from the cobalt-wolf
   GitHub releases at install time (needs internet).

Either way it's the same signed release build as the download page. On first
launch, connect to `http://<host>:8096` and sign in with the Jellyfin admin
account the installer created — then set Seerr/arr details in the app's
Services screen if you want them.

The same phase also covers a **phone or tablet over USB** — pick that at the
target prompt; it prints detailed USB-debugging setup steps (Build-number ×7,
Developer options → USB debugging, accept the RSA prompt). Android 11+
wireless debugging (`adb pair`) works too via the network path.

If the device already has a differently-signed copy, the phase offers to
uninstall + reinstall (this erases that app's data on the device).

Requirements: `adb` (`~/Android/Sdk/platform-tools/adb` or on `PATH`).

## Files it leaves behind

```
<install>/
  .env                  (chmod 600 — VPN keys + passwords)
  docker-compose.yml
  credentials.txt       URLs, accounts and API keys — keep private
  config/<app>/         per-app config (Servarr config.xml holds API keys)
  data/media/{movies,tv}      Jellyfin libraries + *arr roots
  data/torrents/{movies,tv}   qBittorrent category savePaths (radarr -> movies, tv-sonarr -> tv)
  build/*.apk           downloaded release APK (only when sideload ran without a bundle)
.state/install.state    answers + generated keys (next to install.sh)
```

The shared `data/` layout means imports are instant/hardlinked rather than
copied.

## Security notes

- `.env` is `chmod 600`; `credentials.txt` contains everything — treat it
  like a password.
- Servarr API keys are read from the live `config.xml` on re-runs, so the
  file on disk stays the source of truth.
- The dashboard proxy is the only unauthenticated-facing auth boundary;
  the app UIs themselves rely on their own auth or LAN trust.

## Upgrading versions

Image tags are pinned in `templates/docker-compose.yml`. Bump a tag, then:

```bash
cd <install> && docker compose pull && docker compose up -d
```

Check release notes before major bumps — the wiring targets specific API
surfaces (e.g. Seerr v3 auth needs `serverType: 2`; Jellyfin 12 dropped
`X-Emby-*` auth headers in favour of `Authorization: MediaBrowser`).

## Troubleshooting

- **Prowlarr can't reach Radarr/Sonarr** — VPN-side containers lose Docker's
  embedded DNS (Gluetun replaces `resolv.conf`); the apps are registered by
  pinned `mediasuite` IP (`.4`/`.5`) for exactly this reason.
- **qBittorrent/Prowlarr can't resolve anything after Gluetun recreates** —
  the shared netns went stale; `docker compose up -d` recreates them. Their
  healthcheck (Gluetun `:9999` + real outbound probe) detects this
  automatically and Deunhealth restarts them.
- **Seerr setup call fails** — repeat logins must omit the server fields;
  first-time setup needs `hostname`/`port`/`useSsl`/`urlBase`/`serverType`.
- **qBittorrent login looks rejected** — 5.3 returns an empty body on
  success and a `QBT_SID_8080` cookie; check the cookie, not the body.
