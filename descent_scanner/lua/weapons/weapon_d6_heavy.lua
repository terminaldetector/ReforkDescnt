-- ═══════════════════════════════════════════════════════════
-- weapon_d6_heavy.lua — ТЯЖЁЛЫЙ, медленный орб + AoE взрыв
-- Рендер моделей — в d6_wepview.lua (центральный хук).
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

-- Снаряд: граната HL2 (w_grenade.mdl) — тяжёлый AoE-заряд
local MDL_ORB = "models/weapons/w_grenade.mdl"
if SERVER then
    util.PrecacheModel(MDL_ORB)
    util.PrecacheSound("weapons/grenade/grenade_explode.wav")
end

local ENERGY_MAX   = 100
local ENERGY_COST  = 15
local ENERGY_REGEN = 8
local ORB_SPEED    = 900
local ORB_DMG      = 70
local ORB_RADIUS   = 180
local AOE_DMG      = 50

local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

local function EnsurePhys(ent)
    local ph = ent:GetPhysicsObject()
    if IsValid(ph) then return ph end
    ent:PhysicsInitSphere(8, "metal")
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
    orb:SetColor(Color(255, 120, 0, 255))
    orb:SetRenderMode(RENDERMODE_TRANSADD)
    orb:SetModelScale(2.2, 0)

    -- Широкий огненный след + яркое ядро
    util.SpriteTrail(orb, 0, Color(255, 100,   0), false, 36, 3, 0.55, 1/37*0.5, "trails/laser.vmt")
    util.SpriteTrail(orb, 1, Color(255, 220, 120), false, 14, 1, 0.35, 1/15*0.5, "trails/laser.vmt")

    local phys = EnsurePhys(orb)
    if IsValid(phys) then
        phys:SetVelocity(dir * ORB_SPEED)
        phys:EnableGravity(false)
        phys:EnableDrag(false)
    end

    orb.D6_Owner = owner
    local idx = tostring(orb:EntIndex())

    local function Explode()
        if not IsValid(orb) then return end
        hook.Remove("EntityCollision", "D6_Orb_"..idx)
        timer.Remove("D6_Orb_"..idx)
        local hitPos = orb:GetPos()

        local ef = EffectData(); ef:SetOrigin(hitPos); ef:SetScale(4); ef:SetMagnitude(ORB_DMG)
        util.Effect("Explosion", ef)
        util.Effect("HelicopterMegaBomb", ef)
        orb:EmitSound("weapons/grenade/grenade_explode.wav", 100, 95)

        for _, e in ipairs(ents.FindInSphere(hitPos, ORB_RADIUS)) do
            if not IsValid(e) then continue end
            if not (e:IsNPC() or e:IsPlayer()) then continue end
            if e == orb.D6_Owner then continue end
            local dist = e:GetPos():Distance(hitPos)
            local dmg  = ORB_DMG + AOE_DMG * (1 - dist / ORB_RADIUS)
            local di   = DamageInfo()
            di:SetAttacker(IsValid(orb.D6_Owner) and orb.D6_Owner or game.GetWorld())
            di:SetInflictor(orb)
            di:SetDamage(dmg)
            di:SetDamageType(DMG_BLAST + DMG_ENERGYBEAM)
            di:SetDamageForce((e:GetPos() - hitPos):GetNormalized() * 6000)
            e:TakeDamageInfo(di)
        end

        orb:Remove()
    end

    hook.Add("EntityCollision", "D6_Orb_"..idx, function(ent, data)
        if ent ~= orb then return end
        if IsValid(data.HitEntity) and data.HitEntity == orb.D6_Owner then return end
        Explode()
    end)

    timer.Create("D6_Orb_"..idx, 5, 1, function() Explode() end)
end

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
end

function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

function SWEP:Think()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end
    local e = owner:GetNWFloat("D6_WepEnergy", ENERGY_MAX)
    if e < ENERGY_MAX then
        owner:SetNWFloat("D6_WepEnergy", math.min(ENERGY_MAX, e + ENERGY_REGEN * FrameTime()))
    end
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    local energy = owner:GetNWFloat("D6_WepEnergy", ENERGY_MAX)
    if energy < ENERGY_COST then
        owner:EmitSound("buttons/button10.wav", 65, 100); return
    end
    owner:SetNWFloat("D6_WepEnergy", energy - ENERGY_COST)
    self:SetNextPrimaryFire(CurTime() + 1.0)

    local sa  = ShootAng(owner)
    local fwd = sa:Forward()
    -- Орб вылетает из центральной гравипушки
    local src = owner:GetShootPos() + fwd * 40 + sa:Up() * -14

    SpawnHeavyOrb(owner, src, fwd)

    local mf = EffectData(); mf:SetOrigin(src); mf:SetNormal(fwd); mf:SetScale(3)
    util.Effect("cball_explode", mf)

    owner:EmitSound("weapons/physcannon/superphys_launch1.wav", 80, 75)
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
            sw/2, sh-90, Color(220,80,40), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_heavy.lua loaded")
