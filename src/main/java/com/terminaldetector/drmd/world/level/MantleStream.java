package com.terminaldetector.drmd.world.level;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * HL2-style streaming for the diggable mantle + Nether band.
 *
 * <p>Full cavern/mantle fills are expensive; only chunks near diggers / shaft grid
 * (or already marked) get the heavy {@link LevelBuilder} pass. Bedrock→plasma rewrite
 * still runs everywhere so the world border is never unbreakable.
 */
public final class MantleStream {
	/** Chunk radius around a digging pilot that loads mantle/nether content. */
	public static final int STREAM_CHUNKS = 6;

	private MantleStream() {}

	/** True when this chunk should receive the full Nether+mantle build. */
	public static boolean shouldBuildFull(ServerWorld world, int chunkX, int chunkZ) {
		if (!com.terminaldetector.drmd.world.WorldFeatures.NETHER_BAND) return false;
		if (WorldLevels.isShaftChunk(chunkX, chunkZ)) return true;
		MinecraftServer server = world.getServer();
		if (server == null) return false;
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (p.getWorld().getRegistryKey() != World.OVERWORLD) continue;
			// Surface pilots do not force deep generation; diggers and flyers do.
			if (p.getY() > WorldLevels.INDUSTRIAL_TOP + 24) continue;
			int pcx = p.getBlockX() >> 4;
			int pcz = p.getBlockZ() >> 4;
			if (Math.abs(pcx - chunkX) <= STREAM_CHUNKS && Math.abs(pcz - chunkZ) <= STREAM_CHUNKS) {
				return true;
			}
		}
		return false;
	}

	/** Soft escape cue — DimensionSync / LayerBridge already cover HUD; this is travel help. */
	public static int escapeHintY() {
		return WorldLevels.INDUSTRIAL_TOP + 8;
	}
}
