# Fake "VPN provider" for testing: a WireGuard endpoint that NATs traffic onward.
# Gluetun connects to it as a custom WireGuard provider; qBittorrent/Prowlarr
# traffic then flows through the tunnel exactly as it would through AirVPN.
FROM alpine:3.21
RUN apk add --no-cache wireguard-tools iptables
COPY entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/bin/sh", "/entrypoint.sh"]
