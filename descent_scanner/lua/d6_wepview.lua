-- ═══════════════════════════════════════════════════════════
-- d6_wepview.lua — DOOM-стиль рендер оружий Descent 6DOF
--
-- ВАЖНО: SWEP:DrawViewModel() НЕ существует в API GMod —
-- поэтому рендер всех 5 оружий централизован здесь, в хуке
-- PostDrawTranslucentRenderables (вызывается каждый кадр).
--
-- Модели ClientsideModel позиционируются относительно камеры:
--   pos = EyePos + fwd*s.fwd + rgt*s.rgt + up*s.up
-- render.DepthRange(0, 0.1) прижимает их к ближней плоскости,
-- чтобы стволы не утопали в стенах.
-- ═══════════════════════════════════════════════════════════
if not CLIENT then return end

local MDL_AIRBOAT = "models/airboatgun.mdl"
local MDL_NOSEGUN = "models/gibs/gunship_gibs_nosegun.mdl"
local MDL_STRIDER = "models/gibs/strider_weapon.mdl"
local MDL_GRAVGUN = "models/weapons/w_physics.mdl"

-- ── Конфигурация слотов по классу оружия ─────────────────
-- fwd/rgt/up — смещение от камеры; pitch/yaw/roll — поворот;
-- scale — масштаб модели. Правь числа для тонкой настройки.
-- Пушки разведены к краям экрана (outer rgt ±63, inner rgt ±30).
-- weapon_d6_rockets не отображается (ракеты спрятаны на крыше).
local CFG = {
    ["weapon_d6_pulse"] = {
        { mdl=MDL_AIRBOAT, fwd=32, rgt=-63, up=-18, pitch=0, yaw=-8, scale=1.00 },
        { mdl=MDL_AIRBOAT, fwd=30, rgt=-30, up=-22, pitch=0, yaw=-3, scale=0.85 },
        { mdl=MDL_AIRBOAT, fwd=30, rgt= 30, up=-22, pitch=0, yaw= 3, scale=0.85 },
        { mdl=MDL_AIRBOAT, fwd=32, rgt= 63, up=-18, pitch=0, yaw= 8, scale=1.00 },
        { mdl=MDL_GRAVGUN, fwd=24, rgt=  0, up=-14, pitch=6, yaw= 0, scale=1.20 },
    },
    ["weapon_d6_plasma"] = {
        { mdl=MDL_AIRBOAT, fwd=32, rgt=-63, up=-18, pitch=0, yaw=-8, scale=1.00 },
        { mdl=MDL_NOSEGUN, fwd=30, rgt=-28, up=-21, pitch=0, yaw=-3, scale=1.55 },
        { mdl=MDL_NOSEGUN, fwd=30, rgt= 28, up=-21, pitch=0, yaw= 3, scale=1.55 },
        { mdl=MDL_AIRBOAT, fwd=32, rgt= 63, up=-18, pitch=0, yaw= 8, scale=1.00 },
        { mdl=MDL_GRAVGUN, fwd=24, rgt=  0, up=-14, pitch=6, yaw= 0, scale=1.20 },
    },
    ["weapon_d6_heavy"] = {
        { mdl=MDL_AIRBOAT, fwd=32, rgt=-64, up=-17, pitch=0, yaw=-9, scale=1.05 },
        { mdl=MDL_NOSEGUN, fwd=30, rgt=-30, up=-20, pitch=2, yaw=-3, scale=1.65 },
        { mdl=MDL_NOSEGUN, fwd=30, rgt= 30, up=-20, pitch=2, yaw= 3, scale=1.65 },
        { mdl=MDL_AIRBOAT, fwd=32, rgt= 64, up=-17, pitch=0, yaw= 9, scale=1.05 },
        { mdl=MDL_GRAVGUN, fwd=23, rgt=  0, up=-13, pitch=8, yaw= 0, scale=1.25 },
    },
    ["weapon_d6_laser"] = {
        { mdl=MDL_STRIDER, fwd=42, rgt=0, up=-20, pitch=0, yaw=0, scale=0.55 },
    },
    ["weapon_d6_gravy_railgun"] = {
        { mdl=MDL_GRAVGUN, fwd=24, rgt=0, up=-14, pitch=6, yaw=0, scale=1.40 },
    },
}

local curClass = nil
local Models   = {}

local function DestroyModels()
    for _, m in ipairs(Models) do
        if IsValid(m) then m:Remove() end
    end
    Models = {}
end

local function BuildModels(cfg)
    DestroyModels()
    for i, s in ipairs(cfg) do
        local m = ClientsideModel(s.mdl, RENDERGROUP_OPAQUE)
        if IsValid(m) then
            m:SetNoDraw(true)
            if s.scale and s.scale ~= 1 then m:SetModelScale(s.scale, 0) end
            Models[i] = m
        end
    end
end

hook.Add("PostDrawTranslucentRenderables", "D6_WepView", function(bDepth, bSky)
    if bDepth or bSky then return end

    local ply = LocalPlayer()
    if not IsValid(ply) then return end

    local wep   = ply:GetActiveWeapon()
    local class = IsValid(wep) and wep:GetClass() or ""
    local cfg   = CFG[class]

    -- Скрыть/показать стандартный viewmodel (v_physics.mdl)
    local vm = ply:GetViewModel()
    if IsValid(vm) then
        local wantHide = (cfg ~= nil)
        if vm:GetNoDraw() ~= wantHide then vm:SetNoDraw(wantHide) end
    end

    if not cfg then
        if curClass then DestroyModels(); curClass = nil end
        return
    end

    if class ~= curClass then
        BuildModels(cfg)
        curClass = class
    end

    -- В третьем лице свои стволы не рисуем
    if ply:ShouldDrawLocalPlayer() then return end

    local ep  = EyePos()
    local ea  = EyeAngles()
    local fwd = ea:Forward()
    local rgt = ea:Right()
    local up  = ea:Up()

    -- Лёгкое покачивание от скорости полёта
    local spd  = ply:GetVelocity():Length()
    local sway = math.sin(RealTime() * 4) * math.min(spd / 2200, 1) * 0.8

    render.DepthRange(0, 0.1)
    for i, m in ipairs(Models) do
        local s = cfg[i]
        if IsValid(m) and s then
            m:SetPos(ep + fwd * s.fwd + rgt * s.rgt + up * (s.up + sway))
            local a = Angle(ea.p, ea.y, ea.r)
            a:RotateAroundAxis(up,  s.yaw   or 0)
            a:RotateAroundAxis(rgt, s.pitch or 0)
            a:RotateAroundAxis(fwd, s.roll  or 0)
            m:SetAngles(a)
            m:SetupBones()
            m:DrawModel()
        end
    end
    render.DepthRange(0, 1)
end)

print("[D6] d6_wepview.lua OK")
