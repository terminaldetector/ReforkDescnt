-- SCK_Config: runtime weapon config registry.
-- Register(class, cfg) / Get(class) / Set(class, section, key, value)
-- Persists to DATA/drmd_weapons/<class>.txt via glon (client-side).
-- Net-sync: client Set → server store → SCK_ConfigBroadcast → all clients apply.

SCK_Config = SCK_Config or {}
SCK_Config._registry   = SCK_Config._registry   or {}
SCK_Config._schemaExtensions = SCK_Config._schemaExtensions or {}

function SCK_Config.Register(class, cfg)
	SCK_Config._registry[class] = cfg
end

function SCK_Config.Get(class)
	return SCK_Config._registry[class]
end

-- Optional: DRMD or other addons register extra enum choices per schema field.
-- SCK_Config.RegisterSchemaExtension("weapon", "Category", {"Special"})
function SCK_Config.RegisterSchemaExtension(schemaName, fieldKey, extraChoices)
	local k = schemaName .. "." .. fieldKey
	SCK_Config._schemaExtensions[k] = SCK_Config._schemaExtensions[k] or {}
	for _, c in ipairs(extraChoices) do
		table.insert(SCK_Config._schemaExtensions[k], c)
	end
	-- Patch live schema if already loaded (client-side reload-safe)
	if SCK_SCHEMA and SCK_SCHEMA[schemaName] then
		for _, field in ipairs(SCK_SCHEMA[schemaName]) do
			if field.key == fieldKey and field.choices then
				for _, c in ipairs(extraChoices) do
					table.insert(field.choices, c)
				end
				break
			end
		end
	end
end

-- ── Net strings ──────────────────────────────────────────────────────────────
if SERVER then
	util.AddNetworkString("SCK_ConfigSet")
	util.AddNetworkString("SCK_ConfigBroadcast")

	net.Receive("SCK_ConfigSet", function(len, ply)
		local class   = net.ReadString()
		local section = net.ReadString()
		local key     = net.ReadString()
		local valType = net.ReadString()
		local value
		if     valType == "number" then value = net.ReadFloat()
		elseif valType == "bool"   then value = net.ReadBool()
		else                            value = net.ReadString() end

		SCK_Config._registry[class]              = SCK_Config._registry[class]              or {}
		SCK_Config._registry[class][section]     = SCK_Config._registry[class][section]     or {}
		SCK_Config._registry[class][section][key] = value

		net.Start("SCK_ConfigBroadcast")
			net.WriteString(class)
			net.WriteString(section)
			net.WriteString(key)
			net.WriteString(valType)
			if     valType == "number" then net.WriteFloat(value)
			elseif valType == "bool"   then net.WriteBool(value)
			else                            net.WriteString(tostring(value)) end
		net.Broadcast()
	end)
end

-- ── Client side ──────────────────────────────────────────────────────────────
if CLIENT then

	local DATA_DIR = "drmd_weapons"

	local function EnsureDir()
		if not file.IsDir(DATA_DIR, "DATA") then file.CreateDir(DATA_DIR) end
	end

	local function SaveToDisk(class)
		local cfg = SCK_Config._registry[class]
		if not cfg then return end
		EnsureDir()
		local ok, encoded = pcall(glon.encode, cfg)
		if ok and encoded then
			file.Write(DATA_DIR .. "/" .. class .. ".txt", encoded)
		end
	end

	function SCK_Config.LoadForClass(class)
		local path = DATA_DIR .. "/" .. class .. ".txt"
		if not file.Exists(path, "DATA") then return nil end
		local raw = file.Read(path, "DATA")
		if not raw then return nil end
		local ok, tbl = pcall(glon.decode, raw)
		if ok and tbl then
			SCK_Config._registry[class] = tbl
			return tbl
		end
		return nil
	end

	-- Send one value change to server and persist locally.
	function SCK_Config.Set(class, section, key, value)
		SCK_Config._registry[class]              = SCK_Config._registry[class]              or {}
		SCK_Config._registry[class][section]     = SCK_Config._registry[class][section]     or {}
		SCK_Config._registry[class][section][key] = value
		SaveToDisk(class)

		local valType = type(value)
		if     valType == "number"  then valType = "number"
		elseif valType == "boolean" then valType = "bool"
		else                             valType = "string" end

		net.Start("SCK_ConfigSet")
			net.WriteString(class)
			net.WriteString(section)
			net.WriteString(key)
			net.WriteString(valType)
			if     valType == "number" then net.WriteFloat(value)
			elseif valType == "bool"   then net.WriteBool(value)
			else                            net.WriteString(tostring(value)) end
		net.SendToServer()
	end

	net.Receive("SCK_ConfigBroadcast", function()
		local class   = net.ReadString()
		local section = net.ReadString()
		local key     = net.ReadString()
		local valType = net.ReadString()
		local value
		if     valType == "number" then value = net.ReadFloat()
		elseif valType == "bool"   then value = net.ReadBool()
		else                            value = net.ReadString() end

		SCK_Config._registry[class]              = SCK_Config._registry[class]              or {}
		SCK_Config._registry[class][section]     = SCK_Config._registry[class][section]     or {}
		SCK_Config._registry[class][section][key] = value
	end)

end
