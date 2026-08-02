include("shared.lua")

local function FireOut(ent, name, value)
    if ent.TriggerOutput then ent:TriggerOutput(name, ent, value or "") end
end

function ENT:Initialize()
    self:SetTrigger(true)
    self:SetSolid(SOLID_BSP)
    self.D6_Inside = {}
end

function ENT:StartTouch(other)
    if not IsValid(other) or not other:IsPlayer() then return end
    if not other:GetNWBool("D6On", false) then return end
    local idx = other:EntIndex()
    if self.D6_Inside[idx] then return end
    self.D6_Inside[idx] = true
    FireOut(self, "OnD6Enter", "")
end

function ENT:EndTouch(other)
    if not IsValid(other) or not other:IsPlayer() then return end
    if not self.D6_Inside[other:EntIndex()] then return end
    self.D6_Inside[other:EntIndex()] = nil
    FireOut(self, "OnD6Exit", "")
end

function ENT:Touch(other) end
