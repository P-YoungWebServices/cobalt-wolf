#!/usr/bin/env bash
# Spin up the fake VPN endpoint and patch an existing OCMS install's .env so
# gluetun tunnels through it. Run INSIDE the environment under test.
#
#   ./run.sh /path/to/oneclick-media-suite   (dir containing .env)
#
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="${1:?usage: run.sh <install dir containing .env>}"

# --- keys ---
WORK=$(mktemp -d)
docker run --rm --entrypoint sh alpine:3.21 -c \
    'apk add --no-cache -q wireguard-tools >/dev/null && wg genkey' > "$WORK/server.key"
docker run --rm --entrypoint sh alpine:3.21 -c \
    'apk add --no-cache -q wireguard-tools >/dev/null && wg genkey' > "$WORK/client.key"
docker run --rm --entrypoint sh -v "$WORK:/k" alpine:3.21 -c \
    'apk add --no-cache -q wireguard-tools >/dev/null && wg pubkey < /k/server.key' > "$WORK/server.pub"
docker run --rm --entrypoint sh -v "$WORK:/k" alpine:3.21 -c \
    'apk add --no-cache -q wireguard-tools >/dev/null && wg pubkey < /k/client.key' > "$WORK/client.pub"

# --- build + launch the fake server on the mediasuite network ---
docker build -t ocms-fake-vpn "$HERE"
docker rm -f fake-vpn 2>/dev/null || true
docker run -d --name fake-vpn --cap-add NET_ADMIN \
    --sysctl net.ipv4.ip_forward=1 \
    -v "$WORK:/keys:ro" \
    --network mediasuite \
    ocms-fake-vpn
sleep 2
if [[ "$(docker inspect -f '{{.State.Status}}' fake-vpn)" != "running" ]]; then
    docker logs fake-vpn >&2 || true
    echo "fake-vpn container failed to start" >&2
    exit 1
fi
SERVER_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' fake-vpn)
[[ "$SERVER_IP" =~ ^[0-9.]+$ ]] || { echo "no IP for fake-vpn (got '$SERVER_IP')" >&2; exit 1; }
SERVER_PUB=$(cat "$WORK/server.pub")

# --- point the installer's gluetun at it ---
sed -i \
    -e "s|^VPN_SERVICE_PROVIDER=.*|VPN_SERVICE_PROVIDER=custom|" \
    -e "s|^VPN_TYPE=.*|VPN_TYPE=wireguard|" \
    -e "s|^WIREGUARD_PRIVATE_KEY=.*|WIREGUARD_PRIVATE_KEY=$(cat "$WORK/client.key")|" \
    -e "s|^WIREGUARD_PUBLIC_KEY=.*|WIREGUARD_PUBLIC_KEY=$SERVER_PUB|" \
    -e "s|^WIREGUARD_ADDRESSES=.*|WIREGUARD_ADDRESSES=10.99.0.2/32|" \
    -e "s|^VPN_ENDPOINT_IP=.*|VPN_ENDPOINT_IP=$SERVER_IP|" \
    -e "s|^VPN_ENDPOINT_PORT=.*|VPN_ENDPOINT_PORT=51820|" \
    -e "s|^SERVER_HOSTNAMES=.*|SERVER_HOSTNAMES=|" \
    -e "s|^VPN_PORT_FORWARDING=.*|VPN_PORT_FORWARDING=off|" \
    "$INSTALL_DIR/.env"

(cd "$INSTALL_DIR" && docker compose up -d)
echo
echo "fake-vpn running at $SERVER_IP (mediasuite net); server pubkey $SERVER_PUB"
echo "verify:  docker run --rm --network container:gluetun curlimages/curl -s ifconfig.me"
echo "         docker exec fake-vpn wg show   # handshake + transfer bytes"
