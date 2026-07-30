-- ═══════════════════════════════════════════════════════════
-- weapon_d6_homing.lua — ГСН-РАКЕТА (самонаводящаяся ракета)
--
--   Cat B: LIMITED AMMO. Без расхода энергии.
--   Один снаряд. Упреждающее наведение каждые 0.04 с.
--   Скорость поворота растёт при сближении (0.15 → 0.45).
--   Время жизни 15 с. ПКМ = ручная детонация.
--   Отличие от rockets-mode2: один точный перехватчик против 6 роевых.
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "ГСН-РАКЕТА"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 4
SWEP.SlotPos        = 4
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
SWEP.Primary.Ammo          = "d6_homing"
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

local AMMO_TYPE   = "d6_homing"
local MAX_AMMO    = 8
local FIRE_RATE   = 3.5
local PROJ_SPEED  = 1400
local DIRECT_DMG  = 90
local SPLASH_DMG  = 40
local SPLASH_RAD  = 160
local RECOIL      = 120
local SCAN_RANGE  = 2200
local MISSILE_LIFE = 15
local MUZZLE      = { fwd = 30, rgt = 0, up = -8 }

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
    self._HomingMissile = nil
end

function SWEP:Deploy()  return true end

function SWEP:Holster()
    self._HomingMissile = nil
    if SERVER then self:SetNWBool("D6_HomingActive", false) end
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

    local sa   = D6_Wep.ShootAng(owner)
    local dir  = sa:Forward()
    local src  = D6_Wep.Muzzle(owner, MUZZLE)

    local wepRef = self

    local missile = D6_Wep.FireProjectile({
        owner      = owner,
        pos        = src,
        dir        = dir,
        speed      = PROJ_SPEED,
        model      = MDL_MISSILE,
        scale      = 1.2,
        color      = Color(255, 240, 100, 255),
        dmgClass   = "explosive",
        physRadius = 6,
        mass       = 5,
        recoil     = RECOIL,
        life       = MISSILE_LIFE,
        trails     = {
            { col = Color(255, 220, 80),  sw = 28, ew = 3, life = 0.5 },
            { col = Color(255, 255, 200), sw = 10, ew = 0, life = 0.3 },
        },
        onHit = function(b, hitEnt, hitPos, hitNormal)
            timer.Remove("D6_HomingSteer_" .. b:EntIndex())
            if IsValid(wepRef) then
                wepRef._HomingMissile = nil
                wepRef:SetNWBool("D6_HomingActive", false)
            end

            if not hitPos then return end  -- lifetime expiry, no explosion
            local pos = hitPos

            if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
                D6_Wep.DirectDamage(owner, b, hitEnt, DIRECT_DMG, DMG_BLAST,
                    (hitNormal or -dir) * 5000)
            end
            D6_Wep.SplashDamage(owner, b, pos, 0, SPLASH_DMG, SPLASH_RAD, DMG_BLAST, 4000, hitEnt)

            local ef = EffectData()
            ef:SetOrigin(pos); ef:SetScale(2); ef:SetMagnitude(SPLASH_RAD)
            util.Effect("HelicopterMegaBomb", ef)
            sound.Play("weapons/rpg/rocket_explode.wav", pos, 95, 105)
        end,
    })

    if not IsValid(missile) then return end

    self._HomingMissile = missile
    self:SetNWBool("D6_HomingActive", true)

    local idx = missile:EntIndex()
    timer.Create("D6_HomingSteer_" .. idx, 0.04, 0, function()
        if not IsValid(missile) then timer.Remove("D6_HomingSteer_" .. idx); return end
        if not IsValid(owner) then return end

        local mph = missile:GetPhysicsObject()
        if not IsValid(mph) then return end

        local mpos = missile:GetPos()
        local target, bestDistSqr = nil, SCAN_RANGE * SCAN_RANGE

        for _, e in ipairs(ents.FindInSphere(mpos, SCAN_RANGE)) do
            if IsValid(e) and (e:IsNPC() or e:IsPlayer()) and e ~= owner then
                local d = mpos:DistToSqr(e:GetPos())
                if d < bestDistSqr then bestDistSqr = d; target = e end
            end
        end

        if not IsValid(target) then return end

        local dist  = math.sqrt(bestDistSqr)
        local pred  = target:GetPos() + target:GetVelocity() * (dist / PROJ_SPEED * 0.8)
        local steer = Lerp(1 - dist / SCAN_RANGE, 0.15, 0.45)
        local curVel = mph:GetVelocity()
        local wantDir = (pred - mpos):GetNormalized()
        local newVel  = LerpVector(steer, curVel:GetNormalized(), wantDir):GetNormalized() * PROJ_SPEED
        mph:SetVelocity(newVel)
        missile:SetAngles(newVel:Angle())
    end)

    local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(dir)
    util.Effect("RPGShot", ef)
    owner:EmitSound("weapons/rpg/rocketfire1.wav", 85, 100)
end

-- Manual detonate: explodes the live missile in place.
function SWEP:SecondaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if IsValid(self._HomingMissile) then
        local m   = self._HomingMissile
        local pos = m:GetPos()
        timer.Remove("D6_HomingSteer_" .. m:EntIndex())

        D6_Wep.SplashDamage(owner, m, pos, 0, SPLASH_DMG, SPLASH_RAD, DMG_BLAST, 4000)
        local ef = EffectData()
        ef:SetOrigin(pos); ef:SetScale(2); ef:SetMagnitude(SPLASH_RAD)
        util.Effect("HelicopterMegaBomb", ef)
        sound.Play("weapons/rpg/rocket_explode.wav", pos, 95, 105)

        m:Remove()
        self._HomingMissile = nil
        self:SetNWBool("D6_HomingActive", false)
        self:SetNextSecondaryFire(CurTime() + 0.5)
    else
        self:SetNextSecondaryFire(CurTime() + 0.3)
        D6_Wep.DelegateSecondary(owner)
    end
end

if CLIENT then
    function SWEP:DrawHUD()
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        D6_Wep.DrawAmmoHUD(ply, "ГСН-РАКЕТА", AMMO_TYPE, MAX_AMMO, Color(255, 220, 80))

        if self:GetNWBool("D6_HomingActive", false) then
            local sw, sh = ScrW(), ScrH()
            draw.SimpleText("● АКТИВНА", "DermaDefault", sw / 2, sh - 102,
                Color(255, 80, 80, 220 + math.floor(math.sin(CurTime() * 8) * 35)),
                TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
        end
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_homing.lua loaded")
