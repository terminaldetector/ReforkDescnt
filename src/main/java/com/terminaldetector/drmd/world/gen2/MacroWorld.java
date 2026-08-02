package com.terminaldetector.drmd.world.gen2;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of macro-world entries — what the world has built and where, for radar, HUD contacts
 * and generator bookkeeping.
 */
public final class MacroWorld {
	private static final Map<UUID, MacroEntry> ENTRIES = new ConcurrentHashMap<>();

	private MacroWorld() {}

	public static void clear() {
		ENTRIES.clear();
	}

	public static void put(MacroEntry entry) {
		ENTRIES.put(entry.id, entry);
	}

	public static void remove(UUID id) {
		ENTRIES.remove(id);
	}

	public static MacroEntry get(UUID id) {
		return ENTRIES.get(id);
	}

	public static Collection<MacroEntry> all() {
		return ENTRIES.values();
	}

	public static List<MacroEntry> nearby(BlockPos origin, double maxDist) {
		double maxSq = maxDist * maxDist;
		List<MacroEntry> out = new ArrayList<>();
		for (MacroEntry e : ENTRIES.values()) {
			if (e.distanceSq(origin) <= maxSq) out.add(e);
		}
		return out;
	}

	public static int size() {
		return ENTRIES.size();
	}
}
