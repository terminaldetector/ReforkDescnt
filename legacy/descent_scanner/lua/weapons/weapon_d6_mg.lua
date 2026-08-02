-- ═══════════════════════════════════════════════════════════
-- weapon_d6_mg.lua — ПУЛЕМЁТ (кинетика)
--   Одноствольный скорострел. Низкий урон за пулю, высокий темп.
--   НАВЫК: разброс растёт со скоростью корабля — чтобы бить точно,
--   надо сбрасывать ход. Снаряды наследуют импульс корабля
--   (нужно упреждение), каждый выстрел даёт лёгкую отдачу.
--   Снаряд: w_crossbow_bolt.mdl @ ~5000 (Quake nailgun), без хитскана.
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName       = "ПУЛЕМЁТ"
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

local ENERGY_COST  = 1
local FIRE_RATE    = 0.06
local BOLT_SPEED   = 5000
local BOLT_DMG     = 12
local RECOIL       = 5      -- лёгкая, копится при удержании
-- Разброс (градусы): чем быстрее корабль — тем шире конус.
local SPREAD_MIN   = 0.35
local SPREAD_MAX   = 4.5
local SPEED_REF    = 2200   -- скорость, при которой разброс максимален
-- Одно центральное дуло (одноствольный пулемёт)
local MUZZLE = { fwd=30, rgt=0, up=-7 }

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
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + FIRE_RATE)

    local sa  = D6_Wep.ShootAng(owner)
    local fwd = sa:Forward()

    -- Разброс зависит от скорости корабля (связь с движением + навык):
    -- держишь ход — широкий конус; сбрасываешь — точный выстрел.
    local spd    = D6_Wep.ShipVel(owner):Length()
    local spread = math.Clamp(SPREAD_MIN + (spd / SPEED_REF) * SPREAD_MAX, SPREAD_MIN, SPREAD_MAX)
    local tan    = math.tan(math.rad(spread))
    local dir    = (fwd
                  + sa:Right() * math.Rand(-1, 1) * tan
                  + sa:Up()    * math.Rand(-1, 1) * tan):GetNormalized()

    local src = D6_Wep.Muzzle(owner, MUZZLE)

    D6_Wep.FireProjectile({
        owner    = owner,
        pos      = src,
        dir      = dir,
        speed    = BOLT_SPEED,
        model    = MDL_BOLT,
        scale    = 1.6,
        color    = Color(200, 240, 255, 255),
        dmgClass = "kinetic",
        physRadius = 3,
        mass     = 1,
        recoil   = RECOIL,
        life     = 5,
        trails   = {
            { col = Color(140, 200, 255), sw = 12, ew = 1, life = 0.15 },
            { col = Color(255, 255, 255), sw = 4,  ew = 0, life = 0.08 },
        },
        onHit = function(b, hitEnt, hitPos, hitNormal)
            if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
                D6_Wep.DirectDamage(owner, b, hitEnt, BOLT_DMG, DMG_BULLET, dir * 1500)
            end
            if IsValid(hitEnt) then
                local ef = EffectData(); ef:SetOrigin(hitPos); ef:SetNormal(hitNormal); ef:SetScale(1)
                util.Effect("AR2Impact", ef)
                b:EmitSound("weapons/crossbow/hit1.wav", 58, 130)
            end
        end,
    })

    local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(dir); ef:SetScale(0.6)
    util.Effect("MuzzleFlash", ef)
    owner:EmitSound("weapons/crossbow/bolt1.wav", 64, 118 + math.random(-6, 6))
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.8)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    function SWEP:DrawHUD()
        D6_Wep.DrawEnergyHUD(self:GetOwner(), "ПУЛЕМЁТ", Color(180, 220, 255))
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_mg.lua loaded")
