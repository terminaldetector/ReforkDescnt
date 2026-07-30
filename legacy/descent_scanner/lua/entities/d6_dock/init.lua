include("shared.lua")

local function FireOut(ent, name, value)
    if ent.TriggerOutput then ent:TriggerOutput(name, ent, value or "") end
end

function ENT:Initialize()
    self:SetModel("models/props_combine/breencabinet.mdl")
    self:SetSolid(SOLID_VPHYSICS)
    self:PhysicsInit(SOLID_VPHYSICS)
    self:SetMoveType(MOVETYPE_NONE)

    local kvs = self:GetKeyValues()
    self.D6_HealAmt    = tonumber(kvs["heal_amount"])    or 25
    self.D6_EnergyAmt  = tonumber(kvs["energy_amount"])  or 20
    self.D6_Radius     = tonumber(kvs["radius"])         or 200
    self.D6_Enabled    = (kvs["enabled"] ~= "0")
    self.D6_Cooldowns  = {}  -- per player cooldown (SteamID → nextHeal time)
end

function ENT:AcceptInput(name, activator, caller, data)
    local n = name:lower()
    if     n == "enable"  then self.D6_Enabled = true
    elseif n == "disable" then self.D6_Enabled = false end
end

function ENT:Think()
    if not self.D6_Enabled then return end
    local ct = CurTime()
    local myPos = self:GetPos()

    for _, ply in ipairs(player.GetAll()) do
        if not IsValid(ply) then continue end
        if myPos:Distance(ply:GetPos()) > self.D6_Radius then continue end

        local sid = ply:SteamID()
        if (self.D6_Cooldowns[sid] or 0) > ct then continue end
        self.D6_Cooldowns[sid] = ct + 0.5

        -- Heal HP
        local hp    = ply:Health()
        local maxHp = ply:GetMaxHealth()
        if hp < maxHp then
            ply:SetHealth(math.min(hp + self.D6_HealAmt * 0.5, maxHp))
        end

        -- Replenish energy
        local energy = ply:GetNWFloat("D6_WepEnergy", 0)
        if energy < 100 then
            ply:SetNWFloat("D6_WepEnergy", math.min(energy + self.D6_EnergyAmt * 0.5, 100))
        end

        FireOut(self, "OnUse", ply:SteamID())
    end

    self:NextThink(ct + 0.5)
    return true
end
