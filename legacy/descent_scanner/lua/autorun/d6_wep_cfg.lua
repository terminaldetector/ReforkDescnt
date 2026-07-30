-- d6_wep_cfg.lua — DRMD opt-in shim for SCK_Config.
--
--   D6_Wep.Cfg(class, section, key, default)
--     Reads a live weapon config value from SCK_Config if present,
--     returns default otherwise. Weapons call this instead of using
--     hardcoded locals to become live-tunable via the SCK editor.
--     Graceful when SCK is absent — returns default with zero overhead.
--
--   Extension registration (CLIENT):
--     Adds DRMD-specific enum choices to the SCK weapon schema so the
--     editor shows DRMD categories, ammo types, etc.
--
--   SHARED — D6_Wep.Cfg is needed on server (called from FireProjectile).

D6_Wep = D6_Wep or {}

function D6_Wep.Cfg(class, section, key, default)
	if not SCK_Config then return default end
	local cfg = SCK_Config.Get(class)
	if not cfg then return default end
	local sec = cfg[section]
	if not sec then return default end
	local val = sec[key]
	return val ~= nil and val or default
end

-- ── DRMD schema extensions (CLIENT) ──────────────────────────────────────────
if CLIENT then
	local function RegisterExtensions()
		if not SCK_Config then return end

		SCK_Config.RegisterSchemaExtension("weapon", "Category", {
			"Anti-Fighter", "Anti-Capital", "Siege",
		})
		SCK_Config.RegisterSchemaExtension("weapon", "AmmoType", {
			"gravitic", "dark_energy",
		})
	end

	-- Delay until all addons have loaded
	hook.Add("InitPostEntity", "D6_WepCfg_Extensions", function()
		timer.Simple(0.5, RegisterExtensions)
		hook.Remove("InitPostEntity", "D6_WepCfg_Extensions")
	end)
end

print("[D6] d6_wep_cfg.lua loaded")
