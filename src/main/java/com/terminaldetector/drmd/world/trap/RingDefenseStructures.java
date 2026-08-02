package com.terminaldetector.drmd.world.trap;

import com.terminaldetector.drmd.entity.LaserBarrierCartEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;

/**
 * Ring-embedded AA + shield nodes + cyclic laser-barrier minecart loops.
 *
 * <p>Turrets sit in a closed hull ring (not lone pads). Shield crystals sit on the
 * approach axes. Powered-rail squares carry {@link LaserBarrierCartEntity} carts
 * that sweep dual beams as they circulate — buildable in 6DoF with the cyclic kit.
 */
public final class RingDefenseStructures {
	private RingDefenseStructures() {}

	/**
	 * Embed turrets in a deepslate ring hull; place shield projectors on the four approaches.
	 */
	public static void placeTurretRing(WorldAccess world, BlockPos center, int radius, int y,
									   int turretCount, boolean withShields) {
		int r = Math.max(4, radius);
		// Hull ring
		for (int a = 0; a < 360; a += 3) {
			double rad = Math.toRadians(a);
			int x = (int) Math.round(Math.cos(rad) * r);
			int z = (int) Math.round(Math.sin(rad) * r);
			BlockPos p = new BlockPos(center.getX() + x, y, center.getZ() + z);
			set(world, p, Blocks.POLISHED_DEEPSLATE.getDefaultState());
			set(world, p.up(), Blocks.POLISHED_BLACKSTONE.getDefaultState());
			// Clear fly tube above the ring
			for (int h = 2; h <= 6; h++) set(world, p.up(h), Blocks.AIR.getDefaultState());
		}

		Block[] kinds = {
				ModWorldBlocks.LASER_TURRET,
				ModWorldBlocks.PLASMA_TURRET,
				ModWorldBlocks.POINT_DEFENSE_TURRET,
				ModWorldBlocks.VOLUME_TURRET
		};
		int n = Math.max(4, turretCount);
		for (int i = 0; i < n; i++) {
			double ang = i * (Math.PI * 2 / n);
			int x = (int) Math.round(Math.cos(ang) * r);
			int z = (int) Math.round(Math.sin(ang) * r);
			BlockPos pad = new BlockPos(center.getX() + x, y, center.getZ() + z);
			// Nest turret in a small casemate so it reads as part of the ring.
			set(world, pad, Blocks.POLISHED_DEEPSLATE.getDefaultState());
			set(world, pad.up(), kinds[i % kinds.length].getDefaultState());
			set(world, pad.up(2), Blocks.IRON_BARS.getDefaultState());
			Direction out = Direction.fromHorizontal((int) Math.floorMod(Math.round(ang / (Math.PI / 2)), 4));
			set(world, pad.offset(out), Blocks.CYAN_STAINED_GLASS.getDefaultState());
		}

		if (withShields) {
			placeShieldCross(world, center, r + 3, y);
		}
	}

	/** Four approach-axis shield projectors (crystal + registered regen node). */
	public static void placeShieldCross(WorldAccess world, BlockPos center, int radius, int y) {
		int[][] axes = {{radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}};
		for (int[] a : axes) {
			BlockPos base = new BlockPos(center.getX() + a[0], y, center.getZ() + a[1]);
			for (int h = 0; h <= 4; h++) {
				set(world, base.up(h), Blocks.IRON_BLOCK.getDefaultState());
			}
			set(world, base.up(5), Blocks.SEA_LANTERN.getDefaultState());
			BlockPos node = base.up(1);
			ShieldProjectors.register(node);
			if (world instanceof ServerWorld sw) {
				EndCrystalEntity crystal = net.minecraft.entity.EntityType.END_CRYSTAL.create(sw);
				if (crystal != null) {
					crystal.refreshPositionAndAngles(base.getX() + 0.5, base.getY() + 6.0, base.getZ() + 0.5, 0, 0);
					crystal.setShowBottom(false);
					sw.spawnEntity(crystal);
				}
			}
			// Close-in volume cover for the projector itself
			set(world, base.add(2, 2, 0), ModWorldBlocks.VOLUME_TURRET.getDefaultState());
		}
	}

	/**
	 * Square powered-rail loop + cyclic laser carts. Radius is half-width of the square.
	 */
	public static void placeCyclicLaserLoop(ServerWorld world, BlockPos center, int radius, int y, int carts) {
		int r = Math.max(3, radius);
		// Support deck
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				boolean edge = Math.abs(x) == r || Math.abs(z) == r;
				if (!edge) continue;
				BlockPos support = new BlockPos(center.getX() + x, y, center.getZ() + z);
				set(world, support, Blocks.POLISHED_ANDESITE.getDefaultState());
				// Power every other cell so powered rails stay live.
				if (((x + z) & 1) == 0) {
					set(world, support.down(), Blocks.REDSTONE_BLOCK.getDefaultState());
				}
			}
		}

		// Sides
		for (int x = -r + 1; x <= r - 1; x++) {
			placePowered(world, center.getX() + x, y + 1, center.getZ() - r, RailShape.EAST_WEST);
			placePowered(world, center.getX() + x, y + 1, center.getZ() + r, RailShape.EAST_WEST);
		}
		for (int z = -r + 1; z <= r - 1; z++) {
			placePowered(world, center.getX() - r, y + 1, center.getZ() + z, RailShape.NORTH_SOUTH);
			placePowered(world, center.getX() + r, y + 1, center.getZ() + z, RailShape.NORTH_SOUTH);
		}
		// Corners
		placePowered(world, center.getX() - r, y + 1, center.getZ() - r, RailShape.SOUTH_EAST);
		placePowered(world, center.getX() + r, y + 1, center.getZ() - r, RailShape.SOUTH_WEST);
		placePowered(world, center.getX() - r, y + 1, center.getZ() + r, RailShape.NORTH_EAST);
		placePowered(world, center.getX() + r, y + 1, center.getZ() + r, RailShape.NORTH_WEST);

		int n = Math.max(1, Math.min(carts, 4));
		for (int i = 0; i < n; i++) {
			double t = i / (double) n;
			int side = (int) (t * 4) % 4;
			double u = (t * 4) - side;
			double px, pz;
			switch (side) {
				case 0 -> { px = -r + u * 2 * r; pz = -r; }
				case 1 -> { px = r; pz = -r + u * 2 * r; }
				case 2 -> { px = r - u * 2 * r; pz = r; }
				default -> { px = -r; pz = r - u * 2 * r; }
			}
			LaserBarrierCartEntity cart = ModEntities.LASER_BARRIER_CART.create(world);
			if (cart == null) continue;
			cart.refreshPositionAndAngles(
					center.getX() + px + 0.5, y + 1.1, center.getZ() + pz + 0.5, 0, 0);
			cart.setRingCenter(center.getX() + 0.5, center.getZ() + 0.5);
			world.spawnEntity(cart);
		}
	}

	private static void placePowered(WorldAccess world, int x, int y, int z, RailShape shape) {
		BlockState rail = Blocks.POWERED_RAIL.getDefaultState()
				.with(PoweredRailBlock.SHAPE, shape)
				.with(PoweredRailBlock.POWERED, true);
		set(world, new BlockPos(x, y, z), rail);
	}

	private static void set(WorldAccess world, BlockPos pos, BlockState state) {
		int y = pos.getY();
		if (y < world.getBottomY() || y >= world.getBottomY() + world.getHeight()) return;
		if (world.getBlockState(pos) == state) return;
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
	}
}
