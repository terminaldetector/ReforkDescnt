-- ═══════════════════════════════════════════════════════════
-- weapon_d6_reactor.lua — СБРОС РЕАКТОРА (ультимейт, паника)
--
--   Cat G: НЕ балансируется как обычное оружие. Сбрасывает ВЕСЬ
--   текущий энергорезерв в один всенаправленный взрыв вокруг корабля.
--   Чем больше энергии — тем мощнее: радиус 300→900, урон 150→600
--   (exotic). После — ноль энергии (тяжёлый риск). КД 15 с.
--   Кинематографичный «ластик» при окружении.
--
--   Фреймворк: d6_weapon_core (SplashDamage), d6_energy (Get/TryConsume
--   — расходует весь резерв), d6_shield (exotic → energy-резист),
--   d6_weapon_registry (Ultimate).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "СБРОС РЕАКТОРА"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 1
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

if SERVER and not _D6_REACTOR_NET then
    util.AddNetworkString("D6_Reactor")
    _D6_REACTOR_NET = true
    util.PrecacheSound("ambient/explosions/explode_9.wav")
    util.PrecacheSound("npc/strider/strider_die1.wav")
end

local ENERGY_MIN = 30
local COOLDOWN   = 15
local MIN_RADIUS = 300
local MAX_RADIUS = 900
local MIN_DMG    = 150
local MAX_DMG    = 600

function SWEP:Initialize() self:SetWeaponHoldType(self.HoldType) end
function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    local cur = D6_Energy.Get(owner)
    if cur < ENERGY_MIN then
        owner:EmitSound("buttons/button10.wav", 65, 90); return
    end

    -- Discharge the ENTIRE reserve — power scales with what was banked.
    D6_Energy.TryConsume(owner, "weapons", cur)
    self:SetNextPrimaryFire(CurTime() + COOLDOWN)

    local frac   = math.Clamp(cur / 100, 0, 1)
    local radius = Lerp(frac, MIN_RADIUS, MAX_RADIUS)
    local dmg    = Lerp(frac, MIN_DMG, MAX_DMG)
    local center = owner:WorldSpaceCenter()
    local exo    = D6_DAMAGE.exotic.dmgType

    D6_Wep.SplashDamage(owner, owner, center, dmg * 0.4, dmg * 0.6, radius, exo, 14000, owner)

    local ef = EffectData(); ef:SetOrigin(center); ef:SetScale(10); ef:SetMagnitude(radius)
    util.Effect("HelicopterMegaBomb", ef)
    local ef2 = EffectData(); ef2:SetOrigin(center); ef2:SetScale(8)
    util.Effect("Explosion", ef2)
    util.ScreenShake(center, 25, 25, 1.5, radius * 2.5)
    sound.Play("ambient/explosions/explode_9.wav", center, 130, 60)
    sound.Play("npc/strider/strider_die1.wav", center, 120, 80)

    net.Start("D6_Reactor")
        net.WriteVector(center)
        net.WriteFloat(radius)
    net.Broadcast()
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.6)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    local RING_MAT = Material("effects/select_ring")

    net.Receive("D6_Reactor", function()
        local center = net.ReadVector()
        local radius = net.ReadFloat()
        local key    = "D6_ReactorVis_" .. CurTime() .. "_" .. math.random(99999)
        local born   = CurTime()
        local dur    = 0.6

        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            local age = CurTime() - born
            if age >= dur then hook.Remove("PostDrawTranslucentRenderables", key); return end
            local frac = age / dur
            local r    = radius * frac
            local a    = (1 - frac) * 240
            render.SetMaterial(RING_MAT)
            render.DrawSphere(center, r, 28, 28, Color(255, 140, 40, a * 0.6))
            render.DrawWireframeSphere(center, r, 22, 22, Color(255, 220, 120, a), false)
        end)
    end)

    function SWEP:DrawHUD()
        D6_Wep.DrawReadyHUD(self:GetOwner(), self, "СБРОС РЕАКТОРА", Color(255, 140, 40), COOLDOWN)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_reactor.lua loaded")
