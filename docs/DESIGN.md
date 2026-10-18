# Volt Launcher design system

This file is the source of truth for how Volt Launcher looks and behaves.
Read it before you change any UI. If a change breaks a rule here, change this
file in the same pull request and say why.

![Home screen](screenshots/home.png)

## 1. Design direction

**Tesla car UI on a TV.** We use the Tesla in-car screen as the model: a calm
dark canvas, one large primary area, small cards to its right, a row of round
quick controls, and a dock at the bottom. It does not use the Cybertruck chamfered
style. We tried it in early mockups, and the user selected rounded Tesla cards.

Principles, in priority order:

1. **Fast and light first.** The box has 2 GB RAM. A nice effect that costs RAM
   or frame time loses.
2. **Readable from the couch.** Large type for the thing in focus, quiet type for
   everything else.
3. **One accent.** Tesla blue marks focus and the primary action. Nothing else
   is blue.
4. **Calm.** No shadows, no glow, no gradients, no wallpaper bitmaps. Color
   comes from the focused app (the hero card) and from app banners only.
5. **The remote is the only input.** Every feature must work with D-pad, OK,
   Back, Home and Menu. No touch, no pointer.

## 2. Tokens

All colors are constants at the top of `HomeActivity.kt`. Use these names.

| Token     | Value       | Use                                             |
|-----------|-------------|-------------------------------------------------|
| `C_BG`    | `#141517`   | Window and screen background                    |
| `C_CARD`  | `#1D1F22`   | Cards, segmented control track, quick buttons   |
| `C_DOCK`  | `#1B1D20`   | Dock bar                                        |
| `C_SEL`   | `#34373B`   | Selected segment when the control is not focused |
| `C_TEXT`  | `#E9EAEC`   | Primary text                                    |
| `C_SEC`   | `#8D9197`   | Secondary text, labels                          |
| `C_ICON`  | `#C9CCD0`   | Icons on quick buttons (not focused)            |
| `C_BLUE`  | `#3E6AE1`   | Tesla blue: focus, primary button, progress     |
| `C_TRACK` | `#2C2F33`   | Progress bar track                              |

Hero card text: white, with secondary hero text at 70% white (`#B3FFFFFF`).
The Options pill is white at 14% (`#24FFFFFF`).

### Shape

| Element               | Radius          |
|-----------------------|-----------------|
| Cards, dock           | 12dp            |
| App tiles             | 9dp (baked into the bitmap, see 6) |
| Segmented control     | 9dp outer, 7dp segment |
| Pills (Open/Options)  | 18dp (full pill at 36dp height) |
| Quick buttons         | Circle, 42dp    |

### Type

System sans (Roboto on the box). Two weights only: regular and medium
(`sans-serif-medium`). Light (`sans-serif-light`) only for the big weather number.

| Use                 | Size  |
|---------------------|-------|
| Hero app name       | 36sp medium |
| Weather temperature | 42sp light |
| Status bar clock    | 20sp  |
| Body / values       | 14-16sp |
| Labels, category    | 13sp secondary |
| Dock tile name      | 12sp  |

Sentence case. No ALL CAPS, no letter-spacing tricks.

## 3. Layout

The screen is 960x540dp (1920x1080 at density 2). Outer padding is 32dp left and
right, 16dp top and 12dp bottom (TV overscan safe area).

From top to bottom:

1. **Status bar** (28dp): device name with a home icon on the left, clock in
   the center, temperature and Wi-Fi icon on the right.
2. **Top row** (228dp, 14dp gap):
   - **Hero card** (weight 1.75): background = dominant color of the focused app,
     animated. App icon top right (110dp). Bottom left: category, name, and the
     **Open** (blue) and **Options** pills.
   - **Right column** (weight 1): weather card above a system card (Wi-Fi band and
     strength, free memory with a 4dp progress bar).
3. **Middle row** (42dp, 14dp below): the segmented category control on the left
   and 6 round quick controls on the right (Wi-Fi, Bluetooth, free memory, screen
   saver, restart/about, settings).
4. **Dock** (bottom): bar with app tiles of 118x66dp in a horizontal scroll row.
   Under each tile: a 5dp blue dot and the app name, which show only on focus.
   Their space is always reserved so the layout does not jump.

## 4. Focus and motion

Focus is the most important visual state on a TV. Rules:

- **There is always exactly one focused view.** On start and on Home, focus
  goes to the first dock tile.
- **Dock tile focus:** scale 1.15, translate up 4dp, alpha 0.55 -> 1, dot and
  name visible. 150ms `ViewPropertyAnimator`.
- **Quick button focus:** background turns blue, icon turns white.
- **Quick button state:** a button that can be "on" (Wi-Fi, Bluetooth) shows a
  blue icon and a 4dp blue dot 4dp above the bottom of the circle when it is on
  and not focused. Focus wins: blue fill, white icon, no dot. States refresh
  every 5 s and on resume.
- **Pill focus:** 2dp white stroke, scale 1.05, 120ms.
- **Segmented control focus:** the selected segment turns blue. Left/Right
  changes the category at once.
- **Hero color:** ARGB `ValueAnimator`, 250ms, from the old color to the new one.
- Focused tiles stay centered in the dock with `smoothScrollTo`.
- Navigation map: Up from the dock goes to the middle row. Up from the middle row
  goes to the hero pills. Down goes back to the last focused dock tile. Set
  `nextFocus*` explicitly where the default focus search jumps diagonally.
- System animations on the target box run at 0.5x. Design for that, and do not
  depend on long animations.

## 5. Interaction

| Input                      | Action                              |
|----------------------------|-------------------------------------|
| OK on a tile, or Open      | Launch the app                      |
| Long-press OK, Menu, Options | App options                       |
| Back on home               | Nothing                             |
| Home while on home         | Reset to All and the first tile     |

App options open a bottom sheet (see "Bottom sheet" below). Rows: move left,
move right, category (shows the current one), hide from dock, app info, and
show hidden apps (shows the count). Back, Menu or Home closes the sheet.

### Bottom sheet

An in-activity overlay, not a dialog window. Implemented in `Sheet.kt`.

- **Scrim:** full screen, `#A6000000`, not focusable.
- **Panel:** 560dp wide, centered at the bottom, `C_CARD`, top corners 16dp,
  padding 16dp (sides, top) and 20dp (bottom). A 36x4dp drag handle (`#3A3D42`)
  sits at the top.
- **Header:** 64x36dp app tile, name 18sp medium, sub line 13sp secondary, then a
  1dp divider (`#2C2F33`) and a 10dp gap.
- **Row:** 44dp high, 18dp side padding, 9dp radius. 18dp icon, 12dp gap, 16sp
  label. Optional value (15sp secondary) and 16dp chevron on the right. Focused:
  `C_BLUE` fill, white label and icon, value `#DCE5FB`.
- **Focus:** the first row gets focus on open. Up on the first row and Down on the
  last row stay put. Left/Right do nothing unless the row sets a handler. The
  screen below is blocked from focus. Focus returns to the previous view on close.
- **Motion:** slide up 150ms with the scrim fading in; close takes 120ms.

## 6. Implementation rules (performance)

- **Plain Android Views built in code.** No Compose, no RecyclerView, no
  AndroidX UI libraries, no image loading libraries. The Kotlin stdlib is fine.
- **RAM budget: about 30 MB PSS** on the Mi Box 4 (v1 measures 29-30 MB).
  Measure with `adb shell dumpsys meminfo io.github.kobiregev.volt` after each
  visual change. Any feature that adds more than 5 MB needs a reason.
- Scale every banner or icon down to its display size once and cache it
  (`Visuals.cache`, an LruCache of 16 MB).
- **Bake corner radii into bitmaps.** View outline clipping does not survive
  the focus scale animation.
- Any parent of a view that scales on focus needs `clipChildren = false` **and**
  `clipToPadding = false`. Without them, the zoom is cut to the old size and
  the rounded corners look square. We hit this bug in v1.
- Timers (clock, system card every 5 s, weather every 30 min) run only while the
  activity is resumed.
- All disk, network and bitmap work runs on the single background executor.
  Post the results to the main thread.
- No personal data in the repo. The weather location comes from the untracked
  `local.properties` file, and the device name comes from system settings.

## 7. Process for design changes

1. Make an HTML mockup first and get the user's approval before you write code.
   Show variants when the direction is open.
2. Build, install on the real box, take a screenshot with `adb shell screencap`,
   and look at it. A green build is not enough.
3. Measure RAM. Test focus with real key events
   (`adb shell input keyevent KEYCODE_DPAD_*`).
4. Update this file and `CHANGELOG.md`.
