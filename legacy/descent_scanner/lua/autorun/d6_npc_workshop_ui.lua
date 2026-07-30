-- =========================================================
-- d6_npc_workshop_ui.lua — DRMD NPC Workshop (GUI, CLIENT)
--
--   Q → вкладка «DRMD Workshop» → категория «NPC Constructor»:
--     Профиль и пресеты / Модель / Полётная физика / AI и бой /
--     Рой / Вооружение / Навигация
--
--   Все панели редактируют одну рабочую копию профиля (WP).
--   «Сохранить на сервер» → D6_NPCWorkshop.Submit (admin-гейт,
--   персист DATA/drmd_npcs/, рассылка всем клиентам).
--
--   Схемы секций — данные; билдер один (паттерн ui_schema SCK
--   и панелей d6_qmenu). UI-паттерны (DListView-пресеты, диск-
--   сохранение, дискретные слайдеры) — по аудиту npc-toolset.
-- =========================================================
if not CLIENT then return end

local TAB = "DRMD Workshop"

-- ── Рабочая копия профиля ────────────────────────────────

local function DefaultProfile()
    return {
        base   = "custom",
        entity = "npc_cscanner",
        ai     = "pressure",
        model  = { model = "", skin = 0, bodygroups = {}, scale = 1.2,
                   collisionScale = 1.0 },
        stats  = {
            hp = 200, color = { r = 255, g = 80, b = 80 }, speed = 540,
            rangeMin = 400, rangeMax = 600, retreatHP = 0.3,
            shield = 50, shieldMax = 50, armor = 0, armorMax = 0,
            resist = { kinetic = 0, energy = 0.1, explosive = 0 },
        },
        phys   = { flightMode = "physics", mass = 80, spoolUp = 9, spoolDown = 3,
                   velLerp = 5, maxSpeed = 780, angPower = 240, angInertia = 0.87,
                   angSpinSup = 0.18, angMax = 400, rollMul = 1.0,
                   bankGain = 1.1, bankMax = 50 },
        combat = nil,   -- nil → ролевой стиль ядра
        swarm  = nil,   -- nil → константы boids ядра
        slots  = { primaryL = "Pulsar", primaryR = "", utility = "", core = "" },
        nav    = { patrol = "", attack = "", escape = "", idle = "" },
    }
end

local WP       = DefaultProfile()
local WPName   = "my_drone"

-- Значения для включения переопределений (зеркало констант ядра,
-- авторитет — ядро: nil-секция = прежнее поведение)
local DEFAULT_COMBAT = {
    orbitR = 480, inclMax = 0.52, orbitMin = 0.8, orbitMax = 1.6,
    runMin = 0.8, runMax = 1.4, breakMin = 0.7, breakMax = 1.1,
    breakIn = 220, vbias = 0.55, aggression = 0.85, spdMul = 1.0,
    maneuvers = { barrel = true, split_s = true, immelmann = true },
}
local DEFAULT_SWARM = {
    separation = 2.8, cohesion = 0.8, alignment = 1.0, target = 2.2,
    radius = 400, maxSpeed = 480, sepDist = 90, exclude = false,
}

-- ── Перестройка панелей ──────────────────────────────────

local Builders = {}   -- [panelId] = { panel = DPanel, build = fn }

local function Rebuild(id)
    local b = Builders[id]
    if b and IsValid(b.panel) then
        b.panel:ClearControls()
        b.build(b.panel)
    end
end

local function RebuildAll()
    for id in pairs(Builders) do Rebuild(id) end
end

-- Вызывается из d6_npc_workshop.lua (STOOL RMB → профиль в редактор)
function D6_NPCWS_LoadWorking(tbl, name)
    WP = table.Copy(tbl)
    if name then WPName = name end
    RebuildAll()
end

-- ── Схемы секций (данные → один билдер) ──────────────────

local SCHEMA = {
    phys = {
        { "mass",       "Масса",                10,   600,  0 },
        { "spoolUp",    "Разгон двигателя",     0.5,  20,   1 },
        { "spoolDown",  "Торможение",           0.5,  10,   1 },
        { "velLerp",    "Инерция скорости",     0.5,  12,   1 },
        { "maxSpeed",   "Макс. скорость",       100,  2000, 0 },
        { "angPower",   "Угловая мощность",     20,   600,  0 },
        { "angInertia", "Угловая инерция",      0.5,  0.99, 2 },
        { "angSpinSup", "Подавление вращения",  0,    0.6,  2 },
        { "angMax",     "Макс. угл. скорость",  60,   720,  0 },
        { "rollMul",    "Крен (roll)",          0.1,  3,    1 },
        { "bankGain",   "Вход в вираж (bank)",  0,    2.5,  1 },
        { "bankMax",    "Макс. крен в вираже",  0,    85,   0 },
    },
    stats = {
        { "hp",          "Здоровье",            10,  3000, 0 },
        { "speed",       "Боевая скорость",     50,  1500, 0 },
        { "rangeMin",    "Мин. дистанция",      50,  3000, 0 },
        { "rangeMax",    "Макс. дистанция",     100, 4000, 0 },
        { "retreatHP",   "Отступление при HP",  0,   1,    2 },
        { "shield",      "Щит",                 0,   500,  0 },
        { "shieldMax",   "Щит макс.",           0,   500,  0 },
        { "armor",       "Броня",               0,   300,  0 },
        { "armorMax",    "Броня макс.",         0,   300,  0 },
    },
    resist = {
        { "kinetic",   "Резист кинетика",  0, 0.9, 2 },
        { "energy",    "Резист энергия",   0, 0.9, 2 },
        { "explosive", "Резист взрыв",     0, 0.9, 2 },
    },
    combat = {
        { "orbitR",     "Радиус орбиты",        150, 2000, 0 },
        { "inclMax",    "Наклон орбиты (рад)",  0,   1.4,  2 },
        { "orbitMin",   "Орбита мин. (с)",      0.3, 6,    1 },
        { "orbitMax",   "Орбита макс. (с)",     0.5, 8,    1 },
        { "runMin",     "Атака мин. (с)",       0,   4,    1 },
        { "runMax",     "Атака макс. (с)",      0,   5,    1 },
        { "breakMin",   "Отрыв мин. (с)",       0.2, 4,    1 },
        { "breakMax",   "Отрыв макс. (с)",      0.3, 5,    1 },
        { "breakIn",    "Дист. отрыва",         80,  1200, 0 },
        { "vbias",      "Вертикальный уклон",   0,   1,    2 },
        { "aggression", "Агрессия (шанс атаки)",0,   1,    2 },
        { "spdMul",     "Множитель скорости",   0.5, 1.6,  2 },
    },
    swarm = {
        { "separation", "Разделение",        0,   8,    1 },
        { "cohesion",   "Сплочённость",      0,   4,    1 },
        { "alignment",  "Выравнивание",      0,   4,    1 },
        { "target",     "Тяга к цели",       0,   6,    1 },
        { "radius",     "Радиус роя",        100, 1500, 0 },
        { "maxSpeed",   "Скорость в рое",    100, 1200, 0 },
        { "sepDist",    "Дист. разделения",  30,  400,  0 },
    },
}

-- Один билдер слайдеров для любой секции
local function AddSliders(panel, fields, getTbl)
    for _, f in ipairs(fields) do
        local key, label, mn, mx, dec = f[1], f[2], f[3], f[4], f[5]
        local sl = panel:NumSlider(label, nil, mn, mx, dec)
        local t = getTbl()
        sl:SetValue(t and t[key] or mn)
        sl.OnValueChanged = function(_, val)
            local tbl = getTbl()
            if tbl then tbl[key] = math.Round(val, dec) end
        end
    end
end

-- ── Регистрация вкладки/категории/панелей ────────────────

hook.Add("AddToolMenuTabs", "D6_NPCWS_UI", function()
    spawnmenu.AddToolTab(TAB, TAB, "icon16/bomb.png")
end)

hook.Add("AddToolMenuCategories", "D6_NPCWS_UI", function()
    spawnmenu.AddToolCategory(TAB, "NPCConstructor", "NPC Constructor")
end)

local function AddPanel(id, title, build)
    spawnmenu.AddToolMenuOption(TAB, "NPCConstructor", id, title, "", "",
        function(panel)
            Builders[id] = { panel = panel, build = build }
            panel:ClearControls()
            build(panel)
        end)
end

hook.Add("PopulateToolMenu", "D6_NPCWS_UI", function()

    -- ═════ 1. ПРОФИЛЬ И ПРЕСЕТЫ ═════
    AddPanel("d6_npc_profile", "Профиль и пресеты", function(panel)
        panel:Help("Конструктор противников DRMD. Профиль = модель + "
            .. "полёт + AI-роль + бой + рой + оружие + навигация.")

        local nameEntry = panel:TextEntry("Имя профиля")
        nameEntry:SetValue(WPName)
        nameEntry.OnChange = function(s)
            local v = string.Trim(s:GetValue())
            if v ~= "" then WPName = v end
        end

        -- Сохранённые профили (реестр синхронизирован с сервера)
        local combo = panel:ComboBox("Загрузить профиль")
        for _, n in ipairs(D6_NPCWorkshop.GetNames()) do combo:AddChoice(n) end
        combo.OnSelect = function(_, _, val)
            local p = D6_NPCWorkshop.Get(val)
            if p then D6_NPCWS_LoadWorking(p, val) end
        end

        local saveBtn = panel:Button("Сохранить на сервер")
        saveBtn.DoClick = function()
            D6_NPCWorkshop.Submit(WPName, WP)
            timer.Simple(0.3, function() Rebuild("d6_npc_profile") end)
        end
        panel:ControlHelp("Персист + рассылка всем (нужен admin)")

        local spawnBtn = panel:Button("Сохранить и заспавнить")
        spawnBtn.DoClick = function()
            if D6_NPCWorkshop.Submit(WPName, WP) then
                timer.Simple(0.3, function()
                    RunConsoleCommand("6dof_spawn_profile", WPName)
                end)
            end
        end

        panel:Help("Создать из стоковой роли")
        local roleCombo = panel:ComboBox("Роль-основа")
        for _, r in ipairs({ "assault", "interceptor", "artillery",
                             "support", "heavy_elite" }) do
            roleCombo:AddChoice(r, r, r == "assault")
        end
        local cloneBtn = panel:Button("Создать профиль из роли")
        cloneBtn.DoClick = function()
            local _, role = roleCombo:GetSelected()
            D6_NPCWorkshop.NewFromRole(WPName, role or "assault")
            -- сервер пришлёт Sync; подтянем в редактор
            timer.Simple(0.5, function()
                local p = D6_NPCWorkshop.Get(WPName)
                if p then D6_NPCWS_LoadWorking(p, WPName) end
            end)
        end

        local dupBtn = panel:Button("Клонировать текущий (имя + _copy)")
        dupBtn.DoClick = function()
            WPName = WPName .. "_copy"
            D6_NPCWorkshop.Submit(WPName, WP)
            timer.Simple(0.3, function() Rebuild("d6_npc_profile") end)
        end

        local delBtn = panel:Button("Удалить профиль с сервера")
        delBtn.DoClick = function()
            D6_NPCWorkshop.Submit(WPName, nil)
            timer.Simple(0.3, function() Rebuild("d6_npc_profile") end)
        end

        panel:Help("Экспорт / импорт")
        local expBtn = panel:Button("Экспорт в буфер (JSON)")
        expBtn.DoClick = function()
            SetClipboardText(util.TableToJSON(WP, true))
            chat.AddText(Color(80, 255, 80), "[D6 NPC] Профиль → буфер обмена.")
        end
        local impEntry = panel:TextEntry("JSON для импорта")
        local impBtn = panel:Button("Импорт из поля")
        impBtn.DoClick = function()
            local tbl = util.JSONToTable(impEntry:GetValue() or "")
            if istable(tbl) then
                D6_NPCWS_LoadWorking(tbl)
                chat.AddText(Color(80, 255, 80), "[D6 NPC] Профиль импортирован.")
            else
                chat.AddText(Color(255, 80, 80), "[D6 NPC] Невалидный JSON.")
            end
        end

        local resetBtn = panel:Button("Сбросить редактор (новый профиль)")
        resetBtn.DoClick = function()
            WP = DefaultProfile()
            RebuildAll()
        end
    end)

    -- ═════ 2. МОДЕЛЬ ═════
    AddPanel("d6_npc_model", "Модель", function(panel)
        panel:Help("Базовая сущность и внешний вид.")

        local entCombo = panel:ComboBox("Базовая сущность")
        entCombo:AddChoice("npc_cscanner (полная поддержка)", "npc_cscanner",
            (WP.entity or "npc_cscanner") == "npc_cscanner")
        entCombo:AddChoice("npc_manhack (полная поддержка)", "npc_manhack",
            WP.entity == "npc_manhack")
        entCombo:AddChoice("npc_combinegunship (эксперимент)", "npc_combinegunship",
            WP.entity == "npc_combinegunship")
        entCombo:AddChoice("npc_helicopter (эксперимент)", "npc_helicopter",
            WP.entity == "npc_helicopter")
        entCombo.OnSelect = function(_, _, _, data) WP.entity = data end

        local mdlEntry = panel:TextEntry("Модель (пусто = модель сущности)")
        mdlEntry:SetValue(WP.model.model or "")
        mdlEntry.OnChange = function(s)
            WP.model.model = string.Trim(s:GetValue())
        end

        local skinSl = panel:NumSlider("Скин", nil, 0, 10, 0)
        skinSl:SetValue(WP.model.skin or 0)
        skinSl.OnValueChanged = function(_, v) WP.model.skin = math.Round(v) end

        local scaleSl = panel:NumSlider("Масштаб модели", nil, 0.3, 4, 2)
        scaleSl:SetValue(WP.model.scale or 1)
        scaleSl.OnValueChanged = function(_, v) WP.model.scale = math.Round(v, 2) end

        local colSl = panel:NumSlider("Масштаб хитбокса", nil, 0.3, 3, 2)
        colSl:SetValue(WP.model.collisionScale or 1)
        colSl.OnValueChanged = function(_, v)
            WP.model.collisionScale = math.Round(v, 2)
        end
        panel:ControlHelp("Hull Size = масштаб модели × масштаб хитбокса")

        panel:Help("Цвет (RGB)")
        for _, ch in ipairs({ { "r", "Красный" }, { "g", "Зелёный" }, { "b", "Синий" } }) do
            local sl = panel:NumSlider(ch[2], nil, 0, 255, 0)
            sl:SetValue(WP.stats.color[ch[1]] or 255)
            sl.OnValueChanged = function(_, v)
                WP.stats.color[ch[1]] = math.Round(v)
            end
        end

        panel:ControlHelp("Навесные модели (attachments) — через Weapon "
            .. "Workshop / SCK кластеры для оружия игрока; у NPC внешние "
            .. "модули задаются слотами вооружения.")
    end)

    -- ═════ 3. ПОЛЁТНАЯ ФИЗИКА ═════
    AddPanel("d6_npc_phys", "Полётная физика", function(panel)
        panel:Help("12 параметров DRMD Flight Controller (d6_modules).")
        AddSliders(panel, SCHEMA.phys, function() return WP.phys end)
    end)

    -- ═════ 4. AI И БОЙ ═════
    AddPanel("d6_npc_ai", "AI и бой", function(panel)
        panel:Help("Роль определяет машину состояний поведения.")

        local aiCombo = panel:ComboBox("AI-роль")
        for _, v in ipairs({
            { "pressure",  "Assault (pressure)" },
            { "flank",     "Interceptor (flank)" },
            { "standoff",  "Artillery (standoff)" },
            { "support",   "Support" },
            { "anchor",    "Heavy (anchor)" },
        }) do
            aiCombo:AddChoice(v[2], v[1], WP.ai == v[1])
        end
        aiCombo.OnSelect = function(_, _, _, data) WP.ai = data end

        panel:Help("Боевые параметры")
        AddSliders(panel, SCHEMA.stats, function() return WP.stats end)

        panel:Help("Резисты урона")
        AddSliders(panel, SCHEMA.resist, function() return WP.stats.resist end)

        panel:Help("Боевое движение (Combat Motion)")
        local cmBox = panel:CheckBox("Переопределить стиль (иначе — стиль роли)")
        cmBox:SetValue(WP.combat ~= nil)
        cmBox.OnChange = function(_, on)
            WP.combat = on and table.Copy(DEFAULT_COMBAT) or nil
            Rebuild("d6_npc_ai")
        end

        if WP.combat then
            AddSliders(panel, SCHEMA.combat, function() return WP.combat end)
            panel:Help("Манёвры отрыва")
            for _, m in ipairs({
                { "barrel",    "Бочка (Barrel Roll)" },
                { "split_s",   "Сплит-S" },
                { "immelmann", "Иммельман" },
            }) do
                local cb = panel:CheckBox(m[2])
                cb:SetValue(WP.combat.maneuvers[m[1]] == true)
                cb.OnChange = function(_, on)
                    WP.combat.maneuvers[m[1]] = on or nil
                end
            end
        end
    end)

    -- ═════ 5. РОЙ ═════
    AddPanel("d6_npc_swarm", "Рой", function(panel)
        panel:Help("Boids: эмерджентное роевое поведение (лидеров и "
            .. "формаций в ядре нет — рой самоорганизуется).")

        local swBox = panel:CheckBox("Переопределить веса (иначе — константы ядра)")
        swBox:SetValue(WP.swarm ~= nil)
        swBox.OnChange = function(_, on)
            WP.swarm = on and table.Copy(DEFAULT_SWARM) or nil
            Rebuild("d6_npc_swarm")
        end

        if WP.swarm then
            AddSliders(panel, SCHEMA.swarm, function() return WP.swarm end)
            local exBox = panel:CheckBox("Исключить из роя (одиночка)")
            exBox:SetValue(WP.swarm.exclude == true)
            exBox.OnChange = function(_, on) WP.swarm.exclude = on end
        end
    end)

    -- ═════ 6. ВООРУЖЕНИЕ ═════
    AddPanel("d6_npc_weapons", "Вооружение", function(panel)
        panel:Help("Слоты модулей DRMD (реестр D6_Modules; стрельба — "
            .. "через D6_Wep из Weapon Workshop).")

        local MODULES = {
            [""] = "— пусто —",
            Pulsar  = "Pulse (Pulsar — очередь)",
            Laser   = "Laser (лучемёт)",
            Plasma  = "Plasma (сгустки, exotic)",
            Rail    = "Rail (рельса, kinetic)",
            Missile = "Rocket (Missile — самонаведение)",
            Flak    = "Flak (осколочный конус)",
            ComBall = "Heavy (ComBall)",
            GravityUnit     = "Gravity (отталкивание)",
            RepairBeam      = "Repair (лечение союзников)",
            ShieldGenerator = "Shield (реген щита)",
            -- Wave 2
            VulcanMod      = "Vulcan (очередь ближнего боя, ×2)",
            ConcussionMod  = "Concussion (тяжёлая ракета, AoE 280)",
            HomingMod      = "Homing (самонаведение 15 с)",
            GravRailMod    = "GravRail (кинетический рельс, 22000 u/s)",
        }
        local SLOT_OPTS = {
            primaryL = { "", "Pulsar", "Laser", "Plasma", "VulcanMod" },
            primaryR = { "", "Missile", "Rail", "Flak", "ComBall",
                         "ConcussionMod", "HomingMod", "GravRailMod" },
            utility  = { "", "RepairBeam", "GravityUnit" },
            core     = { "", "ShieldGenerator" },
        }
        for _, slot in ipairs({ "primaryL", "primaryR", "utility", "core" }) do
            local cb = panel:ComboBox("Слот " .. slot)
            for _, modName in ipairs(SLOT_OPTS[slot]) do
                cb:AddChoice(MODULES[modName] or modName, modName,
                    (WP.slots[slot] or "") == modName)
            end
            cb.OnSelect = function(_, _, _, data)
                WP.slots[slot] = data
            end
        end
        panel:ControlHelp("Кастомные модули регистрируются через "
            .. "D6_RegisterModule и вводятся импортом JSON.")
    end)

    -- ═════ 7. НАВИГАЦИЯ ═════
    AddPanel("d6_npc_nav", "Навигация", function(panel)
        panel:Help("Маршруты = группы узлов nav-графа (d6_nav_node). "
            .. "Патрульная группа используется в режиме покоя; остальные "
            .. "зарезервированы для сценариев энкаунтеров.")

        for _, g in ipairs({
            { "patrol", "Патруль (группа узлов)" },
            { "attack", "Маршрут атаки" },
            { "escape", "Маршрут отхода" },
            { "idle",   "Маршрут покоя" },
        }) do
            local e = panel:TextEntry(g[2])
            e:SetValue(WP.nav[g[1]] or "")
            e.OnChange = function(s)
                WP.nav[g[1]] = string.Trim(s:GetValue())
            end
        end

        panel:Help("Размещение узлов (по прицелу)")
        local grpEntry = panel:TextEntry("Группа нового узла")
        grpEntry:SetValue("route1")
        local placeBtn = panel:Button("Поставить nav-узел")
        placeBtn.DoClick = function()
            RunConsoleCommand("d6_navnode_place",
                string.Trim(grpEntry:GetValue() or ""), "64")
        end
        local rebuildBtn = panel:Button("Перестроить nav-граф")
        rebuildBtn.DoClick = function() RunConsoleCommand("d6_nav_rebuild") end
        local dbgBtn = panel:Button("Показать/скрыть граф (debug)")
        dbgBtn.DoClick = function()
            local cv = GetConVar("d6_nav_debug")
            RunConsoleCommand("d6_nav_debug",
                (cv and cv:GetInt() or 0) == 0 and "1" or "0")
        end

        panel:Help("Спавнер профиля (d6_npc_spawner)")
        local cntSl = panel:NumSlider("Юнитов за волну", nil, 1, 12, 0)
        cntSl:SetValue(3)
        local respBox = panel:CheckBox("Респавн после зачистки")
        local spBtn = panel:Button("Поставить спавнер текущего профиля")
        spBtn.DoClick = function()
            RunConsoleCommand("d6_npcspawner_place", WPName,
                tostring(math.Round(cntSl:GetValue())),
                respBox:GetChecked() and "1" or "0", "30")
        end
        panel:ControlHelp("Спавнер вызывает SpawnD6Role → понимает и роли, "
            .. "и профили Workshop")
    end)

end)

print("[D6] d6_npc_workshop_ui.lua загружен (Q → DRMD Workshop → NPC Constructor)")
