-- ═══════════════════════════════════════════════════════════
-- animator.lua — SCK Element Animator (CLIENT)
--
-- Анимация VElements/WElements без изменения рендер-циклов
-- base_code.lua: пред-кадровый проход мутирует v.pos / v.angle
-- (база кэшируется в v._basePos / v._baseAng при первом проходе),
-- после чего ViewModelDrawn / DrawWorldModel читают уже
-- анимированные значения.
--
-- Поле элемента:
--   anim = {
--     spin        = 0,    -- град/сек непрерывного вращения
--     spinAxis    = "r",  -- ось вращения: "p" | "y" | "r"
--     bobAmp      = 0,    -- амплитуда покачивания (юниты, ось Up)
--     bobFreq     = 2,    -- частота покачивания (Гц)
--     swayMult    = 1,    -- множитель внешнего sway (читает рендерер)
--     kick        = 0,    -- отдача: смещение назад при выстреле (юниты)
--     kickRecover = 6,    -- скорость восстановления отдачи (1/сек)
--   }
--
-- API:
--   SCK_AnimateTable(elements)  — анимировать таблицу элементов
--   SCK_TriggerRecoil(elements) — запустить kick на всех anim-элементах
--
-- Авто-режим: для любого SWEP с заполненным VElements (включая
-- развёрнутые кластеры) анимация и авто-отдача работают сами через
-- хук PreDrawViewModel. Элементы без anim не затрагиваются вовсе.
--
-- Standalone — без зависимостей от DRMD.
-- ═══════════════════════════════════════════════════════════

if not CLIENT then return end

-- Анимирует все элементы таблицы, у которых задано поле anim.
-- Безопасно вызывать каждый кадр; элементы без anim не трогаются.
function SCK_AnimateTable(elements)
    if not istable(elements) then return end
    local rt = RealTime()
    local ft = FrameTime()

    for _, v in pairs(elements) do
        local a = v.anim
        if istable(a) and v.pos and v.angle then

            -- Кэш базовых значений (один раз)
            if not v._basePos then
                v._basePos = Vector(v.pos.x, v.pos.y, v.pos.z)
                v._baseAng = Angle(v.angle.p, v.angle.y, v.angle.r)
            end

            local p  = Vector(v._basePos.x, v._basePos.y, v._basePos.z)
            local an = Angle(v._baseAng.p, v._baseAng.y, v._baseAng.r)

            -- Непрерывное вращение
            local spin = tonumber(a.spin) or 0
            if spin ~= 0 then
                v._spinPhase = ((v._spinPhase or 0) + spin * ft) % 360
                local ax = a.spinAxis or "r"
                if ax == "p" then
                    an.p = an.p + v._spinPhase
                elseif ax == "y" then
                    an.y = an.y + v._spinPhase
                else
                    an.r = an.r + v._spinPhase
                end
            end

            -- Покачивание (bobbing) по оси Up
            local amp = tonumber(a.bobAmp) or 0
            if amp ~= 0 then
                local freq = tonumber(a.bobFreq) or 2
                p.z = p.z + math.sin(rt * freq * 2 * math.pi) * amp
            end

            -- Отдача (recoil kick): смещение назад, затухает
            local k = v._kickT or 0
            if k > 0 then
                p.x = p.x - (tonumber(a.kick) or 0) * k
                v._kickT = math.max(0, k - ft * (tonumber(a.kickRecover) or 6))
            end

            v.pos   = p
            v.angle = an
        end
    end
end

-- Запускает импульс отдачи на всех элементах с anim.kick.
function SCK_TriggerRecoil(elements)
    if not istable(elements) then return end
    for _, v in pairs(elements) do
        if istable(v.anim) and (tonumber(v.anim.kick) or 0) ~= 0 then
            v._kickT = 1
        end
    end
end

-- ── Авто-режим для SCK-совместимых SWEP ──────────────────────
-- Анимирует VElements активного оружия перед отрисовкой viewmodel
-- и триггерит отдачу в момент выстрела (сдвиг NextPrimaryFire).
hook.Add("PreDrawViewModel", "SCK_Animator", function(vm, ply, wep)
    if not IsValid(wep) then return end
    if not istable(wep.VElements) then return end

    local npf = wep:GetNextPrimaryFire()
    if wep._sckAnimNPF and npf > wep._sckAnimNPF + 0.001
        and IsValid(ply) and ply:KeyDown(IN_ATTACK) then
        SCK_TriggerRecoil(wep.VElements)
    end
    wep._sckAnimNPF = npf

    SCK_AnimateTable(wep.VElements)
end)

print("[SCK] animator.lua loaded")
