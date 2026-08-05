package com.terminaldetector.drmd.world.store;

import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends observed sections to the pilots who are about to look at them.
 *
 * <p>The rule that keeps this from becoming the thing it replaces: a section goes out <b>once</b>.
 * The client keeps what it is given, terrain nobody changes never needs re-sending, and the traffic
 * for a stationary player falls to nothing. The pipeline this succeeds streamed six hundred cells
 * every player tick and drew a shelf with them.
 *
 * <p>What a pilot wants is a neighbourhood at each level: fine sections nearby, coarse ones far
 * out — which is the same shape the horizon's rings have, because both come from the same idea that
 * detail should fall off with distance. Three sections across at every level reaches a kilometre at
 * level 1 and thirty at level 6, comfortably past anything the horizon draws.
 */
public final class SurfaceStreamer {
	/** Sections across, per level, centred on the pilot. */
	private static final int SPAN = 3;
	/** Sections sent per pilot per pass — a few kilobytes, not a burst. */
	private static final int PER_PASS = 6;
	/** Ticks between passes. */
	private static final int INTERVAL = 20;

	private static final Map<UUID, Set<Long>> sent = new ConcurrentHashMap<>();

	private SurfaceStreamer() {}

	public static void forget(UUID player) {
		sent.remove(player);
	}

	public static void clear() {
		sent.clear();
	}

	public static void tick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) return;
		SurfaceStore store = SurfaceIngest.store();
		if (store == null) return;

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			// Sections are Overworld surface; a pilot in the End is looking at the same ground.
			if (player.getWorld().getRegistryKey() != World.OVERWORLD
					&& player.getWorld().getRegistryKey() != World.END) {
				continue;
			}
			pump(player, store);
		}
	}

	private static void pump(ServerPlayerEntity player, SurfaceStore store) {
		Set<Long> already = sent.computeIfAbsent(player.getUuid(), id -> new HashSet<>());
		int budget = PER_PASS;
		int px = player.getBlockX();
		int pz = player.getBlockZ();

		// Fine levels first: they are what the pilot is closest to and notices missing.
		for (int level = 0; level <= SectionKey.MAX_LEVEL && budget > 0; level++) {
			int centreX = SectionKey.sectionOf(px, level);
			int centreZ = SectionKey.sectionOf(pz, level);
			int reach = SPAN / 2;
			for (int dx = -reach; dx <= reach && budget > 0; dx++) {
				for (int dz = -reach; dz <= reach && budget > 0; dz++) {
					long key = SectionKey.of(level, centreX + dx, centreZ + dz);
					if (already.contains(key)) continue;
					SurfaceSection section = store.peek(key);
					if (section == null || !section.hasAny()) continue;
					ServerPlayNetworking.send(player, new ModNetworking.SurfacePayload(key, section.toBytes()));
					already.add(key);
					budget--;
				}
			}
		}
	}

	/**
	 * Forget a section for everyone, so the next pass sends it again.
	 *
	 * <p>For when the ground itself changes — a reactor going up, a hillside coming down. Nothing
	 * calls it yet; Stage 10 will.
	 */
	public static void invalidate(long key) {
		for (Set<Long> keys : sent.values()) {
			keys.remove(key);
		}
	}
}
