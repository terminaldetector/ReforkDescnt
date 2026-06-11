-- =========================================================
-- d6_autoload.lua — Автозагрузка мода Descent 6DOF
-- =========================================================

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

local D6_SPAWNER_SET = {}
for _, v in ipairs(D6_SPAWNERS) do D6_SPAWNER_SET[v] = true end

-- Боевые оружия Descent — явный список для надёжной выдачи при спавне
local D6_COMBAT = {
    "weapon_d6_mg",
    "weapon_d6_plasma",
    "weapon_d6_heavy",
    "weapon_d6_laser",
    "weapon_d6_vulcan",
    "weapon_d6_rockets",
    "weapon_d6_railmk2",
    "weapon_d6_concussion",
    "weapon_d6_homing",
}
local D6_COMBAT_SET = {}
for _, v in ipairs(D6_COMBAT) do D6_COMBAT_SET[v] = true end

if SERVER then
    for _, w in ipairs(D6_SPAWNERS) do
        AddCSLuaFile("weapons/" .. w .. ".lua")
    end
    -- Боевые оружия (отдельные SWEP вместо монолитного омни)
    AddCSLuaFile("weapons/weapon_d6_gravy_railgun.lua")   -- legacy NPC-спавнер "grav"
    AddCSLuaFile("weapons/weapon_d6_railmk2.lua")
    AddCSLuaFile("weapons/weapon_d6_mg.lua")
    AddCSLuaFile("weapons/weapon_d6_plasma.lua")
    AddCSLuaFile("weapons/weapon_d6_heavy.lua")
    AddCSLuaFile("weapons/weapon_d6_laser.lua")
    AddCSLuaFile("weapons/weapon_d6_vulcan.lua")
    AddCSLuaFile("weapons/weapon_d6_rockets.lua")
    AddCSLuaFile("weapons/weapon_d6_concussion.lua")
    AddCSLuaFile("weapons/weapon_d6_homing.lua")
    AddCSLuaFile("d6_core.lua")
    AddCSLuaFile("d6_weapon_core.lua")  -- единый фреймворк оружия (Phase B)
    AddCSLuaFile("d6_client.lua")
    AddCSLuaFile("d6_cockpit.lua")   -- новый кокпит-HUD
    AddCSLuaFile("d6_wepview.lua")   -- DOOM-стиль рендер оружий
    AddCSLuaFile("d6_menu.lua")
    AddCSLuaFile("d6_ang_patch.lua")
    AddCSLuaFile("d6_weapon_registry.lua")
    AddCSLuaFile("d6_nav.lua")       -- nav graph debug overlay
    AddCSLuaFile("d6_energy.lua")    -- энергорезерв (Stage 6)
    AddCSLuaFile("d6_shield.lua")    -- щиты/урон (Stage 7)
end

local function TryInclude(path)
    if file.Exists("lua/" .. path, "GAME") then
        include(path)
    else
        MsgC(Color(255,100,0), "[D6] Не найден: " .. path .. "\n")
    end
end

if SERVER then
    TryInclude("d6_core.lua")
    TryInclude("d6_weapon_core.lua") -- Phase B: фреймворк оружия (после core, до оружий)
    TryInclude("d6_energy.lua")      -- Stage 6: до оружий/щитов/модулей
    TryInclude("d6_ai.lua")
    TryInclude("d6_frags.lua")       -- экспортирует глобальный D6_Explode
    TryInclude("d6_shield.lua")      -- Stage 7: после frags (нужен D6_Explode) и energy
    TryInclude("d6_ang_patch.lua")
    TryInclude("d6_weapon_registry.lua")
    TryInclude("d6_nav.lua")
    TryInclude("d6_modules.lua")     -- Stage 8: регистрирует модули + диспетчер
    TryInclude("d6_ai_roles.lua")    -- тонкий: LOADOUTS + SpawnD6Role-обёртка
    TryInclude("d6_encounter.lua")
    -- Меню — только клиент, но включаем чтобы сработал SERVER-раздел в других файлах
end

if CLIENT then
    include("d6_core.lua")
    include("d6_weapon_core.lua")    -- Phase B: фреймворк оружия (HUD-хелперы, таксономия)
    include("d6_client.lua")
    include("d6_cockpit.lua")        -- кокпит-HUD (не SWEP)
    include("d6_wepview.lua")        -- рендер оружий от 1-го лица
    include("d6_menu.lua")
    include("d6_ang_patch.lua")
    include("d6_weapon_registry.lua")
    include("d6_nav.lua")            -- nav graph client debug
    include("d6_energy.lua")         -- энергорезерв (геттеры для HUD/меню)
    include("d6_shield.lua")         -- щиты (геттеры для cockpit)
end

-- ── Биндинги — подсказка при входе ───────────────────────
if CLIENT then
    hook.Add("InitPostEntity", "D6_BindHint", function()
        timer.Simple(2, function()
            chat.AddText(Color(80,200,80), "[Descent 6DOF] ", color_white, "Привяжи клавиши:")
            chat.AddText(Color(200,200,200), "  bind KP_0      6dof_toggle")
            chat.AddText(Color(200,200,200), "  bind KP_ENTER  6dof_alwaysrun")
            chat.AddText(Color(200,200,200), "  bind SHIFT     6dof_dash")
            chat.AddText(Color(200,200,200), "  bind T         d6_radar_toggle")
            chat.AddText(Color(200,200,200), "  bind TAB       d6_menu_open")
        end)
    end)

    concommand.Add("d6_menu_toggle", function()
        RunConsoleCommand("d6_menu_open")
    end)
end

-- =========================================================
-- СЕРВЕР: авто-выдача оружий
-- =========================================================
if SERVER then

    local function GetAllWeaponClasses()
        local result = {}
        local files  = file.Find("lua/weapons/weapon_*.lua", "GAME")
        for _, fname in ipairs(files) do
            local class = fname:match("^(.-)%.lua$")
            if class and weapons.Get(class) then
                table.insert(result, class)
            end
        end
        table.sort(result)
        return result
    end

    local function GiveAllWeapons(ply)
        if not IsValid(ply) then return end
        local given = {}
        local function TryGive(class)
            if not IsValid(ply:GetWeapon(class)) then
                local ok = pcall(function() ply:Give(class) end)
                if ok then given[#given+1] = class end
            end
        end
        -- Спавнеры NPC
        for _, class in ipairs(D6_SPAWNERS) do TryGive(class) end
        -- Боевые оружия Descent — явный список, не зависит от weapons.Get()
        for _, class in ipairs(D6_COMBAT)   do TryGive(class) end
        -- Внешние оружия Workshop (autoGive=true через D6_RegisterWeapon)
        for _, class in ipairs(GetAllWeaponClasses()) do
            if not D6_SPAWNER_SET[class] and not D6_COMBAT_SET[class] then
                TryGive(class)
            end
        end
        return given
    end

    hook.Add("PlayerSpawn", "D6_GiveWeapons", function(ply)
        timer.Simple(1.5, function()
            if not IsValid(ply) then return end
            GiveAllWeapons(ply)
        end)
    end)

    concommand.Add("d6_give_weapons", function(ply)
        if not IsValid(ply) then return end
        local given = GiveAllWeapons(ply)
        ply:PrintMessage(HUD_PRINTTALK,
            "[D6] Выдано оружия: " .. (given and #given or 0))
    end)

    concommand.Add("d6_weapon_next", function(ply)
        if not IsValid(ply) then return end
        local all    = ply:GetWeapons()
        local combat = {}
        for _, w in ipairs(all) do
            if IsValid(w) and not D6_SPAWNER_SET[w:GetClass()] then
                table.insert(combat, w)
            end
        end
        if #combat==0 then return end
        table.sort(combat, function(a,b) return a:GetClass()<b:GetClass() end)
        local cur = ply:GetActiveWeapon()
        local curIdx = 0
        for i,w in ipairs(combat) do
            if IsValid(cur) and w:GetClass()==cur:GetClass() then curIdx=i end
        end
        local nextIdx=(curIdx%#combat)+1
        ply:SelectWeapon(combat[nextIdx]:GetClass())
        ply:PrintMessage(HUD_PRINTTALK, "[D6] Оружие: "..combat[nextIdx]:GetPrintName())
    end)

end

print("[D6] d6_autoload.lua OK")
