# OpenEmux Mobile

An Android frontend for the RetroArch engine, with the OpenEmux look:
games as cartridges, a shelf per console, per-console defaults and a pause
sheet of its own. It covers the desktop app's 31 systems (`data/Console.kt`),
each with its own controller skin (`data/Skin.kt`).

This directory lives inside a fork of RetroArch, published at
<https://github.com/guilhermefeitosa66/openemux-android> (branch
`openemux-mobile`); the `upstream` remote can fetch from libretro but its
push URL is disabled on purpose.

## Why not reskin RetroArch's own UI

On Android, everything RetroArch shows is drawn in C by its menu drivers
(Ozone, XMB, MaterialUI) inside a `NativeActivity`; the Java side is only
glue (permissions, vibration, keyboard). Matching the design there would
mean writing a new menu driver in C. So the split is:

| Layer | What | Where |
|---|---|---|
| Library, settings, import | Kotlin + Jetpack Compose | `src/main/java/com/openemux/mobile/ui`, `data` |
| Launch | Writes a per-game `retroarch.cfg`, shader preset, core options and the controller skin, then starts the engine | `launch/GameLauncher.kt`, `launch/SkinRenderer.kt` |
| Engine | RetroArch's `NativeActivity`, unchanged except for the hooks below | `game/GameActivity.kt` (extends `RetroActivityFuture`) |
| Pause sheet | Compose, in a dialog over the engine | `game/PauseSheet.kt` |

The engine runs in its own process (`:game`): RetroArch exits the process
when the content closes, and the library must survive that.

### The engine hooks (C)

1. **Menu → frontend.** When RetroArch would open its menu during play
   (the skin's menu button, a gamepad's menu combo), it calls
   `onExternalMenuRequested()` on the activity first. If that returns true,
   the content is paused and the RetroArch menu stays closed.
   `android_app_external_menu()` in `frontend/drivers/platform_unix.c`,
   called from `runloop.c` and `retroarch.c`.
2. **System BACK.** The navigation bar's BACK (device -1) asks for the menu
   during play instead of being registered as a gamepad, which used to
   hide the touch skin. `android_input_back_to_menu()` in
   `input/drivers/android_input.c`.
3. **Gamepad in the pause sheet.** While the content is paused, gamepad
   keys and sticks go to the activity's `onExternalMenuInput()` first; the
   sheet's window cannot take focus (the engine must keep it), so the
   activity drives the sheet with them. `android_input_to_external_menu()`
   in `input/drivers/android_input.c`, `android_app_external_menu_input()`
   in `frontend/drivers/platform_unix.c`.
4. **Overlay shown.** When the overlay changes (a skin key, or turning the
   phone), the activity's `onOverlayChanged()` gets its name. The NDS skin
   has one overlay per screen layout (`portrait@side`...): the activity sets
   DeSmuME's `desmume_screens_layout` to match through `SET_CORE_OPTION`
   and keeps each orientation's last layout. `android_app_overlay_changed()`
   in `frontend/drivers/platform_unix.c`, called from `CMD_EVENT_OVERLAY_NEXT`
   in `retroarch.c`. A video reinit reloads the overlay pack (DeSmuME asks
   for one when a screen gap or layout changes its geometry); the reloaded
   pack opens on the overlay that was shown, by name, instead of its first
   one, so the activity's idea of it stays true.
   `input_overlay_keep_active()` in `input/input_driver.c`.
5. **Toggles lit.** An overlay desc can name a frontend state its image
   shows (`overlayN_descM_lit_by = "fast_forward"`, `"turbo"`,
   `"slow_motion"` or `"anchor"`): the image is hidden while that state is off, with or
   without the overlay LED driver (`input/input_overlay_alpha.c`). When any
   of them changes, the activity's `onToggleStates()` hears it once, so the
   pause sheet shows the engine's fast forward and slow motion. `android_app_toggle_states()` in
   `frontend/drivers/platform_unix.c`, called from `input_overlay_states()`
   in `input/input_driver.c` on every input poll, whether or not an overlay
   is shown (a connected gamepad hides it).
   Fast forward and slow motion never run together: turning one on turns
   the other off, whatever control did it (`runloop.c`; a core's fast
   motion override is left alone).
   The fourth flag is anchor mode, which the activity keeps per console.
6. **D-pad dead zone.** An eight-way area (`dpad_area`, `abxy_area`) takes
   `overlayN_descM_dead_zone`, a radius as a fraction of the area's range
   inside which the touch holds the area but presses no direction. The
   skin's D-pad sets 0.25; the default, 0, is upstream behaviour.
   `task_overlay_desc_populate_eightway_config()` in `tasks/task_overlay.c`,
   `input_overlay_get_eightway_state()` in `input/input_driver.c`.
7. **Touch past the window's edge.** In landscape the game window stops
   short of a display cutout, and a finger held down and slid into that
   strip used to read as lifted on the screen pointer: the D-pad let go,
   and the touches after it were dropped. Its screen position is now held
   at the window's edge; the viewport position (a lightgun's offscreen
   test) is unchanged. `android_pointer_set()` in
   `input/drivers/android_input.c`.
8. **Closing the device.** When the activity is paused (a Flip closed, the
   screen off, Home), the engine writes the auto save state
   (`<rom>.state.auto`) before acknowledging `APP_CMD_PAUSE`, so it is on
   disk before Android can reclaim the process. Not on `APP_CMD_STOP`: by
   then the window is going away, and the runloop can be held until the
   app comes back. On the next focus the content stays paused and the
   activity's `onResumedIntoPause()` opens the pause sheet; a focus never
   lifts a pause it did not make, so a sheet left open keeps the game
   paused. Asked once at start-up through `autoSaveOnPause()`; skipped
   (pause only) in hardcore and for cores without save states.
   `APP_CMD_PAUSE` / `APP_CMD_GAINED_FOCUS` and
   `android_input_flush_pending_state()` in `input/drivers/android_input.c`,
   `retroarch_save_auto_state_for_suspend()` in `retroarch.c`,
   `android_app_resume_into_menu()` in `frontend/drivers/platform_unix.c`;
   the handshake is reproduced in `samples/input/android_lifecycle_flush/`.
9. **Anchor mode.** While `input_overlay_anchor` is on, the first face,
   shoulder or trigger button a finger presses stays held until that finger
   lifts; any other button counts only while the finger is over it. A desc
   with `overlayN_descM_anchor_toggle = true` (written as a `nul` key by the
   app's renderers, never by a skin file) flips the mode on its press.
   `input/input_overlay_anchor.c`, called from `input_overlay_poll()`;
   reproduced in `samples/tasks/overlay/overlay_anchor_test.c`
   (`make -f Makefile.anchor check`).

Upstream behaviour is kept: the base `RetroActivityCommon` returns false
from the first two and from `autoSaveOnPause()`, and ignores the overlay
change and the toggles, so the stock RetroArch app still opens and drives
its own menu.

Three config-only keys (not in the menu; their defaults keep upstream
behaviour) are set by the generated `retroarch.cfg`:
`input_overlay_anchor` starts anchor mode as the console's settings have it,
`input_turbo_locked = true` makes turbo, once toggled, act as if its bind
were held (the skin's bolt), and `notification_show_autoload_state = false`
mutes the message when Continuar loads the automatic state, while saving and
loading from the action strip still report.

### Frontend → engine

The pause sheet talks to the running engine over RetroArch's network
command interface (`network_cmd_enable`, UDP 127.0.0.1:55355):
`SAVE_STATE_SLOT`, `LOAD_STATE_SLOT`, `FAST_FORWARD`, `SCREENSHOT`,
`SET_SHADER`, `MUTE`, `RESET`, `QUIT`, `GET_STATUS`. No JNI needed, and the
engine polls it on its own thread, paused or not. The sheet's window is
not focusable, because an unfocused engine stops polling.

One command is OpenEmux's own: `OVERLAY_LOAD <absolute path>` (`command.c`)
loads another overlay file while the core runs. When the game's window
changes size (a Fold unfolded, split screen, a free-form resize),
`GameActivity` redraws the skin for the new window into a fresh
`launch/current/skin-<n>` directory and swaps it in with it; RetroArch
behaves as before when the command is never sent.

### Controller skin

`data/Skin.kt` holds each console's skin as the design draws it: a layout
family (D-pad + A/B, + A/B/X/Y + L/R, 6 buttons, analog, two screens), the
console's colors, its face buttons, shoulders and console keys, each bound to
a RetroPad button, an analog stick or a hotkey. `SkinRenderer` draws it at
the screen's exact pixel size (portrait and landscape) and writes a RetroArch
overlay with those touch areas, plus `menu_toggle` and the quick actions
(`save_state`, `load_state`, `turbo_fire_toggle`, `toggle_slowmotion`,
`toggle_fast_forward`). Turbo, slow motion and fast forward each get a `nul`
desc over their button with the "on" pill as its image and `_lit_by` naming
the state, so the engine shows the pill exactly while the toggle is on (never
slow motion's or fast forward's in RetroAchievements hardcore, where their
buttons are greyed out). Slow motion runs at RetroArch's default
`slowmotion_ratio` (a third of the speed), like the desktop app.
Each overlay also sets where the game is drawn (`overlayN_viewport`, which
RetroArch fits keeping the aspect): on top in portrait, capped like the
design, and centred between the control sides in landscape, with the quick
actions under the picture in both.

The twelve best-known consoles (NES, SNES, N64, GameCube, Game Boy, Game Boy
Color, Game Boy Advance, NDS, Mega Drive, Master System, PlayStation, PSP)
use skins with drawn art instead, in the package format of the iOS
emulators Delta and Manic EMU (`.deltaskin`/`.manicskin`: a zip with
`info.json` and the art). They ship in `assets/skins` and
`launch/ImportedSkinRenderer.kt` turns one into the same kind of overlay:
the phone or tablet art (tablet from 600 dp on the smallest side) scaled to
the screen, the game's screens cut out of it as the viewport, and each item
a touch area, with each console's button names translated to the RetroPad
as `data/Skin.kt` does. A skin in `files/skins/<console>/` takes the
bundled one's place, and one that fails to load falls back to the drawn
skin. Nineteen more consoles (Game Gear, SG-1000, 32X, Mega-CD, Saturn, PC
Engine, PC Engine CD, Famicom Disk System, Atari 2600, Atari 7800, Lynx, Neo Geo
Pocket, WonderSwan, Vectrex, Virtual Boy, Atari 5200, ColecoVision,
Intellivision, Odyssey 2) ship their skins in OpenEmux's
own format instead (`docs/skin-format.md`: `assets/skins/<console>.zip`,
six layouts with a foldable's among them, and for the last four a keypad
page per layout that their KEYPAD key opens), drawn by
`launch/OpenEmuxSkinRenderer.kt` like a user's skin in that format. The NDS skin draws its two screens one over the other, and DeSmuME is
set to that layout. An item bound to turbo or toggle fast forward gets the
same lit `nul` desc as the drawn strip: the accent pill over the item's
frame, with the skin's own glyph lifted off the art (what differs from the
colour around it) and drawn on it in white, so a user's skin lights up too.
The packages have no slow-motion item, so the renderer makes one right
before fast forward (`launch/SlowMotionSlot.kt`): the keys edge to edge with
fast forward on its row (the bundled skins' save, load, turbo, fast forward)
are spread over the same span in one more cell, their glyphs lifted off the
art and drawn again at their new places, and slow motion's ring is drawn in
fast forward's colour; a lone fast-forward button is split in two. In
RetroAchievements hardcore both speed keys are drawn at 0.45 with no touch
area.
`launch/OverlayWriter.kt` writes the overlays and their images for both
renderers, each stick's movable knob (`<overlay>-knob<N>.png`) and its
widened touch zone included.

OpenEmux also has a skin format of its own, the one the skin editor and the
templates use: a folder (zipped to move it around) with a `skin.json` and up
to six layouts (phone, tablet and fold, each upright and sideways), one image
per element, any of them optional. `docs/skin-format.md` documents it;
`data/SkinFormat.kt` reads, checks and writes it into the model in
`data/OpenEmuxSkin.kt`. Both formats name inputs with one table,
`launch/SkinBinds.kt`.

In that format every image holds its own element and nothing else: the
background is the console's body alone, and a button is its face over a relief
(`depth` units of the same shape showing under it, in its `relief` colour, which
follows the face's when unset), with a label the app can draw itself where
`labelOffset` puts it (`START` under its key). That is what lets the skin editor
move a control without leaving a copy of it behind, repaint a button with its
relief, and select, move and paint a label on its own. A control drawn from its
colours is shaded the way the bundled art is, so one repainted still sits among
the ones left as they were. The skin list shows each skin as a miniature drawn
from the skin itself for this device and the way it is held
(`ui/SkinThumbnail.kt`), kept in memory and in the cache folder.

The editor's Combo tool adds a button of the player's own that presses two or
three buttons together (`data/SkinCombo.kt`): held for a second on the skin or
on a controller, or picked by tapping, it lands in every layout at a free spot
near the buttons it presses. In memory it is a button bound to its inputs
joined with `|` (`"y|b"`), which the overlay engine already presses together;
in `skin.json` it lives under new `combos` keys and never in `elements`, so an
app from before combos opens the skin without them instead of refusing it.

Skin creators start from the templates in `tools/skins/templates/`: one zip
per console with a bundled skin, in this format, with placeholder art at the
exact sizes, and a guide (`tools/skins/templates/README.md`) to the files,
layouts, colours, binds and the install step. `ASSET-PLAN.md` there lists the
elements of a skin for every console the app runs.

One of the player's own skins can go to another Android device on the local
network: "Share" in its actions sheet opens Share nearby with that one skin
(`share/SkinShare.kt`). It travels as a zip of the skin's folder, an item of
kind `skin` in an entry with no ROM, and the other device imports it as a new
skin of its own for the same console, which is not put in use by arriving. The
desktop app has no skins, so a computer cannot be picked; an OpenEmux from
before the kind answers it `skip`, like any kind it does not know.

On a foldable, `SkinRenderer` also draws a tabletop shape for the half-open
posture with the fold across the screen: the picture on the top half, the
controls on the bottom one, nothing within 12 dp of the fold, and on NDS one
screen per half (DeSmuME's stacked layout with `desmume_screens_gap` over the
fold). These `portrait-tabletop` overlays go after the others in the same
`skin.cfg`, and `GameActivity` switches to them and back by name
(`OVERLAY_SELECT`) as the posture changes, without restarting the core. The
packaged skins have no tabletop art, so in tabletop every console uses the
drawn skin, the twelve above included: their `skin.cfg` gets the drawn
tabletop overlays after the package's own, and opening the device flat brings
the package skin back (on NDS with its one-over-the-other layout). A skin
redrawn for a new window (`OVERLAY_LOAD` above) is drawn for the fold the
game sees then, and one drawn before the fold showed up is drawn again for
it.

The art comes from a design canvas whose Skin component holds the layout
engine and each console's colors; `tools/skins/build.py` renders it with
headless Chrome into the packages (see `tools/skins/README.md`).

### Cartridges, labels and box art

Two different assets, as on the desktop app:

- **Labels** go inside the cartridge frame (the desktop app's art,
  `OpenEmux/.../images/cartridges`; the color variants, a colorize filter in
  the SVG, are baked into PNGs). They come from ScreenScraper's
  `support-texture` media, looked up by ROM hash, one request a second, and
  are kept in `files/labels/<console>/` (a `.none` file marks a game with no
  label). A cartridge without one shows a blank label with the title.
- **Box art** is shown in the covers and list modes, at its own aspect ratio.
  It comes from the `openemux-artwork` mirror, matched by name against the
  No-Intro list shipped in `assets/covers`, and is prefetched into the image
  cache.

Both syncs run in the background with a progress pill on the shelf and the
library (`ui/ArtworkSync.kt`); they pause without a connection or when the
ScreenScraper quota runs out, and resume on their own.

ScreenScraper needs a developer credential: put `screenscraper.devid` and
`screenscraper.devpassword` in `local.properties` (untracked). Without it,
cartridges keep blank labels.

### Shaders

GLSL presets from `libretro/glsl-shaders` (Dot, Sharp Bilinear, zfast CRT,
CRT Geom), applied per game through an auto `global.glslp` next to the
generated config; the pause sheet swaps them live with `SET_SHADER`.

## Building

Needs the Android SDK (platform 36), NDK 28 and JDK 17.

```sh
tools/build.sh              # engine (arm64-v8a, x86_64) + APK
./gradlew testDebugUnitTest --no-daemon   # JVM unit tests (src/test)
```

CI (`.github/workflows/Android.yml`, see `.github/CI.md`) runs the unit tests,
`lintDebug` and a compile check of the engine; it builds no APK.

The APK ships no cores. On first open, `data/Setup.kt` downloads from the
RetroArch buildbot every core `data/Console.kt` names (for the device's ABI),
`info.zip` and the PPSSPP and Dolphin system files, and the app stays on that
screen until it finishes. `tools/bake-frames.py` turns the desktop's cartridge
SVGs into the PNGs in `res/drawable-nodpi`, except for the consoles
`tools/render-cartridge-frames.py` renders procedurally (GB, GBC, FC, VB, SMS, GG,
SG1000, S32X, A2600, A5200, A7800, LYNX, PCE, CV, INTV, NGP, O2, VECTREX, WS,
N64, GBA, MD, NDS),
whose WebP frames come from that generator.

Data lives in `Android/data/com.openemux.mobile/files/` (`roms/<console>`,
`states`, `saves`, `system` for BIOS, `screenshots`).

Release builds (`./gradlew assembleRelease`) are signed with the key named by
`openemux.signing` in `local.properties` or `~/.gradle/gradle.properties`: the
path of a properties file kept outside the repository, with `storeFile`,
`storePassword`, `keyAlias` and `keyPassword`. Without it they fall back to the
debug key, with a warning. An APK signed with another key does not install over
this one: the debug build has to be uninstalled first, and that removes the
data above with it.

## Versioning

The version follows the OpenEmux desktop release it pairs with:
`openemuxVersion` in `gradle.properties` sets `versionName`, and
`versionCode` is `major * 10000 + minor * 100 + patch`.

On start the app asks GitHub for the latest OpenEmux release
(`data/Updates.kt`) and, when it is newer than `versionName`, shows a banner
on the shelf that opens the release page. "Check for updates" in Settings
turns the request off. `-PopenemuxVersion=1.0.0` builds an "old" APK to see it.

## Not yet supported

- Cheats (shown as coming soon).
- Picking cores other than the ones `data/Console.kt` names; the setup
  downloads exactly those.
