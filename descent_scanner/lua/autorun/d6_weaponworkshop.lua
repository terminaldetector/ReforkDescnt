-- ═══════════════════════════════════════════════════════════
-- d6_weaponworkshop.lua — DRMD Weapon Workshop Integration
--
--   Registers the DRMD Weapon Workshop in the GMod Q menu.
--   Provides a Weapon Constructor panel that:
--     • Lists saved SCK presets (DATA/swep_construction_kit/*.txt)
--     • Spawns the SCK weapon with a preset pre-loaded
--     • Batch-exports selected presets as SWEP Lua code
--     • Shows built-in DRMD weapon archetypes for quick start
--
--   Dependency: SWEP Construction Kit addon must be installed.
--   If SCK is absent, this file does nothing (graceful degradation).
--
--   CLIENT-ONLY — this file is in autorun/ so it runs on both
--   sides; the active logic is CLIENT-guarded.
-- ═══════════════════════════════════════════════════════════
if not CLIENT then return end

-- Graceful: only activate if SCK is present
local function SCKPresent()
    return file.Exists("lua/weapons/swep_construction_kit/shared.lua", "GAME")
end

-- ── Built-in DRMD Archetypes ─────────────────────────────────
-- Each archetype is a minimal WeaponClusters skeleton that the
-- player can use as a starting point in the SCK Clusters tab.
local D6_ARCHETYPES = {
    {
        label = "Assault Drone",
        desc  = "Dual forward cannons (Center), upper sensor dome (Upper)",
        clusters = {
            Center = {
                { name="barrel_l", slot=1, model="models/weapons/w_pistol.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0,-8,-4), angle=Angle(0,0,0), size=Vector(0.8,0.8,0.8) },
                { name="barrel_r", slot=2, model="models/weapons/w_pistol.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0, 8,-4), angle=Angle(0,0,0), size=Vector(0.8,0.8,0.8) },
            },
            Upper = {
                { name="dome", slot=1, model="models/props_combine/combine_generator01.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0,0,14), angle=Angle(0,0,0), size=Vector(0.3,0.3,0.3) },
            },
            SideLeft={}, SideRight={}, Lower={},
        },
    },
    {
        label = "Heavy Gunship",
        desc  = "Central main gun (Center), missile pods on sides (SideLeft/Right)",
        clusters = {
            Center = {
                { name="main_gun", slot=1, model="models/weapons/w_rocket_launcher.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0,0,-6), angle=Angle(0,0,0), size=Vector(1,1,1) },
            },
            SideLeft = {
                { name="pod_l", slot=1, model="models/weapons/w_missile.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0,-18,0), angle=Angle(0,0,0), size=Vector(0.9,0.9,0.9) },
            },
            SideRight = {
                { name="pod_r", slot=1, model="models/weapons/w_missile.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0, 18,0), angle=Angle(0,0,0), size=Vector(0.9,0.9,0.9) },
            },
            Upper={}, Lower={},
        },
    },
    {
        label = "Interceptor",
        desc  = "Slim energy cannon (Center), forward fins (Lower)",
        clusters = {
            Center = {
                { name="cannon", slot=1, model="models/weapons/w_crossbow.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(0,0,-2), angle=Angle(0,0,0), size=Vector(1.1,1.1,1.1) },
            },
            Lower = {
                { name="fin_l", slot=1, model="models/props_combine/combinetrain01a.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(-20,-12,-8), angle=Angle(0,0,45), size=Vector(0.1,0.1,0.1) },
                { name="fin_r", slot=2, model="models/props_combine/combinetrain01a.mdl",
                  bone="ValveBiped.Bip01_R_Hand",
                  pos=Vector(-20, 12,-8), angle=Angle(0,0,-45), size=Vector(0.1,0.1,0.1) },
            },
            Upper={}, SideLeft={}, SideRight={},
        },
    },
}

-- ── Q Menu / Tool Menu Registration ─────────────────────────

hook.Add("AddToolMenuTabs", "D6_WeaponWorkshop", function()
    spawnmenu.AddToolTab("DRMD Workshop", "DRMD Workshop", "icon16/bomb.png")
end)

hook.Add("AddToolMenuCategories", "D6_WeaponWorkshop", function()
    spawnmenu.AddToolCategory("DRMD Workshop", "WeaponConstructor", "Weapon Constructor")
    spawnmenu.AddToolCategory("DRMD Workshop", "Archetypes",        "Weapon Archetypes")
    spawnmenu.AddToolCategory("DRMD Workshop", "WeaponConfigs",     "Generated Weapons")
end)

hook.Add("PopulateToolMenu", "D6_WeaponWorkshop", function()

    -- ── Weapon Constructor panel ─────────────────────────────
    spawnmenu.AddToolMenuOption("DRMD Workshop", "WeaponConstructor",
        "d6_wep_constructor", "Weapon Constructor", "", "", function(panel)
        panel:ClearControls()

        if not SCKPresent() then
            panel:Help("SWEP Construction Kit is not installed.")
            panel:Help("Install SCK addon, then reload the game.")
            return
        end

        panel:Help("Saved SCK Presets  (DATA/swep_construction_kit/)")

        -- List saved presets
        local files = file.Find("swep_construction_kit/*.txt", "DATA")
        if files and #files > 0 then
            for _, fname in ipairs(files) do
                local presetName = fname:gsub("%.txt$", "")
                local btn = panel:Button("Open preset: " .. presetName)
                btn.DoClick = function()
                    local ply = LocalPlayer()
                    local wep = ply:GetActiveWeapon()
                    if IsValid(wep) and wep.IsSCK then
                        -- Load this preset directly if SCK is active
                        local glondata = file.Read("swep_construction_kit/" .. fname, "DATA")
                        if glondata then
                            -- glon is a local inside SCK's scope; use util.JSONToTable as fallback
                            local decodeFn = (glon and glon.decode) or (wep.glon and wep.glon.decode)
                            if decodeFn then
                                local succ, preset = pcall(decodeFn, glondata)
                                if succ and preset then
                                    wep:CleanMenu()
                                    wep:OpenMenu(preset)
                                    return
                                end
                            end
                        end
                    end
                    -- Otherwise give the player SCK first
                    RunConsoleCommand("give", "swep_construction_kit")
                    chat.AddText(Color(255,200,80), "[DRMD Workshop] SCK given. Open menu then load preset: " .. presetName)
                end
            end
        else
            panel:Help("No saved presets found yet.")
            panel:Help("Use the SCK weapon (Slot 6) to build and save weapon presets.")
        end

        panel:Help(" ")
        panel:Help("Batch Export")
        local expBtn = panel:Button("Export All Presets to Console")
        expBtn.DoClick = function()
            local expFiles = file.Find("swep_construction_kit/*.txt", "DATA")
            if not expFiles or #expFiles == 0 then
                chat.AddText(Color(255,60,60), "[DRMD Workshop] No presets to export.")
                return
            end
            MsgN("\n-- DRMD Weapon Workshop: Batch Export --")
            for _, fname in ipairs(expFiles) do
                local name    = fname:gsub("%.txt$", "")
                local gdata   = file.Read("swep_construction_kit/" .. fname, "DATA")
                local decodeFn = glon and glon.decode
                local ok, pre
                if decodeFn then ok, pre = pcall(decodeFn, gdata) end
                if ok and pre then
                    MsgN("\n-- Preset: " .. name)
                    if pre.clusters and SCK_GetClustersText then
                        MsgN(SCK_GetClustersText(pre.clusters))
                    end
                    if pre.v_models then
                        MsgN("-- VElements for: " .. name)
                        -- Minimal dump
                        for k, v in pairs(pre.v_models) do
                            MsgN('  ["' .. k .. '"] = { type="' .. (v.type or "Model") ..
                                 '", model="' .. (v.model or "") .. '" }')
                        end
                    end
                end
            end
            MsgN("-- End of batch export --\n")
            chat.AddText(Color(80,255,80), "[DRMD Workshop] Exported to console.")
        end
    end)

    -- ── Archetypes panel ─────────────────────────────────────
    spawnmenu.AddToolMenuOption("DRMD Workshop", "Archetypes",
        "d6_archetypes", "Weapon Archetypes", "", "", function(panel)
        panel:ClearControls()

        if not SCKPresent() then
            panel:Help("SWEP Construction Kit is not installed.")
            return
        end

        panel:Help("Built-in DRMD Weapon Archetypes")
        panel:Help("Click to load into SCK editor (SCK weapon must be equipped).")
        panel:Help(" ")

        for _, arch in ipairs(D6_ARCHETYPES) do
            panel:Help(arch.label .. " — " .. arch.desc)
            local btn = panel:Button("Load: " .. arch.label)
            local archCopy = arch  -- closure capture
            btn.DoClick = function()
                local ply = LocalPlayer()
                local wep = ply:GetActiveWeapon()
                if not IsValid(wep) or not wep.IsSCK then
                    RunConsoleCommand("give", "swep_construction_kit")
                    -- Store pending archetype and load on next frame
                    timer.Simple(0.5, function()
                        wep = ply:GetActiveWeapon()
                        if not IsValid(wep) or not wep.IsSCK then return end
                        wep.clusters = table.FullCopy(archCopy.clusters)
                        if not wep.Frame then wep:OpenMenu() end
                        if wep.Frame then wep.Frame:SetVisible(true) end
                        chat.AddText(Color(255,220,40), "[DRMD Workshop] Loaded archetype: " .. archCopy.label)
                    end)
                    return
                end
                wep.clusters = table.FullCopy(archCopy.clusters)
                if not wep.Frame then wep:OpenMenu() end
                if wep.Frame then wep.Frame:SetVisible(true) end
                chat.AddText(Color(255,220,40), "[DRMD Workshop] Loaded archetype: " .. archCopy.label)
            end
            panel:Help(" ")
        end
    end)

    -- ── Generated Weapons panel ──────────────────────────────
    spawnmenu.AddToolMenuOption("DRMD Workshop", "WeaponConfigs",
        "d6_weapon_configs", "Generated Weapons", "", "", function(panel)
        panel:ClearControls()

        if not SCKPresent() then
            panel:Help("SWEP Construction Kit is not installed.")
            return
        end

        panel:Help("Generated SWEP files  (DATA/drmd_weapons/generated/)")
        panel:Help("Open SCK Slot 6 → Stats tab → 'Copy SWEP Lua' to generate one.")
        panel:Help(" ")

        local genFiles = file.Find("drmd_weapons/generated/*.lua", "DATA")
        if genFiles and #genFiles > 0 then
            for _, fname in ipairs(genFiles) do
                panel:Help("• " .. fname:gsub("%.lua$", ""))
            end
        else
            panel:Help("No generated weapons yet.")
        end

        panel:Help(" ")
        panel:Help("Live Configs  (DATA/drmd_weapons/)")

        local cfgFiles = file.Find("drmd_weapons/*.txt", "DATA")
        if cfgFiles and #cfgFiles > 0 then
            for _, fname in ipairs(cfgFiles) do
                local class = fname:gsub("%.txt$", "")
                local btn = panel:Button("Load config: " .. class)
                btn.DoClick = function()
                    if not SCK_Config then
                        chat.AddText(Color(255,60,60), "[DRMD Workshop] SCK_Config not available.")
                        return
                    end
                    SCK_Config.LoadForClass(class)
                    chat.AddText(Color(80,255,80), "[DRMD Workshop] Loaded config: " .. class)
                end
            end
        else
            panel:Help("No saved configs yet.")
            panel:Help("Edit values in SCK Stats/Projectile/Flak/Guidance tabs to create one.")
        end
    end)

end)

-- ── Spawnmenu content tab ─────────────────────────────────────
-- Adds a "DRMD Weapon Workshop" entry in the Browse → Other tab.
hook.Add("PopulateContent", "D6_WeaponWorkshop_Content", function(pnlContent, tree, node)
    local d6node = tree:AddNode("DRMD Weapon Workshop", "icon16/bomb.png")
    d6node.DoClick = function()
        pnlContent:Clear(true)

        local p = vgui.Create("DScrollPanel", pnlContent)
        p:Dock(FILL)

        local function AddHeader(text)
            local lbl = vgui.Create("DLabel", p)
                lbl:SetText(text)
                lbl:SetFont("DermaDefaultBold")
                lbl:SetTall(22)
                lbl:SizeToContentsX()
            lbl:DockMargin(8, 8, 8, 2)
            lbl:Dock(TOP)
        end

        local function AddLine(text)
            local lbl = vgui.Create("DLabel", p)
                lbl:SetText(text)
                lbl:SetTall(18)
                lbl:SizeToContentsX()
            lbl:DockMargin(16, 2, 8, 0)
            lbl:Dock(TOP)
        end

        AddHeader("DRMD 6DOF — Weapon Workshop")
        if not SCKPresent() then
            AddLine("SWEP Construction Kit is not installed.")
            AddLine("Install SCK to unlock the weapon constructor.")
        else
            AddHeader("Saved Presets")
            local sfiles = file.Find("swep_construction_kit/*.txt", "DATA")
            if sfiles and #sfiles > 0 then
                for _, fn in ipairs(sfiles) do
                    AddLine("• " .. fn:gsub("%.txt$", ""))
                end
            else
                AddLine("(none — use Q → DRMD Workshop → Weapon Constructor)")
            end

            AddHeader("Weapon Archetypes")
            for _, a in ipairs(D6_ARCHETYPES) do
                AddLine("• " .. a.label .. " — " .. a.desc)
            end
        end
    end
end)

print("[D6] d6_weaponworkshop.lua loaded")
