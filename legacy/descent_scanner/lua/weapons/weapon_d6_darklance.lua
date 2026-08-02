-- ═══════════════════════════════════════════════════════════
-- weapon_d6_darklance.lua — КОПЬЁ ТЁМНОЙ ЭНЕРГИИ (ультимейт, босс)
--
--   Cat G: НЕ балансируется как обычное оружие. Заряд 1.0 с, затем
--   пробивающее копьё сквозь всё в линии (до 8 целей) — 250 exotic
--   каждой БЕЗ спада, плюс ПОЛНЫЙ съём щитов и брони цели (drain).
--   Боеприпас фазы босса: ломает защитные слои и валит сквозь строй.
--   70 энергии, КД 3 с после выстрела. Отдача 250.
--
--   Фреймворк: d6_weapon_core (Muzzle/ShootAng/DirectDamage/ApplyRecoil),
--   d6_energy (TryConsume), d6_shield (exotic-резист + обнуление слоёв
--   D6_Shield/D6_Armor цели), d6_weapon_registry (Ultimate).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "КОПЬЁ ТЬМЫ"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 1
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

if SERVER and not _D6_DARKLANCE_NET then
    util.AddNetworkString("D6_LanceFire")
    _D6_DARKLANCE_NET = true
    util.PrecacheSound("ambient/energy/newspark04.wav")
    util.PrecacheSound("ambient/energy/zap_loop1.wav")
    util.PrecacheSound("npc/strider/fire.wav")
end

local ENERGY_COST = 70
local CHARGE_TIME = 1.0
local POST_CD     = 3.0
local RANGE       = 9000
local LANCE_DMG   = 250
local MAX_HITS    = 8
local RECOIL      = 250
local MUZZLE      = { fwd = 24, rgt = 0, up = -4 }

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
    self._ChargeStart = nil
end

function SWEP:Deploy()
    self._ChargeStart = nil
    if SERVER then self:SetNWFloat("D6_LanceCharge", 0) end
    return true
end

function SWEP:Holster()
    self._ChargeStart = nil
    if SERVER then self:SetNWFloat("D6_LanceCharge", 0) end
    return true
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    if self._ChargeStart then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    -- Pre-check the reserve before committing to the charge.
    if D6_Energy.Get(owner) < ENERGY_COST then
        owner:EmitSound("buttons/button10.wav", 65, 90); return
    end
    self._ChargeStart = CurTime()
    self:SetNextPrimaryFire(CurTime() + 30)
    owner:EmitSound("ambient/energy/newspark04.wav", 85, 80)
    owner:EmitSound("ambient/energy/zap_loop1.wav", 70, 100)
end

-- Penetrating trace through up to maxHits entities (shared rail pattern).
local function PenetratingTrace(src, dir, range, owner, maxHits)
    local results   = {}
    local filterEnt = { owner }
    local curPos    = src
    for _ = 1, maxHits do
        if curPos:DistToSqr(src) > range * range then break end
        local tr = util.TraceLine({ start = curPos, endpos = src + dir * range,
            filter = filterEnt, mask = MASK_SHOT })
        if not tr.Hit then
            table.insert(results, { hitPos = src + dir * range, entity = nil })
            break
        end
        table.insert(results, { hitPos = tr.HitPos, entity = tr.Entity, normal = tr.HitNormal })
        if not IsValid(tr.Entity) or tr.Entity:GetClass() == "worldspawn" then break end
        table.insert(filterEnt, tr.Entity)
        curPos = tr.HitPos + dir * 8
    end
    return results
end

-- Strip ALL defensive layers (explicit d6_shield interaction).
local function DrainShields(ent)
    if ent.D6_Shield ~= nil then ent.D6_Shield = 0 end
    if ent.D6_Armor  ~= nil then ent.D6_Armor  = 0 end
    if ent:IsPlayer() then ent:SetNWFloat("D6_Shield", 0) end
end

function SWEP:_Fire(owner)
    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end

    local sa   = D6_Wep.ShootAng(owner)
    local dir  = sa:Forward()
    local src  = D6_Wep.Muzzle(owner, MUZZLE)
    local hits = PenetratingTrace(src, dir, RANGE, owner, MAX_HITS)
    local exo  = D6_DAMAGE.exotic.dmgType
    local endPos = src + dir * RANGE

    for _, hit in ipairs(hits) do
        endPos = hit.hitPos
        local e = hit.entity
        if IsValid(e) and (e:IsNPC() or e:IsPlayer()) then
            DrainShields(e)   -- collapse shield/armor first, then full exotic hit
            D6_Wep.DirectDamage(owner, owner, e, LANCE_DMG, exo, dir * 9000)
            local ef = EffectData(); ef:SetOrigin(hit.hitPos); ef:SetNormal(hit.normal or -dir); ef:SetScale(2)
            util.Effect("cball_explode", ef)
        elseif IsValid(e) then
            local ph = e:GetPhysicsObject()
            if IsValid(ph) then ph:ApplyForceCenter(dir * ph:GetMass() * 1200) end
        end
    end

    D6_Wep.ApplyRecoil(owner, dir, RECOIL)

    net.Start("D6_LanceFire")
        net.WriteVector(src)
        net.WriteVector(endPos)
    net.Broadcast()

    owner:EmitSound("npc/strider/fire.wav", 110, 70)
    self:SetNextPrimaryFire(CurTime() + POST_CD)
end

function SWEP:Think()
    if not SERVER then return end
    if not self._ChargeStart then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then self._ChargeStart = nil; return end

    local held = CurTime() - self._ChargeStart
    local frac = math.min(1, held / CHARGE_TIME)
    self:SetNWFloat("D6_LanceCharge", frac)

    if held >= CHARGE_TIME then
        owner:StopSound("ambient/energy/zap_loop1.wav")
        self._ChargeStart = nil
        self:SetNWFloat("D6_LanceCharge", 0)
        self:_Fire(owner)
        return
    end

    -- Released early → cancel (no energy spent).
    if not owner:KeyDown(IN_ATTACK) then
        owner:StopSound("ambient/energy/zap_loop1.wav")
        self._ChargeStart = nil
        self:SetNWFloat("D6_LanceCharge", 0)
        self:SetNextPrimaryFire(CurTime() + 0.4)
    end
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.6)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    local BEAM_MAT = Material("cable/xbeam")

    net.Receive("D6_LanceFire", function()
        local src  = net.ReadVector()
        local dst  = net.ReadVector()
        local key  = "D6_LanceVis_" .. CurTime() .. "_" .. math.random(99999)
        local born = CurTime()
        local dur  = 0.32
        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            local age = CurTime() - born
            if age >= dur then hook.Remove("PostDrawTranslucentRenderables", key); return end
            local a = (1 - age / dur) * 255
            render.SetMaterial(BEAM_MAT)
            render.DrawBeam(src, dst, 16, 0, 1, Color(120, 30, 200, a))
            render.DrawBeam(src, dst, 6,  0, 1, Color(190, 90, 255, a))
            render.DrawBeam(src, dst, 2,  0, 1, Color(235, 210, 255, a))
        end)
    end)

    function SWEP:DrawHUD()
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        D6_Wep.DrawEnergyHUD(ply, "КОПЬЁ ТЬМЫ", Color(180, 90, 255))
        local charge = self:GetNWFloat("D6_LanceCharge", 0)
        if charge > 0 then
            local sw, sh = ScrW(), ScrH()
            local bw, bh = 140, 5
            local bx, by = sw / 2 - bw / 2, sh - 80
            surface.SetDrawColor(25, 10, 40, 180); surface.DrawRect(bx - 1, by - 1, bw + 2, bh + 2)
            surface.SetDrawColor(180, 90, 255, 230); surface.DrawRect(bx, by, bw * charge, bh)
            if charge >= 1 then
                draw.SimpleText("▶ ПУСК", "DermaDefault", sw / 2, by - 6,
                    Color(220, 180, 255), TEXT_ALIGN_CENTER, TEXT_ALIGN_BOTTOM)
            end
        end
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_darklance.lua loaded")
