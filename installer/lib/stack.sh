#!/usr/bin/env bash
# Phase 3: build the deploy directory, preseed app configs, render .env, start the stack.

# Dashboard auth proxy: nginx on :3080 -> homepage:3000, gated by basic auth
# with the Jellyfin admin credentials.
_setup_dashboard() {
    local ddir="$CONFIG_DIR/dashboard"
    mkdir -p "$ddir" "$CONFIG_DIR/homepage"
    chown -R 1000:1000 "$CONFIG_DIR/homepage" 2>/dev/null || true

    cat > "$ddir/nginx.conf" <<'EOF'
worker_processes auto;
events { worker_connections 512; }
http {
    access_log off;
    resolver 127.0.0.11 valid=15s ipv6=off;
    server {
        listen 3080;
        auth_basic "Media Suite";
        auth_basic_user_file /etc/nginx/.htpasswd;
        location / {
            set $up http://homepage:3000;
            proxy_pass $up;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Forwarded-For $remote_addr;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "";
        }
    }
}
EOF

    need_cmd openssl && openssl passwd -apr1 "$JELLYFIN_ADMIN_PASS" \
        | sed "s|^|$JELLYFIN_ADMIN_USER:|" > "$ddir/.htpasswd"
    chmod 644 "$ddir/.htpasswd" "$ddir/nginx.conf"
}

setup_stack() {
    step "Stack layout"

    CONFIG_DIR="$INSTALL_DIR/config"
    export CONFIG_DIR DATA_DIR

    # ---- API keys: Servarr apps generate their own config.xml on first boot
    # (a hand-written file gets replaced wholesale, key included), so keys are
    # harvested from the generated configs during the wire phase. On re-runs an
    # existing config.xml is the source of truth. ----
    _arr_key() { # _arr_key NAME — echoes the apikey from config.xml, or empty
        local key="" cfg="$CONFIG_DIR/${1,,}/config.xml"
        [[ -f "$cfg" ]] && key=$(grep -oP '<ApiKey>\K[^<]+' "$cfg" | head -1 || true)
        printf '%s' "$key"
    }
    RADARR_APIKEY=$(_arr_key Radarr)
    SONARR_APIKEY=$(_arr_key Sonarr)
    PROWLARR_APIKEY=$(_arr_key Prowlarr)
    BAZARR_APIKEY=$(state_get BAZARR_APIKEY); [[ -n "$BAZARR_APIKEY" ]] || BAZARR_APIKEY=$(gen_apikey)
    for v in RADARR_APIKEY SONARR_APIKEY PROWLARR_APIKEY BAZARR_APIKEY; do state_set "$v" "${!v}"; done

    # ---- directories ----
    local d
    for d in \
        "$CONFIG_DIR"/{gluetun,qbittorrent,prowlarr,sonarr,radarr,bazarr,jellyfin,jellyfin-cache,seerr} \
        "$DATA_DIR"/media/{movies,tv} \
        "$DATA_DIR"/torrents/{movies,tv}; do
        mkdir -p "$d"
    done

    # Seerr runs as uid 1000 (node) inside its container.
    chown -R 1000:1000 "$CONFIG_DIR/seerr" 2>/dev/null \
        || sudo -n chown -R 1000:1000 "$CONFIG_DIR/seerr" 2>/dev/null \
        || warn "could not chown $CONFIG_DIR/seerr — Seerr may fail to write its config"
    # All linuxserver apps + Jellyfin write their config as PUID:PGID.
    chown -R "$PUID:$PGID" \
        "$CONFIG_DIR"/{qbittorrent,prowlarr,sonarr,radarr,bazarr,jellyfin,jellyfin-cache} \
        "$DATA_DIR" 2>/dev/null \
        || sudo -n chown -R "$PUID:$PGID" \
            "$CONFIG_DIR"/{qbittorrent,prowlarr,sonarr,radarr,bazarr,jellyfin,jellyfin-cache} \
            "$DATA_DIR" 2>/dev/null \
        || warn "could not chown config/data dirs — check ownership for the media apps"

    chown -R "$PUID:$PGID" "$CONFIG_DIR"/{radarr,sonarr,prowlarr} 2>/dev/null || true

    # OpenVPN custom config gets mounted into gluetun.
    if [[ "$VPN_VENDOR" == "custom-openvpn" ]]; then
        cp "$OVPN_CONF_PATH" "$CONFIG_DIR/gluetun/custom.ovpn"
        chmod 644 "$CONFIG_DIR/gluetun/custom.ovpn"
    fi

    # ---- copy templates into the install dir ----
    cp "$REPO_DIR/templates/docker-compose.yml" "$INSTALL_DIR/docker-compose.yml"
    cp "$REPO_DIR/templates/docker-compose.novpn.yml" "$INSTALL_DIR/docker-compose.novpn.yml"
    mkdir -p "$INSTALL_DIR/portforward-sync"
    cp "$REPO_DIR/templates/portforward-sync/"* "$INSTALL_DIR/portforward-sync/"

    # ---- .env ----
    local host_ip
    host_ip=$(ip -4 route get 1.1.1.1 2>/dev/null | grep -oP 'src \K[\d.]+' | head -1 || true)
    JELLYFIN_PUBLISHED_URL="http://${host_ip:-localhost}:8096"
    state_set HOST_IP "${host_ip:-localhost}"

    # Allow outbound from VPN-side containers to LAN + both docker subnets —
    # the fixed mediasuite subnet (compose template) must be here so Prowlarr
    # can reach Radarr/Sonarr through the gluetun network.
    local docker_subnet
    docker_subnet=$(docker network inspect bridge -f '{{(index .IPAM.Config 0).Subnet}}' 2>/dev/null || echo "172.17.0.0/16")

    local vpn_provider vpn_type
    case "$VPN_VENDOR" in
        airvpn)           vpn_provider=airvpn;      vpn_type=wireguard ;;
        custom-wireguard) vpn_provider=${VPN_SERVICE_PROVIDER_NAME:-custom}; vpn_type=wireguard ;;
        custom-openvpn)   vpn_provider=${VPN_SERVICE_PROVIDER_NAME:-custom}; vpn_type=openvpn ;;
        none)             vpn_provider=none;        vpn_type=none ;;
    esac

    cat > "$INSTALL_DIR/.env" <<EOF
CONFIG_DIR=$CONFIG_DIR
DATA_DIR=$DATA_DIR
TZ=$TZ
PUID=$PUID
PGID=$PGID
QBIT_USER=$QBIT_USER
QBIT_PASS=$QBIT_PASS
JELLYFIN_PUBLISHED_URL=$JELLYFIN_PUBLISHED_URL
HOST_IP=${host_ip:-localhost}

VPN_SERVICE_PROVIDER=$vpn_provider
VPN_TYPE=$vpn_type
VPN_PORT_FORWARDING=${VPN_PORT_FORWARDING:-off}
WIREGUARD_PRIVATE_KEY=${WIREGUARD_PRIVATE_KEY:-}
WIREGUARD_PRESHARED_KEY=${WIREGUARD_PRESHARED_KEY:-}
WIREGUARD_ADDRESSES=${WIREGUARD_ADDRESSES:-}
WIREGUARD_PUBLIC_KEY=${WIREGUARD_PUBLIC_KEY:-}
SERVER_HOSTNAMES=${SERVER_HOSTNAMES:-}
OPENVPN_USER=${OPENVPN_USER:-}
OPENVPN_PASSWORD=${OPENVPN_PASSWORD:-}
FIREWALL_OUTBOUND_SUBNETS=$LAN_SUBNET,$docker_subnet,10.97.0.0/24
EOF
    # endpoint vars are emitted per-type — gluetun parses an irrelevant
    # OPENVPN_ENDPOINT_* even under VPN_TYPE=wireguard; none is emitted at all
    # when the VPN is skipped
    case "$vpn_type" in
        wireguard)
            printf 'WIREGUARD_ENDPOINT_IP=%s\nWIREGUARD_ENDPOINT_PORT=%s\n' \
                "${VPN_ENDPOINT_IP:-}" "${VPN_ENDPOINT_PORT:-}" >> "$INSTALL_DIR/.env" ;;
        openvpn)
            printf 'OPENVPN_ENDPOINT_IP=%s\nOPENVPN_ENDPOINT_PORT=%s\n' \
                "${VPN_ENDPOINT_IP:-}" "${VPN_ENDPOINT_PORT:-}" >> "$INSTALL_DIR/.env" ;;
    esac
    chmod 600 "$INSTALL_DIR/.env"

    _setup_dashboard
    ok "Deploy directory ready at $INSTALL_DIR"

    # ---- boot ----
    step "docker compose up"
    local profiles=()
    [[ "${VPN_PORT_FORWARDING:-N}" == "Y" && "${VPN_VENDOR:-airvpn}" != "none" ]] \
        && profiles=(--profile portforward)
    if [[ "${IMAGE_SOURCE:-latest}" == "bundled" ]]; then
        local tar found=0
        for tar in "$REPO_DIR"/bundle/images*.tar*; do
            [[ -f "$tar" ]] || continue
            found=1
            step "loading bundled images from $(basename "$tar")"
            docker load -i "$tar" >/dev/null || die "docker load failed: $tar"
        done
        (( found )) || die "IMAGE_SOURCE=bundled but no bundle/images*.tar* found — run ./bundle.sh or choose 'Download latest'"
        # arch sanity check
        if [[ -f "$REPO_DIR/bundle/manifest.txt" ]]; then
            local barch; barch=$(grep '^arch=' "$REPO_DIR/bundle/manifest.txt" | cut -d= -f2)
            [[ -n "$barch" && "$barch" != "$(uname -m)" ]] \
                && warn "bundle was built for $barch but this host is $(uname -m) — images may not run"
        fi
    else
        compose_cmd "${profiles[@]}" pull || die "image pull failed"
    fi
    compose_cmd "${profiles[@]}" up -d || die "compose up failed"

    # compose doesn't remove containers for services that became profile-gated —
    # drop the VPN-only helpers when switching to no-VPN mode
    if [[ "${VPN_VENDOR:-airvpn}" == "none" ]]; then
        docker rm -f deunhealth portforward-sync >/dev/null 2>&1 || true
    fi
    state_set COMPOSE_PROFILES "${profiles[*]}"
    ok "Containers started"
}
