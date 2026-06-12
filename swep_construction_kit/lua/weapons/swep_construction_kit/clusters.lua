-- ═══════════════════════════════════════════════════════════
-- clusters.lua — SCK Weapon Cluster System (CLIENT)
--
-- Provides the WeaponClusters declaration format as an
-- alternative to raw VElements / WElements tables.
-- SCK-independent: zero dependency on DRMD or any other addon.
--
-- Cluster order:  Center → Upper → SideLeft → SideRight → Lower
-- Element keys:   "<ClusterName>_<module.name>"
-- Render order:   deterministic — cluster order, then slot order
--                 within each cluster; Models always before Sprites.
-- ═══════════════════════════════════════════════════════════

if not CLIENT then return end

-- table.FullCopy задаётся в base_code.lua, но тот файл исполняется
-- только в экспортированных SWEP. В контексте редактора (shared.lua →
-- clusters.lua → client.lua → menu/*) определяем его здесь, guarded.
if not table.FullCopy then
    function table.FullCopy(tab)
        if not tab then return nil end
        local res = {}
        for k, v in pairs(tab) do
            if type(v) == "table" then
                res[k] = table.FullCopy(v)
            elseif type(v) == "Vector" then
                res[k] = Vector(v.x, v.y, v.z)
            elseif type(v) == "Angle" then
                res[k] = Angle(v.p, v.y, v.r)
            else
                res[k] = v
            end
        end
        return res
    end
end

-- ── Public constants ─────────────────────────────────────────

SCK_CLUSTER_ORDER = { "Center", "Upper", "SideLeft", "SideRight", "Lower" }

SCK_CLUSTER_COLORS = {
    Center    = Color(255, 220, 40),
    Upper     = Color(180, 80,  255),
    SideLeft  = Color(80,  140, 255),
    SideRight = Color(80,  140, 255),
    Lower     = Color(255, 60,  60),
}

SCK_CLUSTER_LABELS = {
    Center    = "Center     (yellow — main weapons)",
    Upper     = "Upper      (purple — heavy / missiles)",
    SideLeft  = "Side Left  (blue — support)",
    SideRight = "Side Right (blue — support)",
    Lower     = "Lower      (red — special)",
}

-- ── SCK_ExpandClusters ───────────────────────────────────────
-- Called from SWEP:Initialize() on CLIENT, after table.FullCopy
-- of VElements / WElements, before CreateModels().
-- Reads swep.WeaponClusters and populates:
--   swep.VElements, swep.WElements
--   swep.vRenderOrder, swep.wRenderOrder
function SCK_ExpandClusters(swep)
    local clusters = swep.WeaponClusters
    if not clusters then return end

    if not swep.VElements then swep.VElements = {} end
    if not swep.WElements then swep.WElements = {} end

    local vModels, vFX = {}, {}
    local wModels, wFX = {}, {}

    for _, cname in ipairs(SCK_CLUSTER_ORDER) do
        local cluster = clusters[cname]
        if not cluster then continue end

        -- Sort by slot ascending so render order is deterministic
        table.sort(cluster, function(a, b)
            return (a.slot or 0) < (b.slot or 0)
        end)

        for _, mod in ipairs(cluster) do
            local key = cname .. "_" .. (mod.name or "mod")

            -- Auto-prefix relative references that don't already have a cluster prefix
            local relKey = ""
            if mod.rel and mod.rel ~= "" then
                if string.find(mod.rel, "_", 1, true) then
                    relKey = mod.rel
                else
                    relKey = cname .. "_" .. mod.rel
                end
            end

            local elem = {
                type              = mod.type or "Model",
                model             = mod.model or "",
                bone              = mod.bone or "ValveBiped.Bip01_R_Hand",
                rel               = relKey,
                pos               = mod.pos   and Vector(mod.pos.x,   mod.pos.y,   mod.pos.z)   or Vector(0,0,0),
                angle             = mod.angle and Angle( mod.angle.p,  mod.angle.y,  mod.angle.r) or Angle(0,0,0),
                size              = mod.size  and Vector(mod.size.x,  mod.size.y,  mod.size.z)  or Vector(1,1,1),
                color             = mod.color and Color(mod.color.r, mod.color.g, mod.color.b, mod.color.a) or Color(255,255,255,255),
                surpresslightning = mod.surpresslightning or false,
                material          = mod.material or "",
                skin              = mod.skin or 0,
                bodygroup         = mod.bodygroup or {},
                -- Optional extensions (animator.lua / attachment points)
                anim              = mod.anim   and table.Copy(mod.anim)   or nil,
                attach            = mod.attach and table.Copy(mod.attach) or nil,
                -- Metadata preserved for editor round-trips
                _cluster          = cname,
                _slot             = mod.slot or 0,
                _name             = mod.name or "mod",
            }

            swep.VElements[key] = elem
            swep.WElements[key] = table.FullCopy(elem)

            if elem.type == "Model" then
                table.insert(vModels, key)
                table.insert(wModels, key)
            else
                table.insert(vFX, key)
                table.insert(wFX, key)
            end
        end
    end

    -- Build render order: Models (cluster order) then Sprites/Quads
    swep.vRenderOrder = {}
    swep.wRenderOrder = {}
    for _, k in ipairs(vModels) do table.insert(swep.vRenderOrder, k) end
    for _, k in ipairs(vFX)     do table.insert(swep.vRenderOrder, k) end
    for _, k in ipairs(wModels) do table.insert(swep.wRenderOrder, k) end
    for _, k in ipairs(wFX)     do table.insert(swep.wRenderOrder, k) end
end

-- ── SCK_GetClustersText ──────────────────────────────────────
-- Serialises a clusters table back to Lua source code.
-- Used by the Clusters editor tab "Export" button.
function SCK_GetClustersText(clusters)
    if not clusters then return "SWEP.WeaponClusters = nil" end

    local out = { "SWEP.WeaponClusters = {" }

    for _, cname in ipairs(SCK_CLUSTER_ORDER) do
        local cluster = clusters[cname]
        if not cluster or #cluster == 0 then continue end

        table.insert(out, "\t" .. cname .. " = {")

        for _, mod in ipairs(cluster) do
            local parts = {}
            parts[#parts+1] = 'name = "' .. (mod.name or "") .. '"'
            parts[#parts+1] = "slot = " .. (mod.slot or 1)

            if mod.type and mod.type ~= "Model" then
                parts[#parts+1] = 'type = "' .. mod.type .. '"'
            end
            if mod.model and mod.model ~= "" then
                parts[#parts+1] = 'model = "' .. mod.model .. '"'
            end
            if mod.bone and mod.bone ~= "ValveBiped.Bip01_R_Hand" then
                parts[#parts+1] = 'bone = "' .. mod.bone .. '"'
            end
            if mod.rel and mod.rel ~= "" then
                parts[#parts+1] = 'rel = "' .. mod.rel .. '"'
            end
            if mod.pos then
                parts[#parts+1] = string.format("pos = Vector(%.3f, %.3f, %.3f)", mod.pos.x, mod.pos.y, mod.pos.z)
            end
            if mod.angle then
                parts[#parts+1] = string.format("angle = Angle(%.3f, %.3f, %.3f)", mod.angle.p, mod.angle.y, mod.angle.r)
            end
            if mod.size and (mod.size.x ~= 1 or mod.size.y ~= 1 or mod.size.z ~= 1) then
                parts[#parts+1] = string.format("size = Vector(%.3f, %.3f, %.3f)", mod.size.x, mod.size.y, mod.size.z)
            end
            if mod.color and (mod.color.r ~= 255 or mod.color.g ~= 255 or mod.color.b ~= 255 or mod.color.a ~= 255) then
                parts[#parts+1] = string.format("color = Color(%d, %d, %d, %d)", mod.color.r, mod.color.g, mod.color.b, mod.color.a)
            end
            if mod.material and mod.material ~= "" then
                parts[#parts+1] = 'material = "' .. mod.material .. '"'
            end
            if mod.skin and mod.skin ~= 0 then
                parts[#parts+1] = "skin = " .. mod.skin
            end
            if istable(mod.anim) then
                local ap = {}
                for _, k in ipairs({ "spin", "spinAxis", "bobAmp", "bobFreq",
                                     "swayMult", "kick", "kickRecover" }) do
                    local val = mod.anim[k]
                    if val ~= nil then
                        if type(val) == "string" then
                            ap[#ap+1] = k .. ' = "' .. val .. '"'
                        else
                            ap[#ap+1] = k .. " = " .. tostring(val)
                        end
                    end
                end
                if #ap > 0 then
                    parts[#parts+1] = "anim = { " .. table.concat(ap, ", ") .. " }"
                end
            end
            if istable(mod.attach) and mod.attach.muzzle then
                parts[#parts+1] = "attach = { muzzle = true, idx = "
                    .. (mod.attach.idx or 1) .. " }"
            end

            table.insert(out, "\t\t{ " .. table.concat(parts, ", ") .. " },")
        end

        table.insert(out, "\t},")
    end

    table.insert(out, "}")
    return table.concat(out, "\n")
end

-- ── SCK_VElementsToClusters ──────────────────────────────────
-- Converts existing v_models (from the V/W element editors) into
-- a WeaponClusters table by examining element name prefixes.
-- Used by "Generate from V/W Elements" in the Clusters editor.
-- Elements whose names begin with a cluster name (case-insensitive)
-- are placed into that cluster; all others go into Center.
function SCK_VElementsToClusters(v_models)
    local result = {}
    for _, cn in ipairs(SCK_CLUSTER_ORDER) do result[cn] = {} end

    local slot_counters = {}
    for _, cn in ipairs(SCK_CLUSTER_ORDER) do slot_counters[cn] = 0 end

    for elemName, v in pairs(v_models) do
        local placed = false
        for _, cn in ipairs(SCK_CLUSTER_ORDER) do
            if string.lower(string.sub(elemName, 1, #cn)) == string.lower(cn) then
                local shortName = string.sub(elemName, #cn + 2)  -- strip "ClusterName_"
                if shortName == "" then shortName = elemName end
                slot_counters[cn] = slot_counters[cn] + 1
                table.insert(result[cn], {
                    name  = shortName,
                    slot  = slot_counters[cn],
                    type  = v.type or "Model",
                    model = v.model or "",
                    bone  = v.bone or "ValveBiped.Bip01_R_Hand",
                    rel   = v.rel or "",
                    pos   = v.pos   and Vector(v.pos.x,   v.pos.y,   v.pos.z)   or Vector(0,0,0),
                    angle = v.angle and Angle( v.angle.p,  v.angle.y,  v.angle.r) or Angle(0,0,0),
                    size  = v.size  and Vector(v.size.x,  v.size.y,  v.size.z)  or Vector(1,1,1),
                    color = v.color and Color(v.color.r, v.color.g, v.color.b, v.color.a) or Color(255,255,255,255),
                    material = v.material or "",
                    skin     = v.skin or 0,
                    bodygroup = v.bodygroup or {},
                    anim     = v.anim   and table.Copy(v.anim)   or nil,
                    attach   = v.attach and table.Copy(v.attach) or nil,
                })
                placed = true
                break
            end
        end
        if not placed then
            slot_counters.Center = slot_counters.Center + 1
            table.insert(result.Center, {
                name  = elemName,
                slot  = slot_counters.Center,
                type  = v.type or "Model",
                model = v.model or "",
                bone  = v.bone or "ValveBiped.Bip01_R_Hand",
                rel   = v.rel or "",
                pos   = v.pos   and Vector(v.pos.x,   v.pos.y,   v.pos.z)   or Vector(0,0,0),
                angle = v.angle and Angle( v.angle.p,  v.angle.y,  v.angle.r) or Angle(0,0,0),
                size  = v.size  and Vector(v.size.x,  v.size.y,  v.size.z)  or Vector(1,1,1),
                color = v.color and Color(v.color.r, v.color.g, v.color.b, v.color.a) or Color(255,255,255,255),
                material = v.material or "",
                skin     = v.skin or 0,
                bodygroup = v.bodygroup or {},
            })
        end
    end

    return result
end
