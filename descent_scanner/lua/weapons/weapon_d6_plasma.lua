-- ═══════════════════════════════════════════════════════════
-- weapon_d6_plasma.lua — ПЛАЗМА, видимые болты со светящимся следом
-- Рендер моделей — в d6_wepview.lua (центральный хук).
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

-- Снаряд: граната SMG из HL2 (есть .phy, гарантированно в базе)
local MDL_BOLT = "models/items/ar2_grenade.mdl"
if SERVER then util.PrecacheModel(MDL_BOLT) end

local ENERGY_MAX   = 100
local ENERGY_COST  = 5
local ENERGY_REGEN = 8
local BOLT_SPEED   = 3800
local BOLT_DMG     = 28

local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

-- Дула двух nosegun — ряд 1, нижние углы (синхр. с d6_wepview.lua)
local MUZZLES = {
    { fwd=48, rgt=-46, up=-20 },
    { fwd=48, rgt= 46, up=-20 },
}

local function MuzzleWorld(ply, off)
    local ang = ShootAng(ply)
    return ply:GetShootPos()
        + ang:Forward() * off.fwd
        + ang:Right()   * off.rgt
        + ang:Up()      * off.up
end

-- Гарантированная физика: если у модели нет .phy — сферический фолбэк
local function EnsurePhys(ent)
    local ph = ent:GetPhysicsObject()
    if IsValid(ph) then return ph end
    ent:PhysicsInitSphere(5, "metal")
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
    bolt:SetColor(Color(0, 240, 255, 255))
    bolt:SetRenderMode(RENDERMODE_TRANSADD)
    bolt:SetModelScale(1.6, 0)

    -- Двойной след: широкий синий + яркое ядро
    util.SpriteTrail(bolt, 0, Color(0,  200, 255), false, 22, 2, 0.30, 1/23*0.5, "trails/laser.vmt")
    util.SpriteTrail(bolt, 1, Color(180, 240, 255), false,  8, 0, 0.20, 1/9 *0.5, "trails/laser.vmt")

    local phys = EnsurePhys(bolt)
    if IsValid(phys) then
        phys:SetVelocity(dir * BOLT_SPEED)
        phys:EnableGravity(false)
        phys:EnableDrag(false)
    end

    bolt.D6_Owner = owner
    local idx = tostring(bolt:EntIndex())

    hook.Add("EntityCollision", "D6_Bolt_"..idx, function(ent, data)
        if ent ~= bolt then return end
        if IsValid(data.HitEntity) and data.HitEntity == bolt.D6_Owner then return end

        if IsValid(data.HitEntity) and (data.HitEntity:IsNPC() or data.HitEntity:IsPlayer()) then
            local di = DamageInfo()
            di:SetAttacker(IsValid(bolt.D6_Owner) and bolt.D6_Owner or game.GetWorld())
            di:SetInflictor(bolt)
            di:SetDamage(BOLT_DMG)
            di:SetDamageType(DMG_ENERGYBEAM)
            di:SetDamageForce(dir * 1500)
            data.HitEntity:TakeDamageInfo(di)
        end

        local p = bolt:GetPos()
        local ef = EffectData(); ef:SetOrigin(p); ef:SetNormal(-dir); ef:SetScale(3); ef:SetMagnitude(2)
        util.Effect("cball_bounce", ef)
        local ef2 = EffectData(); ef2:SetOrigin(p); ef2:SetScale(1.4)
        util.Effect("ElectricSpark", ef2)

        hook.Remove("EntityCollision", "D6_Bolt_"..idx)
        timer.Remove("D6_Bolt_"..idx)
        if IsValid(bolt) then bolt:Remove() end
    end)

    timer.Create("D6_Bolt_"..idx, 4, 1, function()
        hook.Remove("EntityCollision", "D6_Bolt_"..idx)
        if IsValid(bolt) then bolt:Remove() end
    end)
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
    self:SetNextPrimaryFire(CurTime() + 0.35)

    local sa  = ShootAng(owner)
    local aim = owner:GetShootPos() + sa:Forward() * 4000

    for _, off in ipairs(MUZZLES) do
        local src = MuzzleWorld(owner, off)
        local dir = (aim - src):GetNormalized()
        SpawnPlasmaBolt(owner, src, dir)
        local mf = EffectData(); mf:SetOrigin(src); mf:SetNormal(dir); mf:SetScale(1)
        util.Effect("MuzzleFlash", mf)
    end

    owner:EmitSound("weapons/physcannon/energy_sing_flyby.wav", 70, 140)
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
