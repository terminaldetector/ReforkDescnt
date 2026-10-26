package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.enclave.EnclaveOrigin;
import com.terminaldetector.drmd.world.enclave.EnclaveSite;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldAccess;

import java.util.UUID;

/**
 * Strafe techno-medieval guild surface — rust yards, repaired reactors, caste camps.
 */
public final class IronGuildSurfaceGenerator {
	private IronGuildSurfaceGenerator() {}

	public static MacroEntry generateHall(WorldAccess world, BlockPos xz, Random random) {
		int y = top(world, xz.getX(), xz.getZ());
		BlockPos origin = new BlockPos(xz.getX(), y, xz.getZ());
		paintRustYard(world, origin, 42, random);
		buildGuildHall(world, origin, random);
		buildPatchedReactor(world, origin.add(18, 0, 8), random);
		placeEnclaveCamp(world, origin.add(-22, 0, -10), EnclaveOrigin.ENGINEERS, random);
		placeEnclaveCamp(world, origin.add(14, 0, -20), EnclaveOrigin.SCAVENGERS, random);
		placeEnclaveCamp(world, origin.add(-8, 0, 24),
				random.nextBoolean() ? EnclaveOrigin.CULTISTS : EnclaveOrigin.MILITARY, random);

		MacroEntry e = new MacroEntry(
				UUID.nameUUIDFromBytes(("drmd-guild-" + xz.getX() + "," + xz.getZ()).getBytes()),
				MacroEntry.Kind.IRON_GUILD, WorldRules.Layer.SURFACE,
				origin, 88, 32, 88, 0x8B6B4A, "Iron Guild");
		MacroWorld.put(e);
		set(world, origin, Blocks.LODESTONE.getDefaultState());
		return e;
	}

	private static void paintRustYard(WorldAccess world, BlockPos c, int r, Random random) {
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z > r * r) continue;
				int y = top(world, c.getX() + x, c.getZ() + z);
				BlockPos p = new BlockPos(c.getX() + x, y, c.getZ() + z);
				float roll = random.nextFloat();
				BlockState ground = roll < 0.35f ? Blocks.OXIDIZED_COPPER.getDefaultState()
						: roll < 0.55f ? Blocks.COBBLESTONE.getDefaultState()
						: roll < 0.75f ? Blocks.GRAVEL.getDefaultState()
						: Blocks.COARSE_DIRT.getDefaultState();
				set(world, p, ground);
				if (random.nextFloat() < 0.06f) {
					set(world, p.up(), Blocks.IRON_BARS.getDefaultState());
				} else if (random.nextFloat() < 0.04f) {
					set(world, p.up(), Blocks.LANTERN.getDefaultState());
				}
			}
		}
	}

	private static void buildGuildHall(WorldAccess world, BlockPos c, Random random) {
		int y = top(world, c.getX(), c.getZ());
		BlockPos o = new BlockPos(c.getX(), y, c.getZ());
		int w = 14;
		int d = 10;
		int h = 7;
		for (int x = -w / 2; x <= w / 2; x++) {
			for (int z = -d / 2; z <= d / 2; z++) {
				for (int dy = 0; dy <= h; dy++) {
					boolean wall = Math.abs(x) == w / 2 || Math.abs(z) == d / 2 || dy == 0 || dy == h;
					if (!wall) continue;
					// Centuries of patchwork — stone brick bones, copper patches
					BlockState b;
					if (dy == 0) {
						b = Blocks.DEEPSLATE_BRICKS.getDefaultState();
					} else if (dy == h) {
						b = random.nextFloat() < 0.4f
								? Blocks.WEATHERED_COPPER.getDefaultState()
								: Blocks.STONE_BRICK_SLAB.getDefaultState();
					} else if (random.nextFloat() < 0.25f) {
						b = Blocks.OXIDIZED_COPPER.getDefaultState();
					} else if (random.nextFloat() < 0.15f) {
						b = Blocks.IRON_BLOCK.getDefaultState();
					} else {
						b = Blocks.STONE_BRICKS.getDefaultState();
					}
					set(world, o.add(x, dy, z), b);
				}
			}
		}
		// Door gap
		for (int dy = 1; dy <= 3; dy++) {
			set(world, o.add(0, dy, -d / 2), Blocks.AIR.getDefaultState());
			set(world, o.add(1, dy, -d / 2), Blocks.AIR.getDefaultState());
		}
		// Interior hearth / forge
		set(world, o.add(0, 1, 0), Blocks.BLAST_FURNACE.getDefaultState());
		set(world, o.add(0, 2, 0), Blocks.CHAIN.getDefaultState());
		set(world, o.add(2, 1, 1), ModWorldBlocks.ENCLAVE_HERALD.getDefaultState());
	}

	private static void buildPatchedReactor(WorldAccess world, BlockPos xz, Random random) {
		int y = top(world, xz.getX(), xz.getZ());
		BlockPos o = new BlockPos(xz.getX(), y, xz.getZ());
		for (int dy = 0; dy < 10; dy++) {
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					int md = Math.max(Math.abs(x), Math.abs(z));
					if (md != 3 && dy != 0 && dy != 9) continue;
					if (random.nextFloat() < 0.12f) continue; // missing plates
					BlockState b = dy < 3 ? Blocks.DEEPSLATE_BRICKS.getDefaultState()
							: (random.nextFloat() < 0.5f
							? Blocks.WEATHERED_COPPER.getDefaultState()
							: Blocks.OXIDIZED_COPPER.getDefaultState());
					set(world, o.add(x, dy, z), b);
				}
			}
		}
		set(world, o.add(0, 1, 0), Blocks.RESPAWN_ANCHOR.getDefaultState());
		set(world, o.add(0, 4, 0), Blocks.LIGHTNING_ROD.getDefaultState());
	}

	private static void placeEnclaveCamp(WorldAccess world, BlockPos xz, EnclaveOrigin prefer, Random random) {
		int y = top(world, xz.getX(), xz.getZ());
		BlockPos o = new BlockPos(xz.getX(), y, xz.getZ());
		// Lean-to of scrap
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 4; z++) {
				set(world, o.add(x, 0, z), Blocks.COBBLESTONE.getDefaultState());
				boolean wall = x == 0 || z == 0 || x == 4 || z == 3;
				if (wall) {
					BlockState w = random.nextBoolean()
							? Blocks.OXIDIZED_COPPER.getDefaultState()
							: Blocks.SPRUCE_PLANKS.getDefaultState();
					set(world, o.add(x, 1, z), w);
					if (random.nextBoolean()) set(world, o.add(x, 2, z), w);
				}
			}
		}
		set(world, o.add(2, 1, 2), ModWorldBlocks.ENCLAVE_HERALD.getDefaultState());
		// Touch EnclaveSite so generation path is exercised / loggable via seed
		if (world instanceof ServerWorld sw) {
			EnclaveSite.generateBiased(sw.getSeed(), o, prefer);
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
