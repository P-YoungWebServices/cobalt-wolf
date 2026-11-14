#!/usr/bin/env bash
#
# build-iso.sh — bake a bootable Ubuntu Server ISO that installs Ubuntu
# AND this media stack. Uses Ubuntu's autoinstall mechanism:
#
#   boots -> Subiquity asks only for user + disk (safety)
#         -> Ubuntu installs
#         -> first boot: ocms-firstboot.service runs ./install.sh docker
#            (stack answers were baked in, bundle provides images offline)
#
# Usage:
#   ./build-iso.sh [--iso /path/to/ubuntu-server.iso] [--out out.iso] [--keep-work]
#
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UBUNTU_ISO_URL="${UBUNTU_ISO_URL:-https://releases.ubuntu.com/24.04/ubuntu-24.04.3-live-server-amd64.iso}"

info() { echo "[*] $*"; }
ok()   { echo "[+] $*"; }
warn() { echo "[!] $*" >&2; }
die()  { echo "[x] $*" >&2; exit 1; }

SRC_ISO="" OUT_ISO="" KEEP_WORK=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --iso) SRC_ISO="$2"; shift 2 ;;
        --out) OUT_ISO="$2"; shift 2 ;;
        --keep-work) KEEP_WORK=1; shift ;;
        *) die "unknown arg: $1" ;;
    esac
done
OUT_ISO="${OUT_ISO:-$REPO_DIR/oneclick-mediasuite-ubuntu.iso}"

# ---- deps ----
command -v xorriso >/dev/null || die "xorriso missing — sudo apt install xorriso"

# ---- source ISO ----
if [[ -z "$SRC_ISO" ]]; then
    SRC_ISO="$REPO_DIR/.cache/$(basename "$UBUNTU_ISO_URL")"
    if [[ ! -f "$SRC_ISO" ]]; then
        mkdir -p "$REPO_DIR/.cache"
        info "Downloading Ubuntu Server ISO -> $SRC_ISO"
        curl -fL --progress-bar -o "$SRC_ISO" "$UBUNTU_ISO_URL" \
            || die "download failed — pass --iso /path/to/ubuntu-server.iso"
    fi
fi
[[ -f "$SRC_ISO" ]] || die "source ISO not found: $SRC_ISO"
ok "Source ISO: $SRC_ISO ($(du -h "$SRC_ISO" | cut -f1))"

# ---- bundle required (the ISO should install offline) ----
if ! compgen -G "$REPO_DIR/bundle/images*.tar*" >/dev/null; then
    warn "No bundle/ found — the ISO would need internet for images."
    read -r -p "Run ./bundle.sh now to build it? [Y/n] " r
    [[ "${r:-Y}" =~ ^[Yy] ]] && "$REPO_DIR/bundle.sh" \
        || warn "Continuing without a bundle — first boot will pull images from the web."
fi
[[ -f "$REPO_DIR/bundle/cobalt-wolf.apk" ]] \
    || warn "No bundle/cobalt-wolf.apk — the ISO's sideload step will need internet"

# ---- stack answers: bake if present, otherwise ISO stays generic ----
# With answers baked, first boot installs fully unattended. Without them the
# ISO still works — the installer launches interactively on first login.
STATE_FILE="$REPO_DIR/.state/install.state"
if [[ -f "$STATE_FILE" ]] && grep -q JELLYFIN_ADMIN_PASS "$STATE_FILE"; then
    ok "Baking saved answers into the ISO (fully unattended first boot)"
    grep -q 'IMAGE_SOURCE=bundled' "$STATE_FILE" \
        || warn "IMAGE_SOURCE is not 'bundled' — first boot will try to pull latest images"
elif [[ -t 0 ]]; then
    warn "No saved answers at .state/install.state."
    read -r -p "Answer the stack questions now to bake a fully-unattended ISO? [Y/n] " r
    if [[ "${r:-Y}" =~ ^[Yy] ]]; then
        export STATE_FILE REPO_DIR
        mkdir -p "$(dirname "$STATE_FILE")"
        source "$REPO_DIR/lib/common.sh"
        source "$REPO_DIR/lib/prompts.sh"
        collect_answers
    else
        warn "Generic ISO it is — the installer will prompt on first login."
    fi
else
    warn "No saved answers — building a generic ISO (installer prompts on first login)"
fi

# ---- extract ----
WORK="$REPO_DIR/.iso-work"
chmod -R u+w "$WORK" 2>/dev/null || true   # a previous extract leaves read-only files
rm -rf "$WORK"; mkdir -p "$WORK/root"
info "Extracting ISO"
xorriso -osirrox on -indev "$SRC_ISO" -extract / "$WORK/root" >/dev/null 2>&1 \
    || die "ISO extraction failed"
chmod -R u+w "$WORK/root"   # ISO perms come out read-only; sed -i needs writable dirs

# ---- payload ----
info "Copying OneClickMediaSuite payload"
mkdir -p "$WORK/root/ocms"
cp -a "$REPO_DIR"/{install.sh,bundle.sh,build-iso.sh,ocms-firstboot.service,ocms-firstboot.sh,README.md,lib,templates,.state} "$WORK/root/ocms/" 2>/dev/null || true
[[ -d "$REPO_DIR/bundle" ]] && cp -a "$REPO_DIR/bundle" "$WORK/root/ocms/"
chmod +x "$WORK/root/ocms/"*.sh

# when answers are baked, they carry the *builder's* paths — point them inside
# the ISO's install location instead (/opt/OneClickMediaSuite on the target)
[[ -f "$WORK/root/ocms/.state/install.state" ]] && \
sed -i -e 's|^INSTALL_DIR=.*|INSTALL_DIR=/opt/OneClickMediaSuite/deploy|' \
       -e 's|^DATA_DIR=.*|DATA_DIR=/opt/OneClickMediaSuite/deploy/data|' \
       -e 's|^PUID=.*|PUID=1000|' -e 's|^PGID=.*|PGID=1000|' \
       "$WORK/root/ocms/.state/install.state"

# ---- autoinstall seed ----
mkdir -p "$WORK/root/nocloud"
cp "$REPO_DIR/templates/autoinstall/user-data" "$WORK/root/nocloud/user-data"
cp "$REPO_DIR/templates/autoinstall/meta-data" "$WORK/root/nocloud/meta-data"

# ---- patch grub to load the seed ----
info "Patching boot config for autoinstall"
chmod +w "$WORK/root/boot/grub/grub.cfg" "$WORK/root/boot/grub/loopback.cfg" 2>/dev/null || true
for cfg in "$WORK/root/boot/grub/grub.cfg" "$WORK/root/boot/grub/loopback.cfg"; do
    [[ -f "$cfg" ]] || continue
    sed -i 's|vmlinuz\([^ ]*\)\(.*\)---|vmlinuz\1\2autoinstall ds=nocloud;s=/cdrom/nocloud/ ---|g' "$cfg"
done
grep -q "nocloud" "$WORK/root/boot/grub/grub.cfg" \
    || die "grub.cfg patch failed — check the ISO layout"

# ---- rebuild (reuse the source ISO's exact boot parameters) ----
info "Rebuilding ISO"
eval set -- "$(xorriso -indev "$SRC_ISO" -report_el_torito as_mkisofs \
    | grep -v '^-V ' | tr '\n' ' ')"
xorriso -as mkisofs -r -V 'OCMS-UBUNTU-2404' "$@" -o "$OUT_ISO" "$WORK/root" \
    || die "ISO rebuild failed"

(( KEEP_WORK )) || rm -rf "$WORK"
ok "Wrote $OUT_ISO ($(du -h "$OUT_ISO" | cut -f1))"
echo
echo "Flash it:  sudo dd if='$OUT_ISO' of=/dev/sdX bs=4M status=progress conv=fsync"
echo "           (or use balenaEtcher / Rufus in DD mode)"
echo "Boot it:   answer only the user + disk questions; the stack installs itself on first boot."
