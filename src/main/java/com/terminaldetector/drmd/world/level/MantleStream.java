package com.terminaldetector.drmd.world.level;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * HL2-style streaming for the diggable mantle + Nether band.
 *
 * <p>Full cavern/mantle fills are expensive; only chunks near diggers / pilots
 * get the heavy {@link LevelBuilder} pass. Bedrock→plasma rewrite
 * still runs everywhere (cheap bands) so the world border is never unbreakable.
 *
 * <p>Shaft cells are <em>not</em> force-built with zero players online — that used
 * to enqueue mantle work for every spawn-area shaft during "Preparing spawn 100%".
 */
public final class MantleStream {
	/** Chunk radius around a digging pilot that loads mantle/nether content. */
	public static final int STREAM_CHUNKS = 6;
	/**
	 * Inner radius that gets full drain priority; the ring out to {@link #STREAM_CHUNKS} is prefetch
	 * and gets only whatever budget the priority tier doesn't spend.
	 *
	 * <p>{@code STREAM_CHUNKS = 6} around one digger is already a 13×13, 169-chunk neighbourhood — and
	 * a pilot exploring fresh ground near the Core re-fills a fresh 169-chunk batch every time they
	 * round a corner, all requeued the instant it loads (the redundant {@code enqueue} calls the rest
	 * of every tick makes are no-ops once a chunk is queued, but the first pass through unbuilt ground
	 * is not redundant). {@link LevelBuilder}'s per-tick budget divided across however many of those are
	 * still mid-build makes every one of them crawl at once rather than most finishing quickly — the
	 * queue-fairness fix upstream of this one traded starvation (some chunks never touched) for exactly
	 * this (every chunk touched, all of them slowly), and a wide-enough burst still reads as the same
	 * complaint: terrain visibly filling in under the pilot rather than already being there. Shrinking
	 * to a tight priority ring doesn't change the total work or the per-tick budget — it changes where
	 * that fixed budget is spent first, so the handful of chunks the pilot can actually see finish
	 * before the wider look-ahead ring even gets a turn.
	 */
	public static final int STREAM_CHUNKS_NEAR = 2;

	private MantleStream() {}

	/** True when this chunk should receive the full Nether+mantle build. */
	public static boolean shouldBuildFull(ServerWorld world, int chunkX, int chunkZ) {
		if (!com.terminaldetector.drmd.world.WorldFeatures.NETHER_BAND) return false;
		MinecraftServer server = world.getServer();
		if (server == null) return false;
		boolean shaft = WorldLevels.isShaftChunk(chunkX, chunkZ);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (p.getWorld().getRegistryKey() != World.OVERWORLD) continue;
			int pcx = p.getBlockX() >> 4;
			int pcz = p.getBlockZ() >> 4;
			if (Math.abs(pcx - chunkX) > STREAM_CHUNKS || Math.abs(pcz - chunkZ) > STREAM_CHUNKS) {
				continue;
			}
			// Diggers / deep flyers stream the full column around them.
			if (p.getY() <= WorldLevels.INDUSTRIAL_TOP + 24) return true;
			// Approaching the Core seam: pre-stream so the Nether band is seamless.
			if (com.terminaldetector.drmd.world.layer.SeamWarmup.nearNetherSeam(p.getY())) return true;
			// Surface pilots only pull shaft escape routes when nearby.
			if (shaft) return true;
		}
		return false;
	}

	/** Soft escape cue — DimensionSync / LayerBridge already cover HUD; this is travel help. */
	public static int escapeHintY() {
		return WorldLevels.INDUSTRIAL_TOP + 8;
	}
}
