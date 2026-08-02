-- ═══════════════════════════════════════════════════════════
-- d6_whiplash.lua
-- "Whiplash" — крюк в стиле Ultrakill для Descent 6DOF.
--
-- Это РЕРАЙТ старого крюка (D6_Hook_Think / D6_Hook_Move в
-- d6_core.lua). Файл удаляет старые хуки и ставит свои.
--
-- ПОВЕДЕНИЕ:
--   Зажать +d6_whiplash:
--     - Цель = враг → рывок к врагу (3500 ед/с), при касании
--       урон 25 + отбрасывание + комбо-окно.
--     - Цель < 25% HP → мгновенное добивание, +25 энергии
--       Railgun, ускорение ×1.2 на 2 сек.
--     - Цель = геометрия → притягивание к точке (плавно,
--       без таймаута), сохранение инерции.
--   Отпустить:
--     - Полёт прекращается, инерция сохраняется.
--   Пробел/Ctrl во время притягивания к стене → доп. импульс
--     (отскок). Кулдаун 0.5 сек.
--
-- БИНД: bind Z "+d6_whiplash"
-- ═══════════════════════════════════════════════════════════

-- ─── Параметры ──────────────────────────────────────────
local W_RANGE        = 3000
local W_DASH_SPD     = 3500       -- скорость рывка к врагу
local W_PULL_ACC     = 2200       -- ускорение притягивания к стене
local W_PULL_MAX     = 2400       -- лимит скорости при подтягивании
local W_HIT_DMG      = 25
local W_HIT_KNOCK    = 800
local W_FINISH_HP    = 0.25        -- порог HP для добивания (25%)
local W_FINISH_ENERGY= 25          -- бонус энергии Railgun
local W_BOOST_DUR    = 2.0
local W_BOOST_MULT   = 1.2
local W_RELEASE_CD   = 0.5
local W_KICK_SPD     = 1800        -- импульс отталкивания от стены
local W_REACH_DIST   = 80          -- дистанция считается касанием

-- ─── Сетевые строки ─────────────────────────────────────
if SERVER then
    util.AddNetworkString("D6_WhiplashSync")
end

-- =========================================================
-- УДАЛЯЕМ СТАРЫЕ ХУКИ КРЮКА
-- (вызывается после загрузки d6_core.lua)
-- =========================================================
local function RemoveOldHook()
    hook.Remove("Think",      "D6_Hook_Think")
    hook.Remove("SetupMove",  "D6_Hook_Move")
end

hook.Add("InitPostEntity", "D6_Whiplash_Cleanup", RemoveOldHook)
-- На всякий случай и при загрузке файла:
timer.Simple(0, RemoveOldHook)

-- =========================================================
-- ХЕЛПЕРЫ
-- =========================================================
local function ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

local function IsHookableEnemy(ent)
    if not IsValid(ent) then return false end
    if ent:IsPlayer() then return true end
    if ent:IsNPC()    then return true end
    -- Кастомные классы Descent — если есть в моде
    local cls = ent:GetClass()
    if cls:sub(1,4) == "npc_" then return true end
    return false
end

local function GetTargetHPFrac(ent)
    if not IsValid(ent) then return 1 end
    local hp, mhp = ent:Health(), ent:GetMaxHealth()
    if mhp <= 0 then mhp = 100 end
    return hp / mhp
end

-- =========================================================
-- СЕРВЕР: основная логика
-- =========================================================
if SERVER then

    -- ─── Concommand для биндинга ─────────────────────────
    concommand.Add("+d6_whiplash", function(ply)
        if not IsValid(ply) or not ply.D6On then return end
        ply.D6WhipKey = true
    end)
    concommand.Add("-d6_whiplash", function(ply)
        if not IsValid(ply) then return end
        ply.D6WhipKey = false
    end)

    -- ─── Активация рывка / зацепа ────────────────────────
    local function TryActivate(ply)
        if ply.D6WhipOn then return end
        if (ply.D6WhipCD or 0) > CurTime() then return end

        local ang = ShootAng(ply)
        local src = ply:GetShootPos()
        local tr  = util.TraceLine({
            start  = src,
            endpos = src + ang:Forward() * W_RANGE,
            filter = ply,
            mask   = MASK_SHOT,
        })
        if not tr.Hit or tr.HitSky then return end

        local target  = tr.Entity
        local hitPos  = tr.HitPos
        local isEnemy = IsHookableEnemy(target)

        ply.D6WhipOn      = true
        ply.D6WhipMode    = isEnemy and "enemy" or "wall"
        ply.D6WhipTarget  = isEnemy and target or NULL
        ply.D6WhipAnchor  = hitPos
        ply.D6WhipStart   = CurTime()
        ply.D6WhipHit     = false
        ply.D6WhipCombo   = false

        ply:EmitSound("weapons/physcannon/physcannon_pickup.wav", 75, 140)

        -- Финишер?
        if isEnemy and IsValid(target) and GetTargetHPFrac(target) <= W_FINISH_HP then
            ply.D6WhipFinisher = true
        else
            ply.D6WhipFinisher = false
        end

        -- Сетевой сигнал клиентам
        net.Start("D6_WhiplashSync")
            net.WriteEntity(ply)
            net.WriteBool(true)
            net.WriteVector(hitPos)
            net.WriteEntity(isEnemy and target or NULL)
            net.WriteBool(ply.D6WhipFinisher)
        net.Broadcast()
    end

    -- ─── Деактивация (отпускание клавиши) ────────────────
    local function Deactivate(ply, sync)
        if not ply.D6WhipOn then return end
        ply.D6WhipOn     = false
        ply.D6WhipMode   = nil
        ply.D6WhipTarget = NULL
        ply.D6WhipAnchor = nil
        ply.D6WhipCD     = CurTime() + W_RELEASE_CD

        if sync ~= false then
            net.Start("D6_WhiplashSync")
                net.WriteEntity(ply)
                net.WriteBool(false)
                net.WriteVector(vector_origin)
                net.WriteEntity(NULL)
                net.WriteBool(false)
            net.Broadcast()
        end
    end

    -- ─── Применить бонус ускорения после добивания ───────
    local function ApplyBoost(ply)
        ply.D6WhipBoostUntil = CurTime() + W_BOOST_DUR
        -- Восполняем энергию Railgun у активного оружия
        local wep = ply:GetActiveWeapon()
        if IsValid(wep) and wep:GetClass() == "weapon_d6_gravy_railgun" then
            local cur = wep:GetNWInt("D6_RailEnergy", 0)
            wep:SetNWInt("D6_RailEnergy", math.min(100, cur + W_FINISH_ENERGY))
        end
        -- Или у любого Railgun в инвентаре
        for _, w in ipairs(ply:GetWeapons()) do
            if w:GetClass() == "weapon_d6_gravy_railgun" then
                local cur = w:GetNWInt("D6_RailEnergy", 0)
                w:SetNWInt("D6_RailEnergy", math.min(100, cur + W_FINISH_ENERGY))
            end
        end
    end

    -- ─── Основной Think: следим за состоянием Whiplash ───
    hook.Add("Think", "D6_Whiplash_Think", function()
        for _, ply in ipairs(player.GetAll()) do
            if not IsValid(ply) or not ply.D6On then continue end

            -- Активация по нажатию
            if ply.D6WhipKey and not ply.D6WhipOn then
                TryActivate(ply)
            elseif not ply.D6WhipKey and ply.D6WhipOn then
                Deactivate(ply)
            end

            if not ply.D6WhipOn then continue end

            -- Защита от тайм-аута: 6 сек максимум
            if CurTime() - (ply.D6WhipStart or 0) > 6 then
                Deactivate(ply); continue
            end

            local mode = ply.D6WhipMode

            -- ─── Режим: рывок к врагу ────────────────────
            if mode == "enemy" then
                local tgt = ply.D6WhipTarget
                if not IsValid(tgt) then
                    Deactivate(ply); continue
                end
                local dist = ply:GetPos():Distance(tgt:GetPos())
                if dist <= W_REACH_DIST and not ply.D6WhipHit then
                    ply.D6WhipHit = true

                    if ply.D6WhipFinisher and (tgt:IsNPC() or tgt:IsPlayer()) then
                        -- Добивание
                        local dmg = DamageInfo()
                        dmg:SetAttacker(ply); dmg:SetInflictor(ply)
                        dmg:SetDamage(9999)
                        dmg:SetDamageType(DMG_SLASH)
                        tgt:TakeDamageInfo(dmg)
                        ApplyBoost(ply)
                        ply:EmitSound("physics/metal/metal_solid_impact_bullet1.wav", 90, 130)
                    else
                        -- Обычный удар
                        local dmg = DamageInfo()
                        dmg:SetAttacker(ply); dmg:SetInflictor(ply)
                        dmg:SetDamage(W_HIT_DMG)
                        dmg:SetDamageType(DMG_CLUB)
                        tgt:TakeDamageInfo(dmg)

                        -- Отбрасывание
                        local push = ShootAng(ply):Forward() * W_HIT_KNOCK
                        push.z = push.z + 150
                        if tgt:IsPlayer() or tgt:IsNPC() then
                            tgt:SetVelocity(push)
                        else
                            local ph = tgt:GetPhysicsObject()
                            if IsValid(ph) then ph:SetVelocity(push) end
                        end
                        ply:EmitSound("physics/metal/metal_solid_impact_bullet2.wav", 85, 110)
                    end
                    Deactivate(ply); continue
                end
            end
        end
    end)

    -- ─── Движение игрока во время Whiplash ───────────────
    hook.Add("SetupMove", "D6_Whiplash_Move", function(ply, mvd, cmd)
        if not ply.D6On or not ply.D6WhipOn then return end
        ply.D6Vel = ply.D6Vel or Vector(0,0,0)

        local mode = ply.D6WhipMode
        local mp   = ply:GetPos()

        if mode == "enemy" then
            local tgt = ply.D6WhipTarget
            if not IsValid(tgt) then return end
            local dir = (tgt:GetPos() + tgt:OBBCenter() - mp):GetNormalized()
            ply.D6Vel = dir * W_DASH_SPD
            mvd:SetVelocity(ply.D6Vel)

        elseif mode == "wall" then
            local hp = ply.D6WhipAnchor
            if not hp then return end
            local d  = mp:Distance(hp)
            if d < 80 then
                -- Достигли стены — деактивация (но сохраняем скорость)
                ply.D6WhipOn     = false
                ply.D6WhipMode   = nil
                ply.D6WhipAnchor = nil
                ply.D6WhipCD     = CurTime() + W_RELEASE_CD
                net.Start("D6_WhiplashSync")
                    net.WriteEntity(ply); net.WriteBool(false)
                    net.WriteVector(vector_origin); net.WriteEntity(NULL); net.WriteBool(false)
                net.Broadcast()
                return
            end

            -- Импульс отталкивания при Space/Ctrl
            if cmd:KeyDown(IN_JUMP) or cmd:KeyDown(IN_DUCK) then
                local kickDir = (mp - hp):GetNormalized()
                if cmd:KeyDown(IN_JUMP) then kickDir.z = math.max(kickDir.z, 0.4) end
                ply.D6Vel = kickDir * W_KICK_SPD
                ply.D6WhipOn     = false
                ply.D6WhipMode   = nil
                ply.D6WhipAnchor = nil
                ply.D6WhipCD     = CurTime() + W_RELEASE_CD
                net.Start("D6_WhiplashSync")
                    net.WriteEntity(ply); net.WriteBool(false)
                    net.WriteVector(vector_origin); net.WriteEntity(NULL); net.WriteBool(false)
                net.Broadcast()
                mvd:SetVelocity(ply.D6Vel)
                return
            end

            -- Притягивание с ускорением (сохраняет инерцию)
            local dir = (hp - mp):GetNormalized()
            ply.D6Vel = ply.D6Vel + dir * W_PULL_ACC * FrameTime()
            local spd = ply.D6Vel:Length()
            if spd > W_PULL_MAX then
                ply.D6Vel = ply.D6Vel * (W_PULL_MAX / spd)
            end
            mvd:SetVelocity(ply.D6Vel)
        end
    end)

    -- ─── Бонусное ускорение после добивания ──────────────
    hook.Add("SetupMove", "D6_Whiplash_Boost", function(ply, mvd, cmd)
        if not ply.D6On then return end
        local until_ = ply.D6WhipBoostUntil or 0
        if until_ <= CurTime() then return end
        ply.D6Vel = (ply.D6Vel or Vector(0,0,0)) * 1.0
        -- Множитель применяется как буст к текущей скорости каждый тик
        -- (мягкий способ — клиент чувствует ускорение, без читерства)
        if ply.D6Vel:LengthSqr() > 1 then
            local mult = 1 + (W_BOOST_MULT - 1) * 0.5  -- сглаживание
            ply.D6Vel = ply.D6Vel * (1 + (mult - 1) * FrameTime() * 4)
        end
        mvd:SetVelocity(ply.D6Vel)
    end)

    -- ─── Очистка при выходе из 6DOF ──────────────────────
    hook.Add("PlayerSpawn", "D6_Whiplash_Reset", function(ply)
        ply.D6WhipOn  = false
        ply.D6WhipKey = false
        ply.D6WhipCD  = 0
    end)
end

-- =========================================================
-- КЛИЕНТ: визуальные эффекты
-- =========================================================
if CLIENT then

    local BEAM_MAT = Material("trails/laser")
    local W_State  = {}  -- W_State[ply] = { active, anchor, target, finisher, t0 }

    net.Receive("D6_WhiplashSync", function()
        local ply      = net.ReadEntity()
        local active   = net.ReadBool()
        local anchor   = net.ReadVector()
        local target   = net.ReadEntity()
        local finisher = net.ReadBool()

        if not IsValid(ply) then return end
        if active then
            W_State[ply] = {
                active   = true,
                anchor   = anchor,
                target   = target,
                finisher = finisher,
                t0       = CurTime(),
            }
            -- Звук активации
            if ply == LocalPlayer() then
                surface.PlaySound("weapons/physcannon/physcannon_pickup.wav")
            end
        else
            W_State[ply] = nil
        end
    end)

    -- Белый/жёлтый трос-след
    hook.Add("PostDrawTranslucentRenderables", "D6_WhiplashDraw", function(depth, sky)
        if depth or sky then return end
        for ply, st in pairs(W_State) do
            if not IsValid(ply) or not st.active then continue end

            local src
            if ply == LocalPlayer() then
                local ang = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
                src = ply:GetShootPos() + Angle(ang.p, ang.y, 0):Forward() * 18
            else
                src = ply:GetShootPos()
            end

            local dst = IsValid(st.target)
                and (st.target:GetPos() + st.target:OBBCenter())
                or  st.anchor

            -- Цвет: финишер = красный, враг = жёлтый, стена = белый
            local col
            if st.finisher then
                col = Color(255, 60, 60)
            elseif IsValid(st.target) then
                col = Color(255, 220, 80)
            else
                col = Color(230, 230, 230)
            end

            -- Пульсация толщины
            local thick = 3 + math.abs(math.sin(CurTime() * 18)) * 2

            render.SetMaterial(BEAM_MAT)
            render.DrawBeam(src, dst, thick, 0, 1, col)

            -- Точка зацепа — вспышка
            local life = math.Clamp((CurTime() - st.t0) * 4, 0, 1)
            render.DrawSprite(dst, 24 * (1 + life * 0.5), 24 * (1 + life * 0.5), col)
        end
    end)

    -- Очистка ушедших игроков
    hook.Add("Think", "D6_WhiplashCleanup", function()
        for ply, _ in pairs(W_State) do
            if not IsValid(ply) then W_State[ply] = nil end
        end
    end)

    -- HUD: индикатор активного Whiplash
    hook.Add("HUDPaint", "D6_WhiplashHUD", function()
        local ply = LocalPlayer()
        if not IsValid(ply) or not ply:GetNWBool("D6On", false) then return end
        local st = W_State[ply]
        if not st or not st.active then return end

        local sw, sh = ScrW(), ScrH()
        local label  = st.finisher and "▼ ДОБИВАНИЕ"
                       or (IsValid(st.target) and "→ РЫВОК" or "⚓ ЗАЦЕП")
        local col    = st.finisher and Color(255, 80, 80)
                       or (IsValid(st.target) and Color(255, 220, 80) or Color(230, 230, 230))

        draw.SimpleTextOutlined(label, "DermaLarge",
            sw * 0.5, sh * 0.42, col,
            TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER, 2, Color(0,0,0,200))
    end)

    -- Подсказка по привязке (один раз при загрузке)
    timer.Simple(3, function()
        chat.AddText(Color(180, 220, 255),
            "[Whiplash] ", Color(220,220,220),
            "Биндинг: ", Color(255,255,180), "bind Z \"+d6_whiplash\"")
    end)
end

print("[D6] d6_whiplash.lua OK")
