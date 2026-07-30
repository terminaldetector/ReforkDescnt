# MCPE Fast Test / MCPE Master

Bedrock UX-песочница DRMD 6DOF, адаптированная под **MCPE Master** и официальный Bedrock.

## Что скачать

| Файл | Для чего |
|------|----------|
| `drmd-6dof-mcpe-master-1.0.2.mcaddon` | Авто-импорт (открыть файлом) |
| `drmd-6dof-mcpe-master-1.0.2.zip` | Ручная установка в `games/com.mojang` (ZArchiver / Master) |
| `*-bp-*.mcpack` / `*-rp-*.mcpack` | По отдельности BP + RP |

## Установка в MCPE Master

### A) Авто
1. Открой `.mcaddon` через Minecraft / MCPE Master  
2. Мир → **Наборы ресурсов** + **Наборы поведения** → оба **DRMD 6DOF (MCPE Master)**  
3. **Эксперименты → Beta APIs = ВКЛ**

### B) Вручную (часто надёжнее на Master)
1. Распакуй `.zip`  
2. Скопируй:
   - `behavior_packs/DRMD_6DOF_BP` → `/games/com.mojang/behavior_packs/`
   - `resource_packs/DRMD_6DOF_RP` → `/games/com.mojang/resource_packs/`
3. Перезапуск → активируй пакеты в мире → **Beta APIs**  
4. `/function drmd/start` или просто зайди в мир (авто-кит)

## Управление (touch)

| Действие | Как |
|----------|-----|
| Крен ← / → | Держи **Крен влево/вправо** в хотбаре |
| Вверх / вниз | Держи **Вверх/Вниз** |
| Стрейф | Держи **Стрейф** |
| Рывок / форсаж / сброс | Тап по кнопке |
| Все сразу | **Панель управления** или `!d6 panel` |
| Тяга / тормоз | Прыжок / красться |

Чат: `!d6 kit` · `!d6 dash` · `!d6 rolll` · `!d6 rollr` · `!d6 toggle`  
Функции: `/function drmd/help` · `/function drmd/kit` · `/function drmd/start`

## Требования

- Minecraft / MCPE Master **≈ 1.20.60+** (лучше **1.21.x**)  
- Beta APIs / эксперименты для Script API  

Полный Descent = Fabric jar на ПК (`drmd-6dof-1.0.0.jar`).
