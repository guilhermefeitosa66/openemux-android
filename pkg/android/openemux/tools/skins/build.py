"""Builds the bundled controller skins (src/main/assets/skins) from the design.

  python3 tools/skins/build.py [--variant <id>] [CONSOLE ...]
  python3 tools/skins/build.py --templates [CONSOLE ...]   # the templates for skin creators (tools/skins/templates)

Every console is built in each of its variants: its own colours (src/main/assets/skins/<console>.<ext>), the
Dark variant (src/main/assets/skins/dark/<console>.<ext>), the same layout in a black and dark grey palette,
and each official colour it was sold in (issue 720: src/main/assets/skins/<colour>/<console>.<ext>). The
colour variants' art is lossy WebP, a tenth of the PNG's size; the console's own and Dark stay PNG. The
picker's list (src/main/assets/skins/variants.json: per console, its variants in order with the colours of
their swatch) is written on every run.

The consoles in OWN_FORMAT (issue 770) ship in OpenEmux's own skin format instead (docs/skin-format.md):
src/main/assets/skins/<console>.zip and dark/<console>.zip, skin.json and the six layouts (phone, tablet, fold),
one image per element cut out of the engine's art. Needs Pillow as well.

The layout and the art have one source: the engine of the design canvas's
Skin component (Skin.dc.html, between ENGINE START and ENGINE END). Each
representation (phone and tablet, portrait and landscape) is rendered by
headless Chrome from render.html, the game's screens are cut out
(transparent), and info.json gets the frames the engine laid out. Needs
Node, Google Chrome and ImageMagick.
"""
import functools
import json
import math
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
# The consoles bundled in OpenEmux's own skin format (issue 770), with no Delta or Manic identifier: each package is
# <console>.zip, skin.json and the six layouts, the art cut into one image per element (build_own).
OWN_FORMAT = ["GG", "SG1000", "S32X", "MCD", "SATURN", "PCE", "PCECD", "FDS"]
CONSOLES = list(TYPES) + OWN_FORMAT
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
    out = {c: [{"id": v["id"], "body": v["body"], "key": v["key"]} for v in vs] for c, vs in cat.items() if c in CONSOLES}
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
        "console.log(JSON.stringify({W:L.W,H:L.H,items:L.items.map(({look,...i})=>i),screens:L.screens,"
        "knobs:L.knobs.map(k=>({name:k.name,size:k.size})),name:L.name}))"
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
    return rep, L["name"], knob


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


# The templates for skin creators (issue 777): each console's bundled layout in OpenEmux's own skin format
# (docs/skin-format.md): skin.json with every control placed, grouped, bound and in the console's colours, the six
# layout folders with one plain placeholder image per element at its exact size, an SVG per layout that puts them
# together, and README.txt with the console's asset plan. tools/skins/templates/README.md is the guide. The APK does
# not carry them: creators download them from the repository.
TEMPLATES = HERE / "templates"
# The six layouts, and px per engine unit: the bundled art's scale (3 on phones, 2 on tablets) and 3 on a fold, whose
# 604 x 726 units (1812 x 2178 px) are about a Galaxy Z Fold's inner screen (1812 x 2176 px).
TEMPLATE_LAYOUTS = [("phone", "portrait"), ("phone", "landscape"), ("tablet", "portrait"), ("tablet", "landscape"),
                    ("fold", "portrait"), ("fold", "landscape")]
TEMPLATE_SCALE = {"phone": 3, "tablet": 2, "fold": 3}
# The quick actions in the screen's frame, by their input: the element's id.
ACTION_IDS = {"quickSave": "save", "quickLoad": "load", "turbo": "turbo", "toggleFastForward": "fast-forward",
              "fastForward": "fast-forward", "slowMotion": "slow-motion", "screenshot": "screenshot", "menu": "menu",
              "restart": "restart", "screens": "screens"}
# What the app draws an image over, as issue 776's renderer does (launch/OverlayLines.kt): the D-pad's ball image at
# the pad's travel box (the pad's side times DPAD_TRAVEL, centred), a stick's knob at half its well. Both are the
# thing itself, edge to edge: the renderer fits the whole image to the ball or the knob.
DPAD_TRAVEL = 44 / 60
STICK_KNOB = 0.5
# The placeholders' colours: a light body and frame, white controls outlined in dark grey, dark names.
P_BODY, P_FRAME, P_KEY, P_LINE, P_INK, P_WELL, P_ACTION = "#E6E6E6", "#BDBDBD", "#FFFFFF", "#444444", "#222222", "#CFCFCF", "#D6D6D6"
FONT = HERE / "SpaceGrotesk.ttf"


def template_layout(console, device, orient, variant="console"):
    """The engine's layout of [console] on [device] in [orient], with each item's look, the bezel and the colours."""
    return engine(f"OE.layout({json.dumps(console)},{json.dumps(device)},{json.dumps(orient)},"
                  f"{{export:true,variant:{json.dumps(variant)}}})")


def color(value):
    """An engine colour (#rrggbb or rgba(r,g,b,a)) in the format's #RRGGBB or #RRGGBBAA."""
    value = value.strip()
    if value.startswith("rgba("):
        r, g, b, a = (float(v) for v in value[5:-1].split(","))
        out = f"#{round(r):02X}{round(g):02X}{round(b):02X}"
        return out if round(a * 255) == 255 else out + f"{round(a * 255):02X}"
    if len(value) == 4:
        value = "#" + "".join(ch * 2 for ch in value[1:])
    return value.upper()


def half_up(v):
    """[v] rounded half up, as Kotlin's Math.round (Python's round goes to the even neighbour): the tests redo it."""
    return math.floor(v + 0.5)


def px_rect(f, scale, W, H):
    """[f] (units) in whole pixels: its edges rounded, so neighbours still meet, and kept inside the layout."""
    x0, y0 = max(0, half_up(f["x"] * scale)), max(0, half_up(f["y"] * scale))
    x1, y1 = min(W, half_up((f["x"] + f["width"]) * scale)), min(H, half_up((f["y"] + f["height"]) * scale))
    return {"x": x0, "y": y0, "width": x1 - x0, "height": y1 - y0}


def kebab(name):
    """An input name as an element id: cUp is c-up."""
    return "".join(f"-{ch.lower()}" if ch.isupper() else ch for ch in name)


def element(it, scale, W, H, taken):
    """An engine item as an element of the format; [taken] the ids already used in the layout."""
    inputs, look = it["inputs"], it.get("look") or {}
    if isinstance(inputs, dict):
        first = inputs["up"]
        if first.startswith("rightAnalogStick"):
            base, kind, bind = "stick2", "stick", "rightAnalogStick"
        elif first.startswith("analogStick"):
            base, kind, bind = "stick", "stick", "analogStick"
        else:
            base, kind, bind = "dpad", "dpad", "dpad"
    elif look.get("group") == "actions":
        base, kind, bind = ACTION_IDS[inputs[0]], "action", inputs[0]
    else:
        base, kind, bind = kebab(inputs[0]), "button", inputs[0]
    eid, i = base, 1
    while eid in taken:
        i += 1
        eid = f"{base}{i}"
    taken.add(eid)
    out = {"id": eid, "type": kind, "bind": bind}
    if look.get("group"):
        out["group"] = look["group"]
    out.update(px_rect(it["frame"], scale, W, H))
    if look.get("shape"):
        out["shape"] = look["shape"]
    if look.get("label"):
        out["label"] = look["label"]
    if look.get("colors") and kind != "action":
        out["colors"] = {k: color(v) for k, v in look["colors"].items()}
    return out


def skin_layout(L, scale):
    """The format's layout from the engine's [L] at [scale] px per unit."""
    W, H = half_up(L["W"] * scale), half_up(L["H"] * scale)
    taken = set()
    return {
        "size": {"width": W, "height": H},
        "screens": [px_rect(s, scale, W, H) for s in L["screens"]],
        "background": {"color": color(L["colors"]["body"])},
        "frame": dict(px_rect(L["frame"], scale, W, H),
                      colors={"frame": color(L["colors"]["bezel"]), "icons": color(L["colors"]["icons"])}),
        "elements": [element(it, scale, W, H, taken) for it in L["items"]],
    }


def images(layout):
    """Every image a layout's elements may have: file name, (width, height) in px, what it is, the element (or None)."""
    out = [("background.png", (layout["size"]["width"], layout["size"]["height"]), "the whole layout, behind everything", None),
           ("frame.png", (layout["frame"]["width"], layout["frame"]["height"]), "the bezel around the game screen", None)]
    for e in layout["elements"]:
        w, h = e["width"], e["height"]
        if e["type"] == "dpad":
            side = half_up(min(w, h) * DPAD_TRAVEL)
            out += [(f"{e['id']}.png", (w, h), "the D-pad", e),
                    (f"{e['id']}-ball.png", (side, side), "the D-pad's ball, moved by the thumb over the pad", e)]
        elif e["type"] == "stick":
            side = half_up(min(w, h) * STICK_KNOB)
            out += [(f"{e['id']}-base.png", (w, h), "the stick's well", e),
                    (f"{e['id']}-knob.png", (side, side), "the stick's knob, moved by the thumb", e)]
        else:
            what = {"action": "a quick action on the frame", "button": "a button", "keypad": "a keypad key"}[e["type"]]
            out.append((f"{e['id']}.png", (w, h), what, e))
    return out


@functools.lru_cache(maxsize=None)
def font(size):
    from PIL import ImageFont
    return ImageFont.truetype(str(FONT), max(8, round(size)))


def placeholder(name, size, e, layout):
    """The plain placeholder for one image: its shape outlined, the element's name on it."""
    from PIL import Image, ImageDraw
    w, h = size
    if name in ("background.png", "frame.png"):
        img = Image.new("RGBA", size, P_BODY if name == "background.png" else P_FRAME)
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, w - 1, h - 1], outline=P_LINE, width=3)
        if name == "frame.png":
            fx, fy = layout["frame"]["x"], layout["frame"]["y"]
            for s in layout["screens"]:
                d.rectangle([s["x"] - fx, s["y"] - fy, s["x"] - fx + s["width"] - 1, s["y"] - fy + s["height"] - 1], fill=(0, 0, 0, 0))
        if name == "background.png":
            f = font(min(48, h * 0.03))
            d.text((w / 2, h - 12 - f.size), "background", fill=P_INK, font=f, anchor="mm")
        return img.quantize(64, method=Image.Quantize.FASTOCTREE)
    k = 4  # drawn four times larger and reduced: smooth edges
    W, H = w * k, h * k
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    line = max(2, round(min(W, H) * 0.035))
    label = name[:-4]
    if name.endswith("-ball.png"):
        d.ellipse([line / 2, line / 2, W - line / 2, H - line / 2], fill=P_KEY, outline=P_LINE, width=line)
        label = None
    elif name.endswith("-knob.png"):
        d.ellipse([line / 2, line / 2, W - line / 2, H - line / 2], fill=P_KEY, outline=P_LINE, width=line)
        label = "knob"
    elif e["type"] == "dpad":
        t = min(W, H) * 0.34
        cx, cy = W / 2, H / 2
        pts = [(cx - t / 2, 0), (cx + t / 2, 0), (cx + t / 2, cy - t / 2), (W, cy - t / 2), (W, cy + t / 2), (cx + t / 2, cy + t / 2),
               (cx + t / 2, H), (cx - t / 2, H), (cx - t / 2, cy + t / 2), (0, cy + t / 2), (0, cy - t / 2), (cx - t / 2, cy - t / 2)]
        pts = [(min(max(x, line / 2), W - line / 2), min(max(y, line / 2), H - line / 2)) for x, y in pts]
        d.polygon(pts, fill=P_KEY, outline=P_LINE, width=line)
        # The name in the up arm: the ball sits on the centre.
        d.text((cx, (cy - t / 2) / 2 + line), e["id"], fill=P_INK, font=font(min(t * 0.32, (cy - t / 2) * 0.4)), anchor="mm")
        label = None
    elif e["type"] == "stick":
        d.ellipse([line / 2, line / 2, W - line / 2, H - line / 2], fill=P_WELL, outline=P_LINE, width=line)
        # The name above the centre: the knob sits on it.
        d.text((W / 2, H * 0.14), e["id"], fill=P_INK, font=font(H * 0.09), anchor="mm")
        label = None
    else:
        box = [line / 2, line / 2, W - line / 2, H - line / 2]
        fill = P_ACTION if e["type"] == "action" else P_KEY
        shape = e.get("shape") or ("rect" if e["type"] == "action" else "circle")
        if shape == "circle":
            d.ellipse(box, fill=fill, outline=P_LINE, width=line)
        else:
            radius = min(W, H) / 2 if shape == "pill" else min(W, H) * 0.2
            d.rounded_rectangle(box, radius=radius, fill=fill, outline=P_LINE, width=line)
    if label:
        text = min(H * 0.34, W / max(2, len(label)) * 1.5)
        d.text((W / 2, H / 2), label, fill=P_INK, font=font(text), anchor="mm")
    # A placeholder has a handful of colours: a palette keeps the zips small.
    return img.resize(size, Image.LANCZOS).quantize(64, method=Image.Quantize.FASTOCTREE)


def layout_svg(layout_id, layout):
    """One layout put together: each image a layer at its place, linked from the layout's folder, and a hidden guide
    layer with the screens, for creators who paint the whole layout at once and export each layer to its file."""
    W, H = layout["size"]["width"], layout["size"]["height"]
    def lay(name, body, hidden=False):
        style = ' style="display:none"' if hidden else ""
        return f'<g id="{name}" inkscape:groupmode="layer" inkscape:label="{name}"{style}>\n{body}</g>\n'
    def img(file, x, y, w, h):
        return f'  <image href="../{layout_id}/{file}" x="{x}" y="{y}" width="{w}" height="{h}"/>\n'
    out = lay("background", img("background.png", 0, 0, W, H))
    f = layout["frame"]
    out += lay("frame", img("frame.png", f["x"], f["y"], f["width"], f["height"]))
    for name, (w, h), _, e in images(layout)[2:]:
        x = e["x"] + (e["width"] - w) / 2
        y = e["y"] + (e["height"] - h) / 2
        out += lay(name[:-4], img(name, n_(x), n_(y), w, h))
    guides = "".join(f'  <rect x="{s["x"]}" y="{s["y"]}" width="{s["width"]}" height="{s["height"]}" fill="none" '
                     f'stroke="#3A7BD5" stroke-width="4" stroke-dasharray="12 8"/>\n' for s in layout["screens"])
    out += lay("screens", guides, hidden=True)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:inkscape="http://www.inkscape.org/namespaces/inkscape" '
            f'width="{W}" height="{H}" viewBox="0 0 {W} {H}">\n{out}</svg>\n')


def n_(v):
    return f"{v:.1f}".rstrip("0").rstrip(".")


def skin_json(skin):
    """skin.json in the app's key order and two-space indents (docs/skin-format.md, Writing); unlike the app, it keeps
    a D-pad's optional "bind": "dpad", so every element reads the same."""
    return json.dumps(skin, indent=2, ensure_ascii=False) + "\n"


def template_readme(console, name, skin):
    """README.txt: the console's asset plan, every file at its size, and how to install the skin."""
    lines = [f"{name} skin template for OpenEmux (Android)", "",
             "A controller skin in OpenEmux's own format, with plain placeholder art at the exact sizes the app reads.",
             "Repaint the images, keep their names and sizes, and install the folder. The guide (files, layouts,",
             "colours, binds, import) is tools/skins/templates/README.md in the openemux-android repository:",
             "https://github.com/guilhermefeitosa66/openemux-android/blob/openemux-mobile/pkg/android/openemux/tools/skins/templates/README.md",
             "", "Every image is optional: a missing one is drawn from the colours, shape and label in skin.json.",
             "sources/<layout>.svg puts a layout's images together, one layer each, to paint them in context.", ""]
    for layout_id, layout in skin["layouts"].items():
        s = layout["size"]
        lines += [f"{layout_id}/  ({s['width']} x {s['height']} px)"]
        for sc in layout["screens"]:
            lines.append(f"  game screen at {sc['x']}, {sc['y']}, {sc['width']} x {sc['height']} px (left clear: nothing is drawn there)")
        for file, (w, h), what, e in images(layout):
            bind = f", bind {e['bind']}" if e else ""
            lines.append(f"  {file:<22} {w:>5} x {h:<5} px  {what}{bind}")
        lines.append("")
    lines += ["Install (until the app can import a zip):", "",
              f"  1. Unzip this file: it gives one folder, {console}-template/ (rename it if you like).",
              f"  2. Copy that folder into Android/data/com.openemux.mobile/files/skins/{console}/ on the device.",
              f"  3. Start a {name} game. Delete the folder to go back to the bundled skin.", ""]
    return "\n".join(lines)


def build_template(console):
    """[console]'s template: tools/skins/templates/<console>-template.zip."""
    folder = WORK / "templates" / f"{console}-template"
    shutil.rmtree(folder, ignore_errors=True)
    folder.mkdir(parents=True)
    layouts, name = {}, None
    for device, orient in TEMPLATE_LAYOUTS:
        L = template_layout(console, device, orient)
        check_fit(L, f"{console} template {device} {orient}")
        name = name or L["name"]
        layouts[f"{device}-{orient}"] = skin_layout(L, TEMPLATE_SCALE[device])
    skin = {"format": 1, "console": console, "name": f"{name} template", "forms": ["phone", "tablet", "fold"], "layouts": layouts}
    (folder / "sources").mkdir()
    for layout_id, layout in layouts.items():
        (folder / layout_id).mkdir()
        for file, size, _, e in images(layout):
            placeholder(file, size, e, layout).save(folder / layout_id / file, optimize=True)
        (folder / "sources" / f"{layout_id}.svg").write_text(layout_svg(layout_id, layout), encoding="utf-8")
    # UTF-8 whatever the locale: the labels hold symbols (the PlayStation's, the N64's C arrows).
    (folder / "skin.json").write_text(skin_json(skin), encoding="utf-8")
    (folder / "README.txt").write_text(template_readme(console, name, skin), encoding="utf-8")
    TEMPLATES.mkdir(exist_ok=True)
    out = TEMPLATES / f"{console}-template.zip"
    zip_folder(folder, out, f"{folder.name}/")
    print(f"{out.relative_to(APP)}  {out.stat().st_size // 1024} KB")


def zip_folder(folder, out, root=""):
    """[folder]'s files into the zip [out] under [root], sorted and dated 1980-01-01: a rebuild that changes nothing gives
    the same zip, not a new binary in git."""
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(folder.rglob("*")):
            if f.is_file():
                info = zipfile.ZipInfo(root + f.relative_to(folder).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info, f.read_bytes())


def own_layout(console, device, orient, variant, folder, profile, ball):
    """One layout of [console]'s package in OpenEmux's own format: its skin.json entry, and its images in [folder].

    The engine renders the layout twice: the art without the quick actions' icons (background.png, the game's screens
    cut out), and only the controls on a transparent page. Each element's image is the second render cut at the
    element's rect, so drawn over the background it lands on the same pixels and a dimmed quick action is not drawn
    twice. frame.png is the bezel cut out of the background; the D-pad's ball the engine's circle at the ball's size."""
    from PIL import Image
    L = template_layout(console, device, orient, variant)
    where = f"{console} {variant} {device} {orient}"
    check_fit(L, where)
    # A variant changes colours only: the same frames, screens and knobs as the console's own skin.
    if variant != "console" and engine_layout(console, device, orient, variant=variant) != engine_layout(console, device, orient):
        raise SystemExit(f"{where}: the layout differs from the console's own skin")
    scale = TEMPLATE_SCALE[device]
    layout = skin_layout(L, scale)
    W, H = layout["size"]["width"], layout["size"]["height"]
    folder.mkdir(parents=True)
    bg, controls = folder / "background.png", folder.parent / f"{folder.name}-controls.png"
    screenshot(console, device, orient, L["W"], L["H"], scale, bg, profile, "&layer=background", variant)
    cut_screens(bg, L["screens"], scale)
    screenshot(console, device, orient, L["W"], L["H"], scale, controls, profile, "&layer=controls", variant)
    art, keys = Image.open(bg).convert("RGBA"), Image.open(controls).convert("RGBA")
    controls.unlink()
    if art.size != (W, H) or keys.size != (W, H):
        raise SystemExit(f"{where}: rendered {art.size}, the layout is {W} x {H}")
    for file, (w, h), _, e in images(layout):
        if file == "background.png":
            continue
        if file == "frame.png":
            f = layout["frame"]
            img = art.crop((f["x"], f["y"], f["x"] + w, f["y"] + h))
        elif file.endswith("-ball.png"):
            img = ball.resize((w, h), Image.LANCZOS)
        elif e["type"] == "stick":
            raise SystemExit(f"{where}: a stick's images are not cut yet (no console in OWN_FORMAT has one)")
        else:
            img = keys.crop((e["x"], e["y"], e["x"] + w, e["y"] + h))
        img.save(folder / file, optimize=True)
    art.save(bg, optimize=True)
    return layout, L["name"]


def build_own(console, profile, variant="console", label=None):
    """[console]'s package in [variant] in OpenEmux's own skin format: <assets>/<variant>/<console>.zip."""
    from PIL import Image
    folder = WORK / "own" / variant / console
    shutil.rmtree(folder, ignore_errors=True)
    folder.mkdir(parents=True)
    # The D-pad's circle, edge to edge: every layout's ball is this, at its own size.
    knob = folder.parent / f"{console}-ball.png"
    screenshot(console, "phone", "portrait", KNOB_UNITS, KNOB_UNITS, KNOB_SCALE, knob, profile, f"&dpadKnob={KNOB_UNITS}", variant)
    ball = Image.open(knob).convert("RGBA")
    ball = ball.crop(ball.getbbox())
    knob.unlink()
    layouts, name = {}, None
    for device, orient in TEMPLATE_LAYOUTS:
        layout_id = f"{device}-{orient}"
        layouts[layout_id], name = own_layout(console, device, orient, variant, folder / layout_id, profile, ball)
    suffix = {"console": ""}.get(variant, f" {label or variant.capitalize()}")
    skin = {"format": 1, "console": console, "name": f"OpenEmux {name}{suffix}", "forms": ["phone", "tablet", "fold"],
            "layouts": layouts}
    (folder / "skin.json").write_text(skin_json(skin), encoding="utf-8")
    variant_dir(variant).mkdir(parents=True, exist_ok=True)
    pkg = variant_dir(variant) / f"{console}.zip"
    zip_folder(folder, pkg)
    print(f"{pkg.relative_to(APP)}  {pkg.stat().st_size // 1024} KB")


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
    consoles = args or CONSOLES
    unknown = [c for c in consoles if c not in CONSOLES]
    if unknown:
        raise SystemExit(f"unknown console: {', '.join(unknown)} (one of {', '.join(CONSOLES)})")
    sync_engine()
    if templates or any(c in OWN_FORMAT for c in consoles):
        try:
            import PIL  # noqa: F401
        except ImportError:
            raise SystemExit("--templates and the OpenEmux-format packages need Pillow: pip install pillow")
    if templates:
        for c in consoles:
            build_template(c)
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
                    (build_own if c in OWN_FORMAT else build)(c, profile, v["id"], v["label"])


main()
