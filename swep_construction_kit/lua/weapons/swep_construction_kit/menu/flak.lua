-- Flak / Cassette Systems Editor tab.
-- Parent panel: wep.pflak (created in client.lua CreateMenu)

local wep   = GetSCKSWEP(LocalPlayer())
local panel = wep.pflak

local function GetClass()
	return (wep.weaponconfig.weapon and wep.weaponconfig.weapon.ClassName) or "weapon_d6_custom"
end

local function onChange(key, value)
	if SCK_Config then
		SCK_Config.Set(GetClass(), "flak", key, value)
	end
end

SCK_BuildSchemaPanel(panel, SCK_SCHEMA.flak, wep.weaponconfig.flak, onChange, "flak")
