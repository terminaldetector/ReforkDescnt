-- =========================================================
-- d6_weaponworkshop.lua — DRMD Weapon Workshop (Q-меню)
--
--   Q → DRMD Workshop → Редактор оружия:
--     Внешний вид      — визуальный редактор weapon_d6_*
--                        (по-файловый список + SCK-кластеры)
--     SCK Конструктор  — сохранённые пресеты SCK + экспорт
--     Архетипы         — готовые шаблоны кластеров
--     Генерация SWEP   — список сгенерированных файлов + конфиги
--     Грави-пушки      — живая настройка weapon_d6_gravy_railgun /
--                        weapon_d6_railmk2 (через D6_GravCfg) +
--                        быстрое сохранение пресета-«оружия»
--
--   Q → DRMD Workshop → NPC Constructor — регистрируется
--   отдельно в d6_npc_workshop_ui.lua (не трогаем).
--
--   CLIENT-ONLY. Нет SCK — раздел «SCK Конструктор» показывает
--   заглушку, «Внешний вид» работает (SCK нужен только для самого
--   редактирования, но кнопка Load сама его выдаст).
-- =========================================================
if not CLIENT then return end

-- ── Встроенные архетипы кластеров ────────────────────────

local D6_ARCHETYPES = {
    {
        label = "Assault Drone",
        desc  = "Двойные передние пушки (Center), купол сенсора (Upper)",
        clusters = {
            Center = {
                { name="barrel_l", slot=1, model="models/weapons/w_pistol.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0,-8,-4), angle=Angle(0,0,0), size=Vector(0.8,0.8,0.8) },
                { name="barrel_r", slot=2, model="models/weapons/w_pistol.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0, 8,-4), angle=Angle(0,0,0), size=Vector(0.8,0.8,0.8) },
            },
            Upper = {
                { name="dome", slot=1,
                  model="models/props_combine/combine_generator01.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0,0,14), angle=Angle(0,0,0), size=Vector(0.3,0.3,0.3) },
            },
            SideLeft={}, SideRight={}, Lower={},
        },
    },
    {
        label = "Heavy Gunship",
        desc  = "Главная пушка (Center), ракетные блоки по бокам (SideLeft/Right)",
        clusters = {
            Center = {
                { name="main_gun", slot=1,
                  model="models/weapons/w_rocket_launcher.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0,0,-6), angle=Angle(0,0,0), size=Vector(1,1,1) },
            },
            SideLeft = {
                { name="pod_l", slot=1, model="models/weapons/w_missile.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0,-18,0), angle=Angle(0,0,0), size=Vector(0.9,0.9,0.9) },
            },
            SideRight = {
                { name="pod_r", slot=1, model="models/weapons/w_missile.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0, 18,0), angle=Angle(0,0,0), size=Vector(0.9,0.9,0.9) },
            },
            Upper={}, Lower={},
        },
    },
    {
        label = "Interceptor",
        desc  = "Энергопушка по центру (Center), плавники снизу (Lower)",
        clusters = {
            Center = {
                { name="cannon", slot=1, model="models/weapons/w_crossbow.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0,0,-2), angle=Angle(0,0,0), size=Vector(1.1,1.1,1.1) },
            },
            Lower = {
                { name="fin_l", slot=1,
                  model="models/props_combine/combinetrain01a.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(-20,-12,-8), angle=Angle(0,0,45), size=Vector(0.1,0.1,0.1) },
                { name="fin_r", slot=2,
                  model="models/props_combine/combinetrain01a.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(-20, 12,-8), angle=Angle(0,0,-45), size=Vector(0.1,0.1,0.1) },
            },
            Upper={}, SideLeft={}, SideRight={},
        },
    },
}

-- ── Утилиты ───────────────────────────────────────────────

local function SCKPresent()
    return file.Exists("lua/weapons/swep_construction_kit/shared.lua", "GAME")
end

-- Все weapon_d6_* классы (без spawn-оружий), из реестра + SCK-конфигов + wepview
local function CollectWeaponClasses()
    local set = {}
    for _, w in ipairs(weapons.GetList() or {}) do
        local cn = w.ClassName or ""
        if cn:match("^weapon_d6_") and not cn:match("spawn") then
            set[cn] = true
        end
    end
    if D6_SCKBridge then
        for cn in pairs(D6_SCKBridge.Configs) do set[cn] = true end
    end
    if D6_WEPVIEW_CFG then
        for cn in pairs(D6_WEPVIEW_CFG) do
            if cn:match("^weapon_d6_") then set[cn] = true end
        end
    end
    local list = {}
    for cn in pairs(set) do list[#list + 1] = cn end
    table.sort(list)
    return list
end

-- ── Регистрация вкладки/категории ──────────────────────────

hook.Add("AddToolMenuTabs", "D6_WeaponWorkshop", function()
    spawnmenu.AddToolTab("DRMD Workshop", "DRMD Workshop", "icon16/bomb.png")
end)

hook.Add("AddToolMenuCategories", "D6_WeaponWorkshop", function()
    spawnmenu.AddToolCategory("DRMD Workshop", "WeaponEditor", "Редактор оружия")
end)

hook.Add("PopulateToolMenu", "D6_WeaponWorkshop", function()

    -- ═════ 1. ВНЕШНИЙ ВИД ОРУЖИЯ ══════
    spawnmenu.AddToolMenuOption("DRMD Workshop", "WeaponEditor",
        "d6_wep_visual", "Внешний вид оружия", "", "", function(panel)
        panel:ClearControls()

        panel:Help("Редактирование 3D-вида weapon_d6_* через кластеры SCK.")
        panel:Help("Выбери оружие → «Загрузить в SCK» → правь кластеры → «Применить».")

        -- Таблица всех weapon_d6_* с индикатором текущего вида
        local listHolder = vgui.Create("DPanel", panel)
        listHolder:SetTall(210)
        listHolder:Dock(TOP)
        listHolder:DockMargin(4, 6, 4, 4)
        listHolder:SetPaintBackground(false)

        local wepList = vgui.Create("DListView", listHolder)
        wepList:Dock(FILL)
        wepList:SetMultiSelect(false)

        local colClass = wepList:AddColumn("Класс оружия")
        local colStat  = wepList:AddColumn("Вид")
        colStat:SetFixedWidth(80)

        local function GetStatus(cn)
            if D6_SCKBridge and D6_SCKBridge.Has and D6_SCKBridge.Has(cn) then
                local cfg = D6_SCKBridge.Configs[cn]
                return "SCK(" .. ((cfg and #cfg.modules) or 0) .. ")"
            elseif D6_WEPVIEW_CFG and D6_WEPVIEW_CFG[cn] then
                return "wepview"
            else
                return "—"
            end
        end

        local classLines = {}

        local function FillList()
            wepList:Clear()
            classLines = {}
            for _, cn in ipairs(CollectWeaponClasses()) do
                local line = wepList:AddLine(cn, GetStatus(cn))
                line._class   = cn
                classLines[cn] = line
            end
        end
        FillList()

        -- Статус выбранного класса
        local statusLbl = panel:Help("Выбери класс в таблице выше.")

        local function RefreshLine(cn)
            local line = classLines[cn]
            if line and IsValid(line) then
                line:SetColumnText(2, GetStatus(cn))
            end
        end

        local function UpdateStatusLabel(cn)
            if not cn then statusLbl:SetText("Выбери класс в таблице выше.") return end
            local cfg = D6_SCKBridge and D6_SCKBridge.Configs[cn]
            if cfg then
                statusLbl:SetText(cn .. ": SCK-переопределение ("
                    .. #cfg.modules .. " модул., "
                    .. table.Count(cfg.muzzles or {}) .. " мушек)")
            elseif D6_WEPVIEW_CFG and D6_WEPVIEW_CFG[cn] then
                statusLbl:SetText(cn .. ": wepview ("
                    .. #D6_WEPVIEW_CFG[cn] .. " слотов), SCK-переопределения нет")
            else
                statusLbl:SetText(cn .. ": без переопределения (рендер по умолчанию)")
            end
        end

        wepList.OnRowSelected = function(_, _, line)
            UpdateStatusLabel(line._class)
        end

        local function SelectedClass()
            local sel = wepList:GetSelected()
            local line = sel and sel[1]
            return line and line._class or nil
        end

        -- Кнопки управления
        local loadBtn = panel:Button("Загрузить в редактор SCK")
        loadBtn.DoClick = function()
            local class = SelectedClass()
            if not class then
                chat.AddText(Color(255, 80, 80), "[D6 Weapon] Выбери оружие в таблице.")
                return
            end
            local sck = LocalPlayer():GetActiveWeapon()
            if not (IsValid(sck) and sck.IsSCK) then
                RunConsoleCommand("give", "swep_construction_kit")
                chat.AddText(Color(255, 200, 80),
                    "[D6 Weapon] SCK выдан — возьми в руки и нажми «Загрузить» ещё раз.")
                return
            end
            local cfg = D6_SCKBridge and D6_SCKBridge.Configs[class]
            local modules
            if cfg then
                modules = cfg.modules
            elseif D6_WEPVIEW_CFG and D6_WEPVIEW_CFG[class] and D6_SCKBridge then
                modules = D6_SCKBridge.WepviewToModules(D6_WEPVIEW_CFG[class])
            end
            if D6_SCKBridge and D6_SCKBridge.ModulesToClusters then
                sck.clusters = D6_SCKBridge.ModulesToClusters(modules or {})
            end
            sck._d6BridgeClass = class
            if sck.CleanMenu then sck:CleanMenu() end
            sck:OpenMenu()
            chat.AddText(Color(80, 255, 80),
                "[D6 Weapon] «" .. class .. "» загружен. Вкладка Clusters → правь → «Применить».")
        end

        local applyBtn = panel:Button("Применить кластеры SCK → выбранный класс")
        applyBtn.DoClick = function()
            local class = SelectedClass()
            if not class then return end
            local sck = LocalPlayer():GetActiveWeapon()
            if not (IsValid(sck) and sck.IsSCK and istable(sck.clusters)) then
                chat.AddText(Color(255, 80, 80),
                    "[D6 Weapon] Возьми SCK с настроенными кластерами и нажми снова.")
                return
            end
            if not D6_SCKBridge then return end
            local modules = D6_SCKBridge.ClustersToModules(sck.clusters)
            if #modules == 0 then
                chat.AddText(Color(255, 80, 80), "[D6 Weapon] Кластеры пусты — нечего применять.")
                return
            end
            D6_SCKBridge.Submit(class, modules)
            timer.Simple(0.3, function()
                RefreshLine(class)
                UpdateStatusLabel(class)
            end)
        end
        panel:ControlHelp("Требует прав admin. Применяется всем игрокам сразу.")

        local clearBtn = panel:Button("Сбросить переопределение выбранного класса")
        clearBtn.DoClick = function()
            local class = SelectedClass()
            if not class then return end
            if not D6_SCKBridge then return end
            D6_SCKBridge.Submit(class, nil)
            timer.Simple(0.3, function()
                RefreshLine(class)
                UpdateStatusLabel(class)
            end)
        end
        panel:ControlHelp("Класс вернётся к встроенному рендеру d6_wepview.")

        local refreshBtn = panel:Button("Обновить таблицу")
        refreshBtn.DoClick = function()
            FillList()
            UpdateStatusLabel(nil)
        end
    end)

    -- ═════ 2. SCK КОНСТРУКТОР ══════
    spawnmenu.AddToolMenuOption("DRMD Workshop", "WeaponEditor",
        "d6_wep_sck", "SCK Конструктор", "", "", function(panel)
        panel:ClearControls()

        if not SCKPresent() then
            panel:Help("SWEP Construction Kit не установлен.")
            panel:Help("Установи SCK addon и перезапусти игру.")
            return
        end

        panel:Help("Сохранённые пресеты SCK  (DATA/swep_construction_kit/)")

        local files = file.Find("swep_construction_kit/*.txt", "DATA")
        if files and #files > 0 then
            for _, fname in ipairs(files) do
                local presetName = fname:gsub("%.txt$", "")
                local btn = panel:Button("Открыть пресет: " .. presetName)
                btn.DoClick = function()
                    local ply = LocalPlayer()
                    local wep = ply:GetActiveWeapon()
                    if IsValid(wep) and wep.IsSCK then
                        local glondata = file.Read("swep_construction_kit/" .. fname, "DATA")
                        if glondata then
                            local decodeFn = (glon and glon.decode)
                                          or (wep.glon and wep.glon.decode)
                            if decodeFn then
                                local ok, preset = pcall(decodeFn, glondata)
                                if ok and preset then
                                    wep:CleanMenu()
                                    wep:OpenMenu(preset)
                                    return
                                end
                            end
                        end
                    end
                    RunConsoleCommand("give", "swep_construction_kit")
                    chat.AddText(Color(255, 200, 80),
                        "[D6 Workshop] SCK выдан. Возьми в руки и загрузи пресет: " .. presetName)
                end
            end
        else
            panel:Help("Пресетов пока нет.")
            panel:Help("Возьми SCK (Слот 6), собери оружие и сохрани пресет.")
        end

        panel:Help(" ")
        local expBtn = panel:Button("Экспорт всех пресетов в консоль")
        expBtn.DoClick = function()
            local expFiles = file.Find("swep_construction_kit/*.txt", "DATA")
            if not expFiles or #expFiles == 0 then
                chat.AddText(Color(255, 60, 60), "[D6 Workshop] Нет пресетов для экспорта.")
                return
            end
            MsgN("\n-- DRMD Weapon Workshop: Batch Export --")
            for _, fname in ipairs(expFiles) do
                local name    = fname:gsub("%.txt$", "")
                local gdata   = file.Read("swep_construction_kit/" .. fname, "DATA")
                local decodeFn = glon and glon.decode
                if decodeFn and gdata then
                    local ok, pre = pcall(decodeFn, gdata)
                    if ok and pre then
                        MsgN("\n-- Пресет: " .. name)
                        if pre.clusters and SCK_GetClustersText then
                            MsgN(SCK_GetClustersText(pre.clusters))
                        end
                        if pre.v_models then
                            for k, v in pairs(pre.v_models) do
                                MsgN('  ["' .. k .. '"] = { type="' .. (v.type or "Model")
                                    .. '", model="' .. (v.model or "") .. '" }')
                            end
                        end
                    end
                end
            end
            MsgN("-- Конец экспорта --\n")
            chat.AddText(Color(80, 255, 80), "[D6 Workshop] Экспорт завершён.")
        end
    end)

    -- ═════ 3. АРХЕТИПЫ ══════
    spawnmenu.AddToolMenuOption("DRMD Workshop", "WeaponEditor",
        "d6_wep_archetypes", "Архетипы оружия", "", "", function(panel)
        panel:ClearControls()

        if not SCKPresent() then
            panel:Help("SWEP Construction Kit не установлен.")
            return
        end

        panel:Help("Готовые шаблоны кластеров для быстрого старта в SCK.")
        panel:Help("SCK должен быть в руках; кластеры загружаются напрямую.")
        panel:Help(" ")

        for _, arch in ipairs(D6_ARCHETYPES) do
            panel:Help(arch.label .. "  —  " .. arch.desc)
            local btn = panel:Button("Загрузить: " .. arch.label)
            local archCopy = arch
            btn.DoClick = function()
                local ply = LocalPlayer()
                local function Apply(w)
                    w.clusters = table.FullCopy(archCopy.clusters)
                    if not w.Frame then w:OpenMenu() end
                    if w.Frame then w.Frame:SetVisible(true) end
                    chat.AddText(Color(255, 220, 40),
                        "[D6 Workshop] Архетип загружен: " .. archCopy.label)
                end
                local wep = ply:GetActiveWeapon()
                if IsValid(wep) and wep.IsSCK then
                    Apply(wep)
                else
                    RunConsoleCommand("give", "swep_construction_kit")
                    timer.Simple(0.5, function()
                        local w = LocalPlayer():GetActiveWeapon()
                        if IsValid(w) and w.IsSCK then Apply(w) end
                    end)
                end
            end
            panel:Help(" ")
        end
    end)

    -- ═════ 4. ГЕНЕРАЦИЯ SWEP ══════
    spawnmenu.AddToolMenuOption("DRMD Workshop", "WeaponEditor",
        "d6_wep_generated", "Генерация SWEP", "", "", function(panel)
        panel:ClearControls()

        if not SCKPresent() then
            panel:Help("SWEP Construction Kit не установлен.")
            return
        end

        panel:Help("Сгенерированные SWEP-файлы  (DATA/drmd_weapons/generated/)")
        panel:Help("SCK → вкладка Stats → «Copy SWEP Lua» для генерации нового файла.")
        panel:Help(" ")

        local genFiles = file.Find("drmd_weapons/generated/*.lua", "DATA")
        if genFiles and #genFiles > 0 then
            for _, fname in ipairs(genFiles) do
                panel:Help("• " .. fname:gsub("%.lua$", ""))
            end
        else
            panel:Help("Нет сгенерированных файлов.")
        end

        panel:Help(" ")
        panel:Help("Живые конфиги  (DATA/drmd_weapons/)")

        local cfgFiles = file.Find("drmd_weapons/*.txt", "DATA")
        if cfgFiles and #cfgFiles > 0 then
            for _, fname in ipairs(cfgFiles) do
                local class = fname:gsub("%.txt$", "")
                local btn = panel:Button("Загрузить конфиг: " .. class)
                btn.DoClick = function()
                    if not SCK_Config then
                        chat.AddText(Color(255, 60, 60), "[D6 Workshop] SCK_Config недоступен.")
                        return
                    end
                    SCK_Config.LoadForClass(class)
                    chat.AddText(Color(80, 255, 80), "[D6 Workshop] Конфиг загружен: " .. class)
                end
            end
        else
            panel:Help("Нет сохранённых конфигов.")
            panel:Help("Правь Stats/Projectile/Flak/Guidance в SCK для создания.")
        end
    end)

    -- ═════ 5. ГРАВИ-ПУШКИ ══════
    spawnmenu.AddToolMenuOption("DRMD Workshop", "WeaponEditor",
        "d6_wep_gravcfg", "Грави-пушки", "", "", function(panel)
        panel:ClearControls()

        if not D6_GravCfg then
            panel:Help("Система настройки грави-пушек не загружена (d6_gravgun_cfg.lua).")
            return
        end

        panel:Help("Живая настройка грави-оружия — дальность, скорость, масса, "
            .. "энергия, перезарядка. Меняй ползунки: применяется сразу всем (нужен admin).")

        -- ── Быстрое сохранение / загрузка пресета ────────────
        panel:Help(" ")
        local presetCombo
        local function ReloadPresetCombo()
            if not IsValid(presetCombo) then return end
            presetCombo:Clear()
            for _, n in ipairs(D6_GravCfg.ListPresets()) do
                presetCombo:AddChoice(n)
            end
        end

        local nameEntry = panel:TextEntry("Имя оружия:")
        nameEntry:SetValue("my_gravgun")

        local saveBtn = panel:Button("⚡ Быстрое сохранение оружия")
        saveBtn.DoClick = function()
            local ok, res = D6_GravCfg.SavePreset(nameEntry:GetValue())
            if ok then
                chat.AddText(Color(80, 255, 80), "[D6 Грави] Сохранено как «" .. res .. "».")
                ReloadPresetCombo()
                presetCombo:SetValue(res)
            else
                chat.AddText(Color(255, 80, 80), "[D6 Грави] Ошибка сохранения: " .. tostring(res))
            end
        end
        panel:ControlHelp("Снимок текущей настройки всех грави-пушек → DATA/drmd_gravcfg/presets/.")

        presetCombo = panel:ComboBox("Пресет:")
        ReloadPresetCombo()

        local loadBtn = panel:Button("Загрузить пресет")
        loadBtn.DoClick = function()
            local n = presetCombo:GetValue()
            if n and n ~= "" and D6_GravCfg.LoadPreset(n) then
                chat.AddText(Color(80, 255, 80), "[D6 Грави] Применяю пресет «" .. n .. "».")
            else
                chat.AddText(Color(255, 80, 80), "[D6 Грави] Выбери пресет в списке.")
            end
        end

        local delBtn = panel:Button("Удалить пресет")
        delBtn.DoClick = function()
            local n = presetCombo:GetValue()
            if n and n ~= "" then
                D6_GravCfg.DeletePreset(n)
                ReloadPresetCombo()
                chat.AddText(Color(255, 200, 80), "[D6 Грави] Удалён пресет «" .. n .. "».")
            end
        end

        -- ── Ползунки по каждому грави-классу ─────────────────
        for _, class in ipairs(D6_GravCfg.Order) do
            local sch = D6_GravCfg.Schema[class]
            if sch then
                panel:Help(" ")
                panel:Help("──  " .. (D6_GravCfg.Names[class] or class) .. "  ──")

                for _, field in ipairs(sch) do
                    local sl = panel:NumSlider(field.label, nil, field.min, field.max, field.dec or 0)
                    sl:SetValue(D6_GravCfg.Get(class, field.key))
                    -- Дебаунс: один net-сабмит через 0.15с после последнего движения
                    local tname = "D6GravSubmit_" .. class .. "_" .. field.key
                    sl.OnValueChanged = function(_, v)
                        if field.int then v = math.floor(v + 0.5) end
                        timer.Create(tname, 0.15, 1, function()
                            D6_GravCfg.Submit(class, field.key, v)
                        end)
                    end
                end

                local resetBtn = panel:Button("Сбросить «" .. (D6_GravCfg.Names[class] or class) .. "» к умолчанию")
                resetBtn.DoClick = function()
                    D6_GravCfg.ResetClass(class)
                    chat.AddText(Color(255, 200, 80),
                        "[D6 Грави] «" .. (D6_GravCfg.Names[class] or class) ..
                        "» сброшено. Переоткрой вкладку для обновления ползунков.")
                end
            end
        end
    end)

end)

-- ── Панель Browse → Other (обзор пресетов и архетипов) ───

hook.Add("PopulateContent", "D6_WeaponWorkshop_Content", function(pnlContent, tree, node)
    local d6node = tree:AddNode("DRMD Weapon Workshop", "icon16/bomb.png")
    d6node.DoClick = function()
        pnlContent:Clear(true)

        local p = vgui.Create("DScrollPanel", pnlContent)
        p:Dock(FILL)

        local function AddHeader(text)
            local lbl = vgui.Create("DLabel", p)
            lbl:SetText(text)
            lbl:SetFont("DermaDefaultBold")
            lbl:SetTall(22)
            lbl:SizeToContentsX()
            lbl:DockMargin(8, 8, 8, 2)
            lbl:Dock(TOP)
        end

        local function AddLine(text)
            local lbl = vgui.Create("DLabel", p)
            lbl:SetText(text)
            lbl:SetTall(18)
            lbl:SizeToContentsX()
            lbl:DockMargin(16, 2, 8, 0)
            lbl:Dock(TOP)
        end

        AddHeader("DRMD 6DOF — Редактор оружия")

        if not SCKPresent() then
            AddLine("SWEP Construction Kit не установлен.")
            AddLine("Установи SCK для полного доступа к конструктору.")
        else
            AddHeader("Сохранённые пресеты SCK")
            local sfiles = file.Find("swep_construction_kit/*.txt", "DATA")
            if sfiles and #sfiles > 0 then
                for _, fn in ipairs(sfiles) do
                    AddLine("• " .. fn:gsub("%.txt$", ""))
                end
            else
                AddLine("(нет — Q → DRMD Workshop → SCK Конструктор)")
            end

            AddHeader("Шаблоны кластеров (архетипы)")
            for _, a in ipairs(D6_ARCHETYPES) do
                AddLine("• " .. a.label .. " — " .. a.desc)
            end

            AddHeader("SCK-переопределения визуала")
            local hasAny = false
            if D6_SCKBridge then
                for cn in pairs(D6_SCKBridge.Configs) do
                    AddLine("• " .. cn)
                    hasAny = true
                end
            end
            if not hasAny then
                AddLine("(нет — Q → DRMD Workshop → Внешний вид оружия)")
            end
        end
    end
end)

print("[D6] d6_weaponworkshop.lua загружен")
