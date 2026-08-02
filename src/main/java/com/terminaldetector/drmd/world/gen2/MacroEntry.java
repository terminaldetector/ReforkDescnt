package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.world.WorldRules;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.UUID;

/**
 * Macro-world entry — simplified representation of a mega-structure or mega-creature
 * for LLOD / streaming at tens of kilometres.
 */
public final class MacroEntry {
	public enum Kind {
		RIFT, CANYON, ARCH, RING, FLOATING_CONTINENT, SPIRAL_RANGE, INVERTED_ISLAND,
		INDUSTRIAL_COMPLEX, STATION, WORM, SWARM, KEEPER,
		/** Descent 1 abandoned lunar outpost. */
		LUNAR_BASE,
		/** XCOM-style airborne saucer (moving entity macro). */
		UFO,
		/** Crashed abandoned saucer — dense traps, post-Pyro GX. */
		CRASHED_UFO,
		/** Cyberpunk megacity: tower grid, sewer network, reactor pyramid. */
		MEGACITY,
		/** Spark-style mega locator (dish tower) — horizon LLOD. */
		MEGA_LOCATOR,
		/** Smaller technogenic locator dish. */
		LOCATOR,
		/** Scorched / bombed town silhouette. */
		SCORCHED_TOWN
	}

	public final UUID id;
	public final Kind kind;
	public final WorldRules.Layer layer;
	public final BlockPos center;
	public final int sizeX, sizeY, sizeZ;
	public final int colorRgb;
	public final String label;

	public MacroEntry(UUID id, Kind kind, WorldRules.Layer layer, BlockPos center,
					  int sizeX, int sizeY, int sizeZ, int colorRgb, String label) {
		this.id = id;
		this.kind = kind;
		this.layer = layer;
		this.center = center;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.colorRgb = colorRgb;
		this.label = label;
	}

	public Box bounds() {
		return new Box(
				center.getX() - sizeX / 2.0, center.getY() - sizeY / 2.0, center.getZ() - sizeZ / 2.0,
				center.getX() + sizeX / 2.0, center.getY() + sizeY / 2.0, center.getZ() + sizeZ / 2.0);
	}

	public double distanceSq(BlockPos from) {
		return center.getSquaredDistance(from);
	}

	/** Which Voxel LLOD band this entry should use at a given distance. */
	public WorldRules.StreamLevel lodAt(double distance) {
		com.terminaldetector.drmd.world.llod.LlodLevel band =
				com.terminaldetector.drmd.world.llod.LlodLevel.of(distance);
		return switch (band) {
			case LLOD0 -> WorldRules.StreamLevel.LLOD0;
			case LLOD1 -> WorldRules.StreamLevel.LLOD1;
			case LLOD2 -> WorldRules.StreamLevel.LLOD2;
			case CHUNK -> WorldRules.StreamLevel.CHUNK;
			case NONE -> WorldRules.StreamLevel.LLOD0;
		};
	}
}
