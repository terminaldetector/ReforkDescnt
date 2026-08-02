-- ═══════════════════════════════════════════════════════════
-- weapon_d6_darkfield.lua — ПОЛЕ ТЁМНОЙ ЭНЕРГИИ (защита точки)
--
--   Cat E: оборона объектов. Разворачивает большое поле (400u) на
--   12 с, непрерывно жгущее всех врагов внутри (18 exotic за 0.5с →
--   36/с). Без контроля движения — чистое площадное отрицание,
--   крупнее и злее мины. До 2 активных. Держит зону у цели/прохода.
--
--   Фреймворк: d6_weapon_core (Muzzle/DirectDamage), d6_energy
--   (TryConsume), d6_shield (exotic → energy-резист).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "ПОЛЕ ТЬМЫ"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 5
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

local MDL_CORE = "models/dav0r/hoverball.mdl"
if SERVER and not _D6_DARKFIELD_NET then
    util.AddNetworkString("D6_DarkField")
    _D6_DARKFIELD_NET = true
    util.PrecacheModel(MDL_CORE)
    util.PrecacheSound("ambient/atmosphere/ambience_base.wav")
    util.PrecacheSound("ambient/energy/zap9.wav")
end

local ENERGY_COST = 45
local COOLDOWN    = 4.0
local MAX_ACTIVE  = 2
local FIELD_RAD   = 400
local TICK_DMG    = 18      -- за 0.5с → 36/с
local LIFE        = 12

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
    local pos = D6_Wep.Muzzle(owner, { fwd = 40, rgt = 0, up = -6 }) + dir * 80
    local exoType = D6_DAMAGE.exotic.dmgType

    local core = ents.Create("prop_physics")
    if not IsValid(core) then return end
    core:SetModel(MDL_CORE)
    core:SetPos(pos)
    core:SetOwner(owner)
    core:Spawn()
    core:SetModelScale(2.6, 0)
    core:SetColor(Color(130, 40, 200, 255))
    core:SetRenderMode(RENDERMODE_TRANSADD)
    core:SetCollisionGroup(COLLISION_GROUP_WEAPON)
    local ph = core:GetPhysicsObject()
    if IsValid(ph) then ph:EnableGravity(false); ph:EnableMotion(false); ph:Sleep() end

    self:_Register(core)
    core:EmitSound("ambient/atmosphere/ambience_base.wav", 85, 70)

    -- Persistent client visual for the field volume.
    net.Start("D6_DarkField")
        net.WriteEntity(core)
        net.WriteFloat(FIELD_RAD)
        net.WriteFloat(LIFE)
    net.Broadcast()

    local idx = core:EntIndex()
    timer.Create("D6_DField_" .. idx, 0.5, 0, function()
        if not IsValid(core) then timer.Remove("D6_DField_" .. idx); return end
        local c = core:GetPos()
        for _, e in ipairs(ents.FindInSphere(c, FIELD_RAD)) do
            if IsValid(e) and (e:IsNPC() or e:IsPlayer()) and e ~= owner then
                D6_Wep.DirectDamage(owner, core, e, TICK_DMG, exoType,
                    (e:GetPos() - c):GetNormalized() * 600)
            end
        end
    end)

    timer.Simple(LIFE, function()
        if IsValid(core) then
            timer.Remove("D6_DField_" .. idx)
            local c = core:GetPos()
            local ef = EffectData(); ef:SetOrigin(c); ef:SetScale(3); util.Effect("cball_explode", ef)
            sound.Play("ambient/energy/zap9.wav", c, 100, 70)
            core:Remove()
        end
    end)

    owner:EmitSound("weapons/physcannon/superphys_launch3.wav", 80, 70)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.6)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    local FIELD_MAT = Material("effects/select_ring")

    net.Receive("D6_DarkField", function()
        local core   = net.ReadEntity()
        local radius = net.ReadFloat()
        local life   = net.ReadFloat()
        local key    = "D6_DarkFieldVis_" .. (IsValid(core) and core:EntIndex() or math.random(99999))
        local born   = CurTime()

        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            if not IsValid(core) or (CurTime() - born) >= life then
                hook.Remove("PostDrawTranslucentRenderables", key); return
            end
            local pulse = 0.85 + math.sin(CurTime() * 4) * 0.15
            local c     = core:GetPos()
            render.SetMaterial(FIELD_MAT)
            render.DrawSphere(c, radius * pulse, 26, 26, Color(130, 40, 200, 50))
            render.DrawWireframeSphere(c, radius * pulse, 20, 20, Color(180, 90, 255, 70), false)
        end)
    end)

    function SWEP:DrawHUD()
        D6_Wep.DrawReadyHUD(self:GetOwner(), self, "ПОЛЕ ТЬМЫ", Color(180, 90, 255), COOLDOWN)
        local n = self:GetNWInt("D6_Deployed", 0)
        draw.SimpleText("ПОЛЕЙ: " .. n .. " / " .. MAX_ACTIVE, "DermaDefault",
            ScrW() / 2, ScrH() - 104, Color(180, 90, 255), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_darkfield.lua loaded")
