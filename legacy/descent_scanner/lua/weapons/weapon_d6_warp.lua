-- ═══════════════════════════════════════════════════════════
-- weapon_d6_warp.lua — БОЕВОЙ ВАРП (модуль рывка-блинка)
--
--   Cat F: оружие движения. Короткий блинк на 700u по прицелу
--   (проверка корпусом). Оставляет разрыв тёмной энергии в точке
--   входа И выхода: 100 + AoE в 200u (exotic). И атака, и уход —
--   репозиционирование сквозь строй с уроном на обоих концах.
--   КД 2.5 с, 30 энергии.
--
--   Фреймворк: d6_weapon_core (Muzzle/SplashDamage), d6_energy
--   (TryConsume), d6_shield (exotic → energy-резист).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "БОЕВОЙ ВАРП"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 6
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

if SERVER and not _D6_WARP_NET then
    util.AddNetworkString("D6_WarpBlink")
    _D6_WARP_NET = true
    util.PrecacheSound("ambient/machines/teleport1.wav")
    util.PrecacheSound("ambient/levels/labs/electric_explosion1.wav")
end

local ENERGY_COST  = 30
local COOLDOWN     = 2.5
local BLINK_DIST   = 700
local RUPTURE_DMG  = 100
local RUPTURE_RAD  = 200
local HULL_MIN     = Vector(-18, -18, -18)
local HULL_MAX     = Vector(18, 18, 18)

function SWEP:Initialize() self:SetWeaponHoldType(self.HoldType) end
function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

local function Rupture(owner, pos)
    D6_Wep.SplashDamage(owner, owner, pos, 0, RUPTURE_DMG, RUPTURE_RAD,
        D6_DAMAGE.exotic.dmgType, 4000, owner)
    local ef = EffectData(); ef:SetOrigin(pos); ef:SetScale(2.5); ef:SetMagnitude(RUPTURE_RAD)
    util.Effect("cball_explode", ef)
    sound.Play("ambient/levels/labs/electric_explosion1.wav", pos, 95, 95)
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + COOLDOWN)

    local dir    = D6_Wep.ShootAng(owner):Forward()
    local center = owner:WorldSpaceCenter()
    local offset = owner:GetPos() - center

    local tr = util.TraceLine({ start = center, endpos = center + dir * BLINK_DIST,
        filter = owner, mask = MASK_SOLID })
    local dest = tr.Hit and (tr.HitPos - dir * 45) or (center + dir * BLINK_DIST)

    local hull = util.TraceHull({ start = center, endpos = dest, filter = owner,
        mask = MASK_SOLID, mins = HULL_MIN, maxs = HULL_MAX })
    if hull.Hit then dest = hull.HitPos - dir * 35 end

    -- Rupture at both ends; carry momentum forward.
    Rupture(owner, center)
    owner:SetPos(dest + offset)
    owner.D6Vel = dir * 800
    Rupture(owner, dest)

    net.Start("D6_WarpBlink")
        net.WriteVector(center)
        net.WriteVector(dest)
    net.Broadcast()

    owner:EmitSound("ambient/machines/teleport1.wav", 95, 120)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.6)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    local BEAM_MAT = Material("cable/xbeam")

    net.Receive("D6_WarpBlink", function()
        local src  = net.ReadVector()
        local dst  = net.ReadVector()
        local key  = "D6_WarpStreak_" .. CurTime() .. "_" .. math.random(99999)
        local born = CurTime()
        local dur  = 0.28
        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            local age = CurTime() - born
            if age >= dur then hook.Remove("PostDrawTranslucentRenderables", key); return end
            local a = (1 - age / dur) * 255
            render.SetMaterial(BEAM_MAT)
            render.DrawBeam(src, dst, 8, 0, 1, Color(150, 60, 220, a))
            render.DrawBeam(src, dst, 2, 0, 1, Color(220, 180, 255, a))
        end)
    end)

    function SWEP:DrawHUD()
        D6_Wep.DrawReadyHUD(self:GetOwner(), self, "БОЕВОЙ ВАРП", Color(170, 90, 235), COOLDOWN)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_warp.lua loaded")
