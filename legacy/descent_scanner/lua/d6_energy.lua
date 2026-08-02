-- ═══════════════════════════════════════════════════════════
-- d6_energy.lua — D6 Energy Framework (Stage 6)
--
-- Единый энергорезерв игрока, разделённый на три канала
-- распределением (Weapons / Shields / Engines). Один общий пул
-- D6_Energy; распределение масштабирует скорость регена и
-- эффективность каналов. Пресеты Assault/Interceptor/Siege/Balanced.
--
-- Всё вооружение, форсаж и щиты черпают из одного резерва через
-- D6_Energy.TryConsume(ply, channel, amount).
-- ═══════════════════════════════════════════════════════════

D6_Energy = D6_Energy or {}

local ENERGY_MAX_DEFAULT = 100
local ENERGY_REGEN_BASE  = 8     -- совпадает с прежним ENERGY_REGEN=8 у оружий

-- ─── Пресеты распределения (в процентах, сумма 100) ──────
local PRESETS = {
    balanced    = { weapons = 34, shields = 33, engines = 33 },
    assault     = { weapons = 55, shields = 25, engines = 20 },
    interceptor = { weapons = 25, shields = 20, engines = 55 },
    siege       = { weapons = 45, shields = 45, engines = 10 },
}
D6_Energy.Presets     = PRESETS
D6_Energy.PresetOrder = { "balanced", "assault", "interceptor", "siege" }

-- ─── Геттеры (обе стороны, читают NWVars) ────────────────
function D6_Energy.Get(ply)
    if not IsValid(ply) then return 0 end
    return ply:GetNWFloat("D6_Energy", ENERGY_MAX_DEFAULT)
end

function D6_Energy.GetMax(ply)
    if not IsValid(ply) then return ENERGY_MAX_DEFAULT end
    return ply:GetNWFloat("D6_EnergyMax", ENERGY_MAX_DEFAULT)
end

-- channel: "weapons" | "shields" | "engines"  → доля 0..1
function D6_Energy.GetAlloc(ply, channel)
    if not IsValid(ply) then return 0.33 end
    local key = channel == "weapons" and "D6_AllocWeapons"
             or channel == "shields" and "D6_AllocShields"
             or channel == "engines" and "D6_AllocEngines"
    if not key then return 0 end
    return ply:GetNWFloat(key, 33) / 100
end

function D6_Energy.GetPresetName(ply)
    if not IsValid(ply) then return "balanced" end
    return ply:GetNWString("D6_EnergyPreset", "balanced")
end

-- ═══════════════════════════════════════════════════════════
-- СЕРВЕР: мутация резерва, реген, пресеты
-- ═══════════════════════════════════════════════════════════
if SERVER then

    function D6_Energy.Add(ply, amount)
        if not IsValid(ply) then return end
        local cur = ply:GetNWFloat("D6_Energy", ENERGY_MAX_DEFAULT)
        local max = ply:GetNWFloat("D6_EnergyMax", ENERGY_MAX_DEFAULT)
        ply:SetNWFloat("D6_Energy", math.Clamp(cur + amount, 0, max))
    end

    -- Списать энергию из общего резерва. channel сейчас информационный
    -- (резерв общий); возвращает true если хватило и списано.
    function D6_Energy.TryConsume(ply, channel, amount)
        if not IsValid(ply) then return false end
        amount = amount or 0
        if amount <= 0 then return true end
        local cur = ply:GetNWFloat("D6_Energy", ENERGY_MAX_DEFAULT)
        if cur < amount then return false end
        ply:SetNWFloat("D6_Energy", cur - amount)
        return true
    end

    function D6_Energy.SetPreset(ply, name)
        if not IsValid(ply) then return false end
        name = string.lower(tostring(name or ""))
        local p = PRESETS[name]
        if not p then return false end
        ply:SetNWFloat("D6_AllocWeapons", p.weapons)
        ply:SetNWFloat("D6_AllocShields", p.shields)
        ply:SetNWFloat("D6_AllocEngines", p.engines)
        ply:SetNWString("D6_EnergyPreset", name)
        return true
    end

    -- Прямая установка распределения (admin) — нормализуется к 100.
    function D6_Energy.SetAlloc(ply, w, s, e)
        if not IsValid(ply) then return end
        w = math.max(0, w or 0); s = math.max(0, s or 0); e = math.max(0, e or 0)
        local sum = w + s + e
        if sum <= 0 then w, s, e, sum = 34, 33, 33, 100 end
        ply:SetNWFloat("D6_AllocWeapons", w / sum * 100)
        ply:SetNWFloat("D6_AllocShields", s / sum * 100)
        ply:SetNWFloat("D6_AllocEngines", e / sum * 100)
        ply:SetNWString("D6_EnergyPreset", "custom")
    end

    local function EnsureInit(ply)
        if ply.D6_EnergyInit then return end
        ply.D6_EnergyInit = true
        ply:SetNWFloat("D6_EnergyMax", ENERGY_MAX_DEFAULT)
        ply:SetNWFloat("D6_Energy",    ENERGY_MAX_DEFAULT)
        ply:SetNWFloat("D6_WepEnergy", ENERGY_MAX_DEFAULT)  -- зеркало
        D6_Energy.SetPreset(ply, "balanced")
        -- Инициализация щита (d6_shield.lua)
        if D6_Shield_InitPlayer then D6_Shield_InitPlayer(ply) end
    end
    D6_Energy.EnsureInit = EnsureInit

    -- Реген + зеркалирование D6_Energy → D6_WepEnergy (для старых HUD-баров оружий)
    function D6_Energy.RegenTick(ply, dt)
        local cur = ply:GetNWFloat("D6_Energy", ENERGY_MAX_DEFAULT)
        local max = ply:GetNWFloat("D6_EnergyMax", ENERGY_MAX_DEFAULT)
        if cur < max then
            local rate = ENERGY_REGEN_BASE * (0.5 + D6_Energy.GetAlloc(ply, "weapons"))
            cur = math.min(max, cur + rate * dt)
            ply:SetNWFloat("D6_Energy", cur)
        end
        ply:SetNWFloat("D6_WepEnergy", cur)  -- зеркало для weapons DrawHUD + cockpit
    end

    hook.Add("Think", "D6_Energy_Regen", function()
        local dt = FrameTime()
        if dt <= 0 then return end
        for _, ply in ipairs(player.GetAll()) do
            if IsValid(ply) and ply:Alive() then
                EnsureInit(ply)
                D6_Energy.RegenTick(ply, dt)
                if D6_Shield_RegenTick then D6_Shield_RegenTick(ply, dt) end
            end
        end
    end)

    -- Инициализация при спавне (синхронно с D6_AutoActivate, 0.5s)
    hook.Add("PlayerSpawn", "D6_Energy_Init", function(ply)
        timer.Simple(0.5, function()
            if IsValid(ply) then
                ply.D6_EnergyInit = false
                EnsureInit(ply)
            end
        end)
    end)

    -- ─── Консоль ─────────────────────────────────────────
    concommand.Add("d6_energy_preset", function(ply, _, args)
        if not IsValid(ply) then return end
        local name = args[1]
        if D6_Energy.SetPreset(ply, name) then
            ply:PrintMessage(HUD_PRINTTALK, "[D6] Энергопресет: " .. string.upper(name))
        else
            ply:PrintMessage(HUD_PRINTTALK, "[D6] Неизвестный пресет: " .. tostring(name)
                .. " (balanced/assault/interceptor/siege)")
        end
    end)

    concommand.Add("d6_energy_set", function(ply, _, args)
        if not IsValid(ply) then return end
        if not ply:IsAdmin() and not ply:IsListenServerHost() then
            ply:PrintMessage(HUD_PRINTTALK, "[D6] Нет прав (нужен admin)")
            return
        end
        local w, s, e = tonumber(args[1]), tonumber(args[2]), tonumber(args[3])
        if not (w and s and e) then
            ply:PrintMessage(HUD_PRINTTALK, "[D6] d6_energy_set <weapons> <shields> <engines>")
            return
        end
        D6_Energy.SetAlloc(ply, w, s, e)
        ply:PrintMessage(HUD_PRINTTALK, string.format(
            "[D6] Распределение: W %d / S %d / E %d",
            math.Round(ply:GetNWFloat("D6_AllocWeapons")),
            math.Round(ply:GetNWFloat("D6_AllocShields")),
            math.Round(ply:GetNWFloat("D6_AllocEngines"))))
    end)

else
    -- ─── Клиент: безопасные заглушки мутаторов ───────────
    function D6_Energy.TryConsume() return false end
    function D6_Energy.Add()        end
    function D6_Energy.SetPreset()  end
    function D6_Energy.SetAlloc()   end
    function D6_Energy.RegenTick()  end
end

print("[D6] d6_energy.lua OK")
