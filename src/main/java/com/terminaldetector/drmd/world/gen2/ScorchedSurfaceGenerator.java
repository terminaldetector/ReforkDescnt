package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.world.WorldRules;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldAccess;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/**
 * Scorched lands surface — ash trees, bombed village husks, small ruined towns.
 */
public final class ScorchedSurfaceGenerator {
	private ScorchedSurfaceGenerator() {}

	public static MacroEntry generateTown(WorldAccess world, BlockPos xz, Random random) {
		int y = top(world, xz.getX(), xz.getZ());
		BlockPos origin = new BlockPos(xz.getX(), y, xz.getZ());
		paintAshField(world, origin, 48, random);
		spawnBurnedTrees(world, origin, 36, 28, random);
		buildRuinedVillage(world, origin.add(-18, 0, -12), random);
		buildBombedTownCore(world, origin, random);

		MacroEntry e = new MacroEntry(
				UUID.nameUUIDFromBytes(("drmd-scorched-" + xz.getX() + "," + xz.getZ()).getBytes()),
				MacroEntry.Kind.SCORCHED_TOWN, WorldRules.Layer.SURFACE,
				origin, 96, 28, 96, 0xAA6633, "Scorched Town");
		MacroWorld.put(e);
		set(world, origin, Blocks.LODESTONE.getDefaultState());
		return e;
	}

	/** Sparse burned trees across a plate without a full town. */
	public static void generateGrove(WorldAccess world, BlockPos xz, Random random) {
		int y = top(world, xz.getX(), xz.getZ());
		BlockPos origin = new BlockPos(xz.getX(), y, xz.getZ());
		paintAshField(world, origin, 22, random);
		spawnBurnedTrees(world, origin, 20, 14, random);
	}

	private static void paintAshField(WorldAccess world, BlockPos c, int r, Random random) {
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z > r * r) continue;
				int y = top(world, c.getX() + x, c.getZ() + z);
				BlockPos p = new BlockPos(c.getX() + x, y, c.getZ() + z);
				BlockState ground = random.nextFloat() < 0.55f
						? Blocks.COARSE_DIRT.getDefaultState()
						: (random.nextFloat() < 0.5f
						? Blocks.BASALT.getDefaultState()
						: Blocks.BLACKSTONE.getDefaultState());
				set(world, p, ground);
				if (random.nextFloat() < 0.08f) set(world, p.up(), Blocks.FIRE.getDefaultState());
				else if (random.nextFloat() < 0.12f) set(world, p.up(), Blocks.COAL_BLOCK.getDefaultState());
			}
		}
	}

	private static void spawnBurnedTrees(WorldAccess world, BlockPos c, int r, int count, Random random) {
		for (int i = 0; i < count; i++) {
			int x = c.getX() + random.nextInt(r * 2) - r;
			int z = c.getZ() + random.nextInt(r * 2) - r;
			int y = top(world, x, z);
			int h = 3 + random.nextInt(5);
			for (int dy = 0; dy < h; dy++) {
				set(world, new BlockPos(x, y + dy, z),
						dy == h - 1 ? Blocks.COAL_BLOCK.getDefaultState() : Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState());
			}
			// Charred canopy stubs
			if (random.nextBoolean()) {
				set(world, new BlockPos(x + 1, y + h - 1, z), Blocks.BROWN_WOOL.getDefaultState());
				set(world, new BlockPos(x - 1, y + h - 2, z), Blocks.GRAY_WOOL.getDefaultState());
			}
		}
	}

	private static void buildRuinedVillage(WorldAccess world, BlockPos c, Random random) {
		for (int i = 0; i < 6; i++) {
			int ox = (i % 3) * 10;
			int oz = (i / 3) * 12;
			BlockPos hut = c.add(ox, 0, oz);
			int y = top(world, hut.getX(), hut.getZ());
			hut = new BlockPos(hut.getX(), y, hut.getZ());
			int w = 4 + random.nextInt(3);
			int d = 4 + random.nextInt(3);
			int h = 3 + random.nextInt(2);
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					for (int dy = 0; dy < h; dy++) {
						boolean wall = x == 0 || z == 0 || x == w - 1 || z == d - 1 || dy == h - 1;
						// Bomb gaps
						if (wall && random.nextFloat() < 0.22f) continue;
						BlockState b = dy == h - 1
								? Blocks.COBBLESTONE.getDefaultState()
								: Blocks.SPRUCE_PLANKS.getDefaultState();
						if (random.nextFloat() < 0.3f) b = Blocks.COAL_BLOCK.getDefaultState();
						set(world, hut.add(x, dy, z), b);
					}
				}
			}
		}
	}

	private static void buildBombedTownCore(WorldAccess world, BlockPos c, Random random) {
		int y = top(world, c.getX(), c.getZ());
		BlockPos o = new BlockPos(c.getX(), y, c.getZ());
		// Crater
		for (int x = -8; x <= 8; x++) {
			for (int z = -8; z <= 8; z++) {
				int d = x * x + z * z;
				if (d > 64) continue;
				int depth = d < 16 ? 3 : (d < 36 ? 2 : 1);
				for (int dy = 0; dy < depth; dy++) {
					set(world, o.add(x, -dy, z), Blocks.AIR.getDefaultState());
				}
				set(world, o.add(x, -depth, z),
						random.nextBoolean() ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.COAL_BLOCK.getDefaultState());
			}
		}
		// Ruined tower shells
		for (int t = 0; t < 4; t++) {
			double ang = t * (Math.PI / 2) + 0.4;
			int tx = o.getX() + (int) (Math.cos(ang) * 22);
			int tz = o.getZ() + (int) (Math.sin(ang) * 22);
			int ty = top(world, tx, tz);
			int h = 8 + random.nextInt(10);
			for (int dy = 0; dy < h; dy++) {
				for (int x = -2; x <= 2; x++) {
					for (int z = -2; z <= 2; z++) {
						boolean shell = Math.abs(x) == 2 || Math.abs(z) == 2;
						if (!shell) continue;
						if (random.nextFloat() < 0.25f) continue;
						set(world, new BlockPos(tx + x, ty + dy, tz + z),
								Blocks.STONE_BRICKS.getDefaultState());
					}
				}
			}
		}
	}

	private static int top(WorldAccess world, int x, int z) {
		if (world instanceof ServerWorld sw) {
			return sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		}
		for (int y = 120; y >= 40; y--) {
			if (!world.getBlockState(new BlockPos(x, y, z)).isAir()) return y;
		}
		return 64;
	}

	private static void set(WorldAccess world, BlockPos pos, BlockState state) {
		int y = pos.getY();
		if (y < world.getBottomY() || y >= world.getBottomY() + world.getHeight()) return;
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
	}
}
