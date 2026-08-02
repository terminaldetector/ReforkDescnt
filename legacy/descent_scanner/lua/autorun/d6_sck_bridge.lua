-- =========================================================
-- d6_sck_bridge.lua — мост SCK ↔ существующее оружие DRMD
--
--   Позволяет редактировать ВНЕШНИЙ ВИД уже существующего
--   оружия (weapon_d6_*) через редактор кластеров SCK без
--   правки Lua-файлов оружия.
--
--   Архитектура:
--     • Переопределение хранится как плоский список модулей
--       (JSON-safe, без Vector/Angle) по классу оружия.
--     • СЕРВЕР — авторитетное хранилище: принимает от админа,
--       сохраняет в DATA/drmd_weapons/visual/<class>.txt,
--       рассылает всем клиентам. Также отдаёт точки выстрела
--       (muzzles) для D6_Wep.MuzzleFor.
--     • КЛИЕНТ — рендер переопределённых классов через
--       конвейер VElements/vRenderOrder (та же структура и
--       семантика, что в SCK base_code.lua), с корнем в камере
--       (у оружия DRMD нет видимого viewmodel). d6_wepview.lua
--       пропускает такие классы — двойного рендера нет.
--     • Анимация модулей — через SCK_AnimateTable (animator.lua
--       из SCK); без установленного SCK модули статичны.
--
--   Формат модуля (плоский, для JSON):
--     { cluster="Center", slot=1, name="gun", type="Model",
--       model="...", material="", skin=0, rel="",
--       pos={x,y,z}, angle={p,y,r}, size={x,y,z},
--       color={r,g,b,a},
--       anim   = { spin, spinAxis, bobAmp, bobFreq,
--                  swayMult, kick, kickRecover },
--       attach = { muzzle=true, idx=1 } }
--
--   Совместимость: класс без переопределения рендерится
--   старым d6_wepview байт-в-байт. SCK не обязателен.
-- =========================================================

D6_SCKBridge = D6_SCKBridge or {}
D6_SCKBridge.Configs = D6_SCKBridge.Configs or {}   -- [class] = { modules={}, muzzles={} }

local DIR = "drmd_weapons/visual"

local CLUSTER_ORDER = { "Center", "Upper", "SideLeft", "SideRight", "Lower" }
local CLUSTER_RANK  = {}
for i, c in ipairs(CLUSTER_ORDER) do CLUSTER_RANK[c] = i end

-- ── Общие хелперы ────────────────────────────────────────

function D6_SCKBridge.Has(class)
    return D6_SCKBridge.Configs[class] ~= nil
end

function D6_SCKBridge.Get(class)
    return D6_SCKBridge.Configs[class]
end

-- Точка выстрела idx для класса: { fwd, rgt, up } или nil.
-- Используется D6_Wep.MuzzleFor (d6_weapon_core.lua).
function D6_SCKBridge.GetMuzzle(class, idx)
    local cfg = D6_SCKBridge.Configs[class]
    if not cfg or not cfg.muzzles then return nil end
    return cfg.muzzles[idx or 1]
end

local function SortModules(mods)
    table.sort(mods, function(a, b)
        local ca = CLUSTER_RANK[a.cluster or "Center"] or 1
        local cb = CLUSTER_RANK[b.cluster or "Center"] or 1
        if ca ~= cb then return ca < cb end
        return (a.slot or 0) < (b.slot or 0)
    end)
end

-- Производные данные: сортировка + таблица muzzles из attach-модулей.
local function Finalize(cfg)
    SortModules(cfg.modules)
    cfg.muzzles = {}
    for _, m in ipairs(cfg.modules) do
        if m.attach and m.attach.muzzle then
            local p = m.pos or {}
            cfg.muzzles[m.attach.idx or 1] = {
                fwd = p.x or 0, rgt = p.y or 0, up = p.z or 0,
            }
        end
    end
end

-- Установка/удаление конфига в текущем realm (без сети).
function D6_SCKBridge.SetLocal(class, modules)
    if istable(modules) and #modules > 0 then
        local cfg = { modules = modules }
        Finalize(cfg)
        D6_SCKBridge.Configs[class] = cfg
    else
        D6_SCKBridge.Configs[class] = nil
    end
    if CLIENT and D6_SCKBridge.InvalidateRender then
        D6_SCKBridge.InvalidateRender(class)
    end
end

-- =========================================================
-- СЕРВЕР: хранилище, персист, сеть, выдача muzzles
-- =========================================================
if SERVER then

    util.AddNetworkString("D6_SCKBridge_Set")    -- клиент → сервер
    util.AddNetworkString("D6_SCKBridge_Sync")   -- сервер → клиенты

    local function SaveToDisk(class)
        if not file.IsDir("drmd_weapons", "DATA") then file.CreateDir("drmd_weapons") end
        if not file.IsDir(DIR, "DATA") then file.CreateDir(DIR) end
        local cfg = D6_SCKBridge.Configs[class]
        local path = DIR .. "/" .. class .. ".txt"
        if cfg then
            file.Write(path, util.TableToJSON({ modules = cfg.modules }, true))
        elseif file.Exists(path, "DATA") then
            file.Delete(path)
        end
    end

    local function LoadAllFromDisk()
        local files = file.Find(DIR .. "/*.txt", "DATA") or {}
        for _, fn in ipairs(files) do
            local raw = file.Read(DIR .. "/" .. fn, "DATA")
            local tbl = raw and util.JSONToTable(raw)
            if tbl and istable(tbl.modules) then
                D6_SCKBridge.SetLocal(fn:gsub("%.txt$", ""), tbl.modules)
            end
        end
    end
    LoadAllFromDisk()

    local function Broadcast(class, ply)
        local cfg = D6_SCKBridge.Configs[class]
        net.Start("D6_SCKBridge_Sync")
            net.WriteString(class)
            net.WriteString(cfg and util.TableToJSON({ modules = cfg.modules }) or "")
        if ply then net.Send(ply) else net.Broadcast() end
    end

    net.Receive("D6_SCKBridge_Set", function(_, ply)
        if not IsValid(ply) then return end
        if not ply:IsAdmin() and not ply:IsListenServerHost() then
            ply:PrintMessage(HUD_PRINTTALK, "[D6 Bridge] Нет прав (нужен admin)")
            return
        end
        local class = net.ReadString()
        if not class:match("^weapon_[%w_]+$") then return end
        local raw = net.ReadString()

        if raw == "" then
            D6_SCKBridge.SetLocal(class, nil)
        else
            local tbl = util.JSONToTable(raw)
            if not (tbl and istable(tbl.modules)) then return end
            D6_SCKBridge.SetLocal(class, tbl.modules)
        end
        SaveToDisk(class)
        Broadcast(class)
        ply:PrintMessage(HUD_PRINTTALK, "[D6 Bridge] " .. class .. ": " ..
            (raw == "" and "переопределение сброшено" or "внешний вид применён"))
    end)

    -- Полная синхронизация при подключении игрока
    hook.Add("PlayerInitialSpawn", "D6_SCKBridge_Sync", function(ply)
        timer.Simple(4, function()
            if not IsValid(ply) then return end
            for class in pairs(D6_SCKBridge.Configs) do
                Broadcast(class, ply)
            end
        end)
    end)

end

-- =========================================================
-- КЛИЕНТ: приём, конвертеры, рендер, панель в Q-меню
-- =========================================================
if CLIENT then

    net.Receive("D6_SCKBridge_Sync", function()
        local class = net.ReadString()
        local raw   = net.ReadString()
        if raw == "" then
            D6_SCKBridge.SetLocal(class, nil)
            return
        end
        local tbl = util.JSONToTable(raw)
        if tbl and istable(tbl.modules) then
            D6_SCKBridge.SetLocal(class, tbl.modules)
        end
    end)

    -- Отправка переопределения на сервер (modules = nil → сброс)
    function D6_SCKBridge.Submit(class, modules)
        net.Start("D6_SCKBridge_Set")
            net.WriteString(class)
            net.WriteString(modules and util.TableToJSON({ modules = modules }) or "")
        net.SendToServer()
    end

    -- ── Конвертеры ───────────────────────────────────────

    -- Плоские модули → кластеры SCK-редактора (Vector/Angle)
    function D6_SCKBridge.ModulesToClusters(modules)
        local clusters = {}
        for _, c in ipairs(CLUSTER_ORDER) do clusters[c] = {} end
        for _, m in ipairs(modules or {}) do
            local cname = CLUSTER_RANK[m.cluster or ""] and m.cluster or "Center"
            local p, a, s = m.pos or {}, m.angle or {}, m.size or {}
            local col = m.color
            table.insert(clusters[cname], {
                name  = m.name or ("mod" .. #clusters[cname] + 1),
                slot  = m.slot or (#clusters[cname] + 1),
                type  = m.type or "Model",
                model = m.model or "",
                bone  = "ValveBiped.Bip01_R_Hand",
                rel   = m.rel or "",
                pos   = Vector(p.x or 0, p.y or 0, p.z or 0),
                angle = Angle(a.p or 0, a.y or 0, a.r or 0),
                size  = Vector(s.x or 1, s.y or 1, s.z or 1),
                color = col and Color(col.r or 255, col.g or 255, col.b or 255, col.a or 255)
                            or Color(255, 255, 255, 255),
                material = m.material or "",
                skin     = m.skin or 0,
                anim   = m.anim   and table.Copy(m.anim)   or nil,
                attach = m.attach and table.Copy(m.attach) or nil,
            })
        end
        return clusters
    end

    -- Кластеры SCK-редактора → плоские модули (JSON-safe)
    function D6_SCKBridge.ClustersToModules(clusters)
        local modules = {}
        for _, cname in ipairs(CLUSTER_ORDER) do
            for _, mod in ipairs(clusters and clusters[cname] or {}) do
                local p = mod.pos   or Vector(0, 0, 0)
                local a = mod.angle or Angle(0, 0, 0)
                local s = mod.size  or Vector(1, 1, 1)
                local c = mod.color
                table.insert(modules, {
                    cluster = cname,
                    slot    = mod.slot or 1,
                    name    = mod.name or "mod",
                    type    = mod.type or "Model",
                    model   = mod.model or "",
                    rel     = mod.rel or "",
                    pos     = { x = p.x, y = p.y, z = p.z },
                    angle   = { p = a.p, y = a.y, r = a.r },
                    size    = { x = s.x, y = s.y, z = s.z },
                    color   = c and { r = c.r, g = c.g, b = c.b, a = c.a } or nil,
                    material = mod.material or "",
                    skin     = mod.skin or 0,
                    anim   = mod.anim   and table.Copy(mod.anim)   or nil,
                    attach = mod.attach and table.Copy(mod.attach) or nil,
                })
            end
        end
        return modules
    end

    -- Жёсткие слоты d6_wepview (D6_WEPVIEW_CFG) → плоские модули.
    -- Маппинг полей 1:1 (pos.x=fwd, pos.y=rgt, pos.z=up — та же
    -- семантика Forward/Right/Up, что у VElements).
    -- Кластер по геометрии: rgt==0 → Center, up>0 → Upper, иначе Lower.
    function D6_SCKBridge.WepviewToModules(slots)
        local modules = {}
        local counters = {}
        for i, s in ipairs(slots or {}) do
            local cname
            if (s.rgt or 0) == 0 then cname = "Center"
            elseif (s.up or 0) > 0 then cname = "Upper"
            else cname = "Lower" end
            counters[cname] = (counters[cname] or 0) + 1
            local sc = s.scale or 1
            table.insert(modules, {
                cluster = cname,
                slot    = counters[cname],
                name    = "slot" .. i,
                type    = "Model",
                model   = s.mdl or "",
                rel     = "",
                pos     = { x = s.fwd or 0, y = s.rgt or 0, z = s.up or 0 },
                angle   = { p = s.pitch or 0, y = s.yaw or 0, r = s.roll or 0 },
                size    = { x = sc, y = sc, z = sc },
            })
        end
        return modules
    end

    -- ── Рендер (конвейер VElements с корнем в камере) ────

    local RState = nil   -- { class, elements, order, lastNPF }

    local function DestroyState()
        if not RState then return end
        for _, v in pairs(RState.elements) do
            if IsValid(v.modelEnt) then v.modelEnt:Remove() end
        end
        RState = nil
    end

    function D6_SCKBridge.InvalidateRender(class)
        if RState and RState.class == class then DestroyState() end
    end

    -- Строит elements (формат VElements) + order (формат vRenderOrder):
    -- Models в порядке кластер→слот, затем Sprite/Quad — та же
    -- детерминированная семантика, что у SCK_ExpandClusters.
    local function BuildState(class, cfg)
        DestroyState()
        local elements, order, fx = {}, {}, {}
        for _, m in ipairs(cfg.modules) do
            local key = (m.cluster or "Center") .. "_" .. (m.name or "mod")
            local p, a, s = m.pos or {}, m.angle or {}, m.size or {}
            local col = m.color
            local elem = {
                type     = m.type or "Model",
                model    = m.model or "",
                rel      = m.rel or "",
                pos      = Vector(p.x or 0, p.y or 0, p.z or 0),
                angle    = Angle(a.p or 0, a.y or 0, a.r or 0),
                size     = Vector(s.x or 1, s.y or 1, s.z or 1),
                color    = col and Color(col.r or 255, col.g or 255, col.b or 255, col.a or 255) or nil,
                material = m.material or "",
                skin     = m.skin or 0,
                anim     = m.anim,
            }
            if elem.type == "Model" and elem.model ~= ""
                and file.Exists(elem.model, "GAME") then
                local ent = ClientsideModel(elem.model, RENDERGROUP_OPAQUE)
                if IsValid(ent) then
                    ent:SetNoDraw(true)
                    elem.modelEnt = ent
                end
            end
            elements[key] = elem
            if elem.type == "Model" then
                table.insert(order, key)
            else
                table.insert(fx, key)
            end
        end
        for _, k in ipairs(fx) do table.insert(order, k) end
        RState = { class = class, elements = elements, order = order }
    end

    -- Зеркало GetBoneOrientation (base_code.lua:294) с корнем в
    -- камере: rel-цепочки резолвятся так же, корень = EyePos/EyeAngles.
    local function CamOrientation(elements, v, ep, ea, depth)
        depth = depth or 0
        if depth > 8 then return ep, Angle(ea.p, ea.y, ea.r) end
        if v.rel and v.rel ~= "" then
            local parent = elements[v.rel]
            if parent then
                local pos, ang = CamOrientation(elements, parent, ep, ea, depth + 1)
                pos = pos + ang:Forward() * parent.pos.x
                          + ang:Right()   * parent.pos.y
                          + ang:Up()      * parent.pos.z
                ang:RotateAroundAxis(ang:Up(),      parent.angle.y)
                ang:RotateAroundAxis(ang:Right(),   parent.angle.p)
                ang:RotateAroundAxis(ang:Forward(), parent.angle.r)
                return pos, ang
            end
        end
        return ep * 1, Angle(ea.p, ea.y, ea.r)
    end

    hook.Add("PostDrawTranslucentRenderables", "D6_SCKBridge_Render", function(bDepth, bSky)
        if bDepth or bSky then return end

        local ply = LocalPlayer()
        if not IsValid(ply) then return end

        local wep   = ply:GetActiveWeapon()
        local class = IsValid(wep) and wep:GetClass() or ""
        local cfg   = D6_SCKBridge.Configs[class]

        if not cfg then
            if RState then DestroyState() end
            return
        end

        if not RState or RState.class ~= class then
            BuildState(class, cfg)
        end

        if ply:ShouldDrawLocalPlayer() then return end

        -- Анимация модулей (SCK animator; без SCK — статика)
        if SCK_AnimateTable then
            -- авто-триггер отдачи: момент выстрела = сдвиг NextPrimaryFire
            local npf = wep:GetNextPrimaryFire()
            if RState.lastNPF and npf > RState.lastNPF + 0.001
                and ply:KeyDown(IN_ATTACK) and SCK_TriggerRecoil then
                SCK_TriggerRecoil(RState.elements)
            end
            RState.lastNPF = npf
            SCK_AnimateTable(RState.elements)
        end

        local ep, ea = EyePos(), EyeAngles()

        -- Покачивание — формулы из d6_wepview.lua, swayMult на модуль
        local spd   = ply:GetVelocity():Length()
        local sway  = math.sin(RealTime() * 4) * math.min(spd / 2200, 1) * 0.8
        local tv    = ply._D6AngVel or ply.D6TurnVel or { pitch = 0, yaw = 0 }
        local xsway = tv.yaw * 18

        render.DepthRange(0, 0.1)
        for _, name in ipairs(RState.order) do
            local v = RState.elements[name]
            local m = v and v.modelEnt
            if v and not v.hide and IsValid(m) then
                local pos, ang = CamOrientation(RState.elements, v, ep, ea)
                local sm = 1
                if v.anim and v.anim.swayMult ~= nil then sm = v.anim.swayMult end

                m:SetPos(pos + ang:Forward() * v.pos.x
                             + ang:Right()   * (v.pos.y + xsway * sm)
                             + ang:Up()      * (v.pos.z + sway  * sm))
                ang:RotateAroundAxis(ang:Up(),      v.angle.y)
                ang:RotateAroundAxis(ang:Right(),   v.angle.p)
                ang:RotateAroundAxis(ang:Forward(), v.angle.r)
                m:SetAngles(ang)

                local mtx = Matrix()
                mtx:Scale(v.size)
                m:EnableMatrix("RenderMultiply", mtx)

                if v.material == "" then
                    m:SetMaterial("")
                elseif m:GetMaterial() ~= v.material then
                    m:SetMaterial(v.material)
                end
                if v.skin and m:GetSkin() ~= v.skin then m:SetSkin(v.skin) end

                if v.color then
                    render.SetColorModulation(v.color.r / 255, v.color.g / 255, v.color.b / 255)
                    render.SetBlend(v.color.a / 255)
                end
                m:SetupBones()
                m:DrawModel()
                render.SetBlend(1)
                render.SetColorModulation(1, 1, 1)
            end
        end
        render.DepthRange(0, 1)
    end)

    -- Q-меню: зарегистрировано в d6_weaponworkshop.lua
    -- (категория WeaponEditor → панель «Внешний вид оружия»)

end

print("[D6] d6_sck_bridge.lua загружен")
