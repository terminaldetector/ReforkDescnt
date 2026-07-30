-- ═══════════════════════════════════════════════════════════
-- weapon_d6_quad_laser.lua — QUAD-ЛАЗЕР (четыре хитскан-луча)
--
--   4 параллельных луча из сетки 2×2 (±10u право/вверх).
--   Нет зарядки. Мгновенный удар.
--   Близкий бой (все лучи в одну цель): 120 урона @ 0.30 с = 400 ДПС.
--   Средний (2-3 луча): 60-90 урона → 200-300 ДПС.
--   Дальний: лучи расходятся → 4 отдельных цели.
--   Самый быстрый дрейн энергии в классе A — 40/с, пусто за 3 с.
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName       = "QUAD-ЛАЗЕР"
SWEP.Author          = "Descent 6DOF"
SWEP.Category        = "Descent"
SWEP.Slot            = 2
SWEP.SlotPos         = 5
SWEP.Spawnable       = true
SWEP.AdminSpawnable  = true
SWEP.Base            = "weapon_base"
SWEP.HoldType        = "physgun"
SWEP.ViewModel       = "models/weapons/v_physics.mdl"
SWEP.WorldModel      = "models/weapons/w_physics.mdl"
SWEP.UseHands        = false
SWEP.DrawAmmo        = false
SWEP.DrawCrosshair   = false
SWEP.Primary.ClipSize      = -1
SWEP.Primary.DefaultClip   = -1
SWEP.Primary.Automatic     = true
SWEP.Primary.Ammo          = "none"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

if SERVER and not _D6_QUADLASER_NET then
    util.AddNetworkString("D6_QuadFire")
    _D6_QUADLASER_NET = true
    util.PrecacheSound("ambient/energy/zap9.wav")
    util.PrecacheSound("buttons/button10.wav")
end

local ENERGY_COST = 12
local FIRE_RATE   = 0.30
local BEAM_DMG    = 30
local BEAM_RANGE  = 8000
local RECOIL      = 45

-- 2×2 grid, ±10 u right and up — 20u beam separation, fits inside
-- a 60u-diameter NPC hitbox when aim is within ~20u of center.
local MUZZLES = {
    { fwd = 25, rgt = -10, up =  10 },  -- top-left
    { fwd = 25, rgt =  10, up =  10 },  -- top-right
    { fwd = 25, rgt = -10, up = -10 },  -- bottom-left
    { fwd = 25, rgt =  10, up = -10 },  -- bottom-right
}

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
end

function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + FIRE_RATE)

    local sa  = D6_Wep.ShootAng(owner)
    local dir = sa:Forward()
    local dc  = D6_DAMAGE.energy

    local src_center = D6_Wep.Muzzle(owner, { fwd = 25, rgt = 0, up = 0 })
    local hitPos = {}

    -- Each beam independently traces and damages its first hit.
    -- Already-hit entities are NOT re-excluded — two beams can hit
    -- the same entity and both deal full damage (close-range bonus).
    for i, muz in ipairs(MUZZLES) do
        local src = D6_Wep.Muzzle(owner, muz)
        local tr = util.TraceLine({
            start  = src,
            endpos = src + dir * BEAM_RANGE,
            filter = owner,
            mask   = MASK_SHOT,
        })
        hitPos[i] = tr.HitPos

        if IsValid(tr.Entity) and (tr.Entity:IsNPC() or tr.Entity:IsPlayer()) then
            D6_Wep.DirectDamage(owner, owner, tr.Entity, BEAM_DMG, dc.dmgType, dir * 3000)
            local ef = EffectData()
            ef:SetOrigin(tr.HitPos); ef:SetNormal(tr.HitNormal or -dir); ef:SetScale(1)
            util.Effect("AR2Impact", ef)
        end
    end

    D6_Wep.ApplyRecoil(owner, dir, RECOIL)

    -- Send source + 4 hit positions to clients for beam visual.
    net.Start("D6_QuadFire")
        net.WriteVector(src_center)
        for i = 1, 4 do
            net.WriteVector(hitPos[i] or src_center + dir * BEAM_RANGE)
        end
    net.Broadcast()

    -- Muzzle flash at center
    local ef = EffectData(); ef:SetOrigin(src_center); ef:SetNormal(dir); ef:SetScale(0.8)
    util.Effect("MuzzleFlash", ef)
    owner:EmitSound("ambient/energy/zap9.wav", 72, 120)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.8)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

-- ─── Client: 4-beam visual ───────────────────────────────
if CLIENT then
    local BEAM_MAT = Material("cable/xbeam")
    local _qIdx    = 0

    net.Receive("D6_QuadFire", function()
        local src = net.ReadVector()
        local dst = {}
        for i = 1, 4 do dst[i] = net.ReadVector() end

        -- Muzzle flash effect
        local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(vector_origin); ef:SetScale(0.8)
        util.Effect("MuzzleEffect", ef)

        _qIdx = _qIdx + 1
        local key  = "D6_QuadBeam_" .. _qIdx
        local born = CurTime()
        local dur  = 0.09
        local fSrc = Vector(src.x, src.y, src.z)
        local fDst = {}
        for i = 1, 4 do fDst[i] = Vector(dst[i].x, dst[i].y, dst[i].z) end

        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            local age = CurTime() - born
            if age >= dur then
                hook.Remove("PostDrawTranslucentRenderables", key); return
            end
            local a = (1 - age / dur) * 255
            render.SetMaterial(BEAM_MAT)
            for i = 1, 4 do
                -- Outer beam: bright green
                render.DrawBeam(fSrc, fDst[i], 4,   0, 1, Color(80,  255, 140, a))
                -- Inner core: near-white
                render.DrawBeam(fSrc, fDst[i], 1.5, 0, 1, Color(200, 255, 220, a))
            end
        end)
    end)

    function SWEP:DrawHUD()
        D6_Wep.DrawEnergyHUD(self:GetOwner(), "QUAD-ЛАЗЕР", Color(80, 255, 140))
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_quad_laser.lua loaded")
