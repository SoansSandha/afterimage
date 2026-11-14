# Testing

How Afterimage is checked: automatically on every change, and by hand on a phone. Results from a particular phone (logs, battery numbers, screenshots) stay outside the repo (`.claude/rules/git-and-privacy.md` §3).

**Screen control:** lock with `adb shell input keyevent KEYCODE_SLEEP`, wake with `KEYCODE_WAKEUP`. Never inject `KEYCODE_POWER`: repeated power presses can trigger other power-button features, including Emergency SOS.

## Automated

- `./gradlew check`: formatting, lint, unit tests and the coverage gate.
- CI (`.github/workflows/build.yml`) also builds the overlay and packages the Magisk module, which checks the overlay with `aapt2` and the zip's layout.

## The Magisk module

### Build

```bash
./gradlew :overlay:assembleDebug
bash scripts/package-module.sh --overlay overlay/build/outputs/apk/debug/overlay-debug.apk
# → build/outputs/magisk/afterimage-v<version>.zip
```

### Install, remove, recover

Each of these changes the phone, so the owner does them or OKs them first.

| Step | How |
|---|---|
| Copy to the phone | `adb push build/outputs/magisk/afterimage-v<version>.zip /sdcard/Download/` |
| Install | Magisk › Modules › Install from storage › pick the zip › Reboot |
| Turn the wallpaper on AOD off or on | Magisk › Modules › Afterimage › the action (▶) button. No reboot |
| Disable for a while | Magisk › Modules › switch Afterimage off › Reboot. The overlay goes; the Secure setting stays but has no effect without it |
| Remove | Magisk › Modules › Afterimage › Remove › Reboot. `uninstall.sh` restores the Secure setting's old value shortly after boot |
| Phone won't boot | Boot to Android safe mode (hold volume-down while the boot animation plays): Magisk disables every module. Remove Afterimage in the Magisk app, reboot |

What the module changes, and where it keeps state:

| Change | Undone by |
|---|---|
| Static overlay `io.github.soanssandha.afterimage.overlay.aodgate` on `/product/overlay`: `config_dozeSupportsAodWallpaper=true` | Disabling or removing the module, then rebooting |
| Secure `doze_always_on_wallpaper_enabled=1`, set once on the first boot | The action button (off), or removing the module (restored) |
| `/data/adb/afterimage/`: the setting's previous value and a marker that the gate was opened | Removing the module |

`doze_always_on` (the user's own AOD switch) is never touched.

## Phase 0b: the stock AOD-wallpaper path

Goal: see how Google's own wallpaper-on-AOD looks and costs on this phone before writing app code (`plan.md` §7, Phase 0b). Keep the phone's current static wallpaper for the first checks.

| # | Check | How | What to record |
|---|---|---|---|
| 1 | Gate open | `bash scripts/probe-device.sh` | Framework bool `true`, the overlay listed as enabled, Secure `doze_always_on_wallpaper_enabled` `1`, and who supplies the bool in the verbose lookup |
| 2 | Lock from the lock screen | Wake to the lock screen, then `KEYCODE_SLEEP`. Watch the transition and the settled AOD | Is the wallpaper visible? How blurred and how dim? Did it need a SystemUI restart, or did it work right after boot? |
| 3 | Blur source (open question 1) | While on AOD: `adb shell "dumpsys activity service com.android.systemui/.SystemUIService NotificationShadeDepthController"` | `wakeAndUnlockBlurRadius` above 0 means `NotificationShadeDepthController` still drives the AOD blur in scene-container mode |
| 4 | Lock from inside an app (open question 2) | Open any app, then `KEYCODE_SLEEP` | Full black? For the transition only, or the whole AOD session? Does a notification pulse change it? |
| 5 | Wake | `KEYCODE_WAKEUP`; also fingerprint from AOD | How the wallpaper comes back |
| 6 | Settings toggle (open question 4) | Settings › Display › Lock screen (and the AOD page) | Does Google's "show lock screen wallpaper" switch appear? |
| 7 | Google's ambient wallpapers | Wallpaper & style: are Magic Portrait or Photo Shuffle offered? Only try one if the owner wants to change wallpaper | How they draw on AOD |
| 8 | Without blur | Developer options › turn off "Allow window-level blurs", lock, look; turn it back on | Are thin details sharp? (A no-hook way to preview Phase 1 lines) |
| 9 | Light-sensor dimming (open question 3) | `adb shell settings put global always_on_display_constants wallpaper_dimming_scrim_array=-1:0:0:0:0`, lock in a dim room, compare; undo with `adb shell settings delete global always_on_display_constants` | Does the wallpaper stay brighter in the dark? |
| 10 | Action button | Turn it off in Magisk, lock; turn it on, lock | The wallpaper leaves and returns without a reboot? |
| 11 | Removal (last) | Remove the module, reboot, then `adb shell settings get secure doze_always_on_wallpaper_enabled` | Back to its old value (`null` if it was unset)? Only after the battery run |

Then the battery run below, with the gate open and the usual wallpaper.

Exit: the owner decides whether a lit AOD wallpaper is worth it, for looks and battery. Record the decision in ADR 0002 (Accepted, or switch to route 2).

## Battery on AOD

Use the same method as the baseline, so the numbers compare:

1. Charge to full, unplug, and use the phone as usual for an evening and night, locked on AOD most of the time. No USB and no wireless debugging during the run: both keep the phone busier.
2. Before plugging back in, connect once and read the screen-doze figures since the last full charge:
   ```bash
   adb shell dumpsys batterystats --charged | grep -iE 'screen doze'
   ```
3. Screen-doze discharge (mAh) ÷ screen-doze time (h) = mAh/h on AOD. Compare it with the baseline in `.claude/private/environment.md` and record the result there, not in the repo.
