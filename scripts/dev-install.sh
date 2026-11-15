#!/usr/bin/env bash
# Builds the debug app with a fresh versionCode and installs it on the connected phone, over the copy the Magisk
# module put on the system image. Android treats it as an update of that system app, so it keeps AMBIENT_WALLPAPER,
# and no reboot is needed (docs/testing.md, The app on the phone).
#
# Usage: scripts/dev-install.sh
#
# The module must have been installed from a debug build made on this PC: an update signed with another key is
# refused. To go back to the module's own copy: adb uninstall io.github.soanssandha.afterimage (removes the update).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

./gradlew :app:assembleDebug -Pafterimage.versionCode="$(date +%s)" --console=plain -q
# -d allows going back to a lower versionCode (debug builds only), e.g. after a plain assembleDebug.
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
