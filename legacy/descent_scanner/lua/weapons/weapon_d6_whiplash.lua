-- ═══════════════════════════════════════════════════════════
-- weapon_d6_whiplash.lua — ХЛЫСТ (продвинутые варианты крюка)
--
--   Cat F: оружие движения, два варианта одного троса.
--   ЛКМ «рывок»: трос в точку прицела, корабль выстреливается туда
--     (3200 u/с) и при прибытии бьёт слэмом 80 в 160u (кинетика).
--   ПКМ «протяжка»: трос в цель, враг/проп вырывается к кораблю.
--   Высокий риск — рывок может занести в гущу или препятствие.
--
--   Фреймворк: d6_weapon_core (Muzzle/SplashDamage/DirectDamage),
--   d6_energy (TryConsume), d6_shield (kinetic → kinetic-резист).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "ХЛЫСТ"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 6
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
SWEP.Primary.Automatic     = false
SWEP.Primary.Ammo          = "none"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

if SERVER and not _D6_WHIPLASH_NET then
    util.AddNetworkString("D6_WhipBeam")
    _D6_WHIPLASH_NET = true
    util.PrecacheSound("weapons/crossbow/fire1.wav")
    util.PrecacheSound("weapons/physcannon/superphys_launch2.wav")
end

local ZIP_ENERGY  = 20
local ZIP_CD      = 3.0
local ZIP_RANGE   = 3000
local ZIP_SPEED   = 3200
local SLAM_RAD    = 160
local SLAM_DMG    = 80
local ZIP_TIMEOUT = 1.5

local YANK_ENERGY = 25
local YANK_CD     = 2.5
local YANK_RANGE  = 2500
local YANK_SPEED  = 2600

function SWEP:Initialize() self:SetWeaponHoldType(self.HoldType) end
function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

local function WhipBeam(src, dst, warm)
    net.Start("D6_WhipBeam")
        net.WriteVector(src)
        net.WriteVector(dst)
        net.WriteBool(warm and true or false)
    net.Broadcast()
end

-- ЛКМ: рывок корабля к точке прицела + слэм при прибытии.
function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ZIP_ENERGY) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + ZIP_CD)

    local dir    = D6_Wep.ShootAng(owner):Forward()
    local center = owner:WorldSpaceCenter()
    local src    = owner:GetShootPos()

    local tr = util.TraceLine({ start = center, endpos = center + dir * ZIP_RANGE,
        filter = owner, mask = MASK_SOLID })
    local target = tr.Hit and (tr.HitPos - dir * 50) or (center + dir * ZIP_RANGE)

    WhipBeam(src, tr.HitPos, false)
    owner:EmitSound("weapons/crossbow/fire1.wav", 80, 95)

    local kin   = D6_DAMAGE.kinetic.dmgType
    local tid   = "D6_Zip_" .. owner:EntIndex()
    local ends  = CurTime() + ZIP_TIMEOUT
    timer.Create(tid, 0.03, 0, function()
        if not IsValid(owner) then timer.Remove(tid); return end
        local cpos = owner:WorldSpaceCenter()
        local toT  = target - cpos
        local d    = toT:Length()
        if d < SLAM_RAD or CurTime() >= ends then
            timer.Remove(tid)
            D6_Wep.SplashDamage(owner, owner, cpos, 0, SLAM_DMG, SLAM_RAD, kin, 5000, owner)
            local ef = EffectData(); ef:SetOrigin(cpos); ef:SetScale(2); ef:SetMagnitude(SLAM_RAD)
            util.Effect("ThumperDust", ef)
            sound.Play("weapons/physcannon/superphys_launch2.wav", cpos, 90, 100)
            return
        end
        owner.D6Vel = toT:GetNormalized() * ZIP_SPEED
    end)
end

-- ПКМ: протяжка — вырвать врага/проп к кораблю.
function SWEP:SecondaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", YANK_ENERGY) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end
    self:SetNextSecondaryFire(CurTime() + YANK_CD)

    local dir    = D6_Wep.ShootAng(owner):Forward()
    local center = owner:WorldSpaceCenter()
    local src    = owner:GetShootPos()

    local tr = util.TraceLine({ start = center, endpos = center + dir * YANK_RANGE,
        filter = owner, mask = MASK_SHOT })

    WhipBeam(src, tr.HitPos, true)

    local e = tr.Entity
    if not IsValid(e) then
        owner:EmitSound("common/wpn_denyselect.wav", 60, 110); return
    end

    local toOwner = (center - e:WorldSpaceCenter()):GetNormalized()
    if e:IsPlayer() then
        e.D6Vel = toOwner * YANK_SPEED
    else
        local ph = e:GetPhysicsObject()
        if IsValid(ph) then
            ph:EnableGravity(false)
            ph:SetVelocity(toOwner * YANK_SPEED)
            ph:Wake()
            timer.Simple(2.5, function()
                if IsValid(e) then local p = e:GetPhysicsObject(); if IsValid(p) then p:EnableGravity(true) end end
            end)
        end
    end

    owner:EmitSound("weapons/physcannon/superphys_launch2.wav", 78, 120)
end

if CLIENT then
    local BEAM_MAT = Material("cable/rope")

    net.Receive("D6_WhipBeam", function()
        local src  = net.ReadVector()
        local dst  = net.ReadVector()
        local warm = net.ReadBool()
        local key  = "D6_WhipVis_" .. CurTime() .. "_" .. math.random(99999)
        local born = CurTime()
        local dur  = 0.22
        local col  = warm and Color(255, 160, 60) or Color(160, 230, 255)
        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            local age = CurTime() - born
            if age >= dur then hook.Remove("PostDrawTranslucentRenderables", key); return end
            local a = (1 - age / dur) * 255
            render.SetMaterial(BEAM_MAT)
            render.DrawBeam(src, dst, 4, 0, 1, Color(col.r, col.g, col.b, a))
        end)
    end)

    function SWEP:DrawHUD()
        D6_Wep.DrawReadyHUD(self:GetOwner(), self, "ХЛЫСТ", Color(160, 230, 255), ZIP_CD)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_whiplash.lua loaded")
