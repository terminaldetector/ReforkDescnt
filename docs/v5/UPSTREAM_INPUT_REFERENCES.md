# ВНЕШНИЕ РЕФЕРЕНСЫ И ПРЕДВАРИТЕЛЬНЫЙ АНАЛИЗ СОВМЕСТИМОСТИ
Проверено публичными страницами 26 октября 2026. Это сведения разработчиков/каталогов, не результат интеграционного теста на Ungravity.

| Проект | На что полезен | Текущая релевантность | Ограничения/решение |
|---|---|---|---|
| Better Cubic Chunks 0.1.1 Beta | generation/storage/network/render 16³, 32768 default world height, `HUGE`, Sodium claimed integration | **Fabric 1.21.1** | Beta / All Rights Reserved; можно в modpack, нельзя копировать код; обязателен GATE-0 |
| Better Distant Horizons | 3D LOD for Better Cubic Chunks | same ecosystem | matching version, conditional test; не подменять near collision |
| NoCubes | smooth terrain, SurfaceNets, collisions | Modrinth заявляет 1.21–1.21.1, Fabric | LGPL-3.0-only, client-side listing: особое внимание серверным collisions при таком renderer |
| Official Valkyrien Skies / Eureka | physics-enabled block ships | каталоги official в основном 1.20.1 and earlier | не ставить «как готово» на 1.21.1; Eureka Alpha |
| Unofficial VS2 / Eureka Ships 2 ports | block ships on Fabric 1.21.1 | web listings show 1.21.1 | unofficial, экспериментальная совместимость с cubic world + Sodium и NoCubes, отдельный worktree |
| LitePhysics / Block Physics | debris/collapse/ragdoll inspiration | встречаются Fabric 1.21.1 | лицензии/совместимость проверить; не наслаивать три разных collision systems |
| Teardown | reference feeling of destruction and dynamic physics | conceptual reference | это другая игра/движок; не копировать закрытый код/ассеты |
| OpenCubicChunks legacy | исторические подходы | Modrinth 1.12.2/Forge; CubicChunks3 другая версия | не подставлять 1.12.2 Forge mod в Fabric 1.21.1 |

URLs:
- https://www.curseforge.com/minecraft/mc-mods/better-cubic-chunks
- https://www.curseforge.com/minecraft/mc-mods/better-cubic-chunks/files/9091780
- https://www.curseforge.com/minecraft/mc-mods/better-distant-horizons
- https://modrinth.com/mod/nocubes
- https://modrinth.com/mod/valkyrien-skies
- https://modrinth.com/project/valkyrien-skies-unnof-port
- https://modrinth.com/project/eureka
- https://github.com/Eminai-LeoVinci/Eureka-Ships-2
- https://github.com/ValkyrienSkies/Valkyrien-Skies-2
- https://modrinth.com/mod/litephysics
- https://github.com/orca-gamedev/block-physics
- https://github.com/OpenCubicChunks/CubicChunks3

**Правило:** даже если сайт заявляет Sodium/Immersive Portals совместимость, в едином стеке DRMD+world+NoCubes+ship+mirror это НЕ транзитивное доказательство совместимости. Тестировать декартово произведение только нужных комбинаторных вариантов, начиная с самых малых.
