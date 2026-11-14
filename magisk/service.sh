#!/system/bin/sh
# Afterimage: run by Magisk as root in the late_start service stage, in the background.
#
# Opens the Secure half of SystemUI's wallpaper-on-AOD gate once: records the previous value of
# doze_always_on_wallpaper_enabled, then sets it to 1. After that the setting is left alone, so
# turning it off with the action button sticks across reboots. The framework half of the gate is the
# static overlay this module mounts. doze_always_on (the user's AOD switch) is never touched.

STATE=/data/adb/afterimage
KEY=doze_always_on_wallpaper_enabled

until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done

[ -f "$STATE/initialised" ] && exit 0
mkdir -p "$STATE" || exit 1

# The settings service can lag behind boot_completed by a moment.
tries=0
until previous="$(settings get secure "$KEY" 2>/dev/null)" && [ -n "$previous" ]; do
  tries=$((tries + 1))
  [ "$tries" -ge 15 ] && { log -t AfterimageModule "settings unavailable; gate not opened"; exit 1; }
  sleep 2
done

# Keep the first recorded value: after a reinstall it's still the one from before Afterimage.
[ -f "$STATE/previous_$KEY" ] || echo "$previous" > "$STATE/previous_$KEY"

if settings put secure "$KEY" 1; then
  touch "$STATE/initialised"
  log -t AfterimageModule "$KEY set to 1 (was $previous)"
fi
