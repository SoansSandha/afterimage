# AOSP internals: ambient wallpapers, doze, keyguard layout, clock plugins

Researched 2026-11-14 from source. `android17-release` is tag `android-17.0.0_r1` (frameworks/base `94b4c163`, 2026-05-21). No `android17-qpr1-release` or QPR2 branch was published, so on-device behaviour must be confirmed per build (`../hook-targets.md`, which already confirms §1's gate, scrim and blur logic on CP4A). Paths are in `frameworks/base` on `android17-release` unless noted; `SUI/` = `packages/SystemUI/src/com/android/systemui/`. Browse at `https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/<path>`.

## 1. Ambient (AOD) wallpaper path

- `WallpaperService.Engine.onAmbientModeChanged(boolean inAmbientMode, long animationDuration)` exists as `@SystemApi @MainThread` (`core/java/android/service/wallpaper/WallpaperService.java`). Path: `setInAmbientMode` → `DO_IN_AMBIENT_MODE` → `doAmbientModeChanged()` (only if `mCreated`).
- On AOD, an engine without ambient support is reported invisible: `reportVisibility()` computes `visible = mVisible && (displayFullyOn || supportsAmbientMode)`.
- Attribute: `android:supportsAmbientMode` on the `<wallpaper>` XML (`core/res/res/values/attrs.xml`: "@hide @SystemApi … requires the android.permission.AMBIENT_WALLPAPER permission"). `WallpaperInfo` defaults it to true only on watches.
- Permission: `AMBIENT_WALLPAPER` is `signature|preinstalled` (`core/res/AndroidManifest.xml`).
- The permission is checked when binding, in `services/core/java/com/android/server/wallpaper/WallpaperManagerService.java` `bindWallpaperDescriptionLocked()`: a wallpaper that declares `supportsAmbientMode()` without it is rejected ("Selected service does not have AMBIENT_WALLPAPER"; `SecurityException` if user-picked). A Magisk-systemized app counts as preinstalled (inference; DepthWall relies on it).
- `WallpaperManagerService.setInAmbientMode(boolean, long)` has no permission check. `IWallpaperManager.aidl`: `oneway void setInAmbientMode(...)`, "Called from SystemUI when it shows the AoD UI". It forwards to every active engine where `mInfo == null || mInfo.supportsAmbientMode()`; `attachEngine()` re-sends the current state to new engines.
- SystemUI caller: `SUI/doze/DozeWallpaperState.java` (a `DozeMachine.Part`, registered in `doze/dagger/DozeModule.java`). Ambient = true for DOZE, DOZE_AOD, DOZE_AOD_DOCKED, DOZE_AOD_PAUSING/PAUSED, DOZE_REQUEST_PULSE, DOZE_PULSING, DOZE_PULSING_WITHOUT_UI, DOZE_PULSING_AUTH_UI, DOZE_PULSE_DONE; false for DOZE_PULSING_BRIGHT.
- Whether the wallpaper shows on AOD no longer depends on `WallpaperInfo`. `SUI/wallpapers/data/repository/WallpaperRepository.kt`: `wallpaperSupportsAmbientMode = aodEnabled && wallpaperEnabled && configEnabled()`, where `aodEnabled` = Secure `doze_always_on`, `wallpaperEnabled` = Secure `doze_always_on_wallpaper_enabled` (@hide, "Indicates whether ambient wallpaper is visible with AOD"), and `configEnabled()` uses SystemUI's `config_dozeSupportsAodWallpaperOverride` (-1 ignore, 0/1 force) or else the framework's `config_dozeSupportsAodWallpaper` (default false).
- The wallpaper window stays shown under AOD: `SUI/shade/NotificationShadeWindowControllerImpl.applyKeyguardFlags` sets `FLAG_SHOW_WALLPAPER` when `state.dozing && mDozeParameters.getAlwaysOn()`. Scrims hide or dim it:
  - `SUI/keyguard/domain/interactor/LightRevealScrimInteractor.maxAlpha`: `1f` when ambient is off; when on, `0.64f` if the wallpaper colours have `HINT_SUPPORTS_DARK_TEXT`, else `0.4f`, and `1f` during GONE→AOD and OCCLUDED→AOD.
  - Light-sensor dimming: `SUI/doze/DozeScreenBrightness.java` from `config_doze_brightness_sensor_to_wallpaper_scrim_opacity`; in the scene-container shade it goes `DozeHost.setAodWallpaperDimmingScrim` → `AodDimInteractor.setWallpaperDimAmount` → `LockscreenBehindScrimViewModel.alpha`.
  - `ScrimState.AOD` (`SUI/statusbar/phone/ScrimState.java`) no longer has `mWallpaperSupportsAmbientMode`; it sets `mFrontAlpha = (alwaysOn||docked||quickPickup) ? mAodFrontScrimAlpha : 1f`, `mBehindAlpha = TRANSPARENT`.
- No aconfig flag gates this (searched `packages/SystemUI/aconfig/systemui.aconfig`, `frameworks/libs/systemui/aconfig/systemui.aconfig`, core wallpaper aconfig). Related flags that exist: `aod_inactivity_detection`, `adaptive_low_freq_mode_on_aod`, `enable_minmode`. `ImageWallpaper` has no ambient code.

## 2. Doze power model

From `DozeMachine.State.screenState()` and `DozeUi`:

| Doze state | Display state | Minute tick |
|---|---|---|
| DOZE_AOD, DOZE_AOD_PAUSING | `STATE_DOZE_SUSPEND` (becomes `STATE_DOZE` when `doze_suspend_display_state_supported` is false, as on the 9 Pro XL) | yes |
| DOZE_AOD_DOCKED, DOZE_AOD_MINMODE, DOZE_PULSING* | `STATE_ON` | — |
| DOZE, DOZE_AOD_PAUSED, DOZE_SUSPEND_TRIGGERS | `STATE_OFF` | no |
| DOZE_REQUEST_PULSE | OFF if the display needs blanking, else ON | yes |

- Redraw cadence: once a minute. `DozeUi` schedules an `AlarmTimeout` "doze_time_tick" for the next minute boundary; `onTimeTick` runs `dozeTimeTick` under a partial wake lock. On AOD re-entry it pushes a frame immediately and another after 500 ms.
- Clocks tick faster only while `doze < DOZE_TICKRATE_THRESHOLD (0.99f)` (`ClockEventController`).
- `DozeScreenState` delays entering AOD by `ENTER_DOZE_DELAY = 4000` ms during the lockscreen→AOD animation (1200 ms with a finger on the UDFPS), holding a wake lock meanwhile. **This is the window for Afterimage's photo → outline transition.**
- Any held `DRAW_WAKE_LOCK` turns `STATE_DOZE_SUSPEND` into `STATE_DOZE` (`services/core/java/com/android/server/power/PowerGroup.java`). Continuous animation means holding wake locks and paying in battery.
- Burn-in: `SUI/doze/util/BurnInHelper.kt` zigzags over elapsed minutes (X period 43 min, Y 271 min, scale 0.75 + up to 0.2 over 181 min), amplitudes from `R.dimen.burn_in_prevention_offset_x/_y`. `BurnInInteractor.burnIn()` recomputes on every `dozeTimeTick`; `AodBurnInViewModel` blends it with transition progress. Clocks get it via `ClockFaceLayout.applyAodBurnIn(AodClockBurnInModel(scale, tx, ty))`. **SystemUI doesn't shift the wallpaper**, so Afterimage must shift its own outline.

## 3. Keyguard layout (for later scope)

- View path: `KeyguardBlueprint` (id, sections, `replaceViews`/`applyConstraints`) and abstract class `KeyguardSection` (`addViews`, `bindData`, `applyConstraints`, `removeViews`). `DefaultKeyguardBlueprint` lists, in order: accessibility actions, indication area, shortcuts, settings popup, status bar, notification stack, `AodNotificationIconsSection`, AOD promoted notifications, `SmartspaceSection`, `AodBurnInSection`, `ClockSection`, keyguard slice, device entry, fingerprint a11y overlay. Blueprints are contributed with `@Binds @IntoSet` in `KeyguardBlueprintModule`; `KeyguardBlueprintInteractor.blueprintId` follows the shade mode and would overwrite a custom id.
- Compose path (scene container, live on the 9 Pro XL since 17 QPR1): `LockscreenContent.kt` merges a Dagger `@IntoSet` set of `LockscreenElementProvider`s (`ElementProviderModule`) with the current clock's `smallClock.layout` / `largeClock.layout`. `ClockFaceLayout` extends `LockscreenElementProvider`, sorted by priority, so **a clock plugin can add or override lock-screen elements**. Iconify hooks `LockscreenContent.Content` and `LockscreenElementFactoryImpl.LockscreenElement` (`pixel-prior-art.md`).

## 4. Clock plugins (for later scope)

- Interfaces in `packages/SystemUI/plugin/src/com/android/systemui/plugins/keyguard/ui/clocks/`: `ClockProviderPlugin` (action `com.android.systemui.action.PLUGIN_CLOCK_PROVIDER`, version 1), `ClockProvider` (`initialize`, `getClocks`, `createClock(ctx, ClockSettings)`, `getClockPickerConfig`), `ClockController` (`smallClock`/`largeClock`, `events`, `initialize(isDarkTheme, dozeFraction, foldFraction)`), `ClockFaceController` (`layout`, `events.onTimeTick`, `animations.doze(fraction)`, `enter`, `fold`, `charge`).
- Doze reaches clocks via `src/com/android/keyguard/ClockEventController.kt` `handleDoze()` → `animations.doze(doze)`, so a clock can have a separate AOD look.
- Selection: Secure `lock_screen_custom_clock_face`, written by `customization/.../shared/clocks/ClockRegistry.kt` as `ClockSettings.toJson` (`clockId`, `seedColor`, `metadata` with `appliedTimestamp`, `axes`). Default id `"DEFAULT"`.
- Loading gate (`packages/SystemUI/shared/src/.../shared/plugins/PluginActionManager.kt` `loadPluginComponent`): blocked unless `Build.IS_DEBUGGABLE` or the package is privileged (`config_pluginAllowlist`, default only `com.android.systemui`); `PluginEnabler.isEnabled`; and the plugin must hold `com.android.systemui.permission.PLUGIN` (`signature`). A Vector hook on `PackageConfig.isPrivileged` / `loadPluginComponent`, or injecting a provider into `ClockRegistry`, gets around it.
- The picker builds its own `ClockRegistry` in its own process (`packages/apps/ThemePicker` `ClockRegistryProvider`), so custom clocks need hooking there too to appear in the picker.

## 5. Lock-screen live wallpapers

- Since API 34: `WallpaperManager.setWallpaperComponentWithFlags(name, which)` (`@SystemApi`, needs `SET_WALLPAPER_COMPONENT`, `signature|privileged`).
- An engine knows lock vs home through `Engine.getWallpaperFlags()` and `onWallpaperFlagsChanged(int)` (public since API 34). Android 16 added `WallpaperDescription`/`WallpaperInstance`, so one service can show different content on home and lock.
- A normal app uses `ACTION_CHANGE_LIVE_WALLPAPER` + `EXTRA_LIVE_WALLPAPER_COMPONENT` and the user confirms; whether the Pixel picker offers lock-only for live wallpapers is unverified.
- `WallpaperFocalAreaInteractor` sends `COMMAND_LOCKSCREEN_LAYOUT_CHANGED` (focal-area bounds) and `COMMAND_LOCKSCREEN_TAP` to the wallpaper.

## Main hook targets

1. `WallpaperRepositoryImpl.wallpaperSupportsAmbientMode` / config check
2. `LightRevealScrimInteractor.maxAlpha` (0.4 / 0.64 / 1.0)
3. `NotificationShadeDepthController$statusBarStateCallback$1.onDozeAmountChanged` (AOD blur; see `../hook-targets.md`)
4. `DozeWallpaperState.transitionTo`
5. `WallpaperManagerService.bindWallpaperDescriptionLocked` (system_server; the AMBIENT_WALLPAPER check)
6. `WallpaperService.Engine.reportVisibility` / `onAmbientModeChanged`
7. `DozeMachine.State.screenState`, `DozeUi.scheduleTimeTick`
8. `BurnInInteractor.burnIn` / `AodBurnInViewModel`
9. Later scope: `LockscreenContent`, `LockscreenElementProvider`, `DefaultKeyguardBlueprint.getSections()`, `PackageConfig.isPrivileged` / `PluginActionManager.loadPluginComponent`, `ClockRegistry`, `ClockEventController.handleDoze`
