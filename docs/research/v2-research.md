# v2 research (2026-10-18)

This is a summary of the desk research for Volt Launcher v2.

- **Sources:** sources about TV launchers are thin. Items marked *(recollection)*
  come from general knowledge of Android and other launchers, not from a cited
  source.
- **Effort:** S, M and L mean small, medium and large.
- **Status:** this file is research only, not a plan. The owner still selects
  the features, and each one gets a mockup before code.

## 1. Live move mode for the dock (S, RAM ~0)

- **Other launchers:**
  - Google TV and Fire TV have a "Move" action in the app menu. You then press
    Left/Right to shift the tile, OK to drop it and Back to cancel *(recollection)*.
  - tvOS uses a "jiggle" mode that you start with a long press.
- **Recommendation:** add a "Move" item to the options sheet, and also let a
  long press on OK go into move mode.
  - The tile in move mode gets a blue ring and a small scale-up, with the hint
    "Left/Right move · OK done · Back cancel".
  - Left/Right swaps the tile with its neighbor. Animate the neighbor with
    `translationX`.
  - OK saves the new order. Back restores the order from before move mode.
  - Up and Down are blocked while in move mode.

## 2. User-editable categories (M, RAM < 1 MB)

- **Data model:** a list of `Category(id, name, order)` plus per-app overrides,
  saved as JSON in SharedPreferences.
- **Defaults:** All, Streaming, Music, Games, Tools and System.
- **Auto-sorting:** apps go to a category from `ApplicationInfo.category`:
  VIDEO goes to Streaming, AUDIO to Music and GAME to Games.
  - The current package-name rules stay as a fallback, because many TV apps
    do not declare a category.
  - The user's choice always wins.
- **Options sheet:** a "Category…" item opens a list with a check mark on the
  current category.
- **"Manage categories" screen:** the user can rename, delete and reorder
  categories, and add a new one.
  - The TV keyboard is needed only to rename. It may be missing on a debloated
    box, so check it.
- **Segmented control:** it scrolls when there are more than about 6
  categories. Empty auto categories are hidden.

## 3. Tesla patterns to borrow (S each)

- **Bottom sheet:** replace `AlertDialog` with our own in-activity bottom sheet
  with a dim scrim, large rows and focus trapped inside. Back closes it.
  `AlertDialog` focus and sizing are unreliable on TV.
- **Rows with values:** settings rows show the label on the left and the value
  plus a chevron on the right.
- **Quick-control state:** quick controls show their state (blue = on) where we
  can read it.
- **Status icons:** small vector icons for Wi-Fi strength, Bluetooth and mute.

## 4. Other high-value ideas

| Idea | Effort | Notes |
|------|--------|-------|
| Weather city setting | M | Open-Meteo geocoding `geocoding-api.open-meteo.com/v1/search?name=…&count=5`. Show a list of 5 results; also give a preset list for when no keyboard is available. |
| Recent apps | S | Record our own launches in SharedPreferences. This needs no permission. UsageStats needs an adb-only appops grant. |
| Launcher settings screen | M | Holds the weather city, the categories, card toggles and units. |
| Remote battery in the status bar | S | Hidden or unreliable API. Optional. |
| Watch Next / channel rows | L | Not recommended: heavy, and each app owns its own rows. |

## 5. Android 12 TV pitfalls

- **Focus:** set explicit `nextFocus*` between rows, because the automatic focus
  search jumps diagonally.
- **Permissions:** Usage access and Draw over other apps often have no settings
  screen on TV. Document the adb grants for them.
- **Home key:** Home must close any open sheet and reset the screen (`onNewIntent`).

## Sources

- https://github.com/svrooij/flauncher
- https://www.apkmirror.com/?p=5735386 (Projectivy release notes)
- https://open-meteo.com/en/docs/geocoding-api
- https://www.hexnode.com/mobile-device-management/help/grant-permissions-for-hexnode-apps-using-android-debug-bridge-adb/
