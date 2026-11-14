#!/usr/bin/env bash
#
# bundle.sh — download everything the installer needs ahead of time.
#
# Produces bundle/images.tar.gz (every container image, incl. the locally
# built portforward-sync) + bundle/manifest.txt + bundle/cobalt-wolf.apk
# (the latest published Cobalt Wolf release, for the sideload step).
# Optionally bundle/debs/ with the Docker Engine .deb packages for this
# machine's Ubuntu release, so a fully offline host can install Docker too.
#
# Run it on a machine that has internet + docker, then copy the whole
# OneClickMediaSuite folder to the target and pick "Use bundled copy"
# at install time.
#
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUNDLE_DIR="$REPO_DIR/bundle"
mkdir -p "$BUNDLE_DIR"

info() { echo "[*] $*"; }
ok()   { echo "[+] $*"; }
warn() { echo "[!] $*" >&2; }
die()  { echo "[x] $*" >&2; exit 1; }

command -v docker >/dev/null || die "docker is required to build the bundle"
docker info >/dev/null 2>&1 || die "docker daemon not reachable"

# ---- collect image refs from the compose templates ----
# (skip the locally-built tag — it has no registry to pull from)
LOCAL_IMAGE="ocms-portforward-sync:local"
mapfile -t IMAGES < <(
    grep -h '^\s*image:' "$REPO_DIR"/templates/docker-compose*.yml \
        | sed -E 's/^\s*image:\s*//; s/\s*$//' | sort -u \
        | grep -v "^$LOCAL_IMAGE$"
)

info "Pulling ${#IMAGES[@]} images"
for img in "${IMAGES[@]}"; do
    docker pull "$img" || die "pull failed: $img"
done

# ---- build the local helper image (no registry needed at install time) ----
info "Building portforward-sync"
docker build -q -t "$LOCAL_IMAGE" "$REPO_DIR/templates/portforward-sync" >/dev/null
IMAGES+=("$LOCAL_IMAGE")

# ---- save the lot ----
OUT="$BUNDLE_DIR/images.tar.gz"
info "Saving ${#IMAGES[@]} images -> $OUT"
docker save "${IMAGES[@]}" | gzip -1 > "$OUT"
ok "$(du -h "$OUT" | cut -f1) bundle written"

# ---- manifest ----
cat > "$BUNDLE_DIR/manifest.txt" <<EOF
arch=$(uname -m)
built=$(date -u +%Y-%m-%dT%H:%M:%SZ)
images:
$(printf '  %s\n' "${IMAGES[@]}")
EOF
ok "manifest written"

# ---- optional: docker engine debs for the *target* release ----
# Targets are Ubuntu 24.04+; DEB_RELEASE picks which one the debs are for
# (noble debs also install fine on later releases — they're self-contained).
DEB_RELEASE="${DEB_RELEASE:-noble}"
HOST_CODENAME=""; [[ -f /etc/os-release ]] && HOST_CODENAME=$(. /etc/os-release; echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
mkdir -p "$BUNDLE_DIR/debs"
if [[ "$DEB_RELEASE" == "$HOST_CODENAME" ]] && command -v apt-get >/dev/null; then
    info "Downloading Docker Engine .debs for $DEB_RELEASE/$(dpkg --print-architecture) (local apt)"
    if apt-get download docker-ce docker-ce-cli containerd.io \
            docker-buildx-plugin docker-compose-plugin \
            -o Dir::Cache::Archives="$BUNDLE_DIR/debs" 2>/dev/null; then
        mv ./*.deb "$BUNDLE_DIR/debs/" 2>/dev/null || true
        ok "$(find "$BUNDLE_DIR/debs" -name '*.deb' | wc -l) .deb files bundled"
    else
        warn "Docker repo not configured here — skipping .deb bundle (optional; images still work)."
    fi
elif docker image inspect "ubuntu:$DEB_RELEASE" >/dev/null 2>&1 \
     || docker pull -q "ubuntu:$DEB_RELEASE" >/dev/null 2>&1; then
    info "Downloading Docker Engine .debs for $DEB_RELEASE/amd64 (via ubuntu:$DEB_RELEASE container)"
    if docker run --rm -v "$BUNDLE_DIR/debs:/out" "ubuntu:$DEB_RELEASE" bash -c "
        set -e
        apt-get update -qq && apt-get install -y -qq ca-certificates curl gnupg >/dev/null
        install -m 0755 -d /etc/apt/keyrings
        curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
        echo 'deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $DEB_RELEASE stable' \
            > /etc/apt/sources.list.d/docker.list
        apt-get update -qq
        cd /tmp && apt-get download docker-ce docker-ce-cli containerd.io \
            docker-buildx-plugin docker-compose-plugin
        mv *.deb /out/"; then
        ok "$(find "$BUNDLE_DIR/debs" -name '*.deb' | wc -l) .deb files bundled for $DEB_RELEASE"
    else
        warn ".deb download for $DEB_RELEASE failed (optional; images still work)."
    fi
else
    warn "Could not fetch debs for $DEB_RELEASE — skipping (optional; images still work)."
fi

# ---- Cobalt Wolf release APK ----
# The sideload phase prefers this bundled copy; without one it falls back to
# downloading at install time (needs internet on the target).
# COBALT_WOLF_APK=/path/to.apk bundles a specific file instead of downloading.
APK_OUT="$BUNDLE_DIR/cobalt-wolf.apk"
if [[ -n "${COBALT_WOLF_APK:-}" ]]; then
    [[ -f "$COBALT_WOLF_APK" ]] || die "COBALT_WOLF_APK not found: $COBALT_WOLF_APK"
    cp "$COBALT_WOLF_APK" "$APK_OUT"
    ok "APK bundled from $COBALT_WOLF_APK"
else
    # newest release first (incl. prereleases) — first .apk asset wins
    apk_url=$(curl -fsSL "https://api.github.com/repos/P-YoungWebServices/cobalt-wolf/releases" \
        | grep -oP '"browser_download_url":\s*"\K[^"]+\.apk' | head -1 || true)
    if [[ -n "$apk_url" ]]; then
        info "Downloading $(basename "$apk_url")"
        curl -fL --progress-bar -o "$APK_OUT" "$apk_url" \
            && ok "$(du -h "$APK_OUT" | cut -f1) APK bundled -> $APK_OUT" \
            || warn "APK download failed — the sideload step will try again at install time"
        [[ -f "$APK_OUT" ]] && echo "apk=$apk_url" >> "$BUNDLE_DIR/manifest.txt"
    else
        warn "No APK asset found on any cobalt-wolf release — skipping (sideload will download/build instead)"
    fi
fi

echo
ok "Bundle ready. Copy the whole OneClickMediaSuite folder to the target machine;"
ok "at install time choose 'Use bundled copy' when asked about container images."
