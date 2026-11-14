# 0001: Name the project Afterimage; v1 is the AOD outline effect

- Status: Accepted (owner, 2026-11-14)

## Context

The owner wants Samsung Good Lock Wonderland's edge/outline lock-screen effect on a rooted Pixel 9 Pro XL, and liked Wonderland and Galaxy AOD customisation more broadly. The project will be a public GitHub repo, like Sidearm. Candidate names checked on GitHub (2026-11-14): Afterimage (no Kotlin repos with that name; one small unrelated APK-release repo), Linework, Nightline, Tracer (12 Kotlin repos), Afterglow (7), Contour (Cash App's layout library, 1.5k stars).

## Decision

- Name: **Afterimage**: the image that lingers after you look away, as the wallpaper lingers as an outline on AOD. Folder `afterimage`, applicationId `io.github.soanssandha.afterimage`, repo `SoansSandha/afterimage`.
- Keep Samsung's and Google's trademarks (Wonderland, Good Lock, Galaxy, Pixel) out of the name. The README may say it's inspired by Wonderland's effect and isn't affiliated.
- v1 scope is the outline effect. Wonderland-style layers, clock plugins and AOD extras are later scope (`../plan.md` §2), and the name is broad enough to cover them.

## Consequences

- If later scope never happens, the name still fits the single feature.
- Docs must describe Samsung features without implying affiliation.
