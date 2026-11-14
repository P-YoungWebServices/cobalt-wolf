#!/usr/bin/env bash
# Phase 1: collect every answer up front so the rest of the install is unattended.

ask() { # ask VAR "Prompt" [default] [secret]
    local var="$1" prompt="$2" default="${3:-}" secret="${4:-}"
    local reply display
    [[ -n "$default" ]] && display=" [$default]" || display=""
    if [[ "$secret" == "secret" ]]; then
        read -rs -p "$prompt$display: " reply; echo >&2
    else
        read -r -p "$prompt$display: " reply
    fi
    reply="${reply:-$default}"
    [[ -z "$reply" ]] && die "$prompt cannot be empty"
    printf -v "$var" '%s' "$reply"
}

ask_opt() { # ask_opt VAR "Prompt" [default] [secret] — blank allowed
    local var="$1" prompt="$2" default="${3:-}" secret="${4:-}"
    local reply display
    [[ -n "$default" ]] && display=" [$default]" || display=""
    if [[ "$secret" == "secret" ]]; then
        read -rs -p "$prompt$display: " reply; echo >&2
    else
        read -r -p "$prompt$display: " reply
    fi
    printf -v "$var" '%s' "${reply:-$default}"
}

ask_yn() { # ask_yn "Prompt" [Y|N] -> returns 0 for yes
    local prompt="$1" default="${2:-Y}" reply
    local hint="[Y/n]"; [[ "$default" == "N" ]] && hint="[y/N]"
    read -r -p "$prompt $hint: " reply
    reply="${reply:-$default}"
    [[ "$reply" =~ ^[Yy] ]]
}

ask_choice() { # ask_choice VAR "Prompt" opt1 opt2 ...
    local var="$1" prompt="$2"; shift 2
    local i opt
    echo "$prompt" >&2
    i=1; for opt in "$@"; do echo "  $i) $opt" >&2; i=$((i+1)); done
    local reply
    read -r -p "Choose [1]: " reply
    reply="${reply:-1}"
    [[ "$reply" =~ ^[0-9]+$ ]] && (( reply >= 1 && reply <= $# )) || die "invalid choice"
    printf -v "$var" '%s' "${!reply}"
}

default_iface_subnet() { # best-effort LAN subnet in CIDR
    local ip
    ip=$(ip -4 route get 1.1.1.1 2>/dev/null | grep -oP 'src \K[\d.]+' | head -1)
    if [[ -n "$ip" ]]; then
        local cidr
        cidr=$(ip -4 -o addr show | awk -v ip="$ip" '$4 ~ ip"/" {print $4}' | head -1)
        [[ -n "$cidr" ]] && { python3 -c "import ipaddress;print(ipaddress.ip_network('$cidr',strict=False))" 2>/dev/null && return; }
        echo "${ip%.*}.0/24"
        return
    fi
    echo "192.168.1.0/24"
}

collect_answers() {
    step "Setup — answer these once, then the install runs unattended"

    # ---- paths ----
    ask INSTALL_DIR "Install directory (compose file, app configs, credentials)" "$HOME/oneclick-media-suite"
    INSTALL_DIR="${INSTALL_DIR/#\~/$HOME}"
    ask DATA_DIR    "Media + downloads root (media/movies, media/tv, torrents/...)" "$INSTALL_DIR/data"
    DATA_DIR="${DATA_DIR/#\~/$HOME}"

    # ---- identity ----
    local tz_default="Etc/UTC"
    tz_default=$(timedatectl show -p Timezone --value 2>/dev/null || cat /etc/timezone 2>/dev/null || echo Etc/UTC)
    tz_default="${tz_default#/}"   # '/UTC' is invalid for some apps (Bazarr zoneinfo)
    [[ -n "$tz_default" && "$tz_default" == */* ]] || tz_default="Etc/UTC"
    ask TZ "Timezone" "$tz_default"
    TZ="${TZ#/}"; [[ "$TZ" == */* ]] || die "timezone must look like Europe/London or Etc/UTC"
    ask PUID "UID for media apps (owns files)" "$(id -u)"
    ask PGID "GID for media apps" "$(id -g)"

    # ---- VPN ----
    step "VPN (Gluetun) — traffic for qBittorrent + Prowlarr exits via the tunnel"
    ask_choice VPN_VENDOR "VPN provider:" \
        "AirVPN (WireGuard)" \
        "Other provider — custom WireGuard config" \
        "Other provider — custom OpenVPN config" \
        "No VPN — connect directly"
    case "$VPN_VENDOR" in
        AirVPN*) VPN_VENDOR=airvpn ;;
        *WireGuard*) VPN_VENDOR=custom-wireguard ;;
        *OpenVPN*) VPN_VENDOR=custom-openvpn ;;
        *) VPN_VENDOR=none ;;
    esac

    if [[ "$VPN_VENDOR" == "none" ]]; then
        warn "qBittorrent + Prowlarr will connect directly — your real IP will be exposed to peers/indexers"
    elif [[ "$VPN_VENDOR" == "airvpn" ]]; then
        cat <<'EOF'

AirVPN needs values from a generated WireGuard config:
  AirVPN site -> Client Area -> Config Generator -> Linux -> WireGuard -> Generate
Open the downloaded .conf and copy the values below.
EOF
        ask WIREGUARD_PRIVATE_KEY   "WireGuard [Interface] PrivateKey" "" secret
        ask WIREGUARD_PRESHARED_KEY "WireGuard [Peer] PresharedKey" ""
        ask WIREGUARD_ADDRESSES     "WireGuard [Interface] Address (e.g. 10.x.x.x/32)" ""
        ask_opt SERVER_HOSTNAMES "Preferred AirVPN server hostname (blank = pick for you)" ""
        VPN_PORT_FORWARDING=N
        if ask_yn "Enable AirVPN port forwarding (recommended for torrents)?" Y; then
            VPN_PORT_FORWARDING=Y
            echo "  Note: the forwarded port is created on AirVPN's side automatically;"
            echo "  the installer syncs it into qBittorrent for you."
        fi
    elif [[ "$VPN_VENDOR" == "custom-wireguard" ]]; then
        cat <<'EOF'

Fill these from your provider's WireGuard config (.conf file or account page).
EOF
        ask_opt WG_CONF_PATH "Path to the .conf file (blank to enter values by hand)" ""
        if [[ -n "$WG_CONF_PATH" && -f "$WG_CONF_PATH" ]]; then
            _parse_wg_conf "$WG_CONF_PATH"
        else
            ask WIREGUARD_PRIVATE_KEY   "PrivateKey" "" secret
            ask WIREGUARD_PUBLIC_KEY    "Peer PublicKey" ""
            ask_opt WIREGUARD_PRESHARED_KEY "Peer PresharedKey (may be blank)" ""
            ask WIREGUARD_ADDRESSES     "Interface Address (e.g. 10.x.x.x/32)" ""
            ask VPN_ENDPOINT_IP         "Peer Endpoint IP/hostname" ""
            ask VPN_ENDPOINT_PORT       "Peer Endpoint port" "51820"
        fi
        ask VPN_SERVICE_PROVIDER_NAME "Gluetun provider name ('custom' or a known provider like mullvad/protonvpn)" "custom"
    else
        cat <<'EOF'

OpenVPN: point me at your provider's .ovpn file and the account credentials.
EOF
        ask OVPN_CONF_PATH "Path to the .ovpn file" ""
        [[ -f "$OVPN_CONF_PATH" ]] || die "file not found: $OVPN_CONF_PATH"
        ask OPENVPN_USER     "OpenVPN username" ""
        ask OPENVPN_PASSWORD "OpenVPN password" "" secret
        ask VPN_SERVICE_PROVIDER_NAME "Gluetun provider name ('custom' or a known provider)" "custom"
    fi

    local subnet_default; subnet_default=$(default_iface_subnet)
    ask LAN_SUBNET "Your LAN subnet (lets you open the web UIs while VPN is up)" "$subnet_default"

    # ---- accounts ----
    step "Accounts"
    ask JELLYFIN_ADMIN_USER "Jellyfin admin username" "admin"
    ask JELLYFIN_ADMIN_PASS "Jellyfin admin password" "" secret
    ask QBIT_USER           "qBittorrent web UI username" "admin"
    ask QBIT_PASS           "qBittorrent web UI password (min 6 chars)" "" secret
    [[ ${#QBIT_PASS} -ge 6 ]] || die "qBittorrent requires a password of at least 6 characters"

    # ---- images ----
    step "Container images"
    local img_opts=("Download latest from the web (recommended)")
    if compgen -G "${REPO_DIR:-$HOME}/bundle/images*.tar*" >/dev/null; then
        img_opts+=("Use the bundled copy in ./bundle (works offline)")
    fi
    ask_choice IMAGE_SOURCE "Container images:" "${img_opts[@]}"
    [[ "$IMAGE_SOURCE" == *bundled* ]] && IMAGE_SOURCE=bundled || IMAGE_SOURCE=latest

    echo
    info "Summary: install=$INSTALL_DIR data=$DATA_DIR vpn=$VPN_VENDOR lan=$LAN_SUBNET"
    ask_yn "Looks right — continue?" Y || die "aborted by user"

    # persist
    local v
    for v in INSTALL_DIR DATA_DIR TZ PUID PGID LAN_SUBNET VPN_VENDOR \
             WIREGUARD_PRIVATE_KEY WIREGUARD_PRESHARED_KEY WIREGUARD_ADDRESSES \
             WIREGUARD_PUBLIC_KEY VPN_ENDPOINT_IP VPN_ENDPOINT_PORT \
             SERVER_HOSTNAMES VPN_PORT_FORWARDING VPN_SERVICE_PROVIDER_NAME \
             OVPN_CONF_PATH OPENVPN_USER OPENVPN_PASSWORD \
             JELLYFIN_ADMIN_USER JELLYFIN_ADMIN_PASS QBIT_USER QBIT_PASS \
             IMAGE_SOURCE; do
        state_set "$v" "${!v:-}"
    done
}

_parse_wg_conf() { # extract values from a WireGuard .conf
    local conf="$1" k
    _wg_val() { grep -i "^$1" "$conf" | head -1 | cut -d= -f2- | tr -d ' \r'; }
    WIREGUARD_PRIVATE_KEY=$(_wg_val PrivateKey)
    WIREGUARD_PUBLIC_KEY=$(_wg_val PublicKey)
    WIREGUARD_PRESHARED_KEY=$(_wg_val PresharedKey)
    WIREGUARD_ADDRESSES=$(_wg_val Address)
    local ep; ep=$(_wg_val Endpoint)
    VPN_ENDPOINT_IP="${ep%%:*}"; VPN_ENDPOINT_PORT="${ep##*:}"
    [[ -n "$WIREGUARD_PRIVATE_KEY" && -n "$VPN_ENDPOINT_IP" ]] || \
        warn "Could not parse all fields from $conf — check values manually after install"
    info "Parsed WireGuard config from $conf (endpoint $ep)"
}
