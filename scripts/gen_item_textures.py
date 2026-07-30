#!/usr/bin/env python3
"""Generate 16×16 DRMD item icons + refresh item model JSONs. Requires Pillow."""
from __future__ import annotations

import json
import os
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src/main/resources/assets/drmd/textures/item"
MODEL = ROOT / "src/main/resources/assets/drmd/models/item"
TEX.mkdir(parents=True, exist_ok=True)
MODEL.mkdir(parents=True, exist_ok=True)


def new() -> Image.Image:
	return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def px(img: Image.Image, x: int, y: int, c: tuple[int, int, int, int]) -> None:
	if 0 <= x < 16 and 0 <= y < 16:
		img.putpixel((x, y), c)


def fill(img: Image.Image, x0: int, y0: int, x1: int, y1: int, c: tuple[int, int, int, int]) -> None:
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			px(img, x, y, c)


def outline(img: Image.Image, x0: int, y0: int, x1: int, y1: int, c: tuple[int, int, int, int]) -> None:
	for x in range(x0, x1 + 1):
		px(img, x, y0, c)
		px(img, x, y1, c)
	for y in range(y0, y1 + 1):
		px(img, x0, y, c)
		px(img, x1, y, c)


def shade(c: tuple[int, int, int, int], f: float) -> tuple[int, int, int, int]:
	r, g, b, a = c
	return (max(0, min(255, int(r * f))), max(0, min(255, int(g * f))), max(0, min(255, int(b * f))), a)


def rgba(c: tuple[int, int, int], a: int = 255) -> tuple[int, int, int, int]:
	return (c[0], c[1], c[2], a)


def save(img: Image.Image, name: str) -> None:
	img.save(TEX / f"{name}.png", "PNG")


def bomb_body(img, body, stripe, fin, tip=None):
	fill(img, 4, 1, 5, 3, fin)
	fill(img, 10, 1, 11, 3, fin)
	fill(img, 6, 1, 9, 2, shade(fin, 0.7))
	fill(img, 5, 3, 10, 11, body)
	fill(img, 6, 3, 9, 11, shade(body, 1.15))
	fill(img, 5, 3, 5, 11, shade(body, 0.7))
	fill(img, 10, 3, 10, 11, shade(body, 0.55))
	fill(img, 5, 6, 10, 7, stripe)
	t = tip or shade(body, 0.5)
	fill(img, 6, 12, 9, 13, t)
	fill(img, 7, 14, 8, 14, shade(t, 0.8))
	outline(img, 5, 3, 10, 11, shade(body, 0.4))


def gen_sector_a():
	img = new()
	bomb_body(img, (55, 55, 55, 255), (180, 40, 40, 255), (40, 40, 40, 255), (30, 30, 30, 255))
	fill(img, 6, 5, 9, 5, (220, 220, 220, 255))
	save(img, "bomb_tnt")

	img = new()
	bomb_body(img, (70, 55, 35, 255), (200, 140, 40, 255), (50, 40, 25, 255))
	for ox in (3, 11):
		fill(img, ox, 7, ox + 1, 10, (90, 70, 40, 255))
		px(img, ox, 11, (160, 80, 30, 255))
	save(img, "bomb_cluster")

	img = new()
	bomb_body(img, (90, 35, 20, 255), (255, 120, 30, 255), (60, 25, 15, 255), (255, 180, 40, 255))
	px(img, 7, 13, (255, 220, 80, 255))
	px(img, 8, 13, (255, 160, 40, 255))
	px(img, 6, 14, (255, 100, 20, 200))
	px(img, 9, 14, (255, 80, 10, 200))
	save(img, "bomb_incendiary")

	img = new()
	bomb_body(img, (45, 55, 70, 255), (60, 255, 140, 255), (30, 40, 50, 255), (80, 255, 160, 255))
	fill(img, 6, 4, 9, 5, (120, 255, 180, 255))
	px(img, 7, 4, (220, 255, 230, 255))
	px(img, 8, 4, (40, 200, 100, 255))
	save(img, "bomb_guided")

	img = new()
	fill(img, 3, 9, 5, 14, (50, 50, 55, 255))
	fill(img, 5, 6, 11, 10, (70, 75, 85, 255))
	fill(img, 5, 6, 11, 6, (110, 120, 130, 255))
	fill(img, 11, 7, 13, 9, (40, 200, 120, 255))
	px(img, 12, 8, (180, 255, 200, 255))
	px(img, 14, 8, (80, 255, 140, 200))
	px(img, 15, 8, (100, 255, 160, 120))
	px(img, 8, 7, (60, 255, 140, 255))
	px(img, 7, 8, (60, 255, 140, 255))
	px(img, 9, 8, (60, 255, 140, 255))
	px(img, 8, 9, (60, 255, 140, 255))
	save(img, "laser_designator")


def laser_tool(name: str, beam: tuple[int, int, int, int]):
	img = new()
	body = (60, 65, 75, 255)
	fill(img, 2, 11, 4, 14, (45, 40, 35, 255))
	fill(img, 4, 8, 9, 11, body)
	fill(img, 4, 8, 9, 8, shade(body, 1.3))
	fill(img, 9, 8, 11, 10, shade(body, 0.7))
	fill(img, 11, 7, 12, 11, beam)
	for i in range(3):
		px(img, 13 + i, 8 + i % 2, (*beam[:3], 200 - i * 50))
	save(img, name)


def gen_tools_ship():
	laser_tool("construction_laser", (80, 200, 255, 255))
	laser_tool("repair_laser", (80, 255, 140, 255))
	laser_tool("mining_laser", (255, 160, 40, 255))

	img = new()
	fill(img, 6, 2, 9, 3, (90, 100, 140, 255))
	fill(img, 4, 4, 11, 8, (70, 80, 120, 255))
	fill(img, 5, 5, 10, 7, (120, 160, 255, 255))
	px(img, 7, 6, (220, 240, 255, 255))
	fill(img, 7, 9, 8, 13, (55, 55, 65, 255))
	fill(img, 5, 13, 10, 14, (40, 40, 50, 255))
	save(img, "gravity_scanner")

	img = new()
	fill(img, 7, 2, 8, 12, (90, 95, 105, 255))
	fill(img, 4, 3, 11, 5, (70, 160, 220, 255))
	fill(img, 6, 12, 9, 14, (50, 45, 40, 255))
	px(img, 3, 4, (255, 80, 80, 255))
	px(img, 12, 4, (80, 255, 80, 255))
	save(img, "build_tool")

	img = new()
	fill(img, 3, 6, 12, 9, (50, 110, 200, 255))
	fill(img, 3, 6, 12, 6, (100, 170, 240, 255))
	fill(img, 12, 7, 14, 8, (80, 150, 230, 255))
	fill(img, 5, 4, 9, 5, (40, 90, 170, 255))
	fill(img, 5, 10, 9, 11, (40, 90, 170, 255))
	fill(img, 1, 7, 2, 8, (255, 160, 40, 255))
	fill(img, 9, 7, 10, 8, (180, 240, 255, 255))
	save(img, "pyro_gx")


def draw_gun(img, metal, accent):
	m, a = rgba(metal), rgba(accent)
	fill(img, 2, 10, 4, 13, shade(m, 0.6))
	fill(img, 4, 7, 12, 10, m)
	fill(img, 4, 7, 12, 7, shade(m, 1.25))
	fill(img, 12, 8, 14, 9, shade(m, 0.8))
	fill(img, 6, 6, 8, 6, a)


def draw_orb(img, core, glow):
	c, g = rgba(core), rgba(glow)
	for y in range(4, 12):
		for x in range(4, 12):
			dx, dy = x - 7.5, y - 7.5
			if dx * dx + dy * dy <= 16:
				px(img, x, y, shade(c, 1.2 - (dx * dx + dy * dy) / 20))
	fill(img, 6, 5, 8, 6, g)


def draw_cannon(img, metal, accent):
	m, a = rgba(metal), rgba(accent)
	fill(img, 2, 9, 5, 13, shade(m, 0.7))
	fill(img, 5, 5, 13, 11, m)
	fill(img, 7, 7, 10, 9, a)


def draw_beam(img, beam, tip):
	b = rgba(beam)
	fill(img, 2, 10, 4, 13, (50, 50, 55, 255))
	fill(img, 4, 7, 8, 10, (70, 70, 80, 255))
	fill(img, 8, 8, 15, 9, b)


def draw_rocket(img, body, flame):
	b, f = rgba(body), rgba(flame)
	fill(img, 6, 2, 9, 11, b)
	fill(img, 5, 4, 5, 7, shade(b, 0.7))
	fill(img, 10, 4, 10, 7, shade(b, 0.7))
	fill(img, 6, 12, 9, 13, f)


def draw_fork(img, metal, field):
	m, f = rgba(metal), rgba(field)
	fill(img, 7, 8, 8, 14, shade(m, 0.7))
	fill(img, 4, 4, 11, 7, m)
	fill(img, 3, 2, 4, 6, m)
	fill(img, 11, 2, 12, 6, m)
	fill(img, 5, 5, 10, 6, f)


def draw_burst(img, shell, spark):
	s, k = rgba(shell), rgba(spark)
	fill(img, 5, 5, 10, 10, s)
	for x, y in [(3, 4), (12, 4), (3, 11), (12, 11), (7, 2), (8, 13)]:
		px(img, x, y, k)


def draw_quad(img, beam, tip):
	b = rgba(beam)
	fill(img, 5, 5, 10, 10, (60, 60, 70, 255))
	for ox, oy in [(4, 4), (10, 4), (4, 10), (10, 10)]:
		fill(img, ox, oy, ox + 1, oy + 1, b)


def draw_rail(img, metal, glow):
	m, g = rgba(metal), rgba(glow)
	fill(img, 2, 7, 14, 9, m)
	fill(img, 4, 7, 12, 9, g)
	fill(img, 1, 11, 3, 13, shade(m, 0.7))


def draw_ring(img, rim, core):
	r, c = rgba(rim), rgba(core)
	for y in range(3, 13):
		for x in range(3, 13):
			d2 = (x - 7.5) ** 2 + (y - 7.5) ** 2
			if 9 <= d2 <= 25:
				px(img, x, y, r)
			elif d2 < 9:
				px(img, x, y, (*core[:3], 180))


def draw_lance(img, shaft, tip):
	s, t = rgba(shaft), rgba(tip)
	fill(img, 7, 3, 8, 13, s)
	fill(img, 6, 1, 9, 3, t)


def draw_trap(img, frame, energy):
	f, e = rgba(frame), rgba(energy)
	outline(img, 3, 3, 12, 12, f)
	outline(img, 5, 5, 10, 10, e)


def draw_mine(img, shell, core):
	s, c = rgba(shell), rgba(core)
	fill(img, 4, 5, 11, 11, s)
	fill(img, 5, 4, 10, 12, s)
	fill(img, 6, 6, 9, 9, c)


DRAW = {
	"gun": draw_gun, "orb": draw_orb, "cannon": draw_cannon, "beam": draw_beam,
	"rocket": draw_rocket, "fork": draw_fork, "burst": draw_burst, "quad": draw_quad,
	"rail": draw_rail, "ring": draw_ring, "lance": draw_lance, "trap": draw_trap, "mine": draw_mine,
}

WEAPONS = {
	"weapon_d6_mg": ("gun", (180, 180, 190), (220, 180, 60)),
	"weapon_d6_plasma": ("orb", (40, 200, 255), (180, 255, 255)),
	"weapon_d6_heavy": ("cannon", (90, 90, 100), (255, 120, 40)),
	"weapon_d6_laser": ("beam", (255, 60, 60), (255, 180, 180)),
	"weapon_d6_rockets": ("rocket", (70, 70, 80), (255, 100, 40)),
	"weapon_d6_gravy_railgun": ("fork", (120, 80, 200), (200, 160, 255)),
	"weapon_d6_vulcan": ("gun", (200, 120, 40), (255, 200, 80)),
	"weapon_d6_flak": ("burst", (160, 140, 60), (255, 220, 80)),
	"weapon_d6_homing": ("rocket", (60, 140, 80), (80, 255, 140)),
	"weapon_d6_concussion": ("rocket", (100, 100, 120), (200, 200, 255)),
	"weapon_d6_smart_missile": ("rocket", (40, 160, 180), (80, 255, 220)),
	"weapon_d6_mega_missile": ("rocket", (120, 40, 40), (255, 60, 60)),
	"weapon_d6_quad_laser": ("quad", (255, 40, 80), (255, 160, 180)),
	"weapon_d6_railmk2": ("rail", (100, 120, 160), (180, 220, 255)),
	"weapon_d6_bfg": ("orb", (80, 255, 80), (200, 255, 120)),
	"weapon_d6_frag": ("burst", (160, 100, 40), (255, 160, 60)),
	"weapon_d6_overdrive": ("beam", (255, 200, 40), (255, 255, 160)),
	"weapon_d6_shockwave": ("ring", (80, 160, 255), (180, 220, 255)),
	"weapon_d6_darklance": ("lance", (60, 20, 90), (180, 80, 255)),
	"weapon_d6_darkfield": ("ring", (40, 20, 60), (140, 60, 200)),
	"weapon_d6_energytrap": ("trap", (40, 180, 200), (120, 255, 255)),
	"weapon_d6_gravmine": ("mine", (100, 60, 180), (180, 120, 255)),
	"weapon_d6_plasmamine": ("mine", (30, 160, 200), (100, 240, 255)),
	"weapon_d6_reactor": ("orb", (255, 80, 40), (255, 200, 80)),
	"weapon_d6_warp": ("ring", (100, 60, 255), (200, 160, 255)),
	"weapon_d6_telefrag": ("burst", (255, 40, 160), (255, 160, 220)),
	"weapon_d6_whiplash": ("lance", (200, 160, 40), (255, 220, 100)),
}


def gen_weapons():
	for name, (kind, metal, accent) in WEAPONS.items():
		img = new()
		DRAW[kind](img, metal, accent)
		save(img, name)


def gen_eggs():
	eggs = {
		"spawn_egg_assault": ((204, 51, 51), (68, 34, 34)),
		"spawn_egg_interceptor": ((51, 170, 204), (34, 68, 85)),
		"spawn_egg_artillery": ((204, 170, 51), (85, 68, 34)),
		"spawn_egg_support": ((51, 204, 102), (34, 68, 51)),
		"spawn_egg_heavy_elite": ((170, 51, 204), (51, 17, 68)),
		"spawn_egg_mg": ((136, 136, 136), (51, 51, 51)),
		"spawn_egg_laser": ((255, 85, 85), (85, 17, 17)),
		"spawn_egg_rpg": ((204, 119, 51), (68, 34, 17)),
		"spawn_egg_heavy": ((85, 85, 119), (34, 34, 51)),
		"spawn_egg_seeker": ((85, 255, 170), (17, 85, 51)),
	}
	for name, (light, dark) in eggs.items():
		img = new()
		L, D = rgba(light), rgba(dark)
		for y in range(2, 14):
			for x in range(4, 12):
				dx = (x - 7.5) / 4.2
				dy = (y - 7.5) / 5.5
				if dx * dx + dy * dy <= 1:
					c = L if y < 8 else D
					if abs(dx) > 0.75 or abs(dy) > 0.8:
						c = shade(c, 0.75)
					px(img, x, y, c)
		save(img, name)


HANDHELD = {
	"build_tool", "construction_laser", "repair_laser", "mining_laser",
	"gravity_scanner", "laser_designator",
}


def write_models():
	for png in TEX.glob("*.png"):
		name = png.stem
		parent = "minecraft:item/handheld" if (name in HANDHELD or name.startswith("weapon_d6_")) else "minecraft:item/generated"
		data = {"parent": parent, "textures": {"layer0": f"drmd:item/{name}"}}
		(MODEL / f"{name}.json").write_text(json.dumps(data, indent=2) + "\n")


def main():
	gen_sector_a()
	gen_tools_ship()
	gen_weapons()
	gen_eggs()
	write_models()
	print(f"Generated {len(list(TEX.glob('*.png')))} textures + models → {TEX}")


if __name__ == "__main__":
	main()
