-- ═══════════════════════════════════════════════════════════
-- weapon_d6_plasma.lua — ПЛАЗМА
--   Две параллельные пушки страйдера, стреляют одновременно.
--   Снаряд: combineball.mdl — циановый, поведение cball_explode.
--   Звук: npc/strider/fire.wav (плазмопушка страйдера).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName       = "ПЛАЗМА"
SWEP.Author          = "Descent 6DOF"
SWEP.Category        = "Descent"
SWEP.Slot            = 2
SWEP.SlotPos         = 1
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

local MDL_BOLT = "models/effects/combineball.mdl"
if SERVER then
    util.PrecacheModel(MDL_BOLT)
    util.PrecacheSound("npc/strider/fire.wav")
    util.PrecacheSound("weapons/physcannon/energy_sing_explosion2.wav")
end

local ENERGY_MAX   = 100
local ENERGY_COST  = 8
local ENERGY_REGEN = 8
local BOLT_SPEED   = 3200
local BOLT_DMG     = 45
local SPLASH_DMG   = 25
local SPLASH_RAD   = 120

local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

-- Два дула row 1 — совпадают с nosegun в d6_wepview.lua (rgt ±46, up -20)
local MUZZLES = {
    { fwd=44, rgt=-46, up=-20 },
    { fwd=44, rgt= 46, up=-20 },
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
    ent:PhysicsInitSphere(8, "metal")
    ent:SetMoveType(MOVETYPE_VPHYSICS)
    return ent:GetPhysicsObject()
end

local function SpawnPlasmaBolt(owner, pos, dir)
    local bolt = ents.Create("prop_physics")
    if not IsValid(bolt) then return end
    bolt:SetModel(MDL_BOLT)
    bolt:SetPos(pos)
    bolt:SetAngles(dir:Angle())
    bolt:SetOwner(owner)
    bolt:Spawn()
    bolt:SetCollisionGroup(COLLISION_GROUP_PROJECTILE)
    -- Страйдер-циановый плазменный цвет
    bolt:SetColor(Color(0, 220, 255, 255))
    bolt:SetRenderMode(RENDERMODE_TRANSADD)
    bolt:SetModelScale(2.2, 0)

    util.SpriteTrail(bolt, 0, Color(0,  200, 240), false, 30, 3, 0.40, 1/31*0.5, "trails/laser.vmt")
    util.SpriteTrail(bolt, 1, Color(180, 255, 255), false, 10, 0, 0.22, 1/11*0.5, "trails/laser.vmt")

    local phys = EnsurePhys(bolt)
    if IsValid(phys) then
        phys:SetVelocity(dir * BOLT_SPEED)
        phys:EnableGravity(false)
        phys:EnableDrag(false)
    end

    -- Надёжная детекция (PhysicsCollide) → cball_explode + splash AoE
    D6_TrackProjectile(bolt, owner, function(b, hitEnt, hitPos, hitNormal)
        local center = b:GetPos()
        local ef = EffectData(); ef:SetOrigin(center); ef:SetScale(3); ef:SetMagnitude(BOLT_DMG)
        util.Effect("cball_explode", ef)
        b:EmitSound("weapons/physcannon/energy_sing_explosion2.wav", 80, 120)

        local atk = IsValid(owner) and owner or game.GetWorld()
        for _, e in ipairs(ents.FindInSphere(center, SPLASH_RAD)) do
            if not IsValid(e) then continue end
            if not (e:IsNPC() or e:IsPlayer()) then continue end
            if e == owner then continue end
            local dist = e:GetPos():Distance(center)
            local dmg  = BOLT_DMG + SPLASH_DMG * math.max(0, 1 - dist / SPLASH_RAD)
            local di   = DamageInfo()
            di:SetAttacker(atk)
            di:SetInflictor(b)
            di:SetDamage(dmg)
            di:SetDamageType(DMG_ENERGYBEAM + DMG_BLAST)
            di:SetDamageForce((e:GetPos() - center):GetNormalized() * 4000)
            e:TakeDamageInfo(di)
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
    self:SetNextPrimaryFire(CurTime() + 0.45)

    local sa  = ShootAng(owner)
    local aim = owner:GetShootPos() + sa:Forward() * 5000

    for _, off in ipairs(MUZZLES) do
        local src = MuzzleWorld(owner, off)
        local dir = (aim - src):GetNormalized()
        SpawnPlasmaBolt(owner, src, dir)
        local mf = EffectData(); mf:SetOrigin(src); mf:SetNormal(dir); mf:SetScale(2)
        util.Effect("cball_explode", mf)
    end

    owner:EmitSound("npc/strider/fire.wav", 85, 110)
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
        draw.SimpleText("ПЛАЗМА  ⚡ "..math.floor(energy), "DermaDefault",
            sw/2, sh-90, Color(0,220,255), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_plasma.lua loaded")
