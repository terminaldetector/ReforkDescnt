package com.terminaldetector.drmd.world.gen;

import com.terminaldetector.drmd.world.WorldRules;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * Industrial Underground — modular 6DoF complexes.
 * Cavities first: spheres, cylinders, rings, vertical shafts, multi-node intersections.
 * No privileged floor — every surface is traversable in flight.
 */
public final class IndustrialComplexGenerator {
	private IndustrialComplexGenerator() {}

	private static boolean inLimit(net.minecraft.world.WorldAccess world, BlockPos pos) {
		int y = pos.getY();
		return y >= world.getBottomY() && y < world.getBottomY() + world.getHeight();
	}


	public static void generateAt(net.minecraft.world.WorldAccess world, BlockPos origin, WorldRules.ComplexStyle style, Random random) {
		clearVolume(world, origin, 28);
		carveSphere(world, origin, 18 + random.nextInt(6));
		placeReactorCore(world, origin, style);
		placeSpinRings(world, origin, 12, 2);
		placeCoolingChannels(world, origin, random);
		placeTechnicalBridges(world, origin, random);
		placeModuleSpokes(world, origin, random);
		placeEntrances(world, origin);

		// Satellite nodes for 3D route choice
		for (Direction dir : Direction.values()) {
			if (random.nextFloat() < 0.55f) {
				BlockPos node = origin.offset(dir, 22 + random.nextInt(8));
				carveSphere(world, node, 8 + random.nextInt(4));
				carveCylinder(world, origin, node, 3);
				if (random.nextBoolean()) placeHabitationCube(world, node, style);
				else placeStorageHoneycomb(world, node, random);
			}
		}

		// Vertical shaft + spiral for multilayer movement
		carveVerticalShaft(world, origin, 40);
		carveSpiral(world, origin.add(8, -10, 8), 10, 24);
	}

	public static void clearVolume(net.minecraft.world.WorldAccess world, BlockPos c, int r) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + y * y + z * z > r * r) continue;
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (inLimit(world, m)) world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	public static void carveSphere(net.minecraft.world.WorldAccess world, BlockPos c, int r) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		int r2 = r * r;
		int shell = (r - 1) * (r - 1);
		BlockState wall = Blocks.DEEPSLATE_BRICKS.getDefaultState();
		BlockState lamp = Blocks.SEA_LANTERN.getDefaultState();
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					int d = x * x + y * y + z * z;
					if (d > r2) continue;
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (!inLimit(world, m)) continue;
					if (d >= shell) {
						world.setBlockState(m, wall, Block.NOTIFY_LISTENERS);
						if ((x + y + z) % 7 == 0) world.setBlockState(m, lamp, Block.NOTIFY_LISTENERS);
					} else {
						world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
	}

	public static void carveCylinder(net.minecraft.world.WorldAccess world, BlockPos a, BlockPos b, int radius) {
		VecLine line = VecLine.of(a, b);
		BlockPos.Mutable m = new BlockPos.Mutable();
		BlockState wall = Blocks.POLISHED_DEEPSLATE.getDefaultState();
		for (float t = 0; t <= 1f; t += 1f / Math.max(1, line.length())) {
			BlockPos c = line.at(t);
			for (int x = -radius; x <= radius; x++) {
				for (int y = -radius; y <= radius; y++) {
					for (int z = -radius; z <= radius; z++) {
						if (x * x + y * y + z * z > radius * radius) continue;
						// Keep tube hollow along dominant axis of travel
						m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
						if (!inLimit(world, m)) continue;
						int d = x * x + y * y + z * z;
						if (d >= (radius - 1) * (radius - 1) && d <= radius * radius) {
							world.setBlockState(m, wall, Block.NOTIFY_LISTENERS);
						} else {
							world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
						}
					}
				}
			}
		}
	}

	public static void carveVerticalShaft(net.minecraft.world.WorldAccess world, BlockPos c, int halfHeight) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int y = -halfHeight; y <= halfHeight; y++) {
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (!inLimit(world, m)) continue;
					int d = x * x + z * z;
					if (d <= 4) world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					else if (d <= 9) world.setBlockState(m, Blocks.DEEPSLATE_TILES.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	public static void carveSpiral(net.minecraft.world.WorldAccess world, BlockPos base, int radius, int height) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int i = 0; i < height * 8; i++) {
			double ang = i * 0.35;
			int y = i / 4;
			int x = (int) (Math.cos(ang) * radius);
			int z = (int) (Math.sin(ang) * radius);
			for (int ox = -2; ox <= 2; ox++) {
				for (int oz = -2; oz <= 2; oz++) {
					m.set(base.getX() + x + ox, base.getY() + y, base.getZ() + z + oz);
					if (inLimit(world, m)) {
						world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
			m.set(base.getX() + x, base.getY() + y - 1, base.getZ() + z);
			if (inLimit(world, m)) {
				world.setBlockState(m, Blocks.COPPER_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
	}

	private static void placeReactorCore(net.minecraft.world.WorldAccess world, BlockPos c, WorldRules.ComplexStyle style) {
		BlockState core = switch (style) {
			case CRYSTAL_REACTOR -> Blocks.AMETHYST_BLOCK.getDefaultState();
			case ANCIENT_POWER -> Blocks.CRYING_OBSIDIAN.getDefaultState();
			case SMELTERY -> Blocks.MAGMA_BLOCK.getDefaultState();
			default -> Blocks.RESPAWN_ANCHOR.getDefaultState();
		};
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -2; x <= 2; x++) {
			for (int y = -2; y <= 2; y++) {
				for (int z = -2; z <= 2; z++) {
					if (x * x + y * y + z * z <= 6) {
						m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
						world.setBlockState(m, core, Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
		world.setBlockState(c, Blocks.BEACON.getDefaultState(), Block.NOTIFY_ALL);
	}

	private static void placeSpinRings(net.minecraft.world.WorldAccess world, BlockPos c, int radius, int thickness) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		BlockState ring = Blocks.IRON_BLOCK.getDefaultState();
		for (int a = 0; a < 360; a += 3) {
			double rad = Math.toRadians(a);
			for (int t = 0; t < thickness; t++) {
				int x = (int) (Math.cos(rad) * (radius + t));
				int z = (int) (Math.sin(rad) * (radius + t));
				m.set(c.getX() + x, c.getY(), c.getZ() + z);
				world.setBlockState(m, ring, Block.NOTIFY_LISTENERS);
				m.set(c.getX() + x, c.getY() + 1, c.getZ() + z);
				world.setBlockState(m, ring, Block.NOTIFY_LISTENERS);
			}
		}
	}

	private static void placeCoolingChannels(net.minecraft.world.WorldAccess world, BlockPos c, Random random) {
		for (int i = 0; i < 4; i++) {
			Direction d = Direction.Type.HORIZONTAL.random(random);
			BlockPos start = c.offset(d, 4).up(random.nextInt(3) - 1);
			for (int s = 0; s < 14; s++) {
				BlockPos p = start.offset(d, s);
				world.setBlockState(p, Blocks.BLUE_ICE.getDefaultState(), Block.NOTIFY_LISTENERS);
				world.setBlockState(p.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
	}

	private static void placeTechnicalBridges(net.minecraft.world.WorldAccess world, BlockPos c, Random random) {
		for (Direction d : Direction.Type.HORIZONTAL) {
			for (int i = 4; i < 16; i++) {
				BlockPos p = c.offset(d, i);
				world.setBlockState(p, Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);
				if (i % 4 == 0) {
					world.setBlockState(p.up(), Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	private static void placeModuleSpokes(net.minecraft.world.WorldAccess world, BlockPos c, Random random) {
		WorldRules.ModuleType[] mods = WorldRules.ModuleType.values();
		for (int i = 0; i < 3; i++) {
			Direction d = Direction.Type.HORIZONTAL.random(random);
			BlockPos node = c.offset(d, 18).up(random.nextInt(5) - 2);
			carveSphere(world, node, 6);
			carveCylinder(world, c, node, 2);
			WorldRules.ModuleType type = mods[random.nextInt(mods.length)];
			BlockState fill = switch (type) {
				case HABITATION -> Blocks.WHITE_CONCRETE.getDefaultState();
				case STORAGE -> Blocks.BARREL.getDefaultState();
				case COMMAND -> Blocks.COMMAND_BLOCK.getDefaultState();
				case COOLING -> Blocks.PACKED_ICE.getDefaultState();
				case REPAIR_HANGAR -> Blocks.ANVIL.getDefaultState();
				case POWER_SPINE -> Blocks.REDSTONE_BLOCK.getDefaultState();
				case EVAC_TUNNEL -> Blocks.LIME_CONCRETE.getDefaultState();
				default -> Blocks.COPPER_BULB.getDefaultState();
			};
			world.setBlockState(node, fill, Block.NOTIFY_LISTENERS);
		}
	}

	private static void placeEntrances(net.minecraft.world.WorldAccess world, BlockPos c) {
		for (Direction d : Direction.values()) {
			BlockPos p = c.offset(d, 17);
			carveCylinder(world, p, p.offset(d, 6), 3);
		}
	}

	private static void placeHabitationCube(net.minecraft.world.WorldAccess world, BlockPos c, WorldRules.ComplexStyle style) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -4; x <= 4; x++) {
			for (int y = -4; y <= 4; y++) {
				for (int z = -4; z <= 4; z++) {
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					boolean shell = Math.abs(x) == 4 || Math.abs(y) == 4 || Math.abs(z) == 4;
					if (shell) world.setBlockState(m, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(), Block.NOTIFY_LISTENERS);
					else world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	private static void placeStorageHoneycomb(net.minecraft.world.WorldAccess world, BlockPos c, Random random) {
		for (int i = 0; i < 7; i++) {
			double ang = i * (Math.PI * 2 / 7);
			BlockPos p = c.add((int) (Math.cos(ang) * 4), (i % 3) - 1, (int) (Math.sin(ang) * 4));
			carveSphere(world, p, 3);
			world.setBlockState(p, Blocks.BARREL.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	private record VecLine(BlockPos a, BlockPos b) {
		static VecLine of(BlockPos a, BlockPos b) { return new VecLine(a, b); }
		int length() {
			return (int) Math.sqrt(a.getSquaredDistance(b));
		}
		BlockPos at(float t) {
			return BlockPos.ofFloored(
					a.getX() + (b.getX() - a.getX()) * t,
					a.getY() + (b.getY() - a.getY()) * t,
					a.getZ() + (b.getZ() - a.getZ()) * t);
		}
	}
}
