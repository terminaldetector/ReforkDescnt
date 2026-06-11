-- ═══════════════════════════════════════════════════════════
-- weapon_d6_heavy.lua — ТЯЖЁЛЫЙ
--   Снаряд: красный плазменный шар (combineball.mdl)
--   Поведение: cball_explode — AoE взрыв при касании
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName       = "ТЯЖЁЛЫЙ"
SWEP.Author          = "Descent 6DOF"
SWEP.Category        = "Descent"
SWEP.Slot            = 2
SWEP.SlotPos         = 2
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

local MDL_ORB = "models/effects/combineball.mdl"
if SERVER then
    util.PrecacheModel(MDL_ORB)
    util.PrecacheSound("weapons/physcannon/energy_sing_explosion2.wav")
    util.PrecacheSound("weapons/physcannon/superphys_launch1.wav")
end

local ENERGY_MAX   = 100
local ENERGY_COST  = 18
local ENERGY_REGEN = 8
local ORB_SPEED    = 1100
local ORB_DMG      = 80
local ORB_RADIUS   = 220
local AOE_DMG      = 60

local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

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
    ent:PhysicsInitSphere(10, "metal")
    ent:SetMoveType(MOVETYPE_VPHYSICS)
    return ent:GetPhysicsObject()
end

local function SpawnHeavyOrb(owner, pos, dir)
    local orb = ents.Create("prop_physics")
    if not IsValid(orb) then return end
    orb:SetModel(MDL_ORB)
    orb:SetPos(pos)
    orb:SetAngles(dir:Angle())
    orb:SetOwner(owner)
    orb:Spawn()
    orb:SetCollisionGroup(COLLISION_GROUP_PROJECTILE)
    -- Красный плазменный шар
    orb:SetColor(Color(255, 30, 30, 255))
    orb:SetRenderMode(RENDERMODE_TRANSADD)
    orb:SetModelScale(2.8, 0)

    util.SpriteTrail(orb, 0, Color(255,  40,  20), false, 44, 5, 0.65, 1/45*0.5, "trails/laser.vmt")
    util.SpriteTrail(orb, 1, Color(255, 180, 100), false, 18, 1, 0.40, 1/19*0.5, "trails/laser.vmt")

    local phys = EnsurePhys(orb)
    if IsValid(phys) then
        phys:EnableGravity(false)
        phys:EnableDrag(false)
        phys:SetMass(1)
        phys:SetVelocity(dir * ORB_SPEED)
        phys:Wake()
    end

    -- Надёжная детекция (PhysicsCollide) → cball_explode + AoE взрыв
    D6_TrackProjectile(orb, owner, function(b, hitEnt, hitPos, hitNormal)
        local center = b:GetPos()
        local ef = EffectData(); ef:SetOrigin(center); ef:SetScale(5); ef:SetMagnitude(ORB_DMG)
        util.Effect("cball_explode", ef)
        util.Effect("HelicopterMegaBomb", ef)
        b:EmitSound("weapons/physcannon/energy_sing_explosion2.wav", 105, 90)

        local atk = IsValid(owner) and owner or game.GetWorld()
        for _, e in ipairs(ents.FindInSphere(center, ORB_RADIUS)) do
            if not IsValid(e) then continue end
            if not (e:IsNPC() or e:IsPlayer()) then continue end
            if e == owner then continue end
            local dist = e:GetPos():Distance(center)
            local dmg  = ORB_DMG + AOE_DMG * math.max(0, 1 - dist / ORB_RADIUS)
            local di   = DamageInfo()
            di:SetAttacker(atk)
            di:SetInflictor(b)
            di:SetDamage(dmg)
            di:SetDamageType(DMG_BLAST + DMG_ENERGYBEAM)
            di:SetDamageForce((e:GetPos() - center):GetNormalized() * 8000)
            e:TakeDamageInfo(di)
        end
    end, 6)
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
    self:SetNextPrimaryFire(CurTime() + 1.1)

    local sa  = ShootAng(owner)
    local fwd = sa:Forward()
    -- Центральный ствол гравипушки
    local src = MuzzleWorld(owner, { fwd=29, rgt=0, up=-12 })

    SpawnHeavyOrb(owner, src, fwd)

    local mf = EffectData(); mf:SetOrigin(src); mf:SetNormal(fwd); mf:SetScale(4)
    util.Effect("cball_explode", mf)

    owner:EmitSound("weapons/physcannon/superphys_launch1.wav", 85, 70)
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
        draw.SimpleText("ТЯЖЁЛЫЙ  ⚡ "..math.floor(energy), "DermaDefault",
            sw/2, sh-90, Color(255,80,50), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_heavy.lua loaded")
