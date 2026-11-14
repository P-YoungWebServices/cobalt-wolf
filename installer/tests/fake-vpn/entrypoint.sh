#!/bin/sh
# Brings up wg0 as a fake VPN server:
#   server: 10.99.0.1, client (gluetun): 10.99.0.2
#   keys: /keys/server.key + /keys/client.pub (mounted by run.sh)
set -e

ip link add wg0 type wireguard
# interface options (private-key, listen-port) must precede peer sections
wg set wg0 \
    listen-port 51820 \
    private-key /keys/server.key \
    peer "$(cat /keys/client.pub)" allowed-ips 10.99.0.2/32
ip addr add 10.99.0.1/24 dev wg0
ip link set wg0 up

# ip_forward is enabled via --sysctl at container start (entrypoint's own
# sysctl would fail on the read-only /proc/sys)
iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
iptables -A FORWARD -i wg0 -j ACCEPT
iptables -A FORWARD -o wg0 -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT

echo "fake-vpn up; server pubkey: $(wg pubkey < /keys/server.key)"
exec sleep infinity
