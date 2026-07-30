-- ═══════════════════════════════════════════════════════════
-- menu/workshop.lua — SCK Unified Workshop Tab
-- Included inside CreateMenu() in client.lua.
--
-- Единая вкладка, объединяющая все кластеры + параметры оружия:
--   • Список всех модулей из ВСЕХ зон (DListView с цветовой маркировкой)
--   • Инлайн-редактор модуля (pos/ang/scale/anim/muzzle/role)
--   • Быстрые параметры оружия (FireRate, Damage, Projectile...)
--   • Перенос модуля между зонами, копирование, удаление
-- ═══════════════════════════════════════════════════════════

local wep = GetSCKSWEP(LocalPlayer())
if not IsValid(wep) then return end

local pworkshop = wep.pworkshop

local ROLES      = { "—", "Primary L", "Primary R", "Secondary", "Utility", "Passive" }
local ZONE_SHORT = { Center="Cntr", Upper="Up", SideLeft="L", SideRight="R", Lower="Low" }

local selCluster  = nil
local selIdx      = nil
local activePlist = nil
local allPanels   = {}   -- [cluster.."/"..idx] = DPanelList

-- ── Cross-zone module list ────────────────────────────────
local mlist = vgui.Create("DListView", pworkshop)
    mlist:SetMultiSelect(false)
    local c1 = mlist:AddColumn("Зона");   c1:SetWidth(42)
    local c2 = mlist:AddColumn("Слот");   c2:SetWidth(28)
    local c3 = mlist:AddColumn("Модуль"); c3:SetWidth(90)
    local c4 = mlist:AddColumn("Роль");   c4:SetWidth(72)
mlist:SetTall(150)
mlist:DockMargin(0, 0, 0, 4)
mlist:Dock(TOP)

-- ── Action row ────────────────────────────────────────────
local actRow = vgui.Create("DPanel", pworkshop)
    actRow:SetTall(26)
    actRow:SetDrawBackground(false)
actRow:DockMargin(0, 0, 0, 4)
actRow:Dock(TOP)

local zoneXfer = vgui.Create("DComboBox", actRow)
    zoneXfer:SetWide(90)
    zoneXfer:SetValue("→ Зона")
    for _, cn in ipairs(SCK_CLUSTER_ORDER) do zoneXfer:AddChoice(cn) end
zoneXfer:Dock(LEFT)

local function MkBtn(parent, label, w)
    local b = vgui.Create("DButton", parent)
    b:SetText(label); b:SetWide(w)
    b:DockMargin(3, 0, 0, 0); b:Dock(LEFT)
    return b
end

local moveBtn = MkBtn(actRow, "Переместить", 90)
local dupBtn  = MkBtn(actRow, "Копировать",  80)
local delBtn  = MkBtn(actRow, "Удалить",     66)
local refBtn  = MkBtn(actRow, "Обновить",    68)

-- ── Bottom: module editor (left) + quick params (right) ──
local bottomPanel = vgui.Create("DPanel", pworkshop)
    bottomPanel:SetDrawBackground(false)
bottomPanel:Dock(FILL)

local propScroll  = vgui.Create("DScrollPanel", bottomPanel)
local quickScroll = vgui.Create("DScrollPanel", bottomPanel)

bottomPanel.PerformLayout = function(self, w, h)
    local lw = math.floor(w * 0.55)
    propScroll:SetPos(0, 0);      propScroll:SetSize(lw, h)
    quickScroll:SetPos(lw + 4, 0); quickScroll:SetSize(w - lw - 4, h)
end

-- ── Quick params panel (right side) ──────────────────────
local qlist = vgui.Create("DPanelList", quickScroll)
    qlist:SetPaintBackground(false)
    qlist:EnableVerticalScrollbar(false)
    qlist:SetSpacing(3)
    qlist:SetPadding(4)
qlist:Dock(FILL)

local function QPHdr(text)
    local h = vgui.Create("DLabel", qlist)
        h:SetText(text)
        h:SetFont("DermaDefaultBold")
        h:SetColor(Color(200, 220, 140))
        h:SetTall(18)
    qlist:AddItem(h)
end

local function QPSlider(label, getter, setter, mn, mx, dec)
    local row = vgui.Create("DPanel", qlist)
        row:SetTall(22)
        row:SetDrawBackground(false)
    local lb = vgui.Create("DLabel", row)
        lb:SetText(label); lb:SetWide(90); lb:SetTall(22)
    lb:Dock(LEFT)
    local sl = vgui.Create("DNumSlider", row)
        sl:SetMin(mn or 0); sl:SetMax(mx or 100)
        sl:SetDecimals(dec or 1)
        sl:SetValue(tonumber(getter()) or 0)
        sl:SetText("")
        sl.OnValueChanged = function(_, v) setter(v) end
    sl:Dock(FILL)
    qlist:AddItem(row)
end

local function QPText(label, getter, setter)
    local row = vgui.Create("DPanel", qlist)
        row:SetTall(22)
        row:SetDrawBackground(false)
    local lb = vgui.Create("DLabel", row)
        lb:SetText(label); lb:SetWide(90); lb:SetTall(22)
    lb:Dock(LEFT)
    local te = vgui.Create("DTextEntry", row)
        te:SetText(tostring(getter() or ""))
        te:SetTall(22)
        te.OnEnter = function(s) setter(s:GetValue()) end
    te:Dock(FILL)
    qlist:AddItem(row)
end

-- Ensure weaponconfig exists (populated by Stats/Projectile tabs)
if not wep.weaponconfig             then wep.weaponconfig = {} end
if not wep.weaponconfig.weapon      then wep.weaponconfig.weapon = {} end
if not wep.weaponconfig.projectile  then wep.weaponconfig.projectile = {} end

local WW = wep.weaponconfig.weapon
local WP = wep.weaponconfig.projectile

QPHdr("── Оружие ──")
QPSlider("Fire Rate:",   function() return WW.FireRate     or 0.2  end, function(v) WW.FireRate     = v end, 0.01, 5,     3)
QPSlider("Damage:",      function() return WW.Damage       or 0    end, function(v) WW.Damage       = v end, 0,    500,   0)
QPSlider("Energy Cost:", function() return WW.EnergyCost   or 0    end, function(v) WW.EnergyCost   = v end, 0,    200,   0)
QPSlider("Splash Rad:",  function() return WW.SplashRadius or 0    end, function(v) WW.SplashRadius = v end, 0,    1000,  0)

QPHdr("── Снаряд ──")
QPSlider("Proj Speed:",  function() return WP.InitialVelocity or 1200 end, function(v) WP.InitialVelocity = v end, 0, 22000, 0)
QPSlider("Lifetime:",    function() return WP.Lifetime        or 5   end, function(v) WP.Lifetime        = v end, 0.1, 60, 1)
QPSlider("Mass:",        function() return WP.Mass            or 1   end, function(v) WP.Mass            = v end, 0,  200, 1)
QPText("Proj Model:",    function() return WP.Model or "" end, function(v) WP.Model = v end)

-- ── Module property panel builder ────────────────────────
local function BuildModPanel(cn, idx)
    local key = cn .. "/" .. idx
    if allPanels[key] and IsValid(allPanels[key]) then return allPanels[key] end

    local cl  = wep.clusters and wep.clusters[cn]
    local mod = cl and cl[idx]
    if not mod then return nil end

    local plist = vgui.Create("DPanelList", propScroll)
        plist:SetPaintBackground(false)
        plist:EnableVerticalScrollbar(false)
        plist:SetSpacing(3)
        plist:SetPadding(4)
        plist:SetVisible(false)
    plist:Dock(FILL)

    local zc = (SCK_CLUSTER_COLORS and SCK_CLUSTER_COLORS[cn]) or Color(200, 200, 200)

    local hdr = vgui.Create("DLabel", plist)
        hdr:SetText(cn .. "  ›  " .. (mod.name or "?"))
        hdr:SetFont("DermaDefaultBold")
        hdr:SetColor(zc)
        hdr:SetTall(20)
    plist:AddItem(hdr)

    local function AddRow(label, ctrl)
        local row = vgui.Create("DPanel", plist)
            row:SetTall(22)
            row:SetDrawBackground(false)
        if label ~= "" then
            local lb = vgui.Create("DLabel", row)
                lb:SetText(label); lb:SetWide(68); lb:SetTall(22)
            lb:Dock(LEFT)
        end
        ctrl:SetParent(row)
        ctrl:Dock(FILL)
        plist:AddItem(row)
    end

    local function Slider(label, getter, setter, mn, mx, dec)
        local row = vgui.Create("DPanel", plist)
            row:SetTall(22)
            row:SetDrawBackground(false)
        local lb = vgui.Create("DLabel", row)
            lb:SetText(label); lb:SetWide(68); lb:SetTall(22)
        lb:Dock(LEFT)
        local sl = vgui.Create("DNumSlider", row)
            sl:SetMin(mn); sl:SetMax(mx)
            sl:SetDecimals(dec or 2)
            sl:SetValue(getter())
            sl:SetText("")
            sl.OnValueChanged = function(_, v) setter(v) end
        sl:Dock(FILL)
        plist:AddItem(row)
    end

    local function Hdr(text)
        local h = vgui.Create("DLabel", plist)
            h:SetText(text)
            h:SetFont("DermaDefaultBold")
            h:SetColor(Color(160, 200, 240))
            h:SetTall(18)
        plist:AddItem(h)
    end

    -- Model
    local modelTE = vgui.Create("DTextEntry")
        modelTE:SetText(mod.model or ""); modelTE:SetTall(22)
        modelTE.OnEnter = function(s)
            mod.model = string.Trim(s:GetValue())
            if wep.CreateModels then wep:CreateModels(wep.VElements or {}) end
        end
    AddRow("Model:", modelTE)

    -- Weapon Role
    local roleBox = vgui.Create("DComboBox")
        roleBox:SetTall(22)
        for _, r in ipairs(ROLES) do roleBox:AddChoice(r) end
        roleBox:SetValue(mod.role or "—")
        roleBox.OnSelect = function(_, _, val) mod.role = (val == "—") and nil or val end
    AddRow("Role:", roleBox)

    -- Position
    Slider("Pos X:", function() return (mod.pos or Vector(0,0,0)).x end,
        function(v) if not mod.pos then mod.pos=Vector(0,0,0) end mod.pos.x=v end, -200, 200)
    Slider("Pos Y:", function() return (mod.pos or Vector(0,0,0)).y end,
        function(v) if not mod.pos then mod.pos=Vector(0,0,0) end mod.pos.y=v end, -200, 200)
    Slider("Pos Z:", function() return (mod.pos or Vector(0,0,0)).z end,
        function(v) if not mod.pos then mod.pos=Vector(0,0,0) end mod.pos.z=v end, -200, 200)

    -- Angles
    Slider("Ang P:", function() return (mod.angle or Angle(0,0,0)).p end,
        function(v) if not mod.angle then mod.angle=Angle(0,0,0) end mod.angle.p=v end, -180, 180)
    Slider("Ang Y:", function() return (mod.angle or Angle(0,0,0)).y end,
        function(v) if not mod.angle then mod.angle=Angle(0,0,0) end mod.angle.y=v end, -180, 180)
    Slider("Ang R:", function() return (mod.angle or Angle(0,0,0)).r end,
        function(v) if not mod.angle then mod.angle=Angle(0,0,0) end mod.angle.r=v end, -180, 180)

    -- Scale (uniform)
    Slider("Scale:", function() return ((mod.size or Vector(1,1,1)).x) end,
        function(v) mod.size = Vector(v, v, v) end, 0.01, 5)

    -- Animation
    Hdr("── Анимация ──")
    local function AG(f, d) return function() return (mod.anim and mod.anim[f]) or d end end
    local function AS(f) return function(v)
        if not mod.anim then mod.anim = {} end
        mod.anim[f] = tonumber(v) or 0
    end end
    Slider("Spin°/s:", AG("spin",0),        AS("spin"),        -720, 720)
    Slider("Bob amp:", AG("bobAmp",0),      AS("bobAmp"),      0,    10)
    Slider("Bob frq:", AG("bobFreq",2),     AS("bobFreq"),     0,    10)
    Slider("Sway×:",   AG("swayMult",1),   AS("swayMult"),    0,    3)
    Slider("Kick:",    AG("kick",0),        AS("kick"),        0,    20)
    Slider("KickRec:", AG("kickRecover",6), AS("kickRecover"), 0.5,  20)

    -- Attachment
    Hdr("── Крепление ──")
    local muzzChk = vgui.Create("DCheckBoxLabel")
        muzzChk:SetText("Точка выстрела (muzzle)")
        muzzChk:SetTall(20)
        muzzChk:SetValue(mod.attach and mod.attach.muzzle and 1 or 0)
        muzzChk.OnChange = function(_, checked)
            if not mod.attach then mod.attach = { idx = 1 } end
            mod.attach.muzzle = checked
        end
    AddRow("", muzzChk)

    Slider("Muzz idx:", function() return (mod.attach and mod.attach.idx) or 1 end,
        function(v)
            if not mod.attach then mod.attach = {} end
            mod.attach.idx = math.floor((tonumber(v) or 1) + 0.5)
        end, 1, 8)

    allPanels[key] = plist
    return plist
end

-- ── Refresh list from all clusters ───────────────────────
local function RefreshWorkshopList()
    mlist:Clear()
    if not wep.clusters then return end
    for _, cn in ipairs(SCK_CLUSTER_ORDER) do
        local cl = wep.clusters[cn] or {}
        for i, mod in ipairs(cl) do
            local line = mlist:AddLine(
                ZONE_SHORT[cn] or cn,
                tostring(i),
                mod.name or "?",
                mod.role  or "—"
            )
            line._wcluster = cn
            line._widx     = i
        end
    end
end

-- ── Module selection ──────────────────────────────────────
mlist.OnRowSelected = function(self, lineIdx)
    local ln = mlist:GetLine(lineIdx)
    if not ln then return end
    local cn  = ln._wcluster
    local idx = ln._widx
    if not cn or not idx then return end

    selCluster = cn
    selIdx     = idx

    if activePlist and IsValid(activePlist) then activePlist:SetVisible(false) end
    local mp = BuildModPanel(cn, idx)
    if mp then mp:SetVisible(true); activePlist = mp end
end

-- ── Zone transfer ─────────────────────────────────────────
moveBtn.DoClick = function()
    if not selCluster or not selIdx then return end
    local dest = zoneXfer:GetValue()
    if dest == "→ Зона" or dest == selCluster then return end
    if not (wep.clusters and wep.clusters[dest]) then return end

    local src = wep.clusters[selCluster]
    local mod = table.remove(src, selIdx)
    if not mod then return end
    for i, m in ipairs(src) do m.slot = i end

    mod.slot = #wep.clusters[dest] + 1
    table.insert(wep.clusters[dest], mod)

    -- Invalidate all cached panels (indices shifted)
    for _, p in pairs(allPanels) do if IsValid(p) then p:Remove() end end
    allPanels = {}
    if wep.clusterPanels then wep.clusterPanels = {} end

    selCluster = nil; selIdx = nil; activePlist = nil
    RefreshWorkshopList()
    if RefreshModuleList then RefreshModuleList() end
end

-- ── Copy ──────────────────────────────────────────────────
dupBtn.DoClick = function()
    if not selCluster or not selIdx then return end
    local cl  = wep.clusters[selCluster]
    local src = cl[selIdx]
    if not src then return end
    local dup  = table.FullCopy(src)
    dup.name   = src.name .. "_copy"
    dup.slot   = #cl + 1
    table.insert(cl, dup)
    RefreshWorkshopList()
end

-- ── Delete ────────────────────────────────────────────────
delBtn.DoClick = function()
    if not selCluster or not selIdx then return end
    local key = selCluster .. "/" .. selIdx
    if allPanels[key] and IsValid(allPanels[key]) then allPanels[key]:Remove() end
    allPanels[key] = nil
    if wep.clusterPanels then wep.clusterPanels[key] = nil end

    local cl = wep.clusters[selCluster]
    table.remove(cl, selIdx)
    for i, m in ipairs(cl) do m.slot = i end

    selCluster = nil; selIdx = nil; activePlist = nil
    RefreshWorkshopList()
    if RefreshModuleList then RefreshModuleList() end
end

-- ── Refresh (rebuild all from scratch) ───────────────────
refBtn.DoClick = function()
    for _, p in pairs(allPanels) do if IsValid(p) then p:Remove() end end
    allPanels = {}
    selCluster = nil; selIdx = nil; activePlist = nil
    RefreshWorkshopList()
    if RefreshModuleList then RefreshModuleList() end
end

-- ── Initialize ───────────────────────────────────────────
RefreshWorkshopList()
