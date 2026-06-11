-- =========================================================
-- DESCENT 6DOF — d6_client.lua  (чистая версия)
-- Пушки-вертолёты убраны полностью.
-- Модель игрока — combine_scanner.mdl (ставится в d6_core).
-- =========================================================
if not CLIENT then return end

local ROLL_SPEED  = 175
local ROLL_LIMIT  = 180
local DBL_TAP_WIN = 0.38
local ANG_HZ      = 20
local ANG_THRESH  = 1.5

local function SM(p, fb)
    local ok, m = pcall(Material, p)
    return (ok and not m:IsError()) and m or Material(fb or "vgui/white")
end
local MAT_BEAM = SM("cable/xbeam",      "cable/cable")
local MAT_FIRE = SM("sprites/flamelet1","effects/yellowflare")

local Ang        = nil
local LastAng    = Angle()
local LastSendT  = 0
local LastRTap   = 0
local CamReady   = false
local Hook       = { on=false, pos=Vector() }
local Dashing    = false
local DashDir    = Vector(0,0,0)
local HookLightT    = 0
local RamLightT     = 0
local ThirdPerson   = false
local ThrusterLightT = 0
local CamLag        = Vector(0, 0, 0)
local RollVel       = 0

local Remote = {}  -- Remote[ply] = { ang, angLerp, hookOn, hookPos, ramOn }

-- X-клавиша: рывок; Q-клавиша: быстрый выбор грависрельсы
local XKeyDown = false
local QKeyDown = false

-- =========================================================
-- NET RECEIVES
-- =========================================================
net.Receive("D6_Toggle", function()
    local ply = net.ReadEntity(); local on = net.ReadBool()
    if not IsValid(ply) then return end
    if on then
        Remote[ply] = Remote[ply] or {}; Remote[ply].d6On = true
    else
        Remote[ply] = nil
        if ply == LocalPlayer() then
            Ang = nil; CamReady = false
            Hook = { on=false, pos=Vector() }
            Ramming = false
        end
    end
end)

net.Receive("D6_AngSync", function()
    local ply = net.ReadEntity(); local ang = net.ReadAngle()
    if not IsValid(ply) or ply == LocalPlayer() then return end
    Remote[ply] = Remote[ply] or {}
    Remote[ply].ang = ang
    Remote[ply].angLerp = Remote[ply].angLerp or ang
    -- Сохраняем на энтити для D6-оружий
    ply._D6AngLerp = Remote[ply].angLerp
end)

net.Receive("D6_HookSync", function()
    local ply = net.ReadEntity(); local on = net.ReadBool(); local pos = net.ReadVector()
    if not IsValid(ply) then return end
    if ply == LocalPlayer() then Hook.on = on; Hook.pos = pos
    else Remote[ply] = Remote[ply] or {}; Remote[ply].hookOn = on; Remote[ply].hookPos = pos end
end)

net.Receive("D6_DashSync", function()
    local ply = net.ReadEntity(); local on = net.ReadBool(); local dir = net.ReadVector()
    if not IsValid(ply) then return end
    if ply == LocalPlayer() then
        Dashing = on
        if on then DashDir = dir end
    else Remote[ply] = Remote[ply] or {}; Remote[ply].dashOn = on end
end)

net.Receive("D6_RamBoom", function()
    local pos = net.ReadVector(); local norm = net.ReadVector()
    local ef = EffectData(); ef:SetOrigin(pos); ef:SetNormal(norm); ef:SetScale(2)
    util.Effect("cball_explode", ef); util.Effect("Explosion", ef)
end)

net.Receive("D6_AlwaysRunSync", function()
    local ply = net.ReadEntity(); local on = net.ReadBool()
    if IsValid(ply) and ply == LocalPlayer() then
        -- обновляется через NWBool, здесь только для HUD
    end
end)

hook.Add("EntityRemoved", "D6_CLI_EntRemoved", function(ent)
    if ent:IsPlayer() then Remote[ent] = nil end
end)

-- =========================================================
-- КАМЕРА
-- =========================================================
hook.Add("CreateMove", "D6_Cam_CM", function(cmd)
    local ply = LocalPlayer()
    if not IsValid(ply) or not ply:GetNWBool("D6On", false) then
        CamReady = false; return
    end
    if not CamReady or not Ang then
        Ang = cmd:GetViewAngles(); CamReady = true
    end
    local ft = FrameTime()
    if ft <= 0 then return end

    -- ── [Descent] угловая инерция камеры ────────────────────────────
    -- TurnVel: первый слой фильтра — сглаживает мышиный ввод (75ms до 50%).
    --          было ft*12 (60ms) — слишком быстро, нет ощущения массы.
    --          стало ft*8  (75ms) — отклик 75ms, угасание 240ms.
    -- _D6AngVel: второй слой — угловая инерция тела (FA=ON 135ms, FA=OFF 330ms).
    ply.D6TurnVel = ply.D6TurnVel or { pitch = 0, yaw = 0 }
    ply._D6AngVel = ply._D6AngVel or { pitch = 0, yaw = 0 }
    local targetPitch =  cmd:GetMouseY() * 0.015
    local targetYaw   = -cmd:GetMouseX() * 0.015
    ply.D6TurnVel.pitch = Lerp(ft * 8, ply.D6TurnVel.pitch, targetPitch)
    ply.D6TurnVel.yaw   = Lerp(ft * 8, ply.D6TurnVel.yaw,   targetYaw)
    local angDamp = ply:GetNWBool("D6_FlightAssist", true) and 5 or 2.0
    ply._D6AngVel.pitch = Lerp(ft * angDamp, ply._D6AngVel.pitch, ply.D6TurnVel.pitch)
    ply._D6AngVel.yaw   = Lerp(ft * angDamp, ply._D6AngVel.yaw,   ply.D6TurnVel.yaw)
    Ang:RotateAroundAxis(Ang:Right(), ply._D6AngVel.pitch)
    Ang:RotateAroundAxis(Ang:Up(),    ply._D6AngVel.yaw)

    -- ─── [Descent] инерция крена: плавная раскрутка/останов ───
    local rollIn = 0
    if input.IsKeyDown(KEY_LSHIFT) then rollIn = rollIn - 1 end
    if input.IsKeyDown(KEY_F)      then rollIn = rollIn + 1 end
    RollVel = Lerp(ft * 5, RollVel, rollIn * ROLL_SPEED) -- было ft*8: крен инерционнее
    Ang:RotateAroundAxis(Ang:Forward(), RollVel * ft)

    Ang.r = math.Clamp(((Ang.r + 180) % 360) - 180, -ROLL_LIMIT, ROLL_LIMIT)

    if input.IsKeyDown(KEY_R) then
        local now = CurTime()
        if now - LastRTap < DBL_TAP_WIN then
            Ang.r   = Lerp(ft * 12, Ang.r, 0)
            RollVel = Lerp(ft * 12, RollVel, 0)  -- момент крена не мешает выравниванию
        end
        LastRTap = now
    end

    cmd:SetViewAngles(Ang)
    ply.D6Ang = Ang

    -- ─── X-клавиша: рывок ───────────────────────────────────
    -- Читаем направление из текущего cmd (точные данные клиента).
    -- Отправляем только на переднем фронте нажатия.
    local xDown = input.IsKeyDown(KEY_X)
    if xDown and not XKeyDown then
        -- Компоненты направления: -1 / 0 / 1
        local fx = (cmd:KeyDown(IN_FORWARD)   and 1 or 0) - (cmd:KeyDown(IN_BACK)      and 1 or 0)
        local fy = (cmd:KeyDown(IN_MOVERIGHT)  and 1 or 0) - (cmd:KeyDown(IN_MOVELEFT)  and 1 or 0)
        local fz = (cmd:KeyDown(IN_JUMP)       and 1 or 0) - (cmd:KeyDown(IN_DUCK)      and 1 or 0)
        net.Start("D6_DashDir")
            net.WriteInt(fx, 4)
            net.WriteInt(fy, 4)
            net.WriteInt(fz, 4)
        net.SendToServer()
    end
    XKeyDown = xDown

    -- Q-клавиша: быстрый выбор грависрельсы (центральное оружие)
    local qDown = input.IsKeyDown(KEY_Q)
    if qDown and not QKeyDown then
        net.Start("D6_WeaponSwitch"); net.WriteString("weapon_d6_gravy_railgun"); net.SendToServer()
    end
    QKeyDown = qDown

    local now = RealTime()
    if now - LastSendT >= 1 / ANG_HZ then
        if math.abs(Ang.p - LastAng.p) > ANG_THRESH
        or math.abs(Ang.y - LastAng.y) > ANG_THRESH
        or math.abs(Ang.r - LastAng.r) > ANG_THRESH then
            net.Start("D6_AngSync"); net.WriteAngle(Ang); net.SendToServer()
            LastAng = Angle(Ang.p, Ang.y, Ang.r); LastSendT = now
        end
    end
end)

hook.Add("CalcView", "D6_Cam_CV", function(ply, pos, _, fov)
    if not IsValid(ply) or not ply:GetNWBool("D6On", false) or not Ang then return end
    local lagTarget = Vector(0, 0, 0)
    if ply:GetNWBool("D6AlwaysRun", false) then
        local fwdSpd = ply:GetVelocity():Dot(Ang:Forward())
        lagTarget = -Ang:Forward() * math.Clamp(fwdSpd / 800, 0, 5)
    end
    if Dashing then lagTarget = lagTarget - DashDir * 8 end
    CamLag = LerpVector(FrameTime() * 10, CamLag, lagTarget)
    local origin = pos + CamLag

    -- ── Micro-motion: реакторное покачивание корпуса ─────────────────
    -- Двухчастотное смещение ПОЗИЦИИ (не угла, не camera shake).
    -- Амплитуда пропорциональна скорости: ноль при стоянии, макс ~1.4ед на топ-скорости.
    -- Две частоты (8.3Hz + 11.7Hz) создают биение — ощущение работающего реактора.
    -- Направление смещения привязано к осям КОРПУСА, не мировым координатам.
    local spd_mm = ply:GetVelocity():Length()
    local mm_amp = math.Clamp(spd_mm / 1800, 0, 1) * 1.4
    if mm_amp > 0.05 then
        local t_mm  = CurTime() + ply:EntIndex() * 0.37   -- фаза уникальна для каждого игрока
        local drift = Ang:Right()   * math.sin(t_mm * 8.3)  * mm_amp
                    + Ang:Up()      * math.sin(t_mm * 11.7) * mm_amp * 0.55
        origin = origin + drift
    end

    -- ── Engine vibration (уже было, оставлено без изменений) ─────────
    local arOn   = ply:GetNWBool("D6AlwaysRun", false)
    local spd    = ply:GetVelocity():Length()
    local vibAmp = (arOn and 0.6 or 0.25) * math.Clamp(spd / 900, 0, 1)
    if vibAmp > 0.01 then
        local t   = CurTime()
        local vib = Ang:Right()   * math.sin(t * 61)
                  + Ang:Up()      * math.sin(t * 67)
                  + Ang:Forward() * math.sin(t * 53)
        origin = origin + vib * vibAmp
    end
    if ThirdPerson then
        local back = Ang:Forward() * -100 + Ang:Up() * 22
        local tr   = util.TraceLine({ start=origin, endpos=origin+back, filter=ply })
        return { origin=tr.HitPos + tr.HitNormal*3, angles=Ang, fov=fov, drawviewer=true }
    end
    return { origin=origin, angles=Ang, fov=fov, drawviewer=false }
end)

hook.Add("PlayerButtonDown", "D6_CamSwitch", function(key)
    if key ~= KEY_F5 then return end
    local ply = LocalPlayer()
    if not IsValid(ply) or not ply:GetNWBool("D6On", false) then return end
    ThirdPerson = not ThirdPerson
    chat.AddText(Color(160,40,255), "[6DOF] ", color_white,
        ThirdPerson and "Третье лицо" or "Первое лицо")
end)

-- =========================================================
-- ТРОС КРЮКА
-- =========================================================
hook.Add("PreDrawEffects", "D6_DrawHook", function()
    local ply = LocalPlayer()
    if not IsValid(ply) or not Hook.on then return end
    local ang = Ang or ply:GetAngles()
    local s = ply:GetPos() + ang:Forward()*22 - ang:Up()*8
    local e = Hook.pos
    render.SetMaterial(MAT_BEAM)
    local sc = CurTime() * -2
    render.DrawBeam(s, e, 5, sc, sc+1, Color(0,160,255,255))
    render.DrawBeam(s, e, 2, sc*2, sc*2+1, Color(200,230,255,200))
    local now = RealTime()
    if now - HookLightT >= 0.1 then
        HookLightT = now
        local dl = DynamicLight(LocalPlayer():EntIndex()+500)
        if dl then
            dl.pos=e; dl.r=0; dl.g=120; dl.b=255
            dl.brightness=3; dl.Size=110; dl.DieTime=CurTime()+0.15
        end
    end
end)

-- =========================================================
-- ЭФФЕКТ РЫВКА
-- =========================================================
local DashLightT = 0
hook.Add("PostPlayerDraw", "D6_DrawDash", function(ply)
    if not IsValid(ply) then return end
    local dashOn = (ply == LocalPlayer()) and Dashing
                   or (Remote[ply] and Remote[ply].dashOn)
    if not dashOn or not ply:GetNWBool("D6On", false) then return end
    local now = RealTime()
    if now - DashLightT >= 0.05 then
        DashLightT = now
        local dl = DynamicLight(ply:EntIndex() + 600)
        if dl then
            dl.pos        = ply:GetPos()
            dl.r          = 80; dl.g = 200; dl.b = 255
            dl.brightness = 6
            dl.Size       = 300
            dl.DieTime    = CurTime() + 0.12
        end
    end
end)

-- =========================================================
-- СВЕЧЕНИЕ ДВИЖИТЕЛЕЙ
-- =========================================================
hook.Add("PostPlayerDraw", "D6_ThrusterGlow", function(ply)
    if not IsValid(ply) or not ply:GetNWBool("D6On", false) then return end
    local vel = ply:GetVelocity(); local spd = vel:Length()
    if spd < 80 then return end
    local now = RealTime()
    if now - ThrusterLightT < 0.06 then return end
    ThrusterLightT = now
    local ar = ply:GetNWBool("D6AlwaysRun", false)
    local dl = DynamicLight(ply:EntIndex() + 700)
    if dl then
        dl.pos        = ply:GetPos() - vel:GetNormalized() * 22
        dl.r          = 60  + (ar and 80 or 0)
        dl.g          = 180 - (ar and 30 or 0)
        dl.b          = 255
        dl.brightness = 2 + math.min(spd / 1000, 3) + (ar and 2 or 0)
        dl.Size       = 100 + spd * 0.06 + (ar and 120 or 0)
        dl.DieTime    = CurTime() + 0.10
    end
end)

-- =========================================================
-- ИНТЕРПОЛЯЦИЯ УГЛОВ ДРУГИХ ИГРОКОВ (для корректного вида)
-- =========================================================
hook.Add("Think", "D6_LerpRemoteAngs", function()
    local ft = FrameTime()
    for ply, data in pairs(Remote) do
        if not IsValid(ply) then Remote[ply] = nil; continue end
        if data.ang then
            data.angLerp = LerpAngle(ft * 14, data.angLerp or data.ang, data.ang)
            ply._D6AngLerp = data.angLerp
        end
    end
end)

-- =========================================================
-- HUD
-- =========================================================
hook.Add("HUDPaint", "D6_HUD", function()
    -- ─── [D6 idle] предупреждение о приближающейся гравитации ───
    do
        local lp = LocalPlayer()
        if IsValid(lp) and lp:GetNWBool("D6On", false) then
            local lastInput = lp:GetNWFloat("D6LastInput", CurTime())
            local idleSec   = CurTime() - lastInput
            local IDLE_SEC  = 45
            local WARN_LEAD = 10
            if idleSec > (IDLE_SEC - WARN_LEAD) and idleSec < IDLE_SEC then
                local left = IDLE_SEC - idleSec
                local sw, sh = ScrW(), ScrH()
                local alpha = math.Clamp((WARN_LEAD - left) / WARN_LEAD, 0, 1) * 220 + 35
                local pulse = 0.7 + 0.3 * math.abs(math.sin(CurTime() * 4))
                draw.SimpleTextOutlined(
                    string.format("🪂 ГРАВИТАЦИЯ ЧЕРЕЗ %.1f с", left),
                    "DermaLarge", sw * 0.5, sh * 0.18,
                    Color(255, 200 * pulse, 60 * pulse, alpha),
                    TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER,
                    2, Color(0, 0, 0, alpha * 0.8))
            elseif idleSec >= IDLE_SEC then
                draw.SimpleTextOutlined(
                    "⚠ ГРАВИТАЦИЯ АКТИВНА",
                    "DermaLarge", ScrW() * 0.5, ScrH() * 0.18,
                    Color(255, 80, 80, 220),
                    TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER,
                    2, Color(0, 0, 0, 200))
            end
        end
    end

    local ply = LocalPlayer()
    if not IsValid(ply) or not ply:GetNWBool("D6On", false) then return end
    local sw, sh = ScrW(), ScrH()

    local wep  = ply:GetActiveWeapon()
    local mode = 0
    if IsValid(wep) then
        local c = wep:GetClass()
        if     c == "weapon_d6_plasma"  then mode = 1
        elseif c == "weapon_d6_heavy"   then mode = 2
        elseif c == "weapon_d6_laser"   then mode = 3
        elseif c == "weapon_d6_rockets" then mode = 4 end
    end
    local MCOL = {[0]=Color(255,80,80),[1]=Color(80,200,255),[2]=Color(255,150,0),[3]=Color(0,220,120),[4]=Color(255,230,0)}
    local MNAM = {[0]="ПУЛЬСАР",[1]="ПЛАЗМА",[2]="ТЯЖЁЛЫЙ",[3]="ЛАЗЕР",[4]="РАКЕТЫ"}
    local col  = MCOL[mode] or color_white

    draw.SimpleTextOutlined("[ "..(MNAM[mode] or "?").." ]",
        "DermaDefault", sw*.5, sh-38, col,
        TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER, 1, Color(0,0,0,210))

    -- Always-Run
    if ply:GetNWBool("D6AlwaysRun", false) then
        draw.SimpleTextOutlined("▶▶ ALWAYS-RUN", "DermaDefault",
            sw*.5, sh*.12, Color(255,200,0,220),
            TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER, 1, Color(0,0,0,180))
    end

    -- Крен
    local roll = Ang and Ang.r or 0
    if math.abs(roll) > 4 then
        draw.SimpleTextOutlined(string.format("КРЕН %+.0f°", roll),
            "DermaDefault", sw*.5, sh-56,
            math.abs(roll)>90 and Color(255,80,80) or Color(220,200,0),
            TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER, 1, Color(0,0,0,180))
    end

    if Hook.on then
        draw.SimpleTextOutlined("⚓ КРЮК", "DermaDefault", sw*.5, sh-74,
            Color(0,200,255), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER, 1, Color(0,0,0,200))
    end
    if Dashing then
        draw.SimpleTextOutlined("▷▷ РЫВОК", "DermaLarge", sw*.5, sh*.38,
            Color(80,220,255), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER, 2, Color(0,0,0,200))
    end

    -- Кулдаун рывка (серая полоска под прицелом)
    local dashCD   = ply:GetNWFloat("D6DashCD", 0)
    local dashLeft = math.max(0, dashCD - CurTime())
    local dashMax  = 1.8
    if dashLeft > 0 then
        local barW = 80
        local frac = 1 - (dashLeft / dashMax)
        local bx   = sw * 0.5 - barW * 0.5
        local by   = sh * 0.5 + 38
        surface.SetDrawColor(0, 0, 0, 140)
        surface.DrawRect(bx - 1, by - 1, barW + 2, 7)
        surface.SetDrawColor(80, 220, 255, 200)
        surface.DrawRect(bx, by, barW * frac, 5)
    end

    draw.SimpleTextOutlined(
        "WASD Пробел/Ctrl LShift/F=крен  Z=крюк  d6_dash=рывок  KP0=6DOF  TAB=меню",
        "DermaDefault", sw*.5, sh-18,
        Color(150,150,150,130), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER, 1, Color(0,0,0,80))
end)

-- =========================================================
-- ПРИЦЕЛ В СТИЛЕ DESCENT
-- =========================================================
hook.Add("DrawCrosshair", "D6_Crosshair", function()
    local ply = LocalPlayer()
    if not IsValid(ply) or not ply:GetNWBool("D6On", false) then return end
    local wep  = ply:GetActiveWeapon()
    local mode = 0
    if IsValid(wep) then
        local wc = wep:GetClass()
        if     wc == "weapon_d6_plasma"  then mode = 1
        elseif wc == "weapon_d6_heavy"   then mode = 2
        elseif wc == "weapon_d6_laser"   then mode = 3
        elseif wc == "weapon_d6_rockets" then mode = 4 end
    end
    local MCOL = {[0]=Color(255,80,80),[1]=Color(80,200,255),[2]=Color(255,150,0),[3]=Color(0,220,120),[4]=Color(255,230,0)}
    local c    = MCOL[mode] or Color(255,80,80)
    local cx, cy = ScrW()/2, ScrH()/2
    local r1 = 18

    -- Тень
    surface.SetDrawColor(0,0,0,140)
    surface.DrawLine(cx-r1-13,cy+1,cx-8+1,cy+1); surface.DrawLine(cx+8+1,cy+1,cx+r1+13,cy+1)
    surface.DrawLine(cx+1,cy-r1-13,cx+1,cy-8+1); surface.DrawLine(cx+1,cy+8+1,cx+1,cy+r1+13)

    -- Перекрестие
    surface.SetDrawColor(c.r,c.g,c.b,220)
    surface.DrawLine(cx-r1-12,cy,cx-8,cy); surface.DrawLine(cx+8,cy,cx+r1+12,cy)
    surface.DrawLine(cx,cy-r1-12,cx,cy-8); surface.DrawLine(cx,cy+8,cx,cy+r1+12)

    -- Диагональные метки
    surface.SetDrawColor(c.r,c.g,c.b,110)
    local d=r1*0.707; local ts=5
    surface.DrawLine(cx-d,cy-d,cx-d+ts,cy-d+ts); surface.DrawLine(cx+d,cy-d,cx+d-ts,cy-d+ts)
    surface.DrawLine(cx-d,cy+d,cx-d+ts,cy+d-ts); surface.DrawLine(cx+d,cy+d,cx+d-ts,cy+d-ts)

    -- Внешнее кольцо
    surface.SetDrawColor(c.r,c.g,c.b,70)
    local segs=24; local arcR=r1+6
    for i=0,segs-1 do
        local a1=(i/segs)*2*math.pi; local a2=((i+0.6)/segs)*2*math.pi
        surface.DrawLine(cx+math.cos(a1)*arcR,cy+math.sin(a1)*arcR,
                         cx+math.cos(a2)*arcR,cy+math.sin(a2)*arcR)
    end

    -- Центральная точка
    surface.SetDrawColor(255,255,255,255); surface.DrawRect(cx-1,cy-1,3,3)
    return true
end)

-- =========================================================
-- КОЛЕСО МЫШИ: вниз→ракеты, вверх→боевые
-- =========================================================
local _D6_COMBAT = { "weapon_d6_pulse", "weapon_d6_plasma", "weapon_d6_heavy", "weapon_d6_laser" }
local _D6_ROCKET = "weapon_d6_rockets"

hook.Add("PlayerBindPress", "D6_WeaponWheel", function(ply, bind, pressed)
    if not pressed then return end
    local lp = LocalPlayer()
    if not IsValid(lp) or not lp:GetNWBool("D6On", false) then return end

    if bind == "invnext" then   -- колесо вниз → ракеты / сабрежим
        local cur = lp:GetActiveWeapon()
        if IsValid(cur) and cur:GetClass() == _D6_ROCKET then
            net.Start("D6_RocketSubNext"); net.SendToServer()
        else
            net.Start("D6_WeaponSwitch"); net.WriteString(_D6_ROCKET); net.SendToServer()
        end
        return true

    elseif bind == "invprev" then  -- колесо вверх → боевые (цикл)
        local cur = IsValid(lp:GetActiveWeapon()) and lp:GetActiveWeapon():GetClass() or ""
        local idx = 0
        for i, w in ipairs(_D6_COMBAT) do if w == cur then idx = i; break end end
        net.Start("D6_WeaponSwitch")
            net.WriteString(_D6_COMBAT[(idx % #_D6_COMBAT) + 1])
        net.SendToServer()
        return true
    end
end)

print("[D6] d6_client.lua OK")
