# Contributing

How changes move through this repo: branches, commit messages, pull requests, releases, and what to do in each situation that comes up. It applies to everyone, Claude included. The privacy and permission rules in [.claude/rules/git-and-privacy.md](.claude/rules/git-and-privacy.md) apply on top of it. Why this model was chosen: [ADR 0005](docs/decisions/0005-branching-model.md). It's the same model as the owner's other module, [Sidearm](https://github.com/SoansSandha/sidearm).

## The model

```
             v0.1.0                        v0.2.0     v0.2.1
main     -------o-----------------------------o----------o--------
                 \                           / \        / \
                  \             release PR  /   hotfix/…   \ back-merge
develop  ----------o----o----o----o--------o----------------o---
                        ^    ^    ^
                   topic branches off develop, each squash-merged as one commit
```

| Branch | Branches from | Merges into | Merge method | Lifetime |
|---|---|---|---|---|
| `main` | | | | Permanent. Released code only; every release is an annotated `vX.Y.Z` tag on it. GitHub's default branch. |
| `develop` | `main` | `main`, through a release PR | Merge commit | Permanent. The integration branch; `./gradlew check` always passes on it. |
| Topic: `feat/…`, `fix/…`, `docs/…` and the other commit types | `develop` | `develop` | Squash | Days, not weeks. Deleted after merging. |
| `hotfix/…` | `main` | `main`, then `main` is merged back into `develop` | Merge commit | Until the patch release is tagged |
| `spike/…` | `develop` | Never | | A throwaway experiment. Write up the findings through a `docs/` branch, then delete it. |

Nobody commits to `main` or `develop` directly. They change only through pull requests: the pre-push hook refuses direct pushes to them, and so will GitHub's rulesets once the repo exists. The one exception is the very first commit, the scaffold, which goes on `main` before `develop` is created from it.

## Branch names

`<type>/<description>`, for example `feat/outline-pipeline`.

- **type** is one of the commit types below, or `hotfix` or `spike`. Use the type the change will land as.
- **description** is 2–5 lowercase words joined by hyphens (dots are fine inside versions: `chore/release-v0.2.0`). It says what changes, not who is doing it. An issue number can lead: `fix/12-black-frame`. The whole name is at most 60 characters.
- No personal or device details (rules §3). Public Android build IDs are fine.
- `scripts/start-branch.sh` checks the name. To check one by hand: `bash scripts/check-conventions.sh --branch feat/outline-pipeline`.

## Starting and growing a branch

Every new piece of work starts from the latest `develop`:

```bash
bash scripts/start-branch.sh feat/outline-pipeline
```

The script stops unless the worktree is clean. It then fetches, fast-forwards `develop` to `origin/develop`, and creates the branch, refusing if local `develop` has drifted from origin. By hand, the same thing is `git switch develop && git pull --ff-only && git switch -c feat/outline-pipeline`.

- Name the branch after what the task is about to do. More work on the same task stays on its branch, and a task that changes no files needs no branch.
- Claude runs the script only with the owner's OK until the owner grants it a standing permission (rules §1).
- GitHub deletes merged branches, but local copies stay. When the branch you're leaving is already on `develop`, the script says so and gives the `git branch -D` command; deleting it is still the owner's call.

If the work grows past the branch's name, rename the branch so the name still describes it, or split the new part onto its own branch if it could ship on its own. Claude proposes the new name or the split and waits for the owner's OK before renaming.

| Branch state | How to rename |
|---|---|
| Not pushed | `git branch -m <new-name>` |
| Pushed, no PR yet | `git branch -m <new-name>`, `git push -u origin <new-name>`, then `git push origin --delete <old-name>` |
| PR open | Don't rename it: GitHub closes a PR when its branch is renamed. Update the PR title instead; with squash merges, the title is what lands on `develop`. |

## Commit messages

[Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/):

```
<type>(<scope>)!: <subject>

<body>

<footers>
```

| Type | For | Example | In release notes |
|---|---|---|---|
| `feat` | New or changed behaviour a user can notice | `feat(app): add the line-trace effect` | Yes |
| `fix` | A bug fix | `fix(core): keep the outline inside the frame after a shift` | Yes |
| `perf` | Faster or lighter, same behaviour | `perf(core): compute the distance field at half resolution` | Yes |
| `revert` | Undoing an earlier commit | `revert: feat(app): add the line-trace effect` | Yes |
| `refactor` | A code change with no behaviour change | `refactor(app): move shader uniforms into render/` | No |
| `test` | Tests only | `test(core): cover an edge on the image border` | No |
| `docs` | Documentation only | `docs(targets): add the December QPR2 build` | No |
| `build` | Gradle, `build-logic/`, the version catalog, dependencies | `build(deps): bump AGP to 9.6.0` | No |
| `ci` | GitHub Actions | `ci: build the module zip on pull requests` | No |
| `chore` | Repo tooling and housekeeping: scripts, git hooks, `.claude/`, ECC, release prep | `chore(release): prepare v0.2.0` | No |

The scope is optional. Leave it out when a change spans several areas.

| Scope | Covers |
|---|---|
| `core` | `:core`: the outline pipeline, engine state machine, effects, burn-in, config contract |
| `app` | `:app`: the wallpaper engine, renderer, settings UI, packaging, `META-INF/xposed` |
| `hook` | `:hook`: everything that runs in SystemUI |
| `overlay` | `:overlay`: the static overlay APK |
| `module` | `magisk/` and `scripts/package-module.sh`: the Magisk module |
| `targets` | Support for an Android build: `docs/hook-targets.md` and the target resolvers |
| `deps` | Dependency and toolchain versions |
| `repo` | Git hooks, `scripts/`, `.claude/`, ECC |
| `release` | The version bump and changelog for a release |

- **Header:** at most 72 characters, no full stop at the end, and one blank line before the body.
- **Subject:** imperative and lowercase ("add", "fix", "hold", not "Added" or "Adds"). Say what changes, not which file changed.
- **Body** (optional, wrapped at 72): why the change is needed, and anything a reviewer can't see in the diff. For changes to the engine, hook or module, say how it was checked on the phone, for example "locked from the lock screen and from an app, woke with fingerprint and tap". Quote trimmed log lines only (rules §3).
- **Breaking changes:** put `!` before the colon and add a `BREAKING CHANGE: <what breaks and what to do>` footer. For Afterimage that means a settings change that loses saved options or outlines, a raised requirement (Android build, Magisk, Vector or libxposed API), or removed behaviour.
- **Footers:** `Fixes #12` closes the issue once the change reaches `main`, the default branch, which means in the release that ships it. `Refs: #12` links it without closing it. Credit co-authors with `Co-Authored-By: Name <noreply address>`.
- **Topic-branch commits** can be as small as you like, but each still needs a valid header. Only the pull request title lands on `develop`, because topic branches are squash-merged.

The commit-msg hook checks the header's shape and length, the full stop, and the blank line. Messages git writes itself (`Merge …`, `Revert "…"`, `fixup! …`) pass.

## Pull requests

1. Before opening one, `./gradlew check` and `bash scripts/check-repo-hygiene.sh` pass. The docs the change affects are updated in the same branch (`docs/plan.md` status, `docs/hook-targets.md`, `docs/architecture.md`, `README.md`).
2. Target `develop`. GitHub suggests `main`, the default branch, so change the base (`gh pr create --base develop`). Only release PRs (from `develop`) and `hotfix/…` PRs target `main`.
3. The title becomes the squash commit's header, so it follows the commit format; GitHub adds ` (#N)`. The description says what and why, how it was tested (for engine, hook or module changes: on the phone, per `docs/testing.md`), and links the issue. The privacy rules apply to titles, descriptions and comments too.
4. One concern per PR. A phase usually takes several; Phase 1, for example, splits into the outline pipeline, the engine, and the module that installs it.
5. Merge with the method below. When GitHub offers a choice of commit email, pick the noreply address.

| Pull request | Merge method | Why |
|---|---|---|
| Topic → `develop` | Squash and merge | One reviewed, buildable commit per change on `develop`. Work-in-progress commits stay on the branch. |
| `develop` → `main` (release) | Create a merge commit | Squashing or rebasing rewrites the commits, so `main` and `develop` would diverge and every later release PR would conflict. |
| `hotfix/…` → `main` | Create a merge commit | Same reason. `main`'s first-parent history stays one entry per release or hotfix. |
| Back-merge into `develop` | Create a merge commit | Brings a hotfix into `develop` without duplicating it. |

To bring a branch up to date with `develop`: if it has never been pushed, rebase it (`git rebase develop`). Once it's pushed, merge `develop` into it instead. Force-pushing is denied in this repo, and the squash merge flattens the extra merge commit anyway.

## Versions and tags

- Versions are SemVer `MAJOR.MINOR.PATCH`, set in one place: `afterimage` in `gradle/libs.versions.toml`. The release `versionCode` is derived from it as `MAJOR × 10000 + MINOR × 100 + PATCH` (0.5.0 is 500), so minor and patch stay below 100. `scripts/dev-install.sh` (from Phase 1) overrides it for dev installs.
- Until 1.0.0, the minor version marks the phase (`docs/plan.md` §7): `v0.1.0` is Phase 1 (the outline wallpaper), `v0.2.0` Phase 2 (the SystemUI hook), and so on to `v0.5.0`, the first published release (Phase 5). Patch versions are hotfixes. `v1.0.0` follows the Phase 6 soak.
- From 1.0.0: a breaking change bumps major, `feat` bumps minor, and `fix` or `perf` bumps patch.
- A release is an annotated `vX.Y.Z` tag on the merge commit of its PR into `main`. Tags are never moved or deleted; a bad release is fixed by the next patch release. The pre-push hook checks that a tag has the right format, is annotated, and is on `main`.
- From Phase 5, a hand-run Release workflow publishes a tag: it builds and checks the tag's code, signs the APKs in a job that runs no build code, packages the Magisk module zip, and publishes the GitHub Release with the changelog section as its notes, the zip and APK, their SHA-256 and the signing certificate's digest (Sidearm ADR 0009, ported then). Pushing a tag alone never signs anything.

## Playbook

| Situation | Branch from | Name | PR into | Notes |
|---|---|---|---|---|
| A feature, or the next piece of a phase | `develop` | `feat/<what>` | `develop` | Split a phase into PR-sized branches. |
| A bug in unreleased code | `develop` | `fix/<what>` | `develop` | Add a test that fails without the fix (`:core`), or record the phone check. |
| A bug in a release people have installed | `main` | `hotfix/<what>` | `main` | See [Hotfix](#hotfix). |
| A new Android build, targets unchanged | `develop` | `docs/targets-<build>` | `develop` | Run `scripts/probe-device.sh`, add the build to `docs/hook-targets.md`. |
| A new Android build breaks the hook or the gate | `main` once a release exists, else `develop` | `hotfix/targets-<build>`, or `fix/targets-<build>` before the first release | `main` or `develop` | Verify the new signatures with `dexdump`, use scope `targets`, and run the device matrix in `docs/testing.md`. |
| Support a new Android version or QPR | `develop` | `feat/android-<version>` | `develop` | Ships in the next minor release. |
| A release or phase milestone | `develop` | `chore/release-v<x.y.z>` for the prep | `develop`, then `develop` → `main` | See [Release](#release). |
| Documentation | `develop` | `docs/<what>` | `develop` | If the mistake could hurt people using a release (install, safety or recovery steps), use the hotfix flow without a version bump or tag. |
| A refactor | `develop` | `refactor/<what>` | `develop` | No behaviour change. Tests pass before and after. |
| A dependency or toolchain bump | `develop` | `build/<what>`, or Dependabot's own branch | `develop` | `build(deps): …`. Anything loaded into SystemUI (libxposed) or a change to the Magisk or Vector requirement also needs the phone check. |
| An ECC update | `develop` | `chore/ecc-<version>` | `develop` | Review the upstream diff, then run `scripts/sync-ecc.sh`. Update `THIRD_PARTY_NOTICES.md` if the license changed. Keeping Sidearm's pin in step is a separate change in that repo. |
| An experiment or probe | `develop` | `spike/<what>` | Never merged | Write up the findings through a `docs/` branch, then delete the spike. |
| Undo a merged change | `develop` | `revert/<what>` | `develop` | `git revert <sha>`. If the change is already released, use the hotfix flow. |
| A breaking change, such as renaming a settings key | `develop` | `feat/<what>` | `develop` | `!` and a `BREAKING CHANGE:` footer. Migrate old values in the `:core` codec where possible. |
| A security problem in a release | `main` | `hotfix/<neutral name>` | `main` | Don't describe the problem in branch names, commits or PRs until the fixed release is out. Reporters use GitHub's private vulnerability reporting. |
| The work outgrew the branch name | | | | Propose a rename or a split; rename only with the owner's OK. See [Starting and growing a branch](#starting-and-growing-a-branch). |
| A branch has fallen behind `develop` | | | | Rebase if it was never pushed; otherwise merge `develop` into it. |
| Work that needs an unmerged branch | | | | Better: merge the first branch, then branch from `develop`. If you must stack, branch from the first one and use it as the PR base. |
| Abandoned work | | | | Close the PR and delete the branch. |
| An outside contribution | the contributor's fork | any | `develop` | Review it before building or running it (rules §9). If it was opened against `main`, change its base to `develop`. Fix the PR title to the commit format before squash-merging; their authorship is kept. |

### Release

1. Prepare on `chore/release-v<x.y.z>` from `develop`: set `afterimage` in `gradle/libs.versions.toml`, move the "Unreleased" entries in `CHANGELOG.md` under `## [<x.y.z>] - <date>` (and update the links at the bottom), and update the status in `docs/plan.md` and `README.md`. Squash-merge it into `develop`.
2. Freeze `develop` (merge nothing else) and test its build on the phone: the device matrix and battery check in `docs/testing.md`. Fix problems through `fix/…` PRs into `develop`, then test again.
3. Open a PR from `develop` into `main` titled `chore(release): v<x.y.z>`, and merge it with a merge commit.
4. Tag it: `git fetch origin`, then `git tag -a v<x.y.z> origin/main -m "Afterimage v<x.y.z>"` and `git push origin v<x.y.z>`.
5. From Phase 5: run the Release workflow from `main` with the tag and "publish" off, install its module zip on the phone and run the device matrix; then run it again with "publish" on. If a run fails, fix the workflow or scripts through a hotfix into `main`, then run it again for the same tag; tags can't be moved.

### Hotfix

1. Branch `hotfix/<what>` from `main`, which is the released code, not `develop`.
2. Fix the problem with a test or a recorded phone check. Bump the patch version in `gradle/libs.versions.toml`, and add its `CHANGELOG.md` section.
3. Open a PR into `main` and merge it with a merge commit. Then fetch, tag `v<x.y.z>` on `origin/main`, push the tag, and run the Release workflow, as in a release.
4. Back-merge: on `chore/back-merge-v<x.y.z>` from `develop`, run `git merge origin/main` and resolve any conflict (for the version line, keep the higher version). Push it and merge its PR into `develop` with a merge commit.

### Release signing (Phase 5)

Only the Release workflow will sign, run by hand from `main`, in a job that runs no build code, with secrets that only `main` can reach. The owner sets this up once before the first release, the same way as for Sidearm (its CONTRIBUTING.md, Release signing), with Afterimage's own keystore and the secrets `AFTERIMAGE_KEYSTORE_BASE64`, `AFTERIMAGE_KEYSTORE_PASSWORD` and `AFTERIMAGE_KEY_ALIAS` in a `release` environment open only to `main`. Use a new key, not Sidearm's. Keep the keystore and its password in the password manager: without them, installed copies can't be updated in place, and because Afterimage is installed as a system app, users would have to remove the module and reinstall.

## What enforces this

| Rule | Enforced by |
|---|---|
| Commit header format | commit-msg hook → `scripts/check-conventions.sh` |
| Branch and tag names; no direct pushes to `main` or `develop`; tags annotated and on `main` | pre-push hook → `scripts/check-conventions.sh` |
| No secrets or personal data | `scripts/check-repo-hygiene.sh`, from all three hooks. GitHub's secret scanning and push protection are a backstop for secrets, once the repo exists. |
| `main` and `develop` change only through PRs, with the merge methods above, and can't be force-pushed or deleted | Rulesets on GitHub, to be created with the repo (rules §9) |
| Release tags can't be moved or deleted | A tag ruleset, to be created with the repo |
| PR title and branch name; the hygiene check on the title and description | CI: `.github/workflows/pr.yml` |
| The hygiene check on every new commit, the Gradle wrapper jar, formatting, lint, unit tests and the coverage gate, on every PR and every push to `develop` and `main` | CI: `.github/workflows/ci.yml` and `build.yml` |

Turn the hooks on once per clone with `bash scripts/setup-repo.sh`.
