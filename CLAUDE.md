# Afterimage

Afterimage is a root project for the Google Pixel 9 Pro XL. When the phone locks into the always-on display (AOD), the lock-screen wallpaper turns into a glowing line outline of itself. The idea comes from the edge/outline lock-screen transition in Samsung's Good Lock "Wonderland" module. Later scope may grow into Wonderland-style layered wallpapers and other AOD extras (`docs/plan.md` §2, §7). The app is named Afterimage, with applicationId `io.github.soanssandha.afterimage`, to be published at `github.com/SoansSandha/afterimage` (a **public** repo, not created yet).

Status (2026-11-14): Phase 0a is done. The repo is public on GitHub with `main` and `develop`, Sidearm's infrastructure, the Gradle skeleton and ECC (GPL-3.0-or-later, `develop` + `main`, ECC: ADRs 0004–0006); GitHub rulesets and settings are still to do. Phase 0b (the `:overlay` module, the Magisk module in `magisk/`, `scripts/package-module.sh`) is merged, installed on the phone and checked: the gate opens live, the stock AOD wallpaper is blurred (radius 51, a window blur) and dimmed (0.4 scrim), locking from an app is black for ~1 s only, and the owner finds the look acceptable (results in `docs/hook-targets.md` and `docs/plan.md` §4, §7). Still open: the overnight battery run, then removal and ADR 0002's status. Next: Phase 1a, the `:core` outline pipeline and a PC outline lab (`docs/plan.md` §7).

## Read first

| File | What's in it |
|---|---|
| `docs/plan.md` | Goal, design (how Magisk, Vector and the app fit), repo structure, phases, testing, risks, recovery, open questions |
| `docs/hook-targets.md` | What was verified on the phone's build (CP4A), and how to re-check after an OTA |
| `docs/decisions/` | ADRs: name, route, AOD animation budget, license, branching, ECC |
| `docs/research/samsung.md` | What Wonderland, LockStar, ClockFace and Galaxy AOD actually do |
| `docs/research/aosp-internals.md` | Android 17 source findings: ambient wallpapers, doze, keyguard layout, clock plugins |
| `docs/research/pixel-prior-art.md` | Stock Pixel features and existing modules/apps (Iconify, PixelXpert, DozeAodWallpaper, DepthWall, ...) |
| `.claude/rules/git-and-privacy.md` | Loads every conversation. Public repo: git permissions, what may and may not be committed |
| `.claude/rules/project-structure.md` | Loads every conversation. Modules, packages, where every kind of file goes |
| `CONTRIBUTING.md` | Branches, commit messages, PRs, versions, releases, playbook |

## Repo rules (read before any git action)

`.claude/rules/git-and-privacy.md` applies in every conversation. In short:
- Never `git init`, commit, push, create the GitHub repo, or do any other GitHub write without the owner's explicit instruction naming that action. After making changes, stage them (once a repo exists) and stop.
- Never commit secrets, photos, or anything specific to the owner's phone or PC. Personal notes go in `.claude/private/` (git-ignored).
- Before asking to commit or push, run `bash scripts/check-repo-hygiene.sh` and the pre-flight in §6 of the rules.
- Where code, docs and scripts go, and what may depend on what: `.claude/rules/project-structure.md`.
- Branches, commits, PRs and releases follow `CONTRIBUTING.md`: topic branches off `develop`, squash-merged; Conventional Commits; never commit on `main` or `develop` (except the first scaffold commit on `main`). Claude may run `bash scripts/start-branch.sh` only with the owner's OK until the owner grants the standing permission (rules §1).

After `git init` (or a fresh clone), run `bash scripts/setup-repo.sh` once. It turns on the hygiene hooks (pre-commit, commit-msg, pre-push) and says if the commit email isn't the GitHub noreply address, or if `.claude/private/denylist.txt` is missing.

## Target platform

- Pixel 9 Pro XL (`komodo`), Android 17 QPR2, build CP4A.261105.040.A1 (SDK 37), checked 2026-11-14. The lock screen on this build runs on SystemUI's Compose "scene container".
- Root: [Magisk](https://github.com/topjohnwu/Magisk) with [Zygisk Next](https://github.com/Dr-TSNG/ZygiskNext). Hooks: [Vector](https://github.com/JingMatrix/Vector) (JingMatrix's successor to LSPosed) using the modern [libxposed API](https://github.com/libxposed/api) 102 ([example module](https://github.com/libxposed/example)). SystemUI hooks resolve their targets in `onPackageReady`. Tested versions are in `.claude/private/environment.md`.
- The planned parts (`docs/plan.md` §5):
  1. **Outline wallpaper**: a live wallpaper that declares `supportsAmbientMode`. Android only binds such a wallpaper if it holds `AMBIENT_WALLPAPER` (`signature|preinstalled`), so it must be on the system image (`/system/app` is enough).
  2. **Magisk module**: installs the app as a system app, adds a static overlay with `config_dozeSupportsAodWallpaper=true`, sets Secure `doze_always_on_wallpaper_enabled=1`, and restores it on uninstall.
  3. **Vector module** scoped to `com.android.systemui`: removes SystemUI's AOD blur and black scrim while our wallpaper is the lock wallpaper.
- The build enforces the privapp-permissions allowlist: installing under `priv-app` without a complete allowlist stops the phone from booting (`docs/hook-targets.md`).

## This machine

Notes about the dev phone and PC live outside git and are imported here when present:

@.claude/private/environment.md

## Sibling project: Sidearm

Sidearm (`..\sidearm`, `github.com/SoansSandha/sidearm`) is the owner's other Vector module for the same phone model, built in Oct–Nov 2026. It is the template for this repo: Gradle `build-logic/` convention plugins, the version catalog, `:core`/`:hook`/`:app` module boundaries, ADRs in `docs/decisions/`, `docs/hook-targets.md` per build, hygiene scripts and git hooks, CONTRIBUTING, CI and the signed-release workflow (ported in Phase 5), and vendored ECC tooling. Don't modify Sidearm from this project unless the owner asks.

## Build

- Build and verify: `./gradlew check` runs Spotless (ktlint), lint (once there are Android modules), unit tests and the Kover gate on `:core` (80 % line and branch). `./gradlew spotlessApply` fixes formatting.
- Versions are pinned in `gradle/libs.versions.toml` and kept current by Dependabot (PRs into `develop`), so they can run ahead of Sidearm's: AGP 9.5.1, Kotlin 2.4.21, Gradle 9.9.0 (wrapper checksum-pinned), compile/min/target SDK 37. Dependabot titles for a single-update group exceed the 72-character limit; shorten them before squash-merging. AGP 9 has built-in Kotlin, so never apply `org.jetbrains.kotlin.android`. The app version is `afterimage` in the catalog.
- Modules so far: `:core` (pure Kotlin) and `:overlay` (the static framework overlay, resources only). Phases add `:app` (1) and `:hook` (2); see `.claude/rules/project-structure.md`.
- Magisk module: `./gradlew :overlay:assembleDebug`, then `bash scripts/package-module.sh --overlay overlay/build/outputs/apk/debug/overlay-debug.apk` writes `build/outputs/magisk/afterimage-v<version>.zip`. Installing, removing and recovering it, and the on-phone checks: `docs/testing.md`.

## Dev environment and adb

- Same toolchain as Sidearm: Android Studio Rabbit 2 (2026.2.2) or newer, its bundled JBR (JDK 25) as `JAVA_HOME`, SDK build-tools 36.0.0 (`dexdump`, `aapt2`).
- On Windows, PowerShell 5.1 mangles `adb shell` commands that contain quotes or pipes, so run those from Bash. In Git Bash, prefix commands that take a device path with `MSYS_NO_PATHCONV=1`.
- Git on Windows doesn't record the executable bit: stage new scripts with `git add --chmod=+x <file>`, or CI fails with "Permission denied" (it did once, PR #3).
- Windows' `python` is a Store placeholder that hangs waiting for input; don't call it from scripts.
- The dev loop doesn't assume adb root. `aflags` needs root, so read SystemUI flags and state from `dumpsys activity service com.android.systemui/.SystemUIService <Dumpable>` instead (`docs/hook-targets.md`).
- Read-only device checks: `bash scripts/probe-device.sh` (add `--wallpapers` to also scan live wallpapers for AOD support).
- To lock or wake the phone in tests, use `adb shell input keyevent KEYCODE_SLEEP` / `KEYCODE_WAKEUP`. **Never inject `KEYCODE_POWER` presses**: repeated power presses can trigger other power-button features, including Emergency SOS (`.claude/private/environment.md` has this phone's details).
- Ask the owner before anything that changes the phone (installing, flashing, `settings put`, rebooting).
- Throwaway output (pulled APKs, dexdump output, logs, outline-lab images) goes in the session scratchpad, never in the repo.

## Tooling: ECC

[ECC](https://github.com/affaan-m/ECC) will be vendored as a shallow git submodule at `third_party/ecc`, pinned to `91b5f6a` (v2.2.3), the same as Sidearm (ADR 0006). It's added right after `git init`. `scripts/sync-ecc.sh` then copies an Android/Kotlin/Java subset into `.claude/`:
- Rules: `.claude/rules/ecc/{common,kotlin,java}`. `.claude/rules/git-and-privacy.md` wins where they conflict.
- Agents: planner, architect, tdd-guide, code-reviewer, security-reviewer, build-error-resolver, kotlin-reviewer, kotlin-build-resolver, java-reviewer, java-build-resolver.
- Skills: android-clean-architecture, kotlin-patterns, kotlin-coroutines-flows, kotlin-testing, java-coding-standards, coding-standards, error-handling, security-review, git-workflow, verification-loop.
- Not installed: ECC hooks (they need Node.js) and MCP configs. Never edit the synced copies by hand; project rules go in `.claude/rules/` outside `ecc/`.
- Update ECC: `git submodule update --remote third_party/ecc && bash scripts/sync-ecc.sh`. Fresh clone: `git clone --recurse-submodules <url>`.
