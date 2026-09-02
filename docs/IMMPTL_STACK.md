# Immersive Portals (ImmPtl) — optional dimension stack

DRMD Path B (default): one Overworld column (−512…1024) with diggable mantle → Core.
Path A (optional soft-dep): Immersive Portals **Dimension Stack** for true see-through seams.

## Soft dependency

`fabric.mod.json` **suggests** ImmPtl. The mod runs without it.
`PortalComplexity.hasImmersivePortals()` detects common mod ids for HUD only.

### Build-side trap: ImmPtl's transitive access wideners

DRMD compiles against the vendored ImmPtl jar (`modImplementation files("libs/immersiveportals-…")`),
and **Loom applies a mod dependency's `transitive-*` access wideners to this project's compile
environment.** So every vanilla member ImmPtl widens is silently widened for DRMD's compile too. Code
relying on one of those widenings compiles cleanly and CI passes — then crashes at class-load on any
client *without* ImmPtl, because Fabric Loader only applies wideners from **installed** mods.

This is not hypothetical. `SkyUfoEntity.getBoundingBox()` overrides a method vanilla declares `final`;
it compiled only because ImmPtl's own widener covers it (`transitive-extendable class_1297
method_5829`), and a real client without ImmPtl died at startup with `IncompatibleClassChangeError`
before the title screen. Fixed by declaring the widening in DRMD's own `drmd.accesswidener` — which
Fabric Loader *does* apply at runtime, since `fabric.mod.json` declares it.

**Rule: anything DRMD needs widened must be in `src/main/resources/drmd.accesswidener`, never inherited
from ImmPtl.** `AccessWidenerTest` pins the known case; the general risk (a compile that only succeeds
because of an optional dependency's widener) has no compiler or CI signal at all, so check
`imm_ptl.accesswidener` inside the vendored jar before assuming a `final`/`private` vanilla member is
legitimately reachable.

### Что зеркала и панели делают без ImmPtl

Игровые сообщения о связке ссылаются сюда, так что вот прямой ответ.

| | без ImmPtl | с ImmPtl |
|---|---|---|
| Связка пары (авто и вручную) | да | да |
| Проход насквозь | да, `PortalTravel` | да, сущность `Portal` |
| Взгляд насквозь | нет | да |
| Кросс-дименшн связь | нет, отказ с причиной | да |
| Пересчёт масштаба (`Warped Resonance Key`) | нет, отказ с причиной | да |

Раньше без ImmPtl **связка не образовывалась вообще**: оба пути связывания выходили досрочно, `LINKED`
никогда не выставлялся, и блок честно писал «decorative only». Теперь связывается и носит сам; ImmPtl
добавляет к этому вид сквозь портал, а не сам портал.

Кто именно носит — решается по наличию привязанной сущности, а не по наличию мода. Это заодно правильно
отрабатывает обе миграции: мир, связанный с ImmPtl и открытый без него, продолжает работать нативно, а
связанный нативно не ломается, если ImmPtl потом поставить.

Vanilla Nether / End portals still need **gate catalysts** (complex crafts) even with ImmPtl installed — dig-through remains the seamless survival path.

## Инвентарь для вшивания: 181 миксин, посчитанные по джарнику

Не оценка, а разбор `libs/immersiveportals-6.0.6-mc1.21.1-fabric.jar`: пять миксин-конфигов,
перечисленных в его `fabric.mod.json`.

| конфиг | пакет | миксинов |
|---|---|---|
| `imm_ptl.mixins.json` | `imm_ptl.core.mixin` | **134** (74 общих + 60 клиентских) |
| `imm_ptl_compat.mixins.json` | `imm_ptl.core.compat.mixin` | **20** |
| `imm_ptl_peripheral.mixins.json` | `imm_ptl.peripheral.mixin` | **18** |
| `q_misc_util.mixins.json` | `q_misc_util.mixin` | **5** |
| `imm_ptl_fabric.mixins.json` | `platform_specific.mixin` | **4** |
| | | **181** |

### Что из этого не переносится в принципе

**Все 20 из `compat`** — это миксины в чужие моды, а не в ваниль: Sodium 9, Iris 7, Flywheel 3,
Cardinal Components 1. Без этих модов их не во что вставлять, а DRMD совместимость с шейдерпаками уже
объявил вне области (см. `PORTAL_RENDERING.md`). То есть 11% от общего числа отпадает сразу и без
потерь.

**Жёсткая зависимость `dimlib`.** В `fabric.mod.json` ImmPtl она в `depends`, а не в `recommends` —
это ещё один мод, не библиотека внутри джарника.

**Список несовместимостей ImmPtl** (его собственный `breaks`), потому что он же становится списком
ограничений всего, что на нём стоит: `optifabric`, `canvas`, `cardboard`, `vmp`, `gravity_api`,
`resolutioncontrol` — целиком; `pehkui < 3.4.1`; и — главное — **`iris` строго 1.8.1** и **`sodium`
строго 0.6.7**, не «не ниже», а ровно эти версии. Мод, прибитый к точечным версиям двух самых
популярных рендер-модов, — это и есть цена подхода «миксины в рендер».

### Что относится к нашей задаче

Из 134 ядровых:

| группа | миксинов | зачем |
|---|---|---|
| `client.render*` (+ optimization, shader, framebuffer, isometric) | **29** | сам сквозной рендер |
| `client.sync`, `common.chunk_sync`, `position_sync`, `entity_sync`, `other_sync` | **38** | несколько миров разом |
| `common.collision`, `client.collisions` | **9** | физика через портал |
| `common.debug`, `client.debug` | **10** | пропускается |
| остальное (interaction, particle, sound, registry, networking, mc_util, …) | 48 | по потребности |

Из этого следует практический вывод к плану: **38 миксинов синхронизации — это не рендер, это
инфраструктура двух живых миров**, и именно она стоит между «зеркало показывает отражение» и
«кросс-дименшн портал». 29 рендерных — та часть, которую DRMD уже обошёл своим путём (ножницы вместо
маски, косая ближняя плоскость вместо `gl_ClipDistance`), не написав ни одного миксина.

### Во что упираются осознанные ограничения нативного переноса

Два ограничения, записанные в `PORTAL_RENDERING.md` («только одно измерение», «пассажиры
пропускаются»), стоят очень по-разному — вот чем именно каждое снимается у ImmPtl.

**«Только одно измерение» — около 40 миксинов, и ни одного рендерного.**

| группа | шт. | что делает |
|---|---|---|
| `common.chunk_sync` | 10 | шлёт игроку чанки измерения, в котором он не находится |
| `client.sync` | 11 | клиентская половина, включая `MixinReceivingLevelScreen` — экран «Downloading terrain» не должен появляться |
| `common.position_sync` | 7 | шесть из семи — варианты `ServerboundMovePlayerPacket`: ваниль считает пакет движения издалека читом, а проход портала делает таким **каждый** пакет |
| `common.entity_sync` | 6 | `TrackedEntity`, `ServerEntity`, `PersistentEntitySectionManager` |
| `common.other_sync` | 4 | `PlayerList` (сама смена измерения), поза, карты |
| `client.multiworld_awareness` | 2 | туман и эмбиент-звук **чужого** мира |

Это не «перенести рендер». Это заменить то, как Minecraft решает, какие чанки и каких сущностей
игроку вообще позволено видеть.

**«Пассажиры пропускаются» — 9 миксинов**, `common.collision` (8) плюс `client.collisions`
(`MixinLocalPlayer`). Существенно: отдельного пакета `passenger`/`vehicle` **нет вообще** — это
сидит внутри `MixinEntity` плюс персональный `MixinAbstractMinecartEntity`. То есть пассажиров
нельзя вынуть как отдельную фичу: это инъекция в сам `Entity`. И транспорт даже у ImmPtl оказался
достоин отдельного случая.

### Что на самом деле определяет цену вшивания: пересечение целей

У DRMD 18 своих миксинов. Из 11 ванильных классов, в которые он уже вмешивается, **10 — тоже цели
ImmPtl**:

| ванильный класс | миксинов ImmPtl | миксин DRMD |
|---|---|---|
| `GameRenderer` | 4 | `client.GameRendererMixin` |
| `Minecraft` | 4 | — |
| `Entity` | 3 | `EntityFluidMixin` |
| `ClientLevel` | 3 | `client.ClientWorldMixin` |
| `Player` | 2 | `PlayerEntityMixin` |
| `LivingEntity` | 2 | `LivingEntityMixin` |
| `Camera` | 1 | `client.CameraMixin` + `CameraAccessor` |
| `LocalPlayer` | 1 | `client.ClientPlayerEntityMixin` |
| `ServerPlayer` | 1 | `ServerPlayerFlightTravelMixin` |
| `Gui` | 1 | `client.InGameHudMixin` |

И миксины DRMD на `Player` / `ServerPlayer` / `LocalPlayer` / `Camera` / `GameRenderer` — это система
6DoF-полёта, то есть ядро мода. Поэтому вшивание — не «добавить 134 файла», а **свести две системы
инъекций в одни и те же методы тех классов, которыми 6DoF уже владеет**. Оценивать эту работу надо
по этой таблице, а не по числу файлов.

Отсюда же и подтверждение уже выбранного пути: 29 рендерных миксинов ImmPtl DRMD обошёл целиком
(ножницы вместо маски, косая ближняя плоскость вместо `gl_ClipDistance`), не тронув ни `GameRenderer`,
ни `Camera` чужим кодом.

## Recommended stack (Fabric 1.21.1)

```
End (Oblivion)   ↑
Orbit / sky      ↑   ← optional separate dim, or OW sky band
Overworld        ↑
Nether (Core)    ↑
```

Align ImmPtl floor/ceiling links with `WorldLevels`:

| Seam | OW Y | Notes |
|------|------|--------|
| OW floor ↔ Nether ceiling | ≈ −240 (`NETHER_CEILING`) | Mantle dig path meets Core |
| OW top ↔ End floor | ≈ 880 (`ORBITAL_TOP`) | Techno-ring vista / Oblivion |

## Portal crafts (always on)

1. `plasma_granite` + `energy_cell` → **Portal Stabilizer**
2. Stabilizer + netherite scrap + obsidian + cells → **Nether Gate Catalyst**
3. Stabilizer + end crystals + pearls + plasma granite → **End Gate Catalyst**

Igniting a nether frame or placing an eye consumes the matching catalyst (creative exempt).

## SeamWarmup (Path B + optional ImmPtl)

`SeamWarmup` opens real Nether/End with short-lived chunk tickets when the pilot is within 72 blocks of OW faces −240 / 880 (critical intensify at 10). With ImmPtl installed this pre-warms remote sides of the stack; without it, Path B still streams the Nether column seamlessly via `MantleStream` / `LevelBuilder`.

## Escape

Digging up through mantle / shafts, `/d6 level`, or DimensionSync aftermath cues keep the surface path readable after Core events.
