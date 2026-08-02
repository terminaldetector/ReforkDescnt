include("shared.lua")

function ENT:Initialize()
    self:SetModel("models/editor/axis_helper.mdl")
    self:SetSolid(SOLID_NONE)
    self:SetNoDraw(true)
    self:DrawShadow(false)

    local kvs = self:GetKeyValues()
    local radius = tonumber(kvs["radius"])   or 64
    local group  = kvs["patrol_group"]       or ""

    D6_NavRegisterNode(self:EntIndex(), self:GetPos(), radius, group)
end

function ENT:OnRemove()
    D6_NavUnregisterNode(self:EntIndex())
end
