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
local HookLightT = 0
local RamLightT  = 0
local ThirdPerson = false

local Remote = {}  -- Remote[ply] = { ang, angLerp, hookOn, hookPos, ramOn }

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
    -- Сохраняем на энтити для weapon_d6_omni
    ply._D6AngLerp = Remote[ply].angLerp
end)

net.Receive("D6_HookSync", function()
    local ply = net.ReadEntity(); local on = net.ReadBool(); local pos = net.ReadVector()
    if not IsValid(ply) then return end
    if ply == LocalPlayer() then Hook.on = on; Hook.pos = pos
    else Remote[ply] = Remote[ply] or {}; Remote[ply].hookOn = on; Remote[ply].hookPos = pos end
end)

net.Receive("D6_DashSync", function()
    local ply = net.ReadEntity(); local on = net.ReadBool()
    if not IsValid(ply) then return end
    if ply == LocalPlayer() then Dashing = on
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

    -- ─── [Descent] инерция вращения камеры ───
    -- Целевое вращение фильтруется через Lerp с коэффициентом 12.
    -- При резком движении мыши камера слегка отстаёт, потом догоняет.
    ply.D6TurnVel = ply.D6TurnVel or { pitch = 0, yaw = 0 }
    local targetPitch =  cmd:GetMouseY() * 0.015
    local targetYaw   = -cmd:GetMouseX() * 0.015
    ply.D6TurnVel.pitch = Lerp(ft * 12, ply.D6TurnVel.pitch, targetPitch)
    ply.D6TurnVel.yaw   = Lerp(ft * 12, ply.D6TurnVel.yaw,   targetYaw)
    Ang:RotateAroundAxis(Ang:Right(), ply.D6TurnVel.pitch)
    Ang:RotateAroundAxis(Ang:Up(),    ply.D6TurnVel.yaw)

    local roll = 0
    if input.IsKeyDown(KEY_LSHIFT) then roll = roll - 1 end
    if input.IsKeyDown(KEY_F)      then roll = roll + 1 end
    if roll ~= 0 then Ang:RotateAroundAxis(Ang:Forward(), roll * ROLL_SPEED * ft) end

    Ang.r = math.Clamp(((Ang.r + 180) % 360) - 180, -ROLL_LIMIT, ROLL_LIMIT)

    if input.IsKeyDown(KEY_R) then
        local now = CurTime()
        if now - LastRTap < DBL_TAP_WIN then Ang.r = Lerp(ft * 12, Ang.r, 0) end
        LastRTap = now
    end

    cmd:SetViewAngles(Ang)
    ply.D6Ang = Ang

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
    if ThirdPerson then
        local back = Ang:Forward() * -100 + Ang:Up() * 22
        local tr   = util.TraceLine({ start=pos, endpos=pos+back, filter=ply })
        return { origin=tr.HitPos + tr.HitNormal*3, angles=Ang, fov=fov, drawviewer=true }
    end
    return { origin=pos, angles=Ang, fov=fov, drawviewer=false }
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
    local mode = IsValid(wep) and wep:GetClass()=="weapon_d6_omni" and (wep.Mode or 0) or 0
    local MCOL = {[0]=Color(255,80,80),[1]=Color(80,200,255),[2]=Color(255,150,0),[3]=Color(0,220,120)}
    local MNAM = {[0]="ПУШКИ",[1]="ЛАЗЕРЫ",[2]="БОМБЫ",[3]="ЭЛ.КРЮК"}
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
    local mode = IsValid(wep) and wep:GetClass()=="weapon_d6_omni" and (wep.Mode or 0) or 0
    local MCOL = {[0]=Color(255,80,80),[1]=Color(80,200,255),[2]=Color(255,150,0),[3]=Color(0,220,120)}
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

print("[D6] d6_client.lua OK")
