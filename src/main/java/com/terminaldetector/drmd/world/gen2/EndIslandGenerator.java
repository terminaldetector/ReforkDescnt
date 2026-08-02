package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.level.WorldLevels;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

import java.util.UUID;

/**
 * End-band islands — the Klondike island shape, in End stone.
 *
 * <p>The band used to be written as a shard in one chunk out of six: a dense field of small blobs
 * that read as debris rather than as a place, and cost a fill in nearly every chunk a pilot flew
 * over. This is the sky band's island instead — one sparse ellipsoid disk of real blocks with a
 * tapering underside, a lodestone at its centre so a second pass never rebuilds it, and a catalogue
 * entry for radar. Larger ones carry an obsidian spire with an End rod on top, which is what makes
 * the band navigable: from a distance the lights are the only thing that gives the archipelago a
 * shape.
 *
 * <p>Placement is a pure function of the chunk seed, so the background stream
 * ({@code LevelBuilder}) and the chunk-load pass ({@code ModWorldgen2}) agree on where the islands
 * are and neither can double up on one.
 */
public final class EndIslandGenerator {
	private EndIslandGenerator() {}

	/** One island per this many chunks, on average. Sparser than the old one-in-six shard field. */
	public static final long CHUNK_RARITY = 14L;

	/**
	 * No islands this close to the column axis.
	 *
	 * <p>The reactor arena stands at x/z 0 and clears its own radius-30 cylinder when it is raised,
	 * but the two are built by different passes in no fixed order — an island generated afterwards
	 * would drop End stone through the approach. Holding the middle open also gives the arena the
	 * empty run-up the fight wants.
	 */
	public static final int ARENA_CLEARANCE = 56;

	/**
	 * Where this chunk's island hangs, or {@code null} when it has none.
	 *
	 * @param seed the chunk-mixed world seed both placement passes derive the same way
	 */
	public static BlockPos originFor(int chunkX, int chunkZ, long seed) {
		if (Math.floorMod(seed, CHUNK_RARITY) != 0L) return null;
		int x = (chunkX << 4) + 8;
		int z = (chunkZ << 4) + 8;
		if (x * x + z * z < ARENA_CLEARANCE * ARENA_CLEARANCE) return null;
		return new BlockPos(x, placeY(seed), z);
	}

	/** Island Y inside the End band. */
	public static int placeY(long seed) {
		int span = Math.max(8, WorldLevels.END_ISLAND_MAX - WorldLevels.END_ISLAND_MIN);
		return WorldLevels.END_ISLAND_MIN + (int) Math.floorMod(seed >> 5, span);
	}

	public static MacroEntry generate(WorldAccess world, BlockPos origin, Random random) {
		int rx = 7 + random.nextInt(10);
		int rz = 7 + random.nextInt(10);
		int thick = 3 + random.nextInt(5);
		BlockPos.Mutable m = new BlockPos.Mutable();

		for (int x = -rx; x <= rx; x++) {
			for (int z = -rz; z <= rz; z++) {
				double nx = x / (double) rx;
				double nz = z / (double) rz;
				double e = nx * nx + nz * nz;
				if (e > 1.05) continue;
				int depth = (int) Math.max(1, thick * (1.0 - e * 0.65));
				for (int y = -depth; y <= 0; y++) {
					m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					if (!inLimit(world, m)) continue;
					if (y == 0) {
						world.setBlockState(m, random.nextInt(14) == 0
								? Blocks.PURPUR_BLOCK.getDefaultState()
								: Blocks.END_STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
					} else if (y > -depth) {
						world.setBlockState(m, random.nextInt(11) == 0
								? Blocks.PURPUR_BLOCK.getDefaultState()
								: Blocks.END_STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
					} else {
						// Tapered keel — brick reads as a cut edge from below, where every island in
						// the band is seen from.
						world.setBlockState(m, Blocks.END_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
				// Occasional chorus — the only thing that grows up here. It stands on End stone and
				// nothing else, so the speckle underneath is overwritten rather than gambled on:
				// a flower on purpur pops off as an item the first time the block ticks.
				if (e < 0.45 && random.nextInt(30) == 0) {
					m.set(origin.getX() + x, origin.getY(), origin.getZ() + z);
					if (inLimit(world, m)) {
						world.setBlockState(m, Blocks.END_STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
						m.set(origin.getX() + x, origin.getY() + 1, origin.getZ() + z);
						if (inLimit(world, m)) {
							world.setBlockState(m, Blocks.CHORUS_FLOWER.getDefaultState(), Block.NOTIFY_LISTENERS);
						}
					}
				}
			}
		}

		int height = 0;
		if (rx + rz >= 24) {
			height = 7 + random.nextInt(9);
			for (int h = 1; h <= height; h++) {
				m.set(origin.getX(), origin.getY() + h, origin.getZ());
				if (!inLimit(world, m)) break;
				world.setBlockState(m, Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
			m.set(origin.getX(), origin.getY() + height + 1, origin.getZ());
			if (inLimit(world, m)) {
				world.setBlockState(m, Blocks.END_ROD.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}

		// Marker last. It sits in the top layer under the spire base, and both passes above write
		// that same block, so it goes down after them or the island reads as unbuilt forever.
		if (inLimit(world, origin)) {
			world.setBlockState(origin, Blocks.LODESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
		}

		MacroEntry e = new MacroEntry(UUID.randomUUID(), MacroEntry.Kind.END_ISLAND,
				WorldRules.Layer.END_SPACE, origin.toImmutable(),
				rx * 2, thick + height + 2, rz * 2, 0xB6A2D9, "End Island");
		MacroWorld.put(e);
		return e;
	}

	/** Has this island already been built? Takes a chunk or a world — whichever the caller holds. */
	public static boolean built(BlockView view, BlockPos origin) {
		return view.getBlockState(origin).isOf(Blocks.LODESTONE);
	}

	private static boolean inLimit(WorldAccess world, BlockPos pos) {
		int y = pos.getY();
		return y >= world.getBottomY() && y < world.getBottomY() + world.getHeight();
	}
}
