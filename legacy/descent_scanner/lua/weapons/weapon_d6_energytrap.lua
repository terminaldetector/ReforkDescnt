-- ═══════════════════════════════════════════════════════════
-- weapon_d6_energytrap.lua — ЭНЕРГО-КАПКАН (отрицание маршрута)
--
--   Cat E: отрицание маршрута. Ставит узел-капкан. При пересечении
--   врагом зоны 150u — снейр (гашение скорости ×0.5) и energy-DoT на
--   3 с, затем перевзвод через 1.5 с. Живёт 25 с, до 5 активных.
--   Тормозит и треплет прорывающийся рой в коридорах.
--
--   Фреймворк: d6_weapon_core (Muzzle/DirectDamage), d6_energy
--   (TryConsume), d6_shield (energy → energy-резист).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "ЭНЕРГО-КАПКАН"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 5
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

local MDL_NODE = "models/dav0r/hoverball.mdl"
if SERVER then
    util.PrecacheModel(MDL_NODE)
    util.PrecacheSound("ambient/energy/spark6.wav")
    util.PrecacheSound("ambient/energy/zap7.wav")
end

local ENERGY_COST = 22
local COOLDOWN    = 1.2
local MAX_ACTIVE  = 5
local TRIG_RAD    = 150
local SNARE_TIME  = 3.0
local SNARE_DOT   = 6        -- за тик 0.25с → 24/с
local REARM       = 1.5
local LIFE        = 25

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
    self._Deployed = {}
end
function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

function SWEP:_Register(ent)
    self._Deployed = self._Deployed or {}
    for i = #self._Deployed, 1, -1 do
        if not IsValid(self._Deployed[i]) then table.remove(self._Deployed, i) end
    end
    table.insert(self._Deployed, ent)
    if #self._Deployed > MAX_ACTIVE then
        local old = table.remove(self._Deployed, 1)
        if IsValid(old) then old:Remove() end
    end
    if SERVER then self:SetNWInt("D6_Deployed", #self._Deployed) end
end

local function Snare(owner, node, target)
    if not IsValid(target) then return end
    local energyType = D6_DAMAGE.energy.dmgType
    local sid  = "D6_Snare_" .. target:EntIndex() .. "_" .. math.random(99999)
    local ends = CurTime() + SNARE_TIME
    timer.Create(sid, 0.25, 0, function()
        if not IsValid(target) or CurTime() >= ends then timer.Remove(sid); return end
        if target:IsPlayer() then
            target.D6Vel = (target.D6Vel or Vector(0, 0, 0)) * 0.5
        else
            local p = target:GetPhysicsObject()
            if IsValid(p) then p:SetVelocity(p:GetVelocity() * 0.5) end
        end
        D6_Wep.DirectDamage(owner, IsValid(node) and node or owner, target, SNARE_DOT,
            energyType, vector_origin)
        local ef = EffectData(); ef:SetOrigin(target:WorldSpaceCenter()); ef:SetScale(1)
        util.Effect("cball_bounce", ef)
    end)
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 60, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + COOLDOWN)

    local dir = D6_Wep.ShootAng(owner):Forward()
    local pos = D6_Wep.Muzzle(owner, { fwd = 40, rgt = 0, up = -6 }) + dir * 60

    local node = ents.Create("prop_physics")
    if not IsValid(node) then return end
    node:SetModel(MDL_NODE)
    node:SetPos(pos)
    node:SetOwner(owner)
    node:Spawn()
    node:SetModelScale(0.9, 0)
    node:SetColor(Color(60, 255, 230, 255))
    node:SetRenderMode(RENDERMODE_TRANSADD)
    node:SetCollisionGroup(COLLISION_GROUP_WEAPON)
    local ph = node:GetPhysicsObject()
    if IsValid(ph) then ph:EnableGravity(false); ph:EnableMotion(false); ph:Sleep() end

    self:_Register(node)
    node:EmitSound("ambient/energy/spark6.wav", 70, 120)

    node.D6_Armed = true
    local idx = node:EntIndex()
    timer.Create("D6_ETrap_" .. idx, 0.2, 0, function()
        if not IsValid(node) then timer.Remove("D6_ETrap_" .. idx); return end
        if not node.D6_Armed then return end
        for _, e in ipairs(ents.FindInSphere(node:GetPos(), TRIG_RAD)) do
            if IsValid(e) and (e:IsNPC() or e:IsPlayer()) and e ~= owner then
                node.D6_Armed = false
                Snare(owner, node, e)
                sound.Play("ambient/energy/zap7.wav", node:GetPos(), 90, 110)
                local ef = EffectData(); ef:SetOrigin(node:GetPos()); ef:SetMagnitude(2)
                ef:SetScale(2); util.Effect("cball_explode", ef)
                timer.Simple(REARM, function() if IsValid(node) then node.D6_Armed = true end end)
                break
            end
        end
    end)

    timer.Simple(LIFE, function()
        if IsValid(node) then timer.Remove("D6_ETrap_" .. idx); node:Remove() end
    end)

    owner:EmitSound("weapons/physcannon/physcannon_drop.wav", 70, 130)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.6)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    function SWEP:DrawHUD()
        D6_Wep.DrawReadyHUD(self:GetOwner(), self, "ЭНЕРГО-КАПКАН", Color(60, 255, 230), COOLDOWN)
        local n = self:GetNWInt("D6_Deployed", 0)
        draw.SimpleText("КАПКАНОВ: " .. n .. " / " .. MAX_ACTIVE, "DermaDefault",
            ScrW() / 2, ScrH() - 104, Color(60, 255, 230), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_energytrap.lua loaded")
