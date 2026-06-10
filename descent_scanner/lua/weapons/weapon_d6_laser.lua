-- ═══════════════════════════════════════════════════════════
-- weapon_d6_laser.lua — ЛАЗЕР, удержание ЛКМ = непрерывный луч
-- Рендер модели (strider_weapon) — в d6_wepview.lua.
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

if SERVER and not _D6_LASER_NET then
    util.AddNetworkString("D6_LaserBeam")
    _D6_LASER_NET = true
end

local ENERGY_MAX   = 100
local ENERGY_REGEN = 8
local LASER_DRAIN  = 12    -- энергии в секунду при стрельбе
local LASER_DPS    = 200   -- урона в секунду

local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
end

function SWEP:Deploy()
    if SERVER then self:SetNWBool("D6_LaserOn", false) end
    return true
end

function SWEP:Holster()
    if SERVER then self:SetNWBool("D6_LaserOn", false) end
    return true
end

function SWEP:OnRemove()
    if SERVER then self:SetNWBool("D6_LaserOn", false) end
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    self:SetNWBool("D6_LaserOn", true)
    self:SetNextPrimaryFire(CurTime() + 9999)  -- темп ведёт Think
end

function SWEP:Think()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    local laserOn = self:GetNWBool("D6_LaserOn", false)

    if laserOn and not owner:KeyDown(IN_ATTACK) then
        self:SetNWBool("D6_LaserOn", false); laserOn = false
    end

    local e  = owner:GetNWFloat("D6_WepEnergy", ENERGY_MAX)
    local ft = FrameTime()

    if laserOn then
        if e <= 0 then
            self:SetNWBool("D6_LaserOn", false); laserOn = false
        else
            owner:SetNWFloat("D6_WepEnergy", math.max(0, e - LASER_DRAIN * ft))

            local sa  = ShootAng(owner)
            local src = owner:GetShootPos()
            local dir = sa:Forward()
            local tr  = util.TraceLine({ start=src, endpos=src + dir*8000, filter=owner, mask=MASK_SHOT })

            if IsValid(tr.Entity) and (tr.Entity:IsNPC() or tr.Entity:IsPlayer()) then
                local di = DamageInfo()
                di:SetAttacker(owner); di:SetInflictor(self)
                di:SetDamage(LASER_DPS * ft)
                di:SetDamageType(DMG_ENERGYBEAM)
                di:SetDamageForce(dir * 400)
                tr.Entity:TakeDamageInfo(di)
            end

            -- Шлём клиенту позицию (не чаще 20/с)
            if CurTime() >= (self._NextBeamNet or 0) then
                self._NextBeamNet = CurTime() + 0.05
                net.Start("D6_LaserBeam")
                    net.WriteBool(true); net.WriteVector(tr.HitPos)
                net.Send(owner)
                self._BeamWasOn = true
            end
        end
    end

    if not laserOn then
        if e < ENERGY_MAX then
            owner:SetNWFloat("D6_WepEnergy", math.min(ENERGY_MAX, e + ENERGY_REGEN * ft))
        end
        -- "выключен" шлём один раз, не каждый тик
        if self._BeamWasOn then
            self._BeamWasOn = false
            net.Start("D6_LaserBeam")
                net.WriteBool(false); net.WriteVector(vector_origin)
            net.Send(owner)
        end
    end
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end
    self:SetNextSecondaryFire(CurTime() + 0.8)
    local rkt = owner:GetWeapon("weapon_d6_rockets")
    if IsValid(rkt) then rkt:PrimaryAttack() end
end

if CLIENT then

    local _LaserActive = false
    local _LaserHitPos = Vector()
    local MAT_LASER    = Material("sprites/laserbeam")
    local MAT_GLOW     = Material("sprites/light_glow02_add")

    net.Receive("D6_LaserBeam", function()
        _LaserActive = net.ReadBool()
        _LaserHitPos = net.ReadVector()
    end)

    hook.Add("PostDrawTranslucentRenderables", "D6_LaserBeamDraw", function(bDepth, bSky)
        if bDepth or bSky or not _LaserActive then return end
        local ply = LocalPlayer()
        if not IsValid(ply) then return end
        local wep = ply:GetActiveWeapon()
        if not IsValid(wep) or wep:GetClass() ~= "weapon_d6_laser" then
            _LaserActive = false; return
        end
        -- Старт луча — от дула страйдер-пушки (центр-низ экрана)
        local ea  = EyeAngles()
        local src = EyePos() + ea:Forward() * 50 + ea:Up() * -16

        render.SetMaterial(MAT_LASER)
        render.DrawBeam(src, _LaserHitPos, 6, 0, 1, Color(255, 50, 50, 240))
        render.DrawBeam(src, _LaserHitPos, 2, 0, 1, Color(255, 220, 220, 180))
        render.SetMaterial(MAT_GLOW)
        render.DrawSprite(_LaserHitPos, 26, 26, Color(255, 80, 50, 220))
    end)

    function SWEP:DrawHUD()
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        local energy  = ply:GetNWFloat("D6_WepEnergy", ENERGY_MAX)
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
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_laser.lua loaded")
