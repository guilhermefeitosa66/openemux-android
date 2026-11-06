# Bundled controller skins

`build.py` builds the app's bundled skins, `src/main/assets/skins/<console>.zip`
in OpenEmux's own skin format (`docs/skin-format.md`), every console the
same way, from one source:
`Skin.dc.html`, the Skin component of the skins' design canvas. Its script
holds the layout engine (between `ENGINE START` and `ENGINE END`) and each
console's colors, measured on photos of the console's best-known shell
(Wikimedia Commons).

```sh
python3 tools/skins/build.py                  # every console, every variant
python3 tools/skins/build.py GBA PS           # some
python3 tools/skins/build.py --variant dark   # only the Dark ones
python3 tools/skins/build.py --variant white  # one official colour, every console sold in it
```

Every console comes in two variants. `console` is the console's own colors
(`src/main/assets/skins/`); `dark` (`src/main/assets/skins/dark/`) is the
same layout run through one palette the engine applies to every console
(`DARK` and `darkPalette` in the engine): a black body, the controls in a
dark grey and the labels in a muted grey, for playing with the lights off.
Only colors change; the build stops if a Dark layout's frames,
screens or knobs differ from the console's own. The app picks the variant
per console (the "Default skin" setting).

The consoles also come in the official colours they were sold in (issue
720), each a `COLORS` entry in the engine: the shell's colour, the inks
derived to read on it, and, where the shell's plastic also moulds them,
the D-pad, stick and shoulders. Each colour's packages go in a folder named
after it (`src/main/assets/skins/<colour>/`), their art in lossy WebP
(quality 90, alpha lossless): a tenth of the PNG's size or less (issue 806:
200 to 300 KB a zip in OpenEmux's format, against 1 to 2 MB for the
console's own colours in PNG). The format takes `<name>.webp` for any image. The build stops if a colour's layout differs from the
console's own, as it does for Dark. Every run also writes
`src/main/assets/skins/variants.json`, the list the app's picker shows: per
console, its variants in order with the shell and button colours of each
one's swatch.

The colours are each console's regular retail ones (store exclusives,
bundles and special editions left out, except the N64's Gold controller,
kept as iconic); the hexes are approximations of the plastics as photos
show them.

| Console | Variants (retail name) | Source |
|---|---|---|
| FC | Red (the Famicom's controller) | <https://en.wikipedia.org/wiki/Family_Computer> |
| SFC | Purple (the North American SNES's purple and lavender buttons) | <https://en.wikipedia.org/wiki/Super_Nintendo_Entertainment_System> |
| N64 | Grey, Black, Red, Blue, Green, Yellow (the launch solids), Gold | <https://en.wikipedia.org/wiki/Nintendo_64_controller> |
| GB | Red, Yellow, Green, Blue, Black, White, Clear (Play It Loud!, 1995) | <https://en.wikipedia.org/wiki/Game_Boy> |
| GBC | Red (Berry), Purple (Grape), Green (Kiwi), Yellow (Dandelion), Teal; the skin's own is Atomic Purple | <https://en.wikipedia.org/wiki/Game_Boy_Color> |
| GBA | White (Arctic), Black, Clear blue (Glacier), Clear pink (Fuchsia), Silver (Platinum), Orange; the skin's own is Indigo | <https://en.wikipedia.org/wiki/Game_Boy_Advance> |
| NDS | Blue (Electric Blue), Black (Graphite), White (Pure White), Turquoise, Pink (Candy Pink); the skin's own is Titanium | <https://en.wikipedia.org/wiki/Nintendo_DS> |
| GC | Black (Jet Black), Silver (Platinum), Orange (Spice); the skin's own is Indigo | <https://en.wikipedia.org/wiki/GameCube> |
| PSP | White (Ceramic White), Silver, Blue (Metallic Blue); the skin's own is Piano Black | <https://en.wikipedia.org/wiki/PlayStation_Portable>, <https://av.watch.impress.co.jp/docs/20061101/sce.htm> |
| PS, MD, SMS | none: one retail colour each | <https://en.wikipedia.org/wiki/PlayStation_(console)>, <https://en.wikipedia.org/wiki/Sega_Genesis>, <https://en.wikipedia.org/wiki/Master_System> |

A console with two screens (the DS, `SCREEN_LAYOUTS` in `build.py`) gets one
page per screen layout: each layout itself shows the two one over the other,
and its `screenLayouts` (issue 807, `screen_page`) hold the `top`, `bottom`
and `side` ones, each with its own art, screens, bezel and controls, so the
picture always takes the room the layout needs. `build_own` renders each one
like a layout, with `render.html?screens=<page>`, into the layout's `top/`,
`bottom/` and `side/` folders, and the DS template has them with
placeholders. Its chin has a fifth key, `screens`, which the app turns into
the step to the next page. The build stops when a screen would leave the art
or cover a key. Intermediate images go to `build/skins/`. Needs Node, Google
Chrome, ImageMagick and Pillow.

## The consoles in OpenEmux's own format

Every console ships in OpenEmux's own skin format (`docs/skin-format.md`):
nineteen that had the drawn skin (issues 770, 771 and 772), in their own
colours and Dark, and the twelve that were Delta/Manic packages, in every
variant they had, their official colours' images in WebP: the eleven
single-screen ones (issue 806: FC, SFC, GB, GBC, GBA, MD, SMS, N64, GC, PS,
PSP) and the DS with its screen-layout pages (issue 808):
`src/main/assets/skins/<console>.zip`, `dark/<console>.zip` and
`<colour>/<console>.zip`. `OWN_FORMAT` in `build.py` names them; the same
command builds them (`python3 tools/skins/build.py GG PCE`). Their layouts
gained the fold (six instead of four), and their controls keep the binds the
packages had: the same engine items, the same input names. The old packages
are kept as test files (`src/test/resources/skins/packages/`), which the
conversion of a player's Delta/Manic package is tested on.

Each package has `skin.json` (written by the same code as the templates) and
the six layouts, phone, tablet and fold, at the templates' scale (3, 2 and 3 px
per unit). The engine renders each layout in three layers, side by side in one
screenshot (`render.html?layers=body,frame,sprites`; every part the engine
draws records what it is and the element it belongs to):

- **body**: the console alone, which is `background.png`: its plastic, a panel,
  a face well, the logo. No bezel, no control, no shadow of one and no word
  printed beside one, so a control moved in the app's skin editor leaves
  nothing behind.
- **frame**: the bezel alone on a transparent page, which is `frame.png` cut at
  the frame's rect with the game's screens cut out of it. Its rounded corners
  are transparent, and `skin.json` carries its `radius` for when it is drawn
  from a colour.
- **sprites**: every control alone in a cell of a grid, cut at its rect. An
  element's rect is what its image covers (the engine's `art`): the key as it
  is turned and, under it, its relief (`depth`). Two controls whose boxes touch
  (the GameCube's X and A) never show in each other's image.

No control has a soft shadow: its relief, a part of its own straight under the
key, is what lifts it. A word printed beside a control (START under its key,
the Famicom's B and A under their buttons) is in no image: the element carries
it as its `label`, with `labelOffset`, `labelSize` and `art: key`, and the app
draws it, so the editor can move and paint it. A label on a key stays in the
key's image. The D-pad's ball is the engine's circle cut edge to edge at the
ball's size, and a stick's knob the engine's knob rendered alone
(`render.html?knob=<n>`) and cut the same way. Headless Chrome now and then
hands over a screenshot before every tile is painted; the build checks each one
(the body covers the page, the bezel and every control have art) and renders it
again, up to three times. The zips are dated 1980-01-01, so a rebuild that
changes nothing changes no binary.

A full build is a hundred packages; it can be split over several runs at once,
one per group of consoles (`python3 tools/skins/build.py GBA GBC &`), which all
write the same `engine.js` and `variants.json`.

The consoles whose games need the controller's keypad (issue 772: A5200, CV,
INTV, O2, those with a `keypad` in the engine's `C`) have a keypad page in every
layout: the engine draws it with `layout(..., { keypad: true })`
(`render.html?keypad=1`), the twelve keys (`keypad1` … `keypad9`, `keypadStar`,
`keypad0`, `keypadHash`) in the face buttons' place and the centre row's
`keypadToggle` key labelled CONTROLS instead of KEYPAD. It is rendered the same
way into the layout's `keypad/` folder, with a `background.png` of its own;
the frame is the layout's.
The keys press what the drawn skin's keypad presses (`OpenEmuxBinds.keypad`),
so the art only names them.

| Console | Base | Controls (input names) | Colours from |
|---|---|---|---|
| GG | own | D-pad, 1 (`b`), 2 (`a`), START (blue) | <https://en.wikipedia.org/wiki/Game_Gear> |
| SG1000 | own | D-pad, 1 (`b`), 2 (`a`), PAUSE (`start`) | <https://en.wikipedia.org/wiki/SG-1000> |
| S32X | MD | the six-button pad, MODE, START | <https://en.wikipedia.org/wiki/32X> |
| MCD | MD | the six-button pad, MODE, START | <https://en.wikipedia.org/wiki/Sega_CD> |
| SATURN | own | A B C X Y Z, L, R, START (the Japanese grey pad) | <https://en.wikipedia.org/wiki/Sega_Saturn> |
| PCE | own | D-pad, II (`b`), I (`a`), SELECT, RUN (`start`) | <https://en.wikipedia.org/wiki/TurboGrafx-16> |
| PCECD | PCE | as the PC Engine | <https://en.wikipedia.org/wiki/TurboGrafx-CD> |
| FDS | FC | as the Famicom, plus DISK (`disk`, the app's own name, on L) | <https://en.wikipedia.org/wiki/Family_Computer_Disk_System> |
| A2600 | own | D-pad, FIRE (`b`, the face kind `one`), SELECT, RESET (`start`, Stella's Reset) (woodgrain brown, red Fire) | <https://en.wikipedia.org/wiki/Atari_CX40_joystick> |
| A7800 | own | D-pad, 1 (`b`, lower left), 2 (`a`), SELECT, PAUSE (`start`), RESET (`x`, prosystem's) | <https://en.wikipedia.org/wiki/Atari_7800> |
| LYNX | own | D-pad, B, A, OPTION 1 (`option1`, on R), OPTION 2 (`option2`, on L), PAUSE (`start`) | <https://en.wikipedia.org/wiki/Atari_Lynx> |
| NGP | own | D-pad, A (`a`, on the left, Beetle NGP's B), B (`b`, on A), OPTION (`start`) | <https://en.wikipedia.org/wiki/Neo_Geo_Pocket> |
| WS | own | D-pad, B, A, START | <https://en.wikipedia.org/wiki/WonderSwan> |
| VECTREX | own | D-pad, 1 to 4 in a row (`a`, `b`, `x`, `y`, the face kind `row4`), no centre keys (upright screen) | <https://en.wikipedia.org/wiki/Vectrex> |
| VB | own | two D-pads (the right one `dpad2`: L2, L3, R2, R3), B and A under it, L, R, SELECT, START (black and red) | <https://en.wikipedia.org/wiki/Virtual_Boy> |
| A5200 | own | the analog stick (`analogStick`), 2 (`a`, on the left) and 1 (`b`), START, PAUSE (`select`), KEYPAD; keypad with * and # (the CX52's black body, orange-red fires) | <https://en.wikipedia.org/wiki/Atari_5200> |
| CV | own | D-pad, L (`a`, blueMSX's left fire) and R (`b`), KEYPAD; keypad with * and # (Gearcoleco's swapped binds applied in game) | <https://en.wikipedia.org/wiki/ColecoVision> |
| INTV | own | the disc as a D-pad, 1 (`y`, top), 2 (`b`), 3 (`a`) as FreeIntv reads them (the face kind `col3`), KEYPAD; keypad with CLR and ENT (brown and gold) | <https://en.wikipedia.org/wiki/Intellivision> |
| O2 | own | D-pad, ACTION (`b`, the face kind `one`), KEYPAD; keypad with CLR and ENT (O2EM's End and Return) | <https://en.wikipedia.org/wiki/Magnavox_Odyssey_2> |

Where the RetroPad has a console's buttons elsewhere (the six-button pads,
Saturn, the disk key, the Lynx's options, the Neo Geo Pocket's A and B, the
Virtual Boy's right pad), `launch/SkinBinds.kt` says where, as `data/Skin.kt` does
for the drawn skin; `OwnFormatBundledSkinsTest` checks every control against it.
A core that reads them elsewhere (Gearcoleco on the ColecoVision) gets the drawn
skin's remap (`coreBind` in `data/Skin.kt`) in game.

To change a skin, change `Skin.dc.html` (on the canvas, or here) and run the
build. `launch/OpenEmuxSkinRenderer.kt` draws them; `launch/ImportedSkinRenderer.kt`
reads only the Delta and Manic packages a player imports.

`SpaceGrotesk.ttf` is the labels' typeface, under the SIL Open Font License
(`SpaceGrotesk-OFL.txt`); only the rendered art ships in the app.

## Templates for skin creators

`python3 tools/skins/build.py --templates [CONSOLE ...]` builds
`tools/skins/templates/<console>-template.zip` (issue 777): each console's
layout in OpenEmux's own skin format (`docs/skin-format.md`), with plain
placeholder art, one PNG per element at its exact size, and the six layouts
(phone, tablet and fold, each upright and sideways). Phone and tablet are the
bundled skin's layout at its art's scale (3 and 2 px per unit); the fold is
the engine's third device, 604 x 726 units at 3 px per unit (1812 x 2178 px),
about a Galaxy Z Fold's inner screen. The engine's items carry a `look` (group, label, shape,
colours) for this. Needs Node and Pillow,
not Chrome. `tools/skins/templates/README.md` is the creators' guide and
`ASSET-PLAN.md` the asset plan of every console (generated by
`SkinAssetPlanTest`). The templates are not in the APK.
