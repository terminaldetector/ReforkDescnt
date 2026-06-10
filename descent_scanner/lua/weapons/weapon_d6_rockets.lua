-- ═══════════════════════════════════════════════════════════
-- weapon_d6_rockets.lua — РАКЕТЫ, 4 сабрежима
--   0 = ×1 ПРЯМАЯ   1 = ×3 ЗАЛП
--   2 = ×6 НАВЕДЕНИЕ   3 = ☢ АТОМКА
-- Рендер моделей (ww2bomb пилоны) — в d6_wepview.lua.
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName       = "РАКЕТЫ"
SWEP.Author          = "Descent 6DOF"
SWEP.Category        = "Descent"
SWEP.Slot            = 4
SWEP.SlotPos         = 0
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

-- Снаряд: СТОКОВАЯ ракета РПГ — ents.Create("rpg_missile").
-- Своя дым-трасса, взрыв при касании, урон (sk_plr_dmg_rpg_round)
-- и звук — всё как у настоящего HL2 RPG (Способ 1 из списка).
local MDL_ROCKET = "models/weapons/w_missile.mdl"
if SERVER then
    util.PrecacheModel(MDL_ROCKET)
    util.PrecacheSound("weapons/rpg/rocketfire1.wav")
    util.PrecacheSound("weapons/rpg/rocket_explode.wav")
end

local ROCKET_NAMES = { [0]="×1 ПРЯМАЯ", [1]="×3 ЗАЛП", [2]="×6 НАВЕДЕНИЕ", [3]="☢ АТОМКА" }

local ENERGY_MAX   = 100
local ENERGY_COST  = 20
local ENERGY_REGEN = 8

local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

-- ── Запуск стоковой ракеты РПГ ───────────────────────────
-- speed    — стартовая скорость (стоковый think далее держит RPG_SPEED)
-- homing   — наведение на ближайшего врага (override velocity каждый тик)
-- isAtomic — после гибели ракеты добавляем мегавзрыв на её позиции
local function SpawnRPGMissile(owner, pos, dir, speed, homing, isAtomic)
    local m = ents.Create("rpg_missile")
    if not IsValid(m) then return end
    m:SetPos(pos)
    m:SetAngles(dir:Angle())
    m:SetOwner(owner)            -- стоковый RocketTouch игнорирует владельца
    m:Spawn()
    m:Activate()
    if isAtomic then m:SetModelScale(2.0, 0) end

    local ph = m:GetPhysicsObject()
    if IsValid(ph) then
        ph:EnableGravity(false)
        ph:SetVelocity(dir * speed)
    end

    local idx = m:EntIndex()

    -- Наведение: каждые 0.05с доворачиваем к ближайшему врагу
    if homing then
        local tid = "D6_RPGHome_" .. idx
        timer.Create(tid, 0.05, 0, function()
            if not IsValid(m) then timer.Remove(tid); return end
            local p2 = m:GetPhysicsObject()
            if not IsValid(p2) then return end
            local nearest, nd = nil, 2200
            for _, e in ipairs(ents.FindInSphere(m:GetPos(), 2200)) do
                if IsValid(e) and (e:IsNPC() or e:IsPlayer()) and e ~= owner then
                    local d = m:GetPos():Distance(e:GetPos())
                    if d < nd then nd = d; nearest = e end
                end
            end
            if nearest then
                local want = (nearest:WorldSpaceCenter() - m:GetPos()):GetNormalized()
                local nv   = LerpVector(0.2, p2:GetVelocity():GetNormalized(), want):GetNormalized()
                p2:SetVelocity(nv * speed)
                m:SetAngles(nv:Angle())
            end
        end)
    end

    -- Атомка: ловим момент взрыва (ent invalid) → мегавзрыв на последней позиции
    if isAtomic then
        local lastPos = m:GetPos()
        local aid = "D6_Atomic_" .. idx
        timer.Create(aid, 0.05, 0, function()
            if IsValid(m) then lastPos = m:GetPos(); return end
            timer.Remove(aid)
            local atk = IsValid(owner) and owner or game.GetWorld()
            local ef  = EffectData(); ef:SetOrigin(lastPos); ef:SetScale(6); ef:SetMagnitude(300)
            util.Effect("HelicopterMegaBomb", ef)
            util.Effect("Explosion", ef)
            util.BlastDamage(atk, atk, lastPos, 500, 250)
            sound.Play("npc/combine_gunship/explosion1.wav", lastPos, 120, 85)
        end)
        timer.Simple(2.5, function() if IsValid(m) then m:Remove() end end)
    end

    -- Общий лимит жизни + очистка таймера наведения
    timer.Simple(8, function()
        timer.Remove("D6_RPGHome_" .. idx)
        if IsValid(m) then m:Remove() end
    end)
    return m
end

function SWEP:Initialize()
    self:SetWeaponHoldType(self.HoldType)
end

function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

function SWEP:Think()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end
    local e = owner:GetNWFloat("D6_WepEnergy", ENERGY_MAX)
    if e < ENERGY_MAX then
        owner:SetNWFloat("D6_WepEnergy", math.min(ENERGY_MAX, e + ENERGY_REGEN * FrameTime()))
    end
end

-- ПКМ = следующий сабрежим (дублируется колесом вниз)
function SWEP:SecondaryAttack()
    if not SERVER then return end
    local now = CurTime()
    if now < (self._NextSub or 0) then return end
    self._NextSub = now + 0.4
    local nxt = (self:GetNWInt("D6_RktSub", 0) + 1) % 4
    self:SetNWInt("D6_RktSub", nxt)
    local owner = self:GetOwner()
    if IsValid(owner) then owner:EmitSound("buttons/button14.wav", 65, 100 + nxt * 10) end
end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    local energy = owner:GetNWFloat("D6_WepEnergy", ENERGY_MAX)
    if energy < ENERGY_COST then
        owner:EmitSound("buttons/button10.wav", 65, 100); return
    end
    owner:SetNWFloat("D6_WepEnergy", energy - ENERGY_COST)
    self:SetNextPrimaryFire(CurTime() + 0.8)

    local sa  = ShootAng(owner)
    local dir = sa:Forward()
    -- Спавн с большим отступом — дрон крупный (scale 2.5), чтобы
    -- стоковая ракета не коснулась собственной модели при запуске.
    local pos = owner:GetShootPos() + dir * 60
    local sub = self:GetNWInt("D6_RktSub", 0)

    if sub == 0 then
        SpawnRPGMissile(owner, pos, dir, 2600, false, false)

    elseif sub == 1 then
        for i = -1, 1 do
            local sp = Angle(sa.p, sa.y + i*5, 0):Forward()
            SpawnRPGMissile(owner, pos + sa:Right() * i * 14, sp, 2400, false, false)
        end

    elseif sub == 2 then
        for i = 1, 6 do
            local yOff   = (i - 3.5) * 7
            local sp     = Angle(sa.p + math.random(-3, 3), sa.y + yOff, 0):Forward()
            local rktPos = pos + sa:Right() * (i - 3.5) * 12
            SpawnRPGMissile(owner, rktPos, sp, 2000, true, false)
        end

    elseif sub == 3 then
        SpawnRPGMissile(owner, pos, dir, 1800, false, true)
    end

    local efShot = EffectData(); efShot:SetOrigin(pos); efShot:SetNormal(dir)
    util.Effect("RPGShot", efShot)
    owner:EmitSound("weapons/rpg/rocketfire1.wav", 80, 100)
end

if CLIENT then
    function SWEP:DrawHUD()
        local ply = self:GetOwner()
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        local energy = ply:GetNWFloat("D6_WepEnergy", ENERGY_MAX)
        local sub    = self:GetNWInt("D6_RktSub", 0)
        local sw, sh = ScrW(), ScrH()
        local bw, bh = 140, 6
        local bx, by = sw/2 - bw/2, sh - 72
        surface.SetDrawColor(30, 30, 30, 180); surface.DrawRect(bx-1, by-1, bw+2, bh+2)
        local col = energy > 30 and Color(0,180,255) or Color(255,60,60)
        surface.SetDrawColor(col.r, col.g, col.b, 200)
        surface.DrawRect(bx, by, bw * (energy/ENERGY_MAX), bh)
        draw.SimpleText("РАКЕТЫ ["..(ROCKET_NAMES[sub] or "?").."]  ПКМ/колесо вниз — сменить",
            "DermaDefault", sw/2, sh-90, Color(255,220,80), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
        draw.SimpleText("⚡ "..math.floor(energy), "DermaDefault",
            sw/2, sh-60, col, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_rockets.lua loaded")
