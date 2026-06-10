-- =========================================================
-- DESCENT 6DOF: d6_frags.lua
-- Расширенный бестиарий: 5 новых типов врагов
-- Загружается ПОСЛЕ d6_ai.lua (см. autoload.lua)
--
-- НОВЫЕ ПРОТИВНИКИ:
--   worm         – Альянсский червь (npc_headcrab с изменёнными параметрами)
--   wheatley     – Уитли-камикадзе  (npc_cscanner, взрыв при сближении)
--   chopper      – Вертушка-пулемёт (npc_cscanner, медленный поворот, броня)
--   manhack_fast – Быстрый камикадзе-манхок (npc_manhack, рывок+взрыв)
--   seeker       – Тяжёлый сканер с 4 самонаводящимися ракетами
--
-- КОМАНДЫ СПАВНА:
--   6dof_spawn_worm          – червь
--   6dof_spawn_wheatley      – Уитли
--   6dof_spawn_chopper       – вертушка
--   6dof_spawn_manhack       – манхок-камикадзе
--   6dof_spawn_heavy_seeker  – тяжёлый сканер
--   6dof_spawn_bestiary      – полный зоопарк (по одному каждого)
--
-- ИНТЕГРАЦИЯ С d6_ai.lua:
--   Варианты worm, chopper, seeker добавлены в BOIDS_EXCLUDED (через
--   глобальную таблицу D6_FragsBoidsExclude), которую d6_ai.lua может
--   читать. Если d6_ai.lua не обновлён, эти враги просто не войдут
--   в рой Boids — они обрабатываются собственными хуками Think.
--
-- ВСЕ ВРАГИ реагируют на таран и гравипушку игрока корректно:
--   npc_* принимает физический импульс, GetPhysicsObject() валиден.
-- =========================================================

if not SERVER then return end

-- =========================================================
-- ГЛОБАЛЬНАЯ ТАБЛИЦА ДЛЯ ИСКЛЮЧЕНИЙ ИЗ BOIDS
-- d6_ai.lua проверяет D6_FragsBoidsExclude[variant] перед
-- включением дрона в рой. Если d6_ai.lua не обновлён —
-- безвредно (d6_ai.lua проверяет только свою локальную таблицу).
-- =========================================================
D6_FragsBoidsExclude = D6_FragsBoidsExclude or {}
D6_FragsBoidsExclude["worm"]         = true  -- Наземный — в рой не входит
D6_FragsBoidsExclude["chopper"]      = true  -- Своя дистанционная тактика
D6_FragsBoidsExclude["seeker"]       = true  -- Держит позицию самостоятельно
D6_FragsBoidsExclude["wheatley"]     = true  -- Камикадзе — своя логика сближения
D6_FragsBoidsExclude["manhack_fast"] = true  -- Очень быстрый, рой мешает

-- =========================================================
-- ЦВЕТА НОВЫХ ВАРИАНТОВ
-- =========================================================
local FRAG_COLORS = {
    worm         = Color(120, 200, 60),   -- Болотно-зелёный
    wheatley     = Color(200, 220, 255),  -- Почти белый / голубой
    chopper      = Color(80,  80,  80),   -- Тёмно-серый
    manhack_fast = Color(255, 60,  0),    -- Ярко-оранжевый
    seeker       = Color(180, 0,   255),  -- Пурпурный
}

-- =========================================================
-- КОНСТАНТЫ
-- =========================================================

-- Червь
local WORM_SCALE        = 1.8    -- Масштаб модели
local WORM_HP           = 250
local WORM_BITE_DMG     = 40     -- Урон укуса
local WORM_BITE_RANGE   = 120    -- Радиус ближнего боя
local WORM_BITE_CD      = 1.2    -- Задержка укуса (сек)
local WORM_JUMP_SPEED   = 800    -- Скорость прыжка к дрону
local WORM_JUMP_CD      = 2.5
local WORM_EXPLODE_RAD  = 160    -- Радиус взрыва при смерти
local WORM_EXPLODE_DMG  = 90

-- Уитли-камикадзе
local WHEATLEY_SCALE    = 1.3
local WHEATLEY_HP       = 100
local WHEATLEY_TRIG     = 180    -- Дистанция срабатывания взрыва
local WHEATLEY_BOOM_RAD = 200
local WHEATLEY_BOOM_DMG = 120
local WHEATLEY_SPEED    = 620    -- Скорость хаотичного полёта
local WHEATLEY_DRIFT_CD = 0.6    -- Интервал смены случайного вектора

-- Вертушка
local CHOPPER_SCALE     = 0.9
local CHOPPER_HP        = 400
local CHOPPER_ARMOR     = 0.7    -- Входящий урон × 0.7 (30% резист)
local CHOPPER_BURST_RPM = 10     -- Выстрелов в очереди
local CHOPPER_BURST_CD  = 0.1    -- Интервал между выстрелами очереди (сек)
local CHOPPER_RELOAD_CD = 2.5    -- Пауза между очередями (сек)
local CHOPPER_BULLET_DMG = 10
local CHOPPER_TURN_RATE = 1.5    -- Скорость поворота (< у обычных дронов 7)
local CHOPPER_HOVER_DIST = 600   -- Предпочтительная дистанция от игрока

-- Манхок-камикадзе
local MANHACK_HP        = 30
local MANHACK_SPEED     = 1400   -- Очень быстрый
local MANHACK_BOOM_DIST = 55     -- Расстояние детонации
local MANHACK_BOOM_DMG  = 80
local MANHACK_BOOM_RAD  = 130
local MANHACK_DODGE_CD  = 0.3    -- Частые уклонения

-- Тяжёлый сканер-сикер
local SEEKER_SCALE      = 1.6
local SEEKER_HP         = 200
local SEEKER_SALVO      = 4      -- Ракет в залпе
local SEEKER_RELOAD_CD  = 4.0    -- Перезарядка (сек)
local SEEKER_MISSILE_SPD = 2000  -- Скорость ракеты
local SEEKER_MISSILE_LT = 10     -- Время жизни ракеты (сек) — перестраховка
local SEEKER_TURN_RATE  = 0.06   -- Скорость самонаведения (LerpAngle alpha)
local SEEKER_DIST_MIN   = 800    -- Мин. дистанция от игрока
local SEEKER_DIST_MAX   = 1200   -- Макс. дистанция от игрока
local SEEKER_DRIFT_SPD  = 260    -- Скорость дрейфа при удержании позиции

-- =========================================================
-- ВСПОМОГАТЕЛЬНАЯ: найти ближайшего игрока в 6DOF-режиме
-- =========================================================
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

-- =========================================================
-- ВСПОМОГАТЕЛЬНАЯ: безопасный взрыв + визуальный эффект
-- =========================================================
function D6_Explode(attacker, pos, radius, damage)
    util.BlastDamage(attacker, attacker, pos, radius, damage)

    local ef = EffectData()
    ef:SetOrigin(pos)
    ef:SetScale(math.Clamp(radius / 80, 1, 3))
    util.Effect("Explosion", ef)

    local explosion = ents.Create("env_explosion")
    if IsValid(explosion) then
        explosion:SetPos(pos)
        explosion:Spawn()
        explosion:SetKeyValue("iMagnitude", tostring(math.floor(damage * 0.8)))
        explosion:Fire("Explode", 0, 0)
    end
end

-- =========================================================
-- ╔═══════════════════════════════════════════════════════╗
-- ║  1. АЛЬЯНССКИЙ ЧЕРВЬ  (worm)                          ║
-- ║  Базовый класс: npc_headcrab                          ║
-- ║  Поведение: прыгает к дрону, кусает, при смерти       ║
-- ║  взрывается. Наземный — Boids не использует.          ║
-- ╚═══════════════════════════════════════════════════════╝
-- =========================================================

function SpawnWorm(ply, customPos)
    local pos
    if customPos then
        pos = customPos
    else
        local tr = ply:GetEyeTrace()
        pos = tr.HitPos + tr.HitNormal * 40
    end

    local npc = ents.Create("npc_headcrab")
    if not IsValid(npc) then
        ply:PrintMessage(HUD_PRINTTALK, "[ЧЕРВЬ] npc_headcrab недоступен!")
        return
    end

    npc:SetPos(pos)
    npc:Spawn()
    npc:Activate()

    -- Используем headcrab_classic — у него есть анимации атаки
    -- Если карта не содержит модель antlion_grub — используем headcrab
    local wormModel = "models/headcrab.mdl"
    npc:SetModel(wormModel)
    npc:SetModelScale(WORM_SCALE, 0)

    npc:SetMaxHealth(WORM_HP)
    npc:SetHealth(WORM_HP)
    npc:SetRenderMode(RENDERMODE_TRANSCOLOR)
    npc:SetColor(FRAG_COLORS.worm)

    -- Поля состояния
    npc.D6_Variant    = "worm"
    npc.D6_NextBite   = CurTime()
    npc.D6_NextJump   = CurTime() + 1
    npc.D6_Exploded   = false        -- защита от двойного взрыва

    -- Враждебность к игрокам
    npc:AddEntityRelationship(ply, D_HT, 99)

    return npc
end

concommand.Add("6dof_spawn_worm", function(ply)
    if not IsValid(ply) then return end
    ply.D6SpawnCD = ply.D6SpawnCD or 0
    if CurTime() < ply.D6SpawnCD then return end
    ply.D6SpawnCD = CurTime() + 2.5  -- [FIX-17]
    -- original check:
    if not IsValid(ply) then return end
    SpawnWorm(ply)
    ply:PrintMessage(HUD_PRINTTALK, "[ЧЕРВЬ] Альянсский червь появился!")
end)

-- ── Think: прыжки, укусы, смерть-взрыв ──────────────────
hook.Add("Think", "D6Frags_WormAI", function()
    for _, npc in ipairs(ents.FindByClass("npc_headcrab")) do
        if not IsValid(npc) or npc.D6_Variant ~= "worm" then continue end

        local myPos = npc:GetPos()
        local enemy = FindNearest6DOFPlayer(myPos, 2500)

        -- Смерть → взрыв (один раз)
        if npc:Health() <= 0 and not npc.D6_Exploded then
            npc.D6_Exploded = true
            npc:EmitSound("npc/fast_zombie/fz_attack_fast" ..
                math.random(1, 3) .. ".wav", 90, 80)
            D6_Explode(npc, myPos, WORM_EXPLODE_RAD, WORM_EXPLODE_DMG)
            timer.Simple(0.05, function()
                if IsValid(npc) then npc:Remove() end
            end)
            continue
        end

        if not IsValid(enemy) then continue end

        local targetPos = enemy:GetPos()
        local dist      = myPos:Distance(targetPos)

        -- Ориентация к врагу
        local faceAng = (targetPos - myPos):Angle()
        faceAng.p = 0  -- Наземный — не задираем нос вверх
        npc:SetAngles(LerpAngle(FrameTime() * 4, npc:GetAngles(), faceAng))

        -- Прыжок к дрону (если дрон в воздухе — прыгаем вверх-вперёд)
        if CurTime() > (npc.D6_NextJump or 0) and dist < 800 then
            npc.D6_NextJump = CurTime() + WORM_JUMP_CD

            local jumpDir = (targetPos - myPos):GetNormalized()
            -- Добавляем вертикальную составляющую, чтобы достичь летящего дрона
            jumpDir.z = jumpDir.z + 0.6
            jumpDir:Normalize()

            local phys = npc:GetPhysicsObject()
            if IsValid(phys) then
                phys:SetVelocity(jumpDir * WORM_JUMP_SPEED)
            end

            npc:EmitSound("npc/fast_zombie/fz_attack_fast" ..
                math.random(1, 3) .. ".wav", 80, 120)
        end

        -- Ближний укус
        if CurTime() > (npc.D6_NextBite or 0) and dist < WORM_BITE_RANGE then
            npc.D6_NextBite = CurTime() + WORM_BITE_CD

            local dmg = DamageInfo()
            dmg:SetAttacker(npc)
            dmg:SetInflictor(npc)
            dmg:SetDamage(WORM_BITE_DMG)
            dmg:SetDamageType(DMG_SLASH)
            enemy:TakeDamageInfo(dmg)

            npc:EmitSound("npc/fast_zombie/fz_attack_fast" ..
                math.random(1, 3) .. ".wav", 85, 100)
        end
    end
end)

-- ── Взрыв при смерти через EntityRemoved (страховка) ─────
hook.Add("EntityRemoved", "D6Frags_WormDeath", function(ent)
    if not IsValid(ent) then return end
    if ent.D6_Variant ~= "worm" then return end
    if ent.D6_Exploded then return end  -- Уже взорвался через Think

    ent.D6_Exploded = true
    D6_Explode(ent, ent:GetPos(), WORM_EXPLODE_RAD, WORM_EXPLODE_DMG * 0.6)
end)

-- =========================================================
-- ╔═══════════════════════════════════════════════════════╗
-- ║  2. УИТЛИ-КАМИКАДЗЕ  (wheatley)                      ║
-- ║  Базовый класс: npc_cscanner                          ║
-- ║  Поведение: хаотичный полёт, тревожные звуки,         ║
-- ║  при сближении с дроном — взрыв.                      ║
-- ╚═══════════════════════════════════════════════════════╝
-- =========================================================


-- ── Think: хаотичный полёт + детонация ───────────────────
hook.Add("Think", "D6Frags_WheatleyAI", function()
    for _, npc in ipairs(ents.FindByClass("npc_cscanner")) do
        if not IsValid(npc) or npc.D6_Variant ~= "wheatley" then continue end

        local myPos = npc:GetPos()
        local phys  = npc:GetPhysicsObject()
        if not IsValid(phys) then continue end

        local enemy = FindNearest6DOFPlayer(myPos, 2500)
        local dist  = IsValid(enemy) and myPos:Distance(enemy:GetPos()) or 9999

        -- ── Детонация при сближении ────────────────────────
        if dist < WHEATLEY_TRIG and not npc.D6_Detonated then
            npc.D6_Detonated = true

            npc:EmitSound("npc/scanner/scanner_talk" ..
                math.random(1, 3) .. ".wav", 95, 70)

            timer.Simple(0.15, function()
                if not IsValid(npc) then return end
                local boomPos = npc:GetPos()
                D6_Explode(npc, boomPos, WHEATLEY_BOOM_RAD, WHEATLEY_BOOM_DMG)
                npc:EmitSound("ambient/explosions/explode_" ..
                    math.random(1, 9) .. ".wav", 90, 100)
                npc:Remove()
            end)
            continue
        end

        -- ── Хаотичные разговоры (тревога) ─────────────────
        if CurTime() > (npc.D6_NextTalk or 0) then
            npc.D6_NextTalk = CurTime() + math.Rand(1.5, 4.0)
            npc:EmitSound("npc/scanner/scanner_talk" ..
                math.random(1, 3) .. ".wav", 70, math.random(90, 130))
        end

        -- ── Хаотичный дрейф (меняем вектор раз в DRIFT_CD) ─
        if CurTime() > (npc.D6_NextDrift or 0) then
            npc.D6_NextDrift = CurTime() + WHEATLEY_DRIFT_CD

            -- Если есть враг — дрейфуем в его сторону, но с сильным разбросом
            if IsValid(enemy) then
                local toEnemy = (enemy:GetPos() - myPos):GetNormalized()
                local jitter  = VectorRand() * 1.2  -- больше хаоса
                npc.D6_DriftDir = (toEnemy + jitter):GetNormalized()
            else
                npc.D6_DriftDir = VectorRand():GetNormalized()
            end
        end

        -- Плавно ускоряемся в текущий хаотичный вектор
        local curVel   = phys:GetVelocity()
        local wantVel  = npc.D6_DriftDir * WHEATLEY_SPEED
        phys:SetVelocity(LerpVector(FrameTime() * 3, curVel, wantVel))

        -- Вращение — нервное, быстрое
        local targetAng = npc.D6_DriftDir:Angle()
        targetAng.r = math.sin(CurTime() * 8) * 40  -- дрожание крена
        npc:SetAngles(LerpAngle(FrameTime() * 10, npc:GetAngles(), targetAng))

        -- Гасим угловые скорости от физики (нет дикого кувыркания)
        phys:AddAngleVelocity(-phys:GetAngleVelocity() * 0.3)
    end
end)

-- =========================================================
-- ╔═══════════════════════════════════════════════════════╗
-- ║  3. ВЕРТУШКА-ПУЛЕМЁТ  (chopper)                      ║
-- ║  Базовый класс: npc_cscanner (крупный масштаб)        ║
-- ║  Поведение: зависает на CHOPPER_HOVER_DIST, медленно  ║
-- ║  разворачивается к игроку, стреляет очередями.        ║
-- ║  30% резист урону (моделируется через EntityTakeDmg). ║
-- ╚═══════════════════════════════════════════════════════╝
-- =========================================================


-- ── Think: зависание, медленный поворот, очереди ─────────
hook.Add("Think", "D6Frags_ChopperAI", function()
    for _, npc in ipairs(ents.FindByClass("npc_cscanner")) do
        if not IsValid(npc) or npc.D6_Variant ~= "chopper" then continue end

        local myPos = npc:GetPos()
        local phys  = npc:GetPhysicsObject()
        if not IsValid(phys) then continue end

        local enemy = FindNearest6DOFPlayer(myPos, 3000)
        if not IsValid(enemy) then
            -- Нет врага — зависаем на месте
            phys:SetVelocity(phys:GetVelocity() * 0.85)
            continue
        end

        local enemyPos = enemy:GetPos()
        local dist     = myPos:Distance(enemyPos)
        local dirToEnemy = (enemyPos - myPos):GetNormalized()

        -- ── Удержание дистанции (зависание) ───────────────
        local hoverVel = Vector(0, 0, 0)
        if dist < CHOPPER_HOVER_DIST then
            -- Слишком близко — отступаем назад
            hoverVel = -dirToEnemy * 200
        elseif dist > CHOPPER_HOVER_DIST + 200 then
            -- Слишком далеко — сближаемся медленно
            hoverVel = dirToEnemy * 180
        end
        -- Выравниваем по высоте с дроном игрока
        hoverVel.z = hoverVel.z + (enemyPos.z - myPos.z) * 0.5

        local curVel = phys:GetVelocity()
        phys:SetVelocity(LerpVector(FrameTime() * 2.5, curVel, hoverVel))

        -- ── Медленный поворот к врагу (CHOPPER_TURN_RATE < 7) ─
        local wantAng = dirToEnemy:Angle()
        -- Небольшой крен в сторону виража
        local sideSpeed = npc:GetRight():Dot(phys:GetVelocity())
        wantAng.r = math.Clamp(sideSpeed * -0.05, -25, 25)

        npc:SetAngles(LerpAngle(FrameTime() * CHOPPER_TURN_RATE,
                                npc:GetAngles(), wantAng))

        -- ── Звук ротора (каждые 0.4 сек) ─────────────────
        if CurTime() > (npc.D6_RotorTimer or 0) then
            npc.D6_RotorTimer = CurTime() + 0.4
            npc:EmitSound("npc/attack_helicopter/aheli_strafe" ..
                math.random(1, 2) .. ".wav", 80, 95)
        end

        -- ── Стрельба очередями ─────────────────────────────
        if npc.D6_InReload then continue end

        if CurTime() > (npc.D6_NextShot or 0) then
            -- Трассировка: смотрим, виден ли игрок
            local tr = util.TraceLine({
                start  = myPos,
                endpos = enemyPos + Vector(0, 0, 30),
                filter = npc
            })

            if tr.Entity == enemy or dist < 800 then
                -- Один выстрел из очереди
                npc:FireBullets({
                    Num        = 1,
                    Src        = myPos + dirToEnemy * 30,
                    Dir        = (dirToEnemy + VectorRand() * 0.04):GetNormalized(),
                    Spread     = Vector(0.03, 0.03, 0),
                    Tracer     = 1,
                    TracerName = "ToolTracer",
                    Force      = 6,
                    Damage     = CHOPPER_BULLET_DMG,
                    Attacker   = npc
                })
                npc:EmitSound("weapons/rpg/rocketfire1.wav", 75, 140)

                npc.D6_BurstLeft = npc.D6_BurstLeft - 1
                npc.D6_NextShot  = CurTime() + CHOPPER_BURST_CD

                -- Конец очереди → перезарядка
                if npc.D6_BurstLeft <= 0 then
                    npc.D6_InReload  = true
                    npc.D6_BurstLeft = CHOPPER_BURST_RPM

                    timer.Simple(CHOPPER_RELOAD_CD, function()
                        if IsValid(npc) then
                            npc.D6_InReload = false
                            npc.D6_NextShot = CurTime() + 0.1
                        end
                    end)
                end
            end
        end

        -- Гасим физическое вращение
        phys:AddAngleVelocity(-phys:GetAngleVelocity() * 0.25)
    end
end)

-- ── 30% резист урону для вертушки ─────────────────────────
hook.Add("EntityTakeDamage", "D6Frags_ChopperArmor", function(target, dmginfo)
    if not IsValid(target) then return end
    if target.D6_Variant ~= "chopper" then return end

    -- Уменьшаем входящий урон на (1 - CHOPPER_ARMOR) = 30%
    -- Не трогаем DMG_BLAST (таран и ракеты бьют в полную силу)
    local dtype = dmginfo:GetDamageType()
    if bit.band(dtype, DMG_BLAST) == 0 then
        dmginfo:SetDamage(dmginfo:GetDamage() * CHOPPER_ARMOR)
    end
end)

-- =========================================================
-- ╔═══════════════════════════════════════════════════════╗
-- ║  4. МАНХОК-КАМИКАДЗЕ  (manhack_fast)                  ║
-- ║  Базовый класс: npc_manhack                           ║
-- ║  Поведение: очень быстрый, летит прямо на дрона,      ║
-- ║  при сближении взрывается. Уклоняется от ракет        ║
-- ║  резкими рывками (отслеживает rpg_missile рядом).     ║
-- ╚═══════════════════════════════════════════════════════╝
-- =========================================================

function SpawnManhackKamikaze(ply, customPos)
    local pos
    if customPos then
        pos = customPos
    else
        local tr = ply:GetEyeTrace()
        pos = tr.HitPos + tr.HitNormal * 30
    end

    -- npc_manhack — стандартный класс GMod, обычно доступен
    local npc = ents.Create("npc_manhack")
    if not IsValid(npc) then
        -- Fallback: cscanner если manhack недоступен
        npc = ents.Create("npc_cscanner")
        if not IsValid(npc) then return end
    end

    npc:SetPos(pos)
    npc:Spawn()
    npc:Activate()

    -- npc_manhack уже имеет нужный масштаб (1.0), не меняем
    npc:SetMaxHealth(MANHACK_HP)
    npc:SetHealth(MANHACK_HP)
    npc:SetRenderMode(RENDERMODE_TRANSCOLOR)
    npc:SetColor(FRAG_COLORS.manhack_fast)

    npc.D6_Variant    = "manhack_fast"
    npc.D6_NextDodge  = CurTime()
    npc.D6_Detonated  = false

    npc:AddEntityRelationship(ply, D_HT, 99)
    return npc
end

concommand.Add("6dof_spawn_manhack", function(ply)
    if not IsValid(ply) then return end
    ply.D6SpawnCD = ply.D6SpawnCD or 0
    if CurTime() < ply.D6SpawnCD then return end
    ply.D6SpawnCD = CurTime() + 2.5  -- [FIX-17]
    -- original check:
    if not IsValid(ply) then return end
    SpawnManhackKamikaze(ply)
    ply:PrintMessage(HUD_PRINTTALK, "[МАНХОК] Быстрый камикадзе выпущен!")
end)

-- ── Think: рывок на игрока, уклонение от ракет, взрыв ────
hook.Add("Think", "D6Frags_ManhackAI", function()
    -- Работаем с обоими классами (manhack или cscanner-fallback)
    local candidates = {}
    for _, e in ipairs(ents.FindByClass("npc_manhack"))   do table.insert(candidates, e) end
    for _, e in ipairs(ents.FindByClass("npc_cscanner"))  do table.insert(candidates, e) end

    for _, npc in ipairs(candidates) do
        if not IsValid(npc) or npc.D6_Variant ~= "manhack_fast" then continue end

        local myPos = npc:GetPos()
        local phys  = npc:GetPhysicsObject()
        if not IsValid(phys) then continue end

        local enemy = FindNearest6DOFPlayer(myPos, 3000)
        if not IsValid(enemy) then continue end

        local enemyPos = enemy:GetPos()
        local dist     = myPos:Distance(enemyPos)

        -- ── [D6 AI] Прокси-бип: чем ближе цель, тем чаще ───
        if dist < 800 and CurTime() > (npc.D6_NextBeep or 0) then
            -- Интервал: 0.1с в упор, до 0.6с на максимальной дистанции
            local interval = math.Clamp(dist / 1500, 0.1, 0.6)
            npc.D6_NextBeep = CurTime() + interval
            npc:EmitSound("buttons/blip1.wav", 65,
                math.floor(120 + (1 - dist / 800) * 50))
        end

        -- ── Детонация при сближении ────────────────────────
        if dist < MANHACK_BOOM_DIST and not npc.D6_Detonated then
            npc.D6_Detonated = true
            local boomPos = myPos

            npc:EmitSound("npc/manhack/idle" ..
                math.random(1, 3) .. ".wav", 90, 60)

            timer.Simple(0.05, function()
                D6_Explode(npc, boomPos, MANHACK_BOOM_RAD, MANHACK_BOOM_DMG)
                if IsValid(npc) then npc:Remove() end
            end)
            continue
        end

        -- ── Обнаружение ракет рядом → уклонение ──────────
        if CurTime() > (npc.D6_NextDodge or 0) then
            local nearMissile = false
            for _, m in ipairs(ents.FindInSphere(myPos, 200)) do
                if IsValid(m) and m:GetClass() == "rpg_missile" then
                    nearMissile = true
                    break
                end
            end

            if nearMissile then
                npc.D6_NextDodge = CurTime() + MANHACK_DODGE_CD
                -- Резкий рывок в случайную сторону, перпендикулярную курсу
                local dodgeDir = VectorRand():GetNormalized()
                dodgeDir.z     = math.Rand(0.3, 0.7)  -- чуть вверх
                phys:SetVelocity(dodgeDir * MANHACK_SPEED * 1.3)
                continue  -- пропускаем обычное движение в этот кадр
            end
        end

        -- ── Прямолинейный рывок к дрону ──────────────────
        local dir    = (enemyPos - myPos):GetNormalized()
        local curVel = phys:GetVelocity()
        phys:SetVelocity(LerpVector(FrameTime() * 8, curVel, dir * MANHACK_SPEED))

        -- Нос по направлению движения + вращение лезвий (крен)
        local moveAng = dir:Angle()
        moveAng.r = CurTime() * 720  -- вращение "лезвий"
        npc:SetAngles(LerpAngle(FrameTime() * 12, npc:GetAngles(), moveAng))

        phys:AddAngleVelocity(-phys:GetAngleVelocity() * 0.1)
    end
end)

-- =========================================================
-- ╔═══════════════════════════════════════════════════════╗
-- ║  5. ТЯЖЁЛЫЙ СКАНЕР-СИКЕР  (seeker)                   ║
-- ║  Базовый класс: npc_cscanner (крупный масштаб)        ║
-- ║  Поведение: держит дистанцию 800-1200, выпускает      ║
-- ║  залп из 4 самонаводящихся ракет (LerpAngle).         ║
-- ║  Медленный дрейф для позиционирования.                ║
-- ╚═══════════════════════════════════════════════════════╝
-- =========================================================

-- Глобальная таблица активных самонаводящихся ракет
-- D6_SeekerMissiles[ent] = { owner=npc, target=ply, expireTime=t }
D6_SeekerMissiles = D6_SeekerMissiles or {}

function SpawnHeavySeeker(ply, customPos)
    local pos
    if customPos then
        pos = customPos
    else
        local tr = ply:GetEyeTrace()
        pos = tr.HitPos + tr.HitNormal * 70
    end

    local npc = ents.Create("npc_cscanner")
    if not IsValid(npc) then return end

    npc:SetPos(pos)
    npc:Spawn()
    npc:Activate()

    npc:SetModelScale(SEEKER_SCALE, 0)
    npc:SetMaxHealth(SEEKER_HP)
    npc:SetHealth(SEEKER_HP)
    npc:SetRenderMode(RENDERMODE_TRANSCOLOR)
    npc:SetColor(FRAG_COLORS.seeker)

    npc.D6_Variant     = "seeker"
    npc.D6_NextMissile = CurTime() + 2.0  -- Небольшая задержка первого залпа
    npc.D6_DriftTimer  = CurTime()

    npc:AddEntityRelationship(ply, D_HT, 99)
    return npc
end

concommand.Add("6dof_spawn_heavy_seeker", function(ply)
    if not IsValid(ply) then return end
    ply.D6SpawnCD = ply.D6SpawnCD or 0
    if CurTime() < ply.D6SpawnCD then return end
    ply.D6SpawnCD = CurTime() + 2.5  -- [FIX-17]
    -- original check:
    if not IsValid(ply) then return end
    SpawnHeavySeeker(ply)
    ply:PrintMessage(HUD_PRINTTALK,
        "[СИКЕР] Тяжёлый ракетный сканер развёрнут!")
end)

-- ── Think: позиционирование и залп ────────────────────────
hook.Add("Think", "D6Frags_SeekerAI", function()
    for _, npc in ipairs(ents.FindByClass("npc_cscanner")) do
        if not IsValid(npc) or npc.D6_Variant ~= "seeker" then continue end

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

        -- ── Позиционирование: держим дистанцию SEEKER_DIST ─
        local driftVel = Vector(0, 0, 0)
        if dist < SEEKER_DIST_MIN then
            -- Слишком близко — отступаем
            driftVel = -dirToEnemy * SEEKER_DRIFT_SPD
        elseif dist > SEEKER_DIST_MAX then
            -- Слишком далеко — подходим
            driftVel = dirToEnemy * SEEKER_DRIFT_SPD
        else
            -- Боковой дрейф (не стоим на месте)
            if CurTime() > (npc.D6_DriftTimer or 0) then
                npc.D6_DriftTimer = CurTime() + 2.0
                npc.D6_SideDrift  = (npc:GetRight() + VectorRand() * 0.3)
                                        :GetNormalized()
            end
            driftVel = (npc.D6_SideDrift or npc:GetRight()) * (SEEKER_DRIFT_SPD * 0.5)
        end
        -- Выравниваем высоту с дроном
        driftVel.z = driftVel.z + (enemyPos.z - myPos.z) * 0.4

        local curVel = phys:GetVelocity()
        phys:SetVelocity(LerpVector(FrameTime() * 2, curVel, driftVel))

        -- Медленный поворот к врагу
        local wantAng = dirToEnemy:Angle()
        local sideSpd = npc:GetRight():Dot(phys:GetVelocity())
        wantAng.r = math.Clamp(sideSpd * -0.08, -40, 40)
        npc:SetAngles(LerpAngle(FrameTime() * 3, npc:GetAngles(), wantAng))

        -- ── Залп из 4 самонаводящихся ракет ───────────────
        if CurTime() > (npc.D6_NextMissile or 0) then
            npc.D6_NextMissile = CurTime() + SEEKER_RELOAD_CD

            -- Проверяем видимость перед залпом
            local tr = util.TraceLine({
                start  = myPos,
                endpos = enemyPos,
                filter = npc
            })
            -- Стреляем при прямой видимости ИЛИ дистанции < 800
            if tr.Entity == enemy or dist < 800 then
                npc:EmitSound("weapons/rpg/rocketfire1.wav", 85, 90)

                -- Небольшая задержка между ракетами залпа (0.12 сек)
                for i = 1, SEEKER_SALVO do
                    local fireDelay = (i - 1) * 0.12

                    timer.Simple(fireDelay, function()
                        if not IsValid(npc) then return end

                        -- Пересчитываем позицию в момент реального пуска
                        local firePos = npc:GetPos()
                        local fireDir = dirToEnemy

                        -- Лёгкое веерное смещение между ракетами
                        local spread = Angle(
                            math.Rand(-4, 4),
                            math.Rand(-4, 4),
                            0
                        )
                        fireDir = (fireDir:Angle() + spread):Forward()

                        local m = ents.Create("rpg_missile")
                        if not IsValid(m) then return end

                        m:SetPos(firePos + fireDir * 60)
                        m:SetAngles(fireDir:Angle())
                        m:SetOwner(npc)
                        m:Spawn()

                        local mph = m:GetPhysicsObject()
                        if IsValid(mph) then
                            mph:SetVelocity(fireDir * SEEKER_MISSILE_SPD)
                        end

                        -- Регистрируем в таблице самонаведения
                        D6_SeekerMissiles[m] = {
                            owner      = npc,
                            target     = enemy,  -- начальная цель
                            expireTime = CurTime() + SEEKER_MISSILE_LT
                        }

                        -- Страховочное удаление
                        timer.Simple(SEEKER_MISSILE_LT, function()
                            if IsValid(m) then
                                D6_SeekerMissiles[m] = nil
                                m:Remove()
                            end
                        end)

                        if i == 1 then  -- только первая ракета издаёт звук пуска
                            m:EmitSound("weapons/rpg/rocketfire1.wav", 80, 110)
                        end
                    end)
                end
            end
        end

        phys:AddAngleVelocity(-phys:GetAngleVelocity() * 0.2)
    end
end)

-- ── Think: самонаведение ракет (LerpAngle к цели) ─────────
hook.Add("Think", "D6Frags_SeekerMissilesThink", function()
    -- [D6 AI] Троттлинг 20 Hz вместо 66 Hz — облегчает CPU при 10+ ракетах.
    -- Самонаведение на 20 Hz визуально неотличимо от 66 Hz, но 3x дешевле.
    if (D6_SeekerTick or 0) > CurTime() then return end
    D6_SeekerTick = CurTime() + 0.05

    local toRemove = {}

    for missile, data in pairs(D6_SeekerMissiles) do
        -- Проверяем валидность и время жизни
        if not IsValid(missile) then
            toRemove[missile] = true
            continue
        end
        if CurTime() > data.expireTime then
            toRemove[missile] = true
            if IsValid(missile) then missile:Remove() end
            continue
        end

        local missilePos = missile:GetPos()
        local mph        = missile:GetPhysicsObject()
        if not IsValid(mph) then continue end

        -- Обновляем цель: ищем ближайшего игрока
        -- (цель может сменится, если оригинальная потеряна)
        local tgt = data.target
        if not IsValid(tgt) or not tgt:Alive() then
            tgt = FindNearest6DOFPlayer(missilePos, 3000)
            data.target = tgt
        end

        if not IsValid(tgt) then continue end  -- Нет цели — летим прямо

        local targetPos = tgt:GetPos() + Vector(0, 0, 25)  -- чуть выше центра
        local dist      = missilePos:Distance(targetPos)

        -- Предсказание позиции: упреждение на время полёта
        local tgtVel       = tgt.D6_Vel or tgt:GetVelocity()
        local timeToTarget = dist / SEEKER_MISSILE_SPD
        local predictedPos = targetPos + tgtVel * timeToTarget * 0.5

        local wantDir  = (predictedPos - missilePos):GetNormalized()
        local wantAng  = wantDir:Angle()

        -- LerpAngle к целевому углу (SEEKER_TURN_RATE — плавный поворот)
        local curAng   = missile:GetAngles()
        local newAng   = LerpAngle(SEEKER_TURN_RATE, curAng, wantAng)
        local newDir   = newAng:Forward()

        missile:SetAngles(newAng)
        mph:SetVelocity(newDir * SEEKER_MISSILE_SPD)
    end

    -- Удаляем невалидные записи
    for missile, _ in pairs(toRemove) do
        D6_SeekerMissiles[missile] = nil
    end
end)

-- ── Очистка таблицы самонаведения при удалении ракеты ─────
hook.Add("EntityRemoved", "D6Frags_MissileCleanup", function(ent)
    if D6_SeekerMissiles then
        D6_SeekerMissiles[ent] = nil
    end
end)

-- =========================================================
-- КОМАНДА: ПОЛНЫЙ ЗООПАРК (по одному каждого типа)
-- =========================================================
concommand.Add("6dof_spawn_bestiary", function(ply)
    if not IsValid(ply) then return end
    ply.D6SpawnCD = ply.D6SpawnCD or 0
    if CurTime() < ply.D6SpawnCD then return end
    ply.D6SpawnCD = CurTime() + 2.5  -- [FIX-17]
    -- original check:
    if not IsValid(ply) then return end

    local tr      = ply:GetEyeTrace()
    local basePos = tr.HitPos + tr.HitNormal * 80

    -- Расставляем вокруг в круг радиусом 200 ед.
    local types = {
        { fn = SpawnWorm,           name = "червь"    },
        { fn = SpawnWheatley,       name = "уитли"    },
        { fn = SpawnChopper,        name = "вертушка" },
        { fn = SpawnManhackKamikaze,name = "манхок"   },
        { fn = SpawnHeavySeeker,    name = "сикер"    },
    }

    for i, t in ipairs(types) do
        local angle  = ((i - 1) / #types) * math.pi * 2
        local offset = Vector(
            math.sin(angle) * 200,
            math.cos(angle) * 200,
            50
        )
        t.fn(ply, basePos + offset)
    end

    ply:PrintMessage(HUD_PRINTTALK,
        "[БЕСТИАРИЙ] Все " .. #types .. " типов врагов заспавнены!")
end)

-- =========================================================
-- ПАТЧ ДЛЯ d6_ai.lua: расширение BOIDS_EXCLUDED
-- =========================================================
-- d6_ai.lua использует локальную таблицу BOIDS_EXCLUDED.
-- Ниже мы добавляем хук, который при каждом тике Boids
-- дополнительно проверяет D6_FragsBoidsExclude.
-- Это позволяет не модифицировать d6_ai.lua напрямую.
--
-- Хук "D6_Swarm_AI_Core_FragsPatch" запускается ДО основного
-- хука Boids и помечает новые варианты как "не для роя",
-- обнуляя их физическую скорость если Boids её уже изменил.
-- На практике это не нужно: хук Boids проверяет D6_Variant,
-- и если вариант не в локальной BOIDS_EXCLUDED — обрабатывает.
-- Поэтому мы используем более чистый способ:
--
-- При спавне новых врагов мы НЕ устанавливаем D6_Variant,
-- совпадающий с теми, что Boids обрабатывает автоматически
-- (mg, rpg, laser, grav, rpg).
-- Варианты: "worm", "chopper", "seeker", "wheatley", "manhack_fast"
-- не входят в перечень Boids (он итерирует только npc_cscanner
-- с D6_Variant, не входящим в BOIDS_EXCLUDED).
-- Worm — npc_headcrab (Boids его не видит вообще).
-- Manhack — npc_manhack или npc_cscanner с вариантом manhack_fast
--           который НЕ входит в стандартный список d6_ai.lua.
-- Таким образом, новые враги автоматически не участвуют в Boids.
-- D6_FragsBoidsExclude оставляем как документацию и для
-- будущих расширений d6_ai.lua.
-- =========================================================

-- =========================================================
-- ТЕСТ: СУММАРНАЯ ИНФОРМАЦИЯ О ДОСТУПНЫХ КОМАНДАХ
-- =========================================================
hook.Add("InitPostEntity", "D6Frags_PrintInfo", function()
    -- Выводим в консоль сервера список команд при запуске
    MsgC(Color(120, 200, 80),
        "\n[D6 Frags] Бестиарий загружен. Команды спавна:\n",
        color_white,
        "  6dof_spawn_worm          – Альянсский червь (HP:"  .. WORM_HP .. ")\n",
        "  6dof_spawn_wheatley      – Уитли-камикадзе  (HP:"  .. WHEATLEY_HP .. ")\n",
        "  6dof_spawn_chopper       – Вертушка-пулемёт (HP:"  .. CHOPPER_HP .. ", броня 30%)\n",
        "  6dof_spawn_manhack       – Манхок-камикадзе (HP:"  .. MANHACK_HP .. ")\n",
        "  6dof_spawn_heavy_seeker  – Ракетный сканер  (HP:"  .. SEEKER_HP  .. ")\n",
        "  6dof_spawn_bestiary      – Все типы сразу\n",
        Color(120, 200, 80),
        "[D6 Frags] Вариант BOIDS: новые враги не входят в рой автоматически.\n\n"
    )
end)
