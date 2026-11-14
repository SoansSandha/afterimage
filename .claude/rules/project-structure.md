# Project structure rules

These rules load in every conversation. They say where things go, so the repo stays clean as phases add code. The target layout and the phase in which each part arrives are in `docs/plan.md` §6–7. To deviate, update both files first, with the owner's OK. Ported from Sidearm (`../sidearm/.claude/rules/project-structure.md`).

## Modules and their boundaries

| Module | Arrives | Holds | May depend on | Must not |
|---|---|---|---|---|
| `:core` | 0a | Pure Kotlin: the config contract, the outline pipeline, the engine state machine, effect specs and timing, burn-in shift | Kotlin stdlib only | Android, Xposed, coroutines, I/O |
| `:overlay` | 0b | The static overlay APK that opens the AOD-wallpaper gate: manifest and resources only | Nothing | Code |
| `:app` | 1 | The APK: manifest, `META-INF/xposed/*`, the wallpaper service and engine, the renderer and shaders, the settings UI, the data layer | `:core`, `:hook` as `runtimeOnly` (packaged but not importable), libxposed service, AndroidX, Compose, DataStore | Importing `:hook` classes, or containing hook logic |
| `:hook` | 2 | Everything that runs inside SystemUI: the entry class, interceptors, SystemUI targets, the remote-config reader | `:core`, and `compileOnly` libxposed api | AndroidX, Compose, coroutines, or any library (each one gets loaded into SystemUI). Must never reference `:app` |
| `build-logic/` | 0a | Convention plugins shared by the modules | Gradle, AGP, KGP and Kover APIs | Product code |
| `magisk/` | 0b | The Magisk module template and its shell scripts. Not a Gradle module | | Binaries; built APKs go in only when `scripts/package-module.sh` zips them |

- The config contract lives only in `:core`. The app writes through it and the engine and hook read through it. Never write a preference key string anywhere else.
- Add a module only when a real boundary needs one, and update plan §6 first. The expected case: splitting `app`'s `.render` package into `:render` when layered wallpapers arrive (Phase 10).
- Not used here, by decision: feature or `:core:*` sub-modules, Hilt or other DI frameworks (use constructor injection), product flavors.

## Packages

Every package starts with `io.github.soanssandha.afterimage`.

| Module | Package | Contents |
|---|---|---|
| core | `.core.config` | Settings keys, defaults, validation, codecs |
| core | `.core.outline` | The outline pipeline on plain arrays: luminance, blur, XDoG/Canny, clean-up, distance transform, reveal order |
| core | `.core.engine` | The wallpaper engine's state machine: states, events, render commands, `reduce()` |
| core | `.core.effect` | Effect ids, durations, easing |
| core | `.core.burnin` | `BurnInShift` and its `Offset` |
| hook | `.hook` | The `AfterimageModule` entry class only |
| hook | `.hook.target` | `*Targets` classes: reflection handles per SystemUI class, resolved once, all-or-nothing |
| hook | `.hook.intercept` | One `Hooker` per hooked SystemUI method |
| hook | `.hook.config` | Reads remote prefs into a `core.config` type |
| app | (root) | `Application` and `Activity` |
| app | `.wallpaper` | The `WallpaperService`, its engine, and the ambient-component sync |
| app | `.render` | The renderer, AGSL shader sources and uniforms, shared by the engine and the in-app preview |
| app | `.ui` | Screens, components, theme, and each screen's ViewModel and UI state |
| app | `.data` | Photo import, outline cache, settings store, Vector service client, device checks |

Debug-only developer tools, such as an adb config activity, live in `app/src/debug/` and never reach a release build.

## Files

- One public top-level type per file, named after the type.
- Tests live in `src/test/kotlin` under the same package as the code they test, named `<Type>Test.kt`. Test fixtures go in a `fixture` sub-package. Test images are generated in code, never photos (rules §3).
- Module build files only apply a convention plugin and set `namespace`, module-specific dependencies and, in `:app`, the app's identity (`applicationId`, version). Everything shared goes in `build-logic/`.
- Every version (libraries, plugins, SDK levels) lives in `gradle/libs.versions.toml`. Never put a version literal in a build file.
- `META-INF/xposed/*` lives only in `app/src/main/resources/`. App strings go in `app/src/main/res/values/strings.xml`. Icons are vector drawables only; the hygiene check blocks raster images.

## Where everything else goes

| What | Where |
|---|---|
| Roadmap and status | `docs/plan.md` |
| Branches, commit messages, pull requests, releases, and what to do in each situation | `CONTRIBUTING.md` |
| How the system works now | `docs/architecture.md` (from Phase 1) |
| Why a decision was made | `docs/decisions/NNNN-short-title.md`: an ADR with context, decision and consequences. To change one, write a new ADR that supersedes it; never rewrite an accepted one. |
| Verified framework and SystemUI facts per build | `docs/hook-targets.md` |
| Background research | `docs/research/` |
| Device test matrix, battery protocol, post-OTA checklist | `docs/testing.md` (from Phase 1) |
| Install, requirements, safety and recovery for users | `README.md` |
| Our license; third-party licenses and notices | `LICENSE` (GPL-3.0-or-later); `THIRD_PARTY_NOTICES.md` |
| Release notes | `CHANGELOG.md`, in Keep a Changelog format; each release's section becomes its GitHub Release notes |
| Dev and repo scripts | `scripts/*.sh`: bash, kebab-case names, a header comment with usage |
| Git hooks | `.githooks/` |
| CI, dependency updates, PR template | `.github/`: `workflows/` (`build.yml` is called by `ci.yml`, and later by `release.yml`; `pr.yml`), `dependabot.yml`, `PULL_REQUEST_TEMPLATE.md`. Every action is pinned to a commit SHA with its version in a comment. Logic longer than a few lines goes in `scripts/` |
| Claude config | `.claude/`: project rules outside `ecc/`, personal notes in `private/` |
| Third-party tooling | `third_party/`, as pinned submodules |
| Throwaway output (pulled APKs, dexdump output, logs, outline-lab images) | The session scratchpad, never the repo |

Don't add a top-level directory without updating plan §6 and this file.

## Naming, branches and versions

- Gradle modules are short lowercase words. Convention plugin ids are `afterimage.android.application`, `afterimage.android.library` and `afterimage.jvm.library`.
- Branching follows `CONTRIBUTING.md` (why: `docs/decisions/0005-branching-model.md`). In short:
  - `main` holds releases and `develop` is the integration branch. Neither takes direct commits or pushes once they exist; they change only through pull requests.
  - Topic branches come off `develop` as `<type>/<description>`, using a commit type (`feat/outline-pipeline`, `fix/12-black-frame`), and are squash-merged back. `hotfix/…` comes off `main`. `spike/…` is never merged.
  - Commits and PR titles follow Conventional Commits: `<type>(<scope>): <imperative subject>`, at most 72 characters.
  - New work starts with `bash scripts/start-branch.sh <type>/<description>`, which updates `develop` from origin and branches off it. Claude runs it only with the owner's OK until the standing permission is granted (rules §1).
  - The commit-msg and pre-push hooks enforce branch names and the message format.
- Releases are annotated `vX.Y.Z` tags on `main`. Until 1.0.0, the minor version is the phase (`v0.1.0` is Phase 1).
- The release `versionName` (semver) is defined in one place, `afterimage` in the version catalog, and the release `versionCode` is derived from it. Dev installs override `versionCode` through `scripts/dev-install.sh` (from Phase 1).

## One-command verification

`./gradlew check` runs formatting (Spotless + ktlint), lint (once there are Android modules), unit tests and the Kover gate on `:core`. Before any commit, also run `bash scripts/check-repo-hygiene.sh`.
