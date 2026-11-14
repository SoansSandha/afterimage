# Changelog

Notable changes to Afterimage, one section per release. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Until 1.0.0, the minor version is the development phase ([CONTRIBUTING.md](CONTRIBUTING.md#versions-and-tags)).

Each release's section becomes the notes of its GitHub Release, so write entries for people installing the module.

## [Unreleased]

### Added

- A Magisk module that keeps the lock-screen wallpaper visible on the always-on display, using the path Android already has for it (a framework overlay plus a Secure setting). Magisk's action button turns it off and on; removing the module puts the setting back. No outline yet: this build is for testing how Google's own wallpaper-on-AOD looks and what it costs in battery.
