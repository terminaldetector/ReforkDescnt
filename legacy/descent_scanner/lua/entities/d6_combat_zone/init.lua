include("shared.lua")

local function FireOut(ent, name, value)
    if ent.TriggerOutput then ent:TriggerOutput(name, ent, value or "") end
end

function ENT:Initialize()
    self:SetTrigger(true)
    self:SetSolid(SOLID_BSP)
    self.D6_PlayersInside = {}
end

function ENT:StartTouch(other)
    if not IsValid(other) or not other:IsPlayer() then return end
    local idx = other:EntIndex()
    if self.D6_PlayersInside[idx] then return end
    self.D6_PlayersInside[idx] = true
    FireOut(self, "OnPlayerEnter", "")
end

function ENT:EndTouch(other)
    if not IsValid(other) or not other:IsPlayer() then return end
    self.D6_PlayersInside[other:EntIndex()] = nil
    FireOut(self, "OnPlayerExit", "")
end

function ENT:Touch(other) end  -- required for trigger
