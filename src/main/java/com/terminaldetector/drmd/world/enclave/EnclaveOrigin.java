package com.terminaldetector.drmd.world.enclave;

/** Caste / origin of a surviving human enclave. */
public enum EnclaveOrigin {
	MILITARY("military", "Military remnant", "солдаты старого приказа"),
	ENGINEERS("engineers", "Engineer guild", "инженеры, что чинят веками"),
	MINERS("miners", "Mine clan", "шахтёры глубины"),
	CULTISTS("cultists", "Machine cult", "те, кто молится автоматам"),
	SCAVENGERS("scavengers", "Scavenger court", "мусорщики кольца");

	public final String id;
	public final String labelEn;
	public final String labelRu;

	EnclaveOrigin(String id, String labelEn, String labelRu) {
		this.id = id;
		this.labelEn = labelEn;
		this.labelRu = labelRu;
	}

	public static EnclaveOrigin fromSalt(long salt) {
		EnclaveOrigin[] v = values();
		return v[(int) Math.floorMod(salt, v.length)];
	}

	public static EnclaveOrigin byId(String id) {
		if (id == null || id.isEmpty()) return SCAVENGERS;
		for (EnclaveOrigin o : values()) {
			if (o.id.equalsIgnoreCase(id) || o.name().equalsIgnoreCase(id)) return o;
		}
		return SCAVENGERS;
	}
}
