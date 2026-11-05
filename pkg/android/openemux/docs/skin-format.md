# OpenEmux skin format

This is the format of an OpenEmux controller skin: what the skin editor saves, what "Download as template" gives, and
what the skin templates ship in. The app reads it with `data/SkinFormat.kt` (model in `data/OpenEmuxSkin.kt`); this
document and that reader say the same thing, and a change to one is a change to the other.

The app still reads skins in the Delta and Manic EMU package format (`.deltaskin`, `.manicskin`) as before. This is a
separate format, OpenEmux's own.

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
├── phone-landscape/
└── …
```

Files the app does not know are ignored. Nothing in the zip is extracted: the app reads the entries in place, and an
entry whose name starts with `/`, contains a `\` or a `..` segment is ignored.

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

| File | What it is |
|---|---|
| `background.png` | the whole layout, behind everything (the layout's `size`) |
| `frame.png` | the bezel around the game screen, where the quick actions sit (the frame's rect) |
| `<id>.png` | a button, a keypad key or a quick action |
| `<id>.png`, `<id>-ball.png` | a D-pad: the pad, and the ball that moves like a stick |
| `<id>-base.png`, `<id>-knob.png` | an analog stick: the base, and the knob that moves |

`<id>` is the element's `id` (below), so the usual names are `dpad.png`, `dpad-ball.png`, `stick-base.png`,
`stick-knob.png` (`stick2-base.png`, `stick2-knob.png` for a second stick), `a.png`, `b.png`, `x.png`, `y.png`,
`l.png`, `r.png`, `l2.png`, `r2.png`, `start.png`, `select.png`, the keypad's `key-1.png` … `key-9.png`,
`key-0.png`, `key-star.png`, `key-hash.png`, and the quick actions' `save.png`, `load.png`, `turbo.png`,
`slow-motion.png`, `fast-forward.png`, `menu.png`… An element's `image` key replaces `<id>` in those names, and the
background's and the frame's `image` keys replace `background` and `frame` (for instance to share one file between
layouts' names, or to keep a texture's own name). Within a layout, no two images may end up with the same file name.

A texture picked in the editor (stretched, fitted or repeated, moved, zoomed) is baked into the element's image at
its exact size: the format carries the finished picture, not the editor's fill settings.

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
        "x": 0, "y": 140, "width": 1080, "height": 860,
        "colors": { "frame": "#1A1324", "icons": "#FFFFFF", "active": "#7C4DFF", "activeIcon": "light" }
      },
      "elements": [
        { "id": "dpad", "type": "dpad", "group": "dpad", "x": 60, "y": 1320, "width": 420, "height": 420,
          "colors": { "pad": "#3A2C4F", "ball": "#9E8CB8" } },
        { "id": "a", "type": "button", "bind": "a", "group": "face", "x": 840, "y": 1380, "width": 180, "height": 180,
          "shape": "circle", "label": "A", "colors": { "button": "#7C4DFF", "letter": "#FFFFFF" } },
        { "id": "b", "type": "button", "bind": "b", "group": "face", "x": 620, "y": 1500, "width": 180, "height": 180,
          "shape": "circle", "label": "B" },
        { "id": "start", "type": "button", "bind": "start", "group": "center", "x": 560, "y": 2100, "width": 160,
          "height": 60, "shape": "pill", "label": "START" },
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
| `layouts` | object | yes | Layout id → layout (below). At least one entry; keys must be the six layout ids above. |

### Layout

| Key | Type | Required | Meaning |
|---|---|---|---|
| `size` | object | yes | `width`, `height`: whole numbers above 0. The layout's pixel space: every rect below is in these units, and the images are at this scale. The renderer scales the layout to the screen. |
| `screens` | array of rects | yes | Where the game's picture goes. Exactly one rect; exactly two for `NDS` (top screen first). |
| `background` | object | no | `color` (colour) and `image` (base name, default `background`). |
| `frame` | object | no | The bezel around the game screen: a rect (`x`, `y`, `width`, `height`), `colors` and `image` (base name, default `frame`). |
| `elements` | array | no | The controls (below). Empty or left out: a layout with only the picture. |

The frame's `colors`:

| Part | Type | Meaning |
|---|---|---|
| `frame` | colour | The bezel itself. |
| `icons` | colour | The quick actions' icons on the bezel. |
| `active` | colour | The background of a quick action while it is toggled on (turbo, fast forward…). |
| `activeIcon` | `light` or `dark` | The icon's tone on that `active` background. |

Quick actions (`action` elements) use these colours; they have none of their own.

### Rect

`x`, `y`, `width`, `height`: numbers (decimals allowed), in the layout's pixel space, from its top-left corner.
`width` and `height` must be above 0, and the rect must lie wholly inside the layout (`x ≥ 0`, `y ≥ 0`,
`x + width ≤ size.width`, `y + height ≤ size.height`). Screens, the frame and every element follow this rule.

### Element

| Key | Type | Required | Meaning |
|---|---|---|---|
| `id` | string | yes | Unique in the layout; lowercase letters, digits and single hyphens (`a`, `stick2`, `key-star`). Also the base name of the element's images. |
| `type` | string | yes | `button`, `dpad`, `stick`, `action` or `keypad` (below). |
| `bind` | string | yes, except `dpad` | The input it presses (below). A D-pad's is `dpad`, and may be left out. |
| `group` | string | no | The group the editor moves it with (`face`, `dpad`, `shoulders-left`, `center`…). Free text; elements with the same value move together. |
| `x`, `y`, `width`, `height` | numbers | yes | The element's rect (see Rect). |
| `shape` | string | no | `circle`, `pill` or `rect`: the outline drawn when the element has no image. |
| `label` | string | no | The letter or word drawn on it when it has no image (`A`, `START`, `1`). |
| `colors` | object | no | Its colour parts (below), each a colour. Only the parts of its type. |
| `image` | string | no | A base name for its images other than `id` (lowercase letters, digits and single hyphens; no extension). |

### Element types and their colour parts

| Type | What it is | Colour parts | Images |
|---|---|---|---|
| `button` | A button: face buttons, shoulders, triggers, Start, Select, a menu button… | `button`, `letter` | `<id>.png` |
| `dpad` | The D-pad (its four directions) | `pad`, `ball` | `<id>.png`, `<id>-ball.png` |
| `stick` | An analog stick | `base`, `knob` | `<id>-base.png`, `<id>-knob.png` |
| `action` | A quick action on the screen's frame | none: the frame's `icons`, `active`, `activeIcon` | `<id>.png` |
| `keypad` | A key of a numeric keypad | `button`, `letter` | `<id>.png` |

A colour part that is not set is drawn in the renderer's default for it.

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
| Mega Drive: `c`, `z`, `mode` | C, Z, Mode |
| any `action` name below | that quick action |

The console's own names mean the console's own buttons: on N64 `a` and `b` are the N64's A and B, on the Mega Drive
`a`, `b`, `c`, `x`, `y`, `z` its six buttons, on GameCube `l`, `r` the analog triggers and `z` the Z button.

**`action`**: `quickSave`, `quickLoad`, `turbo`, `slowMotion`, `fastForward` (held), `toggleFastForward`,
`screenshot`, `menu`, `restart` (also `reset`), `screens` (NDS: the next screen layout). In RetroAchievements hardcore
the speed keys (`slowMotion`, `fastForward`, `toggleFastForward`) are drawn dimmed and do nothing.

**`stick`**: `analogStick` (the left stick), `rightAnalogStick`.

**`dpad`**: `dpad`.

**`keypad`**: `keypad0` … `keypad9`, `keypadStar`, `keypadHash`.

## What the app refuses

A skin the app cannot draw is refused as a whole, with the kind of error and where it is (for instance
`layouts.phone-portrait.elements[2].bind`), so the editor can point at it:

| Error | When |
|---|---|
| `BAD_PACKAGE` | The file is not a zip. |
| `NO_SKIN_JSON` | No `skin.json` at the root of the folder or zip (or of its single folder). |
| `TOO_LARGE` | `skin.json` is over 1 MiB. |
| `BAD_JSON` | `skin.json` is not JSON, or not an object. |
| `MISSING_KEY` | A required key is missing (or `null`). |
| `BAD_VALUE` | A key holds the wrong kind of value: a string for a number, a decimal for a whole number, an empty name, an `activeIcon` that is not `light` or `dark`. |
| `UNSUPPORTED_FORMAT` | `format` is not 1. |
| `UNKNOWN_CONSOLE` | `console` is not one of the app's console ids. |
| `NO_LAYOUTS` | `layouts` is empty. |
| `UNKNOWN_LAYOUT` | A key of `layouts` is not one of the six layout ids. |
| `UNKNOWN_TYPE` | An element's `type` is not one of the five. |
| `UNKNOWN_BIND` | A `bind` no type takes. |
| `BIND_NOT_FOR_TYPE` | A `bind` the element's type does not take (a stick bound to `a`). |
| `BAD_SIZE` | A layout size, or a rect's width or height, of 0 or less. |
| `OUT_OF_LAYOUT` | A screen, the frame or an element reaching outside the layout. |
| `DUPLICATE_ID` | Two elements with one `id`, or two images with one file name, in a layout. |
| `BAD_ID` | An `id` or an `image` that is not lowercase letters, digits and single hyphens. |
| `BAD_COLOR` | A colour that is not `#RRGGBB` or `#RRGGBBAA`. |
| `UNKNOWN_COLOR_PART` | A colour part the element's type (or the frame) does not have. |
| `BAD_SHAPE` | A `shape` other than `circle`, `pill`, `rect`. |
| `BAD_SCREENS` | Not one screen (two for NDS). |

## Writing

The app writes `skin.json` with its keys in the order of the tables above, two-space indents, optional keys left out
when unset, whole numbers without decimals and the `forms` list derived from the layouts. Reading what it writes
gives back the same skin.
