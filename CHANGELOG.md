# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Volt settings screen: Categories, Home cards and About Volt sections. Open it with OK on the gear button or the "Launcher settings" row in the app options. Long press on the gear still opens the Android settings.
- Manage categories in settings: rename (TV keyboard), move up or down, delete with a confirm step, and add. The limit is 12 categories.
- Switches to hide the weather card or the system card. The other card, or the hero card, uses the free space.
- Editable category data: Streaming, Music, Games, Tools and System by default. Apps sort themselves by the Android app category and by package name. The category control hides empty categories and scrolls when it is wide.
- A category picker in the app options with an "Auto" row and a check mark on the current choice.
- Live move mode for the dock: choose "Move" in the app options, then press Left/Right to slide the app. OK saves, Back cancels. A blue ring, chevrons and a hint show the mode.

### Changed

- Category choices of v1 move to the new format on first start.
- File managers are now sorted into Tools, not System.
- The "Move left" and "Move right" options are now one "Move" option.
- App options are now a bottom sheet with icons, the current category and the hidden app count, instead of a dialog. Back, Menu and Home close it.
- Wi-Fi and Bluetooth quick buttons show a blue icon and dot when they are on.

## [1.0.0]

First release.

### Added

- Tesla-style home screen for Android TV, built for the Xiaomi Mi Box 4 and D-pad only control.
- App dock with banner tiles, a hero area for the focused app, and automatic Streaming / System categories.
- Options menu (long-press OK, MENU key, or "Options"): move left/right, change category, hide, app info, show hidden apps.
- Persistent app order, hidden apps and category overrides.
- Status area with clock, Wi-Fi band and signal quality, Ethernet state and free memory.
- Optional weather from Open-Meteo, configured at build time with `weather.city`, `weather.lat` and `weather.lon` in `local.properties`.
- Unit tests for categories, ordering, weather codes and Wi-Fi text; ktlint, Android lint and GitHub Actions CI and release workflows.

[Unreleased]: https://github.com/kobiregev/volt-tv-launcher/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/kobiregev/volt-tv-launcher/releases/tag/v1.0.0
