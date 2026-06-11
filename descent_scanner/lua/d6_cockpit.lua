-- =========================================================
-- d6_cockpit.lua — HUD-кокпит в стиле Descent
-- Отрисовывается поверх экрана; НЕ является SWEP.
-- Активен только когда D6On == true.
-- =========================================================
if not CLIENT then return end

local function SM(p, fb)
    local ok, m = pcall(Material, p)
    return (ok and not m:IsError()) and m or Material(fb or "vgui/white")
end

local MAT_GRAD = SM("vgui/gradient-d", "vgui/white")
local MAT_BAR  = SM("vgui/white",      "vgui/white")

-- ── Шрифты кокпита ───────────────────────────────────────
surface.CreateFont("D6_CockSmall", {
    font = "Courier New", size = 11, weight = 600, antialias = false })
surface.CreateFont("D6_CockMed", {
    font = "Courier New", size = 14, weight = 700, antialias = false })
surface.CreateFont("D6_CockBig", {
    font = "Courier New", size = 22, weight = 800, antialias = false })

-- ── Цвета ────────────────────────────────────────────────
local COL = {
    panel    = Color(0,   10,  0,   200),
    border   = Color(0,   200, 80,  200),
    border2  = Color(0,   80,  30,  140),
    shHigh   = Color(80,  255, 100, 255),
    shMid    = Color(220, 200, 30,  255),
    shLow    = Color(255, 60,  40,  255),
    energy   = Color(0,   200, 255, 255),
    speed    = Color(180, 255, 100, 255),
    mode     = {[0]=Color(255,140,0),[1]=Color(80,200,255),[2]=Color(220,40,40),[3]=Color(50,255,80),[4]=Color(255,230,0)},
    text     = Color(160, 255, 140, 230),
    dim      = Color(80,  140, 80,  180),
    black    = Color(0,   0,   0,   220),
    white    = Color(255, 255, 255, 220),
}

local MNAM = {[0]="ПУЛЕМЁТ",[1]="ПЛАЗМА",[2]="ТЯЖЁЛЫЙ",[3]="ЛАЗЕР",[4]="РАКЕТЫ"}

-- ── Вспомогательные ──────────────────────────────────────
local function Bar(x, y, w, h, frac, col, bgCol, border)
    frac = math.Clamp(frac, 0, 1)
    surface.SetDrawColor(bgCol or Color(10,30,10,200))
    surface.DrawRect(x, y, w, h)
    surface.SetDrawColor(col)
    surface.DrawRect(x, y, w * frac, h)
    if border then
        surface.SetDrawColor(COL.border2)
        surface.DrawOutlinedRect(x, y, w, h, 1)
    end
end

local function Label(txt, font, x, y, col, ah, av)
    draw.SimpleText(txt, font, x+1, y+1, COL.black, ah or TEXT_ALIGN_LEFT, av or TEXT_ALIGN_TOP)
    draw.SimpleText(txt, font, x,   y,   col,        ah or TEXT_ALIGN_LEFT, av or TEXT_ALIGN_TOP)
end

local function Outline(x, y, w, h, col, thick)
    surface.SetDrawColor(col)
    surface.DrawOutlinedRect(x, y, w, h, thick or 1)
end

-- ── Звуковой индикатор повреждения ───────────────────────
local DmgFlash = 0
hook.Add("HUDEvent", "D6_CockDmgFlash", function(name)
    if name == "DamageTaken" then DmgFlash = CurTime() + 0.35 end
end)

-- =========================================================
-- ГЛАВНАЯ ОТРИСОВКА КОКПИТА
-- =========================================================
hook.Add("HUDPaint", "D6_Cockpit", function()
    local ply = LocalPlayer()
    if not IsValid(ply) then return end
    if not ply:GetNWBool("D6On", false) then return end

    local sw, sh = ScrW(), ScrH()

    -- Размеры нижней панели
    local PH  = 78    -- высота кокпита
    local PW  = sw
    local PY  = sh - PH

    -- ── Фон нижней панели ─────────────────────────────────
    surface.SetDrawColor(COL.panel)
    surface.DrawRect(0, PY, PW, PH)
    Outline(0, PY, PW, PH, COL.border, 2)

    -- Декоративные вертикальные делители
    surface.SetDrawColor(COL.border2)
    local sectionW = PW / 4
    for i = 1, 3 do
        local lx = math.floor(sectionW * i)
        surface.DrawLine(lx, PY + 4, lx, PY + PH - 4)
    end

    -- ===== СЕКЦИЯ 1: ЩИТ / КОРПУС =====
    local s1x = 8
    local barW = sectionW - 16

    local shield    = ply:GetNWFloat("D6_Shield", 100)
    local shieldMax = ply:GetNWFloat("D6_ShieldMax", 100)
    if shieldMax <= 0 then shieldMax = 100 end
    local shFrac = math.Clamp(shield / shieldMax, 0, 1)
    local shCol  = Color(0, 210, 255, 255)

    local hp    = ply:Health()
    local hpMax = ply:GetMaxHealth()
    if hpMax <= 0 then hpMax = 100 end
    local hpFrac  = hp / hpMax
    local hpColor = hpFrac > 0.6 and COL.shHigh
                 or hpFrac > 0.3 and COL.shMid
                 or COL.shLow

    Label("ЩИТ", "D6_CockSmall", s1x, PY + 5, COL.dim)
    Bar(s1x, PY + 16, barW, 8, shFrac, shCol, Color(10,20,30,200), true)
    Label(tostring(math.floor(shield)) .. " / " .. tostring(math.floor(shieldMax)),
        "D6_CockSmall", s1x + barW*0.5, PY + 17, COL.white, TEXT_ALIGN_CENTER, TEXT_ALIGN_TOP)

    Label("КОРПУС", "D6_CockSmall", s1x, PY + 28, COL.dim)
    Bar(s1x, PY + 39, barW, 8, hpFrac, hpColor, Color(10,20,10,200), true)
    Label(tostring(hp) .. " / " .. tostring(hpMax),
        "D6_CockSmall", s1x + barW*0.5, PY + 40, COL.white, TEXT_ALIGN_CENTER, TEXT_ALIGN_TOP)

    -- Число щита крупно
    Label(tostring(math.floor(shield)), "D6_CockBig",
        s1x + barW*0.5, PY + 52, shCol, TEXT_ALIGN_CENTER, TEXT_ALIGN_TOP)

    -- Вспышка повреждения: красная рамка экрана
    if CurTime() < DmgFlash then
        local alpha = math.Clamp((DmgFlash - CurTime()) / 0.35, 0, 1) * 160
        surface.SetDrawColor(255, 40, 40, alpha)
        surface.DrawOutlinedRect(0, 0, sw, sh, 6)
    end

    -- ===== СЕКЦИЯ 2: Энергия гравирельсы =====
    local s2x = math.floor(sectionW) + 8
    local wep  = ply:GetActiveWeapon()
    local railEnergy   = 100
    local railEnergyMax = 100
    local heldEnt = NULL

    if IsValid(wep) and wep:GetClass() == "weapon_d6_gravy_railgun" then
        railEnergy = wep:GetNWInt("D6_RailEnergy", 100)
        heldEnt    = wep:GetNWEntity("D6_RailHeld", NULL)
    end

    local eFrac = railEnergy / railEnergyMax
    local eCol  = eFrac > 0.5 and COL.energy
               or eFrac > 0.2 and Color(255, 180, 0, 255)
               or Color(255, 60, 40, 255)

    Label("ЭНЕРГИЯ", "D6_CockSmall", s2x, PY + 6, COL.dim)
    Bar(s2x, PY + 20, sectionW - 16, 14, eFrac, eCol, Color(5,15,30,200), true)
    Label(tostring(math.floor(railEnergy)) .. " / " .. tostring(railEnergyMax),
        "D6_CockSmall", s2x + (sectionW-16)*0.5, PY + 22, COL.white,
        TEXT_ALIGN_CENTER, TEXT_ALIGN_TOP)

    -- Статус захвата
    if IsValid(heldEnt) then
        Label("[ ЗАХВАТ ]", "D6_CockMed",
            s2x + (sectionW-16)*0.5, PY + 40,
            Color(0, 255, 160, 240), TEXT_ALIGN_CENTER, TEXT_ALIGN_TOP)
    else
        Label(tostring(math.floor(railEnergy)), "D6_CockBig",
            s2x + (sectionW-16)*0.5, PY + 38, eCol,
            TEXT_ALIGN_CENTER, TEXT_ALIGN_TOP)
    end

    -- ===== СЕКЦИЯ 3: Оружие / Режим =====
    local s3x = math.floor(sectionW * 2) + 8
    local D6_WEP_MODE = {
        ["weapon_d6_mg"]=0, ["weapon_d6_plasma"]=1,
        ["weapon_d6_heavy"]=2, ["weapon_d6_laser"]=3,
        ["weapon_d6_rockets"]=4,
    }
    local mode = IsValid(wep) and D6_WEP_MODE[wep:GetClass()] or -1
    local modeCol  = COL.mode[mode] or COL.text
    local modeName = MNAM[mode] or (IsValid(wep) and wep:GetPrintName() or "—")

    Label("ОРУЖИЕ", "D6_CockSmall", s3x, PY + 6, COL.dim)
    Label(modeName, "D6_CockBig",
        s3x + (sectionW-16)*0.5, PY + 20, modeCol,
        TEXT_ALIGN_CENTER, TEXT_ALIGN_TOP)

    -- Индикаторы патронов (если есть)
    local clip1 = IsValid(wep) and wep:Clip1() or -1
    local clip2 = IsValid(wep) and wep:Clip2() or -1
    local ammo1 = IsValid(wep) and ply:GetAmmoCount(wep:GetPrimaryAmmoType()) or 0
    if clip1 >= 0 then
        Label(tostring(clip1) .. " | " .. tostring(ammo1), "D6_CockMed",
            s3x + (sectionW-16)*0.5, PY + 52, COL.text,
            TEXT_ALIGN_CENTER, TEXT_ALIGN_TOP)
    end

    -- ===== СЕКЦИЯ 4: Скорость / Крен =====
    local s4x = math.floor(sectionW * 3) + 8
    -- D6Ang задаётся в d6_client.lua
    local D6Ang  = ply.D6Ang
    local D6Vel  = ply:GetVelocity()
    local speed  = D6Vel:Length()
    local roll   = D6Ang and D6Ang.r or 0

    Label("СКОРОСТЬ", "D6_CockSmall", s4x, PY + 6, COL.dim)
    Label(string.format("%d", math.floor(speed)), "D6_CockBig",
        s4x + (sectionW-16)*0.5 - 4, PY + 20, COL.speed,
        TEXT_ALIGN_CENTER, TEXT_ALIGN_TOP)
    Label("юн/с", "D6_CockSmall",
        s4x + (sectionW-16)*0.5 + 28, PY + 30, COL.dim,
        TEXT_ALIGN_LEFT, TEXT_ALIGN_TOP)

    -- Индикатор крена
    local rollAbs = math.abs(roll)
    local rollCol = rollAbs < 20 and COL.shHigh
                 or rollAbs < 70 and COL.shMid
                 or COL.shLow
    Label(string.format("КРЕН %+.0f°", roll), "D6_CockSmall",
        s4x + (sectionW-16)*0.5, PY + 50, rollCol,
        TEXT_ALIGN_CENTER, TEXT_ALIGN_TOP)

    -- Горизонтальный индикатор крена
    local rollBarW = sectionW - 24
    local rollBarX = s4x + 2
    local rollBarY = PY + 64
    surface.SetDrawColor(COL.border2)
    surface.DrawRect(rollBarX, rollBarY, rollBarW, 5)
    local mid      = rollBarX + rollBarW * 0.5
    local rollOff  = (roll / 180) * rollBarW * 0.5
    local dotX     = math.Clamp(mid + rollOff, rollBarX, rollBarX + rollBarW) - 3
    surface.SetDrawColor(rollCol)
    surface.DrawRect(dotX, rollBarY - 1, 6, 7)
    surface.SetDrawColor(COL.border2)
    surface.DrawLine(mid, rollBarY - 1, mid, rollBarY + 6)  -- центральная метка

    -- Маркер сноса: куда дрон фактически летит vs. куда смотрит
    if speed > 80 and D6Ang then
        local velDir = D6Vel:GetNormalized()
        local driftR = velDir:Dot(D6Ang:Right())
        local driftU = velDir:Dot(D6Ang:Up())
        local cx2, cy2 = sw * 0.5, sh * 0.5
        local scale = 60
        local mx = cx2 + driftR * scale
        local my = cy2 - driftU * scale
        local alpha = math.Clamp((speed - 80) / 200, 0, 1) * 200
        surface.SetDrawColor(0, 255, 180, alpha)
        surface.DrawLine(mx - 5, my - 5, mx + 5, my - 5)
        surface.DrawLine(mx + 5, my - 5, mx + 5, my + 5)
        surface.DrawLine(mx + 5, my + 5, mx - 5, my + 5)
        surface.DrawLine(mx - 5, my + 5, mx - 5, my - 5)
        surface.DrawLine(cx2, cy2, mx, my)
    end

    -- ── Верхняя тонкая полоска-граница ───────────────────
    surface.SetDrawColor(COL.border)
    surface.DrawLine(0, PY, PW, PY)

    -- ── Центральный маленький индикатор (над HUD) ─────────
    local ci_y = PY - 20
    if IsValid(ply) and ply:GetNWBool("D6AlwaysRun", false) then
        Label("▶▶ ALWAYS-RUN", "D6_CockSmall",
            sw * 0.5, ci_y, Color(255, 200, 0, 220),
            TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end

    -- Виньетка перегрузки: тёмные края при высокой скорости
    local vignAlpha = math.Clamp((speed - 1400) / 800, 0, 1) * 55
    if ply:GetNWBool("D6AlwaysRun", false) then vignAlpha = vignAlpha + 20 end
    if vignAlpha > 2 then
        surface.SetDrawColor(0, 0, 0, vignAlpha)
        local thick = math.floor(sw * 0.08)
        surface.DrawRect(0,      0,      thick,          sh)
        surface.DrawRect(sw - thick, 0,  thick,          sh)
        surface.DrawRect(thick,  0,      sw - thick * 2, thick)
        surface.DrawRect(thick,  sh - thick, sw - thick * 2, thick)
    end
end)

-- ── Скрываем стандартный HUD инвентаря в D6-режиме ───────
hook.Add("HUDShouldDraw", "D6_CockHideDefaultHUD", function(name)
    local ply = LocalPlayer()
    if not IsValid(ply) or not ply:GetNWBool("D6On", false) then return end
    -- Скрываем только стандартные элементы, которые дублирует кокпит
    if name == "CHudHealth"
    or name == "CHudBattery"
    or name == "CHudAmmo"
    or name == "CHudSecondaryAmmo" then
        return false
    end
end)

print("[D6] d6_cockpit.lua OK")
