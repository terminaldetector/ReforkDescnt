-- =========================================================
-- DESCENT 6DOF: d6_frags2.lua
-- Бестиарий волна 2: ракетные элиты и страйдеры
--
-- НОВЫЕ ПРОТИВНИКИ:
--   rocket_elite_homing – Ракетный элитный (ГСН-ракета)
--   rocket_elite_aa     – Ракетный элитный (зенитка с взрывчаткой)
--   rocket_strider      – Ракетный страйдер (Gatling + боковые ракеты)
--   plasma_strider      – Плазмострайдер (серии combine_ball)
--
-- КОМАНДЫ СПАВНА:
--   6dof_spawn_rocket_elite_homing – ракетный ГСН-элита
--   6dof_spawn_rocket_elite_aa     – зенитка-элита
--   6dof_spawn_rocket_strider      – ракетный страйдер
--   6dof_spawn_plasma_strider      – плазмострайдер
--   6dof_spawn_wave2               – все 4 типа сразу
--
-- СОВМЕСТИМОСТЬ:
--   Диспетчер d6_modules.lua пропускает эти NPC (D6_AI == nil).
--   D6_FragsBoidsExclude расширяется — Boids их игнорирует.
--   Не трогает d6_frags.lua, d6_ai_roles.lua, d6_modules.lua.
-- =========================================================

if not SERVER then return end

-- ─── Исключение из Boids ──────────────────────────────────
D6_FragsBoidsExclude = D6_FragsBoidsExclude or {}
D6_FragsBoidsExclude["rocket_elite_homing"] = true
D6_FragsBoidsExclude["rocket_elite_aa"]     = true
D6_FragsBoidsExclude["rocket_strider"]      = true
D6_FragsBoidsExclude["plasma_strider"]      = true

-- ─── Цвета ───────────────────────────────────────────────
local WAVE2_COLORS = {
    rocket_elite_homing = Color(255, 140,   0),   -- оранжевый
    rocket_elite_aa     = Color( 60, 160,  60),   -- военный зелёный
    rocket_strider      = Color( 50,  50,  80),   -- тёмный стальной
    plasma_strider      = Color(120,  60, 255),   -- фиолетово-синий
}

-- ─── Константы ───────────────────────────────────────────

-- Ракетный элитный — ГСН-ракета
local REH_HP          = 300
local REH_SCALE       = 1.3
local REH_SHIELD      = 80
local REH_RANGE_MIN   = 500
local REH_RANGE_MAX   = 800
local REH_FIRE_CD     = 5.0    -- кулдаун между пусками
local REH_MISSILE_SPD = 2200   -- скорость ракеты
local REH_TURN_RATE   = 0.08   -- LerpAngle alpha (агрессивнее сикера 0.06)
local REH_MISSILE_LT  = 12     -- время жизни ракеты (с)
local REH_ORBIT_SPEED = 45     -- скорость орбиты (°/с)

-- Ракетный элитный — зенитка (AA)
local REAA_HP          = 250
local REAA_SCALE       = 1.2
local REAA_FIRE_CD     = 4.0   -- кулдаун между залпами
local REAA_SALVO       = 5     -- ракет в залпе
local REAA_SPREAD      = 14    -- угол разброса залпа (градусы)
local REAA_MISSILE_SPD = 2400
local REAA_RESIST      = 0.7   -- 30% резист кинетике (DMG_BULLET)

-- Ракетный страйдер
local RS_HP           = 800
local RS_SCALE        = 2.2
local RS_RESIST       = 0.75   -- 25% резист всему входящему урону
local RS_RANGE_MIN    = 600
local RS_RANGE_MAX    = 1000
local RS_GATL_CD      = 0.12   -- интервал пулемёта
local RS_GATL_DMG     = 12
local RS_GATL_RANGE   = 800
local RS_ROCK_CD      = 4.0    -- кулдаун ракетных пушек
local RS_ROCK_SPD     = 1800
local RS_ORBIT_SPEED  = 20     -- тяжёлый — орбитирует медленно

-- Плазмострайдер
local PS_HP           = 600
local PS_SCALE        = 2.0
local PS_SHIELD       = 200
local PS_RANGE_MIN    = 800
local PS_RANGE_MAX    = 1200
local PS_FIRE_CD      = 3.0    -- кулдаун между сериями
local PS_BURST        = 3      -- шаров в серии
local PS_BURST_INT    = 0.5    -- интервал внутри серии (с)
local PS_BALL_SPD     = 900
local PS_BALL_BOOM_R  = 220
local PS_BALL_BOOM_D  = 120
local PS_ORBIT_SPEED  = 15

-- ─── Вспомогательные ─────────────────────────────────────
local function FindNearest6DOFPlayer(pos, maxRange)
    local best, bestDistSq = nil, (maxRange or 99999) ^ 2
    for _, ply in ipairs(player.GetAll()) do
        if ply:Alive() and ply:GetNWBool("D6On", false) then
            local dSq = pos:DistToSqr(ply:GetPos())
            if dSq < bestDistSq then
                bestDistSq = dSq
                best       = ply
            end
        end
    end
    return best
end

-- Таблица активных ГСН-ракет волны 2 (отдельная от D6_SeekerMissiles)
D6_Wave2Missiles = D6_Wave2Missiles or {}

-- =========================================================
-- ╔════════════════════════════════════════════════════════╗
-- ║  1. РАКЕТНЫЙ ЭЛИТНЫЙ (ГСН) — rocket_elite_homing      ║
-- ║  npc_cscanner, масштаб 1.3, щит 80 HP                 ║
-- ║  Орбитирует на 500-800 u, стреляет одной               ║
-- ║  высокоманёвренной ГСН-ракетой каждые 5 с.             ║
-- ╚════════════════════════════════════════════════════════╝
-- =========================================================

function SpawnRocketEliteHoming(ply, customPos)
    local pos = customPos
    if not pos then
        local tr = ply:GetEyeTrace()
        pos = tr.HitPos + tr.HitNormal * 60
    end

    local npc = ents.Create("npc_cscanner")
    if not IsValid(npc) then return nil end

    npc:SetPos(pos)
    npc:Spawn()
    npc:Activate()

    npc:SetModelScale(REH_SCALE, 0)
    npc:SetMaxHealth(REH_HP)
    npc:SetHealth(REH_HP)
    npc:SetRenderMode(RENDERMODE_TRANSCOLOR)
    npc:SetColor(WAVE2_COLORS.rocket_elite_homing)

    npc.D6_Variant     = "rocket_elite_homing"
    npc.D6_Shield      = REH_SHIELD
    npc.D6_ShieldMax   = REH_SHIELD
    npc.D6_NextMissile = CurTime() + 2.5
    npc.D6_OrbitPhase  = math.Rand(0, math.pi * 2)  -- рандомная фаза орбиты

    if IsValid(ply) then
        npc:AddEntityRelationship(ply, D_HT, 99)
    end
    return npc
end

concommand.Add("6dof_spawn_rocket_elite_homing", function(ply)
    if not IsValid(ply) then return end
    ply.D6SpawnCD = ply.D6SpawnCD or 0
    if CurTime() < ply.D6SpawnCD then return end
    ply.D6SpawnCD = CurTime() + 2.5
    SpawnRocketEliteHoming(ply)
    ply:PrintMessage(HUD_PRINTTALK,
        "[ГСН-ЭЛИТА] Ракетный элитный (самонаводка) развёрнут!")
end)

-- ── Think: орбита + ГСН-ракета ───────────────────────────
hook.Add("Think", "D6Frags2_HomingEliteAI", function()
    for _, npc in ipairs(ents.FindByClass("npc_cscanner")) do
        if not IsValid(npc) or npc.D6_Variant ~= "rocket_elite_homing" then continue end

        local myPos = npc:GetPos()
        local phys  = npc:GetPhysicsObject()
        if not IsValid(phys) then continue end

        local enemy = FindNearest6DOFPlayer(myPos, 4000)
        if not IsValid(enemy) then
            phys:SetVelocity(phys:GetVelocity() * 0.9)
            continue
        end

        local enemyPos   = enemy:GetPos()
        local dist       = myPos:Distance(enemyPos)
        local dirToEnemy = (enemyPos - myPos):GetNormalized()

        -- Плавная орбита: кружим в XY + медленное покачивание по Z
        local t          = CurTime() * (REH_ORBIT_SPEED / 57.3)  -- °/с → рад/с
        local phase      = npc.D6_OrbitPhase or 0
        local orbitDist  = (REH_RANGE_MIN + REH_RANGE_MAX) * 0.5
        local orbitPos   = enemyPos + Vector(
            math.cos(t + phase)        * orbitDist,
            math.sin(t + phase)        * orbitDist,
            math.sin(t * 0.4 + phase)  * 120
        )

        local wantVel = (orbitPos - myPos) * 2.5
        -- Ограничиваем скорость сближения (не телепортируемся)
        if wantVel:Length() > 600 then
            wantVel = wantVel:GetNormalized() * 600
        end

        local curVel = phys:GetVelocity()
        phys:SetVelocity(LerpVector(FrameTime() * 3.5, curVel, wantVel))

        -- Нос смотрит на игрока
        local wantAng = dirToEnemy:Angle()
        npc:SetAngles(LerpAngle(FrameTime() * 5, npc:GetAngles(), wantAng))

        -- Щит регенерируется (+5/с с задержкой 3 с после удара)
        if (npc.D6_Shield or 0) < (npc.D6_ShieldMax or 0) then
            if CurTime() > (npc.D6_ShieldLastHit or 0) + 3 then
                npc.D6_Shield = math.min(npc.D6_ShieldMax,
                    (npc.D6_Shield or 0) + 5 * FrameTime())
            end
        end

        -- Пуск ГСН-ракеты
        if CurTime() > (npc.D6_NextMissile or 0) then
            npc.D6_NextMissile = CurTime() + REH_FIRE_CD

            local tr = util.TraceLine({ start = myPos, endpos = enemyPos, filter = npc })
            if tr.Entity == enemy or dist < 700 then
                local m = ents.Create("rpg_missile")
                if IsValid(m) then
                    m:SetPos(myPos + dirToEnemy * 60)
                    m:SetAngles(dirToEnemy:Angle())
                    m:SetOwner(npc)
                    m:Spawn()

                    local mph = m:GetPhysicsObject()
                    if IsValid(mph) then
                        mph:SetVelocity(dirToEnemy * REH_MISSILE_SPD)
                    end

                    D6_Wave2Missiles[m] = {
                        target     = enemy,
                        owner      = npc,
                        expireTime = CurTime() + REH_MISSILE_LT,
                        turnRate   = REH_TURN_RATE,
                        speed      = REH_MISSILE_SPD,
                    }

                    timer.Simple(REH_MISSILE_LT, function()
                        if IsValid(m) then
                            D6_Wave2Missiles[m] = nil
                            m:Remove()
                        end
                    end)

                    npc:EmitSound("weapons/rpg/rocketfire1.wav", 85, 95)
                    m:EmitSound("weapons/rpg/rocketfire1.wav", 78, 110)
                end
            end
        end

        phys:AddAngleVelocity(-phys:GetAngleVelocity() * 0.22)
    end
end)

-- ── Щит ГСН-элиты поглощает урон ─────────────────────────
hook.Add("EntityTakeDamage", "D6Frags2_HomingEliteShield", function(target, dmginfo)
    if not IsValid(target) or target.D6_Variant ~= "rocket_elite_homing" then return end
    local shield = target.D6_Shield or 0
    if shield <= 0 then return end

    local dmg = dmginfo:GetDamage()
    if shield >= dmg then
        target.D6_Shield = shield - dmg
        dmginfo:SetDamage(0)
    else
        target.D6_Shield = 0
        dmginfo:SetDamage(dmg - shield)
    end
    target.D6_ShieldLastHit = CurTime()

    local ef = EffectData()
    ef:SetOrigin(target:GetPos()); ef:SetScale(0.8)
    util.Effect("cball_bounce", ef)
end)

-- =========================================================
-- ╔════════════════════════════════════════════════════════╗
-- ║  2. РАКЕТНЫЙ ЭЛИТНЫЙ (ЗЕНИТКА-AA) — rocket_elite_aa   ║
-- ║  npc_cscanner, масштаб 1.2                             ║
-- ║  Держится у "земли" (Z спавна), преследует игрока      ║
-- ║  горизонтально. Залп из 5 ракет с веером вверх,        ║
-- ║  каждые 4 с. 30% резист пулям.                         ║
-- ╚════════════════════════════════════════════════════════╝
-- =========================================================

function SpawnRocketEliteAA(ply, customPos)
    local pos = customPos
    if not pos then
        local tr = ply:GetEyeTrace()
        pos = tr.HitPos + tr.HitNormal * 40
    end

    local npc = ents.Create("npc_cscanner")
    if not IsValid(npc) then return nil end

    npc:SetPos(pos)
    npc:Spawn()
    npc:Activate()

    npc:SetModelScale(REAA_SCALE, 0)
    npc:SetMaxHealth(REAA_HP)
    npc:SetHealth(REAA_HP)
    npc:SetRenderMode(RENDERMODE_TRANSCOLOR)
    npc:SetColor(WAVE2_COLORS.rocket_elite_aa)

    npc.D6_Variant   = "rocket_elite_aa"
    npc.D6_NextSalvo = CurTime() + 3.0
    npc.D6_GroundZ   = pos.z  -- зенитка "приземляется" на высоту спавна

    if IsValid(ply) then
        npc:AddEntityRelationship(ply, D_HT, 99)
    end
    return npc
end

concommand.Add("6dof_spawn_rocket_elite_aa", function(ply)
    if not IsValid(ply) then return end
    ply.D6SpawnCD = ply.D6SpawnCD or 0
    if CurTime() < ply.D6SpawnCD then return end
    ply.D6SpawnCD = CurTime() + 2.5
    SpawnRocketEliteAA(ply)
    ply:PrintMessage(HUD_PRINTTALK,
        "[ЗЕНИТКА-AA] Зенитная ракетная установка развёрнута!")
end)

-- ── Think: удержание высоты + зенитный залп ──────────────
hook.Add("Think", "D6Frags2_AAAI", function()
    for _, npc in ipairs(ents.FindByClass("npc_cscanner")) do
        if not IsValid(npc) or npc.D6_Variant ~= "rocket_elite_aa" then continue end

        local myPos = npc:GetPos()
        local phys  = npc:GetPhysicsObject()
        if not IsValid(phys) then continue end

        local enemy = FindNearest6DOFPlayer(myPos, 3500)
        if not IsValid(enemy) then
            phys:SetVelocity(phys:GetVelocity() * 0.85)
            continue
        end

        local enemyPos = enemy:GetPos()
        local groundZ  = npc.D6_GroundZ or myPos.z
        local hoverZ   = groundZ + 90  -- зависает чуть выше точки спавна

        -- Горизонтальное преследование (игнорируем Z)
        local myXY      = Vector(myPos.x,    myPos.y,    0)
        local enemyXY   = Vector(enemyPos.x, enemyPos.y, 0)
        local toEnemyXY = enemyXY - myXY
        local horizDist = toEnemyXY:Length()
        local horizDir  = horizDist > 1 and toEnemyXY / horizDist or npc:GetForward()

        local wantVel = Vector(0, 0, 0)
        if horizDist < 350 then
            -- Слишком близко — отступаем
            wantVel.x = -horizDir.x * 200
            wantVel.y = -horizDir.y * 200
        elseif horizDist > 650 then
            -- Слишком далеко — догоняем
            wantVel.x = horizDir.x * 220
            wantVel.y = horizDir.y * 220
        end
        -- Всегда возвращаемся к "земной" высоте
        wantVel.z = (hoverZ - myPos.z) * 3.5

        phys:SetVelocity(LerpVector(FrameTime() * 3, phys:GetVelocity(), wantVel))

        -- Направляем нос в сторону игрока (будет смотреть вверх)
        local dirToEnemy = (enemyPos - myPos):GetNormalized()
        local wantAng    = dirToEnemy:Angle()
        npc:SetAngles(LerpAngle(FrameTime() * 4, npc:GetAngles(), wantAng))

        -- Зенитный залп (только если игрок ВЫШЕ нас — нас это зенитка, не наземное)
        if CurTime() > (npc.D6_NextSalvo or 0) and enemyPos.z > myPos.z - 100 then
            npc.D6_NextSalvo = CurTime() + REAA_FIRE_CD

            npc:EmitSound("weapons/rpg/rocketfire1.wav", 90, 80)

            for i = 1, REAA_SALVO do
                local delay = (i - 1) * 0.1
                timer.Simple(delay, function()
                    if not IsValid(npc) then return end

                    local firePos = npc:GetPos()
                    local tgtPos  = enemy:GetPos() + Vector(0, 0, 30)
                    local baseDir = (tgtPos - firePos):GetNormalized()
                    local baseAng = baseDir:Angle()

                    -- Равномерный веер по оси тангажа
                    local fanFrac = (REAA_SALVO > 1)
                        and ((i - 1) / (REAA_SALVO - 1) - 0.5)
                        or 0
                    baseAng.p = baseAng.p + fanFrac * REAA_SPREAD * 2
                    -- Небольшой случайный разброс по курсу
                    baseAng.y = baseAng.y + math.Rand(-REAA_SPREAD * 0.35, REAA_SPREAD * 0.35)

                    local fireDir = baseAng:Forward()

                    local m = ents.Create("rpg_missile")
                    if not IsValid(m) then return end

                    m:SetPos(firePos + fireDir * 40)
                    m:SetAngles(baseAng)
                    m:SetOwner(npc)
                    m:Spawn()

                    local mph = m:GetPhysicsObject()
                    if IsValid(mph) then
                        mph:SetVelocity(fireDir * REAA_MISSILE_SPD)
                    end

                    timer.Simple(7, function()
                        if IsValid(m) then m:Remove() end
                    end)
                end)
            end
        end

        phys:AddAngleVelocity(-phys:GetAngleVelocity() * 0.28)
    end
end)

-- ── 30% резист кинетике для зенитки ──────────────────────
hook.Add("EntityTakeDamage", "D6Frags2_AAArmor", function(target, dmginfo)
    if not IsValid(target) or target.D6_Variant ~= "rocket_elite_aa" then return end
    if bit.band(dmginfo:GetDamageType(), DMG_BULLET) ~= 0 then
        dmginfo:SetDamage(dmginfo:GetDamage() * REAA_RESIST)
    end
end)

-- =========================================================
-- ╔════════════════════════════════════════════════════════╗
-- ║  3. РАКЕТНЫЙ СТРАЙДЕР — rocket_strider                 ║
-- ║  npc_cscanner, масштаб 2.2, HP 800                     ║
-- ║  Два оружия одновременно:                              ║
-- ║  • Gatling — пули через каждые 0.12 с (до 800 u)       ║
-- ║  • Ракетные пушки — залп по 2 ракеты с боков (4 с КД) ║
-- ║  25% резист ВСЕМУ входящему урону.                     ║
-- ╚════════════════════════════════════════════════════════╝
-- =========================================================

function SpawnRocketStrider(ply, customPos)
    local pos = customPos
    if not pos then
        local tr = ply:GetEyeTrace()
        pos = tr.HitPos + tr.HitNormal * 100
    end

    local npc = ents.Create("npc_cscanner")
    if not IsValid(npc) then return nil end

    npc:SetPos(pos)
    npc:Spawn()
    npc:Activate()

    npc:SetModelScale(RS_SCALE, 0)
    npc:SetMaxHealth(RS_HP)
    npc:SetHealth(RS_HP)
    npc:SetRenderMode(RENDERMODE_TRANSCOLOR)
    npc:SetColor(WAVE2_COLORS.rocket_strider)

    npc.D6_Variant    = "rocket_strider"
    npc.D6_NextGatl   = CurTime() + 2.0
    npc.D6_NextRocket = CurTime() + 3.5
    npc.D6_OrbitPhase = math.Rand(0, math.pi * 2)
    npc.D6_GatlSide   = 1   -- чередование: левый/правый ствол Gatling

    if IsValid(ply) then
        npc:AddEntityRelationship(ply, D_HT, 99)
    end
    return npc
end

concommand.Add("6dof_spawn_rocket_strider", function(ply)
    if not IsValid(ply) then return end
    ply.D6SpawnCD = ply.D6SpawnCD or 0
    if CurTime() < ply.D6SpawnCD then return end
    ply.D6SpawnCD = CurTime() + 2.5
    SpawnRocketStrider(ply)
    ply:PrintMessage(HUD_PRINTTALK,
        "[СТРАЙДЕР] Ракетный страйдер развёрнут!")
end)

-- ── Think: тяжёлая орбита + Gatling + боковые ракеты ─────
hook.Add("Think", "D6Frags2_RocketStriderAI", function()
    for _, npc in ipairs(ents.FindByClass("npc_cscanner")) do
        if not IsValid(npc) or npc.D6_Variant ~= "rocket_strider" then continue end

        local myPos = npc:GetPos()
        local phys  = npc:GetPhysicsObject()
        if not IsValid(phys) then continue end

        local enemy = FindNearest6DOFPlayer(myPos, 5000)
        if not IsValid(enemy) then
            phys:SetVelocity(phys:GetVelocity() * 0.9)
            continue
        end

        local enemyPos   = enemy:GetPos()
        local dist       = myPos:Distance(enemyPos)
        local dirToEnemy = (enemyPos - myPos):GetNormalized()

        -- Медленная тяжёлая орбита
        local t         = CurTime() * (RS_ORBIT_SPEED / 57.3)
        local phase     = npc.D6_OrbitPhase or 0
        local orbitDist = (RS_RANGE_MIN + RS_RANGE_MAX) * 0.5
        local orbitPos  = enemyPos + Vector(
            math.cos(t + phase)        * orbitDist,
            math.sin(t + phase)        * orbitDist,
            math.sin(t * 0.25 + phase) * 80
        )

        local wantVel = (orbitPos - myPos) * 1.8
        if wantVel:Length() > 450 then
            wantVel = wantVel:GetNormalized() * 450
        end

        phys:SetVelocity(LerpVector(FrameTime() * 2, phys:GetVelocity(), wantVel))

        -- Медленный поворот (тяжёлая машина)
        local wantAng = dirToEnemy:Angle()
        npc:SetAngles(LerpAngle(FrameTime() * 2.5, npc:GetAngles(), wantAng))

        -- ── Gatling: пули при дальности < RS_GATL_RANGE ─────
        if dist < RS_GATL_RANGE and CurTime() > (npc.D6_NextGatl or 0) then
            npc.D6_NextGatl = CurTime() + RS_GATL_CD

            -- Слабое упреждение (Gatling — подавляющий огонь, не снайпер)
            local predPos = enemyPos + (enemy.D6_Vel or enemy:GetVelocity()) * 0.04
            local gDir    = (predPos - myPos):GetNormalized()

            -- Чередование левого и правого стволов (визуальный эффект)
            local side   = npc.D6_GatlSide
            npc.D6_GatlSide = -side
            local offset = npc:GetRight() * (10 * side * RS_SCALE)
            local src    = myPos + offset + npc:GetForward() * 35

            npc:FireBullets({
                Num        = 1,
                Src        = src,
                Dir        = (gDir + VectorRand() * 0.05):GetNormalized(),
                Spread     = Vector(0.05, 0.05, 0),
                Tracer     = 1,
                TracerName = "ToolTracer",
                Force      = 4,
                Damage     = RS_GATL_DMG,
                Attacker   = npc,
            })

            -- Звук Gatling (случайный, чтобы не спамить)
            if math.random(1, 5) == 1 then
                npc:EmitSound("weapons/ar2/ar2_fire1.wav", 68, 115)
            end
        end

        -- ── Ракетные пушки: залп с двух боковых позиций ─────
        if CurTime() > (npc.D6_NextRocket or 0) then
            npc.D6_NextRocket = CurTime() + RS_ROCK_CD

            -- Сильное упреждение для ракет (они медленнее пуль)
            local tgtVel  = enemy.D6_Vel or enemy:GetVelocity()
            local predPos = enemyPos + tgtVel * (dist / RS_ROCK_SPD * 0.55)

            npc:EmitSound("weapons/rpg/rocketfire1.wav", 90, 78)

            -- Левая и правая ракетные пушки (0.2 с задержка между ними)
            for sIdx, sSign in ipairs({ -1, 1 }) do
                local delay = (sIdx - 1) * 0.2
                timer.Simple(delay, function()
                    if not IsValid(npc) then return end

                    local rPos    = npc:GetPos()
                    local rRight  = npc:GetRight()
                    local rFwd    = npc:GetForward()
                    -- Боковое смещение: пушки у "крыльев" страйдера
                    local sideOfs = rRight * (55 * sSign * RS_SCALE)
                    local firePos = rPos + sideOfs + rFwd * 25

                    local rDir = (predPos - firePos):GetNormalized()

                    local m = ents.Create("rpg_missile")
                    if not IsValid(m) then return end

                    m:SetPos(firePos)
                    m:SetAngles(rDir:Angle())
                    m:SetOwner(npc)
                    m:Spawn()

                    local mph = m:GetPhysicsObject()
                    if IsValid(mph) then
                        mph:SetVelocity(rDir * RS_ROCK_SPD)
                    end

                    timer.Simple(8, function()
                        if IsValid(m) then m:Remove() end
                    end)

                    m:EmitSound("weapons/rpg/rocketfire1.wav", 80, 98)
                end)
            end
        end

        phys:AddAngleVelocity(-phys:GetAngleVelocity() * 0.14)
    end
end)

-- ── 25% резист всему урону для страйдера ─────────────────
hook.Add("EntityTakeDamage", "D6Frags2_StriderArmor", function(target, dmginfo)
    if not IsValid(target) or target.D6_Variant ~= "rocket_strider" then return end
    dmginfo:SetDamage(dmginfo:GetDamage() * RS_RESIST)
end)

-- =========================================================
-- ╔════════════════════════════════════════════════════════╗
-- ║  4. ПЛАЗМОСТРАЙДЕР — plasma_strider                    ║
-- ║  npc_cscanner, масштаб 2.0, щит 200 HP                ║
-- ║  Медленная орбита 800-1200 u, серии из 3 шаров         ║
-- ║  combine_ball с предсказанием (как AR2 alt-fire).      ║
-- ║  Шары взрываются при контакте ИЛИ через 6 с.           ║
-- ╚════════════════════════════════════════════════════════╝
-- =========================================================

function SpawnPlasmaStrider(ply, customPos)
    local pos = customPos
    if not pos then
        local tr = ply:GetEyeTrace()
        pos = tr.HitPos + tr.HitNormal * 100
    end

    local npc = ents.Create("npc_cscanner")
    if not IsValid(npc) then return nil end

    npc:SetPos(pos)
    npc:Spawn()
    npc:Activate()

    npc:SetModelScale(PS_SCALE, 0)
    npc:SetMaxHealth(PS_HP)
    npc:SetHealth(PS_HP)
    npc:SetRenderMode(RENDERMODE_TRANSCOLOR)
    npc:SetColor(WAVE2_COLORS.plasma_strider)

    npc.D6_Variant    = "plasma_strider"
    npc.D6_Shield     = PS_SHIELD
    npc.D6_ShieldMax  = PS_SHIELD
    npc.D6_NextPlasma = CurTime() + 4.0
    npc.D6_OrbitPhase = math.Rand(0, math.pi * 2)

    if IsValid(ply) then
        npc:AddEntityRelationship(ply, D_HT, 99)
    end
    return npc
end

concommand.Add("6dof_spawn_plasma_strider", function(ply)
    if not IsValid(ply) then return end
    ply.D6SpawnCD = ply.D6SpawnCD or 0
    if CurTime() < ply.D6SpawnCD then return end
    ply.D6SpawnCD = CurTime() + 2.5
    SpawnPlasmaStrider(ply)
    ply:PrintMessage(HUD_PRINTTALK,
        "[ПЛАЗМАСТР.] Плазмострайдер развёрнут!")
end)

-- ── Think: медленная орбита + серии combine_ball ──────────
hook.Add("Think", "D6Frags2_PlasmaStriderAI", function()
    for _, npc in ipairs(ents.FindByClass("npc_cscanner")) do
        if not IsValid(npc) or npc.D6_Variant ~= "plasma_strider" then continue end

        local myPos = npc:GetPos()
        local phys  = npc:GetPhysicsObject()
        if not IsValid(phys) then continue end

        local enemy = FindNearest6DOFPlayer(myPos, 5000)
        if not IsValid(enemy) then
            phys:SetVelocity(phys:GetVelocity() * 0.9)
            continue
        end

        local enemyPos   = enemy:GetPos()
        local dist       = myPos:Distance(enemyPos)
        local dirToEnemy = (enemyPos - myPos):GetNormalized()

        -- Неспешная дальняя орбита
        local t         = CurTime() * (PS_ORBIT_SPEED / 57.3)
        local phase     = npc.D6_OrbitPhase or 0
        local orbitDist = (PS_RANGE_MIN + PS_RANGE_MAX) * 0.5
        local orbitPos  = enemyPos + Vector(
            math.cos(t + phase)       * orbitDist,
            math.sin(t + phase)       * orbitDist,
            math.sin(t * 0.3 + phase) * 100
        )

        local wantVel = (orbitPos - myPos) * 1.5
        if wantVel:Length() > 380 then
            wantVel = wantVel:GetNormalized() * 380
        end

        phys:SetVelocity(LerpVector(FrameTime() * 2, phys:GetVelocity(), wantVel))

        -- Медленный прицел
        local wantAng = dirToEnemy:Angle()
        npc:SetAngles(LerpAngle(FrameTime() * 2, npc:GetAngles(), wantAng))

        -- Щит медленно регенерируется (после 4 с без урона)
        if (npc.D6_Shield or 0) < (npc.D6_ShieldMax or 0)
            and CurTime() > (npc.D6_ShieldLastHit or 0) + 4 then
            npc.D6_Shield = math.min(npc.D6_ShieldMax,
                (npc.D6_Shield or 0) + 2.5 * FrameTime())
        end

        -- Серия из PS_BURST плазменных шаров
        if CurTime() > (npc.D6_NextPlasma or 0) then
            npc.D6_NextPlasma = CurTime() + PS_FIRE_CD

            local tr = util.TraceLine({ start = myPos, endpos = enemyPos, filter = npc })
            if tr.Entity == enemy or dist < PS_RANGE_MIN + 100 then

                npc:EmitSound("weapons/ar2/fire1.wav", 95, 52)

                for i = 1, PS_BURST do
                    local delay = (i - 1) * PS_BURST_INT
                    timer.Simple(delay, function()
                        if not IsValid(npc) then return end

                        local firePos = npc:GetPos()
                        -- Предсказание по времени полёта шара
                        local tgtVel  = enemy.D6_Vel or enemy:GetVelocity()
                        local tof     = dist / PS_BALL_SPD
                        local predPos = enemy:GetPos() + Vector(0, 0, 30) + tgtVel * tof * 0.55
                        local ballDir = (predPos - firePos):GetNormalized()

                        local ball = ents.Create("prop_combine_ball")
                        if not IsValid(ball) then return end

                        ball:SetPos(firePos + ballDir * 80)
                        ball:SetAngles(ballDir:Angle())
                        ball:SetOwner(npc)
                        ball:SetModel("models/effects/combineball.mdl")
                        ball:SetSaveValue("m_nMaxBounces", 0)
                        ball:Spawn()

                        local bp = ball:GetPhysicsObject()
                        if IsValid(bp) then
                            bp:EnableGravity(false)
                            bp:EnableDrag(false)
                            bp:SetMass(1)
                            bp:SetVelocity(ballDir * PS_BALL_SPD)
                            bp:Wake()
                        end

                        ball:Activate()

                        -- Взрыв при физическом контакте (как AR2 alt-fire)
                        ball.PhysicsCollide = function(self2, data, _phys2)
                            if data.Speed < 40 then return end
                            local bPos = self2:GetPos()
                            timer.Simple(0, function()
                                if not IsValid(self2) then return end
                                local ef = EffectData()
                                ef:SetOrigin(bPos); ef:SetScale(4)
                                util.Effect("cball_explode", ef)
                                util.BlastDamage(npc, npc, bPos, PS_BALL_BOOM_R, PS_BALL_BOOM_D)
                                self2:Remove()
                            end)
                        end

                        -- Страховочный взрыв через 6 с (если ни во что не попал)
                        timer.Simple(6, function()
                            if not IsValid(ball) then return end
                            local bPos = ball:GetPos()
                            local ef   = EffectData()
                            ef:SetOrigin(bPos); ef:SetScale(4)
                            util.Effect("cball_explode", ef)
                            util.BlastDamage(npc, npc, bPos, PS_BALL_BOOM_R, PS_BALL_BOOM_D * 0.7)
                            ball:Remove()
                        end)

                        if i == 1 then
                            ball:EmitSound("weapons/ar2/fire1.wav", 84, 58)
                        end
                    end)
                end
            end
        end

        phys:AddAngleVelocity(-phys:GetAngleVelocity() * 0.14)
    end
end)

-- ── Щит плазмострайдера поглощает урон ───────────────────
hook.Add("EntityTakeDamage", "D6Frags2_PlasmaShield", function(target, dmginfo)
    if not IsValid(target) or target.D6_Variant ~= "plasma_strider" then return end
    local shield = target.D6_Shield or 0
    if shield <= 0 then return end

    local dmg = dmginfo:GetDamage()
    if shield >= dmg then
        target.D6_Shield = shield - dmg
        dmginfo:SetDamage(0)
    else
        target.D6_Shield = 0
        dmginfo:SetDamage(dmg - shield)
    end
    target.D6_ShieldLastHit = CurTime()

    local ef = EffectData()
    ef:SetOrigin(target:GetPos()); ef:SetScale(1.2)
    util.Effect("cball_bounce", ef)
end)

-- ─── Think: самонаведение ГСН-ракет волны 2 ──────────────
-- Обрабатывает только D6_Wave2Missiles (независимо от D6_SeekerMissiles).
hook.Add("Think", "D6Frags2_Wave2MissilesThink", function()
    if (D6_Wave2MissileTick or 0) > CurTime() then return end
    D6_Wave2MissileTick = CurTime() + 0.05  -- 20 Hz — как у Seeker

    local toRemove = {}

    for missile, data in pairs(D6_Wave2Missiles) do
        if not IsValid(missile) then
            toRemove[missile] = true
            continue
        end
        if CurTime() > data.expireTime then
            toRemove[missile] = true
            if IsValid(missile) then missile:Remove() end
            continue
        end

        local mph = missile:GetPhysicsObject()
        if not IsValid(mph) then continue end

        local tgt = data.target
        if not IsValid(tgt) or not tgt:Alive() then
            tgt = FindNearest6DOFPlayer(missile:GetPos(), 3000)
            data.target = tgt
        end
        if not IsValid(tgt) then continue end

        local missPos  = missile:GetPos()
        local tgtPos   = tgt:GetPos() + Vector(0, 0, 25)
        local dist     = missPos:Distance(tgtPos)
        local tgtVel   = tgt.D6_Vel or tgt:GetVelocity()
        local tof      = dist / (data.speed or REH_MISSILE_SPD)
        local predPos  = tgtPos + tgtVel * tof * 0.5

        local wantDir  = (predPos - missPos):GetNormalized()
        local newAng   = LerpAngle(data.turnRate or REH_TURN_RATE,
                                    missile:GetAngles(), wantDir:Angle())

        missile:SetAngles(newAng)
        mph:SetVelocity(newAng:Forward() * (data.speed or REH_MISSILE_SPD))
    end

    for m in pairs(toRemove) do D6_Wave2Missiles[m] = nil end
end)

hook.Add("EntityRemoved", "D6Frags2_MissileCleanup", function(ent)
    if D6_Wave2Missiles then D6_Wave2Missiles[ent] = nil end
end)

-- ─── Команда: все 4 типа волны 2 сразу ──────────────────
concommand.Add("6dof_spawn_wave2", function(ply)
    if not IsValid(ply) then return end
    ply.D6SpawnCD = ply.D6SpawnCD or 0
    if CurTime() < ply.D6SpawnCD then return end
    ply.D6SpawnCD = CurTime() + 2.5

    local tr      = ply:GetEyeTrace()
    local basePos = tr.HitPos + tr.HitNormal * 80

    local types = {
        { fn = SpawnRocketEliteHoming, label = "ГСН-элита"  },
        { fn = SpawnRocketEliteAA,     label = "зенитка-AA" },
        { fn = SpawnRocketStrider,     label = "страйдер"   },
        { fn = SpawnPlasmaStrider,     label = "плазмастр." },
    }

    for i, t in ipairs(types) do
        local angle  = ((i - 1) / #types) * math.pi * 2
        local offset = Vector(
            math.sin(angle) * 300,
            math.cos(angle) * 300,
            60
        )
        t.fn(ply, basePos + offset)
    end

    ply:PrintMessage(HUD_PRINTTALK, "[ВОЛНА 2] Все 4 типа врагов развёрнуты!")
end)

-- ─── InitPostEntity: информация в консоль ────────────────
hook.Add("InitPostEntity", "D6Frags2_PrintInfo", function()
    MsgC(Color(255, 160, 0),
        "\n[D6 Frags2] Волна 2 загружена. Команды спавна:\n",
        color_white,
        "  6dof_spawn_rocket_elite_homing – Ракетный элитный ГСН   (HP:" .. REH_HP  .. ", щит:" .. REH_SHIELD  .. ")\n",
        "  6dof_spawn_rocket_elite_aa     – Зенитная установка       (HP:" .. REAA_HP .. ")\n",
        "  6dof_spawn_rocket_strider      – Ракетный страйдер        (HP:" .. RS_HP   .. ")\n",
        "  6dof_spawn_plasma_strider      – Плазмострайдер           (HP:" .. PS_HP   .. ", щит:" .. PS_SHIELD  .. ")\n",
        "  6dof_spawn_wave2               – Все 4 типа сразу\n",
        Color(255, 160, 0),
        "[D6 Frags2] Варианты не входят в Boids (D6_FragsBoidsExclude).\n\n"
    )
end)
