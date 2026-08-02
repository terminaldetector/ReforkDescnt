-- ═══════════════════════════════════════════════════════════
-- weapon_d6_gravmine.lua — ГРАВИ-МИНА (гравитационный колодец)
--
--   Cat E: пространственный контроль. Ставит колодец, который 8 с
--   притягивает врагов и пропы к центру (сила растёт у центра) и
--   наносит лёгкий DoT (15/с energy). Удерживает рой в зоне —
--   отрицание маршрута. По истечении — имплозия 90 в 300u.
--   До 3 активных. НЕ убивает напрямую — контролирует пространство.
--
--   Фреймворк: d6_weapon_core (Muzzle/SplashDamage/DirectDamage),
--   d6_energy (TryConsume), d6_shield (energy → energy-резист).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "ГРАВИ-МИНА"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 5
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

local MDL_WELL = "models/dav0r/hoverball.mdl"
if SERVER then
    util.PrecacheModel(MDL_WELL)
    util.PrecacheSound("ambient/machines/teleport1.wav")
    util.PrecacheSound("ambient/levels/labs/electric_explosion4.wav")
end

local ENERGY_COST  = 30
local COOLDOWN     = 1.5
local MAX_ACTIVE   = 3
local PULL_RANGE   = 500
local PULL_FORCE   = 30          -- множитель силы к массе
local PLY_ACCEL    = 1400        -- u/с² для игрока
local DOT          = 1.5         -- за тик 0.1с → 15/с
local LIFE         = 8
local IMPLODE_DMG  = 90
local IMPLODE_RAD  = 300

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
    local energyType = D6_DAMAGE.energy.dmgType

    local well = ents.Create("prop_physics")
    if not IsValid(well) then return end
    well:SetModel(MDL_WELL)
    well:SetPos(pos)
    well:SetOwner(owner)
    well:Spawn()
    well:SetModelScale(1.6, 0)
    well:SetColor(Color(170, 80, 255, 255))
    well:SetRenderMode(RENDERMODE_TRANSADD)
    well:SetCollisionGroup(COLLISION_GROUP_WEAPON)
    local ph = well:GetPhysicsObject()
    if IsValid(ph) then ph:EnableGravity(false); ph:EnableMotion(false); ph:Sleep() end

    self:_Register(well)
    well:EmitSound("ambient/machines/teleport1.wav", 80, 90)

    local idx = well:EntIndex()
    timer.Create("D6_GMine_" .. idx, 0.1, 0, function()
        if not IsValid(well) then timer.Remove("D6_GMine_" .. idx); return end
        local c = well:GetPos()
        for _, e in ipairs(ents.FindInSphere(c, PULL_RANGE)) do
            if not IsValid(e) or e == well or e == owner then continue end
            local toC = c - e:GetPos()
            local d   = toC:Length()
            if d < 16 then continue end
            local fall = math.Clamp(1 - d / PULL_RANGE, 0.1, 1)
            if e:IsPlayer() then
                e.D6Vel = (e.D6Vel or Vector(0, 0, 0)) + toC:GetNormalized() * PLY_ACCEL * fall * 0.1
            elseif e:IsNPC() or e:GetClass() == "prop_physics" then
                local p = e:GetPhysicsObject()
                if IsValid(p) then
                    p:ApplyForceCenter(toC:GetNormalized() * p:GetMass() * PULL_FORCE * fall)
                end
                if e:IsNPC() then
                    D6_Wep.DirectDamage(owner, well, e, DOT, energyType, vector_origin)
                end
            end
        end
    end)

    timer.Simple(LIFE, function()
        if not IsValid(well) then return end
        timer.Remove("D6_GMine_" .. idx)
        local c = well:GetPos()
        D6_Wep.SplashDamage(owner, well, c, 0, IMPLODE_DMG, IMPLODE_RAD, energyType, 5000, nil)
        local ef = EffectData(); ef:SetOrigin(c); ef:SetScale(3); ef:SetMagnitude(IMPLODE_RAD)
        util.Effect("cball_explode", ef)
        sound.Play("ambient/levels/labs/electric_explosion4.wav", c, 105, 80)
        well:Remove()
    end)

    owner:EmitSound("weapons/physcannon/physcannon_drop.wav", 70, 100)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.6)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    function SWEP:DrawHUD()
        D6_Wep.DrawReadyHUD(self:GetOwner(), self, "ГРАВИ-МИНА", Color(170, 80, 255), COOLDOWN)
        local n = self:GetNWInt("D6_Deployed", 0)
        draw.SimpleText("КОЛОДЦЕВ: " .. n .. " / " .. MAX_ACTIVE, "DermaDefault",
            ScrW() / 2, ScrH() - 104, Color(170, 80, 255), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_gravmine.lua loaded")
