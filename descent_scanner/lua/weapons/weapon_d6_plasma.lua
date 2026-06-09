-- ═══════════════════════════════════════════════════════════
-- weapon_d6_plasma.lua
-- ПЛАЗМА — двойные плазменные болты для Descent 6DOF.
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

-- ─── Модели ────────────────────────────────────────────────
local MDL_AIRBOAT = "models/airboatgun.mdl"
local MDL_NOSEGUN = "models/gibs/gunship_gibs_nosegun.mdl"
local MDL_GRAVGUN = "models/weapons/w_physics.mdl"

-- ─── Хелперы (сервер) ──────────────────────────────────────
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

-- =========================================================
-- ИНИЦИАЛИЗАЦИЯ
-- =========================================================
function SWEP:Initialize()
    self._Models = {}
    self._Slots = {
        { mdl = MDL_AIRBOAT, fwd = 62, rgt = -30, up = -22, pitch =  5, yaw =  14 },
        { mdl = MDL_NOSEGUN, fwd = 58, rgt = -14, up = -23, pitch =  8, yaw =   5 },
        { mdl = MDL_NOSEGUN, fwd = 58, rgt =  14, up = -23, pitch =  8, yaw =  -5 },
        { mdl = MDL_AIRBOAT, fwd = 62, rgt =  30, up = -22, pitch =  5, yaw = -14 },
        { mdl = MDL_GRAVGUN, fwd = 48, rgt =   0, up = -24, pitch = 18, yaw =   0 },
    }
    if CLIENT and IsValid(self:GetOwner()) and self:GetOwner() == LocalPlayer() then
        self:_BuildModels()
    end
end

-- =========================================================
-- DEPLOY / HOLSTER / REMOVE
-- =========================================================
function SWEP:Deploy()
    if CLIENT and IsValid(self:GetOwner()) and self:GetOwner() == LocalPlayer() then
        timer.Simple(0, function()
            if IsValid(self) then self:_BuildModels() end
        end)
    end
    return true
end

function SWEP:OnHolster(ply)
    if CLIENT and IsValid(ply) and ply == LocalPlayer() then
        self:_DestroyModels()
    end
end

function SWEP:OnRemove()
    self:_DestroyModels()
end

-- =========================================================
-- ЭНЕРГИЯ
-- =========================================================
local ENERGY_MAX   = 100
local ENERGY_COST  = 5
local ENERGY_REGEN = 8  -- /sec

-- =========================================================
-- THINK — регенерация энергии
-- =========================================================
function SWEP:Think()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end
    local e = owner:GetNWInt("D6_WepEnergy", ENERGY_MAX)
    if e < ENERGY_MAX then
        owner:SetNWInt("D6_WepEnergy", math.min(ENERGY_MAX, e + ENERGY_REGEN * FrameTime()))
    end
end

-- =========================================================
-- ОСНОВНАЯ АТАКА — 2 плазменных болта
-- =========================================================
function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    local energy = owner:GetNWInt("D6_WepEnergy", ENERGY_MAX)
    if energy < ENERGY_COST then
        owner:EmitSound("buttons/button10.wav", 65, 100)
        return
    end
    owner:SetNWInt("D6_WepEnergy", energy - ENERGY_COST)
    self:SetNextPrimaryFire(CurTime() + 0.35)

    local sa      = ShootAng(owner)
    local fwd     = sa:Forward()
    local hitPos  = owner:GetShootPos() + fwd * 4000

    -- Стреляем из левого (слот 2) и правого (слот 3) nosegun
    local innerSlots = { self._Slots[2], self._Slots[3] }
    for _, slot in ipairs(innerSlots) do
        local src = MuzzleWorld(owner, slot)
        local dir = (hitPos - src):GetNormalized()

        local tr = util.TraceLine({
            start  = src,
            endpos = src + dir * 4000,
            filter = owner,
            mask   = MASK_SHOT,
        })

        -- Урон попаданию
        if IsValid(tr.Entity) and (tr.Entity:IsNPC() or tr.Entity:IsPlayer()) then
            local di = DamageInfo()
            di:SetAttacker(owner)
            di:SetInflictor(self)
            di:SetDamage(28)
            di:SetDamageType(DMG_ENERGYBEAM)
            di:SetDamageForce(dir * 1200)
            tr.Entity:TakeDamageInfo(di)
        end

        -- Эффект в точке попадания
        local ef = EffectData()
        ef:SetOrigin(tr.HitPos)
        ef:SetNormal(tr.HitNormal)
        ef:SetScale(2)
        util.Effect("cball_bounce", ef)

        -- Вспышка у дула
        local mf = EffectData()
        mf:SetOrigin(src)
        mf:SetNormal(dir)
        mf:SetScale(1)
        util.Effect("MuzzleFlash", mf)
    end

    owner:EmitSound("weapons/physcannon/energy_sing_flyby.wav", 70, 140)
end

-- =========================================================
-- ВТОРИЧНАЯ АТАКА — не используется
-- =========================================================
function SWEP:SecondaryAttack() end

-- =========================================================
-- CLIENT: управление ClientsideModel
-- =========================================================
if CLIENT then

    function SWEP:_BuildModels()
        self:_DestroyModels()
        self._Models = {}
        for i, s in ipairs(self._Slots or {}) do
            local m = ClientsideModel(s.mdl, RENDER_GROUP_OTHER)
            if IsValid(m) then
                m:SetNoDraw(true)
                self._Models[i] = m
            end
        end
    end

    function SWEP:_DestroyModels()
        for _, m in ipairs(self._Models or {}) do
            if IsValid(m) then m:Remove() end
        end
        self._Models = {}
    end

    function SWEP:DrawViewModel()
        if not CLIENT then return end
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        if not self._Models or #self._Models == 0 then self:_BuildModels() end

        local ep  = EyePos()
        local ea  = EyeAngles()
        local fwd = ea:Forward()
        local rgt = ea:Right()
        local up  = ea:Up()

        cam.IgnoreZ(true)
        for i, m in ipairs(self._Models) do
            if IsValid(m) then
                local s   = self._Slots[i]
                local pos = ep + fwd * s.fwd + rgt * s.rgt + up * s.up
                local ang = Angle(ea.p + s.pitch, ea.y + s.yaw, ea.r + (s.roll or 0))
                m:SetPos(pos)
                m:SetAngles(ang)
                m:SetupBones()
                m:DrawModel()
            end
        end
        cam.IgnoreZ(false)
    end

    function SWEP:DrawHUD()
        if not CLIENT then return end
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        local energy = ply:GetNWInt("D6_WepEnergy", ENERGY_MAX)
        local sw, sh = ScrW(), ScrH()
        local bw, bh = 140, 6
        local bx, by = sw / 2 - bw / 2, sh - 72
        surface.SetDrawColor(30, 30, 30, 180)
        surface.DrawRect(bx - 1, by - 1, bw + 2, bh + 2)
        local col = energy > 30 and Color(0, 180, 255) or Color(255, 60, 60)
        surface.SetDrawColor(col.r, col.g, col.b, 200)
        surface.DrawRect(bx, by, bw * (energy / ENERGY_MAX), bh)
        draw.SimpleText(
            "ПЛАЗМА  ⚡ " .. math.floor(energy),
            "DermaDefault", sw / 2, sh - 90,
            Color(0, 220, 255), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end

else
    function SWEP:_BuildModels()   end
    function SWEP:_DestroyModels() end
end

function SWEP:DrawWorldModel()             end
function SWEP:DrawWorldModelTranslucent()  end

print("[D6] weapon_d6_plasma.lua loaded")
