# Controller skin templates

One template per console, for anyone who wants to draw their own on-screen controller for OpenEmux on
Android. A template is a working skin package with plain placeholder art: every control is a white,
outlined shape with its name on it, every image is at the exact size the app reads, and every control
is already placed and bound. Repaint the images, zip the files again and install the result.

| Console | Template | Package extension |
|---|---|---|
| Famicom / NES | [`FC-template.zip`](FC-template.zip) | `.deltaskin` |
| Super Famicom / SNES | [`SFC-template.zip`](SFC-template.zip) | `.deltaskin` |
| Nintendo 64 | [`N64-template.zip`](N64-template.zip) | `.deltaskin` |
| Game Boy | [`GB-template.zip`](GB-template.zip) | `.manicskin` |
| Game Boy Color | [`GBC-template.zip`](GBC-template.zip) | `.deltaskin` |
| Game Boy Advance | [`GBA-template.zip`](GBA-template.zip) | `.deltaskin` |
| Nintendo DS | [`NDS-template.zip`](NDS-template.zip) | `.deltaskin` |
| GameCube | [`GC-template.zip`](GC-template.zip) | `.manicskin` |
| Mega Drive / Genesis | [`MD-template.zip`](MD-template.zip) | `.deltaskin` |
| Master System | [`SMS-template.zip`](SMS-template.zip) | `.manicskin` |
| PlayStation | [`PS-template.zip`](PS-template.zip) | `.manicskin` |
| PSP | [`PSP-template.zip`](PSP-template.zip) | `.manicskin` |

The other consoles the app runs (Atari 2600, 5200 and 7800, Lynx, ColecoVision, Famicom Disk System,
Game Gear, Intellivision, Neo Geo Pocket, Odyssey², SG-1000, 32X, Mega-CD, Saturn, PC Engine and
PC Engine CD, Vectrex, Virtual Boy, WonderSwan) still use the skin the app draws itself. Each gets a
template here when its bundled skin package is added: the templates are built from the same layouts.

## What is in a template

| File | What it is | Needed in the installed skin |
|---|---|---|
| `info.json` | The layouts: where each control and each screen sits, and what each control presses | yes |
| `phone_portrait.png`, `phone_landscape.png`, `tablet_portrait.png`, `tablet_landscape.png` | The art of each layout, buttons painted in. The screens are transparent holes | yes |
| `*_knob0.png`, `*_knob1.png` | A stick's knob (consoles with analog sticks): the app moves it with the thumb over the stick's well | yes, where `info.json` names it |
| `dpad_knob.png` | The D-pad's circle: the app stretches it over the D-pad's travel and moves it with the thumb | yes, where `info.json` names it |
| `sources/*.svg` | The editable source of each PNG, same name | no |
| `README.md` | That console's asset plan: every image, its size in pixels, the screens' frames and the controls | no |

The Nintendo DS template also has one art per screen layout (`*_top.png`, `*_bottom.png`, `*_side.png`),
under each layout's `layouts` key: the app shows the one for the layout the player picks.

## Layouts

The app reads four layouts from a package. On a device it picks the one that fits the screen:

| Layout | In `info.json` | Mapping size (units) | Image size |
|---|---|---|---|
| Phone, portrait | `representations.iphone.edgeToEdge.portrait` | 360 x 746 | 1080 x 2238 px (3 px per unit) |
| Phone, landscape | `representations.iphone.edgeToEdge.landscape` | 746 x 360 | 2238 x 1080 px |
| Tablet, portrait | `representations.ipad.standard.portrait` | 800 x 1280 | 1600 x 2560 px (2 px per unit) |
| Tablet, landscape | `representations.ipad.standard.landscape` | 1280 x 800 | 2560 x 1600 px |

- A device whose smallest side is 600 dp or more is a tablet; anything smaller is a phone.
- **Foldables** use the phone layouts folded and the tablet layouts unfolded. Half-open (tabletop),
  the app always uses its own drawn controls, so a skin has no tabletop art.
- The app stretches each image over the device's screen. Draw at the size above and keep the
  proportions: the positions in `info.json` are in mapping units, and the image covers the whole
  mapping size.

## Editing

1. Unzip the template.
2. Open an SVG from `sources/` in a vector editor (Inkscape, Figma, Illustrator...). Each control is a
   layer named after its input (`a`, `start`, `l2`, `dpad`, `analogStick`...), so you can restyle one at a
   time. Two hidden layers help: `screen-guides` (where the game's picture goes) and `touch-areas` (how
   far each control's touch area reaches). Leave them hidden when you export.
3. Export it to the PNG with the same name, at the size the template's `README.md` gives. Any editor that
   writes PNG works too: start from the PNG itself if you prefer.
4. Keep the screens transparent, and keep each button where `info.json` says it is: the art only shows the
   button, the touch is where the frame is.

To move or resize a control, change its `frame` (`x`, `y`, `width`, `height`, in mapping units) in
`info.json`, for every layout you change, and draw it in the new place. The in-app skin editor will do
this for you.

## Installing

1. Zip the files again, with `info.json` at the zip's root (not inside a folder). `sources/` and
   `README.md` can stay in or go.
2. Name the zip after the console with the package extension from the table above, for example
   `GBA.deltaskin` or `PS.manicskin`.
3. Copy it to `Android/data/com.openemux.mobile/files/skins/<console>/` on the device (for example
   `.../skins/GBA/GBA.deltaskin`) and start a game. The app uses the first package in that folder by
   name, over the bundled skin.
4. To go back to the bundled skin, delete the file.

From a computer with `adb`:

```sh
adb push GBA.deltaskin /sdcard/Android/data/com.openemux.mobile/files/skins/GBA/
```

## The format

A template is a [Delta](https://github.com/rileytestut/Delta) / Manic EMU skin package: a zip with an
`info.json` and the images it names. Skins made for those emulators work in OpenEmux too, and the
templates' `info.json` follows the same structure.

Top level:

| Key | Use |
|---|---|
| `name`, `identifier` | The skin's name and a unique id (reverse-domain style). Shown in logs only |
| `gameTypeIdentifier` | The console, as Delta or Manic EMU names it. The app takes the console from the folder instead |
| `representations` | The layouts (above) |

Each layout:

| Key | Use |
|---|---|
| `assets.large` | The art. `resizable` (a PDF) is read too, and wins over a PNG |
| `mappingSize` | The units every frame is in |
| `screens[].outputFrame` | Where the game's picture goes. Two entries for the Nintendo DS. Optional: without one, the controls sit under the picture in portrait and over it in landscape |
| `items[]` | The controls (below) |
| `extendedEdges` | How far, in units, every control's touch area reaches past its frame |
| `translucent` | `true` draws the art at 70% opacity |

Each control (`items[]`):

| Key | Use |
|---|---|
| `frame` | Where it is, in units |
| `inputs` | What it presses: a list of names for a button (two names press both), or `up`/`down`/`left`/`right` for a D-pad or a stick |
| `extendedEdges` | Its own touch reach, instead of the layout's: a side it leaves out reaches nothing past the frame |
| `thumbstick` | A stick's knob image (`name`) and size (`width`, `height`, in units) |
| `knob` | OpenEmux's own key: the D-pad's circle image. Other emulators ignore it |

OpenEmux's own keys for the Nintendo DS: `layouts` (the `top`, `bottom` and `side` screen layouts, each
with its own art, screens and items) and the `screens` input (the key that steps to the next layout).

### Input names

A button's input names, and what they press:

| Name | What it presses |
|---|---|
| `up`, `down`, `left`, `right` | The D-pad |
| `a`, `b`, `x`, `y`, `l`, `r`, `l2`, `r2`, `l3`, `r3`, `start`, `select` | That button (see the console table below for the ones that differ) |
| `l1`, `r1` | L and R |
| `menu` | The in-game menu |
| `quickSave`, `quickLoad` | Save and load the quick save state |
| `toggleFastForward`, `fastForward` | Fast forward, on and off or while held (left out with RetroAchievements hardcore) |
| `turbo` | Turbo for the next button pressed |
| `screenshot` | A screenshot |
| `restart`, `reset` | Reset the game |
| `screens` | Nintendo DS only: the next screen layout |

A D-pad or stick's directions:

| Directions | What it is |
|---|---|
| `up`, `down`, `left`, `right` | The D-pad |
| `analogStickUp`, `analogStickDown`, `analogStickLeft`, `analogStickRight` | The left analog stick |
| `rightAnalogStickUp`... or `cStickUp`... | The right analog stick (the GameCube's C stick) |

Consoles whose buttons have their own names:

| Console | Names |
|---|---|
| Nintendo 64 | `a`, `b`, `z`, `cUp`, `cDown`, `cLeft`, `cRight` |
| Mega Drive | `a`, `b`, `c`, `x`, `y`, `z`, `mode` |
| GameCube | `z`, `l`, `r` (the analog triggers) |

Any other name is ignored: the control is drawn but presses nothing. The app's table is
`launch/SkinBinds.kt`.

## Building the templates

The templates are built from the bundled skins' layouts, with the skins' build:

```sh
python3 tools/skins/build.py --templates          # all
python3 tools/skins/build.py --templates GBA PS   # some
```

See `tools/skins/README.md`. The unit test `SkinTemplatesTest` checks that each template has its
bundled skin's controls and screens, every image at its size with its source, and that every control
presses something.
