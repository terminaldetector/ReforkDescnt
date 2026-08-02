# Закрытый арсенал Descent (MC release)

Всё, что не входит в этот список, считается placeholder / retired: предмет может остаться в реестре для сейвов, но **не показывается** в креативе и не выдаётся `/d6 weapons give_all`.

## 1. Лазерные установки (5) — dual/quad bolts с модулей

| id | Поведение |
|----|-----------|
| `laser` | Descent dual bolts, LASER LVL 1–4 |
| `laser_pulse` | быстрые слабые dual bolts |
| `quad_laser` | 4 module banks |
| `mega_laser` | толстые dual bolts |
| `laser_prism` | 3-way fan, converge |

## 2. Бластеры

| id | Поведение |
|----|-----------|
| `spread` | Spreadfire — конус кинетических пуль |
| `fusion` | зарядный орб, 3D mesh |
| `vulcan` | зелёный MG со всех модулей |
| `gatling` | скоростной турельный огонь |
| `plasma` | dual orbs |

## 3. Ракеты (6 весов)

`rocket_light` → `rocket_offense` → `rocket_dual` → `rocket_triple` → `rocket_heavy` → `rocket_mega`

## 4. Буровые (инженерка, поведение прежнее)

| Тир | Предмет |
|-----|---------|
| слабая | `mining_laser` |
| средняя | `tunnel_laser` |
| сильная | `drill_rig` |
| тяжёлая | `tunnel_drill_rig` |

## 5. Уникальное

| id | Поведение |
|----|-----------|
| `bfg` | крупный орб + chain |
| `beam_lance` | большой travel-time луч-снаряд |
| `warp` | телепорт + splash |

## 6. Воздушные мины (4) — без гравитационных

`mine_prox` · `mine_plasma` · `mine_energy` · `mine_smart` — proximity fuse, зависают (`gravity=0`).

## 7. Бомбоотсек (оставляем)

| id | Тип |
|----|-----|
| `bomb_tnt` | HE |
| `bomb_cluster` | кассета |
| `bomb_heavy_cluster` | тяжёлая кассета |
| `bomb_rocket` | реактивная |
| `bomb_incendiary` | зажигательная |
| `bomb_guided` | лазерная |
| `laser_designator` | метка |

Физика 360°: выброс с ship F/U, гравитация/стабилизация по local DOWN владельца, трассеры aft-of-velocity, cluster probe вдоль падения, designator по `WeaponCore.aimDir`.

## 8. Прочее в scope

- **Shield / Energy** — пулы как в Descent; орбы `shield_orb` / `energy_orb_pickup` дропаются с дронов и CyberMobs; автоподбор в **LootField** (r≈6.5) при 6DoF.
- **Строительный лазер** — блок только из **левой руки**, размещение по ship-aim / local UP.
- **HUD** — WEAPONS панель по реальному арсеналу; миникарта + полоса RKT/MINE/NRG; wireframe TerrainMap3d (Tab+H).

## Retired (не в креативе)

`heavy`, `gravy_railgun`, `railmk2`, `reactor`, `telefrag`, `whiplash`, и старые имена после ремапа (ассеты сохранены под legacy item id).
