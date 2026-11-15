# Testing

How Afterimage is checked: automatically on every change, and by hand on a phone. Results from a particular phone (logs, battery numbers, screenshots) stay outside the repo (`.claude/rules/git-and-privacy.md` §3).

**Screen control:** lock with `adb shell input keyevent KEYCODE_SLEEP`, wake with `KEYCODE_WAKEUP`. Never inject `KEYCODE_POWER`: repeated power presses can trigger other power-button features, including Emergency SOS.

## Automated

- `./gradlew check`: formatting, lint, unit tests and the coverage gate.
- CI (`.github/workflows/build.yml`) also builds the app (R8-shrunk) and the overlay and packages the Magisk module, which checks both APKs with `aapt2` and the zip's layout.

## The Magisk module

### Build

```bash
./gradlew :overlay:assembleDebug :app:assembleDebug
bash scripts/package-module.sh --overlay overlay/build/outputs/apk/debug/overlay-debug.apk \
  --app app/build/outputs/apk/debug/app-debug.apk
# → build/outputs/magisk/afterimage-v<version>.zip
```

Build the module from a debug app on the PC that will run `scripts/dev-install.sh`: later updates must be signed with the same key.

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
| The Afterimage app at `/system/app/Afterimage`, which makes it a preinstalled app that gets `AMBIENT_WALLPAPER` | Disabling or removing the module, then rebooting. An update installed over it with `dev-install.sh` then becomes a normal app, its wallpaper service switches itself off, and Android falls back to the default wallpaper |
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

## Phase 1: the outline wallpaper on the phone

Install the module built with the app (above), replacing the Phase 0b one, and reboot. Then:

| # | Check | How | What to record |
|---|---|---|---|
| 1 | System app | `adb shell dumpsys package io.github.soanssandha.afterimage \| grep -E 'codePath\|AMBIENT_WALLPAPER'` | `codePath=/system/app/Afterimage` and `AMBIENT_WALLPAPER: granted=true`. The app's status card says "Installed as a system app" |
| 2 | Make an outline | Open Afterimage › Choose photo › pick one. Watch `adb logcat -s Afterimage` | "Outline made: …" with the lit share and times (budget: lit ≤ 15 %, ≤ 1.5 s) |
| 3 | Preview | Drag the slider in the app | Photo → outline looks right; lines follow the photo's colours |
| 4 | Set it (open question 6) | Set as wallpaper › Pixel's preview › Set wallpaper | Which destinations it offers: is "Lock screen" alone one of them? |
| 5 | Lock from the lock screen | `KEYCODE_SLEEP` from the lock screen, watching the phone and `adb logcat -s Afterimage` | The photo fades to the outline. Log: `onAmbientModeChanged` time and duration, display state changes, "Settled … frames in … ms" (open question 5: does the transition finish before the display state turns `DOZE`?) |
| 6 | AOD frame | `adb exec-out screencap -p` while on AOD (see below) | Outline visible, dimmed by the 0.4 scrim and blurred by SystemUI (Phase 2 removes both) |
| 7 | Lock from an app, wake, fingerprint | As in Phase 0b | Black for ~1 s from an app, then the outline; on wake the outline fades back to the photo |
| 8 | Burn-in shift | Lock several times; compare AOD frames | The outline moves a few pixels each time |
| 9 | Battery | The battery run below, with Afterimage as the lock wallpaper | mAh/h on AOD against the baseline |

Iterate on app code with `bash scripts/dev-install.sh`: it installs over the system copy, keeping the permission, with no reboot. Reopen the app afterwards so it re-enables the wallpaper service if needed.

## Measuring an AOD frame

`adb exec-out screencap -p > frame.png` works while the phone is dozing and returns the frame SystemUI composites, which is what the panel shows, though not how bright the panel is. Compare frames numerically rather than by eye: mean luma over the wallpaper area (how dim) and mean absolute difference between neighbouring pixels, "edge energy" (how sharp). In Phase 0b these showed the 0.4 scrim (60 % of the lock screen's luma) and the blur (8 % of its edge energy). For transitions, capture back to back after `KEYCODE_SLEEP` and note the time of each capture; one capture takes roughly 0.5–1.5 s.

Captures show the owner's wallpaper, status bar and notifications: keep them in the session scratchpad and never commit or attach them (rules §3).

## Battery on AOD

Use the same method as the baseline, so the numbers compare:

1. Charge to full, unplug, and use the phone as usual for an evening and night, locked on AOD most of the time. No USB and no wireless debugging during the run: both keep the phone busier.
2. Before plugging back in, connect once and read the screen-doze figures since the last full charge:
   ```bash
   adb shell dumpsys batterystats --charged | grep -iE 'screen doze'
   ```
3. Screen-doze discharge (mAh) ÷ screen-doze time (h) = mAh/h on AOD. Compare it with the baseline in `.claude/private/environment.md` and record the result there, not in the repo.
