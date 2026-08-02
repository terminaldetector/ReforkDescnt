-- ═══════════════════════════════════════════════════════════
-- weapon_d6_telefrag.lua — ТЕЛЕФРАГ (агрессивная переброска)
--
--   Cat F: оружие движения. Мгновенный прыжок по прицелу до 4000u
--   (с проверкой объёма корпусом — не в стену). Любой враг в 140u
--   точки прибытия мгновенно потрошится (1000 exotic). Высокий риск:
--   ты обязуешься к точке — можешь влететь в гущу или в пустоту.
--   Несёт импульс вперёд при выходе. КД 5 с, 50 энергии.
--
--   Фреймворк: d6_weapon_core (Muzzle/DirectDamage), d6_energy
--   (TryConsume), d6_shield (exotic → energy-резист).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "ТЕЛЕФРАГ"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 6
SWEP.SlotPos        = 0
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

if SERVER and not _D6_TELEFRAG_NET then
    util.AddNetworkString("D6_TeleBlink")
    _D6_TELEFRAG_NET = true
    util.PrecacheSound("ambient/machines/teleport4.wav")
end

local ENERGY_COST = 50
local COOLDOWN    = 5.0
local RANGE       = 4000
local FRAG_RAD    = 140
local FRAG_DMG    = 1000
local HULL_MIN    = Vector(-18, -18, -18)
local HULL_MAX    = Vector(18, 18, 18)

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

    local dir    = D6_Wep.ShootAng(owner):Forward()
    local center = owner:WorldSpaceCenter()
    local offset = owner:GetPos() - center

    -- Find clear destination: stop short of any solid, then hull-verify.
    local tr = util.TraceLine({ start = center, endpos = center + dir * RANGE,
        filter = owner, mask = MASK_SOLID })
    local dest = tr.Hit and (tr.HitPos - dir * 60) or (center + dir * RANGE)

    local hull = util.TraceHull({ start = center, endpos = dest, filter = owner,
        mask = MASK_SOLID, mins = HULL_MIN, maxs = HULL_MAX })
    if hull.Hit then dest = hull.HitPos - dir * 40 end

    -- Telefrag everything at the arrival point.
    local exo = D6_DAMAGE.exotic.dmgType
    for _, e in ipairs(ents.FindInSphere(dest, FRAG_RAD)) do
        if IsValid(e) and (e:IsNPC() or e:IsPlayer()) and e ~= owner then
            D6_Wep.DirectDamage(owner, owner, e, FRAG_DMG, exo, dir * 6000)
            local g = EffectData(); g:SetOrigin(e:WorldSpaceCenter()); g:SetScale(3)
            util.Effect("HelicopterMegaBomb", g)
        end
    end

    -- Blink: depart effect, move, arrive effect, carry momentum.
    local ef1 = EffectData(); ef1:SetOrigin(center); ef1:SetScale(3); util.Effect("cball_explode", ef1)
    owner:SetPos(dest + offset)
    owner.D6Vel = dir * 1200
    local ef2 = EffectData(); ef2:SetOrigin(dest); ef2:SetScale(3); util.Effect("cball_explode", ef2)

    net.Start("D6_TeleBlink")
        net.WriteVector(center)
        net.WriteVector(dest)
    net.Broadcast()

    owner:EmitSound("ambient/machines/teleport4.wav", 100, 100)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.6)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    local BEAM_MAT = Material("cable/xbeam")

    net.Receive("D6_TeleBlink", function()
        local src  = net.ReadVector()
        local dst  = net.ReadVector()
        local key  = "D6_TeleStreak_" .. CurTime() .. "_" .. math.random(99999)
        local born = CurTime()
        local dur  = 0.3
        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            local age = CurTime() - born
            if age >= dur then hook.Remove("PostDrawTranslucentRenderables", key); return end
            local a = (1 - age / dur) * 255
            render.SetMaterial(BEAM_MAT)
            render.DrawBeam(src, dst, 10, 0, 1, Color(200, 120, 255, a))
            render.DrawBeam(src, dst, 3,  0, 1, Color(245, 220, 255, a))
        end)
    end)

    function SWEP:DrawHUD()
        D6_Wep.DrawReadyHUD(self:GetOwner(), self, "ТЕЛЕФРАГ", Color(200, 120, 255), COOLDOWN)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_telefrag.lua loaded")
