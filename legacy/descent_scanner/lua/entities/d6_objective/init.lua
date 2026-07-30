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
    self.D6_ObjType    = kvs["objective_type"] or "reach"
    self.D6_TimeLimit  = tonumber(kvs["time_limit"]) or 0
    self.D6_TargetEnt  = kvs["target_entity"] or ""
    self.D6_Active     = false
    self.D6_StartTime  = 0
    self.D6_TouchRadius= tonumber(kvs["touch_radius"]) or 80
end

function ENT:AcceptInput(name, activator, caller, data)
    local n = name:lower()
    if n == "start" then
        self.D6_Active    = true
        self.D6_StartTime = CurTime()
        FireOut(self, "OnStart", "")
    elseif n == "complete" then
        self.D6_Active = false
        FireOut(self, "OnComplete", "")
    elseif n == "fail" then
        self.D6_Active = false
        FireOut(self, "OnFail", "")
    end
end

function ENT:Think()
    if not self.D6_Active then return end

    -- Time limit check
    if self.D6_TimeLimit > 0 and CurTime() - self.D6_StartTime > self.D6_TimeLimit then
        self.D6_Active = false
        FireOut(self, "OnFail", "time")
        return
    end

    -- Reach objective: player comes near
    if self.D6_ObjType == "reach" then
        for _, ply in ipairs(player.GetAll()) do
            if IsValid(ply) and ply:GetPos():Distance(self:GetPos()) < self.D6_TouchRadius then
                self.D6_Active = false
                FireOut(self, "OnComplete", ply:SteamID())
                return
            end
        end
    end

    self:NextThink(CurTime() + 0.2)
    return true
end
