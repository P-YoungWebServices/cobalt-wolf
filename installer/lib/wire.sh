#!/usr/bin/env bash
# Phase 4: wait for every service, then wire them together over their HTTP APIs.
# Everything here is idempotent-ish: existing objects are detected and skipped.

# ---------- generic Servarr helper ----------
# arr_api HOST KEY METHOD PATH [JSON]
arr_api() {
    local host="$1" key="$2" method="$3" path="$4" body="${5:-}"
    local args=(-fsS -X "$method" -H "X-Api-Key: $key" -H "Content-Type: application/json")
    [[ -n "$body" ]] && args+=(-d "$body")
    curl "${args[@]}" "${host}${path}"
}

# Build a creation payload from an implementation's schema, overriding fields.
# arr_schema_payload HOST KEY SCHEMA_RESOURCE IMPL_NAME FIELD_OVERRIDES_JSON EXTRA_JSON
# FIELD_OVERRIDES_JSON: {"field.name":"value",...}   EXTRA_JSON: {"prop":val} merged at top level.
arr_schema_payload() {
    local host="$1" key="$2" res="$3" impl="$4" overrides="$5" extra="${6:-}"
    [[ -z "$extra" ]] && extra='{}'
    arr_api "$host" "$key" GET "$res/schema" | python3 -c "
import json,sys
schemas=json.load(sys.stdin)
impl='$impl'
over=json.loads('''$overrides''')
extra=json.loads('''$extra''')
match=[s for s in schemas if s.get('implementation')==impl or s.get('implementationName')==impl]
if not match:
    sys.exit('no schema for '+impl)
s=match[0]
payload={k:v for k,v in s.items() if k not in ('presets','fields')}
payload['fields']=[]
for f in s['fields']:
    f=dict(f)
    if f['name'] in over:
        f['value']=over[f['name']]
    payload['fields'].append(f)
payload.update(extra)
payload.pop('id',None)
print(json.dumps(payload))
"
}

# Return the id of an existing resource by name, or empty.
arr_find_id() { # arr_find_id HOST KEY RESOURCE NAME
    arr_api "$1" "$2" GET "$3" | python3 -c "
import json,sys
for it in json.load(sys.stdin):
    if it.get('name','').lower()=='$4'.lower(): print(it['id']); break
"
}

# ---------- qBittorrent ----------
QBIT=http://localhost:8080
QBIT_COOKIE=/tmp/qbit.cookie

qbit_login() { # qbit_login USER PASS -> session cookie in the jar (body is empty on 5.x)
    rm -f "$QBIT_COOKIE"
    curl -fsS -c "$QBIT_COOKIE" \
        --data-urlencode "username=$1" --data-urlencode "password=$2" \
        "$QBIT/api/v2/auth/login" >/dev/null 2>&1 \
        && grep -q "SID" "$QBIT_COOKIE"
}

qbit_pref() { # qbit_pref JSON
    curl -fsS -b "$QBIT_COOKIE" --data-urlencode "json=$1" "$QBIT/api/v2/app/setPreferences"
}

wire_qbittorrent() {
    step "qBittorrent"
    wait_http "qBittorrent WebUI" "$QBIT/api/v2/app/version" 300 || return 1

    # Try the requested credentials first (re-runs); on a fresh install qBittorrent
    # instead issues a temporary password to the log which we then replace.
    if qbit_login "$QBIT_USER" "$QBIT_PASS"; then
        ok "logged in as $QBIT_USER"
    else
        local temp_pass
        temp_pass=$(docker logs qbittorrent 2>&1 \
            | grep -oP 'temporary password is provided for this session:\s*\K\S+' | tail -1 || true)
        if [[ -n "$temp_pass" ]] && qbit_login "admin" "$temp_pass"; then
            qbit_pref "{\"web_ui_username\":\"$QBIT_USER\",\"web_ui_password\":\"$QBIT_PASS\"}" \
                || die "could not set qBittorrent credentials"
            qbit_login "$QBIT_USER" "$QBIT_PASS" || die "qBittorrent login failed after password set"
            ok "credentials set for $QBIT_USER"
        else
            die "could not log in to qBittorrent (tried $QBIT_USER and the temporary password)"
        fi
    fi

    # No NAT-PMP/UPnP (the VPN owns the port), sane defaults for automation.
    qbit_pref '{"upnp":false,"natpmp":false,"random_port":false,"dht":true,"pex":true,"lsd":true,"save_path":"/data/torrents","auto_tmm_enabled":true}' >/dev/null

    # Categories so imports land in the right place for each *arr.
    # Per-app categories — each *arr only watches its own, so downloads never
    # cross-import. savePaths keep the torrents/{movies,tv} layout; edit fixes
    # categories the apps already auto-created without one.
    for cat in "radarr=/data/torrents/movies" "tv-sonarr=/data/torrents/tv"; do
        local cname="${cat%%=*}" cpath="${cat#*=}"
        curl -fsS -b "$QBIT_COOKIE" --data "category=$cname&savePath=$cpath" \
            "$QBIT/api/v2/torrents/createCategory" >/dev/null 2>&1 \
            || curl -fsS -b "$QBIT_COOKIE" --data "category=$cname&savePath=$cpath" \
                "$QBIT/api/v2/torrents/editCategory" >/dev/null 2>&1 || true
    done
    ok "download categories 'radarr' and 'tv-sonarr' configured"
}

# ---------- Radarr / Sonarr (shared shape) ----------
arr_apikey() { # svc dir name — live ApiKey from config.xml
    grep -oP '<ApiKey>\K[^<]+' "$CONFIG_DIR/$1/config.xml" 2>/dev/null | head -1
}

arr_wait_key() { # svc — echo ApiKey, waiting for the app to write config.xml
    local key="" t=0
    while (( t < 120 )); do
        key=$(arr_apikey "$1")
        [[ -n "$key" ]] && { printf '%s' "$key"; return 0; }
        sleep 3; t=$((t + 3))
    done
    return 1
}

wire_arr() { # wire_arr APP PORT APIKEY QBIT_CATEGORY ROOT_PATH
    local app="$1" port="$2" key="$3" category="$4" root="$5"
    local host="http://localhost:$port"
    step "$app"
    wait_http "$app" "$host/ping" 300 || return 1

    # root folder
    local have
    have=$(arr_api "$host" "$key" GET /api/v3/rootfolder \
        | python3 -c "import json,sys;print(any(r['path']=='$root' for r in json.load(sys.stdin)))" 2>/dev/null || echo False)
    [[ "$have" == "True" ]] || arr_api "$host" "$key" POST /api/v3/rootfolder "{\"path\":\"$root\"}" >/dev/null
    ok "root folder $root"

    # download client -> qBittorrent (reached through the gluetun network namespace)
    if [[ -z "$(arr_find_id "$host" "$key" /api/v3/downloadclient qBittorrent)" ]]; then
        local payload
        # category field is app-specific: movieCategory on Radarr, tvCategory on Sonarr
        local catfield=tvCategory
        [[ "$app" == "Radarr" ]] && catfield=movieCategory
        payload=$(arr_schema_payload "$host" "$key" /api/v3/downloadclient QBittorrent \
            "{\"host\":\"gluetun\",\"port\":8080,\"useSsl\":false,\"username\":\"$QBIT_USER\",\"password\":\"$QBIT_PASS\",\"$catfield\":\"$category\"}" \
            '{"name":"qBittorrent","enable":true,"protocol":"torrent","priority":1}')
        arr_api "$host" "$key" POST /api/v3/downloadclient "$payload" >/dev/null \
            || warn "$app: download client add failed — add qBittorrent manually in Settings -> Download Clients"
        ok "download client qBittorrent (gluetun:8080, category '$category')"
    else
        ok "download client already present"
    fi

    # notification -> Jellyfin (library refresh on import)
    local jf_impl
    jf_impl=$(arr_api "$host" "$key" GET /api/v3/notification/schema 2>/dev/null \
        | python3 -c "
import json,sys
names=[s['implementation'] for s in json.load(sys.stdin)]
print('Jellyfin' if 'Jellyfin' in names else ('MediaBrowser' if 'MediaBrowser' in names else ''))" || true)
    if [[ -n "$jf_impl" && -n "$JELLYFIN_APIKEY" ]]; then
        if [[ -z "$(arr_find_id "$host" "$key" /api/v3/notification Jellyfin)" ]]; then
            local payload
            payload=$(arr_schema_payload "$host" "$key" /api/v3/notification "$jf_impl" \
                "{\"host\":\"jellyfin\",\"port\":8096,\"useSsl\":false,\"apiKey\":\"$JELLYFIN_APIKEY\"}" \
                '{"name":"Jellyfin","onDownload":true,"onUpgrade":true,"onRename":true,"onHealthIssue":false,"supportsOnDownload":true,"supportsOnUpgrade":true,"supportsOnRename":true,"supportsOnHealthIssue":false}')
            arr_api "$host" "$key" POST /api/v3/notification "$payload" >/dev/null \
                || warn "$app: Jellyfin notification add failed (non-fatal)"
            ok "Jellyfin notification configured"
        else
            ok "Jellyfin notification already present"
        fi
    else
        warn "$app: no Jellyfin notification implementation or API key — skipped (non-fatal)"
    fi
}

# ---------- Prowlarr ----------
wire_prowlarr() {
    step "Prowlarr"
    local host=http://localhost:9696
    wait_http "Prowlarr" "$host/ping" 300 || return 1

    _prowlarr_add_app() { # name impl prowlarrUrl baseUrl apiKey syncCategories
        [[ -n "$(arr_find_id "$host" "$PROWLARR_APIKEY" /api/v1/applications "$1")" ]] && { ok "$1 already registered"; return; }
        local payload
        payload=$(arr_schema_payload "$host" "$PROWLARR_APIKEY" /api/v1/applications "$2" \
            "{\"prowlarrUrl\":\"$3\",\"baseUrl\":\"$4\",\"apiKey\":\"$5\",\"syncCategories\":$6}" \
            "{\"name\":\"$1\",\"syncLevel\":\"fullSync\"}")
        arr_api "$host" "$PROWLARR_APIKEY" POST /api/v1/applications "$payload" >/dev/null \
            || warn "Prowlarr: failed to register $1 — add it under Settings -> Apps"
        ok "application $1 registered (indexers will sync automatically)"
    }

    # baseUrl uses the pinned mediasuite IPs: containers sharing gluetun's
    # network namespace lose Docker's embedded DNS (gluetun replaces
    # resolv.conf), so service names don't resolve for Prowlarr.
    _prowlarr_add_app Radarr Radarr   "http://gluetun:9696" "http://10.97.0.4:7878" "$RADARR_APIKEY" "[2000,2010,2020,2030,2035,2040,2045,2050,2060,2070,2080]"
    _prowlarr_add_app Sonarr Sonarr   "http://gluetun:9696" "http://10.97.0.5:8989" "$SONARR_APIKEY" "[5000,5010,5020,5030,5040,5045,5050,5060,5070,5080,5090]"

    warn "Add your indexers in Prowlarr -> Indexers; they sync to Radarr/Sonarr automatically."
}

# ---------- Bazarr ----------
wire_bazarr() {
    step "Bazarr"
    local host=http://localhost:6767
    wait_http "Bazarr" "$host/api/system/status?apikey=$BAZARR_APIKEY" 300 || {
        # apikey may not be ours yet on a brand-new config; try unauthenticated reachability
        wait_http "Bazarr" "$host" 60 || return 1
    }

    # Patch config.yaml with our apikey + sonarr/radarr connections, then restart.
    docker stop bazarr >/dev/null
    python3 - "$CONFIG_DIR/bazarr/config/config.yaml" <<EOF
import sys
try:
    import yaml
except ImportError:
    yaml=None
path=sys.argv[1]
import re
text=open(path).read()
def set_key(text, section, key, value):
    # replace '  key: ...' under 'section:' block (2-space indent style used by bazarr)
    pat=re.compile(r'(?ms)^'+re.escape(section)+r':\n((?:  [^\n]*\n?)*)')
    m=pat.search(text)
    if not m:
        return text + f'\n{section}:\n  {key}: {value}\n'
    block=m.group(1)
    if re.search(r'^  '+re.escape(key)+r':', block, re.M):
        block=re.sub(r'(?m)^  '+re.escape(key)+r':.*$', f'  {key}: {value}', block)
    else:
        block=block+f'  {key}: {value}\n'
    return text[:m.start(1)] + block + text[m.end(1):]

text=set_key(text,'general','apikey','$BAZARR_APIKEY')
text=set_key(text,'general','use_sonarr','true')
text=set_key(text,'general','use_radarr','true')
for svc,port,key in (('sonarr',8989,'$SONARR_APIKEY'),('radarr',7878,'$RADARR_APIKEY')):
    text=set_key(text,svc,'ip',svc)
    text=set_key(text,svc,'port',port)
    text=set_key(text,svc,'base_url','/')
    text=set_key(text,svc,'ssl','false')
    text=set_key(text,svc,'apikey',key)
open(path,'w').write(text)
print("config.yaml patched")
EOF
    docker start bazarr >/dev/null
    ok "Sonarr/Radarr connections written to config.yaml"
}

# ---------- Jellyfin ----------
JELLY=http://localhost:8096
JELLY_TOKEN=""

jelly_req() { # jelly_req METHOD PATH [DATA] — Jellyfin 12 needs Authorization, not X-Emby-Token
    local m="$1" p="$2" d="${3:-}"
    local args=(-fsS -X "$m" -H "Content-Type: application/json")
    [[ -n "$JELLY_TOKEN" ]] && args+=(-H "Authorization: MediaBrowser Token=\"$JELLY_TOKEN\"")
    [[ -n "$d" ]] && args+=(-d "$d")
    curl "${args[@]}" "$JELLY$p"
}

urlencode() { python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$1"; }

wire_jellyfin() {
    step "Jellyfin"
    wait_http "Jellyfin" "$JELLY/System/Info/Public" 300 || return 1

    # /System/Info/Public answers before the Startup endpoints register; give
    # them a few seconds. 404 here means the wizard was already completed.
    # /System/Info/Public answers before the Startup endpoints register, and
    # the endpoints return 404 once the wizard completes — so drive the flow
    # off the StartupWizardCompleted flag and retry around transient 404s.
    local wizard_done waited=0
    while (( waited < 60 )); do
        wizard_done=$(curl -fsS --max-time 5 "$JELLY/System/Info/Public" 2>/dev/null \
            | python3 -c 'import json,sys;print(json.load(sys.stdin).get("StartupWizardCompleted", ""))' 2>/dev/null || true)
        [[ "$wizard_done" == "False" || "$wizard_done" == "True" ]] && break
        sleep 3; waited=$((waited + 3))
    done
    if [[ "$wizard_done" == "False" ]]; then
        info "running startup wizard via API"
        # IMPORTANT: a wizard that completes with no user bricks the instance —
        # every endpoint then demands credentials none exist for. Verify each
        # step and never POST /Startup/Complete until the admin login works.
        local cfg_json="" cfg_ok="" tries=0
        while (( tries < 10 )); do
            cfg_json=$(jelly_req GET /Startup/Configuration 2>/dev/null \
                | python3 -c 'import json,sys;print(json.dumps(json.load(sys.stdin)))' 2>/dev/null || true)
            [[ -n "$cfg_json" ]] || { sleep 3; tries=$((tries + 1)); continue; }
            jelly_req POST /Startup/Configuration "$cfg_json" >/dev/null 2>&1 \
                && { cfg_ok=1; break; }
            sleep 3; tries=$((tries + 1))
        done
        [[ -n "$cfg_ok" ]] || die "Jellyfin startup configuration step failed"

        # JF12 step-gates /Startup/User: it 404s until the wizard's user step
        # is exposed, which GET /Startup/FirstUser does.
        tries=0
        until (( tries >= 10 )); do
            code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$JELLY/Startup/FirstUser" || true)
            [[ "$code" == "200" ]] && break
            sleep 3; tries=$((tries + 1))
        done
        until (( tries >= 10 )); do
            # 204 created; 403 means a user already exists (partial earlier run)
            code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$JELLY/Startup/User" \
                -H 'Content-Type: application/json' \
                -d "{\"Name\":\"$JELLYFIN_ADMIN_USER\",\"Password\":\"$JELLYFIN_ADMIN_PASS\"}" || true)
            [[ "$code" == "204" || "$code" == "403" ]] && break
            sleep 3; tries=$((tries + 1))
        done
        (( tries < 10 )) || die "Jellyfin admin user creation failed — wizard left open, safe to re-run"

        # confirm the account authenticates before sealing the wizard
        tries=0
        until (( tries >= 10 )); do
            JELLY_TOKEN=$(curl -fsS -X POST "$JELLY/Users/AuthenticateByName" \
                -H 'Authorization: MediaBrowser Client="OneClickMediaSuite", Device="installer", DeviceId="ocms-install", Version="1.0"' \
                -H 'Content-Type: application/json' \
                -d "{\"Username\":\"$JELLYFIN_ADMIN_USER\",\"Pw\":\"$JELLYFIN_ADMIN_PASS\"}" \
                2>/dev/null | json_get AccessToken 2>/dev/null || true)
            [[ -n "$JELLY_TOKEN" ]] && break
            sleep 3; tries=$((tries + 1))
        done
        [[ -n "$JELLY_TOKEN" ]] || die "admin user created but cannot authenticate — aborting before sealing the wizard"

        jelly_req POST /Startup/RemoteAccess \
            '{"EnableRemoteAccess":true,"EnableAutomaticPortMapping":false}' >/dev/null 2>&1 || true
        jelly_req POST /Startup/Complete >/dev/null 2>&1 || true
        ok "startup wizard completed (admin: $JELLYFIN_ADMIN_USER)"
    else
        ok "startup wizard already completed"
    fi

    # admin token
    # Jellyfin 12 dropped X-Emby-Authorization; the standard header is required.
    JELLY_TOKEN=$(curl -fsS -X POST "$JELLY/Users/AuthenticateByName" \
        -H 'Authorization: MediaBrowser Client="OneClickMediaSuite", Device="installer", DeviceId="ocms-install", Version="1.0"' \
        -H 'Content-Type: application/json' \
        -d "{\"Username\":\"$JELLYFIN_ADMIN_USER\",\"Pw\":\"$JELLYFIN_ADMIN_PASS\"}" \
        | json_get AccessToken) || die "Jellyfin admin login failed"
    state_set JELLYFIN_TOKEN "$JELLY_TOKEN"

    # libraries — JF12 drops Paths from the create body, so the media path is
    # added through the dedicated Paths endpoint afterwards
    _jf_library() { # name collectionType path
        local name="$1" ctype="$2" lpath="$3" esc
        esc=$(urlencode "$name")
        jelly_req GET /Library/VirtualFolders \
            | python3 -c "import json,sys;print(any(l['Name']=='$name' for l in json.load(sys.stdin)))" \
            | grep -q True \
            || jelly_req POST "/Library/VirtualFolders?name=$esc&collectionType=$ctype&refreshLibrary=false" \
                '{}' >/dev/null \
            || { warn "could not create Jellyfin library $name"; return 0; }
        jelly_req GET /Library/VirtualFolders \
            | python3 -c "import json,sys;print(any('$lpath' in l.get('Locations',[]) for l in json.load(sys.stdin) if l['Name']=='$name'))" \
            | grep -q True \
            || jelly_req POST "/Library/VirtualFolders/Paths?name=$esc&refreshLibrary=true" \
                "{\"name\":\"$name\",\"path\":\"$lpath\"}" >/dev/null \
            || warn "could not add path $lpath to Jellyfin library $name"

        # realtime monitoring so imports appear without waiting for a rescan
        local libjson opts_body
        libjson=$(jelly_req GET /Library/VirtualFolders \
            | python3 -c "import json,sys;m=[l for l in json.load(sys.stdin) if l['Name']=='$name'];print(json.dumps(m[0] if m else {}))" 2>/dev/null || true)
        if [[ -n "$libjson" && "$libjson" != "{}" ]] \
            && ! printf '%s' "$libjson" | python3 -c "import json,sys;exit(0 if (json.load(sys.stdin).get('LibraryOptions') or {}).get('EnableRealtimeMonitor') else 1)"; then
            opts_body=$(printf '%s' "$libjson" | python3 -c "
import json,sys
l=json.load(sys.stdin)
o=l.get('LibraryOptions') or {}
o['EnableRealtimeMonitor']=True
print(json.dumps({'id':l['ItemId'],'libraryOptions':o}))" 2>/dev/null || true)
            [[ -n "$opts_body" ]] \
                && jelly_req POST /Library/VirtualFolders/LibraryOptions "$opts_body" >/dev/null \
                || warn "could not enable realtime monitoring on $name"
        fi
    }
    _jf_library Movies movies /data/media/movies
    _jf_library "TV Shows" tvshows /data/media/tv
    ok "libraries: Movies, TV Shows"

    # API key for the *arr -> Jellyfin notifications
    JELLYFIN_APIKEY=$(jelly_req POST "/Auth/Keys?app=OneClickMediaSuite" | json_get AccessToken 2>/dev/null || true)
    if [[ -z "$JELLYFIN_APIKEY" ]]; then
        JELLYFIN_APIKEY=$(jelly_req GET /Auth/Keys | python3 -c "
import json,sys
d=json.load(sys.stdin)
items=d.get('Items',d if isinstance(d,list) else [])
for k in items:
    if k.get('AppName')=='OneClickMediaSuite': print(k.get('AccessToken','')); break
" || true)
    fi
    state_set JELLYFIN_APIKEY "$JELLYFIN_APIKEY"
    [[ -n "$JELLYFIN_APIKEY" ]] && ok "Jellyfin API key created" || warn "could not create a Jellyfin API key"
}

# ---------- Seerr ----------
SEERR=http://localhost:5055
SEERR_COOKIE=/tmp/seerr.cookie

seerr_req() { # seerr_req METHOD PATH [DATA]
    local m="$1" p="$2" d="${3:-}"
    local args=(-fsS -X "$m" -b "$SEERR_COOKIE" -H "Content-Type: application/json")
    [[ -n "$d" ]] && args+=(-d "$d")
    curl "${args[@]}" "$SEERR$p"
}

wire_seerr() {
    step "Seerr"
    wait_http "Seerr" "$SEERR/api/v1/status" 300 || return 1

    # Sign in with the Jellyfin admin. On first run the Jellyfin server must be
    # configured too: Seerr wants hostname/port/useSsl/urlBase as separate
    # fields plus serverType=2 (MediaServerType.JELLYFIN). Once configured it
    # rejects a repeat setup call ("Jellyfin hostname already configured"), so
    # try the plain sign-in first — that's also the idempotent re-run path.
    curl -fsS -c "$SEERR_COOKIE" -X POST "$SEERR/api/v1/auth/jellyfin" \
        -H 'Content-Type: application/json' \
        -d "{\"username\":\"$JELLYFIN_ADMIN_USER\",\"password\":\"$JELLYFIN_ADMIN_PASS\"}" \
        >/dev/null 2>&1 \
    || curl -fsS -c "$SEERR_COOKIE" -X POST "$SEERR/api/v1/auth/jellyfin" \
        -H 'Content-Type: application/json' \
        -d "{\"username\":\"$JELLYFIN_ADMIN_USER\",\"password\":\"$JELLYFIN_ADMIN_PASS\",\"hostname\":\"jellyfin\",\"port\":8096,\"useSsl\":false,\"urlBase\":\"\",\"serverType\":2}" \
        >/dev/null || die "Seerr Jellyfin sign-in failed"
    ok "signed into Seerr with the Jellyfin admin account"

    _seerr_add_server() { # type(radarr|sonarr) hostname port apikey is4k
        local type="$1" hname="$2" port="$3" key="$4" is4k="${5:-false}"
        local existing
        existing=$(seerr_req GET "/api/v1/settings/$type" \
            | python3 -c "import json,sys;print(len(json.load(sys.stdin)))" 2>/dev/null || echo 0)
        [[ "$existing" != "0" ]] && { ok "Seerr $type server already configured"; return; }

        # test to learn profile + root folder ids
        local test_out
        # baseUrl must be empty, not "/": Seerr appends it verbatim before
        # /api/v3, and a leading "/" produces //api/v3, which hits the Servarr
        # SPA fallback (HTML) instead of the API.
        test_out=$(seerr_req POST "/api/v1/settings/$type/test" \
            "{\"hostname\":\"$hname\",\"port\":$port,\"apiKey\":\"$key\",\"useSsl\":false,\"baseUrl\":\"\"}") \
            || { warn "Seerr: $type test call failed"; return; }
        local prof_id prof_name root_path
        read prof_id prof_name root_path < <(python3 -c "
import json
d=json.loads('''$test_out''')
p=d['profiles'][0]
r=d['rootFolders'][0]
print(p['id'], json.dumps(p.get('name','')), json.dumps(r.get('path',r.get('freeSpace',''))))
" 2>/dev/null || echo "")
        [[ -n "$prof_id" ]] || { warn "Seerr: could not read $type profiles"; return; }
        root_path=$(python3 -c "import json;print(json.loads('''$test_out''')['rootFolders'][0]['path'])")

        local body
        if [[ "$type" == "radarr" ]]; then
            body=$(python3 -c "
import json
print(json.dumps({'name':'Radarr','hostname':'$hname','port':$port,'apiKey':'$key','useSsl':False,'activeProfileId':$prof_id,'activeProfileName':$prof_name,'activeDirectory':'$root_path','is4k':False,'minimumAvailability':'released','isDefault':True,'externalUrl':'','syncEnabled':True,'preventSearch':False,'tagRequests':False,'tags':[]}))")
        else
            body=$(python3 -c "
import json
print(json.dumps({'name':'Sonarr','hostname':'$hname','port':$port,'apiKey':'$key','useSsl':False,'activeProfileId':$prof_id,'activeProfileName':$prof_name,'activeDirectory':'$root_path','activeLanguageProfileId':1,'activeAnimeProfileId':$prof_id,'activeAnimeDirectory':'$root_path','activeAnimeLanguageProfileId':1,'is4k':False,'isDefault':True,'enableSeasonFolders':True,'externalUrl':'','syncEnabled':True,'preventSearch':False,'tagRequests':False,'tags':[]}))")
        fi
        seerr_req POST "/api/v1/settings/$type" "$body" >/dev/null \
            || warn "Seerr: failed to register $type server"
        ok "Seerr -> $type configured (profile $prof_name, root $root_path)"
    }

    _seerr_add_server radarr radarr 7878 "$RADARR_APIKEY"
    _seerr_add_server sonarr sonarr 8989 "$SONARR_APIKEY"

    # flip the first-run flag so the UI doesn't land on /setup
    seerr_req POST /api/v1/settings/initialize '{}' >/dev/null 2>&1 || true

    # Seerr's API key lives in its settings.json once initialized — needed for
    # the dashboard widget.
    SEERR_APIKEY=$(python3 -c "
import json
print(json.load(open('$CONFIG_DIR/seerr/settings.json'))['main']['apiKey'])" 2>/dev/null || true)
    state_set SEERR_APIKEY "$SEERR_APIKEY"
}

# ---------- dashboard (Homepage + nginx auth proxy) ----------
wire_dashboard() {
    step "Dashboard"
    local hp="$CONFIG_DIR/homepage" ip vpn_note=""
    ip=$(state_get HOST_IP localhost)
    [[ "${VPN_VENDOR:-airvpn}" != "none" ]] && vpn_note=" (through the VPN)"
    mkdir -p "$hp"

    cat > "$hp/settings.yaml" <<EOF
title: Media Suite
theme: dark
color: slate
headerStyle: boxed
EOF

    cat > "$hp/services.yaml" <<EOF
- Media:
    - Jellyfin:
        href: http://$ip:8096
        icon: jellyfin.png
        description: Media server
        widget:
          type: jellyfin
          url: http://jellyfin:8096
          key: $JELLYFIN_APIKEY
          enableBlocks: true
          enableNowPlaying: true
    - Seerr:
        href: http://$ip:5055
        icon: seerr.png
        description: Requests
        widget:
          type: seerr
          url: http://seerr:5055
          key: $SEERR_APIKEY
- Downloads:
    - qBittorrent:
        href: http://$ip:8080
        icon: qbittorrent.png
        description: Torrents${vpn_note}
        widget:
          type: qbittorrent
          url: http://gluetun:8080
          username: $QBIT_USER
          password: $QBIT_PASS
- Management:
    - Radarr:
        href: http://$ip:7878
        icon: radarr.png
        description: Movies
        widget:
          type: radarr
          url: http://radarr:7878
          key: $RADARR_APIKEY
          enableQueue: true
    - Sonarr:
        href: http://$ip:8989
        icon: sonarr.png
        description: TV
        widget:
          type: sonarr
          url: http://sonarr:8989
          key: $SONARR_APIKEY
          enableQueue: true
    - Prowlarr:
        href: http://$ip:9696
        icon: prowlarr.png
        description: Indexers${vpn_note}
        widget:
          type: prowlarr
          url: http://gluetun:9696
          key: $PROWLARR_APIKEY
    - Bazarr:
        href: http://$ip:6767
        icon: bazarr.png
        description: Subtitles
        widget:
          type: bazarr
          url: http://bazarr:6767
          key: $BAZARR_APIKEY
EOF
    chown -R 1000:1000 "$hp" 2>/dev/null || true

    (compose_cmd up -d homepage dashboard >/dev/null 2>&1 \
        && compose_cmd restart homepage >/dev/null 2>&1) \
        && ok "dashboard at http://$ip:3080 (login: $JELLYFIN_ADMIN_USER / your Jellyfin admin password)" \
        || warn "dashboard containers failed to start"
}

# ---------- summary ----------
write_credentials() {
    step "Writing credentials summary"
    local out="$INSTALL_DIR/credentials.txt" vpn_note=""
    local ip; ip=$(state_get HOST_IP localhost)
    [[ "${VPN_VENDOR:-airvpn}" != "none" ]] && vpn_note=" (VPN)"
    {
        echo "OneClick Media Suite — access details ($(date))"
        echo "=========================================================="
        echo
        echo "Web interfaces (LAN):"
        printf '  %-12s http://%s:%s   <- dashboard, login %s/<jellyfin admin password>\n' Dashboard "$ip" 3080 "$JELLYFIN_ADMIN_USER"
        printf '  %-12s http://%s:%s\n' Jellyfin "$ip" 8096
        printf '  %-12s http://%s:%s\n' Seerr "$ip" 5055
        printf '  %-12s http://%s:%s\n' Radarr "$ip" 7878
        printf '  %-12s http://%s:%s\n' Sonarr "$ip" 8989
        printf '  %-12s http://%s:%s\n' Prowlarr "$ip" 9696
        printf '  %-12s http://%s:%s\n' Bazarr "$ip" 6767
        printf '  %-12s http://%s:%s\n' qBittorrent "$ip" 8080
        echo
        echo "Accounts:"
        printf '  Jellyfin admin:   %s / %s\n' "$JELLYFIN_ADMIN_USER" "$JELLYFIN_ADMIN_PASS"
        printf '  qBittorrent:      %s / %s\n' "$QBIT_USER" "$QBIT_PASS"
        printf '  Radarr/Sonarr/Prowlarr/Bazarr: no login needed from LAN (API key auth)\n'
        echo
        echo "API keys:"
        for k in JELLYFIN_APIKEY RADARR_APIKEY SONARR_APIKEY PROWLARR_APIKEY BAZARR_APIKEY; do
            printf '  %-16s %s\n' "$k" "${!k}"
        done
        echo
        echo "Data root: $DATA_DIR   (media/{movies,tv}, torrents/{movies,tv})"
        echo "Configs:   $CONFIG_DIR"
        echo "Compose:   $INSTALL_DIR/docker-compose.yml"
        echo
        echo "Next steps:"
        echo "  1. Open Prowlarr and add your indexers — they sync to Radarr/Sonarr automatically."
        echo "  2. Open Bazarr and add subtitle providers (e.g. OpenSubtitles) under Settings -> Providers."
        echo "  3. Request something in Seerr and watch it flow: Seerr -> Radarr/Sonarr -> Prowlarr"
        echo "     indexers -> qBittorrent${vpn_note} -> import -> Jellyfin."
    } > "$out"
    chmod 600 "$out"
    ok "saved to $out"
    cat "$out"
}

wire_all() {
    wire_qbittorrent
    wire_jellyfin          # creates JELLYFIN_APIKEY used by arr notifications

    # Servarr apps write their own config.xml on first boot — harvest the real
    # keys now that everything is up, and cache them for later phases.
    RADARR_APIKEY=$(arr_wait_key radarr)   || die "Radarr never wrote an ApiKey"
    SONARR_APIKEY=$(arr_wait_key sonarr)   || die "Sonarr never wrote an ApiKey"
    PROWLARR_APIKEY=$(arr_wait_key prowlarr) || die "Prowlarr never wrote an ApiKey"
    for v in RADARR_APIKEY SONARR_APIKEY PROWLARR_APIKEY; do state_set "$v" "${!v}"; done

    wire_arr Radarr 7878 "$RADARR_APIKEY" radarr /data/media/movies
    wire_arr Sonarr 8989 "$SONARR_APIKEY" tv-sonarr /data/media/tv
    wire_prowlarr
    wire_bazarr
    wire_seerr
    wire_dashboard
    write_credentials
}
