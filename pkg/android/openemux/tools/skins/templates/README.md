# Making a controller skin for OpenEmux

These are the templates for skin creators: one zip per console with a bundled skin, in OpenEmux's own skin format
(the format's reference: [`docs/skin-format.md`](../../../docs/skin-format.md)). Each one is the bundled skin's
layout with plain placeholder art, every image at its exact size, so repainting the images is all a skin needs.

| Console | Template |
|---|---|
| NES / Famicom | [`FC-template.zip`](FC-template.zip) |
| Super Nintendo | [`SFC-template.zip`](SFC-template.zip) |
| Nintendo 64 | [`N64-template.zip`](N64-template.zip) |
| Game Boy | [`GB-template.zip`](GB-template.zip) |
| Game Boy Color | [`GBC-template.zip`](GBC-template.zip) |
| Game Boy Advance | [`GBA-template.zip`](GBA-template.zip) |
| Nintendo DS | [`NDS-template.zip`](NDS-template.zip) |
| GameCube | [`GC-template.zip`](GC-template.zip) |
| Mega Drive | [`MD-template.zip`](MD-template.zip) |
| Master System | [`SMS-template.zip`](SMS-template.zip) |
| PlayStation | [`PS-template.zip`](PS-template.zip) |
| PSP | [`PSP-template.zip`](PSP-template.zip) |
| Game Gear | [`GG-template.zip`](GG-template.zip) |
| SG-1000 | [`SG1000-template.zip`](SG1000-template.zip) |
| 32X | [`S32X-template.zip`](S32X-template.zip) |
| Mega-CD | [`MCD-template.zip`](MCD-template.zip) |
| Saturn | [`SATURN-template.zip`](SATURN-template.zip) |
| PC Engine | [`PCE-template.zip`](PCE-template.zip) |
| PC Engine CD | [`PCECD-template.zip`](PCECD-template.zip) |
| Famicom Disk System | [`FDS-template.zip`](FDS-template.zip) |
| Atari 2600 | [`A2600-template.zip`](A2600-template.zip) |
| Atari 7800 | [`A7800-template.zip`](A7800-template.zip) |
| Lynx | [`LYNX-template.zip`](LYNX-template.zip) |
| Neo Geo Pocket | [`NGP-template.zip`](NGP-template.zip) |
| WonderSwan | [`WS-template.zip`](WS-template.zip) |
| Vectrex | [`VECTREX-template.zip`](VECTREX-template.zip) |
| Virtual Boy | [`VB-template.zip`](VB-template.zip) |

The other consoles have no template yet (their bundled skins come in later batches). [`ASSET-PLAN.md`](ASSET-PLAN.md)
lists, for all 31 consoles the app runs, the elements a skin has, so the art can be planned before a template exists.

## What is in a template

```
GBA-template/
├── skin.json            the skin: every control placed, grouped, bound and coloured
├── README.txt           this console's asset plan: every file, its pixel size, its bind
├── phone-portrait/      one folder per layout, one PNG per element
│   ├── background.png
│   ├── frame.png
│   ├── dpad.png
│   ├── dpad-ball.png
│   ├── a.png
│   └── …
├── phone-landscape/
├── tablet-portrait/
├── tablet-landscape/
├── fold-portrait/
├── fold-landscape/
└── sources/             one SVG per layout with its images put together (optional)
```

`README.txt` and `sources/` are for you; the app ignores them, and any other file it does not know.

## Layouts and forms

A skin has up to six layouts: a phone, a tablet and an unfolded foldable, each upright and sideways. The layout's id
is its folder's name. A template has all six; a skin may drop any of them by deleting its entry from `layouts` in
`skin.json` (and its folder), as long as one is left. The app draws a form only when the skin has both of its
layouts, and falls back to the bundled skin otherwise.

Each layout has its own pixel space, `size` in `skin.json`, which the app scales to the screen keeping its
proportions. The templates use these sizes:

| Layout | Size (px) | Made for |
|---|---|---|
| `phone-portrait` | 1080 × 2238 | a 1080p phone |
| `phone-landscape` | 2238 × 1080 | |
| `tablet-portrait` | 1600 × 2560 | a 10" tablet |
| `tablet-landscape` | 2560 × 1600 | |
| `fold-portrait` | 1812 × 2178 | about a Galaxy Z Fold's inner screen (1812 × 2176) |
| `fold-landscape` | 2178 × 1812 | |

Everything in a layout (screens, frame, controls) is a rect in that space, `x`, `y`, `width`, `height` from its
top-left corner, in whole pixels in the templates.

## Images

One PNG per element, at the element's size in its layout. **Every image is optional**: delete one and the app draws
that element from its colours, shape and label in `skin.json` instead, so a skin can be as little as a background.

| File | Size | What it is |
|---|---|---|
| `background.png` | the layout's `size` | the console's body, behind everything: no control and no word beside one |
| `frame.png` | the frame's rect | the bezel around the game screen, where the quick actions sit |
| `<id>.png` | the element's rect | a button, a quick action or a keypad key |
| `dpad.png` | the D-pad's rect | the D-pad |
| `dpad-ball.png` | the D-pad's side × 44/60, square | the ball that moves with the thumb, edge to edge (the app fits the whole image to the ball), drawn centred on the pad |
| `stick-base.png` | the stick's rect | an analog stick's well |
| `stick-knob.png` | half the stick's side, square | the knob that moves with the thumb |
| `keypad/background.png` | the layout's `size` | the keypad page, behind everything (consoles with a keypad) |
| `keypad/<id>.png` | as above | the keypad page's controls: its keys, the toggle, the D-pad or stick, the quick actions |
| `top/`, `bottom/`, `side/` | as above | the DS only: each screen-layout page's own `background.png`, `frame.png` and controls (below) |

`<id>` is the element's `id` in `skin.json` (`a`, `start`, `l2`, `c-up`, `save`, `fast-forward`…): the file is
named after it. The game's screens are left clear whatever the background has there. `README.txt` in each template
lists every file of every layout with its exact size.

Paint with transparency where the shape ends: the app draws each image as it is, over the background. Keep the
names and the sizes; an image of another size is stretched to its element.

## skin.json

The templates' `skin.json` is complete: you only edit it to move or resize a control, change its colours, or drop
a layout. The keys are documented in [`docs/skin-format.md`](../../../docs/skin-format.md); the ones you meet:

- **`console`**, **`name`**: the console's id and the skin's name in the skin list. Rename it.
- **`layouts.<id>.screens`**: where the game's picture goes (two on the DS, the top one first).
- **`layouts.<id>.frame`**: the bezel's rect and its colours: `frame` (the bezel), `icons` (the quick actions'
  icons), `active` (a toggled action's background) and `activeIcon` (`light` or `dark`).
- **`layouts.<id>.elements`**: the controls. Each has an `id`, a `type`, a `bind`, a `group` and its rect, and,
  for when it has no image, a `shape` (`circle`, `pill`, `rect`), a `label` and its `colors`. A button's rect is the
  whole button: its face and, under it, `depth` px of relief (the `relief` colour), so paint the relief into its image
  too. A word printed beside a control (`START` under its key) is not painted anywhere: it is the element's `label`,
  placed by `labelOffset` and sized by `labelSize`, and `"art": "key"` tells the app to draw it over your image. Paint a
  label of your own into an image only where the element says nothing of `art`.

Moving a control means changing its `x` and `y`, and resizing it its `width` and `height` (then repaint its image at
the new size). A rect must stay inside its layout.

### The DS's screen layouts

The DS template's layouts each have three more pages under **`layouts.<id>.screenLayouts`**: `top` (the top screen
only), `bottom` (the bottom screen only) and `side` (both, side by side). The layout itself is the default page,
the two screens one over the other. In game the `screens` key steps through them, and the core shows the screens as
the page does; the pause menu's screen layout switches to its page too. Each page has its own `screens` (one rect, or
two for `side`), `background`, `frame` and `elements`, and its images are in the layout's folder of the page's id
(`phone-portrait/side/a.png`), at the same sizes as the layout's. Keep a `screens` key on every page, or the player
cannot step on. A skin with pages keeps all three in every layout (or drops `screenLayouts` altogether, and then the
screens stay one over the other); the skin editor edits the default page and keeps the others as they are.

### Types and colour parts

| Type | What it is | Colour parts |
|---|---|---|
| `button` | face buttons, shoulders, triggers, Start, Select, the menu key | `button`, `letter` |
| `dpad` | the D-pad | `pad`, `ball` |
| `stick` | an analog stick | `base`, `knob` |
| `action` | a quick action on the frame (save, load, turbo, fast forward…) | none: the frame's `icons` and `active` |
| `keypad` | a key of a numeric keypad | `button`, `letter` |

Colours are `#RRGGBB`, or `#RRGGBBAA` with an alpha. The templates carry the bundled skin's colours, so an element
without an image looks like the bundled skin's.

### Groups

`group` is free text: the skin editor moves the elements of one group together. The templates use `dpad`, `face`,
`shoulders-left`, `shoulders-right`, `center` (Start, Select), `menu`, `stick`, `stick2` and `actions`.

### Binds

`bind` is the input the element presses; [`ASSET-PLAN.md`](ASSET-PLAN.md) has every console's. Each type takes its
own names:

- **`button`**: `up`, `down`, `left`, `right`, `a`, `b`, `x`, `y`, `l`, `r` (also `l1`, `r1`), `l2`, `r2`, `l3`,
  `r3`, `start`, `select`, and any quick action's name. The console's own names mean its own buttons:
  - **N64**: `a`, `b`, `z`, `l`, `r`, `start`, and `cUp`, `cDown`, `cLeft`, `cRight` for the C buttons.
  - **Mega Drive**: `a`, `b`, `c`, `x`, `y`, `z` and `mode`, the six-button pad's own.
  - **GameCube**: `l`, `r` the analog triggers, `z` the Z button.
  - Every other console: the names its template (or its asset plan) gives.
- **`action`**: `quickSave`, `quickLoad`, `turbo`, `slowMotion`, `fastForward` (held), `toggleFastForward`,
  `screenshot`, `menu`, `restart`, and on the DS `screens` (the next screen layout).
- **`dpad`**: `dpad`. **`stick`**: `analogStick`, `rightAnalogStick`. **`keypad`**: `keypad0` … `keypad9`,
  `keypadStar`, `keypadHash`.
- **`keypadToggle`** (a `button`): opens the layout's keypad page, and on that page goes back to the controls.

### The keypad page

The consoles whose games need the controller's keypad (Atari 5200, ColecoVision, Intellivision, Odyssey 2) have a
second page in each layout, under `keypad` in `skin.json`: the same size, screens and frame, with controls of its own
(the twelve keys in the face buttons' place, the D-pad or stick, the quick actions and the `keypadToggle` key) and a
`background.png` of its own, all in the layout's `keypad/` folder. `sources/<layout>-keypad.svg` puts it together. The
app uses the keypad pages only when both layouts of a form have one with a `keypadToggle` key on it.

## Editing

1. Unzip the template. It gives one folder, `<console>-template/`; rename it to your skin's name and set `name` in
   `skin.json`.
2. Repaint the images, keeping their names and sizes. Any editor that writes PNG with transparency will do.
   `sources/<layout>.svg` puts a layout's images together, one layer each (and a hidden `screens` layer showing
   where the game goes): open it in Inkscape or Figma to paint a layout in context, then export each layer to its
   file at its size.
3. Delete the images you do not want: those elements are drawn from their colours.
4. Delete the layouts you do not make, from `layouts` in `skin.json` and their folders; keep both of a form's
   layouts (portrait and landscape) for that form to be used.

## Installing

Zip the skin's folder and import it: Settings → the console → Controller skins → "Import .zip", as a new skin (with
a name) or over one of your skins. A zip for another console, a broken `skin.json` or a zip with paths that leave its
folder is refused, and nothing is written. Only `skin.json` and the images it names are kept (this README and
`sources/` are not), and a layout whose folder is missing from the zip is no longer offered. "Download as template" in
one of your skins' sheet gives the zip back, with an image for every element, to repaint again.

Or copy the folder to the device by hand:

1. Copy the skin's folder into `Android/data/com.openemux.mobile/files/skins/<console>/`, so that
   `files/skins/GBA/my-skin/skin.json` exists (with a USB cable, a file manager, or
   `adb push my-skin /sdcard/Android/data/com.openemux.mobile/files/skins/GBA/`).
2. Start a game for that console: the skin is drawn in place of the bundled one.
3. Delete the folder to go back to the bundled skin. A skin the app cannot read (a typo in `skin.json`) is skipped
   the same way, and the reason is logged.

## Regenerating the templates

The templates are generated, not hand-made, from the bundled skins' layout engine (`../Skin.dc.html`):

```sh
python3 tools/skins/build.py --templates          # all twelve
python3 tools/skins/build.py --templates GBA PS   # some
```

It needs Node and Pillow (`pip install pillow`). A console gets a template as soon as its layout is in the engine.
The zips are deterministic: a rebuild that changes nothing leaves git clean. `SkinTemplatesTest` checks each one
against the app's reader and the bundled skin, and `SkinAssetPlanTest` keeps `ASSET-PLAN.md` up to date.
