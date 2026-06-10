-- ═══════════════════════════════════════════════════════════
-- weapon_d6_pulse.lua — ПУЛЬСАР, 4-ствольный метатель стержней
--   Снаряд: crossbow_bolt / w_crossbow_bolt.mdl
--   Скорость: ~4500 (Quake 1 nailgun), никакого хитскана.
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName       = "ПУЛЬСАР"
SWEP.Author          = "Descent 6DOF"
SWEP.Category        = "Descent"
SWEP.Slot            = 2
SWEP.SlotPos         = 0
SWEP.Spawnable       = true
SWEP.AdminSpawnable  = true
SWEP.Base            = "weapon_base"
SWEP.HoldType        = "physgun"
SWEP.ViewModel       = "models/weapons/v_physics.mdl"
SWEP.WorldModel      = "models/weapons/w_physics.mdl"
SWEP.UseHands        = false
SWEP.DrawAmmo        = false
SWEP.DrawCrosshair   = false
SWEP.Primary.ClipSize    = -1
SWEP.Primary.DefaultClip = -1
SWEP.Primary.Automatic   = true
SWEP.Primary.Ammo        = "none"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

local MDL_BOLT = "models/weapons/w_crossbow_bolt.mdl"
if SERVER then
    util.PrecacheModel(MDL_BOLT)
    util.PrecacheSound("weapons/crossbow/bolt1.wav")
    util.PrecacheSound("weapons/crossbow/hit1.wav")
end

local ENERGY_MAX   = 100
local ENERGY_COST  = 2
local ENERGY_REGEN = 8
local BOLT_SPEED   = 4500
local BOLT_DMG     = 30

-- dir = ShootAng (pitch+yaw, no roll) — только направление стрельбы
local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

-- Позиции дул — синхронизированы с d6_wepview.lua Layout()
-- fwd — через ShootAng (без крена), rgt/up — через полный D6Ang с кренем
local MUZZLES = {
    { fwd=50, rgt=-46, up=-20 },
    { fwd=50, rgt= 46, up=-20 },
    { fwd=50, rgt=-50, up= 16 },
    { fwd=50, rgt= 50, up= 16 },
}

local function MuzzleWorld(ply, off)
    local noRoll = ShootAng(ply)
    local full   = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return ply:GetShootPos()
        + noRoll:Forward() * off.fwd
        + full:Right()     * off.rgt
        + full:Up()        * off.up
end

local function EnsurePhys(ent)
    local ph = ent:GetPhysicsObject()
    if IsValid(ph) then return ph end
    ent:PhysicsInitSphere(3, "metal")
    ent:SetMoveType(MOVETYPE_VPHYSICS)
    return ent:GetPhysicsObject()
end

local function SpawnBolt(owner, pos, dir)
    local bolt = ents.Create("prop_physics")
    if not IsValid(bolt) then return end
    bolt:SetModel(MDL_BOLT)
    bolt:SetPos(pos)
    bolt:SetAngles(dir:Angle())
    bolt:SetOwner(owner)
    bolt:Spawn()
    bolt:SetCollisionGroup(COLLISION_GROUP_PROJECTILE)
    bolt:SetColor(Color(200, 240, 255, 255))
    bolt:SetRenderMode(RENDERMODE_TRANSADD)
    bolt:SetModelScale(1.6, 0)

    util.SpriteTrail(bolt, 0, Color(140, 200, 255), false, 12, 1, 0.15, 1/13*0.5, "trails/laser.vmt")
    util.SpriteTrail(bolt, 1, Color(255, 255, 255), false,  4, 0, 0.08, 1/5 *0.5, "trails/laser.vmt")

    local ph = EnsurePhys(bolt)
    if IsValid(ph) then
        ph:SetVelocity(dir * BOLT_SPEED)
        ph:EnableGravity(false)
        ph:EnableDrag(false)
    end

    -- Надёжная детекция удара (PhysicsCollide) — helper из d6_core.lua
    D6_TrackProjectile(bolt, owner, function(b, hitEnt, hitPos, hitNormal)
        if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
            local di = DamageInfo()
            di:SetAttacker(IsValid(owner) and owner or game.GetWorld())
            di:SetInflictor(b)
            di:SetDamage(BOLT_DMG)
            di:SetDamageType(DMG_BULLET)
            di:SetDamageForce(dir * 2000)
            hitEnt:TakeDamageInfo(di)
        end
        if IsValid(hitEnt) then
            local ef = EffectData(); ef:SetOrigin(hitPos); ef:SetNormal(hitNormal); ef:SetScale(1.5)
            util.Effect("AR2Impact", ef)
            b:EmitSound("weapons/crossbow/hit1.wav", 60, 120)
        end
    end, 5)
end

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
end

function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

-- Реген энергии теперь централизован в d6_energy.lua (D6_Energy.RegenTick).

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 65, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + 0.10)

    local sa  = ShootAng(owner)
    local fwd = sa:Forward()

    for _, off in ipairs(MUZZLES) do
        local src = MuzzleWorld(owner, off)
        local aim = src + fwd * 5000
        local dir = (aim - src):GetNormalized()
        SpawnBolt(owner, src, dir)
        local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(dir); ef:SetScale(0.7)
        util.Effect("MuzzleFlash", ef)
    end

    owner:EmitSound("weapons/crossbow/bolt1.wav", 68, 105 + math.random(-6, 6))
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end
    self:SetNextSecondaryFire(CurTime() + 0.8)
    local rkt = owner:GetWeapon("weapon_d6_rockets")
    if IsValid(rkt) then rkt:PrimaryAttack() end
end

if CLIENT then
    function SWEP:DrawHUD()
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        local energy = ply:GetNWFloat("D6_WepEnergy", ENERGY_MAX)
        local sw, sh = ScrW(), ScrH()
        local bw, bh = 140, 6
        local bx, by = sw/2 - bw/2, sh - 72
        surface.SetDrawColor(30, 30, 30, 180); surface.DrawRect(bx-1, by-1, bw+2, bh+2)
        local col = energy > 30 and Color(0,180,255) or Color(255,60,60)
        surface.SetDrawColor(col.r, col.g, col.b, 200)
        surface.DrawRect(bx, by, bw * (energy/ENERGY_MAX), bh)
        draw.SimpleText("ПУЛЬСАР  ⚡ "..math.floor(energy), "DermaDefault",
            sw/2, sh-90, Color(180,220,255), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_pulse.lua loaded")
