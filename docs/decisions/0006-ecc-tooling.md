# 0006: Vendor ECC as a pinned submodule, the same subset as Sidearm

- Status: Accepted (owner, 2026-11-14)
- How it's wired: `scripts/sync-ecc.sh`, [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md)

## Context

- [ECC](https://github.com/affaan-m/ECC) ("Everything Claude Code") is an MIT-licensed collection of Claude Code rules, agents and skills. Sidearm vendors an Android/Kotlin/Java subset of it.
- The owner works on Afterimage and Sidearm with Claude, on the same PC. Both are Kotlin Android projects with the same build setup and conventions.
- ECC's hooks need Node.js, and some of its workflow rules (commit and push steps) conflict with this repo's git rules.

## Decision

- Add ECC as a shallow git submodule at `third_party/ecc`, pinned to the same commit as Sidearm: `91b5f6a` (v2.2.3).
- `scripts/sync-ecc.sh` copies the same subset as Sidearm into `.claude/`:
  - rules: `common`, `kotlin`, `java` → `.claude/rules/ecc/`
  - agents: planner, architect, tdd-guide, code-reviewer, security-reviewer, build-error-resolver, kotlin-reviewer, kotlin-build-resolver, java-reviewer, java-build-resolver
  - skills: android-clean-architecture, kotlin-patterns, kotlin-coroutines-flows, kotlin-testing, java-coding-standards, coding-standards, error-handling, security-review, git-workflow, verification-loop
- Don't install ECC's hooks or MCP configs.
- `.claude/rules/git-and-privacy.md` wins over anything in `.claude/rules/ecc/`.
- The synced copies are never edited by hand; project rules go in `.claude/rules/` outside `ecc/`.

## Alternatives considered

| Option | Why not |
|---|---|
| No ECC | The two repos would behave differently in review and planning, for no gain. |
| A newer ECC pin | Would diverge from Sidearm. Bump both together after reviewing upstream. |
| Copy the files without a submodule | Loses the link to the exact upstream commit, which makes reviewing a bump harder. |

## Consequences

- The submodule and the first sync happen right after `git init` (a submodule can't exist without a repo), with the owner's instruction.
- Clones need `--recurse-submodules`, or `git submodule update --init`.
- The hygiene check skips only the secret, IP and email patterns in the vendored copies, because they quote public examples.
- An ECC bump is a `chore/ecc-<version>` branch: review the upstream diff, sync, and check its license.
