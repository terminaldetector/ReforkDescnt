-- =========================================================
-- weapon_d6_deployer.lua
-- Q-меню: Descent 6DOF / Оружие
-- Развёртыватель дронов: ЛКМ = одиночный враг, ПКМ = отряд
-- =========================================================
SWEP.PrintName      = "D6: Развёртыватель дронов"
SWEP.Author         = "Descent 6DOF"
SWEP.Instructions   = "ЛКМ: одиночный враг  ПКМ: полный отряд  колесо: тип"
SWEP.Category       = "Descent 6DOF / Оружие"
SWEP.Spawnable      = true
SWEP.AdminSpawnable = true
SWEP.Base           = "weapon_base"
SWEP.HoldType       = "normal"

SWEP.Primary.ClipSize=-1; SWEP.Primary.DefaultClip=-1
SWEP.Primary.Automatic=false; SWEP.Primary.Ammo="none"
SWEP.Secondary.ClipSize=-1; SWEP.Secondary.DefaultClip=-1
SWEP.Secondary.Automatic=false; SWEP.Secondary.Ammo="none"
SWEP.DrawAmmo=false; SWEP.DrawCrosshair=true
SWEP.ViewModel  = "models/weapons/v_hands.mdl"
SWEP.WorldModel = "models/combine_scanner.mdl"

local TYPES = {"mine","mg","rpg","grav","laser","heavy","seeker","worm","wheatley","chopper","manhack"}
local CMDS  = {
    mine="6dof_spawn_mine", mg="6dof_spawn_mg", rpg="6dof_spawn_rpg",
    grav="6dof_spawn_grav", laser="6dof_spawn_laser", heavy="6dof_spawn_heavy",
    seeker="6dof_spawn_heavy_seeker", worm="6dof_spawn_worm",
    wheatley="6dof_spawn_wheatley", chopper="6dof_spawn_chopper", manhack="6dof_spawn_manhack",
}

function SWEP:Initialize() self:SetHoldType("normal"); self.TypeIdx=1 end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    if CurTime()<(self.NextShot or 0) then return end; self.NextShot=CurTime()+0.5
    local ply=self:GetOwner(); if not IsValid(ply) then return end
    local t=TYPES[self.TypeIdx] or "mg"
    local cmd=CMDS[t]
    if cmd then ply:ConCommand(cmd) end
    ply:EmitSound("buttons/button14.wav",60,100)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    if CurTime()<(self.NextAlt or 0) then return end; self.NextAlt=CurTime()+1.5
    local ply=self:GetOwner(); if not IsValid(ply) then return end
    ply:ConCommand("6dof_spawn_squad")
    ply:EmitSound("ambient/explosions/explode_1.wav",70,130)
end

function SWEP:Reload()
    self.TypeIdx=((self.TypeIdx or 1)%#TYPES)+1
    local ply=self:GetOwner()
    if IsValid(ply) then
        ply:PrintMessage(HUD_PRINTTALK,"[ДРОН] "..TYPES[self.TypeIdx])
        ply:EmitSound("buttons/button14.wav",60,120)
    end
end

function SWEP:DrawHUD()
    if not CLIENT then return end
    local t=TYPES[self.TypeIdx or 1] or "?"
    local sw,sh=ScrW(),ScrH()
    draw.SimpleTextOutlined("[ ДРОН: "..string.upper(t).." ]","DermaDefault",
        sw*.5,sh-55,Color(0,220,120),TEXT_ALIGN_CENTER,TEXT_ALIGN_CENTER,1,Color(0,0,0,200))
    draw.SimpleTextOutlined("R: смена типа   ПКМ: отряд","DermaDefault",
        sw*.5,sh-72,Color(180,180,180,180),TEXT_ALIGN_CENTER,TEXT_ALIGN_CENTER,1,Color(0,0,0,150))
end
