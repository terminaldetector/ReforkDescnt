-- =========================================================
-- d6_wep_scroll.lua — скролл-переключатель оружий слота 4
--
--   Когда активно DRMD-оружие слота 4 (вторичное/тяжёлое):
--     Колесо ВНИЗ → следующее оружие (высокий SlotPos = ракеты)
--     Колесо ВВЕРХ → предыдущее оружие (низкий SlotPos = обычное)
--
--   Порядок по SlotPos (задаётся в SWEP.SlotPos каждого файла):
--     0 = rockets            (ближний бой / залп)
--     1 = gravy_railgun      (захват + рельса)
--     2 = railmk2            (рельса Mk2 / гравлур)
--     3 = concussion         (тяжёлая КС-ракета)
--     4 = homing             (ГСН-ракета)
--     5 = smart_missile      (ИИ-ракета)
--     6 = mega_missile       (нюк)
--
--   При выходе из диапазона слот зацикливается (wraparound).
--   Переключение не работает вне Slot 4 — нормальная смена
--   слотов колесом остаётся неизменной.
-- =========================================================
if not CLIENT then return end

-- Классы оружия, участвующие в скролл-цикле (все DRMD Slot 4)
local D6_SLOT4 = {
    weapon_d6_rockets       = true,
    weapon_d6_gravy_railgun = true,
    weapon_d6_railmk2       = true,
    weapon_d6_concussion    = true,
    weapon_d6_homing        = true,
    weapon_d6_smart_missile = true,
    weapon_d6_mega_missile  = true,
}

hook.Add("CreateMove", "D6_WepScrollSlot4", function(cmd)
    local ply = LocalPlayer()
    if not IsValid(ply) then return end

    local wep = ply:GetActiveWeapon()
    if not IsValid(wep) or not D6_SLOT4[wep:GetClass()] then return end

    local wheel = cmd:GetMouseWheel()
    if wheel == 0 then return end

    -- Собрать и отсортировать DRMD Slot 4 оружия игрока по SlotPos
    local slot4 = {}
    for _, w in ipairs(ply:GetWeapons()) do
        if IsValid(w) and D6_SLOT4[w:GetClass()] then
            slot4[#slot4 + 1] = w
        end
    end
    if #slot4 <= 1 then return end

    table.sort(slot4, function(a, b)
        return (a:SlotPos() or 0) < (b:SlotPos() or 0)
    end)

    -- Найти текущий индекс
    local curIdx = 1
    for i, w in ipairs(slot4) do
        if w == wep then curIdx = i; break end
    end

    -- Вверх = меньший SlotPos (обычное), вниз = больший SlotPos (ракеты)
    local newIdx
    if wheel > 0 then
        newIdx = ((curIdx - 2) % #slot4) + 1
    else
        newIdx = (curIdx % #slot4) + 1
    end

    cmd:SelectWeapon(slot4[newIdx])
    cmd:SetMouseWheel(0)   -- поглотить вход
end)

print("[D6] d6_wep_scroll.lua: скролл-цикл слота 4 активен")
