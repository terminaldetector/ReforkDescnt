#!/usr/bin/env python3
"""
Generate the extension icons (16/48/128 px) for TG Link Harvester.

Draws a dark rounded tile with a neon-cyan "link + paper-plane" mark, matching
the popup's dark neon theme. Re-run after editing to regenerate the PNGs:

    python3 make_icons.py
"""
import os
from PIL import Image, ImageDraw

BG = (10, 13, 18, 255)        # --bg
SURFACE = (17, 21, 32, 255)   # --surface
CYAN = (0, 229, 255, 255)     # --cyan
GREEN = (0, 255, 136, 255)    # --green

OUT_DIR = os.path.join(os.path.dirname(__file__), "icons")
os.makedirs(OUT_DIR, exist_ok=True)


def rounded_tile(size):
    """Create a dark rounded-square base tile at the given size."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = max(2, size // 6)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=SURFACE,
                        outline=BG, width=max(1, size // 32))
    return img, d


def draw_link_chain(d, size):
    """Draw two interlocking chain links (the 'link harvester' mark)."""
    w = max(2, size // 11)          # stroke width
    pad = size // 4
    # Left link (cyan).
    box_l = [pad, size * 0.42 - size * 0.18, size * 0.58, size * 0.42 + size * 0.18]
    d.rounded_rectangle(box_l, radius=size // 5, outline=CYAN, width=w)
    # Right link (green), offset to interlock.
    box_r = [size * 0.42, size * 0.58 - size * 0.18, size - pad, size * 0.58 + size * 0.18]
    d.rounded_rectangle(box_r, radius=size // 5, outline=GREEN, width=w)


def make(size):
    img, d = rounded_tile(size)
    draw_link_chain(d, size)
    path = os.path.join(OUT_DIR, f"icon{size}.png")
    img.save(path)
    print("wrote", path)


if __name__ == "__main__":
    for s in (16, 48, 128):
        make(s)
