-- ═══════════════════════════════════════════════════════════
-- weapon_d6_railmk2.lua — РЕЛЬСА МК2 (заряжаемая проникающая рельса)
--
--   Быстрый щелчок (<0.15 с): 18 энергии, одиночный цель, масса→урон.
--   Удержание ≥0.60 с: 35 энергии, проникает через 3 цели (урон ÷ 2).
--   ПКМ: точная гравитационная тяга — притягивает один проп.
--   Занимает слот 4/1 вместо старого gravyrailgun (в списке игрока).
--   Старый файл остаётся — NPC-спавнеры "grav" используют его.
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "РЕЛЬСА МК2"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 4
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

if SERVER and not _D6_RAILMK2_NET then
    util.AddNetworkString("D6_RailFire2")
    _D6_RAILMK2_NET = true
end

local ENERGY_QUICK  = 18
local ENERGY_CHARGE = 35
local ENERGY_LURE   = 12
local QUICK_THRESH  = 0.15
local CHARGE_TIME   = 0.60
local RAIL_RANGE    = 8000
local LURE_RANGE    = 1800
local LURE_SPEED    = 1400
local LURE_MAX_MASS = 400
local LURE_COOLDOWN = 1.2
local MUZZLE        = { fwd = 15, rgt = 0, up = -5 }

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
    self._ChargeStart = nil
    self._ChargeFired = false
end

function SWEP:Deploy()
    self._ChargeStart = nil
    self._ChargeFired = false
    if SERVER then self:SetNWFloat("D6_RailCharge", 0) end
    return true
end

function SWEP:Holster()
    self._ChargeStart = nil
    self._ChargeFired = false
    if SERVER then self:SetNWFloat("D6_RailCharge", 0) end
    return true
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    if self._ChargeStart then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end
    self._ChargeStart = CurTime()
    self._ChargeFired = false
    self:SetNextPrimaryFire(CurTime() + 10)
    owner:EmitSound("ambient/energy/newspark01.wav", 70, 100)
end

-- Charge build and release detection.
function SWEP:Think()
    if not SERVER then return end
    if not self._ChargeStart then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    local ct   = CurTime()
    local held = ct - self._ChargeStart
    local frac = math.min(1, held / CHARGE_TIME)
    self:SetNWFloat("D6_RailCharge", frac)

    -- Auto-fire on full charge
    if held >= CHARGE_TIME and not self._ChargeFired then
        self._ChargeFired = true
        self._ChargeStart = nil
        self:SetNWFloat("D6_RailCharge", 0)
        self:_DoChargedShot(owner)
        return
    end

    -- Fire on release (before full charge)
    if not owner:KeyDown(IN_ATTACK) then
        self._ChargeStart = nil
        self:SetNWFloat("D6_RailCharge", 0)
        if not self._ChargeFired then
            self:_DoQuickShot(owner)
        end
        self._ChargeFired = false
    end
end

-- ─── Penetrating trace: up to maxHits entities in a line ──
local function PenetratingTrace(src, dir, range, owner, maxHits)
    local results   = {}
    local filterEnt = { owner }
    local curPos    = src

    for _ = 1, maxHits do
        if curPos:DistToSqr(src) > range * range then break end
        local tr = util.TraceLine({
            start  = curPos,
            endpos = src + dir * range,
            filter = filterEnt,
            mask   = MASK_SHOT,
        })
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

function SWEP:_DoQuickShot(owner)
    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_QUICK) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end

    local sa  = D6_Wep.ShootAng(owner)
    local dir = sa:Forward()
    local src = D6_Wep.Muzzle(owner, MUZZLE)

    local tr = util.TraceLine({
        start  = src,
        endpos = src + dir * RAIL_RANGE,
        filter = owner,
        mask   = MASK_SHOT,
    })
    local hitPos = tr.HitPos

    if IsValid(tr.Entity) then
        local ph = tr.Entity:GetPhysicsObject()
        local mass = (IsValid(ph) and ph:GetMass()) or 150
        local dmg = math.Clamp(mass * 0.5, 50, 120)
        if tr.Entity:IsNPC() or tr.Entity:IsPlayer() then
            D6_Wep.DirectDamage(owner, owner, tr.Entity, dmg, DMG_BULLET, dir * 8000)
        elseif IsValid(ph) then
            ph:ApplyForceCenter(dir * mass * 600)
        end
        local ef = EffectData(); ef:SetOrigin(hitPos); ef:SetNormal(tr.HitNormal or -dir); ef:SetMagnitude(2)
        util.Effect("ManhackSparks", ef)
    end

    D6_Wep.ApplyRecoil(owner, dir, 90)

    net.Start("D6_RailFire2")
        net.WriteVector(src)
        net.WriteVector(hitPos)
        net.WriteBool(false)
        net.WriteFloat(0)
    net.Broadcast()

    owner:EmitSound("weapons/physcannon/superphys_launch1.wav", 80, 115)
    self:SetNextPrimaryFire(CurTime() + 0.5)
end

function SWEP:_DoChargedShot(owner)
    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_CHARGE) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end

    local sa   = D6_Wep.ShootAng(owner)
    local dir  = sa:Forward()
    local src  = D6_Wep.Muzzle(owner, MUZZLE)
    local hits = PenetratingTrace(src, dir, RAIL_RANGE, owner, 3)
    local baseDmg = math.random(100, 250)
    local endPos  = src + dir * RAIL_RANGE

    for i, hit in ipairs(hits) do
        endPos = hit.hitPos
        local dmgMult = 0.5 ^ (i - 1)
        local hitDmg  = baseDmg * dmgMult
        if IsValid(hit.entity) and (hit.entity:IsNPC() or hit.entity:IsPlayer()) then
            D6_Wep.DirectDamage(owner, owner, hit.entity, hitDmg, DMG_BULLET,
                dir * 8000 * dmgMult)
            local ef = EffectData()
            ef:SetOrigin(hit.hitPos); ef:SetNormal(hit.normal or -dir); ef:SetMagnitude(3)
            util.Effect("ManhackSparks", ef)
        elseif IsValid(hit.entity) then
            local ph = hit.entity:GetPhysicsObject()
            if IsValid(ph) then ph:ApplyForceCenter(dir * ph:GetMass() * 800 * dmgMult) end
        end
    end

    D6_Wep.ApplyRecoil(owner, dir, 150)

    net.Start("D6_RailFire2")
        net.WriteVector(src)
        net.WriteVector(endPos)
        net.WriteBool(true)
        net.WriteFloat(baseDmg)
    net.Broadcast()

    owner:EmitSound("ambient/energy/zap9.wav", 85, 78)
    self:SetNextPrimaryFire(CurTime() + 1.5)
end

-- ─── RMB: precision gravity lure ─────────────────────────
function SWEP:SecondaryAttack()
    if not SERVER then return end
    local ct = CurTime()
    if ct < (self._NextLure or 0) then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_LURE) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end
    self._NextLure = ct + LURE_COOLDOWN
    self:SetNextSecondaryFire(ct + LURE_COOLDOWN)

    local sa       = D6_Wep.ShootAng(owner)
    local fwd      = sa:Forward()
    local muz      = owner:GetShootPos()
    local cosLimit = math.cos(math.rad(12))
    local bestEnt, bestMass = nil, 0

    for _, e in ipairs(ents.FindInSphere(muz, LURE_RANGE)) do
        if e:GetClass() ~= "prop_physics" then continue end
        local ph = e:GetPhysicsObject()
        if not IsValid(ph) then continue end
        local mass = ph:GetMass()
        if mass > LURE_MAX_MASS then continue end
        local toEnt = (e:GetPos() - muz):GetNormalized()
        if toEnt:Dot(fwd) < cosLimit then continue end
        if mass > bestMass then bestMass = mass; bestEnt = e end
    end

    if not IsValid(bestEnt) then
        owner:EmitSound("common/wpn_denyselect.wav", 60, 110); return
    end

    local ph      = bestEnt:GetPhysicsObject()
    local toOwner = (owner:GetPos() - bestEnt:GetPos()):GetNormalized()
    ph:EnableGravity(false)
    ph:SetVelocity(toOwner * LURE_SPEED)
    ph:Wake()

    timer.Simple(2.5, function()
        if IsValid(bestEnt) then
            local p2 = bestEnt:GetPhysicsObject()
            if IsValid(p2) then p2:EnableGravity(true) end
        end
    end)

    owner:EmitSound("weapons/physcannon/physcannon_shoot.wav", 72, 130)
end

-- ─── Client: rail beam visual ─────────────────────────────
if CLIENT then
    local BEAM_MAT  = Material("cable/xbeam")
    local _railIdx  = 0

    net.Receive("D6_RailFire2", function()
        local src     = net.ReadVector()
        local dst     = net.ReadVector()
        local charged = net.ReadBool()
        local _dmg    = net.ReadFloat()

        local ef = EffectData()
        ef:SetOrigin(src); ef:SetNormal((dst - src):GetNormalized()); ef:SetMagnitude(charged and 3 or 2)
        util.Effect("ManhackSparks", ef)

        _railIdx = _railIdx + 1
        local key  = "D6_Rail2Beam_" .. _railIdx
        local life = CurTime() + (charged and 0.22 or 0.10)
        local bw   = charged and 10 or 4
        local col  = charged and Color(180, 255, 200) or Color(140, 255, 180)

        local fSrc = Vector(src.x, src.y, src.z)
        local fDst = Vector(dst.x, dst.y, dst.z)

        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            if CurTime() >= life then
                hook.Remove("PostDrawTranslucentRenderables", key); return
            end
            local frac = math.Clamp((life - CurTime()) / (charged and 0.22 or 0.10), 0, 1)
            render.SetMaterial(BEAM_MAT)
            render.DrawBeam(fSrc, fDst, bw, 0, 1, Color(col.r, col.g, col.b, frac * 255))
            if charged then
                render.DrawBeam(fSrc, fDst, bw * 2, 0, 1, Color(col.r, col.g, col.b, frac * 70))
            end
        end)
    end)
end

if CLIENT then
    function SWEP:DrawHUD()
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        D6_Wep.DrawEnergyHUD(ply, "РЕЛЬСА МК2", Color(140, 255, 180))

        local charge = self:GetNWFloat("D6_RailCharge", 0)
        if charge > 0 then
            local sw, sh = ScrW(), ScrH()
            local bw, bh = 140, 4
            local bx = sw / 2 - bw / 2
            local by = sh - 80
            surface.SetDrawColor(20, 40, 20, 180)
            surface.DrawRect(bx - 1, by - 1, bw + 2, bh + 2)
            local r = charge < 1 and 140 or 255
            local g = charge < 1 and 255 or 255
            surface.SetDrawColor(r, g, 180, 220)
            surface.DrawRect(bx, by, bw * charge, bh)
            if charge >= 1 then
                draw.SimpleText("▶ ОГОНЬ", "DermaDefault", sw / 2, by - 6,
                    Color(180, 255, 200), TEXT_ALIGN_CENTER, TEXT_ALIGN_BOTTOM)
            end
        end
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_railmk2.lua loaded")
