-- ═══════════════════════════════════════════════════════════
-- d6_shield.lua — Щиты игрока + оборона дронов
--
-- ИГРОК: ЩИТ (D6_Shield) поглощает урон перед HP (КОРПУС).
--        Регенерирует через 4с бездействия; скорость зависит
--        от канала "shields" (D6_Energy).
--
-- ДРОНЫ: ЩИТ → БРОНЯ → КОРПУС + резисты + крит-модуль
--        + взрыв реактора при гибели.
--        Инициализируется в D6_BuildDrone (d6_modules.lua).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

-- ── Константы игрока ─────────────────────────────────────
local SHIELD_DEFAULT     = 100
local SHIELD_REGEN_RATE  = 12      -- ед/с (базовая)
local SHIELD_REGEN_DELAY = 4       -- сек до начала регена

-- ── Константы дронов ─────────────────────────────────────
local CRIT_MODULE_CHANCE = 0.15    -- вероятность повредить модуль при ударе в корпус
local REACTOR_CHANCE     = 0.35    -- вероятность взрыва реактора при гибели
local REACTOR_RAD        = 340
local REACTOR_DMG        = 200

-- ─────────────────────────────────────────────────────────
--  ЧАСТЬ 1: ЩИТ ИГРОКА
-- ─────────────────────────────────────────────────────────
if SERVER then

    -- Инициализация NWVar щита (вызывается из D6_Energy.EnsureInit)
    function D6_Shield_InitPlayer(ply)
        if ply:GetNWFloat("D6_ShieldMax", -1) >= 0 then return end
        ply:SetNWFloat("D6_ShieldMax", SHIELD_DEFAULT)
        ply:SetNWFloat("D6_Shield",    SHIELD_DEFAULT)
    end

    -- Поглощение урона игрока щитом
    hook.Add("EntityTakeDamage", "D6_PlayerShield", function(target, dmginfo)
        if not target:IsPlayer() then return end
        if not target:GetNWBool("D6On", false) then return end

        local shield = target:GetNWFloat("D6_Shield", 0)
        if shield <= 0 then return end

        local dmg = dmginfo:GetDamage()
        if dmg <= 0 then return end

        target.D6_ShieldLastHit = CurTime()

        local absorb   = math.min(shield, dmg)
        local overflow = dmg - absorb

        target:SetNWFloat("D6_Shield", math.max(0, shield - absorb))

        dmginfo:SetDamage(overflow)

        -- Лёгкая вспышка: показывает удар по щиту
        local ef = EffectData()
        ef:SetOrigin(target:WorldSpaceCenter())
        ef:SetScale(2)
        util.Effect("cball_bounce", ef)
    end)

    -- Реген щита (интегрирован в D6_Energy_Regen Think)
    function D6_Shield_RegenTick(ply, dt)
        if not IsValid(ply) then return end
        local last = ply.D6_ShieldLastHit or 0
        if (CurTime() - last) < SHIELD_REGEN_DELAY then return end

        local shield    = ply:GetNWFloat("D6_Shield", 0)
        local shieldMax = ply:GetNWFloat("D6_ShieldMax", SHIELD_DEFAULT)
        if shield >= shieldMax then return end

        local alloc = D6_Energy and D6_Energy.GetAlloc(ply, "shields") or 0.33
        local rate  = SHIELD_REGEN_RATE * (0.5 + alloc)
        local newSh = math.min(shieldMax, shield + rate * dt)
        ply:SetNWFloat("D6_Shield", newSh)
    end

end  -- SERVER

-- ─────────────────────────────────────────────────────────
--  ЧАСТЬ 2: ОБОРОНА ДРОНОВ  (ЩИТ → БРОНЯ → КОРПУС)
--  Активируется только когда target.D6_Shield установлен
--  (инициализируется в D6_BuildDrone).
-- ─────────────────────────────────────────────────────────
if SERVER then

    -- Классификация типа урона (по образцу D6Frags_ChopperArmor)
    local function ClassifyDmg(dtype)
        if bit.band(dtype, DMG_BLAST) ~= 0 then return "explosive" end
        if bit.band(dtype, DMG_ENERGYBEAM + DMG_SHOCK) ~= 0 then return "energy" end
        return "kinetic"
    end

    hook.Add("EntityTakeDamage", "D6_DroneDefense", function(target, dmginfo)
        if not IsValid(target) then return end
        -- Только дроны с установленными слоями защиты
        if target.D6_Shield == nil then return end

        local dmg     = dmginfo:GetDamage()
        if dmg <= 0 then return end

        local attacker = dmginfo:GetAttacker()
        local dtype    = dmginfo:GetDamageType()
        local kind     = ClassifyDmg(dtype)

        -- Применяем резист
        local resist = (target.D6_Resist or {})[kind] or 0
        dmg = dmg * (1 - math.Clamp(resist, 0, 0.9))

        -- Слой 1: ЩИТ
        if target.D6_Shield > 0 then
            local absorb   = math.min(target.D6_Shield, dmg)
            target.D6_Shield = target.D6_Shield - absorb
            dmg = dmg - absorb

            local ef = EffectData(); ef:SetOrigin(target:WorldSpaceCenter()); ef:SetScale(1.5)
            util.Effect("cball_bounce", ef)

            if dmg <= 0 then dmginfo:SetDamage(0); return end
        end

        -- Слой 2: БРОНЯ
        if target.D6_Armor ~= nil and target.D6_Armor > 0 then
            local absorb   = math.min(target.D6_Armor, dmg)
            target.D6_Armor = target.D6_Armor - absorb
            dmg = dmg - absorb

            if dmg <= 0 then dmginfo:SetDamage(0); return end
        end

        -- Слой 3: КОРПУС (нативный HP) — пробрасываем overflow
        -- Случайное повреждение модуля при ударе в корпус
        if target.D6_Modules and math.random() < CRIT_MODULE_CHANCE then
            local mods = {}
            for slot, mod in pairs(target.D6_Modules) do
                if mod and not mod.disabledUntil then mods[#mods+1] = mod end
            end
            if #mods > 0 then
                local hit = mods[math.random(#mods)]
                hit.disabledUntil = CurTime() + 8
            end
        end

        dmginfo:SetDamage(dmg)
    end)

    -- Взрыв реактора при гибели дрона (один раз)
    hook.Add("OnNPCKilled", "D6_DroneReactor", function(npc, attacker, inflictor)
        if not IsValid(npc) then return end
        if npc.D6_Shield == nil then return end   -- не наш дрон
        if npc.D6_Exploded then return end
        npc.D6_Exploded = true

        if math.random() > REACTOR_CHANCE then return end

        local pos = npc:GetPos()
        timer.Simple(0.05, function()
            if type(D6_Explode) == "function" then
                D6_Explode(IsValid(attacker) and attacker or game.GetWorld(), pos, REACTOR_RAD, REACTOR_DMG)
            else
                local atk = IsValid(attacker) and attacker or game.GetWorld()
                local ef  = EffectData(); ef:SetOrigin(pos); ef:SetScale(6); ef:SetMagnitude(REACTOR_DMG)
                util.Effect("HelicopterMegaBomb", ef)
                util.BlastDamage(atk, atk, pos, REACTOR_RAD, REACTOR_DMG)
                sound.Play("npc/combine_gunship/explosion1.wav", pos, 115, 90)
            end
        end)
    end)

end  -- SERVER

print("[D6] d6_shield.lua OK")
