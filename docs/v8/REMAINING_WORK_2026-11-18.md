# Остаток ТЗ Ungravity — 18 ноября 2026

Готовый brief для настройки следующей задачи. Публикация текущего проекта в GitHub `main` вновь разрешена пользователем; факт push фиксируется отдельно. Настройку дальнейшей задачи выполняет пользователь. Для неё явно выбрать ветку **`main`**: default-ветка репозитория пока остаётся прежней Claude, её смена не входит в этот push.

## Точка продолжения

Базовый опубликованный в Telegram snapshot: исходники `9226e7a04fa39fbfd12ada8d1770549bacd36264`, игровой JAR SHA-256 `65731fbda613eaf9d3babe7c54e9fe82a74ecbb65102c41342922503754bd674`. [Изменения293](https://t.me/abadonsoft/293), [сборка294](https://t.me/abadonsoft/294), [кадр295](https://t.me/abadonsoft/295), [кадр296](https://t.me/abadonsoft/296); исходные PNG и границы проверки — в [пакете snapshot](../../dist/snapshots/2026-11-18-9226e7a0/README.md).

На финальном игровом JAR подтверждён проход игрока с двумя исходными FIXED-корпусами через портал: 12 последовательных согласованных ACK, два кадра. Встроенный PS1-шейдер подтверждён отдельным фактическим `c72ead54` GPU-прогоном Native→PS1→Native; финальный JAR сохраняет побайтово все 54 относящихся к нему записи. Успех кластерного оружия `6d932620` сохраняет отдельную привязку и паритет 18 классов. Финальные чистые пересъёмки `f2b28c57` и `9226e7a0` **NONPASS**: удаление тестового моба с неустановленной причиной и затем недопуск до создания акторов. Их нельзя называть успешными. Нового полного regression-прогона для этого snapshot нет; прежний full `0107bd85` остаётся историческим результатом.

Детали и исходные отказы: [последний отчёт](IMPLEMENTATION_EMBEDDED_PS1_FIXED_GROUPS_2026-11-18.md). Текущая база требований: [MASTER v8](reference/00_MASTER_SPEC.md), [ENGINE B / встроенный PS1](reference/02_ENGINE/RENDER_NOCUBES_PS1.md), прямое дополнение Dynamic Boarding/BlockBody и последующее указание Create-style: отдельные блоки оружия, подшипника и привода на существующей физике.

## Следующие рабочие направления

Выбирать ограниченный пользовательский результат, реализовывать его в существующей архитектуре, затем проходить подходящий целевой gate. Повторная диагностика сама по себе не закрывает функцию. При интеграции сохранять исходные отказы, пользовательские миры, registry IDs и точную связь исходников/JAR/доказательств.

| Направление | Конкретный следующий результат и граница |
| --- | --- |
| 1. Кубический мир, S02–S03 | Довести штатный native XYZ backend и WorldAccess до полной loaded/unknown-семантики для edits, генерации, жидкостей, света и BE. Закрыть неизвестного соседа, отрицательные/высокие Y, холодную загрузку и совместимость старых сохранений. Установить реальную причину DUMMY/invalid BE, не удаляя данные по догадке. Кубическая инфраструктура уже существует; её полная паритетность ещё не принята. |
| 2. Рельеф и настоящий 3D LOD, U18/U19/U21, R1 | Продолжить объёмную дальнюю геометрию с многослойными пещерами, переходами секций/материалов и обновлением после разрушений; убрать известные плоскости/кольца LOD. Довести соответствие видимого рельефа, контактов, навигации и жидкостей. Округлые осевые стволы, ветви и термобур уже реализованы частично; нужны оставшиеся материалы, body-local metadata и реальные сцены леса/туннелей/неба. |
| 3. Встроенный PS1-рендер, U16/U17, R2/R3 | PS1 — встроенный shader/render pipeline, а coarse meshes — отдельная опция. Расширить реальный shader flow на нужные near/terrain/Sodium, smooth/translucent и reflected/offscreen материалы, сохраняя Native, освещение, HUD и состояние FBO. Закрыть независимые persisted настройки физики/рендера, debug-домены, 1/2/4 зеркала и заявленные backend/GL3.2 границы. c72 shader PASS не закрывает эту матрицу или аппаратную производительность. |
| 4. Полный Dynamic Boarding, U11/U12/U15, B1/B2/B3 | Довести контакты при ускорении/вращении, переход между корпусами, локальную гравитацию, лестницы и подвижные дочерние платформы для игроков/мобов/предметов/ragdoll. Завершить реальное управление generic ControlCore двигателями и существующим weapon scheduler; hinged/slider/motor-механизмы строить отдельными блоками bearing/drive. Сохранить UUID, импульс, edits/COM/shape/packet-согласованность и cold reload. Двери/люки и один FIXED-портальный эпизод уже работают; полные 10 разделов/12 критериев Boarding ещё открыты. |
| 5. Гравитация и ragdoll, S06–S08, U13/U14 | Закончить C1–C7 адаптеры и безопасный fallback, навигацию/атаки при wall/ceiling local-up, переходы полей и восстановление после смерти/портала/выгрузки. Довести семейства articulated rigs, контакты/CCD и явные sleep/wake/aggregation/interest tiers. Существующий 32×1000-tick trace не является реализованным sleeping или массовым бюджетом смертей. |
| 6. Самостоятельный 3D AI и бестиарий, U03/U04/U06/U07/U08/U20 | Развить реальное восприятие, назначение целей, обход объёмных препятствий/движущихся корпусов и боевые роли; довести Shapes и Heavy Operator из reusable оружия/приводов, counterplay отдельного телепортера. Удержать самостоятельные ORB/ROCKET/JET и существующую координацию. Для 256/512/1024 нужны реальные уровни симуляции/рендера и бюджеты. Закрыть оставшиеся held/free/mounted и persistence-сценарии оружия, включая RAIL ballistic accuracy и кластерный carrier. |
| 7. Настоящее оснащение дрона и use graph, U01/U02, S10/S15 | Довести штатные LASER/ROCKET/DRILL слоты и отображение фактического loadout в free/mounted ordinary/6DoF ×Native/PS1, сохранение, quick move и charge lifecycle. Сверять модели/иконки с реестром и custom-renderer путями. Удаление только после доказанного use graph и save-safe mapping; найденные рабочие assets/recipes сохраняются. |
| 8. Реактор, разрушения и Survival, B4, S09/S12/S16 | Завершить связный бой→саботаж/повреждение поддерживаемого ядра→настраиваемое предупреждение/эвакуация→ограниченное физическое разрушение. Существующие countdown и малые TNT/rocket support-collapse случаи не закрывают весь эпизод. Довести hunt→siege→cooldown, два ракетчика, защиту/AA, ремонт/ресурсы и честное восстановление базы; провести обещанную реальную демонстрацию крупного разрушения. |
| 9. Авторский объёмный мир и миссии, U05/U09/U10, S13/S14 | Создать связный same-world граф планетарных зон/лабораторий/шахт/станций/астероидов/кольца/Цитадели и игровые UFO/convoy/faction миссии. Использовать существующие data-driven цели и bounded city CAS. Полный городской полигон ещё не принят; старые Advanced-генераторы не подменяют авторскую реализацию. Пять tech tiers остаются кандидатами до авторского утверждения. |
| 10. Интеграция и измерения, S00/S16/S17 | После выбранных реализаций выполнить актуальную целостную сборку на одном exact JAR, подходящие unit/ordinary/native сцены, холодное сохранение и длительные разрушения. Измерять MSPT/FPS/heap/cubes/draw calls на названном runtime и железе; отсутствующие метрики оставлять неизвестными. Обновить реестр доказательств и воспроизводимые шаги перед следующим пакетом/релизом. |

## Полный остаток реестра

Сохранены все **99 task IDs, 21 пакет U01–U21 и 7 дополнений R1/R2/R3/B1/B2/B3/B4**. Сейчас **3 completed / 40 in_progress / 56 pending_reverification**; ниже все **96 незакрытых IDs**. `pending_reverification` не означает отсутствия кода: частичная реализация и исторические проверки описаны в [TASKS.yaml](TASKS.yaml), [живом JSON пакетов](IMPLEMENTATION_PACKET_PROGRESS_2026-11-17.json), [его читаемой версии и семи дополнениях](IMPLEMENTATION_PACKET_PROGRESS_2026-11-17.md). **0 целых пакетов принято.** Статусы ниже скопированы дословно, без автоматического повышения.

Три закрытые строки остаются в исходном реестре: `S09-T02` — Verify rigid block hull, thrusters and six-axis inertia in live client; `S11-T01` — Keep independent ORB, ROCKET and JET entity class identities separate from UFO; `S11-T03` — Run active128 moving96 proof with exact AI measures and two attack roles. Это узкие принятые результаты, не закрытие всего S09/S11.

### S00 — History & executable truth

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S00-T01 | `in_progress` | Capture all branches/worktrees and uncommitted patches without overwriting Sol/UFO work |
| S00-T02 | `in_progress` | Identify exact running DRMD jar SHA256, fabric.mod.json and matched Git source SHA |
| S00-T03 | `pending_reverification` | Audit pre-Oct26 agent changes in PR9, recovery branch and local logs |
| S00-T04 | `in_progress` | Classify legacy classes and commits KEEP/ADAPT/REMOVE_AFTER_PARITY/UNKNOWN |
| S00-T05 | `in_progress` | Create STATE, DECISIONS, REGRESSIONS and evidence index with real version metadata |
| S00-T06 | `pending_reverification` | Record unresolved assumptions and replay instructions for next agent |

### S01 — Reproduce and instrument baseline

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S01-T01 | `pending_reverification` | Reproduce player-proxy stuck/must-die-in-lava scenario with entity ownership logs |
| S01-T02 | `pending_reverification` | Reproduce vertical terrain breaks and world void death with fixed seed/flight path |
| S01-T03 | `pending_reverification` | Reproduce missing mirror slab and render-target diagnostics |
| S01-T04 | `pending_reverification` | Reproduce gravity torch camera clipping and 6DoF viewmodel hand sweep |
| S01-T05 | `in_progress` | Inspect invalid spawner BlockEntity and DUMMY NBT creation sources |
| S01-T06 | `pending_reverification` | Record stall profiler MSPT/JFR baseline under sim distance 8 and 24 |

### S02 — Choose actual CubicWorld backend

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S02-T01 | `pending_reverification` | Verify Better Cubic Chunks Fabric 1.21.1 exact jar/version/license/metadata |
| S02-T02 | `pending_reverification` | Run isolated Fabric + cubic world new-save smoke test and detect actual cubic preset |
| S02-T03 | `pending_reverification` | Compare true world bounds/16³ load state against historic fake cubic snapshots |
| S02-T04 | `pending_reverification` | Create VolumetricWorldAccess contract with signed coordinate and loaded-state API |
| S02-T05 | `in_progress` | Prove save/reload, fluids, lighting and BlockEntity parity in clean cubic world |
| S02-T06 | `pending_reverification` | Record upstream limits/blocked combinations with Sodium/Intel GL3.2 |

### S03 — Migrate world gameplay and structures

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S03-T01 | `pending_reverification` | Redirect block edits, explosions and fluid queries through WorldAccessAdapter |
| S03-T02 | `pending_reverification` | Port biome/structure placement from legacy height/column assumptions to cubic bounds |
| S03-T03 | `pending_reverification` | Repair generation producing stale mob_spawner and DUMMY BlockEntity NBT |
| S03-T04 | `pending_reverification` | Validate negative Y and Y 1024/1640/2150 across flight and cold save/reload |
| S03-T05 | `pending_reverification` | Remove obsolete shadow world/column emulation only after parity and guarded tests |

### S04 — Hybrid/NoCubes meshing and LOD

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S04-T01 | `in_progress` | Classify SMOOTH_NATURAL versus HARD_ENGINEERED material masks and transitions |
| S04-T02 | `in_progress` | Prototype feature-aware polygonal mesh on terrain and natural tree trunks |
| S04-T03 | `pending_reverification` | Support round cavities and mesh seam continuity across adjacent cubic sections |
| S04-T04 | `in_progress` | Match server collision/nav/fluids to visible smooth surfaces and tunnels |
| S04-T05 | `pending_reverification` | Prototype 3D LOD with Better Distant Horizons under separate compatibility gate |
| S04-T06 | `in_progress` | Prove high-altitude ground remains visible with smoothly decaying LOD |

### S05 — PS1 Hybrid presentation profile

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S05-T01 | `in_progress` | Build 640x360 internal target with nearest upscale and runtime toggle |
| S05-T02 | `in_progress` | Implement configurable vertex snap/quantized color/dither/affine-like UV |
| S05-T03 | `in_progress` | Implement altitude-dependent PS1 fog with sky-ground/asteroid fade continuity |
| S05-T04 | `in_progress` | Keep crosshair HUD and UI stable at readable resolution |
| S05-T05 | `in_progress` | Ensure profile toggles never mutate physics, world geometry or collision state |
| S05-T06 | `in_progress` | Run native/PS1/debug visual regression and GL3.2 fallback checks |

### S06 — Core gravity and player 6DoF

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S06-T01 | `pending_reverification` | Consolidate quaternion-based motion state and local gravity field coordinate transforms |
| S06-T02 | `pending_reverification` | Fix 6DoF proxy/mount lifecycle to make normal exit safe without lava |
| S06-T03 | `pending_reverification` | Fix camera anchor, eye offset and viewmodel at all roll/pitch/yaw positions |
| S06-T04 | `pending_reverification` | Make gravity torch field creation/removal deterministic and dimension-scoped |
| S06-T05 | `pending_reverification` | Replace Local gravity chat spam with state-transition feedback |
| S06-T06 | `pending_reverification` | Prove local/world collision and reset after death, teleport, unload and reload |

### S07 — Universal 6DoF for all mob families

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S07-T01 | `in_progress` | Implement C1–C7 species adapters and universal fallback without breaking vanilla AI |
| S07-T02 | `pending_reverification` | Make ground/wall/ceiling navigation relative to each entity local gravity |
| S07-T03 | `pending_reverification` | Extend target sight/raycast/attacks to arbitrary local orientation |
| S07-T04 | `pending_reverification` | Implement spatial gravity clusters with bounded neighbor queries and interest levels |
| S07-T05 | `pending_reverification` | Test cross-family mob rotation/contact/save/reload under gravity torches |

### S08 — Articulated ragdoll and destructible bodies

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S08-T01 | `pending_reverification` | Reconcile prior Oct8–10 ragdoll solver commits and collision regressions |
| S08-T02 | `in_progress` | Validate articulated joint limits, impulses and off-centre effective mass |
| S08-T03 | `in_progress` | Eliminate wall tunneling and duplicate death ragdolls on player/drone impacts |
| S08-T04 | `in_progress` | Implement debris aggregation/cleanup and sleeping in 128 death stress fixture |
| S08-T05 | `in_progress` | Verify local gravity ragdoll orientation with heavy rail weapon and block collision |

### S09 — BlockBody / UFO boarding and reactor

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S09-T01 | `pending_reverification` | Reconcile Sol/Codex UFO branch and preserve current worktree before edits |
| S09-T03 | `in_progress` | Verify mounted lasers/rockets/hinged actuators maintain transforms during roll |
| S09-T04 | `in_progress` | Implement boarding/capture/control override and deterministic pilot exit |
| S09-T05 | `in_progress` | Implement reactor damage, sabotage, bounded fracture and escape window |
| S09-T06 | `in_progress` | Save/reload UFO in flight and test optional VS unofficial backend only in isolated stand |

### S10 — Weapons, cluster, drill and building

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S10-T01 | `in_progress` | Repair held weapon 6DoF and normal first person visibility and inventory model JSON |
| S10-T02 | `in_progress` | Verify three separate gravity gun behaviours and ballistic rail accuracy |
| S10-T03 | `in_progress` | Implement first-person cluster carrier/staged structured spread/impact and cleanup |
| S10-T04 | `in_progress` | Build weak chunky versus strong thermal circular drill with collision/lighting parity |
| S10-T05 | `pending_reverification` | Repair construction resource accounting, server permission and batch invalidation |

### S11 — ORB/ROCKET/JET swarms

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S11-T02 | `in_progress` | Verify spatial-hash bounded neighbours, group steering and collision avoidance |
| S11-T04 | `pending_reverification` | Scale 256/512/1024 with explicit physics/AI/render LOD and profiler |
| S11-T05 | `in_progress` | Add robust projectile/path/target handling for rockets, lasers and obstacle destruction |
| S11-T06 | `pending_reverification` | Run settlement destruction and debris cleanup after save/reload |

### S12 — Survival threat and minimal armament economy

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S12-T01 | `in_progress` | Create stateful search→hunt→siege→cooldown threat director with faction attribution |
| S12-T02 | `pending_reverification` | Balance two rocket infantry against a base with telegraph, defense and escape options |
| S12-T03 | `pending_reverification` | Implement simple player gun/plasma/anti-missile resource progression without Create factory |
| S12-T04 | `pending_reverification` | Define base repair/resource loss rules and prevent irreversible early survival softlocks |
| S12-T05 | `pending_reverification` | Test one coherent day-night hunt event with player decision branches |

### S13 — World architecture: core to Citadel

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S13-T01 | `pending_reverification` | Create deterministic planetary spatial zones including core and upper atmosphere |
| S13-T02 | `pending_reverification` | Prototype Xenogears-like underground laboratories/shafts with proper permissions |
| S13-T03 | `pending_reverification` | Prototype deserted stations and asteroid bodies at near/mid/far LOD |
| S13-T04 | `pending_reverification` | Block out ruined orbit ring and central faction Citadel with flight routes |
| S13-T05 | `pending_reverification` | Implement smooth ground-sky atmosphere/LOD gradation, no floating random junk |
| S13-T06 | `pending_reverification` | Validate physical navigation across layers and compact mission pacing |

### S14 — Factions, Shapes and mission framework

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S14-T01 | `pending_reverification` | Design five proposed tech levels, keep them candidates until author approval |
| S14-T02 | `in_progress` | Prototype Shapes as armed physical infantry with squad AI and no vanilla teleport |
| S14-T03 | `in_progress` | Assign teleport ability to a distinct flying enemy archetype and validate counter-play |
| S14-T04 | `pending_reverification` | Prototype heavy laser operator with shoulder rockets on BlockBody machinery |
| S14-T05 | `in_progress` | Write Strife-style mission contracts for lab infiltration, UFO boarding and convoy defense |

### S15 — Cleanup / asset and UI integration

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S15-T01 | `pending_reverification` | Identify six red-marked item registry IDs and their actual use graph |
| S15-T02 | `pending_reverification` | Remove only approved dead items/models/textures with save-safe migration mapping |
| S15-T03 | `pending_reverification` | Fix mirror slab and portal separate render passes with correct FBO behavior |
| S15-T04 | `pending_reverification` | Remove confirmed legacy sky junk without deleting approved asteroid concept |
| S15-T05 | `in_progress` | Audit GUI tooltips, HUD scaling, battle debug lines and PS1 readability |

### S16 — Full performance and regression testing

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S16-T01 | `in_progress` | Run source-level unit + GameTest + actual client rendercheck on same exact JAR |
| S16-T02 | `in_progress` | Benchmark mspt/fps/heap/loaded cubes/draw calls across 32→1024 drone tests |
| S16-T03 | `pending_reverification` | Run high altitude full-mesh/camera/LOD pressure with roll and fast vehicle |
| S16-T04 | `pending_reverification` | Run city/dungeon explosions, ragdoll/debris and save/reload for hours |
| S16-T05 | `pending_reverification` | Record unsupported beta/driver/upstream failures and rollback paths |

### S17 — Integration, handoff and release candidate

| ID | Текущий статус | Исходное название задачи |
| --- | --- | --- |
| S17-T01 | `in_progress` | Merge isolated worktrees via reviewed integration branch without losing active UFO work |
| S17-T02 | `in_progress` | Update STATE/TASKS/DECISIONS/REGRESSIONS and exact evidence index on final SHA |
| S17-T03 | `pending_reverification` | Package release candidate JAR/modlist/test save only when passes relevant gates |
| S17-T04 | `pending_reverification` | Write honest user-facing PASS/FAIL/BLOCKED status for every P0/P1 and test |
| S17-T05 | `pending_reverification` | Deliver reproducible run steps, full diffs and follow-on worldbuilding backlog |

Срез реестров прочитан из canonical `32d5f07158d0d560b585d626458efdb3ccefc419`: TASKS SHA-256 `546b827ceaa2f4f80365c74886dabaaa74701ba02fe88a8847acdc5f91bbd91e`, packet JSON SHA-256 `6903ccc35ce452b0f94976649c2dfcc567be4d09a2c7bd21ecad5772868f7428`. Документ не меняет реестры и не объявляет новых PASS.
