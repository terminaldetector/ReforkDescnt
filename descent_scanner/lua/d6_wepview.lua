-- ═══════════════════════════════════════════════════════════
-- d6_wepview.lua — DOOM-стиль рендер оружий Descent 6DOF
--
-- ВАЖНО: SWEP:DrawViewModel() НЕ существует в API GMod —
-- поэтому рендер всех оружий централизован здесь, в хуке
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

-- ── Компоновка слотов ─────────────────────────────────────
--
-- Правила отображения по концепту:
--   РЯД 1 (нижний)  — rgt ±29, up −13  (nosegun/airboat по краям)
--   РЯД 2 (верхний) — rgt ±32, up +10  (nosegun/airboat по краям)
--   ГРАВИЦАПА       — rgt  0,  up −12  (центр, w_physics.mdl)
--   СТРИДЕР-ПУШКА   — rgt  0,  up −13  (центр, strider_weapon.mdl, без гравицапы)
--
-- Правило: airboatgun + gunship_nosegun → гравицапа В ЦЕНТРЕ
--          strider_weapon               → гравицапы НЕТ

-- Центральные пушки
local GRAV_CENTER    = { mdl=MDL_GRAVGUN, fwd=15, rgt=0, up=-12, pitch=6, yaw=0, scale=1.20 }
-- Гравицапа грави-рельсы — опущена ниже обычной (по просьбе: «гравипукалку ниже»)
local GRAV_CENTER_RAIL = { mdl=MDL_GRAVGUN, fwd=15, rgt=0, up=-24, pitch=6, yaw=0, scale=1.20 }
local STRIDER_CENTER = { mdl=MDL_STRIDER, fwd=27, rgt=0, up=-13, pitch=0, yaw=0, scale=0.55 }
local STRIDER_BIG    = { mdl=MDL_STRIDER, fwd=25, rgt=0, up=-13, pitch=0, yaw=0, scale=0.75 }

-- 4 модели (2 нижних + 2 верхних) + опциональный центр
local function Layout(row1Mdl, row1Scale, row2Mdl, row2Scale, center)
    local t = {
        { mdl=row1Mdl, fwd=19, rgt=-29, up=-13, pitch=0, yaw=-9, scale=row1Scale },
        { mdl=row1Mdl, fwd=19, rgt= 29, up=-13, pitch=0, yaw= 9, scale=row1Scale },
        { mdl=row2Mdl, fwd=19, rgt=-32, up= 10, pitch=0, yaw=-9, scale=row2Scale },
        { mdl=row2Mdl, fwd=19, rgt= 32, up= 10, pitch=0, yaw= 9, scale=row2Scale },
    }
    if center then t[#t+1] = center end
    return t
end

-- 2 модели (только нижние крылья) + опциональный центр — для утилитарных оружий
local function LayoutPair(mdl, scale, center)
    local t = {
        { mdl=mdl, fwd=19, rgt=-29, up=-13, pitch=0, yaw=-9, scale=scale },
        { mdl=mdl, fwd=19, rgt= 29, up=-13, pitch=0, yaw= 9, scale=scale },
    }
    if center then t[#t+1] = center end
    return t
end

-- ── Конфигурация по классу оружия ────────────────────────
local CFG = {

    -- ══ СЛОТ 2: ОСНОВНОЕ ОРУЖИЕ ════════════════════════════
    -- MG, Plasma, Heavy: airboat + nosegun + гравицапа центр
    ["weapon_d6_mg"]     = Layout(MDL_AIRBOAT, 1.00, MDL_AIRBOAT, 1.00, GRAV_CENTER),
    ["weapon_d6_plasma"] = Layout(MDL_NOSEGUN, 1.55, MDL_AIRBOAT, 1.00, GRAV_CENTER),
    ["weapon_d6_heavy"]  = Layout(MDL_NOSEGUN, 1.70, MDL_AIRBOAT, 1.05, GRAV_CENTER),

    -- Laser: airboat + стридер-пушка в центре, ГРАВИЦАПЫ НЕТ
    ["weapon_d6_laser"]  = Layout(MDL_AIRBOAT, 1.00, MDL_AIRBOAT, 1.00, STRIDER_CENTER),

    -- Vulcan: симметричные airboat-стволы + гравицапа (ещё одна пара пушек)
    ["weapon_d6_vulcan"] = Layout(MDL_AIRBOAT, 0.92, MDL_AIRBOAT, 0.92, GRAV_CENTER),

    -- Quad Laser: four-beam, стридер центр (энергетическое)
    ["weapon_d6_quad_laser"] = Layout(MDL_AIRBOAT, 0.90, MDL_AIRBOAT, 0.90,
        { mdl=MDL_STRIDER, fwd=27, rgt=0, up=-13, pitch=0, yaw=0, scale=0.60 }),

    -- ══ СЛОТ 4: ВТОРИЧНОЕ / ТЯЖЁЛОЕ ═══════════════════════
    -- Rockets: airboat + гравицапа
    ["weapon_d6_rockets"]       = Layout(MDL_AIRBOAT, 1.00, MDL_AIRBOAT, 1.00, GRAV_CENTER),

    -- Grav Railgun: airboat + гравицапа (опущена ниже)
    ["weapon_d6_gravy_railgun"] = Layout(MDL_AIRBOAT, 1.00, MDL_AIRBOAT, 1.00, GRAV_CENTER_RAIL),

    -- Rail Mk2: nosegun + стридер-пушка центр, ГРАВИЦАПЫ НЕТ
    ["weapon_d6_railmk2"] = Layout(MDL_NOSEGUN, 1.40, MDL_AIRBOAT, 1.00,
        { mdl=MDL_STRIDER, fwd=25, rgt=0, up=-12, pitch=0, yaw=0, scale=0.65 }),

    -- Concussion: тяжёлый nosegun + гравицапа
    ["weapon_d6_concussion"]    = Layout(MDL_NOSEGUN, 1.55, MDL_AIRBOAT, 1.00, GRAV_CENTER),

    -- Homing: nosegun (одна пара) + airboat + гравицапа
    ["weapon_d6_homing"]        = Layout(MDL_NOSEGUN, 1.40, MDL_AIRBOAT, 0.95, GRAV_CENTER),

    -- Smart / Mega Missile: нарастающий размер nosegun
    ["weapon_d6_smart_missile"] = Layout(MDL_NOSEGUN, 1.55, MDL_NOSEGUN, 1.40, GRAV_CENTER),
    ["weapon_d6_mega_missile"]  = Layout(MDL_NOSEGUN, 1.80, MDL_NOSEGUN, 1.80, GRAV_CENTER),

    -- ══ ПРОТИВОРОЕВОЕ (Cat D) ══════════════════════════════
    -- BFG: двойной nosegun + гравицапа (шар энергии)
    ["weapon_d6_bfg"]       = Layout(MDL_NOSEGUN, 1.70, MDL_NOSEGUN, 1.70, GRAV_CENTER),

    -- Flak: airboat + nosegun + гравицапа (осколочная смесь)
    ["weapon_d6_flak"]      = Layout(MDL_AIRBOAT, 1.05, MDL_NOSEGUN, 1.55, GRAV_CENTER),

    -- Frag Launcher: airboat + гравицапа
    ["weapon_d6_frag"]      = Layout(MDL_AIRBOAT, 1.00, MDL_AIRBOAT, 1.00, GRAV_CENTER),

    -- Shockwave: двойной nosegun + гравицапа
    ["weapon_d6_shockwave"] = Layout(MDL_NOSEGUN, 1.60, MDL_NOSEGUN, 1.60, GRAV_CENTER),

    -- ══ ЗОННЫЙ КОНТРОЛЬ (Cat E) ════════════════════════════
    -- Mines: укороченный вид (только нижние крылья)
    ["weapon_d6_plasmamine"] = LayoutPair(MDL_AIRBOAT, 0.85),
    ["weapon_d6_gravmine"]   = LayoutPair(MDL_AIRBOAT, 0.85, GRAV_CENTER),
    ["weapon_d6_energytrap"] = LayoutPair(MDL_NOSEGUN, 1.30, STRIDER_CENTER),
    ["weapon_d6_darkfield"]  = LayoutPair(MDL_NOSEGUN, 1.50,
        { mdl=MDL_STRIDER, fwd=27, rgt=0, up=-13, pitch=0, yaw=0, scale=0.70 }),

    -- ══ МОБИЛЬНОСТЬ (Cat F) ════════════════════════════════
    ["weapon_d6_telefrag"]   = LayoutPair(MDL_AIRBOAT, 0.80),
    ["weapon_d6_whiplash"]   = LayoutPair(MDL_AIRBOAT, 0.90, GRAV_CENTER),
    ["weapon_d6_warp"]       = LayoutPair(MDL_NOSEGUN, 1.20, STRIDER_CENTER),

    -- ══ УЛЬТИМАТИВНЫЕ (Cat G) ══════════════════════════════
    -- Тяжёлый двойной nosegun + большой стридер центр, ГРАВИЦАПЫ НЕТ
    ["weapon_d6_overdrive"]  = Layout(MDL_NOSEGUN, 1.80, MDL_NOSEGUN, 1.70, STRIDER_BIG),
    ["weapon_d6_reactor"]    = Layout(MDL_NOSEGUN, 1.80, MDL_NOSEGUN, 1.80, STRIDER_BIG),
    ["weapon_d6_darklance"]  = Layout(MDL_NOSEGUN, 1.90, MDL_NOSEGUN, 1.90,
        { mdl=MDL_STRIDER, fwd=27, rgt=0, up=-13, pitch=0, yaw=0, scale=0.90 }),
}

-- Экспорт для d6_sck_bridge.lua («Загрузить вид в редактор SCK»)
D6_WEPVIEW_CFG = CFG

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
        if s then
            local m = ClientsideModel(s.mdl, RENDERGROUP_OPAQUE)
            if IsValid(m) then
                m:SetNoDraw(true)
                if s.scale and s.scale ~= 1 then m:SetModelScale(s.scale, 0) end
                Models[i] = m
            end
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

    -- Класс с визуальным переопределением рендерит d6_sck_bridge
    -- (конвейер VElements) — здесь только прячем viewmodel.
    local override = D6_SCKBridge and D6_SCKBridge.Has and D6_SCKBridge.Has(class) or false

    -- Скрыть/показать стандартный viewmodel
    local vm = ply:GetViewModel()
    if IsValid(vm) then
        local wantHide = (cfg ~= nil) or override
        if vm:GetNoDraw() ~= wantHide then vm:SetNoDraw(wantHide) end
    end

    if override or not cfg then
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

    -- Покачивание от скорости + боковой наклон при повороте
    local spd   = ply:GetVelocity():Length()
    local sway  = math.sin(RealTime() * 4) * math.min(spd / 2200, 1) * 0.8
    local tv    = ply._D6AngVel or ply.D6TurnVel or { pitch = 0, yaw = 0 }
    local xsway = tv.yaw * 18

    render.DepthRange(0, 0.1)
    for i, m in ipairs(Models) do
        local s = cfg[i]
        if IsValid(m) and s then
            m:SetPos(ep + fwd * s.fwd + rgt * (s.rgt + xsway) + up * (s.up + sway))
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

print("[D6] d6_wepview.lua OK — " .. table.Count(CFG) .. " оружий")

