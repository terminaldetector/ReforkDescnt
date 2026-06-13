-- =========================================================
-- d6_gravgun_cfg.lua — живая настройка грави-пушек DRMD
--
--   Позволяет регулировать параметры грави-оружия
--   (weapon_d6_gravy_railgun, weapon_d6_railmk2) через Q-меню
--   без правки Lua: дальность, скорость, масса, энергия, КД…
--
--   Архитектура (зеркало d6_sck_bridge):
--     • СЕРВЕР — авторитет: принимает значение от админа,
--       клампит по схеме, хранит в D6_GravCfg.Values, пишет
--       в DATA/drmd_gravcfg/live.txt, рассылает всем.
--       Оружие читает D6_GravCfg.Get на сервере → live-значение.
--     • КЛИЕНТ — зеркало Values для меню + Submit на сервер.
--       Пресеты («быстрое сохранение оружия») — снимок всех
--       значений в DATA/drmd_gravcfg/presets/<name>.txt.
--
--   Совместимость: без этого файла оружие использует свои
--   захардкоженные локалы (RefreshGravCfg делает ранний выход).
--   Дефолты схемы 1:1 совпадают с локалами оружия — без
--   переопределения поведение байт-в-байт прежнее.
-- =========================================================

D6_GravCfg = D6_GravCfg or {}
D6_GravCfg.Values = D6_GravCfg.Values or {}   -- [class][key] = number

-- Порядок и человекочитаемые имена грави-классов
D6_GravCfg.Order = { "weapon_d6_gravy_railgun", "weapon_d6_railmk2" }
D6_GravCfg.Names = {
    weapon_d6_gravy_railgun = "Грави-Рельса",
    weapon_d6_railmk2       = "Рельса МК2",
}

-- ── Схема настраиваемых параметров (дефолты = локалы оружия) ──
D6_GravCfg.Schema = {
    weapon_d6_gravy_railgun = {
        { key="ENERGY_RAIL",     label="Энергия рельсы",     min=0,    max=100,   def=20,    dec=0 },
        { key="ENERGY_FAN",      label="Энергия дробовика",  min=0,    max=100,   def=30,    dec=0 },
        { key="RAIL_RANGE",      label="Дальн. захвата",     min=200,  max=4000,  def=1500,  dec=0 },
        { key="RAIL_FIRE_SPEED", label="Скор. рельсы (экстремальная)", min=2000, max=999999, def=18000, dec=0 },
        { key="RAIL_MAX_MASS",   label="Макс. масса рельсы", min=10,   max=1000,  def=300,   dec=0 },
        { key="FAN_CONE",        label="Конус пылесоса °",   min=2,    max=60,    def=15,    dec=0 },
        { key="FAN_RANGE",       label="Дальн. пылесоса",    min=100,  max=2000,  def=500,   dec=0 },
        { key="FAN_MAX_MASS",    label="Масса пропа",        min=5,    max=300,   def=50,    dec=0 },
        { key="FAN_MAX_COUNT",   label="Кол-во пропов",      min=1,    max=20,    def=8,     dec=0, int=true },
        { key="FAN_FIRE_SPEED",  label="Скор. дроби (экстремальная)", min=2000, max=999999, def=12000, dec=0 },
        { key="FAN_SPREAD",      label="Разброс дроби °",    min=0,    max=45,    def=20,    dec=0 },
        { key="FIRE_COOLDOWN",   label="Перезарядка, с",     min=0.05, max=2,     def=0.3,   dec=2 },
        { key="MAX_RICOCHETS",   label="Рикошеты",           min=0,    max=10,    def=4,     dec=0, int=true },
    },
    weapon_d6_railmk2 = {
        { key="ENERGY_QUICK",  label="Энергия щелчка",   min=0,    max=100,   def=18,   dec=0 },
        { key="ENERGY_CHARGE", label="Энергия заряда",   min=0,    max=100,   def=35,   dec=0 },
        { key="ENERGY_LURE",   label="Энергия тяги",     min=0,    max=100,   def=12,   dec=0 },
        { key="CHARGE_TIME",   label="Время заряда, с",  min=0.1,  max=3,     def=0.6,  dec=2 },
        { key="RAIL_RANGE",    label="Дальн. рельсы",    min=1000, max=20000, def=8000, dec=0 },
        { key="LURE_RANGE",    label="Дальн. тяги",      min=200,  max=4000,  def=1800, dec=0 },
        { key="LURE_SPEED",    label="Скор. тяги",       min=200,  max=5000,  def=1400, dec=0 },
        { key="LURE_MAX_MASS", label="Макс. масса тяги", min=10,   max=1000,  def=400,  dec=0 },
        { key="LURE_COOLDOWN", label="Перезар. тяги, с", min=0.1,  max=5,     def=1.2,  dec=2 },
    },
}

-- ── Хелперы чтения (общие для сервера и клиента) ────────────

local function FindField(class, key)
    local sch = D6_GravCfg.Schema[class]
    if not sch then return nil end
    for _, f in ipairs(sch) do
        if f.key == key then return f end
    end
    return nil
end
D6_GravCfg.FindField = FindField

function D6_GravCfg.Default(class, key)
    local f = FindField(class, key)
    return f and f.def or nil
end

-- Live-значение или дефолт схемы или fallback.
function D6_GravCfg.Get(class, key, fallback)
    local cv = D6_GravCfg.Values[class]
    local v  = cv and cv[key]
    if v ~= nil then return v end
    local def = D6_GravCfg.Default(class, key)
    if def ~= nil then return def end
    return fallback
end

-- Снимок ВСЕХ действующих значений (для сохранения пресета).
function D6_GravCfg.Snapshot()
    local out = {}
    for class, fields in pairs(D6_GravCfg.Schema) do
        out[class] = {}
        for _, f in ipairs(fields) do
            out[class][f.key] = D6_GravCfg.Get(class, f.key)
        end
    end
    return out
end

-- Применение значения в текущем realm (без сети), с клампом.
function D6_GravCfg.SetLocal(class, key, value)
    local f = FindField(class, key)
    if not f then return false end
    value = math.Clamp(tonumber(value) or f.def, f.min, f.max)
    if f.int then value = math.floor(value + 0.5) end
    D6_GravCfg.Values[class] = D6_GravCfg.Values[class] or {}
    D6_GravCfg.Values[class][key] = value
    return true
end

local DIR        = "drmd_gravcfg"
local LIVE_PATH  = DIR .. "/live.txt"
local PRESET_DIR = DIR .. "/presets"

-- =========================================================
-- СЕРВЕР: авторитет, персист, сеть
-- =========================================================
if SERVER then

    util.AddNetworkString("D6_GravCfg_Set")    -- клиент → сервер (одно значение)
    util.AddNetworkString("D6_GravCfg_Sync")   -- сервер → клиенты (весь Values JSON)
    util.AddNetworkString("D6_GravCfg_Preset") -- клиент → сервер (загрузка пресета целиком)

    -- Гиперзвук недостижим при дефолтном sv_maxvelocity (3500): движок
    -- обрежет скорость пропа. Поднимаем лимит, чтобы настройка «скорость
    -- движения пропов вплоть до гиперзвука» реально работала. Корабль и
    -- прочая физика разгоняются собственными силами — потолок лишь снимает
    -- ограничение для разогнанных рельсой пропов.
    hook.Add("InitPostEntity", "D6_GravCfg_MaxVel", function()
        -- Физика Havok принудительно гасит скорость выше sv_maxvelocity.
        -- Устанавливаем предел в 999999, чтобы ползунок «скорость» мог
        -- выдать реальный результат вплоть до экстремальных значений.
        local cv = GetConVar("sv_maxvelocity")
        if cv and cv:GetInt() < 999999 then
            RunConsoleCommand("sv_maxvelocity", "999999")
        end
    end)

    local function SaveLive()
        if not file.IsDir(DIR, "DATA") then file.CreateDir(DIR) end
        file.Write(LIVE_PATH, util.TableToJSON(D6_GravCfg.Values, true))
    end

    local function LoadLive()
        local raw = file.Read(LIVE_PATH, "DATA")
        local tbl = raw and util.JSONToTable(raw)
        if not istable(tbl) then return end
        for class, keys in pairs(tbl) do
            if istable(keys) then
                for key, val in pairs(keys) do
                    D6_GravCfg.SetLocal(class, key, val)
                end
            end
        end
    end
    LoadLive()

    local function Broadcast(ply)
        net.Start("D6_GravCfg_Sync")
            net.WriteString(util.TableToJSON(D6_GravCfg.Values))
        if ply then net.Send(ply) else net.Broadcast() end
    end
    D6_GravCfg.Broadcast = Broadcast

    local function IsAllowed(ply)
        return IsValid(ply) and (ply:IsAdmin() or ply:IsListenServerHost())
    end

    net.Receive("D6_GravCfg_Set", function(_, ply)
        if not IsAllowed(ply) then
            if IsValid(ply) then ply:PrintMessage(HUD_PRINTTALK, "[D6 Грави] Нужны права admin") end
            return
        end
        local class = net.ReadString()
        local key   = net.ReadString()
        local value = net.ReadFloat()
        if D6_GravCfg.SetLocal(class, key, value) then
            SaveLive()
            Broadcast()
        end
    end)

    -- Загрузка пресета: таблица class→{key=val}; применяем всё.
    net.Receive("D6_GravCfg_Preset", function(_, ply)
        if not IsAllowed(ply) then
            if IsValid(ply) then ply:PrintMessage(HUD_PRINTTALK, "[D6 Грави] Нужны права admin") end
            return
        end
        local raw = net.ReadString()
        local tbl = util.JSONToTable(raw or "")
        if not istable(tbl) then return end
        local applied = 0
        for class, keys in pairs(tbl) do
            if istable(keys) then
                for key, val in pairs(keys) do
                    if D6_GravCfg.SetLocal(class, key, val) then applied = applied + 1 end
                end
            end
        end
        if applied > 0 then
            SaveLive()
            Broadcast()
            ply:PrintMessage(HUD_PRINTTALK, "[D6 Грави] Пресет применён (" .. applied .. " парам.)")
        end
    end)

    -- Полная синхронизация при подключении
    hook.Add("PlayerInitialSpawn", "D6_GravCfg_Sync", function(ply)
        timer.Simple(4, function()
            if IsValid(ply) then Broadcast(ply) end
        end)
    end)

end

-- =========================================================
-- КЛИЕНТ: зеркало, Submit, пресеты
-- =========================================================
if CLIENT then

    net.Receive("D6_GravCfg_Sync", function()
        local raw = net.ReadString()
        local tbl = util.JSONToTable(raw or "")
        if not istable(tbl) then return end
        D6_GravCfg.Values = {}
        for class, keys in pairs(tbl) do
            if istable(keys) then
                for key, val in pairs(keys) do
                    D6_GravCfg.SetLocal(class, key, val)
                end
            end
        end
    end)

    -- Отправка одного значения на сервер
    function D6_GravCfg.Submit(class, key, value)
        local f = FindField(class, key)
        if not f then return end
        value = math.Clamp(tonumber(value) or f.def, f.min, f.max)
        if f.int then value = math.floor(value + 0.5) end
        D6_GravCfg.SetLocal(class, key, value)   -- оптимистичное локальное эхо
        net.Start("D6_GravCfg_Set")
            net.WriteString(class)
            net.WriteString(key)
            net.WriteFloat(value)
        net.SendToServer()
    end

    -- Сброс класса к дефолтам схемы — одним батч-сообщением.
    function D6_GravCfg.ResetClass(class)
        local sch = D6_GravCfg.Schema[class]
        if not sch then return end
        local tbl = { [class] = {} }
        for _, f in ipairs(sch) do
            tbl[class][f.key] = f.def
            D6_GravCfg.SetLocal(class, f.key, f.def)   -- локальное эхо
        end
        net.Start("D6_GravCfg_Preset")
            net.WriteString(util.TableToJSON(tbl))
        net.SendToServer()
    end

    -- ── Пресеты («быстрое сохранение оружия») ───────────────
    local function EnsurePresetDir()
        if not file.IsDir(DIR, "DATA") then file.CreateDir(DIR) end
        if not file.IsDir(PRESET_DIR, "DATA") then file.CreateDir(PRESET_DIR) end
    end

    local function SafeName(name)
        name = string.lower(string.Trim(name or ""))
        name = name:gsub("[^%w_%-]", "_")
        return name
    end

    function D6_GravCfg.ListPresets()
        local files = file.Find(PRESET_DIR .. "/*.txt", "DATA") or {}
        local names = {}
        for _, fn in ipairs(files) do names[#names + 1] = fn:gsub("%.txt$", "") end
        table.sort(names)
        return names
    end

    -- Сохранить текущую настройку всех грави-пушек как именованный пресет.
    function D6_GravCfg.SavePreset(name)
        name = SafeName(name)
        if name == "" then return false, "пустое имя" end
        EnsurePresetDir()
        file.Write(PRESET_DIR .. "/" .. name .. ".txt",
            util.TableToJSON(D6_GravCfg.Snapshot(), true))
        return true, name
    end

    function D6_GravCfg.DeletePreset(name)
        name = SafeName(name)
        local path = PRESET_DIR .. "/" .. name .. ".txt"
        if file.Exists(path, "DATA") then file.Delete(path) end
    end

    -- Загрузить пресет → отправить весь снимок на сервер одним сообщением.
    function D6_GravCfg.LoadPreset(name)
        name = SafeName(name)
        local raw = file.Read(PRESET_DIR .. "/" .. name .. ".txt", "DATA")
        local tbl = raw and util.JSONToTable(raw)
        if not istable(tbl) then return false end
        net.Start("D6_GravCfg_Preset")
            net.WriteString(util.TableToJSON(tbl))
        net.SendToServer()
        return true
    end

end

print("[D6] d6_gravgun_cfg.lua загружен")
