# Technogenic sea — Spark-style locators

Biome plate `drmd:technogenic_sea`: industrial towers with satellite dishes on the water, a Mega Locator (voxel LLOD on the horizon), a network of smaller locators, and an underwater 6DoF dungeon.

Inspired by *Spark the Electric Jester* seascapes.

## Placement

| | |
|--|--|
| Biome id | `drmd:technogenic_sea` |
| Grid | ~4096 · ~1/5 cells · r=220 · ≥1800 m from spawn |
| Inject | `MultiNoiseBiomeSourceMixin` |
| Structure | Queued when a plate chunk loads |

## Contents

| Landmark | Role |
|----------|------|
| **Mega Locator** | Tall deepslate tower + iron parabolic dish · `MacroWorld` / voxel LLOD silhouette |
| **Locator ×5** | Smaller dish towers around the mega |
| **Subsea dungeon** | `SUBSEA_LOCATOR` industrial complex under the mega — flyable cavities |

## Commands

- `/d6 technogenic` — nearest plate
- `/d6 technogenic tp` — OP teleport
- `/d6 llod` — confirm Mega Locator silhouette on the horizon

## Notes

Not spawned at the lunar hub. Needs a fresh world (or unexplored plate chunks).
