package com.terminaldetector.drmd.world.dungeon;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.trap.RingDefenseStructures;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;

/**
 * Trap / turret wiring for living reactor sections — no mob spawn yet.
 */
public final class LivingDefensePass {
	private LivingDefensePass() {}

	public static void apply(WorldAccess world, BlockPos origin, DungeonVitality vitality, Random random) {
		if (vitality == DungeonVitality.DEAD) {
			// Sparse dead hazards only
			if (random.nextFloat() < 0.35f) {
				set(world, origin.add(6, 2, 0), ModWorldBlocks.MAGNETIC_ANOMALY.getDefaultState());
			}
			return;
		}

		boolean shellDefense = vitality == DungeonVitality.ALIVE && !vitality.disguiseAsDead;
		if (shellDefense) {
			RingDefenseStructures.placeTurretRing(world, origin, 14, origin.getY(), 6, true);
		}

		// Inner living section — always for ALIVE / SEMI_ALIVE
		placeInnerTraps(world, origin, random, vitality == DungeonVitality.SEMI_ALIVE);

		if (vitality == DungeonVitality.ALIVE && random.nextBoolean()
				&& world instanceof net.minecraft.server.world.ServerWorld sw) {
			RingDefenseStructures.placeCyclicLaserLoop(sw, origin, 8, origin.getY() + 4, 2);
		}
	}

	private static void placeInnerTraps(WorldAccess world, BlockPos origin, Random random, boolean deepOnly) {
		int start = deepOnly ? 4 : 2;
		for (Direction d : Direction.Type.HORIZONTAL) {
			BlockPos arm = origin.offset(d, 8 + start);
			set(world, arm, ModWorldBlocks.LASER_BARRIER.getDefaultState());
			set(world, arm.up(), ModWorldBlocks.VOLUME_TURRET.getDefaultState());
			if (random.nextBoolean()) {
				set(world, arm.offset(d, 3), ModWorldBlocks.HERMETIC_GATE.getDefaultState());
			}
			if (random.nextFloat() < 0.5f) {
				set(world, arm.up(2).offset(d.rotateYClockwise()),
						ModWorldBlocks.POINT_DEFENSE_TURRET.getDefaultState());
			}
		}
		// Deep reactor casemate traps
		for (int i = 0; i < 4; i++) {
			BlockPos p = origin.add(
					random.nextInt(7) - 3,
					-2 - random.nextInt(3),
					random.nextInt(7) - 3);
			Block trap = switch (random.nextInt(3)) {
				case 0 -> ModWorldBlocks.LASER_TURRET;
				case 1 -> ModWorldBlocks.PLASMA_TURRET;
				default -> ModWorldBlocks.MAGNETIC_ANOMALY;
			};
			set(world, p, trap.getDefaultState());
		}
	}

	/** Paint cracked / ruined shell so SEMI_ALIVE reads as dead from outside. */
	public static void paintDeadShell(WorldAccess world, BlockPos origin, int radius, Random random) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		int r2 = radius * radius;
		int shell = (radius - 2) * (radius - 2);
		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					int d = x * x + y * y + z * z;
					if (d > r2 || d < shell) continue;
					if (random.nextFloat() > 0.18f) continue;
					m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					var st = world.getBlockState(m);
					if (st.isAir() || st.isOf(Blocks.BEDROCK)) continue;
					world.setBlockState(m, Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	private static void set(WorldAccess world, BlockPos pos, net.minecraft.block.BlockState state) {
		int y = pos.getY();
		if (y < world.getBottomY() || y >= world.getBottomY() + world.getHeight()) return;
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
	}
}
