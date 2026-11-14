# 0003: Animate only on AOD entry and exit; hold one still frame during AOD

- Status: Proposed (2026-11-14)

## Context

During AOD, SystemUI redraws once a minute and the CPU sleeps in between. On the Pixel 9 Pro XL, `doze_suspend_display_state_supported` is false, so AOD runs in `Display.STATE_DOZE` and frames drawn during AOD do reach the panel. But anything that animates keeps the CPU and GPU awake. SystemUI holds a wake lock for up to `ENTER_DOZE_DELAY` (4000 ms) while it animates into AOD. Samsung keeps AOD still too: GIFs play once or twice and freeze, and Wonderland's transitions are short. A static outline held for hours is also a burn-in risk, and SystemUI's burn-in shift (`BurnInHelper`) moves only its own content, not the wallpaper.

## Decision

- The photo → outline transition runs once on AOD entry, within the doze-entry window (target ≤ 3 s, hard cap 4 s), and the reverse runs on wake.
- During AOD the wallpaper holds one still frame. No continuous or looping animation, and no sensor listeners while ambient.
- The outline shifts by a few pixels on every AOD entry (burn-in), and its brightness is capped.

## Consequences

- Battery cost should be close to a static AOD wallpaper; Phase 0b measures the baseline and the stock lit-wallpaper cost.
- Effects that need motion during AOD (particles, slow pans) are out of scope unless measurements show a short burst on notification pulses is cheap.
