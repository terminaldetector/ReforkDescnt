-- ═══════════════════════════════════════════════════════════
-- weapon_d6_bfg.lua — BFG-ИЗЛУЧАТЕЛЬ (анти-рой платформа)
--
--   Cat D: НЕ убийца боссов. АНТИ-РОЙ платформа (адаптация Quake II).
--   Медленная (400 u/s) энергосфера. В полёте каждые 0.25 с излучает
--   до 16 лучей по врагам в 800u с прямой видимостью (30 урона каждый).
--   При ударе: 300 прямого + 150 AoE в 600u.
--   Тяжёлые цели выживают (резист энергии + броня) — это не нюк.
--   Рой выкашивается непрерывным излучением во время полёта.
--
--   Фреймворк: d6_weapon_core (FireProjectile/DirectDamage/SplashDamage),
--   d6_energy (TryConsume), d6_shield (урон energy → energy-резист),
--   d6_weapon_registry (категория Anti-Swarm).
-- ═══════════════════════════════════════════════════════════
AddCSLuaFile()

SWEP.PrintName      = "BFG-ИЗЛУЧАТЕЛЬ"
SWEP.Author         = "Descent 6DOF"
SWEP.Category       = "Descent"
SWEP.Slot           = 3
SWEP.SlotPos        = 0
SWEP.Spawnable      = true
SWEP.AdminSpawnable = true
SWEP.Base           = "weapon_base"
SWEP.HoldType       = "physgun"
SWEP.ViewModel      = "models/weapons/v_physics.mdl"
SWEP.WorldModel     = "models/weapons/w_physics.mdl"
SWEP.UseHands       = false
SWEP.DrawAmmo       = false
SWEP.DrawCrosshair  = false
SWEP.Primary.ClipSize      = -1
SWEP.Primary.DefaultClip   = -1
SWEP.Primary.Automatic     = false
SWEP.Primary.Ammo          = "none"
SWEP.Secondary.ClipSize    = -1
SWEP.Secondary.DefaultClip = -1
SWEP.Secondary.Automatic   = false
SWEP.Secondary.Ammo        = "none"

local MDL_SPHERE = "models/dav0r/hoverball.mdl"
if SERVER and not _D6_BFG_NET then
    util.AddNetworkString("D6_BFGPulse")
    _D6_BFG_NET = true
    util.PrecacheModel(MDL_SPHERE)
    util.PrecacheSound("weapons/physcannon/energy_sing_flyby1.wav")
    util.PrecacheSound("ambient/energy/whiteflash.wav")
end

local ENERGY_COST   = 80
local COOLDOWN      = 10
local PROJ_SPEED    = 400
local PULSE_INT     = 0.25
local PULSE_RANGE   = 800
local PULSE_DMG     = 30
local MAX_BEAMS     = 16
local IMPACT_DMG    = 300
local IMPACT_SPLASH = 150
local IMPACT_RAD    = 600
local LIFE          = 8
local MUZZLE        = { fwd = 38, rgt = 0, up = -6 }

function SWEP:Initialize() self:SetWeaponHoldType(self.HoldType) end
function SWEP:Deploy()  return true end
function SWEP:Holster() return true end

function SWEP:PrimaryAttack()
    if not SERVER then return end
    local owner = self:GetOwner()
    if not IsValid(owner) then return end

    if not D6_Energy.TryConsume(owner, "weapons", ENERGY_COST) then
        owner:EmitSound("buttons/button10.wav", 65, 100); return
    end
    self:SetNextPrimaryFire(CurTime() + COOLDOWN)

    local sa  = D6_Wep.ShootAng(owner)
    local dir = sa:Forward()
    local src = D6_Wep.Muzzle(owner, MUZZLE)
    local exo = D6_DAMAGE.exotic.dmgType

    local sphere = D6_Wep.FireProjectile({
        owner      = owner,
        pos        = src,
        dir        = dir,
        speed      = PROJ_SPEED,
        model      = MDL_SPHERE,
        scale      = 2.2,
        color      = Color(120, 255, 170, 255),
        dmgClass   = "exotic",
        physRadius = 14,
        mass       = 4,
        recoil     = 60,
        life       = LIFE,
        trails     = {
            { col = Color(120, 255, 170), sw = 64, ew = 24, life = 0.6 },
            { col = Color(220, 255, 235), sw = 26, ew = 6,  life = 0.4 },
        },
        onHit = function(b, hitEnt, hitPos, hitNormal)
            timer.Remove("D6_BFGPulse_" .. b:EntIndex())
            if not hitPos then return end
            local pos = hitPos

            if IsValid(hitEnt) and (hitEnt:IsNPC() or hitEnt:IsPlayer()) then
                D6_Wep.DirectDamage(owner, b, hitEnt, IMPACT_DMG, exo, (hitNormal or -dir) * 8000)
            end
            D6_Wep.SplashDamage(owner, b, pos, 0, IMPACT_SPLASH, IMPACT_RAD, exo, 7000, hitEnt)

            local ef = EffectData(); ef:SetOrigin(pos); ef:SetScale(6); ef:SetMagnitude(IMPACT_RAD)
            util.Effect("HelicopterMegaBomb", ef)
            local ef2 = EffectData(); ef2:SetOrigin(pos); ef2:SetScale(4)
            util.Effect("cball_explode", ef2)
            sound.Play("ambient/energy/whiteflash.wav", pos, 125, 70)
        end,
    })
    if not IsValid(sphere) then return end

    -- Radiating beam pulse: clears swarms while in flight.
    local idx = sphere:EntIndex()
    timer.Create("D6_BFGPulse_" .. idx, PULSE_INT, 0, function()
        if not IsValid(sphere) then timer.Remove("D6_BFGPulse_" .. idx); return end
        if not IsValid(owner) then return end
        local spos  = sphere:GetPos()
        local beams = {}
        for _, e in ipairs(ents.FindInSphere(spos, PULSE_RANGE)) do
            if IsValid(e) and e ~= owner and (e:IsNPC() or e:IsPlayer()) then
                local tr = util.TraceLine({
                    start  = spos,
                    endpos = e:WorldSpaceCenter(),
                    filter = { sphere, owner },
                    mask   = MASK_SHOT,
                })
                if (not tr.Hit) or tr.Entity == e then
                    D6_Wep.DirectDamage(owner, sphere, e, PULSE_DMG, exo,
                        (e:GetPos() - spos):GetNormalized() * 1500)
                    beams[#beams + 1] = e:WorldSpaceCenter()
                    if #beams >= MAX_BEAMS then break end
                end
            end
        end
        if #beams > 0 then
            net.Start("D6_BFGPulse")
                net.WriteVector(spos)
                net.WriteUInt(#beams, 5)
                for _, bp in ipairs(beams) do net.WriteVector(bp) end
            net.Broadcast()
        end
    end)

    local ef = EffectData(); ef:SetOrigin(src); ef:SetNormal(dir); ef:SetScale(2)
    util.Effect("MuzzleFlash", ef)
    owner:EmitSound("weapons/physcannon/energy_sing_flyby1.wav", 98, 80)
end

function SWEP:SecondaryAttack()
    if not SERVER then return end
    self:SetNextSecondaryFire(CurTime() + 0.5)
    D6_Wep.DelegateSecondary(self:GetOwner())
end

if CLIENT then
    local BEAM_MAT = Material("cable/xbeam")

    net.Receive("D6_BFGPulse", function()
        local src = net.ReadVector()
        local n   = net.ReadUInt(5)
        local dst = {}
        for i = 1, n do dst[i] = net.ReadVector() end

        local key  = "D6_BFGBeam_" .. CurTime() .. "_" .. math.random(100000)
        local born = CurTime()
        local dur  = 0.22

        hook.Add("PostDrawTranslucentRenderables", key, function(d, s)
            if d or s then return end
            local age = CurTime() - born
            if age >= dur then hook.Remove("PostDrawTranslucentRenderables", key); return end
            local a = (1 - age / dur) * 255
            render.SetMaterial(BEAM_MAT)
            for i = 1, n do
                render.DrawBeam(src, dst[i], 6, 0, 1, Color(120, 255, 170, a))
                render.DrawBeam(src, dst[i], 2, 0, 1, Color(220, 255, 235, a))
            end
        end)
    end)

    function SWEP:DrawHUD()
        D6_Wep.DrawReadyHUD(self:GetOwner(), self, "BFG-ИЗЛУЧАТЕЛЬ", Color(120, 255, 170), COOLDOWN)
    end
end

function SWEP:DrawWorldModel()            end
function SWEP:DrawWorldModelTranslucent() end

print("[D6] weapon_d6_bfg.lua loaded")
