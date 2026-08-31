# Свой see-through рендер порталов и зеркал

План инициативы: DRMD рисует зеркала и порталы **сам**, без зависимости от Immersive Portals в рантайме.
ImmPtl используется как референс (Apache 2.0) и как библиотека для компиляции, но не как обязательный мод.

Документ лежит в репозитории намеренно. Раньше он жил в рабочем каталоге агента вне git и **дважды
пропадал при перезапуске контейнера** вместе с загруженными архивами и распакованными исходниками.
Всё, что было записано в код и доки, пережило это без потерь — поэтому выводы держим здесь.

## Где брать исходники ImmPtl

Загруженные в чат архивы не переживают перезапуск контейнера и в git не попадают. Качать заново:

```
https://raw.githubusercontent.com/iPortalTeam/ImmersivePortalsMod/1.21/src/main/java/qouteall/imm_ptl/core/<путь>
```

Ветка `1.21`, исходники лежат прямо под `src/main/java` — **без** префикса модуля `imm_ptl_core/`,
который подсказывает текущая раскладка репозитория. Проверено после перезапуска на трёх файлах, на
которых держатся выводы ниже: `render/renderer/RendererUsingStencil.java` (317 строк),
`mixin/client/render/MixinGameRenderer.java` (337), `compat/IPPortingLibCompat.java` (68).

Скачивание репозитория целиком (tar.gz) прокси песочницы блокирует — 403; отдельные raw-файлы
работают. Бинарник `libs/immersiveportals-6.0.6-mc1.21.1-fabric.jar` лежит в git и переживает всё;
декомпилятор в образе не установлен, но `javap` есть.

## Что уже сделано

| Часть | Файл | Состояние |
|---|---|---|
| Математика отражений и переноса | `client/portal/PortalTransform.java` | готово, покрыто тестами |
| yaw/pitch ↔ вектор | там же | готово, сверено с формулой `ShipAttitude` |
| Гейт рекурсии и дальности | `client/portal/MirrorRenderGate.java` | готово, покрыто тестами |
| Поиск зеркал у камеры | `client/portal/MirrorScanner.java` | готово |
| Offscreen-цель | `client/portal/MirrorFramebuffer.java` | готово |
| Рекурсивный отражённый рендер | `client/portal/MirrorReflectionRenderer.java` | **проба**, см. ниже |

Всё это под тумблером `DescentConfig.mirrorReflection`, **выключенным по умолчанию** — единственный
такой в конфиге. Причина: это первая часть проекта, где зелёный CI не говорит ничего о том, работает
ли оно.

## Главный вывод: обе маски ImmPtl упираются в чужую инфраструктуру

Установлено чтением настоящих исходников, а не предположено.

**Стенсиль** (`RendererUsingStencil` — у ImmPtl это основной путь) требует stencil-attachment на
главном render target. В ванили его нет. ImmPtl достаёт его через поле
`RenderTarget.port_lib$stencilEnabled`, которое вставляет **Porting Lib** — другой мод; читается
рефлексией с проверкой на null (`IPPortingLibCompat`). То есть это не «код ImmPtl, который можно
вшить», это ImmPtl, зависящий от второго мода. Сделать attachment самим — миксин, меняющий формат
главного фреймбуфера на каждый кадр игры.

**Фреймбуфер** (`RendererUsingFrameBuffer`) стенсиля не требует, но композит идёт через **собственный
шейдер** `DrawFbInAreaShader`: размер экрана приходит юниформом, UV берутся из `gl_FragCoord`. Иначе
нельзя — вершинные UV в экранных координатах интерполируются перспективно-корректно и на квадре дают
искажение. Своих шейдеров у DRMD нет вообще, так что это тоже новая инфраструктура
(`assets/drmd/shaders/core/*.json` + `.vsh`/`.fsh` + `CoreShaderRegistrationCallback`).

**Практический вывод для оценки «вшивания»:** в ~10 000 строк миксинов ImmPtl часть кода не переносится
в принципе — она опирается на сторонние моды. Это надо закладывать в план.

## Поэтому текущий шаг — проба, а не фича

Отражение рендерится в offscreen-цель и выводится **на весь экран** ванильным `Framebuffer.draw`: без
своего шейдера, без нового миксина. Это изолирует единственный вопрос, на который здесь ответить
нельзя — **даёт ли рекурсивный отражённый рендер корректную картинку вообще**. Пока ответ не «да»,
маску строить не на чем.

Как читать результат (`mirrorReflection=true`, подойти к зеркалу ближе 24 блоков):

- **правильно** — весь экран показывает мир с точки за зеркалом, отражённый слева-направо,
  поворачивается согласованно с движением;
- **не та позиция или ориентация** — смотреть реконструкцию матрицы в `MirrorReflectionRenderer`;
- **чёрный экран или картинка не меняется** — смотреть сам offscreen-рендер.

Три исхода различимы, и каждый указывает на свой файл. Ради этого проба и сделана полноэкранной.

## Осознанно пропущено

- **Подмена поля фреймбуфера** (`IEMinecraftClient.ip_setFrameBuffer`) — нужна ImmPtl ради
  fabulous-графики и шейдерпаков, оба вне области DRMD. Хватает обычной привязки. Fabulous — известный
  пробел, не недосмотр.
- **Совместимость с шейдерпаками (Iris/Sodium)** — вне области целиком.
- **Порталы произвольной формы** — у DRMD зеркала и панели фиксированной формы, общая иерархия
  `PortalShape` не нужна.
- **Occlusion query** для проверки видимости портала — на первом заходе можно без неё, гейт по
  дистанции уже есть.

## Что дальше

1. Подтвердить пробу на живом клиенте.
2. Маска по форме зеркала — потребует своего шейдера (см. выше).
3. Два живых `ClientWorld` одновременно — самая новая для этого кода часть; референс
   `ClientWorldLoader` и список подменяемых полей в `MyGameRenderer.switchAndRenderTheWorld`.
4. Кросс-дименшн портал поверх пунктов 2–3.
5. Связка со слоями мира — см. [`WORLD_CONCEPT.md`](WORLD_CONCEPT.md).

## Проверенные снаружи имена

Всё, что бралось из Minecraft API, сверялось с официальными маппингами Yarn 1.21.1
(`https://raw.githubusercontent.com/FabricMC/yarn/1.21.1/mappings/...`), а не по памяти:

- `WorldRenderer.render(RenderTickCounter, boolean, Camera, GameRenderer, LightmapTextureManager, Matrix4f, Matrix4f)`
- `Framebuffer`: `beginWrite(boolean)` / `endWrite()` / `resize(int,int,boolean)` / `clear(boolean)` /
  `setClearColor(float,float,float,float)` / `draw(int,int)` / `getColorAttachment()`
- `SimpleFramebuffer(int width, int height, boolean useDepth, boolean getError)`
- `MinecraftClient.getFramebuffer()`, `MinecraftClient.IS_SYSTEM_MAC`, `Window.getFramebufferWidth/Height`
- `VertexBuffer`: `bind()` / `upload(BuiltBuffer)` / `draw(Matrix4f, Matrix4f, ShaderProgram)` —
  проверено для будущей оптимизации воксельного горизонта, пока не применяется

Порядок аргументов `projectionMatrix` / `positionMatrix` подтверждён по реальному исходнику Fabric API
(`WorldRenderContextImpl.prepare` пробрасывает их 1:1 из вызова рендера), а форма самой матрицы —
по `MixinGameRenderer.wrapCameraTransformation`, который оборачивает ванильный вызов
`new Matrix4f().rotation(camera.rotation())`.
