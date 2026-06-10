-- ═══════════════════════════════════════════════════════════
-- d6_ai_roles.lua — Ролевые AI для Descent 6DOF
-- Пять новых вариантов: assault, interceptor, artillery,
--   support, heavy_elite. Отдельные Think-хуки, не конфликтуют
--   с d6_ai.lua. SpawnD6Role() — глобальная функция для карт.
-- ═══════════════════════════════════════════════════════════
if not SERVER then return end

-- ─── Boids exclusion ─────────────────────────────────────
D6_FragsBoidsExclude                = D6_FragsBoidsExclude or {}
D6_FragsBoidsExclude["assault"]     = true
D6_FragsBoidsExclude["interceptor"] = true
D6_FragsBoidsExclude["artillery"]   = true
D6_FragsBoidsExclude["support"]     = true
D6_FragsBoidsExclude["heavy_elite"] = true

-- ─── Role config ─────────────────────────────────────────
local ROLE_CFG = {
    assault = {
        hp = 200, scale = 1.2, color = Color(255, 80, 80),
        speed = 520, rangeMin = 400, rangeMax = 600,
        retreatHP = 0.30,
    },
    interceptor = {
        hp = 150, scale = 1.0, color = Color(80, 255, 200),
        speed = 750, rangeMin = 250, rangeMax = 500,
    },
    artillery = {
        hp = 180, scale = 1.3, color = Color(255, 200, 0),
        speed = 220, rangeMin = 1500, rangeMax = 2500,
        retreatDist = 800,
    },
    support = {
        hp = 220, scale = 1.1, color = Color(100, 255, 100),
        speed = 280, healRadius = 300, healAmt = 4, healInterval = 0.5,
    },
    heavy_elite = {
        hp = 800, scale = 2.0, color = Color(40, 40, 40),
        speed = 200, rangeMin = 500, rangeMax = 900,
        chargeDist = 600, chargeCD = 8, chargeDur = 0.4, chargeSpeed = 900,
    },
}

-- ─── Global spawn function ────────────────────────────────
function SpawnD6Role(variant, pos, targetPly)
    local cfg = ROLE_CFG[variant]
    if not cfg then
        MsgC(Color(255, 100, 0), "[D6 Roles] Unknown variant: " .. tostring(variant) .. "\n")
        return nil
    end

    local npc = ents.Create("npc_cscanner")
    if not IsValid(npc) then return nil end

    npc:SetPos(pos)
    npc:Spawn()
    npc:Activate()

    npc.D6_Variant    = variant
    npc.D6_RoleState  = 1
    npc.D6_NextAttack = CurTime() + 1
    npc.D6_NextHeal   = CurTime() + (cfg.healInterval or 0.5)
    npc.D6_ChargeCD   = 0
    npc.D6_ChargeOn   = false
    npc.D6_ChargeEnd  = 0

    npc:SetRenderMode(RENDERMODE_TRANSCOLOR)
    npc:SetColor(cfg.color)
    npc:SetModelScale(cfg.scale, 0)
    npc:SetMaxHealth(cfg.hp)
    npc:SetHealth(cfg.hp)

    if IsValid(targetPly) then
        npc:AddEntityRelationship(targetPly, D_HT, 99)
    else
        for _, ply in ipairs(player.GetAll()) do
            if IsValid(ply) then npc:AddEntityRelationship(ply, D_HT, 99) end
        end
    end

    return npc
end

-- ─── Shared helpers ──────────────────────────────────────
local _D6RoleCache  = {}
local _D6RoleCacheT = 0

local function RefreshCache()
    local ct = CurTime()
    if ct - _D6RoleCacheT > 0.10 then
        _D6RoleCache  = ents.FindByClass("npc_cscanner")
        _D6RoleCacheT = ct
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

local function Lerp6(phys, target)
    phys:SetVelocity(LerpVector(FrameTime() * 5, phys:GetVelocity(), target))
end

-- ─── ASSAULT ─────────────────────────────────────────────
-- States: 1=PATROL 2=APPROACH 3=ATTACK 4=RETREAT
local function BulletHit(_, tr, _)
    if tr.Hit then
        local ef = EffectData(); ef:SetOrigin(tr.HitPos); ef:SetNormal(tr.HitNormal); ef:SetScale(0.7)
        util.Effect("AR2Impact", ef)
    end
end

hook.Add("Think", "D6_Role_Assault", function()
    RefreshCache()
    local ct  = CurTime()
    local cfg = ROLE_CFG.assault
    for _, npc in ipairs(_D6RoleCache) do
        if not IsValid(npc) or npc.D6_Variant ~= "assault" then continue end
        local phys = RolePhys(npc)
        if not phys then continue end

        local myPos   = npc:GetPos()
        local target, dist = NearestPlayer(myPos)
        local state   = npc.D6_RoleState
        local hpFrac  = npc:Health() / npc:GetMaxHealth()

        if state == 1 then
            if IsValid(target) and dist < 2000 then state = 2 end
        elseif state == 2 then
            if not IsValid(target) then state = 1
            elseif dist < cfg.rangeMax then state = 3
            elseif hpFrac < cfg.retreatHP then state = 4 end
        elseif state == 3 then
            if not IsValid(target) then state = 1
            elseif dist < 300 or hpFrac < cfg.retreatHP then state = 4
            elseif dist > cfg.rangeMax * 1.4 then state = 2 end
        elseif state == 4 then
            if not IsValid(target) or (dist > 700 and hpFrac > 0.5) then state = 2 end
        end
        npc.D6_RoleState = state

        if IsValid(target) then
            local dir = (target:GetPos() - myPos):GetNormalized()
            if state == 2 then
                Lerp6(phys, dir * cfg.speed)
            elseif state == 3 then
                local perp = dir:Cross(Vector(0, 0, 1)):GetNormalized()
                Lerp6(phys, (dir * 0.3 + perp * 0.7):GetNormalized() * cfg.speed)
            elseif state == 4 then
                Lerp6(phys, -dir * cfg.speed)
            end
        end

        if state == 3 and IsValid(target) and ct > npc.D6_NextAttack then
            npc.D6_NextAttack = ct + 0.15
            npc:FireBullets({
                Num = 2, Src = myPos, Dir = (target:GetPos() - myPos):GetNormalized(),
                Spread = Vector(0.04, 0.04, 0), Tracer = 1, TracerName = "GlowTracer",
                Force = 8, Damage = 10, Attacker = npc, Callback = BulletHit,
            })
            npc:EmitSound("weapons/ar2/ar2_fire1.wav", 68, math.random(110, 125))
        end
    end
end)

-- ─── INTERCEPTOR ─────────────────────────────────────────
-- States: 1=PATROL 2=APPROACH 3=ATTACK (fast flanking)
hook.Add("Think", "D6_Role_Interceptor", function()
    RefreshCache()
    local ct  = CurTime()
    local cfg = ROLE_CFG.interceptor
    for _, npc in ipairs(_D6RoleCache) do
        if not IsValid(npc) or npc.D6_Variant ~= "interceptor" then continue end
        local phys = RolePhys(npc)
        if not phys then continue end

        local myPos   = npc:GetPos()
        local target, dist = NearestPlayer(myPos, 2500)
        local state   = npc.D6_RoleState

        if state == 1 then
            if IsValid(target) and dist < 2000 then state = 2 end
        elseif state == 2 then
            if not IsValid(target) then state = 1
            elseif dist < cfg.rangeMax then state = 3 end
        elseif state == 3 then
            if not IsValid(target) then state = 1
            elseif dist > cfg.rangeMax * 1.5 then state = 2 end
        end
        npc.D6_RoleState = state

        if IsValid(target) then
            local dir = (target:GetPos() - myPos):GetNormalized()
            if state == 2 then
                -- Perpendicular approach from the side
                local perp = dir:Cross(Vector(0, 0, 1)):GetNormalized()
                Lerp6(phys, (dir * 0.5 + perp * 0.5):GetNormalized() * cfg.speed)
            elseif state == 3 then
                -- Fast strafing run
                local perp = dir:Cross(Vector(0, 0, 1)):GetNormalized()
                Lerp6(phys, perp * cfg.speed)
            end
        end

        if state == 3 and IsValid(target) and ct > npc.D6_NextAttack then
            npc.D6_NextAttack = ct + 0.06
            npc:FireBullets({
                Num = 1, Src = myPos, Dir = (target:GetPos() - myPos):GetNormalized(),
                Spread = Vector(0.02, 0.02, 0), Tracer = 1, TracerName = "GlowTracer",
                Force = 5, Damage = 8, Attacker = npc, Callback = BulletHit,
            })
            npc:EmitSound("weapons/irifle/irifle_fire2.wav", 68, 150)
        end
    end
end)

-- ─── ARTILLERY ───────────────────────────────────────────
-- States: 1=PATROL 2=HOLD 3=ATTACK 4=RETREAT
hook.Add("Think", "D6_Role_Artillery", function()
    RefreshCache()
    local ct  = CurTime()
    local cfg = ROLE_CFG.artillery
    for _, npc in ipairs(_D6RoleCache) do
        if not IsValid(npc) or npc.D6_Variant ~= "artillery" then continue end
        local phys = RolePhys(npc)
        if not phys then continue end

        local myPos   = npc:GetPos()
        local target, dist = NearestPlayer(myPos, 4000)
        local state   = npc.D6_RoleState

        if state == 1 then
            if IsValid(target) and dist < 3000 then state = 2 end
        elseif state == 2 then
            if not IsValid(target) then state = 1
            elseif dist < cfg.retreatDist then state = 4
            elseif dist >= cfg.rangeMin then state = 3 end
        elseif state == 3 then
            if not IsValid(target) then state = 1
            elseif dist < cfg.retreatDist then state = 4
            elseif dist < cfg.rangeMin then state = 2 end
        elseif state == 4 then
            if not IsValid(target) then state = 1
            elseif dist > cfg.retreatDist * 1.5 then state = 2 end
        end
        npc.D6_RoleState = state

        if IsValid(target) then
            local dir = (target:GetPos() - myPos):GetNormalized()
            if state == 2 then
                -- Approach to long range if needed
                if dist > cfg.rangeMax then
                    Lerp6(phys, dir * cfg.speed)
                else
                    Lerp6(phys, Vector(0, 0, 0))
                end
            elseif state == 3 then
                local perp = dir:Cross(Vector(0, 0, 1)):GetNormalized()
                Lerp6(phys, perp * (cfg.speed * 0.4))
            elseif state == 4 then
                Lerp6(phys, -dir * cfg.speed * 1.5)
            end
        end

        if state == 3 and IsValid(target) and ct > npc.D6_NextAttack then
            npc.D6_NextAttack = ct + 2.5
            local predicted = target:GetPos() + target:GetVelocity() * (dist / 2200) + Vector(0, 0, 40)
            local shootDir  = (predicted - myPos):GetNormalized()
            local m = ents.Create("rpg_missile")
            if IsValid(m) then
                m:SetPos(myPos + shootDir * 60)
                m:SetAngles(shootDir:Angle())
                m:SetOwner(npc)
                m:Spawn()
                local mp = m:GetPhysicsObject()
                if IsValid(mp) then
                    mp:EnableGravity(false); mp:SetVelocity(shootDir * 2200); mp:Wake()
                end
                m:Activate()
                timer.Simple(6, function() if IsValid(m) then m:Remove() end end)
                npc:EmitSound("weapons/rpg/rocketfire1.wav", 82, 85)
            end
        end
    end
end)

-- ─── SUPPORT ─────────────────────────────────────────────
-- Heals nearby allied scanners; fights only if cornered
hook.Add("Think", "D6_Role_Support", function()
    RefreshCache()
    local ct  = CurTime()
    local cfg = ROLE_CFG.support
    for _, npc in ipairs(_D6RoleCache) do
        if not IsValid(npc) or npc.D6_Variant ~= "support" then continue end
        local phys = RolePhys(npc)
        if not phys then continue end

        local myPos   = npc:GetPos()
        local target, dist = NearestPlayer(myPos, 2000)

        -- Retreat from players
        if IsValid(target) and dist < 600 then
            local dir = (target:GetPos() - myPos):GetNormalized()
            Lerp6(phys, -dir * cfg.speed)
        end

        -- Heal nearby allies every healInterval seconds
        if ct >= npc.D6_NextHeal then
            npc.D6_NextHeal = ct + cfg.healInterval
            for _, ally in ipairs(ents.FindInSphere(myPos, cfg.healRadius)) do
                if not IsValid(ally) then continue end
                if ally:GetClass() ~= "npc_cscanner" then continue end
                if ally == npc or not ally.D6_Variant then continue end
                local curHp = ally:Health()
                local maxHp = ally:GetMaxHealth()
                if curHp < maxHp then
                    ally:SetHealth(math.min(curHp + cfg.healAmt, maxHp))
                    local ef = EffectData(); ef:SetOrigin(ally:GetPos()); ef:SetScale(0.4)
                    util.Effect("cball_bounce", ef)
                end
            end
        end

        -- Weak self-defense
        if IsValid(target) and dist < 400 and ct > npc.D6_NextAttack then
            npc.D6_NextAttack = ct + 0.4
            npc:FireBullets({
                Num = 1, Src = myPos, Dir = (target:GetPos() - myPos):GetNormalized(),
                Spread = Vector(0.06, 0.06, 0), Tracer = 1, Force = 3, Damage = 5,
                Attacker = npc,
            })
        end
    end
end)

-- ─── HEAVY ELITE ─────────────────────────────────────────
-- States: 1=PATROL 2=APPROACH 3=ATTACK 4=CHARGE
hook.Add("Think", "D6_Role_HeavyElite", function()
    RefreshCache()
    local ct  = CurTime()
    local cfg = ROLE_CFG.heavy_elite
    for _, npc in ipairs(_D6RoleCache) do
        if not IsValid(npc) or npc.D6_Variant ~= "heavy_elite" then continue end
        local phys = RolePhys(npc)
        if not phys then continue end

        local myPos   = npc:GetPos()
        local target, dist = NearestPlayer(myPos, 3000)
        local state   = npc.D6_RoleState

        -- End charge
        if state == 4 and ct > npc.D6_ChargeEnd then
            state = 3
            npc.D6_ChargeCD = ct + cfg.chargeCD
            npc.D6_ChargeOn = false
            -- Contact blast
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
                elseif dist < cfg.rangeMax then state = 3 end
            elseif state == 3 then
                if not IsValid(target) then state = 1
                elseif dist > cfg.rangeMax * 1.3 then state = 2
                elseif dist < cfg.chargeDist and ct > npc.D6_ChargeCD then
                    state = 4
                    npc.D6_ChargeOn  = true
                    npc.D6_ChargeEnd = ct + cfg.chargeDur
                end
            end
            npc.D6_RoleState = state
        end

        if IsValid(target) then
            local dir = (target:GetPos() - myPos):GetNormalized()
            if state == 2 then
                Lerp6(phys, dir * cfg.speed)
            elseif state == 3 then
                local perp = dir:Cross(Vector(0, 0, 1)):GetNormalized()
                Lerp6(phys, (dir * 0.5 + perp * 0.5):GetNormalized() * cfg.speed)
            elseif state == 4 then
                LerpVector(FrameTime() * 8, phys:GetVelocity(), dir * cfg.chargeSpeed)
                phys:SetVelocity(LerpVector(FrameTime() * 8, phys:GetVelocity(), dir * cfg.chargeSpeed))
            end
        end

        -- Combine ball cannon
        if state == 3 and IsValid(target) and ct > npc.D6_NextAttack then
            npc.D6_NextAttack = ct + 3.5
            local shootDir = (target:GetPos() + Vector(0, 0, 40) - myPos):GetNormalized()
            local ball = ents.Create("prop_combine_ball")
            if IsValid(ball) then
                ball:SetPos(myPos + shootDir * 100)
                ball:SetAngles(shootDir:Angle())
                ball:SetOwner(npc)
                ball:SetModel("models/effects/combineball.mdl")
                ball:SetSaveValue("m_nMaxBounces", 0)
                ball:Spawn()
                local bp = ball:GetPhysicsObject()
                if IsValid(bp) then
                    bp:EnableGravity(false); bp:EnableDrag(false)
                    bp:SetMass(1); bp:SetVelocity(shootDir * 1000); bp:Wake()
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
        end
    end
end)

-- ─── Console commands ─────────────────────────────────────
for _, v in ipairs({ "assault", "interceptor", "artillery", "support", "heavy_elite" }) do
    local vname = v
    concommand.Add("6dof_spawn_" .. vname, function(ply)
        if not IsValid(ply) then return end
        local tr = ply:GetEyeTrace()
        local pos = tr.HitPos + tr.HitNormal * 50
        SpawnD6Role(vname, pos, ply)
        ply:PrintMessage(HUD_PRINTTALK, "[D6 Roles] Spawned: " .. vname)
    end)
end

print("[D6] d6_ai_roles.lua loaded")
