#!/usr/bin/env python3
"""Render the relief cartridge frames into res/drawable-nodpi.

Each console's cartridge front is modelled as a height map in the frame's own
unit space (the same units as its CartFrame in data/Console.kt), following the
layout of the current frame: silhouette, notches, grooves, logo plate, arrows
and the label window, which keeps today's position and size. One shared
directional light from the top-left lights it, with shadows cast across the
height field, two-scale ambient occlusion, a satin specular and a gentle
top-to-bottom falloff. The label window stays transparent; the lip's shadow
over the label is baked in as a translucent dark overlay.

The light, the depth scale and the lip shadow are shared by every console:
geometry is scaled so the frame's longest side spans REF reference units (the
200 units of the approved Game Boy Color prototype), and every lighting
constant is expressed in those units. Only the shapes differ per console.

Each console is exported with its longest side at OUT_LONG pixels, as WebP with
alpha, neutral plus the 12 colors of tools/gen-cartridge-colors.py with the
same colorize math as tools/bake-frames.py; the old .png of each name is
removed (two files with one resource name do not build).

    tools/render-cartridge-frames.py                 # every redone console
    tools/render-cartridge-frames.py GBC FC          # a subset
    tools/render-cartridge-frames.py --preview DIR   # also a contact sheet

Needs Pillow and numpy, and the Noto fonts for embossed lettering
(fonts-noto-core on Debian/Ubuntu).
"""
import argparse
import importlib.util
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

TOOLS = Path(__file__).resolve().parent
OUT_DIR = TOOLS.parent / "src/main/res/drawable-nodpi"
FONT_DIR = Path("/usr/share/fonts/truetype/noto")
CONSOLE_KT = TOOLS.parent / "src/main/java/com/openemux/mobile/data/Console.kt"

REF = 200.0          # reference units on the frame's longest side
S = 12               # render pixels per reference unit (supersampled)
OUT_LONG = 800       # exported pixels on the frame's longest side
WEBP_QUALITY = 90

# Shared lighting, in reference units. Never per console.
LIGHT_ELEVATION = 32.0
SHADOW_STEP = 0.25
SHADOW_STEPS = 27
SHADOW_HARDNESS = 0.18
SHADOW_FLOOR = 0.25
AO_WIDE, AO_TIGHT = 1.6, 0.5
SPEC_POWER, SPEC_STRENGTH = 28, 0.22
AMBIENT, DIFFUSE = 0.50, 0.80
FALLOFF = (1.03, 0.95)
LIP_DEPTH = 0.9
LIP_ALPHA_GAIN, LIP_ALPHA_MAX = 1.25, 0.75
RIM_SOFTNESS = 0.85
GRAIN = 0.0035


def _load_tool(name):
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), TOOLS / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# ---------------------------------------------------------------- drawing
class Canvas:
    """Masks in a frame's own units, rasterised at S pixels per reference unit."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.k = REF / max(w, h) * S
        self.pw, self.ph = round(w * self.k), round(h * self.k)

    def p(self, v):
        return v * self.k

    def _new(self):
        im = Image.new("L", (self.pw, self.ph), 0)
        return im, ImageDraw.Draw(im)

    @staticmethod
    def _arr(im):
        return np.asarray(im, dtype=np.float32) / 255.0

    def rrect(self, *boxes):
        """Union of (x0, y0, x1, y1, radius) rounded rectangles."""
        im, d = self._new()
        for x0, y0, x1, y1, r in boxes:
            d.rounded_rectangle([self.p(x0), self.p(y0), self.p(x1), self.p(y1)],
                                radius=max(0, self.p(r)), fill=255)
        return self._arr(im)

    def poly(self, *polys):
        im, d = self._new()
        for pts in polys:
            d.polygon([(self.p(x), self.p(y)) for x, y in pts], fill=255)
        return self._arr(im)

    def outline_triangle(self, outer, inner):
        return np.clip(self.poly(outer) - self.poly(inner), 0, 1)

    def text(self, runs, cx, baseline):
        """Runs of (text, font file, size, gap after) centred on cx, on one baseline."""
        im, d = self._new()
        fonts = [(t, font(f, self.p(size)), self.p(gap)) for t, f, size, gap in runs]
        total = sum(d.textlength(t, font=fo) + g for t, fo, g in fonts) - fonts[-1][2]
        x = self.p(cx) - total / 2
        for t, fo, g in fonts:
            d.text((x, self.p(baseline)), t, font=fo, fill=255, anchor="ls")
            x += d.textlength(t, font=fo) + g
        return self._arr(im)


def font(name, size):
    path = FONT_DIR / name
    if not path.is_file():
        sys.exit(f"render-cartridge-frames: missing font {path} (install fonts-noto-core)")
    return ImageFont.truetype(str(path), round(size))


def _box_blur(a, r, axis):
    pad = [(0, 0)] * a.ndim
    pad[axis] = (r + 1, r)
    c = np.cumsum(np.pad(a, pad, mode="edge"), axis=axis)
    n = a.shape[axis]
    hi = np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis)
    lo = np.take(c, np.arange(0, n), axis=axis)
    return (hi - lo) / (2 * r + 1)


def blur(a, sigma):
    """Approximate gaussian (3 box passes), sigma in reference units."""
    s = sigma * S
    r = max(1, int(round(np.sqrt(12 * s * s / 3 + 1) / 2)))
    for _ in range(3):
        a = _box_blur(a, r, 0)
        a = _box_blur(a, r, 1)
    return a


def shift(a, dx, dy):
    """a moved by (dx, dy) pixels, zero filled (np.roll would wrap edges round)."""
    out = np.zeros_like(a)
    h, w = a.shape
    out[max(dy, 0):h + min(dy, 0), max(dx, 0):w + min(dx, 0)] = \
        a[max(-dy, 0):h + min(-dy, 0), max(-dx, 0):w + min(-dx, 0)]
    return out


# ---------------------------------------------------------------- geometry
class Frame:
    """What a console contributes: shapes only, lighting is shared."""

    def __init__(self, cv, body, window, layers, albedo, dark=None, dark_albedo=0.12):
        self.cv, self.body, self.window = cv, body, window
        self.layers = layers            # (mask, height, softness) in reference units
        self.albedo = np.array(albedo, np.float32)
        self.dark, self.dark_albedo = dark, dark_albedo


def game_boy(albedo):
    def build():
        cv = Canvas(177, 200)
        # Shell with the lock notch stepped in at the top right, as in the current frame.
        body = np.minimum(cv.rrect((3, 3, 172, 196.5, 2.2)), 1 - cv.rrect((157.5, -10, 190, 13, 0)))
        body = np.maximum(body, cv.rrect((158, 13, 172, 30, 1.6), (158, 20, 172, 40, 0)))
        grooves = cv.rrect(*[(x0, y, x1, y + 1.5, 0)
                             for y in (19.6, 25.6, 31.6, 37.6, 43.6)
                             for x0, x1 in ((2, 20.5), (154.5, 175))])
        lettering = cv.text([("Nintendo", "NotoSans-Black.ttf", 7.6, 2.2),
                             ("GAME BOY", "NotoSansDisplay-SemiCondensedBlackItalic.ttf", 12.6, 0.8),
                             ("TM", "NotoSans-Bold.ttf", 3.2, 0)], 87.5, 38.2)
        arrow = cv.outline_triangle([(77, 177.5), (100, 177.5), (88.5, 190.5)],
                                    [(80.6, 179.3), (96.4, 179.3), (88.5, 188.0)])
        layers = [
            (cv.rrect((0, 84, 6.2, 210, 0), (168.8, 84, 180, 210, 0)), -0.55, 0.25),  # side steps
            (cv.rrect((22.5, 13.5, 152.5, 50.5, 17)), -0.75, 0.45),                  # logo plate
            (lettering, 0.50, 0.10),
            (grooves, -0.55, 0.35),
            (cv.rrect((22.5, 56, 153, 173.5, 3.2)), -0.60, 0.40),                    # label recess
            (arrow, -0.55, 0.12),
        ]
        return Frame(cv, body, cv.rrect((25.8, 60, 150, 170.2, 2.6)), layers, albedo)
    return build


def nes():
    def build():
        cv = Canvas(400, 449)
        # Open at the top (the label wraps over it), narrower below the side cuts.
        body = cv.rrect((1.5, -10, 398.5, 365, 2.5), (20, -10, 378, 447.5, 2.5))
        ribs = cv.rrect(*[(64, y, 148, y + 2.4, 0.8) for y in (65.2 + 16.85 * k for k in range(20))])
        layers = [
            (cv.rrect((14, -10, 36, 6, 1), (364, -10, 386, 6, 1)), -0.70, 0.12),     # top notches
            (cv.rrect((62, -10, 150, 460, 0)), -0.20, 0.30),                         # rib channel
            (cv.rrect((64, -10, 148, 57, 3)), -1.10, 0.25),                          # top slot
            (ribs, -0.45, 0.14),
            (cv.rrect((64, 403, 148, 460, 3)), -0.45, 0.25),                         # bottom pad
            (cv.rrect((155.5, -10, 354.5, 312.5, 4)), -0.45, 0.35),                  # label recess
            (cv.poly([(181, 331), (219, 331), (200, 360)]), -0.40, 0.12),            # arrow
        ]
        window = cv.rrect((163.7, -10, 346.3, 304.5, 2.5))
        return Frame(cv, body, window, layers, (0.44, 0.44, 0.455))
    return build


def virtual_boy():
    def build():
        cv = Canvas(400, 383)
        body = cv.rrect((10, 8, 390, 375, 20))
        band = cv.rrect((0, 318, 400, 400, 0)) * body
        lettering = cv.text([("VIRTUAL BOY", "NotoSans-BlackItalic.ttf", 15, 0)], 200, 354)
        arrow = cv.outline_triangle([(182, 285), (218, 285), (200, 307)],
                                    [(187.5, 288.3), (212.5, 288.3), (200, 303.5)])
        layers = [
            (cv.rrect((44.5, 22, 50.5, 314, 3), (349.5, 22, 355.5, 314, 3)), -0.55, 0.30),  # side grooves
            (cv.rrect((55, 32.5, 343, 263.5, 5)), -0.55, 0.40),                       # label recess
            (arrow, -0.55, 0.12),
            (band, -0.35, 0.25),                                                      # dark lower band
            (lettering * band, 0.60, 0.10),
        ]
        window = cv.rrect((63.3, 40.7, 334, 255.3, 3))
        return Frame(cv, body, window, layers, (0.66, 0.65, 0.67), dark=band)
    return build


CONSOLES = {
    "GBC": game_boy((0.80, 0.80, 0.815)),
    "GB": game_boy((0.90, 0.90, 0.905)),
    "FC": nes(),
    "VB": virtual_boy(),
}


# ---------------------------------------------------------------- render
def height(frame):
    h = 2.4 * blur(frame.body, RIM_SOFTNESS)
    for mask, amount, softness in frame.layers:
        h += amount * blur(mask, softness)
    win = frame.window
    h = np.where(win > 0.5, h - LIP_DEPTH * blur(win, 0.12), h)
    grain = blur(np.random.default_rng(7).standard_normal(h.shape).astype(np.float32), 0.10)
    return h + GRAIN * grain / (grain.std() + 1e-6)


def shade(frame, h):
    win = frame.window
    gy, gx = np.gradient(h, 1.0 / S)
    n = np.dstack([-gx, -gy, np.ones_like(h)])
    n /= np.linalg.norm(n, axis=2, keepdims=True)

    el = np.deg2rad(LIGHT_ELEVATION)
    light = np.array([-np.cos(el) * 0.70710678, -np.cos(el) * 0.70710678, np.sin(el)])
    ndl = np.clip((n * light).sum(2), 0, 1)

    # Cast shadows: march toward the light across the height field.
    slope = np.tan(el)
    occ = np.zeros_like(h)
    for k in range(1, SHADOW_STEPS + 1):
        t = k * SHADOW_STEP
        d = int(round(0.70710678 * t * S))
        occ = np.maximum(occ, shift(h, d, d) - h - t * slope)
    shadow = 1 - np.clip(occ / SHADOW_HARDNESS, 0, 1)
    shadow = SHADOW_FLOOR + (1 - SHADOW_FLOOR) * blur(shadow, 0.15)

    ao = 1 - np.clip((blur(h, AO_WIDE) - h) * 0.55, 0, 0.45)
    ao *= 1 - np.clip((blur(h, AO_TIGHT) - h) * 0.6, 0, 0.25)

    half = (light + [0, 0, 1]) / np.linalg.norm(light + [0, 0, 1])
    spec = np.clip((n * half).sum(2), 0, 1) ** SPEC_POWER * SPEC_STRENGTH * shadow

    albedo = np.broadcast_to(frame.albedo, h.shape + (3,))
    if frame.dark is not None:
        dark = blur(frame.dark, 0.2)[..., None]
        albedo = albedo * (1 - dark) + frame.dark_albedo * dark
    lit = AMBIENT * ao + DIFFUSE * ndl * shadow
    rgb = albedo * lit[..., None] + spec[..., None]
    rgb = np.clip(rgb * np.linspace(*FALLOFF, h.shape[0])[:, None, None], 0, 1)

    alpha = np.clip(frame.body, 0, 1)
    lip = np.clip(np.clip(1 - shadow * ao, 0, 1) * LIP_ALPHA_GAIN, 0, LIP_ALPHA_MAX)
    inside = win > 0.5
    alpha = np.where(inside, lip * win, alpha)
    rgb = np.where(inside[..., None], 0.05, rgb)
    return rgb, alpha, inside


def render(name):
    frame = CONSOLES[name]()
    rgb, alpha, inside = shade(frame, height(frame))
    img = Image.fromarray((np.dstack([rgb, alpha]) * 255 + 0.5).astype(np.uint8), "RGBA")
    cv = frame.cv
    size = (round(cv.w * OUT_LONG / max(cv.w, cv.h)), round(cv.h * OUT_LONG / max(cv.w, cv.h)))
    lip = Image.fromarray((inside * 255).astype(np.uint8)).resize(size, Image.BILINEAR)
    return img.resize(size, Image.LANCZOS), np.asarray(lip) > 127


# ---------------------------------------------------------------- colors
def ramp(img, colors):
    """The colorize filter's luminance ramp, measured as gen-cartridge-colors.py does."""
    a = np.asarray(img, dtype=np.int32)
    opaque = a[..., 3] >= 200
    lum = (a[..., 0] * 299 + a[..., 1] * 587 + a[..., 2] * 114) // 1000
    p5, p50, p95 = (np.percentile(lum[opaque], q, method="inverted_cdf") / 255.0 for q in (5, 50, 95))
    lo, hi = colors.SLOPE_LIMITS
    slope = min(max(colors.TARGET_SPREAD / max(p95 - p5, 1e-4), lo), hi)
    return slope, colors.TARGET_MID - slope * p50


def colorize(img, inside, base_hex, ramp_):
    """bake-frames.py's colorize (desaturate, linear ramp, multiply the flood, keep alpha).

    The lip's shadow inside the label window stays a neutral shadow."""
    slope, intercept = ramp_
    a = np.asarray(img, dtype=np.float32) / 255.0
    lum = 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]
    p = np.clip(slope * lum + intercept, 0, 1)[..., None]
    k = a[..., 3:4]
    flood = np.array([int(base_hex[i:i + 2], 16) / 255 for i in (1, 3, 5)], np.float32)
    rgb = flood * (1 - k + k * p)
    rgb = np.where(inside[..., None], a[..., :3], rgb)
    out = np.dstack([rgb, a[..., 3]])
    out[a[..., 3] == 0] = 0
    return Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA")


def save(img, name):
    target = OUT_DIR / f"{name}.webp"
    img.save(target, "WEBP", quality=WEBP_QUALITY, method=6, alpha_quality=100, exact=False)
    (OUT_DIR / f"{name}.png").unlink(missing_ok=True)
    return target


def preview(sheet, path):
    """A sample label under each neutral frame, the way the app composes them."""
    rects = label_rects()
    label = _sample_label()
    tiles = [label_composite(rects[console], img, label) for console, img in sheet]
    hgt = max(t.height for t in tiles) + 40
    out = Image.new("RGB", (sum(t.width + 20 for t in tiles) + 20, hgt), (36, 36, 40))
    x = 20
    for t in tiles:
        out.paste(t, (x, 20), t)
        x += t.width + 20
    out.save(path)


def label_rects():
    """Each console's CartFrame in data/Console.kt: frame w, h, then label x, y, w, h."""
    num = r"([\d.]+)f"
    pattern = re.compile(r'frame\("cart_(\w+)",\s*' + r",\s*".join([num] * 6) + r"\)")
    return {name.upper(): tuple(float(v) for v in vals) for name, *vals in pattern.findall(CONSOLE_KT.read_text())}


def _sample_label():
    w, h = 400, 400
    y, x = np.mgrid[0:h, 0:w] / np.array([h, w], np.float32)[:, None, None]
    rgb = np.dstack([0.2 + 0.7 * x, 0.3 + 0.5 * y, 0.8 - 0.5 * x * y])
    im = Image.fromarray((rgb * 255).astype(np.uint8), "RGB").convert("RGBA")
    ImageDraw.Draw(im).rectangle([0, 0, w - 1, h - 1], outline=(255, 255, 255, 255), width=6)
    return im


def label_composite(rect, frame_img, label):
    fw, fh, lx, ly, lw, lh = rect
    sx, sy = frame_img.width / fw, frame_img.height / fh
    canvas = Image.new("RGBA", frame_img.size, (0, 0, 0, 0))
    canvas.paste(label.resize((round(lw * sx), round(lh * sy)), Image.LANCZOS), (round(lx * sx), round(ly * sy)))
    canvas.alpha_composite(frame_img)
    return canvas


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("consoles", nargs="*", metavar="CONSOLE", help=f"subset of {' '.join(CONSOLES)}")
    ap.add_argument("--preview", metavar="DIR", help="also write a contact sheet with sample labels")
    args = ap.parse_args(argv)
    unknown = [c for c in args.consoles if c.upper() not in CONSOLES]
    if unknown:
        ap.error(f"unknown console(s): {' '.join(unknown)}; known: {' '.join(CONSOLES)}")
    colors = _load_tool("gen-cartridge-colors")
    sheet = []
    for console in [c.upper() for c in args.consoles] or list(CONSOLES):
        img, inside = render(console)
        r = ramp(img, colors)
        base = f"cart_{console.lower()}"
        save(img, base)
        for color_id, _name, base_hex in colors.PALETTE:
            save(colorize(img, inside, base_hex, r), f"{base}_{color_id}")
        print(f"{console:4} {img.size[0]}x{img.size[1]}  ramp slope={r[0]:.3f} intercept={r[1]:+.3f}")
        sheet.append((console, img))
    if args.preview:
        out = Path(args.preview)
        out.mkdir(parents=True, exist_ok=True)
        preview(sheet, out / "cartridge-frames.png")
        print("preview:", out / "cartridge-frames.png")


if __name__ == "__main__":
    main()
