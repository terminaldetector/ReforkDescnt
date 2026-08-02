-- ═══════════════════════════════════════════════════════════
-- weapon_d6_smart_missile.lua — УМНАЯ-РАКЕТА
--
--   Cat B: LIMITED AMMO. Без расхода энергии.
--   HP-приоритет: ищет цель с наибольшим Health в 2400u.
--   Перезахват если цель уничтожена в полёте.
--   Угол поворота лучше чем ГСН (0.20 → 0.60).
--   Отличие: умнее ГСН (HP-приоритет + перезахват), медленнее Мега.
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "УМНАЯ-РАКЕТА"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 4
SWEP.SlotPos        = 5
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
SWEP.Primary.Ammo          = "d6_smart"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = 0
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

local MDL_MISSILE = "models/weapons/w_missile.mdl"
if SERVER then
    util.PrecacheModel(MDL_MISSILE)
    util.PrecacheSound("weapons/rpg/rocketfire1.wav")
    util.PrecacheSound("weapons/rpg/rocket_explode.wav")
end

local AMMO_TYPE    = "d6_smart"
local MAX_AMMO     = 4
local FIRE_RATE    = 4.0
local PROJ_SPEED   = 1600
local DIRECT_DMG   = 110
local SPLASH_DMG   = 60
local SPLASH_RAD   = 200
local RECOIL       = 150
local SCAN_RANGE   = 2400
local MISSILE_LIFE = 18
local MUZZLE       = { fwd = 32, rgt = 0, up = -8 }

local function FindBestTarget(mpos, owner)
    local bestEnt, bestHp = nil, -1
    for _, e in ipairs(ents.FindInSphere(mpos, SCAN_RANGE)) do
        if IsValid(e) and e ~= owner and (e:IsNPC() or e:IsPlayer()) then
            local hp = e:Health()
            if hp > bestHp then bestHp = hp; bestEnt = e end
        end
    end
    return bestEnt
end

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
    self._SmartMissile = nil
end

function SWEP:Deploy()  return true end

function SWEP:Holster()
    self._SmartMissile = nil
    if SERVER then self:SetNWBool("D6_SmartActive", false) end
    return true
end

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

    local wepRef = self

    local missile = D6_Wep.FireProjectile({
        owner      = owner,
        pos        = src,
        dir        = dir,
        speed      = PROJ_SPEED,
        model      = MDL_MISSILE,
        scale      = 1.4,
        color      = Color(100, 200, 255, 255),
        dmgClass   = "explosive",
        physRadius = 6,
        mass       = 5,
        recoil     = RECOIL,
        life       = MISSILE_LIFE,
        trails     = {
            { col = Color(100, 180, 255), sw = 36, ew = 4, life = 0.6 },
            { col = Color(200, 230, 255), sw = 12, ew = 0, life = 0.35 },
        },
        onHit = function(b, hitEnt, hitPos, hitNormal)
            timer.Remove("D6_SmartSteer_" .. b:EntIndex())
            if IsValid(wepRef) then
                wepRef._SmartMissile = nil
                wepRef:SetNWBool("D6_SmartActive", false)
            end

            if not hitPos then return end
            local pos = hitPos

            if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
                D6_Wep.DirectDamage(owner, b, hitEnt, DIRECT_DMG, DMG_BLAST,
                    (hitNormal or -dir) * 5500)
            end
            D6_Wep.SplashDamage(owner, b, pos, 0, SPLASH_DMG, SPLASH_RAD, DMG_BLAST, 4500, hitEnt)

            local ef = EffectData()
            ef:SetOrigin(pos); ef:SetScale(2.5); ef:SetMagnitude(SPLASH_RAD)
            util.Effect("HelicopterMegaBomb", ef)
            local ef2 = EffectData()
            ef2:SetOrigin(pos); ef2:SetScale(2)
            util.Effect("cball_explode", ef2)
            sound.Play("weapons/rpg/rocket_explode.wav", pos, 98, 90)
        end,
    })

    if not IsValid(missile) then return end

    self._SmartMissile = missile
    self:SetNWBool("D6_SmartActive", true)

    local idx = missile:EntIndex()
    local currentTarget = nil

    timer.Create("D6_SmartSteer_" .. idx, 0.06, 0, function()
        if not IsValid(missile) then timer.Remove("D6_SmartSteer_" .. idx); return end
        if not IsValid(owner) then return end

        local mph = missile:GetPhysicsObject()
        if not IsValid(mph) then return end

        local mpos = missile:GetPos()

        -- Reacquire if current target is dead or out of range
        if not IsValid(currentTarget) or
           mpos:DistToSqr(currentTarget:GetPos()) > SCAN_RANGE * SCAN_RANGE then
            currentTarget = FindBestTarget(mpos, owner)
        end

        if not IsValid(currentTarget) then return end

        local dist  = mpos:Distance(currentTarget:GetPos())
        local pred  = currentTarget:GetPos() + currentTarget:GetVelocity() * (dist / PROJ_SPEED * 0.7)
        -- Turn rate increases sharply at close range vs homing missile
        local steer = Lerp(1 - dist / SCAN_RANGE, 0.20, 0.60)
        local curVel  = mph:GetVelocity()
        local wantDir = (pred - mpos):GetNormalized()
        local newVel  = LerpVector(steer, curVel:GetNormalized(), wantDir):GetNormalized() * PROJ_SPEED
        mph:SetVelocity(newVel)
        missile:SetAngles(newVel:Angle())
    end)

    local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(dir)
    util.Effect("RPGShot", ef)
    owner:EmitSound("weapons/rpg/rocketfire1.wav", 88, 95)
end

-- Manual detonate (same as homing)
function SWEP:SecondaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if IsValid(self._SmartMissile) then
        local m   = self._SmartMissile
        local pos = m:GetPos()
        timer.Remove("D6_SmartSteer_" .. m:EntIndex())

        D6_Wep.SplashDamage(owner, m, pos, 0, SPLASH_DMG, SPLASH_RAD, DMG_BLAST, 4500)
        local ef = EffectData()
        ef:SetOrigin(pos); ef:SetScale(2.5); ef:SetMagnitude(SPLASH_RAD)
        util.Effect("HelicopterMegaBomb", ef)
        sound.Play("weapons/rpg/rocket_explode.wav", pos, 98, 90)

        m:Remove()
        self._SmartMissile = nil
        self:SetNWBool("D6_SmartActive", false)
        self:SetNextSecondaryFire(CurTime() + 0.5)
    else
        self:SetNextSecondaryFire(CurTime() + 0.3)
    end
end

if CLIENT then
    function SWEP:DrawHUD()
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        D6_Wep.DrawAmmoHUD(ply, "УМНАЯ-РАКЕТА", AMMO_TYPE, MAX_AMMO, Color(100, 200, 255))

        if self:GetNWBool("D6_SmartActive", false) then
            local sw, sh = ScrW(), ScrH()
            draw.SimpleText("● ЗАХВАТ", "DermaDefault", sw / 2, sh - 102,
                Color(100, 200, 255, 220 + math.floor(math.sin(CurTime() * 10) * 35)),
                TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
        end
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_smart_missile.lua loaded")
