-- ═══════════════════════════════════════════════════════════
-- d6_nav.lua — Volumetric Navigation Graph for Descent 6DOF
--
-- Sparse node graph (placed by mapper via d6_nav_node entities).
-- Nodes auto-connect on map load via ray tests.
-- BFS pathfinding, NPC path-follow helper, debug overlay.
-- ═══════════════════════════════════════════════════════════

-- ─── Global graph ────────────────────────────────────────
-- Populated by d6_nav_node entities on Initialize/OnRemove.
D6_NavGraph = D6_NavGraph or {
    nodes  = {},   -- [entIndex] = { pos, connections={entIdx,...}, radius, group }
    groups = {},   -- [groupName] = { entIdx, ... } ordered patrol lists
}

-- ─── Node registration (called by d6_nav_node entity) ────
function D6_NavRegisterNode(idx, pos, radius, group)
    D6_NavGraph.nodes[idx] = {
        pos         = pos,
        connections = {},
        radius      = radius or 64,
        group       = group  or "",
    }
    if group ~= "" then
        D6_NavGraph.groups[group] = D6_NavGraph.groups[group] or {}
        table.insert(D6_NavGraph.groups[group], idx)
    end
end

function D6_NavUnregisterNode(idx)
    local node = D6_NavGraph.nodes[idx]
    if not node then return end
    -- Remove from group list
    if node.group ~= "" and D6_NavGraph.groups[node.group] then
        for i, id in ipairs(D6_NavGraph.groups[node.group]) do
            if id == idx then table.remove(D6_NavGraph.groups[node.group], i); break end
        end
    end
    -- Remove from neighbors
    for _, other in pairs(D6_NavGraph.nodes) do
        for i = #other.connections, 1, -1 do
            if other.connections[i] == idx then table.remove(other.connections, i) end
        end
    end
    D6_NavGraph.nodes[idx] = nil
end

-- ─── Auto-build connections ───────────────────────────────
-- Called server-side after all entities initialize.
-- Connect pairs of nodes within MAX_CONNECT_DIST if line-of-sight clear.
local MAX_CONNECT_DIST = 1500

local function BuildGraph()
    local ids = {}
    for idx in pairs(D6_NavGraph.nodes) do ids[#ids+1] = idx end

    -- Reset all connections first
    for _, node in pairs(D6_NavGraph.nodes) do node.connections = {} end

    for i = 1, #ids do
        for j = i+1, #ids do
            local a = D6_NavGraph.nodes[ids[i]]
            local b = D6_NavGraph.nodes[ids[j]]
            if not a or not b then continue end
            if a.pos:DistToSqr(b.pos) > MAX_CONNECT_DIST * MAX_CONNECT_DIST then continue end

            local tr = util.TraceLine({
                start  = a.pos,
                endpos = b.pos,
                mask   = MASK_SOLID_BRUSHONLY,
            })
            if not tr.Hit then
                table.insert(a.connections, ids[j])
                table.insert(b.connections, ids[i])
            end
        end
    end

    local nodeCount = table.Count(D6_NavGraph.nodes)
    local edgeCount = 0
    for _, n in pairs(D6_NavGraph.nodes) do edgeCount = edgeCount + #n.connections end
    MsgC(Color(80,200,140), string.format("[D6 Nav] Graph built: %d nodes, %d edges\n",
        nodeCount, edgeCount / 2))
end

if SERVER then
    -- Rebuild after all entities have spawned (small delay for late spawners)
    hook.Add("InitPostEntity", "D6_NavBuild", function()
        timer.Simple(0.5, function() BuildGraph() end)
    end)

    -- Allow manual rebuild
    concommand.Add("d6_nav_rebuild", function(ply)
        BuildGraph()
        if IsValid(ply) then
            ply:PrintMessage(HUD_PRINTTALK, "[D6 Nav] Graph rebuilt: "
                .. table.Count(D6_NavGraph.nodes) .. " nodes")
        end
    end)
end

-- ─── Nearest node helper ──────────────────────────────────
function D6_NavNearestNode(pos)
    local best, bestDist = nil, math.huge
    for idx, node in pairs(D6_NavGraph.nodes) do
        local d = pos:DistToSqr(node.pos)
        if d < bestDist then bestDist = d; best = idx end
    end
    return best
end

-- ─── BFS Pathfinding ─────────────────────────────────────
-- Returns ordered table of { pos=Vector, radius=number }, or nil.
function D6_NavFindPath(startPos, goalPos)
    if table.Count(D6_NavGraph.nodes) == 0 then return nil end

    local startIdx = D6_NavNearestNode(startPos)
    local goalIdx  = D6_NavNearestNode(goalPos)
    if not startIdx or not goalIdx then return nil end
    if startIdx == goalIdx then
        return { { pos = goalPos, radius = 64 } }
    end

    -- BFS
    local visited = { [startIdx] = true }
    local parent  = {}
    local queue   = { startIdx }
    local found   = false

    while #queue > 0 do
        local cur = table.remove(queue, 1)
        if cur == goalIdx then found = true; break end
        local node = D6_NavGraph.nodes[cur]
        if not node then continue end
        for _, nb in ipairs(node.connections) do
            if not visited[nb] then
                visited[nb] = true
                parent[nb]  = cur
                queue[#queue+1] = nb
            end
        end
    end

    if not found then return nil end

    -- Reconstruct
    local path = {}
    local cur  = goalIdx
    while cur do
        local n = D6_NavGraph.nodes[cur]
        if n then table.insert(path, 1, { pos = n.pos, radius = n.radius }) end
        cur = parent[cur]
    end
    -- Append actual goal
    path[#path+1] = { pos = goalPos, radius = 32 }
    return path
end

-- ─── NPC Path Follow ─────────────────────────────────────
-- Call in NPC Think every frame. Returns true when path complete.
-- npc.D6_PathIdx tracks current waypoint.
function D6_PathFollow(npc, path, speed)
    if not path or #path == 0 then return true end
    npc.D6_PathIdx = npc.D6_PathIdx or 1

    local wp = path[npc.D6_PathIdx]
    if not wp then return true end

    local myPos = npc:GetPos()
    local dist  = myPos:Distance(wp.pos)

    if dist < (wp.radius or 64) then
        npc.D6_PathIdx = npc.D6_PathIdx + 1
        if npc.D6_PathIdx > #path then
            npc.D6_PathIdx = nil
            return true
        end
        wp = path[npc.D6_PathIdx]
    end

    local ph = npc:GetPhysicsObject()
    if IsValid(ph) then
        local dir = (wp.pos - myPos):GetNormalized()
        ph:SetVelocity(LerpVector(FrameTime() * 5, ph:GetVelocity(), dir * (speed or 320)))
    end
    return false
end

-- ─── Debug overlay (client) ──────────────────────────────
if CLIENT then
    local cvar = CreateClientConVar("d6_nav_debug", "0", true, false,
        "Show D6 navigation graph (0/1)")

    -- Sync graph to client via net message
    util.AddNetworkString and nil  -- server only

    hook.Add("PostDrawTranslucentRenderables", "D6_NavDebugDraw", function(depth, sky)
        if depth or sky then return end
        if not cvar:GetBool() then return end

        for idx, node in pairs(D6_NavGraph.nodes) do
            debugoverlay.Sphere(node.pos, node.radius * 0.5, 0, Color(0, 255, 80, 180), true)
            debugoverlay.Text(node.pos + Vector(0,0,18),
                "#" .. idx .. (node.group ~= "" and ("\n["..node.group.."]") or ""), 0, true)
            for _, nb in ipairs(node.connections) do
                local nb_node = D6_NavGraph.nodes[nb]
                if nb_node and idx < nb then
                    debugoverlay.Line(node.pos, nb_node.pos, 0, Color(80, 200, 80, 200), true)
                end
            end
        end
    end)
end

-- ─── Server: sync graph state to clients ─────────────────
if SERVER then
    util.AddNetworkString("D6_NavSync")

    local function SyncGraphToClient(ply)
        -- Send all node positions to the requesting client
        net.Start("D6_NavSync")
            net.WriteUInt(table.Count(D6_NavGraph.nodes), 16)
            for idx, node in pairs(D6_NavGraph.nodes) do
                net.WriteUInt(idx, 16)
                net.WriteVector(node.pos)
                net.WriteFloat(node.radius)
                net.WriteString(node.group)
                net.WriteUInt(#node.connections, 8)
                for _, nb in ipairs(node.connections) do
                    net.WriteUInt(nb, 16)
                end
            end
        net.Send(ply)
    end

    concommand.Add("d6_nav_sync", function(ply)
        if IsValid(ply) then SyncGraphToClient(ply) end
    end)

    hook.Add("PlayerInitialSpawn", "D6_NavSyncOnJoin", function(ply)
        timer.Simple(3, function()
            if IsValid(ply) then SyncGraphToClient(ply) end
        end)
    end)
end

if CLIENT then
    net.Receive("D6_NavSync", function()
        local count = net.ReadUInt(16)
        for _ = 1, count do
            local idx   = net.ReadUInt(16)
            local pos   = net.ReadVector()
            local rad   = net.ReadFloat()
            local grp   = net.ReadString()
            local ncons = net.ReadUInt(8)
            local cons  = {}
            for _ = 1, ncons do cons[#cons+1] = net.ReadUInt(16) end
            D6_NavGraph.nodes[idx] = { pos = pos, radius = rad, group = grp, connections = cons }
            if grp ~= "" then
                D6_NavGraph.groups[grp] = D6_NavGraph.groups[grp] or {}
                -- Avoid duplicates on re-sync
                local found = false
                for _, id in ipairs(D6_NavGraph.groups[grp]) do
                    if id == idx then found = true; break end
                end
                if not found then D6_NavGraph.groups[grp][#D6_NavGraph.groups[grp]+1] = idx end
            end
        end
        MsgC(Color(80,200,140), "[D6 Nav] Client graph synced: " .. count .. " nodes\n")
    end)
end

print("[D6] d6_nav.lua loaded")
