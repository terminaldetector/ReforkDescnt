-- ═══════════════════════════════════════════════════════════
-- weapon_d6_heavy.lua — ТЯЖЁЛЫЙ (взрывной)
--   Снаряд: красный плазменный шар (combineball.mdl), AoE-взрыв.
--   НАВЫК: медленный снаряд + наследование импульса → сильное
--   упреждение. Мощная отдача ощутимо толкает корабль —
--   залпами надо управлять (drift management).
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

local ENERGY_COST  = 18
local FIRE_RATE    = 1.1
local ORB_SPEED    = 1100
local ORB_DMG      = 80
local ORB_RADIUS   = 220
local AOE_DMG      = 60
local RECOIL       = 160     -- мощный толчок корабля назад
local MUZZLE       = { fwd=29, rgt=0, up=-12 }

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
end

function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 65, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + FIRE_RATE)

    local dir = D6_Wep.ShootAng(owner):Forward()
    local src = D6_Wep.Muzzle(owner, MUZZLE)

    D6_Wep.FireProjectile({
        owner    = owner,
        pos      = src,
        dir      = dir,
        speed    = ORB_SPEED,
        model    = MDL_ORB,
        scale    = 2.8,
        color    = Color(255, 30, 30, 255),
        dmgClass = "explosive",
        physRadius = 10,
        mass     = 1,
        recoil   = RECOIL,
        life     = 6,
        trails   = {
            { col = Color(255,  40,  20), sw = 44, ew = 5, life = 0.65 },
            { col = Color(255, 180, 100), sw = 18, ew = 1, life = 0.40 },
        },
        onHit = function(b, hitEnt, hitPos, hitNormal)
            local center = b:GetPos()
            local ef = EffectData(); ef:SetOrigin(center); ef:SetScale(5); ef:SetMagnitude(ORB_DMG)
            util.Effect("cball_explode", ef)
            util.Effect("HelicopterMegaBomb", ef)
            b:EmitSound("weapons/physcannon/energy_sing_explosion2.wav", 105, 90)
            D6_Wep.SplashDamage(owner, b, center, ORB_DMG, AOE_DMG, ORB_RADIUS,
                D6_DAMAGE.explosive.dmgType, 8000, owner)
        end,
    })

    local mf = EffectData(); mf:SetOrigin(src); mf:SetNormal(dir); mf:SetScale(4)
    util.Effect("cball_explode", mf)
    owner:EmitSound("weapons/physcannon/superphys_launch1.wav", 85, 70)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.8)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    function SWEP:DrawHUD()
        D6_Wep.DrawEnergyHUD(self:GetOwner(), "ТЯЖЁЛЫЙ", Color(255, 80, 50))
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_heavy.lua loaded")
