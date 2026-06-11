-- ═══════════════════════════════════════════════════════════
-- d6_weapon_core.lua — Unified Weapon Framework (Phase B)
--
-- ЦЕЛЬ: не визуал, а ВЫРАЖЕНИЕ НАВЫКА ПИЛОТА. Оружие связано с
-- движением корабля:
--   • наследование скорости — снаряды несут импульс корабля,
--     поэтому по движущейся цели нужно упреждение (lead);
--   • отдача — каждый выстрел толкает корабль через ply.D6Vel,
--     поэтому залпами надо управлять (drift management);
--   • параллельные стволы — все дула бьют по борсайту (прицелу)
--     на любой дистанции, без схождения в фиксированную точку;
--   • цветовой язык урона — единая читаемость боя по классам.
--
-- Этот файл устраняет дублирование (ShootAng/Muzzle/EnsurePhys/
-- энергобар/делегирование ракет были скопированы в каждом SWEP)
-- и даёт единый спавнер D6_Wep.FireProjectile().
-- ═══════════════════════════════════════════════════════════

D6_Wep    = D6_Wep or {}
D6_DAMAGE = D6_DAMAGE or {}

-- ── Тюнинг связи с движением (реплицируемые конвары) ──────
-- Полная связь по умолчанию (Descent). Меняй вживую без правки кода:
--   d6_wep_inherit 1   — наследование скорости (0..1+)
--   d6_wep_recoil  1   — множитель отдачи (0 = выкл)
local cv_inherit = CreateConVar("d6_wep_inherit", "1.0",
    bit.bor(FCVAR_ARCHIVE, FCVAR_REPLICATED), "Projectile ship-velocity inheritance factor", 0, 2)
local cv_recoil = CreateConVar("d6_wep_recoil", "1.0",
    bit.bor(FCVAR_ARCHIVE, FCVAR_REPLICATED), "Weapon firing recoil factor", 0, 3)

function D6_Wep.Inherit() return cv_inherit:GetFloat() end
function D6_Wep.Recoil()  return cv_recoil:GetFloat()  end

-- ═══════════════════════════════════════════════════════════
-- ТАКСОНОМИЯ УРОНА
-- Каждый класс задаёт: биты DMG_* (→ канал резиста в d6_shield),
-- цвета трассера/свечения (читаемость) и физпрофиль снаряда.
--   resist-канал d6_shield: BLAST→explosive, EBEAM/SHOCK→energy,
--   иначе→kinetic. Поэтому классы дают РАЗНЫЕ каналы.
-- ═══════════════════════════════════════════════════════════
D6_DAMAGE.kinetic = {
    dmgType = DMG_BULLET,                        -- → kinetic
    tracer  = Color(180, 220, 255),
    glow    = Color(140, 200, 255),
    gravity = false,                             -- пули быстрые, без дуги
}
D6_DAMAGE.energy = {
    dmgType = bit.bor(DMG_ENERGYBEAM, DMG_SHOCK),-- → energy
    tracer  = Color(80, 255, 140),
    glow    = Color(160, 255, 190),
    gravity = false,
}
D6_DAMAGE.explosive = {
    dmgType = DMG_BLAST,                          -- → explosive
    tracer  = Color(255, 140, 40),
    glow    = Color(255, 185, 95),
    gravity = false,
}
D6_DAMAGE.exotic = {
    dmgType = bit.bor(DMG_SHOCK, DMG_ENERGYBEAM), -- → energy (плазма-шар)
    tracer  = Color(0, 220, 255),
    glow    = Color(180, 255, 255),
    gravity = false,
}

function D6_Wep.DmgClass(name)
    return D6_DAMAGE[name] or D6_DAMAGE.kinetic
end

-- ═══════════════════════════════════════════════════════════
-- ПРИЦЕЛИВАНИЕ (единый источник — раньше ShootAng был в 7 файлах)
-- ═══════════════════════════════════════════════════════════
-- Направление стрельбы: pitch+yaw без крена (снаряд летит туда,
-- куда смотрит нос, крен на траекторию не влияет).
function D6_Wep.ShootAng(ply)
    local a = ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
    return Angle(a.p, a.y, 0)
end

-- Полный угол с креном — для смещения дул (rgt/up вращаются с креном).
function D6_Wep.AimAng(ply)
    return ply.D6AngSynced or ply.D6Ang or ply:EyeAngles()
end

-- Мировая позиция дула из { fwd, rgt, up }.
-- fwd берётся без крена (ShootAng), rgt/up — с креном (AimAng).
function D6_Wep.Muzzle(ply, off)
    local noRoll = D6_Wep.ShootAng(ply)
    local full   = D6_Wep.AimAng(ply)
    return ply:GetShootPos()
        + noRoll:Forward() * (off.fwd or 0)
        + full:Right()     * (off.rgt or 0)
        + full:Up()        * (off.up  or 0)
end

-- ═══════════════════════════════════════════════════════════
-- СВЯЗЬ С ДВИЖЕНИЕМ
-- ═══════════════════════════════════════════════════════════
-- Текущая скорость корабля. В 6DOF интегратор хранит ply.D6Vel
-- (d6_core.lua); вне 6DOF — обычный GetVelocity().
function D6_Wep.ShipVel(owner)
    if not IsValid(owner) then return Vector(0, 0, 0) end
    return owner.D6Vel or owner:GetVelocity() or Vector(0, 0, 0)
end

-- Отдача: толкает корабль ПРОТИВ направления выстрела.
-- Пишем прямо в ply.D6Vel — следующий тик Move() это интегрирует
-- (так же, как рывок/крюк в d6_core.lua). Сервер авторитетен.
function D6_Wep.ApplyRecoil(owner, dir, mag)
    if not SERVER or not IsValid(owner) then return end
    mag = (mag or 0) * D6_Wep.Recoil()
    if mag <= 0 then return end
    if owner.D6Vel then
        owner.D6Vel = owner.D6Vel - dir * mag
    elseif owner.SetVelocity then
        owner:SetVelocity(owner:GetVelocity() - dir * mag)
    end
end

-- ═══════════════════════════════════════════════════════════
-- ФИЗИКА СНАРЯДА
-- ═══════════════════════════════════════════════════════════
function D6_Wep.EnsurePhys(ent, radius)
    local ph = ent:GetPhysicsObject()
    if IsValid(ph) then return ph end
    ent:PhysicsInitSphere(radius or 4, "metal")
    ent:SetMoveType(MOVETYPE_VPHYSICS)
    return ent:GetPhysicsObject()
end

if SERVER then
    -- ── Единый спавнер снаряда ───────────────────────────
    -- cfg:
    --   owner, pos, dir, speed              (обязательно)
    --   model, scale, color, renderAdd      (визуал)
    --   dmgClass = "kinetic|energy|explosive|exotic"
    --   physRadius, mass, gravity, drag     (физика; gravity по умолчанию из класса)
    --   inherit                             (override фактора наследования)
    --   recoil                              (импульс отдачи на корабль)
    --   trails = { {col,sw,ew,life,mat}, {...} }
    --   life, onHit                         (→ D6_TrackProjectile)
    function D6_Wep.FireProjectile(cfg)
        local owner = cfg.owner
        local dc    = D6_Wep.DmgClass(cfg.dmgClass)

        local p = ents.Create("prop_physics")
        if not IsValid(p) then return end
        p:SetModel(cfg.model or "models/weapons/w_crossbow_bolt.mdl")
        p:SetPos(cfg.pos)
        p:SetAngles(cfg.dir:Angle())
        p:SetOwner(owner)
        p:Spawn()
        p:SetCollisionGroup(COLLISION_GROUP_PROJECTILE)
        if cfg.color then p:SetColor(cfg.color) end
        p:SetRenderMode(cfg.renderAdd ~= false and RENDERMODE_TRANSADD or RENDERMODE_NORMAL)
        if cfg.scale then p:SetModelScale(cfg.scale, 0) end

        -- Трассеры
        if cfg.trails then
            for i, t in ipairs(cfg.trails) do
                local sw = t.sw or 12
                util.SpriteTrail(p, i - 1, t.col or dc.tracer, false,
                    sw, t.ew or 0, t.life or 0.2, 1 / (sw + 1) * 0.5,
                    t.mat or "trails/laser.vmt")
            end
        end

        local ph = D6_Wep.EnsurePhys(p, cfg.physRadius)
        if IsValid(ph) then
            local grav = cfg.gravity
            if grav == nil then grav = dc.gravity end
            ph:EnableGravity(grav and true or false)
            ph:EnableDrag(cfg.drag and true or false)
            ph:SetMass(cfg.mass or 1)
            local inherit = (cfg.inherit or 1.0) * D6_Wep.Inherit()
            ph:SetVelocity(cfg.dir * cfg.speed + D6_Wep.ShipVel(owner) * inherit)
            ph:Wake()
        end

        if cfg.recoil then
            D6_Wep.ApplyRecoil(owner, cfg.dir, cfg.recoil)
        end

        D6_TrackProjectile(p, owner, cfg.onHit, cfg.life or 5)
        return p
    end

    -- ── Хелперы урона для onHit-колбэков ─────────────────
    function D6_Wep.DirectDamage(attacker, inflictor, target, dmg, dmgType, force)
        if not IsValid(target) then return end
        local di = DamageInfo()
        di:SetAttacker(IsValid(attacker) and attacker or game.GetWorld())
        di:SetInflictor(IsValid(inflictor) and inflictor or game.GetWorld())
        di:SetDamage(dmg)
        di:SetDamageType(dmgType)
        di:SetDamageForce(force or Vector(0, 0, 0))
        target:TakeDamageInfo(di)
    end

    -- Радиальный урон: base в эпицентре + splash по спаду к радиусу.
    function D6_Wep.SplashDamage(attacker, inflictor, center, baseDmg, splashDmg, radius, dmgType, forceScale, ignore)
        for _, e in ipairs(ents.FindInSphere(center, radius)) do
            if IsValid(e) and (e:IsNPC() or e:IsPlayer()) and e ~= ignore then
                local dist = e:GetPos():Distance(center)
                local dmg  = baseDmg + splashDmg * math.max(0, 1 - dist / radius)
                local dir  = (e:GetPos() - center):GetNormalized()
                D6_Wep.DirectDamage(attacker, inflictor, e, dmg, dmgType, dir * (forceScale or 4000))
            end
        end
    end
end

-- ═══════════════════════════════════════════════════════════
-- ОБЩИЕ ХЕЛПЕРЫ SWEP (раньше копировались в каждый файл)
-- ═══════════════════════════════════════════════════════════
-- Вторичка всех основных пушек = пуск ракет (делегирование).
-- (Пустые world-model стабы оставлены инлайн в каждом SWEP —
--  их вызов идёт на загрузке файла, до подключения этой либы.)
function D6_Wep.DelegateSecondary(owner)
    if not IsValid(owner) then return end
    local rkt = owner:GetWeapon("weapon_d6_rockets")
    if IsValid(rkt) then rkt:PrimaryAttack() end
end

if CLIENT then
    -- Единый энергобар над прицелом (раньше 5 копий DrawHUD).
    function D6_Wep.DrawEnergyHUD(ply, label, labelCol)
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        local energy = ply:GetNWFloat("D6_WepEnergy", 100)
        local sw, sh = ScrW(), ScrH()
        local bw, bh = 140, 6
        local bx, by = sw / 2 - bw / 2, sh - 72
        surface.SetDrawColor(30, 30, 30, 180); surface.DrawRect(bx - 1, by - 1, bw + 2, bh + 2)
        local col = energy > 30 and Color(0, 180, 255) or Color(255, 60, 60)
        surface.SetDrawColor(col.r, col.g, col.b, 200)
        surface.DrawRect(bx, by, bw * (energy / 100), bh)
        draw.SimpleText(label .. "  ⚡ " .. math.floor(energy), "DermaDefault",
            sw / 2, sh - 90, labelCol or Color(180, 220, 255),
            TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end

    -- Ammo pip bar for Category B weapons (limited-ammo missiles).
    -- Shows individual missile pips so remaining count reads at a glance.
    function D6_Wep.DrawAmmoHUD(ply, label, ammoType, maxAmmo, col)
        if not (IsValid(ply) and ply == LocalPlayer()) then return end
        local count = ply:GetAmmoCount(ammoType)
        local sw, sh = ScrW(), ScrH()
        local bw, bh = 140, 8
        local bx    = sw / 2 - bw / 2
        local by    = sh - 74
        local pip   = math.max(4, math.floor((bw - (maxAmmo - 1) * 3) / maxAmmo))
        for i = 1, maxAmmo do
            local x = bx + (i - 1) * (pip + 3)
            surface.SetDrawColor(30, 30, 30, 180)
            surface.DrawRect(x - 1, by - 1, pip + 2, bh + 2)
            if i <= count then
                surface.SetDrawColor(col.r, col.g, col.b, 220)
            else
                surface.SetDrawColor(50, 50, 50, 120)
            end
            surface.DrawRect(x, by, pip, bh)
        end
        local labelCol = count > 0 and col or Color(255, 60, 60)
        draw.SimpleText(label .. "   " .. count .. " / " .. maxAmmo, "DermaDefault",
            sw / 2, sh - 90, labelCol, TEXT_ALIGN_CENTER, TEXT_ALIGN_CENTER)
    end
end

print("[D6] d6_weapon_core.lua OK")
