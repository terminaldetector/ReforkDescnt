-- =========================================================
-- weapon_d6_spawn_manhack.lua
-- Q-меню GMod → Weapons → "Descent 6DOF / Враги"
--
-- ПРИНЦИП ФЛЕШЕТЫ:
--   ЛКМ → TraceHull к точке прицела → ents.Create(npc_class)
--         → SetModel → Spawn → Activate → SpawnEnemyDrone()
--   Снаряды = prop_physics с HL2-моделями (видны сразу)
--   NPC получают полный AI из d6_ai.lua / d6_frags.lua
-- =========================================================
SWEP.PrintName      = "D6: Манхок-камикадзе"
SWEP.Author         = "Descent 6DOF"
SWEP.Instructions   = "ЛКМ: заспавнить   ПКМ: рой 6"
SWEP.Category       = "Descent 6DOF / Враги"
SWEP.Spawnable      = true
SWEP.AdminSpawnable = true
SWEP.Base           = "weapon_base"
SWEP.HoldType       = "normal"

SWEP.Primary.ClipSize      = 10
SWEP.Primary.DefaultClip   = 10
SWEP.Primary.Automatic     = false
SWEP.Primary.Ammo          = "none"

SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

SWEP.DrawAmmo      = false
SWEP.DrawCrosshair = true
SWEP.ViewModel     = "models/weapons/v_hands.mdl"
SWEP.WorldModel    = "models/manhack.mdl"

-- Кулдаун между спавнами NPC
local SPAWN_CD = 0.8

function SWEP:Initialize()
    self:SetHoldType("normal")
    self.NextSpawn = 0
end

-- Получить точку спавна (трассировка как у флешеты)
local function GetSpawnPos(ply)
    local tr = util.TraceLine({
        start  = ply:GetPos() + Vector(0,0,16),
        endpos = ply:GetPos() + ply:GetAimVector() * 512,
        filter = ply,
        mask   = MASK_NPCSOLID,
    })
    -- Если попали в поверхность — чуть выше, иначе 300 юн перед игроком
    if tr.Hit then
        return tr.HitPos + tr.HitNormal * 24
    end
    return ply:GetPos() + ply:GetAimVector() * 300
end


local function SpawnMH(ply, pos)
    if SpawnManhackKamikaze then SpawnManhackKamikaze(ply, pos)
    else
        local npc=ents.Create("npc_manhack"); if not IsValid(npc) then return end
        npc:SetPos(pos); npc:Spawn(); npc:Activate()
        npc:SetMaxHealth(30); npc:SetHealth(30)
        npc:SetColor(Color(255,60,0)); npc.D6_Variant="manhack_fast"
        npc.D6_Detonated=false
        npc:AddEntityRelationship(ply,D_HT,99)
    end
end

-- ── Серверная логика спавна ─────────────────────────────
if SERVER then
    function SWEP:PrimaryAttack()
        local ply = self:GetOwner()
        if not IsValid(ply) then return end
        if CurTime() < self.NextSpawn then
            ply:PrintMessage(HUD_PRINTTALK, string.format(
                "[D6] Перезарядка: %.1f с", self.NextSpawn - CurTime()))
            return
        end
        self.NextSpawn = CurTime() + SPAWN_CD

        local pos = GetSpawnPos(ply)
        self:DoSpawn(ply, pos)
        self:TakePrimaryAmmo(1)
        ply:EmitSound("buttons/button14.wav", 60, 80)
    end

    function SWEP:SecondaryAttack()
        local ply = self:GetOwner()
        if not IsValid(ply) then return end
        self:DoSecondary(ply)
    end
end
-- ─────────────────────────────────────────────────────────

if SERVER then
    function SWEP:DoSpawn(ply, pos) SpawnMH(ply, pos) end
    function SWEP:DoSecondary(ply)
        if CurTime()<(self.NextAlt or 0) then return end; self.NextAlt=CurTime()+4
        local p=ply:GetPos(); local sa=ply:GetAimVector()
        for i=1,6 do SpawnMH(ply,p+sa*150+Vector(math.Rand(-80,80),math.Rand(-80,80),math.Rand(0,60))) end
    end
end
