-- =========================================================
-- d6_rearview.lua — Вид сзади (Descent-стиль), CLIENT
--
--   Маленькое окно в верхней части экрана с видом сзади —
--   как в оригинальном Descent.
--
--   Управление:
--     V         — переключить (только в D6-режиме)
--     concommand d6_rearview_toggle  — то же самое
--     convar     d6_rearview  0/1    — прямое управление
--
--   Реализация:
--     PreDrawEffects → render.RenderView в render target →
--     HUDPaint рисует RT как текстурированный прямоугольник
--     с рамкой в стиле Descent.
-- =========================================================
if not CLIENT then return end

-- ── Настройки отображения ────────────────────────────────

local RT_W, RT_H = 320, 180           -- разрешение RT
local DISP_W     = 320                -- ширина окна на экране
local DISP_H     = 180                -- высота окна на экране
local MARGIN_TOP = 6                  -- отступ от верха экрана

local COL_BORDER  = Color(0, 220, 130, 255)
local COL_BORDER2 = Color(0, 160, 100, 180)
local COL_LABEL   = Color(0, 255, 160, 230)
local COL_BG      = Color(0,   0,   0, 210)
local COL_SCAN    = Color(0, 200, 120,  12)  -- строчная развёртка

-- ── ConVar и RT ──────────────────────────────────────────

local cv = CreateClientConVar("d6_rearview", "0", true, false,
    "Вид сзади в стиле Descent (0/1)")

local rv_rt   = nil
local rv_mat  = nil
local rv_busy = false    -- защита от рекурсии при render.RenderView

local function InitRT()
    rv_rt = GetRenderTargetEx(
        "d6_rearview_rt", RT_W, RT_H,
        RT_SIZE_NO_CHANGE,
        MATERIAL_RT_DEPTH_SEPARATE,
        4 + 8, 0,
        IMAGE_FORMAT_RGB888
    )
    if not rv_rt then
        MsgC(Color(255,100,0), "[D6] d6_rearview: не удалось создать RT\n")
        return
    end
    rv_mat = CreateMaterial("d6_rearviewmat_" .. tostring(SysTime()):sub(-6),
        "UnlitGeneric", {
            ["$basetexture"]  = rv_rt:GetName(),
            ["$nolod"]        = "1",
            ["$ignorez"]      = "1",
            ["$vertexcolor"]  = "1",
            ["$vertexalpha"]  = "1",
        })
end

hook.Add("InitPostEntity", "D6_RearView_Init", InitRT)

-- ── Рендер в RT ──────────────────────────────────────────
-- PreDrawEffects вызывается во время 3D-прохода. Внутри
-- render.RenderView снова вызовет этот хук → rv_busy блокирует рекурсию.

hook.Add("PreDrawEffects", "D6_RearView_Render", function()
    if rv_busy then return end
    if not cv:GetBool() then return end
    if not rv_rt or not rv_mat then return end

    local ply = LocalPlayer()
    if not IsValid(ply)
    or not ply:GetNWBool("D6On", false)
    or not ply:Alive() then return end

    -- Угол «назад»: разворот по рысканью на 180°,
    -- тангаж и крен сохраняются (корабль не переворачивается).
    local eyeAng = ply.D6Ang or ply:EyeAngles()
    local rearAng = Angle(eyeAng.p, eyeAng.y + 180, eyeAng.r)

    rv_busy = true
    render.PushRenderTarget(rv_rt)
        render.Clear(0, 0, 0, 255, true, true)
        render.RenderView({
            origin        = ply:EyePos(),
            angles        = rearAng,
            fov           = 95,
            x = 0, y = 0,
            w = RT_W, h = RT_H,
            drawviewmodel = false,
            drawmonitors  = true,
            drawhud       = false,
        })
    render.PopRenderTarget()
    rv_busy = false
end)

-- ── Отрисовка HUD ─────────────────────────────────────────

hook.Add("HUDPaint", "D6_RearView_HUD", function()
    if not cv:GetBool() then return end
    if not rv_mat then return end

    local ply = LocalPlayer()
    if not IsValid(ply)
    or not ply:GetNWBool("D6On", false)
    or not ply:Alive() then return end

    local sw  = ScrW()
    local dx  = math.floor((sw - DISP_W) * 0.5)
    local dy  = MARGIN_TOP

    -- Фон
    surface.SetDrawColor(COL_BG)
    surface.DrawRect(dx, dy, DISP_W, DISP_H)

    -- Изображение RT
    surface.SetMaterial(rv_mat)
    surface.SetDrawColor(255, 255, 255, 255)
    surface.DrawTexturedRect(dx, dy, DISP_W, DISP_H)

    -- Строчная развёртка (ретро-эффект)
    surface.SetDrawColor(COL_SCAN)
    for row = 0, DISP_H - 1, 2 do
        surface.DrawRect(dx, dy + row, DISP_W, 1)
    end

    -- Рамка — внешняя
    surface.SetDrawColor(COL_BORDER2)
    surface.DrawOutlinedRect(dx - 2, dy - 2, DISP_W + 4, DISP_H + 4, 1)

    -- Рамка — внутренняя (ярче)
    surface.SetDrawColor(COL_BORDER)
    surface.DrawOutlinedRect(dx, dy, DISP_W, DISP_H, 2)

    -- Угловые декорации в стиле Descent
    local cs = 10
    local bx2, by2 = dx + DISP_W - 1, dy + DISP_H - 1
    surface.SetDrawColor(COL_BORDER)
    -- верхний-левый
    surface.DrawRect(dx,       dy,       cs, 2)
    surface.DrawRect(dx,       dy,       2,  cs)
    -- верхний-правый
    surface.DrawRect(bx2-cs+1, dy,       cs, 2)
    surface.DrawRect(bx2-1,    dy,       2,  cs)
    -- нижний-левый
    surface.DrawRect(dx,       by2-1,    cs, 2)
    surface.DrawRect(dx,       by2-cs+1, 2,  cs)
    -- нижний-правый
    surface.DrawRect(bx2-cs+1, by2-1,    cs, 2)
    surface.DrawRect(bx2-1,    by2-cs+1, 2,  cs)

    -- Подпись
    draw.SimpleTextOutlined("ВИД СЗАДИ", "DermaDefault",
        dx + DISP_W * 0.5, dy + DISP_H - 11,
        COL_LABEL, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER,
        1, Color(0, 0, 0, 220))

    -- Подсказка клавиши (маленький текст справа снизу)
    draw.SimpleTextOutlined("[V]", "DermaDefault",
        dx + DISP_W - 4, dy + DISP_H - 11,
        Color(0, 180, 100, 160), TEXT_ALIGN_RIGHT, TEXT_ALIGN_CENTER,
        1, Color(0, 0, 0, 120))
end)

-- ── Команды ──────────────────────────────────────────────

concommand.Add("d6_rearview_toggle", function()
    local new = cv:GetBool() and "0" or "1"
    RunConsoleCommand("d6_rearview", new)
    chat.AddText(Color(0, 220, 130), "[6DOF] Вид сзади: ",
        color_white, new == "1" and "ВКЛ" or "ВЫКЛ")
end)

-- Клавиша V (только когда игрок в D6-режиме)
hook.Add("PlayerButtonDown", "D6_RearView_Key", function(ply, key)
    if key ~= KEY_V then return end
    if not IsValid(ply) or ply ~= LocalPlayer() then return end
    if not ply:GetNWBool("D6On", false) then return end
    RunConsoleCommand("d6_rearview_toggle")
end)

print("[D6] d6_rearview.lua OK  (V = вид сзади)")
