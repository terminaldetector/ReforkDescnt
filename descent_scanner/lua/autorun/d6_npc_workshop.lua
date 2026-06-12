-- =========================================================
-- d6_npc_workshop.lua — DRMD NPC Workshop (ядро, SHARED)
--
--   Конструктор противников: профиль NPC (JSON-safe таблица)
--   → D6_NPCWorkshop.ToLoadout() → D6_BuildDrone() напрямую.
--   Ядро DRMD (d6_core/d6_modules/d6_ai/d6_nav/d6_encounter)
--   не ломается: все переопределения — персональные поля NPC
--   с фолбэком на прежние константы.
--
--   Паттерн хранения — как в d6_sck_bridge:
--     СЕРВЕР — авторитетное хранилище. Приём от админа,
--     DATA/drmd_npcs/<name>.txt (JSON), рассылка всем,
--     синхронизация при подключении.
--
--   Формат профиля (все значения — числа/строки/плоские таблицы):
--     {
--       base   = "assault",            -- роль-источник (справочно)
--       entity = "npc_cscanner",       -- базовая сущность
--       ai     = "pressure",           -- pressure|flank|standoff|support|anchor
--       model  = { model="", skin=0, bodygroups={}, scale=1.2,
--                  collisionScale=1.0 },
--       stats  = { hp, color={r,g,b}, speed, rangeMin, rangeMax,
--                  retreatHP, retreatDist, chargeDist, chargeCD,
--                  chargeDur, chargeSpeed, healRadius, healAmt,
--                  healInterval, shield, shieldMax, armor, armorMax,
--                  resist={kinetic,energy,explosive} },
--       phys   = { 12 полей flight-контроллера },
--       combat = nil | { orbitR, inclMax, orbitMin, orbitMax, runMin,
--                  runMax, breakMin, breakMax, breakIn, vbias,
--                  aggression, spdMul,
--                  maneuvers={barrel,split_s,immelmann} },
--                  -- nil → ролевой D6_COMBAT_STYLE ядра
--       swarm  = nil | { separation, cohesion, alignment, target,
--                  radius, maxSpeed, sepDist, exclude },
--                  -- nil → константы boids ядра
--       slots  = { primaryL="Pulsar", primaryR="", utility="", core="" },
--       nav    = { patrol="", attack="", escape="", idle="" },
--     }
-- =========================================================

D6_NPCWorkshop = D6_NPCWorkshop or {}
D6_NPCWorkshop.Profiles = D6_NPCWorkshop.Profiles or {}

local DIR = "drmd_npcs"

local function ValidName(name)
    return isstring(name) and name:match("^[%w_]+$") ~= nil and #name <= 48
end

-- ── Общие хелперы ────────────────────────────────────────

function D6_NPCWorkshop.Get(name)
    return D6_NPCWorkshop.Profiles[name]
end

function D6_NPCWorkshop.GetNames()
    local t = {}
    for name in pairs(D6_NPCWorkshop.Profiles) do t[#t + 1] = name end
    table.sort(t)
    return t
end

function D6_NPCWorkshop.SetLocal(name, profile)
    if istable(profile) then
        D6_NPCWorkshop.Profiles[name] = profile
    else
        D6_NPCWorkshop.Profiles[name] = nil
    end
end

-- Профиль → loadout для D6_BuildDrone (плоские таблицы → Color и т.д.)
-- Принимает имя профиля или саму таблицу профиля.
function D6_NPCWorkshop.ToLoadout(profileOrName)
    local p = profileOrName
    if isstring(p) then p = D6_NPCWorkshop.Profiles[p] end
    if not istable(p) then return nil end

    local m  = istable(p.model) and p.model or {}
    local st = istable(p.stats) and p.stats or {}
    local c  = istable(st.color) and st.color or {}

    local lo = {
        -- NPC Workshop поля (читает D6_BuildDrone)
        entity         = p.entity or "npc_cscanner",
        model          = m.model or "",
        skin           = m.skin or 0,
        bodygroups     = istable(m.bodygroups) and table.Copy(m.bodygroups) or nil,
        collisionScale = m.collisionScale or 1.0,
        combat         = istable(p.combat) and table.Copy(p.combat) or nil,
        maneuvers      = istable(p.combat) and istable(p.combat.maneuvers)
                             and table.Copy(p.combat.maneuvers) or nil,
        swarm          = istable(p.swarm) and table.Copy(p.swarm) or nil,
        nav            = istable(p.nav) and table.Copy(p.nav) or nil,

        -- Классические поля loadout (контракт LOADOUTS)
        ai        = p.ai or "pressure",
        scale     = m.scale or 1.0,
        color     = Color(c.r or 200, c.g or 200, c.b or 200),
        hp        = st.hp or 200,
        speed     = st.speed or 500,
        rangeMin  = st.rangeMin, rangeMax = st.rangeMax,
        retreatHP = st.retreatHP, retreatDist = st.retreatDist,
        chargeDist = st.chargeDist, chargeCD = st.chargeCD,
        chargeDur = st.chargeDur, chargeSpeed = st.chargeSpeed,
        healRadius = st.healRadius, healAmt = st.healAmt,
        healInterval = st.healInterval,
        shield    = st.shield or 0,    shieldMax = st.shieldMax or 0,
        armor     = st.armor or 0,     armorMax  = st.armorMax or 0,
        resist    = istable(st.resist) and table.Copy(st.resist)
                        or { kinetic = 0, energy = 0, explosive = 0 },
        phys      = istable(p.phys) and table.Copy(p.phys) or nil,
        slots     = {},
    }

    -- Слоты: пустые строки = слот не занят
    if istable(p.slots) then
        for slot, modName in pairs(p.slots) do
            if isstring(modName) and modName ~= "" then
                lo.slots[slot] = modName
            end
        end
    end

    return lo
end

-- Loadout (стоковая роль / снятый с дрона) → JSON-safe профиль
function D6_NPCWorkshop.FromLoadout(lo, baseName)
    if not istable(lo) then return nil end
    local col = lo.color or Color(200, 200, 200)
    return {
        base   = baseName or "custom",
        entity = lo.entity or "npc_cscanner",
        ai     = lo.ai or "pressure",
        model  = {
            model = lo.model or "", skin = lo.skin or 0,
            bodygroups = istable(lo.bodygroups) and table.Copy(lo.bodygroups) or {},
            scale = lo.scale or 1.0,
            collisionScale = lo.collisionScale or 1.0,
        },
        stats  = {
            hp = lo.hp or 200,
            color = { r = col.r, g = col.g, b = col.b },
            speed = lo.speed or 500,
            rangeMin = lo.rangeMin, rangeMax = lo.rangeMax,
            retreatHP = lo.retreatHP, retreatDist = lo.retreatDist,
            chargeDist = lo.chargeDist, chargeCD = lo.chargeCD,
            chargeDur = lo.chargeDur, chargeSpeed = lo.chargeSpeed,
            healRadius = lo.healRadius, healAmt = lo.healAmt,
            healInterval = lo.healInterval,
            shield = lo.shield or 0, shieldMax = lo.shieldMax or 0,
            armor = lo.armor or 0,   armorMax  = lo.armorMax or 0,
            resist = istable(lo.resist) and table.Copy(lo.resist)
                         or { kinetic = 0, energy = 0, explosive = 0 },
        },
        phys   = istable(lo.phys) and table.Copy(lo.phys) or {},
        combat = istable(lo.combat) and table.Copy(lo.combat) or nil,
        swarm  = istable(lo.swarm) and table.Copy(lo.swarm) or nil,
        slots  = istable(lo.slots) and table.Copy(lo.slots) or {},
        nav    = istable(lo.nav) and table.Copy(lo.nav)
                     or { patrol = "", attack = "", escape = "", idle = "" },
    }
end

-- =========================================================
-- СЕРВЕР: персист, сеть, спавн, размещение энтити
-- =========================================================
if SERVER then

    util.AddNetworkString("D6_NPCWS_Set")          -- клиент → сервер: сохранить/удалить профиль
    util.AddNetworkString("D6_NPCWS_Sync")         -- сервер → клиенты: один профиль
    util.AddNetworkString("D6_NPCWS_NewFromRole")  -- клиент → сервер: клонировать стоковую роль
    util.AddNetworkString("D6_NPCWS_PushWorking")  -- сервер → клиент: профиль в редактор (STOOL RMB)

    local function SaveToDisk(name)
        if not file.IsDir(DIR, "DATA") then file.CreateDir(DIR) end
        local p = D6_NPCWorkshop.Profiles[name]
        local path = DIR .. "/" .. name .. ".txt"
        if p then
            file.Write(path, util.TableToJSON(p, true))
        elseif file.Exists(path, "DATA") then
            file.Delete(path)
        end
    end

    local function LoadAllFromDisk()
        for _, fn in ipairs(file.Find(DIR .. "/*.txt", "DATA") or {}) do
            local raw = file.Read(DIR .. "/" .. fn, "DATA")
            local tbl = raw and util.JSONToTable(raw)
            if istable(tbl) then
                D6_NPCWorkshop.SetLocal(fn:gsub("%.txt$", ""), tbl)
            end
        end
    end
    LoadAllFromDisk()

    local function Broadcast(name, ply)
        local p = D6_NPCWorkshop.Profiles[name]
        net.Start("D6_NPCWS_Sync")
            net.WriteString(name)
            net.WriteString(p and util.TableToJSON(p) or "")
        if ply then net.Send(ply) else net.Broadcast() end
    end
    D6_NPCWorkshop.Broadcast = Broadcast

    local function IsAdmin(ply)
        return IsValid(ply) and (ply:IsAdmin() or ply:IsListenServerHost())
    end

    net.Receive("D6_NPCWS_Set", function(_, ply)
        if not IsAdmin(ply) then
            ply:PrintMessage(HUD_PRINTTALK, "[D6 NPC] Нет прав (нужен admin)")
            return
        end
        local name = net.ReadString()
        if not ValidName(name) then return end
        local raw = net.ReadString()
        if raw == "" then
            D6_NPCWorkshop.SetLocal(name, nil)
        else
            local tbl = util.JSONToTable(raw)
            if not istable(tbl) then return end
            D6_NPCWorkshop.SetLocal(name, tbl)
        end
        SaveToDisk(name)
        Broadcast(name)
        ply:PrintMessage(HUD_PRINTTALK, "[D6 NPC] " .. name .. ": " ..
            (raw == "" and "профиль удалён" or "профиль сохранён"))
    end)

    -- Клонировать стоковую роль в новый профиль
    net.Receive("D6_NPCWS_NewFromRole", function(_, ply)
        if not IsAdmin(ply) then return end
        local name = net.ReadString()
        local role = net.ReadString()
        if not ValidName(name) then return end
        local lo = D6_GetRoleLoadout and D6_GetRoleLoadout(role)
        if not lo then
            ply:PrintMessage(HUD_PRINTTALK, "[D6 NPC] Неизвестная роль: " .. role)
            return
        end
        local p = D6_NPCWorkshop.FromLoadout(lo, role)
        D6_NPCWorkshop.SetLocal(name, p)
        SaveToDisk(name)
        Broadcast(name)
        ply:PrintMessage(HUD_PRINTTALK,
            "[D6 NPC] Профиль «" .. name .. "» создан из роли " .. role)
    end)

    hook.Add("PlayerInitialSpawn", "D6_NPCWS_Sync", function(ply)
        timer.Simple(4, function()
            if not IsValid(ply) then return end
            for name in pairs(D6_NPCWorkshop.Profiles) do
                Broadcast(name, ply)
            end
        end)
    end)

    -- ── Спавн профиля ────────────────────────────────────
    function D6_SpawnNPCProfile(name, pos, targetPly)
        local lo = D6_NPCWorkshop.ToLoadout(name)
        if not lo then return nil end
        local npc = D6_BuildDrone(lo, pos, targetPly)
        if IsValid(npc) then
            npc.D6_Variant = name       -- радар/boids/энкаунтер-учёт
            npc.D6_Profile = name
        end
        return npc
    end

    concommand.Add("6dof_spawn_profile", function(ply, _, args)
        if not IsValid(ply) then return end
        local name = args[1]
        if not name or not D6_NPCWorkshop.Profiles[name] then
            ply:PrintMessage(HUD_PRINTTALK, "[D6 NPC] Профили: "
                .. table.concat(D6_NPCWorkshop.GetNames(), ", "))
            return
        end
        local tr = ply:GetEyeTrace()
        local npc = D6_SpawnNPCProfile(name, tr.HitPos + tr.HitNormal * 60, ply)
        if IsValid(npc) then
            ply:PrintMessage(HUD_PRINTTALK, "[D6 NPC] Заспавнен: " .. name)
        end
    end)

    -- ── Размещение nav-узлов и спавнеров из GUI ──────────
    concommand.Add("d6_navnode_place", function(ply, _, args)
        if not IsAdmin(ply) then return end
        local tr  = ply:GetEyeTrace()
        local ent = ents.Create("d6_nav_node")
        if not IsValid(ent) then return end
        ent:SetPos(tr.HitPos + tr.HitNormal * 80)
        ent:SetKeyValue("radius", args[2] or "64")
        ent:SetKeyValue("patrol_group", args[1] or "")
        ent:Spawn()
        undo.Create("D6 Nav Node")
            undo.AddEntity(ent)
            undo.SetPlayer(ply)
        undo.Finish()
        ply:PrintMessage(HUD_PRINTTALK, ("[D6 NPC] Nav-узел (группа «%s»). Не забудь d6_nav_rebuild.")
            :format(args[1] or ""))
    end)

    concommand.Add("d6_npcspawner_place", function(ply, _, args)
        if not IsAdmin(ply) then return end
        local variant = args[1]
        if not variant then return end
        local tr  = ply:GetEyeTrace()
        local ent = ents.Create("d6_npc_spawner")
        if not IsValid(ent) then return end
        ent:SetPos(tr.HitPos + tr.HitNormal * 80)
        ent:SetKeyValue("variant", variant)
        ent:SetKeyValue("spawn_count", args[2] or "1")
        ent:SetKeyValue("respawn", args[3] or "0")
        ent:SetKeyValue("respawn_delay", args[4] or "30")
        ent:Spawn()
        undo.Create("D6 NPC Spawner")
            undo.AddEntity(ent)
            undo.SetPlayer(ply)
        undo.Finish()
        ply:PrintMessage(HUD_PRINTTALK, "[D6 NPC] Спавнер размещён: " .. variant)
    end)

    -- ── NPC-модули поверх D6_Wep (Plasma / Rail / Flak) ──
    -- Дополняют сток (Pulsar/Laser/Missile/ComBall/RepairBeam/
    -- ShieldGenerator/GravityUnit) до списка вооружения из ТЗ.
    local function ModuleMuzzle(npc)
        return npc:GetPos() + npc:GetForward() * 24
    end

    local function LeadDir(npc, target, projSpeed)
        local from = ModuleMuzzle(npc)
        local tpos = target:WorldSpaceCenter()
        local lead = tpos + target:GetVelocity() * (from:Distance(tpos) / projSpeed) * 0.6
        return (lead - from):GetNormalized(), from
    end

    local function RegisterWorkshopModules()
        if not (D6_RegisterModule and D6_Wep and D6_Wep.FireProjectile) then return end

        D6_RegisterModule("Plasma", {
            slot = "primaryL", cooldown = 0.5, range = 950, energyCost = 6,
            OnFire = function(npc, target)
                local dir, from = LeadDir(npc, target, 2400)
                D6_Wep.FireProjectile({
                    owner = npc, pos = from, dir = dir, speed = 2400,
                    model = "models/spore.mdl", scale = 1.2,
                    color = Color(120, 255, 160), dmgClass = "exotic",
                    mass = 2, life = 3,
                    trails = { { col = Color(120, 255, 160), sw = 16, ew = 0, life = 0.25 } },
                    onHit = function(proj, hitEnt, hitPos, hitNormal)
                        if IsValid(hitEnt) then
                            D6_Wep.DirectDamage(npc, proj, hitEnt, 18,
                                D6_DAMAGE and D6_DAMAGE.exotic or DMG_SHOCK,
                                (hitNormal or Vector(0, 0, 1)) * -300)
                        end
                    end,
                })
            end,
        })

        D6_RegisterModule("Rail", {
            slot = "primaryR", cooldown = 3.0, range = 2600, energyCost = 18,
            OnFire = function(npc, target)
                local dir, from = LeadDir(npc, target, 9000)
                npc:EmitSound("weapons/gauss/fire1.wav", 80, 130)
                D6_Wep.FireProjectile({
                    owner = npc, pos = from, dir = dir, speed = 9000,
                    model = "models/weapons/w_crossbow_bolt.mdl", scale = 1.5,
                    color = Color(140, 255, 180), dmgClass = "kinetic",
                    mass = 4, life = 2,
                    trails = { { col = Color(140, 255, 180), sw = 24, ew = 0, life = 0.35 } },
                    onHit = function(proj, hitEnt, hitPos, hitNormal)
                        if IsValid(hitEnt) then
                            D6_Wep.DirectDamage(npc, proj, hitEnt, 45,
                                D6_DAMAGE and D6_DAMAGE.kinetic or DMG_BULLET,
                                (hitNormal or Vector(0, 0, 1)) * -800)
                        end
                    end,
                })
            end,
        })

        D6_RegisterModule("Flak", {
            slot = "primaryR", cooldown = 1.6, range = 1100, energyCost = 10,
            OnFire = function(npc, target)
                local dir, from = LeadDir(npc, target, 2000)
                npc:EmitSound("weapons/shotgun/shotgun_fire7.wav", 75, 90)
                for i = 1, 6 do
                    local spread = (dir + VectorRand() * 0.12):GetNormalized()
                    D6_Wep.FireProjectile({
                        owner = npc, pos = from, dir = spread, speed = 2000,
                        model = "models/weapons/w_crossbow_bolt.mdl", scale = 0.4,
                        color = Color(255, 180, 80), dmgClass = "kinetic",
                        mass = 0.5, life = 0.8,
                        onHit = function(proj, hitEnt, hitPos, hitNormal)
                            if IsValid(hitEnt) then
                                D6_Wep.DirectDamage(npc, proj, hitEnt, 7,
                                    D6_DAMAGE and D6_DAMAGE.kinetic or DMG_BULLET,
                                    (hitNormal or Vector(0, 0, 1)) * -150)
                            end
                        end,
                    })
                end
            end,
        })
    end

    -- d6_autoload (autorun, по алфавиту раньше) уже включил d6_modules
    -- и d6_weapon_core; страховка — отложенная регистрация.
    if D6_RegisterModule then
        RegisterWorkshopModules()
    else
        hook.Add("InitPostEntity", "D6_NPCWS_Modules", RegisterWorkshopModules)
    end

end

-- =========================================================
-- КЛИЕНТ: приём синхронизации, отправка профилей
-- =========================================================
if CLIENT then

    net.Receive("D6_NPCWS_Sync", function()
        local name = net.ReadString()
        local raw  = net.ReadString()
        if raw == "" then
            D6_NPCWorkshop.SetLocal(name, nil)
            return
        end
        local tbl = util.JSONToTable(raw)
        if istable(tbl) then D6_NPCWorkshop.SetLocal(name, tbl) end
    end)

    -- Сервер прислал профиль для загрузки в редактор (STOOL RMB)
    net.Receive("D6_NPCWS_PushWorking", function()
        local raw = net.ReadString()
        local tbl = util.JSONToTable(raw)
        if istable(tbl) and D6_NPCWS_LoadWorking then
            D6_NPCWS_LoadWorking(tbl)
            chat.AddText(Color(80, 255, 80),
                "[D6 NPC] Профиль дрона загружен в редактор (Q → DRMD Workshop).")
        end
    end)

    -- Отправка профиля на сервер (profile = nil → удалить)
    function D6_NPCWorkshop.Submit(name, profile)
        if not ValidName(name) then
            chat.AddText(Color(255, 80, 80),
                "[D6 NPC] Имя: латиница/цифры/подчёркивание, до 48 символов.")
            return false
        end
        net.Start("D6_NPCWS_Set")
            net.WriteString(name)
            net.WriteString(profile and util.TableToJSON(profile) or "")
        net.SendToServer()
        return true
    end

    function D6_NPCWorkshop.NewFromRole(name, role)
        if not ValidName(name) then return false end
        net.Start("D6_NPCWS_NewFromRole")
            net.WriteString(name)
            net.WriteString(role)
        net.SendToServer()
        return true
    end

end

print("[D6] d6_npc_workshop.lua загружен")
