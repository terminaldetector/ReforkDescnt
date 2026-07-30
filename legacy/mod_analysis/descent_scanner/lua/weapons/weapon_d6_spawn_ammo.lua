-- =========================================================
-- weapon_d6_spawn_ammo.lua
-- Q-меню GMod → Weapons → "Descent 6DOF / Враги"
--
-- ПРИНЦИП ФЛЕШЕТЫ:
--   ЛКМ → TraceHull к точке прицела → ents.Create(npc_class)
--         → SetModel → Spawn → Activate → SpawnEnemyDrone()
--   Снаряды = prop_physics с HL2-моделями (видны сразу)
--   NPC получают полный AI из d6_ai.lua / d6_frags.lua
-- =========================================================
SWEP.PrintName      = "D6: Пикап боеприпасов"
SWEP.Author         = "Descent 6DOF"
SWEP.Instructions   = "ЛКМ: поставить пикап   ПКМ: сразу 3 пикапа"
SWEP.Category       = "Descent 6DOF / Предметы"
SWEP.Spawnable      = true
SWEP.AdminSpawnable = true
SWEP.Base           = "weapon_base"
SWEP.HoldType       = "normal"

SWEP.Primary.ClipSize      = -1
SWEP.Primary.DefaultClip   = -1
SWEP.Primary.Automatic     = false
SWEP.Primary.Ammo          = "none"

SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

SWEP.DrawAmmo      = false
SWEP.DrawCrosshair = true
SWEP.ViewModel     = "models/weapons/v_hands.mdl"
SWEP.WorldModel    = "models/items/combine_ammo_large.mdl"

-- Кулдаун между спавнами NPC
local SPAWN_CD = 0.5

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
    local function MakeAmmo(pos)
        local e=ents.Create("prop_physics"); if not IsValid(e) then return end
        e:SetModel("models/items/combine_ammo_large.mdl")
        e:SetPos(pos); e:Spawn()
        e:SetColor(Color(0,255,140)); e:SetRenderMode(RENDERMODE_TRANSCOLOR)
        e.IsD6Ammo=true
        local ph=e:GetPhysicsObject()
        if IsValid(ph) then ph:EnableGravity(false); ph:SetDamping(8,8) end
        timer.Simple(60,function() if IsValid(e) then e:Remove() end end)
    end
    function SWEP:DoSpawn(ply, pos) MakeAmmo(pos) end
    function SWEP:DoSecondary(ply)
        if CurTime()<(self.NextAlt or 0) then return end; self.NextAlt=CurTime()+2
        local p=ply:GetPos(); local sa=ply:GetAimVector()
        for i=1,3 do MakeAmmo(p+sa*150+Vector(math.Rand(-60,60),math.Rand(-60,60),0)) end
    end
end
