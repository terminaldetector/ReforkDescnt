-- ═══════════════════════════════════════════════════════════
-- weapon_d6_shockwave.lua — ЭНЕРГОВОЛНА (радиальная очистка)
--
--   Cat D: расчистка роя вокруг корабля. Мгновенный радиальный
--   импульс в 700u от корпуса: до 100 урона в эпицентре (спад к
--   краю) + мощный отброс врагов и пропов. Кнопка паники ближнего
--   боя — создаёт «дыхание» в окружении, отрицание объёма.
--
--   Фреймворк: d6_weapon_core (SplashDamage), d6_energy (TryConsume),
--   d6_shield (energy → energy-резист), d6_weapon_registry (Anti-Swarm).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "ЭНЕРГОВОЛНА"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 3
SWEP.SlotPos        = 3
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

if SERVER and not _D6_SHOCKWAVE_NET then
    util.AddNetworkString("D6_Shockwave")
    _D6_SHOCKWAVE_NET = true
    util.PrecacheSound("ambient/energy/whiteflash.wav")
    util.PrecacheSound("ambient/explosions/explode_7.wav")
end

local ENERGY_COST = 40
local COOLDOWN    = 3.0
local RADIUS      = 700
local BASE_DMG    = 20
local SPLASH_DMG  = 80
local PUSH_FORCE  = 9000

function SWEP:Initialize() self:SetWeaponHoldType(self.HoldType) end
function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + COOLDOWN)

    local center = owner:WorldSpaceCenter()
    local energy = D6_DAMAGE.energy.dmgType

    -- Radial damage with falloff + strong knockback (SplashDamage applies
    -- outward force already; props get an extra physics shove).
    D6_Wep.SplashDamage(owner, owner, center, BASE_DMG, SPLASH_DMG, RADIUS, energy, PUSH_FORCE, owner)

    for _, e in ipairs(ents.FindInSphere(center, RADIUS)) do
        if IsValid(e) and e:GetClass() == "prop_physics" and e ~= owner then
            local ph = e:GetPhysicsObject()
            if IsValid(ph) then
                local away = (e:GetPos() - center):GetNormalized()
                local fall = math.max(0.2, 1 - e:GetPos():Distance(center) / RADIUS)
                ph:ApplyForceCenter(away * ph:GetMass() * 1400 * fall)
            end
        end
    end

    net.Start("D6_Shockwave")
        net.WriteVector(center)
        net.WriteFloat(RADIUS)
    net.Broadcast()

    local ef = EffectData(); ef:SetOrigin(center); ef:SetScale(5); ef:SetMagnitude(RADIUS)
    util.Effect("cball_explode", ef)
    sound.Play("ambient/explosions/explode_7.wav", center, 120, 95)
    owner:EmitSound("ambient/energy/whiteflash.wav", 100, 110)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.6)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    local RING_MAT = Material("effects/select_ring")

    net.Receive("D6_Shockwave", function()
        local center = net.ReadVector()
        local radius = net.ReadFloat()
        local key    = "D6_ShockRing_" .. CurTime() .. "_" .. math.random(100000)
        local born   = CurTime()
        local dur    = 0.45

        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            local age = CurTime() - born
            if age >= dur then hook.Remove("PostDrawTranslucentRenderables", key); return end
            local frac = age / dur
            local r    = radius * frac
            local a    = (1 - frac) * 220
            render.SetMaterial(RING_MAT)
            render.DrawSphere(center, r,  24, 24, Color(120, 220, 255, a * 0.5))
            render.DrawWireframeSphere(center, r, 18, 18, Color(160, 240, 255, a), false)
        end)
    end)

    function SWEP:DrawHUD()
        D6_Wep.DrawReadyHUD(self:GetOwner(), self, "ЭНЕРГОВОЛНА", Color(120, 220, 255), COOLDOWN)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_shockwave.lua loaded")
