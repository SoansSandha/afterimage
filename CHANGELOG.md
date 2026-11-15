# Changelog

Notable changes to Afterimage, one section per release. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Until 1.0.0, the minor version is the development phase ([CONTRIBUTING.md](CONTRIBUTING.md#versions-and-tags)).

Each release's section becomes the notes of its GitHub Release, so write entries for people installing the module.

## [Unreleased]

### Added

- The Afterimage wallpaper: choose a photo in the Afterimage app and set it as your wallpaper. When the phone locks into the always-on display, the photo fades into a glowing outline of itself, in the photo's own colours, and fades back when you wake the phone. The outline moves a few pixels each time to protect the screen. Photos stay on the phone: the app has no internet access.
- A Magisk module that installs the app as a system app (Android only lets those draw on the always-on display) and turns on the path Android already has for showing the wallpaper there. Magisk's action button turns the wallpaper on the always-on display off and on; removing the module puts everything back.

### Known issues

- Android still blurs and dims the wallpaper on the always-on display, so the outline is softer and darker than intended. Removing that is the next step.
