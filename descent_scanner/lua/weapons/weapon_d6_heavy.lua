-- ═══════════════════════════════════════════════════════════
-- weapon_d6_heavy.lua — ТЯЖЁЛЫЙ, медленный шар + AoE
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName       = "ТЯЖЁЛЫЙ"
SWEP.Author          = "Descent 6DOF"
SWEP.Category        = "Descent"
SWEP.Slot            = 2
SWEP.SlotPos         = 2
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
-- Видимый снаряд: тот же HL2 energy-шар, но оранжевый + медленный
local MDL_ORB     = "models/items/ar2_grenade.mdl"

local ENERGY_MAX   = 100
local ENERGY_COST  = 15
local ENERGY_REGEN = 8
local ORB_SPEED    = 900
local ORB_DMG      = 70
local ORB_RADIUS   = 180
local AOE_DMG      = 50

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
    { mdl=MDL_AIRBOAT, fwd=42, rgt=-34, up=-28, pitch= 8, yaw= 16 },
    { mdl=MDL_NOSEGUN, fwd=40, rgt=-16, up=-30, pitch= 7, yaw=  6 },
    { mdl=MDL_NOSEGUN, fwd=40, rgt= 16, up=-30, pitch= 7, yaw= -6 },
    { mdl=MDL_AIRBOAT, fwd=42, rgt= 34, up=-28, pitch= 8, yaw=-16 },
    { mdl=MDL_GRAVGUN, fwd=36, rgt=  0, up=-22, pitch=22, yaw=  0 },
}

-- ── Медленный орб с AoE при столкновении ─────────────────
local function SpawnHeavyOrb(owner, pos, dir)
    if not SERVER then return end
    local orb = ents.Create("prop_physics")
    if not IsValid(orb) then return end
    orb:SetModel(MDL_ORB)
    orb:SetPos(pos)
    orb:SetAngles(dir:Angle())
    orb:SetOwner(owner)
    orb:Spawn()
    orb:SetCollisionGroup(COLLISION_GROUP_PROJECTILE)
    orb:SetColor(Color(255, 120, 20, 230))
    orb:SetRenderMode(RENDERMODE_TRANSADD)
    orb:SetMaterial("models/effects/combineball")
    orb:SetModelScale(1.8, 0)

    local phys = orb:GetPhysicsObject()
    if IsValid(phys) then
        phys:SetVelocity(dir * ORB_SPEED)
        phys:EnableGravity(false)
        phys:EnableDrag(false)
    end

    orb.D6_Owner = owner
    local idx = tostring(orb:EntIndex())

    local function Explode()
        if not IsValid(orb) then return end
        hook.Remove("EntityCollision", "D6_Orb_"..idx)
        timer.Remove("D6_Orb_"..idx)
        local hitPos = orb:GetPos()

        -- Прямой урон
        local ef = EffectData(); ef:SetOrigin(hitPos); ef:SetScale(4); ef:SetMagnitude(ORB_DMG)
        util.Effect("Explosion", ef)

        -- AoE
        for _, e in ipairs(ents.FindInSphere(hitPos, ORB_RADIUS)) do
            if not IsValid(e) then continue end
            if not (e:IsNPC() or e:IsPlayer()) then continue end
            if e == orb.D6_Owner then continue end
            local dist = e:GetPos():Distance(hitPos)
            local dmg  = ORB_DMG + AOE_DMG * (1 - dist / ORB_RADIUS)
            local di   = DamageInfo()
            di:SetAttacker(IsValid(orb.D6_Owner) and orb.D6_Owner or game.GetWorld())
            di:SetInflictor(orb)
            di:SetDamage(dmg)
            di:SetDamageType(DMG_BLAST + DMG_ENERGYBEAM)
            di:SetDamageForce((e:GetPos() - hitPos):GetNormalized() * 6000)
            e:TakeDamageInfo(di)
        end

        orb:Remove()
    end

    hook.Add("EntityCollision", "D6_Orb_"..idx, function(ent, data)
        if ent ~= orb then return end
        if IsValid(data.HitEntity) and data.HitEntity == orb.D6_Owner then return end
        Explode()
    end)

    timer.Create("D6_Orb_"..idx, 5, 1, function() Explode() end)
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
    self:SetNextPrimaryFire(CurTime() + 1.0)

    local sa   = ShootAng(owner)
    local fwd  = sa:Forward()
    local slots = self._Slots or SLOTS
    local src  = MuzzleWorld(owner, slots[5])  -- из гравипушки в центре

    SpawnHeavyOrb(owner, src, fwd)

    local mf = EffectData(); mf:SetOrigin(src); mf:SetNormal(fwd); mf:SetScale(3)
    util.Effect("cball_explode", mf)

    owner:EmitSound("weapons/physcannon/superphys_launch1.wav", 80, 75)
    owner:EmitSound("ambient/explosions/explode_4.wav", 70, 90)
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
    draw.SimpleText("ТЯЖЁЛЫЙ  ⚡ "..math.floor(energy), "DermaDefault",
        sw/2, sh-90, Color(220,80,40), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
end

else
    function SWEP:_BuildModels()   end
    function SWEP:_DestroyModels() end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_heavy.lua loaded")
