-- ═══════════════════════════════════════════════════════════
-- weapon_d6_mega_missile.lua — МЕГА-РАКЕТА
--
--   Cat B: LIMITED AMMO. Без расхода энергии.
--   Тупая, медленная (800 u/s). Нет самонаведения.
--   Огромный взрыв 500u, 350+200 урона. Отдача 500.
--   Нанесение: влётная траектория, потом детонация.
--   Одиночный выстрел, 8 с КД. Запас: 1 шт.
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "МЕГА-РАКЕТА"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 4
SWEP.SlotPos        = 6
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
SWEP.Primary.DefaultClip   = 0
SWEP.Primary.Automatic     = false
SWEP.Primary.Ammo          = "d6_mega"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = 0
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

local MDL_MISSILE = "models/weapons/w_missile.mdl"
if SERVER then
    util.PrecacheModel(MDL_MISSILE)
    util.PrecacheSound("weapons/rpg/rocketfire1.wav")
    util.PrecacheSound("weapons/rpg/rocket_explode.wav")
    util.PrecacheSound("ambient/explosions/explode_4.wav")
end

local AMMO_TYPE    = "d6_mega"
local MAX_AMMO     = 2
local FIRE_RATE    = 8.0
local PROJ_SPEED   = 800
local DIRECT_DMG   = 350
local SPLASH_DMG   = 200
local SPLASH_RAD   = 500
local RECOIL       = 500
local MISSILE_LIFE = 20
local MUZZLE       = { fwd = 36, rgt = 0, up = -12 }

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
end

function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if owner:GetAmmoCount(AMMO_TYPE) <= 0 then
        owner:EmitSound("buttons/button10.wav", 65, 100); return
    end
    owner:RemoveAmmo(1, AMMO_TYPE)
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
        scale      = 3.5,
        color      = Color(255, 60, 0, 255),
        dmgClass   = "explosive",
        physRadius = 20,
        mass       = 40,
        recoil     = RECOIL,
        life       = MISSILE_LIFE,
        trails     = {
            { col = Color(255,  60,  0),  sw = 120, ew = 20, life = 1.4 },
            { col = Color(255, 160, 50),  sw =  60, ew =  8, life = 0.9 },
            { col = Color(255, 240, 200), sw =  24, ew =  2, life = 0.5 },
        },
        onHit = function(b, hitEnt, hitPos, hitNormal)
            if not hitPos then return end
            local pos = hitPos

            if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
                D6_Wep.DirectDamage(owner, b, hitEnt, DIRECT_DMG, DMG_BLAST,
                    (hitNormal or -dir) * 12000)
            end
            D6_Wep.SplashDamage(owner, b, pos, 0, SPLASH_DMG, SPLASH_RAD, DMG_BLAST, 10000, hitEnt)

            -- Layered explosion effects for nuke scale
            local ef = EffectData()
            ef:SetOrigin(pos); ef:SetScale(12); ef:SetMagnitude(SPLASH_RAD)
            util.Effect("HelicopterMegaBomb", ef)
            local ef2 = EffectData()
            ef2:SetOrigin(pos); ef2:SetScale(12); ef2:SetMagnitude(SPLASH_RAD)
            util.Effect("Explosion", ef2)
            local ef3 = EffectData()
            ef3:SetOrigin(pos); ef3:SetScale(6)
            util.Effect("cball_explode", ef3)

            sound.Play("weapons/rpg/rocket_explode.wav", pos, 120, 60)
            sound.Play("ambient/explosions/explode_4.wav", pos, 120, 70)
        end,
    })

    local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(dir)
    util.Effect("RPGShot", ef)
    owner:EmitSound("weapons/rpg/rocketfire1.wav", 100, 70)
end

function SWEP:SecondaryAttack()
    self:SetNextSecondaryFire(CurTime() + 0.5)
end

if CLIENT then
    function SWEP:DrawHUD()
        D6_Wep.DrawAmmoHUD(self:GetOwner(), "МЕГА-РАКЕТА", AMMO_TYPE, MAX_AMMO,
            Color(255, 60, 0))
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_mega_missile.lua loaded")
