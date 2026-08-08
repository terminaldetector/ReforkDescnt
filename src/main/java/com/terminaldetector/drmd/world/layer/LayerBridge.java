package com.terminaldetector.drmd.world.layer;

import com.terminaldetector.drmd.world.level.WorldLevels;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which narrative band of the column a pilot is in — Core · Surface · Sky/Orbit/End.
 *
 * <p>The bands are Y ranges of one dimension, not three volumes, so there is no seam to cross:
 * flying up out of Surface and into Orbit is flying. This used to teleport the pilot a few blocks
 * past each edge and paint a checkerboard curtain across it; both were inventing a boundary the
 * world does not have, and both are gone. What is left is the name of the band you are in.
 *
 * <p>Optional Immersive Portals remains a soft-dep for true see-through stacks.
 */
public final class LayerBridge {
	private static final int COOLDOWN_TICKS = 20 * 6;

	private static final Map<UUID, WorldLayer> LAST = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> COOLDOWN = new ConcurrentHashMap<>();

	private LayerBridge() {}

	/** Name the band on entry, for every pilot, flying or walking. */
	public static void tick(ServerPlayerEntity player) {
		if (!WorldLevels.isAdvancedColumn(player.getServerWorld())) return;

		UUID id = player.getUuid();
		int cd = COOLDOWN.getOrDefault(id, 0);
		if (cd > 0) COOLDOWN.put(id, cd - 1);

		if (player.getWorld().getRegistryKey() != World.OVERWORLD) {
			LAST.put(id, WorldLayer.at(player.getWorld(), player.getY()));
			return;
		}

		WorldLayer now = WorldLayer.at(player.getWorld(), player.getY());
		WorldLayer prev = LAST.put(id, now);
		if (prev == null || prev == now) return;
		if (cd > 0) return;

		// Crossing a layer edge is flying, not travelling. The bands are Y ranges of one column, so
		// there is nothing on the far side of an edge that the ship is not already in — the hop that
		// used to run here moved the pilot a few blocks, threw away their velocity and dropped
		// creative flight, which is the whole of what the boundary felt like. It is gone; the band
		// content is streamed on approach by SeamWarmup either way.
		announce(player, now);
		COOLDOWN.put(id, COOLDOWN_TICKS);
	}

	/**
	 * Name the band on the action bar, once, and nothing else.
	 *
	 * <p>This used to throw a full-screen title and a chat line naming the seam Y every time a pilot
	 * crossed an edge. Announcing a boundary is another way of drawing one: a climb through the
	 * column would flash four titles at the player, which is precisely the seam the column is not
	 * supposed to have. One quiet line is enough to say where you are.
	 */
	private static void announce(ServerPlayerEntity player, WorldLayer layer) {
		player.sendMessage(Text.literal("§7" + layer.label), true);
	}

	public static void clear(UUID id) {
		LAST.remove(id);
		COOLDOWN.remove(id);
	}

	public static void clearAll() {
		LAST.clear();
		COOLDOWN.clear();
	}
}
