-- ═══════════════════════════════════════════════════════════
-- weapon_d6_vulcan.lua — ВУЛКАН (кинетический пулемёт с раскруткой)
--
--   Spin-up: 0.40s от холодного до пикового темпа.
--   Spin-down: 0.30s после отпускания.
--   Предраскрутка перед атакой — ключевой навык.
--   Два ствола чередуются (L/R). Фиксированный разброс 1.2°.
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "ВУЛКАН"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 2
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
SWEP.Primary.DefaultClip   = -1
SWEP.Primary.Automatic     = true
SWEP.Primary.Ammo          = "none"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

local MDL_BOLT = "models/weapons/w_crossbow_bolt.mdl"
if SERVER then
    util.PrecacheModel(MDL_BOLT)
    util.PrecacheSound("weapons/crossbow/bolt1.wav")
end

local ENERGY_COST = 2
local BOLT_SPEED  = 4800
local BOLT_DMG    = 15
local RECOIL      = 8
local SPREAD_DEG  = 1.2
local SPIN_UP     = 0.40
local SPIN_DOWN   = 0.30
local MAX_RATE    = 0.065
local MIN_RATE    = 0.35

local MUZZLES = {
    { fwd = 28, rgt = -18, up = -8 },
    { fwd = 28, rgt =  18, up = -8 },
}

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
    self._SpinLevel = 0
    self._NextFire  = 0
    self._Barrel    = 1
end

function SWEP:Deploy()  return true end

function SWEP:Holster()
    self._SpinLevel = 0
    if SERVER then self:SetNWFloat("D6_VulcanSpin", 0) end
    return true
end

-- Spin-up/down and firing live in Think() for variable fire rate.
function SWEP:Think()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    local ct      = CurTime()
    local ft      = FrameTime()
    local holding = owner:KeyDown(IN_ATTACK)

    if holding then
        self._SpinLevel = math.min(1, (self._SpinLevel or 0) + ft / SPIN_UP)
    else
        self._SpinLevel = math.max(0, (self._SpinLevel or 0) - ft / SPIN_DOWN)
    end
    self:SetNWFloat("D6_VulcanSpin", self._SpinLevel)

    if holding and self._SpinLevel > 0.05 and ct >= (self._NextFire or 0) then
        if D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
            local interval = Lerp(self._SpinLevel, MIN_RATE, MAX_RATE)
            self._NextFire = ct + interval
            self:SetNextPrimaryFire(ct + interval)
            self:_DoFire(owner)
        else
            owner:EmitSound("buttons/button10.wav", 60, 100)
            self._NextFire = ct + 0.1
        end
    end
end

function SWEP:_DoFire(owner)
    local sa  = D6_Wep.ShootAng(owner)
    local fwd = sa:Forward()
    local tan = math.tan(math.rad(SPREAD_DEG))
    local dir = (fwd
               + sa:Right() * math.Rand(-1, 1) * tan
               + sa:Up()    * math.Rand(-1, 1) * tan):GetNormalized()

    self._Barrel = self._Barrel == 1 and 2 or 1
    local src = D6_Wep.Muzzle(owner, MUZZLES[self._Barrel])

    D6_Wep.FireProjectile({
        owner      = owner,
        pos        = src,
        dir        = dir,
        speed      = BOLT_SPEED,
        model      = MDL_BOLT,
        scale      = 1.4,
        color      = Color(180, 210, 255, 255),
        dmgClass   = "kinetic",
        physRadius = 3,
        mass       = 1,
        recoil     = RECOIL,
        life       = 4,
        trails     = {
            { col = Color(140, 190, 255), sw = 14, ew = 0, life = 0.18 },
            { col = Color(220, 240, 255), sw = 5,  ew = 0, life = 0.10 },
        },
        onHit = function(b, hitEnt, hitPos, hitNormal)
            if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
                D6_Wep.DirectDamage(owner, b, hitEnt, BOLT_DMG, DMG_BULLET, dir * 2000)
            end
            local ef = EffectData()
            ef:SetOrigin(hitPos or b:GetPos())
            ef:SetNormal(hitNormal or vector_origin)
            ef:SetScale(0.5)
            util.Effect("AR2Impact", ef)
        end,
    })

    local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(dir); ef:SetScale(0.5)
    util.Effect("MuzzleFlash", ef)
    owner:EmitSound("weapons/crossbow/bolt1.wav", 61, 140 + math.random(-5, 5))
end

function SWEP:PrimaryAttack()
    -- Think() handles all firing; block weapon switch on click.
    self:SetNextPrimaryFire(CurTime() + 0.05)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.8)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    function SWEP:DrawHUD()
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        D6_Wep.DrawEnergyHUD(ply, "ВУЛКАН", Color(140, 190, 255))
        local spin = self:GetNWFloat("D6_VulcanSpin", 0)
        local sw, sh = ScrW(), ScrH()
        local bw, bh = 140, 4
        local bx = sw / 2 - bw / 2
        local by = sh - 62
        surface.SetDrawColor(20, 20, 40, 180)
        surface.DrawRect(bx - 1, by - 1, bw + 2, bh + 2)
        surface.SetDrawColor(60, 140, 255, 200)
        surface.DrawRect(bx, by, bw * spin, bh)
        if spin < 0.1 then
            draw.SimpleText("РАСКРУТКА", "DermaDefault", sw / 2, by + bh + 4,
                Color(100, 140, 220, 180), TEXT_ALIGN_CENTER, TEXT_ALIGN_TOP)
        end
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_vulcan.lua loaded")
