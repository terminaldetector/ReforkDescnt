package com.terminaldetector.drmd.world.layer;

import com.terminaldetector.drmd.world.level.LevelBuilder;
import com.terminaldetector.drmd.world.level.WorldLevels;
import com.terminaldetector.drmd.world.portal.PortalComplexity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.Comparator;

/**
 * Background warm-load for Nether (Core) and End (Oblivion) before the pilot hits a seam.
 *
 * <p>Path B doctrine: the tall Overworld column is the seamless dig/fly path. This hook starts
 * streaming the Nether band in the column and opens real Nether/End dimensions with short-lived
 * chunk tickets so ImmPtl / vanilla portals / reactor arena do not hitch at the edge.
 *
 * <p>Timing: background work begins at {@link #WARM_START} blocks from the face; by
 * {@link #WARM_CRITICAL} (10) the destination should already be draining.
 */
public final class SeamWarmup {
	/** Start enqueue / dim open this far from the seam face. */
	public static final int WARM_START = 72;
	/** User-facing critical distance — intensify tickets & stream radius. */
	public static final int WARM_CRITICAL = 10;

	/** Nether Core face (OW band ↔ diggable Core). */
	public static final int NETHER_SEAM_Y = WorldLevels.NETHER_CEILING;
	/** Oblivion / End face (Orbit ↔ End band). */
	public static final int END_SEAM_Y = WorldLevels.ORBITAL_TOP;

	private static final ChunkTicketType<ChunkPos> SEAM_TICKET = ChunkTicketType.create(
			"drmd_seam_warm", Comparator.comparingLong(ChunkPos::toLong), 80);

	private SeamWarmup() {}

	public static boolean nearNetherSeam(double y) {
		return Math.abs(y - NETHER_SEAM_Y) <= WARM_START;
	}

	public static boolean nearEndSeam(double y) {
		return Math.abs(y - END_SEAM_Y) <= WARM_START;
	}

	public static boolean criticalNether(double y) {
		return Math.abs(y - NETHER_SEAM_Y) <= WARM_CRITICAL;
	}

	public static boolean criticalEnd(double y) {
		return Math.abs(y - END_SEAM_Y) <= WARM_CRITICAL;
	}

	/** Call every server player tick (Overworld). Cheap when far from seams. */
	public static void tick(ServerPlayerEntity player) {
		if (player.getWorld().getRegistryKey() != World.OVERWORLD) return;
		MinecraftServer server = player.getServer();
		if (server == null) return;

		double y = player.getY();
		boolean nether = nearNetherSeam(y);
		boolean end = nearEndSeam(y);
		if (!nether && !end) return;

		int cx = player.getBlockX() >> 4;
		int cz = player.getBlockZ() >> 4;

		if (nether) {
			boolean crit = criticalNether(y);
			int streamR = crit ? 8 : 5;
			LevelBuilder.streamAround(player.getServerWorld(), cx, cz, streamR);
			// Real Nether dim — 1:8 scale — so ImmPtl / catalyst portals open without hitch.
			warmDimension(server, World.NETHER,
					player.getBlockX() >> 3, player.getBlockZ() >> 3,
					crit ? 3 : 2);
			if (crit && player.age % 80 == 0) {
				player.sendMessage(net.minecraft.text.Text.literal(
						"§c◈ CORE SEAM §7— Nether band streaming · dim warm"
								+ (PortalComplexity.hasImmersivePortals() ? " · ImmPtl" : "")), true);
			}
		}

		if (end) {
			boolean crit = criticalEnd(y);
			// Soft End-band islands in the column only when END_BAND is on; always warm real End.
			if (com.terminaldetector.drmd.world.WorldFeatures.END_BAND) {
				LevelBuilder.streamAround(player.getServerWorld(), cx, cz, crit ? 6 : 4);
			}
			warmDimension(server, World.END, cx, cz, crit ? 3 : 2);
			// Reactor arena lives near origin — keep its chunks warm while approaching Oblivion.
			warmDimension(server, World.END, 0, 0, crit ? 4 : 2);
			if (crit && player.age % 80 == 0) {
				player.sendMessage(net.minecraft.text.Text.literal(
						"§d◈ OBLIVION SEAM §7— End dim warm"
								+ (PortalComplexity.hasImmersivePortals() ? " · ImmPtl" : "")), true);
			}
		}
	}

	private static void warmDimension(MinecraftServer server, RegistryKey<World> key,
									  int chunkX, int chunkZ, int ticketLevel) {
		ServerWorld world = server.getWorld(key);
		if (world == null) return;
		ChunkPos centre = new ChunkPos(chunkX, chunkZ);
		// Ticket level 2 ≈ ~3×3 neighbourhood; higher = wider. Expires via SEAM_TICKET.
		world.getChunkManager().addTicket(SEAM_TICKET, centre, ticketLevel, centre);
	}
}
