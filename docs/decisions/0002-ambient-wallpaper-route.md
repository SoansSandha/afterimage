# 0002: Draw the outline in a system-installed ambient live wallpaper

- Status: Proposed (2026-11-14). Confirm with the owner after Phase 0b.

## Context

The outline must appear on AOD and animate in when the phone locks. Two ways to get pixels onto the Pixel's AOD with root:

1. **Ambient live wallpaper.** A live wallpaper that declares `supportsAmbientMode` stays visible on AOD and gets `onAmbientModeChanged(inAmbientMode, animationDuration)`. Needs `AMBIENT_WALLPAPER` (`signature|preinstalled`), so the app must be installed as a system app (Magisk). SystemUI only reveals it when the stock AOD-wallpaper gate is on (framework bool + Secure setting), and then blurs and dims it. Google's own Magic Portrait and Photo Shuffle wallpapers use this path on CP4A. Wonderland itself is a live wallpaper.
2. **Draw inside SystemUI.** A Vector hook adds a full-screen view behind the AOD clock showing a precomputed outline of the current wallpaper (how Iconify and PixelXpert do depth wallpapers). Works with any wallpaper and needs no gate, but it lives in SystemUI's Compose lock screen, which changes every quarterly release, and a crash there takes down SystemUI.

## Decision

Use route 1: the outline is rendered by our own live wallpaper, installed as a system app by our Magisk module, with the stock gate turned on. A small Vector hook in SystemUI only removes the AOD blur and scrim while our wallpaper is active. Route 2 is the fallback if the blur/scrim or the GONE→AOD full-black case can't be dealt with cleanly.

## Consequences

- Most logic sits behind the stable `WallpaperService` API, not SystemUI internals, so QPR updates should break less.
- Our wallpaper must be the lock wallpaper; users can't keep an arbitrary stock wallpaper with the effect (route 2 could).
- Turning the gate on changes AOD for every wallpaper (stock blurred look), so the module needs an easy off switch.
- The same wallpaper engine is the base for later Wonderland-style layers.
