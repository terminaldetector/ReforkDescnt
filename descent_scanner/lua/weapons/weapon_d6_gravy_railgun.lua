-- ═══════════════════════════════════════════════════════════
-- weapons/weapon_d6_gravy_railgun.lua
-- Грави-рельсовая пушка для Descent 6DOF
--
-- ЛКМ: захват объекта → повторный ЛКМ → рельсовый выстрел.
-- ПКМ: пылесос мелких пропов → повторный ПКМ → дробовик.
--
-- ИСПРАВЛЕНО:
--   - правильное имя класса (weapon_d6_gravy_railgun)
--   - удержание пропа не бьёт игрока (COLLISION_GROUP_DEBRIS)
--   - анимации deploy / idle / fire / holster через physcannon vm
--   - скорость рельсы увеличена (8 000), дробовика (7 000)
--   - утечка хуков flash-трассера устранена
--   - удалён мёртвый hook.Add("EntityTakeDamage") с пустым телом
--   - FanProps тоже получают COLLISION_GROUP_DEBRIS при подборе
-- ═══════════════════════════════════════════════════════════

if SERVER then AddCSLuaFile() end

SWEP.PrintName       = "Грави-Рельса"
SWEP.Author          = "Descent 6DOF"
SWEP.Instructions    = "ЛКМ — захват/рельса   ПКМ — пылесос/дробовик"
SWEP.Category        = "Descent"
SWEP.Slot            = 4
SWEP.SlotPos         = 1
SWEP.Spawnable       = true
SWEP.AdminSpawnable  = true

SWEP.Base            = "weapon_base"
SWEP.HoldType        = "physgun"
SWEP.ViewModel       = "models/weapons/v_physcannon.mdl"
SWEP.WorldModel      = "models/weapons/w_physcannon.mdl"
SWEP.UseHands        = false
SWEP.DrawCrosshair   = true
SWEP.DrawAmmo        = false

SWEP.Primary.ClipSize    = -1
SWEP.Primary.DefaultClip = -1
SWEP.Primary.Automatic   = false
SWEP.Primary.Ammo        = "none"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

-- ─── Параметры ──────────────────────────────────────────
local ENERGY_MAX      = 100
local ENERGY_REGEN    = 10          -- в секунду
local ENERGY_RAIL     = 20
local ENERGY_FAN      = 30
local RAIL_RANGE      = 1500
local RAIL_FIRE_SPEED = 18000       -- увеличено
local RAIL_MAX_MASS   = 300
local FAN_CONE        = 15          -- градусов
local FAN_RANGE       = 500
local FAN_MAX_MASS    = 50
local FAN_MAX_COUNT   = 8
local FAN_FIRE_SPEED  = 12000       -- увеличено
local FAN_SPREAD      = 20          -- градусов
local FIRE_COOLDOWN   = 0.3
local MAX_RICOCHETS   = 4           -- рикошеты для невзрывных пропов
local PROP_MODE       = 0           -- 0=гравити (Havok+gravity), 1=унгравити (глайдер)
local KIN_IMPACT_MIN  = 28000       -- порог кинетического взрыва (выше дефолтных скоростей)
local KIN_IMPACT_REF  = 90000       -- гиперзвук: полная мощность импакта
local HYPER_FLIGHT_MIN = 20000      -- скорость, при которой проп рвётся в полёте (глайдер)
local HYPER_ABLATION   = 1          -- 0=выкл, 1=вкл дым/осколки/распад в полёте
local HYPER_FRAG_MUL   = 1.0        -- множитель числа и урона осколков
local TINT            = Color(0, 255, 0)
local TINT_INNER      = Color(180, 255, 180)

-- ── Живая настройка через D6_GravCfg (Q-меню) ───────────
-- Переписывает локалы-параметры действующими значениями.
-- Без D6_GravCfg — ранний выход, локалы сохраняют дефолты.
local function RefreshGravCfg()
    if not D6_GravCfg then return end
    local c = "weapon_d6_gravy_railgun"
    ENERGY_RAIL     = D6_GravCfg.Get(c, "ENERGY_RAIL",     ENERGY_RAIL)
    ENERGY_FAN      = D6_GravCfg.Get(c, "ENERGY_FAN",      ENERGY_FAN)
    RAIL_RANGE      = D6_GravCfg.Get(c, "RAIL_RANGE",      RAIL_RANGE)
    RAIL_FIRE_SPEED = D6_GravCfg.Get(c, "RAIL_FIRE_SPEED", RAIL_FIRE_SPEED)
    RAIL_MAX_MASS   = D6_GravCfg.Get(c, "RAIL_MAX_MASS",   RAIL_MAX_MASS)
    FAN_CONE        = D6_GravCfg.Get(c, "FAN_CONE",        FAN_CONE)
    FAN_RANGE       = D6_GravCfg.Get(c, "FAN_RANGE",       FAN_RANGE)
    FAN_MAX_MASS    = D6_GravCfg.Get(c, "FAN_MAX_MASS",    FAN_MAX_MASS)
    FAN_MAX_COUNT   = math.floor(D6_GravCfg.Get(c, "FAN_MAX_COUNT", FAN_MAX_COUNT) + 0.5)
    FAN_FIRE_SPEED  = D6_GravCfg.Get(c, "FAN_FIRE_SPEED",  FAN_FIRE_SPEED)
    FAN_SPREAD      = D6_GravCfg.Get(c, "FAN_SPREAD",      FAN_SPREAD)
    FIRE_COOLDOWN   = D6_GravCfg.Get(c, "FIRE_COOLDOWN",   FIRE_COOLDOWN)
    MAX_RICOCHETS   = math.floor(D6_GravCfg.Get(c, "MAX_RICOCHETS", MAX_RICOCHETS) + 0.5)
    PROP_MODE       = math.floor(D6_GravCfg.Get(c, "PROP_MODE",     PROP_MODE)     + 0.5)
    HYPER_FLIGHT_MIN = D6_GravCfg.Get(c, "HYPER_FLIGHT_MIN", HYPER_FLIGHT_MIN)
    HYPER_ABLATION   = math.floor(D6_GravCfg.Get(c, "HYPER_ABLATION", HYPER_ABLATION) + 0.5)
    HYPER_FRAG_MUL   = D6_GravCfg.Get(c, "HYPER_FRAG_MUL", HYPER_FRAG_MUL)
end

-- Коробки/ящики — кассетные контейнеры при гиперзвуковом ударе.
local function IsBoxProp(ent)
    if not IsValid(ent) then return false end
    local mdl = string.lower(ent:GetModel() or "")
    return mdl:find("cardboard") ~= nil
        or mdl:find("carton")    ~= nil
        or mdl:find("crate")     ~= nil
        or (mdl:find("box") ~= nil and not mdl:find("ammo") and not mdl:find("speaker"))
end

-- Определяет, взрывчатый ли проп по имени модели.
-- Совпадает с детекцией в d6_ai.lua (explosive/gascan/canister).
local function IsExplosiveProp(ent)
    if not IsValid(ent) then return false end
    local mdl = string.lower(ent:GetModel() or "")
    return mdl:find("explosive") ~= nil
        or mdl:find("gascan")    ~= nil
        or mdl:find("canister")  ~= nil
        or mdl:find("propane")   ~= nil
end

-- ─── Сетевые строки ─────────────────────────────────────
if SERVER and not _D6_RAIL_NET then
    util.AddNetworkString("D6_RailFire")
    util.AddNetworkString("D6_RailGlow")         -- ореол/свет разогнанного пропа
    util.AddNetworkString("D6_RailHyperImpact")  -- ударная волна + вспышка клиенту
    _D6_RAIL_NET = true
end

-- ─── Защита удерживаемых пропов от детонации ─────────────
-- Пока проп держится рельсой (D6_RailNoBoom) или подтянут пылесосом
-- (D6_RailFanLocked), он не получает урон → взрывные бочки и т.п. НЕ
-- взрываются в захвате. Флаги снимаются при выстреле/сбросе, после чего
-- проп снова уязвим и детонирует от удара как снаряд.
if SERVER and not _D6_RAIL_DMGHOOK then
    _D6_RAIL_DMGHOOK = true
    hook.Add("EntityTakeDamage", "D6_RailHeldNoBoom", function(ent)
        if IsValid(ent) and (ent.D6_RailNoBoom or ent.D6_RailFanLocked) then
            return true   -- поглощаем урон целиком
        end
    end)
end

-- =========================================================
-- ХЕЛПЕРЫ
-- =========================================================
local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

local function MuzzlePos(ply)
    local ang = ShootAng(ply)
    return ply:GetShootPos() + ang:Forward() * 13, ang
end

-- Длинная (главная) ось пропа в его локальной системе: "x"|"y"|"z".
-- Берётся по наибольшему измерению OBB — для баллона/бочки это "z".
local function PropLongAxis(ent)
    if not IsValid(ent) then return "x" end
    local s  = ent:OBBMaxs() - ent:OBBMins()
    local ax, ay, az = math.abs(s.x), math.abs(s.y), math.abs(s.z)
    if az >= ax and az >= ay then return "z" end
    if ay >= ax and ay >= az then return "y" end
    return "x"
end

-- Мировой угол, при котором длинная ось пропа смотрит ВДОЛЬ прицела (fwd) —
-- ровный «дротик по прицелу», как на референс-скрине с баллонами. Не путать
-- с фиксированным вертикальным положением: проп всегда указывает туда, куда
-- целится игрок (вверх смотришь — баллон смотрит вверх, и т.д.).
local function AimLongAngle(longAxis, fwd)
    local fa = fwd:Angle()
    if longAxis == "z" then
        -- локальный +Z (стоячий баллон/бочка) → направление прицела
        return Angle(fa.p + 90, fa.y, 0)
    elseif longAxis == "y" then
        -- локальный +Y (вбок) → направление прицела
        return Angle(fa.p, fa.y - 90, 0)
    end
    -- локальный +X (вперёд) → направление прицела (по умолчанию)
    return Angle(fa.p, fa.y, 0)
end

local function CanHold(ent)
    if not IsValid(ent) then return false end
    if ent:IsPlayer() or ent:IsNPC() then return false end
    if ent:GetClass() == "worldspawn" then return false end
    local ph = ent:GetPhysicsObject()
    if not IsValid(ph) then return false end
    if ph:GetMass() > RAIL_MAX_MASS then return false end
    return true
end

-- Захваченный проп не должен физически сталкиваться с игроком.
-- COLLISION_GROUP_DEBRIS — проп проходит сквозь игрока (как в оригинальной гравипушке HL2).
local function LockCollision(ent)
    if not IsValid(ent) then return end
    ent._D6_OldCollision = ent:GetCollisionGroup()
    ent:SetCollisionGroup(COLLISION_GROUP_DEBRIS)
end

local function UnlockCollision(ent)
    if not IsValid(ent) then return end
    ent:SetCollisionGroup(ent._D6_OldCollision or COLLISION_GROUP_NONE)
    ent._D6_OldCollision = nil
end

-- =========================================================
-- ИНИЦИАЛИЗАЦИЯ
-- =========================================================
function SWEP:Initialize()
    RefreshGravCfg()
    self:SetHoldType("physgun")
    self.NextFire    = 0
    self.HeldEnt     = NULL
    self.HeldGrabT   = 0
    self.FanProps    = {}
    self.LastRegenT  = CurTime()
    self:SetNWInt("D6_RailEnergy", ENERGY_MAX)
    self:SetNWInt("D6_RailFanCount", 0)
    self:SetNWEntity("D6_RailHeld", NULL)
end

function SWEP:Deploy()
    self:SendWeaponAnim(ACT_VM_DRAW)
    self.NextFire = CurTime() + 0.6
    return true
end

function SWEP:OnRemove()  self:ReleaseHeld(); self:ReleaseFan() end
function SWEP:OnDrop()    self:ReleaseHeld(); self:ReleaseFan() end

function SWEP:Holster()
    self:ReleaseHeld()
    self:ReleaseFan()
    self:SendWeaponAnim(ACT_VM_HOLSTER)
    return true
end

-- ─── Освобождение одиночного пропа ──────────────────────
function SWEP:ReleaseHeld()
    if SERVER and IsValid(self.HeldEnt) then
        local ph = self.HeldEnt:GetPhysicsObject()
        if IsValid(ph) then
            ph:EnableGravity(true)
            ph:EnableMotion(true)
            ph:Wake()
        end
        UnlockCollision(self.HeldEnt)
        self.HeldEnt.D6_RailNoBoom = nil
    end
    self.HeldEnt = NULL
    self.HeldLongAxis = nil
    if SERVER then self:SetNWEntity("D6_RailHeld", NULL) end
end

-- =========================================================
-- ЭНЕРГИЯ
-- =========================================================
-- Энергия рельсы теперь черпается из общего резерва игрока (d6_energy.lua).
-- D6_RailEnergy сохраняется как зеркало NWInt для кокпит-HUD (Section 2).
function SWEP:GetEnergy()
    local owner = self:GetOwner()
    if IsValid(owner) and D6_Energy then return math.floor(D6_Energy.Get(owner)) end
    return self:GetNWInt("D6_RailEnergy", ENERGY_MAX)
end
function SWEP:SetEnergy(v)
    if not SERVER then return end
    v = math.Clamp(math.floor(v), 0, ENERGY_MAX)
    local owner = self:GetOwner()
    if IsValid(owner) and D6_Energy then
        -- расход идёт через SetEnergy(GetEnergy()-COST): переводим абсолют в дельту пула
        local cur = D6_Energy.Get(owner)
        if v < cur then
            D6_Energy.TryConsume(owner, "weapons", cur - v)
        elseif v > cur then
            D6_Energy.Add(owner, v - cur)
        end
    end
    self:SetNWInt("D6_RailEnergy", v)  -- зеркало для cockpit
end
function SWEP:HasEnergy(cost) return self:GetEnergy() >= cost end

function SWEP:DenyFire()
    local ply = self:GetOwner()
    if IsValid(ply) then
        ply:EmitSound("common/wpn_denyselect.wav", 60, 110)
    end
    self.NextFire = CurTime() + 0.2
end

-- =========================================================
-- THINK — регенерация + удержание пропа
-- =========================================================
function SWEP:Think()
    if SERVER then
        -- Реген централизован в d6_energy.lua; держим зеркало D6_RailEnergy для cockpit
        local owner = self:GetOwner()
        if IsValid(owner) and D6_Energy then
            self:SetNWInt("D6_RailEnergy", math.floor(D6_Energy.Get(owner)))
        end

        -- Удержание пропа перед стволом
        if IsValid(self.HeldEnt) then
            local ply = self:GetOwner()
            if not IsValid(ply) or ply:GetActiveWeapon() ~= self then
                self:ReleaseHeld(); return
            end
            local ang    = ShootAng(ply)
            local target = ply:GetShootPos() + ang:Forward() * 90
            local ph     = self.HeldEnt:GetPhysicsObject()
            if IsValid(ph) then
                ph:Wake()
                ph:EnableGravity(false)

                -- Желаемая ориентация: длинная ось пропа смотрит вдоль
                -- прицела (ровный «дротик»), а не в исходную позу.
                local holdAng = AimLongAngle(self.HeldLongAxis or "x", ang:Forward())

                -- ComputeShadowControl — тот же контроллер, что у физгана HL2:
                -- плавно ведёт проп к позиции И ориентации, полностью гася
                -- кувыркание. Критически задемпфирован (dampfactor≈1) — без
                -- перелёта даже для тяжёлых пропов.
                ph:ComputeShadowControl({
                    secondstoarrive  = 0.05,
                    pos              = target,
                    angle            = holdAng,
                    maxangular       = 5000,
                    maxangulardamp   = 20000,
                    maxspeed         = 100000,
                    maxspeeddamp     = 200000,
                    dampfactor       = 1.0,
                    teleportdistance = 0,
                })
                -- Коллизия задана при захвате (LockCollision)
            else
                self:ReleaseHeld()
            end
        end
    end

    -- Анимация idle когда ничего не происходит
    if CLIENT then return end
end

-- =========================================================
-- ЛКМ — РЕЛЬСА (захват ↔ выстрел)
-- =========================================================
function SWEP:PrimaryAttack()
    RefreshGravCfg()
    if CurTime() < (self.NextFire or 0) then return end
    self.NextFire = CurTime() + FIRE_COOLDOWN

    if not SERVER then return end

    local ply = self:GetOwner()
    if not IsValid(ply) then return end

    if IsValid(self.HeldEnt) then
        self:FireRail()
        return
    end

    if not self:HasEnergy(ENERGY_RAIL) then
        self:DenyFire(); return
    end

    local ang = ShootAng(ply)
    local tr  = util.TraceLine({
        start  = ply:GetShootPos(),
        endpos = ply:GetShootPos() + ang:Forward() * RAIL_RANGE,
        filter = { ply, self },
        mask   = MASK_SHOT,
    })

    if CanHold(tr.Entity) then
        self.HeldEnt   = tr.Entity
        self.HeldGrabT = CurTime()
        -- Длинная ось пропа смотрит вдоль прицела (ровный «дротик»),
        -- единое поведение со стоком/дробовиком.
        self.HeldLongAxis = PropLongAxis(tr.Entity)
        LockCollision(tr.Entity)
        tr.Entity.D6_RailNoBoom = true   -- взрывной проп не детонирует в захвате
        self:SetNWEntity("D6_RailHeld", tr.Entity)
        ply:EmitSound("weapons/physcannon/physcannon_pickup.wav", 75, 130)
        self:SendWeaponAnim(ACT_VM_PRIMARYATTACK)
    else
        self:DenyFire()
    end
end

-- ── Выстрел рельсой ──────────────────────────────────────
function SWEP:FireRail()
    local ply = self:GetOwner()
    if not IsValid(ply) or not IsValid(self.HeldEnt) then
        self:ReleaseHeld(); return
    end
    if not self:HasEnergy(ENERGY_RAIL) then
        self:DenyFire(); return
    end
    self:SetEnergy(self:GetEnergy() - ENERGY_RAIL)

    local ent  = self.HeldEnt
    local ph   = ent:GetPhysicsObject()
    local ang  = ShootAng(ply)
    local dir  = ang:Forward()
    local mass = IsValid(ph) and ph:GetMass() or 50
    local dmg  = math.Clamp(mass * 0.5, 20, 120)

    -- Снять блокировку коллизий ПЕРЕД выстрелом
    UnlockCollision(ent)
    ent.D6_RailNoBoom = nil      -- выпущенный проп снова может детонировать
    self:ReleaseHeld()

    -- Наследование импульса корабля + отдача (связь с движением)
    local inherit = D6_Wep and (D6_Wep.ShipVel(ply) * D6_Wep.Inherit()) or Vector(0, 0, 0)
    local fireVel = dir * RAIL_FIRE_SPEED + inherit
    if IsValid(ph) then
        ph:EnableMotion(true)
        ph:EnableDrag(false)    -- гасим аэродинамику: Havok иначе замедляет проп
        if PROP_MODE == 1 then
            -- Унгравити: полное снятие Havok-сил → чистый гиперзвуковой глайдер
            ph:EnableGravity(false)
            ph:SetDamping(0, 0)  -- убрать линейное и угловое затухание
        else
            ph:EnableGravity(true)
        end
        ph:Wake()
        ph:SetVelocity(fireVel)
        timer.Simple(0, function()
            if IsValid(ent) then
                local p2 = ent:GetPhysicsObject()
                if IsValid(p2) then p2:SetVelocity(fireVel) end
            end
        end)
    end
    if D6_Wep then D6_Wep.ApplyRecoil(ply, dir, 90) end

    -- Метки снаряда для детекции урона
    ent.D6_RailOwner = ply
    ent.D6_RailDmg   = dmg
    ent.D6_RailHit   = false
    ent.D6_RailUntil = CurTime() + 4
    ent:CollisionRulesChanged()

    ply:EmitSound("weapons/physcannon/superphys_launch1.wav", 80, 90)

    local muz = ply:GetShootPos() + dir * 30
    local ef  = EffectData()
    ef:SetOrigin(muz); ef:SetNormal(dir); ef:SetMagnitude(2); ef:SetScale(1)
    util.Effect("ManhackSparks", ef)

    net.Start("D6_RailFire")
        net.WriteEntity(ply)
        net.WriteVector(muz)
        net.WriteVector(ent:GetPos())
        net.WriteUInt(0, 2)
    net.Broadcast()

    self:HookRailProjectile(ent)
    -- Визуальная подпись рельсотрона: жёлто-белый шлейф + ореол.
    -- Скорость запуска (вплоть до гиперзвука) задаёт размер шлейфа.
    self:AttachRailVisual(ent, (dir * RAIL_FIRE_SPEED + inherit):Length())
    -- Разрушение пропа в полёте: дым, осколки, распад «глайдера».
    -- Только если запуск действительно гиперзвуковой (иначе поведение прежнее).
    if fireVel:Length() >= HYPER_FLIGHT_MIN then
        self:AttachHyperFlight(ent, mass)
    end
    self:SendWeaponAnim(ACT_VM_SECONDARYATTACK)
end

-- ── Рельсовый визуальный след: жёлто-белый хвост + ореол ──
-- Хвост (env_spritetrail) сервер-сторонний — реплицируется сам.
-- Ореол + динамический свет рисует клиент по net D6_RailGlow.
-- Ширина/яркость растут со скоростью: гиперзвуковой проп тянет
-- огромный светящийся шлейф — однозначная подпись рельсотрона.
function SWEP:AttachRailVisual(ent, speed)
    if not SERVER or not IsValid(ent) then return end
    local base = 12000   -- ниже этой скорости заметного шлейфа нет
    local frac = math.Clamp((speed - base) / (KIN_IMPACT_REF - base), 0, 1)

    local w = Lerp(frac, 26, 120)
    -- Внешний жёлто-белый светящийся шлейф
    local t1 = util.SpriteTrail(ent, 0, Color(255, 236, 140), true,
        w, 0, Lerp(frac, 0.45, 1.0), 1 / (w * 0.7 + 1), "trails/laser.vmt")
    -- Внутренний яркий белый сердечник
    local t2 = util.SpriteTrail(ent, 0, Color(255, 255, 255), true,
        w * 0.4, 0, Lerp(frac, 0.3, 0.6), 1 / (w * 0.4 + 1), "trails/laser.vmt")

    -- Снимаем шлейф, когда снаряд отработал (проп оседает как мусор)
    timer.Simple(4.5, function()
        if IsValid(t1) then t1:Remove() end
        if IsValid(t2) then t2:Remove() end
    end)

    net.Start("D6_RailGlow")
        net.WriteEntity(ent)
        net.WriteFloat(frac)
    net.Broadcast()
end

-- ── Воздушный кассетный залп: коробка распадается в полёте ──
-- Распад «глайдера» прямо в воздухе → веер мелких детонаций по
-- ходу движения. Геометрия залпа привязана к направлению полёта,
-- мощность/число — к скорости (t) и массе (massF).
function SWEP:HyperClusterBurst(atk, pos, dir, t, massF)
    local count  = math.floor(Lerp(t, 6, 18) * (0.7 + massF * 0.3))
    local sDmg   = Lerp(t, 20, 70) * HYPER_FRAG_MUL
    local sRad   = Lerp(t, 60, 160)
    local sRange = Lerp(t, 140, 420)

    -- Ортонормированный базис вокруг направления полёта
    local up = math.abs(dir.z) < 0.9 and Vector(0, 0, 1) or Vector(1, 0, 0)
    local cr = dir:Cross(up):GetNormalized()
    local cu = dir:Cross(cr):GetNormalized()

    local ef = EffectData()
    ef:SetOrigin(pos); ef:SetScale(2 + t * 4); ef:SetMagnitude(200)
    util.Effect("HelicopterMegaBomb", ef)
    sound.Play("npc/attack_helicopter/aheli_strafe1.wav", pos, 92, 90)

    for j = 1, count do
        timer.Simple(j * Lerp(t, 0.035, 0.016), function()
            local a   = j * 2.39996                -- золотое сечение → плотный веер
            local d   = math.Rand(40, sRange)
            local fwd = math.Rand(0, d * 0.6)      -- осколки идут преимущественно вперёд
            local sp  = pos + cr * math.cos(a) * d + cu * math.sin(a) * d + dir * fwd
            util.BlastDamage(atk, atk, sp, sRad, sDmg)
            local e1 = EffectData(); e1:SetOrigin(sp); e1:SetScale(1 + t * 0.8)
            util.Effect("Explosion", e1)
            local e2 = EffectData(); e2:SetOrigin(sp); e2:SetScale(0.6 + t)
            util.Effect("WheelDust", e2)
            sound.Play("ambient/explosions/explode_" .. math.random(1, 4) .. ".wav",
                sp, 66, math.random(125, 160))
        end)
    end

    net.Start("D6_RailHyperImpact")
        net.WriteVector(pos); net.WriteVector(dir); net.WriteFloat(t); net.WriteBool(true)
    net.Broadcast()
end

-- ── Разрушение пропа в полёте: «глайдер-лаунчер» ────────────
-- Пока запущенный проп идёт на гиперзвуке, набегающий поток рвёт его
-- на части: он тянет дымовой шлейф (сприте-лента + клубы) и сбрасывает
-- горящие осколки-взрывчатку. Картон/коробки распадаются полностью в
-- кассетный залп ещё в воздухе; взрывной баллон вскрывается и детонирует
-- в полёте; прочный проп лишь дымит и сыплет искрами до удара.
-- Всё масштабируется: скорость × масса × характеристика пропа.
function SWEP:AttachHyperFlight(ent, mass)
    if not SERVER or not IsValid(ent) then return end
    if HYPER_ABLATION ~= 1 then return end

    local owner  = ent.D6_RailOwner
    local isBox  = IsBoxProp(ent)
    local isExpl = IsExplosiveProp(ent)
    local massF  = math.Clamp((mass or 50) / 100, 0.35, 2.5)
    local mNorm  = (massF - 0.35) / 2.15            -- 0..1 по массе
    local id     = "D6_HyperFlight_" .. ent:EntIndex()

    -- Прочность: сколько гиперзвукового напора проп держит до распада.
    -- Картон рвётся быстро, баллон — средне, прочный проп не распадается
    -- (math.huge) и доживает до удара, где работает DoKineticImpact.
    local integ = isBox  and (70  + mass * 0.6)
               or isExpl and (130 + mass * 1.0)
               or            math.huge
    local broken = false

    -- Дымовой «снаряд»: серая лента, тянущаяся за пропом всю трассу.
    local smokeW = Lerp(mNorm, 26, 96)
    local strail = util.SpriteTrail(ent, 0, Color(46, 42, 38), true,
        smokeW, 4, 0.95, 1 / (smokeW + 1), "trails/smoke.vmt")
    local function CleanTrail()
        if IsValid(strail) then
            local s = strail
            timer.Simple(1.2, function() if IsValid(s) then s:Remove() end end)
        end
    end

    local smokeInt        = Lerp(mNorm, 0.10, 0.045)   -- тяжёлый проп дымит гуще
    local smokeAcc, fragAcc = 0, 0
    local dt              = 0.045

    timer.Create(id, dt, 0, function()
        if not IsValid(ent) or ent.D6_RailHit or broken then
            timer.Remove(id); CleanTrail(); return
        end
        local ph = ent:GetPhysicsObject()
        if not IsValid(ph) then timer.Remove(id); CleanTrail(); return end

        local vel = ph:GetVelocity()
        local spd = vel:Length()
        local pos = ent:GetPos()

        -- Ниже порога — поток уже не разрушает проп; гасим, когда совсем сбросил.
        if spd < HYPER_FLIGHT_MIN then
            if spd < HYPER_FLIGHT_MIN * 0.7 then timer.Remove(id); CleanTrail() end
            return
        end

        local t   = math.Clamp((spd - HYPER_FLIGHT_MIN) / (KIN_IMPACT_REF - HYPER_FLIGHT_MIN), 0, 1)
        local dir = vel:GetNormalized()
        local atk = IsValid(owner) and owner or game.GetWorld()

        -- ── Дымовые клубы вдоль трассы ────────────────────────
        smokeAcc = smokeAcc + dt
        if smokeAcc >= smokeInt then
            smokeAcc = 0
            local sm = EffectData()
            sm:SetOrigin(pos - dir * Lerp(t, 20, 60))
            sm:SetNormal(-dir)
            sm:SetScale(0.5 + t * (0.8 + massF * 0.7))
            sm:SetMagnitude(1)
            util.Effect("WheelDust", sm)
        end

        -- ── Сброс осколков ────────────────────────────────────
        fragAcc = fragAcc + dt
        if fragAcc >= Lerp(t, 0.16, 0.06) then
            fragAcc = 0
            local side = (dir:Cross(VectorRand())):GetNormalized()
            local fpos = pos - dir * math.Rand(10, 50)
                       + side * math.Rand(6, 24 + t * 40)
            if isBox or isExpl then
                -- осколок-взрывчатка: маленькая детонация + урон по площади
                local fr = EffectData(); fr:SetOrigin(fpos); fr:SetScale(0.6 + t * 0.9)
                util.Effect("Explosion", fr)
                local fd = EffectData(); fd:SetOrigin(fpos); fd:SetScale(0.6 + t)
                util.Effect("WheelDust", fd)
                util.BlastDamage(IsValid(ent) and ent or atk, atk, fpos,
                    Lerp(t, 50, 130) * (0.7 + massF * 0.3),
                    Lerp(t, 12, 48) * HYPER_FRAG_MUL)
                if math.random() < 0.4 then
                    sound.Play("ambient/explosions/explode_" .. math.random(1, 4) .. ".wav",
                        fpos, 70, math.random(120, 160))
                end
            else
                -- прочный проп: только искры абляции, без урона
                local sp = EffectData()
                sp:SetOrigin(fpos); sp:SetNormal(-dir); sp:SetMagnitude(1 + t * 2)
                util.Effect("ManhackSparks", sp)
            end
        end

        -- ── Расход прочности → распад в воздухе ───────────────
        if integ ~= math.huge then
            integ = integ - spd * dt * 0.012 * HYPER_FRAG_MUL
            if integ <= 0 then
                broken = true
                ent.D6_RailHit = true
                timer.Remove(id)
                CleanTrail()
                local bpos = pos
                if isExpl then
                    -- баллон вскрывается и детонирует в полёте
                    local ef = EffectData(); ef:SetOrigin(bpos)
                    ef:SetScale(3); ef:SetMagnitude(180)
                    util.Effect("Explosion", ef); util.Effect("HelicopterMegaBomb", ef)
                    util.BlastDamage(atk, atk, bpos, 250, 200)
                    sound.Play("weapons/rpg/rocket_explode.wav", bpos, 100, 100)
                else
                    -- коробка распадается в кассетный залп прямо в воздухе
                    self:HyperClusterBurst(atk, bpos, dir, t, massF)
                end
                if IsValid(ent) then SafeRemoveEntity(ent) end
            end
        end
    end)
end

-- ── Отслеживание снаряда: взрыв, рикошет, урон ──────────
-- Детекция контакта — через PhysicsCollide (реальный физконтакт).
-- Внутри колбэка нельзя удалять энтить/спавнить эффекты — переносим
-- всё в timer.Simple(0, ...). Для отражения берём OurOldVelocity
-- (скорость ДО столкновения), иначе физика уже погасит импульс.
function SWEP:HookRailProjectile(ent)
    if not IsValid(ent) then return end
    ent.D6_IsProjectile = true
    local owner   = ent.D6_RailOwner
    local isExpl  = IsExplosiveProp(ent)
    local bounces = 0
    local done    = false

    local function DoExplode(pos)
        local atk = IsValid(owner) and owner or game.GetWorld()
        local ef = EffectData(); ef:SetOrigin(pos); ef:SetScale(4); ef:SetMagnitude(180)
        util.Effect("Explosion", ef)
        util.Effect("HelicopterMegaBomb", ef)
        util.BlastDamage(atk, atk, pos, 250, 180)
        sound.Play("weapons/rpg/rocket_explode.wav", pos, 100, 100)
    end

    -- Кинетический импакт (масштаб от скорости):
    --   • Основной взрыв + многослойный серый дым
    --   • Кассетный рассев для коробок/ящиков (IsBoxProp)
    --   • Ударная волна клиенту (D6_RailHyperImpact)
    local function DoKineticImpact(pos, nrm, speed)
        local t   = math.Clamp((speed - KIN_IMPACT_MIN) / (KIN_IMPACT_REF - KIN_IMPACT_MIN), 0, 1)
        local rad = Lerp(t, 120, 600)
        local dm  = Lerp(t, 30, 220)
        local atk = IsValid(owner) and owner or game.GetWorld()
        nrm = (nrm and nrm:LengthSqr() > 0.0001) and nrm or Vector(0, 0, 1)

        -- Основной взрыв
        local ef = EffectData()
        ef:SetOrigin(pos); ef:SetNormal(nrm)
        ef:SetScale(1 + t * 3); ef:SetMagnitude(60 + t * 180)
        util.Effect("Explosion", ef)
        if t > 0.2 then util.Effect("HelicopterMegaBomb", ef) end

        -- Послойный серый дым: клубы вокруг эпицентра
        local smokeLayers = 1 + math.floor(t * 4)
        for i = 1, smokeLayers do
            local sof = EffectData()
            local ang  = (i / smokeLayers) * math.pi * 2
            local dist = (i - 1) * Lerp(t, 28, 75)
            sof:SetOrigin(pos + Vector(math.cos(ang)*dist, math.sin(ang)*dist, dist*0.25))
            sof:SetNormal(nrm); sof:SetScale(1 + t * (1.4 + i * 0.25)); sof:SetMagnitude(1)
            util.Effect("WheelDust", sof)
        end

        util.Decal("Scorch", pos + nrm * 8, pos - nrm * 8)
        util.BlastDamage(IsValid(ent) and ent or atk, atk, pos, rad, dm)
        sound.Play("ambient/explosions/explode_" .. math.random(2, 4) .. ".wav",
            pos, 100, math.random(80, 110))

        -- ── Кассетные боеприпасы: картонные коробки → рассеянный залп ──
        local isBox = IsBoxProp(ent)
        if isBox and t > 0.05 then
            local count  = math.floor(Lerp(t, 8, 22))
            local sDmg   = Lerp(t, 22, 80)
            local sRad   = Lerp(t, 65, 175)
            local sRange = Lerp(t, 180, 530)
            -- Перпендикулярный базис для равномерного рассева
            local tmp2 = math.abs(nrm.z) < 0.9 and Vector(0,0,1) or Vector(1,0,0)
            local cr2  = nrm:Cross(tmp2):GetNormalized()
            local cu2  = nrm:Cross(cr2):GetNormalized()

            sound.Play("npc/attack_helicopter/aheli_strafe1.wav", pos, 92, 88)
            -- Вспышка при «вскрытии контейнера»
            local ef2 = EffectData(); ef2:SetOrigin(pos + Vector(0,0,25))
            ef2:SetScale(3 + t * 5); ef2:SetMagnitude(240)
            util.Effect("HelicopterMegaBomb", ef2)

            for j = 1, count do
                timer.Simple(j * Lerp(t, 0.038, 0.018), function()
                    local angle  = j * 2.39996  -- золотое сечение → плотный равномерный веер
                    local dist2  = math.Rand(45, sRange)
                    local rise2  = math.Rand(0, dist2 * 0.38)
                    local sPos   = pos
                        + cr2 * math.cos(angle) * dist2
                        + cu2 * math.sin(angle) * dist2
                        + Vector(0, 0, rise2)
                    util.BlastDamage(atk, atk, sPos, sRad, sDmg)
                    local sfe = EffectData(); sfe:SetOrigin(sPos)
                    sfe:SetScale(1.1 + t * 0.9)
                    util.Effect("Explosion", sfe)
                    local sf2 = EffectData(); sf2:SetOrigin(sPos); sf2:SetScale(0.7 + t)
                    util.Effect("WheelDust", sf2)
                    sound.Play("ambient/explosions/explode_" .. math.random(1, 4) .. ".wav",
                        sPos, 68, math.random(128, 165))
                end)
            end
        end

        -- Ударная волна на клиентах: расширяющееся кольцо + вспышка
        net.Start("D6_RailHyperImpact")
            net.WriteVector(pos)
            net.WriteVector(nrm)
            net.WriteFloat(t)
            net.WriteBool(isBox)
        net.Broadcast()
    end

    ent:AddCallback("PhysicsCollide", function(e, data)
        if done then return end
        local hitEnt  = data.HitEntity
        local hitPos  = data.HitPos
        local hitNorm = data.HitNormal
        local oldVel  = data.OurOldVelocity or vector_origin

        -- Игнорируем владельца и другие снаряды D6
        if IsValid(hitEnt) then
            if IsValid(owner) and hitEnt == owner then return end
            if hitEnt.D6_IsProjectile then return end
        end

        -- Живая цель → урон (+детонация, если снаряд взрывной)
        if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
            done = true
            ent.D6_RailHit = true
            timer.Simple(0, function()
                if IsValid(hitEnt) then
                    local di = DamageInfo()
                    di:SetAttacker(IsValid(owner) and owner or game.GetWorld())
                    di:SetInflictor(IsValid(e) and e or game.GetWorld())
                    di:SetDamage(ent.D6_RailDmg or 50)
                    di:SetDamageType(DMG_CRUSH)
                    di:SetDamageForce(oldVel:GetNormalized() * 10000)
                    hitEnt:TakeDamageInfo(di)
                end
                local ef = EffectData(); ef:SetOrigin(hitPos); ef:SetNormal(hitNorm); ef:SetMagnitude(3)
                util.Effect("Sparks", ef)
                if isExpl then
                    DoExplode(IsValid(e) and e:GetPos() or hitPos)
                    if IsValid(e) then e:Remove() end
                elseif oldVel:Length() >= KIN_IMPACT_MIN then
                    -- прямое попадание разогнанного пропа → импакт по площади
                    DoKineticImpact(IsValid(e) and e:GetPos() or hitPos, hitNorm, oldVel:Length())
                end
            end)
            return
        end

        -- Взрывной проп → детонация при любом касании мира/пропа
        if isExpl then
            done = true
            ent.D6_RailHit = true
            timer.Simple(0, function()
                DoExplode(IsValid(e) and e:GetPos() or hitPos)
                if IsValid(e) then e:Remove() end
            end)
            return
        end

        -- Кинетический импакт: на высокой скорости (вплоть до гиперзвука)
        -- удар невзрывного пропа о мир/проп = взрыв + дым + урон по площади.
        -- Ниже порога — обычный рикошет (дефолтные скорости не меняются).
        local impactSpeed = oldVel:Length()
        if impactSpeed >= KIN_IMPACT_MIN then
            done = true
            ent.D6_RailHit = true
            local pos = (IsValid(e) and e:GetPos()) or hitPos
            timer.Simple(0, function() DoKineticImpact(pos, hitNorm, impactSpeed) end)
            return
        end

        -- Невзрывной проп → рикошет (до MAX_RICOCHETS раз)
        bounces = bounces + 1
        if bounces > MAX_RICOCHETS then return end   -- импульс исчерпан, летит по физике
        if hitNorm and hitNorm:LengthSqr() > 0.0001 then
            local reflected = oldVel - 2 * oldVel:Dot(hitNorm) * hitNorm
            timer.Simple(0, function()
                if not IsValid(e) then return end
                local ph = e:GetPhysicsObject()
                if IsValid(ph) then ph:SetVelocity(reflected * 0.8) end
                local ef = EffectData(); ef:SetOrigin(hitPos); ef:SetNormal(hitNorm); ef:SetMagnitude(2)
                util.Effect("ManhackSparks", ef)
            end)
        end
    end)

    -- По истечении жизни прекращаем обработку (проп остаётся как мусор)
    timer.Simple(6, function()
        done = true
        if IsValid(ent) then ent.D6_IsProjectile = nil end
    end)
end

-- =========================================================
-- ПКМ — ДРОБОВИК (пылесос ↔ веер)
-- =========================================================
function SWEP:SecondaryAttack()
    RefreshGravCfg()
    if CurTime() < (self.NextFire or 0) then return end
    self.NextFire = CurTime() + FIRE_COOLDOWN

    if not SERVER then return end

    local ply = self:GetOwner()
    if not IsValid(ply) then return end

    if self.FanProps and #self.FanProps > 0 then
        self:FireFan(); return
    end

    if not self:HasEnergy(ENERGY_FAN) then
        self:DenyFire(); return
    end

    local ang      = ShootAng(ply)
    local fwd      = ang:Forward()
    local muz      = ply:GetShootPos()
    local cosLimit = math.cos(math.rad(FAN_CONE))
    local collected = {}

    for _, e in ipairs(ents.FindInSphere(muz, FAN_RANGE)) do
        if #collected >= FAN_MAX_COUNT then break end
        if e:GetClass() ~= "prop_physics" then continue end
        local ph = e:GetPhysicsObject()
        if not IsValid(ph) then continue end
        if ph:GetMass() > FAN_MAX_MASS then continue end
        if e.D6_RailFanLocked then continue end

        local toEnt = (e:GetPos() - muz):GetNormalized()
        if toEnt:Dot(fwd) < cosLimit then continue end

        collected[#collected+1] = e
        e.D6_RailFanLocked = true
        LockCollision(e)           -- не бьют игрока при подтягивании
        ph:EnableGravity(false)
        -- Стабильное смещение от точки удержания в системе координат прицела
        local h0  = muz + fwd * 70
        local raw = e:GetPos() - h0
        e.D6_FanHoldDX  = math.Clamp(raw:Dot(ang:Right()), -30, 30)
        e.D6_FanHoldDY  = math.Clamp(raw:Dot(ang:Up()),    -25, 25)
        e.D6_FanLongAxis = PropLongAxis(e)   -- длинная ось → вдоль прицела
    end

    if #collected == 0 then
        self:DenyFire(); return
    end

    self.FanProps = collected
    self:SetNWInt("D6_RailFanCount", #collected)
    ply:EmitSound("weapons/physcannon/physcannon_pickup.wav", 60, 100)
    self:SendWeaponAnim(ACT_VM_PRIMARYATTACK)

    local fanTimerID = "D6_RailFan_" .. self:EntIndex()
    timer.Create(fanTimerID, 0.05, 0, function()
        if not IsValid(self) or not IsValid(ply)
           or not self.FanProps or #self.FanProps == 0 then
            timer.Remove(fanTimerID); return
        end
        local a    = ShootAng(ply)
        local hold = ply:GetShootPos() + a:Forward() * 70

        for i = #self.FanProps, 1, -1 do
            local e = self.FanProps[i]
            if not IsValid(e) then
                table.remove(self.FanProps, i)
            else
                local ph = e:GetPhysicsObject()
                if IsValid(ph) then
                    -- Стабилизация: позиция в aim-пространстве + длинная ось вдоль прицела
                    local holdPos = hold
                        + a:Right() * (e.D6_FanHoldDX or 0)
                        + a:Up()    * (e.D6_FanHoldDY or 0)
                    ph:ComputeShadowControl({
                        secondstoarrive  = 0.05,
                        pos              = holdPos,
                        angle            = AimLongAngle(e.D6_FanLongAxis or "x", a:Forward()),
                        maxangular       = 5000,
                        maxangulardamp   = 20000,
                        maxspeed         = 100000,
                        maxspeeddamp     = 200000,
                        dampfactor       = 1.0,
                        teleportdistance = 0,
                    })
                end
            end
        end
        self:SetNWInt("D6_RailFanCount", #self.FanProps)
    end)
end

-- ── Выстрел дробовиком ───────────────────────────────────
function SWEP:FireFan()
    local ply = self:GetOwner()
    if not IsValid(ply) then return end
    if not self:HasEnergy(ENERGY_FAN) then
        self:DenyFire(); self:ReleaseFan(); return
    end
    self:SetEnergy(self:GetEnergy() - ENERGY_FAN)

    local ang = ShootAng(ply)
    local fwd = ang:Forward()
    local rgt = ang:Right()
    local up  = ang:Up()

    timer.Remove("D6_RailFan_" .. self:EntIndex())

    for _, e in ipairs(self.FanProps) do
        if not IsValid(e) then continue end
        local ph = e:GetPhysicsObject()
        if not IsValid(ph) then continue end

        local sx  = math.Rand(-FAN_SPREAD, FAN_SPREAD)
        local sy  = math.Rand(-FAN_SPREAD, FAN_SPREAD)
        local dir = (fwd
                   + rgt * math.tan(math.rad(sx))
                   + up  * math.tan(math.rad(sy))):GetNormalized()

        UnlockCollision(e)         -- после выстрела коллизии возвращаем
        e.D6_RailFanLocked = false
        e.D6_FanHoldDX     = nil
        e.D6_FanHoldDY     = nil
        e.D6_FanLongAxis   = nil
        ph:EnableDrag(false)       -- гасим аэродинамику чтобы скорость не упала
        if PROP_MODE == 1 then
            -- Унгравити: пропы летят идеально прямо, без гравитации и затухания
            ph:EnableGravity(false)
            ph:SetDamping(0, 0)
            local pelletVel = dir * FAN_FIRE_SPEED
            ph:Wake()
            ph:SetVelocity(pelletVel)
            local eRef = e
            timer.Simple(0, function()
                if IsValid(eRef) then
                    local pp = eRef:GetPhysicsObject()
                    if IsValid(pp) then pp:SetVelocity(pelletVel) end
                end
            end)
        else
            ph:EnableGravity(true)
            ph:Wake()
            ph:SetVelocity(dir * FAN_FIRE_SPEED)
        end

        -- Снаряды дробовика могут наносить урон врагам
        e.D6_RailOwner = ply
        e.D6_RailDmg   = math.Rand(5, 15)
        e.D6_RailHit   = false
        e.D6_RailUntil = CurTime() + 2.5
        self:HookRailProjectile(e)
        -- Разогнанная до гиперзвука дробь тоже получает рельсовый шлейф
        if FAN_FIRE_SPEED >= KIN_IMPACT_MIN then
            self:AttachRailVisual(e, FAN_FIRE_SPEED)
        end
        -- Дробь тоже рвётся в полёте (дым/осколки по массе пеллета),
        -- только когда дробь разогнана до гиперзвука.
        if FAN_FIRE_SPEED >= HYPER_FLIGHT_MIN then
            self:AttachHyperFlight(e, ph:GetMass())
        end
    end

    ply:EmitSound("weapons/physcannon/physcannon_shoot.wav", 80, 105)
    self:SendWeaponAnim(ACT_VM_SECONDARYATTACK)
    if D6_Wep then D6_Wep.ApplyRecoil(ply, fwd, 55) end

    net.Start("D6_RailFire")
        net.WriteEntity(ply)
        net.WriteVector(ply:GetShootPos() + fwd * 30)
        net.WriteVector(ply:GetShootPos() + fwd * 600)
        net.WriteUInt(1, 2)
    net.Broadcast()

    self.FanProps = {}
    self:SetNWInt("D6_RailFanCount", 0)
end

-- ── Полный сброс пылесоса ─────────────────────────────────
function SWEP:ReleaseFan()
    timer.Remove("D6_RailFan_" .. self:EntIndex())
    for _, e in ipairs(self.FanProps or {}) do
        if IsValid(e) then
            e.D6_RailFanLocked = false
            e.D6_FanHoldDX     = nil
            e.D6_FanHoldDY     = nil
            e.D6_FanLongAxis   = nil
            UnlockCollision(e)
            local ph = e:GetPhysicsObject()
            if IsValid(ph) then ph:EnableGravity(true) end
        end
    end
    self.FanProps = {}
    self:SetNWInt("D6_RailFanCount", 0)
end

function SWEP:Reload()
    self:ReleaseHeld()
    self:ReleaseFan()
end

-- =========================================================
-- КЛИЕНТСКАЯ ЧАСТЬ
-- =========================================================
if CLIENT then
    local BEAM = Material("cable/redlaser")
    local GLOW_MAT = Material("sprites/light_glow02_add")
    local _flashIdx = 0

    -- Разогнанные рельсой пропы: ореол + динамический свет.
    -- [ent] = { ed = время_снятия, frac = интенсивность 0..1 }
    local D6_RailGlowProps = {}

    net.Receive("D6_RailGlow", function()
        local ent  = net.ReadEntity()
        local frac = net.ReadFloat()
        if IsValid(ent) then
            D6_RailGlowProps[ent] = { ed = CurTime() + 4.5, frac = frac }
        end
    end)

    -- Ореол + конус Маха летящего пропа
    hook.Add("PostDrawTranslucentRenderables", "D6_RailPropGlow", function(depth, sky)
        if depth or sky then return end
        local now = CurTime()
        render.SetMaterial(GLOW_MAT)
        for ent, d in pairs(D6_RailGlowProps) do
            if not IsValid(ent) or now >= d.ed then continue end

            local pos   = ent:GetPos() + ent:OBBCenter()
            local pulse = 1 + math.sin(now * 30) * 0.12
            local sz    = (60 + d.frac * 150) * pulse
            render.DrawSprite(pos, sz, sz, Color(255, 236, 140, 235))
            render.DrawSprite(pos, sz * 0.5, sz * 0.5, Color(255, 255, 255, 255))

            -- Конус Маха: серо-белый конус ударной волны позади пропа
            if d.frac > 0.04 then
                local vel = ent:GetVelocity()
                local spd = vel:Length()
                if spd > 5000 then
                    local vdir = vel:GetNormalized()
                    local spdF = math.min(spd / 14000, 1)
                    local cLen = (45 + d.frac * 270) * spdF
                    local cRad = math.tan(math.rad(Lerp(d.frac, 52, 13))) * cLen
                    local cBase = pos - vdir * cLen
                    local tmp  = math.abs(vdir.z) < 0.9 and Vector(0,0,1) or Vector(1,0,0)
                    local cr   = vdir:Cross(tmp):GetNormalized()
                    local cu   = vdir:Cross(cr):GetNormalized()
                    local ca   = math.Clamp(d.frac * 155 * spdF, 18, 135)
                    render.SetMaterial(BEAM)
                    local prev = nil
                    for j = 0, 18 do
                        local th = (j / 18) * math.pi * 2
                        local pt = cBase + (cr * math.cos(th) + cu * math.sin(th)) * cRad
                        if prev then
                            render.DrawBeam(prev, pt, 1.4 + d.frac * 3.2, 0, 1,
                                Color(210, 215, 225, ca))
                        end
                        if j % 6 == 0 then
                            render.DrawBeam(pos, pt, 0.6, 0, 1,
                                Color(245, 248, 255, ca * 0.38))
                        end
                        prev = pt
                    end
                    render.SetMaterial(GLOW_MAT)
                end
            end
        end
    end)

    -- Контурный ореол (halo) — обводит модель пропа сиянием
    hook.Add("PreDrawHalos", "D6_RailPropHalo", function()
        local now  = CurTime()
        local list = {}
        for ent, d in pairs(D6_RailGlowProps) do
            if IsValid(ent) and now < d.ed then
                list[#list + 1] = ent
            end
        end
        if #list > 0 then
            halo.Add(list, Color(255, 240, 160), 6, 6, 2, true, true)
        end
    end)

    -- Динамический жёлто-белый свет от снаряда + очистка таблицы
    hook.Add("Think", "D6_RailPropLight", function()
        local now = CurTime()
        for ent, d in pairs(D6_RailGlowProps) do
            if not IsValid(ent) or now >= d.ed then
                D6_RailGlowProps[ent] = nil
            else
                local dl = DynamicLight(ent:EntIndex())
                if dl then
                    dl.pos        = ent:GetPos()
                    dl.r          = 255
                    dl.g          = 236
                    dl.b          = 150
                    dl.brightness = 2 + d.frac * 4
                    dl.size       = 160 + d.frac * 320
                    dl.decay      = 1000
                    dl.dietime    = now + 0.06
                end
            end
        end
    end)

    -- ── Ударные волны + вспышки + кассетные засветки ────────
    -- Клиент получает позицию, нормаль, интенсивность и флаг кассеты.
    -- Рисуем: яркая вспышка → расширяющееся кольцо ударной волны →
    -- вторичное серое кольцо дыма → (кассета) веер засвечивающих точек.
    local D6_HyperImpacts = {}
    local RING_MAT = Material("cable/redlaser")

    net.Receive("D6_RailHyperImpact", function()
        local pos     = net.ReadVector()
        local nrm     = net.ReadVector()
        local frac    = net.ReadFloat()
        local cluster = net.ReadBool()
        table.insert(D6_HyperImpacts, {
            pos = pos, nrm = nrm, frac = frac, cluster = cluster,
            t0  = CurTime(),
            dur = 0.55 + frac * 0.65,
        })
    end)

    hook.Add("PostDrawTranslucentRenderables", "D6_RailHyperImpactFX", function(depth, sky)
        if depth or sky then return end
        local now = CurTime()
        render.SetMaterial(RING_MAT)
        for i = #D6_HyperImpacts, 1, -1 do
            local h = D6_HyperImpacts[i]
            local p = (now - h.t0) / h.dur
            if p >= 1 then table.remove(D6_HyperImpacts, i); continue end

            local pos  = h.pos
            local nrm  = h.nrm
            local frac = h.frac

            -- Перпендикулярный базис кольца в плоскости нормали удара
            local tmp = math.abs(nrm.z) < 0.9 and Vector(0,0,1) or Vector(1,0,0)
            local cr  = nrm:Cross(tmp):GetNormalized()
            local cu  = nrm:Cross(cr):GetNormalized()

            -- 1. Яркая белая вспышка в эпицентре (0.0..0.25)
            if p < 0.25 then
                local fp  = 1 - p / 0.25
                local fsz = (120 + frac * 480) * fp
                render.SetMaterial(GLOW_MAT)
                render.DrawSprite(pos, fsz,       fsz,       Color(255, 248, 230, fp * 240))
                render.DrawSprite(pos, fsz * 0.4, fsz * 0.4, Color(255, 255, 255, fp * 255))
                render.SetMaterial(RING_MAT)
            end

            -- 2. Расширяющееся кольцо ударной волны (серо-белое)
            local rRad = (80 + frac * 480) * math.sqrt(p)
            local rAlp = (1 - p) * (75 + frac * 130)
            local rBw  = 2.5 + frac * 7
            local rCol = Color(210, 215, 225, rAlp)
            local prev = nil
            for j = 0, 24 do
                local th = (j / 24) * math.pi * 2
                local pt = pos + (cr * math.cos(th) + cu * math.sin(th)) * rRad
                if prev then render.DrawBeam(prev, pt, rBw, 0, 1, rCol) end
                prev = pt
            end

            -- 3. Второе (медленное) серое кольцо дыма
            if frac > 0.15 then
                local r2   = (40 + frac * 190) * p
                local a2   = (1 - p) * 85
                local col2 = Color(145, 145, 155, a2)
                local prev2 = nil
                for j = 0, 20 do
                    local th = (j / 20) * math.pi * 2
                    local pt = pos
                        + (cr * math.cos(th) + cu * math.sin(th)) * r2
                        + nrm * r2 * 0.22
                    if prev2 then render.DrawBeam(prev2, pt, 1.8, 0, 1, col2) end
                    prev2 = pt
                end
            end

            -- 4. Кассетные засветки: мерцающие вспышки в ореоле рассева
            if h.cluster and p < 0.65 then
                render.SetMaterial(GLOW_MAT)
                local count = math.floor(3 + frac * 9)
                for k = 1, count do
                    local fp2 = k / count  -- равномерное распределение во времени
                    local lo  = fp2 * 0.5
                    local hi  = lo + 0.18
                    if p >= lo and p < hi then
                        local angle = k * 2.39996
                        local dist2 = (55 + frac * 210) * (k / count)
                        local cPos  = pos
                            + cr * math.cos(angle) * dist2
                            + cu * math.sin(angle) * dist2
                            + nrm * dist2 * 0.18
                        local bf = 1 - (p - lo) / 0.18
                        local bsz = (35 + frac * 90) * bf
                        render.DrawSprite(cPos, bsz, bsz,
                            Color(255, 230, 170, bf * 210))
                    end
                end
                render.SetMaterial(RING_MAT)
            end
        end
    end)

    -- DynamicLight в эпицентре удара (вспышка освещает окружение)
    hook.Add("Think", "D6_RailImpactLight", function()
        for _, h in ipairs(D6_HyperImpacts) do
            local p = (CurTime() - h.t0) / h.dur
            if p >= 0.3 then continue end  -- только в начале
            local bright = (1 - p / 0.3) * (1 + h.frac * 4)
            local dl = DynamicLight(1234567 + math.floor(h.t0 * 100) % 1000)
            if dl then
                dl.pos        = h.pos
                dl.r          = 255; dl.g = 245; dl.b = 200
                dl.brightness = bright
                dl.size       = 200 + h.frac * 600
                dl.decay      = 800
                dl.dietime    = CurTime() + 0.12
            end
        end
    end)

    -- Зелёная перекраска модели мира
    function SWEP:DrawWorldModel()
        render.SetColorModulation(0, 1, 0)
        self:DrawModel()
        render.SetColorModulation(1, 1, 1)
    end

    -- Зелёный луч к удерживаемому пропу
    hook.Add("PostDrawTranslucentRenderables", "D6_RailBeam", function(depth, sky)
        if depth or sky then return end
        for _, ply in ipairs(player.GetAll()) do
            local wep = ply:GetActiveWeapon()
            if not IsValid(wep) or wep:GetClass() ~= "weapon_d6_gravy_railgun" then continue end
            local held = wep:GetNWEntity("D6_RailHeld", NULL)
            if not IsValid(held) then continue end

            local a
            if ply == LocalPlayer() then
                a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
            else
                a = ply:EyeAngles()
            end
            local muz = ply:GetShootPos() + Angle(a.p, a.y, 0):Forward() * 13

            render.SetMaterial(BEAM)
            render.DrawBeam(muz, held:GetPos() + held:OBBCenter(), 4,   0, 1, TINT)
            render.DrawBeam(muz, held:GetPos() + held:OBBCenter(), 1.5, 0, 1, TINT_INNER)
        end
    end)

    -- Конус кучности дроби — визуализация разброса (FAN_SPREAD) от
    -- ствола для LocalPlayer. Полуугол = текущий разброс из D6_GravCfg
    -- (синхронизирован), длина — превью. Помогает целиться дробовиком.
    hook.Add("PostDrawTranslucentRenderables", "D6_RailSpreadCone", function(depth, sky)
        if depth or sky then return end
        local ply = LocalPlayer()
        if not IsValid(ply) or ply:ShouldDrawLocalPlayer() then return end
        local wep = ply:GetActiveWeapon()
        if not IsValid(wep) or wep:GetClass() ~= "weapon_d6_gravy_railgun" then return end

        local spread = (D6_GravCfg and D6_GravCfg.Get("weapon_d6_gravy_railgun", "FAN_SPREAD", 20)) or 20
        if spread <= 0 then return end

        local a    = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
        local ang  = Angle(a.p, a.y, 0)
        local fwd  = ang:Forward()
        local rgt  = ang:Right()
        local up   = ang:Up()
        local apex = ply:GetShootPos() + fwd * 13

        local len    = 380
        local rad    = math.tan(math.rad(spread)) * len
        local center = apex + fwd * len

        -- ярче, когда дробовик заряжен пропами (готов к выстрелу)
        local loaded = wep:GetNWInt("D6_RailFanCount", 0) > 0
        local col    = loaded and Color(120, 255, 150, 200) or Color(0, 255, 0, 70)
        local bw     = loaded and 2 or 1

        render.SetMaterial(BEAM)
        local seg, prev = 28, nil
        for i = 0, seg do
            local th = (i / seg) * math.pi * 2
            local pt = center + (rgt * math.cos(th) + up * math.sin(th)) * rad
            if prev then render.DrawBeam(prev, pt, bw, 0, 1, col) end   -- кольцо
            if i % 7 == 0 then render.DrawBeam(apex, pt, bw, 0, 1, col) end  -- спицы
            prev = pt
        end
    end)

    -- Трассер выстрела (зелёная полоса, исчезает за 0.15 с)
    -- Каждый выстрел = уникальный числовой ключ, без CurTime() в имени.
    net.Receive("D6_RailFire", function()
        local ply  = net.ReadEntity()
        local src  = net.ReadVector()
        local dst  = net.ReadVector()
        local mode = net.ReadUInt(2)

        -- Эффект в месте выстрела
        local ef = EffectData()
        ef:SetOrigin(src); ef:SetNormal((dst-src):GetNormalized()); ef:SetMagnitude(3)
        util.Effect("ManhackSparks", ef)
        local sm = EffectData(); sm:SetOrigin(src); sm:SetScale(0.3)
        util.Effect("MuzzleEffect", sm)

        -- Трассер: используем уникальный числовой ключ
        _flashIdx = _flashIdx + 1
        local key  = "D6_RailFlash_" .. _flashIdx
        local life = CurTime() + 0.15
        local bw   = mode == 0 and 6 or 3
        local fSrc, fDst = Vector(src.x,src.y,src.z), Vector(dst.x,dst.y,dst.z)
        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            if CurTime() >= life then
                hook.Remove("PostDrawTranslucentRenderables", key)
                return
            end
            render.SetMaterial(BEAM)
            local a = math.Clamp((life - CurTime()) / 0.15, 0, 1) * 255
            render.DrawBeam(fSrc, fDst, bw, 0, 1, Color(TINT.r, TINT.g, TINT.b, a))
        end)
    end)

    -- HUD энергии — теперь дублируется в d6_cockpit.lua,
    -- оставляем мини-бар над прицелом только если кокпит недоступен
    hook.Add("HUDPaint", "D6_RailHUD_Minimal", function()
        local ply = LocalPlayer()
        if not IsValid(ply) then return end
        local wep = ply:GetActiveWeapon()
        if not IsValid(wep) or wep:GetClass() ~= "weapon_d6_gravy_railgun" then return end
        -- Если кокпит включён — не рисуем дублирующую полосу
        if ply:GetNWBool("D6On", false) then return end

        local e    = wep:GetNWInt("D6_RailEnergy", ENERGY_MAX)
        local frac = e / ENERGY_MAX
        local sw, sh = ScrW(), ScrH()
        local w, h   = 220, 12
        local x = sw / 2 - w / 2
        local y = sh - 80

        surface.SetDrawColor(0, 0, 0, 180)
        surface.DrawRect(x-2, y-2, w+4, h+4)
        surface.SetDrawColor(0, 60, 0, 200)
        surface.DrawRect(x, y, w, h)
        surface.SetDrawColor(0, 255, 0, 240)
        surface.DrawRect(x, y, w * frac, h)
        draw.SimpleText("ЭНЕРГИЯ " .. math.floor(e) .. "/" .. ENERGY_MAX,
            "DermaDefault", sw/2, y+h/2,
            Color(220,255,220), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end)
end

-- ── Регистрация в реестре D6 ─────────────────────────────
if D6_RegisterWeapon then
    D6_RegisterWeapon("weapon_d6_gravy_railgun", {
        category = "Descent",
        autoGive = false,
        patchAim = true,
    })
end
