-- ═══════════════════════════════════════════════════════════
-- weapon_d6_plasma.lua — ПЛАЗМА (экзотика → энергоканал)
--   Две параллельные пушки страйдера, стреляют одновременно.
--   Снаряд: combineball.mdl — циановый плазмошар, splash AoE.
--   НАВЫК: средняя скорость снаряда + наследование импульса —
--   по движущейся цели нужно упреждение. Заметная отдача.
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

local ENERGY_COST  = 8
local FIRE_RATE    = 0.45
local BOLT_SPEED   = 3200
local BOLT_DMG     = 45
local SPLASH_DMG   = 25
local SPLASH_RAD   = 120
local RECOIL       = 40
-- Два дула row 1 — совпадают с nosegun в d6_wepview.lua
local MUZZLES = {
    { fwd=28, rgt=-29, up=-13 },
    { fwd=28, rgt= 29, up=-13 },
}

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
end

function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

local function OnHit(owner, b, hitEnt, hitPos, hitNormal)
    local center = b:GetPos()
    local ef = EffectData(); ef:SetOrigin(center); ef:SetScale(3); ef:SetMagnitude(BOLT_DMG)
    util.Effect("cball_explode", ef)
    b:EmitSound("weapons/physcannon/energy_sing_explosion2.wav", 80, 120)
    -- Экзотика → канал energy (DMG_SHOCK+EBEAM), splash AoE
    D6_Wep.SplashDamage(owner, b, center, BOLT_DMG, SPLASH_DMG, SPLASH_RAD,
        D6_DAMAGE.exotic.dmgType, 4000, owner)
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 65, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + FIRE_RATE)

    -- Параллельные стволы: оба дула бьют по борсайту (без схождения).
    local dir = D6_Wep.ShootAng(owner):Forward()

    for _, off in ipairs(MUZZLES) do
        local src = D6_Wep.Muzzle(owner, off)
        D6_Wep.FireProjectile({
            owner    = owner,
            pos      = src,
            dir      = dir,
            speed    = BOLT_SPEED,
            model    = MDL_BOLT,
            scale    = 2.2,
            color    = Color(0, 220, 255, 255),
            dmgClass = "exotic",
            physRadius = 8,
            mass     = 1,
            recoil   = RECOIL,
            life     = 5,
            trails   = {
                { col = Color(0,   200, 240), sw = 30, ew = 3, life = 0.40 },
                { col = Color(180, 255, 255), sw = 10, ew = 0, life = 0.22 },
            },
            onHit = function(b, hitEnt, hitPos, hitNormal)
                OnHit(owner, b, hitEnt, hitPos, hitNormal)
            end,
        })
        local mf = EffectData(); mf:SetOrigin(src); mf:SetNormal(dir); mf:SetScale(2)
        util.Effect("cball_explode", mf)
    end

    owner:EmitSound("npc/strider/fire.wav", 85, 110)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.8)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    function SWEP:DrawHUD()
        D6_Wep.DrawEnergyHUD(self:GetOwner(), "ПЛАЗМА", Color(0, 220, 255))
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_plasma.lua loaded")
