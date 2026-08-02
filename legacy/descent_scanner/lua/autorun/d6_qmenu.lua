-- =========================================================
-- d6_qmenu.lua — DRMD 6DOF во встроенном меню Garry's Mod
--
--   Перенос всех меню мода (ранее: консольные команды и
--   кастомное TAB-меню d6_menu.lua) во встроенный Tool Menu
--   спавн-меню GMod: Q → вкладка «DRMD 6DOF».
--
--   Категории:
--     • Полёт      — переключатели режима + параметры полёта
--     • Энергия    — пресеты и распределение энергии
--     • Противники — спавн всех видов врагов, очистка карты
--     • Утилиты    — оружие, радар, навигация, быстрая команда
--
--   Бэкенд не дублируется: панели вызывают те же концкоманды
--   (6dof_*, d6_*) и используют D6Menu.Cfg/Apply/Save/Reset,
--   экспортированные из d6_menu.lua.
--
--   CLIENT-ONLY.
-- =========================================================
if not CLIENT then return end

local TAB = "DRMD 6DOF"

-- ── Хелперы ──────────────────────────────────────────────

-- Кнопка, выполняющая концкоманду с аргументами
local function CmdButton(panel, label, cmd, ...)
    local args = { ... }
    local btn = panel:Button(label)
    btn.DoClick = function()
        RunConsoleCommand(cmd, unpack(args))
    end
    return btn
end

-- ── Регистрация вкладки и категорий ──────────────────────

hook.Add("AddToolMenuTabs", "D6_QMenu", function()
    spawnmenu.AddToolTab(TAB, TAB, "icon16/controller.png")
end)

hook.Add("AddToolMenuCategories", "D6_QMenu", function()
    spawnmenu.AddToolCategory(TAB, "Flight",  "Полёт и управление")
    spawnmenu.AddToolCategory(TAB, "Energy",  "Энергосистема")
    spawnmenu.AddToolCategory(TAB, "Enemies", "Противники")
    spawnmenu.AddToolCategory(TAB, "Utility", "Утилиты")
end)

hook.Add("PopulateToolMenu", "D6_QMenu", function()

    -- ═════════════════════════════════════════════════════
    -- ПОЛЁТ: переключатели + параметры (бывшие вкладки
    -- «КОМАНДЫ» и «НАСТРОЙКИ» TAB-меню)
    -- ═════════════════════════════════════════════════════
    spawnmenu.AddToolMenuOption(TAB, "Flight", "d6_flight",
        "Управление полётом", "", "", function(panel)
        panel:ClearControls()

        panel:Help("Режим дрона")
        CmdButton(panel, "Включить / выключить 6DOF", "6dof_toggle")
        panel:ControlHelp("Также: клавиша R")

        CmdButton(panel, "Flight Assist (стабилизация / Ньютон)", "6dof_flightassist")
        CmdButton(panel, "Always-Run (постоянная тяга)", "6dof_alwaysrun")
        CmdButton(panel, "Рывок (dash)", "6dof_dash")
        panel:ControlHelp("Рывок удобнее забиндить: bind SHIFT 6dof_dash")
        CmdButton(panel, "Сброс крена (выровнять дрон)", "d6_reset_roll")

        panel:Help("")
        panel:Help("Параметры полёта")

        if not (D6Menu and D6Menu.Cfg and D6Menu.Settings) then
            panel:Help("d6_menu.lua не загружен — параметры недоступны.")
            return
        end

        local Cfg     = D6Menu.Cfg
        local sliders = {}

        for _, s in ipairs(D6Menu.Settings) do
            local sl = panel:NumSlider(s.label, nil, s.min, s.max, 0)
            sl:SetValue(Cfg[s.key])
            sl.OnValueChanged = function(_, val)
                -- округляем до шага, как в legacy-меню
                local v = math.Clamp(
                    math.floor(val / s.step + 0.5) * s.step, s.min, s.max)
                Cfg[s.key] = v
            end
            sliders[s.key] = sl
        end

        local applyBtn = panel:Button("Применить и сохранить")
        applyBtn.DoClick = function()
            D6Menu.Apply()
            D6Menu.Save()
            chat.AddText(Color(80, 200, 80), "[D6] Настройки применены.")
        end
        panel:ControlHelp("Отправляет d6_set на сервер (нужен admin)")

        local resetBtn = panel:Button("Сброс к заводским")
        resetBtn.DoClick = function()
            D6Menu.Reset()
            for key, sl in pairs(sliders) do
                sl:SetValue(Cfg[key])
            end
            chat.AddText(Color(255, 150, 80), "[D6] Настройки сброшены.")
        end
    end)

    -- ═════════════════════════════════════════════════════
    -- ЭНЕРГИЯ (бывшая вкладка «ЭНЕРГИЯ» TAB-меню)
    -- ═════════════════════════════════════════════════════
    spawnmenu.AddToolMenuOption(TAB, "Energy", "d6_energy",
        "Распределение энергии", "", "", function(panel)
        panel:ClearControls()

        -- Живой статус: проценты и запас энергии
        local status = vgui.Create("DPanel", panel)
        status:SetTall(76)
        status:Dock(TOP)
        status:DockMargin(10, 4, 10, 4)
        status.Paint = function(_, w, h)
            surface.SetDrawColor(30, 30, 30, 255)
            surface.DrawRect(0, 0, w, h)
            local ply = LocalPlayer()
            if not IsValid(ply) then return end
            local aW = math.Round(ply:GetNWFloat("D6_AllocWeapons", 34))
            local aS = math.Round(ply:GetNWFloat("D6_AllocShields", 33))
            local aE = math.Round(ply:GetNWFloat("D6_AllocEngines", 33))
            local en  = ply:GetNWFloat("D6_Energy",    100)
            local mx  = ply:GetNWFloat("D6_EnergyMax", 100)
            local pre = ply:GetNWString("D6_EnergyPreset", "balanced")
            draw.SimpleText(("ОРУЖИЕ %d%%   ЩИТЫ %d%%   ДВИЖКИ %d%%"):format(aW, aS, aE),
                "DermaDefaultBold", 8, 8,  Color(255, 200, 80))
            draw.SimpleText(("Пресет: %s"):format(string.upper(pre)),
                "DermaDefault",     8, 30, Color(200, 200, 200))
            draw.SimpleText(("Энергия: %d / %d"):format(math.floor(en), math.floor(mx)),
                "DermaDefault",     8, 50, Color(0, 180, 255))
        end

        panel:Help("Пресеты")
        CmdButton(panel, "БАЛАНС (34/33/33)",    "d6_energy_preset", "balanced")
        CmdButton(panel, "АТАКА (оружие)",       "d6_energy_preset", "assault")
        CmdButton(panel, "ПЕРЕХВАТ (движки)",    "d6_energy_preset", "interceptor")
        CmdButton(panel, "ОСАДА (щиты+оружие)",  "d6_energy_preset", "siege")

        panel:Help("")
        panel:Help("Ручное распределение (сумма ~100)")

        local alloc = { weapons = 34, shields = 33, engines = 33 }
        local function AllocSlider(label, key)
            local sl = panel:NumSlider(label, nil, 0, 100, 0)
            sl:SetValue(alloc[key])
            sl.OnValueChanged = function(_, val)
                alloc[key] = math.Round(val)
            end
        end
        AllocSlider("Оружие, %", "weapons")
        AllocSlider("Щиты, %",   "shields")
        AllocSlider("Движки, %", "engines")

        local applyBtn = panel:Button("Применить распределение")
        applyBtn.DoClick = function()
            RunConsoleCommand("d6_energy_set",
                tostring(alloc.weapons), tostring(alloc.shields), tostring(alloc.engines))
        end
        panel:ControlHelp("d6_energy_set <оружие> <щиты> <движки> (нужен admin)")
    end)

    -- ═════════════════════════════════════════════════════
    -- ПРОТИВНИКИ: все команды спавна + очистка
    -- ═════════════════════════════════════════════════════
    spawnmenu.AddToolMenuOption(TAB, "Enemies", "d6_enemies",
        "Спавн противников", "", "", function(panel)
        panel:ClearControls()

        panel:Help("Спавн в точке прицела.")

        panel:Help("Боевые роли (модульный AI)")
        CmdButton(panel, "Штурмовик (assault)",       "6dof_spawn_assault")
        CmdButton(panel, "Перехватчик (interceptor)", "6dof_spawn_interceptor")
        CmdButton(panel, "Артиллерия (artillery)",    "6dof_spawn_artillery")
        CmdButton(panel, "Поддержка (support)",       "6dof_spawn_support")
        CmdButton(panel, "Тяжёлый элитный (heavy)",   "6dof_spawn_heavy_elite")

        panel:Help("Классические дроны-сканеры")
        CmdButton(panel, "Мина-камикадзе",  "6dof_spawn_mine")
        CmdButton(panel, "Пулемётчик",      "6dof_spawn_mg")
        CmdButton(panel, "Ракетчик (RPG)",  "6dof_spawn_rpg")
        CmdButton(panel, "Грави-дрон",      "6dof_spawn_grav")
        CmdButton(panel, "Лазерный дрон",   "6dof_spawn_laser")
        CmdButton(panel, "Тяжёлый дрон",    "6dof_spawn_heavy")

        panel:Help("Особые")
        CmdButton(panel, "Червь (worm)",          "6dof_spawn_worm")
        CmdButton(panel, "Манхак (manhack)",      "6dof_spawn_manhack")
        CmdButton(panel, "Тяжёлый ловец (seeker)","6dof_spawn_heavy_seeker")
        CmdButton(panel, "Воздушная мина",        "6dof_spawn_airmine")

        panel:Help("Группы")
        CmdButton(panel, "Боевой отряд (squad)",       "6dof_spawn_squad")
        CmdButton(panel, "Бестиарий (все виды)",       "6dof_spawn_bestiary")

        panel:Help("")
        CmdButton(panel, "✖ Убить всех NPC", "d6_kill_npcs")
        panel:ControlHelp("Удаляет всех npc_* и дронов DRMD с карты")
    end)

    -- ═════════════════════════════════════════════════════
    -- УТИЛИТЫ: оружие, радар, навигация, быстрая команда
    -- (бывшая вкладка «МОДИФИКАЦИИ» TAB-меню)
    -- ═════════════════════════════════════════════════════
    spawnmenu.AddToolMenuOption(TAB, "Utility", "d6_utility",
        "Оружие и утилиты", "", "", function(panel)
        panel:ClearControls()

        panel:Help("Оружие")
        CmdButton(panel, "Выдать все оружия DRMD",         "d6_give_weapons")
        CmdButton(panel, "Следующее оружие",               "d6_weapon_next")
        CmdButton(panel, "Выдать внешние (Workshop)",      "d6_weapons_give_all")
        CmdButton(panel, "Список зарегистрированных (чат)","d6_weapons_list")

        panel:Help("Сканер")
        CmdButton(panel, "Радар врагов вкл/выкл", "d6_radar_toggle")
        panel:ControlHelp("Также: клавиша Numpad . (точка)")

        panel:Help("Навигация AI")
        CmdButton(panel, "Перестроить nav-граф",        "d6_nav_rebuild")
        CmdButton(panel, "Синхронизировать граф (debug)","d6_nav_sync")

        panel:Help("Legacy-меню")
        CmdButton(panel, "Открыть старое меню (CS 1.6)", "d6_menu_open")
        panel:ControlHelp("Также: Numpad 0")

        -- Быстрая команда — перенос вкладки «МОДИФИКАЦИИ»
        panel:Help("Быстрая команда (только d6_* / 6dof_*)")
        local entry = panel:TextEntry("")
        entry:SetPlaceholderText("d6_set maxSpeed 4000")

        local function RunQuick()
            local cmd = string.Trim(entry:GetValue() or "")
            if cmd == "" then return end
            if not (cmd:match("^d6_") or cmd:match("^6dof_")) then
                chat.AddText(Color(255, 80, 80),
                    "[D6] Разрешены только d6_* и 6dof_* команды")
                return
            end
            local parts = {}
            for p in cmd:gmatch("%S+") do parts[#parts + 1] = p end
            RunConsoleCommand(parts[1], unpack(parts, 2))
            chat.AddText(Color(80, 200, 80), "[D6] ✓ " .. cmd)
        end

        entry.OnEnter = RunQuick
        local runBtn = panel:Button("▶ Выполнить")
        runBtn.DoClick = RunQuick

        -- Примеры из legacy-меню
        panel:Help("Примеры")
        local EXAMPLES = {
            { "Сверх-скорость",   "d6_set maxSpeed 4000" },
            { "Нулевая гравитация","d6_set gravity 0" },
            { "Боевые настройки", "d6_set gunDamage 60" },
            { "Сброс гравитации", "d6_set gravity 500" },
        }
        for _, ex in ipairs(EXAMPLES) do
            local b = panel:Button(ex[1] .. "  —  " .. ex[2])
            b.DoClick = function()
                entry:SetValue(ex[2])
            end
        end
    end)

end)

print("[D6] d6_qmenu.lua загружен (Q → DRMD 6DOF)")
