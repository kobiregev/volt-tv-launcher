# Contributing

Thanks for helping with Volt Launcher. It is a small, focused project: a fast D-pad launcher for low-RAM Android TV boxes.

## Setup

Requirements: JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).

1. Set `JAVA_HOME` and `ANDROID_HOME`.
2. Create `local.properties` (untracked) with `sdk.dir=...`. Optional weather: `weather.city`, `weather.lat`, `weather.lon`.
3. Build: `./gradlew assembleDebug` (`gradlew.bat` on Windows).

## Checks

Run all checks before you open a pull request. CI runs the same commands.

```
./gradlew ktlintCheck lintDebug testDebugUnitTest assembleDebug
./gradlew ktlintFormat      # fixes most style problems
```

Reports are in `app/build/reports/`.

## Guidelines

- Keep logic that does not need Android in pure Kotlin (see `Logic.kt`) and add JUnit 4 tests in `app/src/test`.
- No new dependencies. Do not add Compose, RecyclerView or image libraries.
- Follow the [design system](docs/DESIGN.md). If you change a rule, update that file in the same pull request.
- Never commit `local.properties`, `keystore.properties`, keystores or APKs. Do not add personal data (home city, IP addresses) anywhere.
- AI agents: read [CLAUDE.md](CLAUDE.md) first.

## Pull request checklist

- [ ] ktlint, lint and unit tests pass
- [ ] RAM budget kept (about 30 MB PSS, `adb shell dumpsys meminfo io.github.kobiregev.volt`)
- [ ] D-pad focus checked on a real device: every control is reachable and focus is never lost
- [ ] `CHANGELOG.md` updated for user-visible changes
- [ ] Screenshots for UI changes (without private data)

## Commits and releases

Use short, imperative commit messages. Releases are made by the maintainer by pushing a `vX.Y.Z` tag (see the README).
