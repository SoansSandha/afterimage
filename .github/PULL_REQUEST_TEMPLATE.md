## Summary

<!-- What changes and why. Link the plan section, ADR or issue if there is one. -->

## Test plan

<!-- How it was checked: unit tests, scripts/probe-device.sh, rows of docs/testing.md. -->

## Pre-flight

- [ ] The title follows the commit format, `<type>(<scope>): <subject>`, in at most 72 characters ([CONTRIBUTING.md](../CONTRIBUTING.md)).
- [ ] Commits use your GitHub noreply email (GitHub › Settings › Emails); CI rejects other addresses.
- [ ] `./gradlew check` passes.
- [ ] `bash scripts/check-repo-hygiene.sh` passes, and the diff and this description hold no pasted logs, screenshots, photos, or details of a particular phone or PC.
- [ ] Docs match the change: `docs/plan.md`, `docs/architecture.md`, `docs/hook-targets.md`, `README.md`, and an entry under "Unreleased" in `CHANGELOG.md` for anything people installing the module would notice.
- [ ] Changes to the wallpaper engine, the SystemUI hook or the Magisk module were checked on a phone (`docs/testing.md`): locking from the lock screen and from an app, and waking. The screen was driven with `KEYCODE_SLEEP` / `KEYCODE_WAKEUP`, never `KEYCODE_POWER`.
