-- ═══════════════════════════════════════════════════════════
-- weapon_d6_omni.lua
-- ОМНИ-ОРУЖИЕ для Descent 6DOF — главное оружие мода.
--
-- 5 режимов (R = смена):
--   0 ПУЛЬСАР  — 4× пулемёт, быстрый огонь
--   1 ПЛАЗМА   — 2× авт + 2× плазма, двойные болты
--   2 ТЯЖЁЛЫЙ  — 5 стволов, плазма + AoE взрыв
--   3 ЛАЗЕР    — удержание ЛКМ, непрерывный урон
--   4 РАКЕТЫ   — 4 сабрежима (одиночная/залп/наводящиеся/атомка)
--
-- Рендер в стиле DOOM/Quake: ClientsideModel зафиксированы
-- внизу экрана относительно позиции камеры.
-- ═══════════════════════════════════════════════════════════

AddCSLuaFile()

-- ─── Базовые свойства ────────────────────────────────────
SWEP.PrintName       = "Omni-Weapon"
SWEP.Author          = "Descent 6DOF"
SWEP.Instructions    = "ЛКМ — огонь   R — сменить режим   ПКМ — сабрежим ракет"
SWEP.Category        = "Descent"
SWEP.Slot            = 3
SWEP.SlotPos         = 0
SWEP.Spawnable       = true
SWEP.AdminSpawnable  = true

SWEP.Base            = "weapon_base"
SWEP.HoldType        = "physgun"
SWEP.ViewModel       = "models/weapons/v_shotgun.mdl"   -- скрыто, рисуем сами
SWEP.WorldModel      = "models/weapons/w_shotgun.mdl"   -- скрыто
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

-- ─── Регистрация сетевых строк (однократно) ─────────────
if SERVER and not _D6_OMNI_NET then
    util.AddNetworkString("D6_OmniFire")
    util.AddNetworkString("D6_OmniMode")
    util.AddNetworkString("D6_OmniLaser")
    _D6_OMNI_NET = true
end

-- =========================================================
-- КОНСТАНТЫ
-- =========================================================
local ENERGY_MAX   = 100
local ENERGY_REGEN = 8      -- в секунду

-- Стоимость энергии по режиму
local ENERGY_COST  = { [0]=1, [1]=5, [2]=15, [3]=2, [4]=20 }

-- Скорострельность (секунды между выстрелами)
local FIRE_RATE    = { [0]=0.08, [1]=0.4, [2]=1.0, [3]=0.1, [4]=0.5 }

-- Урон по режиму
local FIRE_DMG     = { [0]=8, [1]=30, [2]=80, [3]=20, [4]=100 }

-- Имена режимов и ракетных сабрежимов
local MNAMES       = { [0]="ПУЛЬСАР", [1]="ПЛАЗМА", [2]="ТЯЖЁЛЫЙ", [3]="ЛАЗЕР", [4]="РАКЕТЫ" }
local ROCKET_NAMES = { [0]="×1 ПРЯМАЯ", [1]="×3 ЗАЛП", [2]="×6 НАВЕДЕНИЕ", [3]="☢ АТОМКА" }
local MODE_COLORS  = {
    [0] = Color(255, 140, 0),    -- оранжевый
    [1] = Color(0, 220, 255),    -- голубой
    [2] = Color(220, 40, 40),    -- красный
    [3] = Color(50, 255, 80),    -- лаймовый
    [4] = Color(255, 230, 0),    -- жёлтый
}

-- Модели
local MDL_AIRBOAT  = "models/airboatgun.mdl"
local MDL_NOSEGUN  = "models/gibs/gunship_gibs_nosegun.mdl"
local MDL_STRIDER  = "models/gibs/strider_weapon.mdl"
local MDL_BOMB     = "models/props_phx/ww2bomb.mdl"
local MDL_FLAK     = "models/props_phx/misc/flakshell_big.mdl"

-- ─── Конфигурации моделей пушек по режимам ───────────────
-- Каждая запись: { fwd, rgt, up, pitch, yaw, model }
-- Позиции в «пушечном пространстве» относительно камеры.
local GUN_CONFIGS = {
    [0] = {  -- ПУЛЬСАР: 4× airboatgun
        { fwd=55, rgt=-22, up=-16, pitch= 8, yaw= -8, mdl=MDL_AIRBOAT },
        { fwd=55, rgt=-11, up=-18, pitch= 8, yaw= -3, mdl=MDL_AIRBOAT },
        { fwd=55, rgt= 11, up=-18, pitch= 8, yaw=  3, mdl=MDL_AIRBOAT },
        { fwd=55, rgt= 22, up=-16, pitch= 8, yaw=  8, mdl=MDL_AIRBOAT },
    },
    [1] = {  -- ПЛАЗМА: airboatgun снаружи, nosegun внутри
        { fwd=55, rgt=-22, up=-16, pitch=8, yaw=-8, mdl=MDL_AIRBOAT },
        { fwd=52, rgt=-11, up=-15, pitch=5, yaw=-3, mdl=MDL_NOSEGUN  },
        { fwd=52, rgt= 11, up=-15, pitch=5, yaw= 3, mdl=MDL_NOSEGUN  },
        { fwd=55, rgt= 22, up=-16, pitch=8, yaw= 8, mdl=MDL_AIRBOAT  },
    },
    [2] = {  -- ТЯЖЁЛЫЙ: 5 стволов
        { fwd=55, rgt=-24, up=-14, pitch= 8, yaw=-10, mdl=MDL_AIRBOAT },
        { fwd=52, rgt=-12, up=-16, pitch= 5, yaw= -4, mdl=MDL_NOSEGUN },
        { fwd=50, rgt=  0, up=-22, pitch=12, yaw=  0, mdl=MDL_NOSEGUN },
        { fwd=52, rgt= 12, up=-16, pitch= 5, yaw=  4, mdl=MDL_NOSEGUN },
        { fwd=55, rgt= 24, up=-14, pitch= 8, yaw= 10, mdl=MDL_AIRBOAT },
    },
    [3] = {  -- ЛАЗЕР: одна пушка по центру
        { fwd=58, rgt=0, up=-16, pitch=10, yaw=0, mdl=MDL_STRIDER },
    },
    [4] = {  -- РАКЕТЫ: 2 контейнера
        { fwd=52, rgt=-16, up=-15, pitch=0, yaw=0, mdl=MDL_BOMB },
        { fwd=52, rgt= 16, up=-15, pitch=0, yaw=0, mdl=MDL_BOMB },
    },
}

-- =========================================================
-- ХЕЛПЕРЫ (общие)
-- =========================================================
local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

-- Позиция дула для конкретного слота оружия (серверный расчёт)
local function MuzzleWorld(ply, slot)
    local ang  = ShootAng(ply)
    local fwd  = ang:Forward()
    local rgt  = ang:Right()
    local up   = ang:Up()
    local base = ply:GetShootPos()
    return base + fwd * (slot.fwd + 10) + rgt * slot.rgt + up * slot.up
end

-- =========================================================
-- ВЗРЫВ РАКЕТЫ (доступна ещё до объявления SWEP)
-- =========================================================
local function D6_ExplodeRocket(rkt)
    if not IsValid(rkt) then return end
    -- Удалить хуки до Remove чтобы не вызвать рекурсию
    local idx = rkt:EntIndex()
    timer.Remove("D6_Rkt_" .. idx)
    hook.Remove("EntityCollision", "D6_Rkt_" .. idx)

    local pos    = rkt:GetPos()
    local owner  = rkt.D6_Owner
    local radius = rkt.D6_ExpRadius or 200
    local dmg    = rkt.D6_Damage   or 80

    -- Визуальный эффект
    local ef = EffectData()
    ef:SetOrigin(pos)
    ef:SetScale(radius / 50)
    ef:SetMagnitude(dmg)
    util.Effect("Explosion", ef)
    if dmg > 150 then
        util.Effect("HelicopterMegaBomb", ef)
    end

    -- Урон в радиусе
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
-- ИНИЦИАЛИЗАЦИЯ
-- =========================================================
function SWEP:Initialize()
    self:SetHoldType("physgun")

    -- Серверные состояния
    self.NextFire    = 0
    self.LastRegenT  = CurTime()
    self.LaserOn     = false
    self.LaserTick   = 0

    -- Режимы
    self:SetNWInt("D6_Mode",       0)
    self:SetNWInt("D6_RocketSub",  0)
    self:SetNWInt("D6_OmniEnergy", ENERGY_MAX)

    if CLIENT then
        self:_BuildGunModels(0)
    end
end

-- =========================================================
-- DEPLOY / HOLSTER / REMOVE
-- =========================================================
function SWEP:Deploy()
    self:SetHoldType("physgun")
    local ply = self:GetOwner()
    if IsValid(ply) then
        ply:EmitSound("weapons/physcannon/physcannon_dryfire.wav", 75, 120)
    end
    if CLIENT then
        self:_BuildGunModels(self:GetNWInt("D6_Mode", 0))
    end
    return true
end

function SWEP:Holster()
    if CLIENT then self:_DestroyGunModels() end
    self.LaserOn = false
    if SERVER then
        net.Start("D6_OmniLaser")
            net.WriteEntity(self:GetOwner())
            net.WriteBool(false)
            net.WriteVector(Vector(0, 0, 0))
        net.Broadcast()
    end
    return true
end

function SWEP:OnRemove()
    if CLIENT then self:_DestroyGunModels() end
    if SERVER then
        -- Убрать лазер если был активен
        net.Start("D6_OmniLaser")
            local ply = self:GetOwner()
            if IsValid(ply) then net.WriteEntity(ply) else net.WriteEntity(Entity(0)) end
            net.WriteBool(false)
            net.WriteVector(Vector(0, 0, 0))
        net.Broadcast()
    end
end

-- =========================================================
-- ЭНЕРГИЯ
-- =========================================================
function SWEP:GetEnergy()
    return self:GetNWInt("D6_OmniEnergy", ENERGY_MAX)
end

function SWEP:SetEnergy(v)
    if not SERVER then return end
    self:SetNWInt("D6_OmniEnergy", math.Clamp(math.floor(v + 0.5), 0, ENERGY_MAX))
end

function SWEP:HasEnergy(cost)
    return self:GetEnergy() >= cost
end

function SWEP:DenySound()
    local ply = self:GetOwner()
    if IsValid(ply) then
        ply:EmitSound("common/wpn_denyselect.wav", 60, 110)
    end
end

-- =========================================================
-- THINK — регенерация, лазер, модели пушек
-- =========================================================
function SWEP:Think()
    -- ─── СЕРВЕР: регенерация энергии и лазер ─────────────
    if SERVER then
        local now = CurTime()

        -- Регенерация
        local dt = now - (self.LastRegenT or now)
        if dt >= 0.05 then
            self.LastRegenT = now
            local cur = self:GetEnergy()
            if cur < ENERGY_MAX then
                self:SetEnergy(cur + ENERGY_REGEN * dt)
            end
        end

        -- Лазер: удержание ЛКМ
        local ply = self:GetOwner()
        if IsValid(ply) and self:GetNWInt("D6_Mode", 0) == 3 then
            local lmbHeld = ply:KeyDown(IN_ATTACK)

            if lmbHeld and self:HasEnergy(ENERGY_COST[3]) then
                if now >= (self.LaserTick or 0) then
                    self.LaserTick = now + FIRE_RATE[3]
                    self:SetEnergy(self:GetEnergy() - ENERGY_COST[3])

                    local ang = ShootAng(ply)
                    local src = ply:GetShootPos()
                    local dir = ang:Forward()
                    local tr  = util.TraceLine({
                        start  = src,
                        endpos = src + dir * 6000,
                        filter = ply,
                        mask   = MASK_SHOT,
                    })

                    -- Урон по цели
                    if IsValid(tr.Entity) and (tr.Entity:IsNPC() or tr.Entity:IsPlayer()) then
                        local di = DamageInfo()
                        di:SetAttacker(ply)
                        di:SetInflictor(self)
                        di:SetDamage(FIRE_DMG[3])
                        di:SetDamageType(DMG_ENERGYBEAM)
                        di:SetDamageForce(dir * 500)
                        tr.Entity:TakeDamageInfo(di)
                    end

                    self.LaserOn = true
                    net.Start("D6_OmniLaser")
                        net.WriteEntity(ply)
                        net.WriteBool(true)
                        net.WriteVector(tr.HitPos)
                    net.Broadcast()
                end
            else
                -- Лазер отключён
                if self.LaserOn then
                    self.LaserOn = false
                    net.Start("D6_OmniLaser")
                        net.WriteEntity(ply)
                        net.WriteBool(false)
                        net.WriteVector(Vector(0, 0, 0))
                    net.Broadcast()
                end
            end
        elseif self.LaserOn then
            -- Сменили режим — выключить лазер
            self.LaserOn = false
            local ply2 = self:GetOwner()
            if IsValid(ply2) then
                net.Start("D6_OmniLaser")
                    net.WriteEntity(ply2)
                    net.WriteBool(false)
                    net.WriteVector(Vector(0, 0, 0))
                net.Broadcast()
            end
        end
    end

    -- ─── КЛИЕНТ: обновить позиции моделей пушек ──────────
    if CLIENT then
        local mode = self:GetNWInt("D6_Mode", 0)
        -- Пересоздать модели если сменился режим
        if mode ~= (self._LastDrawMode or -1) then
            self:_BuildGunModels(mode)
            self._LastDrawMode = mode
        end
    end
end

-- =========================================================
-- ОСНОВНАЯ АТАКА (ЛКМ)
-- Режим 3 (Лазер) обрабатывается в Think.
-- Режим 0,1,2 — здесь.
-- Режим 4 — здесь.
-- =========================================================
function SWEP:PrimaryAttack()
    if CLIENT then return end

    local now = CurTime()
    if now < (self.NextFire or 0) then return end

    local ply  = self:GetOwner()
    if not IsValid(ply) then return end

    local mode = self:GetNWInt("D6_Mode", 0)

    -- Лазер стреляет через Think
    if mode == 3 then return end

    local cost = ENERGY_COST[mode]
    if not self:HasEnergy(cost) then
        self:DenySound()
        self.NextFire = now + 0.2
        return
    end

    self:SetEnergy(self:GetEnergy() - cost)
    self.NextFire = now + FIRE_RATE[mode]

    if     mode == 0 then self:_FirePulsar(ply)
    elseif mode == 1 then self:_FirePlasma(ply)
    elseif mode == 2 then self:_FireHeavy(ply)
    elseif mode == 4 then self:_FireRocket(ply)
    end
end

-- =========================================================
-- ВТОРИЧНАЯ АТАКА (ПКМ) — цикл ракетных сабрежимов
-- =========================================================
function SWEP:SecondaryAttack()
    if CLIENT then return end
    local mode = self:GetNWInt("D6_Mode", 0)
    if mode ~= 4 then return end

    local now = CurTime()
    if now < (self.NextModeSw or 0) then return end
    self.NextModeSw = now + 0.3

    local sub     = self:GetNWInt("D6_RocketSub", 0)
    local newSub  = (sub + 1) % 4
    self:SetNWInt("D6_RocketSub", newSub)

    local ply = self:GetOwner()
    if IsValid(ply) then
        ply:EmitSound("buttons/button14.wav", 65, 110 + newSub * 10)
    end

    net.Start("D6_OmniMode")
        net.WriteEntity(ply)
        net.WriteUInt(mode, 3)
        net.WriteUInt(newSub, 2)
    net.Broadcast()
end

-- =========================================================
-- RELOAD — смена режима
-- =========================================================
function SWEP:Reload()
    if CLIENT then return end

    local now = CurTime()
    if now < (self.NextModeSw or 0) then return end
    self.NextModeSw = now + 0.35

    local mode    = self:GetNWInt("D6_Mode", 0)
    local newMode = (mode + 1) % 5
    self:SetNWInt("D6_Mode", newMode)

    -- При смене с лазера — выключить
    if mode == 3 and self.LaserOn then
        self.LaserOn = false
        local ply = self:GetOwner()
        if IsValid(ply) then
            net.Start("D6_OmniLaser")
                net.WriteEntity(ply)
                net.WriteBool(false)
                net.WriteVector(Vector(0, 0, 0))
            net.Broadcast()
        end
    end

    local ply = self:GetOwner()
    if IsValid(ply) then
        ply:EmitSound("buttons/button15.wav", 70, 100 + newMode * 15)
    end

    net.Start("D6_OmniMode")
        net.WriteEntity(ply)
        net.WriteUInt(newMode, 3)
        net.WriteUInt(self:GetNWInt("D6_RocketSub", 0), 2)
    net.Broadcast()
end

-- =========================================================
-- РЕЖИМ 0: ПУЛЬСАР — 4× скорострельный пулемёт
-- =========================================================
function SWEP:_FirePulsar(ply)
    local ang = ShootAng(ply)
    local cfg  = GUN_CONFIGS[0]

    -- Чередуем стволы для визуального разнообразия
    self._PulsarIdx = ((self._PulsarIdx or 0) + 1) % 4
    local slot = cfg[self._PulsarIdx + 1]
    local src  = MuzzleWorld(ply, slot)
    local dir  = ang:Forward()
                 + ang:Right()  * math.Rand(-0.025, 0.025)
                 + ang:Up()    * math.Rand(-0.025, 0.025)
    dir:Normalize()

    ply:FireBullets({
        Src        = src,
        Dir        = dir,
        Damage     = FIRE_DMG[0],
        Distance   = 8000,
        Spread     = Vector(0.025, 0.025, 0),
        Tracer     = 1,
        TracerName = "Tracer",
        Force      = 300,
        Num        = 1,
        AmmoType   = "Pistol",
        AttackPos  = src,
    })

    ply:EmitSound("weapons/airboat/airboat_gun_energy1.wav", 60, 120 + math.random(-8, 8))

    -- Эффект дула
    local ef = EffectData()
    ef:SetOrigin(src)
    ef:SetNormal(dir)
    ef:SetScale(1)
    util.Effect("MuzzleEffect", ef)

    net.Start("D6_OmniFire")
        net.WriteEntity(ply)
        net.WriteVector(src)
        net.WriteVector(dir)
        net.WriteUInt(0, 3)
        net.WriteUInt(0, 2)
    net.Broadcast()
end

-- =========================================================
-- РЕЖИМ 1: ПЛАЗМА — 2× болта (внутренние nosegun)
-- =========================================================
function SWEP:_FirePlasma(ply)
    local ang  = ShootAng(ply)
    local fwd  = ang:Forward()
    local cfg  = GUN_CONFIGS[1]
    -- Стреляем из внутренних стволов (nosegun, индексы 2 и 3 в конфиге)
    local slots = { cfg[2], cfg[3] }
    local hitPos = ply:GetShootPos() + fwd * 4000

    for _, slot in ipairs(slots) do
        local src = MuzzleWorld(ply, slot)
        local dir = (hitPos - src):GetNormalized()

        local tr = util.TraceLine({
            start  = src,
            endpos = src + dir * 4000,
            filter = ply,
            mask   = MASK_SHOT,
        })

        if IsValid(tr.Entity) and (tr.Entity:IsNPC() or tr.Entity:IsPlayer()) then
            local di = DamageInfo()
            di:SetAttacker(ply)
            di:SetInflictor(self)
            di:SetDamage(FIRE_DMG[1])
            di:SetDamageType(DMG_ENERGYBEAM)
            di:SetDamageForce(dir * 1500)
            tr.Entity:TakeDamageInfo(di)
        end

        -- Эффект попадания
        local ef = EffectData()
        ef:SetOrigin(tr.HitPos)
        ef:SetNormal(tr.HitNormal)
        ef:SetScale(2)
        util.Effect("GlassImpact", ef)
    end

    ply:EmitSound("weapons/physcannon/energy_sing_flyby.wav", 70, 140)

    local src0 = MuzzleWorld(ply, slots[1])
    net.Start("D6_OmniFire")
        net.WriteEntity(ply)
        net.WriteVector(src0)
        net.WriteVector(fwd)
        net.WriteUInt(1, 3)
        net.WriteUInt(0, 2)
    net.Broadcast()
end

-- =========================================================
-- РЕЖИМ 2: ТЯЖЁЛЫЙ — плазма + AoE взрыв
-- =========================================================
function SWEP:_FireHeavy(ply)
    local ang = ShootAng(ply)
    local fwd = ang:Forward()
    local cfg = GUN_CONFIGS[2]

    -- Центральный ствол (BOT, индекс 3)
    local centerSlot = cfg[3]
    local src  = MuzzleWorld(ply, centerSlot)
    local dir  = fwd

    local tr = util.TraceLine({
        start  = src,
        endpos = src + dir * 3000,
        filter = ply,
        mask   = MASK_SHOT,
    })

    local hitPos = tr.HitPos

    -- Взрыв в точке попадания
    local ef = EffectData()
    ef:SetOrigin(hitPos)
    ef:SetScale(3)
    ef:SetMagnitude(FIRE_DMG[2])
    util.Effect("Explosion", ef)

    -- AoE урон
    for _, e in ipairs(ents.FindInSphere(hitPos, 150)) do
        if not IsValid(e) then continue end
        if not (e:IsNPC() or e:IsPlayer()) then continue end
        if e == ply then continue end

        local dist = e:GetPos():Distance(hitPos)
        local di   = DamageInfo()
        di:SetAttacker(ply)
        di:SetInflictor(self)
        di:SetDamage(FIRE_DMG[2] * (1 - dist / 150))
        di:SetDamageType(DMG_BLAST)
        di:SetDamageForce((e:GetPos() - hitPos):GetNormalized() * 4000)
        e:TakeDamageInfo(di)
    end

    ply:EmitSound("ambient/explosions/explode_4.wav", 85, 90)
    ply:EmitSound("weapons/physcannon/superphys_launch1.wav", 70, 80)

    net.Start("D6_OmniFire")
        net.WriteEntity(ply)
        net.WriteVector(src)
        net.WriteVector(fwd)
        net.WriteUInt(2, 3)
        net.WriteUInt(0, 2)
    net.Broadcast()
end

-- =========================================================
-- РЕЖИМ 4: РАКЕТЫ
-- =========================================================
function SWEP:_FireRocket(ply)
    local sub = self:GetNWInt("D6_RocketSub", 0)
    local ang = ShootAng(ply)
    local fwd = ang:Forward()
    local src = ply:GetShootPos() + fwd * 30

    -- Параметры по сабрежиму
    local rktModel, rktDmg, rktRadius, rktSpeed, rktCount, rktHoming
    if sub == 0 then
        rktModel  = MDL_BOMB; rktDmg = 100; rktRadius = 180
        rktSpeed  = 1800;     rktCount = 1; rktHoming = false
    elseif sub == 1 then
        rktModel  = MDL_BOMB; rktDmg = 100; rktRadius = 180
        rktSpeed  = 1600;     rktCount = 3; rktHoming = false
    elseif sub == 2 then
        rktModel  = MDL_BOMB; rktDmg = 80; rktRadius = 180
        rktSpeed  = 1400;     rktCount = 6; rktHoming = true
    else -- sub == 3: атомка
        rktModel  = MDL_FLAK; rktDmg = 300; rktRadius = 400
        rktSpeed  = 1200;     rktCount = 1; rktHoming = false
    end

    -- Стоимость: rktCount ракет
    local totalCost = ENERGY_COST[4] * math.max(1, rktCount / 2)
    if not self:HasEnergy(totalCost) then
        self:DenySound(); return
    end
    self:SetEnergy(self:GetEnergy() - totalCost)

    -- Направления для залпа
    local rkt_ang = ShootAng(ply)
    local rgt = rkt_ang:Right()
    local up  = rkt_ang:Up()

    for i = 1, rktCount do
        -- Разброс для залпов/роя
        local spreadDir = fwd
        if rktCount > 1 then
            local sx = math.Rand(-0.08, 0.08)
            local sy = math.Rand(-0.08, 0.08)
            spreadDir = (fwd + rgt * sx + up * sy):GetNormalized()
        end

        -- Небольшая задержка для визуального разнообразия
        local delay = (i - 1) * 0.05
        local capDir  = Vector(spreadDir.x, spreadDir.y, spreadDir.z)
        local capSrc  = Vector(src.x, src.y, src.z)
        local capPly  = ply

        timer.Simple(delay, function()
            if not IsValid(capPly) then return end

            local rkt = ents.Create("prop_physics")
            if not IsValid(rkt) then return end
            rkt:SetModel(rktModel)
            rkt:Spawn()
            rkt:SetPos(capSrc)
            rkt:SetAngles(capDir:Angle())
            rkt:SetMaterial("models/weapons/v_irifle")

            local ph = rkt:GetPhysicsObject()
            if IsValid(ph) then
                ph:EnableGravity(false)
                ph:SetVelocity(capDir * rktSpeed)
            end

            rkt.D6_Rocket    = true
            rkt.D6_Owner     = capPly
            rkt.D6_Damage    = rktDmg
            rkt.D6_ExpRadius = rktRadius
            rkt.D6_Until     = CurTime() + 8
            rkt.D6_Homing    = rktHoming
            rkt.D6_Speed     = rktSpeed

            local idx = rkt:EntIndex()

            -- Think-таймер ракеты
            timer.Create("D6_Rkt_" .. idx, 0.05, 0, function()
                if not IsValid(rkt) or CurTime() > rkt.D6_Until then
                    if IsValid(rkt) then D6_ExplodeRocket(rkt) end
                    timer.Remove("D6_Rkt_" .. idx)
                    return
                end

                if rkt.D6_Homing then
                    local nearest, nearDist = nil, 1500
                    for _, npc in ipairs(ents.GetAll()) do
                        if IsValid(npc) and npc:IsNPC() and npc:Health() > 0 then
                            local d = rkt:GetPos():Distance(npc:GetPos())
                            if d < nearDist then nearDist = d; nearest = npc end
                        end
                    end
                    if nearest then
                        local ph2 = rkt:GetPhysicsObject()
                        if IsValid(ph2) then
                            local vel    = ph2:GetVelocity()
                            local spd    = math.max(vel:Length(), rkt.D6_Speed or 1400)
                            local toTgt  = (nearest:GetPos() - rkt:GetPos()):GetNormalized()
                            local newDir = LerpVector(FrameTime() * 4, vel:GetNormalized(), toTgt)
                            ph2:SetVelocity(newDir:GetNormalized() * spd)
                            rkt:SetAngles(newDir:GetNormalized():Angle())
                        end
                    end
                end
            end)

            -- Коллизия
            hook.Add("EntityCollision", "D6_Rkt_" .. idx, function(ent, data)
                if ent ~= rkt then return end
                local hit = data.HitEntity
                if IsValid(hit) and hit == rkt.D6_Owner then return end
                D6_ExplodeRocket(rkt)
            end)
        end)
    end

    ply:EmitSound("weapons/rpg/rocketfire1.wav", 85, 100)

    net.Start("D6_OmniFire")
        net.WriteEntity(ply)
        net.WriteVector(src)
        net.WriteVector(fwd)
        net.WriteUInt(4, 3)
        net.WriteUInt(sub, 2)
    net.Broadcast()
end

-- =========================================================
-- СКРЫТЬ СТАНДАРТНЫЕ МОДЕЛИ
-- =========================================================
function SWEP:DrawWorldModel()
    -- Ничего не рисуем — у нас ClientsideModel
end

function SWEP:DrawWorldModelTranslucent()
end

-- =========================================================
-- CLIENT: управление ClientsideModel
-- =========================================================
if CLIENT then

    -- Построить (или перестроить) модели пушек для режима
    function SWEP:_BuildGunModels(mode)
        self:_DestroyGunModels()
        self.GunModels = {}
        local cfg = GUN_CONFIGS[mode]
        if not cfg then return end

        for _, slot in ipairs(cfg) do
            local mdl = ClientsideModel(slot.mdl)
            if IsValid(mdl) then
                mdl:SetNoDraw(true)
                self.GunModels[#self.GunModels + 1] = { mdl = mdl, slot = slot }
            end
        end
        self._LastDrawMode = mode
    end

    function SWEP:_DestroyGunModels()
        if self.GunModels then
            for _, entry in ipairs(self.GunModels) do
                if IsValid(entry.mdl) then entry.mdl:Remove() end
            end
        end
        self.GunModels = {}
    end

    -- ─── DOOM-стиль: рисование моделей внизу экрана ─────
    function SWEP:DrawViewModel()
        local ply = self:GetOwner()
        if not IsValid(ply) or ply ~= LocalPlayer() then return end
        if not self.GunModels or #self.GunModels == 0 then return end

        local eyePos = ply:GetShootPos()
        local eyeAng = ply.D6Ang or EyeAngles()
        local fwd    = eyeAng:Forward()
        local rgt    = eyeAng:Right()
        local up     = eyeAng:Up()

        -- Убрать z-test чтобы пушки не прятались за геометрию
        cam.IgnoreZ(true)

        for _, entry in ipairs(self.GunModels) do
            local mdl  = entry.mdl
            local slot = entry.slot
            if not IsValid(mdl) then continue end

            -- Мировая позиция модели
            local worldPos = eyePos
                           + fwd * slot.fwd
                           + rgt * slot.rgt
                           + up  * slot.up

            -- Угол модели = угол камеры + коррекция pitch/yaw
            local mdlAng = Angle(
                eyeAng.p + slot.pitch,
                eyeAng.y + slot.yaw,
                eyeAng.r
            )

            mdl:SetPos(worldPos)
            mdl:SetAngles(mdlAng)
            mdl:DrawModel()
        end

        cam.IgnoreZ(false)
    end

    -- ─── Лазерный луч ────────────────────────────────────
    local _laserState = { on = false, src = Vector(), dst = Vector() }
    local MAT_LASER = Material("sprites/laserbeam")
    local MAT_GLOW  = Material("sprites/light_glow02_add")

    net.Receive("D6_OmniLaser", function()
        local plyEnt = net.ReadEntity()
        local on     = net.ReadBool()
        local hitPos = net.ReadVector()
        if not IsValid(plyEnt) then return end
        _laserState.on  = on
        _laserState.ply = plyEnt
        _laserState.dst = hitPos
    end)

    hook.Add("PostDrawTranslucentRenderables", "D6_OmniLaserDraw", function(depth, sky)
        if depth or sky then return end
        if not _laserState.on then return end

        local plyEnt = _laserState.ply
        if not IsValid(plyEnt) then return end

        local ang = plyEnt.D6Ang or plyEnt:EyeAngles()
        local src = plyEnt:GetShootPos()

        render.SetMaterial(MAT_LASER)
        render.DrawBeam(src, _laserState.dst, 3, 0, 1, Color(50, 255, 80, 230))
        render.DrawBeam(src, _laserState.dst, 1, 0, 1, Color(200, 255, 200, 180))

        -- Свечение на кончике
        render.SetMaterial(MAT_GLOW)
        render.DrawSprite(_laserState.dst, 18, 18, Color(50, 255, 80, 200))
    end)

    -- ─── Эффекты выстрелов ───────────────────────────────
    local _matPlasma = Material("sprites/plasmabeam")
    local _matBeam   = Material("cable/xbeam")
    local _flashIdx  = 0

    net.Receive("D6_OmniFire", function()
        local plyEnt = net.ReadEntity()
        local src    = net.ReadVector()
        local dir    = net.ReadVector()
        local mode   = net.ReadUInt(3)
        local sub    = net.ReadUInt(2)

        if not IsValid(plyEnt) then return end

        local dst = src + dir * 3000

        if mode == 0 then
            -- Пульсар: маленькие искры у дула
            local ef = EffectData()
            ef:SetOrigin(src)
            ef:SetNormal(dir)
            ef:SetScale(0.5)
            ef:SetMagnitude(1)
            util.Effect("ManhackSparks", ef)

        elseif mode == 1 then
            -- Плазма: быстрый зелёный трассер (тонкий, короткое время жизни)
            _flashIdx = _flashIdx + 1
            local key  = "D6_OmniFlash_" .. _flashIdx
            local life = CurTime() + 0.12
            local fSrc = Vector(src.x, src.y, src.z)
            local fDst = Vector(dst.x, dst.y, dst.z)
            hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
                if d or s then return end
                if CurTime() >= life then
                    hook.Remove("PostDrawTranslucentRenderables", key); return
                end
                local a = math.Clamp((life - CurTime()) / 0.12, 0, 1) * 240
                render.SetMaterial(_matPlasma)
                render.DrawBeam(fSrc, fDst, 2, 0, 1, Color(0, 220, 100, a))
                render.DrawBeam(fSrc, fDst, 0.6, 0, 1, Color(200, 255, 200, a))
            end)

        elseif mode == 2 then
            -- Тяжёлый: большой плазма-шар
            local ef = EffectData()
            ef:SetOrigin(src)
            ef:SetNormal(dir)
            ef:SetScale(4)
            ef:SetMagnitude(3)
            util.Effect("cball_explode", ef)
            local ef2 = EffectData()
            ef2:SetOrigin(src + dir * 200)
            ef2:SetScale(2)
            util.Effect("Explosion", ef2)

        elseif mode == 4 then
            -- Ракеты: дымовой след (от EffectData)
            local ef = EffectData()
            ef:SetOrigin(src)
            ef:SetNormal(dir)
            ef:SetScale(1.5)
            util.Effect("RocketTrail", ef)
        end
    end)

    net.Receive("D6_OmniMode", function()
        local plyEnt = net.ReadEntity()
        local mode   = net.ReadUInt(3)
        local sub    = net.ReadUInt(2)
        -- Если мы локальный игрок — обновим модели немедленно
        if IsValid(plyEnt) and plyEnt == LocalPlayer() then
            local wep = plyEnt:GetActiveWeapon()
            if IsValid(wep) and wep:GetClass() == "weapon_d6_omni" then
                wep:_BuildGunModels(mode)
                wep._LastDrawMode = mode
            end
        end
    end)

    -- ─── HUD: режим оружия внизу экрана ─────────────────
    hook.Add("HUDPaint", "D6_OmniHUD", function()
        local ply = LocalPlayer()
        if not IsValid(ply) then return end
        local wep = ply:GetActiveWeapon()
        if not IsValid(wep) or wep:GetClass() ~= "weapon_d6_omni" then return end

        local mode = wep:GetNWInt("D6_Mode", 0)
        local sub  = wep:GetNWInt("D6_RocketSub", 0)
        local nrg  = wep:GetNWInt("D6_OmniEnergy", ENERGY_MAX)
        local col  = MODE_COLORS[mode] or color_white

        local sw, sh = ScrW(), ScrH()
        local cx     = sw * 0.5
        local by     = sh - 56

        -- Имя режима
        local mname = MNAMES[mode] or "?"
        draw.SimpleTextOutlined("[ " .. mname .. " ]",
            "DermaLarge", cx, by,
            col, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER,
            2, Color(0, 0, 0, 220))

        -- Сабрежим ракет
        if mode == 4 then
            local rname = ROCKET_NAMES[sub] or "?"
            draw.SimpleTextOutlined(rname,
                "DermaDefault", cx, by + 22,
                Color(255, 220, 80), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER,
                1, Color(0, 0, 0, 200))
        end

        -- Полоса энергии
        local barW  = 240
        local barH  = 10
        local bx    = cx - barW * 0.5
        local barY  = by + (mode == 4 and 44 or 28)
        local frac  = nrg / ENERGY_MAX

        surface.SetDrawColor(0, 0, 0, 180)
        surface.DrawRect(bx - 2, barY - 2, barW + 4, barH + 4)
        surface.SetDrawColor(20, 20, 40, 200)
        surface.DrawRect(bx, barY, barW, barH)
        local ec = MODE_COLORS[mode] or Color(0, 200, 255)
        surface.SetDrawColor(ec.r, ec.g, ec.b, 230)
        surface.DrawRect(bx, barY, barW * frac, barH)

        draw.SimpleText(
            "⚡ " .. math.floor(nrg) .. " / " .. ENERGY_MAX,
            "DermaDefault", cx, barY + barH * 0.5,
            Color(220, 220, 255), TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end)

end -- if CLIENT

-- =========================================================
-- РЕГИСТРАЦИЯ В РЕЕСТРЕ D6
-- =========================================================
if D6_RegisterWeapon then
    D6_RegisterWeapon("weapon_d6_omni", {
        category   = "Descent",
        autoGive   = true,
        patchAim   = true,
    })
end

print("[D6] weapon_d6_omni.lua OK")
