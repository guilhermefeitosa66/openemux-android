# The theme file (`.openemux-theme`)

A user theme moves between OpenEmux on the desktop and on Android as one file
(issue 1018 on Android, issue 1012 on the desktop). Both apps follow this page.

## The file

A zip with the extension `.openemux-theme`, holding:

- `theme.yaml` — the settings (below), at most 64 KiB;
- `images/` — the background pictures the settings name, PNG, JPEG or WebP only.

A single top folder around the two is accepted, and `__MACOSX/` is ignored.
Any other entry is skipped without being read.

## `theme.yaml`

One flat YAML mapping of scalars, the format the desktop's part 1 (issue 1011)
writes with PyYAML's `safe_dump`:

```yaml
version: 1
name: Midnight
base: dark
library_bg: '#24283b'
corner_radius: 8.0
library_image: images/library-1a2b.jpg
```

- `version`: an integer, 1 today. A newer number still loads: the keys a build
  knows are read, the rest ignored.
- `name`: the theme's name, one line, at most 64 characters.
- `base`: `light` or `dark`, the built-in look every setting starts from.
- Every other key is a setting from the table below. **Only the settings the
  person changed are written**; a missing key paints as the base does on that
  platform.

Reading is forgiving and safe: an unknown key, a value of the wrong type and a
nested value (a block or a list) are dropped; a number out of range is clamped
into it; a color is `#RRGGBB` text (a bare number is not a color). A file that is
not a mapping, has no valid `base` or a bad `version` is refused as a whole.

## Settings

Colors are `#RRGGBB` text. Sizes in `dp` are pixels on the desktop. A key that
only one app has is simply ignored by the other.

| Key | Kind | Range / values | Android paints | Desktop |
|---|---|---|---|---|
| `window_bg` | color | | Screen background | Window |
| `headerbar_bg` | color | | The header's round buttons | Header bar |
| `sidebar_bg` | color | | Bottom tabs and side rail | Sidebar |
| `library_bg` | color | | Behind the shelf, the library and Recent | ROM list/grid |
| `card_bg` | color | | Cards and rows | Cards |
| `popover_bg` | color | | Sheets and dialogs | Popovers and dialogs |
| `details_bg` | color | | A game's details | Game details |
| `text_primary` | color | | Primary text | Primary text |
| `text_secondary` | color | | Secondary text | Secondary text |
| `sidebar_text_primary` | color | | Current tab's label and icon | Sidebar primary text |
| `sidebar_text_secondary` | color | | Other tabs | Sidebar secondary text |
| `accent` | color | | Accent text and links, the ring of a picked item | Accent and focus |
| `selection_bg` | color | | Selection and the gamepad focus fill | Selection |
| `accent_button_bg` | color | | Filled buttons and switches | Accent buttons |
| `accent_button_fg` | color | | Their label | Accent button text |
| `corner_radius` | number (dp) | 0 – 28 | A card's corner (18 by default); every other corner keeps its proportion | |
| `border_width` | number (dp) | 0 – 4 | Border around cards | |
| `border_color` | color | | That border | |
| `shadow_color` | color | | Shadow under cards | |
| `shadow_strength` | number | 0 – 1 | How strong the shadow is (0: none) | |
| `icon_scale` | number | 0.75 – 1.5 | Every icon's size | |
| `icon_color` | color | | Plain icons (those in the text colors) | |
| `font_scale` | number | 0.85 – 1.3 | Text size, on top of the system's | |
| `density` | choice | `compact`, `default`, `comfortable` | Row heights and paddings (×0.85, ×1, ×1.15) | |
| `grid_gap` | number (dp) | 0 – 32 | The library grid's gap | |
| `card_scale` | number | 0.75 – 1.5 | The library grid's card width where the columns are automatic (a phone in portrait keeps its pinch zoom) | |
| `library_image` | image | `images/<name>.(png\|jpg\|jpeg\|webp)` | Picture behind the library | |
| `library_image_fit` | choice | `cover`, `contain`, `tile` | How it fits | |
| `library_image_dim` | number | 0 – 0.9 | How much it is dimmed toward `library_bg` | |
| `library_image_blur` | number (dp) | 0 – 24 | How much it is blurred | |
| `sidebar_image`, `sidebar_image_fit`, `sidebar_image_dim`, `sidebar_image_blur` | as above | | The same for the tabs | Sidebar |

The empty Desktop cells are for issue 1012 to fill: the desktop adopts these
names and ranges for the settings both apps have, and keeps its own names for
the ones only it has (a sidebar width, a font family).

## Images

- An image key names a file inside `images/`: lowercase letters, digits, `-`
  and `_`, at most 40 characters, then `.png`, `.jpg`, `.jpeg` or `.webp`.
  Nothing else (no `..`, no folder, no absolute path) is accepted.
- On import each picture is decoded, scaled to at most 2048 px on its long side
  and re-encoded (JPEG on Android) into the new theme's own folder under a new
  name. A picture that cannot be decoded refuses the whole file.

## Limits on import

| | |
|---|---|
| The file | 48 MiB |
| Entries | 32 |
| `theme.yaml` | 64 KiB |
| One image | 16 MiB |
| Everything unpacked | 48 MiB, counted by the bytes actually read (a zip bomb stops at the limit) |

An entry whose name starts with `/`, holds a `\` or a `..` segment refuses the
file. An import never overwrites a theme: it gets a new id, and a name already
taken becomes "Name (2)".

## On Android

Themes live in the app's private storage, `files/themes/<id>/theme.yaml` and
`files/themes/<id>/images/`. The id is a slug of the name made when the theme
is created (`midnight`, `midnight-2`; `^[a-z0-9][a-z0-9-]{0,47}$`, never
`system`, `light` or `dark`) and never changes. The choice is the `theme`
preference, which also holds `system`, `light` or `dark`; a theme that is gone
paints as System. The backup includes the themes folder (format version 3).
