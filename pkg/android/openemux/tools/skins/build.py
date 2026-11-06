"""Builds the bundled controller skins (src/main/assets/skins) from the design.

  python3 tools/skins/build.py [--variant <id>] [CONSOLE ...]
  python3 tools/skins/build.py --templates [CONSOLE ...]   # the templates for skin creators (tools/skins/templates)

Every console is built in each of its variants: its own colours (src/main/assets/skins/<console>.zip), the Dark
variant (src/main/assets/skins/dark/<console>.zip), the same layout in a black and dark grey palette, and each
official colour it was sold in (issue 720: src/main/assets/skins/<colour>/<console>.zip). The picker's list
(src/main/assets/skins/variants.json: per console, its variants in order with the colours of their swatch) is written
on every run.

Every package is in OpenEmux's own skin format (docs/skin-format.md; issues 770, 771, 772, 806 and 808, which moved the
last Delta package, the DS's): skin.json and the six layouts (phone, tablet, fold), one image per element cut out of
the engine's art, a console with two screens with a page per screen layout. The console's own and Dark images are PNG,
a colour variant's lossy WebP, a tenth of the size.

The layout and the art have one source: the engine of the design canvas's Skin component (Skin.dc.html, between ENGINE
START and ENGINE END). Each layout is rendered by headless Chrome from render.html in three layers (the console's
body, the bezel, the controls), so the background holds the body alone and each control its whole self, relief
included; skin.json gets the frames the engine laid out. Needs Node, Google Chrome, ImageMagick and Pillow.
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

# The consoles bundled in OpenEmux's own skin format (issue 770), with no Delta or Manic identifier: each package is
# <console>.zip, skin.json and the six layouts, the art cut into one image per element (build_own).
# Issue 771 added the Atari consoles, the Lynx, the Neo Geo Pocket, the WonderSwan, the Vectrex and the Virtual Boy.
# Issue 772 the consoles with a keypad, each layout with its keypad page (<layout>/keypad/, see keypad_page).
# Issue 806 the eleven single-screen consoles that were Delta/Manic packages, in every variant they had: their colour
# variants' images in WebP (see PNG_VARIANTS). Issue 808 the DS, the last that was a Delta package, each layout with
# its screen-layout pages (<layout>/<page>/, see screen_page).
OWN_FORMAT = ["NDS", "FC", "SFC", "N64", "GBC", "GBA", "MD", "GB", "GC", "SMS", "PS", "PSP",
              "GG", "SG1000", "S32X", "MCD", "SATURN", "PCE", "PCECD", "FDS",
              "A2600", "A7800", "LYNX", "NGP", "WS", "VECTREX", "VB",
              "A5200", "CV", "INTV", "O2"]
CONSOLES = OWN_FORMAT
# The D-pad's circle: one image per console, stretched by the app over each D-pad's travel square.
KNOB_UNITS, KNOB_SCALE = 100, 4
# The layers a page is rendered in, side by side in one screenshot (render.html?layers=): the console's body, the bezel,
# and every control alone in a cell of a grid, each cut into its own images (own_page).
LAYERS = ("body", "frame", "sprites")
# How many times a page is rendered before the build gives up on a screenshot that came out incomplete or never
# came, and how long Chrome gets for one.
SHOTS = 3
SHOT_SECONDS = 90
# The page Chrome renders: the engine's parts as HTML.
RENDER = "render.html"
# Consoles with two screens get one art per screen layout: the layout itself draws the two one over the other, and
# each other screen layout is one of its "screenLayouts" pages (issue 807), in <layout>/<page>/ (screen_page).
SCREEN_LAYOUTS = {"NDS": ["top", "bottom", "side"]}
# The palettes whose art stays PNG: the console's own colours and Dark (issue 718). Every other variant is one
# of the console's official colours (issue 720), its art in lossy WebP: quality 90 and the alpha lossless, so the
# screens' cut-outs keep their exact edges.
PNG_VARIANTS = ("console", "dark")
WEBP = ["-quality", "90", "-define", "webp:alpha-quality=100", "-define", "webp:method=6"]


def variant_dir(variant):
    """The assets folder [variant]'s packages go in: the console's own in skins/, each other in its own folder."""
    return ASSETS if variant == "console" else ASSETS / variant


def write_changed(path, text):
    """[text] into [path] unless it is there already: runs side by side (one per group of consoles) all write the same
    engine and the same catalog, and none of them may find the file half written by another."""
    if not path.is_file() or path.read_text() != text:
        path.write_text(text)


def sync_engine():
    src = (HERE / "Skin.dc.html").read_text()
    engine = src.split("// ENGINE START")[1].split("// ENGINE END")[0]
    write_changed(HERE / "engine.js", engine + "\nwindow.OE = OE;\n")


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
    write_changed(ASSETS / "variants.json", json.dumps(out, indent=1) + "\n")


def to_webp(folder, files):
    """Each of [files] (PNGs in [folder]) as WebP; the new names by the old."""
    names = {}
    for f in files:
        webp = f.removesuffix(".png") + ".webp"
        subprocess.run(["convert", str(folder / f), *WEBP, str(folder / webp)], check=True)
        (folder / f).unlink()
        names[f] = webp
    return names


def engine_layout(console, device, orient, screens=None, variant="console", keypad=False):
    script = (
        "global.window={};require(process.argv[1]);"
        "const L=window.OE.layout(process.argv[2],process.argv[3],process.argv[4],"
        "{export:true,screens:process.argv[5]||undefined,variant:process.argv[6],keypad:process.argv[7]==='1'});"
        "console.log(JSON.stringify({W:L.W,H:L.H,items:L.items.map(({look,...i})=>i),screens:L.screens,"
        "knobs:L.knobs.map(k=>({name:k.name,size:k.size})),name:L.name}))"
    )
    out = subprocess.run(["node", "-e", script, str(HERE / "engine.js"), console, device, orient, screens or "", variant,
                          "1" if keypad else "0"],
                         capture_output=True, text=True, check=True).stdout
    return json.loads(out)


def screenshot(console, device, orient, w, h, scale, out, profile, query="", variant="console"):
    """One page of the renderer as a PNG. Headless Chrome now and then never comes back (seen with several builds
    running side by side): each try gets [SHOT_SECONDS], and the page is asked for again up to [SHOTS] times."""
    url = f"file://{HERE / RENDER}?console={console}&device={device}&orient={orient}&variant={variant}{query}"
    for attempt in range(SHOTS):
        try:
            subprocess.run([
                CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--user-data-dir={profile}",
                f"--force-device-scale-factor={scale}", f"--window-size={w},{h}", "--default-background-color=00000000",
                "--run-all-compositor-stages-before-draw", "--virtual-time-budget=4000", f"--screenshot={out}", url,
            ], check=True, capture_output=True, timeout=SHOT_SECONDS)
            return
        except subprocess.TimeoutExpired:
            print(f"{console} {variant} {device} {orient}{query}: Chrome did not answer in {SHOT_SECONDS} s "
                  f"(screenshot {attempt + 1} of {SHOTS})", file=sys.stderr)
    raise SystemExit(f"{console} {variant} {device} {orient}{query}: no screenshot after {SHOTS} tries")


def check_fit(L, where):
    """The screens inside the art and clear of every item, or the build stops: a layout's art must never hide a key."""
    # Edges that meet do not overlap: the engine rounds each position and size to a tenth of a unit, so an edge
    # (position plus size) may land up to a tenth past the neighbour's, a third of a pixel at most.
    eps = 0.1 + 1e-6

    def overlap(a, b):
        return (a["x"] < b["x"] + b["width"] - eps and b["x"] < a["x"] + a["width"] - eps
                and a["y"] < b["y"] + b["height"] - eps and b["y"] < a["y"] + a["height"] - eps)
    for sc in L["screens"]:
        if sc["x"] < -eps or sc["y"] < -eps or sc["x"] + sc["width"] > L["W"] + eps or sc["y"] + sc["height"] > L["H"] + eps:
            raise SystemExit(f"{where}: a screen leaves the art")
        for it in L["items"]:
            if overlap(sc, it["frame"]):
                raise SystemExit(f"{where}: a screen covers {it['inputs']}")


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
# A keypad key's id by its input: key-1 … key-9, key-0, key-star, key-hash (docs/skin-format.md).
KEYPAD_IDS = dict({f"keypad{i}": f"key-{i}" for i in range(10)}, keypadStar="key-star", keypadHash="key-hash")
DPAD_TRAVEL = 44 / 60
STICK_KNOB = 0.5
# The placeholders' colours: a light body and frame, white controls outlined in dark grey, dark names.
P_BODY, P_FRAME, P_KEY, P_LINE, P_INK, P_WELL, P_ACTION = "#E6E6E6", "#BDBDBD", "#FFFFFF", "#444444", "#222222", "#CFCFCF", "#D6D6D6"
FONT = HERE / "SpaceGrotesk.ttf"


def template_layout(console, device, orient, variant="console", keypad=False, screens=None):
    """The engine's layout of [console] on [device] in [orient] (its keypad page with [keypad], one of its screen
    layouts with [screens]), with each item's look, the bezel and the colours."""
    return engine(f"OE.layout({json.dumps(console)},{json.dumps(device)},{json.dumps(orient)},"
                  f"{{export:true,variant:{json.dumps(variant)},keypad:{json.dumps(keypad)},screens:{json.dumps(screens)}||undefined}})")


def screen_layouts(console, device, orient, variant="console", where=None):
    """The engine's layout of each of [console]'s screen layouts (SCREEN_LAYOUTS), by page id; none for a console with
    one screen. Each is checked as its layout is."""
    pages = {}
    for page in SCREEN_LAYOUTS.get(console, []):
        P = template_layout(console, device, orient, variant, screens=page)
        check_fit(P, f"{where or f'{console} {device} {orient}'} {page}")
        pages[page] = P
    return pages


@functools.lru_cache(maxsize=None)
def has_keypad(console):
    """Whether [console]'s layouts have a keypad page (the engine's C entry has a keypad)."""
    return engine(f"!!OE.C[{json.dumps(console)}].keypad")


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


def num(v):
    """[v] to a tenth, a whole number as an int: 7.5, 9."""
    v = round(v + 0.0, 1)
    return int(v) if v == int(v) else v


def element(it, scale, W, H, taken):
    """An engine item as an element of the format; [taken] the ids already used in the layout. Its rect is what its
    image covers (the engine's art: the key turned, with its relief under it), its frame when the engine gives none."""
    inputs, look = it["inputs"], it.get("look") or {}
    if isinstance(inputs, dict):
        first = inputs["up"]
        if first.startswith("rightAnalogStick"):
            base, kind, bind = "stick2", "stick", "rightAnalogStick"
        elif first.startswith("analogStick"):
            base, kind, bind = "stick", "stick", "analogStick"
        else:
            # A second D-pad (the Virtual Boy's right one) is the format's dpad2, its directions the console's own.
            bind = it.get("dpad") or "dpad"
            base, kind = bind, "dpad"
    elif look.get("group") == "actions":
        base, kind, bind = ACTION_IDS[inputs[0]], "action", inputs[0]
    elif inputs[0] in KEYPAD_IDS:
        base, kind, bind = KEYPAD_IDS[inputs[0]], "keypad", inputs[0]
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
    out.update(px_rect(it.get("art") or it["frame"], scale, W, H))
    if look.get("shape"):
        out["shape"] = look["shape"]
    if look.get("radius"):
        out["radius"] = num(look["radius"] * scale)
    if look.get("rotation"):
        out["rotation"] = look["rotation"]
    if look.get("depth"):
        out["depth"] = num(look["depth"] * scale)
    if look.get("label"):
        out["label"] = look["label"]
    if look.get("labelSize") and kind != "stick":
        out["labelSize"] = num(look["labelSize"] * scale)
    if look.get("labelOffset"):
        out["labelOffset"] = {k: num(look["labelOffset"][k] * scale) for k in ("x", "y")}
    if look.get("colors") and kind != "action":
        # No relief colour: the app shades a relief from its face, so a button repainted in the editor takes its relief
        # along, and the editor's Relief holds a colour only once the player picks one.
        out["colors"] = {k: color(v) for k, v in look["colors"].items() if k != "relief"}
    if look.get("labelBeside"):
        # The label is printed beside the control, so the control's image leaves it out: the app draws it.
        out["art"] = "key"
    return out


def frame_json(L, scale, W, H):
    """The engine's bezel as the format's frame: its rect, its corners and its colours."""
    return dict(px_rect(L["frame"], scale, W, H), radius=num(L["frame"]["radius"] * scale),
                colors={"frame": color(L["colors"]["bezel"]), "icons": color(L["colors"]["icons"])})


def skin_layout(L, scale, K=None, S=None):
    """The format's layout from the engine's [L] at [scale] px per unit; [K], the engine's keypad page of the same
    layout, adds the layout's keypad page; [S], the engine's screen layouts of it by page id, its screen-layout pages."""
    W, H = half_up(L["W"] * scale), half_up(L["H"] * scale)
    taken = set()
    out = {
        "size": {"width": W, "height": H},
        "screens": [px_rect(s, scale, W, H) for s in L["screens"]],
        "background": {"color": color(L["colors"]["body"])},
        "frame": frame_json(L, scale, W, H),
        "elements": [element(it, scale, W, H, taken) for it in L["items"]],
    }
    if K is not None:
        out["keypad"] = keypad_page(L, K, scale, W, H)
    if S:
        out["screenLayouts"] = {page: screen_page(L, P, scale, W, H) for page, P in S.items()}
    return out


def screen_page(L, P, scale, W, H):
    """One screen-layout page of a layout ([L]) from the engine's [P] (the same layout with the screens shown another
    way): the same size, its own screens, bezel, controls and background, in <layout>/<page>/ (docs/skin-format.md)."""
    if (P["W"], P["H"]) != (L["W"], L["H"]):
        raise SystemExit(f"{L['name']}: a screen layout changes the layout's size")
    taken = set()
    return {"screens": [px_rect(s, scale, W, H) for s in P["screens"]],
            "background": {"color": color(P["colors"]["body"])},
            "frame": frame_json(P, scale, W, H),
            "elements": [element(it, scale, W, H, taken) for it in P["items"]]}


def page_layout(layout, page):
    """Screen-layout page [page] of [layout] as a layout of its own: its size, screens, frame and controls."""
    return dict(layout["screenLayouts"][page], size=layout["size"])

def keypad_page(L, K, scale, W, H):
    """The keypad page of a layout ([L]) from the engine's [K]: the same size, screens and bezel, controls of its own,
    and a background of its own, in <layout>/keypad/."""
    if (K["W"], K["H"], K["screens"], K["frame"]) != (L["W"], L["H"], L["screens"], L["frame"]):
        raise SystemExit(f"{L['name']}: the keypad page moves the screen or the bezel")
    taken = set()
    return {"background": {"color": color(K["colors"]["body"])},
            "elements": [element(it, scale, W, H, taken) for it in K["items"]]}


def images(layout, keypad=False, page=None):
    """Every image a layout's elements may have: file name, (width, height) in px, what it is, the element (or None).
    With [keypad], its keypad page's: in its keypad/ folder, its own background and no frame (the layout's). With
    [page], that screen-layout page's: in its folder, its own background, frame and controls."""
    if page:
        P = page_layout(layout, page)
        return [(f"{page}/background.png", (layout["size"]["width"], layout["size"]["height"]), f"the {page} page, behind everything", None),
                (f"{page}/frame.png", (P["frame"]["width"], P["frame"]["height"]), f"the {page} page's bezel", None)] + \
            element_images(P["elements"], f"{page}/")
    if keypad:
        out = [("keypad/background.png", (layout["size"]["width"], layout["size"]["height"]), "the keypad page, behind everything", None)]
        return out + element_images(layout["keypad"]["elements"], "keypad/")
    out = [("background.png", (layout["size"]["width"], layout["size"]["height"]), "the whole layout, behind everything", None),
           ("frame.png", (layout["frame"]["width"], layout["frame"]["height"]), "the bezel around the game screen", None)]
    return out + element_images(layout["elements"], "")


def all_images(layout):
    """Every image of a layout: its own, its keypad page's and its screen-layout pages'."""
    return (images(layout) + (images(layout, True) if "keypad" in layout else [])
            + [i for page in layout.get("screenLayouts", {}) for i in images(layout, page=page)])


def element_images(elements, folder):
    """The images of [elements], each file in [folder] (the layout's, "", or its keypad page's)."""
    out = []
    for e in elements:
        w, h = e["width"], e["height"]
        if e["type"] == "dpad":
            side = half_up(min(w, h) * DPAD_TRAVEL)
            out += [(f"{folder}{e['id']}.png", (w, h), "the D-pad", e),
                    (f"{folder}{e['id']}-ball.png", (side, side), "the D-pad's ball, moved by the thumb over the pad", e)]
        elif e["type"] == "stick":
            side = half_up(min(w, h) * STICK_KNOB)
            out += [(f"{folder}{e['id']}-base.png", (w, h), "the stick's well", e),
                    (f"{folder}{e['id']}-knob.png", (side, side), "the stick's knob, moved by the thumb", e)]
        else:
            what = {"action": "a quick action on the frame", "button": "a button", "keypad": "a keypad key"}[e["type"]]
            out.append((f"{folder}{e['id']}.png", (w, h), what, e))
    return out


@functools.lru_cache(maxsize=None)
def font(size):
    from PIL import ImageFont
    return ImageFont.truetype(str(FONT), max(8, round(size)))


def placeholder(name, size, e, layout):
    """The plain placeholder for one image: its shape outlined, the element's name on it."""
    from PIL import Image, ImageDraw
    w, h = size
    # A keypad page's images are in its keypad/ folder: the name is the file's own.
    name = name.rsplit("/", 1)[-1]
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


def layout_svg(layout_id, layout, keypad=False, page=None):
    """One layout put together (its [keypad] page: that page's background and controls over the layout's frame; its
    screen-layout [page]: that page's own): each image a layer at its place, linked from the layout's folder, and a
    hidden guide layer with the screens, for creators who paint the whole layout at once and export each layer to its
    file."""
    W, H = layout["size"]["width"], layout["size"]["height"]
    shown = page_layout(layout, page) if page else layout
    folder = f"{page}/" if page else ""
    def lay(name, body, hidden=False):
        style = ' style="display:none"' if hidden else ""
        return f'<g id="{name}" inkscape:groupmode="layer" inkscape:label="{name}"{style}>\n{body}</g>\n'
    def img(file, x, y, w, h):
        return f'  <image href="../{layout_id}/{file}" x="{x}" y="{y}" width="{w}" height="{h}"/>\n'
    out = lay("background", img("keypad/background.png" if keypad else f"{folder}background.png", 0, 0, W, H))
    f = shown["frame"]
    out += lay("frame", img(f"{folder}frame.png", f["x"], f["y"], f["width"], f["height"]))
    for name, (w, h), _, e in images(layout, keypad, page)[1 if keypad else 2:]:
        x = e["x"] + (e["width"] - w) / 2
        y = e["y"] + (e["height"] - h) / 2
        out += lay(name.rsplit("/", 1)[-1][:-4], img(name, n_(x), n_(y), w, h))
    guides = "".join(f'  <rect x="{s["x"]}" y="{s["y"]}" width="{s["width"]}" height="{s["height"]}" fill="none" '
                     f'stroke="#3A7BD5" stroke-width="4" stroke-dasharray="12 8"/>\n' for s in shown["screens"])
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
        for page, P in layout.get("screenLayouts", {}).items():
            for sc in P["screens"]:
                lines.append(f"  {page}/ game screen at {sc['x']}, {sc['y']}, {sc['width']} x {sc['height']} px (its screen-layout page)")
        for file, (w, h), what, e in all_images(layout):
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
        K = template_layout(console, device, orient, keypad=True) if has_keypad(console) else None
        if K is not None:
            check_fit(K, f"{console} template {device} {orient} keypad")
        S = screen_layouts(console, device, orient, where=f"{console} template {device} {orient}")
        name = name or L["name"]
        layouts[f"{device}-{orient}"] = skin_layout(L, TEMPLATE_SCALE[device], K, S)
    skin = {"format": 1, "console": console, "name": f"{name} template", "forms": ["phone", "tablet", "fold"], "layouts": layouts}
    (folder / "sources").mkdir()
    for layout_id, layout in layouts.items():
        (folder / layout_id).mkdir()
        for kp in [False] + ([True] if "keypad" in layout else []):
            if kp:
                (folder / layout_id / "keypad").mkdir()
            for file, size, _, e in images(layout, kp):
                placeholder(file, size, e, layout).save(folder / layout_id / file, optimize=True)
            svg = f"{layout_id}-keypad.svg" if kp else f"{layout_id}.svg"
            (folder / "sources" / svg).write_text(layout_svg(layout_id, layout, kp), encoding="utf-8")
        for page in layout.get("screenLayouts", {}):
            (folder / layout_id / page).mkdir()
            for file, size, _, e in images(layout, page=page):
                placeholder(file, size, e, page_layout(layout, page)).save(folder / layout_id / file, optimize=True)
            (folder / "sources" / f"{layout_id}-{page}.svg").write_text(layout_svg(layout_id, layout, page=page), encoding="utf-8")
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

    The engine renders the layout in three layers, side by side in one screenshot: the console's body (background.png:
    no bezel, no control, no label beside one), the bezel on a transparent page (frame.png, cut at its rect, the game's
    screens cut out of it) and every control alone in a cell of a grid, cut at its rect. That rect holds the whole
    control (its relief under it, a turned key's corners) and nothing of its neighbours, so moving it in the editor
    leaves nothing of it behind and takes nothing along. A label printed beside a control (START under its key) is in
    no image: the app draws it from the element's label. The D-pad's ball is the engine's circle at the ball's size, a stick's knob the
    engine's knob. A console with a keypad has its keypad page rendered the same way, into [folder]/keypad/, and one
    with two screens each screen-layout page into [folder]/<page>/."""
    L = template_layout(console, device, orient, variant)
    K = template_layout(console, device, orient, variant, keypad=True) if has_keypad(console) else None
    where = f"{console} {variant} {device} {orient}"
    check_fit(L, where)
    if K is not None:
        check_fit(K, f"{where} keypad")
    S = screen_layouts(console, device, orient, variant, where)
    # A variant changes colours only: the same frames, screens and knobs as the console's own skin.
    for kp in [False] + ([True] if K is not None else []):
        if variant != "console" and engine_layout(console, device, orient, variant=variant, keypad=kp) != engine_layout(console, device, orient, keypad=kp):
            raise SystemExit(f"{where}: the {'keypad page' if kp else 'layout'} differs from the console's own skin")
    for page in S:
        if variant != "console" and engine_layout(console, device, orient, page, variant) != engine_layout(console, device, orient, page):
            raise SystemExit(f"{where}: the {page} page differs from the console's own skin")
    scale = TEMPLATE_SCALE[device]
    layout = skin_layout(L, scale, K, S)
    folder.mkdir(parents=True)
    own_page(console, device, orient, variant, folder, profile, ball, L, layout, scale, False)
    if K is not None:
        (folder / "keypad").mkdir()
        own_page(console, device, orient, variant, folder, profile, ball, K, layout, scale, True)
    for page, P in S.items():
        (folder / page).mkdir()
        own_page(console, device, orient, variant, folder, profile, ball, P, layout, scale, False, page)
    return layout, L["name"]


def own_page(console, device, orient, variant, folder, profile, ball, L, layout, scale, keypad, page=None):
    """The images of one page of a layout (the controls, the [keypad] page or the screen-layout [page], from the
    engine's [L]) into [folder]."""
    from PIL import Image
    where = f"{console} {variant} {device} {orient}{' keypad' if keypad else ''}{f' {page}' if page else ''}"
    W, H = layout["size"]["width"], layout["size"]["height"]
    query = "&keypad=1" if keypad else f"&screens={page}" if page else ""
    bg = folder / ("keypad/background.png" if keypad else f"{page}/background.png" if page else "background.png")
    shot = folder.parent / f"{folder.name}{'-keypad' if keypad else f'-{page}' if page else ''}-layers.png"
    # The sprites' grid, in the engine's units: a cell holds the largest control and a unit of margin, the columns
    # fill a page's width, and the screenshot is as tall as the grid needs.
    shown = page_layout(layout, page) if page else layout["keypad"] if keypad else layout
    cell_w = math.ceil(max(e["width"] for e in shown["elements"]) / scale) + 1
    cell_h = math.ceil(max(e["height"] for e in shown["elements"]) / scale) + 1
    cols = max(1, L["W"] // cell_w)
    rows = math.ceil(len(shown["elements"]) / cols)
    high = max(L["H"], rows * cell_h)
    grid = f"&layers={','.join(LAYERS)}&cell={cell_w}x{cell_h}&cols={cols}&height={high}"

    def sprite(sheet, e):
        """[e]'s image: its cell of the grid, at its size."""
        n = shown["elements"].index(e)
        x, y = 2 * W + (n % cols) * cell_w * scale, (n // cols) * cell_h * scale
        return sheet.crop((x, y, x + e["width"], y + e["height"]))

    def wrong(sheet):
        """What is wrong with a screenshot, or None: headless Chrome now and then hands one over before every tile is
        painted, which shows as a hole in the body or a control with no art."""
        if sheet.size != (W * len(LAYERS), half_up(high * scale)):
            return f"rendered {sheet.size}, {len(LAYERS)} layers of {W} x {half_up(high * scale)} expected"
        if sheet.crop((0, 0, W, H)).getextrema()[3][0] != 255:
            return "the body does not cover the page"
        if not sheet.crop((W, 0, 2 * W, H)).getbbox():
            return "no bezel"
        return next((f"{e['id']} has no art" for e in shown["elements"] if not sprite(sheet, e).getbbox()), None)

    for attempt in range(SHOTS):
        screenshot(console, device, orient, L["W"] * len(LAYERS), high, scale, shot, profile, grid + query, variant)
        sheet = Image.open(shot).convert("RGBA")
        shot.unlink()
        problem = wrong(sheet)
        if problem is None:
            break
        print(f"{where}: {problem} (screenshot {attempt + 1} of {SHOTS})", file=sys.stderr)
    else:
        raise SystemExit(f"{where}: {problem}")
    art, bezel = (sheet.crop((W * i, 0, W * (i + 1), H)) for i in range(2))
    sticks = 0
    for file, (w, h), _, e in images(layout, keypad, page):
        base = file.rsplit("/", 1)[-1]
        if base == "background.png":
            continue
        if base == "frame.png":
            shown = page_layout(layout, page) if page else layout
            f = shown["frame"]
            img = bezel.crop((f["x"], f["y"], f["x"] + w, f["y"] + h))
            # The game's picture shows through the bezel.
            for sc in shown["screens"]:
                img.paste((0, 0, 0, 0), (sc["x"] - f["x"], sc["y"] - f["y"], sc["x"] - f["x"] + sc["width"], sc["y"] - f["y"] + sc["height"]))
        elif base.endswith("-ball.png"):
            img = ball.resize((w, h), Image.LANCZOS)
        elif base.endswith("-knob.png"):
            img = knob_image(console, device, orient, variant, folder, profile, L, scale, sticks, query).resize((w, h), Image.LANCZOS)
            sticks += 1
        else:
            img = sprite(sheet, e)
        img.save(folder / file, optimize=True)
    art.save(bg, optimize=True)


def knob_image(console, device, orient, variant, folder, profile, L, scale, i, query):
    """The engine's [i]th stick knob of the page [L], alone, cut to the knob itself (its edge to edge)."""
    from PIL import Image
    k = L["knobs"][i]
    px = round(k["size"] * scale)
    out = folder.parent / f"{folder.name}-knob{i}.png"
    screenshot(console, device, orient, px // scale + 1, px // scale + 1, scale, out, profile, f"&knob={i}{query}", variant)
    img = Image.open(out).convert("RGBA").crop((0, 0, px, px))
    out.unlink()
    return img.crop(img.getbbox())


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
    if variant not in PNG_VARIANTS:
        # A colour variant's images in lossy WebP, as its Delta/Manic package had (issue 720): the format reads
        # <name>.webp in place of <name>.png (docs/skin-format.md, Images).
        to_webp(folder, [f.relative_to(folder).as_posix() for f in sorted(folder.rglob("*.png"))])
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
    try:
        import PIL  # noqa: F401
    except ImportError:
        raise SystemExit("the packages and the templates need Pillow: pip install pillow")
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
                    build_own(c, profile, v["id"], v["label"])


main()
