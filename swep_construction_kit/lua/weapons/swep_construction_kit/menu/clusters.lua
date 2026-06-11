-- ═══════════════════════════════════════════════════════════
-- menu/clusters.lua — SCK Weapon Clusters Editor Tab
--
-- Included inside CreateMenu() in client.lua.
-- Provides a 6th tab: "Clusters" in the DPropertySheet.
--
-- Structure:
--   wep.pclusters     — the tab panel
--   wep.clusters      — live cluster data  { Center={...}, ... }
--   wep.clusterPanels — per-module property panels keyed by
--                       "<ClusterName>/<slot_index>"
-- ═══════════════════════════════════════════════════════════

local wep = GetSCKSWEP(LocalPlayer())
if not IsValid(wep) then return end

local pclusters = wep.pclusters
pclusters:DockPadding(5, 5, 5, 5)

-- Initialise live cluster store if not already loaded from preset
if not wep.clusters then
    wep.clusters = {}
    for _, cn in ipairs(SCK_CLUSTER_ORDER) do
        wep.clusters[cn] = {}
    end
end

-- Track which cluster zone and module index are currently selected
local selectedCluster = "Center"
local selectedIdx     = nil
local lastModPanel    = nil   -- currently visible module property panel

-- ── Helper: notification label ───────────────────────────────
local function ClusterNote(text)
    local lbl = vgui.Create("DLabel")
        lbl:SetText(text)
        lbl:SizeToContents()
    local notif = vgui.Create("DNotify", pclusters)
        notif:SetPos(5, pclusters:GetTall() - 40)
        notif:SetSize(lbl:GetWide() + 10, 24)
        notif:SetLife(4)
    notif:AddItem(lbl)
end

-- ── Zone selector row (5 coloured buttons) ───────────────────
local zoneRow = vgui.Create("DPanel", pclusters)
    zoneRow:SetTall(28)
    zoneRow:SetDrawBackground(false)
zoneRow:DockMargin(0, 0, 0, 4)
zoneRow:Dock(TOP)

local zoneBtns = {}
for i, cn in ipairs(SCK_CLUSTER_ORDER) do
    local col = SCK_CLUSTER_COLORS[cn]
    local btn = vgui.Create("DButton", zoneRow)
        btn:SetText(cn)
        btn:SetFont("DermaDefault")
    btn:DockMargin(i == 1 and 0 or 3, 0, 0, 0)
    btn:Dock(LEFT)
    btn:SetWide(82)
    btn.Paint = function(self, w, h)
        local active = (selectedCluster == cn)
        local alpha  = active and 255 or 160
        surface.SetDrawColor(col.r, col.g, col.b, alpha)
        surface.DrawRect(0, 0, w, h)
        surface.SetDrawColor(active and 255 or 80, active and 255 or 80, active and 255 or 80, 255)
        surface.DrawOutlinedRect(0, 0, w, h)
    end
    btn.DoClick = function()
        selectedCluster = cn
        selectedIdx     = nil
        if lastModPanel and IsValid(lastModPanel) then
            lastModPanel:SetVisible(false)
        end
        -- Rebuild module list for new zone
        RefreshModuleList()
    end
    zoneBtns[cn] = btn
end

-- ── Module list ───────────────────────────────────────────────
local mlist = vgui.Create("DListView", pclusters)
    mlist:SetTall(140)
    mlist:SetMultiSelect(false)
    mlist:SetDrawBackground(true)
    mlist:AddColumn("Slot")
    mlist:AddColumn("Name")
    mlist:AddColumn("Type")
    mlist:AddColumn("Model")
mlist:DockMargin(0, 0, 0, 4)
mlist:Dock(TOP)

-- ── List action buttons row ───────────────────────────────────
local btnRow = vgui.Create("DPanel", pclusters)
    btnRow:SetTall(24)
    btnRow:SetDrawBackground(false)
btnRow:DockMargin(0, 0, 0, 6)
btnRow:Dock(TOP)

local addNameEntry = vgui.Create("DTextEntry", btnRow)
    addNameEntry:SetTall(24)
    addNameEntry:SetText("module_name")
    addNameEntry:SetMultiline(false)
addNameEntry:Dock(FILL)

local typeBox = vgui.Create("DComboBox", btnRow)
    typeBox:SetWide(70)
    typeBox:SetTall(24)
    typeBox:AddChoice("Model")
    typeBox:AddChoice("Sprite")
    typeBox:AddChoice("Quad")
    typeBox:SetValue("Model")
typeBox:DockMargin(4, 0, 0, 0)
typeBox:Dock(RIGHT)

local addBtn = vgui.Create("DButton", btnRow)
    addBtn:SetWide(40)
    addBtn:SetTall(24)
    addBtn:SetText("Add")
addBtn:DockMargin(4, 0, 0, 0)
addBtn:Dock(RIGHT)

local removeBtn = vgui.Create("DButton", btnRow)
    removeBtn:SetWide(60)
    removeBtn:SetTall(24)
    removeBtn:SetText("Remove")
removeBtn:DockMargin(4, 0, 0, 0)
removeBtn:Dock(RIGHT)

local copyBtn = vgui.Create("DButton", btnRow)
    copyBtn:SetWide(44)
    copyBtn:SetTall(24)
    copyBtn:SetText("Copy")
copyBtn:DockMargin(4, 0, 0, 0)
copyBtn:Dock(RIGHT)

local mvUpBtn = vgui.Create("DButton", btnRow)
    mvUpBtn:SetWide(24)
    mvUpBtn:SetTall(24)
    mvUpBtn:SetText("↑")
mvUpBtn:DockMargin(4, 0, 0, 0)
mvUpBtn:Dock(RIGHT)

local mvDnBtn = vgui.Create("DButton", btnRow)
    mvDnBtn:SetWide(24)
    mvDnBtn:SetTall(24)
    mvDnBtn:SetText("↓")
mvDnBtn:DockMargin(2, 0, 0, 0)
mvDnBtn:Dock(RIGHT)

-- ── Module property scroll panel (FILL area) ─────────────────
local propScroll = vgui.Create("DScrollPanel", pclusters)
propScroll:Dock(FILL)

-- ── Per-module property panel builder ────────────────────────
local function CreateModPanel(cluster, idx)
    local mod  = cluster[idx]
    if not mod then return end

    local key = selectedCluster .. "/" .. idx
    if wep.clusterPanels and wep.clusterPanels[key] and IsValid(wep.clusterPanels[key]) then
        return wep.clusterPanels[key]
    end

    if not wep.clusterPanels then wep.clusterPanels = {} end

    local plist = vgui.Create("DPanelList", propScroll)
        plist:SetPaintBackground(false)
        plist:EnableVerticalScrollbar(false)
        plist:SetSpacing(4)
        plist:SetPadding(4)
        plist:SetVisible(false)
    plist:Dock(FILL)

    local function AddRow(label, control)
        local row = vgui.Create("DPanel", plist)
            row:SetTall(22)
            row:SetDrawBackground(false)
        local lbl = vgui.Create("DLabel", row)
            lbl:SetText(label)
            lbl:SetWide(80)
            lbl:SetTall(22)
        lbl:Dock(LEFT)
        control:SetParent(row)
        control:Dock(FILL)
        plist:AddItem(row)
        return row
    end

    -- Name (read-only display)
    local nameLabel = vgui.Create("DLabel")
        nameLabel:SetText("Name: " .. (mod.name or ""))
        nameLabel:SetTall(20)
    plist:AddItem(nameLabel)

    -- Model path
    local modelEntry = vgui.Create("DTextEntry")
        modelEntry:SetText(mod.model or "")
        modelEntry:SetTall(22)
        modelEntry.OnTextChanged = function(s)
            mod.model = string.Trim(s:GetValue())
            wep:CreateModels(wep.VElements or {})
        end
    AddRow("Model:", modelEntry)

    -- Bone
    local boneEntry = vgui.Create("DTextEntry")
        boneEntry:SetText(mod.bone or "ValveBiped.Bip01_R_Hand")
        boneEntry:SetTall(22)
        boneEntry.OnTextChanged = function(s)
            mod.bone = string.Trim(s:GetValue())
            wep.vRenderOrder = nil
            wep.wRenderOrder = nil
        end
    AddRow("Bone:", boneEntry)

    -- Pos X/Y/Z
    local function MakeSlider(label, getter, setter, mn, mx)
        local row = vgui.Create("DPanel", plist)
            row:SetTall(22)
            row:SetDrawBackground(false)
        local lbl = vgui.Create("DLabel", row)
            lbl:SetText(label)
            lbl:SetWide(80)
            lbl:SetTall(22)
        lbl:Dock(LEFT)
        local sl = vgui.Create("DNumSlider", row)
            sl:SetMin(mn)
            sl:SetMax(mx)
            sl:SetDecimals(2)
            sl:SetValue(getter())
            sl:SetText("")
            sl.OnValueChanged = function(_, val) setter(val) end
        sl:Dock(FILL)
        plist:AddItem(row)
    end

    MakeSlider("Pos X:", function() return (mod.pos or Vector(0,0,0)).x end,
        function(v) if not mod.pos then mod.pos = Vector(0,0,0) end mod.pos.x = v end, -200, 200)
    MakeSlider("Pos Y:", function() return (mod.pos or Vector(0,0,0)).y end,
        function(v) if not mod.pos then mod.pos = Vector(0,0,0) end mod.pos.y = v end, -200, 200)
    MakeSlider("Pos Z:", function() return (mod.pos or Vector(0,0,0)).z end,
        function(v) if not mod.pos then mod.pos = Vector(0,0,0) end mod.pos.z = v end, -200, 200)

    MakeSlider("Ang P:", function() return (mod.angle or Angle(0,0,0)).p end,
        function(v) if not mod.angle then mod.angle = Angle(0,0,0) end mod.angle.p = v end, -180, 180)
    MakeSlider("Ang Y:", function() return (mod.angle or Angle(0,0,0)).y end,
        function(v) if not mod.angle then mod.angle = Angle(0,0,0) end mod.angle.y = v end, -180, 180)
    MakeSlider("Ang R:", function() return (mod.angle or Angle(0,0,0)).r end,
        function(v) if not mod.angle then mod.angle = Angle(0,0,0) end mod.angle.r = v end, -180, 180)

    MakeSlider("Size X:", function() return (mod.size or Vector(1,1,1)).x end,
        function(v) if not mod.size then mod.size = Vector(1,1,1) end mod.size.x = v end, 0.01, 5)
    MakeSlider("Size Y:", function() return (mod.size or Vector(1,1,1)).y end,
        function(v) if not mod.size then mod.size = Vector(1,1,1) end mod.size.y = v end, 0.01, 5)
    MakeSlider("Size Z:", function() return (mod.size or Vector(1,1,1)).z end,
        function(v) if not mod.size then mod.size = Vector(1,1,1) end mod.size.z = v end, 0.01, 5)

    wep.clusterPanels[key] = plist
    return plist
end

-- ── RefreshModuleList ─────────────────────────────────────────
-- Rebuilds mlist for the current selectedCluster.
-- Also re-expands clusters into VElements so preview is live.
function RefreshModuleList()
    mlist:Clear()
    local cluster = wep.clusters[selectedCluster] or {}
    for i, mod in ipairs(cluster) do
        mlist:AddLine(tostring(i), mod.name or "?", mod.type or "Model",
            mod.model and string.match(mod.model, "([^/]+)%.mdl$") or "")
    end

    -- Live-expand into VElements/WElements for in-game preview
    if wep.WeaponClusters ~= wep.clusters then
        wep.WeaponClusters = wep.clusters
    end
    if SCK_ExpandClusters then
        wep.VElements = {}
        wep.WElements = {}
        SCK_ExpandClusters(wep)
        wep:CreateModels(wep.VElements)
        wep:CreateModels(wep.WElements)
    end
end

-- ── Module list selection ─────────────────────────────────────
mlist.OnRowSelected = function(panel, line)
    local cluster = wep.clusters[selectedCluster]
    if not cluster then return end
    local idx = tonumber(mlist:GetLine(line):GetValue(1))
    if not idx then return end
    selectedIdx = idx

    if lastModPanel and IsValid(lastModPanel) then
        lastModPanel:SetVisible(false)
    end
    local mpanel = CreateModPanel(cluster, idx)
    if mpanel then
        mpanel:SetVisible(true)
        lastModPanel = mpanel
    end
end

-- ── Add ──────────────────────────────────────────────────────
addBtn.DoClick = function()
    local name = string.Trim(addNameEntry:GetValue())
    if name == "" then ClusterNote("Name cannot be empty"); return end
    local cluster = wep.clusters[selectedCluster]
    for _, m in ipairs(cluster) do
        if m.name == name then ClusterNote("Name already exists in this cluster"); return end
    end
    local modType = typeBox:GetValue()
    local newMod = {
        name  = name,
        slot  = #cluster + 1,
        type  = modType,
        model = "",
        bone  = "ValveBiped.Bip01_R_Hand",
        rel   = "",
        pos   = Vector(0,0,0),
        angle = Angle(0,0,0),
        size  = Vector(1,1,1),
        color = Color(255,255,255,255),
        material = "", skin = 0, bodygroup = {},
    }
    table.insert(cluster, newMod)
    RefreshModuleList()
    -- Select the new row
    mlist:SetDirty(true)
    for _, line in pairs(mlist:GetLines()) do
        if line:GetValue(2) == name then line:SetSelected(true); break end
    end
end

-- ── Remove ───────────────────────────────────────────────────
removeBtn.DoClick = function()
    if not selectedIdx then return end
    local cluster = wep.clusters[selectedCluster]
    if lastModPanel and IsValid(lastModPanel) then
        lastModPanel:Remove()
        lastModPanel = nil
    end
    -- Clear cached panel for this index
    if wep.clusterPanels then
        wep.clusterPanels[selectedCluster .. "/" .. selectedIdx] = nil
    end
    table.remove(cluster, selectedIdx)
    -- Re-number slots
    for i, m in ipairs(cluster) do m.slot = i end
    selectedIdx = nil
    RefreshModuleList()
end

-- ── Copy ─────────────────────────────────────────────────────
copyBtn.DoClick = function()
    if not selectedIdx then return end
    local cluster = wep.clusters[selectedCluster]
    local src     = cluster[selectedIdx]
    if not src then return end
    local dup = table.FullCopy(src)
    dup.name = src.name .. "_copy"
    dup.slot = #cluster + 1
    table.insert(cluster, dup)
    RefreshModuleList()
end

-- ── Move up / down ───────────────────────────────────────────
mvUpBtn.DoClick = function()
    if not selectedIdx or selectedIdx <= 1 then return end
    local cluster = wep.clusters[selectedCluster]
    cluster[selectedIdx], cluster[selectedIdx - 1] = cluster[selectedIdx - 1], cluster[selectedIdx]
    for i, m in ipairs(cluster) do m.slot = i end
    selectedIdx = selectedIdx - 1
    RefreshModuleList()
end

mvDnBtn.DoClick = function()
    local cluster = wep.clusters[selectedCluster]
    if not selectedIdx or selectedIdx >= #cluster then return end
    cluster[selectedIdx], cluster[selectedIdx + 1] = cluster[selectedIdx + 1], cluster[selectedIdx]
    for i, m in ipairs(cluster) do m.slot = i end
    selectedIdx = selectedIdx + 1
    RefreshModuleList()
end

-- ── Export buttons row ────────────────────────────────────────
local expRow = vgui.Create("DPanel", pclusters)
    expRow:SetTall(28)
    expRow:SetDrawBackground(false)
expRow:DockMargin(0, 6, 0, 0)
expRow:Dock(BOTTOM)

local genBtn = vgui.Create("DButton", expRow)
    genBtn:SetText("Generate from V/W Elements")
    genBtn:SetTall(28)
    genBtn.DoClick = function()
        if not wep.v_models then return end
        wep.clusters = SCK_VElementsToClusters(wep.v_models)
        for _, cn in ipairs(SCK_CLUSTER_ORDER) do
            if not wep.clusters[cn] then wep.clusters[cn] = {} end
        end
        RefreshModuleList()
        ClusterNote("Imported from V elements")
    end
genBtn:Dock(LEFT)
genBtn:SetWide(200)

local expVBtn = vgui.Create("DButton", expRow)
    expVBtn:SetText("Export Clusters Code")
    expVBtn:SetTall(28)
    expVBtn.DoClick = function()
        local text = SCK_GetClustersText(wep.clusters)
        SetClipboardText(text)
        LocalPlayer():ChatPrint("[SCK] Cluster code copied to clipboard!")
    end
expVBtn:DockMargin(4, 0, 0, 0)
expVBtn:Dock(LEFT)
expVBtn:SetWide(160)

local expAllBtn = vgui.Create("DButton", expRow)
    expAllBtn:SetText("Export Full SWEP")
    expAllBtn:SetTall(28)
    expAllBtn.DoClick = function()
        -- Combined: clusters + VElements + WElements + weapon settings
        local parts = {}
        parts[#parts+1] = SCK_GetClustersText(wep.clusters)
        -- Optionally append VElements/WElements for non-cluster weapons
        if wep.v_models and next(wep.v_models) then
            local vt = "SWEP.VElements = {\n"
            for k, v in pairs(wep.v_models) do
                if v.type == "Model" then
                    vt = vt .. string.format('\t["%s"] = { type="Model", model="%s", bone="%s" },\n',
                        k, v.model or "", v.bone or "")
                end
            end
            vt = vt .. "}"
            parts[#parts+1] = vt
        end
        parts[#parts+1] = string.format(
            'SWEP.HoldType = "%s"\nSWEP.ViewModelFOV = %d\nSWEP.ViewModel = "%s"\nSWEP.WorldModel = "%s"',
            wep.HoldType or "physgun", wep.ViewModelFOV or 70,
            wep.ViewModel or "", wep.CurWorldModel or "")
        SetClipboardText(table.concat(parts, "\n\n"))
        LocalPlayer():ChatPrint("[SCK] Full SWEP code copied to clipboard!")
    end
expAllBtn:DockMargin(4, 0, 0, 0)
expAllBtn:Dock(LEFT)
expAllBtn:SetWide(130)

-- ── Load preset clusters if available ─────────────────────────
if wep.save_data and wep.save_data.clusters then
    wep.clusters = wep.save_data.clusters
    for _, cn in ipairs(SCK_CLUSTER_ORDER) do
        if not wep.clusters[cn] then wep.clusters[cn] = {} end
    end
end

-- ── Initial render ────────────────────────────────────────────
RefreshModuleList()
