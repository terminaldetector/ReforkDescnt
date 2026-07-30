#!/usr/bin/env python3
"""
DRMD 6DOF texture generator.

Regenerates every mod-owned texture from code so the whole set stays on one
palette and one lighting convention:

  * items   16x16, 1px near-black outline, light from the upper left
  * blocks  16x16, tileable industrial plating
  * entity  64x64 atlases laid out to match the cuboid UVs in
            entity/model/*.java  (keep the two in sync when a model changes)

Usage:  python3 scripts/gen_textures.py
Needs:  Pillow
"""

import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TEX = os.path.join(ROOT, "src/main/resources/assets/drmd/textures")

# ----------------------------------------------------------------- palette

OUT = (10, 14, 20, 255)          # outline
SHADOW = (24, 30, 38, 255)
HULL_D = (42, 48, 56, 255)
HULL_M = (69, 78, 89, 255)
HULL_L = (107, 118, 132, 255)
HULL_X = (150, 162, 178, 255)
RUST = (122, 74, 46, 255)

RED = (217, 59, 43, 255)
RED_L = (255, 106, 77, 255)
ORANGE = (255, 140, 26, 255)
AMBER = (255, 194, 77, 255)
CYAN = (53, 224, 255, 255)
CYAN_L = (155, 246, 255, 255)
GREEN = (59, 255, 154, 255)
GREEN_D = (24, 140, 84, 255)
VIOLET = (179, 107, 255, 255)
PINK = (255, 92, 190, 255)
WHITE = (240, 248, 255, 255)
BLACK = (0, 0, 0, 0)


def img(w=16, h=16):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def px(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def outline(im, c=OUT):
    """Trace a 1px dark border around every opaque cluster."""
    src = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < im.width and 0 <= ny < im.height and src.getpixel((nx, ny))[3] != 0:
                    px(im, x, y, c)
                    break


def shade(c, k):
    return (
        max(0, min(255, int(c[0] * k))),
        max(0, min(255, int(c[1] * k))),
        max(0, min(255, int(c[2] * k))),
        c[3],
    )


def glow(im, cx, cy, r, c):
    """Soft radial emissive blob."""
    for y in range(im.height):
        for x in range(im.width):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r:
                k = 1.0 - (d / (r + 0.001)) * 0.65
                px(im, x, y, shade(c, k))


# ----------------------------------------------------------------- item shapes
# Every shape draws body pixels only; outline() adds the border afterwards.

def sh_cannon(im, body, accent):
    """Side-on autocannon: receiver block, long barrel, muzzle brake."""
    rect(im, 2, 7, 9, 11, body)
    rect(im, 2, 7, 9, 7, shade(body, 1.35))
    rect(im, 2, 11, 9, 11, shade(body, 0.62))
    rect(im, 9, 8, 13, 10, shade(body, 0.85))
    rect(im, 13, 8, 14, 10, accent)
    rect(im, 3, 12, 6, 14, shade(body, 0.7))     # grip
    rect(im, 4, 5, 7, 6, shade(body, 1.1))       # sight rail
    px(im, 5, 6, accent)


def sh_gatling(im, body, accent):
    """Rotary barrel cluster."""
    rect(im, 2, 6, 8, 12, body)
    rect(im, 2, 6, 8, 6, shade(body, 1.35))
    rect(im, 2, 12, 8, 12, shade(body, 0.6))
    for i, y in enumerate((7, 9, 11)):
        rect(im, 8, y, 14, y, shade(body, 1.0 - i * 0.12))
        px(im, 14, y, accent)
    rect(im, 3, 13, 6, 14, shade(body, 0.7))


def sh_launcher(im, body, accent):
    """Shoulder tube with a warhead peeking out."""
    rect(im, 1, 6, 12, 10, body)
    rect(im, 1, 6, 12, 6, shade(body, 1.3))
    rect(im, 1, 10, 12, 10, shade(body, 0.6))
    rect(im, 12, 7, 14, 9, accent)
    px(im, 14, 8, WHITE)
    rect(im, 2, 11, 5, 13, shade(body, 0.7))     # grip
    rect(im, 6, 4, 9, 5, shade(body, 0.9))       # sight
    px(im, 7, 4, accent)


def sh_missile(im, body, accent):
    """Free missile: nose cone, body, tail fins, exhaust."""
    rect(im, 5, 2, 10, 12, body)
    rect(im, 5, 2, 6, 12, shade(body, 1.3))
    rect(im, 9, 2, 10, 12, shade(body, 0.65))
    rect(im, 6, 0, 9, 2, accent)
    px(im, 7, 0, WHITE)
    px(im, 8, 0, WHITE)
    rect(im, 3, 9, 4, 13, shade(body, 0.8))
    rect(im, 11, 9, 12, 13, shade(body, 0.8))
    rect(im, 6, 13, 9, 14, ORANGE)
    rect(im, 7, 15, 8, 15, AMBER)


def sh_emitter(im, body, accent):
    """Beam emitter: prism housing plus focusing lens."""
    rect(im, 2, 5, 8, 11, body)
    rect(im, 2, 5, 8, 5, shade(body, 1.35))
    rect(im, 2, 11, 8, 11, shade(body, 0.6))
    rect(im, 8, 6, 10, 10, shade(body, 0.85))
    rect(im, 10, 7, 11, 9, accent)
    rect(im, 12, 8, 15, 8, shade(accent, 1.15))
    rect(im, 4, 12, 7, 14, shade(body, 0.7))
    rect(im, 3, 7, 4, 9, shade(accent, 0.8))


def sh_fusion(im, body, accent):
    """Fusion cannon: twin charge prongs with an arc bridging the gap."""
    rect(im, 1, 6, 8, 11, body)
    rect(im, 1, 6, 8, 6, shade(body, 1.35))
    rect(im, 1, 11, 8, 11, shade(body, 0.6))
    rect(im, 3, 12, 6, 15, shade(body, 0.7))       # grip
    # Coil bands on the receiver.
    for x in (3, 5, 7):
        rect(im, x, 6, x, 11, shade(accent, 0.55))
    # Twin prongs.
    rect(im, 8, 4, 13, 5, shade(body, 1.05))
    rect(im, 8, 12, 13, 13, shade(body, 0.8))
    rect(im, 13, 4, 14, 6, shade(accent, 0.9))
    rect(im, 13, 11, 14, 13, shade(accent, 0.9))
    # Arc across the gap.
    glow(im, 13.5, 8.5, 3.0, accent)
    px(im, 13, 8, WHITE)
    px(im, 14, 9, WHITE)


def sh_rail(im, body, accent):
    """Railgun: twin accelerator rails with an armature between them."""
    rect(im, 1, 5, 14, 6, body)
    rect(im, 1, 10, 14, 11, body)
    rect(im, 1, 5, 14, 5, shade(body, 1.3))
    rect(im, 1, 11, 14, 11, shade(body, 0.6))
    rect(im, 3, 7, 6, 9, shade(body, 0.85))
    rect(im, 7, 7, 12, 9, accent)
    px(im, 13, 8, WHITE)
    rect(im, 2, 12, 5, 14, shade(body, 0.7))


def sh_mine(im, body, accent):
    """Proximity mine: faceted shell with spikes and a pulse core."""
    rect(im, 5, 4, 10, 11, body)
    rect(im, 4, 5, 11, 10, body)
    rect(im, 4, 5, 11, 5, shade(body, 1.3))
    rect(im, 4, 10, 11, 10, shade(body, 0.6))
    for x, y in ((7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)):
        px(im, x, y, shade(body, 0.9))
    rect(im, 6, 2, 9, 3, shade(body, 0.8))
    rect(im, 6, 12, 9, 13, shade(body, 0.8))
    rect(im, 2, 6, 3, 9, shade(body, 0.8))
    rect(im, 12, 6, 13, 9, shade(body, 0.8))
    glow(im, 7.5, 7.5, 2.4, accent)


def sh_orb(im, body, accent):
    """Exotic charge: plasma sphere held in a containment cage."""
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.0) ** 2) ** 0.5
            if d <= 4.7:
                px(im, x, y, shade(accent, 1.15 - d * 0.12))
    # Cage: two meridians plus an equator ring.
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.0) ** 2) ** 0.5
            if 4.4 < d <= 5.6 and (abs(x - 7.5) < 1.4 or abs(y - 7.0) < 1.2):
                px(im, x, y, shade(body, 1.1))
    rect(im, 2, 6, 3, 8, shade(body, 1.0))
    rect(im, 12, 6, 13, 8, shade(body, 1.0))
    rect(im, 3, 12, 12, 13, body)
    rect(im, 3, 12, 12, 12, shade(body, 1.3))
    rect(im, 3, 13, 12, 13, shade(body, 0.6))
    rect(im, 5, 14, 6, 15, shade(body, 0.75))
    rect(im, 9, 14, 10, 15, shade(body, 0.75))
    px(im, 5, 4, WHITE)


def sh_deploy(im, body, accent):
    """Deployable field projector: tripod pod with an antenna."""
    rect(im, 4, 6, 11, 11, body)
    rect(im, 4, 6, 11, 6, shade(body, 1.3))
    rect(im, 4, 11, 11, 11, shade(body, 0.6))
    rect(im, 7, 2, 8, 6, shade(body, 0.9))
    glow(im, 7.5, 1.5, 2.0, accent)
    rect(im, 2, 12, 4, 14, shade(body, 0.75))
    rect(im, 11, 12, 13, 14, shade(body, 0.75))
    rect(im, 6, 12, 9, 14, shade(body, 0.75))
    rect(im, 5, 8, 10, 9, accent)


def sh_bomb(im, body, accent):
    """Aerial bomb: ogive nose, banded body, cruciform tail."""
    rect(im, 7, 0, 8, 1, shade(body, 1.2))
    rect(im, 6, 1, 9, 2, shade(body, 1.15))
    rect(im, 6, 2, 9, 10, body)
    rect(im, 6, 2, 6, 10, shade(body, 1.35))
    rect(im, 9, 2, 9, 10, shade(body, 0.6))
    rect(im, 6, 4, 9, 4, accent)
    rect(im, 6, 8, 9, 8, shade(accent, 0.7))
    rect(im, 6, 10, 9, 11, shade(body, 0.85))
    rect(im, 4, 11, 5, 14, shade(body, 0.75))     # port fin
    rect(im, 10, 11, 11, 14, shade(body, 0.75))   # starboard fin
    rect(im, 7, 11, 8, 15, shade(body, 0.95))     # ventral fin
    rect(im, 4, 14, 11, 14, shade(body, 0.6))


def sh_tool(im, body, accent):
    """Engineer tool: pistol grip, chassis, focusing head."""
    rect(im, 2, 5, 10, 10, body)
    rect(im, 2, 5, 10, 5, shade(body, 1.35))
    rect(im, 2, 10, 10, 10, shade(body, 0.6))
    rect(im, 3, 11, 6, 15, shade(body, 0.72))    # grip
    rect(im, 10, 6, 13, 9, shade(body, 0.9))
    glow(im, 13.5, 7.5, 2.2, accent)
    rect(im, 4, 3, 7, 4, shade(body, 1.05))
    px(im, 5, 3, accent)
    rect(im, 3, 7, 4, 8, shade(accent, 0.75))


def sh_scanner(im, body, accent):
    """Handheld scanner: screen slab on a stub grip."""
    rect(im, 3, 2, 12, 11, body)
    rect(im, 3, 2, 12, 2, shade(body, 1.35))
    rect(im, 3, 11, 12, 11, shade(body, 0.6))
    rect(im, 5, 4, 10, 9, shade(accent, 0.35))
    for i in range(3):
        rect(im, 5, 5 + i * 2, 10 - i * 2, 5 + i * 2, accent)
    rect(im, 6, 12, 9, 15, shade(body, 0.72))
    px(im, 12, 3, RED_L)


def sh_designator(im, body, accent):
    """Laser designator: optic tube on a bipod."""
    rect(im, 1, 6, 11, 9, body)
    rect(im, 1, 6, 11, 6, shade(body, 1.3))
    rect(im, 1, 9, 11, 9, shade(body, 0.6))
    rect(im, 11, 7, 13, 8, shade(accent, 0.9))
    rect(im, 14, 7, 15, 8, accent)
    rect(im, 3, 3, 7, 5, shade(body, 0.9))
    rect(im, 4, 4, 6, 4, shade(accent, 0.8))
    rect(im, 2, 10, 3, 14, shade(body, 0.75))
    rect(im, 8, 10, 9, 14, shade(body, 0.75))


def sh_ship(im, body, accent):
    """Pyro GX top-down: fuselage, swept wings, twin thrusters."""
    rect(im, 7, 1, 8, 13, body)
    rect(im, 7, 1, 7, 13, shade(body, 1.25))
    rect(im, 6, 3, 9, 12, body)
    rect(im, 9, 3, 9, 12, shade(body, 0.7))
    rect(im, 2, 7, 5, 11, shade(body, 0.9))
    rect(im, 10, 7, 13, 11, shade(body, 0.9))
    rect(im, 1, 9, 2, 11, shade(body, 0.75))
    rect(im, 13, 9, 14, 11, shade(body, 0.75))
    rect(im, 6, 5, 9, 7, accent)                  # canopy
    px(im, 7, 6, WHITE)
    rect(im, 5, 13, 6, 15, CYAN)
    rect(im, 9, 13, 10, 15, CYAN)
    px(im, 5, 15, CYAN_L)
    px(im, 10, 15, CYAN_L)


def sh_egg(im, body, accent):
    """Drone spawn egg: shell plus role-coloured markings."""
    for y in range(16):
        for x in range(16):
            dx = (x - 7.5) / 5.0
            dy = (y - 8.5) / 6.6
            if dx * dx + dy * dy <= 1.0:
                k = 1.25 - ((x - 4) ** 2 + (y - 4) ** 2) ** 0.5 * 0.055
                px(im, x, y, shade(body, k))
    for cx, cy, r in ((5, 6, 1.4), (10, 9, 1.6), (7, 12, 1.3)):
        for y in range(16):
            for x in range(16):
                if im.getpixel((x, y))[3] and ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 <= r:
                    px(im, x, y, accent)


def sh_plate(im, body, accent):
    """Crafting intermediate: stacked alloy plate."""
    rect(im, 2, 4, 13, 11, body)
    rect(im, 2, 4, 13, 4, shade(body, 1.35))
    rect(im, 2, 11, 13, 11, shade(body, 0.6))
    rect(im, 2, 12, 13, 13, shade(body, 0.75))
    rect(im, 2, 13, 13, 13, shade(body, 0.5))
    for x in (4, 11):
        px(im, x, 6, accent)
        px(im, x, 9, accent)
    rect(im, 6, 7, 9, 8, shade(accent, 0.55))


def sh_cell(im, body, accent):
    """Crafting intermediate: energy cell."""
    rect(im, 4, 2, 11, 14, body)
    rect(im, 4, 2, 5, 14, shade(body, 1.3))
    rect(im, 10, 2, 11, 14, shade(body, 0.65))
    rect(im, 6, 0, 9, 2, shade(body, 0.85))
    rect(im, 5, 4, 10, 12, shade(accent, 0.35))
    for i in range(4):
        rect(im, 6, 5 + i * 2, 9, 5 + i * 2, accent)
    px(im, 7, 1, accent)
    px(im, 8, 1, accent)


def sh_core(im, body, accent):
    """Crafting intermediate: targeting core."""
    rect(im, 3, 3, 12, 12, body)
    rect(im, 3, 3, 12, 3, shade(body, 1.35))
    rect(im, 3, 12, 12, 12, shade(body, 0.6))
    rect(im, 5, 5, 10, 10, shade(body, 0.7))
    glow(im, 7.5, 7.5, 2.6, accent)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px(im, x, y, shade(accent, 0.9))
    rect(im, 7, 1, 8, 2, shade(body, 0.9))
    rect(im, 7, 13, 8, 14, shade(body, 0.9))
    rect(im, 1, 7, 2, 8, shade(body, 0.9))
    rect(im, 13, 7, 14, 8, shade(body, 0.9))


SHAPES = {
    "fusion": sh_fusion,
    "cannon": sh_cannon, "gatling": sh_gatling, "launcher": sh_launcher,
    "missile": sh_missile, "emitter": sh_emitter, "rail": sh_rail,
    "mine": sh_mine, "orb": sh_orb, "deploy": sh_deploy, "bomb": sh_bomb,
    "tool": sh_tool, "scanner": sh_scanner, "designator": sh_designator,
    "ship": sh_ship, "egg": sh_egg, "plate": sh_plate, "cell": sh_cell,
    "core": sh_core,
}

# id -> (shape, body colour, accent colour)
ITEMS = {
    # --- primary / secondary guns
    "weapon_d6_mg":            ("cannon", HULL_M, AMBER),
    "weapon_d6_vulcan":        ("gatling", HULL_M, ORANGE),
    "weapon_d6_heavy":         ("cannon", HULL_D, RED_L),
    "weapon_d6_flak":          ("cannon", RUST, ORANGE),
    "weapon_d6_plasma":        ("emitter", HULL_D, VIOLET),
    "weapon_d6_laser":         ("emitter", HULL_M, RED_L),
    "weapon_d6_quad_laser":    ("emitter", HULL_L, CYAN),
    "weapon_d6_overdrive":     ("emitter", HULL_M, GREEN),
    "weapon_d6_darklance":     ("emitter", (36, 28, 52, 255), VIOLET),
    "weapon_d6_bfg":           ("emitter", (30, 54, 44, 255), GREEN),
    "weapon_d6_fusion":        ("fusion", (44, 34, 62, 255), VIOLET),
    # --- rails
    "weapon_d6_railmk2":       ("rail", HULL_L, CYAN),
    "weapon_d6_gravy_railgun": ("rail", HULL_D, VIOLET),
    # --- launchers & missiles
    "weapon_d6_rockets":       ("launcher", HULL_M, RED),
    "weapon_d6_frag":          ("launcher", RUST, AMBER),
    "weapon_d6_homing":        ("missile", HULL_L, CYAN),
    "weapon_d6_concussion":    ("missile", HULL_M, ORANGE),
    "weapon_d6_smart_missile": ("missile", HULL_L, GREEN),
    "weapon_d6_mega_missile":  ("missile", HULL_D, RED_L),
    # --- mines & deployables
    "weapon_d6_gravmine":      ("mine", HULL_D, VIOLET),
    "weapon_d6_plasmamine":    ("mine", HULL_D, CYAN),
    "weapon_d6_energytrap":    ("deploy", HULL_M, GREEN),
    "weapon_d6_darkfield":     ("deploy", (36, 28, 52, 255), VIOLET),
    # --- exotic
    "weapon_d6_reactor":       ("orb", HULL_D, ORANGE),
    "weapon_d6_shockwave":     ("orb", HULL_M, CYAN),
    "weapon_d6_warp":          ("orb", HULL_D, VIOLET),
    "weapon_d6_telefrag":      ("orb", HULL_D, PINK),
    "weapon_d6_whiplash":      ("deploy", HULL_L, AMBER),
    # --- ordnance
    "bomb_tnt":                ("bomb", HULL_D, RED),
    "bomb_cluster":            ("bomb", RUST, AMBER),
    "bomb_incendiary":         ("bomb", (74, 44, 26, 255), ORANGE),
    "bomb_guided":             ("bomb", HULL_L, GREEN),
    "laser_designator":        ("designator", HULL_M, RED_L),
    # --- engineer kit
    "build_tool":              ("tool", HULL_L, AMBER),
    "construction_laser":      ("tool", HULL_M, GREEN),
    "repair_laser":            ("tool", HULL_M, CYAN),
    "mining_laser":            ("tool", RUST, ORANGE),
    "gravity_scanner":         ("scanner", HULL_D, CYAN),
    # --- ship
    "pyro_gx":                 ("ship", HULL_L, CYAN),
    # --- crafting intermediates
    "alloy_plate":             ("plate", HULL_L, ORANGE),
    "energy_cell":             ("cell", HULL_D, CYAN),
    "targeting_core":          ("core", HULL_M, GREEN),
}

EGGS = {
    "spawn_egg_assault":     ((0xCC, 0x33, 0x33), (0x44, 0x22, 0x22)),
    "spawn_egg_interceptor": ((0x33, 0xAA, 0xCC), (0x22, 0x44, 0x55)),
    "spawn_egg_artillery":   ((0xCC, 0xAA, 0x33), (0x55, 0x44, 0x22)),
    "spawn_egg_support":     ((0x33, 0xCC, 0x66), (0x22, 0x44, 0x33)),
    "spawn_egg_heavy_elite": ((0xAA, 0x33, 0xCC), (0x33, 0x11, 0x44)),
    "spawn_egg_mg":          ((0x88, 0x88, 0x88), (0x33, 0x33, 0x33)),
    "spawn_egg_laser":       ((0xFF, 0x55, 0x55), (0x55, 0x11, 0x11)),
    "spawn_egg_rpg":         ((0xCC, 0x77, 0x33), (0x44, 0x22, 0x11)),
    "spawn_egg_heavy":       ((0x55, 0x55, 0x77), (0x22, 0x22, 0x33)),
    "spawn_egg_seeker":       ((0x55, 0xFF, 0xAA), (0x11, 0x55, 0x33)),
    "spawn_egg_tripod":       ((0x3A, 0x44, 0x50), (0xFF, 0x33, 0x66)),
    "spawn_egg_scanner":      ((0x1E, 0x2A, 0x38), (0x35, 0xE0, 0xFF)),
    "spawn_egg_spider_turret": ((0x2A, 0x30, 0x38), (0xFF, 0xC2, 0x4D)),
}


def build_items(outdir):
    n = 0
    for name, (shape, body, accent) in ITEMS.items():
        im = img()
        SHAPES[shape](im, body, accent)
        outline(im)
        im.save(os.path.join(outdir, name + ".png"))
        n += 1
    for name, (primary, secondary) in EGGS.items():
        im = img()
        sh_egg(im, primary + (255,), secondary + (255,))
        outline(im)
        im.save(os.path.join(outdir, name + ".png"))
        n += 1
    return n


# ----------------------------------------------------------------- block textures

def plating(im, base, bolt=None, seam=True):
    """Tileable brushed-metal plate with corner bolts."""
    for y in range(16):
        for x in range(16):
            k = 1.0 + ((x * 7 + y * 13) % 5 - 2) * 0.022
            if y < 2:
                k += 0.16
            if y > 13:
                k -= 0.16
            px(im, x, y, shade(base, k))
    if seam:
        for x in range(16):
            px(im, x, 7, shade(base, 0.72))
            px(im, x, 8, shade(base, 1.12))
        for y in range(16):
            px(im, 7, y, shade(base, 0.78))
    if bolt:
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (2, 5), (13, 10)):
            px(im, x, y, bolt)


def blk_six_d_soil():
    im = img()
    plating(im, (46, 66, 48, 255), seam=False)
    for x in range(16):
        for y in range(16):
            if (x * 5 + y * 11) % 17 == 0:
                px(im, x, y, (86, 148, 88, 255))
            elif (x * 3 + y * 7) % 23 == 0:
                px(im, x, y, (34, 48, 36, 255))
    for x, y in ((3, 4), (11, 6), (6, 12), (13, 11)):
        px(im, x, y, GREEN)
    return im


def blk_hermetic_gate():
    im = img()
    plating(im, HULL_M, bolt=shade(HULL_X, 1.0), seam=False)
    rect(im, 0, 6, 15, 9, shade(HULL_D, 1.0))
    rect(im, 0, 7, 15, 7, AMBER)
    rect(im, 0, 8, 15, 8, shade(AMBER, 0.55))
    for x in range(0, 16, 4):
        rect(im, x, 6, x + 1, 6, shade(HULL_X, 1.05))
        rect(im, x + 2, 9, x + 3, 9, shade(HULL_D, 0.8))
    return im


def blk_laser_barrier():
    im = img()
    for y in range(16):
        for x in range(16):
            px(im, x, y, (14, 34, 44, 150))
    for x in range(16):
        rect(im, x, 0, x, 1, shade(HULL_D, 1.0))
        rect(im, x, 14, x, 15, shade(HULL_D, 1.0))
    for y in (4, 7, 10):
        for x in range(16):
            px(im, x, y, CYAN if (x + y) % 3 else CYAN_L)
    for x in (2, 8, 13):
        for y in range(2, 14):
            px(im, x, y, shade(CYAN, 0.5))
    return im


def turret(base, lens, barrels):
    im = img()
    plating(im, base, bolt=shade(HULL_X, 0.9), seam=False)
    rect(im, 3, 3, 12, 12, shade(base, 0.78))
    rect(im, 3, 3, 12, 3, shade(base, 1.25))
    rect(im, 3, 12, 12, 12, shade(base, 0.55))
    glow(im, 7.5, 7.5, 3.0, lens)
    for i in range(barrels):
        y = 6 + i * 3
        rect(im, 13, y, 15, y, shade(base, 1.05))
        px(im, 15, y, lens)
    rect(im, 0, 7, 2, 8, shade(base, 0.9))
    return im


def blk_magnetic_anomaly():
    im = img()
    plating(im, (36, 34, 52, 255), seam=False)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 5.0 < d < 6.2 or 2.4 < d < 3.4:
                px(im, x, y, shade(VIOLET, 0.85))
    glow(im, 7.5, 7.5, 2.0, VIOLET)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(im, x, y, shade(VIOLET, 1.1))
    return im


def blk_unstable_reactor():
    im = img()
    plating(im, (52, 34, 26, 255), bolt=shade(AMBER, 0.8), seam=False)
    rect(im, 4, 4, 11, 11, (28, 18, 14, 255))
    glow(im, 7.5, 7.5, 3.6, ORANGE)
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        px(im, x, y, AMBER)
    for x in range(16):
        if (x * 5) % 7 == 0:
            px(im, x, 0, RED_L)
            px(im, x, 15, RED_L)
    return im


def blk_gravity_generator():
    im = img()
    plating(im, HULL_D, bolt=shade(HULL_X, 0.95), seam=False)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 4.6 < d < 5.8:
                px(im, x, y, shade(CYAN, 0.8))
    rect(im, 6, 6, 9, 9, shade(HULL_L, 0.9))
    glow(im, 7.5, 7.5, 1.8, CYAN_L)
    rect(im, 0, 0, 15, 0, shade(HULL_L, 1.1))
    rect(im, 0, 15, 15, 15, shade(HULL_D, 0.7))
    return im


def blk_gravity_torch():
    """Torch template samples the top-left of the sheet; keep the emitter there."""
    im = img()
    rect(im, 6, 8, 9, 15, shade(HULL_M, 0.9))
    rect(im, 6, 8, 6, 15, shade(HULL_M, 1.25))
    rect(im, 9, 8, 9, 15, shade(HULL_M, 0.65))
    rect(im, 6, 5, 9, 8, shade(HULL_D, 1.0))
    glow(im, 7.5, 4.0, 3.0, CYAN)
    px(im, 7, 2, CYAN_L)
    px(im, 8, 2, CYAN_L)
    return im


def blk_checkpoint():
    im = img()
    plating(im, (24, 44, 40, 255), seam=False)
    for y in range(16):
        for x in range(16):
            if abs(x - y) < 2 or abs(15 - x - y) < 2:
                px(im, x, y, shade(GREEN_D, 1.0))
    glow(im, 7.5, 7.5, 3.2, GREEN)
    rect(im, 0, 0, 15, 0, shade(GREEN_D, 0.8))
    rect(im, 0, 15, 15, 15, shade(GREEN_D, 0.8))
    return im


def blk_dock():
    im = img()
    plating(im, HULL_M, bolt=shade(HULL_X, 1.0))
    rect(im, 4, 4, 11, 11, shade(HULL_D, 1.0))
    rect(im, 5, 5, 10, 10, shade(HULL_L, 0.95))
    rect(im, 6, 6, 9, 9, shade(CYAN, 0.55))
    px(im, 7, 7, CYAN_L)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px(im, x, y, AMBER)
    return im


def blk_combat_zone():
    im = img()
    for y in range(16):
        for x in range(16):
            px(im, x, y, (48, 14, 18, 130))
    for y in range(16):
        for x in range(16):
            if (x + y) % 6 < 2:
                px(im, x, y, (150, 34, 40, 190))
    for x in range(16):
        px(im, x, 0, RED)
        px(im, x, 15, RED)
        px(im, 0, x, RED)
        px(im, 15, x, RED)
    return im


def blk_nav_node():
    im = img()
    plating(im, (30, 40, 26, 255), seam=False)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 3.2 < d < 4.4 or 6.0 < d < 7.0:
                px(im, x, y, shade(AMBER, 0.9))
    glow(im, 7.5, 7.5, 2.2, AMBER)
    return im


def blk_objective():
    im = img()
    plating(im, (86, 66, 20, 255), bolt=shade(AMBER, 1.1), seam=False)
    for y in range(16):
        for x in range(16):
            if abs(x - 7.5) + abs(y - 7.5) < 5.0:
                px(im, x, y, shade(AMBER, 1.0 - abs(x - 7.5) * 0.04))
    px(im, 7, 7, WHITE)
    px(im, 8, 8, shade(AMBER, 0.6))
    return im


def blk_reactor_casing():
    im = img()
    plating(im, HULL_D, bolt=shade(HULL_X, 0.9))
    for x in range(16):
        px(im, x, 3, shade(HULL_L, 1.0))
        px(im, x, 12, shade(HULL_D, 0.7))
    return im


def blk_reactor_core_block():
    im = img()
    plating(im, (36, 26, 20, 255), seam=False)
    glow(im, 7.5, 7.5, 6.4, ORANGE)
    for y in range(16):
        for x in range(16):
            if (x * 3 + y * 5) % 11 == 0:
                px(im, x, y, AMBER)
    rect(im, 0, 0, 15, 0, shade(HULL_D, 1.0))
    rect(im, 0, 15, 15, 15, shade(HULL_D, 0.8))
    return im


def blk_end_reactor_panel():
    im = img()
    plating(im, (30, 26, 44, 255), bolt=shade(VIOLET, 0.9), seam=False)
    for y in range(3, 13):
        for x in range(3, 13):
            if (x + y) % 4 == 0:
                px(im, x, y, shade(VIOLET, 0.9))
    glow(im, 7.5, 7.5, 2.4, VIOLET)
    return im


def blk_drill_rig(active=False):
    """Laser drill rig: armoured housing around a downward emitter."""
    im = img()
    plating(im, (48, 44, 40, 255), bolt=shade(AMBER, 0.9), seam=False)
    rect(im, 3, 2, 12, 11, shade(HULL_D, 1.0))
    rect(im, 3, 2, 12, 2, shade(HULL_L, 1.0))
    hazard(im, 3, 3, 13, 6, AMBER if active else shade(AMBER, 0.45), (26, 22, 20, 255))
    lens = ORANGE if active else shade(ORANGE, 0.35)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 9.5) ** 2) ** 0.5
            if d < 3.2:
                px(im, x, y, shade(lens, 1.1 - d * 0.13))
    if active:
        for x in range(6, 10):
            px(im, x, 14, AMBER)
            px(im, x, 15, shade(ORANGE, 1.0))
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(im, x, y, shade(HULL_X, 1.0))
    return im


BLOCKS = {
    "drill_rig": lambda: blk_drill_rig(False),
    "drill_rig_active": lambda: blk_drill_rig(True),
    "six_d_soil": blk_six_d_soil,
    "hermetic_gate": blk_hermetic_gate,
    "laser_barrier": blk_laser_barrier,
    "volume_turret": lambda: turret(HULL_D, GREEN, 1),
    "laser_turret": lambda: turret(HULL_M, RED_L, 2),
    "plasma_turret": lambda: turret((40, 32, 58, 255), VIOLET, 2),
    "point_defense_turret": lambda: turret(HULL_L, CYAN, 3),
    "magnetic_anomaly": blk_magnetic_anomaly,
    "unstable_reactor": blk_unstable_reactor,
    "gravity_generator": blk_gravity_generator,
    "gravity_torch": blk_gravity_torch,
    "checkpoint": blk_checkpoint,
    "dock": blk_dock,
    "combat_zone": blk_combat_zone,
    "nav_node": blk_nav_node,
    "objective": blk_objective,
    "reactor_casing": blk_reactor_casing,
    "reactor_core_block": blk_reactor_core_block,
    "end_reactor_panel": blk_end_reactor_panel,
}


def build_blocks(outdir):
    for name, fn in BLOCKS.items():
        fn().save(os.path.join(outdir, name + ".png"))
    return len(BLOCKS)


# ----------------------------------------------------------------- entity atlases
# Minecraft box UV: width = 2*(sz+sx), height = sz+sy, laid out as
#   (u+sz, v)              top     sx x sz
#   (u+sz+sx, v)           bottom  sx x sz
#   (u, v+sz)              right   sz x sy
#   (u+sz, v+sz)           front   sx x sy
#   (u+sz+sx, v+sz)        left    sz x sy
#   (u+sz+sx+sz, v+sz)     back    sx x sy

def box(im, u, v, sx, sy, sz, base, top=None, accent=None, stripe=False):
    top = top or shade(base, 1.28)
    bottom = shade(base, 0.62)
    side = shade(base, 0.86)

    def fill(x0, y0, w, h, c, hi=None):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                k = 1.0 + ((x * 5 + y * 9) % 4 - 1.5) * 0.03
                px(im, x, y, shade(c, k))
        if hi:
            for x in range(x0, x0 + w):
                px(im, x, y0, hi)

    fill(u + sz, v, sx, sz, top)
    fill(u + sz + sx, v, sx, sz, bottom)
    fill(u, v + sz, sz, sy, side, shade(side, 1.2))
    fill(u + sz, v + sz, sx, sy, base, shade(base, 1.25))
    fill(u + sz + sx, v + sz, sz, sy, side, shade(side, 1.2))
    fill(u + sz + sx + sz, v + sz, sx, sy, shade(base, 0.9), shade(base, 1.1))

    if stripe and accent and sy >= 3:
        ys = v + sz + sy // 2
        for x in range(u + sz, u + sz + sx):
            px(im, x, ys, accent)
    if accent:
        px(im, u + sz, v, accent)
        px(im, u + sz + sx - 1, v + sz + sy - 1, shade(accent, 0.7))


def ent_pyro_ship():
    im = img(64, 64)
    box(im, 0, 0, 4, 4, 16, HULL_M, accent=RED, stripe=True)      # fuselage
    box(im, 40, 0, 3, 3, 3, HULL_L, accent=CYAN)                  # nose cone
    box(im, 40, 7, 3, 3, 3, (48, 40, 34, 255), accent=ORANGE)     # thruster
    box(im, 0, 20, 8, 1, 6, HULL_D, accent=RED)                   # wing L
    box(im, 0, 27, 8, 1, 6, HULL_D, accent=RED)                   # wing R
    box(im, 28, 20, 1, 5, 4, HULL_L, accent=CYAN)                 # fin
    # Cockpit glass on the fuselage top face.
    for x in range(6, 12):
        for y in range(1, 3):
            px(im, x, y, shade(CYAN, 0.75))
    px(im, 7, 1, CYAN_L)
    # Thruster bell glows on its front face.
    for x in range(43, 46):
        for y in range(10, 13):
            px(im, x, y, ORANGE)
    return im


def ent_drone():
    im = img(64, 64)
    box(im, 0, 0, 6, 6, 6, HULL_D, accent=RED, stripe=True)       # core
    box(im, 0, 12, 2, 2, 5, HULL_M, accent=RED)                   # spars N/S
    box(im, 16, 12, 5, 2, 2, HULL_M, accent=RED)                  # spars E/W
    box(im, 0, 20, 3, 3, 2, (52, 22, 22, 255), accent=RED_L)      # eye
    # Sensor lens on the eye front face.
    for x in range(2, 5):
        for y in range(22, 25):
            px(im, x, y, RED_L)
    px(im, 3, 23, WHITE)
    # Hazard chevrons on the core front face.
    for x in range(6, 12):
        px(im, x, 9, AMBER if (x % 2) else shade(AMBER, 0.5))
    return im


def ent_reactor_core():
    im = img(64, 64)
    box(im, 0, 0, 8, 8, 8, (46, 30, 22, 255), accent=ORANGE, stripe=True)  # core
    box(im, 0, 16, 16, 2, 16, HULL_D, accent=AMBER)                        # ring
    box(im, 0, 34, 2, 20, 2, HULL_M, accent=AMBER)                         # pillar
    # Molten core face.
    for x in range(8, 16):
        for y in range(8, 16):
            d = ((x - 11.5) ** 2 + (y - 11.5) ** 2) ** 0.5
            if d < 3.6:
                px(im, x, y, shade(ORANGE, 1.15 - d * 0.09))
    px(im, 11, 11, AMBER)
    return im


def hazard(im, x0, y0, x1, y1, a, b):
    """Diagonal warning stripes — the roster's shared 'this thing shoots' cue."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            px(im, x, y, a if ((x + y) // 2) % 2 == 0 else b)


def ent_tripod():
    """128×128 — cubic hull, sensor turret, plasma lance, one shared leg." """
    im = img(128, 128)
    box(im, 0, 0, 18, 18, 18, HULL_D, accent=PINK, stripe=True)     # hull
    box(im, 72, 0, 10, 7, 10, HULL_M, accent=CYAN)                  # sensor turret
    box(im, 72, 17, 3, 3, 12, (58, 44, 74, 255), accent=VIOLET)     # plasma lance
    box(im, 0, 36, 5, 26, 5, HULL_M, accent=PINK)                   # leg
    box(im, 20, 36, 7, 3, 7, HULL_L, accent=AMBER)                  # hip / foot pad
    # Hull front face: armoured plate with a neon core slit and hazard banding.
    for x in range(18, 36):
        for y in range(18, 36):
            px(im, x, y, shade(HULL_D, 0.92))
    hazard(im, 18, 19, 36, 22, PINK, shade(HULL_D, 0.7))
    for x in range(22, 32):
        for y in range(26, 30):
            px(im, x, y, shade(VIOLET, 1.0))
    px(im, 26, 27, WHITE)
    px(im, 27, 27, WHITE)
    # Sensor eye on the turret's front face.
    for x in range(82, 92):
        for y in range(10, 17):
            px(im, x, y, shade(CYAN, 0.55))
    for x in range(85, 89):
        px(im, x, 13, CYAN_L)
    # Lance muzzle glows.
    for x in range(75, 78):
        for y in range(29, 32):
            px(im, x, y, VIOLET)
    return im


def ent_scanner():
    """64×64 — sensor core, detector ring, three rocket pods."""
    im = img(64, 64)
    box(im, 0, 0, 8, 8, 8, HULL_D, accent=CYAN, stripe=True)        # core
    box(im, 32, 0, 4, 4, 2, (24, 46, 58, 255), accent=CYAN_L)       # eye
    box(im, 0, 16, 16, 2, 16, HULL_M, accent=CYAN)                  # ring
    box(im, 0, 34, 3, 3, 6, (56, 34, 26, 255), accent=ORANGE)       # rocket pod
    box(im, 18, 34, 1, 5, 1, HULL_L, accent=CYAN)                   # antenna
    # Big single lens on the eye's front face.
    for x in range(34, 38):
        for y in range(2, 6):
            px(im, x, y, CYAN_L)
    px(im, 35, 3, WHITE)
    # Ring top face gets scan segments so the spin reads.
    for x in range(16, 32):
        if x % 3 == 0:
            for y in range(16, 32):
                if (x + y) % 5 == 0:
                    px(im, x, y, shade(CYAN, 0.9))
    # Pod nose is the warhead.
    for x in range(6, 9):
        for y in range(40, 43):
            px(im, x, y, ORANGE)
    return im


def ent_spider_turret():
    """64×64 — chassis, tracking head, MG barrel, laser emitter, one shared leg."""
    im = img(64, 64)
    box(im, 0, 0, 10, 4, 10, HULL_D, accent=AMBER, stripe=True)     # base
    box(im, 0, 14, 8, 6, 8, HULL_M, accent=AMBER)                   # head
    box(im, 0, 28, 2, 2, 8, (46, 40, 34, 255), accent=AMBER)        # MG barrel
    box(im, 20, 28, 3, 3, 4, (26, 46, 52, 255), accent=CYAN)        # laser emitter
    box(im, 0, 38, 2, 10, 2, HULL_M, accent=AMBER)                  # leg
    box(im, 8, 38, 3, 3, 3, HULL_L, accent=AMBER)                   # knee
    # Head front face: optics band.
    for x in range(8, 16):
        for y in range(22, 28):
            px(im, x, y, shade(HULL_D, 1.0))
    for x in range(9, 15):
        px(im, x, 24, AMBER)
        px(im, x, 25, shade(AMBER, 0.55))
    hazard(im, 10, 0, 20, 2, AMBER, shade(HULL_D, 0.7))
    # Muzzle + emitter lens.
    for y in range(30, 32):
        px(im, 2, y, AMBER)
        px(im, 3, y, shade(AMBER, 0.8))
    for x in range(23, 26):
        for y in range(31, 34):
            px(im, x, y, CYAN_L)
    return im


def panel_skin(size, base, seam, accent, rivets=True, glow_rows=()):
    """
    Square skin for the procedurally-built giants.

    Their renderers stretch (0,0)-(1,1) over every face and multiply by a vertex tint, so this
    stays close to neutral value and lets the tint carry the hue.
    """
    im = img(size, size)
    for y in range(size):
        for x in range(size):
            k = 1.0 + ((x * 7 + y * 13) % 5 - 2) * 0.03
            px(im, x, y, shade(base, k))
    step = size // 4
    for i in range(0, size, step):
        for x in range(size):
            px(im, x, i, seam)
            px(im, i, x, seam)
    if rivets:
        for gy in range(step // 2, size, step):
            for gx in range(step // 2, size, step):
                px(im, gx, gy, accent)
    for row in glow_rows:
        y = int(row * size)
        for x in range(size):
            if (x // 2) % 2 == 0:
                px(im, x, y, accent)
                if y + 1 < size:
                    px(im, x, y + 1, shade(accent, 0.5))
    return im


def ent_mega_worm():
    """Segmented chitin plating with magenta seam glow."""
    im = panel_skin(32, (54, 46, 44, 255), (28, 22, 22, 255), PINK, glow_rows=(0.45,))
    for x in range(32):
        for y in range(32):
            if (x + y) % 9 == 0:
                px(im, x, y, shade(RUST, 1.0))
    return im


def ent_drone_swarm():
    """Translucent haze so the swarm anchor reads as a cloud, not a solid block."""
    im = img(32, 32)
    for y in range(32):
        for x in range(32):
            d = ((x - 16) ** 2 + (y - 16) ** 2) ** 0.5
            a = max(0, int(150 - d * 7))
            px(im, x, y, (200, 60, 70, a))
    for i in range(0, 32, 5):
        for j in range(0, 32, 5):
            px(im, (i * 3 + j) % 32, (j * 2 + i) % 32, (255, 120, 120, 220))
    return im


def ent_reactor_keeper():
    return panel_skin(32, (86, 104, 110, 255), (34, 46, 52, 255), CYAN, glow_rows=(0.25, 0.75))


def ent_end_reactor_boss():
    return panel_skin(32, (52, 42, 74, 255), (24, 18, 36, 255), VIOLET, glow_rows=(0.5,))


def ent_sky_ufo():
    return panel_skin(32, (74, 110, 106, 255), (30, 48, 46, 255), CYAN, glow_rows=(0.35, 0.65))


def ent_air_mine():
    """Mine casing: hazard banding over a dark shell."""
    im = panel_skin(16, (44, 40, 38, 255), (22, 18, 18, 255), AMBER, rivets=False)
    hazard(im, 0, 6, 16, 10, AMBER, (30, 24, 22, 255))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px(im, x, y, RED_L)
    return im


ENTITIES = {
    "pyro_ship": ent_pyro_ship,
    "drone": ent_drone,
    "reactor_core": ent_reactor_core,
    "tripod": ent_tripod,
    "scanner": ent_scanner,
    "spider_turret": ent_spider_turret,
    "mega_worm": ent_mega_worm,
    "drone_swarm": ent_drone_swarm,
    "reactor_keeper": ent_reactor_keeper,
    "end_reactor_boss": ent_end_reactor_boss,
    "sky_ufo": ent_sky_ufo,
    "air_mine": ent_air_mine,
}


def build_entities(outdir):
    for name, fn in ENTITIES.items():
        fn().save(os.path.join(outdir, name + ".png"))
    return len(ENTITIES)


def build_icon():
    """Mod icon — Pyro over a reactor glow."""
    im = img(128, 128)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 127, 127], fill=(12, 18, 24, 255))
    for y in range(128):
        for x in range(128):
            dist = ((x - 64) ** 2 + (y - 70) ** 2) ** 0.5
            if dist < 54:
                k = 1.0 - dist / 54.0
                px(im, x, y, (int(10 + 40 * k), int(20 + 90 * k), int(24 + 60 * k), 255))
    ship = img()
    sh_ship(ship, HULL_L, CYAN)
    outline(ship)
    im.alpha_composite(ship.resize((96, 96), Image.NEAREST), (16, 16))
    for x in range(128):
        for t in (0, 1, 126, 127):
            px(im, x, t, (26, 255, 122, 255))
            px(im, t, x, (26, 255, 122, 255))
    return im


def main():
    items = os.path.join(TEX, "item")
    blocks = os.path.join(TEX, "block")
    entity = os.path.join(TEX, "entity")
    for p in (items, blocks, entity):
        os.makedirs(p, exist_ok=True)
    ni = build_items(items)
    nb = build_blocks(blocks)
    ne = build_entities(entity)
    build_icon().save(os.path.join(TEX, "..", "icon.png"))
    print(f"items={ni} blocks={nb} entities={ne} icon=1")


if __name__ == "__main__":
    main()
