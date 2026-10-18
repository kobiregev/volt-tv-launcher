## What and why

<!-- Describe the change and the reason for it. Link issues with "Fixes #123". -->

## Checklist

- [ ] `./gradlew ktlintCheck lintDebug testDebugUnitTest assembleDebug` passes
- [ ] New logic is in pure Kotlin and has unit tests
- [ ] RAM budget kept (about 30 MB PSS, checked with `dumpsys meminfo io.github.kobiregev.volt`)
- [ ] D-pad focus checked on a real device (every control reachable, focus never lost)
- [ ] No new dependencies (no Compose, RecyclerView or image libraries)
- [ ] No personal data, secrets or keystores in the diff
- [ ] `CHANGELOG.md` updated for user-visible changes
- [ ] `docs/DESIGN.md` updated if a design rule changed

## Screenshots

<!-- For UI changes. Do not include private data. -->
