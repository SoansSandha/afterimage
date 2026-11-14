# 0005: Branching model: `develop` and `main`, with short-lived topic branches

- Status: Accepted (owner, 2026-11-14)
- How to work with it: [CONTRIBUTING.md](../../CONTRIBUTING.md)

## Context

- One maintainer works on Afterimage with Claude. Outside contributions become possible once the repo is public.
- Afterimage ships versioned APKs and Magisk module zips that run inside SystemUI and install as system apps. Every release needs manual checks on the phone (AOD entry and exit, both lock paths, battery), which CI can't do.
- A monthly Pixel OTA can break the SystemUI hook. A fix then has to ship from the released code, even while new work is half done.
- Sidearm already uses this model (its ADR 0001), with scripts and hooks that enforce it. Using the same model here means the same scripts, the same habits and the same CI.

## Decision

- `main` holds releases only and stays GitHub's default branch. `develop` is the integration branch.
- Work happens on short-lived topic branches off `develop`, named `<type>/<description>` using the Conventional Commits types, and squash-merged.
- `hotfix/…` branches come off `main`, merge into `main`, and are then merged back into `develop`.
- No `release/…` branches. Release prep happens on `develop`, which freezes while the release is tested on the phone.
- Everything that enters `main`, and every back-merge, uses a merge commit, never squash or rebase.
- Commit messages and PR titles follow Conventional Commits 1.0.0.
- Releases are annotated `vX.Y.Z` tags on `main`. Until 1.0.0, the minor version is the phase number.
- `scripts/check-conventions.sh` enforces this locally from the commit-msg and pre-push hooks; GitHub rulesets and CI add server-side enforcement once the repo exists.
- The very first commit (repo scaffold) goes on `main`, and `develop` is created from it. After that, neither takes direct commits.

## Alternatives considered

| Option | Why not |
|---|---|
| GitHub Flow: `main` plus topic branches | `main` would mix phone-tested releases with untested work, and hotfixing a release would mean branching from a tag. |
| Trunk-based development | Keeping the trunk releasable needs strong CI and feature flags. Afterimage's riskiest checks are manual, on a phone. |
| Full git-flow, with `release/…` branches | With one maintainer nothing else moves during a release, so it would only add a back-merge to every release. |
| Squash or rebase into `main` | Both rewrite the commits, so `main` and `develop` diverge and every release PR conflicts. |

## Consequences

- `main` is always the latest release; `git log --first-parent main` lists releases and hotfixes.
- `develop` gets one commit per PR, which keeps a `git bisect` on the phone short.
- Each hotfix costs an extra back-merge PR, and `develop` is frozen while a release is tested.
- Because `main` is the default branch, PRs need `develop` picked as their base.
- Revisit if several people work in parallel, or if CI with device tests makes trunk-based development realistic.
