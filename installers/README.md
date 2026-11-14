# OneClick Media Suite installers

These three files turn a fresh **Ubuntu 24.04+** machine into a complete
Jellyfin + Servarr media stack, with an optional final step that builds and
sideloads a preconfigured Cobalt Wolf APK to a Fire Stick or Android
phone/tablet over adb.

**You do not need any of this to use Cobalt Wolf** — the app works with any
Jellyfin 12.0+ server and any existing Seerr/Radarr/Sonarr/qBittorrent/
Bazarr/Prowlarr stack you already run. This is an optional convenience for
starting from a blank machine.

| File | What it is |
|---|---|
| `oneclick-mediasuite-online.tar.gz` (~34 KB) | Extract → `./install.sh`. Downloads the latest container images during install. |
| `oneclick-mediasuite-offline.tar.gz` (~1.8 GB) | Same, but ships every image + Docker Engine `.deb`s inside the tarball. Needs no internet at all on the target. |
| `oneclick-mediasuite-ubuntu.iso` (~4.8 GB) | Bootable Ubuntu Server 24.04 installer — installs Ubuntu, then the whole stack automatically on first boot. Flash to USB, boot, answer only the Ubuntu user/disk questions. |

## Where to download them

The binaries are attached to the `media-suite-*` releases on the
[Releases page](https://github.com/P-YoungWebServices/cobalt-wolf/releases)
(they exceed GitHub's 100 MB git file limit, so they are not in this tree).
`SHA256SUMS.txt` here covers the release assets — always verify a download:

```sh
sha256sum -c SHA256SUMS.txt
```

## Rejoining the ISO

The ISO is released in parts (`...iso.part-aa`, `part-ab`, `part-ac`) because
release assets are capped at 2 GB. Rejoin before flashing:

- **Linux / macOS:**
  ```sh
  cat oneclick-mediasuite-ubuntu.iso.part-* > oneclick-mediasuite-ubuntu.iso
  ```
- **Windows:** double-click `join-iso.bat` (in this folder and attached to
  the release), or run `copy /b oneclick-mediasuite-ubuntu.iso.part-aa+part-ab+part-ac oneclick-mediasuite-ubuntu.iso` in the folder.

Then verify with `SHA256SUMS.txt` and flash with dd / Rufus / balenaEtcher.

## Full docs

See the wiki page: https://p-youngwebservices.github.io/cobalt-wolf/wiki/media-suite.html
