# Samsung research: Wonderland, Galaxy AOD, LockStar, ClockFace

Researched 2026-11-14 (One UI 8 / 8.5 era) from SamMobile, SammyGuru, Android Authority, 9to5Google, XDA and Samsung's own support and newsroom pages. APKMirror and SammyFans refused automated fetches, so a few details come from search snippets only. **Unconfirmed** marks things without a primary source.

Afterimage is an independent project and is not affiliated with Samsung. Samsung, Galaxy, Good Lock, Wonderland, LockStar and ClockFace are Samsung's names, used here only to describe what we studied.

## The effect Afterimage recreates

The owner's notes on Wonderland's edge/outline effect (via SammyFans):
1. Install Good Lock from the Galaxy Store, then Wonderland from inside it.
2. In Wonderland, tap "Create your different lock screen effect" and choose a transition wallpaper.
3. Pick the edge/outline style. Tap Edit before "Set as wallpaper" to adjust **effect type, speed, thickness, brightness and grayscale**.
4. Apply it to the lock screen with Always On Display on.
5. A photo with strong, clear edges (a subject against a plain background) gives the cleanest outline.

## 1. Wonderland (package `com.samsung.android.wonderland.wallpaper`)

| Feature | What it does | Where | Mechanism / notes |
|---|---|---|---|
| Background | Gallery photo, solid colour, or a gradient of up to 3 colours with an angle | Home, lock | — |
| Layer stack | Image (gallery, presets, stickers), text, video, particle and particle-preset layers; each resizable, rotatable, movable, with transparency | Home, lock | Early versions: background + 2 image layers. Current maximum **unconfirmed**. 1.1.07 added text layers and per-layer motion |
| Motion | A general motion setting plus optional per-layer motion; separate X, Y and Z (zoom/depth) amounts; negative values reverse direction; responds to tilt and touch | Home, lock | Gyroscope parallax. Depth comes from per-layer motion amounts, not true 3D. 1.6.22 fixed tilt direction after rotation |
| Auto motion | Particles can "Auto Play" or react to touch | — | Whether non-particle layers can move on their own is **unconfirmed** |
| Particles | Snow, rain, bubbles, stars, petals, sparks, bokeh, leaves, or a gallery image as the sprite; colour style, size, duration, rotation, amount, spread direction; combinable | Home, lock | Sprite particle system |
| 3D portrait | Uses the depth map from Galaxy Camera portrait photos so the subject floats in front | Home, lock | Restored in 1.6.19 (May 2026); 1.6.22 (Aug 2026) added support for photos from the latest camera app |
| AI / generative | None found | — | Subject separation relies on camera depth data, not segmentation |
| Lock-screen transitions | Screen-on and AOD↔lock effects: CRT, Pixelate, Wrap, Blur, Slide, Fluid, Hexagon, Slice, Flip, with speed and depth; vertical/horizontal blur (1.6.15/16, One UI 8). The owner's notes add an edge/outline style (see above) | Lock ↔ AOD | Added in 1.3.01 (2023); AOD→lock in 1.4.05; "Fullscreen AOD support" on S24 in 1.5.05. "12 effects" per one changelog snippet (**unconfirmed**). 1.5.05 fixed a conflict with LockStar, suggesting both touch the same lock-screen layer (inference) |
| How it is applied | Saved as a live wallpaper; home and lock can differ (Samsung newsroom, Apr 2025) | Home, lock | Android live-wallpaper service. Whether the lock screen uses a Samsung-only path is **unconfirmed** |
| Export / share | 2021 guides: can't save to Gallery or share. 1.2.29 mentions sharing via Galaxy To Share. Backup via Smart Switch | — | No documented file format |
| Battery | 1.6.16 (Sep 2025): "moving wallpapers consume less power". Video layers, many particles and large XYZ values cost most | — | Idle/low-battery throttling claim is **unconfirmed** |

## 2. Galaxy AOD (stock)

| Feature | What it does | Notes |
|---|---|---|
| When to show | Auto (hides in dark/pocket), Always, Tap to show, Scheduled, New notifications | Proximity, RGB light and accelerometer sensors |
| Clock | Since One UI 6.1, AOD uses the lock-screen clock style; only the colour is separate | Separate AOD clock styles, including image/GIF clocks, removed in 6.1+ |
| GIF (One UI 6.0 and earlier) | GIFs under 5 s play twice; longer ones once, then freeze on one frame | Animation is a short burst on AOD entry, then static |
| Show lock screen wallpaper | Full-screen wallpaper, dimmed, in colour | S24/S25 (One UI 6.1+); older models show it in a small window |
| Erase background | Shows only the detected subject (person/animal) | S24/S25; greyed out without a subject; 9to5Google found it unreliable |
| Now Bar | Live-activity pill on AOD and lock | S25 / One UI 7+ |
| Brightness | No user control from One UI 6.1 | LockStar adds an override |
| Adaptive clock (One UI 8) | Clock reshapes around the wallpaper subject (lock) | AOD fallback **unconfirmed** |
| One UI 8.5 | Wake/sleep animation starts or ends at the double-tap point | — |

## 3. Good Lock modules that touch AOD and the lock screen

| Module | What it does | Notes |
|---|---|---|
| LockStar: layout | Drag clock, date, notifications, shortcuts and widgets anywhere; swap or hide shortcuts; notifications as icons or full; add widgets and a free Text item (One UI 8.0); widget corner rounding | From One UI 8.0 the AOD layout is synced to the lock layout |
| LockStar: AOD brightness | Manual override (non-full-screen-AOD devices on 8.0; full-screen-AOD devices on 8.5) | — |
| LockStar: unlock animations | One UI 8 fingerprint effects; One UI 8.5 Slide, Expand, Spread, Wave, Warp, Ripple, Swirl with shape/colour/duration/interpolator | — |
| ClockFace | Separate clocks for lock and AOD where supported; analog, digital and word clocks; font, weight, size, colour, hands, dial; custom text, stickers, images/GIFs with frames; weather; Galaxy Watch-face presets | Whether GIFs animate on AOD is **unconfirmed** (one guide says they pause) |

## 4. Battery and burn-in constraints Samsung imposes

- Simple AOD content ran in a reduced-colour mode on the S7, with a lower refresh rate and a display-driver power-saving algorithm (Samsung newsroom, 2016).
- Owners report AOD at a 24 Hz floor on S23/S24 Ultra; 1 Hz AOD is **unconfirmed**.
- Full-screen AOD wallpaper is limited to S24 and later, reportedly for panel efficiency; S24 AOD is noticeably dimmer.
- AOD content shifts slightly over time for burn-in (Samsung support).
- AOD turns off in power saving mode and when sensors detect a pocket or darkness.

**Takeaway for Afterimage:** Samsung keeps AOD mostly still. Animation is a short burst on entry (GIFs play once or twice), and a lit wallpaper is limited to newer, more efficient panels.

## Sources

- https://sammyguru.com/samsung-wonderland-complete-guide/
- https://www.sammobile.com/news/new-good-lock-module-wonderland-create-custom-live-wallpapers/
- https://www.xda-developers.com/samsung-wonderland-new-good-lock-module-create-live-wallpapers
- https://www.androidauthority.com/samsung-wonderland-live-wallpaper-1156984/
- https://www.samsung.com/hk_en/news/product/exploring-good-lock-3-3-features-recommended-by-samsung-developers-and-newsroom-editors
- https://sammobile.com/news/good-lock-wonderland-pentastic-updates
- https://sammobile.com/news/wonderland-gets-new-features-galaxy-s24
- https://samlover.com/2025/08/14/good-lock-wonderlands-latest-update-adds-blur-effects-for-lock-screen-transitions/
- https://sammyguru.com/wonderland-1-6-16-update-boosts-battery-efficiency-for-dynamic-wallpapers/
- https://www.sammobile.com/news/new-samsung-wonderland-update-fixes-many-bugs/
- https://sammyguru.com/samsung-wonderland-update-3d-effects/
- https://www.samsung.com/us/support/answer/ANS10001976/
- https://9to5google.com/2024/01/17/samsung-always-on-display-iphone
- https://sammobile.com/news/no-full-screen-always-on-display-wallpapers-one-ui-6-1-update
- https://sammobile.com/news/always-on-display-looks-off-galaxy-s24
- https://www.sammobile.com/news/one-ui-8-5-adds-subtle-new-animation-to-always-on-display
- https://www.sammobile.com/news/galaxy-s25-one-ui-8-beta-5-adaptive-clock/
- https://www.androidauthority.com/samsung-good-lock-lockstar-one-ui-8-3580942/
- https://www.androidauthority.com/samsung-lockstar-custom-unlock-animations-3632333/
- https://www.sammobile.com/news/new-lockstar-update-screen-unlock-animation-effects-one-ui-8-5/
- https://www.sammobile.com/news/new-lockstar-update-brings-changes-for-one-ui-8-0-one-ui-8-5/
- https://sammobile.com/news/good-lock-clockface-module-more-customization
- https://www.androidauthority.com/samsung-galaxy-clockface-update-new-styles-3563281/
- https://www.sammobile.com/news/samsung-clockface-hands-on-ultimate-lock-screen-upgrade/
- https://www.samsung.com/ph/support/mobile-devices/what-is-always-on-display-aod-and-how-to-use-it/
- https://news.samsung.com/global/in-depth-look-keeping-you-always-on-track-the-always-on-display-of-the-galaxy-s7-and-galaxy-s7-ed
