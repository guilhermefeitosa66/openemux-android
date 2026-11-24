#!/usr/bin/env python3
"""The ready-made themes (issue 1018): their colors, their artwork and the files the app ships.

The files it writes under src/main/assets are force-added to git (`git add -f`): RetroArch's .gitignore ignores
every folder named assets.

Everything here is original: the pictures are drawn by this script from basic shapes (no image is copied
from anywhere), and the colors of Dracula and Tokyo Night come from those MIT-licensed palettes (credited in
art/themes/CREDITS.md and in the app's licenses).

    tools/gen-ready-themes.py            # writes art/themes/<id>/library.svg, assets/themes/<id>/...
    tools/gen-ready-themes.py --check    # only checks the contrast, writes nothing

For each theme it writes:
  art/themes/<id>/library.svg          the source picture (also for the desktop to ship its own copy)
  src/main/assets/themes/<id>/theme.yaml
  src/main/assets/themes/<id>/images/library.webp   rendered with headless Chrome, 720 x 1280

and fails when a text color reads under WCAG AA (4.5:1) on the surface it sits on, the dimmed picture
included (every pixel of it, not an average). Needs google-chrome and Pillow.
"""

import io
import math
import os
import random
import subprocess
import sys
import tempfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ART = os.path.join(ROOT, "art", "themes")
ASSETS = os.path.join(ROOT, "src", "main", "assets", "themes")
W, H = 1080, 1920
OUT_W, OUT_H = 720, 1280

# ---------------------------------------------------------------------------------------------------------------
# The themes: the same ids and colors on the desktop (docs/theme-format.md). Keys are theme.yaml's.

THEMES = [
    ("pastel", "Pastel", "light", {
        "window_bg": "#fff7fa", "headerbar_bg": "#fbe4ef", "sidebar_bg": "#f6efff", "library_bg": "#fff7fa",
        "console_list_bg": "#fbe4ef", "card_bg": "#ffffff", "popover_bg": "#ffffff", "details_bg": "#fff7fa",
        "text_primary": "#3b2f40", "text_secondary": "#66586b",
        "sidebar_text_primary": "#8a3f9c", "sidebar_text_secondary": "#66586b",
        "accent": "#a63a74", "selection_bg": "#fbd3e6", "accent_button_bg": "#b04a80", "accent_button_fg": "#ffffff",
        "border_color": "#f2d3e2", "shadow_color": "#e7a9c6",
        "corner_radius": 22.0, "border_width": 1.0, "shadow_strength": 0.3, "icon_color": "#8a6f8f",
        "library_image": "images/library.webp", "library_image_dim": 0.3, "library_image_blur": 0.0,
    }),
    ("dracula", "Dracula", "dark", {
        "window_bg": "#282a36", "headerbar_bg": "#44475a", "sidebar_bg": "#21222c", "library_bg": "#282a36",
        "console_list_bg": "#383a4a", "card_bg": "#343746", "popover_bg": "#303241", "details_bg": "#282a36",
        "text_primary": "#f8f8f2", "text_secondary": "#c3c5d8",
        "sidebar_text_primary": "#ff79c6", "sidebar_text_secondary": "#b4b7cc",
        "accent": "#bd93f9", "selection_bg": "#4b4f68", "accent_button_bg": "#bd93f9", "accent_button_fg": "#21222c",
        "border_color": "#44475a", "shadow_color": "#000000",
        "corner_radius": 14.0, "shadow_strength": 0.35, "icon_color": "#bd93f9",
        "library_image": "images/library.webp", "library_image_dim": 0.7, "library_image_blur": 0.0,
    }),
    ("kitty", "Kitty", "dark", {
        "window_bg": "#1f1a25", "headerbar_bg": "#2e2636", "sidebar_bg": "#1a1620", "library_bg": "#1f1a25",
        "console_list_bg": "#2e2636", "card_bg": "#2b2432", "popover_bg": "#2b2432", "details_bg": "#1f1a25",
        "text_primary": "#f6ecf4", "text_secondary": "#c9b8cc",
        "sidebar_text_primary": "#f7b9d7", "sidebar_text_secondary": "#bba9bf",
        "accent": "#f7b9d7", "selection_bg": "#4d3855", "accent_button_bg": "#f7b9d7", "accent_button_fg": "#2a1e2e",
        "border_color": "#3d3245", "shadow_color": "#000000",
        "corner_radius": 26.0, "shadow_strength": 0.3, "icon_color": "#d8c4e6",
        "library_image": "images/library.webp", "library_image_dim": 0.7, "library_image_blur": 0.0,
    }),
    ("neon", "Neon", "dark", {
        "window_bg": "#0d0221", "headerbar_bg": "#1d0b3a", "sidebar_bg": "#120530", "library_bg": "#0d0221",
        "console_list_bg": "#1d0b3a", "card_bg": "#1a0a36", "popover_bg": "#1a0a36", "details_bg": "#0d0221",
        "text_primary": "#f4eeff", "text_secondary": "#bfaee0",
        "sidebar_text_primary": "#ff4fb0", "sidebar_text_secondary": "#ab98d0",
        "accent": "#05d9e8", "selection_bg": "#3b1670", "accent_button_bg": "#ff2a6d", "accent_button_fg": "#12001c",
        "border_color": "#7b2ff7", "shadow_color": "#ff2a6d",
        "corner_radius": 10.0, "border_width": 1.0, "shadow_strength": 0.6, "icon_color": "#05d9e8",
        "library_image": "images/library.webp", "library_image_dim": 0.45, "library_image_blur": 0.0,
    }),
    ("tokyo-night", "Tokyo Night", "dark", {
        "window_bg": "#1a1b26", "headerbar_bg": "#24283b", "sidebar_bg": "#16161e", "library_bg": "#1a1b26",
        "console_list_bg": "#24283b", "card_bg": "#24283b", "popover_bg": "#1f2335", "details_bg": "#1a1b26",
        "text_primary": "#c0caf5", "text_secondary": "#a9b1d6",
        "sidebar_text_primary": "#7aa2f7", "sidebar_text_secondary": "#9aa5ce",
        "accent": "#7aa2f7", "selection_bg": "#2e3c64", "accent_button_bg": "#7aa2f7", "accent_button_fg": "#16161e",
        "border_color": "#292e42", "shadow_color": "#000000",
        "corner_radius": 12.0, "shadow_strength": 0.3, "icon_color": "#7dcfff",
        "library_image": "images/library.webp", "library_image_dim": 0.65, "library_image_blur": 0.0,
    }),
]

# ---------------------------------------------------------------------------------------------------------------
# The pictures. Each function returns the SVG body; all are 1080 x 1920 (portrait: a phone's library), with the
# main subject kept in the lower middle so a cover fit on a phone keeps it.


def svg(body, defs=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">\n'
            f"<!-- Original artwork for OpenEmux (issue 1018), drawn by tools/gen-ready-themes.py. MIT. -->\n"
            f"<defs>{defs}</defs>\n{body}\n</svg>\n")


def stars(rng, n, color, top=0, bottom=H, rmax=3.0, opacity=0.8):
    out = []
    for _ in range(n):
        x, y, r = rng.uniform(0, W), rng.uniform(top, bottom), rng.uniform(0.8, rmax)
        out.append(f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{r:.1f}" fill="{color}" opacity="{rng.uniform(0.35, opacity):.2f}"/>')
    return "\n".join(out)


def pastel():
    rng = random.Random(1)
    defs = ('<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">'
            '<stop offset="0" stop-color="#fdeef6"/><stop offset="0.55" stop-color="#fef6ee"/><stop offset="1" stop-color="#eef7ff"/></linearGradient>')
    body = ['<rect width="100%" height="100%" fill="url(#sky)"/>']
    # Soft clouds: rows of overlapping circles.
    for cx, cy, s, c in [(260, 420, 1.0, "#ffffff"), (820, 300, 0.8, "#fff4fb"), (640, 760, 1.2, "#ffffff"), (180, 1050, 0.7, "#f7f1ff")]:
        for dx, dy, r in [(-90, 20, 70), (-20, -20, 95), (70, 10, 80), (140, 30, 55)]:
            body.append(f'<circle cx="{cx + dx * s:.0f}" cy="{cy + dy * s:.0f}" r="{r * s:.0f}" fill="{c}" opacity="0.9"/>')
    # Sparkles.
    for _ in range(26):
        x, y, r = rng.uniform(40, W - 40), rng.uniform(80, 1250), rng.uniform(6, 14)
        c = rng.choice(["#fbcfe8", "#fed7aa", "#fef08a", "#bbf7d0", "#bfdbfe", "#ddd6fe"])
        body.append(f'<path d="M{x:.0f} {y - r:.0f} L{x + r * .3:.0f} {y - r * .3:.0f} L{x + r:.0f} {y:.0f} L{x + r * .3:.0f} {y + r * .3:.0f} '
                    f'L{x:.0f} {y + r:.0f} L{x - r * .3:.0f} {y + r * .3:.0f} L{x - r:.0f} {y:.0f} L{x - r * .3:.0f} {y - r * .3:.0f} Z" fill="{c}"/>')
    # Rolling hills, back to front.
    for base, amp, phase, color in [(1340, 70, 0.3, "#ddd6fe"), (1460, 60, 1.7, "#bfdbfe"), (1580, 55, 0.9, "#bbf7d0"), (1720, 45, 2.4, "#fed7aa")]:
        pts = " ".join(f"{x},{base + amp * math.sin(x / 180 + phase):.0f}" for x in range(0, W + 41, 40))
        body.append(f'<polygon points="0,{H} {pts} {W},{H}" fill="{color}"/>')
    return svg("\n".join(body), defs)


def dracula():
    rng = random.Random(2)
    defs = ('<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">'
            '<stop offset="0" stop-color="#1e1f29"/><stop offset="0.6" stop-color="#282a36"/><stop offset="1" stop-color="#343746"/></linearGradient>'
            '<radialGradient id="glow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#bd93f9" stop-opacity="0.16"/>'
            '<stop offset="1" stop-color="#bd93f9" stop-opacity="0"/></radialGradient>')
    body = ['<rect width="100%" height="100%" fill="url(#sky)"/>', stars(rng, 90, "#f8f8f2", 0, 1100, 2.4, 0.4)]
    # A crescent moon in a violet glow.
    body.append('<circle cx="760" cy="420" r="260" fill="url(#glow)"/>')
    body.append('<circle cx="760" cy="420" r="120" fill="#8c8aa6" opacity="0.55"/><circle cx="810" cy="385" r="112" fill="#252733"/>')
    # Bats: two arcs and a body each.
    for x, y, s in [(330, 560, 1.0), (470, 470, 0.7), (230, 690, 0.55), (600, 640, 0.6)]:
        body.append(f'<path transform="translate({x} {y}) scale({s})" fill="#191a21" d="M0 0 C-20 -30 -60 -30 -90 -5 C-70 -10 -55 0 -50 15 '
                    f'C-40 0 -25 0 -12 12 C-8 0 -4 -6 0 -6 C4 -6 8 0 12 12 C25 0 40 0 50 15 C55 0 70 -10 90 -5 C60 -30 20 -30 0 0 Z"/>')
    # Hills with a few dark pines.
    for base, amp, phase, color in [(1430, 80, 0.5, "#343746"), (1600, 60, 2.0, "#2b2d3a"), (1760, 40, 1.2, "#21222c")]:
        pts = " ".join(f"{x},{base + amp * math.sin(x / 210 + phase):.0f}" for x in range(0, W + 41, 40))
        body.append(f'<polygon points="0,{H} {pts} {W},{H}" fill="{color}"/>')
    for x in range(40, W, 95):
        h = rng.uniform(90, 170)
        base = 1610 + 60 * math.sin(x / 210 + 2.0)
        body.append(f'<polygon points="{x - 28},{base:.0f} {x},{base - h:.0f} {x + 28},{base:.0f}" fill="#1b1c25"/>')
    return svg("\n".join(body), defs)


def kitty():
    rng = random.Random(3)
    defs = ('<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">'
            '<stop offset="0" stop-color="#1c1722"/><stop offset="1" stop-color="#2c2233"/></linearGradient>')
    body = ['<rect width="100%" height="100%" fill="url(#sky)"/>', stars(rng, 50, "#f6ecf4", 0, 1000, 2.2, 0.35)]
    # Floating hearts.
    for _ in range(14):
        x, y, s = rng.uniform(60, W - 60), rng.uniform(150, 1150), rng.uniform(0.5, 1.2)
        c = rng.choice(["#f7b9d7", "#d8c4e6", "#bfe3f0", "#f9d9b4"])
        body.append(f'<path transform="translate({x:.0f} {y:.0f}) scale({s:.2f})" fill="{c}" opacity="0.22" '
                    'd="M0 12 C-26 -6 -18 -30 0 -18 C18 -30 26 -6 0 12 Z"/>')
    # A rug, a ball of yarn and the cat (moved to the upper right below).
    body.append('<g transform="translate(420 -450) scale(0.62)"><ellipse cx="640" cy="1700" rx="420" ry="70" fill="#2e2536"/>')
    body.append('<circle cx="330" cy="1630" r="62" fill="#3f5c69"/>'
                '<path d="M282 1600 C320 1590 360 1610 384 1640 M276 1640 C315 1625 355 1645 378 1675 M300 1580 C335 1600 350 1640 345 1688" '
                'stroke="#2e4652" stroke-width="7" fill="none" stroke-linecap="round"/>'
                '<path d="M392 1640 C450 1660 470 1700 520 1690" stroke="#3f5c69" stroke-width="6" fill="none" stroke-linecap="round"/></g>')
    cat = []
    fur, dark, pink = "#5e4b68", "#16111b", "#9a5c7c"
    cat.append(f'<path d="M905 1560 C985 1520 1010 1430 960 1400 C935 1385 925 1420 945 1440 C965 1460 945 1520 895 1530 Z" fill="{fur}" stroke="{dark}" stroke-width="8"/>')  # tail
    cat.append(f'<ellipse cx="760" cy="1560" rx="170" ry="150" fill="{fur}" stroke="{dark}" stroke-width="8"/>')  # body
    cat.append(f'<ellipse cx="700" cy="1680" rx="55" ry="32" fill="{fur}" stroke="{dark}" stroke-width="8"/>'
               f'<ellipse cx="830" cy="1680" rx="55" ry="32" fill="{fur}" stroke="{dark}" stroke-width="8"/>')  # paws
    cat.append(f'<path d="M640 1300 L655 1180 L735 1255 Z" fill="{fur}" stroke="{dark}" stroke-width="8" stroke-linejoin="round"/>'
               f'<path d="M880 1300 L865 1180 L785 1255 Z" fill="{fur}" stroke="{dark}" stroke-width="8" stroke-linejoin="round"/>'
               f'<path d="M662 1272 L668 1215 L708 1252 Z" fill="{pink}"/><path d="M858 1272 L852 1215 L812 1252 Z" fill="{pink}"/>')  # ears
    cat.append(f'<ellipse cx="760" cy="1345" rx="150" ry="125" fill="{fur}" stroke="{dark}" stroke-width="8"/>')  # head
    cat.append(f'<ellipse cx="705" cy="1345" rx="17" ry="24" fill="{dark}"/><ellipse cx="815" cy="1345" rx="17" ry="24" fill="{dark}"/>'
               '<circle cx="711" cy="1336" r="6" fill="#a99bb3"/><circle cx="821" cy="1336" r="6" fill="#a99bb3"/>')  # eyes
    cat.append(f'<path d="M748 1380 L772 1380 L760 1394 Z" fill="{pink}"/>'
               f'<path d="M760 1394 C755 1410 738 1414 730 1404 M760 1394 C765 1410 782 1414 790 1404" stroke="{dark}" stroke-width="6" fill="none" stroke-linecap="round"/>')
    cat.append(f'<ellipse cx="665" cy="1390" rx="24" ry="14" fill="{pink}" opacity="0.6"/><ellipse cx="855" cy="1390" rx="24" ry="14" fill="{pink}" opacity="0.6"/>')
    cat.append(f'<path d="M640 1372 L560 1360 M640 1388 L565 1395 M880 1372 L960 1360 M880 1388 L955 1395" stroke="{dark}" stroke-width="5" stroke-linecap="round"/>')
    # In the upper right, beside the screen's title, where the library's cards leave the picture visible.
    body.append('<g transform="translate(420 -450) scale(0.62)">' + "\n".join(cat) + "</g>")
    return svg("\n".join(body), defs)


def neon():
    defs = ('<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">'
            '<stop offset="0" stop-color="#0d0221"/><stop offset="0.62" stop-color="#2a0845"/><stop offset="0.62" stop-color="#0d0221"/>'
            '<stop offset="1" stop-color="#190636"/></linearGradient>'
            '<linearGradient id="sun" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f9c80e"/><stop offset="1" stop-color="#ff2a6d"/></linearGradient>'
            '<clipPath id="half"><rect x="0" y="0" width="1080" height="1190"/></clipPath>')
    rng = random.Random(4)
    body = ['<rect width="100%" height="100%" fill="url(#sky)"/>', stars(rng, 70, "#d1f7ff", 0, 900, 2.0, 0.4)]
    # A striped sun sitting on the horizon.
    sun = ['<g clip-path="url(#half)"><circle cx="540" cy="1050" r="300" fill="url(#sun)" opacity="0.5"/>']
    for i, y in enumerate(range(930, 1190, 34)):
        sun.append(f'<rect x="200" y="{y}" width="680" height="{6 + i * 2}" fill="#2a0845"/>')
    sun.append("</g>")
    body.append("".join(sun))
    # Mountains.
    body.append('<polygon points="0,1190 160,1020 300,1130 420,980 560,1190" fill="#1a0a36" stroke="#7b2ff7" stroke-width="4" stroke-opacity="0.6"/>'
                '<polygon points="520,1190 700,1000 820,1100 940,970 1080,1080 1080,1190" fill="#1a0a36" stroke="#7b2ff7" stroke-width="4"/>')
    # The perspective grid.
    grid = []
    horizon, vx = 1190, 540
    for i in range(-12, 13):
        x = vx + i * 150
        grid.append(f'<line x1="{vx + i * 18}" y1="{horizon}" x2="{x}" y2="{H}" stroke="#ff2a6d" stroke-width="3" opacity="0.35"/>')
    y, step = horizon + 12, 14
    while y < H:
        grid.append(f'<line x1="0" y1="{y:.0f}" x2="{W}" y2="{y:.0f}" stroke="#05d9e8" stroke-width="3" opacity="0.3"/>')
        step *= 1.32
        y += step
    body.append("\n".join(grid))
    return svg("\n".join(body), defs)


def tokyo_night():
    rng = random.Random(5)
    defs = ('<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">'
            '<stop offset="0" stop-color="#16161e"/><stop offset="0.7" stop-color="#1f2335"/><stop offset="1" stop-color="#24283b"/></linearGradient>')
    body = ['<rect width="100%" height="100%" fill="url(#sky)"/>', stars(rng, 80, "#c0caf5", 0, 900, 2.0, 0.35)]
    body.append('<circle cx="260" cy="360" r="90" fill="#6e7aa8" opacity="0.6"/><circle cx="292" cy="340" r="84" fill="#191a25"/>')
    # Two rows of buildings, the back one lighter, with lit windows.
    for row, (base, color, hmin, hmax) in enumerate([(1500, "#24283b", 300, 640), (1920, "#16161e", 380, 760)]):
        x = -20
        while x < W:
            w = rng.randint(90, 170)
            h = rng.randint(hmin, hmax)
            top = base - h if row == 0 else base - h - 120
            body.append(f'<rect x="{x}" y="{top}" width="{w}" height="{base - top}" fill="{color}"/>')
            if rng.random() < 0.4:
                body.append(f'<rect x="{x + w // 2 - 3}" y="{top - 40}" width="6" height="40" fill="{color}"/>'
                            f'<circle cx="{x + w // 2}" cy="{top - 42}" r="5" fill="#f7768e" opacity="0.5"/>')
            for wy in range(top + 24, base - 30, 34):
                for wx in range(x + 14, x + w - 20, 26):
                    if rng.random() < (0.22 if row == 0 else 0.3):
                        c = rng.choice(["#7aa2f7", "#e0af68", "#bb9af7", "#7dcfff"])
                        body.append(f'<rect x="{wx}" y="{wy}" width="12" height="16" fill="{c}" opacity="{0.28 if row == 0 else 0.45}"/>')
            x += w + rng.randint(4, 18)
    return svg("\n".join(body), defs)


ART_FOR = {"pastel": pastel, "dracula": dracula, "kitty": kitty, "neon": neon, "tokyo-night": tokyo_night}

# ---------------------------------------------------------------------------------------------------------------
# Contrast, as the app checks it (data/theme/UserTheme.kt): WCAG 2's ratio.


def lum(rgb):
    def ch(c):
        s = c / 255
        return s / 12.92 if s <= 0.03928 else ((s + 0.055) / 1.055) ** 2.4
    r, g, b = rgb
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def ratio(a, b):
    la, lb = lum(a), lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def rgb(hexstr):
    return tuple(int(hexstr[i:i + 2], 16) for i in (1, 3, 5))


PAIRS = [
    ("text_primary", ["window_bg", "library_bg", "card_bg", "popover_bg", "details_bg", "console_list_bg"]),
    ("text_secondary", ["window_bg", "library_bg", "card_bg", "popover_bg", "details_bg", "console_list_bg"]),
    ("sidebar_text_primary", ["sidebar_bg"]),
    ("sidebar_text_secondary", ["sidebar_bg"]),
    ("accent", ["window_bg", "card_bg"]),
    ("accent_button_fg", ["accent_button_bg"]),
]


def check(theme_id, values, image):
    problems = []
    for fg, bgs in PAIRS:
        for bg in bgs:
            r = ratio(rgb(values[fg]), rgb(values[bg]))
            if r < 4.5:
                problems.append(f"{fg} on {bg}: {r:.2f}")
    # Text drawn straight on the library reads on the picture dimmed toward library_bg: every pixel counts.
    dim, base = values["library_image_dim"], rgb(values["library_bg"])
    small = image.convert("RGB").resize((180, 320))
    worst = {}
    for px in small.get_flattened_data() if hasattr(small, 'get_flattened_data') else small.getdata():
        seen = tuple(round(p * (1 - dim) + b * dim) for p, b in zip(px, base))
        for fg in ("text_primary", "text_secondary", "accent"):
            r = ratio(rgb(values[fg]), seen)
            worst[fg] = min(worst.get(fg, 99), r)
    for fg, r in worst.items():
        if r < 4.5:
            problems.append(f"{fg} on the dimmed picture: {r:.2f}")
    return problems, worst


def render(svg_text):
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "art.html")
        with open(src, "w") as f:
            f.write(f"<html><body style='margin:0'>{svg_text}</body></html>")
        png = os.path.join(tmp, "art.png")
        subprocess.run(["google-chrome", "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--window-size={W},{H}",
                        f"--screenshot={png}", "file://" + src], check=True, capture_output=True, timeout=120)
        return Image.open(png).convert("RGB").copy()


def yaml_text(name, base, values):
    lines = ["version: 1", f"name: '{name}'", f"base: {base}"]
    for k, v in values.items():
        lines.append(f"{k}: {v:.1f}" if isinstance(v, float) else f"{k}: '{v}'" if v.startswith("#") else f"{k}: {v}")
    return "\n".join(lines) + "\n"


def main():
    only_check = "--check" in sys.argv
    failed = False
    for theme_id, name, base, values in THEMES:
        svg_text = ART_FOR[theme_id]()
        image = render(svg_text)
        problems, worst = check(theme_id, values, image)
        print(f"{theme_id}: worst on the picture " + ", ".join(f"{k} {v:.1f}" for k, v in worst.items()))
        for p in problems:
            print(f"  FAIL {p}")
        failed |= bool(problems)
        if only_check:
            continue
        os.makedirs(os.path.join(ART, theme_id), exist_ok=True)
        with open(os.path.join(ART, theme_id, "library.svg"), "w") as f:
            f.write(svg_text)
        out = os.path.join(ASSETS, theme_id)
        os.makedirs(os.path.join(out, "images"), exist_ok=True)
        with open(os.path.join(out, "theme.yaml"), "w") as f:
            f.write(yaml_text(name, base, values))
        buf = io.BytesIO()
        image.resize((OUT_W, OUT_H), Image.LANCZOS).save(buf, "WEBP", quality=78, method=6)
        with open(os.path.join(out, "images", "library.webp"), "wb") as f:
            f.write(buf.getvalue())
        print(f"  library.webp {len(buf.getvalue()) // 1024} KiB")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
