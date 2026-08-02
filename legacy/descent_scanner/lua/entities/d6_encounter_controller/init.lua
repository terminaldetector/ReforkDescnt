include("shared.lua")

local function FireOut(ent, name, value)
    if ent.TriggerOutput then ent:TriggerOutput(name, ent, value or "") end
end

function ENT:Initialize()
    self:SetModel("models/props_combine/combine_mine01.mdl")
    self:SetSolid(SOLID_NONE)
    self:SetNoDraw(true)
    self:DrawShadow(false)

    local kvs = self:GetKeyValues()
    self.D6_WaveStr    = kvs["waves"]          or ""
    self.D6_AutoStart  = (kvs["auto_start"]    == "1")
    self.D6_TrigRadius = tonumber(kvs["trigger_radius"]) or 1000
    self.D6_SpawnRadius= 800
    self.D6_WaveDelay  = tonumber(kvs["wave_delay"])  or 4.0
    self.D6_BossWave   = tonumber(kvs["boss_wave"])   or 0
    self.D6_BossVariant= kvs["boss_variant"]   or "heavy_elite"

    self.D6_Waves      = D6_ParseWaveString and D6_ParseWaveString(self.D6_WaveStr) or {}
    self.D6_WaveIdx    = 0
    self.D6_Active     = false
    self.D6_WaveNPCs   = {}
    self.D6_WaveSpawned= false
    self.D6_PendingCheck = false

    if D6_EncounterManager then
        D6_EncounterManager[self:EntIndex()] = self
    end
end

function ENT:AcceptInput(name, activator, caller, data)
    local n = name:lower()
    if     n == "startencounter" then self:StartEncounter()
    elseif n == "stopencounter"  then self:StopEncounter()
    elseif n == "nextwave"       then self:NextWave() end
end

function ENT:StartEncounter()
    if self.D6_Active then return end
    self.D6_Active   = true
    self.D6_WaveIdx  = 0
    self.D6_WaveNPCs = {}
    timer.Simple(tonumber(self:GetKeyValues()["spawn_delay"]) or 2.0, function()
        if IsValid(self) and self.D6_Active then self:NextWave() end
    end)
end

function ENT:StopEncounter()
    self.D6_Active      = false
    self.D6_WaveNPCs    = {}
    self.D6_WaveSpawned = false
end

function ENT:NextWave()
    if not self.D6_Active then return end
    self.D6_WaveIdx = self.D6_WaveIdx + 1
    local wave = self.D6_Waves[self.D6_WaveIdx]
    if not wave then
        -- All waves complete
        FireOut(self, "OnEncounterComplete", "")
        self.D6_Active = false
        MsgC(Color(80, 200, 140),
            "[D6 Enc] Encounter complete on controller #" .. self:EntIndex() .. "\n")
        return
    end

    -- Boss wave injection
    if self.D6_BossWave > 0 and self.D6_WaveIdx == self.D6_BossWave then
        wave[#wave + 1] = { variant = self.D6_BossVariant, count = 1 }
        FireOut(self, "OnBossSpawn", "")
    end

    self.D6_WaveNPCs    = {}
    self.D6_WaveSpawned = false

    local count = D6_SpawnEncounterWave and D6_SpawnEncounterWave(self, wave) or 0
    self.D6_WaveSpawned = (count > 0)

    FireOut(self, "OnWaveStart", tostring(self.D6_WaveIdx))
    MsgC(Color(80, 200, 140),
        string.format("[D6 Enc] Wave %d started (%d NPCs)\n", self.D6_WaveIdx, count))
end

function ENT:Think()
    local ct = CurTime()

    -- Auto-start: wait for player in radius
    if not self.D6_Active and self.D6_AutoStart and self.D6_WaveIdx == 0 then
        for _, ply in ipairs(player.GetAll()) do
            if IsValid(ply) and ply:GetPos():Distance(self:GetPos()) < self.D6_TrigRadius then
                self.D6_AutoStart = false  -- fire once
                self:StartEncounter()
                break
            end
        end
    end

    -- Live wave: clean dead NPCs and check completion
    if self.D6_Active and self.D6_WaveSpawned then
        local alive = {}
        for _, npc in ipairs(self.D6_WaveNPCs) do
            if IsValid(npc) then alive[#alive + 1] = npc end
        end
        self.D6_WaveNPCs = alive
        if #alive == 0 then
            self.D6_WaveSpawned = false
            FireOut(self, "OnWaveComplete", tostring(self.D6_WaveIdx))
            timer.Simple(self.D6_WaveDelay, function()
                if IsValid(self) and self.D6_Active then self:NextWave() end
            end)
        end
    end

    self:NextThink(ct + 0.5)
    return true
end

function ENT:OnRemove()
    if D6_EncounterManager then
        D6_EncounterManager[self:EntIndex()] = nil
    end
end
