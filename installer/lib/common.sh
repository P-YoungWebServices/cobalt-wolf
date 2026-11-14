#!/usr/bin/env bash
# Common helpers shared by all installer phases.

# ---------- logging ----------
if [[ -t 1 ]]; then
    C_RED=$'\033[0;31m' C_GRN=$'\033[0;32m' C_YLW=$'\033[0;33m'
    C_BLU=$'\033[0;34m' C_CYN=$'\033[0;36m' C_BLD=$'\033[1m' C_RST=$'\033[0m'
else
    C_RED='' C_GRN='' C_YLW='' C_BLU='' C_CYN='' C_BLD='' C_RST=''
fi

info()  { printf '%s[*]%s %s\n'  "$C_BLU" "$C_RST" "$*"; }
ok()    { printf '%s[+]%s %s\n'  "$C_GRN" "$C_RST" "$*"; }
warn()  { printf '%s[!]%s %s\n'  "$C_YLW" "$C_RST" "$*" >&2; }
err()   { printf '%s[-]%s %s\n'  "$C_RED" "$C_RST" "$*" >&2; }
step()  { printf '\n%s== %s ==%s\n' "$C_BLD" "$*" "$C_RST"; }
die()   { err "$*"; exit 1; }

# ---------- state file ----------
# STATE_FILE holds KEY=value lines persisted across phases.
state_set() {
    local key="$1" value="$2"
    touch "$STATE_FILE"; chmod 600 "$STATE_FILE"
    if grep -q "^${key}=" "$STATE_FILE" 2>/dev/null; then
        sed -i "s|^${key}=.*|${key}=${value}|" "$STATE_FILE"
    else
        printf '%s=%s\n' "$key" "$value" >> "$STATE_FILE"
    fi
}

state_get() {
    local key="$1" default="${2:-}"
    local line
    line=$(grep "^${key}=" "$STATE_FILE" 2>/dev/null | tail -1)
    if [[ -n "$line" ]]; then printf '%s' "${line#*=}"; else printf '%s' "$default"; fi
}

# ---------- http helpers ----------
# http_req METHOD URL [CURL_ARGS...] — prints body, returns curl's status.
http_req() {
    local method="$1" url="$2"; shift 2
    curl -fsS -X "$method" "$url" "$@"
}

# Wait until an HTTP endpoint answers — any status code counts (connection
# refused is what we are really waiting out); some APIs 401/403 unauthenticated.
wait_http() {
    local name="$1" url="$2" timeout="${3:-180}" elapsed=0
    info "Waiting for $name at $url ..."
    while (( elapsed < timeout )); do
        if curl -sS -o /dev/null --max-time 5 "$url" 2>/dev/null; then
            ok "$name is up (${elapsed}s)"; return 0
        fi
        sleep 3; elapsed=$((elapsed + 3))
    done
    err "$name did not come up within ${timeout}s"
    return 1
}

# Wait for a docker container to be running.
wait_container() {
    local name="$1" timeout="${2:-120}" elapsed=0
    while (( elapsed < timeout )); do
        if [[ "$(docker inspect -f '{{.State.Running}}' "$name" 2>/dev/null)" == "true" ]]; then
            return 0
        fi
        sleep 2; elapsed=$((elapsed + 2))
    done
    err "container $name not running after ${timeout}s"; return 1
}

# ---------- json ----------
# json_get 'path' — reads JSON on stdin, prints value (needs python3).
json_get() {
    python3 -c "
import json,sys
d=json.load(sys.stdin)
cur=d
for p in '$1'.split('.'):
    if p=='': continue
    cur=cur[int(p)] if isinstance(cur,list) else cur[p]
print(cur if not isinstance(cur,(dict,list)) else json.dumps(cur))
"
}

# jq-lite: json_arr '.[] | .name' style — kept simple: pass a python expression.
json_eval() {
    python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"
}

need_cmd() { command -v "$1" >/dev/null 2>&1 || die "required command missing: $1"; }

gen_apikey() { cat /proc/sys/kernel/random/uuid | tr -d '-'; }

# docker compose with the correct -f set. In no-VPN mode the override file
# swaps gluetun for an inert network holder.
compose_cmd() {
    local files=(-f docker-compose.yml)
    [[ "${VPN_VENDOR:-airvpn}" == "none" ]] && files+=(-f docker-compose.novpn.yml)
    (cd "$INSTALL_DIR" && docker compose "${files[@]}" "$@")
}
