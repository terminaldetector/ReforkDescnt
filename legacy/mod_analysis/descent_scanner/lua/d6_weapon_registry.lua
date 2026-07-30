-- ═══════════════════════════════════════════════════════════
-- d6_weapon_registry.lua
-- Глобальный реестр совместимых SWEP из сторонних weaponset.
--
-- ИДЕЯ:
--   Игрок ставит weaponset из Workshop (Quake III, Doom, Descent
--   ports, Forsaken). Этот файл:
--     1. Регистрирует подходящие классы автоматически.
--     2. Применяет патч углов (стрельба по D6AngSynced с креном).
--     3. Раздаёт оружие при включении 6DOF (если autoGive=true).
--     4. Показывает категории в TAB-меню.
--
-- ИСПОЛЬЗОВАНИЕ ИЗ КОНСОЛИ:
--   lua_run D6_RegisterWeapon("weapon_q3_rocket", {autoGive=true})
--   lua_run print(table.Count(D6_Weapons))
-- ═══════════════════════════════════════════════════════════

D6_Weapons = D6_Weapons or {}

-- Префиксы классов которые мы считаем "своими" по умолчанию.
-- Все weaponset с такими префиксами регистрируются автоматически.
local AUTO_PREFIXES = {
    "weapon_q",          -- Quake III, Quake Live ports
    "weapon_quake",
    "weapon_descent",
    "weapon_forsaken",
    "weapon_doom",
    "weapon_unreal",
    "weapon_d6_",        -- наши собственные (если останутся)
}

-- =========================================================
-- API: регистрация совместимого оружия
-- =========================================================
function D6_RegisterWeapon(class, opts)
    opts = opts or {}
    if type(class) ~= "string" or class == "" then return false end
    D6_Weapons[class] = {
        category   = opts.category   or "External",
        autoGive   = opts.autoGive   or false,
        patchAim   = opts.patchAim   ~= false,
        ammoOnGive = opts.ammoOnGive or {},
        replaces   = opts.replaces,
    }
    return true
end

function D6_UnregisterWeapon(class)
    D6_Weapons[class] = nil
end

function D6_IsRegistered(class)
    return D6_Weapons[class] ~= nil
end

-- =========================================================
-- АВТОДЕТЕКТ: после загрузки всех аддонов сканируем weapons.GetList()
-- =========================================================
local function MatchesPrefix(class)
    for _, prefix in ipairs(AUTO_PREFIXES) do
        if class:sub(1, #prefix) == prefix then return true end
    end
    return false
end

hook.Add("InitPostEntity", "D6_AutoDetectWeapons", function()
    local detected = 0
    for _, w in ipairs(weapons.GetList()) do
        local class = w.ClassName
        if class and MatchesPrefix(class) and not D6_Weapons[class] then
            D6_RegisterWeapon(class, { category = "Auto-detected" })
            detected = detected + 1
        end
    end
    if detected > 0 then
        MsgC(Color(100, 220, 140),
            string.format("[D6 Weapons] Авто-зарегистрировано: %d\n", detected))
    end
end)

-- =========================================================
-- РАЗДАЧА ВНЕШНИХ SWEP ПРИ АКТИВАЦИИ 6DOF (autoGive=true)
-- =========================================================
if SERVER then
    hook.Add("PlayerSpawn", "D6_GiveExternalWeapons", function(ply)
        timer.Simple(1.5, function()
            if not IsValid(ply) then return end
            for class, data in pairs(D6_Weapons) do
                if data.autoGive and not IsValid(ply:GetWeapon(class)) then
                    ply:Give(class)
                    for ammoType, count in pairs(data.ammoOnGive) do
                        ply:GiveAmmo(count, ammoType, true)
                    end
                end
            end
        end)
    end)

    concommand.Add("d6_weapons_list", function(ply)
        local target = ply
        if not IsValid(target) then return end
        target:PrintMessage(HUD_PRINTTALK,
            string.format("[D6 Weapons] Зарегистрировано: %d",
                table.Count(D6_Weapons)))
        for class, data in pairs(D6_Weapons) do
            target:PrintMessage(HUD_PRINTTALK,
                string.format("  %s [%s%s]", class, data.category,
                    data.autoGive and ", autoGive" or ""))
        end
    end)

    concommand.Add("d6_weapons_give_all", function(ply)
        if not IsValid(ply) then return end
        if not ply:IsAdmin() and not ply:IsListenServerHost() then return end
        for class, _ in pairs(D6_Weapons) do
            if not IsValid(ply:GetWeapon(class)) then ply:Give(class) end
        end
        ply:PrintMessage(HUD_PRINTTALK, "[D6 Weapons] Все выданы.")
    end)
end

print("[D6] d6_weapon_registry.lua OK")
