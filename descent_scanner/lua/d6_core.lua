-- ═══════════════════════════════════════════════════════════
-- D6 ФИЗИКА ПО DESCENT
-- Idle-таймер: гравитация выключена пока игрок активен.
-- ═══════════════════════════════════════════════════════════
local CFG = {
    gravity      = 200,    -- максимальная гравитация (после idle)
    accel        = 3600,
    drag         = 1.8,    -- unified drag (A1)
    maxSpeed     = 2200,
    inertia      = 0.96,   -- palpable coasting (A1)
    brakeMult    = 0.025,  -- linear brake when assist ON + no input (P5: momentum)
    spoolUp      = 20,     -- thrust ramp-IN  (~50ms to 63% — near-instant, responsive)
    spoolDown    = 5,      -- thrust ramp-OUT (~200ms — slow wind-down = weight on reversals)
    strafeMult   = 0.8,    -- [Descent] боковая тяга 80% от forward
    vertMult     = 0.8,    -- [Descent] вертикальная тяга 80% от forward
    wallBounce   = 0.5,    -- [Descent] упругость стен 50%
    idleGravSec  = 45,     -- секунд бездействия до активации гравитации
    idleGravRamp = 5,      -- секунд плавного нарастания гравитации
}

-- ── Рывок (заменяет таран) ────────────────────────────────
-- Мгновенный импульс в направлении движения, без урона.
-- Кулдаун + короткая непробиваемость во время рывка.
local DASH_VEL  = 3200   -- скорость импульса
local DASH_DUR  = 0.18   -- секунды действия рывка
local DASH_CD   = 1.8    -- кулдаун (секунды)

local HOOK_DIST = 2500
local HOOK_PULL = 3800

local DRONE_SCALE = 2.5
local ANG_RATE    = 0.1

if SERVER then
    util.AddNetworkString("D6_HookSync")
    util.AddNetworkString("D6_DashSync")
    util.AddNetworkString("D6_AngSync")
    util.AddNetworkString("D6_Toggle")
    util.AddNetworkString("D6_AlwaysRunSync")
    util.AddNetworkString("D6_DashDir")
    util.AddNetworkString("D6_WeaponSwitch")
    util.AddNetworkString("D6_RocketSubNext")
    -- Боевые оружия: регистрируем заранее при загрузке карты
    util.AddNetworkString("D6_LaserBeam")   -- weapon_d6_laser: серверный луч
    util.AddNetworkString("D6_RailFire")    -- weapon_d6_gravy_railgun: трассер выстрела

    -- ── Центральный прекеш ресурсов снарядов (внедрено в автолоад) ──
    -- Внутренние ресурсы HL2 для проджектайлов всех боевых оружий D6.
    local D6_PRECACHE_MDL = {
        "models/weapons/w_missile.mdl",          -- РАКЕТЫ (rpg_missile)
        "models/weapons/w_irifle_ball.mdl",      -- ПЛАЗМА (энергошар AR2)
        "models/weapons/w_grenade.mdl",          -- ТЯЖЁЛЫЙ (граната)
        "models/weapons/w_crossbow_bolt.mdl",    -- резерв (арбалетный болт)
        "models/weapons/shell.mdl",              -- ПУЛЬСАР (гильза)
        "models/effects/combineball.mdl",        -- AI heavy (combine ball)
    }
    for _, m in ipairs(D6_PRECACHE_MDL) do util.PrecacheModel(m) end

    local D6_PRECACHE_SND = {
        "weapons/rpg/rocketfire1.wav", "weapons/rpg/rocket_explode.wav",
        "weapons/irifle/irifle_fire2.wav", "weapons/ar2/ar2_fire1.wav",
        "weapons/grenade/grenade_explode.wav", "weapons/crossbow/fire1.wav",
        "weapons/shotgun/shotgun_fire6.wav",
    }
    for _, s in ipairs(D6_PRECACHE_SND) do util.PrecacheSound(s) end
end

-- ═══════════════════════════════════════════════════════════
-- D6_TrackProjectile — надёжная детекция столкновений снаряда
-- ───────────────────────────────────────────────────────────
-- ВАЖНО: хука "EntityCollision" в GMod НЕ существует — старый код
-- на нём не детектил НИ ОДНОГО попадания. Реальный физический
-- контакт ловится через Entity:AddCallback("PhysicsCollide").
-- Внутри колбэка НЕЛЬЗЯ удалять энтить или спавнить эффекты —
-- вся реакция переносится в timer.Simple(0, ...).
--
--   ent   — снаряд (prop_physics с валидным физтелом)
--   owner — стрелок (контакт с ним игнорируется)
--   onHit(ent, hitEnt, hitPos, hitNormal) — реакция на удар;
--           hitEnt == nil при истечении времени жизни
--   life  — секунд до самоликвидации (по умолчанию 5)
-- ═══════════════════════════════════════════════════════════
if SERVER then
    function D6_TrackProjectile(ent, owner, onHit, life)
        if not IsValid(ent) then return end
        ent.D6_IsProjectile = true
        local handled = false

        local function Fire(hitEnt, hitPos, hitNormal)
            if handled then return end
            handled = true
            local he, hp, hn = hitEnt, hitPos, hitNormal
            timer.Simple(0, function()
                if onHit and IsValid(ent) then onHit(ent, he, hp, hn) end
                if IsValid(ent) then ent:Remove() end
            end)
        end

        ent:AddCallback("PhysicsCollide", function(e, data)
            local hitEnt = data.HitEntity
            if IsValid(hitEnt) then
                if hitEnt == owner then return end
                if hitEnt.D6_IsProjectile then return end
            end
            Fire(hitEnt, data.HitPos, data.HitNormal)
        end)

        timer.Simple(life or 5, function()
            Fire(nil, IsValid(ent) and ent:GetPos() or vector_origin, vector_up)
        end)
    end
end

local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

-- Глобальное отключение вспышек сканеров
if SERVER then
    hook.Add("AcceptInput", "D6_NoScannerFlash", function(ent, inp)
        if ent:GetClass() == "npc_cscanner" then
            if inp == "Flash" or inp == "FlashBank"
            or inp == "DisableFlashlight" or inp == "EnableFlashlight" then
                return true
            end
        end
    end)

    hook.Add("Think", "D6_ScannerFlash_Think", function()
        for _, ent in ipairs(ents.FindByClass("npc_cscanner")) do
            if IsValid(ent) then
                ent:Fire("DisableFlashlight", "", 0)
            end
        end
    end)
end

local function D6_Activate(ply)
    ply.D6LastInput  = CurTime()  -- [D6 idle] таймер бездействия
    ply.D6On        = true
    ply.D6OldModel  = ply:GetModel()
    ply.D6Vel       = Vector(0, 0, 0)
    ply.D6ThrustCur = Vector(0, 0, 0)
    ply.D6HookOn    = false
    ply.D6DashOn    = false
    ply.D6DashDir   = nil
    ply.D6DashCD    = 0
    ply.D6Ang       = nil
    ply.D6AngSynced = nil
    ply.D6AlwaysRun = false
    ply:SetNWBool("D6On", true)
    ply:SetNWBool("D6AlwaysRun", false)
    ply:SetModel("models/combine_scanner.mdl")
    ply:SetModelScale(DRONE_SCALE, 0)
    ply:SetMoveType(MOVETYPE_WALK)
    ply:SetVelocity(-ply:GetVelocity())

    net.Start("D6_Toggle")
        net.WriteEntity(ply); net.WriteBool(true)
    net.Broadcast()

    ply:PrintMessage(HUD_PRINTTALK,
        "[6DOF] ON | WASD движение  Пробел/Ctrl высота  LShift/F крен  Z крюк  d6_dash — рывок  R выкл")
end

local function D6_Deactivate(ply)
    ply.D6On        = false
    ply.D6HookOn    = false
    ply.D6DashOn    = false
    ply.D6Vel       = Vector(0, 0, 0)
    ply.D6ThrustCur = Vector(0, 0, 0)
    ply.D6AlwaysRun = false
    ply:SetNWBool("D6On", false)
    ply:SetNWBool("D6AlwaysRun", false)
    if ply.D6OldModel then ply:SetModel(ply.D6OldModel) end
    ply:SetModelScale(1, 0)
    ply:SetMoveType(MOVETYPE_WALK)

    net.Start("D6_HookSync")
        net.WriteEntity(ply); net.WriteBool(false); net.WriteVector(Vector(0,0,0))
    net.Broadcast()
    net.Start("D6_Toggle")
        net.WriteEntity(ply); net.WriteBool(false)
    net.Broadcast()

    ply:PrintMessage(HUD_PRINTTALK, "[6DOF] OFF")
end

if SERVER then
    concommand.Add("6dof_toggle", function(ply)
        if not IsValid(ply) or not ply:Alive() then return end
        ply.D6On = ply.D6On or false
        if ply.D6On then D6_Deactivate(ply) else D6_Activate(ply) end
    end)

    concommand.Add("6dof_alwaysrun", function(ply)
        if not IsValid(ply) or not ply.D6On then return end
        ply.D6AlwaysRun = not ply.D6AlwaysRun
        ply:SetNWBool("D6AlwaysRun", ply.D6AlwaysRun)
        net.Start("D6_AlwaysRunSync")
            net.WriteEntity(ply); net.WriteBool(ply.D6AlwaysRun)
        net.Broadcast()
        ply:PrintMessage(HUD_PRINTTALK,
            "[6DOF] Always-Run: " .. (ply.D6AlwaysRun and "ON ▶▶" or "OFF"))
    end)

    concommand.Add("6dof_flightassist", function(ply)
        if not IsValid(ply) or not ply.D6On then return end
        ply.D6_FlightAssist = not (ply.D6_FlightAssist == true)
        ply:SetNWBool("D6_FlightAssist", ply.D6_FlightAssist)
        ply:PrintMessage(HUD_PRINTTALK, "[6DOF] Flight Assist: "
            .. (ply.D6_FlightAssist and "ON" or "OFF (Ньютон)"))
    end)

    -- ── Рывок: concommand — биндится игроком на любую клавишу ──
    -- Направление: по текущему вводу WASD, если нет ввода — вперёд.
    concommand.Add("6dof_dash", function(ply)
        if not IsValid(ply) or not ply.D6On then return end
        local ct = CurTime()
        ply.D6DashCD = ply.D6DashCD or 0
        if ct < ply.D6DashCD then return end
        if ply.D6DashOn then return end
        if not D6_Energy.TryConsume(ply, "engines", 15) then
            ply:EmitSound("buttons/button10.wav", 65, 100); return
        end

        local sa  = ShootAng(ply)
        local cmd = ply:GetCurrentCommand()

        local fwd  = (cmd and cmd:KeyDown(IN_FORWARD)   and 1 or 0) - (cmd and cmd:KeyDown(IN_BACK)      and 1 or 0)
        local side = (cmd and cmd:KeyDown(IN_MOVERIGHT)  and 1 or 0) - (cmd and cmd:KeyDown(IN_MOVELEFT)  and 1 or 0)
        local up   = (cmd and cmd:KeyDown(IN_JUMP)       and 1 or 0) - (cmd and cmd:KeyDown(IN_DUCK)      and 1 or 0)

        local dir = Vector(fwd, side, up)
        if dir:LengthSqr() < 0.01 then
            dir = sa:Forward()
        else
            dir = (dir.x * sa:Forward() + dir.y * sa:Right() + dir.z * sa:Up()):GetNormalized()
        end

        ply.D6DashDir = dir
        ply.D6DashOn  = true
        ply.D6DashEnd = ct + DASH_DUR
        ply.D6DashCD  = ct + DASH_CD
        ply:SetNWFloat("D6DashCD", ply.D6DashCD)

        net.Start("D6_DashSync")
            net.WriteEntity(ply); net.WriteBool(true); net.WriteVector(dir)
        net.Broadcast()

        ply:EmitSound("ambient/machines/thumper_top.wav", 75, 160)
    end)

    -- X-клавиша: клиент отправляет направление рывка через net (надёжнее concommand)
    net.Receive("D6_DashDir", function(_, ply)
        if not IsValid(ply) or not ply.D6On then return end
        local ct = CurTime()
        ply.D6DashCD = ply.D6DashCD or 0
        if ct < ply.D6DashCD or ply.D6DashOn then return end
        if not D6_Energy.TryConsume(ply, "engines", 15) then
            ply:EmitSound("buttons/button10.wav", 65, 100); return
        end

        -- Направление закодировано как три int (-1/0/1) для fwd/right/up
        local fx = net.ReadInt(4)
        local fy = net.ReadInt(4)
        local fz = net.ReadInt(4)

        local sa  = ShootAng(ply)
        local dir
        if fx == 0 and fy == 0 and fz == 0 then
            dir = sa:Forward()
        else
            dir = (fx * sa:Forward() + fy * sa:Right() + fz * sa:Up()):GetNormalized()
        end

        ply.D6DashDir = dir
        ply.D6DashOn  = true
        ply.D6DashEnd = ct + DASH_DUR
        ply.D6DashCD  = ct + DASH_CD
        ply:SetNWFloat("D6DashCD", ply.D6DashCD)

        net.Start("D6_DashSync")
            net.WriteEntity(ply); net.WriteBool(true); net.WriteVector(dir)
        net.Broadcast()

        ply:EmitSound("ambient/machines/thumper_top.wav", 75, 160)
    end)

    -- Смена оружия колесом мыши (клиент отправляет classname)
    local _D6_WHEEL_WEPS = {
        ["weapon_d6_pulse"]=true, ["weapon_d6_plasma"]=true,
        ["weapon_d6_heavy"]=true, ["weapon_d6_laser"]=true,
        ["weapon_d6_rockets"]=true, ["weapon_d6_gravy_railgun"]=true,
    }
    net.Receive("D6_WeaponSwitch", function(_, ply)
        if not IsValid(ply) then return end
        local class = net.ReadString()
        if not _D6_WHEEL_WEPS[class] then return end
        if IsValid(ply:GetWeapon(class)) then
            ply:SelectWeapon(class)
        end
    end)

    net.Receive("D6_RocketSubNext", function(_, ply)
        if not IsValid(ply) then return end
        local w = ply:GetActiveWeapon()
        if not IsValid(w) or w:GetClass() ~= "weapon_d6_rockets" then return end
        local sub = (ply:GetNWInt("D6_RktSub", 0) + 1) % 4
        ply:SetNWInt("D6_RktSub", sub)
        w:SetNWInt("D6_RktSub", sub)
        ply:EmitSound("buttons/blip1.wav", 65, 120)
    end)

    concommand.Add("d6_set", function(ply, _, args)
        if not IsValid(ply) then return end
        if not ply:IsAdmin() and not ply:IsListenServerHost() then
            ply:PrintMessage(HUD_PRINTTALK, "[D6] Нет прав (нужен admin)")
            return
        end
        local k, v = args[1], tonumber(args[2])
        if not k or not v then return end
        ply.D6_Params = ply.D6_Params or {}
        ply.D6_Params[k] = v
    end)

    concommand.Add("d6_kill_npcs", function(ply)
        if not IsValid(ply) then return end
        local n = 0
        for _, e in ipairs(ents.FindByClass("npc_*")) do
            if IsValid(e) then e:Remove(); n = n + 1 end
        end
        for _, e in ipairs(ents.FindByClass("prop_physics")) do
            if IsValid(e) and (e.D6_Variant or e.IsAirMine) then e:Remove(); n = n + 1 end
        end
        ply:PrintMessage(HUD_PRINTTALK, "[D6] Удалено: " .. n)
    end)

    concommand.Add("d6_reset_roll", function(ply)
        if not IsValid(ply) then return end
        if ply.D6AngSynced then ply.D6AngSynced.r = 0; ply:SetAngles(ply.D6AngSynced) end
    end)

    hook.Add("PlayerButtonDown", "D6_R_Toggle", function(ply, key)
        if key == KEY_R and IsValid(ply) and ply:Alive() then
            ply:ConCommand("6dof_toggle")
        end
    end)

    hook.Add("PlayerSpawn", "D6_Reset", function(ply)
        timer.Simple(0.3, function()
            if not IsValid(ply) then return end
            ply.D6On    = false; ply.D6Vel = Vector(0, 0, 0)
            ply.D6ThrustCur = Vector(0, 0, 0)
            ply.D6HookOn = false; ply.D6DashOn = false
            ply.D6AlwaysRun = false
            ply:SetNWBool("D6On", false)
            ply:SetNWBool("D6AlwaysRun", false)
            ply:SetModelScale(1, 0)
        end)
    end)

    hook.Add("PlayerDisconnected", "D6_Disconnect", function(ply)
        if IsValid(ply) then ply:SetModelScale(1, 0) end
    end)
end

-- ── Синхронизация угла ────────────────────────────────────
if SERVER then
    net.Receive("D6_AngSync", function(_, sender)
        if not IsValid(sender) or not sender.D6On then return end
        local ang = net.ReadAngle()
        sender.D6AngSynced = ang
        sender.D6AngApply  = sender.D6AngApply or 0
        if CurTime() >= sender.D6AngApply then
            sender.D6AngApply = CurTime() + ANG_RATE
            sender:SetAngles(ang)
        end
        for _, ply in ipairs(player.GetAll()) do
            if ply ~= sender and IsValid(ply) then
                net.Start("D6_AngSync")
                    net.WriteEntity(sender); net.WriteAngle(ang)
                net.Send(ply)
            end
        end
    end)
end

-- ── Крюк (сервер) ────────────────────────────────────────
if SERVER then
    hook.Add("Think", "D6_Hook_Think", function()
        for _, ply in ipairs(player.GetAll()) do
            if not IsValid(ply) or not ply.D6On then continue end
            local want = ply:KeyDown(IN_USE)

            if want and not ply.D6HookOn then
                local sa = ShootAng(ply)
                local tr = util.TraceLine({
                    start = ply:GetPos(), endpos = ply:GetPos() + sa:Forward() * HOOK_DIST, filter = ply
                })
                if tr.Hit and not tr.HitSky then
                    ply.D6HookOn   = true
                    ply.D6HookPos  = tr.HitPos
                    ply.D6HookEnt  = tr.Entity
                    ply.D6HookLight = IsValid(tr.Entity)
                        and tr.Entity:GetClass() == "prop_physics"
                        and IsValid(tr.Entity:GetPhysicsObject())
                        and tr.Entity:GetPhysicsObject():GetMass() < 120
                    net.Start("D6_HookSync")
                        net.WriteEntity(ply); net.WriteBool(true); net.WriteVector(tr.HitPos)
                    net.Broadcast()
                    ply:EmitSound("weapons/physcannon/physcannon_pickup.wav", 75, 130)
                end
            end

            if not want and ply.D6HookOn then
                ply.D6HookOn = false
                net.Start("D6_HookSync")
                    net.WriteEntity(ply); net.WriteBool(false); net.WriteVector(Vector(0,0,0))
                net.Broadcast()
            end

            if ply.D6HookOn and IsValid(ply.D6HookEnt) then
                local np = ply.D6HookEnt:GetPos()
                if ply:GetPos():Distance(np) > HOOK_DIST * 1.2 then
                    ply.D6HookOn = false
                    net.Start("D6_HookSync")
                        net.WriteEntity(ply); net.WriteBool(false); net.WriteVector(Vector(0,0,0))
                    net.Broadcast()
                else
                    ply.D6HookPos = np
                end
            end
        end
    end)

    hook.Add("SetupMove", "D6_Hook_Move", function(ply, mvd)
        if not ply.D6On or not ply.D6HookOn then return end
        local mp = ply:GetPos()
        local hp = ply.D6HookPos or mp
        local d  = mp:Distance(hp)
        if d < 55 then
            ply.D6HookOn = false
            net.Start("D6_HookSync")
                net.WriteEntity(ply); net.WriteBool(false); net.WriteVector(Vector(0,0,0))
            net.Broadcast()
            return
        end
        local dir = (hp - mp):GetNormalized()
        if not ply.D6HookLight then
            ply.D6Vel = (ply.D6Vel or Vector()) + dir * HOOK_PULL * FrameTime()
            mvd:SetVelocity(ply.D6Vel)
        elseif IsValid(ply.D6HookEnt) then
            local ph = ply.D6HookEnt:GetPhysicsObject()
            if IsValid(ph) then
                ph:SetVelocity((mp - ply.D6HookEnt:GetPos()):GetNormalized() * 1400)
            end
        end
    end)
end

-- ── Рывок: SetupMove ─────────────────────────────────────
if SERVER then
    hook.Add("SetupMove", "D6_Dash_Move", function(ply, mvd)
        if not ply.D6On or not ply.D6DashOn then return end
        local ct = CurTime()

        if ct > (ply.D6DashEnd or 0) then
            ply.D6DashOn = false
            net.Start("D6_DashSync")
                net.WriteEntity(ply); net.WriteBool(false); net.WriteVector(Vector(0,0,0))
            net.Broadcast()
            return
        end

        local dir = ply.D6DashDir or ShootAng(ply):Forward()
        ply.D6Vel = ply.D6Vel or Vector(0, 0, 0)
        local parallel = ply.D6Vel:Dot(dir)
        local lateral  = ply.D6Vel - dir * parallel
        ply.D6Vel = lateral + dir * DASH_VEL
        local newSpd = ply.D6Vel:Length()
        if newSpd > DASH_VEL * 1.3 then ply.D6Vel = ply.D6Vel * (DASH_VEL * 1.3 / newSpd) end
        mvd:SetVelocity(ply.D6Vel)
    end)
end


-- ═══════════════════════════════════════════════════════════
-- [D6] АВТО-АКТИВАЦИЯ 6DOF ПРИ СПАВНЕ
-- ═══════════════════════════════════════════════════════════
if SERVER then
    hook.Add("PlayerSpawn", "D6_AutoActivate", function(ply)
        timer.Simple(0.5, function()
            if IsValid(ply) and ply:Alive() and not ply.D6On then
                D6_Activate(ply)
            end
        end)
    end)
end

-- ── Физика полёта (Descent-style + idle gravity) ─────────
hook.Add("SetupMove", "D6_Flight_Move", function(ply, mvd, cmd)
    if not ply.D6On then return end
    local ft = FrameTime()
    if ft <= 0 or ft > 0.1 then return end
    ply.D6Vel       = ply.D6Vel or Vector(0, 0, 0)
    ply.D6ThrustCur = ply.D6ThrustCur or Vector(0, 0, 0)

    local params      = ply.D6_Params or {}
    local gravity     = params.gravity  or CFG.gravity
    local accel       = params.accel    or CFG.accel
    local drag        = params.drag     or CFG.drag
    local maxSpeed    = params.maxSpeed or CFG.maxSpeed
    local inertia     = CFG.inertia
    local strafeMult  = CFG.strafeMult
    local vertMult    = CFG.vertMult
    local wallBounce   = CFG.wallBounce
    local flightAssist = ply.D6_FlightAssist ~= false

    local fwd  = (cmd:KeyDown(IN_FORWARD)   and 1 or 0) - (cmd:KeyDown(IN_BACK)      and 1 or 0)
    local side = (cmd:KeyDown(IN_MOVERIGHT)  and 1 or 0) - (cmd:KeyDown(IN_MOVELEFT)  and 1 or 0)
    local up   = (cmd:KeyDown(IN_JUMP)       and 1 or 0) - (cmd:KeyDown(IN_DUCK)      and 1 or 0)

    local inp = Vector(fwd, side, up)
    if inp:LengthSqr() > 1 then inp:Normalize() end

    -- ─── [D6 idle] обновляем таймер бездействия ───
    local hasInput = (fwd ~= 0 or side ~= 0 or up ~= 0)
                  or cmd:KeyDown(IN_ATTACK) or cmd:KeyDown(IN_ATTACK2)
                  or ply.D6DashOn
    if hasInput then ply.D6LastInput = CurTime() end
    if SERVER then ply:SetNWFloat("D6LastInput", ply.D6LastInput or CurTime()) end

    if SERVER and ply.D6AlwaysRun and hasInput then
        if not D6_Energy.TryConsume(ply, "engines", 6 * ft) then
            ply.D6AlwaysRun = false
            ply:SetNWBool("D6AlwaysRun", false)
            net.Start("D6_AlwaysRunSync")
                net.WriteEntity(ply); net.WriteBool(false)
            net.Broadcast()
        end
    end

    local eng    = D6_Energy.GetAlloc(ply, "engines")
    local arMult = ply.D6AlwaysRun and Lerp(eng, 1.3, 1.9) or 1.0
    local ang    = cmd:GetViewAngles()

    -- ─── [Descent] асимметричная тяга: strafe 80%, vert 80% ───
    local thrustTarget = (
        inp.x * ang:Forward()
      + inp.y * ang:Right() * strafeMult
      + inp.z * ang:Up()    * vertMult
    ) * accel * arMult

    -- ─── [D6 idle] гравитация только при бездействии ───
    local idle = CurTime() - (ply.D6LastInput or 0)
    local gravFactor = 0
    if idle > CFG.idleGravSec then
        gravFactor = math.Clamp((idle - CFG.idleGravSec) / CFG.idleGravRamp, 0, 1)
    end
    local MICRO_GRAV = 20
    ply.D6Vel.z = ply.D6Vel.z - (MICRO_GRAV + gravity * gravFactor) * ft

    -- ─── [Descent] раскрутка тяги: двигатель набирает/сбрасывает тягу ───
    -- Асимметрия (быстрый набор / медленный спад) даёт ощущение массы
    -- без вялости управления. Рывок владеет D6Vel сам — тогда только
    -- отслеживаем цель, чтобы на выходе из рывка не было задержки набора.
    if ply.D6DashOn then
        ply.D6ThrustCur = thrustTarget
    else
        local spool = (thrustTarget:LengthSqr() >= ply.D6ThrustCur:LengthSqr())
                      and CFG.spoolUp or CFG.spoolDown
        ply.D6ThrustCur = LerpVector(ft * spool, ply.D6ThrustCur, thrustTarget)
        ply.D6Vel = ply.D6Vel * math.pow(inertia, ft * 60) + ply.D6ThrustCur * ft
    end

    local sq  = ply.D6Vel:LengthSqr()
    local spd = math.sqrt(sq)
    if not flightAssist then
        if sq > 0 then
            ply.D6Vel = ply.D6Vel - ply.D6Vel:GetNormalized() * (sq * 0.000020 * drag * ft)
        end
    else
        if sq > 0 then
            local quadDrag    = sq * 0.000020 * drag * ft
            local linearBrake = (not hasInput) and (spd * CFG.brakeMult * ft) or 0
            ply.D6Vel = ply.D6Vel - ply.D6Vel:GetNormalized() * math.min(spd, quadDrag + linearBrake)
        end
        if ply.D6Vel:LengthSqr() < 4 and not hasInput then ply.D6Vel = Vector(0, 0, 0) end
    end

    local spd = ply.D6Vel:Length()
    local cap = maxSpeed * (ply.D6AlwaysRun and Lerp(eng, 1.3, 1.8) or 1.0)
    if spd > cap then ply.D6Vel = ply.D6Vel * (cap / spd) end

    if SERVER then
        local tr = util.TraceHull({
            start  = ply:GetPos(),
            endpos = ply:GetPos() + ply.D6Vel * ft,
            filter = ply,
            mins   = Vector(-16, -16, -16),
            maxs   = Vector(16,  16,  16),
            mask   = MASK_SOLID_BRUSHONLY
        })
        if tr.Hit then
            -- ─── [Descent] упругий отскок 50% энергии ───
            local d = ply.D6Vel:Dot(tr.HitNormal)
            if d < 0 then
                ply.D6Vel = ply.D6Vel - tr.HitNormal * d * (1 + wallBounce)
            end
        end
    end

    mvd:SetVelocity(ply.D6Vel)
    cmd:SetForwardMove(0); cmd:SetSideMove(0); cmd:SetUpMove(0)
end)

print("[D6] d6_core.lua OK")
