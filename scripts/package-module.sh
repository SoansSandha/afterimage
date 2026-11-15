#!/usr/bin/env bash
# Builds the Afterimage Magisk module zip from already-built APKs (docs/plan.md §5.5). It runs no Gradle, so
# the release job can package APKs that were signed after the build (Sidearm ADR 0009, ported in Phase 5).
#
# Usage: scripts/package-module.sh --overlay <overlay.apk> [--app <app.apk>] [--version <x.y.z>] [--out <zip>]
#   --overlay  the :overlay APK, e.g. overlay/build/outputs/apk/debug/overlay-debug.apk
#   --app      the :app APK; mounted as a system app (/system/app/Afterimage), which gets AMBIENT_WALLPAPER
#   --version  defaults to `afterimage` in gradle/libs.versions.toml
#   --out      defaults to build/outputs/magisk/afterimage-v<version>.zip
#
# Dev loop:
#   ./gradlew :overlay:assembleDebug :app:assembleDebug
#   bash scripts/package-module.sh --overlay overlay/build/outputs/apk/debug/overlay-debug.apk \
#     --app app/build/outputs/apk/debug/app-debug.apk
# After the first install, update just the app with scripts/dev-install.sh (no reboot).
#
# The scripts are copied with Windows line endings stripped, module.prop gets the version, and both APKs
# are checked with aapt2 (build-tools 36.0.0) when it's available. The zip needs no META-INF installer:
# the Magisk app installs modules itself.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

overlay="" app="" version="" out=""
while [ $# -gt 0 ]; do
  case "$1" in
    --overlay) overlay="${2:?--overlay needs a path}"; shift 2 ;;
    --app) app="${2:?--app needs a path}"; shift 2 ;;
    --version) version="${2:?--version needs a value}"; shift 2 ;;
    --out) out="${2:?--out needs a path}"; shift 2 ;;
    -h | --help) sed -n '2,19p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1 (see --help)" >&2; exit 2 ;;
  esac
done

fail() { echo "package-module: $1" >&2; exit 1; }

[ -n "$overlay" ] || fail "--overlay is required (see --help)"
[ -f "$overlay" ] || fail "overlay APK not found: $overlay"
[ -z "$app" ] || [ -f "$app" ] || fail "app APK not found: $app"

[ -n "$version" ] || version="$(sed -n 's/^afterimage = "\(.*\)"$/\1/p' gradle/libs.versions.toml)"
# The same formula as build-logic's releaseVersionCode: MAJOR * 10000 + MINOR * 100 + PATCH.
[[ "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)$ ]] \
  || fail "version '$version' must be MAJOR.MINOR.PATCH with minor and patch below 100"
version_code=$(( BASH_REMATCH[1] * 10000 + BASH_REMATCH[2] * 100 + BASH_REMATCH[3] ))
[ -n "$out" ] || out="build/outputs/magisk/afterimage-v$version.zip"

# --- Check the overlay: a static overlay on android that sets the AOD-wallpaper bool to true ---
sdk="${ANDROID_HOME:-${LOCALAPPDATA:-}/Android/Sdk}"
aapt2="$sdk/build-tools/36.0.0/aapt2"
[ -x "$aapt2" ] || aapt2="$aapt2.exe"
if [ -x "$aapt2" ]; then
  manifest="$("$aapt2" dump xmltree --file AndroidManifest.xml "$overlay" 2>&1)" \
    || fail "$overlay isn't a readable APK: $manifest"
  grep -q 'E: overlay' <<<"$manifest" || fail "$overlay has no <overlay> element"
  grep -qE 'targetPackage\([^)]*\)="android"' <<<"$manifest" || fail "$overlay doesn't target android"
  grep -qE 'isStatic\([^)]*\)=true' <<<"$manifest" || fail "$overlay isn't a static overlay"
  "$aapt2" dump resources "$overlay" | grep -A1 'bool/config_dozeSupportsAodWallpaper' | grep -q 'true' \
    || fail "$overlay doesn't set bool/config_dozeSupportsAodWallpaper to true"
  if [ -n "$app" ]; then
    package="$("$aapt2" dump packagename "$app" 2>&1 | tr -d '\r')" || fail "$app isn't a readable APK: $package"
    [ "$package" = io.github.soanssandha.afterimage ] || fail "$app is $package, not Afterimage"
    "$aapt2" dump permissions "$app" | grep -q "uses-permission: name='android.permission.AMBIENT_WALLPAPER'" \
      || fail "$app doesn't request AMBIENT_WALLPAPER"
  fi
else
  echo "package-module: aapt2 not found under $sdk/build-tools/36.0.0; overlay not checked" >&2
fi

# --- Stage the module ---
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT

for f in customize.sh service.sh action.sh uninstall.sh; do
  tr -d '\r' <"magisk/$f" >"$stage/$f"
done
tr -d '\r' <magisk/module.prop | sed -e "s/@VERSION@/$version/" -e "s/@VERSION_CODE@/$version_code/" >"$stage/module.prop"
! grep -q '@[A-Z_]*@' "$stage/module.prop" || fail "module.prop still has a placeholder"

mkdir -p "$stage/system/product/overlay"
cp "$overlay" "$stage/system/product/overlay/AfterimageAodGate.apk"
if [ -n "$app" ]; then
  mkdir -p "$stage/system/app/Afterimage"
  cp "$app" "$stage/system/app/Afterimage/Afterimage.apk"
fi

# --- Zip it with the JDK's jar (Git Bash has no zip); entries get forward slashes either way ---
jar="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
command -v "$jar" >/dev/null 2>&1 || jar=jar
command -v "$jar" >/dev/null 2>&1 || fail "no jar tool found; set JAVA_HOME"
mkdir -p "$(dirname "$out")"
rm -f "$out"
native() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }
out_abs="$(cd "$(dirname "$out")" && pwd)/$(basename "$out")"
"$jar" --create --no-manifest --file "$(native "$out_abs")" -C "$(native "$stage")" .

# --- Check the result ---
entries="$("$jar" --list --file "$(native "$out_abs")" | tr -d '\r')"
for required in module.prop customize.sh service.sh action.sh uninstall.sh system/product/overlay/AfterimageAodGate.apk; do
  grep -qx "$required" <<<"$entries" || fail "the zip is missing $required"
done
[ -z "$app" ] || grep -qx 'system/app/Afterimage/Afterimage.apk' <<<"$entries" || fail "the zip is missing the app"

echo "Built $out (v$version, versionCode $version_code)"
echo "SHA-256 $(sha256sum "$out_abs" | cut -d' ' -f1)"
