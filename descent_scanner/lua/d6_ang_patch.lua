-- =========================================================
-- d6_ang_patch.lua
-- Глобальный патч угла для ЛЮБОГО SWEP в 6DOF-режиме.
--
-- Проблема: внешние SWEPы стреляют по ply:EyeAngles() или
--           cmd:GetViewAngles() — стандартный угол без крена.
--           В 6DOF игрок смотрит через D6AngSynced (синхронизированный
--           полный угол с pitch/yaw/roll), который расходится
--           с EyeAngles при крене или быстром движении.
--
-- Решение: перехватываем GetOwner():EyeAngles() на уровне
--           хука SetupMove (серверный патч позиции) и
--           monkey-patch методов GetAimVector / EyeAngles
--           на время выстрела через SWEP:PrimaryAttack.
-- =========================================================

-- ── Серверный патч: CalculateAimVector и SWEP:Think ──────
-- Патчим GetAimVector игрока пока он в 6DOF-режиме.
-- GetAimVector используется стандартным FireBullets и многими SWEPами.
if SERVER then

    -- Хук срабатывает ПЕРЕД каждым SetupMove.
    -- Подменяем EyeAngles на D6AngSynced чтобы FireBullets
    -- и util.TraceLine внутри SWEP получали правильный угол.
    hook.Add("SetupMove", "D6_AngPatch_Server", function(ply, mvd, cmd)
        if not ply.D6On then return end
        local synced = ply.D6AngSynced
        if not synced then return end

        -- Запоминаем оригинал только один раз за тик
        if not ply._D6OrigEyeAngles then
            ply._D6OrigEyeAngles = true

            -- Monkey-patch EyeAngles на этот серверный тик
            local origEA = ply.EyeAngles
            ply.EyeAngles = function(self)
                if self.D6On and self.D6AngSynced then
                    return Angle(self.D6AngSynced.p, self.D6AngSynced.y, 0)
                end
                return origEA(self)
            end

            -- Сбросить в конце тика
            timer.Simple(0, function()
                if IsValid(ply) then
                    ply.EyeAngles = nil
                    ply._D6OrigEyeAngles = nil
                end
            end)
        end
    end)

    -- Дополнительный патч: GetAimVector тоже используется SWEPами
    hook.Add("Think", "D6_AngPatch_AimVec", function()
        for _, ply in ipairs(player.GetAll()) do
            if not IsValid(ply) or not ply.D6On then continue end
            local synced = ply.D6AngSynced
            if not synced then continue end

            -- SetEyeAngles синхронизирует серверный угол взгляда
            -- Это влияет на GetAimVector и стандартный EyeTrace
            ply:SetEyeAngles(Angle(synced.p, synced.y, 0))
        end
    end)

end

-- ── SWEP-хук: перехват PrimaryAttack / SecondaryAttack ────
-- Самый надёжный способ — оборачиваем вызов атаки.
-- До вызова: подменяем GetOwner():EyeAngles() временно.
-- После вызова: восстанавливаем.
--
-- Работает для ЛЮБОГО SWEP независимо от источника.
if SERVER then

    local _origCallSWEP = {}

    local function PatchWeapon(wep)
        if not IsValid(wep) then return end
        if wep._D6AngPatched then return end
        wep._D6AngPatched = true

        local origPrimary   = wep.PrimaryAttack
        local origSecondary = wep.SecondaryAttack

        -- Временная подмена EyeAngles на время вызова атаки
        local function WithPatchedAngle(self, fn, ...)
            if not fn then return end
            local owner = self:GetOwner()
            local patched = false

            if IsValid(owner) and owner:IsPlayer()
            and owner.D6On and owner.D6AngSynced then
                local synced = owner.D6AngSynced
                local cleanAng = Angle(synced.p, synced.y, 0)

                -- Временная функция: flat-угол (без крена для прицела)
                local orig = owner.EyeAngles
                owner.EyeAngles = function(s)
                    return cleanAng
                end
                patched = true

                local ok, err = pcall(fn, self, ...)

                -- Восстанавливаем
                owner.EyeAngles = orig

                if not ok then
                    MsgC(Color(255,100,0),
                        "[D6 AngPatch] Ошибка в " .. tostring(wep:GetClass())
                        .. ": " .. tostring(err) .. "\n")
                end
                return
            end

            fn(self, ...)
        end

        if origPrimary then
            wep.PrimaryAttack = function(self)
                WithPatchedAngle(self, origPrimary)
            end
        end

        if origSecondary then
            wep.SecondaryAttack = function(self)
                WithPatchedAngle(self, origSecondary)
            end
        end
    end

    -- Патчим оружие когда игрок его берёт
    hook.Add("PlayerSwitchWeapon", "D6_AngPatch_Switch", function(ply, old, new)
        if not IsValid(ply) then return end
        timer.Simple(0.1, function()
            if IsValid(new) then PatchWeapon(new) end
        end)
    end)

    -- Патчим оружие при выдаче (Give)
    hook.Add("PlayerGivenWeapon", "D6_AngPatch_Give", function(ply, wep)
        timer.Simple(0.1, function()
            if IsValid(wep) then PatchWeapon(wep) end
        end)
    end)

    -- Патчим уже имеющееся оружие когда игрок входит в 6DOF
    hook.Add("Think", "D6_AngPatch_D6Enter", function()
        for _, ply in ipairs(player.GetAll()) do
            if not IsValid(ply) or not ply.D6On then continue end
            if ply._D6AngPatchDone then continue end
            ply._D6AngPatchDone = true

            for _, wep in ipairs(ply:GetWeapons()) do
                if IsValid(wep) then PatchWeapon(wep) end
            end
        end
        -- Сбрасываем флаг когда игрок выходит из 6DOF
        for _, ply in ipairs(player.GetAll()) do
            if IsValid(ply) and not ply.D6On and ply._D6AngPatchDone then
                ply._D6AngPatchDone = nil
            end
        end
    end)

end

-- =========================================================
-- КЛИЕНТСКИЙ ПАТЧ: подмена cmd:SetViewAngles
-- Многие SWEP смотрят на cmd:GetViewAngles() или
-- LocalPlayer():EyeAngles() для построения трассеров, HUD,
-- DrawWorldModel. Без патча трассеры идут по стандартному
-- углу (без крена) и расходятся с реальным взглядом.
--
-- Решение: записываем D6Ang в cmd, чтобы все SWEP
-- (включая внешние) видели полный угол с креном.
-- Выполняется ПОСЛЕ D6_Cam_CM (он же ставит D6Ang).
-- =========================================================
if CLIENT then
    hook.Add("CreateMove", "D6_AngPatch_Client", function(cmd)
        local ply = LocalPlayer()
        if not IsValid(ply) or not ply:GetNWBool("D6On", false) then return end
        if not ply.D6Ang then return end
        cmd:SetViewAngles(ply.D6Ang)
    end)
end

print("[D6] d6_ang_patch.lua OK")
