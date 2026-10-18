# Volt Launcher: notes for AI agents

Read this file and [docs/DESIGN.md](docs/DESIGN.md) before you change anything.

## What this is

Volt Launcher is a lightweight Android TV home launcher with a Tesla car style UI.
It is made for one device: a Xiaomi Mi Box 4 (MIBOX4, Android 12 / API 31,
2 GB RAM, 1080p UI at density 2) that is used only with a D-pad remote.

- The code is in Kotlin with plain Android Views built in code.
- The main files are `HomeActivity.kt` (UI), `Model.kt` (apps, store, visuals)
  and `Weather.kt` (Open-Meteo).
- The pure logic is covered by JVM unit tests in `app/src/test`.

## How to work with the owner

- **Write in simple English.** The owner wants answers in about 80% ASD-STE100
  Simplified Technical English: short sentences, active voice, simple words.
- **Give options before you act.** When the owner gives a new direction, show
  the options with a recommendation and wait for a choice. When the owner has
  already selected a clear action, do it.
- **Mockup before code.** For a visual change, make an HTML mockup first and show
  variants when the direction is open. Write no UI code before approval.
- **Delegate.** The lead agent makes the decisions. Research and large tasks go to
  Sonnet subagents with a complete brief. The lead agent then reviews the result:
  it reads the diff, builds it, and tests it on the device.
- **Report the facts.** Say what you tested on the real box and what you did not
  test.

## Build, test and lint

Windows PC, JDK 17, Android SDK platform 34 and build-tools 34.0.0. Set
`JAVA_HOME` and `ANDROID_HOME` in the same process as the build.

```
gradlew.bat ktlintCheck lintDebug testDebugUnitTest assembleRelease
```

- **Local release signing:** the build reads `keystore.properties` and
  `keystore/volt-release.jks`. Never commit, print or upload them. The owner
  keeps a backup, and updates need the same key.
- **Weather location:** set `weather.city`, `weather.lat` and `weather.lon` in
  `local.properties` (untracked). Empty values turn the weather off.

## Device loop (real Mi Box)

ADB runs over the network. The address is in the owner's environment and is not
part of this repo; ask the owner for it.

1. Install the build: `adb install -r app/build/outputs/apk/release/app-release.apk`
2. Wake the box and go home:
   - `adb shell input keyevent KEYCODE_WAKEUP`
   - `adb shell input keyevent KEYCODE_HOME`
3. Take a screenshot and look at the PNG:
   - `adb shell screencap -p /sdcard/s.png`
   - `adb pull /sdcard/s.png`
4. Drive the UI with key events: `adb shell input keyevent KEYCODE_DPAD_RIGHT`, and so on.
5. Measure RAM: `adb shell dumpsys meminfo io.github.kobiregev.volt`. Check the TOTAL PSS
   value against the budget, which is about 30 MB.

### Pitfalls on this box (learned the hard way)

- **Restart only after the box saves its state.** Package and home-app changes
  are lost if the box restarts within about a minute. Wait about 120 s, run
  `adb shell sync`, then restart with `adb shell svc power reboot`.
- **Home app:** `cmd package set-home-activity io.github.kobiregev.volt/.HomeActivity`
  works only while the stock `com.google.android.tvlauncher` is disabled. That
  launcher has a higher priority.
- **No accessibility services to catch the Home key.** They add input delay to
  every remote press.
- **Clipping kills the focus zoom.** The parents of a view that scales need
  `clipChildren = false` and `clipToPadding = false`.
- **The box turns on the screensaver quickly.** Send `KEYCODE_WAKEUP` before you
  take a screenshot.
- **No shell commands that need root.** `pm disable` on components of other apps
  fails with a SecurityException.

## Rules

- Keep the [design system](docs/DESIGN.md): its tokens, its focus model and the
  RAM budget.
- Use no new dependencies without a strong reason. Never add Compose,
  RecyclerView or image libraries.
- Put new pure logic into small testable functions, and add unit tests for it.
- Keep personal data out of the repo: no home city, no IP addresses, no account
  names, no screenshots that show private data.
- For a user-visible change, update `CHANGELOG.md`.
- The v2 plan and research are in [docs/research/v2-research.md](docs/research/v2-research.md).
