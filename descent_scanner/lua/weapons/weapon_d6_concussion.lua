-- ═══════════════════════════════════════════════════════════
-- weapon_d6_concussion.lua — КС-РАКЕТА (тяжёлая ударная ракета)
--
--   Одиночная ракета без наведения. Большой радиус взрыва 280u.
--   Огромная отдача (200) толкает корабль назад при пуске.
--   Разрушение комнат. Добивание тяжёлых целей.
--   Отличие от rockets: медленнее, взрыв мощнее, отдача критическая.
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "КС-РАКЕТА"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 4
SWEP.SlotPos        = 3
SWEP.Spawnable      = true
SWEP.AdminSpawnable = true
SWEP.Base           = "weapon_base"
SWEP.HoldType       = "physgun"
SWEP.ViewModel      = "models/weapons/v_physics.mdl"
SWEP.WorldModel     = "models/weapons/w_physics.mdl"
SWEP.UseHands       = false
SWEP.DrawAmmo       = false
SWEP.DrawCrosshair  = false
SWEP.Primary.ClipSize      = -1
SWEP.Primary.DefaultClip   = -1
SWEP.Primary.Automatic     = false
SWEP.Primary.Ammo          = "none"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

local MDL_MISSILE = "models/weapons/w_missile.mdl"
if SERVER then
    util.PrecacheModel(MDL_MISSILE)
    util.PrecacheSound("weapons/rpg/rocketfire1.wav")
    util.PrecacheSound("weapons/rpg/rocket_explode.wav")
end

local ENERGY_COST = 35
local FIRE_RATE   = 2.0
local PROJ_SPEED  = 1600
local DIRECT_DMG  = 120
local SPLASH_DMG  = 90
local SPLASH_RAD  = 280
local RECOIL      = 200
local MUZZLE      = { fwd = 32, rgt = 0, up = -10 }

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

    local sa  = D6_Wep.ShootAng(owner)
    local dir = sa:Forward()
    local src = D6_Wep.Muzzle(owner, MUZZLE)

    D6_Wep.FireProjectile({
        owner      = owner,
        pos        = src,
        dir        = dir,
        speed      = PROJ_SPEED,
        model      = MDL_MISSILE,
        scale      = 1.6,
        color      = Color(255, 100, 20, 255),
        dmgClass   = "explosive",
        physRadius = 8,
        mass       = 8,
        recoil     = RECOIL,
        life       = 8,
        trails     = {
            { col = Color(255, 100, 20),  sw = 60, ew = 8, life = 0.8 },
            { col = Color(255, 220, 160), sw = 20, ew = 2, life = 0.5 },
        },
        onHit = function(b, hitEnt, hitPos, hitNormal)
            local pos = hitPos or b:GetPos()

            if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
                D6_Wep.DirectDamage(owner, b, hitEnt, DIRECT_DMG, DMG_BLAST,
                    (hitNormal or -dir) * 7000)
            end
            D6_Wep.SplashDamage(owner, b, pos, 0, SPLASH_DMG, SPLASH_RAD, DMG_BLAST, 6000, hitEnt)

            local ef = EffectData()
            ef:SetOrigin(pos); ef:SetScale(3); ef:SetMagnitude(SPLASH_RAD)
            util.Effect("HelicopterMegaBomb", ef)
            local ef2 = EffectData()
            ef2:SetOrigin(pos); ef2:SetScale(3)
            util.Effect("cball_explode", ef2)
            sound.Play("weapons/rpg/rocket_explode.wav", pos, 100, 80)
        end,
    })

    local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(dir)
    util.Effect("RPGShot", ef)
    owner:EmitSound("weapons/rpg/rocketfire1.wav", 90, 85)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.8)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    function SWEP:DrawHUD()
        D6_Wep.DrawEnergyHUD(self:GetOwner(), "КС-РАКЕТА", Color(255, 100, 20))
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_concussion.lua loaded")
