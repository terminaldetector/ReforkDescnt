-- ═══════════════════════════════════════════════════════════
-- weapons/d6_gravy_railgun.lua
-- "Gravy Rail Gun" — зелёная гравипушка для Descent 6DOF
--
-- ЛКМ: захват объекта, повторный ЛКМ — выстрел рельсой.
-- ПКМ: пылесос мелких пропов в конусе, повторный ПКМ — веер.
--
-- Энергия 100 макс, регенерация 10/сек.
-- ═══════════════════════════════════════════════════════════

if SERVER then AddCSLuaFile() end

SWEP.PrintName       = "Gravy Rail Gun"
SWEP.Author          = "Descent 6DOF"
SWEP.Instructions    = "ЛКМ — захват/рельса   ПКМ — пылесос/веер"
SWEP.Category        = "Descent"
SWEP.Slot            = 4
SWEP.SlotPos         = 1
SWEP.Spawnable       = true
SWEP.AdminSpawnable  = true

SWEP.Base            = "weapon_base"
SWEP.HoldType        = "physgun"
SWEP.ViewModel       = "models/weapons/v_physcannon.mdl"  -- v_, не c_, чтобы не требовать c_arms
SWEP.WorldModel      = "models/weapons/w_physcannon.mdl"
SWEP.UseHands        = false  -- c_arms hands не используем
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
local ENERGY_MAX        = 100
local ENERGY_REGEN      = 10           -- в секунду
local ENERGY_RAIL       = 20
local ENERGY_FAN        = 30
local RAIL_RANGE        = 1500
local RAIL_FIRE_SPEED   = 4000
local RAIL_MAX_MASS     = 300
local FAN_CONE          = 15           -- градусов
local FAN_RANGE         = 500
local FAN_MAX_MASS      = 50
local FAN_MAX_COUNT     = 8
local FAN_FIRE_SPEED    = 3000
local FAN_SPREAD        = 20           -- градусов
local FIRE_COOLDOWN     = 0.3
local TINT              = Color(0, 255, 0)

-- ─── Сетевая строка (регистрируется один раз) ───────────
if SERVER and not _D6_RAIL_NET then
    util.AddNetworkString("D6_RailFire")
    _D6_RAIL_NET = true
end

-- =========================================================
-- ХЕЛПЕРЫ
-- =========================================================
local function ShootAng(ply)
    -- Используем угол с креном (если 6DOF активен)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

local function MuzzlePos(ply)
    local ang = ShootAng(ply)
    return ply:GetShootPos() + ang:Forward() * 20, ang
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

-- =========================================================
-- ИНИЦИАЛИЗАЦИЯ
-- =========================================================
function SWEP:Initialize()
    self:SetHoldType("physgun")
    self.NextFire   = 0
    self.HeldEnt    = NULL
    self.HeldGrabT  = 0
    self.FanProps   = {}
    self.LastRegenT = CurTime()
    self:SetNWInt("D6_RailEnergy", ENERGY_MAX)
    self:SetNWEntity("D6_RailHeld", NULL)
end

function SWEP:OnRemove()      self:ReleaseHeld() end
function SWEP:OnDrop()         self:ReleaseHeld() end
function SWEP:Holster()        self:ReleaseHeld(); return true end

function SWEP:ReleaseHeld()
    if SERVER and IsValid(self.HeldEnt) then
        local ph = self.HeldEnt:GetPhysicsObject()
        if IsValid(ph) then
            ph:EnableGravity(true)
            ph:EnableMotion(true)
            ph:Wake()
        end
    end
    self.HeldEnt = NULL
    if SERVER then self:SetNWEntity("D6_RailHeld", NULL) end
end

-- =========================================================
-- ЭНЕРГИЯ
-- =========================================================
function SWEP:GetEnergy() return self:GetNWInt("D6_RailEnergy", ENERGY_MAX) end

function SWEP:SetEnergy(v)
    if not SERVER then return end
    self:SetNWInt("D6_RailEnergy", math.Clamp(math.floor(v), 0, ENERGY_MAX))
end

function SWEP:HasEnergy(cost)
    return self:GetEnergy() >= cost
end

function SWEP:DenyFire()
    local ply = self:GetOwner()
    if IsValid(ply) then
        ply:EmitSound("common/wpn_denyselect.wav", 60, 110)
    end
    self.NextFire = CurTime() + 0.2
end

-- =========================================================
-- THINK — регенерация и удержание объекта
-- =========================================================
function SWEP:Think()
    if SERVER then
        -- Регенерация
        local now = CurTime()
        local dt  = now - (self.LastRegenT or now)
        if dt >= 0.1 then
            self.LastRegenT = now
            local cur = self:GetEnergy()
            if cur < ENERGY_MAX then
                self:SetEnergy(cur + ENERGY_REGEN * dt)
            end
        end

        -- Удержание объекта перед стволом
        if IsValid(self.HeldEnt) then
            local ply = self:GetOwner()
            if not IsValid(ply) or ply:GetActiveWeapon() ~= self then
                self:ReleaseHeld()
                return
            end
            local ang = ShootAng(ply)
            local target = ply:GetShootPos() + ang:Forward() * 90
            local ph = self.HeldEnt:GetPhysicsObject()
            if IsValid(ph) then
                ph:Wake()
                ph:EnableGravity(false)
                local cur = self.HeldEnt:GetPos()
                local vel = (target - cur) * 12
                ph:SetVelocity(vel)
                ph:SetAngleDragCoefficient(8000)
            else
                self:ReleaseHeld()
            end
        end
    end
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

    -- Уже что-то держим? → выстрел
    if IsValid(self.HeldEnt) then
        self:FireRail()
        return
    end

    -- Нет энергии — отказ
    if not self:HasEnergy(ENERGY_RAIL) then
        self:DenyFire()
        return
    end

    -- Трассировка для захвата
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
        self:SetNWEntity("D6_RailHeld", tr.Entity)
        ply:EmitSound("weapons/physcannon/physcannon_pickup.wav", 75, 130)
    else
        self:DenyFire()
    end
end

function SWEP:FireRail()
    local ply = self:GetOwner()
    if not IsValid(ply) or not IsValid(self.HeldEnt) then
        self:ReleaseHeld()
        return
    end
    if not self:HasEnergy(ENERGY_RAIL) then
        self:DenyFire()
        return
    end
    self:SetEnergy(self:GetEnergy() - ENERGY_RAIL)

    local ent  = self.HeldEnt
    local ph   = ent:GetPhysicsObject()
    local ang  = ShootAng(ply)
    local dir  = ang:Forward()

    -- Урон зависит от массы
    local mass = IsValid(ph) and ph:GetMass() or 50
    local dmg  = math.Clamp(mass * 0.5, 20, 120)

    self:ReleaseHeld()

    if IsValid(ph) then
        ph:EnableGravity(true)
        ph:EnableMotion(true)
        ph:Wake()
        ph:SetVelocity(dir * RAIL_FIRE_SPEED)
    end

    -- Пометка снаряда: при касании врага нанести урон
    ent.D6_RailOwner = ply
    ent.D6_RailDmg   = dmg
    ent.D6_RailHit   = false
    ent.D6_RailUntil = CurTime() + 4
    ent:CollisionRulesChanged()

    -- Эффект выстрела
    ply:EmitSound("weapons/physcannon/superphys_launch1.wav", 80, 90)
    local muz = ply:GetShootPos() + dir * 30
    local ef  = EffectData()
    ef:SetOrigin(muz); ef:SetNormal(dir); ef:SetMagnitude(2); ef:SetScale(1)
    util.Effect("ManhackSparks", ef)

    -- Сетевой пакет (визуал луча у клиентов)
    net.Start("D6_RailFire")
        net.WriteEntity(ply)
        net.WriteVector(muz)
        net.WriteVector(ent:GetPos())
        net.WriteUInt(0, 2)  -- 0 = рельса
    net.Broadcast()

    -- Регистрируем обработчик столкновений
    self:HookRailProjectile(ent)
end

function SWEP:HookRailProjectile(ent)
    if not IsValid(ent) then return end
    local idx = ent:EntIndex()
    local hookId = "D6_Rail_Proj_" .. idx

    hook.Add("EntityTakeDamage", hookId, function(target, dmginfo)
        -- Когда снаряд бьёт что-то — он сам получит damage event
        -- Но нам нужно отследить ЕГО касание. Используем Think.
    end)

    -- Простая трассировка движения для детекта попадания
    local started = CurTime()
    timer.Create(hookId, 0.05, 0, function()
        if not IsValid(ent) or CurTime() > (ent.D6_RailUntil or 0) then
            timer.Remove(hookId)
            hook.Remove("EntityTakeDamage", hookId)
            return
        end
        if ent.D6_RailHit then
            timer.Remove(hookId)
            hook.Remove("EntityTakeDamage", hookId)
            return
        end

        local owner = ent.D6_RailOwner
        if not IsValid(owner) then return end

        local pos = ent:GetPos()
        local tr  = util.TraceLine({
            start  = pos,
            endpos = pos + ent:GetVelocity() * 0.06,
            filter = { ent, owner },
            mask   = MASK_SHOT,
        })

        if IsValid(tr.Entity) and (tr.Entity:IsNPC() or tr.Entity:IsPlayer()) then
            ent.D6_RailHit = true
            local dmg = DamageInfo()
            dmg:SetAttacker(owner)
            dmg:SetInflictor(ent)
            dmg:SetDamage(ent.D6_RailDmg or 50)
            dmg:SetDamageType(DMG_CRUSH)
            dmg:SetDamageForce((tr.Entity:GetPos() - owner:GetPos()):GetNormalized() * 8000)
            tr.Entity:TakeDamageInfo(dmg)

            -- Отбрасывание
            if tr.Entity:IsPlayer() or tr.Entity:IsNPC() then
                local push = (tr.Entity:GetPos() - owner:GetPos()):GetNormalized() * 700
                push.z = push.z + 200
                tr.Entity:SetVelocity(push)
            end

            local ef = EffectData()
            ef:SetOrigin(tr.HitPos); ef:SetNormal(tr.HitNormal); ef:SetMagnitude(3)
            util.Effect("Sparks", ef)
        end
    end)
end

-- =========================================================
-- ПКМ — ВЕНТИЛЯТОР (пылесос ↔ веер)
-- =========================================================
function SWEP:SecondaryAttack()
    if CurTime() < (self.NextFire or 0) then return end
    self.NextFire = CurTime() + FIRE_COOLDOWN

    if not SERVER then return end

    local ply = self:GetOwner()
    if not IsValid(ply) then return end

    -- Есть собранные пропы — стреляем веером
    if self.FanProps and #self.FanProps > 0 then
        self:FireFan()
        return
    end

    if not self:HasEnergy(ENERGY_FAN) then
        self:DenyFire()
        return
    end

    -- Сбор мелких объектов в конусе
    local ang  = ShootAng(ply)
    local fwd  = ang:Forward()
    local muz  = ply:GetShootPos()
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

        collected[#collected + 1] = e
        e.D6_RailFanLocked = true
        ph:EnableGravity(false)
    end

    if #collected == 0 then
        self:DenyFire()
        return
    end

    self.FanProps = collected
    ply:EmitSound("weapons/physcannon/physcannon_pickup.wav", 60, 100)

    -- Стягиваем к стволу
    timer.Create("D6_RailFan_" .. self:EntIndex(), 0.05, 0, function()
        if not IsValid(self) or not IsValid(ply)
           or not self.FanProps or #self.FanProps == 0 then
            timer.Remove("D6_RailFan_" .. self:EntIndex())
            return
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
                    local vel  = (hold + offs - e:GetPos()) * 10
                    ph:SetVelocity(vel)
                    ph:Wake()
                end
            end
        end
    end)
end

function SWEP:FireFan()
    local ply = self:GetOwner()
    if not IsValid(ply) then return end
    if not self:HasEnergy(ENERGY_FAN) then
        self:DenyFire(); self:ReleaseFan(); return
    end
    self:SetEnergy(self:GetEnergy() - ENERGY_FAN)

    local ang   = ShootAng(ply)
    local fwd   = ang:Forward()
    local rgt   = ang:Right()
    local up    = ang:Up()

    timer.Remove("D6_RailFan_" .. self:EntIndex())

    for _, e in ipairs(self.FanProps) do
        if not IsValid(e) then continue end
        local ph = e:GetPhysicsObject()
        if not IsValid(ph) then continue end

        -- Случайное направление в конусе разброса
        local sx = math.Rand(-FAN_SPREAD, FAN_SPREAD)
        local sy = math.Rand(-FAN_SPREAD, FAN_SPREAD)
        local dir = (fwd
                   + rgt * math.tan(math.rad(sx))
                   + up  * math.tan(math.rad(sy))):GetNormalized()

        ph:EnableGravity(true)
        ph:Wake()
        ph:SetVelocity(dir * FAN_FIRE_SPEED)

        e.D6_RailFanLocked = false
        e.D6_RailOwner = ply
        e.D6_RailDmg   = math.Rand(5, 10)
        e.D6_RailHit   = false
        e.D6_RailUntil = CurTime() + 2.5
        self:HookRailProjectile(e)
    end

    ply:EmitSound("weapons/physcannon/physcannon_shoot.wav", 80, 105)
    net.Start("D6_RailFire")
        net.WriteEntity(ply)
        net.WriteVector(ply:GetShootPos() + fwd * 30)
        net.WriteVector(ply:GetShootPos() + fwd * 600)
        net.WriteUInt(1, 2)  -- 1 = веер
    net.Broadcast()

    self.FanProps = {}
end

function SWEP:ReleaseFan()
    timer.Remove("D6_RailFan_" .. self:EntIndex())
    for _, e in ipairs(self.FanProps or {}) do
        if IsValid(e) then
            e.D6_RailFanLocked = false
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
-- КЛИЕНТСКАЯ ЧАСТЬ — луч, HUD, эффект выстрела
-- =========================================================
if CLIENT then

    local BEAM = Material("cable/redlaser")

    -- Перекрашиваем модель в зелёный (через RenderColor)
    function SWEP:DrawWorldModel()
        render.SetColorModulation(0, 1, 0)
        self:DrawModel()
        render.SetColorModulation(1, 1, 1)
    end

    -- Зелёный луч от ствола к удерживаемому объекту
    hook.Add("PostDrawTranslucentRenderables", "D6_RailBeam", function(depth, sky)
        if depth or sky then return end
        for _, ply in ipairs(player.GetAll()) do
            local wep = ply:GetActiveWeapon()
            if not IsValid(wep) or wep:GetClass() ~= "weapon_d6_gravy_railgun" then continue end
            local held = wep:GetNWEntity("D6_RailHeld", NULL)
            if not IsValid(held) then continue end

            local ang
            if ply == LocalPlayer() then
                ang = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
            else
                ang = ply:EyeAngles()
            end
            local muz = ply:GetShootPos() + Angle(ang.p, ang.y, 0):Forward() * 20

            render.SetMaterial(BEAM)
            render.DrawBeam(muz, held:GetPos() + held:OBBCenter(),
                4, 0, 1, TINT)
            render.DrawBeam(muz, held:GetPos() + held:OBBCenter(),
                1.5, 0, 1, Color(180, 255, 180))
        end
    end)

    -- Эффект выстрела (искры + дым)
    net.Receive("D6_RailFire", function()
        local ply  = net.ReadEntity()
        local src  = net.ReadVector()
        local dst  = net.ReadVector()
        local mode = net.ReadUInt(2)

        local ef = EffectData()
        ef:SetOrigin(src)
        ef:SetNormal((dst - src):GetNormalized())
        ef:SetMagnitude(3)
        ef:SetScale(1)
        util.Effect("ManhackSparks", ef)

        local smoke = EffectData()
        smoke:SetOrigin(src)
        smoke:SetScale(0.3)
        util.Effect("MuzzleEffect", smoke)

        -- Короткая зелёная вспышка-трассер
        local life = CurTime() + 0.15
        hook.Add("PostDrawTranslucentRenderables", "D6_RailFlash_" .. CurTime(), function(d, s)
            if d or s then return end
            if CurTime() > life then
                hook.Remove("PostDrawTranslucentRenderables", "D6_RailFlash_" .. CurTime())
                return
            end
            render.SetMaterial(BEAM)
            local a = math.Clamp((life - CurTime()) / 0.15, 0, 1) * 255
            render.DrawBeam(src, dst, mode == 0 and 6 or 3, 0, 1,
                Color(TINT.r, TINT.g, TINT.b, a))
        end)
    end)

    -- HUD: полоска энергии
    hook.Add("HUDPaint", "D6_RailHUD", function()
        local ply = LocalPlayer()
        if not IsValid(ply) then return end
        local wep = ply:GetActiveWeapon()
        if not IsValid(wep) or wep:GetClass() ~= "weapon_d6_gravy_railgun" then return end

        local e = wep:GetNWInt("D6_RailEnergy", ENERGY_MAX)
        local frac = e / ENERGY_MAX
        local sw, sh = ScrW(), ScrH()
        local w, h = 220, 12
        local x = sw / 2 - w / 2
        local y = sh - 80

        surface.SetDrawColor(0, 0, 0, 180)
        surface.DrawRect(x - 2, y - 2, w + 4, h + 4)
        surface.SetDrawColor(0, 60, 0, 200)
        surface.DrawRect(x, y, w, h)
        surface.SetDrawColor(0, 255, 0, 240)
        surface.DrawRect(x, y, w * frac, h)
        draw.SimpleText("ЭНЕРГИЯ " .. math.floor(e) .. "/" .. ENERGY_MAX,
            "DermaDefault", sw / 2, y + h / 2,
            Color(220, 255, 220), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end)

end

-- =========================================================
-- РЕГИСТРАЦИЯ В РЕЕСТРЕ D6
-- =========================================================
if D6_RegisterWeapon then
    D6_RegisterWeapon("weapon_d6_gravy_railgun", {
        category = "Descent",
        autoGive = false,
        patchAim = true,
    })
end
