#!/system/bin/sh
# Afterimage: run by Magisk when the module is removed, early in boot (post-fs-data), before
# Android's settings service exists. Once the overlay is gone the gate is closed whatever the Secure
# setting says; this also puts the setting back to its value from before Afterimage, as soon as boot
# completes, then deletes the module's state. Best effort: if it doesn't run, nothing visible changes.

STATE=/data/adb/afterimage
KEY=doze_always_on_wallpaper_enabled

[ -d "$STATE" ] || exit 0

(
  until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done
  sleep 5
  previous="$(cat "$STATE/previous_$KEY" 2>/dev/null)"
  case "$previous" in
    "" | null) settings delete secure "$KEY" ;;
    *) settings put secure "$KEY" "$previous" ;;
  esac
  log -t AfterimageModule "removed; $KEY restored to ${previous:-unset}"
  rm -rf "$STATE"
) </dev/null >/dev/null 2>&1 &
