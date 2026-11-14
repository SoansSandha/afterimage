# Afterimage plan

Status: Phase 0a in progress (2026-11-14). The repo infrastructure and Gradle skeleton are ported from Sidearm and `./gradlew check` passes; `git init`, the ECC submodule and the first commit wait for the owner. Decisions: GPL-3.0-or-later, ECC like Sidearm, `develop` + `main` (ADRs 0004–0006). This is the roadmap from repo setup to v1.0 and beyond. The facts behind it are in [hook-targets.md](hook-targets.md) and [research/](research/), and the decisions are in [decisions/](decisions/).

## 1. Goal

Recreate the edge/outline lock-screen effect from Samsung's Good Lock "Wonderland" module on a rooted Pixel 9 Pro XL: when the phone locks and drops into the always-on display (AOD), the lock-screen photo turns into a glowing line drawing of itself, and turns back into the photo on wake.

Samsung's version (`research/samsung.md` §1) is set up as a "lock screen effect" / transition wallpaper in Wonderland, applied to the lock screen with AOD on. Its options are **effect type, speed, thickness, brightness and grayscale**. Samsung's own advice is to use a photo with strong, clear edges (a subject against a plain background).

## 2. Scope

| | In scope |
|---|---|
| **v1** | The outline effect, with options matching Samsung's (effect type, speed, thickness, brightness, grayscale), plus "subject only" (outline just the person or pet, using an on-device cut-out) and burn-in protection |
| **Later, if v1 works** | More lock↔AOD transitions, a charging mode, photo rotation, Wonderland-style layered wallpapers (image/text/particle layers, gyro tilt parallax). Phases 7–10 in §7 |
| **Maybe** | SystemUI clock plugins (a separate AOD clock look, like Samsung ClockFace), stickers/images on AOD, LockStar-style layout editing, other Pixels. Feasibility notes are in `research/aosp-internals.md` §3–4 and `research/pixel-prior-art.md` |
| **Out** | Continuous animation on battery during AOD (ADR 0003). Samsung's names or assets. Any network access |

## 3. What the user sees

| Moment | What happens |
|---|---|
| Photo chosen | The app computes the outline once (edge detection, optionally limited to the subject) and caches it |
| Lock screen | The photo, drawn normally |
| Screen off → AOD | `onAmbientModeChanged(true, duration)`: photo → outline animation, finished within the doze-entry window (SystemUI's `ENTER_DOZE_DELAY` is 4000 ms; target ≤ 3 s) |
| AOD, including notification pulses | One still outline frame. Nothing animates (ADR 0003). The frame is shifted a few pixels on every entry (burn-in) |
| Wake to the lock screen | `onAmbientModeChanged(false, duration)`: outline → photo |
| Fingerprint unlock straight from AOD | Jump to the photo; the launcher takes over |

v1 settings: effect type, speed, line thickness, glow, brightness, grayscale or colour lines, subject only, burn-in shift, the photo, setting it as the lock wallpaper, and an on/off switch for the wallpaper on AOD.

## 4. Facts this plan rests on

Verified on CP4A unless marked otherwise (`hook-targets.md`).

- **Gates.** SystemUI only shows the wallpaper on AOD when Secure `doze_always_on`=1, Secure `doze_always_on_wallpaper_enabled`=1 and the config allows it (SystemUI's `config_dozeSupportsAodWallpaperOverride`, -1 = defer to the framework's `config_dozeSupportsAodWallpaper`, which is false on the 9 Pro XL). Google enables it only on the Pixel 10 Pro / 10 Pro XL.
- **Permission.** An ambient wallpaper needs `AMBIENT_WALLPAPER` (`signature|preinstalled`), checked when system_server binds it. A `/system/app` copy is enough; priv-app isn't needed for this. On an Android 17 emulator, DepthWall confirmed that a same-key APK installed over the system copy becomes an `UPDATED_SYSTEM_APP` and keeps the permission and its data (`research/pixel-prior-art.md` §2).
- **Hidden callback.** `onAmbientModeChanged(boolean, long)` is `@SystemApi` and missing from the SDK stubs. Declared without `override` it is still dispatched by ART, and it reaches an R8 build if a keep rule protects it (DepthWall, emulator).
- **Dimming.** With the gate on, SystemUI puts a black scrim over the wallpaper on AOD: 0.4 alpha (0.64 for wallpapers with dark-text hints), and 1.0 for transitions that start from GONE or OCCLUDED (locking from inside an app). Light-sensor dimming adds to it (`setAodWallpaperDimmingScrim`). Global `always_on_display_constants` has a `wallpaper_dimming_scrim_array` key that DozeAodWallpaper zeroes on Android 17 (Pixel 8 Pro). CP4A's SystemUI still contains it (`AlwaysOnDisplayPolicy`); whether zeroing it works is tested in Phase 0b.
- **Blur.** With the gate on, SystemUI blurs the wallpaper as AOD comes in: `wakeAndUnlockBlurRadius = lerp(minBlur, maxBlur/2, dozeAmount)` (radius 44 on DepthWall's emulator). That smears thin lines. The developer option "Allow window-level blurs" (Global `disable_window_blurs`) turns off every SystemUI blur, which is a way to see sharp lines while testing before the hook exists.
- **Turning the gate on affects every wallpaper.** Other wallpapers get Google's blurred, dimmed AOD look. Acceptable, but the module needs an easy off switch.
- **Power.** On this panel `doze_suspend_display_state_supported` is false, so AOD runs in `Display.STATE_DOZE`, not `STATE_DOZE_SUSPEND`: frames drawn during AOD do reach the screen. The CPU still sleeps between SystemUI's once-a-minute redraws, and anything that animates keeps it awake. Budget: a transition of a few seconds on entry/exit, then one still frame (ADR 0003).
- **Burn-in.** A static outline held for hours is the main risk. SystemUI shifts its own AOD content (`BurnInHelper`), but not the wallpaper.
- **Hooks in SystemUI.** libxposed API 102 calls `onPackageLoaded` and then `onPackageReady(PackageReadyParam)` for each scoped app; `onPackageReady` carries the app's class loader, after its `AppComponentFactory`. SystemUI class names aren't obfuscated on CP4A.
- **Privileged permissions.** `signature|privileged` permissions (`SET_WALLPAPER_COMPONENT`, `READ_WALLPAPER_INTERNAL`) need a priv-app install plus a `privapp-permissions` allowlist. CP4A enforces the allowlist (`ro.control_privapp_permissions=enforce`), so a missing entry stops the phone booting.
- **OTA churn.** Hooks must be re-verified per build (`hook-targets.md`). The lock screen moved to Compose in Android 17 QPR1; QPR2 is on the phone now.

## 5. Design

### 5.1 How the parts fit

```
 Magisk (at boot, as root)                          system_server
 ┌──────────────────────────────────────┐           ┌───────────────────────────────────────┐
 │ mounts /system/app/Afterimage        │──────────►│ WallpaperManagerService               │
 │   (preinstalled → AMBIENT_WALLPAPER) │           │   binds our engine, relays ambient    │
 │ mounts /product/overlay/…AodConfig   │           │   mode to it                          │
 │   (config_dozeSupportsAodWallpaper)  │           └──────────▲──────────────────┬─────────┘
 │ service.sh: Secure setting on        │       setInAmbientMode│                  │onAmbientModeChanged
 └──────────────────────────────────────┘                      │                  ▼
 SystemUI (com.android.systemui)                    Afterimage app process
 ┌──────────────────────────────────────┐           ┌───────────────────────────────────────┐
 │ DozeWallpaperState ──────────────────┼───────────┘ AmbientOutlineWallpaperService       │
 │ scrim + blur over the wallpaper      │             │ engine → render thread (AGSL)       │
 │ AfterimageModule (loaded by Vector): │◄── remote ──┤ settings UI, outline cache          │
 │   zero blur and scrim for our        │    prefs    │ Vector client (hook settings)       │
 │   wallpaper only                     │   (Vector)  └─────────────────────────────────────┘
 └──────────────────────────────────────┘
```

| Piece | Runs | Why it's needed | Built from |
|---|---|---|---|
| Magisk module | At boot, as root | Puts the APK on the system image (so it holds `AMBIENT_WALLPAPER`), adds the static overlay that opens the gate, sets the Secure setting, restores it on uninstall | `magisk/` + `:overlay` + the APK, zipped by `scripts/package-module.sh` |
| Wallpaper engine | App process, bound by system_server | Draws the photo, the transition and the outline | `:app` (`.wallpaper`, `.render`) + `:core` |
| Settings app | App process, while open | Photo, options, preview, status checks, setting the wallpaper | `:app` (`.ui`, `.data`) |
| SystemUI hook | SystemUI process, loaded by Vector | Removes AOD blur and scrim, only while our wallpaper is the lock wallpaper | `:hook` + `:core` |

One APK carries the app, the wallpaper and the hook, as in Sidearm. Vector loads `:hook` from it into SystemUI; system_server binds the wallpaper service from it. ADR in Phase 1.

### 5.2 Wallpaper engine

- One service, `AmbientOutlineWallpaperService`, declared `android:enabled="false"`, with `supportsAmbientMode="true"` in its `<wallpaper>` XML. At app start `AmbientComponent.sync()` enables it only while `AMBIENT_WALLPAPER` is granted (the module is installed) and disables it otherwise. A plain install then never offers a wallpaper that system_server would refuse to bind.
- `onAmbientModeChanged(inAmbientMode: Boolean, animationDuration: Long)` is declared without `override` and kept in `proguard-rules.pro`. Fallback: a `DisplayManager` listener (`STATE_DOZE`/`STATE_DOZE_SUSPEND`) in case the callback never arrives.
- Engine callbacks arrive on the app's main thread. They only turn into events for a pure state machine in `:core` (`EngineState`, `reduce(event)` → render commands), the same shape as Sidearm's sequencer. Rendering runs on one `HandlerThread` per engine, paced by `Choreographer`.
- States: **Awake** (photo, drawn once per change, no loop) → **Entering** (transition) → **Ambient** (one still frame, then nothing) → **Exiting** (reverse) → Awake. A surface recreated during Ambient redraws the still frame.
- Rendering: `SurfaceHolder.lockHardwareCanvas()` with an AGSL `RuntimeShader`. The photo and the outline field are `BitmapShader` children; the uniforms are `progress`, `effect`, `thickness`, `glow`, `brightness`, `tint`, `saturation` and `offset`. The same shader source drives the in-app preview through Compose's `ShaderBrush`. GLES is the fallback if the hardware canvas misbehaves while dozing. ADR in Phase 1.
- `onComputeColors()` returns colours from the photo (`WallpaperColors.fromBitmap`), so the clock and theme match, and SystemUI picks the right scrim level (it uses 0.64 instead of 0.4 when the colours carry the dark-text hint).
- Burn-in: every AOD entry takes the next of N offsets (a few px) from `:core`'s `BurnInShift`. Line brightness is capped.
- Failure: a render error logs, draws the plain photo, and never crashes the process (after repeated crashes WallpaperManagerService falls back to the default wallpaper).
- Later: `onCommand(COMMAND_LOCKSCREEN_LAYOUT_CHANGED)` gives the clock's focal area, to keep lines clear of it; `onZoomChanged` to zoom like `ImageWallpaper` does.

### 5.3 Outline pipeline (`:core`, pure Kotlin)

Runs in the app when a photo is chosen, or when a setting changes the geometry, on a background dispatcher. The engine only loads the result.

1. The Android side decodes the photo with `ImageDecoder` at the display's size (read at runtime, never hard-coded) and passes a working copy (long side about 1024–1536 px) as an `IntArray`.
2. Luminance → Gaussian → **XDoG** (soft-thresholded difference of Gaussians), or **Canny** (non-max suppression and hysteresis). Phase 1 builds both and picks a default; the other may become a "line style" option.
3. Clean-up: drop connected components below a size threshold, so texture noise doesn't sparkle.
4. Optional subject mask (Phase 4): keep edges inside a dilated mask, plus the mask's own silhouette.
5. Exact Euclidean distance transform (Felzenszwalb–Huttenlocher, linear time) of the edge map. The shader turns distance into line width and glow, so thickness and glow change live without recomputing, and the field can be stored at half resolution and still upscale smoothly.
6. A second channel holds a reveal order for the line-trace effect, such as the distance along each edge from its endpoints.
7. Cache: one packed bitmap (R = distance, G = reveal order, B = subject mask) and a small metadata file in app-private storage, keyed by photo hash and pipeline version.

Tests check the results numerically: edges of synthetic shapes, the distance transform against brute force, and an AOD frame that lights at most ~15 % of pixels with mean luminance under a cap.

### 5.4 Effects

One shader, driven by `progress` from 0 to 1 and an effect id. Each effect's default duration and easing live in `:core`.

| Effect | Photo → outline | Phase |
|---|---|---|
| Fade | The photo darkens and desaturates as the lines brighten | 1 |
| Glow dissolve | A noise-thresholded dissolve; the lines flare, then settle | 3 |
| Line trace | Lines draw themselves in reveal order while the photo fades | 3 |
| Wipe | A soft edge sweeps across the screen, leaving the outline behind it | 3 |

### 5.5 Magisk module

```
module.prop          id=afterimage; version from the catalog; updateJson from Phase 5
customize.sh         checks Android 17 and the device; refuses if an installed Afterimage is signed with another key
service.sh           after boot: remembers the old Secure value (once), sets doze_always_on_wallpaper_enabled=1;
                     from Phase 3 also grants the app WRITE_SECURE_SETTINGS so its switch works
action.sh            Magisk's action button: turn the wallpaper on AOD off or on (works even if the app is broken)
uninstall.sh         restores the remembered value; never touches doze_always_on (the user's AOD setting)
system/app/Afterimage/Afterimage.apk                            priv-app only if Phase 3 needs it
system/product/overlay/AfterimageAodConfig.apk                  static overlay on android: config_dozeSupportsAodWallpaper=true
system/etc/permissions/privapp-permissions-afterimage.xml       only with priv-app
```

- The templates and scripts live in `magisk/`. `scripts/package-module.sh <app.apk> <overlay.apk> <version>` builds the zip. It's a script rather than a Gradle task so that the release job can package already-signed APKs without running Gradle (Sidearm ADR 0009).
- Dev loop: install the module once, built from a debug APK. After that, `adb install -r` of a debug build with the same key updates the system app in place and keeps `AMBIENT_WALLPAPER`. Moving between debug and release keys means: remove the module, reboot, uninstall, install the other module.
- Lessons from DozeAodWallpaper: don't delete `doze_always_on` on uninstall (that turns AOD off), and don't kill SystemUI on every boot. Phase 0b checks whether SystemUI picks up the Secure setting live before restarting anything.
- Move to priv-app only when a privileged permission is needed (Phase 3). A CI check compares the manifest's privileged permissions with the allowlist, so a missing entry can't ship.

### 5.6 SystemUI hook

- Entry class `AfterimageModule : XposedModule`, scope `com.android.systemui`. It resolves targets in `onPackageReady` from `param.classLoader`.
- Same safety rules as Sidearm:
  - `*Targets` classes resolve everything once, all-or-nothing.
  - `ExceptionMode.PROTECTIVE` on every hook.
  - Nothing it posts to SystemUI's threads can throw.
  - No AndroidX, coroutines or other libraries in `:hook`.
  - One log line at load: active, or why not.
- Active only while our component is the lock wallpaper: `WallpaperManager.getWallpaperInfo(FLAG_LOCK)`, falling back to the system wallpaper. The result is cached and refreshed on `ACTION_WALLPAPER_CHANGED`.
- What it changes (targets in `hook-targets.md`, re-checked per build):
  - the AOD blur goes to 0
  - the scrim's max alpha goes to 0, or to a user value
  - the GONE/OCCLUDED → AOD full-black case is handled
  - Named methods and property setters are preferred over synthetic lambda classes (`$special$$inlined$…`), whose names shift between builds.
- Without the hook everything still works, with stock blur and dimming. The engine raises line brightness to make up for the 0.4 scrim.
- Phase 2 also evaluates a **scoped gate**: having SystemUI report ambient support only for our wallpaper. That could replace the overlay and leave every other wallpaper stock. ADR if adopted.

### 5.7 Settings and config

| Setting | Stored in | Read by |
|---|---|---|
| Wallpaper options (effect, speed, thickness, glow, brightness, colour, subject only, burn-in) | Jetpack DataStore in the app's storage. Keys, defaults and validation live in `:core` | The engine (same process, as a `Flow`) and the UI |
| Hook options (enabled, scrim level, blur) | Vector remote preferences, through `:core`'s codec | `:hook` in SystemUI, live |
| Wallpaper on AOD | Secure `doze_always_on_wallpaper_enabled` | SystemUI |

Stored values are untrusted: anything missing or malformed decodes to defaults (Sidearm's `ConfigCodec` pattern). The app reaches Vector the way Sidearm does: a `VectorBinderProvider` that accepts only the system and root uids, and one process-wide `VectorConnection`.

The app declares no `INTERNET` permission. Photos and outlines stay in app-private storage and are excluded from backup.

### 5.8 Budgets

Starting targets. Phase 0b and Phase 1 measurements confirm or replace them.

| Item | Target |
|---|---|
| Entry transition | ≤ 3 s (hard cap 4 s), finished before the panel enters DOZE |
| Frames during AOD | One per entry, plus a redraw if the surface is recreated |
| Wake locks, sensors, loops while ambient | None |
| Lit pixels in the AOD frame | ≤ 15 % |
| Outline precompute | ≤ 1.5 s for a full-size camera photo on the 9 Pro XL |
| AOD battery | Within a margin agreed after Phase 0b of the stock lit-wallpaper result, which is itself compared to a stock-AOD baseline kept outside the repo |

### 5.9 Alternatives rejected

| Option | Why not |
|---|---|
| Draw the outline inside SystemUI (route 2) | ADR 0002: high churn in the Compose lock screen, and a crash takes SystemUI down. Kept as the fallback |
| GLES first | More code (EGL lifecycle, surface loss), and the in-app preview couldn't share the shader |
| ML Kit for subject cut-outs | Proprietary, downloads its model through Play services, and sits awkwardly with GPL and with having no network. Decided in Phase 4 |
| Hilt, feature modules, product flavours | Same as Sidearm: constructor injection and few modules |
| The third-party DozeAodWallpaper module | We need to know exactly what changes, and it alters AOD brightness and deletes `doze_always_on` on uninstall |

## 6. Repo structure

Ported from Sidearm (`../sidearm/.claude/rules/project-structure.md`); Phase 0a writes Afterimage's own copy of that rule.

| Module | Holds | May depend on | Must not |
|---|---|---|---|
| `:core` | Pure Kotlin: `.config` (keys, defaults, codecs), `.outline` (pipeline), `.engine` (state machine), `.effect` (effect specs, timing), `.burnin` | Kotlin stdlib | Android, Xposed, coroutines, I/O |
| `:hook` | Runs in SystemUI: entry class, `.target`, `.intercept`, `.config` | `:core`, `compileOnly` libxposed api | AndroidX, Compose, coroutines, libraries; referencing `:app` |
| `:app` | The APK: manifest, `META-INF/xposed/*`, `.wallpaper` (service, engine), `.render` (renderer, AGSL), `.ui`, `.data`; debug tools in `src/debug` | `:core`, `:hook` as `runtimeOnly`, libxposed service, AndroidX, Compose, DataStore | Importing `:hook` classes or containing hook logic |
| `:overlay` | The static overlay APK: manifest and resources, no code | Nothing | Code |
| `build-logic/` | Convention plugins `afterimage.android.application`, `.android.library`, `.jvm.library` | Gradle, AGP, KGP | Product code |
| `magisk/` (not a Gradle module) | Module template and shell scripts | | |

- Add a module only when a real boundary needs one. The expected case is splitting `.render` out into `:render` when layered wallpapers arrive (Phase 10).
- Everything else follows Sidearm: one public type per file, tests next to their code in `src/test/kotlin` with fixtures in `fixture/`, every version in `gradle/libs.versions.toml`, vector drawables only, and an ADR for every decision.
- Docs: `plan.md` (this file), `architecture.md` (from Phase 1), `decisions/`, `hook-targets.md`, `testing.md` (from Phase 1), `research/`.

## 7. Phases

Until 1.0.0 the minor version is the phase number, as in Sidearm. Public releases start in Phase 5, once signing exists; earlier phases are dev builds only.

| Phase | Goal | Version | Exit |
|---|---|---|---|
| 0a | Repo foundation | — | `./gradlew check` and the hygiene check pass; the owner makes the first commit |
| 0b | Test the stock AOD-wallpaper path, no app code | — | The owner decides on looks and battery |
| 1 | Outline wallpaper MVP | 0.1.0 | Photo → outline on AOD on the phone, within budget |
| 2 | SystemUI hook | 0.2.0 | Sharp, undimmed lines from both lock paths |
| 3 | Settings app and full options | 0.3.0 | Samsung's options, plus status checks and a switch |
| 4 | Subject only and smarter outlines | 0.4.0 | Clean subject-only outlines, offline |
| 5 | CI and signed releases | 0.5.0 | First public release, installed from the zip |
| 6 | Soak | 1.0.0 | Two weeks of daily use across a monthly OTA |
| 7–10 | Later scope | 1.x | One ADR and budget each |

### Phase 0a: repo foundation
- Owner decisions (2026-11-14): GPL-3.0-or-later (ADR 0004), `develop` + `main` (ADR 0005), ECC like Sidearm (ADR 0006).
- Done: `CLAUDE.md` no longer names the owner's installed apps or settings; those stay in `.claude/private/environment.md`.
- Done in the working tree: everything below except the git steps. `scripts/dev-install.sh` waits for `:app` (Phase 1), and `release.yml` / `sign-apk.sh` for Phase 5. `:core` holds one real class, `BurnInShift` (ADR 0003), so the convention plugin, tests and Kover gate run end to end.
- Port, renaming `sidearm` to `afterimage`:
  - `.claude/settings.json` and the full rules
  - `.claude/rules/project-structure.md` (from §6)
  - `.editorconfig`, `.gitattributes`
  - `.githooks/`
  - the scripts: `check-repo-hygiene.sh`, `check-conventions.sh`, `setup-repo.sh`, `start-branch.sh`, `dev-install.sh`, `sync-ecc.sh`
  - `CONTRIBUTING.md`, `LICENSE`, `THIRD_PARTY_NOTICES.md`, `CHANGELOG.md`
  - the CI workflows for PRs and pushes (they run once the GitHub repo exists)
- Gradle skeleton:
  - `build-logic/` with the three convention plugins
  - the version catalog, with Sidearm's versions (AGP 9.5.0, Kotlin 2.4.21, Gradle 9.8.2, compile/min/target SDK 37)
  - the checksum-pinned wrapper, Spotless, Kover
  - `:core` with one test, so `./gradlew check` runs end to end
- Git steps, each on the owner's explicit instruction: `git init -b main`; repo-local `user.email` (GitHub noreply) and `bash scripts/setup-repo.sh` (hooks); add the ECC submodule at `91b5f6a` and run `scripts/sync-ecc.sh`; the first commit on `main`; create `develop` from it. Creating the GitHub repo and pushing are separate instructions.

### Phase 0b: test the stock path
- Build:
  - `:overlay` (static overlay on `android`, `config_dozeSupportsAodWallpaper=true`)
  - `magisk/` with `service.sh`, `uninstall.sh` and `action.sh`
  - `scripts/package-module.sh`
  - This module carries no app. Check that AGP builds a code-less overlay module; otherwise fall back to an `aapt2` + `apksigner` script.
- Already checked read-only (2026-11-14, `hook-targets.md`): the privapp allowlist is enforced; SystemUI still has the `wallpaper_dimming_scrim_array` key; the `NotificationShadeDepthController` dumpable prints `wakeAndUnlockBlurRadius`.
- The owner installs the module from the Magisk app and reboots (a phone change: ask first). Then:
  - `probe-device.sh` shows the gate open.
  - Lock from the lock screen and from inside an app: how do the stock blurred, dimmed wallpaper and the full-black case look on this panel?
  - Does Settings now show Google's "show lock screen wallpaper" AOD toggle? If so, it can be the off switch.
  - Do Magic Portrait / Photo Shuffle appear in the picker, and how do they draw on AOD?
  - Read the blur radius from SystemUI's dumpables during AOD (open question 1).
  - With "Allow window-level blurs" off, are the lines sharp?
  - Does a zeroed `wallpaper_dimming_scrim_array` remove the light-sensor dimming?
- Battery: one unplugged evening on AOD, with no USB or wireless adb attached. Compare against the baseline in `.claude/private/environment.md`.
- Exit: the owner decides whether an AOD-lit wallpaper is acceptable for battery and looks. ADR 0002 becomes Accepted, or the project switches to route 2.

### Phase 1: outline wallpaper MVP
- **1a, `:core`:**
  - The outline pipeline (XDoG and Canny, clean-up, distance transform, reveal order), the engine state machine, burn-in shift and the config codec, fully unit-tested.
  - An "outline lab": a JVM command run through Gradle that takes an image path and writes the outline to a scratch folder, for tuning on the PC. Photos and outputs never enter the repo.
- **1b, engine and a minimal app:**
  - The ambient service and component sync, the render thread, the Fade effect, burn-in, `onComputeColors`, and the display-state fallback.
  - One screen: pick a photo with the system Photo Picker (no storage permission), compute the outline, and set the wallpaper through `ACTION_CHANGE_LIVE_WALLPAPER`.
- **1c, on the phone:**
  - The module now carries the APK in `/system/app`. After that, iterate with `scripts/dev-install.sh`.
  - Log under the tag `Afterimage`: timestamps of the ambient callbacks, display-state changes and frames drawn. This measures the real entry window.
  - Watch the stock blur and scrim with no hooks, and tune the brightness compensation.
- ADRs: one APK for app, wallpaper and hook; AGSL on the hardware canvas.
- Exit: on the phone, the photo becomes an outline on AOD when locking from the lock screen. No frames are drawn after the AOD frame, and battery stays within budget. `docs/architecture.md` and `docs/testing.md` are written.

### Phase 2: SystemUI hook
- Disassemble the candidate classes on the current build into the scratchpad, and record the exact members in `hook-targets.md`.
- Build `:hook`:
  - the entry class, `*Targets`, interceptors, and the lock-wallpaper check
  - unit tests with fixture stand-ins for the SystemUI classes, as in Sidearm
- Find out whether Vector hot-reloads modules in SystemUI. If it doesn't, the dev loop is install plus reboot.
- Evaluate the scoped gate (§5.6) and write an ADR if adopted.
- Exit:
  - Sharp, undimmed lines when locking from the lock screen and from inside an app.
  - With the hook disabled, stock behaviour.
  - No SystemUI crash in a day of use.

### Phase 3: settings app and full options
- The app, in Compose and Material 3, matching Sidearm's look:
  - a live preview with a progress slider (the same shader)
  - every v1 option
  - a status card with checks: module installed, system app, `AMBIENT_WALLPAPER`, gate, Secure setting, hook loaded in SystemUI, tested build
  - the AOD-wallpaper switch
- The remaining effects: Glow dissolve, Line trace, Wipe. Plus speed, colour/tint and grayscale.
- Lock-only wallpaper:
  - Use the picker if it offers lock-only for live wallpapers.
  - Otherwise move to priv-app with an allowlist and call `setWallpaperComponentWithFlags(…, FLAG_LOCK)`.
  - Optionally, with `READ_WALLPAPER_INTERNAL`, import the current wallpaper.
- A debug-only `DevActivity` (needs `DUMP`, like Sidearm's) sets options from adb and pins the effect at a given progress.
- Exit: Samsung's options all work, and every manual check in `testing.md` passes.

### Phase 4: subject only and smarter outlines
- On-device segmentation with a bundled, openly licensed model (MediaPipe or a U²-Net-class model). Decided in an ADR that covers license, size and speed.
- A mask brush to erase stray lines or keep missed ones.
- Per-photo automatic detail tuning, aiming at a target edge density.
- Keep lines clear of the clock through the focal area.
- Use the depth map in Pixel portrait photos, if one is there.
- Exit: clean subject-only outlines for people and pets, with no network access.

### Phase 5: CI and signed releases
- Port Sidearm's `release.yml` / `build.yml` / `sign-apk.sh` (ADR 0009):
  - Build both APKs unsigned.
  - Sign them in a job that runs no Gradle.
  - Package the module zip with `package-module.sh`.
  - Publish the zip, the APK and SHA-256 sums.
- Add Magisk's `updateJson` so the Magisk app offers updates.
- README: install, uninstall, recovery. CHANGELOG.
- Exit: v0.5.0 is published, and installed from its zip on the phone.

### Phase 6: soak
- At least two weeks of daily use, spanning one monthly OTA.
- Post-OTA checklist: re-run the probe, re-verify the hook targets, add the build to the tested builds.
- Review burn-in and battery, and fix what turns up.
- Exit: v1.0.0.

### Later scope (after 1.0)

| Phase | Feature | Notes |
|---|---|---|
| 7 | More lock↔AOD transitions (pixelate, CRT, blur, slice, hexagon) and wake-only effects | Same shader framework |
| 8 | Charging mode: a slow glow or "breathing" outline on AOD while plugged in | Battery isn't the constraint while charging; still capped for burn-in |
| 9 | Photo rotation: a set of photos, the next one per day or per unlock, outlines precomputed | Like Photo Shuffle |
| 10 | Wonderland-style layered wallpapers: image, text and particle layers, gyro parallax on lock and home, scene import/export | Sensors only while awake; still frame on AOD; `.render` becomes `:render` |
| — (maybe) | Separate AOD clock faces through a SystemUI clock plugin; stickers/images on AOD; other Pixels (the Pixel 10 Pro opens the gate natively) | Plugin loading needs hooks (`research/aosp-internals.md` §4) |

## 8. Testing

Details move into `docs/testing.md` in Phase 1.

- **Automated** (`./gradlew check`):
  - `:core` unit tests, with an 80 % Kover gate, as in Sidearm
  - `:hook` tests with fixture stand-ins
  - `:app` ViewModel tests with fakes
  - lint and Spotless
- **Device matrix** (manual, every release and OTA):
  - lock from the lock screen, from an app, and by timeout
  - notification pulse
  - fingerprint unlock from AOD
  - tap/lift to wake
  - charging
  - Battery Saver (AOD off)
  - changing the wallpaper
  - reboot
  - module disabled, hook disabled
- **Screen control:** `KEYCODE_SLEEP` / `KEYCODE_WAKEUP` only, never `KEYCODE_POWER`.
- **Battery:** reset batterystats, run unplugged with no adb, and compare screen-doze discharge per hour.
- Screenshots and logs from the phone stay on the PC (rules §3).

## 9. Risks

| Risk | Mitigation |
|---|---|
| A QPR or monthly update moves SystemUI internals | All-or-nothing targets, per-build `hook-targets.md`, degrade to stock blur and dim (still visible), a tested-builds check in the app |
| Google removes or renames the AOD-wallpaper gate | Probe each OTA; route 2 is the fallback |
| Battery cost of a lit AOD wallpaper | Mostly black frames on OLED, a lit-pixel cap, no loops; measured in 0b, 1 and 6 |
| Burn-in from a static outline | A shift on every entry, a brightness cap, thin lines |
| Bootloop from a missing privapp allowlist entry | Stay in `/system/app` until a privileged permission is needed; a CI check; recovery steps (§10) |
| Signing key mismatch between the system copy and an update | `customize.sh` refuses to install; documented dev-loop steps |
| Hidden callback removed or renamed | Display-state fallback |
| libxposed or Vector changes | `compileOnly`, pinned versions; read release notes before updating Vector (Sidearm ADR 0003) |
| SystemUI crash from the hook | `PROTECTIVE` mode, nothing thrown on SystemUI threads, all-or-nothing install |

## 10. Recovery

- **Phone won't boot after installing the module:** Magisk disables all modules when Android boots in safe mode (hold volume-down while the boot animation plays). Remove Afterimage in the Magisk app and reboot. Check that this works before the first risky install (priv-app, Phase 3).
- **SystemUI keeps crashing:** disable Afterimage in the Vector manager and reboot. If the UI is unusable, use safe mode as above, which also stops Zygisk Next and Vector.
- **Wallpaper looks wrong or costs too much battery:** turn the wallpaper on AOD off with the module's action button or the app's switch, or pick another wallpaper.

## 11. Open questions

| # | Question | Answered in |
|---|---|---|
| 1 | Does the scene-container lock screen still blur through `NotificationShadeDepthController`, or does `WindowRootViewBlurInteractor` own AOD blur now? | 0b |
| 2 | After locking from an app, does the scrim stay at 1.0 for the whole AOD session or only during the transition? | 0b |
| 3 | Does zeroing `wallpaper_dimming_scrim_array` remove the light-sensor dimming? (The key exists on CP4A; the allowlist is enforced.) | 0b |
| 4 | Does Settings show Google's AOD-wallpaper toggle once the framework bool is true? | 0b |
| 5 | When does `setInAmbientMode(true)` arrive relative to the panel entering DOZE, and what `animationDuration` comes with it? | 1 |
| 6 | Can a live wallpaper be set for the lock screen only through the Pixel picker, or does it need `SET_WALLPAPER_COMPONENT`? | 3 |
| 7 | Does Vector hot-reload modules inside SystemUI? | 2 |
| 8 | Overlay gate or a SystemUI-side scoped gate? | 2 |
| 9 | One APK or two? (Plan: one.) | 1 |
| 10 | License, ECC, branch model | 0a |
