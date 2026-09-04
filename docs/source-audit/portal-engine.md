# Аудит: порталы и портальные пушки

*Отдельный бриф. Цель — собственный портальный слой в DRMD, где портальная пушка это клиент
портального движка, а не наоборот.*

## A. Матрица проектов

| Форк | Ветка по умолчанию | Лицензия | Роль |
|---|---|---|---|
| `SeamlessPortals` | **`1.21`** | Apache-2.0 | **на деле не SeamlessPortals** — порт Immersive Portals под NeoForge, см. ниже |
| `portal-gun-mod` | **`1.21.1`** | MIT | пушка (MeowMC), **NeoForge** |
| `portal_gun` | `main` | MIT | пушка (TarLaboratories), **Forge, MC 1.20.2** |
| Immersive Portals | ветка `1.21` | Apache-2.0 | движок; исходники есть, три файла уже перенесены |

Отсутствует относительно брифа: `ricks-portal-gun-multiloader`, `iPortalTeam/PortalGun`,
`ImmersivePortalsModForForge`.

**Поправка к тому, что я написал часом раньше: ни один из трёх новых портальных форков не Fabric.**

| Форк | Что это на самом деле | Загрузчик | MC |
|---|---|---|---|
| `SeamlessPortals` | Immersive Portals | NeoForge | 1.21.1 |
| `portal-gun-mod` | пушка MeowMC | NeoForge | 1.21.1 |
| `portal_gun` | пушка TarLaboratories | Forge | 1.20.2 |
| присланный архив ImmPtl | Immersive Portals | **Fabric** | 1.21.1 |

Проверено по `build.gradle` каждого: `net.neoforged.gradle.userdev`, `net.neoforged.moddev`,
`net.minecraftforge.gradle` соответственно. Я сказал, что `portal-gun-mod` не требует перевода ни
через версию, ни через загрузчик — версия совпадает, загрузчик нет.

Практически: **единственный портальный исходник, который можно вставить в сборку DRMD, — это
присланный архив Immersive Portals под Fabric.** Три новых форка читаются как проекты, а не как код
для копирования: идея и структура переносятся, вызовы переписываются.

## Что такое `terminaldetector/SeamlessPortals` на самом деле

Прочитано, а не предположено по имени. Это **не** SeamlessPortals Voidlighter'а.

- `README.md` начинается со слов «# Immersive Portals Mod … This fork is not yet available on
  Modrinth or Curseforge».
- `archives_base_name=immersive-portals`, `minecraft_version=1.21.1`.
- Все 525 файлов лежат в пакетах `qouteall.imm_ptl.*` и `qouteall.q_misc_util.*` — это дерево
  Immersive Portals.
- Верхний коммит: «First attempt adding Gravity Control support, based on the work done by
  Matthew-Alpha … ImmersivePortalsModForForge/tree/1.20.1».

То есть это ветка `ImmersivePortalsModForForge` из списка брифа, лежащая под чужим именем.

**И это NeoForge, а не Fabric.** Единственный gradle-плагин сборки — `net.neoforged.gradle.userdev`;
Fabric API подтягивается через `org.sinytra.forgified-fabric-api`, то есть переиздание Fabric API
поверх NeoForge от Sinytra Connector. `fabric.mod.json` в ресурсах лежит, но собирается проект под
Neo.

Практический вывод: **вставить его в DRMD нельзя** — DRMD собирается под Fabric. Ценность форка
другая, и она большая.

## Чем этот форк ценен: измеренная граница платформы

Бриф и общий план требуют «Minecraft = platform adapter». Этот форк — рабочий ответ на вопрос,
сколько это стоит на живом движке порталов в 70 000 строк, потому что рядом лежат обе версии одного
и того же кода: присланный архив под Fabric (492 файла) и этот форк под Neo (525).

| | Fabric-исходники | Форк под Neo |
|---|---|---|
| файлов всего | 492 | 525 |
| импортируют `net.fabricmc` | **105** (21%) | 3 |
| импортируют `net.neoforged` | — | 76 |
| импортируют `net.minecraft.client` | 148 | 154 |

**Слой загрузчика не исчез — он переехал.** Было 105 файлов, привязанных к Fabric; стало 79,
привязанных к Neo и остаткам Fabric. Пятая часть дерева трогает API загрузчика в любом случае, и
полностью развязать движок такого размера у портировавших не вышло — вместо этого они опёрлись на
слой совместимости (Sinytra), чтобы не переписывать код против Fabric API заново.

Для плана это отрезвляющий факт, и лучше знать его сейчас: «ядро, не знающее о платформе» на
масштабе портального движка — не бесплатная архитектурная позиция, а работа, которую даже авторы
порта предпочли обойти.

## Три приёма из этого форка, которые стоят переноса

Дельта между двумя деревьями — 34 добавленных файла и один удалённый, и она вся про границу
платформы.

**1. Клиентский код вынесен в отдельные файлы.** `ScaleUtilsClient`, `CollisionHelperClient`,
`ImmPtlNetworkingClient`, `GlobalPortalStorageClient`, `PortalWandItemClient`,
`McRemoteProcedureCallClient` и ещё несколько. В самих файлах это записано прямо:
`// @Nick1st copy of ScaleUtils with ClientOnly code`. Причина не косметическая: Neo строго делит
клиент и сервер, и класс с клиентским кодом не должен грузиться на выделенном сервере, тогда как
Fabric терпит смешанные классы с `@Environment`.

У DRMD это уже частично сделано разложением по пакетам `client/` и `world/`, но не до конца: например
`PortalTransform` с чистой математикой живёт в `client.portal`, а пользуется им серверный
`PortalCrossing`. Это не ошибка сборки, а мина под будущий адаптер.

**2. Свой слой событий вместо колбэков загрузчика** — `de.nick1st.imm_ptl.events`, 11 файлов:
`ServerPortalTickEvent`, `ClientPortalTickEvent`, `PortalDisposeEvent`, `ReadPortalDataEvent`,
`WritePortalDataEvent`, `DimensionEvents` и другие. Движок публикует свои события, адаптер их
переводит в события конкретного загрузчика.

**3. Свой слой сети** — `de.nick1st.*.networking`, 4 файла: `Payloads`, `ClientPayloadHandler`,
`ImplRPCPayload`. Ровно то, что бриф называет `D6PortalNetwork`.

Порядок именно такой: без пункта 1 два других не имеют смысла, потому что граница проходит не только
между загрузчиками, но и между сторонами.

## Что в DRMD уже есть по этому брифу

Портальный слой не пустой, и бриф надо накладывать на то, что стоит.

| Из брифа | В DRMD | Состояние |
|---|---|---|
| `D6PortalTransform` | `client/portal/PortalTransform` | перенос точки, поворот, отражение, yaw/pitch; чистый, покрыт тестами |
| `D6PortalEntityTransport` | `world/portal/PortalTravel` + `PortalCrossing` | переносит игроков нативно, без ImmPtl |
| `D6PortalRenderer` | `client/portal/OffscreenWorldView` | сквозной вид с косым ближним отсечением и ножницами, без шейдеров |
| `D6PortalPair` | `ChargedMirrorBlockEntity` / `PortalPanelBlockEntity` | связка хранится на обеих сторонах, разрыв при сломе |
| `D6PortalGun` | `world/portal/mirror/PortalGunItem` | ставит панель, ассеты добавлены |
| `D6PortalSpace` | — | нет; ближайшее — `CoordinateSpaceType` из VS1, разобран в `algorithm-map.md` |
| `D6PortalCollision` | — | нет |
| `D6PortalPhysics` | `d6/D6PhysicsBody` | тело есть, связи с порталом нет |
| `D6PortalNetwork` | — | нет; синхронизация двух миров это ~38 миксинов ImmPtl, см. `IMMPTL_STACK.md` |

## §3 брифа: не телепортация, а преобразование

Требование — при пересечении портала преобразовывать позицию, скорость, ориентацию и угловую
скорость одним трансформом.

`PortalTransform` умеет позицию и направление. Скорость — это направление, то есть уже покрыто.
**Ориентация и угловая скорость — нет**, и до сегодняшнего дня их не к чему было применять: у
корабля не было ни кватерниона, ни угловой скорости как состояния. Теперь `D6PhysicsBody` даёт и то,
и другое, а `ImmPtlQuaternions` — композицию поворотов. То есть недостающая часть §3 стала
выполнимой ровно сейчас.

Есть и деталь, которую бриф не называет, а она решает качество: **точка пересечения**. Переход
решается по тику, между «где был» и «где стал», и без точки пересечения объект прибывает не туда,
где прошёл, а туда, куда попала граница тика. `ImmPtlPlane.intersectionWithSegment` это уже даёт.

## Чего здесь пока нет

Чтение исходников `SeamlessPortals`, `portal-gun-mod` и `portal_gun`: как каждый решает
рендер, коллизию, перенос сущностей и сеть, и что из этого лучше уже сделанного в DRMD.
