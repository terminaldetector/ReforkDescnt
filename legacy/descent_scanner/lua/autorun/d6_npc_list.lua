if CLIENT then

    local function RegNPC(key, name, class, model, category)
        list.Set("NPC", key, {
            Name      = name,
            Class     = key,
            Category  = category or "Descent 6DOF",
            Model     = model,
            Weapons   = {},
            KeyValues = {},
            NPC       = true,
        })
    end

    RegNPC("d6_npc_mine",   "D6: Воздушная мина",    "npc_cscanner",
        "models/props_combine/combine_mine01.mdl",  "Descent 6DOF / Дроны")

    RegNPC("d6_npc_mg",     "D6: Пулемётный сканер", "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Дроны")

    RegNPC("d6_npc_rpg",    "D6: РПГ-сканер",        "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Дроны")

    RegNPC("d6_npc_grav",   "D6: Грави-мастер",      "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Дроны")

    RegNPC("d6_npc_laser",  "D6: Лазерный сканер",   "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Дроны")

    RegNPC("d6_npc_heavy",  "D6: Тяжёлый сканер",    "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Дроны")

    RegNPC("d6_npc_seeker", "D6: Ракетный сикер",    "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Дроны")

    RegNPC("d6_npc_worm",      "D6: Альянсский червь",    "npc_headcrab",
        "models/headcrab.mdl",                      "Descent 6DOF / Бестиарий")

    RegNPC("d6_npc_manhack",   "D6: Манхок-камикадзе",   "npc_manhack",
        "models/manhack.mdl",                       "Descent 6DOF / Бестиарий")

    RegNPC("d6_npc_squad",     "D6: Боевой отряд (все)", "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Группы")

    RegNPC("d6_npc_bestiary",  "D6: Весь бестиарий",     "npc_headcrab",
        "models/headcrab.mdl",                      "Descent 6DOF / Группы")

    -- Wave 2
    RegNPC("d6_npc_rocket_elite_homing", "D6: Ракетная элита (самонаведение)", "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Волна 2")

    RegNPC("d6_npc_rocket_elite_aa", "D6: Ракетная элита (зенитная)", "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Волна 2")

    RegNPC("d6_npc_rocket_strider", "D6: Ракетный страйдер", "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Волна 2")

    RegNPC("d6_npc_plasma_strider", "D6: Плазменный страйдер", "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Волна 2")

    RegNPC("d6_npc_wave2",  "D6: Волна 2 (все элиты)", "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Волна 2")

    -- Role variants
    RegNPC("d6_npc_assault",     "D6: Штурмовик",         "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Роли")

    RegNPC("d6_npc_interceptor", "D6: Перехватчик",       "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Роли")

    RegNPC("d6_npc_artillery",   "D6: Артиллерия",        "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Роли")

    RegNPC("d6_npc_support",     "D6: Саппорт",           "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Роли")

    RegNPC("d6_npc_heavy_elite", "D6: Тяжёлая элита",     "npc_cscanner",
        "models/combine_scanner.mdl",               "Descent 6DOF / Роли")

end

if SERVER then

    local D6NPC_Spawners = {

        d6_npc_mine = function(ply)
            if SpawnEnemyDrone then SpawnEnemyDrone(ply,"npc_cscanner","mine")
            else
                local n=ents.Create("npc_cscanner"); if not IsValid(n) then return end
                local tr=ply:GetEyeTrace()
                n:SetPos(tr.HitPos+tr.HitNormal*30); n:Spawn(); n:Activate()
                n:SetMaxHealth(50); n:SetHealth(50)
                n:SetColor(Color(255,180,0)); n.D6_Variant="mine"
                n:AddEntityRelationship(ply,D_HT,99)
            end
        end,

        d6_npc_mg = function(ply)
            if SpawnEnemyDrone then SpawnEnemyDrone(ply,"npc_cscanner","mg")
            else
                local n=ents.Create("npc_cscanner"); if not IsValid(n) then return end
                local tr=ply:GetEyeTrace()
                n:SetPos(tr.HitPos+tr.HitNormal*30); n:Spawn(); n:Activate()
                n:SetMaxHealth(120); n:SetHealth(120)
                n:SetColor(Color(255,40,40)); n.D6_Variant="mg"
                n:AddEntityRelationship(ply,D_HT,99)
            end
        end,

        d6_npc_rpg = function(ply)
            if SpawnEnemyDrone then SpawnEnemyDrone(ply,"npc_cscanner","rpg")
            else
                local n=ents.Create("npc_cscanner"); if not IsValid(n) then return end
                local tr=ply:GetEyeTrace()
                n:SetPos(tr.HitPos+tr.HitNormal*30); n:Spawn(); n:Activate()
                n:SetMaxHealth(150); n:SetHealth(150)
                n:SetColor(Color(40,100,255)); n.D6_Variant="rpg"
                n:AddEntityRelationship(ply,D_HT,99)
            end
        end,

        d6_npc_grav = function(ply)
            if SpawnEnemyDrone then SpawnEnemyDrone(ply,"npc_cscanner","grav")
            else
                local n=ents.Create("npc_cscanner"); if not IsValid(n) then return end
                local tr=ply:GetEyeTrace()
                n:SetPos(tr.HitPos+tr.HitNormal*30); n:Spawn(); n:Activate()
                n:SetMaxHealth(150); n:SetHealth(150)
                n:SetColor(Color(160,40,255)); n.D6_Variant="grav"
                n:AddEntityRelationship(ply,D_HT,99)
            end
        end,

        d6_npc_laser = function(ply)
            if SpawnEnemyDrone then SpawnEnemyDrone(ply,"npc_cscanner","laser")
            else
                local n=ents.Create("npc_cscanner"); if not IsValid(n) then return end
                local tr=ply:GetEyeTrace()
                n:SetPos(tr.HitPos+tr.HitNormal*30); n:Spawn(); n:Activate()
                n:SetMaxHealth(150); n:SetHealth(150)
                n:SetColor(Color(0,255,255)); n.D6_Variant="laser"
                n:AddEntityRelationship(ply,D_HT,99)
            end
        end,

        d6_npc_heavy = function(ply)
            if SpawnEnemyDrone then SpawnEnemyDrone(ply,"npc_cscanner","heavy")
            else
                local n=ents.Create("npc_cscanner"); if not IsValid(n) then return end
                local tr=ply:GetEyeTrace()
                n:SetPos(tr.HitPos+tr.HitNormal*30); n:Spawn(); n:Activate()
                n:SetMaxHealth(450); n:SetHealth(450); n:SetModelScale(1.4,0)
                n:SetColor(Color(20,20,20)); n.D6_Variant="heavy"
                n:AddEntityRelationship(ply,D_HT,99)
            end
        end,

        d6_npc_seeker = function(ply)
            if SpawnHeavySeeker then
                local tr=ply:GetEyeTrace()
                SpawnHeavySeeker(ply, tr.HitPos+tr.HitNormal*30)
            else
                local n=ents.Create("npc_cscanner"); if not IsValid(n) then return end
                local tr=ply:GetEyeTrace()
                n:SetPos(tr.HitPos+tr.HitNormal*30); n:Spawn(); n:Activate()
                n:SetMaxHealth(200); n:SetHealth(200); n:SetModelScale(1.6,0)
                n:SetColor(Color(180,0,255)); n.D6_Variant="seeker"
                n.D6_NextMissile=CurTime()+2
                n:AddEntityRelationship(ply,D_HT,99)
            end
        end,

        d6_npc_worm = function(ply)
            if SpawnWorm then
                local tr=ply:GetEyeTrace()
                SpawnWorm(ply, tr.HitPos+tr.HitNormal*20)
            else
                local n=ents.Create("npc_headcrab"); if not IsValid(n) then return end
                local tr=ply:GetEyeTrace()
                n:SetPos(tr.HitPos+tr.HitNormal*20); n:Spawn(); n:Activate()
                n:SetMaxHealth(250); n:SetHealth(250); n:SetModelScale(1.8,0)
                n:SetColor(Color(120,200,60)); n.D6_Variant="worm"
                n:AddEntityRelationship(ply,D_HT,99)
            end
        end,

        d6_npc_manhack = function(ply)
            if SpawnManhackKamikaze then
                local tr=ply:GetEyeTrace()
                SpawnManhackKamikaze(ply, tr.HitPos+tr.HitNormal*20)
            else
                local n=ents.Create("npc_manhack"); if not IsValid(n) then return end
                local tr=ply:GetEyeTrace()
                n:SetPos(tr.HitPos+tr.HitNormal*20); n:Spawn(); n:Activate()
                n:SetMaxHealth(30); n:SetHealth(30)
                n:SetColor(Color(255,60,0)); n.D6_Variant="manhack_fast"
                n:AddEntityRelationship(ply,D_HT,99)
            end
        end,

        d6_npc_squad = function(ply)
            if IsValid(ply) then ply:ConCommand("6dof_spawn_squad") end
        end,

        d6_npc_bestiary = function(ply)
            if IsValid(ply) then ply:ConCommand("6dof_spawn_bestiary") end
        end,

        -- Role variants
        d6_npc_assault = function(ply)
            if not IsValid(ply) then return end
            if SpawnD6Role then
                local tr = ply:GetEyeTrace()
                SpawnD6Role("assault", tr.HitPos + tr.HitNormal * 50, ply)
            end
        end,

        d6_npc_interceptor = function(ply)
            if not IsValid(ply) then return end
            if SpawnD6Role then
                local tr = ply:GetEyeTrace()
                SpawnD6Role("interceptor", tr.HitPos + tr.HitNormal * 50, ply)
            end
        end,

        d6_npc_artillery = function(ply)
            if not IsValid(ply) then return end
            if SpawnD6Role then
                local tr = ply:GetEyeTrace()
                SpawnD6Role("artillery", tr.HitPos + tr.HitNormal * 50, ply)
            end
        end,

        d6_npc_support = function(ply)
            if not IsValid(ply) then return end
            if SpawnD6Role then
                local tr = ply:GetEyeTrace()
                SpawnD6Role("support", tr.HitPos + tr.HitNormal * 50, ply)
            end
        end,

        d6_npc_heavy_elite = function(ply)
            if not IsValid(ply) then return end
            if SpawnD6Role then
                local tr = ply:GetEyeTrace()
                SpawnD6Role("heavy_elite", tr.HitPos + tr.HitNormal * 50, ply)
            end
        end,

        -- Wave 2 (d6_frags2.lua)
        d6_npc_rocket_elite_homing = function(ply)
            if not IsValid(ply) then return end
            ply:ConCommand("6dof_spawn_rocket_elite_homing")
        end,

        d6_npc_rocket_elite_aa = function(ply)
            if not IsValid(ply) then return end
            ply:ConCommand("6dof_spawn_rocket_elite_aa")
        end,

        d6_npc_rocket_strider = function(ply)
            if not IsValid(ply) then return end
            ply:ConCommand("6dof_spawn_rocket_strider")
        end,

        d6_npc_plasma_strider = function(ply)
            if not IsValid(ply) then return end
            ply:ConCommand("6dof_spawn_plasma_strider")
        end,

        d6_npc_wave2 = function(ply)
            if not IsValid(ply) then return end
            ply:ConCommand("6dof_spawn_wave2")
        end,
    }

    hook.Add("PlayerSpawnNPC", "D6_NPCTab_Spawner", function(ply, npcClass, weapon)
        local spawner = D6NPC_Spawners[npcClass]
        if not spawner then return end

        if not gamemode.Call("PlayerCanSpawnNPC", ply, npcClass) then return false end

        local ok, err = pcall(spawner, ply)
        if not ok then
            MsgC(Color(255,100,0), "[D6 NPC] Ошибка спавна "..npcClass..": "..tostring(err).."\n")
        end

        ply:PrintMessage(HUD_PRINTTALK, "[D6] NPC заспавнен: " .. npcClass)

        return false
    end)

end

print("[D6] NPC-вкладка зарегистрирована (" .. (CLIENT and "client" or "server") .. ")")
