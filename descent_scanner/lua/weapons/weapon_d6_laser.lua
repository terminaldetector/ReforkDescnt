-- ═══════════════════════════════════════════════════════════
-- weapon_d6_laser.lua — ЛАЗЕР, удержание ЛКМ = луч
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName       = "ЛАЗЕР"
SWEP.Author          = "Descent 6DOF"
SWEP.Category        = "Descent"
SWEP.Slot            = 2
SWEP.SlotPos         = 3
SWEP.Spawnable       = true
SWEP.AdminSpawnable  = true
SWEP.Base            = "weapon_base"
SWEP.HoldType        = "physgun"
SWEP.ViewModel       = "models/weapons/v_physics.mdl"
SWEP.WorldModel      = "models/weapons/w_physics.mdl"
SWEP.UseHands        = false
SWEP.DrawAmmo        = false
SWEP.DrawCrosshair   = false
SWEP.Primary.ClipSize    = -1
SWEP.Primary.DefaultClip = -1
SWEP.Primary.Automatic   = true
SWEP.Primary.Ammo        = "none"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

local MDL_STRIDER = "models/gibs/strider_weapon.mdl"

if SERVER and not _D6_LASER_NET then
    util.AddNetworkString("D6_LaserBeam")
    _D6_LASER_NET = true
end

local ENERGY_MAX   = 100
local ENERGY_REGEN = 8
local LASER_DRAIN  = 3
local LASER_DMG    = 200  -- урон/сек, умножается на FrameTime

local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

local SLOTS = {
    { mdl=MDL_STRIDER, fwd=44, rgt=0, up=-26, pitch=15, yaw=0 },
}

-- =========================================================
function SWEP:Initialize()
    self._Models = {}
    self._Slots  = SLOTS
    if CLIENT and IsValid(self:GetOwner()) and self:GetOwner() == LocalPlayer() then
        self:_BuildModels()
    end
end

function SWEP:Deploy()
    if SERVER then self:SetNWBool("D6_LaserOn", false) end
    if CLIENT and IsValid(self:GetOwner()) and self:GetOwner() == LocalPlayer() then
        local vm = self:GetOwner():GetViewModel()
        if IsValid(vm) then vm:SetNoDraw(true) end
        timer.Simple(0, function()
            if IsValid(self) then self:_BuildModels() end
        end)
    end
    return true
end

function SWEP:Holster()
    if SERVER then self:SetNWBool("D6_LaserOn", false) end
    if CLIENT then
        local ply = self:GetOwner()
        if IsValid(ply) and ply == LocalPlayer() then
            local vm = ply:GetViewModel()
            if IsValid(vm) then vm:SetNoDraw(false) end
            self:_DestroyModels()
        end
    end
    return true
end

function SWEP:OnRemove()
    if SERVER then self:SetNWBool("D6_LaserOn", false) end
    self:_DestroyModels()
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    self:SetNWBool("D6_LaserOn", true)
    self:SetNextPrimaryFire(CurTime() + 9999)
end

function SWEP:Think()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    local laserOn = self:GetNWBool("D6_LaserOn", false)

    if laserOn and not owner:KeyDown(IN_ATTACK) then
        self:SetNWBool("D6_LaserOn", false); laserOn = false
    end

    local e = owner:GetNWInt("D6_WepEnergy", ENERGY_MAX)

    if laserOn then
        if e <= 0 then self:SetNWBool("D6_LaserOn", false); laserOn = false
        else
            owner:SetNWInt("D6_WepEnergy", math.max(0, e - LASER_DRAIN))

            local sa  = ShootAng(owner)
            local src = owner:GetShootPos()
            local dir = sa:Forward()
            local tr  = util.TraceLine({ start=src, endpos=src+dir*8000, filter=owner, mask=MASK_SHOT })

            if IsValid(tr.Entity) and (tr.Entity:IsNPC() or tr.Entity:IsPlayer()) then
                local di = DamageInfo()
                di:SetAttacker(owner); di:SetInflictor(self)
                di:SetDamage(LASER_DMG * FrameTime())
                di:SetDamageType(DMG_ENERGYBEAM)
                di:SetDamageForce(dir * 400)
                tr.Entity:TakeDamageInfo(di)
            end

            net.Start("D6_LaserBeam"); net.WriteBool(true); net.WriteVector(tr.HitPos)
            net.Send(owner)
        end
    else
        if e < ENERGY_MAX then
            owner:SetNWInt("D6_WepEnergy", math.min(ENERGY_MAX, e + ENERGY_REGEN * FrameTime()))
        end
        net.Start("D6_LaserBeam"); net.WriteBool(false); net.WriteVector(Vector())
        net.Send(owner)
    end
end

function SWEP:SecondaryAttack() end

-- =========================================================
if CLIENT then

local _LaserActive = false
local _LaserHitPos = Vector()
local MAT_LASER    = Material("sprites/laserbeam")
local MAT_GLOW     = Material("sprites/light_glow02_add")

net.Receive("D6_LaserBeam", function()
    _LaserActive = net.ReadBool()
    _LaserHitPos = net.ReadVector()
end)

hook.Add("PostDrawTranslucentRenderables", "D6_LaserBeamDraw", function(depth, sky)
    if depth or sky or not _LaserActive then return end
    local ply = LocalPlayer()
    if not IsValid(ply) then return end
    local wep = ply:GetActiveWeapon()
    if not IsValid(wep) or wep:GetClass() ~= "weapon_d6_laser" then
        _LaserActive = false; return
    end
    local src = EyePos()
    render.SetMaterial(MAT_LASER)
    render.DrawBeam(src, _LaserHitPos, 5, 0, 1, Color(255, 50, 50, 240))
    render.DrawBeam(src, _LaserHitPos, 2, 0, 1, Color(255, 200, 200, 160))
    render.SetMaterial(MAT_GLOW)
    render.DrawSprite(_LaserHitPos, 24, 24, Color(255, 80, 50, 200))
end)

function SWEP:_BuildModels()
    self:_DestroyModels()
    self._Models = {}
    for i, s in ipairs(self._Slots or SLOTS) do
        local m = ClientsideModel(s.mdl, RENDER_GROUP_TRANSLUCENT_IGNORE_Z)
        if IsValid(m) then m:SetNoDraw(true); self._Models[i] = m end
    end
end

function SWEP:_DestroyModels()
    for _, m in ipairs(self._Models or {}) do
        if IsValid(m) then m:Remove() end
    end
    self._Models = {}
end

function SWEP:DrawViewModel()
    local ply = self:GetOwner()
    if not (IsValid(ply) and ply == LocalPlayer()) then return end
    local slots = self._Slots or SLOTS
    if not self._Models or #self._Models == 0 then self:_BuildModels() end

    local ep  = EyePos()
    local ea  = EyeAngles()
    local fwd = ea:Forward()
    local rgt = ea:Right()
    local up  = ea:Up()

    render.DepthRange(0, 0.1)
    for i, m in ipairs(self._Models) do
        local s = slots[i]
        if IsValid(m) and s then
            m:SetPos(ep + fwd*s.fwd + rgt*s.rgt + up*s.up)
            m:SetAngles(Angle(ea.p + s.pitch, ea.y + s.yaw, ea.r + (s.roll or 0)))
            m:SetupBones()
            m:DrawModel()
        end
    end
    render.DepthRange(0, 1)
end

function SWEP:DrawHUD()
    local ply = self:GetOwner()
    if not (IsValid(ply) and ply == LocalPlayer()) then return end
    local energy  = ply:GetNWInt("D6_WepEnergy", ENERGY_MAX)
    local laserOn = self:GetNWBool("D6_LaserOn", false)
    local sw, sh  = ScrW(), ScrH()
    local bw, bh  = 140, 6
    local bx, by  = sw/2 - bw/2, sh - 72
    surface.SetDrawColor(30, 30, 30, 180); surface.DrawRect(bx-1, by-1, bw+2, bh+2)
    local col = laserOn and Color(255,80,50) or (energy > 30 and Color(0,180,255) or Color(255,60,60))
    surface.SetDrawColor(col.r, col.g, col.b, 200)
    surface.DrawRect(bx, by, bw * (energy/ENERGY_MAX), bh)
    draw.SimpleText("ЛАЗЕР  ⚡ "..math.floor(energy), "DermaDefault",
        sw/2, sh-90, laserOn and Color(255,120,80) or Color(50,255,80),
        TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
end

else
    function SWEP:_BuildModels()   end
    function SWEP:_DestroyModels() end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_laser.lua loaded")
