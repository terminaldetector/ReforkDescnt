-- =========================================================
-- drmd_npc_workshop.lua — STOOL конструктора противников DRMD
--
--   Toolgun-интеграция NPC Workshop (скелет — по аудиту
--   npc-toolset/npccontrol, логика — целиком DRMD):
--     ЛКМ    — заспавнить текущий профиль в точке прицела
--     ПКМ    — снять профиль с прицельного DRMD-дрона
--              и отправить в редактор (Q → DRMD Workshop)
--     Reload — удалить прицельного DRMD-дрона
--
--   Профили хранятся на сервере (DATA/drmd_npcs/, JSON),
--   спавн — через D6_SpawnNPCProfile → D6_BuildDrone.
-- =========================================================

TOOL.Category = "DRMD Tools"
TOOL.Name     = "NPC Workshop"

TOOL.ClientConVar["profile"] = "my_drone"

TOOL.Information = {
    { name = "left" },
    { name = "right" },
    { name = "reload" },
}

if CLIENT then
    language.Add("tool.drmd_npc_workshop.name", "NPC Workshop — конструктор противников")
    language.Add("tool.drmd_npc_workshop.desc", "Спавн и снятие профилей DRMD-дронов")
    language.Add("tool.drmd_npc_workshop.left", "Заспавнить выбранный профиль")
    language.Add("tool.drmd_npc_workshop.right", "Снять профиль с дрона в редактор")
    language.Add("tool.drmd_npc_workshop.reload", "Удалить DRMD-дрона")
end

local function IsD6Drone(ent)
    return IsValid(ent) and (ent.D6_AI ~= nil or ent.D6_Variant ~= nil)
end

function TOOL:LeftClick(trace)
    if not trace.Hit or trace.HitSky then return false end
    if CLIENT then return true end

    local ply  = self:GetOwner()
    local name = self:GetClientInfo("profile")
    if not (D6_NPCWorkshop and D6_NPCWorkshop.Profiles[name]) then
        ply:PrintMessage(HUD_PRINTTALK,
            "[D6 NPC] Профиль «" .. tostring(name) .. "» не найден. "
            .. "Сохрани его: Q → DRMD Workshop → NPC Constructor.")
        return false
    end

    local npc = D6_SpawnNPCProfile(name, trace.HitPos + trace.HitNormal * 60, ply)
    if not IsValid(npc) then return false end

    undo.Create("D6 NPC (" .. name .. ")")
        undo.AddEntity(npc)
        undo.SetPlayer(ply)
    undo.Finish()
    cleanup.Add(ply, "npcs", npc)

    return true
end

function TOOL:RightClick(trace)
    local ent = trace.Entity
    if not IsD6Drone(ent) then return false end
    if CLIENT then return true end

    local ply = self:GetOwner()
    local lo  = ent.D6_Loadout
    if not istable(lo) then
        ply:PrintMessage(HUD_PRINTTALK,
            "[D6 NPC] У этого дрона нет loadout (легаси-вариант) — снять профиль нельзя.")
        return false
    end

    local profile = D6_NPCWorkshop.FromLoadout(lo,
        ent.D6_Profile or ent.D6_Variant or "custom")
    net.Start("D6_NPCWS_PushWorking")
        net.WriteString(util.TableToJSON(profile))
    net.Send(ply)

    ply:PrintMessage(HUD_PRINTTALK,
        "[D6 NPC] Профиль снят с дрона → редактор (Q → DRMD Workshop).")
    return true
end

function TOOL:Reload(trace)
    local ent = trace.Entity
    if not IsD6Drone(ent) then return false end
    if CLIENT then return true end
    ent:Remove()
    return true
end

function TOOL.BuildCPanel(panel)
    panel:Help("Конструктор противников DRMD. Полный редактор — "
        .. "Q → DRMD Workshop → NPC Constructor.")

    -- Селектор профиля (паттерн пресет-комбо из npc-toolset)
    local combo = panel:ComboBox("Профиль", "drmd_npc_workshop_profile")
    local function FillCombo()
        combo:Clear()
        if D6_NPCWorkshop then
            for _, n in ipairs(D6_NPCWorkshop.GetNames()) do
                combo:AddChoice(n)
            end
        end
    end
    FillCombo()

    local refreshBtn = panel:Button("Обновить список профилей")
    refreshBtn.DoClick = FillCombo

    panel:ControlHelp("ЛКМ — спавн профиля; ПКМ — снять профиль с дрона "
        .. "в редактор; Reload — удалить дрона.")

    panel:Help("Быстрые стоковые роли")
    for _, v in ipairs({ "assault", "interceptor", "artillery",
                         "support", "heavy_elite" }) do
        local b = panel:Button("Спавн роли: " .. v)
        b.DoClick = function() RunConsoleCommand("6dof_spawn_" .. v) end
    end
end
