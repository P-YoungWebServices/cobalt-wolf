#!/usr/bin/env bash
# Phase 2: Docker Engine + compose plugin on Ubuntu.

install_docker() {
    step "Docker"

    if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
        ok "Docker already installed: $(docker --version | grep -oP '[\d.]+' | head -1), $(docker compose version --short 2>/dev/null || echo 'compose ok')"
        _docker_group_check; return 0
    fi

    . /etc/os-release
    [[ "${ID:-}" == "ubuntu" || "${ID_LIKE:-}" == *ubuntu* || "${ID_LIKE:-}" == *debian* ]] \
        || warn "This installer targets Ubuntu; '$ID' detected — proceeding anyway"

    # Bundled debs: install Docker Engine from ./bundle/debs without the web repo.
    if [[ "${IMAGE_SOURCE:-latest}" == "bundled" ]] \
        && compgen -G "$REPO_DIR/bundle/debs/*.deb" >/dev/null; then
        info "Installing Docker Engine from bundled .deb packages"
        sudo -v
        sudo apt-get install -y -qq "$REPO_DIR"/bundle/debs/*.deb \
            || sudo dpkg -i "$REPO_DIR"/bundle/debs/*.deb \
            || die "bundled Docker install failed — re-run ./bundle.sh on a matching Ubuntu or choose 'Download latest'"
        if command -v systemctl >/dev/null 2>&1 && systemctl list-units >/dev/null 2>&1; then
            sudo systemctl enable --now docker
        fi
        docker compose version >/dev/null 2>&1 || die "docker compose plugin missing after bundled install"
        ok "Docker installed from bundle: $(docker --version | grep -oP '[\d.]+' | head -1)"
        _docker_group_check; return 0
    fi
    [[ "${IMAGE_SOURCE:-latest}" == "bundled" ]] \
        && warn "bundle/debs has no .deb files — falling back to Docker's apt repo (needs internet)"

    # Map derivative codenames (Mint, Pop!_OS ...) to the Ubuntu base codename.
    local codename="${UBUNTU_CODENAME:-${VERSION_CODENAME:-}}"
    [[ -n "$codename" ]] || die "could not determine Ubuntu codename"
    info "Using Docker apt repo for codename '$codename'"

    info "Installing prerequisites"
    sudo -v   # prime sudo credentials so nothing later blocks on a password
    sudo apt-get update -qq
    sudo apt-get install -y -qq ca-certificates curl gnupg jq python3 iproute2 openssl

    info "Adding Docker's apt repository"
    sudo install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | sudo gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
    sudo chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $codename stable" \
        | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null

    sudo apt-get update -qq
    sudo apt-get install -y -qq \
        docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    if command -v systemctl >/dev/null 2>&1 && systemctl list-units >/dev/null 2>&1; then
        sudo systemctl enable --now docker
    else
        warn "No systemd — start dockerd yourself if it is not running"
    fi

    docker compose version >/dev/null 2>&1 || die "docker compose plugin missing after install"
    ok "Docker installed: $(docker --version | grep -oP '[\d.]+' | head -1)"
    _docker_group_check
}

_docker_group_check() {
    if ! docker info >/dev/null 2>&1; then
        info "Adding $USER to the docker group"
        sudo usermod -aG docker "$USER"
        if ! docker info >/dev/null 2>&1; then
            warn "Group change needs a new login session."
            warn "Run 'newgrp docker' (or log out/in), then re-run ./install.sh — it resumes where it left off."
            exit 1
        fi
    fi
    ok "Docker daemon reachable"
}
