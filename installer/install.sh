#!/usr/bin/env bash
#
# OneClick Media Suite — Ubuntu installer
#
# Docker -> Gluetun (VPN) -> qBittorrent + Prowlarr (via VPN) ->
# Sonarr + Radarr + Bazarr + Jellyfin + Seerr + Deunhealth, all wired together,
# then optionally sideloads the Cobalt Wolf release APK onto a Fire Stick.
#
# Everything interactive is asked up front; the rest is unattended.
# Re-running the script resumes/repairs — existing objects are left alone.
#
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export REPO_DIR

STATE_DIR="$REPO_DIR/.state"
mkdir -p "$STATE_DIR"
STATE_FILE="$STATE_DIR/install.state"
export STATE_FILE

# shellcheck source=lib/common.sh
source "$REPO_DIR/lib/common.sh"
source "$REPO_DIR/lib/prompts.sh"
source "$REPO_DIR/lib/docker.sh"
source "$REPO_DIR/lib/stack.sh"
source "$REPO_DIR/lib/wire.sh"
source "$REPO_DIR/lib/firestick.sh"

# Pull any answers from a previous run into the environment.
INSTALL_DIR="$(state_get INSTALL_DIR "$HOME/oneclick-media-suite")"

PHASES=(prompts docker stack wire firestick)
START_PHASE="${1:-}"

usage() {
    cat <<EOF
Usage: ./install.sh [phase]

Phases: ${PHASES[*]}
  (no args)   run all phases in order
  <phase>     start from that phase (answers from the previous run are reused)

Examples:
  ./install.sh            # full install
  ./install.sh wire       # re-run only the API wiring + summary
  ./install.sh firestick  # only the Fire Stick / Cobalt Wolf step
EOF
}

[[ "${1:-}" == "-h" || "${1:-}" == "--help" ]] && { usage; exit 0; }

# Pull every answer the later phases need out of the state file.
load_state() {
    local v
    for v in INSTALL_DIR DATA_DIR CONFIG_DIR TZ PUID PGID LAN_SUBNET \
             VPN_VENDOR VPN_PORT_FORWARDING VPN_SERVICE_PROVIDER_NAME \
             WIREGUARD_PRIVATE_KEY WIREGUARD_PRESHARED_KEY WIREGUARD_ADDRESSES \
             WIREGUARD_PUBLIC_KEY VPN_ENDPOINT_IP VPN_ENDPOINT_PORT \
             SERVER_HOSTNAMES OVPN_CONF_PATH OPENVPN_USER OPENVPN_PASSWORD \
             JELLYFIN_ADMIN_USER JELLYFIN_ADMIN_PASS QBIT_USER QBIT_PASS \
             RADARR_APIKEY SONARR_APIKEY PROWLARR_APIKEY BAZARR_APIKEY \
             JELLYFIN_APIKEY HOST_IP COMPOSE_PROFILES IMAGE_SOURCE; do
        eval "$v=\"\$(state_get $v '')\""
    done
    CONFIG_DIR="${CONFIG_DIR:-$INSTALL_DIR/config}"
}

main() {
    cat <<'EOF'
   ___            ____ ___      __   __  ___         ___       _ __
  / _ \___  ___  / __// (_)___ / /__/ / /  |/  ___ __/ (_)___ _/ _/_ _____ _____ ___
 / // / _ \/ -_)/ _/_/ / / __//  '_/ / /|_/ / -_) // / / __/ / _/ / -_) __/ // / -_)
/____/_//_/\__//___//_/_/\__//_/\_\ /_/  /_/\__/\_,_/_/\__/_/_//_/\__/\__/\_,_/\__/
                                              media suite installer
EOF

    local start_idx=0
    if [[ -n "$START_PHASE" ]]; then
        local i found=""
        for i in "${!PHASES[@]}"; do
            [[ "${PHASES[$i]}" == "$START_PHASE" ]] && { start_idx=$i; found=1; break; }
        done
        [[ -n "$found" ]] || { err "unknown phase '$START_PHASE'"; usage; exit 1; }
    fi

    load_state

    for ((i=start_idx; i<${#PHASES[@]}; i++)); do
        case "${PHASES[$i]}" in
            prompts)   collect_answers ;;
            docker)    install_docker ;;
            stack)     setup_stack ;;
            wire)      wire_all ;;
            firestick) setup_firestick ;;
        esac
    done

    step "Done"
    ok "Media suite is up. Access details: $INSTALL_DIR/credentials.txt"
}

main "$@"
