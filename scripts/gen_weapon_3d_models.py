#!/usr/bin/env python3
"""Generate Blockbench-style 3D item models for flat DRMD weapons (not plane sprites)."""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
					"assets", "drmd", "models", "item")

DISPLAY = {
	"thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
	"thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
	"firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
	"firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
	"ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
	"gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [1.0, 1.0, 1.0]},
	"fixed": {"rotation": [0, 180, 0], "translation": [0, 2, 0], "scale": [1.0, 1.0, 1.0]},
}


def face(tex="#0"):
	return {s: {"uv": [0, 0, 16, 16], "texture": tex} for s in ("north", "south", "east", "west", "up", "down")}


def box(x0, y0, z0, x1, y1, z1):
	return {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": face()}


def gun_stock_barrel(long_barrel=True, dual=False, heavy=False, orb=False, claw=False, mine=False, exotic=False):
	els = [box(7, 0, 9, 9, 4, 11)]
	if heavy:
		els.append(box(6, 4, 6, 10, 8, 12))
	else:
		els.append(box(6.5, 4, 7, 9.5, 7.5, 12))
	if long_barrel:
		z0 = 0 if not dual else 1
		els.append(box(7.2, 5, z0, 8.8, 7, 7))
		els.append(box(7.4, 5.2, -0.5, 8.6, 6.8, z0 + 0.2))
	if dual:
		els.append(box(5.2, 5.2, 1, 6.8, 6.8, 7))
		els.append(box(9.2, 5.2, 1, 10.8, 6.8, 7))
	if orb:
		els.append(box(6, 7, 8, 10, 11, 12))
	if claw:
		els.append(box(5, 5, 4, 6.5, 8, 8))
		els.append(box(9.5, 5, 4, 11, 8, 8))
		els.append(box(6.5, 3.5, 5, 9.5, 5, 7))
	if mine:
		els.append(box(5.5, 6, 8, 10.5, 10, 13))
		els.append(box(7, 10, 9.5, 9, 12, 11.5))
	if exotic:
		els.append(box(7.5, 7.5, 2, 8.5, 10, 10))
		els.append(box(6, 8, 9, 10, 9.5, 14))
	return els


TEMPLATES = {
	"mg": dict(long_barrel=True),
	"plasma": dict(long_barrel=True, dual=True),
	"vulcan": dict(long_barrel=True, dual=True, heavy=True),
	"flak": dict(long_barrel=False, heavy=True),
	"gravy_railgun": dict(long_barrel=True, claw=True),
	"railmk2": dict(long_barrel=True, heavy=True),
	"darklance": dict(long_barrel=True, exotic=True),
	"darkfield": dict(orb=True, long_barrel=False),
	"energytrap": dict(orb=True, mine=True, long_barrel=False),
	"gravmine": dict(mine=True, long_barrel=False),
	"plasmamine": dict(mine=True, dual=True, long_barrel=False),
	"reactor": dict(orb=True, heavy=True, long_barrel=False),
	"shockwave": dict(orb=True, heavy=True, long_barrel=False),
	"warp": dict(exotic=True, long_barrel=False),
	"telefrag": dict(exotic=True, heavy=True, long_barrel=False),
	"whiplash": dict(long_barrel=True, exotic=True),
}


def main():
	root = os.path.abspath(ROOT)
	n = 0
	for name in sorted(os.listdir(root)):
		if not name.startswith("weapon_d6_") or not name.endswith(".json"):
			continue
		path = os.path.join(root, name)
		data = json.load(open(path))
		if "elements" in data:
			continue
		wid = name[len("weapon_d6_"):-5]
		tex = f"drmd:item/weapon_d6_{wid}"
		kwargs = TEMPLATES.get(wid, dict(long_barrel=True))
		model = {
			"parent": "minecraft:item/handheld",
			"textures": {"0": tex, "particle": tex},
			"elements": gun_stock_barrel(**kwargs),
			"display": DISPLAY,
		}
		with open(path, "w") as f:
			json.dump(model, f, indent=2)
			f.write("\n")
		print("3D", wid)
		n += 1
	print("wrote", n)


if __name__ == "__main__":
	main()
