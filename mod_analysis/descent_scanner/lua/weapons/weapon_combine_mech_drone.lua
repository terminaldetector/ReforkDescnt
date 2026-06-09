AddCSLuaFile()

-- ═══════════════════════════════════════════════════════════
-- «Центурион» — cockpit-view дрон для Descent 6DOF.
-- (Перевод с mech-сборки на 1-е лицо по концепту Image 1)
--
--   • Миниgun'ы по краям экрана — рендерятся в камера-space
--   • Грав-пушка снизу-по-центру — только при активном захвате
--   • HUD с угловыми скобками (GRAV_UNIT / LOAD_MASS / AMMO /
--     UNIT_INTEGRITY / TARGET / STATUS)
--   • Кокпит видит только локальный игрок; другим — модель
--     сканера (D6_Activate уже её ставит)
-- ═══════════════════════════════════════════════════════════

SWEP.PrintName      = "Тяжёлый дрон «Центурион»"
SWEP.Author         = "Descent 6DOF"
SWEP.Instructions   = "ЛКМ: парная очередь   ПКМ: ракета   R: грав-захват"
SWEP.Category       = "Descent: ВПК Альянса"
SWEP.Slot           = 4
SWEP.SlotPos        = 2
SWEP.Spawnable      = true
SWEP.AdminSpawnable = true

SWEP.Base           = "weapon_base"
SWEP.HoldType       = "rpg"
SWEP.UseHands       = false
SWEP.DrawCrosshair  = true
SWEP.DrawAmmo       = false  -- свой HUD

SWEP.ViewModel      = "models/weapons/v_rpg.mdl"  -- v_, не c_
SWEP.WorldModel     = "models/weapons/w_rpg.mdl"  -- валидная модель (отрисовку гасим в DrawWorldModel)

SWEP.Primary.ClipSize    = 100
SWEP.Primary.DefaultClip = 400
SWEP.Primary.Automatic   = true
SWEP.Primary.Ammo        = "AR2"

SWEP.Secondary.ClipSize    = 4
SWEP.Secondary.DefaultClip = 12
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "RPG_Round"

-- ─── Сетевая строка ──────────────────────────────────────
if SERVER and not _D6_MECH_NET then
    util.AddNetworkString("D6_MechFire")
    _D6_MECH_NET = true
end

-- =========================================================
-- КАМЕРА-SPACE СМЕЩЕНИЯ КОМПОНЕНТОВ (от EyePos игрока)
-- =========================================================
local MUZZLE_L   = { fwd = 35, rgt = -25, up = -10 }
local MUZZLE_R   = { fwd = 35, rgt =  25, up = -10 }
local GRAV_POS   = { fwd = 26, rgt =   0, up = -18 }
local ROCKET_POS = { fwd = 15, rgt =   0, up =  20 }

-- ─── Параметры боя ──────────────────────────────────────
local FIRE_CD       = 0.07
local FIRE_DAMAGE   = 22
local FIRE_RECOIL   = 30
local RPG_CD        = 1.8
local RPG_RECOIL    = 300
local GRAV_RANGE    = 1500
local GRAV_HOLD_AT  = 110
local GRAV_MAX_MASS = 300

-- =========================================================
-- ХЕЛПЕРЫ
-- =========================================================
local function ShootAng(ply)
    return ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
end

-- Превращает камера-space смещение { fwd, rgt, up } в мировую позицию
local function CamSpacePos(eyePos, ang, off)
    return eyePos
         + ang:Forward() * off.fwd
         + ang:Right()   * off.rgt
         + ang:Up()      * off.up
end

local function MuzzleWorld(ply, off)
    return CamSpacePos(ply:EyePos(), ShootAng(ply), off), ShootAng(ply)
end

-- =========================================================
-- ИНИЦИАЛИЗАЦИЯ
-- =========================================================
function SWEP:Initialize()
    self:SetHoldType("rpg")
    if CLIENT then self.ClientModels = {} end
    self.NextRightGun = false
    self.GravHeld     = NULL
    self:SetNWEntity("D6_MechHeld", NULL)
end

function SWEP:ClearClientModels()
    if not CLIENT then return end
    if self.ClientModels then
        for k, m in pairs(self.ClientModels) do
            if IsValid(m) then m:Remove() end
            self.ClientModels[k] = nil
        end
    end
end

function SWEP:ReleaseGrav()
    if not SERVER then return end
    if IsValid(self.GravHeld) then
        local ph = self.GravHeld:GetPhysicsObject()
        if IsValid(ph) then ph:EnableGravity(true); ph:EnableMotion(true); ph:Wake() end
    end
    self.GravHeld = NULL
    self:SetNWEntity("D6_MechHeld", NULL)
end

function SWEP:OnRemove() self:ClearClientModels(); if SERVER then self:ReleaseGrav() end end
function SWEP:Holster()  self:ClearClientModels(); if SERVER then self:ReleaseGrav() end; return true end
function SWEP:OnDrop()   if SERVER then self:ReleaseGrav() end end

-- =========================================================
-- ЛКМ — парный залп миниgun
-- =========================================================
function SWEP:PrimaryAttack()
    if not self:CanPrimaryAttack() then return end
    self:SetNextPrimaryFire(CurTime() + FIRE_CD)

    local ply = self:GetOwner()
    if not IsValid(ply) then return end

    local off = self.NextRightGun and MUZZLE_R or MUZZLE_L
    self.NextRightGun = not self.NextRightGun

    local src, ang = MuzzleWorld(ply, off)
    local dir = ang:Forward()

    self:EmitSound("NPC_LightCombatDrone.FireFlash")

    ply:FireBullets({
        Num=1, Src=src, Dir=dir,
        Spread=Vector(0.025,0.025,0),
        Tracer=1, TracerName="AirboatGunTracer",
        Force=10, Damage=FIRE_DAMAGE, Attacker=ply,
    })
    self:TakePrimaryAmmo(1)

    if SERVER then
        util.ScreenShake(ply:GetPos(), 2, 5, 0.1, 200)
        ply.D6Vel = (ply.D6Vel or Vector()) - dir * FIRE_RECOIL
        net.Start("D6_MechFire")
            net.WriteEntity(ply); net.WriteUInt(0, 2)
            net.WriteVector(src); net.WriteVector(dir)
        net.Broadcast()
    end
end

-- =========================================================
-- ПКМ — ракета
-- =========================================================
function SWEP:SecondaryAttack()
    if not self:CanSecondaryAttack() then return end
    self:SetNextSecondaryFire(CurTime() + RPG_CD)

    local ply = self:GetOwner()
    if not IsValid(ply) then return end

    local src, ang = MuzzleWorld(ply, ROCKET_POS)
    local dir = ang:Forward()

    if SERVER then
        local r = ents.Create("rpg_missile")
        if IsValid(r) then
            r:SetPos(src); r:SetAngles(dir:Angle()); r:SetOwner(ply)
            r:Spawn(); r:Activate()
            local ph = r:GetPhysicsObject()
            if IsValid(ph) then ph:SetVelocity(dir * 1800) end
        end
        self:EmitSound("Weapon_RPG.NPC_Single")
        ply.D6Vel = (ply.D6Vel or Vector()) - dir * RPG_RECOIL
        net.Start("D6_MechFire")
            net.WriteEntity(ply); net.WriteUInt(1, 2)
            net.WriteVector(src); net.WriteVector(dir)
        net.Broadcast()
    end
    self:TakeSecondaryAmmo(1)
end

-- =========================================================
-- THINK — грав-захват на удержание R
-- =========================================================
function SWEP:Reload() end

function SWEP:Think()
    if not SERVER then return end
    local ply = self:GetOwner()
    if not IsValid(ply) then return end

    local want = ply:KeyDown(IN_RELOAD)

    if want then
        if not IsValid(self.GravHeld) then
            if (self.NextGravTry or 0) < CurTime() then
                self.NextGravTry = CurTime() + 0.2
                local src, ang = MuzzleWorld(ply, GRAV_POS)
                local tr = util.TraceLine({
                    start  = src,
                    endpos = src + ang:Forward() * GRAV_RANGE,
                    filter = { ply, self },
                    mask   = MASK_SHOT,
                })
                local ent = tr.Entity
                if IsValid(ent) and ent:GetClass() == "prop_physics" then
                    local ph = ent:GetPhysicsObject()
                    if IsValid(ph) and ph:GetMass() <= GRAV_MAX_MASS then
                        self.GravHeld = ent
                        self:SetNWEntity("D6_MechHeld", ent)
                        ply:EmitSound("weapons/physcannon/physcannon_pickup.wav", 70, 100)
                    end
                end
            end
        end
        if IsValid(self.GravHeld) then
            local hold = CamSpacePos(ply:EyePos(), ShootAng(ply),
                                     { fwd = GRAV_HOLD_AT, rgt = 0, up = 0 })
            local ph = self.GravHeld:GetPhysicsObject()
            if IsValid(ph) then
                ph:Wake(); ph:EnableGravity(false)
                ph:SetVelocity((hold - self.GravHeld:GetPos()) * 12)
            else
                self:ReleaseGrav()
            end
        end
    else
        if IsValid(self.GravHeld) then
            local dir = ShootAng(ply):Forward()
            local ph  = self.GravHeld:GetPhysicsObject()
            if IsValid(ph) then ph:SetVelocity(dir * 800) end
            self:ReleaseGrav()
            ply:EmitSound("weapons/physcannon/superphys_launch1.wav", 70, 110)
        end
    end
end

-- =========================================================
-- КЛИЕНТ: COCKPIT RENDER + HUD
-- =========================================================
if CLIENT then

function SWEP:PreDrawViewModel() return true end       -- скрываем стандартную VM
function SWEP:DrawWorldModel() end                      -- ничего не рисуем для других — у них и так модель сканера

-- Получает или создаёт ClientsideModel для слота
local function GetCSM(self, slot, mdl)
    self.ClientModels = self.ClientModels or {}
    local m = self.ClientModels[slot]
    if IsValid(m) then return m end
    m = ClientsideModel(mdl, RENDERGROUP_OPAQUE)  -- world pass, не viewmodel
    if not IsValid(m) then return nil end
    m:SetNoDraw(true)
    self.ClientModels[slot] = m
    return m
end

-- Главный хук cockpit-рендера. Активен только для LocalPlayer
-- с этим оружием. Срабатывает в opaque-проходе (после мировых
-- стен, до полупрозрачных эффектов).
local _D6_MECH_FIRSTDRAW = false
hook.Add("PostDrawOpaqueRenderables", "D6_Mech_Cockpit", function(depth, sky)
    if depth or sky then return end

    local ply = LocalPlayer()
    if not IsValid(ply) then return end
    local wep = ply:GetActiveWeapon()
    if not IsValid(wep) or wep:GetClass() ~= "weapon_combine_mech_drone" then return end

    local eye = ply:GetShootPos()  -- надёжнее EyePos для модели сканера
    local ang = ShootAng(ply)

    -- ─── Левый миниgun ───
    local lm = GetCSM(wep, "minigun_l", "models/combine_helicopter/helicopter_gun01.mdl")
    if IsValid(lm) then
        lm:SetPos(CamSpacePos(eye, ang, MUZZLE_L))
        lm:SetAngles(ang)
        local mat = Matrix(); mat:Scale(Vector(1.2,1.2,1.2))
        lm:EnableMatrix("RenderMultiply", mat)
        lm:DrawModel()
        if not _D6_MECH_FIRSTDRAW then
            _D6_MECH_FIRSTDRAW = true
            MsgC(Color(100,220,255), "[D6 Mech] Кокпит активен (первый рендер).\n")
        end
    end

    -- ─── Правый миниgun ───
    local rm = GetCSM(wep, "minigun_r", "models/combine_helicopter/helicopter_gun01.mdl")
    if IsValid(rm) then
        rm:SetPos(CamSpacePos(eye, ang, MUZZLE_R))
        local mirror = Angle(ang.p, ang.y, ang.r)
        mirror:RotateAroundAxis(mirror:Forward(), 180)  -- зеркало
        rm:SetAngles(mirror)
        local mat = Matrix(); mat:Scale(Vector(1.2,1.2,1.2))
        rm:EnableMatrix("RenderMultiply", mat)
        rm:DrawModel()
    end

    -- ─── Грав-пушка — только когда удерживается проп или зажата R ───
    local held    = wep:GetNWEntity("D6_MechHeld", NULL)
    local pulling = ply:KeyDown(IN_RELOAD)
    if IsValid(held) or pulling then
        local gc = GetCSM(wep, "gravcannon", "models/weapons/w_physics.mdl")
        if IsValid(gc) then
            gc:SetPos(CamSpacePos(eye, ang, GRAV_POS))
            gc:SetAngles(ang)
            local mat = Matrix(); mat:Scale(Vector(1.5,1.5,1.5))
            gc:EnableMatrix("RenderMultiply", mat)
            gc:DrawModel()
        end
    end
end)

-- ─── Полупрозрачный слой: луч + дуги вокруг пропа ────────
local BEAM_MAT = Material("cable/redlaser")
hook.Add("PostDrawTranslucentRenderables", "D6_Mech_Beam", function(depth, sky)
    if depth or sky then return end
    local ply = LocalPlayer()
    if not IsValid(ply) then return end
    local wep = ply:GetActiveWeapon()
    if not IsValid(wep) or wep:GetClass() ~= "weapon_combine_mech_drone" then return end

    local held = wep:GetNWEntity("D6_MechHeld", NULL)
    if not IsValid(held) then return end

    local eye = ply:GetShootPos()  -- надёжнее EyePos для модели сканера
    local ang = ShootAng(ply)
    local src = CamSpacePos(eye, ang, GRAV_POS) + ang:Forward() * 14
    local dst = held:GetPos() + held:OBBCenter()

    render.SetMaterial(BEAM_MAT)
    render.DrawBeam(src, dst, 6, 0, 1, Color(255, 140, 30))
    render.DrawBeam(src, dst, 2, 0, 1, Color(255, 230, 180))

    -- Электрические искры на пропе (троттлинг 0.05с)
    wep._NextSpark = wep._NextSpark or 0
    if CurTime() > wep._NextSpark then
        wep._NextSpark = CurTime() + 0.05
        local ef = EffectData()
        ef:SetOrigin(dst)
        ef:SetMagnitude(2); ef:SetScale(1)
        util.Effect("ManhackSparks", ef)
    end
end)

-- ─── Muzzle flash от пушек/ракеты ────────────────────────
net.Receive("D6_MechFire", function()
    local ply  = net.ReadEntity()
    local mode = net.ReadUInt(2)
    local src  = net.ReadVector()
    local dir  = net.ReadVector()
    if not IsValid(ply) then return end
    local ef = EffectData()
    ef:SetOrigin(src); ef:SetNormal(dir)
    ef:SetScale(mode == 1 and 1.5 or 0.5)
    util.Effect("MuzzleFlash", ef)
    if mode == 1 then
        local s = EffectData(); s:SetOrigin(src); s:SetScale(1)
        util.Effect("MuzzleEffect", s)
    end
end)

-- =========================================================
-- HUD — угловые скобки + статусные строки
-- =========================================================
surface.CreateFont("D6MechHUD", {
    font = "Roboto", size = 18, weight = 600, antialias = true,
})
surface.CreateFont("D6MechHUDSmall", {
    font = "Roboto", size = 14, weight = 500, antialias = true,
})

local function DrawCornerBracket(x, y, w, h, dir, col)
    surface.SetDrawColor(col)
    if dir == "tl" then
        surface.DrawLine(x,   y,   x+w, y)
        surface.DrawLine(x,   y,   x,   y+h)
    elseif dir == "tr" then
        surface.DrawLine(x-w, y,   x,   y)
        surface.DrawLine(x,   y,   x,   y+h)
    elseif dir == "bl" then
        surface.DrawLine(x,   y,   x+w, y)
        surface.DrawLine(x,   y-h, x,   y)
    elseif dir == "br" then
        surface.DrawLine(x-w, y,   x,   y)
        surface.DrawLine(x,   y-h, x,   y)
    end
end

hook.Add("HUDPaint", "D6_Mech_HUD", function()
    local ply = LocalPlayer()
    if not IsValid(ply) then return end
    local wep = ply:GetActiveWeapon()
    if not IsValid(wep) or wep:GetClass() ~= "weapon_combine_mech_drone" then return end

    local sw, sh = ScrW(), ScrH()
    local CYAN   = Color(120, 220, 255, 220)
    local ORANGE = Color(255, 160,  40, 230)
    local DIM    = Color(180, 220, 255, 180)
    local margin = 22
    local brW, brH = 80, 80

    -- Скобки в 4 углах
    DrawCornerBracket(margin,        margin,       brW, brH, "tl", CYAN)
    DrawCornerBracket(sw - margin,   margin,       brW, brH, "tr", CYAN)
    DrawCornerBracket(margin,        sh - margin,  brW, brH, "bl", CYAN)
    DrawCornerBracket(sw - margin,   sh - margin,  brW, brH, "br", CYAN)

    -- ─── Верх-лево: GRAV_UNIT + LOAD_MASS ───
    local held = wep:GetNWEntity("D6_MechHeld", NULL)
    local gravText, gravCol
    if IsValid(held) then
        gravText, gravCol = "[GRAV_UNIT: ACTIVE]", CYAN
    elseif ply:KeyDown(IN_RELOAD) then
        gravText, gravCol = "[GRAV_UNIT: SEEKING]", ORANGE
    else
        gravText, gravCol = "[GRAV_UNIT: IDLE]", DIM
    end
    draw.SimpleText(gravText, "D6MechHUD",
        margin + 10, margin + 4, gravCol, TEXT_ALIGN_LEFT, TEXT_ALIGN_TOP)

    if IsValid(held) then
        local ph   = held:GetPhysicsObject()
        local mass = IsValid(ph) and math.floor(ph:GetMass()) or 0
        draw.SimpleText(string.format("[LOAD_MASS: %dkg]", mass), "D6MechHUDSmall",
            margin + 10, margin + 24, CYAN, TEXT_ALIGN_LEFT, TEXT_ALIGN_TOP)
    end

    -- ─── Верх-право: AMMO ───
    local rpgClip = wep:Clip2()
    local ar2Ammo = ply:GetAmmoCount("AR2")
    draw.SimpleText(string.format("[AMMO: %d/%d RPG | %d AR2]",
            rpgClip, wep:GetMaxClip2(), ar2Ammo),
        "D6MechHUD", sw - margin - 10, margin + 4,
        CYAN, TEXT_ALIGN_RIGHT, TEXT_ALIGN_TOP)

    -- ─── Низ-лево: UNIT_INTEGRITY ───
    local hp     = math.max(0, ply:Health())
    local maxhp  = math.max(1, ply:GetMaxHealth())
    local pct    = math.floor(hp / maxhp * 100)
    local hpCol  = pct > 50 and CYAN or (pct > 25 and ORANGE or Color(255,80,80,230))
    draw.SimpleText(string.format("[UNIT_INTEGRITY: %d%%]", pct),
        "D6MechHUD", margin + 10, sh - margin - 22,
        hpCol, TEXT_ALIGN_LEFT, TEXT_ALIGN_TOP)

    -- ─── Низ-право: TARGET + STATUS ───
    local ang = ShootAng(ply)
    local tr  = util.TraceLine({
        start  = ply:EyePos(),
        endpos = ply:EyePos() + ang:Forward() * 4000,
        filter = ply,
        mask   = MASK_SHOT,
    })

    local tName  = "—"
    if IsValid(tr.Entity) then
        local e = tr.Entity
        if e:IsPlayer() then
            tName = e:Nick()
        elseif e:IsNPC() then
            tName = string.upper(e:GetClass():gsub("npc_",""))
        elseif e:GetClass() == "prop_physics" then
            tName = "PROP_PHYSICS"
        else
            tName = string.upper(e:GetClass())
        end
    elseif tr.HitWorld then
        tName = "TERRAIN"
    end

    local lockReady = CurTime() >= (wep:GetNextSecondaryFire() or 0)
    local statusText = lockReady and "[STATUS: LOCKED]" or "[STATUS: RELOAD]"
    local statusCol  = lockReady and ORANGE or DIM

    draw.SimpleText(string.format("[TARGET: %s]", tName),
        "D6MechHUDSmall", sw - margin - 10, sh - margin - 42,
        DIM, TEXT_ALIGN_RIGHT, TEXT_ALIGN_TOP)
    draw.SimpleText(statusText,
        "D6MechHUD", sw - margin - 10, sh - margin - 22,
        statusCol, TEXT_ALIGN_RIGHT, TEXT_ALIGN_TOP)

    -- ─── Прицел: оранжевый ромб как на арте ───
    local cx, cy = sw/2, sh/2
    surface.SetDrawColor(ORANGE)
    local s = 6
    surface.DrawLine(cx-s, cy,   cx,   cy-s)
    surface.DrawLine(cx,   cy-s, cx+s, cy)
    surface.DrawLine(cx+s, cy,   cx,   cy+s)
    surface.DrawLine(cx,   cy+s, cx-s, cy)
end)

end -- if CLIENT

-- =========================================================
-- РЕГИСТРАЦИЯ
-- =========================================================
if D6_RegisterWeapon then
    D6_RegisterWeapon("weapon_combine_mech_drone", {
        category = "Descent: ВПК",
        autoGive = false,
        patchAim = true,
    })
end

-- =========================================================
-- ГЛОБАЛЬНАЯ ЧИСТКА (один раз)
-- =========================================================
if CLIENT and not _D6_MECH_CLEANUP_HOOKED then
    _D6_MECH_CLEANUP_HOOKED = true
    hook.Add("EntityRemoved", "D6_Mech_CleanModels", function(ent)
        if not ent:IsPlayer() then return end
        for _, w in ipairs(ents.FindByClass("weapon_combine_mech_drone")) do
            if IsValid(w) and w:GetOwner() == ent and w.ClearClientModels then
                w:ClearClientModels()
            end
        end
    end)
end
