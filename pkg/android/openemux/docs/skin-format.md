# OpenEmux skin format

This is the format of an OpenEmux controller skin: what the skin editor saves, what "Download as template" gives, and
what the skin templates ship in. The app reads it with `data/SkinFormat.kt` (model in `data/OpenEmuxSkin.kt`); this
document and that reader say the same thing, and a change to one is a change to the other.

Every skin the app ships is in this format (issue 808 moved the last Delta package, the DS's). The app still reads the
skins a player imports in the Delta and Manic EMU package format (`.deltaskin`, `.manicskin`) as before; that is a
separate format.

Format version: **1**.

## The package

A skin is a folder. To move it around (import, export, share) the folder is zipped: the zip may hold the files at its
root or inside one folder (what zipping the folder itself gives).

```
my-skin/
├── skin.json              required: the skin's description (below)
├── README.txt             optional, ignored by the app
├── phone-portrait/        one folder per layout the skin offers
│   ├── background.png
│   ├── frame.png
│   ├── dpad.png
│   ├── dpad-ball.png
│   ├── a.png
│   └── …
│   └── keypad/            optional: the layout's keypad page (below)
│       ├── background.png
│       ├── key-1.png
│       └── …
│   └── top/, bottom/, side/  NDS, optional: the layout's screen-layout pages (below)
│       ├── background.png
│       ├── frame.png
│       └── …
├── phone-landscape/
└── …
```

Files the app does not know are ignored. Nothing in the zip is extracted: the app reads the entries in place, and an
entry whose name starts with `/`, contains a `\` or a `..` segment is ignored, and so is the `__MACOSX/` folder macOS
adds when it zips a folder.

### Layouts and forms

A skin has up to six layouts, one per device form and orientation. The layout id is also the name of its folder.

| Layout id | Form | Orientation |
|---|---|---|
| `phone-portrait` | Phone | upright |
| `phone-landscape` | Phone | sideways |
| `tablet-portrait` | Tablet | upright |
| `tablet-landscape` | Tablet | sideways |
| `fold-portrait` | Fold | upright |
| `fold-landscape` | Fold | sideways |

**The `layouts` object of `skin.json` decides which layouts the skin offers.** A layout with no entry there is not
offered, even if its folder exists; a layout with an entry and no folder is offered and drawn entirely from its
colours. The forms a skin is made for (the Phone, Tablet and Fold badges) follow from its layouts: a form is offered
when at least one of its two layouts is. No skin has to cover every form, but it must have at least one layout.

### Images

Each layout folder holds **one image per element**, PNG, at the element's exact size in the layout's pixel space
(an element 180 × 180 in the layout is a 180 × 180 image). **Every image is optional**: a missing one is drawn from
the element's colours, shape and label.

**Each image holds its own element and nothing else.** The background is the console's body alone (its plastic, a
panel, a logo): no control, no shadow of one and no word printed beside one, so a control moved in the editor leaves
nothing behind. A button's image is the whole button, its relief included, inside the element's rect.

Any image may be **WebP** instead: `<name>.webp` in place of `<name>.png` (`a.webp` for `a.png`,
`keypad/background.webp`), read, drawn, copied and imported the same way. Each image is one or the other: a layout
with both `<name>.png` and `<name>.webp` is refused (`DUPLICATE_ID`). The names below say `.png` for either. The app's
bundled colour variants use lossy WebP (with lossless alpha) to keep the APK small; what the app writes itself (a
converted or drawn copy, a template's images) is PNG.

| File | What it is |
|---|---|
| `background.png` | the console's body, behind everything (the layout's `size`) |
| `frame.png` | the bezel around the game screen, where the quick actions sit (the frame's rect; transparent outside its rounded corners) |
| `<id>.png` | a button, a keypad key or a quick action (what it holds of a button or key: the element's `art`) |
| `<id>.png`, `<id>-ball.png` | a D-pad: the pad, and the ball that moves like a stick (`dpad2.png`, `dpad2-ball.png` for a second D-pad) |
| `<id>-base.png`, `<id>-knob.png` | an analog stick: the base, and the knob that moves |

`<id>` is the element's `id` (below), so the usual names are `dpad.png`, `dpad-ball.png`, `stick-base.png`,
`stick-knob.png` (`stick2-base.png`, `stick2-knob.png` for a second stick), `a.png`, `b.png`, `x.png`, `y.png`,
`l.png`, `r.png`, `l2.png`, `r2.png`, `start.png`, `select.png`, the keypad's `key-1.png` … `key-9.png`,
`key-0.png`, `key-star.png`, `key-hash.png`, and the quick actions' `save.png`, `load.png`, `turbo.png`,
`slow-motion.png`, `fast-forward.png`, `menu.png`… An element's `image` key replaces `<id>` in those names, and the
background's and the frame's `image` keys replace `background` and `frame` (for instance to share one file between
layouts' names, or to keep a texture's own name). Within a layout, no two images may end up with the same file name.

A texture picked in the editor (stretched, fitted or repeated, moved, zoomed) is baked into the element's image at
its exact size: the format carries the finished picture, not the editor's fill settings. On a button, a key or the
D-pad the picture fills the face alone, and the element's `art` becomes `face`: the relief and the label stay the
app's to draw, in their own colours.

## skin.json

UTF-8 JSON, one object, at most 1 MiB. Keys the app does not know are ignored (and not written back). A key set to
`null` reads as left out.

```json
{
  "format": 1,
  "console": "GBA",
  "name": "Clear purple",
  "forms": ["phone"],
  "layouts": {
    "phone-portrait": {
      "size": { "width": 1080, "height": 2340 },
      "screens": [{ "x": 0, "y": 180, "width": 1080, "height": 720 }],
      "background": { "color": "#2B1F3A" },
      "frame": {
        "x": 0, "y": 140, "width": 1080, "height": 860, "radius": 30,
        "colors": { "frame": "#1A1324", "icons": "#FFFFFF", "active": "#7C4DFF", "activeIcon": "light" }
      },
      "elements": [
        { "id": "dpad", "type": "dpad", "group": "dpad", "x": 60, "y": 1320, "width": 420, "height": 432, "depth": 12,
          "colors": { "pad": "#3A2C4F", "ball": "#9E8CB8", "relief": "#1F1729", "letter": "#9E8CB8" } },
        { "id": "a", "type": "button", "bind": "a", "group": "face", "x": 840, "y": 1380, "width": 180, "height": 192,
          "shape": "circle", "depth": 12, "label": "A", "colors": { "button": "#7C4DFF", "relief": "#472C94", "letter": "#FFFFFF" } },
        { "id": "b", "type": "button", "bind": "b", "group": "face", "x": 620, "y": 1500, "width": 180, "height": 180,
          "shape": "circle", "label": "B" },
        { "id": "start", "type": "button", "bind": "start", "group": "center", "x": 560, "y": 2100, "width": 160,
          "height": 66, "shape": "pill", "depth": 6, "label": "START", "labelSize": 30, "labelOffset": { "x": 0, "y": 66 },
          "art": "key" },
        { "id": "save", "type": "action", "bind": "quickSave", "x": 40, "y": 920, "width": 64, "height": 64 }
      ]
    }
  }
}
```

### Top level

| Key | Type | Required | Meaning |
|---|---|---|---|
| `format` | integer | yes | The format version. `1`; any other value is refused. |
| `console` | string | yes | The app's console id: `FC`, `SFC`, `N64`, `GC`, `GB`, `GBC`, `GBA`, `NDS`, `MD`, `SMS`, `PS`, `PSP`, … (`Console.id` in `data/Console.kt`). |
| `name` | string | yes | The skin's name as the skin list shows it. Not empty. |
| `forms` | array of strings | no | `phone`, `tablet`, `fold`. Informative only (for people and tools reading the file): the app derives the forms from `layouts` and writes the derived list back. |
| `combos` | array | no | The combo buttons the player made in the editor (below): each one's `id` and the inputs it presses. |
| `layouts` | object | yes | Layout id → layout (below). At least one entry; keys must be the six layout ids above. |

### Layout

| Key | Type | Required | Meaning |
|---|---|---|---|
| `size` | object | yes | `width`, `height`: whole numbers above 0. The layout's pixel space: every rect below is in these units, and the images are at this scale. The renderer scales the layout to the screen. |
| `screens` | array of rects | yes | Where the game's picture goes. Exactly one rect; exactly two for `NDS` (top screen first). |
| `background` | object | no | `color` (colour) and `image` (base name, default `background`). |
| `frame` | object | no | The bezel around the game screen: a rect (`x`, `y`, `width`, `height`), `radius` (how round its corners are when it is drawn from its colour; left out: square), `colors` and `image` (base name, default `frame`). |
| `elements` | array | no | The controls (below). Empty or left out: a layout with only the picture. |
| `keypad` | object | no | The layout's keypad page (below). |
| `screenLayouts` | object | no | NDS only: the layout's screen-layout pages (below). |
| `combos` | array | no | Where the skin's combo buttons are on this layout (below). |

The frame's `colors`:

| Part | Type | Meaning |
|---|---|---|
| `frame` | colour | The bezel itself. |
| `icons` | colour | The quick actions' icons on the bezel. |
| `active` | colour | The background of a quick action while it is toggled on (turbo, fast forward…). |
| `activeIcon` | `light` or `dark` | The icon's tone on that `active` background. |

Quick actions (`action` elements) use these colours; they have none of their own. In the editor the frame and its
quick actions are painted and never moved.

### Keypad page

A layout may have a second page, for the consoles whose games need the controller's keypad (Atari 5200, ColecoVision,
Intellivision, Odyssey 2): a `keypadToggle` key on the layout's controls switches to it, and the same key on the page
switches back. The page is the layout's `size`, `screens` and `frame` (the game's picture does not move) with controls
of its own, typically the twelve keypad keys in the face buttons' place.

| Key | Type | Required | Meaning |
|---|---|---|---|
| `background` | object | no | `color` and `image`, as the layout's; its image in the page's folder, its colour left out the layout's. Left out (or empty): the layout's background, colour and image. |
| `elements` | array | yes | The page's controls, under the same rules as the layout's; ids and image names only need to be unique within the page. |

Its images are in the layout's `keypad/` folder (`phone-portrait/keypad/key-1.png`, and its own `background.png` when
it has a `background`); the frame's image stays the layout's. Errors in it are reported at
`layouts.<id>.keypad…` (`layouts.phone-portrait.keypad.elements[3].bind`).

### Screen-layout pages

An NDS layout draws the two screens one over the other. It may also have one page per other way the DS screens can be
shown, so the skin's `screens` key steps through them and the core's screens follow, as with the DS skins the app
ships: `screenLayouts` holds `top` (the top screen only), `bottom` (the bottom screen only) and `side` (both, side by
side), **all three or none**. The layout itself stays the default page, the screens one over the other.

```json
"screenLayouts": {
  "top":    { "screens": [{ "x": 60, "y": 180, "width": 960, "height": 720 }], "frame": { … }, "elements": [ … ] },
  "bottom": { "screens": [{ "x": 60, "y": 180, "width": 960, "height": 720 }], "elements": [ … ] },
  "side":   { "screens": [{ "x": 0, "y": 360, "width": 540, "height": 405 }, { "x": 540, "y": 360, "width": 540, "height": 405 }] }
}
```

Each page is the layout's `size` with:

| Key | Type | Required | Meaning |
|---|---|---|---|
| `screens` | array of rects | yes | Where the page's picture goes: one rect for `top` and `bottom`, two for `side` (the top screen first, on the left). |
| `background` | object | no | `color` and `image`, as the layout's; its image in the page's folder, its colour left out the layout's. Left out (or empty): the layout's background, colour and image. |
| `frame` | object | no | The page's own bezel, as the layout's `frame`, its image in the page's folder. Left out: no frame on the page. |
| `elements` | array | no | The page's controls, under the same rules as the layout's (include a `screens` key, or the player cannot step on); ids and image names only need to be unique within the page. |

Its images are in the layout's folder of the page's id (`phone-portrait/side/a.png`, `phone-portrait/top/frame.png`).
Errors in it are reported at `layouts.<id>.screenLayouts.<page>…` (`layouts.phone-portrait.screenLayouts.side.screens`).
The skin editor edits the default page (its subtitle says "One above the other") and keeps the pages as they are.

### Combo buttons

A combo button (issue 842) is one on-screen button that presses two or three of the console's buttons together: Y+B
to run and jump, L+R, three punches. The player adds them in the editor of a skin of their own; no bundled skin has
one. The skin keeps them in two places:

- the root `combos`: one entry per combo, `id` (lowercase letters, digits and single hyphens; unique among the
  combos) and `buttons`, the input names it presses, in the order its label reads;
- each layout's `combos`: one entry per combo on that layout, `id` (the root entry's), a rect (`x`, `y`, `width`,
  `height`) and, optionally, `shape` and `corner` (its outline, below), `depth`, `rotation`, `labelSize`,
  `labelOffset` and `colors` (`button`, `relief`, `letter`), as for a button element. Its `id` must not be an
  element's id on that layout.

```json
"combos": [{ "id": "combo-1", "buttons": ["y", "b"] }],
"layouts": {
  "phone-portrait": {
    "combos": [{ "id": "combo-1", "x": 540, "y": 1460, "width": 100, "height": 100, "colors": { "button": "#5A606E" } }]
  }
}
```

A combo may press 2 or 3 different buttons among the face buttons, shoulders, triggers, C buttons, Select and Start:
`a`, `b`, `x`, `y`, `c`, `z`, `l`, `r`, `l1`, `r1`, `l2`, `r2`, `start`, `select`, `mode`, `cUp`, `cDown`, `cLeft`,
`cRight`, `option1`, `option2`. Not the D-pad, the sticks or their clicks, the quick actions, the menu key, the keypad
or the FDS disk key. Its label is the name the skin gives each of its buttons (an element's `label`), joined with
"+": "Y+B", "L+R+A", "□+✕"; a button with no label is its input name, capitalised. It is drawn as a button in its
colours with a light ring round its face, so it never reads as one of the console's own buttons, and pressing it
presses all its buttons; releasing it releases them.

**Outline.** A combo's outline is written on each layout's place for it: `shape` is `circle` (the default), `pill`, `rect` (a rounded
square, issue 891) or `triangle`. A
triangle is a right triangle in its rect whose square corner points at `corner`: `bottom-left`, `bottom-right`,
`top-left` or `top-right`. Its legs lie on the two edges of the rect that meet at that corner and its long edge
crosses the rect, so it tucks into the gap between two round buttons (Y+B at the bottom left of a SNES diamond). It
is touched on the triangle alone: a touch on the rest of its rect, over a neighbouring button, never presses it
(the overlay desc's `_triangle` key), so a triangle is never turned: its `rotation` is read as 0. Its label sits at the triangle's centroid, pulled toward the square corner. A
`shape` this app does not know, or a `triangle` with no known `corner`, is drawn as a circle rather than refused,
so a newer app's outline still opens. The editor makes a circle, a rounded square and a triangle as wide as tall (the
face buttons' diameter) and a pill 1.6 times as wide and 0.8 times as tall; Size scales any of them. A triangle's
three corners are rounded as much as a rounded square's (issue 891). An element's own `shape` is never `triangle`:
one with it is refused (`BAD_SHAPE`); a button the player gave a triangle says so in its `outline` (below).

```json
"combos": [{ "id": "combo-1", "x": 520, "y": 1390, "width": 90, "height": 90, "shape": "triangle", "corner": "bottom-left" }]
```

**Why not an element.** A combo is not an element with a new `type` or a `bind` such as `"y|b"`: either one makes an
app from before combos refuse the whole skin (`UNKNOWN_TYPE`, `UNKNOWN_BIND`). The `combos` keys are new optional
keys, which every version ignores when it does not read them, so an older app opens the skin without its combos.

**Left out, not refused.** A combo this app cannot press (an input it does not know or a combo may not press, fewer
than 2 or more than 3 buttons, one input twice) is left out with its places, so a newer app may allow more without
older ones refusing its skins. A layout's place for a combo the root does not have is left out too. Anything else
wrong in a combo (a bad `id`, a duplicate id, a rect outside the layout, a bad colour) refuses the skin like the same
mistake in an element. The editor keeps a combo it left out in the file when it saves, with its places, and gives
its own combos other ids.

Combos are on the layout's own page: not on its keypad page, nor on an NDS layout's screen-layout pages.

### Outline of the player's

A player can draw any button or keypad key of their own skin in another outline (issue 891): L and R moved next to A
and B on a GBA layout look right as circles, not as the pills the skin drew. The element keeps the skin's own
`shape`, `rotation` and rect keys as the skin had them, and says what the player chose in `outline`:

- `shape`: `circle`, `pill`, `rect` (a rounded square) or `triangle`;
- `corner`: for a `triangle`, the corner its square corner points at, as a combo's (above);
- `width`, `height`: the control's size in the skin's own outline when the player first chose another, whose
  proportions "Original" in the editor brings back.

The element is drawn in that outline from its colours (an image of its own is the skin's drawing of its own outline,
and is not used), and a triangle is never turned and is touched on the triangle alone, as a combo's. The rect keys
are where the player put it, in that outline. An `outline` this app cannot read (not an object, an unknown `shape`, a
`triangle` with no known `corner`) is ignored, and the skin's own outline drawn; one on a D-pad, a stick or a quick
action is ignored too. An app from before it ignores the key and draws the skin's own. "Original" removes the key.

**One outline per control.** An outline is the control's, not one layout's: the editor gives it to every layout that
has the control (each where the control is there, about its centre), as it does a colour. A skin whose layouts
disagree (saved by an earlier build, which kept one outline per layout, combos' included) is read with each control
in the outline the player picked on most of its layouts, the first in layout order (`phone-portrait`,
`phone-landscape`, `tablet-portrait`, …) on a tie; a layout in the skin's own outline (a combo's circle) never wins
over one the player picked. The file is not rewritten until the player saves; the save then writes each such control where it is drawn, with its `outline`.

```json
{ "id": "l", "type": "button", "bind": "l", "x": 520, "y": 1330, "width": 100, "height": 100, "shape": "pill",
  "outline": { "shape": "circle", "width": 200, "height": 80 } }
```

### Pressed look

While a button is held in game, it is drawn tinted (issue 891): 30 % black over a light face, 35 % white over a dark
one, judged from what the button draws, over its own pixels only (its outline and its label keep their shape). Each
of the D-pad's four directions is tinted on its own, the wedge from its centre to that side, under its ball. The tint
follows what the overlay presses, not where the finger is: a combo lights, and so does a button anchor mode holds. It
is not a key of the skin: every skin in this format has it. The overlay draws it from an image per button written
with `overlayN_descM_lit_by = "press"`, which the engine shows only while the overlay presses every bind of that desc
and which takes no touch.

### Rect

`x`, `y`, `width`, `height`: numbers (decimals allowed), in the layout's pixel space, from its top-left corner.
`width` and `height` must be above 0, and the rect must lie wholly inside the layout (`x ≥ 0`, `y ≥ 0`,
`x + width ≤ size.width`, `y + height ≤ size.height`). Screens, the frame and every element follow this rule.

### Element

| Key | Type | Required | Meaning |
|---|---|---|---|
| `id` | string | yes | Unique in the layout; lowercase letters, digits and single hyphens (`a`, `stick2`, `key-star`). Also the base name of the element's images. |
| `type` | string | yes | `button`, `dpad`, `stick`, `action` or `keypad` (below). |
| `bind` | string | yes, except `dpad` | The input it presses (below). A D-pad's is `dpad`, and may be left out; a second D-pad's is `dpad2`. |
| `group` | string | no | The group the editor moves it with (`face`, `dpad`, `shoulders-left`, `center`…). Free text; elements with the same value move together. |
| `x`, `y`, `width`, `height` | numbers | yes | The element's rect (see Rect): the whole control, its relief included. |
| `shape` | string | no | `circle`, `pill` or `rect`: the outline drawn when the element has no image. |
| `outline` | object | no | The outline the player gave a `button` or a `keypad` key in the editor (issue 891), drawn instead of `shape` (below). |
| `radius` | number | no | How round a `rect` shape's corners are. 0 or more; left out, a share of its shorter side. |
| `rotation` | number | no | Degrees the shape is turned, clockwise (-40 to 40). The rect is then the box around the turned shape. |
| `depth` | number | no | A button's, a key's or the D-pad's relief: how many units of the rect's height, at the bottom, show the same shape again under the face, in the `relief` colour. Left out or 0: flat. At most three quarters of the height. |
| `label` | string | no | The letter or word drawn on it (`A`, `START`, `1`). A PlayStation shape (`△` `○` `✕` `□`), an arrow (`▲` `▶` `▼` `◀`) or the menu's lines (`☰`) is drawn as that glyph. |
| `labelSize` | number | no | The label's type size (a glyph's side). Above 0; left out, a share of the face, a long word shrunk to fit it. |
| `labelOffset` | object | no | `x`, `y`: where the label's centre is, from the centre of the element's rect. Left out: on the middle of the face. A label may sit beside its control (`START` under its key). |
| `colors` | object | no | Its colour parts (below), each a colour. Only the parts of its type. |
| `image` | string | no | A base name for its images other than `id` (lowercase letters, digits and single hyphens; no extension). |
| `art` | string | no | What a button's, a key's or the D-pad's image holds: `full` (the default: all of it, label and relief included), `key` (all but the label, which the app draws where `labelOffset` puts it) or `face` (the face alone: the app draws the relief under it and the label over it). |

`radius`, `depth`, `labelSize` and `labelOffset` are in the layout's units. A control resized in the editor keeps its
proportions: all four follow its width.

### Element types and their colour parts

| Type | What it is | Colour parts | Images |
|---|---|---|---|
| `button` | A button: face buttons, shoulders, triggers, Start, Select, a menu button… | `button`, `relief`, `letter` | `<id>.png` |
| `dpad` | The D-pad (its four directions) | `pad`, `ball`, `relief`, `letter` (its four arrows) | `<id>.png`, `<id>-ball.png` |
| `stick` | An analog stick | `base`, `knob` | `<id>-base.png`, `<id>-knob.png` |
| `action` | A quick action on the screen's frame | none: the frame's `icons`, `active`, `activeIcon` | `<id>.png` |
| `keypad` | A key of a numeric keypad | `button`, `relief`, `letter` | `<id>.png` |

A colour part that is not set is drawn in the renderer's default for it. A `relief` left unset follows its face: the
same colour, darker.

### Colours

`#RRGGBB`, or `#RRGGBBAA` with an alpha (`00` transparent, `FF` opaque). Upper or lower case on reading; the app
writes upper case, and drops the alpha when it is `FF`.

### Input names (`bind`)

Each type takes its own names; a name another type takes is refused with "not for this type", a name no type takes
as unknown.

**`button`** takes the names a Delta or Manic skin uses (the same table, `launch/SkinBinds.kt`) and the quick
actions:

| Name | Presses |
|---|---|
| `up`, `down`, `left`, `right` | a direction (as a separate button) |
| `a`, `b`, `x`, `y` | a face button |
| `l`, `r` (also `l1`, `r1`), `l2`, `r2`, `l3`, `r3` | shoulders, triggers, stick clicks |
| `start`, `select` | Start, Select |
| N64: `z`, `cUp`, `cDown`, `cLeft`, `cRight` | Z and the C buttons |
| Mega Drive, 32X, Mega-CD: `c`, `z`, `mode` | C, Z, Mode |
| Saturn: `c`, `z` | C, Z (its `l`, `r` are the Saturn's own shoulders) |
| Famicom Disk System: `disk` | turns the disk over (pressing nothing on other consoles) |
| Lynx: `option1`, `option2` | Option 1, Option 2 (pressing nothing on other consoles) |
| Neo Geo Pocket: `a`, `b` | the Neo Geo Pocket's A (on the left) and B |
| `keypadToggle` | opens the layout's keypad page; on that page, goes back to the controls (pressing nothing when the skin has no keypad pages) |
| any `action` name below | that quick action |

The console's own names mean the console's own buttons: on N64 `a` and `b` are the N64's A and B, on the Mega Drive
(and the 32X, the Mega-CD and the Saturn) `a`, `b`, `c`, `x`, `y`, `z` its six buttons, on GameCube `l`, `r` the analog triggers and `z` the Z button, on the Neo Geo Pocket `a` its A (on the left) and `b` its B.

**`action`**: `quickSave`, `quickLoad`, `turbo`, `slowMotion`, `fastForward` (held), `toggleFastForward`,
`screenshot`, `menu`, `restart` (also `reset`), `screens` (NDS: the next screen layout). In RetroAchievements hardcore
the speed keys (`slowMotion`, `fastForward`, `toggleFastForward`) are drawn dimmed and do nothing.

**`stick`**: `analogStick` (the left stick), `rightAnalogStick`.

**`dpad`**: `dpad` (the D-pad), `dpad2` (the console's second D-pad: the Virtual Boy's right pad, which presses
Beetle VB's L2 up, L3 down, R2 left and R3 right; on a console with one D-pad it is drawn and presses nothing).

**`keypad`**: `keypad0` … `keypad9`, `keypadStar`, `keypadHash`.

## What the app refuses

A skin the app cannot draw is refused as a whole, with the kind of error and where it is (for instance
`layouts.phone-portrait.elements[2].bind`), so the editor can point at it:

| Error | When |
|---|---|
| `BAD_PACKAGE` | The file is not a zip, or cannot be read as one. |
| `NO_SKIN_JSON` | No `skin.json` at the root of the folder or zip (or of its single folder). |
| `TOO_LARGE` | `skin.json` is over 1 MiB. |
| `BAD_JSON` | `skin.json` is not JSON, or not an object. |
| `MISSING_KEY` | A required key is missing (or `null`). |
| `BAD_VALUE` | A key holds the wrong kind of value: a string for a number, a decimal for a whole number, an empty name, an `activeIcon` that is not `light` or `dark`, an `art` that is not `full`, `key` or `face`, a negative `radius` or `depth`, a `depth` over three quarters of the height, a `rotation` past 40 degrees, a `labelSize` of 0 or less. |
| `UNSUPPORTED_FORMAT` | `format` is not 1. |
| `UNKNOWN_CONSOLE` | `console` is not one of the app's console ids. |
| `NO_LAYOUTS` | `layouts` is empty. |
| `UNKNOWN_LAYOUT` | A key of `layouts` is not one of the six layout ids. |
| `UNKNOWN_TYPE` | An element's `type` is not one of the five. |
| `UNKNOWN_BIND` | A `bind` no type takes. |
| `BIND_NOT_FOR_TYPE` | A `bind` the element's type does not take (a stick bound to `a`). |
| `BAD_SIZE` | A layout size, or a rect's width or height, of 0 or less. |
| `OUT_OF_LAYOUT` | A screen, the frame or an element reaching outside the layout. |
| `DUPLICATE_ID` | Two elements with one `id`, or two images with one file name, in a layout; or one image as both `<name>.png` and `<name>.webp`. |
| `BAD_ID` | An `id` or an `image` that is not lowercase letters, digits and single hyphens. |
| `BAD_COLOR` | A colour that is not `#RRGGBB` or `#RRGGBBAA`. |
| `UNKNOWN_COLOR_PART` | A colour part the element's type (or the frame) does not have. |
| `BAD_SHAPE` | A `shape` other than `circle`, `pill`, `rect` (`triangle` is a combo's alone). |
| `BAD_SCREENS` | Not one screen (two for NDS); on a screen-layout page, not one (`top`, `bottom`) or two (`side`). |
| `UNKNOWN_SCREEN_LAYOUT` | A key of `screenLayouts` other than `top`, `bottom` and `side`. |
| `SCREEN_LAYOUTS_NOT_NDS` | `screenLayouts` in a skin for a console other than `NDS`. |
| `INCOMPLETE_SCREEN_LAYOUTS` | `screenLayouts` without all three of `top`, `bottom` and `side`. |

## How the app draws it

A skin folder (holding `skin.json`) in `Android/data/com.openemux.mobile/files/skins/<console id>/` is the user's skin
for that console, alongside `.deltaskin` and `.manicskin` files there: the first by name is used. Zipped skins are not
read from that folder. The skins the app ships for some consoles (issue 770: GG, SG1000, S32X, MCD, SATURN, PCE,
PCECD, FDS; issue 771: A2600, A7800, LYNX, NGP, WS, VECTREX, VB; issue 772: A5200, CV, INTV, O2; issue 806: FC, SFC,
GB, GBC, GBA, MD, SMS, N64, GC, PS, PSP) are in this format too, zipped, in the APK's `assets/skins/<console>.zip`
(and `dark/`, and `<colour>/` for each official colour, its images WebP); a user's skin still wins over them, and one
the app cannot draw falls back to the drawn skin.

- **Form.** A window with a fold (a foldable's inner screen, flat or half open) uses the `fold-*` layouts; else a window
  600dp or more across its short side uses `tablet-*`; else `phone-*`. One form never borrows another's layouts.
- **Both orientations.** The game turns between the portrait and the landscape page without redrawing, so the skin is
  used only when it has **both** layouts of the window's form. Missing either, the app draws the console's bundled skin
  instead (then the drawn skin), and logs which layout was missing (logcat tag `OpenEmuxSkin`). A skin that cannot be
  read falls back the same way.
- **Scaling.** Each layout is scaled to the window keeping its proportions and centred; the space left around it takes
  the background colour. The game's picture goes in `screens`; with two (NDS), in the box around both, the core showing
  the screens one over the other: put the two screens one over the other.
- **Images.** A button, keypad key or quick action image is stretched over the element's rect, a stick base over the
  stick's rect. The D-pad ball is drawn 55% as wide as its travel (the D-pad's face, its rect without the relief,
  times 44/60), the stick knob half as wide as the stick, both centred (the ball on the face), and both move with the
  thumb. A missing or unreadable image is drawn from the element's colours.
- **From its colours.** A button, a key or the D-pad with no image is its face over its relief: the same shape
  `depth` units lower, in the `relief` colour, showing under the face. With a relief the face is shaded from its one
  colour the way the bundled art is (lit from above); with none it is flat. The D-pad gets an arrow near the tip of
  each arm, in its `letter` colour. Labels are set in Space Grotesk bold, the bundled art's typeface. A button bound
  to a quick action (the menu key) is drawn as any button; with no label and no image it shows the action's glyph.
- **Ball and knob.** The D-pad's ball and a stick's knob drawn from a colour take four fifths of the box their image
  would be drawn over, centred: the bundled art leaves that margin around the circle, so a ball repainted keeps its
  size. An image of your own is the thing itself, as large in its image as you paint it.
- **Labels.** The app draws a label when the element has no image, or one whose `art` leaves it out (`key`, `face`).
  One that lies inside its control is part of the control's image on the overlay; one printed beside it (`START`
  under its key) is part of the page.
- **Defaults.** A colour part left unset: background `#16161A`, frame `#0E0E11`, icons `#FFFFFF`, active `#7C3AED`
  (light icon), button and pad and stick base `#2E2E35`, letter `#FFFFFF` (a D-pad's arrows: its ball's colour), ball
  and knob `#6B6B76`, relief its face's colour 42% of the way to black. A button with no `shape` is a circle, a keypad
  key a rounded rect.
- **Keypad pages.** When both layouts of the window's form have a keypad page with a `keypadToggle` key on it, the
  skin has four pages: portrait, landscape and their keypad pages (`keypad-portrait`, `keypad-landscape`). The toggle
  switches between a layout's two pages, and turning the device keeps the kind of page. Otherwise the skin plays
  with its controls only, and a `keypadToggle` key presses nothing. A redraw while the keypad page is open (the
  window changing size) opens on it again.
- **Cores.** A control presses the RetroPad button the drawn skin presses on the console, as the core in use reads
  it: Gearcoleco reads the ColecoVision's buttons and keypad elsewhere than blueMSX, and gets them there.
- **Screen-layout pages.** On DeSmuME, when both layouts of the window's form have screen-layout pages, the skin has
  one page per screen layout and orientation (`portrait@stacked`, `portrait@top`, … `landscape@side`), named, linked
  and remembered as with the DS skin the app ships: the `screens` key steps to the next one (upright: top, bottom, one
  over the other; sideways side by side too on a tablet), the pause menu's choice switches to its page, the core shows
  the screens as the page does, and the next game opens on the layout used last. Otherwise (melonDS, or a layout
  without pages) the skin draws the screens one over the other.
- **Quick actions** light (the `active` background) while turbo, fast forward or slow motion is on. `screens` steps
  through the screen-layout pages; on a skin without them it is drawn and does nothing. In RetroAchievements hardcore
  the speed keys are dimmed and do nothing.
- The half-open (tabletop) posture keeps the app's drawn tabletop layout, and a connected gamepad its gamepad layout;
  on a console with a keypad both reach the drawn keypad, as with the drawn skin.
- **Core binds.** Buttons and keypad keys press what the running core reads them as, as the drawn skin does: on the
  ColecoVision with Gearcoleco, the fire buttons, the keypad pairs, `*` and `#`, 9 and 0 go where that core reads them
  (`coreBind` in `data/Skin.kt`).

## Copies of the drawn skin

On a console with no bundled package, the bundled skin's **Create copy and edit** converts the app's drawn skin into
this format (`launch/DrawnSkinCopy.kt`, issue 798). Issue 798 made it for the A5200, CV, INTV and O2; since issue 772
those four ship packages of their own (with a keypad page), so their copy is the package's, and the drawn copy is left
for a console added later with no package. The copy has the six layouts, each the drawn skin laid out
for a reference window of its form (Phone 1080 × 2340, Tablet 1600 × 2560 at density 2, Fold 1812 × 2178 at 2.625),
with its colours, shapes, labels and binds, and `background.png` (with the console's name), `dpad.png`,
`dpad-ball.png` and `menu.png` painted as the game draws them. The copy has no keypad page (the format's since issue
772) and no hide key: the keypad key and the hide key are left out, and the keypad keys get a place of their own (upright, a grid between the clusters, or two rows of six
under them when the column is too narrow; sideways, under the face buttons, the pills moving to the left).

## Import and export

The skin list's **Import .zip** (Settings → a console → Controller skins) takes a zip in this format, or a Delta or
Manic EMU package, and **Download as template** (a skin of your own → its sheet) gives one back.

- **Download as template** writes the skin's own `skin.json` unchanged, a `README.txt` listing every file at its size,
  and **one image per element in every layout the skin has** (the ones this device cannot edit too), all under one
  folder named after the skin. An image the skin has as a PNG at the right size is copied as is, a WebP one or one at
  another size is written as a PNG at that size, and a missing one is drawn the way the game draws it (colours,
  shape, relief, and the label when the element's `art` says the image holds it). A bundled skin's Download as
  template gives the same, from the app's own package. Sizes: the element's
  rect; the background the layout's `size`; the frame its rect; a D-pad's `-ball` image `min(width, height) × 44/60`;
  a stick's `-knob` image half its shorter side (rounded half up). A layout's keypad page adds its own images in the
  layout's `keypad/` folder, at the same sizes (its background only when it names one of its own), and an NDS layout's
  screen-layout pages theirs in its `top/`, `bottom/` and `side/` folders (each page's background only when it names
  one, its frame when it has one); **Import** takes them back from there.
- **Import** refuses, before writing anything, a file that is not a zip, a zip with neither `skin.json` nor a
  package's `info.json`, a `skin.json` the reader refuses, a skin for another console (also a Delta package whose
  `gameTypeIdentifier` names one), a zip with an entry whose name starts with `/`, contains `\` or a `..` segment,
  and anything over the limits: 128 MiB per file or in all once read, 16 MiB per image, 2,000 entries.
- A skin in this format goes in as **a new skin** (the name typed replaces `name`) or **over one of the player's
  skins**, which keeps its folder (so a console using it keeps using it) and its name. Bundled skins are never a
  target. Only `skin.json` and the images it names are kept. **A layout whose folder is missing from the zip is
  dropped** from `layouts` (and `forms`), so deleting a template's `fold-*` folders stops the skin offering folds;
  a zip with no layout folder at all keeps every layout (a skin drawn from its colours). An image the zip lacks is
  the replaced skin's, when it had one; else the element is drawn from its colours. Each image keeps its name, PNG or
  WebP.
- The skin is built in a hidden folder, read back with this reader, and only then renamed into place (a replaced
  skin moved aside until then): a zip that fails at any point leaves the list as it was.
- A package is copied as it is to `<name>.deltaskin` (or `.manicskin`, when the picked file was one) and shows under
  Imported. A package whose game type the app does not know goes to the console whose list is open.

## Writing

The app writes `skin.json` with its keys in the order of the tables above, two-space indents, optional keys left out
when unset, whole numbers without decimals and the `forms` list derived from the layouts. Reading what it writes
gives back the same skin.
