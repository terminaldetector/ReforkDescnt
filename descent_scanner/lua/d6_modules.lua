-- ═══════════════════════════════════════════════════════════
-- d6_modules.lua — Модульная система дронов (Stage 8)
--   Регистр модулей, D6_BuildDrone(), единый диспетчер.
--   Хелперы перенесены из d6_ai_roles.lua.
-- ═══════════════════════════════════════════════════════════
if not SERVER then return end

-- ─── Регистр модулей ─────────────────────────────────────
D6_Modules = D6_Modules or {}

function D6_RegisterModule(name, def)
    assert(type(name) == "string", "D6_RegisterModule: name must be string")
    local VALID_SLOTS = { primaryL=true, primaryR=true, utility=true, core=true }
    assert(VALID_SLOTS[def.slot], "D6_RegisterModule: invalid slot '" .. tostring(def.slot) .. "'")
    D6_Modules[name] = def
end

-- ─── Shared helpers (moved from d6_ai_roles.lua) ─────────
local _D6ModCache  = {}
local _D6ModCacheT = 0

local function RefreshModCache()
    local ct = CurTime()
    if ct - _D6ModCacheT > 0.10 then
        _D6ModCache  = ents.FindByClass("npc_cscanner")
        _D6ModCacheT = ct
    end
end

local function RolePhys(npc)
    local ph = npc:GetPhysicsObject()
    return IsValid(ph) and ph or nil
end

local function NearestPlayer(pos, maxDist)
    local best, bestD = nil, (maxDist or 3000)
    for _, ply in ipairs(player.GetAll()) do
        if IsValid(ply) and ply:Alive() then
            local d = pos:Distance(ply:GetPos())
            if d < bestD then bestD = d; best = ply end
        end
    end
    return best, bestD
end

-- Lerp6 replaced by D6_FlightControl below; kept for reference only.
-- local function Lerp6(phys, target)
--     phys:SetVelocity(LerpVector(FrameTime() * 5, phys:GetVelocity(), target))
-- end

local function BulletHit(_, tr, _)
    if tr.Hit then
        local ef = EffectData()
        ef:SetOrigin(tr.HitPos); ef:SetNormal(tr.HitNormal); ef:SetScale(0.7)
        util.Effect("AR2Impact", ef)
    end
end

-- ─── Act 3: Flight Evolution — Stage 9-12 helpers ────────

-- Default physics profile (fallback when LOADOUT has no phys={} table).
-- Phase C: promoted legacy→physics (Stage 9-12 is now the default path),
-- + angMax clamp (Stage 10 stability).
local D6_DEFAULT_PHYS = {
    mass       = 80,
    spoolUp    = 8,   spoolDown = 3,
    velLerp    = 5,   maxSpeed  = 700,
    angPower   = 220, angInertia = 0.88, angSpinSup = 0.20, angMax = 400,
    flightMode = "physics",
}

-- Stage 9: Orientation policy
-- Yaw → face target; Pitch → blend velocity/target; Roll → emergent bank.
-- Phase C: no-target fallback (orient to travel + auto-level) so patrolling
-- and idle drones no longer freeze their facing.
local function D6_ResolveOrientation(npc, target)
    local des    = npc.D6_DesiredAng
    local vel    = npc.D6_Vel
    local moving = vel:Length() > 50

    if IsValid(target) then
        local toTarget = target:GetPos() - npc:GetPos()
        local desYaw   = toTarget:Angle().y
        local tgtPitch = toTarget:Angle().p
        local velPitch = moving and vel:Angle().p or tgtPitch
        local desPitch = tgtPitch + (velPitch - tgtPitch) * 0.6
        -- Roll: bank into yaw rate (mirrors d6_ai.lua:274 proven formula)
        des.p = desPitch
        des.y = desYaw
        des.r = math.Clamp(npc.D6_AngVel.y * -0.4, -50, 50)
    elseif moving then
        -- No target: face direction of travel, gentle bank, level wings.
        local va = vel:Angle()
        des.y = va.y
        des.p = va.p
        des.r = math.Clamp(npc.D6_AngVel.y * -0.4, -30, 30)
    else
        -- Idle: hold heading, level out pitch/roll.
        des.p = des.p * 0.9
        des.r = des.r * 0.8
    end
end

-- Stage 10: Angular spring-damper — moves D6_BodyAng toward D6_DesiredAng.
-- Phase C: per-axis angular-velocity clamp (fp.angMax) prevents over-rotation.
local function D6_StepAngular(npc, fp, ft)
    local av    = npc.D6_AngVel
    local body  = npc.D6_BodyAng
    local des   = npc.D6_DesiredAng
    local ai    = math.pow(fp.angInertia, ft * 60)
    local ap    = fp.angPower
    local maxAV = fp.angMax or 400

    av.y  = math.Clamp(av.y * ai + math.NormalizeAngle(des.y - body.y) * ap * ft, -maxAV, maxAV)
    body.y = math.NormalizeAngle(body.y + av.y * ft)

    av.p  = math.Clamp(av.p * ai + math.NormalizeAngle(des.p - body.p) * ap * ft, -maxAV, maxAV)
    body.p = math.Clamp(body.p + av.p * ft, -89, 89)

    av.r  = math.Clamp(av.r * ai + math.NormalizeAngle(des.r - body.r) * ap * ft, -maxAV, maxAV)
    body.r = math.NormalizeAngle(body.r + av.r * ft)

    npc:SetAngles(body)
    local ph = RolePhys(npc)
    if ph then ph:AddAngleVelocity(-ph:GetAngleVelocity() * fp.angSpinSup) end
end

-- Stage 11/12: Flight controller
-- legacy mode: bit-identical Lerp6 reproduction (Phase-0 non-breaking migration)
-- physics mode: two-stage spool (ThrustCur → DesiredVel) + inertia (Vel → ThrustCur)
local function D6_FlightControl(npc, fp, phys, ft)
    if fp.flightMode == "legacy" then
        if npc.D6_DesiredVel == nil then return end
        local rate = npc.D6_DesiredVelRate or 5
        phys:SetVelocity(LerpVector(ft * rate, phys:GetVelocity(), npc.D6_DesiredVel))
        return
    end

    -- Physics mode: spool ThrustCur, apply inertia via velLerp
    if npc.D6_DesiredVel ~= nil then
        local sp = (npc.D6_DesiredVel:LengthSqr() >= npc.D6_ThrustCur:LengthSqr())
                   and fp.spoolUp or fp.spoolDown
        npc.D6_ThrustCur = LerpVector(ft * sp, npc.D6_ThrustCur, npc.D6_DesiredVel)
    else
        -- Coast: engine winds down
        npc.D6_ThrustCur = LerpVector(ft * fp.spoolDown, npc.D6_ThrustCur, Vector(0,0,0))
    end

    npc.D6_Vel = LerpVector(ft * fp.velLerp, npc.D6_Vel, npc.D6_ThrustCur)
    local speed = npc.D6_Vel:Length()
    if speed > fp.maxSpeed then npc.D6_Vel = npc.D6_Vel * (fp.maxSpeed / speed) end
    if speed > 0.5 then phys:SetVelocity(npc.D6_Vel) end
end

-- ═══════════════════════════════════════════════════════════
-- Stage 15 — Collision Avoidance
-- Bends D6_DesiredVel away from world geometry BEFORE the flight
-- controller integrates it. Forward + horizontal whiskers; steer along
-- hit normals weighted by closeness. World brushes only (cheap), so it
-- never fights props/other drones. Additive — DesiredVel still owned by
-- the behavior; we only rotate it.
-- ═══════════════════════════════════════════════════════════
local _AVOID_MASK = MASK_SOLID_BRUSHONLY
local function D6_AvoidObstacles(npc, fp, ft)
    local dv = npc.D6_DesiredVel
    if dv == nil then return end
    local speed = dv:Length()
    if speed < 1 then return end

    local dir   = dv / speed
    local pos   = npc:GetPos()
    local look  = math.Clamp(speed * 0.45, 90, 420)  -- lookahead scales with speed
    local right = dir:Cross(Vector(0, 0, 1)):GetNormalized()

    local steer, hit = Vector(0, 0, 0), false
    local function probe(d, weight)
        local tr = util.TraceLine({ start = pos, endpos = pos + d * look, filter = npc, mask = _AVOID_MASK })
        if tr.Hit and tr.Fraction < 1 then
            hit = true
            steer = steer + tr.HitNormal * (1 - tr.Fraction) * weight
        end
    end

    probe(dir, 1.0)
    probe((dir + right * 0.6):GetNormalized(), 0.6)
    probe((dir - right * 0.6):GetNormalized(), 0.6)

    if hit then
        local strength = fp.avoid or 1.3
        npc.D6_DesiredVel = (dir + steer * strength):GetNormalized() * speed
    end
end

-- ═══════════════════════════════════════════════════════════
-- Stage 14 — Advanced Maneuvers (orientation-only flair)
-- Barrel Roll / Split-S / Immelmann driven on D6_BodyAng directly (no
-- position teleports; velocity keeps flowing from combat motion). Module
-- fire stays angle-independent, so accuracy is never degraded.
-- ═══════════════════════════════════════════════════════════
local function D6_StartManeuver(npc, kind, ct)
    local body = npc.D6_BodyAng
    npc.D6_Maneuver = {
        kind = kind, t0 = ct,
        dur  = (kind == "barrel") and 0.7 or 1.0,
        r0   = body.r, p0 = body.p,
    }
end

-- Returns true while a maneuver controls orientation this tick.
local function D6_StepManeuver(npc, ct)
    local m = npc.D6_Maneuver
    if not m then return false end
    local frac = (ct - m.t0) / m.dur
    if frac >= 1 then npc.D6_Maneuver = nil; return false end

    local body = npc.D6_BodyAng
    if m.kind == "barrel" then
        body.r = math.NormalizeAngle(m.r0 + frac * 360)
    elseif m.kind == "split_s" then
        body.r = math.NormalizeAngle(m.r0 + math.min(1, frac * 2) * 180)
        body.p = math.Clamp(m.p0 + math.max(0, frac - 0.5) * 2 * -100, -89, 89)
    elseif m.kind == "immelmann" then
        body.p = math.Clamp(m.p0 + math.min(1, frac * 1.6) * 70, -89, 89)
        body.r = math.NormalizeAngle(m.r0 + math.max(0, frac - 0.6) * 2.5 * 180)
    end

    npc.D6_AngVel.r = 0
    npc:SetAngles(body)
    return true
end

-- ═══════════════════════════════════════════════════════════
-- Phase D — Combat Motion (fight through movement)
-- A per-role pattern state machine that NEVER lets a drone sit still
-- while engaged. Three phases compose the six requested motions:
--   ORBIT  → orbit attack (circle-strafe at preferred radius, firing)
--   RUN    → attack run; vertical bias = dive attack (from above) or
--            climb attack (from below)
--   BREAK  → break-away maneuver (peel off + altitude swing, maybe flair)
--   loop ORBIT→RUN→BREAK→ORBIT = re-engagement loop
-- Per-role style decides preferred radius, how often it commits to a run
-- (aggression), and vertical aggressiveness (vbias). Ranged/support roles
-- (aggression 0, no runs) orbit perpetually instead of hovering.
-- ═══════════════════════════════════════════════════════════
local D6_COMBAT_STYLE = {
    -- assault: aggressive diver — tight orbit, frequent diving runs
    pressure = { orbitR=480, orbitMin=0.8, orbitMax=1.6, runMin=0.8, runMax=1.4,
                 breakMin=0.7, breakMax=1.1, breakIn=220, vbias=0.55, aggression=0.85, spdMul=1.0 },
    -- interceptor: fast circler — longer orbits, quick strafing runs
    flank    = { orbitR=380, orbitMin=1.4, orbitMax=3.0, runMin=0.6, runMax=1.0,
                 breakMin=0.6, breakMax=1.0, breakIn=200, vbias=0.7,  aggression=0.6,  spdMul=1.05 },
    -- artillery: long lazy orbit while shelling, never closes
    standoff = { orbitR=2000, orbitZ=160, orbitMin=3.0, orbitMax=5.0, runMin=0, runMax=0,
                 breakMin=1.0, breakMax=1.6, breakIn=600, vbias=0.2,  aggression=0.0,  spdMul=0.95 },
    -- support: kiting orbit, evasive, no runs
    support  = { orbitR=650, orbitMin=2.0, orbitMax=4.0, runMin=0, runMax=0,
                 breakMin=1.0, breakMax=1.5, breakIn=400, vbias=0.3,  aggression=0.0,  spdMul=1.0 },
    -- heavy_elite: slow orbit + diving charge-runs (charge owned by behavior)
    anchor   = { orbitR=550, orbitMin=1.2, orbitMax=2.2, runMin=1.0, runMax=1.6,
                 breakMin=0.6, breakMax=1.0, breakIn=200, vbias=0.45, aggression=0.7, spdMul=1.0 },
}

local _UP = Vector(0, 0, 1)

-- Dive when above the target, climb when below, else set up a vertical pass.
local function D6_PickRunType(npc, target)
    local dz = npc:GetPos().z - target:GetPos().z
    if dz > 120 then return "dive"
    elseif dz < -120 then return "climb"
    else return (math.random() < 0.5) and "dive" or "climb" end
end

-- Combat motion drives whenever engaged, EXCEPT during behavior-owned
-- special movement (retreat / charge) — those keep the behavior's intent.
local function D6_ShouldFightMoving(npc, target, dist, loadout)
    if not IsValid(target) then return false end
    if dist > (loadout.rangeMax or 800) * 2.4 then return false end  -- still approaching: let behavior run
    local ai, st = npc.D6_AI, npc.D6_RoleState
    if (ai == "pressure" or ai == "standoff") and st == 4 then return false end  -- retreat
    if ai == "anchor" and st == 4 then return false end                          -- charge
    return true
end

local function D6_CombatMotion(npc, loadout, target, dist, ct, style)
    local spd  = (loadout.speed or 500) * (style.spdMul or 1)
    local tpos = target:GetPos()
    local mpos = npc:GetPos()
    local to   = tpos - mpos
    local flat = Vector(to.x, to.y, 0)
    local fd   = flat:Length()
    local flatDir = fd > 1 and (flat / fd) or npc:GetForward()
    local tangent = flatDir:Cross(_UP):GetNormalized()

    local phase = npc.D6_CMPhase or 1   -- 1=ORBIT 2=RUN 3=BREAK
    if not npc.D6_CMSign or npc.D6_CMSign == 0 then
        npc.D6_CMSign = (math.random() < 0.5) and 1 or -1
    end

    if phase == 1 then
        -- ORBIT ATTACK: circle at preferred radius, hold altitude near target.
        local R   = style.orbitR or 500
        local rad = math.Clamp((fd - R) * 0.008, -1, 1)
        local vz  = math.Clamp((tpos.z + (style.orbitZ or 0) - mpos.z) * 0.01, -0.6, 0.6)
        npc.D6_DesiredVel = (tangent * npc.D6_CMSign + flatDir * rad + _UP * vz):GetNormalized() * spd
        if ct > (npc.D6_CMEnd or 0) then
            if (style.runMax or 0) > 0 and math.random() < (style.aggression or 0.5) then
                phase = 2
                npc.D6_CMRunType = D6_PickRunType(npc, target)
                npc.D6_CMEnd = ct + math.Rand(style.runMin or 0.8, style.runMax or 1.4)
            else
                npc.D6_CMEnd = ct + math.Rand(style.orbitMin or 1.5, style.orbitMax or 3.0)
                if math.random() < 0.3 then npc.D6_CMSign = -npc.D6_CMSign end  -- reverse circle
            end
        end

    elseif phase == 2 then
        -- ATTACK RUN: drive at the target; vertical bias = dive or climb.
        local vb   = style.vbias or 0.4
        local vert = (npc.D6_CMRunType == "dive") and -vb
                  or  (npc.D6_CMRunType == "climb") and vb or 0
        npc.D6_DesiredVel = (flatDir + _UP * vert):GetNormalized() * spd
        if ct > (npc.D6_CMEnd or 0) or dist < (style.breakIn or 250) then
            phase = 3
            npc.D6_CMEnd = ct + math.Rand(style.breakMin or 0.7, style.breakMax or 1.1)
            if ct > (npc.D6_ManeuverCD or 0) and math.random() < 0.5 then
                local kinds = { "barrel", "split_s", "immelmann" }
                D6_StartManeuver(npc, kinds[math.random(#kinds)], ct)
                npc.D6_ManeuverCD = ct + math.Rand(4, 7)
            end
        end

    else
        -- BREAK-AWAY: peel past + away, swing altitude opposite the run, re-engage.
        local swing = (npc.D6_CMRunType == "climb") and -0.4 or 0.5  -- climb out of a dive
        npc.D6_DesiredVel = (-flatDir * 0.4 + tangent * npc.D6_CMSign * 0.7 + _UP * swing):GetNormalized() * spd
        if ct > (npc.D6_CMEnd or 0) then
            phase = 1
            npc.D6_CMEnd = ct + math.Rand(style.orbitMin or 1.0, style.orbitMax or 2.0)
            if math.random() < 0.4 then npc.D6_CMSign = -npc.D6_CMSign end
        end
    end

    npc.D6_CMPhase = phase
end

-- ─── Модули ───────────────────────────────────────────────

-- PULSAR — пулемётная очередь (assault, verbatim d6_ai_roles:168-173)
D6_RegisterModule("Pulsar", {
    slot = "primaryL", energyCost = 2, cooldown = 0.15, range = 700,
    OnFire = function(npc, target, ctx)
        local myPos = npc:GetPos()
        npc:FireBullets({
            Num = 2, Src = myPos, Dir = (target:GetPos() - myPos):GetNormalized(),
            Spread = Vector(0.04, 0.04, 0), Tracer = 1, TracerName = "GlowTracer",
            Force = 8, Damage = 10, Attacker = npc, Callback = BulletHit,
        })
        npc:EmitSound("weapons/ar2/ar2_fire1.wav", 68, math.random(110, 125))
    end,
})

-- LASER — точная лучевая очередь (interceptor, verbatim d6_ai_roles:219-224)
D6_RegisterModule("Laser", {
    slot = "primaryL", energyCost = 1, cooldown = 0.06, range = 800,
    OnFire = function(npc, target, ctx)
        local myPos = npc:GetPos()
        npc:FireBullets({
            Num = 1, Src = myPos, Dir = (target:GetPos() - myPos):GetNormalized(),
            Spread = Vector(0.02, 0.02, 0), Tracer = 1, TracerName = "GlowTracer",
            Force = 5, Damage = 8, Attacker = npc, Callback = BulletHit,
        })
        npc:EmitSound("weapons/irifle/irifle_fire2.wav", 68, 150)
    end,
})

-- MISSILE — управляемая ракета (artillery, verbatim d6_ai_roles:278-295)
D6_RegisterModule("Missile", {
    slot = "primaryR", energyCost = 20, cooldown = 2.5, range = 2500,
    OnFire = function(npc, target, ctx)
        local myPos = npc:GetPos()
        local dist  = ctx.dist or myPos:Distance(target:GetPos())
        local pred  = target:GetPos() + target:GetVelocity() * (dist / 2200) + Vector(0, 0, 40)
        local dir   = (pred - myPos):GetNormalized()
        local m = ents.Create("rpg_missile")
        if IsValid(m) then
            m:SetPos(myPos + dir * 60)
            m:SetAngles(dir:Angle())
            m:SetOwner(npc)
            m:Spawn()
            local mp = m:GetPhysicsObject()
            if IsValid(mp) then
                mp:EnableGravity(false); mp:SetVelocity(dir * 2200); mp:Wake()
            end
            m:Activate()
            timer.Simple(6, function() if IsValid(m) then m:Remove() end end)
            npc:EmitSound("weapons/rpg/rocketfire1.wav", 82, 85)
        end
    end,
})

-- COMBALL — плазменный шар (heavy_elite, verbatim d6_ai_roles:411-437)
D6_RegisterModule("ComBall", {
    slot = "primaryR", energyCost = 30, cooldown = 3.5, range = 900,
    OnFire = function(npc, target, ctx)
        local myPos = npc:GetPos()
        local dir   = (target:GetPos() + Vector(0, 0, 40) - myPos):GetNormalized()
        local ball  = ents.Create("prop_combine_ball")
        if IsValid(ball) then
            ball:SetPos(myPos + dir * 100)
            ball:SetAngles(dir:Angle())
            ball:SetOwner(npc)
            ball:SetModel("models/effects/combineball.mdl")
            ball:SetSaveValue("m_nMaxBounces", 0)
            ball:Spawn()
            local bp = ball:GetPhysicsObject()
            if IsValid(bp) then
                bp:EnableGravity(false); bp:EnableDrag(false)
                bp:SetMass(1); bp:SetVelocity(dir * 1000); bp:Wake()
            end
            ball:Activate()
            timer.Simple(4, function()
                if IsValid(ball) then
                    local bPos = ball:GetPos()
                    local ef = EffectData(); ef:SetOrigin(bPos); ef:SetScale(4)
                    util.Effect("cball_explode", ef)
                    util.BlastDamage(npc, npc, bPos, 250, 150)
                    ball:Remove()
                end
            end)
            npc:EmitSound("weapons/ar2/fire1.wav", 90, 60)
        end
    end,
})

-- REPAIRBEAM — лечит союзников в радиусе (support, verbatim d6_ai_roles:319-334)
D6_RegisterModule("RepairBeam", {
    slot = "utility", energyCost = 0, cooldown = 0, range = 0,
    Think = function(npc, ctx)
        if CurTime() < (npc.D6_NextHeal or 0) then return end
        local lo       = npc.D6_Loadout or {}
        local interval = lo.healInterval or 0.5
        local radius   = lo.healRadius   or 300
        local amt      = lo.healAmt      or 4
        npc.D6_NextHeal = CurTime() + interval
        local myPos = npc:GetPos()
        for _, ally in ipairs(ents.FindInSphere(myPos, radius)) do
            if not IsValid(ally) then continue end
            if ally:GetClass() ~= "npc_cscanner" then continue end
            if ally == npc or not ally.D6_AI then continue end
            local curHp = ally:Health(); local maxHp = ally:GetMaxHealth()
            if curHp < maxHp then
                ally:SetHealth(math.min(curHp + amt, maxHp))
                local ef = EffectData(); ef:SetOrigin(ally:GetPos()); ef:SetScale(0.4)
                util.Effect("cball_bounce", ef)
            end
        end
    end,
})

-- SHIELDGENERATOR — регенерирует щит дрона (core, Think-based)
D6_RegisterModule("ShieldGenerator", {
    slot = "core", energyCost = 0, cooldown = 0, range = 0,
    Think = function(npc, ctx)
        if npc.D6_Shield == nil or npc.D6_ShieldMax == nil then return end
        local last = npc.D6_ShieldLastHit or 0
        if (CurTime() - last) < 3 then return end
        if npc.D6_Shield < npc.D6_ShieldMax then
            npc.D6_Shield = math.min(npc.D6_ShieldMax, npc.D6_Shield + 8 * FrameTime())
        end
    end,
})

-- GRAVITYUNIT — гравитационный толчок (utility)
D6_RegisterModule("GravityUnit", {
    slot = "utility", energyCost = 15, cooldown = 3.0, range = 600,
    OnFire = function(npc, target, ctx)
        local myPos = npc:GetPos()
        local dir   = (target:GetPos() - myPos):GetNormalized()
        target:SetVelocity(-dir * 800 + Vector(0, 0, 200))
        local ef = EffectData(); ef:SetOrigin(target:GetPos()); ef:SetScale(3)
        util.Effect("cball_bounce", ef)
        npc:EmitSound("weapons/physcannon/superphys_launch1.wav", 85, 80)
    end,
})

-- ─── D6_BuildDrone ────────────────────────────────────────
-- loadout = {
--   ai, slots={primaryL,primaryR,utility,core}, hp, scale, color, speed,
--   rangeMin, rangeMax, retreatHP, retreatDist, chargeDist, chargeCD, chargeDur, chargeSpeed,
--   healRadius, healAmt, healInterval,
--   shield, shieldMax, armor, armorMax, resist={kinetic,energy,explosive}
-- }
function D6_BuildDrone(loadout, pos, targetPly)
    local npc = ents.Create("npc_cscanner")
    if not IsValid(npc) then return nil end

    npc:SetPos(pos)
    npc:Spawn()
    npc:Activate()

    -- Поведение и состояние
    npc.D6_AI        = loadout.ai
    npc.D6_Loadout   = loadout
    npc.D6_RoleState = 1
    npc.D6_NextAttack = CurTime() + 1
    npc.D6_NextHeal   = CurTime() + (loadout.healInterval or 0.5)
    npc.D6_ChargeCD   = 0
    npc.D6_ChargeOn   = false
    npc.D6_ChargeEnd  = 0

    -- Визуал
    npc:SetRenderMode(RENDERMODE_TRANSCOLOR)
    npc:SetColor(loadout.color or Color(200, 200, 200))
    npc:SetModelScale(loadout.scale or 1.0, 0)
    local _cb = math.floor(12 * (loadout.scale or 1.0))
    npc:SetCollisionBounds(Vector(-_cb, -_cb, math.floor(-_cb * 0.8)), Vector(_cb, _cb, math.floor(_cb * 1.4)))
    npc:SetMaxHealth(loadout.hp or 100)
    npc:SetHealth(loadout.hp or 100)

    -- Враждебность
    if IsValid(targetPly) then
        npc:AddEntityRelationship(targetPly, D_HT, 99)
    else
        for _, ply in ipairs(player.GetAll()) do
            if IsValid(ply) then npc:AddEntityRelationship(ply, D_HT, 99) end
        end
    end

    -- Защитные слои (Stage 7, D6_DroneDefense)
    npc.D6_Shield       = loadout.shield    or 0
    npc.D6_ShieldMax    = loadout.shieldMax or 0
    npc.D6_Armor        = loadout.armor     or 0
    npc.D6_ArmorMax     = loadout.armorMax  or 0
    npc.D6_ShieldLastHit = 0
    npc.D6_Resist       = loadout.resist    or { kinetic=0, energy=0, explosive=0 }

    -- Модульная энергия (отдельно от пула игрока)
    npc.D6_ModEnergyMax = 100
    npc.D6_ModEnergy    = 100

    -- Привязка модулей по слотам
    npc.D6_Modules = {}
    local slots = loadout.slots or {}
    for slot, modName in pairs(slots) do
        if modName and D6_Modules[modName] then
            npc.D6_Modules[slot] = {
                def          = D6_Modules[modName],
                name         = modName,
                lastFire     = 0,
                disabledUntil = nil,
            }
        end
    end

    -- ── Act 3: Flight Evolution fields ───────────────────────
    local fp = loadout.phys or D6_DEFAULT_PHYS
    npc.D6_PhysProfile    = fp
    npc.D6_BodyAng        = Angle(npc:GetAngles())
    npc.D6_DesiredAng     = Angle(npc:GetAngles())
    npc.D6_AngVel         = { p=0, y=0, r=0 }
    npc.D6_Vel            = Vector(0, 0, 0)
    npc.D6_ThrustCur      = Vector(0, 0, 0)
    npc.D6_DesiredVel     = nil
    npc.D6_DesiredVelRate = 5
    -- Combat-motion + maneuver state (Phase C/D)
    npc.D6_CMPhase        = nil
    npc.D6_CMSign         = 0
    npc.D6_CMRunType      = nil
    npc.D6_Maneuver       = nil
    npc.D6_ManeuverCD     = 0
    local bp = RolePhys(npc)
    if bp then
        bp:SetMass(fp.mass or D6_DEFAULT_PHYS.mass)
        bp:EnableGravity(false)
    end

    return npc
end

-- ─── Поведения (движение + машина состояний) ─────────────
local D6_RunBehavior = {}

-- PRESSURE (assault): PATROL/APPROACH/ATTACK/RETREAT
D6_RunBehavior["pressure"] = function(npc, phys, loadout, target, dist, ct)
    local state  = npc.D6_RoleState
    local hpFrac = npc:Health() / math.max(npc:GetMaxHealth(), 1)
    local rHP    = loadout.retreatHP or 0.3
    local rMax   = loadout.rangeMax  or 600
    local spd    = loadout.speed     or 500

    if state == 1 then
        if IsValid(target) and dist < 2000 then state = 2 end
    elseif state == 2 then
        if not IsValid(target) then state = 1
        elseif dist < rMax then state = 3
        elseif hpFrac < rHP then state = 4 end
    elseif state == 3 then
        if not IsValid(target) then state = 1
        elseif dist < 300 or hpFrac < rHP then state = 4
        elseif dist > rMax * 1.4 then state = 2 end
    elseif state == 4 then
        if not IsValid(target) or (dist > 700 and hpFrac > 0.5) then state = 2 end
    end
    npc.D6_RoleState = state

    if IsValid(target) then
        local dir  = (target:GetPos() - npc:GetPos()):GetNormalized()
        if state == 2 then
            npc.D6_DesiredVel = dir * spd
        elseif state == 3 then
            local perp = dir:Cross(Vector(0, 0, 1)):GetNormalized()
            npc.D6_DesiredVel = (dir * 0.3 + perp * 0.7):GetNormalized() * spd
        elseif state == 4 then
            npc.D6_DesiredVel = -dir * spd
        end
    end
    return state == 3
end

-- FLANK (interceptor): PATROL/APPROACH(перпендикулярно)/ATTACK(страйф)
D6_RunBehavior["flank"] = function(npc, phys, loadout, target, dist, ct)
    local state = npc.D6_RoleState
    local rMax  = loadout.rangeMax or 500
    local spd   = loadout.speed    or 750

    if state == 1 then
        if IsValid(target) and dist < 2000 then state = 2 end
    elseif state == 2 then
        if not IsValid(target) then state = 1
        elseif dist < rMax then state = 3 end
    elseif state == 3 then
        if not IsValid(target) then state = 1
        elseif dist > rMax * 1.5 then state = 2 end
    end
    npc.D6_RoleState = state

    if IsValid(target) then
        local dir  = (target:GetPos() - npc:GetPos()):GetNormalized()
        local perp = dir:Cross(Vector(0, 0, 1)):GetNormalized()
        if state == 2 then
            npc.D6_DesiredVel = (dir * 0.5 + perp * 0.5):GetNormalized() * spd
        elseif state == 3 then
            npc.D6_DesiredVel = perp * spd
        end
    end
    return state == 3
end

-- STANDOFF (artillery): PATROL/HOLD/ATTACK/RETREAT
D6_RunBehavior["standoff"] = function(npc, phys, loadout, target, dist, ct)
    local state   = npc.D6_RoleState
    local rMin    = loadout.rangeMin    or 1500
    local rMax    = loadout.rangeMax    or 2500
    local retDist = loadout.retreatDist or 800
    local spd     = loadout.speed       or 220

    if state == 1 then
        if IsValid(target) and dist < 3000 then state = 2 end
    elseif state == 2 then
        if not IsValid(target) then state = 1
        elseif dist < retDist then state = 4
        elseif dist >= rMin then state = 3 end
    elseif state == 3 then
        if not IsValid(target) then state = 1
        elseif dist < retDist then state = 4
        elseif dist < rMin then state = 2 end
    elseif state == 4 then
        if not IsValid(target) then state = 1
        elseif dist > retDist * 1.5 then state = 2 end
    end
    npc.D6_RoleState = state

    if IsValid(target) then
        local dir  = (target:GetPos() - npc:GetPos()):GetNormalized()
        if state == 2 then
            npc.D6_DesiredVel = dist > rMax and dir * spd or Vector(0,0,0)
        elseif state == 3 then
            local perp = dir:Cross(Vector(0, 0, 1)):GetNormalized()
            npc.D6_DesiredVel = perp * (spd * 0.4)
        elseif state == 4 then
            npc.D6_DesiredVel = -dir * spd * 1.5
        end
    end
    return state == 3
end

-- SUPPORT: уходит от игроков (атака — через модуль)
D6_RunBehavior["support"] = function(npc, phys, loadout, target, dist, ct)
    if IsValid(target) and dist < 600 then
        local dir = (target:GetPos() - npc:GetPos()):GetNormalized()
        npc.D6_DesiredVel = -dir * (loadout.speed or 280)
    end
    return IsValid(target) and dist < 400  -- слабая самозащита в ближнем бою
end

-- ANCHOR (heavy_elite): PATROL/APPROACH/ATTACK/CHARGE
D6_RunBehavior["anchor"] = function(npc, phys, loadout, target, dist, ct)
    local state = npc.D6_RoleState
    local rMax  = loadout.rangeMax    or 900
    local spd   = loadout.speed       or 200

    -- Завершение заряда
    if state == 4 and ct > npc.D6_ChargeEnd then
        state = 3
        npc.D6_ChargeCD = ct + (loadout.chargeCD or 8)
        npc.D6_ChargeOn = false
        local myPos = npc:GetPos()
        for _, e in ipairs(ents.FindInSphere(myPos, 100)) do
            if IsValid(e) and (e:IsPlayer() or e:IsNPC()) and e ~= npc then
                util.BlastDamage(npc, npc, myPos, 100, 80)
                npc:EmitSound("ambient/explosions/explode_1.wav", 80, 110)
                break
            end
        end
    end

    if state ~= 4 then
        if state == 1 then
            if IsValid(target) and dist < 2500 then state = 2 end
        elseif state == 2 then
            if not IsValid(target) then state = 1
            elseif dist < rMax then state = 3 end
        elseif state == 3 then
            if not IsValid(target) then state = 1
            elseif dist > rMax * 1.3 then state = 2
            elseif dist < (loadout.chargeDist or 600) and ct > npc.D6_ChargeCD then
                state = 4
                npc.D6_ChargeOn  = true
                npc.D6_ChargeEnd = ct + (loadout.chargeDur or 0.4)
            end
        end
        npc.D6_RoleState = state
    end

    if IsValid(target) then
        local dir = (target:GetPos() - npc:GetPos()):GetNormalized()
        if state == 2 then
            npc.D6_DesiredVel = dir * spd
        elseif state == 3 then
            local perp = dir:Cross(Vector(0, 0, 1)):GetNormalized()
            npc.D6_DesiredVel = (dir * 0.5 + perp * 0.5):GetNormalized() * spd
        elseif state == 4 then
            npc.D6_DesiredVelRate = 8  -- charge uses faster lerp rate in legacy mode
            npc.D6_DesiredVel = dir * (loadout.chargeSpeed or 900)
        end
    end
    return state == 3
end

-- ─── Единый диспетчер ────────────────────────────────────
hook.Add("Think", "D6_RoleDispatch", function()
    RefreshModCache()
    local ct = CurTime()
    local ft = FrameTime()
    if ft <= 0 then return end

    for _, npc in ipairs(_D6ModCache) do
        if not IsValid(npc) or not npc.D6_AI then continue end
        local phys = RolePhys(npc)
        if not phys then continue end

        local loadout = npc.D6_Loadout or {}
        local myPos   = npc:GetPos()
        local scanDist = math.max(loadout.rangeMax or 900, 900) * 2
        local target, dist = NearestPlayer(myPos, scanDist)

        -- Reset per-tick movement intent (behavior will set if actively moving)
        npc.D6_DesiredVel     = nil
        npc.D6_DesiredVelRate = 5

        local behavior = D6_RunBehavior[npc.D6_AI]
        local inAttack = behavior and behavior(npc, phys, loadout, target, dist, ct) or false

        local fp = npc.D6_PhysProfile or D6_DEFAULT_PHYS

        -- Phase D: combat motion drives every engaged drone (fight through
        -- movement). Retreat/charge stay with the behavior's own intent.
        local style = D6_COMBAT_STYLE[npc.D6_AI]
        if style and D6_ShouldFightMoving(npc, target, dist, loadout) then
            D6_CombatMotion(npc, loadout, target, dist, ct, style)
        end

        -- Stage 15: collision avoidance bends DesiredVel before integration
        D6_AvoidObstacles(npc, fp, ft)

        -- Stages 9/10/14: orientation (maneuver override → else target/travel) → angular
        if not D6_StepManeuver(npc, ct) then
            D6_ResolveOrientation(npc, IsValid(target) and target or nil)
            D6_StepAngular(npc, fp, ft)
        end

        -- Stages 11/12: flight controller integrates D6_Vel → physics
        D6_FlightControl(npc, fp, phys, ft)

        -- Модульный огонь и Think
        if npc.D6_Modules and IsValid(target) then
            for slot, modEntry in pairs(npc.D6_Modules) do
                if not modEntry then continue end
                -- Восстановление повреждённого модуля
                if modEntry.disabledUntil then
                    if ct >= modEntry.disabledUntil then
                        modEntry.disabledUntil = nil
                    else
                        continue
                    end
                end

                local def = modEntry.def
                if not def then continue end

                -- Think (всегда)
                if def.Think then
                    def.Think(npc, { target = target, dist = dist, ct = ct })
                end

                -- OnFire: только в фазе атаки, с кулдауном, дальностью, энергией
                if def.OnFire and inAttack then
                    local cd = def.cooldown or 1
                    if ct >= modEntry.lastFire + cd then
                        if dist <= (def.range or 1000) then
                            local cost = def.energyCost or 0
                            if npc.D6_ModEnergy >= cost then
                                npc.D6_ModEnergy  = npc.D6_ModEnergy - cost
                                modEntry.lastFire = ct
                                def.OnFire(npc, target, { dist = dist, ct = ct })
                            end
                        end
                    end
                end
            end
        end

        -- Реген щита дрона (базовый, если нет ShieldGenerator)
        if npc.D6_Shield ~= nil and npc.D6_ShieldMax and npc.D6_Shield < npc.D6_ShieldMax then
            local last = npc.D6_ShieldLastHit or 0
            if (ct - last) > 5 and not (npc.D6_Modules and npc.D6_Modules["core"]) then
                npc.D6_Shield = math.min(npc.D6_ShieldMax, npc.D6_Shield + 4 * ft)
            end
        end

        -- Реген модульной энергии
        local modMax = npc.D6_ModEnergyMax or 100
        if (npc.D6_ModEnergy or 0) < modMax then
            npc.D6_ModEnergy = math.min(modMax, (npc.D6_ModEnergy or 0) + 20 * ft)
        end
    end
end)

print("[D6] d6_modules.lua OK")
