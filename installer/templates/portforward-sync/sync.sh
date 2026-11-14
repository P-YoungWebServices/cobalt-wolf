#!/bin/sh
# Watches gluetun's forwarded-port file and pushes it into qBittorrent.
# Shares gluetun's network namespace, so qBittorrent is at 127.0.0.1:8080.

PORT_FILE=/state/forwarded_port
QBIT=http://127.0.0.1:8080
LAST=""

log() { echo "[pf-sync] $*"; }

JAR=/tmp/qbit.cookies

apply_port() {
    port="$1"
    rm -f "$JAR"
    if ! curl -s --max-time 10 -c "$JAR" \
        --data-urlencode "username=${QBIT_USER}" \
        --data-urlencode "password=${QBIT_PASS}" \
        "${QBIT}/api/v2/auth/login" >/dev/null \
        || ! grep -q "SID" "$JAR"; then
        log "login failed (qBittorrent not ready or bad credentials); will retry"
        return 1
    fi
    curl -s --max-time 10 -b "$JAR" \
        --data-urlencode "json={\"listen_port\":${port},\"random_port\":false,\"upnp\":false,\"upnp_lease_duration\":0,\"natpmp\":false}" \
        "${QBIT}/api/v2/app/setPreferences" >/dev/null \
        && log "qBittorrent listen port set to ${port}"
}

log "watching ${PORT_FILE}"
while :; do
    if [ -f "$PORT_FILE" ]; then
        port=$(tr -dc '0-9' < "$PORT_FILE")
        if [ -n "$port" ] && [ "$port" != "0" ] && [ "$port" != "$LAST" ]; then
            apply_port "$port" && LAST="$port"
        fi
    fi
    sleep 15
done
