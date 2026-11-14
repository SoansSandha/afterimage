#!/usr/bin/env bash
# probe-device.sh: read-only checks of the AOD-wallpaper path on a connected Pixel.
#
# Usage: bash scripts/probe-device.sh [--wallpapers]
#   --wallpapers  also pull each installed live-wallpaper APK into a temp folder and
#                 report whether it declares supportsAmbientMode (needs aapt2).
#
# Changes nothing on the phone. Prints the build, lock-screen mode, the AOD-wallpaper
# gates, permission levels, doze display mode and the current wallpaper component.
# The output contains device-specific values: never paste it raw into the repo,
# issues or PRs (.claude/rules/git-and-privacy.md section 3).
set -euo pipefail
export MSYS_NO_PATHCONV=1

scan_wallpapers=false
for arg in "$@"; do
  case "$arg" in
    --wallpapers) scan_wallpapers=true ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

if [ "$(adb get-state 2>/dev/null || true)" != "device" ]; then
  echo "No device connected (adb get-state didn't return 'device')." >&2
  exit 1
fi

SUI='com.android.systemui/.SystemUIService'
section() { printf '\n== %s ==\n' "$1"; }
value() { printf '%-52s %s\n' "$1" "$(adb shell "$2" 2>&1 | tr -d '\r' | head -1)"; }

section "Build"
value "ro.build.fingerprint" "getprop ro.build.fingerprint"
value "ro.build.version.security_patch" "getprop ro.build.version.security_patch"

section "Lock screen"
adb shell "dumpsys activity service $SUI SceneContainerStartable" | tr -d '\r' \
  | grep -E 'isEnabled=|isAodAvailable=' | sed 's/^ */  /' || echo "  (SceneContainerStartable dumpable not found)"

section "AOD wallpaper gates"
value "framework bool config_dozeSupportsAodWallpaper" \
  "cmd overlay lookup android android:bool/config_dozeSupportsAodWallpaper"
value "SystemUI int config_dozeSupportsAodWallpaperOverride" \
  "cmd overlay lookup com.android.systemui com.android.systemui:integer/config_dozeSupportsAodWallpaperOverride"
value "Secure doze_always_on" "settings get secure doze_always_on"
value "Secure doze_always_on_wallpaper_enabled" "settings get secure doze_always_on_wallpaper_enabled"

section "Doze display mode"
value "SystemUI bool doze_suspend_display_state_supported" \
  "cmd overlay lookup com.android.systemui com.android.systemui:bool/doze_suspend_display_state_supported"

section "Permissions"
for p in AMBIENT_WALLPAPER SET_WALLPAPER_COMPONENT; do
  value "android.permission.$p" \
    "dumpsys package permission android.permission.$p | grep -m1 -o 'prot=[a-z|]*'"
done

section "Current wallpaper"
adb shell dumpsys wallpaper | tr -d '\r' \
  | grep -E '^(System|Lock) wallpaper state|mWhich=|mWallpaperComponent=|\(null entry\)' \
  | sed 's/^ */  /' | head -8

if [ "$scan_wallpapers" = true ]; then
  section "Live wallpapers declaring supportsAmbientMode"
  sdk="${ANDROID_HOME:-${LOCALAPPDATA:-}/Android/Sdk}"
  aapt2="$sdk/build-tools/36.0.0/aapt2"
  [ -x "$aapt2" ] || aapt2="$aapt2.exe"
  if [ ! -x "$aapt2" ]; then
    echo "  aapt2 not found under $sdk/build-tools/36.0.0" >&2
    exit 1
  fi
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  pkgs="$(adb shell cmd package query-services --brief -a android.service.wallpaper.WallpaperService \
    | tr -d '\r' | grep / | sed 's/^ *//; s#/.*##' | sort -u)"
  for pkg in $pkgs; do
    apk="$(adb shell pm path "$pkg" | tr -d '\r' | head -1 | sed 's/^package://')"
    adb pull "$apk" "$tmp/$pkg.apk" >/dev/null 2>&1 || { echo "  $pkg: pull failed"; continue; }
    found=""
    for xml in $(unzip -Z1 "$tmp/$pkg.apk" | grep -E '^res/.*\.xml$'); do
      tree="$("$aapt2" dump xmltree --file "$xml" "$tmp/$pkg.apk" 2>/dev/null || true)"
      if printf '%s\n' "$tree" | grep -q 'E: wallpaper'; then
        amb="$(printf '%s\n' "$tree" | grep -o 'supportsAmbientMode([^)]*)=[a-z]*' | head -1)"
        found="$found ${amb:-supportsAmbientMode=unset}"
      fi
    done
    echo "  $pkg:${found:- (no wallpaper xml found)}"
    rm -f "$tmp/$pkg.apk"
  done
fi
