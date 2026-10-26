# Roadmap — после порта

Приоритеты после того, как мод уже «играбельный продукт» в Minecraft.

---

## 1. Havok-подобная физика для гравипушки

В GMod гравипушка/physics gun опирается на **Havok**: захват prop, масса, импульс, удержание на луче, бросок.

В Minecraft сейчас `gravy_railgun` упрощён до кинетического выстрела из пула gravy-energy. Нужен отдельный слой:

| Компонент | Задача |
|-----------|--------|
| `GravyPhysicsBody` | Обёртка над entity / falling-block / special prop-entity |
| Grab ray | Луч от камеры → захват ближайшего тела в радиусе |
| Hold spring | ПИД/пружина держит объект в точке перед прицелом (6DoF-совместимо) |
| Impulse throw | Отпускание + скорость взгляда → импульс |
| Mass / drag | Простые коэффициенты без полного rigid-body solver |
| Sync | Server-authoritative позиция/скорость захваченного тела |

Не нужен полный Havok. Нужен **ощущаемый subset**: grab → orbit around player local forward → fling. Достаточно для реакторных обломков, дронов-трупов, специальных `drmd:debris` блоков.

Файлы-якоря сегодня:
- `DescentWeaponItem.fireGravy`
- `DescentPlayerData.gravyEnergy / gravyGrabbing`
- Workshop model tag `"gravy"` в `DefaultLayouts`

---

## 2. Модели стволов (barrel / nosegun / gravy)

В GMod стволы = props (airboat gun, nosegun, physics gun). В Minecraft проще:

1. Procedural FP view уже есть (`WeaponViewRenderer`) — кластеры из примитивов.
2. Дальше — Blockbench JSON models на каждый `modelHint`:
   - `barrel` — лёгкий ствол
   - `nosegun` — тяжёлый конус
   - `gravy` — вилочный манипулятор / «клешня» гравипушки
   - `strider` / `core` — центр кластера
3. Привязка к `ClusterModule` offsets из Workshop (уже портануто).

Цель UI/UX: в FP видно **сборку стволов**, как в концепте кабины, а не один flat item sprite.

---

## 3. HUD по концепт-арту ✅ (в работе / итерации)

Концепт (зелёный тактический оверлей):

- Top-left: `6DOF MODE`, `THRUST`, `DAMPENERS`, `GRAVITY`, `SPEED`, XYZ
- Top-right: сферический 3D-радар
- Mid-left: `TARGET INFO` + wireframe + HP/dist
- Mid-right: weapons list + SHIELD / ARMOR + силуэт корабля
- Center: LOCK reticle + lead marker
- Bottom: combat log / cockpit panels
- Vanilla hearts/hunger/hotbar **сохраняются**

Реализация: `client/hud/DescentHud.java` — emerald-on-dark стиль.

---

## 4. Дальше по миру

- Полноценная высота −50k…100k (кастомный dimension / raised world height)
- Внутренние пространства mega-creatures
- Datapack placed-features вместо только chunk-load fallback
- Публикация Modrinth / CurseForge (Nexus-ready jar уже собирается CI)
- ~~Multiplayer smoke cloud sync~~ (`SmokePayload` / `DimensionSync`)

---

## 5. AI Sector A ✅

Atmosphere bands + aerial bombardment + dynamic smoke/fire LLOD — см. `docs/ATMOSPHERE_COMBAT.md`.

---

## Принцип

Minecraft даёт воксели и аудиторию. GMod дал дизайн боя и физики. Порт должен сохранить **ощущение Descent-кабины**, а недостающую физику (gravy) — добить явно, а не притворяться ванильным «ударом».
