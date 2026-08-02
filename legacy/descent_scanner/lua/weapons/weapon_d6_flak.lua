-- ═══════════════════════════════════════════════════════════
-- weapon_d6_flak.lua — ФЛАК-ПУШКА (кинетическое облако)
--
--   Cat D: контроль объёма. Залп из 12 осколков в конусе 7°.
--   Каждый осколок: 14 прямого + 16 фраг-AoE в 90u (кинетика).
--   Близко все осколки в одну цель (~360 урона) — добивание;
--   дальше расходятся, заполняя объём — отрицание пространства роем.
--
--   Фреймворк: d6_weapon_core (FireProjectile/SplashDamage/ApplyRecoil),
--   d6_energy (TryConsume), d6_shield (кинетика → kinetic-резист).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "ФЛАК-ПУШКА"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 3
SWEP.SlotPos        = 1
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
SWEP.Primary.Automatic     = true
SWEP.Primary.Ammo          = "none"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

local MDL_PELLET = "models/weapons/w_crossbow_bolt.mdl"
if SERVER then
    util.PrecacheModel(MDL_PELLET)
    util.PrecacheSound("weapons/shotgun/shotgun_fire7.wav")
end

local ENERGY_COST   = 14
local FIRE_RATE     = 0.70
local PELLETS       = 12
local SPREAD        = 7       -- градусов конуса
local PELLET_SPEED  = 3200
local PELLET_DMG    = 14
local PELLET_SPLASH = 16
local PELLET_RAD    = 90
local PELLET_LIFE   = 1.4
local RECOIL        = 70
local MUZZLE        = { fwd = 28, rgt = 0, up = -6 }

function SWEP:Initialize() self:SetWeaponHoldType(self.HoldType) end
function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + FIRE_RATE)

    local sa  = D6_Wep.ShootAng(owner)
    local dir = sa:Forward()
    local src = D6_Wep.Muzzle(owner, MUZZLE)
    local kin = D6_DAMAGE.kinetic.dmgType

    for _ = 1, PELLETS do
        local pa   = Angle(sa.p + math.Rand(-SPREAD, SPREAD),
                           sa.y + math.Rand(-SPREAD, SPREAD), 0)
        local pdir = pa:Forward()

        D6_Wep.FireProjectile({
            owner      = owner,
            pos        = src,
            dir        = pdir,
            speed      = PELLET_SPEED,
            model      = MDL_PELLET,
            scale      = 0.6,
            color      = Color(255, 220, 150, 255),
            dmgClass   = "kinetic",
            physRadius = 3,
            mass       = 1,
            life       = PELLET_LIFE,
            trails     = {
                { col = Color(255, 210, 140), sw = 8, ew = 0, life = 0.14 },
            },
            onHit = function(b, hitEnt, hitPos, hitNormal)
                if not hitPos then return end
                if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
                    D6_Wep.DirectDamage(owner, b, hitEnt, PELLET_DMG, kin, (hitNormal or -pdir) * 1500)
                end
                D6_Wep.SplashDamage(owner, b, hitPos, 0, PELLET_SPLASH, PELLET_RAD, kin, 1800, hitEnt)
                local ef = EffectData(); ef:SetOrigin(hitPos); ef:SetNormal(hitNormal or -pdir); ef:SetScale(1)
                util.Effect("cball_bounce", ef)
            end,
        })
    end

    D6_Wep.ApplyRecoil(owner, dir, RECOIL)

    local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(dir); ef:SetScale(1.4)
    util.Effect("MuzzleFlash", ef)
    owner:EmitSound("weapons/shotgun/shotgun_fire7.wav", 88, 90)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.6)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    function SWEP:DrawHUD()
        D6_Wep.DrawEnergyHUD(self:GetOwner(), "ФЛАК-ПУШКА", Color(255, 210, 140))
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_flak.lua loaded")
