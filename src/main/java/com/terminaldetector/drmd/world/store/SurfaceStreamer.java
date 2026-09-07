package com.terminaldetector.drmd.world.store;

import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

	/**
	 * The offsets of {@link #SPAN} squared, nearest first.
	 *
	 * <p>Nested loops visit a corner first and the centre in the middle, which is backwards: the
	 * section the pilot is standing in is the one they notice missing. The donor this pattern came
	 * from — {@code FarPlaneTwo}, see the source audit — separates the two questions deliberately: the
	 * <em>set</em> is a box, because a box is cheap to enumerate, and the <em>order</em> is Manhattan
	 * distance, because that approximates radial and gets the near ones there first. DRMD had the box
	 * and not the order.
	 *
	 * <p>Built once. It matters more the wider the span gets, and more again when a third axis is
	 * added: a cube's corner is farther from its centre than a square's.
	 */
	private static final int[][] OFFSETS = nearestFirst(SPAN);

	private static int[][] nearestFirst(int span) {
		int reach = span / 2;
		List<int[]> offsets = new ArrayList<>();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				offsets.add(new int[] { dx, dz });
			}
		}
		offsets.sort(Comparator.comparingInt(o -> Math.abs(o[0]) + Math.abs(o[1])));
		return offsets.toArray(new int[0][]);
	}
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
			for (int[] offset : OFFSETS) {
				if (budget <= 0) break;
				long key = SectionKey.of(level, centreX + offset[0], centreZ + offset[1]);
				if (already.contains(key)) continue;
				SurfaceSection section = store.peek(key);
				if (section == null || !section.hasAny()) continue;
				ServerPlayNetworking.send(player, new ModNetworking.SurfacePayload(key, section.toBytes()));
				already.add(key);
				budget--;
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
