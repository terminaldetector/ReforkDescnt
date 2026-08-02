package com.terminaldetector.drmd.weapon.registry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Port of d6_weapon_registry.lua */
public final class WeaponRegistry {
	private static final Map<String, WeaponDef> REGISTRY = new LinkedHashMap<>();

	private WeaponRegistry() {}

	public static void bootstrap() {
		// Items register themselves during ModItems.register()
	}

	public static void register(WeaponDef def) {
		REGISTRY.put(def.id, def);
	}

	public static WeaponDef get(String id) {
		return REGISTRY.get(id);
	}

	public static Collection<WeaponDef> all() {
		return REGISTRY.values();
	}
}
