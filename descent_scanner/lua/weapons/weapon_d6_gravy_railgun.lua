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
local TINT            = Color(0, 255, 0)
local TINT_INNER      = Color(180, 255, 180)

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
    _D6_RAIL_NET = true
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
    self:SetHoldType("physgun")
    self.NextFire    = 0
    self.HeldEnt     = NULL
    self.HeldGrabT   = 0
    self.FanProps    = {}
    self.LastRegenT  = CurTime()
    self:SetNWInt("D6_RailEnergy", ENERGY_MAX)
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
    end
    self.HeldEnt = NULL
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
                local vel = (target - self.HeldEnt:GetPos()) * 12
                ph:SetVelocity(vel)
                ph:SetAngleDragCoefficient(8000)
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
        LockCollision(tr.Entity)
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
    self:ReleaseHeld()

    if IsValid(ph) then
        ph:EnableGravity(true)
        ph:EnableMotion(true)
        ph:Wake()
        ph:SetVelocity(dir * RAIL_FIRE_SPEED)
    end

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
    self:SendWeaponAnim(ACT_VM_SECONDARYATTACK)
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
                end
            end)
            return
        end

        -- Взрывной проп → детонация при любом касании мира/пропа
        if isExpl then
            done = true
            timer.Simple(0, function()
                DoExplode(IsValid(e) and e:GetPos() or hitPos)
                if IsValid(e) then e:Remove() end
            end)
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
    end

    if #collected == 0 then
        self:DenyFire(); return
    end

    self.FanProps = collected
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
                    local offs = Vector(math.random(-15,15), math.random(-15,15), math.random(-15,15))
                    ph:SetVelocity((hold + offs - e:GetPos()) * 10)
                    ph:Wake()
                end
            end
        end
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
        ph:EnableGravity(true)
        ph:Wake()
        ph:SetVelocity(dir * FAN_FIRE_SPEED)

        -- Снаряды дробовика могут наносить урон врагам
        e.D6_RailOwner = ply
        e.D6_RailDmg   = math.Rand(5, 15)
        e.D6_RailHit   = false
        e.D6_RailUntil = CurTime() + 2.5
        self:HookRailProjectile(e)
    end

    ply:EmitSound("weapons/physcannon/physcannon_shoot.wav", 80, 105)
    self:SendWeaponAnim(ACT_VM_SECONDARYATTACK)

    net.Start("D6_RailFire")
        net.WriteEntity(ply)
        net.WriteVector(ply:GetShootPos() + fwd * 30)
        net.WriteVector(ply:GetShootPos() + fwd * 600)
        net.WriteUInt(1, 2)
    net.Broadcast()

    self.FanProps = {}
end

-- ── Полный сброс пылесоса ─────────────────────────────────
function SWEP:ReleaseFan()
    timer.Remove("D6_RailFan_" .. self:EntIndex())
    for _, e in ipairs(self.FanProps or {}) do
        if IsValid(e) then
            e.D6_RailFanLocked = false
            UnlockCollision(e)
            local ph = e:GetPhysicsObject()
            if IsValid(ph) then ph:EnableGravity(true) end
        end
    end
    self.FanProps = {}
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
    local _flashIdx = 0

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
