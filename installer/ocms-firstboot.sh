#!/usr/bin/env bash
# ocms-firstboot — runs once on the ISO-installed system's first boot.
# If stack answers were baked into the ISO (.state present), install fully
# unattended. Otherwise drop a hook that launches the interactive installer
# on the user's first login.
set -euo pipefail

OCMS=/opt/OneClickMediaSuite

if grep -q 'JELLYFIN_ADMIN_PASS' "$OCMS/.state/install.state" 2>/dev/null; then
    exec "$OCMS/install.sh" docker
else
    cat > /etc/profile.d/zz-ocms-install.sh <<'EOF'
# one-shot: run the media-suite installer on first interactive login
if [ -t 1 ] && [ -f /opt/OneClickMediaSuite/install.sh ]; then
    echo
    echo "=== OneClick Media Suite — first login setup ==="
    echo "The installer will ask for VPN/media settings once, then run unattended."
    echo
    sudo /opt/OneClickMediaSuite/install.sh
    sudo rm -f /etc/profile.d/zz-ocms-install.sh
fi
EOF
    chmod 644 /etc/profile.d/zz-ocms-install.sh
fi
