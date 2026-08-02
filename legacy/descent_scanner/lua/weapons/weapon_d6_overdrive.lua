-- ═══════════════════════════════════════════════════════════
-- weapon_d6_overdrive.lua — ОВЕРДРАЙВ-ЛУЧ (ультимейт, фаза босса)
--
--   Cat G: НЕ балансируется как обычное оружие. Непрерывный хитскан-
--   луч, урон растёт с удержанием (25→80 за 2 с разгона). Снимает
--   щиты дронов ускоренно (shield-strip). Бешеный дрейн (~37/с) —
--   опустошает резерв за ~3 с. Кинематографичная фаза добивания.
--
--   Фреймворк: d6_weapon_core (Muzzle/ShootAng/DirectDamage),
--   d6_energy (TryConsume), d6_shield (energy-резист + прямой съём
--   слоя D6_Shield дрона), d6_weapon_registry (Ultimate).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "ОВЕРДРАЙВ-ЛУЧ"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 1
SWEP.SlotPos        = 0
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
SWEP.Primary.Automatic     = true
SWEP.Primary.Ammo          = "none"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

if SERVER then
    util.PrecacheSound("ambient/energy/weld1.wav")
end

local DRAIN        = 3        -- энергии за тик луча
local BEAM_INT     = 0.08
local RAMP_TIME    = 2.0
local BASE_DMG     = 25
local MAX_DMG      = 80
local RANGE        = 9000
local SHIELD_STRIP = 12       -- доп. съём слоя щита дрона за тик
local MUZZLE       = { fwd = 30, rgt = 0, up = -4 }

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
    self._Heat = 0
    if CLIENT then self:_StartBeam() end
end

function SWEP:Deploy()
    if CLIENT then self:_StartBeam() end
    return true
end

function SWEP:Holster()
    if SERVER then
        local o = self:GetOwner()
        if IsValid(o) then o:SetNWBool("D6_OverFire", false) end
    end
    if CLIENT then self:_StopBeam() end
    return true
end

function SWEP:OnRemove()
    if CLIENT then self:_StopBeam() end
end

function SWEP:PrimaryAttack() end  -- стрельбой управляет Think

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.6)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

function SWEP:Think()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    local ct, ft = CurTime(), FrameTime()
    local holding = owner:KeyDown(IN_ATTACK)

    if holding then
        self._Heat = math.min(1, (self._Heat or 0) + ft / RAMP_TIME)
    else
        self._Heat = math.max(0, (self._Heat or 0) - ft / RAMP_TIME * 1.5)
    end
    owner:SetNWFloat("D6_OverHeat", self._Heat)

    if holding and ct >= (self._NextBeam or 0) then
        if D6_Energy.TryConsume(owner, "weapons", DRAIN) then
            self._NextBeam = ct + BEAM_INT

            local sa  = D6_Wep.ShootAng(owner)
            local dir = sa:Forward()
            local src = D6_Wep.Muzzle(owner, MUZZLE)
            local tr  = util.TraceLine({ start = src, endpos = src + dir * RANGE,
                filter = owner, mask = MASK_SHOT })

            local hitEnt = tr.Entity
            if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
                local dmg = Lerp(self._Heat, BASE_DMG, MAX_DMG)
                D6_Wep.DirectDamage(owner, owner, hitEnt, dmg,
                    D6_DAMAGE.energy.dmgType, dir * 2000)
                -- Shield-strip: melt the drone shield layer directly.
                if hitEnt.D6_Shield and hitEnt.D6_Shield > 0 then
                    hitEnt.D6_Shield = math.max(0, hitEnt.D6_Shield - SHIELD_STRIP)
                end
                local ef = EffectData(); ef:SetOrigin(tr.HitPos); ef:SetNormal(tr.HitNormal or -dir)
                util.Effect("AR2Impact", ef)
            end

            owner:SetNWVector("D6_OverMuz", src)
            owner:SetNWVector("D6_OverHit", tr.HitPos)
            owner:SetNWBool("D6_OverFire", true)
            if not owner.D6_OverSnd or ct > owner.D6_OverSnd then
                owner:EmitSound("ambient/energy/weld1.wav", 80, 90)
                owner.D6_OverSnd = ct + 0.4
            end
        else
            owner:SetNWBool("D6_OverFire", false)  -- резерв пуст
        end
    elseif not holding then
        owner:SetNWBool("D6_OverFire", false)
    end
end

if CLIENT then
    local BEAM_MAT = Material("cable/xbeam")

    function SWEP:_StartBeam()
        local key = "D6_OverBeam_" .. self:EntIndex()
        local wep = self
        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            if not IsValid(wep) then hook.Remove("PostDrawTranslucentRenderables", key); return end
            local owner = wep:GetOwner()
            if not IsValid(owner) or not owner:GetNWBool("D6_OverFire", false) then return end
            local muz  = owner:GetNWVector("D6_OverMuz", owner:GetShootPos())
            local hit  = owner:GetNWVector("D6_OverHit", muz)
            local heat = owner:GetNWFloat("D6_OverHeat", 0)
            local w    = Lerp(heat, 8, 24)
            render.SetMaterial(BEAM_MAT)
            render.DrawBeam(muz, hit, w,       0, 1, Color(255, Lerp(heat, 180, 60), 40, 235))
            render.DrawBeam(muz, hit, w * 0.4, 0, 1, Color(255, 255, 210, 235))
        end)
    end

    function SWEP:_StopBeam()
        hook.Remove("PostDrawTranslucentRenderables", "D6_OverBeam_" .. self:EntIndex())
    end

    function SWEP:DrawHUD()
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        D6_Wep.DrawEnergyHUD(ply, "ОВЕРДРАЙВ-ЛУЧ", Color(255, 120, 40))
        local heat = ply:GetNWFloat("D6_OverHeat", 0)
        if heat > 0.01 then
            local sw, sh = ScrW(), ScrH()
            local bw, bh = 140, 5
            local bx, by = sw / 2 - bw / 2, sh - 80
            surface.SetDrawColor(40, 20, 10, 180); surface.DrawRect(bx - 1, by - 1, bw + 2, bh + 2)
            surface.SetDrawColor(255, Lerp(heat, 180, 60), 40, 230)
            surface.DrawRect(bx, by, bw * heat, bh)
            if heat >= 0.99 then
                draw.SimpleText("⚡ ОВЕРДРАЙВ", "DermaDefault", sw / 2, by - 6,
                    Color(255, 230, 120), TEXT_ALIGN_CENTER, TEXT_ALIGN_BOTTOM)
            end
        end
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_overdrive.lua loaded")
