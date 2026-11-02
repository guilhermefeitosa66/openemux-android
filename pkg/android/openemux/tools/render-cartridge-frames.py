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
(fonts-noto-core and fonts-noto-extra on Debian/Ubuntu).
"""
import argparse
import importlib.util
import re
import sys
from concurrent.futures import ProcessPoolExecutor
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
METAL_SPEC = 2.5     # extra specular on metal contacts
METAL_EDGE = 0.08    # softness of the metal's edge


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

    def rpoly(self, pts, radii):
        """A polygon with each vertex filleted by its radius (frame units); 0 keeps it sharp."""
        return self.poly(fillet(pts, radii))

    def line(self, width, *paths):
        """Union of strokes of the given width (frame units) through each path's points, round joints and ends."""
        im, d = self._new()
        r = self.p(width) / 2
        for pts in paths:
            d.line([(self.p(x), self.p(y)) for x, y in pts], fill=255, width=max(1, round(self.p(width))), joint="curve")
            for x, y in (pts[0], pts[-1]):
                d.ellipse([self.p(x) - r, self.p(y) - r, self.p(x) + r, self.p(y) + r], fill=255)
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


def fillet(pts, radii):
    """The polygon's points with each corner replaced by a circular arc of the given radius."""
    pts = np.asarray(pts, np.float64)
    radii = np.broadcast_to(np.asarray(radii, np.float64), (len(pts),))
    out = []
    for i, (p, r) in enumerate(zip(pts, radii)):
        a, b = pts[i - 1] - p, pts[(i + 1) % len(pts)] - p
        la, lb = np.hypot(*a), np.hypot(*b)
        half = np.arccos(np.clip(a @ b / (la * lb), -1, 1)) / 2
        if r <= 0 or half > np.deg2rad(89.5):
            out.append(tuple(p))
            continue
        d = min(r / np.tan(half), la / 2, lb / 2)
        r = d * np.tan(half)
        ua, ub = a / la, b / lb
        centre = p + (ua + ub) / np.hypot(*(ua + ub)) * (r / np.sin(half))
        t0, t1 = (np.arctan2(*(q - centre)[::-1]) for q in (p + ua * d, p + ub * d))
        sweep = (t1 - t0 + np.pi) % (2 * np.pi) - np.pi
        out += [tuple(centre + r * np.array([np.cos(t), np.sin(t)])) for t in t0 + sweep * np.linspace(0, 1, 12)]
    return out


def font(name, size):
    path = FONT_DIR / name
    if not path.is_file():
        sys.exit(f"render-cartridge-frames: missing font {path} (install fonts-noto-core and fonts-noto-extra)")
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

    def __init__(self, cv, body, window, layers, albedo, dark=None, dark_albedo=0.12, metal=None):
        self.cv, self.body, self.window = cv, body, window
        self.layers = layers            # (mask, height, softness) in reference units
        self.albedo = np.array(albedo, np.float32)
        self.dark, self.dark_albedo = dark, dark_albedo
        # Metal contacts: (mask, rgb albedo). They keep their own color in every variant.
        self.metal = metal or []

    def metal_mask(self):
        if not self.metal:
            return None
        # Blurred as shade() blends the metal albedo, so colorize keeps the whole blended edge.
        return np.clip(sum(blur(mask, METAL_EDGE) for mask, _rgb in self.metal), 0, 1)


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


SEGA_BLACK = (0.30, 0.30, 0.31)   # the Sega shells are black today


def master_system():
    def build():
        cv = Canvas(295, 200)
        grooves = ((53.8, 55.6), (59.0, 60.8), (64.8, 66.6))
        # The three grooves below the label strip run across the front and notch both sides.
        notches = cv.rrect(*[(x0, y0, x1, y1, 0) for y0, y1 in grooves for x0, x1 in ((-10, 5), (288, 305))])
        body = np.minimum(cv.rrect((3, 6, 290, 190, 3.5)), 1 - notches)
        layers = [
            (cv.rrect((5.8, 7.2, 287.2, 50.2, 3)), -0.40, 0.30),                    # label recess
            (cv.rrect(*[(-10, y0, 305, y1, 0.6) for y0, y1 in grooves]), -0.55, 0.16),
        ]
        window = cv.rrect((10.7, 8.7, 283.4, 46.6, 2.5))
        return Frame(cv, body, window, layers, SEGA_BLACK)
    return build


def game_gear():
    def build():
        cv = Canvas(400, 403)
        body = cv.rrect((9, 8, 392, 397, 6))
        # Details share the shell's axis (x 200.5); the vents sit clear of the rounded rim.
        vents = cv.rrect(*[(x - 1.6, 12.4, x + 1.6, 15.6, 1.6) for x in (200.5 + 11 * (k - 4.5) for k in range(10))])
        # The arch over the window: thick across the top, narrow down the sides.
        arch = np.clip(cv.rrect((22, 40, 379, 165, 36)) - cv.rrect((36, 72, 365, 220, 22)), 0, 1)
        sega = cv.text([(ch, "NotoSans-Black.ttf", 24, 1.6) for ch in "SEGA"], 200.5, 362)
        layers = [
            (vents, -0.50, 0.10),
            (arch, 0.55, 0.35),
            (cv.rrect((108.5, 49.4, 292.5, 51.2, 0.9), (108.5, 61, 292.5, 62.8, 0.9)), -0.50, 0.12),  # arch grooves
            (cv.rrect((40.5, 76.5, 362.8, 312.8, 4)), -0.50, 0.35),                 # label recess
            (cv.rrect((126.5, 333, 274.5, 374, 20.5)), -0.55, 0.30),                    # SEGA plate
            (sega, 0.60, 0.10),
        ]
        window = cv.rrect((47.3, 83.3, 356, 306, 2))
        return Frame(cv, body, window, layers, SEGA_BLACK)
    return build


def sg1000():
    def build():
        cv = Canvas(400, 461)
        # Ten grip tabs stand out of each side, as in the current frame.
        tabs = cv.rrect(*[(x0, y, x1, y + 12, 1.5) for y in (35 + 17.4 * k for k in range(10))
                          for x0, x1 in ((3, 11), (388, 396))])
        body = np.maximum(cv.rrect((5, 3, 394, 455, 4)), tabs)
        layers = [
            (tabs * cv.rrect((-10, -10, 9, 470, 0), (390, -10, 410, 470, 0)), 0.35, 0.15),
            (cv.rrect((35, 4.5, 367, 271.5, 3)), -0.45, 0.35),                      # label recess
        ]
        window = cv.rrect((40.7, 8.7, 361.3, 266, 2))
        return Frame(cv, body, window, layers, SEGA_BLACK)
    return build


def sega_32x():
    def build():
        cv = Canvas(400, 263)
        body = cv.rrect((8, 5, 387, 256.5, 19))
        # Raised bezel round the window: slanted sides and a lower edge that dips in the middle.
        curve = [(62 + (333 - 62) * t, 195 + 19 * 4 * t * (1 - t)) for t in np.linspace(0, 1, 33)]
        bezel = cv.poly([(40, -10), (355, -10), *curve[::-1]])
        sweeps = cv.line(1.6, [(14, 40), (24, 120), (34, 190), (56, 238)],
                         [(381, 40), (371, 120), (361, 190), (339, 238)])
        slots = cv.rrect(*[(x - 2.2, 229, x + 2.2, 249, 2.2) for x in (197.5 + 37.5 * (k - 2.5) for k in range(6))])
        layers = [
            (bezel, 0.45, 0.40),
            (sweeps, -0.45, 0.14),
            (slots, -0.70, 0.14),
            (cv.rrect((66, 30.5, 328.7, 189.5, 4)), -0.50, 0.35),                   # label recess
        ]
        window = cv.rrect((72, 36.7, 322.7, 183.3, 2))
        return Frame(cv, body, window, layers, SEGA_BLACK)
    return build


DARK_PLASTIC = SEGA_BLACK        # the Atari and the other black shells use the Sega frames' grey


def atari_vcs():
    """The 2600 and the 7800 share one cartridge front today: a bevelled rim round a tall label."""
    def build():
        cv = Canvas(400, 476)
        body = cv.rrect((12, 12, 388, 465, 6))
        layers = [
            (cv.rrect((24, 24, 376, 453, 5)), -0.30, 0.70),                        # bevel down from the rim
            (cv.rrect((35, 35.5, 365, 446, 3.5)), -0.45, 0.30),                    # label recess
        ]
        window = cv.rrect((42, 42.6, 358, 438.6, 2.5))
        return Frame(cv, body, window, layers, DARK_PLASTIC)
    return build


def _fuji(cv, cx, top, bottom):
    """The Atari symbol: a straight centre bar and two prongs that flare outwards to the base."""
    t = np.linspace(0, 1, 25)
    y = top + (bottom - top) * t
    prongs = []
    for side in (-1, 1):
        mid = cx + side * (9 + 15 * t ** 2.2)     # prong centreline, bending out near the base
        half = 3.4 + 2.6 * t
        prongs.append([*zip(mid - half, y), *zip((mid + half)[::-1], y[::-1])])
    bar = (cx - 3.6, top, cx + 3.6, bottom, 0)
    return np.maximum(cv.poly(*prongs), cv.rrect(bar))


def atari_5200():
    def build():
        cv = Canvas(400, 368)
        # Sixteen grip teeth stand out of each side, between chamfered full-width blocks at the
        # top and the bottom, as in the current frame.
        teeth = cv.rrect(*[(x0, y, x1, y + 11, 1) for y in (37 + 18.75 * k for k in range(16))
                           for x0, x1 in ((9, 15), (384, 390))])
        blocks = cv.poly([(20, 9), (379, 9), (390, 18), (390, 29), (9, 29), (9, 18)],
                         [(9, 337), (390, 337), (390, 351), (382, 358), (17, 358), (9, 351)])
        body = np.maximum.reduce([cv.rrect((14, 9, 385, 358, 2)), blocks, teeth])
        sides = cv.rrect((-10, 31, 14.5, 335, 0), (384.5, 31, 410, 335, 0))
        logo = _fuji(cv, 200, 288, 327)
        lettering = cv.text([("ATARI", "NotoSans-Black.ttf", 17, 0)], 200, 344.5)
        layers = [
            (sides * teeth, -0.35, 0.14),                                           # teeth step down
            (cv.rrect((45, 31, 354, 286, 4)), -0.50, 0.35),                         # label recess
            (logo, 0.55, 0.10),
            (lettering, 0.55, 0.10),
        ]
        window = cv.rrect((56.7, 44.7, 340, 272, 2))
        return Frame(cv, body, window, layers, DARK_PLASTIC)
    return build


def lynx():
    def build():
        cv = Canvas(400, 417)
        body = cv.rrect((6, 5, 395, 411, 17))
        # The contact slot along the bottom: 32 raised metal contacts, lighter than the shell
        # (a second albedo zone, left out of the colour ramp like the Virtual Boy's band).
        contacts = cv.rrect(*[(x, 358, x + 9.6, 392, 0.8) for x in (18.5 + 11.44 * k for k in range(32))])
        layers = [
            (cv.rrect((14, 10, 387, 20, 5)), 0.35, 0.30),                           # grip ridge
            (cv.rrect((9, 87.5, 392, 353, 2.5)), -0.45, 0.25),                      # label recess
            (cv.rrect((15, 355, 386, 395, 2)), -0.60, 0.20),                        # contact slot
            (contacts, 0.30, 0.10),
        ]
        window = cv.rrect((12.4, 92.3, 388.3, 348.7, 2))
        return Frame(cv, body, window, layers, DARK_PLASTIC, dark=contacts, dark_albedo=0.62)
    return build


LIGHT_PLASTIC = (0.88, 0.88, 0.885)
GOLD = (0.86, 0.68, 0.30)
COPPER = (0.80, 0.47, 0.26)


def pc_engine():
    def build():
        cv = Canvas(400, 506)
        # White HuCard: black top panel with the gold contacts, label below, ribbed tab at the bottom.
        body = cv.rrect((42.5, 8, 351.5, 496.5, 9))
        panel = cv.rrect((50, 18.5, 344, 150, 1.5), (50, 18.5, 344, 163, 12))
        pins = cv.rrect(*[(x - 1.7, 22.5, x + 1.7, 42, 1.7) for x in (59 + 7.46 * k for k in range(38))])
        tab = np.maximum(cv.poly([(154, 478), (197, 465.5), (240, 478)]), cv.rrect((154, 477, 240, 493.5, 0.6)))
        ribs = cv.rrect(*[(155.5, y, 238.5, y + 1.1, 0.5) for y in (479.5, 483.2, 486.9, 490.6)])
        layers = [
            (panel, -0.30, 0.25),
            (pins, 0.25, 0.08),
            (cv.rrect((50, 172.5, 349.5, 465.5, 3)), -0.35, 0.30),                   # label recess
            (tab, 0.30, 0.15),
            (ribs, -0.30, 0.08),
        ]
        window = cv.rrect((54.3, 177.5, 345.7, 460.4, 2))
        return Frame(cv, body, window, layers, LIGHT_PLASTIC, dark=panel, metal=[(pins, GOLD)])
    return build


def colecovision():
    def build():
        cv = Canvas(400, 461)
        # Seven ridged grips stand out of each upper side; the label sits in a deep well.
        notches = cv.rrect(*[(x0, y, x1, y + 3, 1.2) for y in (20.5 + 13.5 * k for k in range(6))
                             for x0, x1 in ((-10, 12.5), (383.5, 410))])
        grips = np.minimum(cv.rrect((8.5, 10, 30, 108, 3), (366, 10, 387.5, 108, 3)), 1 - notches)
        body = np.maximum(cv.rrect((15, 3, 381, 455.5, 4)), grips)
        layers = [
            (grips * cv.rrect((-10, -10, 15, 470, 0), (381, -10, 410, 470, 0)), 0.35, 0.18),
            (cv.rrect((15, 3, 381, 12.5, 3)), 0.40, 0.25),                          # top cap bar
            (cv.rrect((31, 14, 365, 357.5, 3)), -0.55, 0.30),                       # window well
            (cv.rrect((43, 18, 352.7, 335.3, 3)), -0.30, 0.25),                     # label recess
        ]
        window = cv.rrect((49.3, 24, 346.7, 329.3, 2))
        return Frame(cv, body, window, layers, DARK_PLASTIC)
    return build


def intellivision():
    def build():
        cv = Canvas(400, 494)
        slots = [276 + 26 * k for k in range(4)]
        # Four grip slots on each side run in from the edge and notch it.
        body = np.minimum(cv.rrect((4, 4, 396, 490, 4)),
                          1 - cv.rrect(*[(x0, y + 2, x1, y + 15, 0) for y in slots for x0, x1 in ((-10, 6), (394, 410))]))
        bold = "NotoSans-Black.ttf"
        layers = [
            (cv.text([("INSERT TO", bold, 23, 0)], 198, 238.5), -0.45, 0.10),
            (cv.rrect((15, 249.5, 384, 252.5, 1.5)), -0.50, 0.12),                  # the line
            (cv.text([("THIS LINE", bold, 23, 0)], 200, 290.5), -0.45, 0.10),
            (cv.rrect(*[(-10, y, 124, y + 17, 8.5) for y in slots]), -0.50, 0.20),
            (cv.rrect(*[(273, y, 410, y + 17, 8.5) for y in slots]), -0.50, 0.20),
            (cv.rrect((4, 371, 396, 490, 4)), 0.30, 0.25),                           # bottom band
            (cv.rrect((17, 384, 383, 483, 3)), -0.25, 0.60),                         # bevelled frame
            (cv.rrect((26.5, 389.5, 373, 473, 2)), -0.30, 0.25),                     # label recess
        ]
        window = cv.rrect((32, 394.7, 367.3, 467.3, 2))
        return Frame(cv, body, window, layers, DARK_PLASTIC)
    return build


def neo_geo_pocket():
    def build():
        cv = Canvas(400, 400)
        body = cv.rrect((3.5, 19.5, 395.5, 370.5, 6))
        ribs = cv.rrect(*[(41, y, 358, y + 2.2, 1) for y in (44 + 6.3 * k for k in range(8))])
        layers = [
            (cv.rrect((3.5, 19.5, 395.5, 78, 6)), 0.35, 0.50),                      # top cap
            (cv.rrect((39, 40, 360, 95, 3)), -0.60, 0.25),                          # ribbed panel
            (ribs, 0.35, 0.12),
            (cv.rrect((38, 98.5, 362.5, 279.5, 4)), -0.45, 0.35),                   # label recess
            (cv.rrect((-10, 297.3, 410, 298.7, 0)), -0.35, 0.10),                   # seam
            (cv.rrect((37, 315, 144, 353, 2), (256, 315, 363, 353, 2)), -0.40, 0.20),  # plates
            (cv.text([("SNK", "NotoSans-BlackItalic.ttf", 31, 0)], 90.5, 344.5), 0.45, 0.10),
            (cv.text([("NEOGEO POCKET", "NotoSans-Black.ttf", 10.5, 0)], 309.5, 345), 0.40, 0.08),
            (cv.poly([(171, 316), (229, 316), (200, 352)]), -0.50, 0.12),            # arrow
        ]
        window = cv.rrect((44.7, 105.3, 356, 272.7, 2))
        return Frame(cv, body, window, layers, DARK_PLASTIC)
    return build


def odyssey2():
    def build():
        cv = Canvas(400, 603)
        # A handle with a see-through hole on top, the label below, a slot and a pad at the bottom.
        shell = np.maximum(cv.rrect((9.5, 6, 390.5, 300, 16)), cv.rrect((9.5, 200, 390.5, 597, 4)))
        body = np.minimum(shell, 1 - cv.rrect((69, 51, 336, 145, 10)))
        layers = [
            (cv.rrect((9.5, 6, 390.5, 140, 16)), 0.45, 0.50),                       # handle
            (cv.rrect((139.5, 8, 141, 50, 0), (261, 8, 262.5, 50, 0)), -0.50, 0.08),  # seams
            (cv.rrect((31.5, 165.5, 365.5, 504, 4)), -0.45, 0.35),                  # label recess
            (cv.rrect((117, 535, 162, 610, 0)), -0.70, 0.15),                       # slot
            (cv.rrect((162, 535, 282, 610, 0)), -0.20, 0.25),                       # pad
        ]
        window = cv.rrect((36.5, 170.1, 360.1, 498, 2))
        return Frame(cv, body, window, layers, DARK_PLASTIC)
    return build


def vectrex():
    def build():
        cv = Canvas(400, 391)
        body = np.maximum(cv.rrect((11, 9, 390, 160, 2)), cv.rrect((7.5, 150, 392.5, 384, 10)))
        arrow = cv.outline_triangle(
            [(200, 37), (232, 57), (221, 57), (221, 70), (179, 70), (179, 57), (168, 57)],
            [(200, 41), (222.5, 54.5), (218, 54.5), (218, 67), (182, 67), (182, 54.5), (177.5, 54.5)])
        layers = [
            (cv.rrect((12, 9, 22, 150, 0), (378, 9, 389, 150, 0)), -0.80, 0.15),    # side rails
            (cv.rrect((22, 9, 378, 148, 2)), 0.25, 0.25),                           # insert panel
            (arrow, -0.50, 0.10),
            (cv.text([("INSERT TO THIS LINE", "NotoSans-CondensedBlack.ttf", 22, 0)], 200, 137), -0.45, 0.10),
            (cv.rrect((7.5, 150, 392.5, 163, 2)), 0.20, 0.20),                      # lower ledge
            (cv.rrect(*[(6, y, 10.5, y + 1.6, 0.5) for y in (272 + 4 * k for k in range(22))]), -0.30, 0.08),
            (cv.rrect((21, 171.5, 379, 368, 3)), -0.45, 0.35),                      # label recess
        ]
        window = cv.rrect((28, 178.7, 372, 360.7, 2))
        return Frame(cv, body, window, layers, DARK_PLASTIC)
    return build


def wonderswan():
    def build():
        cv = Canvas(400, 241)
        # The shell, then the narrower connector with side notches, holes and copper fingers.
        connector = np.minimum(cv.rrect((15, 180, 385, 238.5, 3)),
                               1 - cv.rrect((-10, 207, 23, 226, 3), (377, 207, 410, 226, 3)))
        body = np.maximum(cv.rrect((3, 2, 398, 186, 4)), connector)
        holes = [33, 46, 53, 59, 66, 76, 83, 90, 213, 226, 234, 251, 260]
        holes += [127 + 6.15 * k for k in range(13)] + [310 + 6 * k for k in range(10)]
        fingers = cv.rrect(*[(x - 1.3, 195, x + 1.3, 234, 0.6) for x in (33 + 4.2 * k for k in range(81))
                             if abs(x - 200) > 2.5])
        layers = [
            (cv.rrect((116, 8, 288, 27, 9.5)), -0.35, 0.20),                       # logo plate
            (cv.text([("WonderSwan", "NotoSans-BlackItalic.ttf", 16, 0)], 202, 23.5), 0.45, 0.08),
            (cv.rrect((12, 9, 14, 29, 0.6), (55, 9, 57, 29, 0.6), (296, 9, 298, 29, 0.6)), 0.30, 0.08),
            (cv.text([("U", "NotoSans-Bold.ttf", 12, 0)], 341, 28), 0.30, 0.08),
            (cv.rrect((11, 29, 389, 171, 3)), -0.40, 0.30),                         # label recess
            (cv.rrect((14, 173, 386, 185, 1.5)), -0.25, 0.20),                      # ledge
            (connector, -0.20, 0.25),
            (cv.rrect(*[(x - 1.8, 186.3, x + 1.8, 189.9, 1.8) for x in holes]), -0.60, 0.08),
            (cv.rrect((30, 192, 370, 236.5, 1.5)), -0.60, 0.15),                    # contact bay
            (fingers, 0.20, 0.06),
        ]
        window = cv.rrect((16.7, 34, 383.3, 166, 2))
        return Frame(cv, body, window, layers, LIGHT_PLASTIC, metal=[(fingers, COPPER)])
    return build


def nintendo_64():
    def build():
        cv = Canvas(311, 200)
        # The arched top, both shoulders split off the centre panel by a groove, and each
        # shoulder's lower corner stepped in under a curved edge, as in the current frame.
        arch = [(x, 6 + 30 * ((x - 155.25) / 149.25) ** 2) for x in np.linspace(6, 304.5, 41)]
        body = cv.rpoly([(6, 195.5), *arch, (304.5, 195.5)], [2.5, 9] + [0] * 39 + [9, 2.5])
        win_arch = [(x, 25 + 5 * ((x - 155) / 66) ** 2) for x in np.linspace(89, 221, 25)]
        window = cv.rpoly([(89, 176.4), *win_arch, (221, 176.4)], [3] + [3] + [0] * 23 + [3] + [3])
        rec_arch = [(x, 21.5 + 5.6 * ((x - 155) / 70) ** 2) for x in np.linspace(85, 225, 25)]
        recess = cv.rpoly([(85, 180), *rec_arch, (225, 180)], [4] + [4] + [0] * 23 + [4] + [4])
        steps = [[(-10, 137.5), (21, 137.5), (27.5, 140.5), (30.5, 147), (30.5, 210)],
                 [(321, 137.5), (290, 137.5), (283.5, 140.5), (280.5, 147), (280.5, 210)]]
        corners = cv.poly([(-10, 137.5), (21, 137.5), (27.5, 140.5), (30.5, 147), (30.5, 210), (-10, 210)],
                          [(321, 137.5), (290, 137.5), (283.5, 140.5), (280.5, 147), (280.5, 210), (321, 210)])
        layers = [
            (cv.rrect((-10, -10, 31, 137.5, 0), (280, -10, 321, 137.5, 0)), 0.40, 1.60),   # rounded shoulder edges
            (corners, -0.25, 0.30),                                                  # stepped lower corners
            (cv.line(1.4, *steps), -0.45, 0.12),
            (cv.rrect((68.3, -10, 70.5, 210, 0.6), (240.5, -10, 242.7, 210, 0.6)), -0.55, 0.14),  # grooves
            (recess, -0.45, 0.35),                                                   # label recess
            (cv.rrect((-10, 184, 321, 210, 0)), -0.15, 0.80),                        # bottom edge rolls off
        ]
        return Frame(cv, body, window, layers, (0.52, 0.52, 0.53))
    return build


def game_boy_advance():
    def build():
        cv = Canvas(400, 230)
        # Wide top with rounded corners, a small shoulder step, then the narrower body.
        body = cv.rpoly([(0, 0), (400, 0), (400, 44), (389, 50), (389, 230), (11, 230), (11, 50), (0, 44)],
                        [12, 12, 3, 2, 3, 3, 2, 3])
        # The recessed logo plate under an arch across the top, lettering embossed in it.
        plate_top = [(x, 13 + 22 * ((x - 200) / 145) ** 4) for x in np.linspace(55, 345, 31)]
        plate = cv.rpoly([(48, 45), (55, 35), *plate_top[1:-1], (345, 35), (352, 45)], [2, 3] + [0] * 29 + [3, 2])
        lettering = cv.text([("GAME BOY", "NotoSansDisplay-SemiCondensedBlackItalic.ttf", 16.5, 3.5),
                             ("ADVANCE", "NotoSans-Black.ttf", 14, 0)], 200, 43.2)
        layers = [
            (cv.rpoly([(10, 9), (390, 9), (390, 230), (10, 230)], 4) * cv.rrect((19, 9, 381, 240, 3)) +
             cv.rrect((10, 9, 390, 47, 4)) * (1 - cv.rrect((-10, 47, 410, 240, 0))), -0.30, 0.55),  # bevel down from the rim
            (plate, -0.45, 0.30),
            (lettering, 0.45, 0.08),
            (cv.rrect((49, 47.5, 351, 206.5, 4)), -0.45, 0.35),                      # label recess
            (cv.poly([(163, 211), (237, 211), (200, 225.5)]), -0.50, 0.12),          # arrow
        ]
        window = cv.rrect((53.4, 51.8, 347.2, 202.9, 3))
        return Frame(cv, body, window, layers, DARK_PLASTIC)
    return build


def mega_drive():
    def build():
        cv = Canvas(295, 200)
        # Two big rounded grip columns at the sides; the label runs up to the top edge.
        body = cv.rpoly([(3, 3), (291.5, 3), (291.5, 196.5), (3, 196.5)], [11, 11, 5, 5])
        layers = [
            (cv.rrect((5, 3, 39, 196.5, 9), (255.5, 3, 289.5, 196.5, 9)), 1.80, 2.60),  # grip columns
            (cv.rrect((40.5, -10, 42.5, 210, 0.8), (252, -10, 254, 210, 0.8)), -0.40, 0.14),  # column seams
            (cv.rrect((44, -10, 250.5, 173.5, 3)), -0.45, 0.35),                    # label recess
            (cv.rrect((41, 186.5, 253.5, 210, 0)), -0.15, 0.60),                    # bottom edge rolls off
        ]
        window = cv.rrect((47.4, 4.6, 247.4, 170.4, 2))
        return Frame(cv, body, window, layers, DARK_PLASTIC)
    return build


CONSOLES = {
    "GBC": game_boy((0.80, 0.80, 0.815)),
    "GB": game_boy((0.90, 0.90, 0.905)),
    "FC": nes(),
    "VB": virtual_boy(),
    "SMS": master_system(),
    "GG": game_gear(),
    "SG1000": sg1000(),
    "S32X": sega_32x(),
    "A2600": atari_vcs(),
    "A5200": atari_5200(),
    "A7800": atari_vcs(),
    "LYNX": lynx(),
    "PCE": pc_engine(),
    "CV": colecovision(),
    "INTV": intellivision(),
    "NGP": neo_geo_pocket(),
    "O2": odyssey2(),
    "VECTREX": vectrex(),
    "WS": wonderswan(),
    "N64": nintendo_64(),
    "GBA": game_boy_advance(),
    "MD": mega_drive(),
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
    if frame.metal:
        shine = np.zeros_like(h)
        for mask, metal_rgb in frame.metal:
            m = blur(mask, METAL_EDGE)
            albedo = albedo * (1 - m[..., None]) + np.array(metal_rgb, np.float32) * m[..., None]
            shine = np.maximum(shine, m)
        spec = spec * (1 + METAL_SPEC * shine)
    lit = AMBIENT * ao + DIFFUSE * ndl * shadow
    rgb = albedo * lit[..., None] + spec[..., None]
    rgb = np.clip(rgb * np.linspace(*FALLOFF, h.shape[0])[:, None, None], 0, 1)

    alpha = np.clip(frame.body, 0, 1)
    lip = np.clip(np.clip(1 - shadow * ao, 0, 1) * LIP_ALPHA_GAIN, 0, LIP_ALPHA_MAX)
    inside = win > 0.5
    alpha = np.where(inside, lip * win, alpha)
    rgb = np.where(inside[..., None], 0.05, rgb)
    return rgb, alpha


def render(name):
    """The neutral frame at export size, plus its window, dark-plastic and metal coverage (0..1)."""
    frame = CONSOLES[name]()
    rgb, alpha = shade(frame, height(frame))
    img = Image.fromarray((np.dstack([rgb, alpha]) * 255 + 0.5).astype(np.uint8), "RGBA")
    cv = frame.cv
    size = (round(cv.w * OUT_LONG / max(cv.w, cv.h)), round(cv.h * OUT_LONG / max(cv.w, cv.h)))

    def coverage(mask):
        if mask is None:
            return np.zeros(size[::-1], np.float32)
        im = Image.fromarray((np.clip(mask, 0, 1) * 255 + 0.5).astype(np.uint8)).resize(size, Image.BOX)
        return np.asarray(im, dtype=np.float32) / 255.0

    return img.resize(size, Image.LANCZOS), coverage(frame.window), coverage(frame.dark), coverage(frame.metal_mask())


# ---------------------------------------------------------------- colors
def ramp(img, dark, colors):
    """The colorize filter's luminance ramp, measured as gen-cartridge-colors.py does.

    Second-albedo zones (the Virtual Boy's dark band, the Lynx's contacts) and metal
    contacts are left out of the measure (dark is their combined coverage), so they
    do not flatten the shell's relief in every color."""
    a = np.asarray(img, dtype=np.int32)
    opaque = (a[..., 3] >= 200) & (dark < 0.5)
    lum = (a[..., 0] * 299 + a[..., 1] * 587 + a[..., 2] * 114) // 1000
    p5, p50, p95 = (np.percentile(lum[opaque], q, method="inverted_cdf") / 255.0 for q in (5, 50, 95))
    lo, hi = colors.SLOPE_LIMITS
    slope = min(max(colors.TARGET_SPREAD / max(p95 - p5, 1e-4), lo), hi)
    return slope, colors.TARGET_MID - slope * p50


def colorize(img, window, base_hex, ramp_):
    """bake-frames.py's colorize (desaturate, linear ramp, multiply the flood, keep alpha).

    The lip's shadow inside the label window (and any metal contacts, passed in the
    same coverage) stays as rendered, blended by that coverage so its edge has no
    colored halo."""
    slope, intercept = ramp_
    a = np.asarray(img, dtype=np.float32) / 255.0
    lum = 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]
    p = np.clip(slope * lum + intercept, 0, 1)[..., None]
    k = a[..., 3:4]
    flood = np.array([int(base_hex[i:i + 2], 16) / 255 for i in (1, 3, 5)], np.float32)
    rgb = flood * (1 - k + k * p)
    w = window[..., None]
    rgb = rgb * (1 - w) + a[..., :3] * w
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


def bake(console):
    """Render one console and write its neutral and colored drawables."""
    colors = _load_tool("gen-cartridge-colors")
    img, window, dark, metal = render(console)
    r = ramp(img, np.maximum(dark, metal), colors)
    keep = np.maximum(window, metal)     # pixels colorize leaves as rendered
    base = f"cart_{console.lower()}"
    save(img, base)
    for color_id, _name, base_hex in colors.PALETTE:
        save(colorize(img, keep, base_hex, r), f"{base}_{color_id}")
    print(f"{console:6} {img.size[0]}x{img.size[1]}  ramp slope={r[0]:.3f} intercept={r[1]:+.3f}", flush=True)
    return console, img


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("consoles", nargs="*", metavar="CONSOLE", help=f"subset of {' '.join(CONSOLES)}")
    ap.add_argument("--preview", metavar="DIR", help="also write a contact sheet with sample labels")
    ap.add_argument("--jobs", type=int, default=2, help="consoles rendered at once (each takes ~1 GB; default 2)")
    args = ap.parse_args(argv)
    unknown = [c for c in args.consoles if c.upper() not in CONSOLES]
    if unknown:
        ap.error(f"unknown console(s): {' '.join(unknown)}; known: {' '.join(CONSOLES)}")
    consoles = [c.upper() for c in args.consoles] or list(CONSOLES)
    with ProcessPoolExecutor(max_workers=max(1, min(args.jobs, len(consoles)))) as pool:
        sheet = list(pool.map(bake, consoles))
    if args.preview:
        out = Path(args.preview)
        out.mkdir(parents=True, exist_ok=True)
        preview(sheet, out / "cartridge-frames.png")
        print("preview:", out / "cartridge-frames.png")


if __name__ == "__main__":
    main()
