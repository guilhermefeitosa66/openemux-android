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
```

Every console comes in two variants. `console` is the console's own colors
(`src/main/assets/skins/`); `dark` (`src/main/assets/skins/dark/`) is the
same layout run through one palette the engine applies to every console
(`DARK` and `darkPalette` in the engine): a black body, the controls in a
dark grey and the labels in a muted grey, for playing with the lights off.
Only colors change; the build stops if a Dark representation's frames,
screens or knobs differ from the console's own. The app picks the variant
per console (the "Default skin" setting).

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
