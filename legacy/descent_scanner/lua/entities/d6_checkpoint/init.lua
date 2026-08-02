include("shared.lua")

local function FireOut(ent, name, value)
    if ent.TriggerOutput then ent:TriggerOutput(name, ent, value or "") end
end

function ENT:Initialize()
    self:SetModel("models/editor/axis_helper.mdl")
    self:SetSolid(SOLID_NONE)
    self:DrawShadow(false)

    local kvs = self:GetKeyValues()
    self.D6_SingleUse  = (kvs["single_use"] ~= "0")
    self.D6_Radius     = tonumber(kvs["radius"]) or 120
    self.D6_Enabled    = true
    self.D6_Activated  = {}
    self:SetNoDraw(true)
end

function ENT:AcceptInput(name, activator, caller, data)
    local n = name:lower()
    if n == "activate"   then self.D6_Enabled = true
    elseif n == "deactivate" then self.D6_Enabled = false end
end

function ENT:Think()
    if not self.D6_Enabled then return end
    for _, ply in ipairs(player.GetAll()) do
        if not IsValid(ply) then continue end
        if self.D6_SingleUse and self.D6_Activated[ply:SteamID()] then continue end
        if ply:GetPos():Distance(self:GetPos()) < self.D6_Radius then
            ply.D6_CheckpointPos = self:GetPos() + Vector(0, 0, 40)
            if self.D6_SingleUse then
                self.D6_Activated[ply:SteamID()] = true
            end
            FireOut(self, "OnActivated", ply:SteamID())
            ply:ChatPrint("[D6] Checkpoint saved.")
        end
    end
    self:NextThink(CurTime() + 0.3)
    return true
end
