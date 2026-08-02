-- ═══════════════════════════════════════════════════════════
-- weapon_d6_frag.lua — ФРАГ-ПУСКАТЕЛЬ (кассетный боеприпас)
--
--   Cat D: контроль толпы насыщением. Один снаряд (1400 u/s),
--   при детонации раскрывается в 8 осколков, разлетающихся и
--   взрывающихся по объёму — широкое площадное поражение роя.
--   Главный взрыв: 60 + 50 AoE в 200u; осколок: 40 AoE в 140u.
--
--   Фреймворк: d6_weapon_core (FireProjectile/SplashDamage),
--   d6_energy (TryConsume), d6_shield (explosive → explosive-резист).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "ФРАГ-ПУСКАТЕЛЬ"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 3
SWEP.SlotPos        = 2
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

local MDL_MAIN = "models/weapons/w_missile.mdl"
local MDL_FRAG = "models/weapons/w_crossbow_bolt.mdl"
if SERVER then
    util.PrecacheModel(MDL_MAIN)
    util.PrecacheModel(MDL_FRAG)
    util.PrecacheSound("weapons/grenade/tripmine_shoot1.wav")
    util.PrecacheSound("weapons/rpg/rocket_explode.wav")
end

local ENERGY_COST  = 24
local COOLDOWN     = 1.5
local PROJ_SPEED   = 1400
local MAIN_DIRECT  = 60
local MAIN_SPLASH  = 50
local MAIN_RAD     = 200
local FRAG_COUNT   = 8
local FRAG_SPEED   = 900
local FRAG_SPLASH  = 40
local FRAG_RAD     = 140
local FRAG_LIFE    = 0.7
local RECOIL       = 120
local MUZZLE       = { fwd = 30, rgt = 0, up = -8 }

function SWEP:Initialize() self:SetWeaponHoldType(self.HoldType) end
function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

local function SpawnFrags(owner, pos)
    local blast = D6_DAMAGE.explosive.dmgType
    for _ = 1, FRAG_COUNT do
        local rd = (Vector(0, 0, 0.25) + VectorRand()):GetNormalized()
        D6_Wep.FireProjectile({
            owner      = owner,
            pos        = pos + rd * 14,
            dir        = rd,
            speed      = FRAG_SPEED,
            model      = MDL_FRAG,
            scale      = 0.6,
            color      = Color(255, 170, 70, 255),
            dmgClass   = "explosive",
            physRadius = 3,
            mass       = 1,
            life       = FRAG_LIFE,
            trails     = {
                { col = Color(255, 160, 60), sw = 10, ew = 0, life = 0.2 },
            },
            onHit = function(b, hitEnt, hitPos, hitNormal)
                if not hitPos then return end
                D6_Wep.SplashDamage(owner, b, hitPos, 0, FRAG_SPLASH, FRAG_RAD, blast, 3000, nil)
                local ef = EffectData(); ef:SetOrigin(hitPos); ef:SetScale(1.5)
                util.Effect("cball_explode", ef)
            end,
        })
    end
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + COOLDOWN)

    local sa  = D6_Wep.ShootAng(owner)
    local dir = sa:Forward()
    local src = D6_Wep.Muzzle(owner, MUZZLE)
    local blast = D6_DAMAGE.explosive.dmgType

    D6_Wep.FireProjectile({
        owner      = owner,
        pos        = src,
        dir        = dir,
        speed      = PROJ_SPEED,
        model      = MDL_MAIN,
        scale      = 0.9,
        color      = Color(255, 150, 50, 255),
        dmgClass   = "explosive",
        physRadius = 5,
        mass       = 3,
        recoil     = RECOIL,
        life       = 4,
        trails     = {
            { col = Color(255, 150, 50),  sw = 26, ew = 4, life = 0.4 },
            { col = Color(255, 220, 160), sw = 10, ew = 0, life = 0.25 },
        },
        onHit = function(b, hitEnt, hitPos, hitNormal)
            if not hitPos then return end
            local pos = hitPos
            if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
                D6_Wep.DirectDamage(owner, b, hitEnt, MAIN_DIRECT, blast, (hitNormal or -dir) * 4000)
            end
            D6_Wep.SplashDamage(owner, b, pos, 0, MAIN_SPLASH, MAIN_RAD, blast, 4000, hitEnt)

            local ef = EffectData(); ef:SetOrigin(pos); ef:SetScale(2.5); ef:SetMagnitude(MAIN_RAD)
            util.Effect("HelicopterMegaBomb", ef)
            sound.Play("weapons/rpg/rocket_explode.wav", pos, 95, 100)

            -- Cluster opens into saturating fragments.
            SpawnFrags(owner, pos)
        end,
    })

    local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(dir)
    util.Effect("RPGShot", ef)
    owner:EmitSound("weapons/grenade/tripmine_shoot1.wav", 85, 95)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.6)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    function SWEP:DrawHUD()
        D6_Wep.DrawReadyHUD(self:GetOwner(), self, "ФРАГ-ПУСКАТЕЛЬ", Color(255, 160, 60), COOLDOWN)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_frag.lua loaded")
