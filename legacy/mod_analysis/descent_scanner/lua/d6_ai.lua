if not SERVER then return end

local COLORS = {
    mine  = Color(255, 180, 0),
    mg    = Color(255, 40,  40),
    rpg   = Color(40,  100, 255),
    grav  = Color(160, 40,  255),
    laser = Color(0,   255, 255),
    heavy = Color(20,  20,  20),
}

local SWARM_RADIUS      = 400
local SEPARATION_DIST   = 90
local WEIGHT_SEPARATION = 2.8
local WEIGHT_COHESION   = 0.8
local WEIGHT_ALIGNMENT  = 1.0
local WEIGHT_TARGET     = 2.2
local SWARM_MAX_SPEED   = 480

local MINE_DRIFT_SPEED   = 45
local MINE_TRIGGER_DIST  = 160
local MINE_BLIP_INTERVAL = 1.2

local GRAV_SCAN_RADIUS   = 700
local GRAV_SWARM_RADIUS  = 550

local BOIDS_EXCLUDED = { heavy = true, mine = true }

local BOUNCE_PASS_RANGE_MIN  = 400
local BOUNCE_PASS_RANGE_MAX  = 800
local BOUNCE_CATCH_RADIUS    = 110
local BOUNCE_THROW_SPEED     = 1200
local BOUNCE_ATTACK_SPEED    = 1500
local BOUNCE_ATTACK_RANGE    = 520
local BOUNCE_THROW_COOLDOWN  = 2.5
local BOUNCE_HOLD_TIME       = 3.0

CreateConVar("d6_grav_bounce_enabled", "1", FCVAR_ARCHIVE,
    "Включить жонглирование бочками между грави-дронами (1/0)")

D6_ThrownProps = D6_ThrownProps or {}

timer.Create("D6_CleanThrownProps", 5, 0, function()
    local ct = CurTime()
    for ent, expireTime in pairs(D6_ThrownProps) do
        if not IsValid(ent) or ct > expireTime then
            D6_ThrownProps[ent] = nil
        end
    end
end)

local function HasLOS(from, to, filter)
    local tr = util.TraceLine({
        start  = from,
        endpos = to,
        filter = filter,
        mask   = MASK_OPAQUE
    })
    return not tr.Hit
end

local function SafeGetPhys(ent)
    if not IsValid(ent) then return nil end
    local ph = ent:GetPhysicsObject()
    return IsValid(ph) and ph or nil
end

local function ThrowProp(npc, ent, velocity)
    if not IsValid(npc) or not IsValid(ent) then return end
    D6_ThrownProps[ent] = CurTime() + BOUNCE_HOLD_TIME
    ent.D6_ThrownBy     = npc
    if npc.D6_HeldProps then
        for i, p in ipairs(npc.D6_HeldProps) do
            if p == ent then table.remove(npc.D6_HeldProps, i); break end
        end
    end
    ent.D6_BeingHeld   = false
    ent.D6_MasterOwner = nil
    ent:SetCollisionGroup(COLLISION_GROUP_NONE)
    local phys = SafeGetPhys(ent)
    if phys then
        phys:EnableGravity(true)
        phys:Wake()
        phys:SetVelocity(velocity)
    end
    npc:EmitSound("weapons/physcannon/superphys_launch1.wav", 70, 130)
end

local function TryCatchThrownProp(npc)
    local myPos = npc:GetPos()
    for ent, _ in pairs(D6_ThrownProps) do
        if not IsValid(ent) then D6_ThrownProps[ent] = nil; continue end
        if ent.D6_ThrownBy == npc then continue end
        if #npc.D6_HeldProps >= 8 then continue end
        if myPos:Distance(ent:GetPos()) > BOUNCE_CATCH_RADIUS then continue end
        if not HasLOS(myPos, ent:GetPos(), npc) then continue end
        D6_ThrownProps[ent] = nil
        ent.D6_ThrownBy     = nil
        ent.D6_BeingHeld    = true
        ent.D6_MasterOwner  = npc
        ent:SetCollisionGroup(COLLISION_GROUP_DEBRIS)
        local phys = SafeGetPhys(ent)
        if phys then
            phys:EnableGravity(false)
            phys:SetVelocity(Vector(0, 0, 0))
        end
        table.insert(npc.D6_HeldProps, ent)
        npc:EmitSound("weapons/physcannon/physcannon_pickup.wav", 65, 150)
        break
    end
end

function SpawnEnemyDrone(ply, class, variant, customPos)
    local npc = ents.Create(class)
    if not IsValid(npc) then return end
    if customPos then
        npc:SetPos(customPos)
    else
        local tr = ply:GetEyeTrace()
        npc:SetPos(tr.HitPos + tr.HitNormal * 50)
    end
    npc:Spawn()
    npc:Activate()
    npc.D6_Variant        = variant
    npc.D6_NextAttack     = CurTime()
    npc.D6_NextDodge      = CurTime() + 1
    npc.D6_HeldProps      = {}
    npc.D6_NextThrow      = 0
    npc.D6_NextCatchScan  = 0
    npc.D6_PatrolTarget   = nil
    npc.D6_PatrolTimeout  = 0
    npc.D6_GravSwarmPhase = math.Rand(0, math.pi * 2)
    npc:SetRenderMode(RENDERMODE_TRANSCOLOR)
    npc:SetColor(COLORS[variant] or color_white)
    if variant == "mg" then
        npc:SetModelScale(1.0, 0); npc:SetMaxHealth(120); npc:SetHealth(120)
    elseif variant == "heavy" then
        -- [D6 AI] heavy теперь визуально отличим: используем dropship.mdl × 0.15
        npc:SetModel("models/combine_dropship.mdl")
        npc:SetModelScale(0.15, 0)
        npc:SetMaxHealth(450); npc:SetHealth(450)
    elseif variant == "seeker" then
        -- [D6 AI] seeker — крупнее dropship × 0.18 + фиолетовый цвет
        npc:SetModel("models/combine_dropship.mdl")
        npc:SetModelScale(0.18, 0)
        npc:SetMaxHealth(280); npc:SetHealth(280)
        npc:SetColor(Color(180, 80, 220))
    else
        npc:SetModelScale(1.4, 0); npc:SetMaxHealth(150); npc:SetHealth(150)
    end
    npc:AddEntityRelationship(ply, D_HT, 99)
    return npc
end

concommand.Add("6dof_spawn_mine",  function(ply) SpawnEnemyDrone(ply, "npc_cscanner", "mine")  end)
concommand.Add("6dof_spawn_mg",    function(ply) SpawnEnemyDrone(ply, "npc_cscanner", "mg")    end)
concommand.Add("6dof_spawn_rpg",   function(ply) SpawnEnemyDrone(ply, "npc_cscanner", "rpg")   end)
concommand.Add("6dof_spawn_grav",  function(ply) SpawnEnemyDrone(ply, "npc_cscanner", "grav")  end)
concommand.Add("6dof_spawn_laser", function(ply) SpawnEnemyDrone(ply, "npc_cscanner", "laser") end)
concommand.Add("6dof_spawn_heavy", function(ply) SpawnEnemyDrone(ply, "npc_cscanner", "heavy") end)

concommand.Add("6dof_spawn_squad", function(ply)
    local tr      = ply:GetEyeTrace()
    local basePos = tr.HitPos + tr.HitNormal * 60
    local layout  = { "heavy", "mg", "mg", "rpg", "laser", "mine" }
    for i, variant in ipairs(layout) do
        local angle  = (i / #layout) * math.pi * 2
        local offset = Vector(math.sin(angle) * 120, math.cos(angle) * 120, 40)
        SpawnEnemyDrone(ply, "npc_cscanner", variant, basePos + offset)
    end
    ply:PrintMessage(HUD_PRINTTALK, "[ОТРЯД] Тактическое звено Альянса развернуто!")
end)

concommand.Add("6dof_spawn_airmine", function(ply)
    local tr       = ply:GetEyeTrace()
    local spawnPos = tr.HitPos + tr.HitNormal * 70
    if tr.HitNormal.z == 0 then spawnPos.z = spawnPos.z + 80 end
    local mine = ents.Create("prop_physics")
    if not IsValid(mine) then return end
    mine:SetModel("models/props_combine/combine_mine01.mdl")
    mine:SetPos(spawnPos)
    mine:Spawn()
    mine:SetColor(Color(255, 60, 0))
    mine:SetRenderMode(RENDERMODE_TRANSCOLOR)
    local phys = SafeGetPhys(mine)
    if phys then
        phys:EnableGravity(false)
        phys:EnableMotion(true)
        phys:SetDamping(8, 8)
    end
    mine.IsAirMine = true
    mine.NextScan  = CurTime()
    mine.NextBlip  = CurTime()
    mine.NextDrift = CurTime()
end)

local _D6PropCache    = {}; local _D6PropT    = 0
local _D6ScannerCache = {}; local _D6ScannerT = 0

-- =========================================================
-- ГЛАВНЫЙ AI THINK
-- =========================================================
hook.Add("Think", "D6_AI_Core", function()
    local ct = CurTime()
    if ct - _D6PropT    > 0.12 then _D6PropCache    = ents.FindByClass("prop_physics");  _D6PropT    = ct end
    if ct - _D6ScannerT > 0.10 then _D6ScannerCache = ents.FindByClass("npc_cscanner"); _D6ScannerT = ct end

    -- ── 1. Воздушные мины ─────────────────────────────────
    for _, mine in ipairs(_D6PropCache) do
        if not mine.IsAirMine then continue end
        if not IsValid(mine) then continue end
        local minePos = mine:GetPos()
        if ct > (mine.NextBlip or 0) then
            mine:EmitSound("buttons/button14.wav", 60, 120)
            mine.NextBlip = ct + MINE_BLIP_INTERVAL
        end
        if ct > (mine.NextDrift or 0) then
            mine.NextDrift = ct + 0.2
            local closestPly, minDist = nil, 9999999
            for _, p in ipairs(player.GetAll()) do
                if p:Alive() then
                    local d = minePos:Distance(p:GetPos())
                    if d < minDist then minDist = d; closestPly = p end
                end
            end
            if IsValid(closestPly) then
                local phys = SafeGetPhys(mine)
                if phys then
                    local driftDir = (closestPly:GetPos() - minePos):GetNormalized()
                    phys:SetVelocity(LerpVector(0.15, phys:GetVelocity(), driftDir * MINE_DRIFT_SPEED))
                end
            end
        end
        if ct > (mine.NextScan or 0) then
            mine.NextScan = ct + 0.15
            for _, ent in ipairs(ents.FindInSphere(minePos, MINE_TRIGGER_DIST)) do
                if (ent:IsPlayer() or ent:IsNPC()) and ent:GetClass() ~= "npc_cscanner" then
                    mine:EmitSound("ambient/explosions/explode_1.wav", 90, 100)
                    local explode = ents.Create("env_explosion")
                    if IsValid(explode) then
                        explode:SetPos(minePos)
                        explode:Spawn()
                        explode:SetKeyValue("iMagnitude", "120")
                        explode:Fire("Explode", 0, 0)
                        timer.Simple(0.1, function() if IsValid(explode) then explode:Remove() end end)
                    end
                    mine:Remove()
                    break
                end
            end
        end
    end

    -- ── 2. Атаки боевых дронов (mg, laser, rpg, heavy) ────
    for _, npc in ipairs(_D6ScannerCache) do
        if not IsValid(npc) or not npc.D6_Variant then continue end
        local v = npc.D6_Variant
        if v == "grav" or v == "mine" then continue end

        local enemy = npc:GetEnemy()
        local phys  = SafeGetPhys(npc)
        if not IsValid(enemy) or not phys then continue end

        local myPos      = npc:GetPos()
        local targetPos  = enemy:GetPos() + Vector(0, 0, 40)
        local dist       = myPos:Distance(targetPos)
        local dirToEnemy = (targetPos - myPos):GetNormalized()

        local currentAng = npc:GetAngles()
        local targetAng  = dirToEnemy:Angle()
        local vel        = phys:GetVelocity()
        local sideSpeed  = npc:GetRight():Dot(vel)
        local turnSpeed  = currentAng.y - targetAng.y
        targetAng.r = math.Clamp((sideSpeed * -0.12) + (turnSpeed * 1.5), -65, 65)
        targetAng.p = targetAng.p + (npc:GetForward():Dot(vel) * 0.04)
        npc:SetAngles(LerpAngle(FrameTime() * 7, currentAng, targetAng))

        if ct > (npc.D6_NextDodge or 0) then
            local enemyLook = enemy:IsPlayer() and enemy:EyeAngles():Forward() or enemy:GetForward()
            local toMe = (myPos - targetPos):GetNormalized()
            if enemyLook:Dot(toMe) > 0.85 then
                npc.D6_NextDodge = ct + math.random(1, 3)
                local dodgeDir = (npc:GetRight() * math.random(-1, 1)
                                + npc:GetUp() * math.Rand(-0.5, 0.5)):GetNormalized()
                phys:AddVelocity(dodgeDir * 1400)
            end
        end

        if ct > (npc.D6_NextAttack or 0) then
            local tr = util.TraceLine({ start = myPos, endpos = targetPos, filter = npc })

            if v == "laser" and tr.Entity == enemy and dist < 2000 then
                npc.D6_NextAttack = ct + 0.12
                npc:FireBullets({
                    Num = 1, Src = myPos, Dir = dirToEnemy,
                    Spread = Vector(0,0,0), Tracer = 1, TracerName = "ToolTracer",
                    Force = 4, Damage = 7, Attacker = npc
                })
                npc:EmitSound("weapons/physcannon/superphys_small_zap1.wav", 70, 160)

            elseif v == "heavy" and tr.Entity == enemy and dist < 1600 then
                npc.D6_NextAttack = ct + 2.2
                local enemyVel = (enemy:IsPlayer() and (enemy.D6_Vel or enemy:GetVelocity())) or enemy:GetVelocity()
                local predictedPos = targetPos + enemyVel * (dist / 1200)
                local shootDir     = (predictedPos - myPos):GetNormalized()

                local ball = ents.Create("prop_combine_ball")
                if IsValid(ball) then
                    ball:SetPos(myPos + shootDir * 75)
                    ball:SetAngles(shootDir:Angle())
                    ball:SetOwner(npc)
                    ball:SetModel("models/effects/combineball.mdl")
                    ball:SetSaveValue("m_nMaxBounces", 0)
                    ball:Spawn()
                    local bphys = SafeGetPhys(ball)
                    if bphys then
                        bphys:EnableGravity(false)
                        bphys:EnableDrag(false)
                        bphys:SetMass(1)
                        bphys:SetVelocity(shootDir * 1100)
                        bphys:Wake()
                    end
                    ball:Activate()
                    timer.Simple(4, function()
                        if IsValid(ball) then
                            local bPos = ball:GetPos()
                            local ef = EffectData(); ef:SetOrigin(bPos); ef:SetScale(2)
                            util.Effect("cball_explode", ef)
                            util.BlastDamage(npc, npc, bPos, 180, 90)
                            ball:Remove()
                        end
                    end)
                    npc:EmitSound("weapons/ar2/fire1.wav", 85, 75)
                end

            elseif v == "rpg" and dist < 1800 and tr.Entity == enemy then
                npc.D6_NextAttack = ct + 1.8
                local m = ents.Create("rpg_missile")
                if IsValid(m) then
                    m:SetPos(myPos + dirToEnemy * 50)
                    m:SetAngles(dirToEnemy:Angle())
                    m:SetOwner(npc)
                    m:Spawn()
                    local mp = SafeGetPhys(m)
                    if mp then
                        mp:EnableGravity(false)
                        mp:SetVelocity(dirToEnemy * 2800)
                        mp:Wake()
                    end
                    m:Activate()
                    timer.Simple(6, function() if IsValid(m) then m:Remove() end end)
                    npc:EmitSound("weapons/rpg/rocketfire1.wav", 80, 90)
                end

            elseif v == "mg" and dist < 1400 then
                npc.D6_NextAttack = ct + 0.08
                npc:FireBullets({
                    Num = 2, Src = myPos, Dir = dirToEnemy,
                    Spread = Vector(0.03, 0.03, 0), Tracer = 1, TracerName = "Tracer",
                    Force = 6, Damage = 9, Attacker = npc
                })
                npc:EmitSound("weapons/ar2/fire1.wav", 68, math.random(110, 125))
            end
        end

        phys:AddAngleVelocity(-phys:GetAngleVelocity() * 0.2)
    end
end)

-- =========================================================
-- ГРАВИСКАНЕР: роевое патрулирование → сбор мусора → атака
--
-- БАГ А ИСПРАВЛЕН: без мусора и без врага — патрулируем
--                 точки в радиусе GRAV_SCAN_RADIUS по роевой
--                 схеме (фазовый сдвиг у каждого дрона).
--                 К игроку НЕ летим пока нечего бросить.
--
-- БАГ Б ИСПРАВЛЕН: bphys/mp создавались без SafeGetPhys.
--                 Теперь все prop_combine_ball и rpg_missile
--                 проходят через SafeGetPhys + Wake() до Activate().
--                 Краш при столкновении пропа с игроком был из-за
--                 нулевого физтела (sleeping) — теперь невозможен.
--
-- БАГ В ИСПРАВЛЕН: поиск мусора теперь приоритет 1.
--                 Роевое поведение между грависканерами через
--                 фазовый сдвиг + Boids-векторы среди грави-соседей.
-- =========================================================
hook.Add("Think", "D6_GravMaster_Logic", function()
    local ct = CurTime()
    local bounceEnabled = GetConVar("d6_grav_bounce_enabled"):GetBool()

    for _, npc in ipairs(_D6ScannerCache) do
        if not IsValid(npc) or npc.D6_Variant ~= "grav" then continue end

        local myPos = npc:GetPos()
        npc.D6_HeldProps     = npc.D6_HeldProps or {}
        npc.D6_NextThrow     = npc.D6_NextThrow or 0
        npc.D6_NextCatchScan = npc.D6_NextCatchScan or 0

        local phys = SafeGetPhys(npc)
        if not phys then continue end

        -- ── А. Поймать летящий проп ───────────────────────
        if bounceEnabled and ct > npc.D6_NextCatchScan then
            npc.D6_NextCatchScan = ct + 0.25
            TryCatchThrownProp(npc)
        end

        -- ── Б. Сканирование и захват пропов ───────────────
        -- Приоритет 1: всегда ищем мусор, независимо от врага
        if #npc.D6_HeldProps < 8 and ct > (npc.D6_NextScan or 0) then
            npc.D6_NextScan = ct + 0.35
            for _, ent in ipairs(ents.FindInSphere(myPos, GRAV_SCAN_RADIUS)) do
                if ent:GetClass() ~= "prop_physics" then continue end
                if table.HasValue(npc.D6_HeldProps, ent) then continue end
                if ent.D6_BeingHeld then continue end
                if D6_ThrownProps[ent] then continue end
                if ent.IsAirMine then continue end
                local p_phys = SafeGetPhys(ent)
                if not p_phys or p_phys:GetMass() >= 120 then continue end
                if not HasLOS(myPos, ent:GetPos(), npc) then continue end

                ent.D6_BeingHeld   = true
                ent.D6_MasterOwner = npc
                ent:SetCollisionGroup(COLLISION_GROUP_DEBRIS)
                p_phys:EnableGravity(false)
                table.insert(npc.D6_HeldProps, ent)
                npc:EmitSound("weapons/physcannon/physcannon_pickup.wav", 65, 140)
                break
            end
        end

        -- Чистка уничтоженных пропов
        local validProps = {}
        for _, ent in ipairs(npc.D6_HeldProps) do
            if IsValid(ent) then table.insert(validProps, ent) end
        end
        npc.D6_HeldProps = validProps

        -- ── В. Движение грависканера ───────────────────────
        local enemy    = npc:GetEnemy()
        local hasEnemy = IsValid(enemy)
        local hasAmmo  = #npc.D6_HeldProps > 0

        if hasEnemy and hasAmmo then
            -- Есть что бросить и есть враг: сближаемся для броска
            local enemyPos  = enemy:GetPos()
            local dist      = myPos:Distance(enemyPos)
            local dir       = (enemyPos - myPos):GetNormalized()
            local keepDist  = 400

            if dist > keepDist + 60 then
                phys:SetVelocity(LerpVector(FrameTime() * 4, phys:GetVelocity(), dir * 340))
            elseif dist < keepDist - 60 then
                phys:SetVelocity(LerpVector(FrameTime() * 4, phys:GetVelocity(), -dir * 200))
            else
                local orbitDir = npc:GetRight()
                phys:SetVelocity(LerpVector(FrameTime() * 3, phys:GetVelocity(), orbitDir * 230))
            end

        else
            -- Нет мусора или нет врага: роевое патрулирование
            -- Каждый грависканер имеет фазовый сдвиг — летят по разным точкам,
            -- не собираясь в одну кучу (роевое поведение).
            local phase = npc.D6_GravSwarmPhase or 0

            -- Boids-вектора среди других грависканеров
            local sep = Vector(0, 0, 0)
            local coh = Vector(0, 0, 0)
            local gravCount = 0
            for _, other in ipairs(ents.FindInSphere(myPos, GRAV_SWARM_RADIUS)) do
                if not IsValid(other) then continue end
                if other == npc then continue end
                if other:GetClass() ~= "npc_cscanner" then continue end
                if other.D6_Variant ~= "grav" then continue end
                local d = myPos:Distance(other:GetPos())
                if d < 100 and d > 0 then
                    sep = sep + (myPos - other:GetPos()):GetNormalized() * (100 / d)
                end
                coh = coh + other:GetPos()
                gravCount = gravCount + 1
            end

            -- Смещение по индивидуальной фазе — точки патруля не совпадают
            local scanAngle = phase + ct * 0.18
            local r = GRAV_SCAN_RADIUS * 0.75
            local patrolTarget = npc.D6_PatrolBase and
                (npc.D6_PatrolBase + Vector(
                    math.cos(scanAngle) * r,
                    math.sin(scanAngle) * r,
                    math.sin(ct * 0.9 + phase) * 80
                )) or myPos

            -- Обновляем базу патруля раз в 8 секунд
            if not npc.D6_PatrolBase or ct > (npc.D6_PatrolTimeout or 0) then
                local ang = math.Rand(0, math.pi * 2)
                npc.D6_PatrolBase = myPos + Vector(
                    math.cos(ang) * 200,
                    math.sin(ang) * 200,
                    math.Rand(-60, 60)
                )
                npc.D6_PatrolTimeout = ct + 8
            end

            local moveDir = (patrolTarget - myPos)
            if moveDir:LengthSqr() > 1 then moveDir:Normalize() end

            if gravCount > 0 then
                coh = (coh / gravCount - myPos):GetNormalized()
                moveDir = (moveDir + sep * 1.6 + coh * 0.5):GetNormalized()
            end

            -- Проверка стены
            local trP = util.TraceLine({
                start  = myPos,
                endpos = myPos + moveDir * 120,
                filter = npc,
                mask   = MASK_SOLID_BRUSHONLY
            })
            if trP.Hit then
                -- Отражаемся от стены
                moveDir = moveDir - trP.HitNormal * moveDir:Dot(trP.HitNormal) * 2
                npc.D6_PatrolBase = nil
            end

            phys:SetVelocity(LerpVector(FrameTime() * 2.2, phys:GetVelocity(), moveDir * 210))
        end

        -- ── Г. Броски пропов ──────────────────────────────
        if bounceEnabled and hasAmmo and ct > npc.D6_NextThrow then
            local didThrow = false

            if hasEnemy and myPos:Distance(enemy:GetPos()) < BOUNCE_ATTACK_RANGE then
                local throwTarget = enemy:GetPos() + Vector(0, 0, 40)
                local throwDir    = (throwTarget - myPos):GetNormalized()
                local throwEnt    = npc.D6_HeldProps[1]
                if IsValid(throwEnt) then
                    ThrowProp(npc, throwEnt, throwDir * BOUNCE_ATTACK_SPEED)
                    npc.D6_NextThrow = ct + BOUNCE_THROW_COOLDOWN
                    didThrow = true
                    npc:EmitSound("ambient/energy/zap5.wav", 80, 100)
                end
            end

            if not didThrow then
                local bestAlly, bestDist = nil, 99999
                for _, ally in ipairs(ents.FindInSphere(myPos, BOUNCE_PASS_RANGE_MAX)) do
                    if not IsValid(ally) then continue end
                    if ally == npc then continue end
                    if ally:GetClass() ~= "npc_cscanner" then continue end
                    if ally.D6_Variant ~= "grav" then continue end
                    if #(ally.D6_HeldProps or {}) >= 8 then continue end
                    local d = myPos:Distance(ally:GetPos())
                    if d < BOUNCE_PASS_RANGE_MIN then continue end
                    if not HasLOS(myPos, ally:GetPos(), npc) then continue end
                    if d < bestDist then bestDist = d; bestAlly = ally end
                end
                if IsValid(bestAlly) then
                    local throwEnt = npc.D6_HeldProps[1]
                    if IsValid(throwEnt) then
                        local passDir = (bestAlly:GetPos() - myPos):GetNormalized()
                        ThrowProp(npc, throwEnt, passDir * BOUNCE_THROW_SPEED + VectorRand() * 80)
                        npc.D6_NextThrow = ct + BOUNCE_THROW_COOLDOWN
                    end
                end
            end
        end

        -- ── Д. Орбита пропов вокруг грависканера ──────────
        local propCount = #npc.D6_HeldProps
        for i, ent in ipairs(npc.D6_HeldProps) do
            if not IsValid(ent) then continue end
            local angle    = (i / propCount) * math.pi * 2 + (ct * 3.5)
            local orbitPos = myPos + Vector(
                math.cos(angle) * 130,
                math.sin(angle) * 130,
                math.sin(ct * 6 + i) * 15
            )
            local p_phys = SafeGetPhys(ent)
            if p_phys then
                local toOrbit = orbitPos - ent:GetPos()
                p_phys:SetVelocity(toOrbit * 18)
            end
        end

        -- ── Е. Захват и бросок манхоков ───────────────────
        if bounceEnabled and hasEnemy and ct > (npc.D6_NextManhack or 0) then
            if myPos:Distance(enemy:GetPos()) < 900 then
                for _, mh in ipairs(ents.FindInSphere(myPos, 500)) do
                    if not IsValid(mh) then continue end
                    if mh:GetClass() ~= "npc_manhack" then continue end
                    if mh.D6_GravGrabbed then continue end
                    local mph = SafeGetPhys(mh)
                    if not mph then continue end
                    mh.D6_GravGrabbed = true
                    npc.D6_NextManhack = ct + 3.5
                    mph:SetVelocity((myPos - mh:GetPos()):GetNormalized() * 800)
                    timer.Simple(0.4, function()
                        if not IsValid(mh) or not IsValid(npc) or not IsValid(enemy) then
                            if IsValid(mh) then mh.D6_GravGrabbed = false end
                            return
                        end
                        local throwDir = (enemy:GetPos() + Vector(0,0,30) - mh:GetPos()):GetNormalized()
                        local mph2 = SafeGetPhys(mh)
                        if mph2 then mph2:SetVelocity(throwDir * 2800) end
                        mh.D6_GravGrabbed = false
                        npc:EmitSound("weapons/physcannon/superphys_launch1.wav", 80, 90)
                    end)
                    npc:EmitSound("weapons/physcannon/physcannon_pickup.wav", 70, 130)
                    break
                end
            end
        end
    end
end)

-- =========================================================
-- Детонация взрывных бочек на орбите
-- =========================================================
hook.Add("EntityTakeDamage", "D6_ExplosiveShield", function(target, dmginfo)
    if target:GetClass() ~= "prop_physics" then return end
    if not target.D6_BeingHeld then return end
    local model = target:GetModel() or ""
    if string.find(model, "explosive") or string.find(model, "canister") or string.find(model, "gascan") then
        local owner = target.D6_MasterOwner
        target:SetCollisionGroup(COLLISION_GROUP_NONE)
        target.D6_BeingHeld = false
        target:Fire("Break", "", 0)
        if IsValid(owner) then
            owner:EmitSound("ambient/energy/zap5.wav", 85, 90)
            local dmg = DamageInfo()
            dmg:SetDamage(130)
            dmg:SetAttacker(dmginfo:GetAttacker())
            dmg:SetInflictor(target)
            dmg:SetDamageType(DMG_BLAST)
            owner:TakeDamageInfo(dmg)
            owner.D6_HeldProps = {}
        end
    end
end)

hook.Add("EntityTakeDamage", "D6_ThrownPropHitPlayer", function(target, dmginfo)
    if not target:IsPlayer() then return end
    local inflictor = dmginfo:GetInflictor()
    if not IsValid(inflictor) then return end
    if inflictor:GetClass() ~= "prop_physics" then return end
    if not D6_ThrownProps[inflictor] then return end
    util.BlastDamage(inflictor, inflictor, inflictor:GetPos(), 150, 60)
    target:EmitSound("ambient/energy/zap5.wav", 80, 90)
    local ef = EffectData(); ef:SetOrigin(inflictor:GetPos()); ef:SetScale(1.5)
    util.Effect("Explosion", ef)
    D6_ThrownProps[inflictor] = nil
end)

hook.Add("EntityRemoved", "D6_CleanHeldPropFlags", function(ent)
    if ent.D6_BeingHeld then
        ent.D6_BeingHeld   = false
        ent.D6_MasterOwner = nil
    end
    if D6_ThrownProps then D6_ThrownProps[ent] = nil end
end)

-- =========================================================
-- BOIDS: роевое поведение для mg, rpg, laser, seeker
-- =========================================================
local function GetSwarmNeighbors(drone)
    local neighbors = {}
    for _, ent in ipairs(ents.FindInSphere(drone:GetPos(), SWARM_RADIUS)) do
        if IsValid(ent) and ent ~= drone
        and ent:GetClass() == "npc_cscanner"
        and ent.D6_Variant
        and not BOIDS_EXCLUDED[ent.D6_Variant] then
            table.insert(neighbors, ent)
        end
    end
    return neighbors
end

hook.Add("Think", "D6_Swarm_AI_Core", function()
    for _, drone in ipairs(_D6ScannerCache) do
        if not IsValid(drone) then continue end
        if not drone.D6_Variant then continue end
        if BOIDS_EXCLUDED[drone.D6_Variant] then continue end
        if drone.D6_Variant == "grav" then continue end

        local phys = SafeGetPhys(drone)
        if not phys then continue end

        local myPos     = drone:GetPos()
        local neighbors = GetSwarmNeighbors(drone)
        local count     = #neighbors

        local separation = Vector(0, 0, 0)
        local cohesion   = Vector(0, 0, 0)
        local alignment  = Vector(0, 0, 0)
        local targetDir  = Vector(0, 0, 0)

        local targetPlayer, minDist = nil, 999999
        for _, ply in ipairs(player.GetAll()) do
            if ply:Alive() and ply:GetNWBool("D6On", false) then
                local d = myPos:Distance(ply:GetPos())
                if d < minDist then minDist = d; targetPlayer = ply end
            end
        end

        if count > 0 then
            local centerOfMass = Vector(0, 0, 0)
            local avgVelocity  = Vector(0, 0, 0)
            for _, neighbor in ipairs(neighbors) do
                local nPos = neighbor:GetPos()
                local d    = myPos:Distance(nPos)
                if d < SEPARATION_DIST and d > 0 then
                    separation = separation + (myPos - nPos):GetNormalized() / d
                end
                centerOfMass = centerOfMass + nPos
                avgVelocity  = avgVelocity  + neighbor:GetVelocity()
            end
            centerOfMass = centerOfMass / count
            cohesion  = (centerOfMass - myPos):GetNormalized()
            alignment = (avgVelocity / count):GetNormalized()
        end

        if IsValid(targetPlayer) then
            targetDir = (targetPlayer:GetPos() - myPos):GetNormalized()
            if drone.D6_Variant == "mg" and minDist < 400 then
                targetDir = -targetDir * 0.5
            end
        end

        local jitter   = VectorRand() * 0.1
        local finalVel = (separation * WEIGHT_SEPARATION)
                       + (cohesion   * WEIGHT_COHESION)
                       + (alignment  * WEIGHT_ALIGNMENT)
                       + (targetDir  * WEIGHT_TARGET)
                       + jitter
        finalVel:Normalize()

        local newVel = LerpVector(FrameTime() * 3, phys:GetVelocity(), finalVel * SWARM_MAX_SPEED)
        phys:SetVelocity(newVel)

        if IsValid(targetPlayer) then
            local targetAng = (targetPlayer:GetPos() - myPos):Angle()
            drone:SetAngles(LerpAngle(FrameTime() * 5, drone:GetAngles(), targetAng))
        end
    end
end)

print("[D6] d6_ai.lua OK")
