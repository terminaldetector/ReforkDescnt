-- ═══════════════════════════════════════════════════════════
-- weapon_d6_rockets.lua
-- РАКЕТЫ — 4 сабрежима для Descent 6DOF.
--   0 = ×1 ПРЯМАЯ   — одиночная ракета
--   1 = ×3 ЗАЛП     — тройной залп
--   2 = ×6 НАВЕДЕНИЕ — 6 наводящихся ракет
--   3 = ☢ АТОМКА   — атомная бомба с огромным AoE
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

-- ─── Модели ────────────────────────────────────────────────
local MDL_BOMB    = "models/props_phx/ww2bomb.mdl"
local MDL_FLAK    = "models/props_phx/misc/flakshell_big.mdl"
local MDL_ROCKET  = "models/weapons/w_rocket.mdl"

-- ─── Имена сабрежимов для HUD ──────────────────────────────
local ROCKET_NAMES = {
    [0] = "x1 ПРЯМАЯ",
    [1] = "x3 ЗАЛП",
    [2] = "x6 НАВЕДЕНИЕ",
    [3] = "* АТОМКА",
}

-- ─── Хелперы (сервер) ──────────────────────────────────────
local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

-- =========================================================
-- ВЗРЫВ РАКЕТЫ
-- =========================================================
local function D6_ExplodeRocket(rkt)
    if not IsValid(rkt) then return end
    local idx = rkt:EntIndex()
    timer.Remove("D6_Rkt_" .. idx)
    hook.Remove("EntityCollision", "D6_Rkt_" .. idx)

    local pos    = rkt:GetPos()
    local owner  = rkt.D6_Owner
    local radius = rkt.D6_Radius or 200
    local dmg    = rkt.D6_Damage or 80

    local ef = EffectData()
    ef:SetOrigin(pos)
    ef:SetScale(radius / 50)
    ef:SetMagnitude(dmg)
    util.Effect("Explosion", ef)
    if dmg > 150 then
        util.Effect("HelicopterMegaBomb", ef)
    end

    for _, e in ipairs(ents.FindInSphere(pos, radius)) do
        if not IsValid(e) then continue end
        if not (e:IsNPC() or e:IsPlayer()) then continue end
        if e == owner then continue end

        local dist = e:GetPos():Distance(pos)
        local di   = DamageInfo()
        di:SetAttacker(IsValid(owner) and owner or game.GetWorld())
        di:SetInflictor(rkt)
        di:SetDamage(dmg * (1 - dist / radius))
        di:SetDamageType(DMG_BLAST)
        di:SetDamageForce((e:GetPos() - pos):GetNormalized() * 5000)
        e:TakeDamageInfo(di)
    end

    rkt:Remove()
end

-- =========================================================
-- СОЗДАНИЕ РАКЕТЫ
-- =========================================================
local function SpawnRocket(owner, pos, dir, speed, dmg, radius, homing, isAtomic)
    local rkt = ents.Create("prop_physics")
    if not IsValid(rkt) then return end
    rkt:SetModel(isAtomic and MDL_FLAK or MDL_ROCKET)
    rkt:SetPos(pos)
    rkt:SetAngles(dir:Angle())
    rkt:Spawn()
    rkt:SetCollisionGroup(COLLISION_GROUP_PROJECTILE)

    rkt.D6_Owner  = owner
    rkt.D6_Damage = dmg
    rkt.D6_Radius = radius
    rkt.D6_Homing = homing
    -- Цель для наводящихся — ближайшее NPC в трейсе из глаз
    if homing then
        local et = owner:GetEyeTrace().Entity
        rkt.D6_Target = (IsValid(et) and (et:IsNPC() or et:IsPlayer())) and et or nil
    end

    local phys = rkt:GetPhysicsObject()
    if IsValid(phys) then
        phys:SetVelocity(dir * speed)
        phys:EnableGravity(false)
        phys:EnableDrag(false)
    end

    local idx = rkt:EntIndex()

    -- Наводящийся таймер
    if homing then
        timer.Create("D6_Rkt_" .. idx, 0.05, 0, function()
            if not IsValid(rkt) then return end
            local ph = rkt:GetPhysicsObject()
            if not IsValid(ph) then return end

            local tgt = rkt.D6_Target
            local targetPos

            if IsValid(tgt) then
                targetPos = tgt:WorldSpaceCenter()
            else
                -- Найти ближайший NPC
                local nearest, nd = nil, 9999
                for _, e in ipairs(ents.FindInSphere(rkt:GetPos(), 2000)) do
                    if IsValid(e) and (e:IsNPC() or e:IsPlayer()) and e ~= owner then
                        local d = e:GetPos():Distance(rkt:GetPos())
                        if d < nd then nd = d; nearest = e end
                    end
                end
                targetPos = nearest and nearest:WorldSpaceCenter() or nil
            end

            if targetPos then
                local curVel = ph:GetVelocity()
                local wantDir = (targetPos - rkt:GetPos()):GetNormalized()
                local newVel = LerpVector(0.08, curVel:GetNormalized(), wantDir) * speed
                ph:SetVelocity(newVel)
                rkt:SetAngles(newVel:Angle())
            end
        end)
    end

    -- Атомная бомба: автовзрыв через 2 секунды
    if isAtomic then
        timer.Simple(2.0, function()
            if IsValid(rkt) then D6_ExplodeRocket(rkt) end
        end)
    end

    -- Коллизия — взрыв
    hook.Add("EntityCollision", "D6_Rkt_" .. idx, function(ent, data)
        if ent == rkt then
            local hit = data.HitEntity
            if IsValid(hit) and hit == rkt.D6_Owner then return end
            D6_ExplodeRocket(rkt)
        end
    end)

    -- Страховочное удаление через 8 секунд
    timer.Simple(8, function()
        if IsValid(rkt) then D6_ExplodeRocket(rkt) end
    end)

    return rkt
end

-- =========================================================
-- ИНИЦИАЛИЗАЦИЯ
-- =========================================================
function SWEP:Initialize()
    self._Models = {}
    self._Slots = {
        { mdl = MDL_BOMB, fwd = 55, rgt = -18, up = -18, pitch = 0, yaw = 0 },
        { mdl = MDL_BOMB, fwd = 55, rgt =  18, up = -18, pitch = 0, yaw = 0 },
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
local ENERGY_COST  = 20
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
-- ВТОРИЧНАЯ АТАКА — переключение сабрежима
-- Используем сетевую строку D6_RocketSubNext из d6_core.lua.
-- =========================================================
function SWEP:SecondaryAttack()
    if not SERVER then return end
    local now = CurTime()
    if now < (self._NextSubSwitch or 0) then return end
    self._NextSubSwitch = now + 0.4

    local cur = self:GetNWInt("D6_RktSub", 0)
    local nxt = (cur + 1) % 4
    self:SetNWInt("D6_RktSub", nxt)

    local owner = self:GetOwner()
    if IsValid(owner) then
        owner:GetActiveWeapon():SetNWInt("D6_RktSub", nxt)
        owner:EmitSound("buttons/button14.wav", 65, 100 + nxt * 10)
    end
end

-- =========================================================
-- ОСНОВНАЯ АТАКА
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
    self:SetNextPrimaryFire(CurTime() + 0.8)

    local sa  = ShootAng(owner)
    local dir = sa:Forward()
    local pos = owner:GetShootPos() + dir * 30
    local sub = self:GetNWInt("D6_RktSub", 0)

    if sub == 0 then
        -- ×1 ПРЯМАЯ
        SpawnRocket(owner, pos, dir, 1800, 80, 200, false, false)

    elseif sub == 1 then
        -- ×3 ЗАЛП — небольшой горизонтальный разброс
        for i = -1, 1 do
            local spread = Angle(0, sa.y + i * 4, 0):Forward()
            SpawnRocket(owner, pos, spread, 1800, 60, 180, false, false)
        end

    elseif sub == 2 then
        -- ×6 НАВЕДЕНИЕ — рой из 6 ракет
        for i = 1, 6 do
            local yOff    = (i - 3.5) * 8
            local sp      = Angle(math.random(-5, 5), sa.y + yOff, 0):Forward()
            local rktPos  = pos + sa:Right() * (i - 3.5) * 10
            SpawnRocket(owner, rktPos, sp, 1400, 50, 150, true, false)
        end

    elseif sub == 3 then
        -- ☢ АТОМКА
        SpawnRocket(owner, pos, dir, 1200, 300, 500, false, true)
    end

    owner:EmitSound("weapons/rpg/rocket1.wav", 75, 100)
end

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
        local sub    = self:GetNWInt("D6_RktSub", 0)
        local sw, sh = ScrW(), ScrH()
        local bw, bh = 140, 6
        local bx, by = sw / 2 - bw / 2, sh - 72
        surface.SetDrawColor(30, 30, 30, 180)
        surface.DrawRect(bx - 1, by - 1, bw + 2, bh + 2)
        local col = energy > 30 and Color(0, 180, 255) or Color(255, 60, 60)
        surface.SetDrawColor(col.r, col.g, col.b, 200)
        surface.DrawRect(bx, by, bw * (energy / ENERGY_MAX), bh)
        -- Имя режима
        local rname = ROCKET_NAMES[sub] or "?"
        draw.SimpleText(
            "РАКЕТЫ  [" .. rname .. "]  ПКМ - сменить",
            "DermaDefault", sw / 2, sh - 90,
            Color(255, 220, 80), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
        draw.SimpleText(
            "E " .. math.floor(energy),
            "DermaDefault", sw / 2, sh - 60,
            col, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end

else
    function SWEP:_BuildModels()   end
    function SWEP:_DestroyModels() end
end

function SWEP:DrawWorldModel()             end
function SWEP:DrawWorldModelTranslucent()  end

print("[D6] weapon_d6_rockets.lua loaded")
