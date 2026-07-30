include("shared.lua")

local function FireOut(ent, name, value)
    if ent.TriggerOutput then ent:TriggerOutput(name, ent, value or "") end
end

function ENT:Initialize()
    self:SetModel("models/editor/axis_helper.mdl")
    self:SetSolid(SOLID_NONE)
    self:SetNoDraw(true)
    self:DrawShadow(false)

    local kvs = self:GetKeyValues()
    self.D6_Variant      = kvs["variant"]       or "mg"
    self.D6_SpawnCount   = tonumber(kvs["spawn_count"])   or 1
    self.D6_Respawn      = (kvs["respawn"]    == "1")
    self.D6_RespawnDelay = tonumber(kvs["respawn_delay"]) or 30
    self.D6_Enabled      = (kvs["enabled"] ~= "0")
    self.D6_SpawnedNPCs  = {}
    self.D6_Spawning     = false

    if self.D6_Enabled then
        timer.Simple(0.5, function()
            if IsValid(self) then self:StartSpawning() end
        end)
    end
end

function ENT:AcceptInput(name, activator, caller, data)
    local n = name:lower()
    if     n == "startspawning" then self:StartSpawning()
    elseif n == "stopspawning"  then self.D6_Spawning = false end
end

function ENT:StartSpawning()
    if self.D6_Spawning then return end
    self.D6_Spawning = true
    self:DoSpawn()
end

function ENT:DoSpawn()
    if not IsValid(self) or not self.D6_Spawning then return end
    self.D6_SpawnedNPCs = {}
    local spawnFn = SpawnD6Role or SpawnEnemyDrone
    for i = 1, self.D6_SpawnCount do
        local offset = VectorRand() * 60
        local npc
        if SpawnD6Role then
            npc = SpawnD6Role(self.D6_Variant, self:GetPos() + offset, nil)
        elseif SpawnEnemyDrone then
            npc = SpawnEnemyDrone(nil, "npc_cscanner", self.D6_Variant,
                self:GetPos() + offset)
        end
        if IsValid(npc) then
            npc.D6_SpawnerOwner = self
            self.D6_SpawnedNPCs[#self.D6_SpawnedNPCs + 1] = npc
        end
    end
    FireOut(self, "OnSpawned", "")
end

function ENT:Think()
    if not self.D6_Spawning then return end
    -- Check if all spawned NPCs are dead
    local alive = 0
    for _, npc in ipairs(self.D6_SpawnedNPCs) do
        if IsValid(npc) then alive = alive + 1 end
    end
    if alive == 0 and #self.D6_SpawnedNPCs > 0 then
        self.D6_SpawnedNPCs = {}
        FireOut(self, "OnKilled", "")
        if self.D6_Respawn then
            timer.Simple(self.D6_RespawnDelay, function()
                if IsValid(self) and self.D6_Spawning then self:DoSpawn() end
            end)
        else
            self.D6_Spawning = false
        end
    end
    self:NextThink(CurTime() + 0.5)
    return true
end
