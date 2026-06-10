-- ═══════════════════════════════════════════════════════════
-- d6_ai_roles.lua — Ролевые AI для Descent 6DOF
-- Пять ролей мигрированы на модульную систему (d6_modules.lua).
-- SpawnD6Role() — публичный API: энкаунтеры, сущности, консоль.
-- ═══════════════════════════════════════════════════════════
if not SERVER then return end

-- ─── Boids exclusion ─────────────────────────────────────
D6_FragsBoidsExclude                = D6_FragsBoidsExclude or {}
D6_FragsBoidsExclude["assault"]     = true
D6_FragsBoidsExclude["interceptor"] = true
D6_FragsBoidsExclude["artillery"]   = true
D6_FragsBoidsExclude["support"]     = true
D6_FragsBoidsExclude["heavy_elite"] = true

-- ─── Loadouts ─────────────────────────────────────────────
-- Поля: ai, slots, hp, scale, color, speed, rangeMin, rangeMax,
--        retreatHP, retreatDist, chargeDist, chargeCD, chargeDur, chargeSpeed,
--        healRadius, healAmt, healInterval,
--        shield, shieldMax, armor, armorMax, resist={kinetic,energy,explosive}
local LOADOUTS = {
    assault = {
        ai    = "pressure",
        slots = { primaryL = "Pulsar", primaryR = "Pulsar" },
        hp = 200, scale = 1.2, color = Color(255, 80, 80),
        speed = 520, rangeMin = 400, rangeMax = 600,
        retreatHP = 0.30,
        shield = 50, shieldMax = 50, armor = 0, armorMax = 0,
        resist = { kinetic = 0, energy = 0.1, explosive = 0 },
    },
    interceptor = {
        ai    = "flank",
        slots = { primaryL = "Laser", primaryR = "Laser" },
        hp = 150, scale = 1.0, color = Color(80, 255, 200),
        speed = 750, rangeMin = 250, rangeMax = 500,
        shield = 30, shieldMax = 30, armor = 0, armorMax = 0,
        resist = { kinetic = 0, energy = 0, explosive = 0 },
    },
    artillery = {
        ai    = "standoff",
        slots = { primaryR = "Missile" },
        hp = 180, scale = 1.3, color = Color(255, 200, 0),
        speed = 220, rangeMin = 1500, rangeMax = 2500,
        retreatDist = 800,
        shield = 0, shieldMax = 0, armor = 60, armorMax = 60,
        resist = { kinetic = 0.2, energy = 0, explosive = 0.1 },
    },
    support = {
        ai    = "support",
        slots = { utility = "RepairBeam" },
        hp = 220, scale = 1.1, color = Color(100, 255, 100),
        speed = 280, healRadius = 300, healAmt = 4, healInterval = 0.5,
        shield = 40, shieldMax = 40, armor = 20, armorMax = 20,
        resist = { kinetic = 0, energy = 0.15, explosive = 0 },
    },
    heavy_elite = {
        ai    = "anchor",
        slots = { primaryR = "ComBall", core = "ShieldGenerator" },
        hp = 800, scale = 2.0, color = Color(40, 40, 40),
        speed = 200, rangeMin = 500, rangeMax = 900,
        chargeDist = 600, chargeCD = 8, chargeDur = 0.4, chargeSpeed = 900,
        shield = 150, shieldMax = 150, armor = 100, armorMax = 100,
        resist = { kinetic = 0.2, energy = 0.2, explosive = 0.3 },
    },
}

-- ─── Public API — thin wrapper over D6_BuildDrone ─────────
function SpawnD6Role(variant, pos, targetPly)
    local lo = LOADOUTS[variant]
    if not lo then
        MsgC(Color(255, 100, 0), "[D6 Roles] Unknown variant: " .. tostring(variant) .. "\n")
        return nil
    end
    local npc = D6_BuildDrone(lo, pos, targetPly)
    if IsValid(npc) then
        npc.D6_Variant = variant  -- сохраняем для boids-исключения
    end
    return npc
end

-- ─── Console commands ─────────────────────────────────────
for _, v in ipairs({ "assault", "interceptor", "artillery", "support", "heavy_elite" }) do
    local vname = v
    concommand.Add("6dof_spawn_" .. vname, function(ply)
        if not IsValid(ply) then return end
        local tr  = ply:GetEyeTrace()
        local pos = tr.HitPos + tr.HitNormal * 50
        SpawnD6Role(vname, pos, ply)
        ply:PrintMessage(HUD_PRINTTALK, "[D6 Roles] Spawned: " .. vname)
    end)
end

print("[D6] d6_ai_roles.lua loaded")
