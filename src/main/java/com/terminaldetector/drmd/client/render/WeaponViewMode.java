package com.terminaldetector.drmd.client.render;

/**
 * First-person weapon display mode for existing DRMD guns.
 *
 * <ul>
 *   <li>{@link #COCKPIT_3D} — construction clusters as multi-part 3D meshes (default in 6DoF)</li>
 *   <li>{@link #ITEM_3D} — held item's Blockbench / elements model in cockpit space</li>
 *   <li>{@link #VANILLA} — Minecraft hand + flat/handheld item (no custom FP overlay)</li>
 * </ul>
 */
public enum WeaponViewMode {
	COCKPIT_3D,
	ITEM_3D,
	VANILLA;

	public WeaponViewMode next() {
		WeaponViewMode[] v = values();
		return v[(ordinal() + 1) % v.length];
	}

	public String label() {
		return switch (this) {
			case COCKPIT_3D -> "COCKPIT 3D";
			case ITEM_3D -> "ITEM 3D";
			case VANILLA -> "VANILLA";
		};
	}

	/** Custom FP overlay replaces the vanilla held item. */
	public boolean hidesVanillaHandItem() {
		return this == COCKPIT_3D || this == ITEM_3D;
	}
}
