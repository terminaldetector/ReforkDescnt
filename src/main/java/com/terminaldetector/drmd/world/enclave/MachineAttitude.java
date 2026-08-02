package com.terminaldetector.drmd.world.enclave;

/** How an enclave relates to automatic systems (tripods, drones, the Ring). */
public enum MachineAttitude {
	HATE("hate", "hate the machines"),
	USE("use", "use what still runs"),
	WORSHIP("worship", "worship the Archons");

	public final String id;
	public final String gloss;

	MachineAttitude(String id, String gloss) {
		this.id = id;
		this.gloss = gloss;
	}

	public static MachineAttitude fromSalt(long salt, EnclaveOrigin origin) {
		int roll = (int) Math.floorMod(salt >> 3, 100L);
		return switch (origin) {
			case CULTISTS -> roll < 70 ? WORSHIP : USE;
			case MILITARY -> roll < 65 ? HATE : USE;
			case ENGINEERS -> roll < 55 ? USE : (roll < 80 ? HATE : WORSHIP);
			case MINERS -> roll < 40 ? USE : (roll < 75 ? HATE : WORSHIP);
			case SCAVENGERS -> roll < 50 ? USE : (roll < 80 ? HATE : WORSHIP);
		};
	}

	public static MachineAttitude byId(String id) {
		if (id == null || id.isEmpty()) return USE;
		for (MachineAttitude a : values()) {
			if (a.id.equalsIgnoreCase(id) || a.name().equalsIgnoreCase(id)) return a;
		}
		return USE;
	}
}
