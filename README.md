# Afterimage

When your Pixel locks into the always-on display, the lock-screen wallpaper fades into a glowing line outline of itself, and turns back into the photo when you wake the phone.

> **Status: planning.** Nothing is built yet. Research notes and the plan are in [`docs/`](docs/plan.md).

## How it will work

- A live wallpaper that knows when the phone enters and leaves the always-on display, and animates between your photo and its outline.
- A Magisk module that installs it as a system app (Android only lets system wallpapers draw on the always-on display) and turns on Android's built-in "wallpaper on always-on display" path.
- A small Vector module that stops the system UI from blurring and darkening the outline.

Planned options: effect type, speed, line thickness, brightness, grayscale or colour lines, and "subject only" (outline just the person or pet in the photo).

## Requirements (planned)

- Google Pixel 9 Pro XL on Android 17 (other Pixels may work later; untested)
- Root with [Magisk](https://github.com/topjohnwu/Magisk) and [Zygisk Next](https://github.com/Dr-TSNG/ZygiskNext)
- [Vector](https://github.com/JingMatrix/Vector) (the successor to LSPosed, libxposed API 102)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). The plan and design are in [docs/plan.md](docs/plan.md).

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Credits

Inspired by the edge/outline lock-screen effect in Samsung's Good Lock "Wonderland" module. Afterimage is an independent project, not affiliated with or endorsed by Samsung or Google.
