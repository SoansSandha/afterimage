# 0004: License Afterimage under GPL-3.0-or-later

- Status: Accepted (owner, 2026-11-14)
- Files: [LICENSE](../../LICENSE), [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md)

## Context

- The repo will be public, so it needs a license before anyone can reuse or contribute to it.
- GPL-3.0 is the norm for LSPosed and Vector modules, and Vector itself is GPL-3.0. Sidearm, the owner's other module, is GPL-3.0-or-later (its ADR 0005).
- Afterimage runs code inside SystemUI and installs itself as a system app, so people should be able to see and rebuild what they run.
- The repo will vendor part of ECC (MIT) into `.claude/` and depends on libxposed (Apache-2.0). Phase 4 adds an on-device segmentation model.

## Decision

- Our own code is `GPL-3.0-or-later`.
- Third-party material keeps its own license, with its notice in `THIRD_PARTY_NOTICES.md` in the same commit that adds it.
- Don't add code, libraries or models under a license that conflicts with GPL-3.0.

## Alternatives considered

| Option | Why not |
|---|---|
| MIT or Apache-2.0 | Lets forks ship closed builds of a module that runs inside SystemUI. The module ecosystem expects GPL. |
| GPL-3.0-only | "Or later" leaves room for a future GPL version without asking every contributor. |

## Consequences

- Anyone who distributes a modified build has to publish its source under the same terms.
- MIT and Apache-2.0 material can be included, with its notice kept.
- Proprietary libraries are out. This rules out Google's ML Kit for subject cut-outs (closed source, model delivered through Play services); Phase 4 picks an openly licensed model (`../plan.md` §5.9).
