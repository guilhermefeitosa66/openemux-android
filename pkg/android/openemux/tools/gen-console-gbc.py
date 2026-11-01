#!/usr/bin/env python3
"""Generate console_gbc.png from console_gb.png (issue 656).

The Game Boy Color icon is the Game Boy's OpenEmu pixel art recoloured to
the Pokemon "Pikachu" edition: yellow body, blue screen bezel and A/B
buttons, a dark (switched off) GBC screen. Each region is remapped along a
luminance ramp, so the outline, the drop shadow and the shading stay the
Game Boy's pixel for pixel. Deterministic: running it twice writes the same
bytes.

    tools/gen-console-gbc.py
"""
import colorsys
from pathlib import Path

from PIL import Image

DRAWABLES = Path(__file__).resolve().parent.parent / "src" / "main" / "res" / "drawable-nodpi"
SOURCE = DRAWABLES / "console_gb.png"
TARGET = DRAWABLES / "console_gbc.png"

# Pixels below this alpha are the soft outline and the drop shadow: kept.
OPAQUE = 200

YELLOW = ("#966E0A", "#FFE250")
BLUE = ("#162C78", "#5080DC")
SLATE = ("#1A2226", "#3A4A4E")

# The red power LED would fall in the screen's hue range: kept as is.
POWER_LED = (0x5C, 0x00, 0x00)


def rgb(hex_color):
    return tuple(int(hex_color[i:i + 2], 16) for i in (1, 3, 5))


def ramp(colors, t):
    """The colour at t (clamped to [0, 1]) between the ramp's dark and light end."""
    t = min(1.0, max(0.0, t))
    dark, light = rgb(colors[0]), rgb(colors[1])
    return tuple(round(d + (l - d) * t) for d, l in zip(dark, light))


def recolour(r, g, b):
    """The Pikachu edition colour for one opaque Game Boy pixel, or None to keep it."""
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    hue = h * 360
    luma = (0.299 * r + 0.587 * g + 0.114 * b) / 255
    if (r, g, b) == POWER_LED or v < 0.30:
        return None  # D-pad, power LED, dark details
    if s < 0.16:
        return ramp(YELLOW, (luma - 0.40) / 0.52)  # body
    if 240 <= hue < 290:
        return ramp(BLUE, (luma - 0.40) / 0.32)  # screen bezel
    if 290 <= hue < 345:
        return ramp(BLUE, (luma - 0.20) / 0.40)  # A/B buttons
    if hue < 40:
        return ramp(SLATE, (luma - 0.15) / 0.20)  # screen
    return None


def main():
    image = Image.open(SOURCE).convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            if a < OPAQUE:
                continue
            new = recolour(r, g, b)
            if new is not None:
                pixels[x, y] = (*new, a)
    image.save(TARGET, optimize=True)


if __name__ == "__main__":
    main()
