-- ═══════════════════════════════════════════════════════════
-- weapon_d6_rockets.lua — РАКЕТЫ, 4 сабрежима
--   0 = ×1 ПРЯМАЯ   1 = ×3 ЗАЛП
--   2 = ×6 НАВЕДЕНИЕ   3 = ☢ АТОМКА
--   Умное наведение: 60% вероятность на цель с наибольшим HP,
--   ×6 режим — распределяет цели между ракетами.
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

-- ── Умный выбор цели ────────────────────────────────────────
-- Возвращает список врагов, отсортированных по HP (убывание).
local function GatherTargets(origin, range, owner)
    local out = {}
    for _, e in ipairs(ents.FindInSphere(origin, range)) do
        if IsValid(e) and (e:IsNPC() or e:IsPlayer()) and e ~= owner then
            out[#out + 1] = { ent = e, hp = e:Health() }
        end
    end
    table.sort(out, function(a, b) return a.hp > b.hp end)
    return out
end

-- 60% на наибольший HP, 40% равномерно по остальным
local function SelectTarget(targets)
    if #targets == 0 then return nil end
    if math.random() < 0.6 then return targets[1].ent end
    return targets[math.random(1, #targets)].ent
end

-- Для залпа из N ракет: распределяем цели циклически
local function AssignTargets(targets, count)
    local out = {}
    if #targets == 0 then
        for i = 1, count do out[i] = nil end
        return out
    end
    -- Первая цель — высокоприоритетная (60% берут её)
    for i = 1, count do
        if math.random() < 0.6 then
            out[i] = targets[1].ent
        else
            out[i] = targets[((i - 1) % #targets) + 1].ent
        end
    end
    return out
end

-- ── Запуск ракеты РПГ ───────────────────────────────────────
local function SpawnRPGMissile(owner, pos, dir, speed, homingTarget, isAtomic)
    local m = ents.Create("rpg_missile")
    if not IsValid(m) then return end
    m:SetPos(pos)
    m:SetAngles(dir:Angle())
    m:SetOwner(owner)
    m:Spawn()

    if isAtomic then m:SetModelScale(2.8, 0) end

    local ph = m:GetPhysicsObject()
    if IsValid(ph) then
        ph:EnableGravity(false)
        ph:EnableDrag(false)
        ph:SetMass(5)
        -- Наследование импульса корабля при пуске (связь с движением)
        ph:SetVelocity(dir * speed + D6_Wep.ShipVel(owner) * D6_Wep.Inherit())
        ph:Wake()
    end
    m:Activate()

    local idx = m:EntIndex()

    -- Наведение на конкретную (или ближайшую) цель
    if homingTarget ~= false then
        local tid = "D6_RPGHome_" .. idx
        timer.Create(tid, 0.05, 0, function()
            if not IsValid(m) then timer.Remove(tid); return end
            local p2 = m:GetPhysicsObject()
            if not IsValid(p2) then return end

            -- Если назначенная цель недоступна — переключиться на ближайшую
            local target = (IsValid(homingTarget) and homingTarget) or nil
            if not target then
                local nd = 2400
                for _, e in ipairs(ents.FindInSphere(m:GetPos(), 2400)) do
                    if IsValid(e) and (e:IsNPC() or e:IsPlayer()) and e ~= owner then
                        local d = m:GetPos():Distance(e:GetPos())
                        if d < nd then nd = d; target = e end
                    end
                end
            end

            if target then
                local dist   = m:GetPos():Distance(target:WorldSpaceCenter())
                -- Наводимость растёт по мере сближения (резче в конце)
                local steer  = math.Clamp(0.12 + (1 - dist / 2400) * 0.25, 0.12, 0.35)
                local want   = (target:WorldSpaceCenter() - m:GetPos()):GetNormalized()
                local curVel = p2:GetVelocity():GetNormalized()
                local nv     = LerpVector(steer, curVel, want):GetNormalized()
                p2:SetVelocity(nv * speed)
                m:SetAngles(nv:Angle())
            end
        end)
    end

    -- Атомный взрыв при гибели ракеты
    if isAtomic then
        local lastPos = m:GetPos()
        local aid = "D6_Atomic_" .. idx
        timer.Create(aid, 0.05, 0, function()
            if IsValid(m) then lastPos = m:GetPos(); return end
            timer.Remove(aid)
            local atk = IsValid(owner) and owner or game.GetWorld()
            local ef  = EffectData(); ef:SetOrigin(lastPos); ef:SetScale(8); ef:SetMagnitude(400)
            util.Effect("HelicopterMegaBomb", ef)
            util.Effect("Explosion", ef)
            util.BlastDamage(atk, atk, lastPos, 650, 350)
            sound.Play("npc/combine_gunship/explosion1.wav", lastPos, 125, 80)
        end)
        timer.Simple(3.0, function() if IsValid(m) then m:Remove() end end)
    end

    timer.Simple(9, function()
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

-- Реген энергии теперь централизован в d6_energy.lua (D6_Energy.RegenTick).

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

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 65, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + 0.8)

    local sa  = ShootAng(owner)
    local dir = sa:Forward()
    local full = owner.D6AngSynced or owner.D6Ang or owner:EyeAngles()
    local pos = owner:GetShootPos() + dir * 60
    local sub = self:GetNWInt("D6_RktSub", 0)

    if sub == 0 then
        SpawnRPGMissile(owner, pos, dir, 2800, false, false)

    elseif sub == 1 then
        for i = -1, 1 do
            local sp = Angle(sa.p, sa.y + i*5, 0):Forward()
            SpawnRPGMissile(owner, pos + full:Right() * i * 14, sp, 2600, false, false)
        end

    elseif sub == 2 then
        local targets = GatherTargets(pos, 2600, owner)
        local assigned = AssignTargets(targets, 6)
        for i = 1, 6 do
            local yOff   = (i - 3.5) * 7
            local sp     = Angle(sa.p + math.random(-3, 3), sa.y + yOff, 0):Forward()
            local rktPos = pos + full:Right() * (i - 3.5) * 12
            SpawnRPGMissile(owner, rktPos, sp, 2200, assigned[i] or false, false)
        end

    elseif sub == 3 then
        SpawnRPGMissile(owner, pos, dir, 2000, false, true)
    end

    -- Отдача пуска толкает корабль назад (атомка — сильнее всего)
    local RECOIL = { [0] = 120, [1] = 150, [2] = 130, [3] = 220 }
    D6_Wep.ApplyRecoil(owner, dir, RECOIL[sub] or 120)

    local efShot = EffectData(); efShot:SetOrigin(pos); efShot:SetNormal(dir)
    util.Effect("RPGShot", efShot)
    owner:EmitSound("weapons/rpg/rocketfire1.wav", 85, 100)
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
