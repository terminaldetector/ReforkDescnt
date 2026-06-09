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

-- Ракета: w_missile.mdl — настоящий RPG-снаряд HL2
-- Атомка: flakshell_big.mdl — кассетный заряд
local MDL_ROCKET = "models/weapons/w_missile.mdl"
local MDL_ATOMIC = "models/props_phx/misc/flakshell_big.mdl"
if SERVER then
    util.PrecacheModel(MDL_ROCKET)
    util.PrecacheModel(MDL_ATOMIC)
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

local function EnsurePhys(ent)
    local ph = ent:GetPhysicsObject()
    if IsValid(ph) then return ph end
    ent:PhysicsInitSphere(8, "metal")
    ent:SetMoveType(MOVETYPE_VPHYSICS)
    return ent:GetPhysicsObject()
end

-- ── Взрыв ────────────────────────────────────────────────
local function D6_ExplodeRocket(rkt)
    if not IsValid(rkt) then return end
    local idx = tostring(rkt:EntIndex())
    timer.Remove("D6_Rkt_"..idx)
    hook.Remove("EntityCollision", "D6_Rkt_"..idx)

    local pos    = rkt:GetPos()
    local owner  = rkt.D6_Owner
    local radius = rkt.D6_Radius or 200
    local dmg    = rkt.D6_Damage or 80

    local ef = EffectData(); ef:SetOrigin(pos); ef:SetScale(radius/50); ef:SetMagnitude(dmg)
    util.Effect("Explosion", ef)
    if dmg > 150 then
        util.Effect("HelicopterMegaBomb", ef)
        rkt:EmitSound("npc/combine_gunship/explosion1.wav", 110, 90)
    else
        rkt:EmitSound("weapons/rpg/rocket_explode.wav", 100, 100)
    end

    for _, e in ipairs(ents.FindInSphere(pos, radius)) do
        if not IsValid(e) then continue end
        if not (e:IsNPC() or e:IsPlayer()) then continue end
        if e == owner then continue end
        local dist = e:GetPos():Distance(pos)
        local di   = DamageInfo()
        di:SetAttacker(IsValid(owner) and owner or game.GetWorld())
        di:SetInflictor(rkt)
        di:SetDamage(dmg * (1 - dist/radius))
        di:SetDamageType(DMG_BLAST)
        di:SetDamageForce((e:GetPos()-pos):GetNormalized() * 5000)
        e:TakeDamageInfo(di)
    end
    rkt:Remove()
end

-- ── Создание ракеты ──────────────────────────────────────
local function SpawnRocket(owner, pos, dir, speed, dmg, radius, homing, isAtomic)
    local rkt = ents.Create("prop_physics")
    if not IsValid(rkt) then return end
    rkt:SetModel(isAtomic and MDL_ATOMIC or MDL_ROCKET)
    rkt:SetPos(pos)
    rkt:SetAngles(dir:Angle())
    rkt:SetOwner(owner)
    rkt:Spawn()
    rkt:SetCollisionGroup(COLLISION_GROUP_PROJECTILE)

    -- След: дым для обычных, жёлтый луч для атомки
    if isAtomic then
        rkt:SetModelScale(1.6, 0)
        util.SpriteTrail(rkt, 0, Color(255, 220, 60), false, 30, 2, 0.8, 1/32*0.5, "trails/laser.vmt")
    else
        util.SpriteTrail(rkt, 0, Color(220, 220, 220), false, 12, 1, 0.6, 1/13*0.5, "trails/smoke.vmt")
    end

    local phys = EnsurePhys(rkt)
    if IsValid(phys) then
        phys:SetVelocity(dir * speed)
        phys:EnableGravity(false)
        phys:EnableDrag(false)
    end

    rkt.D6_Owner  = owner
    rkt.D6_Damage = dmg
    rkt.D6_Radius = radius

    local idx = tostring(rkt:EntIndex())

    -- Наведение: ближайший NPC в радиусе 2000
    if homing then
        timer.Create("D6_Rkt_"..idx, 0.05, 0, function()
            if not IsValid(rkt) then return end
            local ph = rkt:GetPhysicsObject()
            if not IsValid(ph) then return end
            local nearest, nd = nil, 2000
            for _, e in ipairs(ents.FindInSphere(rkt:GetPos(), 2000)) do
                if IsValid(e) and (e:IsNPC() or e:IsPlayer()) and e ~= owner then
                    local d = rkt:GetPos():Distance(e:GetPos())
                    if d < nd then nd = d; nearest = e end
                end
            end
            if nearest then
                local want   = (nearest:WorldSpaceCenter() - rkt:GetPos()):GetNormalized()
                local newDir = LerpVector(0.15, ph:GetVelocity():GetNormalized(), want):GetNormalized()
                ph:SetVelocity(newDir * speed)
                rkt:SetAngles(newDir:Angle())
            end
        end)
    end

    if isAtomic then
        timer.Simple(2.0, function()
            if IsValid(rkt) then D6_ExplodeRocket(rkt) end
        end)
    end

    hook.Add("EntityCollision", "D6_Rkt_"..idx, function(ent, data)
        if ent ~= rkt then return end
        if IsValid(data.HitEntity) and data.HitEntity == rkt.D6_Owner then return end
        D6_ExplodeRocket(rkt)
    end)

    timer.Simple(8, function()
        if IsValid(rkt) then D6_ExplodeRocket(rkt) end
    end)
    return rkt
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
    local pos = owner:GetShootPos() + dir * 40
    local sub = self:GetNWInt("D6_RktSub", 0)

    if sub == 0 then
        SpawnRocket(owner, pos, dir, 1800, 80, 200, false, false)

    elseif sub == 1 then
        for i = -1, 1 do
            local sp = Angle(sa.p, sa.y + i*4, 0):Forward()
            SpawnRocket(owner, pos, sp, 1800, 60, 180, false, false)
        end

    elseif sub == 2 then
        for i = 1, 6 do
            local yOff   = (i - 3.5) * 8
            local sp     = Angle(sa.p + math.random(-4, 4), sa.y + yOff, 0):Forward()
            local rktPos = pos + sa:Right() * (i - 3.5) * 12
            SpawnRocket(owner, rktPos, sp, 1400, 50, 150, true, false)
        end

    elseif sub == 3 then
        SpawnRocket(owner, pos, dir, 1200, 300, 500, false, true)
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
