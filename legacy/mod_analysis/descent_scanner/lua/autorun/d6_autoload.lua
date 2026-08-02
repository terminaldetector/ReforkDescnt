-- =========================================================
-- d6_autoload.lua
-- Автозагрузка мода Descent 6DOF
--
-- Логика оружия:
--   - spawn_* и deployer: технические инструменты, всегда есть
--   - Всё остальное из weapons/: выдаётся автоматически при спавне
--   - Внешние SWEPы с Nexus/Workshop просто кладёшь в weapons/ — работают
-- =========================================================

-- ── Технические spawn-инструменты (жёстко прописаны) ─────
local D6_SPAWNERS = {
    "weapon_d6_deployer",
    "weapon_d6_spawn_mine",
    "weapon_d6_spawn_mg",
    "weapon_d6_spawn_rpg",
    "weapon_d6_spawn_grav",
    "weapon_d6_spawn_laser",
    "weapon_d6_spawn_heavy",
    "weapon_d6_spawn_seeker",
    "weapon_d6_spawn_worm",
    "weapon_d6_spawn_manhack",
    "weapon_d6_spawn_squad",
    "weapon_d6_spawn_ammo",
}

-- ── Список классов которые НЕ являются боевым оружием ─────
-- Используется чтобы исключить их из авто-цикла weapon_next
local D6_SPAWNER_SET = {}
for _, v in ipairs(D6_SPAWNERS) do D6_SPAWNER_SET[v] = true end

-- ── AddCSLuaFile для spawn-инструментов ───────────────────
if SERVER then
    for _, w in ipairs(D6_SPAWNERS) do
        AddCSLuaFile("weapons/" .. w .. ".lua")
    end
    AddCSLuaFile("d6_core.lua")
    AddCSLuaFile("d6_client.lua")
    AddCSLuaFile("d6_menu.lua")
    AddCSLuaFile("d6_ang_patch.lua")
    AddCSLuaFile("d6_weapon_registry.lua")
end

-- ── Подключение ядра ──────────────────────────────────────
local function TryInclude(path)
    if file.Exists("lua/" .. path, "GAME") then
        include(path)
    else
        MsgC(Color(255, 100, 0), "[D6] Не найден: " .. path .. "\n")
    end
end

if SERVER then
    TryInclude("d6_core.lua")
    TryInclude("d6_ai.lua")
    TryInclude("d6_frags.lua")
    TryInclude("d6_menu.lua")
    TryInclude("d6_ang_patch.lua")
    TryInclude("d6_weapon_registry.lua")
end

if CLIENT then
    include("d6_core.lua")
    include("d6_client.lua")
    include("d6_menu.lua")
    include("d6_ang_patch.lua")
    include("d6_weapon_registry.lua")
end

-- ── Биндинги (подсказка при входе) ───────────────────────
if CLIENT then
    hook.Add("InitPostEntity", "D6_BindHint", function()
        timer.Simple(2, function()
            chat.AddText(Color(80, 200, 80), "[Descent 6DOF] ", color_white, "Привяжи клавиши:")
            chat.AddText(Color(200, 200, 200), "  bind KP_0      6dof_toggle")
            chat.AddText(Color(200, 200, 200), "  bind KP_ENTER  6dof_alwaysrun")
            chat.AddText(Color(200, 200, 200), "  bind KP_PLUS   d6_weapon_next")
            chat.AddText(Color(200, 200, 200), "  bind KP_DEL    d6_radar_toggle")
            chat.AddText(Color(200, 200, 200), "  bind TAB       d6_menu_toggle")
            chat.AddText(Color(200, 200, 200), "  bind H         6dof_toggle")
            chat.AddText(Color(255, 200, 80), "  bind SHIFT     6dof_dash   ← рывок (любую клавишу!)")
        end)
    end)

    concommand.Add("d6_menu_toggle", function()
        RunConsoleCommand("d6_menu_open")
    end)
end

-- =========================================================
-- СЕРВЕР: автовыдача и управление оружием
-- =========================================================
if SERVER then

    -- Возвращает список ВСЕХ классов оружия в папке weapons/
    -- которые зарегистрированы в GMod (weapons.Get проверяет регистрацию)
    local function GetAllWeaponClasses()
        local result = {}
        local files = file.Find("lua/weapons/weapon_*.lua", "GAME")
        for _, fname in ipairs(files) do
            local class = fname:match("^(.-)%.lua$")
            if class and weapons.Get(class) then
                table.insert(result, class)
            end
        end
        -- Сортировка для стабильного порядка
        table.sort(result)
        return result
    end

    -- Выдаёт игроку все зарегистрированные оружия
    local function GiveAllWeapons(ply)
        if not IsValid(ply) then return end
        local given = {}

        -- Сначала spawn-инструменты
        for _, class in ipairs(D6_SPAWNERS) do
            if not IsValid(ply:GetWeapon(class)) then
                local ok = pcall(function() ply:Give(class) end)
                if ok then given[#given + 1] = class end
            end
        end

        -- Затем всё остальное из weapons/
        local all = GetAllWeaponClasses()
        for _, class in ipairs(all) do
            if not D6_SPAWNER_SET[class] and not IsValid(ply:GetWeapon(class)) then
                local ok = pcall(function() ply:Give(class) end)
                if ok then given[#given + 1] = class end
            end
        end

        return given
    end

    hook.Add("PlayerSpawn", "D6_GiveWeapons", function(ply)
        -- Задержка: оружия должны успеть зарегистрироваться
        timer.Simple(1.5, function()
            if not IsValid(ply) then return end
            GiveAllWeapons(ply)
        end)
    end)

    -- Принудительная перевыдача через консоль
    concommand.Add("d6_give_weapons", function(ply)
        if not IsValid(ply) then return end
        local given = GiveAllWeapons(ply)
        ply:PrintMessage(HUD_PRINTTALK,
            "[D6] Выдано оружия: " .. (given and #given or 0))
    end)

    -- weapon_next: циклический перебор боевых оружий
    -- (spawn_* и deployer пропускаются)
    concommand.Add("d6_weapon_next", function(ply)
        if not IsValid(ply) then return end
        local all = ply:GetWeapons()

        local combat = {}
        for _, w in ipairs(all) do
            if IsValid(w) and not D6_SPAWNER_SET[w:GetClass()] then
                table.insert(combat, w)
            end
        end
        if #combat == 0 then return end

        table.sort(combat, function(a, b)
            return a:GetClass() < b:GetClass()
        end)

        local cur = ply:GetActiveWeapon()
        local curIdx = 0
        for i, w in ipairs(combat) do
            if IsValid(cur) and w:GetClass() == cur:GetClass() then
                curIdx = i
            end
        end

        local nextIdx = (curIdx % #combat) + 1
        ply:SelectWeapon(combat[nextIdx]:GetClass())
        ply:PrintMessage(HUD_PRINTTALK,
            "[D6] Оружие: " .. combat[nextIdx]:GetPrintName())
    end)

end

print("[D6] d6_autoload.lua OK")
