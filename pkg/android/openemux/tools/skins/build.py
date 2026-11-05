"""Builds the bundled controller skins (src/main/assets/skins) from the design.

  python3 tools/skins/build.py [--variant <id>] [CONSOLE ...]
  python3 tools/skins/build.py --templates [CONSOLE ...]   # the templates for skin creators (tools/skins/templates)

Every console is built in each of its variants: its own colours (src/main/assets/skins/<console>.<ext>), the
Dark variant (src/main/assets/skins/dark/<console>.<ext>), the same layout in a black and dark grey palette,
and each official colour it was sold in (issue 720: src/main/assets/skins/<colour>/<console>.<ext>). The
colour variants' art is lossy WebP, a tenth of the PNG's size; the console's own and Dark stay PNG. The
picker's list (src/main/assets/skins/variants.json: per console, its variants in order with the colours of
their swatch) is written on every run.

The layout and the art have one source: the engine of the design canvas's
Skin component (Skin.dc.html, between ENGINE START and ENGINE END). Each
representation (phone and tablet, portrait and landscape) is rendered by
headless Chrome from render.html, the game's screens are cut out
(transparent), and info.json gets the frames the engine laid out. Needs
Node, Google Chrome and ImageMagick.
"""
import json
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
APP = HERE.parent.parent
ASSETS = APP / "src/main/assets/skins"
WORK = APP / "build/skins"
CHROME = shutil.which("google-chrome") or shutil.which("google-chrome-stable")

# Delta's identifiers where Delta has the console, Manic EMU's otherwise (and then a .manicskin).
TYPES = {
    "FC": "com.rileytestut.delta.game.nes", "SFC": "com.rileytestut.delta.game.snes",
    "N64": "com.rileytestut.delta.game.n64", "GBC": "com.rileytestut.delta.game.gbc",
    "GBA": "com.rileytestut.delta.game.gba", "NDS": "com.rileytestut.delta.game.ds",
    "MD": "com.rileytestut.delta.game.genesis",
    "GB": "public.aoshuang.game.gb", "GC": "public.aoshuang.game.ngc", "SMS": "public.aoshuang.game.ms",
    "PS": "public.aoshuang.game.ps1", "PSP": "public.aoshuang.game.psp",
}
# The format's families: iphone/edgeToEdge for phones, ipad/standard for tablets; px per unit.
REPS = [("phone", "iphone", "edgeToEdge", 3), ("tablet", "ipad", "standard", 2)]
# The D-pad's circle: one image per console, stretched by the app over each D-pad's travel square.
KNOB_UNITS, KNOB_SCALE = 100, 4
# Consoles with two screens get one art per screen layout. The base representation is the two one over the other
# (what Delta draws); the others go under its "layouts", a key of the app's own that other emulators ignore.
SCREEN_LAYOUTS = {"NDS": ["top", "bottom", "side"]}
# The palettes whose art stays PNG: the console's own colours and Dark (issue 718). Every other variant is one
# of the console's official colours (issue 720), its art in lossy WebP: quality 90 and the alpha lossless, so the
# screens' cut-outs keep their exact edges.
PNG_VARIANTS = ("console", "dark")
WEBP = ["-quality", "90", "-define", "webp:alpha-quality=100", "-define", "webp:method=6"]


def variant_dir(variant):
    """The assets folder [variant]'s packages go in: the console's own in skins/, each other in its own folder."""
    return ASSETS if variant == "console" else ASSETS / variant


def sync_engine():
    src = (HERE / "Skin.dc.html").read_text()
    engine = src.split("// ENGINE START")[1].split("// ENGINE END")[0]
    (HERE / "engine.js").write_text(engine + "\nwindow.OE = OE;\n")


def engine(expr, *args):
    """[expr], evaluated in the engine with argv[2...] = [args], as JSON."""
    script = f"global.window={{}};require(process.argv[1]);const OE=window.OE;console.log(JSON.stringify({expr}))"
    out = subprocess.run(["node", "-e", script, str(HERE / "engine.js"), *args], capture_output=True, text=True, check=True).stdout
    return json.loads(out)


def catalog():
    """Per console, its variants in the picker's order: id, the name of its colour (none for console and Dark), swatch."""
    return engine("Object.fromEntries(Object.keys(OE.C).map((c)=>[c,OE.variants(c).map((v)=>Object.assign("
                  "{id:v,label:(OE.COLORS[c]||{})[v]?OE.COLORS[c][v].label:null},OE.swatch(c,v)))]))")


def write_catalog(cat):
    """skins/variants.json, what the app's picker lists: per console, each variant's id and its swatch's colours."""
    out = {c: [{"id": v["id"], "body": v["body"], "key": v["key"]} for v in vs] for c, vs in cat.items() if c in TYPES}
    (ASSETS / "variants.json").write_text(json.dumps(out, indent=1) + "\n")


def to_webp(folder, files):
    """Each of [files] (PNGs in [folder]) as WebP; the new names by the old."""
    names = {}
    for f in files:
        webp = f.removesuffix(".png") + ".webp"
        subprocess.run(["convert", str(folder / f), *WEBP, str(folder / webp)], check=True)
        (folder / f).unlink()
        names[f] = webp
    return names


def renamed(value, names):
    """[value] (info.json, or a piece of it) with every file name in [names] replaced by its new one."""
    if isinstance(value, dict):
        return {k: renamed(v, names) for k, v in value.items()}
    if isinstance(value, list):
        return [renamed(v, names) for v in value]
    return names.get(value, value) if isinstance(value, str) else value


def engine_layout(console, device, orient, screens=None, variant="console"):
    script = (
        "global.window={};require(process.argv[1]);"
        "const L=window.OE.layout(process.argv[2],process.argv[3],process.argv[4],"
        "{export:true,screens:process.argv[5]||undefined,variant:process.argv[6]});"
        "console.log(JSON.stringify({W:L.W,H:L.H,items:L.items,screens:L.screens,knobs:L.knobs.map(k=>({name:k.name,size:k.size})),name:L.name}))"
    )
    out = subprocess.run(["node", "-e", script, str(HERE / "engine.js"), console, device, orient, screens or "", variant],
                         capture_output=True, text=True, check=True).stdout
    return json.loads(out)


def screenshot(console, device, orient, w, h, scale, out, profile, query="", variant="console"):
    url = f"file://{HERE / 'render.html'}?console={console}&device={device}&orient={orient}&variant={variant}{query}"
    subprocess.run([
        CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--user-data-dir={profile}",
        f"--force-device-scale-factor={scale}", f"--window-size={w},{h}", "--default-background-color=00000000",
        "--virtual-time-budget=4000", f"--screenshot={out}", url,
    ], check=True, capture_output=True)


def cut_screens(png, screens, scale):
    args = ["convert", str(png), "-alpha", "set"]
    for s in screens:
        x, y, w, h = (round(s[k] * scale) for k in ("x", "y", "width", "height"))
        args += ["-region", f"{w}x{h}+{x}+{y}", "-alpha", "transparent", "+region"]
    subprocess.run(args + [str(png)], check=True)


def check_fit(L, where):
    """The screens inside the art and clear of every item, or the build stops: a layout's art must never hide a key."""
    def overlap(a, b):
        return a["x"] < b["x"] + b["width"] and b["x"] < a["x"] + a["width"] and a["y"] < b["y"] + b["height"] and b["y"] < a["y"] + a["height"]
    for sc in L["screens"]:
        if sc["x"] < 0 or sc["y"] < 0 or sc["x"] + sc["width"] > L["W"] or sc["y"] + sc["height"] > L["H"]:
            raise SystemExit(f"{where}: a screen leaves the art")
        for it in L["items"]:
            if overlap(sc, it["frame"]):
                raise SystemExit(f"{where}: a screen covers {it['inputs']}")


def representation(console, device, orient, scale, folder, profile, files, screens=None, variant="console"):
    """One representation's entry in info.json, its art rendered and cut; [screens] one of the console's layouts."""
    L = engine_layout(console, device, orient, screens, variant)
    where = f"{console} {variant} {device} {orient} {screens or ''}"
    check_fit(L, where)
    # A variant changes colours only: the same frames, screens and knobs as the console's own skin.
    if variant != "console" and L != engine_layout(console, device, orient, screens):
        raise SystemExit(f"{where}: the layout differs from the console's own skin")
    suffix = f"_{screens}" if screens else ""
    image = f"{device}_{orient}{suffix}.png"
    query = f"&screens={screens}" if screens else ""
    screenshot(console, device, orient, L["W"], L["H"], scale, folder / image, profile, query, variant)
    cut_screens(folder / image, L["screens"], scale)
    files.append(image)
    # Each stick's knob, an image of its own the app moves with the thumb.
    knobs = {}
    for i, k in enumerate(L["knobs"]):
        file = f"{device}_{orient}{suffix}_{k['name']}"
        px = round(k["size"] * scale)
        screenshot(console, device, orient, px // scale + 1, px // scale + 1, scale, folder / file, profile, f"&knob={i}{query}", variant)
        subprocess.run(["convert", str(folder / file), "-crop", f"{px}x{px}+0+0", "+repage", str(folder / file)], check=True)
        knobs[k["name"]] = file
    files.extend(knobs.values())
    rep, knob = entry(L, image, knobs)
    return rep, L["name"], knob


def entry(L, image, knobs):
    """A representation's entry in info.json from the engine's layout [L], its art [image] and its sticks' [knobs]
    images by name; and the D-pad circle's image, if a D-pad names one. The bundled skins and the templates share it,
    so a template's frames are always the bundled skin's."""
    items = []
    knob = None
    for it in L["items"]:
        entry = {"inputs": it["inputs"], "frame": it["frame"]}
        if it.get("extendedEdges"):
            entry["extendedEdges"] = it["extendedEdges"]
        if it.get("thumbstick"):
            entry["thumbstick"] = dict(it["thumbstick"], name=knobs[it["thumbstick"]["name"]])
        if it.get("knob"):
            # Not in Delta's format: the app's own key for the D-pad's moving circle.
            entry["knob"] = it["knob"]
            knob = it["knob"]
        items.append(entry)
    rep = {
        "assets": {"large": image},
        "mappingSize": {"width": L["W"], "height": L["H"]},
        "extendedEdges": {"top": 6, "bottom": 6, "left": 6, "right": 6},
        "translucent": False,
        "screens": [{"outputFrame": s} for s in L["screens"]],
        "items": items,
    }
    return rep, knob


def build(console, profile, variant="console", label=None):
    """[console]'s package in [variant]; [label] the name of a colour variant's colour."""
    folder = WORK / variant / console
    folder.mkdir(parents=True, exist_ok=True)
    reps = {}
    files = []
    name = None
    knob = None
    for device, family, kind, scale in REPS:
        for orient in ("portrait", "landscape"):
            rep, name, k = representation(console, device, orient, scale, folder, profile, files, variant=variant)
            knob = knob or k
            layouts = SCREEN_LAYOUTS.get(console, [])
            if layouts:
                rep["layouts"] = {}
                for layout in layouts:
                    v = representation(console, device, orient, scale, folder, profile, files, layout, variant)[0]
                    # Top only and bottom only draw the same art: one image serves both.
                    same = next((o for o in rep["layouts"].values() if dict(o, assets=None) == dict(v, assets=None)), None)
                    if same:
                        files.remove(v["assets"]["large"])
                        v["assets"] = same["assets"]
                    rep["layouts"][layout] = v
            reps.setdefault(family, {}).setdefault(kind, {})[orient] = rep
    suffix = {"console": ""}.get(variant, f" {label or variant.capitalize()}")
    info = {
        "name": f"OpenEmux {name}{suffix}",
        "identifier": f"openemux.skin.{console.lower()}" + ("" if variant == "console" else f".{variant}"),
        "gameTypeIdentifier": TYPES[console],
        "debug": False,
        "representations": reps,
    }
    if knob:
        screenshot(console, "phone", "portrait", KNOB_UNITS, KNOB_UNITS, KNOB_SCALE, folder / knob, profile,
                   f"&dpadKnob={KNOB_UNITS}", variant)
    if variant not in PNG_VARIANTS:
        names = to_webp(folder, files + ([knob] if knob else []))
        files = [names[f] for f in files]
        knob = knob and names[knob]
        info = renamed(info, names)
    (folder / "info.json").write_text(json.dumps(info, indent=2, ensure_ascii=False))
    ext = ".manicskin" if TYPES[console].startswith("public.aoshuang") else ".deltaskin"
    variant_dir(variant).mkdir(parents=True, exist_ok=True)
    pkg = variant_dir(variant) / f"{console}{ext}"
    with zipfile.ZipFile(pkg, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(folder / "info.json", "info.json")
        for file in files:
            z.write(folder / file, file)
        if knob:
            z.write(folder / knob, knob)
    print(f"{pkg.relative_to(APP)}  {pkg.stat().st_size // 1024} KB")


# The templates for skin creators (issue 665): each console's bundled layout with plain placeholder art, the SVG
# each image is rendered from, and a README with the console's asset plan. tools/skins/templates/README.md is the
# guide. They are not in the app's assets: the APK does not carry them.
TEMPLATES = HERE / "templates"
# The placeholder's colours: a light body, white controls outlined in dark grey, dark labels.
T_BODY, T_EDGE, T_KEY, T_LINE, T_INK, T_WELL = "#E6E6E6", "#A8A8A8", "#FFFFFF", "#444444", "#222222", "#CFCFCF"
# The D-pad circle's diameter on its travel square, as the engine draws it (DPAD_KNOB: radius over travel).
DPAD_CIRCLE = 14 / 44


def esc(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def n(v):
    """[v] for an SVG attribute: at most two decimals, no trailing zeros."""
    return f"{v:.2f}".rstrip("0").rstrip(".")


def control_name(inputs):
    """What a control is called in its template: its SVG layer's id and its label."""
    if isinstance(inputs, dict):
        first = str(inputs.get("up", ""))
        if first.startswith(("rightAnalogStick", "cStick")):
            return "rightAnalogStick"
        return "analogStick" if first.startswith("analogStick") else "dpad"
    return "+".join(inputs)


def svg_document(w, h, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:inkscape="http://www.inkscape.org/namespaces/inkscape" '
            f'width="{n(w)}" height="{n(h)}" viewBox="0 0 {n(w)} {n(h)}">\n{body}</svg>\n')


def layer(name, content, hidden=False):
    style = ' style="display:none"' if hidden else ""
    return f'<g id="{esc(name)}" inkscape:groupmode="layer" inkscape:label="{esc(name)}"{style}>\n{content}</g>\n'


def control_svg(item):
    """One control drawn in placeholder style: its frame's shape, outlined, with its name."""
    f = item["frame"]
    x, y, w, h = f["x"], f["y"], f["width"], f["height"]
    cx, cy, line = x + w / 2, y + h / 2, max(1.5, min(3.0, min(w, h) * 0.04))
    name = control_name(item["inputs"])
    out = []
    if isinstance(item["inputs"], dict) and name == "dpad":
        arm = min(w, h) / 3
        out.append(f'<path d="M{n(cx - arm / 2)} {n(y)}h{n(arm)}v{n(h / 2 - arm / 2)}h{n(w / 2 - arm / 2)}v{n(arm)}'
                   f'h{n(-(w / 2 - arm / 2))}v{n(h / 2 - arm / 2)}h{n(-arm)}v{n(-(h / 2 - arm / 2))}h{n(-(w / 2 - arm / 2))}'
                   f'v{n(-arm)}h{n(w / 2 - arm / 2)}z" fill="{T_KEY}" stroke="{T_LINE}" stroke-width="{n(line)}"/>')
        return "".join(out) + "\n"
    if isinstance(item["inputs"], dict):
        # A stick's well: its knob is an image of its own, which the app moves over it.
        out.append(f'<circle cx="{n(cx)}" cy="{n(cy)}" r="{n(min(w, h) / 2 - line / 2)}" fill="{T_WELL}" '
                   f'stroke="{T_LINE}" stroke-width="{n(line)}"/>')
        size = min(12.0, h * 0.13)
        out.append(f'<text x="{n(cx)}" y="{n(y + size * 1.4)}" font-family="sans-serif" font-size="{n(size)}" '
                   f'text-anchor="middle" fill="{T_INK}">{esc(name)}</text>')
        return "".join(out) + "\n"
    if abs(w - h) <= 0.15 * max(w, h):
        out.append(f'<ellipse cx="{n(cx)}" cy="{n(cy)}" rx="{n(w / 2 - line / 2)}" ry="{n(h / 2 - line / 2)}" '
                   f'fill="{T_KEY}" stroke="{T_LINE}" stroke-width="{n(line)}"/>')
    else:
        r = min(w, h) / 2 if min(w, h) <= 34 else 10
        out.append(f'<rect x="{n(x + line / 2)}" y="{n(y + line / 2)}" width="{n(w - line)}" height="{n(h - line)}" '
                   f'rx="{n(r)}" fill="{T_KEY}" stroke="{T_LINE}" stroke-width="{n(line)}"/>')
    size = max(6.0, min(14.0, h * 0.42, w / max(1, len(name)) * 1.6))
    out.append(f'<text x="{n(cx)}" y="{n(cy + size * 0.35)}" font-family="sans-serif" font-size="{n(size)}" '
               f'text-anchor="middle" fill="{T_INK}">{esc(name)}</text>')
    return "".join(out) + "\n"


def template_svg(L, edges):
    """The template's art for one layout [L], as an SVG in mapping units: a body with the screens cut out, one layer
    per control, and two hidden helper layers (where the screens are, where each control's touch area reaches)."""
    W, H = L["W"], L["H"]
    holes = "".join(f'M{n(s["x"])} {n(s["y"])}h{n(s["width"])}v{n(s["height"])}h{n(-s["width"])}z' for s in L["screens"])
    body = (f'<path d="M0 0H{n(W)}V{n(H)}H0z{holes}" fill="{T_BODY}" fill-rule="evenodd"/>\n'
            f'<rect x="1" y="1" width="{n(W - 2)}" height="{n(H - 2)}" fill="none" stroke="{T_EDGE}" stroke-width="2"/>\n')
    out = layer("body", body)
    ids = {}
    for it in L["items"]:
        name = control_name(it["inputs"])
        ids[name] = ids.get(name, 0) + 1
        out += layer(name if ids[name] == 1 else f"{name}-{ids[name]}", control_svg(it))
    guides = "".join(f'<rect x="{n(s["x"])}" y="{n(s["y"])}" width="{n(s["width"])}" height="{n(s["height"])}" '
                     f'fill="none" stroke="#3A7BD5" stroke-width="2" stroke-dasharray="6 4"/>\n' for s in L["screens"])
    out += layer("screen-guides", guides, hidden=True)
    touch = ""
    for it in L["items"]:
        # As the app reads them: an item's own edges replace the layout's, and a side they leave out reaches nothing.
        own = it.get("extendedEdges")
        e = {k: (own if own else edges).get(k, 0) for k in ("top", "bottom", "left", "right")}
        f = it["frame"]
        touch += (f'<rect x="{n(f["x"] - e["left"])}" y="{n(f["y"] - e["top"])}" '
                  f'width="{n(f["width"] + e["left"] + e["right"])}" height="{n(f["height"] + e["top"] + e["bottom"])}" '
                  f'fill="none" stroke="#D53A3A" stroke-width="1" stroke-dasharray="2 3"/>\n')
    out += layer("touch-areas", touch, hidden=True)
    return svg_document(W, H, out)


def circle_svg(side, diameter):
    """A round knob, [diameter] units wide, centred on a transparent square [side] units wide."""
    line = max(1.5, diameter * 0.06)
    return svg_document(side, side, layer("knob", f'<circle cx="{n(side / 2)}" cy="{n(side / 2)}" '
                        f'r="{n(diameter / 2 - line / 2)}" fill="{T_KEY}" stroke="{T_LINE}" stroke-width="{n(line)}"/>\n'))


def render_svg(svg, png, w, h, scale, profile):
    """[svg] (a file) rendered by headless Chrome to [png], [w] by [h] units at [scale] px per unit."""
    page = svg.with_suffix(".html")
    page.write_text('<!doctype html><html><body style="margin:0;background:transparent">'
                    f'<img src="{svg.name}" width="{w}" height="{h}" style="display:block"></body></html>')
    subprocess.run([
        CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--user-data-dir={profile}",
        f"--force-device-scale-factor={scale}", f"--window-size={w},{h}", "--default-background-color=00000000",
        "--virtual-time-budget=2000", f"--screenshot={png}", f"file://{page}",
    ], check=True, capture_output=True)
    page.unlink()
    px, py = round(w * scale), round(h * scale)
    subprocess.run(["convert", str(png), "-crop", f"{px}x{py}+0+0", "+repage", str(png)], check=True)


def template_representation(console, device, orient, scale, folder, profile, plan, plan_knobs, screens=None):
    """One representation of [console]'s template: its art drawn from the layout's frames, and its entry in info.json.
    [plan] gets a row per layout image for the README, [plan_knobs] one per stick knob image (its name, its pixels)."""
    L = engine_layout(console, device, orient, screens)
    check_fit(L, f"{console} template {device} {orient} {screens or ''}")
    suffix = f"_{screens}" if screens else ""
    image = f"{device}_{orient}{suffix}.png"
    sources = folder / "sources"
    sources.mkdir(exist_ok=True)
    svg = sources / image.replace(".png", ".svg")
    plan.append((image, f"{device} {orient}" + (f", {screens} layout" if screens else ""), L, scale))
    knobs = {}
    for k in L["knobs"]:
        file = f"{device}_{orient}{suffix}_{k['name']}"
        side = round(k["size"] * scale) / scale
        ksvg = sources / file.replace(".png", ".svg")
        ksvg.write_text(circle_svg(side, side))
        render_svg(ksvg, folder / file, side, side, scale, profile)
        knobs[k["name"]] = file
        plan_knobs.append((file, round(side * scale)))
    rep = entry(L, image, knobs)[0]
    # The touch-areas guide uses the layout's edges info.json gets, so the two cannot drift apart.
    svg.write_text(template_svg(L, rep["extendedEdges"]))
    render_svg(svg, folder / image, L["W"], L["H"], scale, profile)
    cut_screens(folder / image, L["screens"], scale)
    return rep


def plan_readme(console, name, ext, plan, knob, knob_files, items):
    """The template's README: what the package is and every image it holds, at its size."""
    rows = []
    for image, where, L, scale in plan:
        screens = "; ".join(f'{n(s["x"])}, {n(s["y"])}, {n(s["width"])} x {n(s["height"])}' for s in L["screens"]) or "none"
        rows.append(f"| `{image}` | {where} | {L['W']} x {L['H']} | {round(L['W'] * scale)} x {round(L['H'] * scale)} px "
                    f"| {screens} |")
    knob_rows = [f"| `{f}` | {round(px)} x {round(px)} px | a stick's knob, moved by the thumb over the stick's well |"
                 for f, px in knob_files]
    if knob:
        knob_rows.append(f"| `{knob}` | {KNOB_UNITS * KNOB_SCALE} x {KNOB_UNITS * KNOB_SCALE} px | the D-pad's circle, "
                         "stretched over the D-pad's travel and moved by the thumb |")
    controls = "\n".join(f"- `{c}`" for c in items)
    return f"""# {name} skin template

A skin package for OpenEmux on Android, with plain placeholder art at the exact sizes the app reads. Repaint the
images, keep the file names and sizes, zip the files again and install it. The guide that explains the format,
every input name and the install steps is `tools/skins/templates/README.md` in the openemux-android repository:
<https://github.com/guilhermefeitosa66/openemux-android/blob/openemux-mobile/pkg/android/openemux/tools/skins/templates/README.md>

## Files

- `info.json`: the layouts, with every control placed and bound. Positions are in mapping units, not pixels.
- The PNG images below: the art, one per layout. The screens are transparent holes.
- `sources/`: one SVG per PNG, the image's editable source. Each control is a layer named after its input
  (`a`, `start`, `dpad`, `analogStick`...); two hidden layers show where the screens are (`screen-guides`)
  and how far each control's touch area reaches (`touch-areas`). Export each SVG to its PNG at the size below.
- `README.md`: this file.

`sources/` and `README.md` are optional in the installed package: the app reads only `info.json` and the images it
names.

## Layout art

| Image | Layout | Mapping size (units) | Image size | Screen frame(s) (x, y, w x h, units) |
|---|---|---|---|---|
{chr(10).join(rows)}

## Knobs

| Image | Size | What it is |
|---|---|---|
{chr(10).join(knob_rows) or "| none | | |"}

## Controls

The inputs of the phone portrait layout, in drawing order (the other layouts have the same ones):

{controls}

## Install

1. Zip the files so that `info.json` sits at the zip's root (not inside a folder).
2. Name the zip `{console}.{ext}`.
3. Copy it to `Android/data/com.openemux.mobile/files/skins/{console}/` on the device, then start a game.
   Delete it to go back to the bundled skin.
"""


def build_template(console, profile):
    """[console]'s template: tools/skins/templates/<console>-template.zip."""
    folder = WORK / "templates" / console
    shutil.rmtree(folder, ignore_errors=True)
    folder.mkdir(parents=True)
    reps, plan, knob, knob_files = {}, [], None, []
    for device, family, kind, scale in REPS:
        for orient in ("portrait", "landscape"):
            rep = template_representation(console, device, orient, scale, folder, profile, plan, knob_files)
            knob = knob or next((it["knob"] for it in rep["items"] if it.get("knob")), None)
            layouts = SCREEN_LAYOUTS.get(console, [])
            if layouts:
                rep["layouts"] = {}
                for layout_id in layouts:
                    rep["layouts"][layout_id] = template_representation(console, device, orient, scale, folder, profile,
                                                                        plan, knob_files, layout_id)
            reps.setdefault(family, {}).setdefault(kind, {})[orient] = rep
    name = engine_layout(console, "phone", "portrait")["name"]
    if knob:
        svg = folder / "sources" / knob.replace(".png", ".svg")
        svg.write_text(circle_svg(KNOB_UNITS, KNOB_UNITS * DPAD_CIRCLE))
        render_svg(svg, folder / knob, KNOB_UNITS, KNOB_UNITS, KNOB_SCALE, profile)
    info = {
        "name": f"{name} template",
        "identifier": f"openemux.template.{console.lower()}",
        "gameTypeIdentifier": TYPES[console],
        "debug": False,
        "representations": reps,
    }
    (folder / "info.json").write_text(json.dumps(info, indent=2, ensure_ascii=False) + "\n")
    ext = "manicskin" if TYPES[console].startswith("public.aoshuang") else "deltaskin"
    items = [control_name(it["inputs"]) for it in reps["iphone"]["edgeToEdge"]["portrait"]["items"]]
    (folder / "README.md").write_text(plan_readme(console, name, ext, plan, knob, knob_files, items))
    TEMPLATES.mkdir(exist_ok=True)
    out = TEMPLATES / f"{console}-template.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(folder.rglob("*")):
            if f.is_file():
                # A fixed date: a rebuild that changes nothing gives the same zip, not a new binary in git.
                entry_info = zipfile.ZipInfo(f.relative_to(folder).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
                z.writestr(entry_info, f.read_bytes(), zipfile.ZIP_DEFLATED)
    print(f"{out.relative_to(APP)}  {out.stat().st_size // 1024} KB")


def main():
    args = sys.argv[1:]
    only = None
    if "--variant" in args:
        i = args.index("--variant")
        only = args[i + 1] if i + 1 < len(args) else None
        del args[i:i + 2]
        if not only:
            raise SystemExit("--variant takes a variant id (console, dark or a colour)")
    templates = "--templates" in args
    if templates:
        args.remove("--templates")
        if only:
            raise SystemExit("--templates and --variant do not go together: a template has its own placeholder art")
    consoles = args or list(TYPES)
    unknown = [c for c in consoles if c not in TYPES]
    if unknown:
        raise SystemExit(f"unknown console: {', '.join(unknown)} (one of {', '.join(TYPES)})")
    sync_engine()
    if templates:
        with tempfile.TemporaryDirectory() as profile:
            for c in consoles:
                build_template(c, profile)
        return
    ASSETS.mkdir(parents=True, exist_ok=True)
    cat = catalog()
    known = {v["id"] for vs in cat.values() for v in vs}
    if only and only not in known:
        raise SystemExit(f"unknown variant: {only} (one of {', '.join(sorted(known))})")
    write_catalog(cat)
    # Consoles named on the command line that do not come in the variant asked for would build nothing, silently.
    lacking = [c for c in args if only and only not in {v["id"] for v in cat[c]}]
    if lacking:
        raise SystemExit(f"{', '.join(lacking)} not sold in {only}")
    with tempfile.TemporaryDirectory() as profile:
        for c in consoles:
            for v in cat[c]:
                if only in (None, v["id"]):
                    build(c, profile, v["id"], v["label"])


main()
