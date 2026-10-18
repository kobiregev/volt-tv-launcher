# v2 build plan

The owner approved the v2 mockups on 2026-10-18. This file is the build plan.
The research behind it is in [research/v2-research.md](research/v2-research.md).

## Scope

In v2:

- Bottom sheet that replaces the AlertDialog options menu
- Quick controls that show state (Wi-Fi, Bluetooth)
- Live move mode for the dock
- User-editable categories
- Settings screen
- Weather city setting (search and preset list)

Not in v2: recent apps, Watch Next rows, the AGP 9 / Kotlin 2 upgrade.

## Rules for every phase

- Work on the `v2` branch. One commit per phase.
- The lead agent writes the brief. A Sonnet subagent writes the code.
- The lead agent reviews the diff, runs
  `gradlew.bat ktlintCheck lintDebug testDebugUnitTest assembleRelease`,
  installs the APK on the box, drives it with key events, takes screenshots
  and measures RAM (budget about 30 MB PSS).
- Put new pure logic in `Logic.kt` (or a new pure file) with unit tests.
- Update `CHANGELOG.md` (Unreleased section) and `docs/DESIGN.md` in the same phase.

## Phases

### Phase 1: bottom sheet and quick-control state

- A reusable in-activity bottom sheet: dim scrim (black 65%), card color,
  top radius 16dp, drag handle, header (app tile, name, sub line), rows of 44dp
  with an icon, a label, and a value plus chevron on the right. The focused row
  is blue. Focus stays inside the sheet. Back and Home close it.
- The app options use the sheet instead of AlertDialog.
- Wi-Fi and Bluetooth quick controls show state: when on, the icon is blue
  with a 4dp blue dot under it. Focus still wins (blue fill, white icon).
  Bluetooth state comes from `Settings.Global` `bluetooth_on` (no permission).

### Phase 2: live move mode

- "Move" in the sheet, or a long press on OK, starts move mode.
- The tile lifts (scale 1.15) with a 3dp blue ring and blue chevrons on both
  sides. A blue hint pill shows "Left / Right move · OK done · Back cancel".
- Left/Right moves the tile one place; neighbors slide with `translationX`.
- OK saves. Back restores the old order. Up/Down are blocked.

### Phase 3: editable categories

- Data: a list of categories (id, name, order) and per-app overrides, as JSON
  in SharedPreferences. Migrate the v1 `cat:<pkg>` values.
- Defaults: All, Streaming, Music, Games, Tools, System. Auto-sort with
  `ApplicationInfo.category` first, then the v1 package rules.
- Category picker sheet with a check mark, an "Auto" row and "Manage categories".
- The segmented control hides empty categories and scrolls when needed.

### Phase 4: settings screen

- A second activity built in code: section list on the left, detail rows on the
  right. Sections: Weather, Categories, Home cards, Units, About Volt.
- Opened from the settings quick control (long press keeps the system
  settings) and from the options sheet.
- Categories section: rename (TV keyboard), move up/down, delete, add.

### Phase 5: weather city

- The city, latitude and longitude are saved on the box. The build-time
  `local.properties` values become only the first default.
- Search with the Open-Meteo geocoding API (5 results). A preset list of about
  40 large cities for use without a keyboard.
- Weather on/off, °C/°F, update interval.

### Phase 6: release v2.0.0

- Full device test, RAM check, new screenshot (no private data), docs,
  changelog, tag `v2.0.0`.

## Facts checked on the box

- The TV keyboard (Gboard) is installed and is the default input method.
- `settings get global bluetooth_on` works without root.
