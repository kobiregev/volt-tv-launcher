# Volt Launcher

A fast, minimal Android TV home launcher with a Tesla-style UI, built for low-RAM boxes and D-pad remotes.

[![CI](https://github.com/kobiregev/volt-tv-launcher/actions/workflows/ci.yml/badge.svg)](https://github.com/kobiregev/volt-tv-launcher/actions/workflows/ci.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
![Min Android](https://img.shields.io/badge/Android-9%2B%20(API%2028)-green.svg)

![Volt Launcher home screen](docs/screenshots/home.png)

## Features

- Dock of app tiles with banners, plus a hero area for the focused app
- Categories: All, Streaming, Music, Games, Tools and System by default, sorted automatically. Rename, reorder, delete or add your own in Volt settings. Empty categories are hidden
- App options in a bottom sheet (long-press OK, MENU key, or "Options"): move, category, hide, app info, show hidden apps, launcher settings
- Live move mode: Left/Right slides the app through the dock, OK saves, Back cancels
- Quick controls that show Wi-Fi and Bluetooth state
- Volt settings screen: weather, categories, home cards, about
- Status area: clock, Wi-Fi band and signal quality, Ethernet state, free RAM
- Weather from [Open-Meteo](https://open-meteo.com/) (no API key). You set the city, the unit (°C/°F) and the update interval on the box, in Volt settings, with search or a list of large cities
- Entirely D-pad driven, with smooth focus animation

## Design principles

- Built for one thing: a 2 GB Xiaomi Mi Box 4 that stays responsive.
- Plain Android Views. No Compose, no RecyclerView, no image libraries.
- Everything is reachable with a D-pad. Focus is never lost.
- Logic that does not need Android lives in plain Kotlin and is unit-tested.

Details are in [docs/DESIGN.md](docs/DESIGN.md).

## Requirements

- Android TV device with Android 9 or newer (API 28+). Developed and tested on the Xiaomi Mi Box 4 (Android 12).
- `adb` access to the device for installing and setting the launcher as home.

## Install

1. Download `volt-launcher-<version>.apk` from the [Releases](https://github.com/kobiregev/volt-tv-launcher/releases) page.
2. Install it:

   ```
   adb install -r volt-launcher-<version>.apk
   ```

3. Set it as the home app (or pick it when Android asks):

   ```
   adb shell cmd package set-home-activity io.github.kobiregev.volt/.HomeActivity
   ```

On some boxes the stock launcher has a higher priority and must be disabled first. This is reversible:

```
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell pm enable com.google.android.tvlauncher     # to restore
```

## Build from source

Requirements: JDK 17, Android SDK (platform 34, build-tools 34.0.0).

```
# Windows: gradlew.bat, Linux/macOS: ./gradlew
./gradlew assembleDebug
./gradlew assembleRelease
```

Create an untracked `local.properties` in the project root:

```
sdk.dir=/path/to/Android/Sdk

# Optional first default for the weather city. You can change it on the box in Volt settings.
# Leave empty to start with no city.
weather.city=Berlin
weather.lat=52.52
weather.lon=13.41
```

The values are compiled into the APK as `BuildConfig` fields and are never committed. They are only the first default: a city that you choose in Volt settings replaces them.

### Version

The version defaults to `2.0.0`. Override with `-PversionName=1.2.3 -PversionCode=12`. The version code is never lower than `major*10000 + minor*100 + patch` (for example 20000 for 2.0.0), so a release always installs over older builds.

### Signing

Release builds are signed when signing info is available, in this order:

1. Environment variables `VOLT_KEYSTORE_FILE`, `VOLT_KEYSTORE_PASSWORD`, `VOLT_KEY_ALIAS`, `VOLT_KEY_PASSWORD` (used by CI)
2. `keystore.properties` in the project root (local builds):

   ```
   storeFile=keystore/volt-release.jks
   storePassword=...
   alias=volt
   keyPassword=...
   ```

3. Otherwise the release APK is built unsigned.

Never commit `keystore.properties` or the keystore (both are git-ignored). Back them up: updates can only be installed over an existing install if they are signed with the same key.

## Tests and lint

```
./gradlew ktlintCheck lintDebug testDebugUnitTest
./gradlew ktlintFormat
```

## CI/CD and releases

GitHub Actions runs ktlint, Android lint, unit tests and a debug build on every push to `main` and every pull request.

To publish a release, push a tag:

```
git tag v1.0.0
git push origin v1.0.0
```

The release workflow derives `versionName` from the tag, uses the run number as `versionCode`, builds and signs the APK, verifies the signature, and attaches `volt-launcher-<version>.apk` to a GitHub Release with generated notes.

Required repository secrets (Settings, Secrets and variables, Actions):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of the keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

The easiest way is the GitHub CLI. Run this from the repository root, so no secret value appears on screen:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path 'keystore\volt-release.jks'))) | gh secret set KEYSTORE_BASE64
```

`Resolve-Path` is required: .NET resolves relative paths against the process directory, not the current PowerShell location.

On Linux/macOS use `base64 -w0 keystore/volt-release.jks | gh secret set KEYSTORE_BASE64`.

## Project structure

```
app/src/main/java/io/github/kobiregev/volt/
  HomeActivity.kt   UI, focus handling, options menu, status area
  Model.kt          app loading, persistence (Store), tile bitmaps and colors
  Logic.kt          pure logic: categories, ordering/moving, weather text, Wi-Fi text
  WeatherLogic.kt  pure weather settings logic: URLs, labels, validation
  PresetCities.kt   list of large world cities for the weather picker
  Weather.kt        Open-Meteo forecast and city search, icon mapping
app/src/test/       JVM unit tests for Logic.kt
docs/               design system and research
.github/            CI, release workflow, templates
```

## RAM budget

The launcher targets about 30 MB PSS on the Mi Box 4 (`adb shell dumpsys meminfo io.github.kobiregev.volt`). v2 measures about 29-34 MB in steady state (24 MB right after a start), and about 31-35 MB after a visit to Volt settings. Tile bitmaps are pre-scaled and kept in a 16 MB LRU cache. Changes that raise memory use need a good reason.

## Roadmap

Ideas after v2:

- Recent apps
- Upgrade to AGP 9 and Kotlin 2

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). AI agents should read [CLAUDE.md](CLAUDE.md) first.

## License

[GPL-3.0](LICENSE) (c) 2026 kobiregev
