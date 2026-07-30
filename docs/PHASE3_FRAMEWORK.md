# Phase 3 — Full 6DoF Gameplay Framework

Основная реализация: **Java / Fabric 1.21.1**.  
MCPE Fast Test Edition — отдельный экспериментальный трек (см. ниже).

---

## 1. Adaptive Construction

После высадки из **Pyro GX** (`removePassenger`) автоматически:

- включается `ConstructionMode`;
- локальный UP берётся с ближайшей твёрдой грани;
- Build Tool / Construction Laser ставят блоки по **локальной нормали** (нет абсолютного верха).

Команды: `/d6 construct` · Build Tool Shift+ПКМ = snap UP.

Файлы: `world/build/ConstructionMode`, `AdaptivePlacement`, `BuildToolItem`.

---

## 2. Engineer Tools

| Item | ID | Роль |
|------|-----|------|
| Construction Laser | `drmd:construction_laser` | установка на расстоянии, привязка к поверхности |
| Repair Laser | `drmd:repair_laser` | ремонт блоков / хил / щит |
| Mining Laser | `drmd:mining_laser` | добыча без контакта |
| Gravity Scanner | `drmd:gravity_scanner` | вектор локальной гравитации + поля |

`/d6 kit` выдаёт полный набор.

---

## 3. Projectile Framework

Единый `ProjectileKind` + `ProjectileFramework`:

LASER · PLASMA · KINETIC · ROCKET · GRAVITY_SPHERE · ENERGY_ORB · DRILL_CHARGE

Общий пайплайн столкновений через `WeaponCore.FireConfig.onHit`.  
Снаряды учитывают **локальную** гравитацию владельца (`LocalOrientation`), не только −Y.

---

## 4–6. Gravity Generator / Torch / Dynamic stations

- **Gravity Generator** — BE с radius / power / shape (`SPHERE|CYLINDER|PLANE`), facing = down.
  - ПКМ: power · Shift+ПКМ: shape
- **Gravity Torch** — компактное поле (~8 блоков); мобы и игроки воспринимают ось факела как низ.
- **GravityFields** — runtime-каталог; `FlightSystem` блендит downDir + силу → плавная смена ориентации между зонами станции.

Spawn-хаб ставит generator + два torch с разными направлениями (демо независимых зон).

---

## 7. Modular combat

Уже в моде (GMod-порт): AI роли, энергия, урон/щиты, HUD, радар, воздушный бой, события. Phase 3 не дублирует — стыкует с гравитацией и construction.

---

## 8. Fast Test Edition (MCPE)

**Не полный порт.** Отдельная экспериментальная сборка Bedrock/MCPE (вне этого репозитория / future `mcpe/`):

Цель — быстрые проверки:

- ощущение 6DoF на таче;
- строительство без верха/низа;
- локальная гравитация;
- HUD;
- поведение мобов.

**Без:** LLOD, мегаструктур, тяжёлого AI, WG 2.0.

Java/Fabric остаётся каноном для глубокой модификации движка.

См. также: `docs/BLOG_PRESENT.md`, `docs/ROADMAP.md`, `docs/VOXEL_LLOD.md`.
