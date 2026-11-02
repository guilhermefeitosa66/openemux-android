# Bundled controller skins

`build.py` builds `src/main/assets/skins/<console>.deltaskin` (or
`.manicskin`), the skins of the twelve best-known consoles, from one source:
`Skin.dc.html`, the Skin component of the skins' design canvas. Its script
holds the layout engine (between `ENGINE START` and `ENGINE END`) and each
console's colors, measured on photos of the console's best-known shell
(Wikimedia Commons).

```sh
python3 tools/skins/build.py                  # all twelve, both variants
python3 tools/skins/build.py GBA PS           # some
python3 tools/skins/build.py --variant dark   # only the Dark ones
python3 tools/skins/build.py --variant white  # one official colour, every console sold in it
```

Every console comes in two variants. `console` is the console's own colors
(`src/main/assets/skins/`); `dark` (`src/main/assets/skins/dark/`) is the
same layout run through one palette the engine applies to every console
(`DARK` and `darkPalette` in the engine): a black body, the controls in a
dark grey and the labels in a muted grey, for playing with the lights off.
Only colors change; the build stops if a Dark representation's frames,
screens or knobs differ from the console's own. The app picks the variant
per console (the "Default skin" setting).

The consoles also come in the official colours they were sold in (issue
720), each a `COLORS` entry in the engine: the shell's colour, the inks
derived to read on it, and, where the shell's plastic also moulds them,
the D-pad, stick and shoulders. Each colour's packages go in a folder named
after it (`src/main/assets/skins/<colour>/`), their art in lossy WebP
(quality 90, alpha lossless): 40 to 140 KB a package instead of the PNG's
0.5 to 4 MB. The build stops if a colour's layout differs from the
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

For each console it lays out four representations (phone and tablet,
portrait and landscape: `iphone/edgeToEdge` and `ipad/standard` in the
format), renders each with headless Chrome from `render.html` (3 px per unit
on phones, 2 on tablets), cuts the game's screens out of the art and writes
`info.json` with the frames the engine laid out. A stick's knob is not in
the art: each one is rendered on its own, the size of its travel, and the
stick item names it as its `thumbstick` (`<device>_<orient>_knob<N>.png`), so
the app can move it with the thumb. Nor is the D-pad's circle, which the app
moves with the thumb too: it is one more image per package (`dpad_knob.png`,
the circle on its travel square), which each D-pad item names under `knob`, a
key of the app's own that other emulators ignore. A console with two screens
(the DS) gets one art per screen layout: the base representation shows the two
one over the other, as Delta expects, and its `layouts` key (the app's own
again) holds the `top`, `bottom` and `side` ones, each with its own art,
screens and items, so the picture always takes the room the layout needs. Its
chin has a fifth key, `screens`, which the app turns into the step to the next
layout. The build stops when a screen would leave the art or cover a key.
Intermediate images go to
`build/skins/`. Needs Node, Google Chrome and ImageMagick.

To change a skin, change `Skin.dc.html` (on the canvas, or here) and run the
build. `launch/ImportedSkinRenderer.kt` reads the packages.

`SpaceGrotesk.ttf` is the labels' typeface, under the SIL Open Font License
(`SpaceGrotesk-OFL.txt`); only the rendered art ships in the app.
