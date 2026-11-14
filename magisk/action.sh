#!/system/bin/sh
# Afterimage: Magisk's action button (Magisk 28 or newer), run as root while Android is up.
# Turns the wallpaper on AOD off or back on through the Secure half of SystemUI's gate. The overlay
# stays; with the setting at 0, SystemUI hides every wallpaper on AOD again, as on a stock phone.

KEY=doze_always_on_wallpaper_enabled

if [ "$(settings get secure "$KEY")" = 1 ]; then
  settings put secure "$KEY" 0 && echo "Wallpaper on the always-on display: OFF"
else
  settings put secure "$KEY" 1 && echo "Wallpaper on the always-on display: ON"
fi
echo "Lock the phone to see the change."
