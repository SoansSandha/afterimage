# Afterimage: sourced by Magisk's module installer (busybox ash), which provides $API, $MODPATH,
# ui_print, abort and set_perm_recursive. What the module does and why: docs/plan.md §5.5.

[ "$API" -ge 37 ] || abort "! Afterimage needs Android 17 (API 37). This phone has API $API."

device="$(getprop ro.product.device)"
if [ "$device" != komodo ]; then
  ui_print "! Only tested on the Pixel 9 Pro XL (komodo); this is $device. Installing anyway."
fi

if [ -f "$MODPATH/system/app/Afterimage/Afterimage.apk" ]; then
  ui_print "- System app: /system/app/Afterimage (allowed to draw on the always-on display)"
  # A copy installed outside the module with another signing key can't update the system copy: Android
  # keeps the system copy and drops the other, with its photo and settings.
  if pm path io.github.soanssandha.afterimage >/dev/null 2>&1; then
    ui_print "  Afterimage is already installed; it must be signed with the same key as this build."
  fi
fi
ui_print "- Framework overlay: config_dozeSupportsAodWallpaper=true (/product/overlay)"
ui_print "- First boot: Secure doze_always_on_wallpaper_enabled=1 (the old value is kept for uninstall)"
if [ "${MAGISK_VER_CODE:-0}" -ge 28000 ]; then
  ui_print "- The action button in Magisk's module list turns the wallpaper on AOD off and on"
fi

# Settings state lives outside the module folder, which an update replaces. On a fresh install (not
# an update), forget that the gate was opened before, so service.sh opens it again; keep the recorded
# original value, which is still the one to restore.
STATE=/data/adb/afterimage
if [ ! -d /data/adb/modules/afterimage ]; then
  rm -f "$STATE/initialised"
fi

set_perm_recursive "$MODPATH" 0 0 0755 0644
ui_print "- Reboot to apply, then open Afterimage. Remove the module and reboot to undo."
