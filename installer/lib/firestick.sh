#!/usr/bin/env bash
# Phase 5 (optional): install the Cobalt Wolf release APK on a Fire Stick
# over network adb or any Android device over USB adb. APK source:
#   1. bundle/cobalt-wolf.apk — bundled by ./bundle.sh (works offline)
#   2. latest release APK from GitHub (needs internet)

REPO_DIR="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
BUNDLE_APK="$REPO_DIR/bundle/cobalt-wolf.apk"

# package id of an already-built APK — aapt/apkanalyzer when present,
# else the published id (it never changes between versions)
apk_pkg() {
    local apk="$1" id=""
    if command -v aapt2 >/dev/null; then
        id=$(aapt2 dump badging "$apk" 2>/dev/null | grep -oP "package: name='\K[^']+" | head -1)
    elif command -v aapt >/dev/null; then
        id=$(aapt dump badging "$apk" 2>/dev/null | grep -oP "package: name='\K[^']+" | head -1)
    elif command -v apkanalyzer >/dev/null; then
        id=$(apkanalyzer manifest application-id "$apk" 2>/dev/null | head -1)
    fi
    echo "${id:-com.pyoungws.cobaltwolf}"
}

# download URL of the newest release APK asset (releases are newest-first,
# prereleases included) — empty when GitHub is unreachable or none exist
latest_release_apk_url() {
    curl -fsSL "https://api.github.com/repos/P-YoungWebServices/cobalt-wolf/releases" \
        | grep -oP '"browser_download_url":\s*"\K[^"]+\.apk' | head -1
}

find_adb() {
    if command -v adb >/dev/null 2>&1; then ADB=adb
    elif [[ -x "$HOME/Android/Sdk/platform-tools/adb" ]]; then ADB="$HOME/Android/Sdk/platform-tools/adb"
    else die "adb not found (install platform-tools or set PATH)"
    fi
    export ADB
}

firestick_instructions() {
    cat <<'EOF'

+-----------------------------------------------------------------------+
|                    FIRE STICK ADB SETUP                                |
+-----------------------------------------------------------------------+
| COMPATIBILITY: this only works on Fire TV devices running classic     |
| Fire OS (Fire OS 6/7 — Android-based, e.g. Fire TV Stick 4K / 4K Max  |
| 1st gen, Fire TV Cube). The NEW 2024+ models (e.g. Fire TV Stick      |
| 4K Select / anything on Vega OS) do NOT support ADB sideloading of    |
| Android APKs — check Settings -> My Fire TV -> About for "Fire OS".   |
+-----------------------------------------------------------------------+
| On the Fire Stick (classic Fire OS):                                   |
|                                                                        |
| 1. Go to  Settings -> My Fire TV -> Developer Options.                 |
|    * If Developer Options is hidden:                                   |
|      Settings -> My Fire TV -> About, highlight the device name,       |
|      press SELECT on the remote ~7 times until it says                 |
|      "You are now a developer", then go back.                          |
| 2. Turn ON "ADB debugging" (or "Android debugging").                   |
| 3. Turn ON "Install unknown apps" / "Apps from Unknown Sources"        |
|    for the Downloader/adb source if asked.                             |
| 4. Find the IP:  Settings -> My Fire TV -> About -> Network            |
|    and write down the IP address shown on the right.                   |
| 5. The Fire Stick and this machine must be on the same network.        |
|                                                                        |
| The first `adb connect` may pop an "Allow USB debugging" prompt on     |
| the TV — tick "always allow" and confirm with the remote.              |
+------------------------------------------------------------------------+
EOF
}

phone_instructions() {
    cat <<'EOF'

+-----------------------------------------------------------------------+
|                  ANDROID PHONE / TABLET USB SETUP                      |
+-----------------------------------------------------------------------+
| On the phone or tablet:                                                |
|                                                                        |
| 1. Enable Developer Options:                                           |
|    Settings -> About phone -> tap "Build number" 7 times               |
|    (Samsung: About phone -> Software information -> Build number).     |
|    A toast says "You are now a developer".                             |
|                                                                        |
| 2. Turn on USB debugging:                                              |
|    Settings -> Developer options -> "USB debugging" -> ON.             |
|    (Samsung/Pixel: Settings -> System -> Developer options.)           |
|                                                                        |
| 3. Plug the phone into THIS machine with a USB cable                   |
|    (use a data-capable cable — charge-only cables won't work).         |
|                                                                        |
| 4. Unlock the phone. An "Allow USB debugging?" dialog appears          |
|    showing this computer's RSA fingerprint — tick                      |
|    "Always allow from this computer" and tap Allow.                    |
|                                                                        |
| 5. If the device shows as "unauthorized" below, unplug/replug          |
|    and watch for the prompt again. If nothing appears, run             |
|    `adb kill-server && adb devices` yourself and retry.                |
|                                                                        |
| Alternative (no cable): Android 11+ "Wireless debugging" pairs         |
| with `adb pair <ip>:<port>` — enable it in Developer options,          |
| pair once, then this installer works over the network like the         |
| Fire Stick path.                                                       |
+------------------------------------------------------------------------+
EOF
}

setup_firestick() {
    step "Cobalt Wolf on Fire Stick (optional)"

    if ! ask_yn "Install Cobalt Wolf on an Android device now?" N; then
        info "Skipping — re-run ./install.sh and choose the adb/Firestick step later,"
        info "or see README for the manual commands."
        return 0
    fi

# pick a target + connect; sets fs_addr. returns 1 to abort the phase.
_fs_pick_device() {
    ask_choice ADB_TARGET "Install target:" \
        "Fire Stick / Fire TV over the network (classic Fire OS only)" \
        "Phone or tablet plugged in over USB"

    if [[ "$ADB_TARGET" == *USB* ]]; then
        phone_instructions
        ask_yn "USB debugging enabled and the phone plugged in?" Y || return 1
        "$ADB" devices -l >&2
        local serials; mapfile -t serials < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
        case "${#serials[@]}" in
            0) die "no USB device in 'device' state — check the cable and the on-screen debug prompt" ;;
            1) fs_addr="${serials[0]}" ;;
            *) echo "Multiple devices:" >&2
               local i=1
               for s in "${serials[@]}"; do echo "  $i) $s" >&2; i=$((i+1)); done
               local pick; read -r -p "Choose [1]: " pick; pick="${pick:-1}"
               fs_addr="${serials[$((pick-1))]}" ;;
        esac
        "$ADB" -s "$fs_addr" get-state >/dev/null 2>&1 || die "device not reachable: $fs_addr"
        ok "connected to $fs_addr (USB)"
    else
        firestick_instructions
        ask_yn "ADB debugging enabled and the IP noted?" Y || return 1
        ask FIRESTICK_IP "Fire Stick IP address" ""
        fs_addr="${FIRESTICK_IP}:5555"
        "$ADB" connect "$fs_addr" || die "adb connect $fs_addr failed"
        sleep 1
        "$ADB" -s "$fs_addr" get-state >/dev/null 2>&1 || die "Fire Stick not reachable at $fs_addr"
        ok "connected to $fs_addr"
    fi
    state_set FIRESTICK_ADDR "$fs_addr"
}

# install apk on fs_addr, handling foreign signatures; returns 1 to abort
_fs_install_apk() {
    local pkg="$1" apk="$2" inst_out
    step "Installing on $fs_addr"
    inst_out=$("$ADB" -s "$fs_addr" install -r "$apk" 2>&1) || {
        if grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE\|signatures do not match" <<<"$inst_out"; then
            warn "A differently-signed copy of $pkg is already on the device."
            warn "Android won't update across signatures — it must be uninstalled first"
            warn "(this erases that app's data on the device)."
            if ask_yn "Uninstall the existing copy and install this one?" N; then
                "$ADB" -s "$fs_addr" uninstall "$pkg" || die "uninstall failed"
                "$ADB" -s "$fs_addr" install -r "$apk" || die "adb install failed"
            else
                return 1
            fi
        else
            echo "$inst_out" >&2; die "adb install failed"
        fi
    }
    "$ADB" -s "$fs_addr" shell am start -n "$pkg/.MainActivity" >/dev/null 2>&1 || true
    ok "installed and launched on $fs_addr"
}

    find_adb
    _fs_pick_device || return 0

    # --- resolve the APK once; it is reused across devices ---
    local apk pkg
    if [[ -f "$BUNDLE_APK" ]]; then
        apk="$BUNDLE_APK"
        info "using bundled release APK: $apk"
    else
        step "Downloading the latest Cobalt Wolf release"
        local url; url=$(latest_release_apk_url || true)
        if [[ -z "$url" ]]; then
            warn "no bundled APK and GitHub unreachable —"
            warn "skipping sideload; re-run ./install.sh firestick later."
            return 0
        fi
        apk="$INSTALL_DIR/build/$(basename "$url")"
        mkdir -p "$(dirname "$apk")"
        curl -fL --progress-bar -o "$apk" "$url" || die "APK download failed"
    fi
    pkg=$(apk_pkg "$apk")

    # install + offer more devices — APK is resolved once and reused
    local devices=0
    while :; do
        _fs_install_apk "$pkg" "$apk" || break
        devices=$((devices+1))
        ask_yn "Install on another device?" N || break
        _fs_pick_device || break
    done

    if (( devices > 0 )); then
        ok "Cobalt Wolf installed on $devices device(s)."
        info "On the device: connect to http://$(state_get HOST_IP localhost):8096 and sign in"
        info "with the Jellyfin admin account you created during this install — then set"
        info "Seerr + arr details in the app's Services screen if you want them."
    fi
}
