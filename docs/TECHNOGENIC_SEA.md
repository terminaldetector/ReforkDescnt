# Technogenic sea — Spark-style locators

Biome plate `drmd:technogenic_sea`: industrial towers with satellite dishes on the water, a Mega Locator (a tower you can see from far off), a network of smaller locators, and an underwater 6DoF dungeon.

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
| **Mega Locator** | Tower + dish with `locator_core` / panel / resonators · `MacroWorld` landmark |
| **Locator ×5** | Smaller dish towers (`locator_resonator`) around the mega |
| **Subsea dungeon** | Territory-picked style (`SUBSEA_LOCATOR` / `SIGNAL_ARRAY` / `HOLLOW_RING`) under the mega |
| **Signal satellite** | Second underwater 6DoF node nearby |

## Commands

- `/d6 technogenic` — nearest plate
- `/d6 technogenic tp` — OP teleport
- `/d6 worldgen2 locator` — place a locator to compare against the plate's own

## Notes

Not spawned at the lunar hub. Needs a fresh world (or unexplored plate chunks).
