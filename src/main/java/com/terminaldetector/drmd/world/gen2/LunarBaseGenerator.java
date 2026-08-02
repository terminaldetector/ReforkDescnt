package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.entity.ReactorDisplayEntity;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;

import java.util.UUID;

/**
 * Abandoned Descent 1 lunar base — grey hull, force-shield crystals,
 * trap corridors, micro-reactor chamber + Reactor Keeper bossfight.
 */
public final class LunarBaseGenerator {
	private LunarBaseGenerator() {}

	public static MacroEntry generate(WorldAccess world, BlockPos origin, Random random) {
		if (world.getBlockState(origin).isOf(Blocks.LODESTONE)) {
			return new MacroEntry(UUID.randomUUID(), MacroEntry.Kind.LUNAR_BASE,
					WorldRules.Layer.SKY_ARCHIPELAGO, origin.toImmutable(), 48, 24, 48, 0xAAB8C8, "Lunar Base");
		}

		int radius = 22;
		BlockPos.Mutable m = new BlockPos.Mutable();

		// Disc hull (lunar grey)
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				int d2 = x * x + z * z;
				if (d2 > radius * radius) continue;
				for (int y = -2; y <= 6; y++) {
					m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					if (!inLimit(world, m)) continue;
					boolean rim = d2 > (radius - 2) * (radius - 2);
					boolean floor = y == -2 || y == 0;
					boolean roof = y == 6;
					if (rim || floor || roof) {
						world.setBlockState(m, (rim ? Blocks.POLISHED_ANDESITE : Blocks.LIGHT_GRAY_CONCRETE).getDefaultState(),
								Block.NOTIFY_LISTENERS);
					} else if (y > 0 && y < 6 && d2 < (radius - 3) * (radius - 3)) {
						world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
				// Outer walkway lip
				if (d2 > (radius - 1) * (radius - 1)) {
					m.set(origin.getX() + x, origin.getY() + 1, origin.getZ() + z);
					if (inLimit(world, m)) {
						world.setBlockState(m, Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}

		// Spoke corridors + hermetic gates
		int[][] spokes = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] s : spokes) {
			for (int d = 4; d <= radius - 3; d++) {
				BlockPos p = origin.add(s[0] * d, 1, s[1] * d);
				clearChamber(world, p, 1);
				if (d == 8 || d == 14) {
					world.setBlockState(p, ModWorldBlocks.HERMETIC_GATE.getDefaultState(), Block.NOTIFY_ALL);
				}
				if (d == 11) {
					world.setBlockState(p.up(2), ModWorldBlocks.LASER_BARRIER.getDefaultState(), Block.NOTIFY_ALL);
				}
			}
			// Turret pads at mid-spoke
			BlockPos pad = origin.add(s[0] * 12, 0, s[1] * 12);
			world.setBlockState(pad.up(), Blocks.POLISHED_ANDESITE.getDefaultState(), Block.NOTIFY_LISTENERS);
			Block turret = switch (Math.abs(s[0] + 2 * s[1])) {
				case 1 -> ModWorldBlocks.LASER_TURRET;
				case 2 -> ModWorldBlocks.PLASMA_TURRET;
				default -> ModWorldBlocks.POINT_DEFENSE_TURRET;
			};
			world.setBlockState(pad.up(2), turret.getDefaultState(), Block.NOTIFY_ALL);
		}

		// Magnetic anomalies in side bays
		place(world, origin.add(10, 1, 10), ModWorldBlocks.MAGNETIC_ANOMALY);
		place(world, origin.add(-10, 1, -10), ModWorldBlocks.MAGNETIC_ANOMALY);

		// Central micro-reactor chamber
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				if (x * x + z * z > 25) continue;
				m.set(origin.getX() + x, origin.getY() + 1, origin.getZ() + z);
				if (inLimit(world, m)) world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				world.setBlockState(origin.add(x, 0, z), Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
		for (int y = 1; y <= 4; y++) {
			world.setBlockState(origin.up(y), Blocks.RESPAWN_ANCHOR.getDefaultState(), Block.NOTIFY_ALL);
		}
		world.setBlockState(origin.up(5), ModWorldBlocks.UNSTABLE_REACTOR.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(origin, Blocks.LODESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);

		// Force-shield crystals (Descent shield nodes analogue)
		BlockPos[] nodes = {
				origin.add(7, 0, 0), origin.add(-7, 0, 0),
				origin.add(0, 0, 7), origin.add(0, 0, -7)
		};
		for (BlockPos n : nodes) {
			for (int y = 1; y <= 4; y++) {
				world.setBlockState(n.up(y), Blocks.IRON_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
			world.setBlockState(n.up(5), Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
			if (world instanceof ServerWorld sw) {
				EndCrystalEntity crystal = net.minecraft.entity.EntityType.END_CRYSTAL.create(sw);
				if (crystal != null) {
					crystal.refreshPositionAndAngles(n.getX() + 0.5, n.getY() + 6.0, n.getZ() + 0.5, 0, 0);
					crystal.setShowBottom(false);
					sw.spawnEntity(crystal);
				}
			}
			world.setBlockState(n.add(2, 2, 0), ModWorldBlocks.VOLUME_TURRET.getDefaultState(), Block.NOTIFY_ALL);
		}

		if (world instanceof ServerWorld sw) {
			ReactorDisplayEntity core = ModEntities.REACTOR_DISPLAY.create(sw);
			if (core != null) {
				core.refreshPositionAndAngles(origin.getX() + 0.5, origin.getY() + 7.0, origin.getZ() + 0.5, 0, 0);
				sw.spawnEntity(core);
			}
			ReactorKeeperEntity keeper = ModEntities.REACTOR_KEEPER.create(sw);
			if (keeper != null) {
				Vec3d anchor = new Vec3d(origin.getX() + 0.5, origin.getY() + 8.0, origin.getZ() + 0.5);
				keeper.refreshPositionAndAngles(anchor.x + 8, anchor.y, anchor.z, 0, 0);
				keeper.setAnchor(anchor);
				sw.spawnEntity(keeper);
			}
		}

		// Ring-embedded AA + shield projectors + cyclic laser carts on the disc rim.
		com.terminaldetector.drmd.world.trap.RingDefenseStructures.placeTurretRing(
				world, origin, radius - 2, origin.getY(), 8, true);
		if (world instanceof ServerWorld sw) {
			com.terminaldetector.drmd.world.trap.RingDefenseStructures.placeCyclicLaserLoop(
					sw, origin, 10, origin.getY(), 2);
		}

		MacroEntry e = new MacroEntry(UUID.randomUUID(), MacroEntry.Kind.LUNAR_BASE,
				WorldRules.Layer.SKY_ARCHIPELAGO, origin.toImmutable(),
				radius * 2 + 8, 18, radius * 2 + 8, 0xAAB8C8, "Abandoned Lunar Base");
		MacroWorld.put(e);
		return e;
	}

	private static void clearChamber(WorldAccess world, BlockPos c, int r) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -r; x <= r; x++) {
			for (int y = 0; y <= 3; y++) {
				for (int z = -r; z <= r; z++) {
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (inLimit(world, m)) {
						world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
	}

	private static void place(WorldAccess world, BlockPos pos, Block block) {
		if (inLimit(world, pos)) {
			world.setBlockState(pos, block.getDefaultState(), Block.NOTIFY_ALL);
		}
	}

	private static boolean inLimit(WorldAccess world, BlockPos pos) {
		int y = pos.getY();
		return y >= world.getBottomY() && y < world.getBottomY() + world.getHeight();
	}
}
