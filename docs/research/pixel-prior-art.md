# Pixel prior art: stock features, modules and apps

Researched 2026-11-14. Dates matter: this ecosystem changes with every Pixel feature drop. Android Authority and XDA blocked direct fetches, so some details come from search snippets. **Unconfirmed** marks items without a primary source.

**Bottom line:** no stock feature and no module recreates Wonderland (or its outline effect) on a Pixel. Two root routes put a wallpaper on AOD: an overlay that turns on the stock path (DozeAodWallpaper), and drawing a copy inside SystemUI (Iconify, PixelXpert). The Pixel lock screen moved fully to Jetpack Compose in Android 17 QPR1, which broke modules that hook the old View-based lock screen.

## 1. Stock Pixel features

| Feature | What it does | On a Pixel 9 Pro XL? | When | Source |
|---|---|---|---|---|
| AOD wallpaper ("Show lock screen wallpaper") | Lock wallpaper behind the AOD clock, blurred and dimmed | **No**: "only available on Pixel 10 Pro & Pixel 10 Pro XL". Gated by a framework resource (`../hook-targets.md`) | Android 16 QPR1, Aug–Sep 2025 | [Pixel Help](https://support.google.com/pixelphone/answer/16520562), [Android Authority](https://www.androidauthority.com/pixel-10-pro-aod-wallpaper-3589468) |
| Live effects (Shape, Weather, Cinematic) | Shape cuts out the subject; Weather adds an animated overlay; effects play ~3–4 s then go static | Yes | Android 16 QPR1, Sep 2025 | [PiunikaWeb](https://piunikaweb.com/2025/09/05/pixels-new-live-effects-wallpaper-feature-falls-flat-with-users/) |
| Cinematic wallpaper | AI 3D depth from one photo; moves on unlock and app switch | Yes | Jun 2023 | [Chrome Unboxed](https://chromeunboxed.com/how-to-use-google-pixel-phone-cinematic-wallpaper-effect/) |
| Magic Portrait / Photo Shuffle wallpapers | Google live wallpapers that declare `supportsAmbientMode=true` | Installed on CP4A (`../hook-targets.md`); whether the picker offers them on this device is unverified | — | on-device check |
| Lock screen clock | Preset styles, width/weight sliders, colour, size, dynamic size | Yes | Android 16 / 16 QPR1 | Pixel Help |
| AI-generated lock screen clocks | Clock designed from a text prompt | Not live (Canary 2608) | Aug 2026 | [Android Authority](https://www.androidauthority.com/google-android-17-ai-lock-screen-clock-3695639/) |
| Lock screen widgets / hub mode | Swipe-left widget page; hub while charging | Yes | Android 16 QPR2, Dec 2025 | [9to5Google](https://9to5google.com/2025/08/20/android-qpr2-lock-screen-widgets/) |
| Lock screen blur | Blur behind notifications and shortcuts | Android 17 QPR2 (rolled out 10 Nov 2026) | Nov 2026 | [9to5Google](https://9to5google.com/2026/11/10/android-17-qpr2-pixel/) |
| New lock screen customisation page | Buttons for wallpaper, colours, clock, widgets, "media" | Not live (17 QPR3 Beta 1) | Oct 2026 | [Android Authority](https://www.androidauthority.com/google-wallpaper-style-app-lock-screen-customization-teardown-3718877/) |
| AOD "Min Mode" | Apps drawing on AOD; `enable_minmode` flag exists | **Unconfirmed**, no public API | Oct 2025 | [Android Authority](https://www.androidauthority.com/android-17-aod-min-mode-rumor-3611806/) |

## 2. Root and Xposed modules

| Name | What | How | Android 17 / Vector status (Nov 2026) |
|---|---|---|---|
| [Iconify](https://github.com/Mahmud0808/Iconify) | 61 lock-screen clock layouts, depth wallpaper (auto cut-out or own images) with "show on AOD", lock-screen widgets, weather, album art | Old path: hooks `AodBurnInSection.addViews`, injects Views into `keyguard_root_view`. 17 QPR1+ path: hooks `LockscreenContent.Content` and `LockscreenElementFactoryImpl.LockscreenElement` ([LockscreenSceneInjector.kt](https://github.com/Mahmud0808/Iconify/blob/beta/app/src/main/java/com/drdisagree/iconify/xposed/modules/extras/utils/misc/LockscreenSceneInjector.kt)). Depth: grabs the bitmap in `ImageWallpaper$CanvasEngine.drawFrameOnCanvas`, cuts out with ML Kit or an AI plugin; on AOD hides the background and dims the subject (alpha 192) ([BaseDepthWallpaper.kt](https://github.com/Mahmud0808/Iconify/blob/beta/app/src/main/java/com/drdisagree/iconify/xposed/modules/lockscreen/BaseDepthWallpaper.kt)) | Stable v8.0.0 (May 2026) predates the Compose lock screen. The **beta** branch (CI builds, 8 Nov 2026) supports 17 QPR1 Compose ([#966](https://github.com/Mahmud0808/Iconify/issues/966), [#971](https://github.com/Mahmud0808/Iconify/issues/971)) and moved to libxposed API 102 on 2 Nov. Lists Vector |
| [PixelXpert](https://github.com/siavash79/PixelXpert) | Depth wallpaper with AOD option, lock-screen tweaks | [DepthWallpaper.java](https://github.com/siavash79/PixelXpert/blob/canary/app/src/main/java/sh/siava/pixelxpert/xposed/modpacks/systemui/DepthWallpaper.java): wallpaper and subject FrameLayouts in `keyguard_root_view`, clock views at z = −5; cut-out via ML Kit or a bundled U²-Net-lite model | **Archived 21 Jun 2026** |
| [Codecity001/PixelXpert](https://github.com/Codecity001/PixelXpert) (fork) | Same features | Android 17 Compose fixes (Sep 2026), libxposed API 102 (Oct 2026); its depth wallpaper still uses the View-based `AodBurnInSection` hook | Depth wallpaper likely broken on 17 QPR1+ (unverified) |
| [DozeAodWallpaper](https://github.com/xpasha/DozeAodWallpaper) (Magisk) | Turns on the stock blurred AOD wallpaper; AOD brightness control | Static overlay in `system/product/overlay/` (target `android`, `isStatic`, priority 100) setting `config_dozeSupportsAodWallpaper=true` and `config_screenBrightnessDoze`. `service.sh` waits for boot, then sets Secure `doze_always_on_wallpaper_enabled=1` and `doze_always_on=1`, writes Global `always_on_display_constants` (`screen_brightness_array=…`, `dimming_scrim_array=-1:0:0:0:0`, `wallpaper_dimming_scrim_array=-1:0:0:0:0`) and `killall com.android.systemui`. `action.sh` cycles brightness; `uninstall.sh` *deletes* `doze_always_on` (turns AOD off) | Tested on a Pixel 8 Pro on Android 17 (pre-QPR1); untested on the 9 Pro XL. v2.1, Aug 2026. The prebuilt overlay APK is committed to its repo |
| [DepthWall](https://github.com/Srimi1/depthwall) | Layered depth/parallax live wallpaper with a root module for AOD | Module puts the APK in `/system/app` for `AMBIENT_WALLPAPER` and a static overlay (priority 980) in `/product/overlay`; `customize.sh` aborts if an installed copy has another signing key; `service.sh` grants `WRITE_SECURE_SETTINGS` with `pm grant` and turns the Secure setting on once (marker file), after which the app's switch decides. The ambient service ships `android:enabled="false"` and is enabled at runtime only while the permission is granted. The engine declares `onAmbientModeChanged` without `override`, keeps it with an R8 rule, and falls back to display-state changes. One still frame per AOD entry, eight burn-in offsets (~6 px), no wake lock or sensors. Renders with GLES | Verified on an Android 17 emulator (`docs/AOD_AND_EMULATOR.md`, 2026-11-03): system copy gets the permission; a same-key normal install becomes `UPDATED_SYSTEM_APP` and keeps data and permission; the hidden callback reaches an R8 build; scrim settles at 0.4; SystemUI blur radius 44. Notes that the developer option "Allow window-level blurs" removes the blur. Not verified on a phone. v1.5.0, Nov 2026. **Closest prior art to Afterimage's route** |

Only DozeAodWallpaper and DepthWall use AOSP's ambient-wallpaper path; Iconify and PixelXpert draw their own copy inside SystemUI. No Pixel module does gyro parallax, particles, outline transitions or LockStar-style layout editing.

## 3. No-root apps

| App | Technique | Limitations |
|---|---|---|
| [AlwaysOn](https://github.com/Domi04151309/AlwaysOn) (open source) | Relaunches its own full-screen activity over the lock screen at screen-off; locks with Device Admin | Panel at normal power, not doze; replaces stock AOD. v3.10.0, May 2025 |
| Always On AMOLED | Same fake-AOD-activity model | Open-source repo archived Jun 2024 |
| aodNotify / Notification light for Pixel | Accessibility overlay drawn over stock AOD | ~1.5–3 %/h claimed; relies on accessibility |
| Always On Edge | Accessibility overlay for edge lighting, plus its own AOD | Same caveats |
| KLWP | Live wallpaper with layer editor, gyro-driven layers, animations | Goes dark on AOD |
| Depth clock wallpapers (Play Store) | On-device cut-out; clock drawn inside the wallpaper | Can't hide the stock clock |
| FloatDeck (F-Droid), Pixel4D | Gyro/accelerometer parallax | Home and lock only |

## 4. Building blocks for subject cut-outs

| Option | Status | Size / latency | Notes |
|---|---|---|---|
| [ML Kit Subject Segmentation](https://developers.google.com/ml-kit/vision/subject-segmentation/android) | 16.0.0-beta1 (Nov 2023), still beta | ~200 KB app size; model via Play services; ~200 ms on a Pixel 7 Pro (search snippet) | Input ≥ 512×512; foreground and per-subject masks. Used by Iconify and PixelXpert |
| [MediaPipe Image Segmenter](https://developers.google.com/edge/mediapipe/solutions/vision/image_segmenter) | Active | Pixel 6: Selfie 33 ms, DeepLab-v3 124/103 ms CPU/GPU | People and fixed classes, not general subjects |
| [MediaPipe Interactive Segmenter](https://developers.google.com/edge/mediapipe/solutions/vision/interactive_segmenter) | Active, brush-stroke input | 768², int8 | Good for user-guided cut-outs |
| U²-Net-lite / ONNX Runtime | PixelXpert bundles a 4.6 MB model; [Oxygen Customizer AI Plugin](https://github.com/DHD2280/Oxygen-Customizer-AI-Plugin) serves Iconify's FOSS build | — | Offline, general subjects |
| Android platform | No public segmentation API; the cinematic generator is a privileged system API | — | — |

## Gaps nobody fills yet

1. Wonderland itself (gyro parallax, particles, multi-layer animation) on the Pixel lock screen.
2. An outline / line-art transition into AOD. **Afterimage's v1.**
3. LockStar-style free layout editing.
4. Custom images or GIFs on AOD on a 9 Pro XL.
5. Separate AOD-only clock faces.
