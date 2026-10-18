# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

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
