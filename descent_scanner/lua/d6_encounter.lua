-- ═══════════════════════════════════════════════════════════
-- d6_encounter.lua — Волновой менеджер встреч
-- Вызывается из d6_encounter_controller (entity).
--
-- Формат волн: "assault:2,interceptor:1|artillery:2,heavy_elite:1"
--   | — разделитель волн
--   , — разделитель типов внутри волны
--   type:count — тип:количество
-- ═══════════════════════════════════════════════════════════
if not SERVER then return end

D6_EncounterManager = D6_EncounterManager or {}

-- ─── Wave string parser ───────────────────────────────────
-- Returns: { {assault=2, interceptor=1}, {artillery=2} } or nil
function D6_ParseWaveString(str)
    if not str or str == "" then return nil end
    local waves = {}
    for waveStr in string.gmatch(str, "([^|]+)") do
        local waveDef = {}
        for entry in string.gmatch(waveStr, "([^,]+)") do
            local variant, count = string.match(entry, "^%s*(%a[%w_]*)%s*:%s*(%d+)%s*$")
            if variant and count then
                waveDef[#waveDef + 1] = { variant = variant, count = tonumber(count) }
            else
                MsgC(Color(255, 100, 0), "[D6 Enc] Bad entry: '" .. entry .. "'\n")
            end
        end
        if #waveDef > 0 then waves[#waves + 1] = waveDef end
    end
    return #waves > 0 and waves or nil
end

-- ─── Spawn a wave from a wave definition table ────────────
-- Returns number of NPCs spawned.
function D6_SpawnEncounterWave(controller, waveDef)
    if not IsValid(controller) or not waveDef then return 0 end
    local basePos   = controller:GetPos()
    local spawnRad  = controller.D6_SpawnRadius or 800
    local total     = 0

    -- Collect nearby d6_npc_spawner positions for placement hints
    local spawnerPositions = {}
    for _, ent in ipairs(ents.FindInSphere(basePos, spawnRad * 1.5)) do
        if IsValid(ent) and ent:GetClass() == "d6_npc_spawner" then
            spawnerPositions[#spawnerPositions + 1] = ent:GetPos()
        end
    end

    for _, entry in ipairs(waveDef) do
        local variant = entry.variant
        local count   = entry.count or 1
        for i = 1, count do
            -- Pick spawn position
            local pos
            if #spawnerPositions > 0 then
                pos = spawnerPositions[((i - 1) % #spawnerPositions) + 1]
                pos = pos + VectorRand() * 40
            else
                local ang   = (i / count) * math.pi * 2
                local r     = math.Rand(120, spawnRad * 0.4)
                pos = basePos + Vector(math.sin(ang) * r, math.cos(ang) * r, 80)
            end

            local npc = SpawnD6Role and SpawnD6Role(variant, pos, nil)
            if IsValid(npc) then
                npc.D6_EncounterOwner = controller
                controller.D6_WaveNPCs[#controller.D6_WaveNPCs + 1] = npc
                total = total + 1
            end
        end
    end

    MsgC(Color(80, 200, 140), string.format(
        "[D6 Enc] Wave spawned: %d NPCs (controller #%d)\n",
        total, controller:EntIndex()))
    return total
end

-- ─── Kill tracking ────────────────────────────────────────
hook.Add("EntityRemoved", "D6_EncounterKillTrack", function(ent)
    local ctrl = ent.D6_EncounterOwner
    if not IsValid(ctrl) then return end
    if not ctrl.D6_Active then return end
    -- Remove from live list (Think cleans on next cycle — just trigger check)
    ctrl.D6_PendingCheck = true
end)

print("[D6] d6_encounter.lua loaded")
