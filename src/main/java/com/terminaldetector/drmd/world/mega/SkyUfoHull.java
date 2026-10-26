package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enterable flying saucer hull — hollow deck, bay door, reactor core.
 * Owned/moved by {@link SkyUfoEntity}.
 */
public final class SkyUfoHull {
	public static final int MAJOR = 11;
	public static final int MINOR = 4;

	private SkyUfoHull() {}

	public record Built(BlockPos center, BlockPos core, Set<BlockPos> blocks, Box interior) {}

	/** Build hollow saucer at center. Returns core pos + block set for later clear/move. */
	public static Built build(ServerWorld world, BlockPos center) {
		Set<BlockPos> placed = new HashSet<>();
		BlockPos.Mutable m = new BlockPos.Mutable();

		for (int x = -MAJOR; x <= MAJOR; x++) {
			for (int y = -MINOR; y <= MINOR + 1; y++) {
				for (int z = -MAJOR; z <= MAJOR; z++) {
					double nx = x / (double) MAJOR;
					double ny = (y + 0.2) / (double) (MINOR + 0.5);
					double nz = z / (double) MAJOR;
					double e = nx * nx + ny * ny * 1.7 + nz * nz;
					if (e > 1.02 || e < 0.52) continue;

					// Underside bay door — open shaft so Pyro can fly in from below
					boolean bay = z >= -2 && z <= 2 && x >= -2 && x <= 2 && y < -1;
					if (bay) continue;

					m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					if (!inLimit(world, m)) continue;

					boolean outer = e > 0.78;
					BlockState state;
					if (outer) {
						state = Blocks.OXIDIZED_COPPER.getDefaultState();
					} else if (Math.abs(y) <= 1 && nx * nx + nz * nz < 0.55) {
						// Interior air (deck cavity)
						continue;
					} else if (y == -1 && nx * nx + nz * nz < 0.7) {
						state = Blocks.DARK_PRISMARINE.getDefaultState(); // deck
					} else if ((x + z) % 7 == 0 && outer) {
						state = Blocks.CYAN_STAINED_GLASS.getDefaultState();
					} else {
						state = Blocks.PRISMARINE_BRICKS.getDefaultState();
					}
					world.setBlockState(m, state, Block.NOTIFY_LISTENERS);
					placed.add(m.toImmutable());
				}
			}
		}

		// Clear interior volume for flight
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				if (x * x + z * z > 55) continue;
				for (int y = 0; y <= 2; y++) {
					m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					if (!inLimit(world, m)) continue;
					BlockState cur = world.getBlockState(m);
					if (cur.isOf(Blocks.OXIDIZED_COPPER) || cur.isOf(Blocks.PRISMARINE_BRICKS)
							|| cur.isOf(Blocks.CYAN_STAINED_GLASS)) {
						world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
						placed.remove(m.toImmutable());
					}
				}
				// Deck plate
				m.set(center.getX() + x, center.getY() - 1, center.getZ() + z);
				if (inLimit(world, m) && world.getBlockState(m).isAir()) {
					world.setBlockState(m, Blocks.DARK_PRISMARINE.getDefaultState(), Block.NOTIFY_LISTENERS);
					placed.add(m.toImmutable());
				}
			}
		}

		// Side bay door (open toward -Z)
		for (int x = -2; x <= 2; x++) {
			for (int y = 0; y <= 2; y++) {
				for (int z = -MAJOR; z <= -MAJOR + 2; z++) {
					m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					if (!inLimit(world, m)) continue;
					world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					placed.remove(m.toImmutable());
				}
			}
		}

		// Reactor core pedestal
		BlockPos core = center.up(1);
		world.setBlockState(center, Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_ALL);
		placed.add(center.toImmutable());
		world.setBlockState(core, ModWorldBlocks.UNSTABLE_REACTOR.getDefaultState(), Block.NOTIFY_ALL);
		placed.add(core.toImmutable());
		world.setBlockState(center.up(2), Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
		placed.add(center.up(2).toImmutable());

		// Ring lights
		for (int a = 0; a < 360; a += 45) {
			double rad = Math.toRadians(a);
			BlockPos p = center.add((int) (Math.cos(rad) * 8), 2, (int) (Math.sin(rad) * 8));
			if (inLimit(world, p)) {
				world.setBlockState(p, Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
				placed.add(p.toImmutable());
			}
		}

		Box interior = new Box(
				center.getX() - 8.5, center.getY() - 1.5, center.getZ() - 8.5,
				center.getX() + 8.5, center.getY() + 3.5, center.getZ() + 8.5);
		return new Built(center.toImmutable(), core.toImmutable(), placed, interior);
	}

	public static void clear(ServerWorld world, Set<BlockPos> blocks) {
		if (blocks == null || blocks.isEmpty()) return;
		// Copy + empty first: setBlockState on the reactor fires onStateReplaced →
		// SkyUfoEntity.notifyCoreBroken, which used to re-enter shatter/clear on the same Set (CME).
		List<BlockPos> copy = new ArrayList<>(blocks);
		blocks.clear();
		for (BlockPos p : copy) {
			BlockState st = world.getBlockState(p);
			if (isHullMaterial(st) || st.isOf(ModWorldBlocks.UNSTABLE_REACTOR)
					|| st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.SEA_LANTERN)
					|| st.isOf(Blocks.DARK_PRISMARINE)) {
				world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
	}

	/** Shatter hull with leftover debris / fire. */
	public static void shatter(ServerWorld world, Set<BlockPos> blocks, BlockPos epicenter) {
		if (blocks == null || blocks.isEmpty()) return;
		List<BlockPos> copy = new ArrayList<>(blocks);
		blocks.clear();
		for (BlockPos p : copy) {
			double d = p.getSquaredDistance(epicenter);
			BlockState st = world.getBlockState(p);
			if (!isHullMaterial(st) && !st.isOf(ModWorldBlocks.UNSTABLE_REACTOR)
					&& !st.isOf(Blocks.OBSIDIAN) && !st.isOf(Blocks.SEA_LANTERN)
					&& !st.isOf(Blocks.DARK_PRISMARINE)) continue;
			if (d < 36 || world.getRandom().nextInt(3) != 0) {
				world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
				if (world.getRandom().nextInt(5) == 0) {
					com.terminaldetector.drmd.world.fire.FireSystem.ignite(world, p, 2);
				}
			} else if (st.isOf(Blocks.OXIDIZED_COPPER)) {
				world.setBlockState(p, Blocks.COPPER_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
	}

	public static List<Entity> collectInterior(ServerWorld world, Box interior, Entity exclude) {
		return world.getOtherEntities(exclude, interior, e -> e.isAlive() && !(e instanceof SkyUfoEntity));
	}

	public static void shiftEntities(List<Entity> entities, Vec3d delta) {
		for (Entity e : entities) {
			e.setPosition(e.getPos().add(delta));
			e.velocityModified = true;
		}
	}

	public static boolean contains(Box interior, Vec3d pos) {
		return interior.contains(pos);
	}

	private static boolean isHullMaterial(BlockState st) {
		return st.isOf(Blocks.OXIDIZED_COPPER)
				|| st.isOf(Blocks.PRISMARINE_BRICKS)
				|| st.isOf(Blocks.CYAN_STAINED_GLASS)
				|| st.isOf(Blocks.COPPER_BLOCK)
				|| st.isOf(Blocks.DARK_PRISMARINE);
	}

	private static boolean inLimit(ServerWorld world, BlockPos pos) {
		int y = pos.getY();
		return y >= world.getBottomY() && y < world.getBottomY() + world.getHeight();
	}
}
