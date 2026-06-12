-- =========================================================
-- DESCENT 6DOF — d6_menu.lua
-- Меню в стиле CS 1.6: клавиша TAB открывает/закрывает.
-- Три вкладки: КОМАНДЫ / НАСТРОЙКИ / МОДИФИКАЦИИ
--
-- ИСПРАВЛЕНО:
--   - удалён мёртвый SERVER-блок (файл CLIENT-only)
--   - добавлен concommand d6_radar_toggle
--   - hover команд обновляется при движении мыши (GUI.MouseMoved)
--   - дублирующий d6_set убран (он зарегистрирован в d6_core.lua)
-- =========================================================
if not CLIENT then return end

-- =========================================================
-- КОНСТАНТЫ СТИЛЯ  (палитра CS 1.6)
-- =========================================================
local C = {
    bg       = Color(0,   0,   0,   210),
    bgDark   = Color(0,   0,   0,   240),
    border   = Color(80,  200, 80,  255),
    border2  = Color(40,  120, 40,  180),
    title    = Color(80,  200, 80,  255),
    text     = Color(200, 200, 200, 255),
    textDim  = Color(140, 140, 140, 200),
    hot      = Color(255, 255, 80,  255),
    hotBg    = Color(60,  60,  0,   180),
    btn      = Color(20,  60,  20,  220),
    btnHover = Color(40,  100, 40,  240),
    btnPress = Color(80,  180, 80,  255),
    sliderBg = Color(20,  40,  20,  200),
    sliderFg = Color(80,  200, 80,  255),
    modBg    = Color(10,  20,  10,  230),
    modText  = Color(160, 255, 120, 255),
    warn     = Color(255, 80,  80,  255),
    ok       = Color(80,  255, 80,  255),
    tab      = Color(0,   0,   0,   200),
    tabAct   = Color(20,  60,  20,  240),
}

surface.CreateFont("D6Menu_Title", {
    font = "Courier New", size = 18, weight = 800, antialias = false })
surface.CreateFont("D6Menu_Item",  {
    font = "Courier New", size = 14, weight = 400, antialias = false })
surface.CreateFont("D6Menu_Small", {
    font = "Courier New", size = 12, weight = 400, antialias = false })
surface.CreateFont("D6Menu_Code",  {
    font = "Courier New", size = 13, weight = 400, antialias = false })

-- =========================================================
-- СОСТОЯНИЕ МЕНЮ
-- =========================================================
local Menu = {
    open         = false,
    tab          = 1,
    hotCmd       = 0,
    hotSet       = 0,
    drag         = nil,
    modText      = "",
    modCursor    = 0,
    modMsg       = "",
    modMsgTimer  = 0,
    modMsgOk     = true,
}

local Cfg = {
    gravity     = 500,
    accel       = 3000,
    maxSpeed    = 1800,
    rollSpeed   = 175,
    fov         = 90,
    thirdPerson = false,
    gunDamage   = 12,
    gunBurst    = 8,
    ramSpeed    = 3400,
    ramDamage   = 240,
}

-- Заводские значения (используются кнопками «Сброс» здесь и в Q-меню)
local DEFAULTS = {
    gravity   = 500,  accel     = 3000, maxSpeed = 1800,
    rollSpeed = 175,  fov       = 90,
    gunDamage = 12,   gunBurst  = 8,
    ramSpeed  = 3400, ramDamage = 240,
}

local function ResetCfg()
    for k, v in pairs(DEFAULTS) do Cfg[k] = v end
end

local CFG_FILE = "d6_config.txt"

local function SaveCfg()
    local lines = {}
    for k, v in pairs(Cfg) do
        table.insert(lines, k .. "=" .. tostring(v))
    end
    file.Write(CFG_FILE, table.concat(lines, "\n"))
end

local function LoadCfg()
    if not file.Exists(CFG_FILE, "DATA") then return end
    local data = file.Read(CFG_FILE, "DATA")
    for line in data:gmatch("[^\n]+") do
        local k, v = line:match("^(.-)=(.+)$")
        if k and Cfg[k] ~= nil then
            local n = tonumber(v)
            if     n         then Cfg[k] = n
            elseif v=="true"  then Cfg[k] = true
            elseif v=="false" then Cfg[k] = false end
        end
    end
end
LoadCfg()

local function ApplyCfg()
    RunConsoleCommand("d6_set", "gravity",   tostring(Cfg.gravity))
    RunConsoleCommand("d6_set", "accel",     tostring(Cfg.accel))
    RunConsoleCommand("d6_set", "maxSpeed",  tostring(Cfg.maxSpeed))
    RunConsoleCommand("d6_set", "ramSpeed",  tostring(Cfg.ramSpeed))
    RunConsoleCommand("d6_set", "ramDamage", tostring(Cfg.ramDamage))
    RunConsoleCommand("d6_set", "gunDamage", tostring(Cfg.gunDamage))
    RunConsoleCommand("d6_set", "gunBurst",  tostring(Cfg.gunBurst))
    D6Menu = D6Menu or {}
    D6Menu.Cfg = Cfg
end

D6Menu = { Cfg = Cfg }

-- =========================================================
-- КОМАНДЫ И НАСТРОЙКИ
-- =========================================================
local COMMANDS = {
    { key="F1", label="Включить/Выключить 6DOF",   cmd="6dof_toggle",          desc="Активировать режим дрона" },
    { key="F2", label="Дать грави-рельсу",          cmd="d6_give_weapons",      desc="Выдать оружие в инвентарь" },
    { key="F3", label="Камера 1-е/3-е лицо",        cmd="d6_cam_toggle",        desc="Переключить вид (F5)" },
    { key="F4", label="Спавн: Боевой отряд",        cmd="6dof_spawn_squad",     desc="Полный отряд врагов" },
    { key="F5", label="Спавн: Рой сканеров",        cmd="6dof_spawn_bestiary",  desc="Все виды врагов" },
    { key="F6", label="Дать патроны (x30)",         cmd="6dof_give_ammo all 30",desc="Пополнить боеприпасы" },
    { key="F7", label="Сброс крена",                cmd="d6_reset_roll",        desc="Выровнять дрон" },
    { key="F8", label="Убить всех NPC",             cmd="d6_kill_npcs",         desc="Очистить карту от врагов" },
    { key="F9", label="Flight Assist ON/OFF",        cmd="6dof_flightassist",    desc="стабилизация / Ньютон" },
}

local SETTINGS = {
    { key="gravity",   label="Гравитация",      min=0,   max=1200, step=10,  fmt="%d" },
    { key="accel",     label="Ускорение",        min=500, max=6000, step=50,  fmt="%d" },
    { key="maxSpeed",  label="Макс. скорость",   min=500, max=4000, step=50,  fmt="%d" },
    { key="rollSpeed", label="Скорость крена",   min=30,  max=360,  step=5,   fmt="%d°/с" },
    { key="fov",       label="FOV камеры",       min=60,  max=130,  step=5,   fmt="%d°" },
    { key="gunDamage", label="Урон пушки",       min=1,   max=100,  step=1,   fmt="%d" },
    { key="gunBurst",  label="Очередь пушки",    min=1,   max=20,   step=1,   fmt="%d пуль" },
    { key="ramSpeed",  label="Скорость тарана",  min=500, max=6000, step=100, fmt="%d" },
    { key="ramDamage", label="Урон тарана",       min=50,  max=500,  step=10,  fmt="%d" },
}

-- ── Экспорт для Q-меню (d6_qmenu.lua) ────────────────────
-- Встроенное меню GMod использует ту же конфигурацию,
-- применение и сохранение, что и это legacy-меню.
D6Menu.Settings = SETTINGS
D6Menu.Defaults = DEFAULTS
D6Menu.Apply    = ApplyCfg
D6Menu.Save     = SaveCfg
D6Menu.Reset    = ResetCfg

-- =========================================================
-- ВСПОМОГАТЕЛЬНЫЙ РЕНДЕР
-- =========================================================
local function DrawPanel(x, y, w, h, bgCol, borderCol, bw)
    bw = bw or 1
    draw.RoundedBox(0, x, y, w, h, bgCol or C.bg)
    surface.SetDrawColor(borderCol or C.border)
    surface.DrawOutlinedRect(x, y, w, h, bw)
end

local function DrawTextShadow(txt, font, x, y, col, ah, av)
    ah = ah or TEXT_ALIGN_LEFT; av = av or TEXT_ALIGN_TOP
    draw.SimpleText(txt, font, x+1, y+1, Color(0,0,0,200), ah, av)
    draw.SimpleText(txt, font, x,   y,   col,               ah, av)
end

local function DrawButton(x, y, w, h, keyStr, labelStr, hot)
    DrawPanel(x, y, w, h, hot and C.btnHover or C.btn, hot and C.border or C.border2, 1)
    surface.SetDrawColor(C.border2); surface.DrawRect(x, y, 36, h)
    surface.SetDrawColor(C.border);  surface.DrawLine(x+36, y, x+36, y+h)
    DrawTextShadow(keyStr, "D6Menu_Item",
        x+18, y+h*0.5, C.border, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    DrawTextShadow(labelStr, "D6Menu_Item",
        x+44, y+h*0.5, hot and C.hot or C.text, TEXT_ALIGN_LEFT, TEXT_ALIGN_CENTER)
end

local function DrawSlider(x, y, w, h, setting, hot)
    local val  = Cfg[setting.key]
    local frac = math.Clamp((val-setting.min)/(setting.max-setting.min), 0, 1)
    DrawPanel(x, y, w, h, hot and C.btnHover or C.btn, hot and C.border or C.border2)
    DrawTextShadow(setting.label, "D6Menu_Item",
        x+6, y+h*0.5, hot and C.hot or C.text, TEXT_ALIGN_LEFT, TEXT_ALIGN_CENTER)
    DrawTextShadow(string.format(setting.fmt or "%s", val), "D6Menu_Item",
        x+w-6, y+h*0.5, C.hot, TEXT_ALIGN_RIGHT, TEXT_ALIGN_CENTER)
    local trackX = x+160; local trackW = w-200; local trackH = 8
    local trackY = y+(h-trackH)*0.5
    surface.SetDrawColor(C.sliderBg); surface.DrawRect(trackX, trackY, trackW, trackH)
    surface.SetDrawColor(C.sliderFg); surface.DrawRect(trackX, trackY, trackW*frac, trackH)
    local hx = trackX + trackW*frac - 4
    surface.SetDrawColor(C.border);   surface.DrawRect(hx, trackY-3, 8, trackH+6)
    surface.SetDrawColor(hot and C.hot or C.text)
    surface.DrawOutlinedRect(hx, trackY-3, 8, trackH+6, 1)
    return trackX, trackY, trackW, trackH
end

-- =========================================================
-- ГЛАВНАЯ ОТРИСОВКА
-- =========================================================
local function DrawMenu()
    if not Menu.open then return end
    local sw, sh = ScrW(), ScrH()
    local pw, ph = 620, 520
    local px = (sw-pw)*0.5
    local py = (sh-ph)*0.5

    surface.SetDrawColor(0,0,0,120); surface.DrawRect(0,0,sw,sh)
    DrawPanel(px, py, pw, ph, C.bgDark, C.border, 2)
    surface.SetDrawColor(C.border2)
    surface.DrawLine(px+3,py+3,px+3,py+ph-3)
    surface.DrawLine(px+pw-4,py+3,px+pw-4,py+ph-3)

    local titleH = 36
    DrawPanel(px, py, pw, titleH, Color(0,30,0,255), C.border, 2)
    DrawTextShadow("▸ DESCENT 6DOF", "D6Menu_Title",
        px+14, py+titleH*0.5, C.title, TEXT_ALIGN_LEFT, TEXT_ALIGN_CENTER)
    DrawTextShadow("v5.1", "D6Menu_Small",
        px+pw-10, py+titleH*0.5, C.textDim, TEXT_ALIGN_RIGHT, TEXT_ALIGN_CENTER)
    DrawTextShadow("[KP0] закрыть", "D6Menu_Small",
        px+pw*0.5, py+titleH*0.5, C.textDim, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)

    local tabY = py+titleH; local tabH = 28
    local tabs  = {"◉ КОМАНДЫ","⚙ НАСТРОЙКИ","⌨ МОДИФИКАЦИИ","⚡ ЭНЕРГИЯ"}
    local tabW  = pw/#tabs
    for i, name in ipairs(tabs) do
        local tx  = px+(i-1)*tabW
        local act = (i == Menu.tab)
        DrawPanel(tx, tabY, tabW, tabH, act and C.tabAct or C.tab, act and C.border or C.border2, 1)
        DrawTextShadow(name, "D6Menu_Item",
            tx+tabW*0.5, tabY+tabH*0.5,
            act and C.hot or C.textDim, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end

    local cx = px+4; local cy = tabY+tabH+4
    local cw = pw-8; local ch = ph-titleH-tabH-28

    -- ══ Вкладка 1: КОМАНДЫ ══════════════════════════════
    if Menu.tab == 1 then
        local itemH = 38; local gap = 4
        local visN  = math.floor(ch/(itemH+gap))
        for i, cmd in ipairs(COMMANDS) do
            if i > visN then break end
            local iy  = cy+(i-1)*(itemH+gap)
            local hot = (Menu.hotCmd == i)
            DrawButton(cx, iy, cw-4, itemH, cmd.key, cmd.label, hot)
            if cmd.desc then
                DrawTextShadow(cmd.desc, "D6Menu_Small",
                    cx+cw-8, iy+itemH*0.5, C.textDim, TEXT_ALIGN_RIGHT, TEXT_ALIGN_CENTER)
            end
        end
        DrawTextShadow("↑↓ навигация  Enter выполнить  Fn-клавиши напрямую",
            "D6Menu_Small", px+pw*0.5, py+ph-14, C.textDim, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)

        -- Сообщение о выполнении команды
        if CurTime() < Menu.modMsgTimer then
            DrawTextShadow(Menu.modMsg, "D6Menu_Item",
                cx+cw-8, cy+2, Menu.modMsgOk and C.ok or C.warn,
                TEXT_ALIGN_RIGHT, TEXT_ALIGN_TOP)
        end

    -- ══ Вкладка 2: НАСТРОЙКИ ═════════════════════════════
    elseif Menu.tab == 2 then
        local itemH = 36; local gap = 3
        for i, s in ipairs(SETTINGS) do
            local iy  = cy+(i-1)*(itemH+gap)
            DrawSlider(cx, iy, cw-4, itemH, s, Menu.hotSet == i)
        end
        local btnY = cy+#SETTINGS*(itemH+gap)+6
        DrawPanel(cx, btnY, (cw-8)*0.5, 28, C.btn, C.border, 1)
        DrawTextShadow("  ПРИМЕНИТЬ [Enter]", "D6Menu_Item",
            cx+4, btnY+14, C.ok, TEXT_ALIGN_LEFT, TEXT_ALIGN_CENTER)
        DrawPanel(cx+(cw-8)*0.5+8, btnY, (cw-8)*0.5, 28, C.btn, C.border2, 1)
        DrawTextShadow("  СБРОС [Delete]", "D6Menu_Item",
            cx+(cw-8)*0.5+12, btnY+14, C.warn, TEXT_ALIGN_LEFT, TEXT_ALIGN_CENTER)
        DrawTextShadow("← → изменить  Enter применить  Del сброс",
            "D6Menu_Small", px+pw*0.5, py+ph-14, C.textDim, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)

    -- ══ Вкладка 3: МОДИФИКАЦИИ ════════════════════════════
    elseif Menu.tab == 3 then
        DrawTextShadow("── Команда (только d6_* или 6dof_*) ──",
            "D6Menu_Small", cx+4, cy+4, C.textDim)
        local edY = cy+22; local edH = ch-80
        DrawPanel(cx, edY, cw-4, edH, C.modBg, C.border2, 1)

        local lines = {}; local curLine = ""
        for c in Menu.modText:gmatch(".") do
            if c == "\n" then table.insert(lines, curLine); curLine = ""
            else curLine = curLine .. c end
        end
        table.insert(lines, curLine)

        local lineH   = 16
        local visLines = math.floor((edH-8)/lineH)
        for ln, line in ipairs(lines) do
            if ln > visLines then break end
            local ly = edY+4+(ln-1)*lineH
            DrawTextShadow(string.format("%3d", ln), "D6Menu_Code", cx+2, ly, C.textDim)
            DrawTextShadow(line, "D6Menu_Code", cx+34, ly, C.modText)
        end

        if math.floor(CurTime()*2)%2 == 0 then
            local cursorLine = 1
            local tmp = Menu.modText:sub(1, Menu.modCursor)
            for _ in tmp:gmatch("\n") do cursorLine = cursorLine+1 end
            local lastNL   = tmp:find("\n[^\n]*$") or 0
            local cursorCol = #tmp - lastNL
            surface.SetDrawColor(C.border)
            surface.DrawRect(cx+34+cursorCol*8, edY+4+(cursorLine-1)*lineH, 2, lineH)
        end

        local btnY = edY+edH+4
        DrawPanel(cx, btnY, 120, 26, C.btn, C.border, 1)
        DrawTextShadow(" ▶ ПРИМЕНИТЬ", "D6Menu_Item", cx+6, btnY+13, C.ok, TEXT_ALIGN_LEFT, TEXT_ALIGN_CENTER)
        DrawPanel(cx+128, btnY, 90, 26, C.btn, C.border2, 1)
        DrawTextShadow(" ОЧИСТИТЬ", "D6Menu_Item", cx+134, btnY+13, C.warn, TEXT_ALIGN_LEFT, TEXT_ALIGN_CENTER)
        DrawPanel(cx+226, btnY, 140, 26, C.btn, C.border2, 1)
        DrawTextShadow(" ПРИМЕРЫ ▾", "D6Menu_Item", cx+232, btnY+13, C.textDim, TEXT_ALIGN_LEFT, TEXT_ALIGN_CENTER)

        if CurTime() < Menu.modMsgTimer then
            DrawTextShadow(Menu.modMsg, "D6Menu_Item",
                cx+cw-8, btnY+13,
                Menu.modMsgOk and C.ok or C.warn, TEXT_ALIGN_RIGHT, TEXT_ALIGN_CENTER)
        end
        DrawTextShadow("Печатай команду ▸ F9=применить ▸ F10=очистить",
            "D6Menu_Small", px+pw*0.5, py+ph-14, C.textDim, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)

    -- ══ Вкладка 4: ЭНЕРГИЯ ════════════════════════════════
    elseif Menu.tab == 4 then
        local ply2      = LocalPlayer()
        local allocW    = math.Round(ply2:GetNWFloat("D6_AllocWeapons", 34))
        local allocS    = math.Round(ply2:GetNWFloat("D6_AllocShields", 33))
        local allocE    = math.Round(ply2:GetNWFloat("D6_AllocEngines", 33))
        local presetName = ply2:GetNWString("D6_EnergyPreset", "balanced")
        local energy    = ply2:GetNWFloat("D6_Energy",    100)
        local energyMax = ply2:GetNWFloat("D6_EnergyMax", 100)

        DrawTextShadow("── Распределение энергии ──", "D6Menu_Small", cx+4, cy+4, C.textDim)

        local rowY = cy + 26
        DrawTextShadow(string.format("ОРУЖИЕ:   %d%%", allocW), "D6Menu_Item",
            cx+10, rowY,     Color(255,200,80,255))
        DrawTextShadow(string.format("ЩИТЫ:     %d%%", allocS), "D6Menu_Item",
            cx+10, rowY+24,  Color(0,200,255,255))
        DrawTextShadow(string.format("ДВИЖКИ:   %d%%", allocE), "D6Menu_Item",
            cx+10, rowY+48,  Color(100,255,100,255))

        DrawTextShadow("Пресет: " .. string.upper(presetName), "D6Menu_Item",
            cx+10, rowY+80, C.text)
        DrawTextShadow(string.format("Энергия: %d / %d", math.floor(energy), math.floor(energyMax)),
            "D6Menu_Item", cx+10, rowY+104, Color(0,180,255,255))

        -- Кнопки пресетов
        local presets = (D6_Energy and D6_Energy.PresetOrder)
                     or {"balanced","assault","interceptor","siege"}
        local btnCount = #presets
        local btnW  = (cw-8) / btnCount
        local btnH  = 34
        local btnY  = cy + 160
        for i, pname in ipairs(presets) do
            local bx      = cx + (i-1)*btnW
            local isActive = (presetName == pname)
            DrawPanel(bx, btnY, btnW-4, btnH,
                isActive and C.tabAct or C.btn,
                isActive and C.border  or C.border2, 1)
            DrawTextShadow(string.upper(pname), "D6Menu_Item",
                bx + (btnW-4)*0.5, btnY + btnH*0.5,
                isActive and C.hot or C.textDim, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
        end

        DrawTextShadow("Клик или [1-4] для смены пресета",
            "D6Menu_Small", px+pw*0.5, py+ph-14, C.textDim, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end

    -- Нижняя строка статуса
    local statusY = py+ph-22
    surface.SetDrawColor(C.border2); surface.DrawLine(px+4, statusY, px+pw-4, statusY)
    local ply  = LocalPlayer()
    local d6   = IsValid(ply) and ply:GetNWBool("D6On", false)
    local wep  = IsValid(ply) and ply:GetActiveWeapon()
    local D6_WEP_MODE = {
        ["weapon_d6_mg"]=0, ["weapon_d6_plasma"]=1,
        ["weapon_d6_heavy"]=2, ["weapon_d6_laser"]=3,
        ["weapon_d6_rockets"]=4,
    }
    local mode = IsValid(wep) and D6_WEP_MODE[wep:GetClass()] or -1
    local modeNames = {[0]="ПУЛЕМЁТ",[1]="ПЛАЗМА",[2]="ТЯЖЁЛЫЙ",[3]="ЛАЗЕР",[4]="РАКЕТЫ"}
    DrawTextShadow(
        string.format("6DOF: %s  |  Режим: %s  |  [1]Команды [2]Настройки [3]Моды [4]Энергия",
            d6 and "ON" or "OFF",
            mode >= 0 and (modeNames[mode] or "?") or "—"),
        "D6Menu_Small", px+pw*0.5, statusY+11, C.textDim, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
end

-- =========================================================
-- ПРЕСЕТЫ КОДА
-- =========================================================
local MOD_EXAMPLES = {
    { name="Сверх-скорость",   code="d6_set maxSpeed 4000" },
    { name="Тихий режим",      code="d6_set gravity 0" },
    { name="Боевые настройки", code="d6_set gunDamage 60" },
    { name="Сброс всего",      code="d6_set gravity 500" },
}
local showExamples = false

-- =========================================================
-- ПРИМЕНИТЬ КОМАНДУ ИЗ ВКЛАДКИ 3
-- =========================================================
local function ApplyMod()
    local cmd = Menu.modText:match("^%s*(.-)%s*$")
    if cmd == "" then
        Menu.modMsg="✗ Поле пустое"; Menu.modMsgOk=false; Menu.modMsgTimer=CurTime()+3; return
    end
    if not (cmd:match("^d6_") or cmd:match("^6dof_")) then
        Menu.modMsg="✗ Разрешены только d6_* и 6dof_* команды"
        Menu.modMsgOk=false; Menu.modMsgTimer=CurTime()+4; return
    end
    local parts={}; for p in cmd:gmatch("%S+") do parts[#parts+1]=p end
    if     #parts>=3 then RunConsoleCommand(parts[1],parts[2],parts[3])
    elseif #parts==2 then RunConsoleCommand(parts[1],parts[2])
    elseif #parts==1 then RunConsoleCommand(parts[1]) end
    Menu.modMsg="✓ Отправлено: "..cmd; Menu.modMsgOk=true; Menu.modMsgTimer=CurTime()+3
end

-- =========================================================
-- ВЫПОЛНИТЬ КОМАНДУ (вкладка 1)
-- =========================================================
local function ExecCommand(idx)
    local cmd = COMMANDS[idx]; if not cmd then return end
    local parts={}; for p in cmd.cmd:gmatch("%S+") do table.insert(parts,p) end
    if     #parts==1 then RunConsoleCommand(parts[1])
    elseif #parts==2 then RunConsoleCommand(parts[1],parts[2])
    elseif #parts>=3 then RunConsoleCommand(parts[1],parts[2],parts[3]) end
    Menu.modMsg="► "..cmd.label; Menu.modMsgOk=true; Menu.modMsgTimer=CurTime()+1.5
    chat.AddText(Color(80,200,80),"[D6] ",color_white,cmd.label)
end

-- =========================================================
-- ОТКРЫТИЕ / ЗАКРЫТИЕ
-- =========================================================
local function OpenMenu()
    Menu.open = true
    gui.EnableScreenClicker(true)
end
local function CloseMenu()
    Menu.open = false
    gui.EnableScreenClicker(false)
    showExamples = false
end

concommand.Add("d6_menu_open", function()
    if Menu.open then CloseMenu() else OpenMenu() end
end)

-- =========================================================
-- КЛАВИАТУРА
-- =========================================================
-- TAB больше не перехватывается: все функции перенесены во
-- встроенное меню GMod (Q → вкладка «DRMD 6DOF», d6_qmenu.lua).
-- Legacy-меню осталось доступно через d6_menu_open и Numpad 0.

hook.Add("PlayerButtonDown", "D6Menu_Keys", function(key)
    if not Menu.open then return end

    if key == KEY_1 then Menu.tab=1; return true end
    if key == KEY_2 then Menu.tab=2; return true end
    if key == KEY_3 then Menu.tab=3; return true end
    if key == KEY_4 then Menu.tab=4; return true end

    if Menu.tab == 1 then
        if key==KEY_UP    then Menu.hotCmd=math.max(1,(Menu.hotCmd or 1)-1)
        elseif key==KEY_DOWN  then Menu.hotCmd=math.min(#COMMANDS,(Menu.hotCmd or 0)+1)
        elseif key==KEY_ENTER then if Menu.hotCmd>0 then ExecCommand(Menu.hotCmd) end end
        local fkeys={KEY_F1,KEY_F2,KEY_F3,KEY_F4,KEY_F5,KEY_F6,KEY_F7,KEY_F8}
        for i,fk in ipairs(fkeys) do if key==fk then ExecCommand(i); return true end end

    elseif Menu.tab == 2 then
        if     key==KEY_UP    then Menu.hotSet=math.max(1,(Menu.hotSet or 1)-1)
        elseif key==KEY_DOWN  then Menu.hotSet=math.min(#SETTINGS,(Menu.hotSet or 0)+1)
        elseif key==KEY_LEFT  and Menu.hotSet>0 then
            local s=SETTINGS[Menu.hotSet]; if type(Cfg[s.key])=="number" then
                Cfg[s.key]=math.max(s.min, Cfg[s.key]-s.step) end
        elseif key==KEY_RIGHT and Menu.hotSet>0 then
            local s=SETTINGS[Menu.hotSet]; if type(Cfg[s.key])=="number" then
                Cfg[s.key]=math.min(s.max, Cfg[s.key]+s.step) end
        elseif key==KEY_ENTER  then ApplyCfg(); SaveCfg(); chat.AddText(Color(80,200,80),"[D6] Настройки применены.")
        elseif key==KEY_DELETE then
            ResetCfg()
            chat.AddText(Color(255,150,80),"[D6] Настройки сброшены.")
        end

    elseif Menu.tab == 3 then
        if key==KEY_F9  then ApplyMod(); return true end
        if key==KEY_F10 then Menu.modText=""; Menu.modCursor=0; return true end
    end

    return true
end)

-- =========================================================
-- ВВОД ТЕКСТА (вкладка 3)
-- =========================================================
hook.Add("GUI.KeyCodeTyped", "D6Menu_Type", function(key)
    if not Menu.open or Menu.tab~=3 then return end
    local ch = input.GetKeyName(key)
    if not ch then return end
    if     key==KEY_ENTER    then Menu.modText=Menu.modText.."\n"; Menu.modCursor=#Menu.modText
    elseif key==KEY_BACKSPACE then
        if #Menu.modText>0 then Menu.modText=Menu.modText:sub(1,-2); Menu.modCursor=#Menu.modText end
    elseif #ch==1 then Menu.modText=Menu.modText..ch; Menu.modCursor=#Menu.modText end
end)

-- =========================================================
-- КЛИК МЫШИ — вкладки и кнопки
-- =========================================================
local function GetMenuLayout()
    local sw,sh = ScrW(),ScrH()
    local pw,ph = 620,520
    return (sw-pw)*0.5, (sh-ph)*0.5, pw, ph
end

hook.Add("GUI.MousePressed", "D6Menu_Click", function(btn)
    if not Menu.open or btn~=MOUSE_LEFT then return end
    local mx,my = gui.MousePos()
    local px,py,pw,ph = GetMenuLayout()

    local tabY=py+36; local tabH=28; local tabW=pw/4
    if my>=tabY and my<=tabY+tabH then
        for i=1,4 do
            local tx=px+(i-1)*tabW
            if mx>=tx and mx<=tx+tabW then Menu.tab=i; return end
        end
    end

    local cx=px+4; local cy=tabY+tabH+4; local cw=pw-8

    if Menu.tab==1 then
        local itemH=38; local gap=4
        for i=1,#COMMANDS do
            local iy=cy+(i-1)*(itemH+gap)
            if mx>=cx and mx<=cx+cw-4 and my>=iy and my<=iy+itemH then
                Menu.hotCmd=i; ExecCommand(i); return
            end
        end
    end

    if Menu.tab==2 then
        local sItemH=36; local sGap=3
        for i,s in ipairs(SETTINGS) do
            local iy=cy+(i-1)*(sItemH+sGap)
            if mx>=cx and mx<=cx+cw-4 and my>=iy and my<=iy+sItemH then
                Menu.hotSet=i
                local trackX=cx+160; local trackW=cw-204
                if mx>=trackX and mx<=trackX+trackW then
                    local frac=math.Clamp((mx-trackX)/trackW,0,1)
                    local raw=s.min+(s.max-s.min)*frac
                    Cfg[s.key]=math.Clamp(math.floor(raw/s.step+0.5)*s.step, s.min, s.max)
                end
                return
            end
        end
        local btnY=cy+#SETTINGS*(sItemH+sGap)+6
        if mx>=cx and mx<=cx+(cw-8)*0.5 and my>=btnY and my<=btnY+28 then
            ApplyCfg(); SaveCfg(); chat.AddText(Color(80,200,80),"[D6] Настройки применены."); return
        end
        local rx=cx+(cw-8)*0.5+8
        if mx>=rx and mx<=rx+(cw-8)*0.5 and my>=btnY and my<=btnY+28 then
            ResetCfg()
            chat.AddText(Color(255,150,80),"[D6] Сброс."); return
        end
    end

    if Menu.tab==3 then
        local edH=(ph-36-28-4)-80; local edY=cy+22; local btnY=edY+edH+4
        if mx>=cx and mx<=cx+120 and my>=btnY and my<=btnY+26 then ApplyMod(); return end
        if mx>=cx+128 and mx<=cx+218 and my>=btnY and my<=btnY+26 then
            Menu.modText=""; Menu.modCursor=0; return end
        if mx>=cx+226 and mx<=cx+366 and my>=btnY and my<=btnY+26 then
            showExamples=not showExamples; return end
    end

    if Menu.tab==4 then
        local presets  = (D6_Energy and D6_Energy.PresetOrder)
                      or {"balanced","assault","interceptor","siege"}
        local btnCount = #presets
        local btnW     = (cw-8) / btnCount
        local btnH     = 34
        local btnY     = cy + 160
        for i, pname in ipairs(presets) do
            local bx = cx+(i-1)*btnW
            if mx>=bx and mx<=bx+btnW-4 and my>=btnY and my<=btnY+btnH then
                RunConsoleCommand("d6_energy_preset", pname); return
            end
        end
    end
end)

-- ── Hover при движении мыши (вкладка 1 и 2) ──────────────
hook.Add("GUI.MouseMoved", "D6Menu_Hover", function()
    if not Menu.open then return end
    local mx,my = gui.MousePos()
    local px,py,pw,ph = GetMenuLayout()
    local tabY=py+36; local tabH=28
    local cx=px+4; local cy=tabY+tabH+4; local cw=pw-8

    if Menu.tab==1 then
        local itemH=38; local gap=4
        Menu.hotCmd=0
        for i=1,#COMMANDS do
            local iy=cy+(i-1)*(itemH+gap)
            if mx>=cx and mx<=cx+cw-4 and my>=iy and my<=iy+itemH then
                Menu.hotCmd=i; break
            end
        end
    elseif Menu.tab==2 then
        local sItemH=36; local sGap=3
        Menu.hotSet=0
        for i=1,#SETTINGS do
            local iy=cy+(i-1)*(sItemH+sGap)
            if mx>=cx and mx<=cx+cw-4 and my>=iy and my<=iy+sItemH then
                Menu.hotSet=i; break
            end
        end
    end
end)

-- ── Выпадающий список примеров ───────────────────────────
local function DrawExamples()
    if not showExamples or Menu.tab~=3 then return end
    local px,py,pw,ph = GetMenuLayout()
    local cx=px+4
    local exY=py+ph-120; local exW=240; local exH=#MOD_EXAMPLES*26+4
    DrawPanel(cx+226, exY-exH, exW, exH, C.bgDark, C.border, 1)
    local mx,my = gui.MousePos()
    for i,ex in ipairs(MOD_EXAMPLES) do
        local iy=exY-exH+2+(i-1)*26
        local hot=mx>=cx+226 and mx<=cx+226+exW and my>=iy and my<=iy+24
        if hot then
            surface.SetDrawColor(C.btnHover); surface.DrawRect(cx+226,iy,exW,24)
        end
        DrawTextShadow("  "..ex.name, "D6Menu_Item",
            cx+228, iy+12, hot and C.hot or C.text, TEXT_ALIGN_LEFT, TEXT_ALIGN_CENTER)
    end
end

hook.Add("GUI.MousePressed", "D6Menu_ExamplesClick", function(btn)
    if not showExamples or not Menu.open or Menu.tab~=3 or btn~=MOUSE_LEFT then return end
    local mx,my = gui.MousePos()
    local px,py,pw,ph = GetMenuLayout()
    local cx=px+4
    local exY=py+ph-120; local exW=240; local exH=#MOD_EXAMPLES*26+4
    for i,ex in ipairs(MOD_EXAMPLES) do
        local iy=exY-exH+2+(i-1)*26
        if mx>=cx+226 and mx<=cx+226+exW and my>=iy and my<=iy+24 then
            Menu.modText=ex.code; Menu.modCursor=#ex.code; showExamples=false; return
        end
    end
end)

-- =========================================================
-- РЕГИСТРАЦИЯ В HUDPaint
-- =========================================================
hook.Add("HUDPaint", "D6Menu_Draw", function()
    DrawMenu(); DrawExamples()
end)

hook.Add("HUDPaint", "D6Menu_Hint", function()
    if Menu.open then return end
    local ply = LocalPlayer()
    if not IsValid(ply) then return end
    draw.SimpleText("[Q] Меню → DRMD 6DOF", "D6Menu_Small",
        ScrW()-8, ScrH()-36,
        ply:GetNWBool("D6On",false) and Color(80,200,80,180) or Color(80,80,80,120),
        TEXT_ALIGN_RIGHT, TEXT_ALIGN_CENTER)
end)

-- =========================================================
-- НАМПАД-НАВИГАЦИЯ
-- =========================================================
hook.Add("PlayerButtonDown","D6Menu_Numpad",function(key)
    if key==KEY_PAD_0 then
        if Menu.open then CloseMenu() else OpenMenu() end; return true
    end
    if not Menu.open then return end
    if key==KEY_PAD_1 then Menu.tab=1; return true end
    if key==KEY_PAD_2 then
        if Menu.tab==1 then Menu.hotCmd=math.min(#COMMANDS,(Menu.hotCmd or 0)+1)
        elseif Menu.tab==2 then Menu.hotSet=math.min(#SETTINGS,(Menu.hotSet or 0)+1) end; return true
    end
    if key==KEY_PAD_3 then Menu.tab=3; return true end
    if key==KEY_PAD_4 then
        if Menu.tab==2 and Menu.hotSet>0 then local s=SETTINGS[Menu.hotSet]
            if type(Cfg[s.key])=="number" then Cfg[s.key]=math.max(s.min,Cfg[s.key]-s.step) end
        end; return true
    end
    if key==KEY_PAD_5 then Menu.tab=2; return true end
    if key==KEY_PAD_6 then
        if Menu.tab==2 and Menu.hotSet>0 then local s=SETTINGS[Menu.hotSet]
            if type(Cfg[s.key])=="number" then Cfg[s.key]=math.min(s.max,Cfg[s.key]+s.step) end
        end; return true
    end
    if key==KEY_PAD_7 then Menu.tab=1; return true end
    if key==KEY_PAD_8 then
        if Menu.tab==1 then Menu.hotCmd=math.max(1,(Menu.hotCmd or 1)-1)
        elseif Menu.tab==2 then Menu.hotSet=math.max(1,(Menu.hotSet or 1)-1) end; return true
    end
    if key==KEY_PAD_9 then Menu.tab=3; return true end
end)

-- =========================================================
-- РАДАР ВРАГОВ
-- =========================================================
local RadarOn     = false
local RADAR_RANGE = 2500
local RADAR_R     = 120

-- Конкоманда d6_radar_toggle — теперь работает!
concommand.Add("d6_radar_toggle", function()
    local ply = LocalPlayer()
    if not IsValid(ply) then return end
    RadarOn = not RadarOn
    chat.AddText(Color(0,220,120),"[6DOF] Сканер: ",
        (RadarOn and Color(0,255,0) or Color(255,80,80)),
        RadarOn and "АКТИВЕН" or "ВЫКЛ")
end)

hook.Add("PlayerButtonDown","D6_RadarToggle",function(key)
    if key~=KEY_PAD_DECIMAL then return end
    local ply = LocalPlayer()
    if not IsValid(ply) or not ply:GetNWBool("D6On",false) then return end
    RadarOn = not RadarOn
    chat.AddText(Color(0,220,120),"[6DOF] Сканер: ",
        (RadarOn and Color(0,255,0) or Color(255,80,80)),
        RadarOn and "АКТИВЕН" or "ВЫКЛ")
end)

hook.Add("HUDPaint","D6_RadarDraw",function()
    local ply = LocalPlayer()
    if not IsValid(ply) or not ply:GetNWBool("D6On",false) then return end
    if not RadarOn then
        draw.SimpleText("◌ СКАН","DermaDefault",
            ScrW()-8,ScrH()-52,Color(80,80,80,120),TEXT_ALIGN_RIGHT,TEXT_ALIGN_CENTER)
        return
    end

    local sw,sh=ScrW(),ScrH()
    local cx,cy = sw-RADAR_R-16, sh-RADAR_R-100  -- поднято чтобы не перекрывать кокпит

    draw.NoTexture(); surface.SetDrawColor(0,0,0,160)
    local segs=32; local verts={}
    for i=0,segs do
        local a=i/segs*2*math.pi
        table.insert(verts,{x=cx+math.cos(a)*RADAR_R, y=cy+math.sin(a)*RADAR_R})
    end
    surface.DrawPoly(verts)

    surface.SetDrawColor(0,220,120,200)
    for i=0,segs-1 do
        local a1=i/segs*2*math.pi; local a2=(i+1)/segs*2*math.pi
        surface.DrawLine(cx+math.cos(a1)*RADAR_R,cy+math.sin(a1)*RADAR_R,
                         cx+math.cos(a2)*RADAR_R,cy+math.sin(a2)*RADAR_R)
    end

    surface.SetDrawColor(0,220,120,120)
    surface.DrawLine(cx-6,cy,cx+6,cy); surface.DrawLine(cx,cy-6,cx,cy+6)

    surface.SetDrawColor(0,100,60,60)
    for r=RADAR_R/3,RADAR_R,RADAR_R/3 do
        for i=0,segs-1 do
            local a1=i/segs*2*math.pi; local a2=(i+1)/segs*2*math.pi
            surface.DrawLine(cx+math.cos(a1)*r,cy+math.sin(a1)*r,cx+math.cos(a2)*r,cy+math.sin(a2)*r)
        end
    end

    local ang = ply.D6Ang or ply:GetAngles()
    -- Корректная формула направления: forward вектор в 2D
    local ang_flat = Angle(0, ang.y, 0)
    local fwdV = ang_flat:Forward()  -- (cos(yaw), sin(yaw), 0) в мировых коорд
    local rgtV = ang_flat:Right()    -- перпендикуляр вправо

    -- Стрелка направления взгляда (forward = UP на экране)
    surface.SetDrawColor(0,220,120,180)
    surface.DrawLine(cx, cy,
        cx + fwdV.y * (RADAR_R-8),
        cy - fwdV.x * (RADAR_R-8))

    local myPos = ply:GetPos()
    local scale = RADAR_R / RADAR_RANGE

    -- DrawBlip: правильные оси — forward→UP, right→RIGHT
    local function DrawBlip(entPos, col, sz, label)
        local delta = entPos - myPos
        local dist  = delta:Length()
        if dist > RADAR_RANGE then return end

        -- Проекция delta на оси камеры (2D, Z игнорируем)
        local fwd_comp = delta.x * fwdV.x + delta.y * fwdV.y  -- сколько "вперёд"
        local rgt_comp = delta.x * rgtV.x + delta.y * rgtV.y  -- сколько "вправо"

        local bx = cx + rgt_comp * scale
        local by = cy - fwd_comp * scale  -- вперёд = выше на экране

        -- Клип к краю круга
        local bdist = math.sqrt((bx-cx)^2 + (by-cy)^2)
        if bdist > RADAR_R - sz then
            local a2 = math.atan2(by-cy, bx-cx)
            bx = cx + math.cos(a2) * (RADAR_R - sz - 1)
            by = cy + math.sin(a2) * (RADAR_R - sz - 1)
        end

        surface.SetDrawColor(col.r, col.g, col.b, 220)
        surface.DrawRect(bx - sz*0.5, by - sz*0.5, sz, sz)
        if label and dist < RADAR_RANGE * 0.5 then
            draw.SimpleText(label, "DermaDefault", bx, by-10,
                col, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
        end
    end

    -- Игроки (синие)
    for _, p in ipairs(player.GetAll()) do
        if p ~= ply and IsValid(p) and p:Alive() then
            DrawBlip(p:GetPos(), Color(80,150,255), 6, p:Nick())
        end
    end

    -- Все NPC (цвет по типу D6_Variant или красный по умолчанию)
    local D6_COLS = {
        mine=Color(255,200,0), mg=Color(255,60,60),
        rpg=Color(255,100,40), grav=Color(160,40,255),
        laser=Color(0,220,255), heavy=Color(120,120,120),
        seeker=Color(200,0,255), wheatley=Color(200,220,255),
        chopper=Color(80,80,80),
    }
    for _, npc in ipairs(ents.GetAll()) do
        if IsValid(npc) and npc:IsNPC() and npc:Health() > 0 then
            local col = (npc.D6_Variant and D6_COLS[npc.D6_Variant]) or Color(255,60,60)
            local sz  = npc.D6_Variant and 5 or 4
            DrawBlip(npc:GetPos(), col, sz)
        end
    end

    -- Воздушные мины (жёлтые)
    for _, e in ipairs(ents.FindByClass("prop_physics")) do
        if IsValid(e) and e.IsAirMine then
            DrawBlip(e:GetPos(), Color(255,220,0), 5, "☠")
        end
    end

    draw.SimpleText("◉ СКАН ["..math.floor(RADAR_RANGE/52).."м]","DermaDefault",
        cx,cy-RADAR_R-12,Color(0,220,120,200),TEXT_ALIGN_CENTER,TEXT_ALIGN_CENTER)
    draw.SimpleText("KP. / d6_radar_toggle","DermaDefault",
        cx,cy+RADAR_R+8,Color(80,80,80,150),TEXT_ALIGN_CENTER,TEXT_ALIGN_CENTER)
end)

print("[D6] d6_menu.lua загружен")
