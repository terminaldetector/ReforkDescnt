-- ═══════════════════════════════════════════════════════════
-- weapon_d6_pulse.lua — ПУЛЬСАР, 4-ствольный пулемёт
-- Рендер моделей — в d6_wepview.lua (центральный хук).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName       = "ПУЛЬСАР"
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

local ENERGY_MAX   = 100
local ENERGY_COST  = 1
local ENERGY_REGEN = 8

local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

-- Позиции дул — синхронизированы с d6_wepview.lua:
-- ряд 1 — нижние углы (±46, −20), ряд 2 — верхние края (±50, +16)
local MUZZLES = {
    { fwd=50, rgt=-46, up=-20 },
    { fwd=50, rgt= 46, up=-20 },
    { fwd=50, rgt=-50, up= 16 },
    { fwd=50, rgt= 50, up= 16 },
}

local function MuzzleWorld(ply, off)
    local ang = ShootAng(ply)
    return ply:GetShootPos()
        + ang:Forward() * off.fwd
        + ang:Right()   * off.rgt
        + ang:Up()      * off.up
end

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
end

function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

function SWEP:Think()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end
    local e = owner:GetNWFloat("D6_WepEnergy", ENERGY_MAX)
    if e < ENERGY_MAX then
        owner:SetNWFloat("D6_WepEnergy", math.min(ENERGY_MAX, e + ENERGY_REGEN * FrameTime()))
    end
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    local energy = owner:GetNWFloat("D6_WepEnergy", ENERGY_MAX)
    if energy < ENERGY_COST then
        owner:EmitSound("buttons/button10.wav", 65, 100); return
    end
    owner:SetNWFloat("D6_WepEnergy", energy - ENERGY_COST)
    self:SetNextPrimaryFire(CurTime() + 0.08)

    local sa  = ShootAng(owner)
    local fwd = sa:Forward()
    local rgt = sa:Right()
    local up  = sa:Up()

    for _, off in ipairs(MUZZLES) do
        local src = MuzzleWorld(owner, off)
        local dir = (fwd + rgt * math.Rand(-0.02, 0.02) + up * math.Rand(-0.02, 0.02)):GetNormalized()
        owner:FireBullets({
            Src=src, Dir=dir, Damage=8, Distance=8000,
            Spread=Vector(0.02, 0.02, 0), Tracer=1, TracerName="Tracer",
            Force=200, Num=1, AmmoType="Pistol", AttackPos=src,
        })
        local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(dir); ef:SetScale(1.2)
        util.Effect("MuzzleFlash", ef)
        local ef2 = EffectData(); ef2:SetOrigin(src); ef2:SetNormal(dir); ef2:SetScale(0.5)
        util.Effect("ManhackSparks", ef2)
    end
    owner:EmitSound("weapons/airboat/airboat_gun_energy1.wav", 65, 115 + math.random(-6, 6))
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end
    self:SetNextSecondaryFire(CurTime() + 0.8)
    local rkt = owner:GetWeapon("weapon_d6_rockets")
    if IsValid(rkt) then rkt:PrimaryAttack() end
end

if CLIENT then
    function SWEP:DrawHUD()
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        local energy = ply:GetNWFloat("D6_WepEnergy", ENERGY_MAX)
        local sw, sh = ScrW(), ScrH()
        local bw, bh = 140, 6
        local bx, by = sw/2 - bw/2, sh - 72
        surface.SetDrawColor(30, 30, 30, 180); surface.DrawRect(bx-1, by-1, bw+2, bh+2)
        local col = energy > 30 and Color(0,180,255) or Color(255,60,60)
        surface.SetDrawColor(col.r, col.g, col.b, 200)
        surface.DrawRect(bx, by, bw * (energy/ENERGY_MAX), bh)
        draw.SimpleText("ПУЛЬСАР  ⚡ "..math.floor(energy), "DermaDefault",
            sw/2, sh-90, Color(180,220,255), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_pulse.lua loaded")
