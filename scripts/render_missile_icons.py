#!/usr/bin/env python3
"""Bake the inventory sprites from the authored missile mesh and its material palette."""

import json
from math import sqrt
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/drmd"
MODEL = ASSETS / "models/projectile"
ICONS = ASSETS / "textures/item/missile"
SIZE = 48
SUPERSAMPLE = 4


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def unit(vector):
    length = sqrt(dot(vector, vector))
    return tuple(value / length for value in vector)


def load_meshes():
    vertices = []
    meshes = {}
    for line in (MODEL / "missile.obj").read_text().splitlines():
        match line.split():
            case ["v", x, y, z]:
                vertices.append(tuple(map(float, (x, y, z))))
            case ["o", name]:
                faces = meshes.setdefault(name, [])
            case ["usemtl", material]:
                active_material = material
            case ["f", *indices]:
                face = [vertices[int(index.split("/")[0]) - 1] for index in indices]
                faces.append((active_material, face))
    return meshes


def render(variant, mesh):
    eye = unit((.57, .81, .43))
    right = unit(cross((0, 1, 0), eye))
    up = cross(eye, right)
    sun = unit((-.31, .88, .43))
    points = []
    visible = []
    offsets = (-.76, .76) if variant.get("pair") else (0,)
    for offset in offsets:
        for material, corners in mesh:
            a, b, c = corners[:3]
            normal = unit(cross(tuple(y - x for x, y in zip(a, b)),
                                tuple(z - x for x, z in zip(a, c))))
            if dot(normal, eye) <= 0:
                continue
            placed = [(x + offset, y, z + .27 * offset) for x, y, z in corners]
            xy = [(dot(xyz, right), -dot(xyz, up)) for xyz in placed]
            points.extend(xy)
            depth = sum(dot(xyz, eye) for xyz in placed) / len(placed)
            tint = bytes.fromhex(variant["materials"][material])
            shade = .56 + .39 * max(0, dot(normal, sun))
            color = tuple(round(component * shade) for component in tint)
            visible.append((depth, xy, color))

    min_x, min_y = map(min, zip(*points))
    max_x, max_y = map(max, zip(*points))
    side = SIZE * SUPERSAMPLE
    scale = (SIZE - 8) * SUPERSAMPLE / max(max_x - min_x, max_y - min_y)
    mx, my = (min_x + max_x) / 2, (min_y + max_y) / 2
    image = Image.new("RGBA", (side, side))
    draw = ImageDraw.Draw(image)
    for _, xy, color in sorted(visible, key=lambda face: face[0]):
        polygon = [(side / 2 + (x - mx) * scale, side / 2 + (y - my) * scale) for x, y in xy]
        draw.polygon(polygon, fill=color + (255,))
    outline = Image.new("RGBA", (side, side), (17, 21, 22))
    outline.putalpha(image.getchannel("A").filter(ImageFilter.MaxFilter(SUPERSAMPLE * 2 - 1)))
    outline.alpha_composite(image)
    return outline.resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def main():
    meshes = load_meshes()
    catalogue = json.loads((MODEL / "missile.json").read_text())
    ICONS.mkdir(parents=True, exist_ok=True)
    for variant in catalogue["variants"].values():
        render(variant, meshes[variant["mesh"]]).save(ICONS / (variant["item"] + ".png"), optimize=True)


if __name__ == "__main__":
    main()
