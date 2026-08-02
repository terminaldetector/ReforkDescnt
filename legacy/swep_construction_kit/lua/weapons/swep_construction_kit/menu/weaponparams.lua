-- Weapon Stats tab — game parameters editor.
-- Parent panel: wep.pstats (created in client.lua CreateMenu)

local wep   = GetSCKSWEP(LocalPlayer())
local panel = wep.pstats

local function CollectConfig()
	local cfg = table.FullCopy(wep.weaponconfig)
	cfg.class    = cfg.weapon and cfg.weapon.ClassName or "weapon_d6_custom"
	cfg.clusters = table.FullCopy(wep.clusters or {})
	return cfg
end

local function GetClass()
	return (wep.weaponconfig.weapon and wep.weaponconfig.weapon.ClassName) or "weapon_d6_custom"
end

local function onChange(key, value)
	if SCK_Config then
		SCK_Config.Set(GetClass(), "weapon", key, value)
	end
end

-- ── Generation / preset bar (BOTTOM docked — must be added before FILL) ─────

local bar = vgui.Create("DPanel", panel)
bar:SetTall(84)
bar:SetDrawBackground(false)
bar:DockMargin(0, 4, 0, 0)
bar:Dock(BOTTOM)

-- Row 1: Save / Load / Clone
local row1 = vgui.Create("DPanel", bar)
row1:SetTall(26)
row1:SetDrawBackground(false)
row1:DockMargin(0, 0, 0, 2)
row1:Dock(TOP)

local nameEntry = vgui.Create("DTextEntry", row1)
nameEntry:SetValue(wep.save_data._savename or "custom1")
nameEntry:SetWide(100)
nameEntry:Dock(LEFT)

local function SavePreset(name)
	local sd = wep:SCK_CollectSaveData(name)
	if not sd then return end
	local ok, encoded = pcall(glon.encode, sd)
	if ok and encoded then
		file.Write("swep_construction_kit/" .. name .. ".txt", encoded)
		LocalPlayer():ChatPrint("[SCK] Saved: " .. name)
	else
		LocalPlayer():ChatPrint("[SCK] Save failed.")
	end
end

local saveBtn = vgui.Create("DButton", row1)
saveBtn:SetText("Save")
saveBtn:SetWide(52)
saveBtn:DockMargin(2, 0, 0, 0)
saveBtn:Dock(LEFT)
saveBtn.DoClick = function()
	local name = string.Trim(nameEntry:GetValue())
	if name == "" then return end
	SavePreset(name)
end

local loadBtn = vgui.Create("DButton", row1)
loadBtn:SetText("Load")
loadBtn:SetWide(52)
loadBtn:DockMargin(2, 0, 0, 0)
loadBtn:Dock(LEFT)
loadBtn.DoClick = function()
	local name = string.Trim(nameEntry:GetValue())
	if name == "" then return end
	local path = "swep_construction_kit/" .. name .. ".txt"
	if not file.Exists(path, "DATA") then
		LocalPlayer():ChatPrint("[SCK] Not found: " .. name)
		return
	end
	local raw = file.Read(path, "DATA")
	local ok, preset = pcall(glon.decode, raw)
	if ok and preset then
		wep:CleanMenu()
		wep:OpenMenu(preset)
	else
		LocalPlayer():ChatPrint("[SCK] Load failed.")
	end
end

local cloneBtn = vgui.Create("DButton", row1)
cloneBtn:SetText("Clone")
cloneBtn:SetWide(52)
cloneBtn:DockMargin(2, 0, 0, 0)
cloneBtn:Dock(LEFT)
cloneBtn.DoClick = function()
	local name = string.Trim(nameEntry:GetValue())
	if name == "" then return end
	local path = "swep_construction_kit/" .. name .. ".txt"
	if not file.Exists(path, "DATA") then return end
	local raw  = file.Read(path, "DATA")
	local copy = name .. "_copy"
	file.Write("swep_construction_kit/" .. copy .. ".txt", raw)
	nameEntry:SetValue(copy)
	LocalPlayer():ChatPrint("[SCK] Cloned to: " .. copy)
end

-- Row 2: Templates
local row2 = vgui.Create("DPanel", bar)
row2:SetTall(26)
row2:SetDrawBackground(false)
row2:DockMargin(0, 0, 0, 2)
row2:Dock(TOP)

local TEMPLATES = {
	{ label = "Cannon",
	  weapon = { PrintName="Cannon", WeaponType="projectile", Category="Primary", FireRate=0.8, Damage=80, EnergyCost=18 },
	  projectile = { InitialVelocity=4000, Mass=10, Scale=1.2, Lifetime=5, Gravity=false } },
	{ label = "Missile",
	  weapon = { PrintName="Missile", WeaponType="projectile", Category="Secondary", FireRate=2.0, Damage=90, EnergyCost=28, SplashRadius=200 },
	  projectile = { InitialVelocity=1400, Mass=20, Scale=1.5, Lifetime=15, Homing=true },
	  guidance = { GuidanceType="target", TargetSeeking=true, LockTime=0.5, TrackingCone=60 } },
	{ label = "Flak",
	  weapon = { PrintName="Flak", WeaponType="projectile", Category="Primary", FireRate=1.2, Damage=20, EnergyCost=15 },
	  flak = { FragmentCount=30, SpreadAngle=40, DetonateTime=0.5, FragmentDamage=15 } },
	{ label = "Seeker",
	  weapon = { PrintName="Seeker", WeaponType="projectile", Category="Secondary", FireRate=3.5, Damage=60, EnergyCost=22 },
	  guidance = { GuidanceType="heat", HeatSeeking=true, LockTime=1.0, TrackingCone=60, MaxAngularVelocity=90 } },
}

local tmplCombo = vgui.Create("DComboBox", row2)
tmplCombo:SetWide(100)
tmplCombo:SetValue("Template...")
for _, t in ipairs(TEMPLATES) do tmplCombo:AddChoice(t.label) end
tmplCombo:Dock(LEFT)

local applyBtn = vgui.Create("DButton", row2)
applyBtn:SetText("Apply Template")
applyBtn:SetWide(105)
applyBtn:DockMargin(2, 0, 0, 0)
applyBtn:Dock(LEFT)
applyBtn.DoClick = function()
	local _, label = tmplCombo:GetSelected()
	if not label then return end
	for _, t in ipairs(TEMPLATES) do
		if t.label == label then
			for k, v in pairs(t.weapon     or {}) do wep.weaponconfig.weapon[k]     = v end
			for k, v in pairs(t.projectile or {}) do wep.weaponconfig.projectile[k] = v end
			for k, v in pairs(t.flak       or {}) do wep.weaponconfig.flak[k]       = v end
			for k, v in pairs(t.guidance   or {}) do wep.weaponconfig.guidance[k]   = v end
			wep:CleanMenu()
			wep:OpenMenu(wep.save_data)
			break
		end
	end
end

-- Row 3: Generate
local row3 = vgui.Create("DPanel", bar)
row3:SetTall(26)
row3:SetDrawBackground(false)
row3:Dock(TOP)

local genBtn = vgui.Create("DButton", row3)
genBtn:SetText("Copy SWEP Lua")
genBtn:SetWide(120)
genBtn:Dock(LEFT)
genBtn.DoClick = function()
	if not SCK_GenerateSWEP then
		LocalPlayer():ChatPrint("[SCK] Generator not loaded.")
		return
	end
	SCK_GenerateSWEP(CollectConfig())
	LocalPlayer():ChatPrint("[SCK] SWEP Lua → clipboard + DATA/drmd_weapons/generated/")
end

local writeBtn = vgui.Create("DButton", row3)
writeBtn:SetText("Write SWEP file")
writeBtn:SetWide(105)
writeBtn:DockMargin(2, 0, 0, 0)
writeBtn:Dock(LEFT)
writeBtn.DoClick = function()
	if not SCK_GenerateSWEP then return end
	local cfg = CollectConfig()
	SCK_GenerateSWEP(cfg)
	LocalPlayer():ChatPrint("[SCK] Written: DATA/drmd_weapons/generated/" .. cfg.class .. ".lua")
end

-- ── Schema scroll panel (FILL) ───────────────────────────────────────────────

SCK_BuildSchemaPanel(panel, SCK_SCHEMA.weapon, wep.weaponconfig.weapon, onChange, "weapon")
