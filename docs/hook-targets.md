# Hook targets per build

What Afterimage relies on in the framework and SystemUI, checked against each build's own APKs. Add a section for each new build fingerprint after an OTA, and keep the old ones.

## How to check a build

```bash
# Read-only checks of gates, permissions and lock-screen mode (changes nothing on the phone):
bash scripts/probe-device.sh            # add --wallpapers to scan live wallpapers for supportsAmbientMode

# Pull SystemUI and disassemble the classes of interest into a scratch folder (never the repo):
MSYS_NO_PATHCONV=1 adb pull /system_ext/priv-app/SystemUIGoogle/SystemUIGoogle.apk .
unzip -o -q SystemUIGoogle.apk classes.dex -d sui
DD="$LOCALAPPDATA/Android/Sdk/build-tools/36.0.0/dexdump.exe"
# One class (plus its inner classes) per file:
"$DD" -d sui/classes.dex | awk '/^Class #/{o=""} /Class descriptor/{d=$0; sub(/.*: .L/,"",d); sub(/;.*/,"",d); n=d; sub(/.*\//,"",n); b=n; sub(/\$.*/,"",b); if (b=="NotificationShadeDepthController"){f=n; gsub(/\$/,"_",f); o="cls/" f ".txt"}} {if(o!="") print > o}'
# Quick existence check for a class name:
grep -a -o 'Lcom/android/systemui/[a-zA-Z0-9/$_]*/WallpaperRepositoryImpl;' sui/classes.dex | sort -u
```

`aflags` needs root, so read flag state from SystemUI's dumpables, e.g. `adb shell "dumpsys activity service com.android.systemui/.SystemUIService SceneContainerStartable"`.

## google/komodo/komodo:17/CP4A.261105.040.A1/16581829 (Android 17 QPR2, 2026-11-05 patch)

Checked 2026-11-14, read-only. SystemUI is `/system_ext/priv-app/SystemUIGoogle/SystemUIGoogle.apk`, a single `classes.dex` (~27 MB). **Class names are not obfuscated**: every class below was found by name.

### Lock screen mode

- `SceneContainerFlag` `isEnabled=true` (from the `SceneContainerStartable` dumpable): the lock screen runs on the Compose scene container ("flexiglass"). `LockscreenContent` and `elements.LockscreenElementFactoryImpl` (in `com.android.systemui.keyguard.ui.composable`) exist; so do the View-era `AodBurnInSection` and `KeyguardBlueprintInteractor`.

### AOD wallpaper gates

| Gate | Value on this build | Notes |
|---|---|---|
| Framework bool `android:bool/config_dozeSupportsAodWallpaper` | `false` | Resource id `0x01110179` on this build |
| SystemUI integer `config_dozeSupportsAodWallpaperOverride` | `-1` (defer to framework) | Resource id `0x7f0b0028` on this build; 0/1 would force |
| Secure `doze_always_on` | user setting | Must be 1 |
| Secure `doze_always_on_wallpaper_enabled` | unset by default | Must be 1 |

`com.android.systemui.wallpapers.data.repository.WallpaperRepositoryImpl` evaluates `wallpaperSupportsAmbientMode` (inside `WallpaperRepositoryImpl$special$$inlined$map$1$2.emit`) as `doze_always_on == 1 && doze_always_on_wallpaper_enabled == 1 && configEnabled`, where `configEnabled` reads the SystemUI override integer and falls back to the framework bool. Same logic as `android17-release` source. `WallpaperInteractor.wallpaperSupportsAmbientMode` (a `Flow`) exposes it to consumers.

### Who tells the wallpaper about AOD

| Caller | Call |
|---|---|
| `com.android.systemui.doze.DozeWallpaperState.transitionTo` | `IWallpaperManager.setInAmbientMode(ZJ)V` |
| `com.android.systemui.statusbar.phone.CentralSurfacesImpl.start` | `IWallpaperManager.setInAmbientMode(ZJ)V` |

### Scrim (dimming) over the wallpaper on AOD

- `com.android.systemui.keyguard.domain.interactor.LightRevealScrimInteractor`: max alpha constants `0.64f` and `0.4f` live in `LightRevealScrimInteractor$listenForStartedKeyguardTransitionStep$1$1`; the `KeyguardState.GONE` / `OCCLUDED` → `AOD` special case (full black) is in `LightRevealScrimInteractor$special$$inlined$flatMapLatest$1`.
- Light-sensor dimming: `com.android.systemui.doze.DozeScreenBrightness` (`transitionTo`, `updateBrightnessAndReady`, `resetBrightnessToDefault`) calls `com.android.systemui.statusbar.phone.DozeServiceHost.setAodWallpaperDimmingScrim(F)V`. Scene-container consumers: `keyguard.domain.interactor.AodDimInteractor`, `keyguard.ui.viewmodel.LockscreenBehindScrimViewModel` (has an `aodDimInteractor` field).
- The dimming levels can be overridden without a hook through Global `always_on_display_constants` (unset by default): `com.android.systemui.doze.AlwaysOnDisplayPolicy` is present and the dex still contains the `wallpaper_dimming_scrim_array` and `dimming_scrim_array` keys (string check, 2026-11-14). Whether a zeroed `wallpaper_dimming_scrim_array` removes the wallpaper dimming is tested in Phase 0b.

### Blur on AOD

`com.android.systemui.statusbar.NotificationShadeDepthController$statusBarStateCallback$1.onDozeAmountChanged(FF)V`, bytecode summary:

```
outer.prevDozeAmount = eased
r = 0f
if (outer.wallpaperSupportsAmbientMode && eased != 0f)
    r = MathUtils.lerp(blurUtils.minBlurRadius, blurUtils.maxBlurRadius / 2f, eased)
if (outer.wakeAndUnlockBlurRadius != r) outer.wakeAndUnlockBlurRadius = r
```

Fields on `NotificationShadeDepthController`: `prevDozeAmount:F`, `wallpaperSupportsAmbientMode:Z`, `wakeAndUnlockBlurRadius:F`, `blurUtils:Lcom/android/systemui/statusbar/BlurUtils;` (`minBlurRadius:F`, `maxBlurRadius:F`). Phase 2 candidate: after `onDozeAmountChanged`, force `wakeAndUnlockBlurRadius = 0f` while our wallpaper is the lock wallpaper. Unverified: whether this path still drives the visible blur in scene-container mode (`window.domain.interactor.WindowRootViewBlurInteractor`, `scene.ui.viewmodel.SceneTransitionBlurViewModel` and `window.ui.BlurChoreographer` also exist).

The live value can be read without root: `adb shell "dumpsys activity service com.android.systemui/.SystemUIService NotificationShadeDepthController"` prints a `StatusBarWindowBlurController` block with `wakeAndUnlockBlurRadius`, `shouldApplyShadeBlur`, `blursDisabledForAppLaunch` and `lastAppliedBlur` (all 0 with the gate closed and the screen on, 2026-11-14). Reading it during AOD with the gate open answers whether this path still drives the blur (Phase 0b). Global `disable_window_blurs` (the "Allow window-level blurs" developer option) is unset by default.

### Doze and power

| Item | Value | Meaning |
|---|---|---|
| SystemUI bool `doze_suspend_display_state_supported` | `false` | `DozeSuspendScreenStatePreventingAdapter` turns `DOZE_SUSPEND` into `DOZE` |
| Observed doze screen state (`dumpsys power`, `mDozeScreenStateOverrideFromDreamManager`) | `3` = `Display.STATE_DOZE` | Frames drawn during AOD reach the panel |
| `DozeParameters` | `getAlwaysOn()=true`, `getDisplayStateSupported()=true`, pulse 6730 ms (in 130, visible 6000, out 600) | |

Present: `doze.util.BurnInHelperKt`, `keyguard.domain.interactor.BurnInInteractor`, `keyguard.ui.viewmodel.AodBurnInViewModel`, `statusbar.LightRevealScrim`, `statusbar.LightRevealEffect`, `wallpapers.domain.interactor.WallpaperFocalAreaInteractor`.

### With the AOD-wallpaper gate open (Phase 0b, 2026-11-14)

Observed with the Phase 0b module installed (static overlay + Secure setting) and a static `ImageWallpaper`. Frames were captured with `adb exec-out screencap -p` while dozing (it returns SystemUI's composited frame, not panel brightness) and compared by mean luma and edge energy over the wallpaper area. Captures stay outside the repo.

| What | Result |
|---|---|
| Gate | The overlay makes `config_dozeSupportsAodWallpaper` resolve to `true`. Before it, the `false` came from `framework-res.apk` itself, not a Google overlay. Changing Secure `doze_always_on_wallpaper_enabled` applies live in both directions: no reboot or SystemUI restart |
| Settings switch | With the framework bool `true`, Settings shows Google's "Show lock screen wallpaper" switch for the always-on display, driving the same Secure setting |
| Lock screen → AOD | The wallpaper stays visible, blurred and dimmed; the frame settles within 1 s and holds |
| Dimming | The AOD frame keeps 60 % of the lock screen's luma on a dark wallpaper: `LightRevealScrimInteractor`'s 0.4 scrim |
| Light-sensor dimming | None on this device: `DozeScreenBrightness` dumps `sensorToWallpaperScrimOpacity=[-1, 0, 0, 0, 0]` (`dumpsys activity service com.android.systemui/.doze.DozeService`), so overriding `wallpaper_dimming_scrim_array` changes nothing |
| Blur | `NotificationShadeDepthController` reports `wakeAndUnlockBlurRadius: 51.0` while dozing (0 when the gate is closed), while its `lastAppliedBlur` stays 0. The visible blur is a window-level blur: with Global `disable_window_blurs=1` the AOD frame is as sharp as the lock screen. Edge energy is 8 % of the lock screen's with the blur, and 63 % without it, about what the 0.4 scrim alone leaves (60 %). Phase 2 still has to find where the radius is applied in scene-container mode (`WindowRootViewBlurInteractor`, `BlurChoreographer`) |
| From an app (GONE → AOD) | Full black (luma 0.1) at about 0.7 s, then the same frame as from the lock screen by 1.7 s, held to at least 16 s. The 1.0 scrim covers only the transition |
| Secure setting 0 (gate half-closed) | AOD is black again and the blur radius drops to 0 |
| Google's ambient wallpapers | `com.google.android.apps.magicportrait/.service.MagicPortraitWallpaperService` and `.service.PhotoShuffleWallpaperService` are present; Photo Shuffle's preview didn't load without setup, so how they draw on AOD wasn't checked |

### Permissions

| Permission | Protection level | Consequence |
|---|---|---|
| `android.permission.AMBIENT_WALLPAPER` | `signature|preinstalled` | Granted to apps on the system image, so a Magisk-systemized app gets it. Checked when the wallpaper binds (`WallpaperManagerService`), not on `setInAmbientMode` |
| `android.permission.SET_WALLPAPER_COMPONENT` | `signature|privileged` | Needed to set a live wallpaper directly (`setWallpaperComponentWithFlags`); otherwise use `ACTION_CHANGE_LIVE_WALLPAPER` |

`ro.control_privapp_permissions` is `enforce` on this build: a priv-app that requests a `signature|privileged` permission missing from a `privapp-permissions` allowlist stops the phone from booting. Any module that installs Afterimage under `priv-app` must ship the allowlist.

Panel: 1344 × 2992 (`wm size`). Read the size at runtime anyway.

### Preinstalled live wallpapers and AOD support

Checked by dumping each APK's `<wallpaper>` XML with `aapt2 dump xmltree` (`supportsAmbientMode` is attr `0x0101058d`).

| Package / service | `supportsAmbientMode` |
|---|---|
| `com.google.android.apps.magicportrait` (`/system_ext/priv-app/MagicPortraitWallpapers`, holds `AMBIENT_WALLPAPER`): `MagicPortraitWallpaperService`, `PhotoShuffleWallpaperService` | **true** |
| `com.google.android.apps.emojiwallpaper` (emoji and video wallpapers) | false |
| `com.google.android.wallpaper.effects` (`CinematicWallpaperService`) | false |
| `com.google.android.apps.ai.icons` | false |
| `com.google.android.apps.aiwallpapers`, `com.google.android.apps.pixel.customizationbundle` | not determined |

### Clock plugins (for later scope)

`com.android.systemui.shared.clocks.ClockRegistry` and `com.android.systemui.shared.plugins.PluginActionManager` exist under those names. The selected clock is Secure `lock_screen_custom_clock_face`, JSON `{"clockId":…,"metadata":{…,"appliedTimestamp":…},"axes":[…]}`.

## google/komodo/komodo:17/CP3A.261005.005/16271449 (Android 17 QPR1)

Not checked for Afterimage. The phone moved off this build between 2026-10-29 and 2026-11-14. Iconify's issue tracker reports the lock screen moved fully to Compose on QPR1 (`research/pixel-prior-art.md`).
