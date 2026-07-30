-- =========================================================
-- weapon_d6_spawn_squad.lua
-- Q-меню GMod → Weapons → "Descent 6DOF / Враги"
--
-- ПРИНЦИП ФЛЕШЕТЫ:
--   ЛКМ → TraceHull к точке прицела → ents.Create(npc_class)
--         → SetModel → Spawn → Activate → SpawnEnemyDrone()
--   Снаряды = prop_physics с HL2-моделями (видны сразу)
--   NPC получают полный AI из d6_ai.lua / d6_frags.lua
-- =========================================================
SWEP.PrintName      = "D6: Боевой отряд"
SWEP.Author         = "Descent 6DOF"
SWEP.Instructions   = "ЛКМ: полный отряд   ПКМ: двойной отряд"
SWEP.Category       = "Descent 6DOF / Отряды"
SWEP.Spawnable      = true
SWEP.AdminSpawnable = true
SWEP.Base           = "weapon_base"
SWEP.HoldType       = "normal"

SWEP.Primary.ClipSize      = 1
SWEP.Primary.DefaultClip   = 1
SWEP.Primary.Automatic     = false
SWEP.Primary.Ammo          = "none"

SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

SWEP.DrawAmmo      = false
SWEP.DrawCrosshair = true
SWEP.ViewModel     = "models/weapons/v_hands.mdl"
SWEP.WorldModel    = "models/combine_scanner.mdl"

-- Кулдаун между спавнами NPC
local SPAWN_CD = 5.0

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
    local function Squad(ply, offset)
        offset = offset or Vector(0,0,0)
        local p = ply:GetPos() + ply:GetAimVector()*200 + offset
        local types = {
            {"npc_cscanner","heavy"},{"npc_cscanner","mg"},{"npc_cscanner","mg"},
            {"npc_cscanner","rpg"},{"npc_cscanner","laser"},{"npc_cscanner","mine"},
        }
        if SpawnEnemyDrone then
            for i,t in ipairs(types) do
                local off=Vector(math.Rand(-120,120),math.Rand(-120,120),math.Rand(0,60))
                timer.Simple((i-1)*0.2,function()
                    if IsValid(ply) then SpawnEnemyDrone(ply,t[1],t[2]) end
                end)
            end
        else
            ply:ConCommand("6dof_spawn_squad")
        end
    end

    function SWEP:DoSpawn(ply, pos)
        Squad(ply)
        ply:EmitSound("ambient/explosions/explode_1.wav",70,80)
    end
    function SWEP:DoSecondary(ply)
        if CurTime()<(self.NextAlt or 0) then return end; self.NextAlt=CurTime()+12
        Squad(ply, Vector(-150,-150,0))
        Squad(ply, Vector( 150, 150,0))
        ply:EmitSound("ambient/explosions/explode_1.wav",75,70)
    end
end
