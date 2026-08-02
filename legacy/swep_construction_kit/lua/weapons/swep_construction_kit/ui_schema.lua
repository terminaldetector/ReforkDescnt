-- SCK_BuildSchemaPanel(parent, schema, data, onChange, schemaName)
-- Builds a scrollable editor panel from a schema array.
--   parent     : DPanel that will contain the scroll panel (Dock(FILL))
--   schema     : SCK_SCHEMA.weapon / projectile / flak / guidance
--   data       : live table that receives every edit (e.g. wep.weaponconfig.weapon)
--   onChange   : function(key, value) called after each write; may be nil
--   schemaName : string used for SCK_Config.RegisterSchemaExtension lookups

function SCK_BuildSchemaPanel(parent, schema, data, onChange, schemaName)

	local scroll = vgui.Create("DScrollPanel", parent)
	scroll:Dock(FILL)

	local lastSection = nil

	for _, field in ipairs(schema) do

		-- Section header row
		if field.section ~= lastSection then
			lastSection = field.section
			local hdr = vgui.Create("DLabel", scroll)
			hdr:SetText("── " .. field.section .. " ──")
			hdr:SetFont("DermaDefaultBold")
			hdr:SetColor(Color(180, 200, 240, 255))
			hdr:SetTall(22)
			hdr:SizeToContentsX()
			hdr:DockMargin(4, 10, 4, 2)
			hdr:Dock(TOP)
		end

		local t   = field.type
		local cur = data[field.key]
		if cur == nil then cur = field.default end

		-- ── int / float ─────────────────────────────────────────────────────
		if t == "int" or t == "float" then

			local row = vgui.Create("DNumSlider", scroll)
			row:SetText(field.label)
			row:SetMin(field.min or 0)
			row:SetMax(field.max or 100)
			row:SetDecimals(t == "int" and 0 or (field.decimals or 2))
			row:SetValue(cur or 0)
			row:SetTall(24)
			row:DockMargin(4, 1, 4, 0)
			row:Dock(TOP)
			row.Wang.ConVarChanged = function(_, val)
				local v = tonumber(val) or 0
				if t == "int" then v = math.floor(v + 0.5) end
				data[field.key] = v
				if onChange then onChange(field.key, v) end
			end

		-- ── string (single line) ─────────────────────────────────────────────
		elseif t == "string" then

			local row = vgui.Create("DPanel", scroll)
			row:SetTall(24)
			row:SetDrawBackground(false)
			row:DockMargin(4, 2, 4, 0)
			row:Dock(TOP)

			local lbl = vgui.Create("DLabel", row)
			lbl:SetText(field.label .. ":")
			lbl:SetWide(130)
			lbl:SetContentAlignment(6) -- right
			lbl:Dock(LEFT)

			local entry = vgui.Create("DTextEntry", row)
			entry:SetValue(tostring(cur or ""))
			entry:DockMargin(4, 0, 0, 0)
			entry:Dock(FILL)
			entry.OnTextChanged = function()
				local v = entry:GetValue()
				data[field.key] = v
				if onChange then onChange(field.key, v) end
			end

		-- ── text (multi-line) ────────────────────────────────────────────────
		elseif t == "text" then

			local wrap = vgui.Create("DPanel", scroll)
			wrap:SetTall(64)
			wrap:SetDrawBackground(false)
			wrap:DockMargin(4, 2, 4, 0)
			wrap:Dock(TOP)

			local lbl = vgui.Create("DLabel", wrap)
			lbl:SetText(field.label .. ":")
			lbl:SetTall(18)
			lbl:Dock(TOP)

			local entry = vgui.Create("DTextEntry", wrap)
			entry:SetMultiline(true)
			entry:SetValue(tostring(cur or ""))
			entry:Dock(FILL)
			entry.OnTextChanged = function()
				local v = entry:GetValue()
				data[field.key] = v
				if onChange then onChange(field.key, v) end
			end

		-- ── bool ─────────────────────────────────────────────────────────────
		elseif t == "bool" then

			local row = vgui.Create("DCheckBoxLabel", scroll)
			row:SetText(field.label)
			row:SetValue(cur or false)
			row:SetTall(22)
			row:DockMargin(4, 2, 4, 0)
			row:Dock(TOP)
			row.OnChange = function(_, val)
				data[field.key] = val
				if onChange then onChange(field.key, val) end
			end

		-- ── enum (dropdown) ──────────────────────────────────────────────────
		elseif t == "enum" then

			local row = vgui.Create("DPanel", scroll)
			row:SetTall(24)
			row:SetDrawBackground(false)
			row:DockMargin(4, 2, 4, 0)
			row:Dock(TOP)

			local lbl = vgui.Create("DLabel", row)
			lbl:SetText(field.label .. ":")
			lbl:SetWide(130)
			lbl:SetContentAlignment(6)
			lbl:Dock(LEFT)

			local combo = vgui.Create("DComboBox", row)
			combo:DockMargin(4, 0, 0, 0)
			combo:Dock(FILL)

			local choices = field.choices or {}
			-- Merge any registered extensions for this field
			if schemaName and SCK_Config then
				local ext = SCK_Config._schemaExtensions[schemaName .. "." .. field.key]
				if ext then
					choices = table.Copy(choices)
					for _, c in ipairs(ext) do table.insert(choices, c) end
				end
			end
			for _, c in ipairs(choices) do combo:AddChoice(c) end
			combo:SetValue(tostring(cur or field.default or ""))
			combo.OnSelect = function(_, _, val)
				data[field.key] = val
				if onChange then onChange(field.key, val) end
			end

		end
	end

	return scroll
end
