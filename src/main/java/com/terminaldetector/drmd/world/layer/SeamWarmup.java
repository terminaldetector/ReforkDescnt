package com.terminaldetector.drmd.world.layer;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.world.level.LevelBuilder;
import com.terminaldetector.drmd.world.level.WorldLevels;
import com.terminaldetector.drmd.world.portal.PortalComplexity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;

/**
 * Background warm-load for Nether (Core) and End (Oblivion) before the pilot reaches a seam.
 *
 * <p>Path B doctrine: the tall Overworld column is the seamless dig/fly path. This hook streams the
 * band the pilot is heading into and opens the real Nether/End dimensions on short-lived chunk
 * tickets, so ImmPtl / vanilla portals / the reactor arena are already resident at the edge.
 *
 * <p>Distance alone was not enough to make that seamless. {@link #WARM_START} is 72 blocks, and a
 * Pyro under afterburner covers that in about a second and a half — the warm began at the same
 * moment the pilot needed it finished. Every decision here is made on where the ship will be
 * {@link #LOOKAHEAD_SECONDS} from now instead: warm starts while the seam is still out of range and
 * follows the projected position, so what loads is the ground the pilot is about to arrive over
 * rather than the ground they are leaving. Standing still projects to standing still, so nothing
 * warms for a pilot parked below the seam.
 *
 * <p>It also works in both directions. Warming only from the Overworld left the way home cold: a
 * pilot leaving the End hit an unloaded column on arrival, which is the same hitch on the same seam
 * from the other side.
 */
public final class SeamWarmup {
	/** Start enqueue / dim open this far from the seam face. */
	public static final int WARM_START = 72;
	/** User-facing critical distance — intensify tickets & stream radius. */
	public static final int WARM_CRITICAL = 10;

	/**
	 * How far ahead of the ship the approach is projected.
	 *
	 * <p>Three seconds is roughly what a chunk needs to go from a ticket to walkable ground, and at
	 * cruise it puts the warm window a couple of hundred blocks out — early enough to finish, close
	 * enough that a pilot merely passing under the seam does not drag the load with them.
	 */
	private static final double LOOKAHEAD_SECONDS = 3.0;

	/**
	 * Ticks between re-walks of the stream footprint.
	 *
	 * <p>Enqueueing is idempotent, so doing it every tick only re-checks chunks that are already
	 * queued or already built. A quarter of a second sits well inside the lookahead and keeps the
	 * repeat work off the tick loop.
	 */
	private static final int STREAM_INTERVAL = 5;

	/** Nether Core face (OW band ↔ diggable Core). */
	public static final int NETHER_SEAM_Y = WorldLevels.NETHER_CEILING;
	/** Oblivion / End face (Orbit ↔ End band). */
	public static final int END_SEAM_Y = WorldLevels.ORBITAL_TOP;

	/** Reactor arena column axis — kept warm on approach so the fight never pops in. */
	private static final ChunkPos ARENA_CHUNK = new ChunkPos(0, 0);

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

	/** Call every server player tick. Cheap when far from every seam. */
	public static void tick(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) return;

		if (player.getWorld().getRegistryKey() == World.OVERWORLD) {
			tickColumn(player, server);
		} else {
			tickReturn(player, server);
		}
	}

	/** In the column: warm whichever face the ship is heading for, and the band beyond it. */
	private static void tickColumn(ServerPlayerEntity player, MinecraftServer server) {
		DescentPlayerData data = DescentPlayerData.get(player);
		// The pilot's surface focus — where an orbital strike lands and where a return trip
		// arrives. Recorded here because this is the one hook that sees every Overworld tick.
		if (player.age % 20 == 0) {
			data.setLastOverworldBlock(player.getBlockX(), player.getBlockZ());
		}

		Vec3d velocity = velocityPerSecond(player, data);
		double y = player.getY();
		double projectedY = y + velocity.y * LOOKAHEAD_SECONDS;

		boolean nether = approaching(y, projectedY, NETHER_SEAM_Y, WARM_START);
		boolean end = approaching(y, projectedY, END_SEAM_Y, WARM_START)
				|| y >= END_SEAM_Y; // inside the band, its islands still stream in around you
		if (!nether && !end) return;

		int cx = projectedChunk(player.getX(), velocity.x);
		int cz = projectedChunk(player.getZ(), velocity.z);
		int hereCx = player.getBlockX() >> 4;
		int hereCz = player.getBlockZ() >> 4;

		if (nether) {
			boolean crit = approaching(y, projectedY, NETHER_SEAM_Y, WARM_CRITICAL);
			int streamR = crit ? 8 : 5;
			if (player.age % STREAM_INTERVAL == 0) {
				LevelBuilder.streamAround(player.getServerWorld(), cx, cz, streamR);
				if (cx != hereCx || cz != hereCz) {
					LevelBuilder.streamAround(player.getServerWorld(), hereCx, hereCz, streamR - 2);
				}
			}
			// Real Nether dim — 1:8 scale — so ImmPtl / catalyst portals open without hitch.
			warmDimension(server, World.NETHER, cx >> 3, cz >> 3, crit ? 3 : 2);
			if (crit && player.age % 80 == 0) {
				announce(player, "§c◈ CORE SEAM §7— Nether band streaming · dim warm");
			}
		}

		if (end) {
			// Deliberately wider than the face check below: islands keep streaming in around a pilot
			// who is roaming deep inside the band, not just one crossing the seam.
			boolean atSeamFace = approaching(y, projectedY, END_SEAM_Y, WARM_CRITICAL);
			boolean crit = atSeamFace || y >= END_SEAM_Y;
			// End-band islands are their own queue: a pilot up here wants the archipelago, not the
			// mantle nine hundred blocks below them.
			if (player.age % STREAM_INTERVAL == 0) {
				LevelBuilder.streamEndBand(player.getServerWorld(), cx, cz, crit ? 6 : 4);
				if (cx != hereCx || cz != hereCz) {
					LevelBuilder.streamEndBand(player.getServerWorld(), hereCx, hereCz, crit ? 4 : 3);
				}
			}
			warmDimension(server, World.END, cx, cz, crit ? 3 : 2);
			// Reactor arena sits on the column axis in both worlds — keep it resident while the
			// pilot closes on the band, in whichever of the two the fight is going to happen.
			warmDimension(server, World.END, ARENA_CHUNK.x, ARENA_CHUNK.z, crit ? 4 : 2);
			warmDimension(server, World.OVERWORLD, ARENA_CHUNK.x, ARENA_CHUNK.z, crit ? 3 : 2);
			// Gated on the narrower atSeamFace, not crit: crit stays true for the pilot's whole time
			// in the band (see above), and re-announcing every 80 ticks forever means this is on
			// screen almost continuously rather than during the crossing it is meant to flag. The
			// DRMD flight HUD sits in the same screen region as the vanilla action bar this renders
			// through, so a message that never stops repeating reads as the HUD itself glitching —
			// translucent instrument panels drawn afterward on top of it, so both texts show at once.
			if (atSeamFace && player.age % 80 == 0) {
				announce(player, "§d◈ OBLIVION SEAM §7— End band streaming · dim warm");
			}
		}
	}

	/**
	 * Outside the column: keep the way home open.
	 *
	 * <p>A pilot in the End or the Nether will come back to the Overworld at a position the server
	 * already knows — their last surface focus. Holding a small ticket there costs one chunk group
	 * and turns the return trip from a load screen's worth of generation into a hop.
	 */
	private static void tickReturn(ServerPlayerEntity player, MinecraftServer server) {
		RegistryKey<World> here = player.getWorld().getRegistryKey();
		if (here != World.END && here != World.NETHER) return;
		// Once a second is plenty: the ticket outlives the gap between refreshes four times over.
		if (player.age % 20 != 0) return;

		DescentPlayerData data = DescentPlayerData.get(player);
		int x = data.getLastOverworldX();
		int z = data.getLastOverworldZ();
		warmDimension(server, World.OVERWORLD, x >> 4, z >> 4, 2);
	}

	/**
	 * Is the seam inside the warm window now, or will it be within the lookahead?
	 *
	 * <p>The third test catches the case the first two miss: a fast enough ship can be outside the
	 * window at both ends of the projection and still pass through the seam in between.
	 */
	private static boolean approaching(double y, double projectedY, int seamY, int window) {
		if (Math.abs(y - seamY) <= window) return true;
		if (Math.abs(projectedY - seamY) <= window) return true;
		return (y - seamY) * (projectedY - seamY) < 0;
	}

	/**
	 * Ship velocity in blocks per second.
	 *
	 * <p>The flight model owns motion while 6DoF is armed and keeps it in blocks per second;
	 * {@code Entity.getVelocity} is per tick and, for a flying player, mostly stale. Prefer the
	 * former, fall back to the latter for anyone walking or falling.
	 */
	private static Vec3d velocityPerSecond(ServerPlayerEntity player, DescentPlayerData data) {
		if (data.isEnabled()) {
			Vec3d v = data.getFlightVelocity();
			if (v.lengthSquared() > 1.0e-3) return v;
		}
		return player.getVelocity().multiply(DescentMod.TICKS_PER_SECOND);
	}

	/** Chunk the ship will be over after the lookahead, clamped to a sane travel distance. */
	private static int projectedChunk(double coordinate, double blocksPerSecond) {
		double ahead = MathHelper.clamp(blocksPerSecond * LOOKAHEAD_SECONDS, -512.0, 512.0);
		return MathHelper.floor(coordinate + ahead) >> 4;
	}

	private static void announce(ServerPlayerEntity player, String message) {
		player.sendMessage(net.minecraft.text.Text.literal(
				message + (PortalComplexity.hasImmersivePortals() ? " · ImmPtl" : "")), true);
	}

	private static void warmDimension(MinecraftServer server, RegistryKey<World> key,
									  int chunkX, int chunkZ, int radius) {
		ServerWorld world = server.getWorld(key);
		if (world == null) return;
		ChunkPos centre = new ChunkPos(chunkX, chunkZ);
		// Radius is in chunks around the centre; the ticket expires on its own via SEAM_TICKET.
		world.getChunkManager().addTicket(SEAM_TICKET, centre, radius, centre);
	}
}
