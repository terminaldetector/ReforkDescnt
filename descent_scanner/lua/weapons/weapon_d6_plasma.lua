-- ═══════════════════════════════════════════════════════════
-- weapon_d6_plasma.lua — ПЛАЗМА, видимые болты из HL2-моделей
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName       = "ПЛАЗМА"
SWEP.Author          = "Descent 6DOF"
SWEP.Category        = "Descent"
SWEP.Slot            = 2
SWEP.SlotPos         = 1
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

local MDL_AIRBOAT = "models/airboatgun.mdl"
local MDL_NOSEGUN = "models/gibs/gunship_gibs_nosegun.mdl"
local MDL_GRAVGUN = "models/weapons/w_physics.mdl"
-- Видимый снаряд: тёмно-энергетический шар из HL2 (стандартный файл)
local MDL_BOLT    = "models/items/ar2_grenade.mdl"

local ENERGY_MAX   = 100
local ENERGY_COST  = 5
local ENERGY_REGEN = 8
local BOLT_SPEED   = 2200
local BOLT_DMG     = 28

local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end
local function MuzzleWorld(ply, slot)
    local ang = ShootAng(ply)
    return ply:GetShootPos()
        + ang:Forward() * (slot.fwd + 10)
        + ang:Right()   * slot.rgt
        + ang:Up()      * slot.up
end

local SLOTS = {
    { mdl=MDL_AIRBOAT, fwd=42, rgt=-34, up=-30, pitch= 8, yaw= 15 },
    { mdl=MDL_NOSEGUN, fwd=40, rgt=-15, up=-32, pitch= 7, yaw=  6 },
    { mdl=MDL_NOSEGUN, fwd=40, rgt= 15, up=-32, pitch= 7, yaw= -6 },
    { mdl=MDL_AIRBOAT, fwd=42, rgt= 34, up=-30, pitch= 8, yaw=-15 },
    { mdl=MDL_GRAVGUN, fwd=38, rgt=  0, up=-24, pitch=20, yaw=  0 },
}

-- ── Видимый плазменный болт (prop_physics с HL2 моделью) ──
local function SpawnPlasmaBolt(owner, pos, dir)
    if not SERVER then return end
    local bolt = ents.Create("prop_physics")
    if not IsValid(bolt) then return end
    bolt:SetModel(MDL_BOLT)
    bolt:SetPos(pos)
    bolt:SetAngles(dir:Angle())
    bolt:SetOwner(owner)
    bolt:Spawn()
    bolt:SetCollisionGroup(COLLISION_GROUP_PROJECTILE)
    bolt:SetColor(Color(50, 200, 255, 220))
    bolt:SetRenderMode(RENDERMODE_TRANSADD)
    bolt:SetMaterial("models/effects/combineball")

    local phys = bolt:GetPhysicsObject()
    if IsValid(phys) then
        phys:SetVelocity(dir * BOLT_SPEED)
        phys:EnableGravity(false)
        phys:EnableDrag(false)
    end

    bolt.D6_Owner = owner
    local idx = tostring(bolt:EntIndex())

    hook.Add("EntityCollision", "D6_Bolt_"..idx, function(ent, data)
        if ent ~= bolt then return end
        if IsValid(data.HitEntity) and data.HitEntity == bolt.D6_Owner then return end

        if IsValid(data.HitEntity) and (data.HitEntity:IsNPC() or data.HitEntity:IsPlayer()) then
            local di = DamageInfo()
            di:SetAttacker(IsValid(bolt.D6_Owner) and bolt.D6_Owner or game.GetWorld())
            di:SetInflictor(bolt)
            di:SetDamage(BOLT_DMG)
            di:SetDamageType(DMG_ENERGYBEAM)
            di:SetDamageForce(dir * 1500)
            data.HitEntity:TakeDamageInfo(di)
        end

        local ef = EffectData(); ef:SetOrigin(bolt:GetPos()); ef:SetNormal(-dir); ef:SetScale(2)
        util.Effect("cball_bounce", ef)

        hook.Remove("EntityCollision", "D6_Bolt_"..idx)
        timer.Remove("D6_Bolt_"..idx)
        if IsValid(bolt) then bolt:Remove() end
    end)

    -- Страховочное удаление через 4 секунды
    timer.Create("D6_Bolt_"..idx, 4, 1, function()
        hook.Remove("EntityCollision", "D6_Bolt_"..idx)
        if IsValid(bolt) then bolt:Remove() end
    end)
end

-- =========================================================
function SWEP:Initialize()
    self._Models = {}
    self._Slots  = SLOTS
    if CLIENT and IsValid(self:GetOwner()) and self:GetOwner() == LocalPlayer() then
        self:_BuildModels()
    end
end

function SWEP:Deploy()
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
    self:_DestroyModels()
end

function SWEP:Think()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end
    local e = owner:GetNWInt("D6_WepEnergy", ENERGY_MAX)
    if e < ENERGY_MAX then
        owner:SetNWInt("D6_WepEnergy", math.min(ENERGY_MAX, e + ENERGY_REGEN * FrameTime()))
    end
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    local energy = owner:GetNWInt("D6_WepEnergy", ENERGY_MAX)
    if energy < ENERGY_COST then
        owner:EmitSound("buttons/button10.wav", 65, 100); return
    end
    owner:SetNWInt("D6_WepEnergy", energy - ENERGY_COST)
    self:SetNextPrimaryFire(CurTime() + 0.35)

    local sa  = ShootAng(owner)
    local fwd = sa:Forward()

    -- Оба болта конвергируют к центру прицела
    local aim = owner:GetShootPos() + fwd * 4000
    local slots = self._Slots or SLOTS
    for _, idx in ipairs({2, 3}) do
        local slot = slots[idx]
        local src  = MuzzleWorld(owner, slot)
        local dir  = (aim - src):GetNormalized()
        SpawnPlasmaBolt(owner, src, dir)
        local mf = EffectData(); mf:SetOrigin(src); mf:SetNormal(dir); mf:SetScale(1)
        util.Effect("MuzzleFlash", mf)
    end

    owner:EmitSound("weapons/physcannon/energy_sing_flyby.wav", 70, 140)
end

function SWEP:SecondaryAttack() end

-- =========================================================
if CLIENT then

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
    local energy = ply:GetNWInt("D6_WepEnergy", ENERGY_MAX)
    local sw, sh = ScrW(), ScrH()
    local bw, bh = 140, 6
    local bx, by = sw/2 - bw/2, sh - 72
    surface.SetDrawColor(30, 30, 30, 180); surface.DrawRect(bx-1, by-1, bw+2, bh+2)
    local col = energy > 30 and Color(0,180,255) or Color(255,60,60)
    surface.SetDrawColor(col.r, col.g, col.b, 200)
    surface.DrawRect(bx, by, bw * (energy/ENERGY_MAX), bh)
    draw.SimpleText("ПЛАЗМА  ⚡ "..math.floor(energy), "DermaDefault",
        sw/2, sh-90, Color(0,220,255), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
end

else
    function SWEP:_BuildModels()   end
    function SWEP:_DestroyModels() end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_plasma.lua loaded")
