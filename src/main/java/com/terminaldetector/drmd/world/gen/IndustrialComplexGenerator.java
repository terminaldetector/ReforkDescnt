package com.terminaldetector.drmd.world.gen;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.WorldRules;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Industrial Underground — modular 6DoF complexes.
 * Cavities first: spheres, cylinders, rings, vertical shafts, multi-node intersections.
 * No privileged floor — every surface is traversable in flight.
 * Layout / palette branch on {@link WorldRules.ComplexStyle}.
 */
public final class IndustrialComplexGenerator {
	private IndustrialComplexGenerator() {}

	private static boolean inLimit(net.minecraft.world.WorldAccess world, BlockPos pos) {
		int y = pos.getY();
		return y >= world.getBottomY() && y < world.getBottomY() + world.getHeight();
	}

	public static void generateAt(net.minecraft.world.WorldAccess world, BlockPos origin,
			WorldRules.ComplexStyle style, Random random) {
		StyleProfile p = StyleProfile.of(style);
		clearVolume(world, origin, p.clearR);
		switch (p.layout) {
			case TORUS -> layoutTorus(world, origin, p, random);
			case SPIRE -> layoutSpire(world, origin, p, random);
			case CLUSTER -> layoutCluster(world, origin, p, random);
			case SHELL -> layoutShell(world, origin, p, random);
			case WARPED -> layoutWarped(world, origin, p, random);
			case SIGNAL -> layoutSignal(world, origin, p, random);
			default -> layoutSphere(world, origin, p, random);
		}
		placeReactorCore(world, origin, p);
		if (p.rings) placeSpinRings(world, origin, Math.max(8, p.mainR - 6), 2, p.trim);
		if (p.cooling) placeCoolingChannels(world, origin, random, p.accent);
		placeTechnicalBridges(world, origin, random, p.trim);
		placeModuleSpokes(world, origin, random, p);
		placeEntrances(world, origin, p.mainR - 1);

		for (Direction dir : Direction.values()) {
			if (random.nextFloat() < p.satelliteChance) {
				BlockPos node = origin.offset(dir, 18 + random.nextInt(10));
				carveSphere(world, node, 6 + random.nextInt(4), p.wall, p.lamp);
				carveCylinder(world, origin, node, 2, p.trim);
				if (random.nextBoolean()) placeHabitationCube(world, node, p);
				else placeStorageHoneycomb(world, node, random);
			}
		}

		if (p.shaft) carveVerticalShaft(world, origin, p.shaftHalf, p.wall);
		if (p.spiral) carveSpiral(world, origin.add(8, -10, 8), 10, 24, p.trim);
		if (p.locatorDecor) placeLocatorDecor(world, origin, random);
	}

	// ── Layouts ──────────────────────────────────────────────────────────────

	private static void layoutSphere(net.minecraft.world.WorldAccess world, BlockPos origin,
			StyleProfile p, Random random) {
		carveSphere(world, origin, p.mainR + random.nextInt(4), p.wall, p.lamp);
	}

	private static void layoutTorus(net.minecraft.world.WorldAccess world, BlockPos origin,
			StyleProfile p, Random random) {
		int major = p.mainR;
		int minor = 5 + random.nextInt(3);
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int a = 0; a < 360; a += 2) {
			double rad = Math.toRadians(a);
			int cx = (int) (Math.cos(rad) * major);
			int cz = (int) (Math.sin(rad) * major);
			BlockPos ring = origin.add(cx, 0, cz);
			for (int x = -minor; x <= minor; x++) {
				for (int y = -minor; y <= minor; y++) {
					for (int z = -minor; z <= minor; z++) {
						int d = x * x + y * y + z * z;
						if (d > minor * minor) continue;
						m.set(ring.getX() + x, ring.getY() + y, ring.getZ() + z);
						if (!inLimit(world, m)) continue;
						if (d >= (minor - 1) * (minor - 1)) {
							world.setBlockState(m, (a % 18 == 0) ? p.lamp : p.wall, Block.NOTIFY_LISTENERS);
						} else {
							world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
						}
					}
				}
			}
		}
		carveSphere(world, origin, 7, p.wall, p.lamp);
	}

	private static void layoutSpire(net.minecraft.world.WorldAccess world, BlockPos origin,
			StyleProfile p, Random random) {
		carveVerticalShaft(world, origin, p.shaftHalf + 12, p.wall);
		for (int i = -3; i <= 3; i++) {
			BlockPos node = origin.up(i * 10);
			carveSphere(world, node, 7 + (Math.abs(i) % 3), p.wall, p.lamp);
		}
		carveSphere(world, origin, 10, p.wall, p.lamp);
	}

	private static void layoutCluster(net.minecraft.world.WorldAccess world, BlockPos origin,
			StyleProfile p, Random random) {
		carveSphere(world, origin, p.mainR - 2, p.wall, p.lamp);
		for (int i = 0; i < 5; i++) {
			double ang = i * (Math.PI * 2 / 5) + random.nextFloat();
			int dist = 14 + random.nextInt(8);
			int oy = random.nextInt(9) - 4;
			BlockPos node = origin.add((int) (Math.cos(ang) * dist), oy, (int) (Math.sin(ang) * dist));
			carveSphere(world, node, 6 + random.nextInt(3), p.wall, p.lamp);
			carveCylinder(world, origin, node, 2, p.trim);
		}
	}

	private static void layoutShell(net.minecraft.world.WorldAccess world, BlockPos origin,
			StyleProfile p, Random random) {
		int r = p.mainR + 2;
		BlockPos.Mutable m = new BlockPos.Mutable();
		int r2 = r * r;
		int inner = (r - 3) * (r - 3);
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					int d = x * x + y * y + z * z;
					if (d > r2 || d < inner) continue;
					// Open poles for 6DoF through-flight
					if (Math.abs(y) > r - 4 && x * x + z * z < 16) continue;
					m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					if (!inLimit(world, m)) continue;
					world.setBlockState(m, ((x + y + z) % 9 == 0) ? p.lamp : p.wall, Block.NOTIFY_LISTENERS);
				}
			}
		}
		clearVolume(world, origin, r - 4);
	}

	private static void layoutWarped(net.minecraft.world.WorldAccess world, BlockPos origin,
			StyleProfile p, Random random) {
		carveSphere(world, origin, p.mainR - 4, p.wall, p.lamp);
		for (int i = 0; i < 8; i++) {
			int ox = random.nextInt(21) - 10;
			int oy = random.nextInt(17) - 8;
			int oz = random.nextInt(21) - 10;
			BlockPos node = origin.add(ox, oy, oz);
			carveSphere(world, node, 4 + random.nextInt(4), p.wall, p.lamp);
			if (i > 0) carveCylinder(world, origin.add(ox / 2, oy / 2, oz / 2), node, 2, p.trim);
		}
	}

	private static void layoutSignal(net.minecraft.world.WorldAccess world, BlockPos origin,
			StyleProfile p, Random random) {
		carveSphere(world, origin, p.mainR, p.wall, p.lamp);
		placeSpinRings(world, origin.up(6), 10, 1, p.trim);
		placeSpinRings(world, origin.down(6), 10, 1, p.trim);
		for (Direction d : Direction.Type.HORIZONTAL) {
			BlockPos arm = origin.offset(d, 16);
			carveCylinder(world, origin, arm, 3, p.trim);
			carveSphere(world, arm, 5, p.wall, p.lamp);
			set(world, arm, ModWorldBlocks.LOCATOR_RESONATOR.getDefaultState());
		}
	}

	private static void placeLocatorDecor(net.minecraft.world.WorldAccess world, BlockPos origin, Random random) {
		set(world, origin.up(4), ModWorldBlocks.LOCATOR_CORE.getDefaultState());
		for (Direction d : Direction.Type.HORIZONTAL) {
			set(world, origin.offset(d, 3).up(2), ModWorldBlocks.LOCATOR_PANEL.getDefaultState());
			if (random.nextBoolean()) {
				set(world, origin.offset(d, 5), ModWorldBlocks.LOCATOR_RESONATOR.getDefaultState());
			}
		}
	}

	// ── Primitives ───────────────────────────────────────────────────────────

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
		carveSphere(world, c, r, Blocks.DEEPSLATE_BRICKS.getDefaultState(), Blocks.SEA_LANTERN.getDefaultState());
	}

	public static void carveSphere(net.minecraft.world.WorldAccess world, BlockPos c, int r,
			BlockState wall, BlockState lamp) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		int r2 = r * r;
		int shell = (r - 1) * (r - 1);
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					int d = x * x + y * y + z * z;
					if (d > r2) continue;
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (!inLimit(world, m)) continue;
					if (d >= shell) {
						world.setBlockState(m, ((x + y + z) % 7 == 0) ? lamp : wall, Block.NOTIFY_LISTENERS);
					} else {
						world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
	}

	public static void carveCylinder(net.minecraft.world.WorldAccess world, BlockPos a, BlockPos b, int radius) {
		carveCylinder(world, a, b, radius, Blocks.POLISHED_DEEPSLATE.getDefaultState());
	}

	public static void carveCylinder(net.minecraft.world.WorldAccess world, BlockPos a, BlockPos b, int radius,
			BlockState wall) {
		VecLine line = VecLine.of(a, b);
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (float t = 0; t <= 1f; t += 1f / Math.max(1, line.length())) {
			BlockPos c = line.at(t);
			for (int x = -radius; x <= radius; x++) {
				for (int y = -radius; y <= radius; y++) {
					for (int z = -radius; z <= radius; z++) {
						if (x * x + y * y + z * z > radius * radius) continue;
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
		carveVerticalShaft(world, c, halfHeight, Blocks.DEEPSLATE_TILES.getDefaultState());
	}

	public static void carveVerticalShaft(net.minecraft.world.WorldAccess world, BlockPos c, int halfHeight,
			BlockState wall) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int y = -halfHeight; y <= halfHeight; y++) {
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (!inLimit(world, m)) continue;
					int d = x * x + z * z;
					if (d <= 4) world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					else if (d <= 9) world.setBlockState(m, wall, Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	public static void carveSpiral(net.minecraft.world.WorldAccess world, BlockPos base, int radius, int height) {
		carveSpiral(world, base, radius, height, Blocks.COPPER_BLOCK.getDefaultState());
	}

	public static void carveSpiral(net.minecraft.world.WorldAccess world, BlockPos base, int radius, int height,
			BlockState rail) {
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
				world.setBlockState(m, rail, Block.NOTIFY_LISTENERS);
			}
		}
	}

	private static void placeReactorCore(net.minecraft.world.WorldAccess world, BlockPos c, StyleProfile p) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -2; x <= 2; x++) {
			for (int y = -2; y <= 2; y++) {
				for (int z = -2; z <= 2; z++) {
					if (x * x + y * y + z * z <= 6) {
						m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
						world.setBlockState(m, p.core, Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
		world.setBlockState(c, Blocks.BEACON.getDefaultState(), Block.NOTIFY_ALL);
	}

	private static void placeSpinRings(net.minecraft.world.WorldAccess world, BlockPos c, int radius, int thickness,
			BlockState ring) {
		BlockPos.Mutable m = new BlockPos.Mutable();
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

	private static void placeCoolingChannels(net.minecraft.world.WorldAccess world, BlockPos c, Random random,
			BlockState cool) {
		for (int i = 0; i < 4; i++) {
			Direction d = Direction.Type.HORIZONTAL.random(random);
			BlockPos start = c.offset(d, 4).up(random.nextInt(3) - 1);
			for (int s = 0; s < 14; s++) {
				BlockPos p = start.offset(d, s);
				world.setBlockState(p, cool, Block.NOTIFY_LISTENERS);
				world.setBlockState(p.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
	}

	private static void placeTechnicalBridges(net.minecraft.world.WorldAccess world, BlockPos c, Random random,
			BlockState trim) {
		for (Direction d : Direction.Type.HORIZONTAL) {
			for (int i = 4; i < 16; i++) {
				BlockPos p = c.offset(d, i);
				world.setBlockState(p, Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);
				if (i % 4 == 0) {
					world.setBlockState(p.up(), Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
				if (i % 5 == 0) {
					world.setBlockState(p.down(), trim, Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	private static void placeModuleSpokes(net.minecraft.world.WorldAccess world, BlockPos c, Random random,
			StyleProfile p) {
		WorldRules.ModuleType[] mods = WorldRules.ModuleType.values();
		for (int i = 0; i < 3; i++) {
			Direction d = Direction.Type.HORIZONTAL.random(random);
			BlockPos node = c.offset(d, 18).up(random.nextInt(5) - 2);
			carveSphere(world, node, 6, p.wall, p.lamp);
			carveCylinder(world, c, node, 2, p.trim);
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

	private static void placeEntrances(net.minecraft.world.WorldAccess world, BlockPos c, int dist) {
		int d0 = MathHelper.clamp(dist, 12, 22);
		for (Direction d : Direction.values()) {
			BlockPos p = c.offset(d, d0);
			carveCylinder(world, p, p.offset(d, 6), 3);
		}
	}

	private static void placeHabitationCube(net.minecraft.world.WorldAccess world, BlockPos c, StyleProfile p) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -4; x <= 4; x++) {
			for (int y = -4; y <= 4; y++) {
				for (int z = -4; z <= 4; z++) {
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					boolean shell = Math.abs(x) == 4 || Math.abs(y) == 4 || Math.abs(z) == 4;
					if (shell) world.setBlockState(m, p.hab, Block.NOTIFY_LISTENERS);
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

	private static void set(net.minecraft.world.WorldAccess world, BlockPos pos, BlockState state) {
		if (inLimit(world, pos)) world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
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

	private enum Layout { SPHERE, TORUS, SPIRE, CLUSTER, SHELL, WARPED, SIGNAL }

	private record StyleProfile(
			Layout layout,
			BlockState wall, BlockState trim, BlockState core, BlockState accent, BlockState lamp, BlockState hab,
			int mainR, int clearR, float satelliteChance,
			boolean rings, boolean cooling, boolean shaft, boolean spiral, boolean locatorDecor,
			int shaftHalf
	) {
		static StyleProfile of(WorldRules.ComplexStyle style) {
			return switch (style) {
				case CRYSTAL_REACTOR -> base(Layout.SPHERE,
						Blocks.PURPUR_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_BLOCK,
						Blocks.BLUE_ICE, Blocks.SEA_LANTERN, Blocks.PURPLE_CONCRETE,
						18, true, true, true, true, false);
				case ANCIENT_POWER -> base(Layout.SPHERE,
						Blocks.BLACKSTONE, Blocks.CRYING_OBSIDIAN, Blocks.CRYING_OBSIDIAN,
						Blocks.MAGMA_BLOCK, Blocks.SHROOMLIGHT, Blocks.GRAY_CONCRETE,
						17, true, false, true, true, false);
				case SMELTERY -> base(Layout.CLUSTER,
						Blocks.DEEPSLATE_BRICKS, Blocks.COPPER_BLOCK, Blocks.MAGMA_BLOCK,
						Blocks.MAGMA_BLOCK, Blocks.LANTERN, Blocks.ORANGE_CONCRETE,
						16, true, false, true, false, false);
				case AUTO_FACTORY -> base(Layout.CLUSTER,
						Blocks.IRON_BLOCK, Blocks.OXIDIZED_COPPER, Blocks.REDSTONE_BLOCK,
						Blocks.BLUE_ICE, Blocks.SEA_LANTERN, Blocks.LIGHT_GRAY_CONCRETE,
						16, true, true, true, true, false);
				case TECH_RUINS -> base(Layout.CLUSTER,
						Blocks.DEEPSLATE_TILES, Blocks.WEATHERED_COPPER, Blocks.RESPAWN_ANCHOR,
						Blocks.PACKED_ICE, Blocks.SEA_LANTERN, Blocks.CYAN_CONCRETE,
						18, true, true, true, true, false);
				case SUBSEA_LOCATOR -> base(Layout.SPHERE,
						Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
						Blocks.PACKED_ICE, Blocks.SEA_LANTERN, Blocks.PRISMARINE,
						17, true, true, true, false, true);
				case HOLLOW_RING -> base(Layout.TORUS,
						Blocks.POLISHED_DEEPSLATE, Blocks.IRON_BLOCK, Blocks.RESPAWN_ANCHOR,
						Blocks.BLUE_ICE, Blocks.SEA_LANTERN, Blocks.GRAY_CONCRETE,
						14, true, true, false, true, false);
				case VERTICAL_SPIRE -> base(Layout.SPIRE,
						Blocks.DEEPSLATE_BRICKS, Blocks.COPPER_BLOCK, Blocks.COPPER_BULB,
						Blocks.BLUE_ICE, Blocks.SEA_LANTERN, Blocks.WHITE_CONCRETE,
						12, false, true, true, true, false).withShaft(48);
				case DRIFT_LAB -> base(Layout.CLUSTER,
						Blocks.SMOOTH_BASALT, Blocks.OXIDIZED_COPPER, Blocks.RESPAWN_ANCHOR,
						Blocks.PACKED_ICE, Blocks.GLOWSTONE, Blocks.LIGHT_BLUE_CONCRETE,
						15, false, true, false, false, false);
				case SIGNAL_ARRAY -> base(Layout.SIGNAL,
						Blocks.DEEPSLATE_TILES, Blocks.OXIDIZED_COPPER, Blocks.LODESTONE,
						Blocks.BLUE_ICE, Blocks.SEA_LANTERN, Blocks.CYAN_CONCRETE,
						16, true, false, true, false, true);
				case WARPED_CAVERN -> base(Layout.WARPED,
						Blocks.WARPED_NYLIUM, Blocks.WARPED_WART_BLOCK, Blocks.CRYING_OBSIDIAN,
						Blocks.SOUL_SAND, Blocks.SHROOMLIGHT, Blocks.CYAN_TERRACOTTA,
						16, false, false, false, false, false);
				case ORBITAL_SHELL -> base(Layout.SHELL,
						Blocks.END_STONE_BRICKS, Blocks.PURPUR_PILLAR, Blocks.PURPUR_BLOCK,
						Blocks.BLUE_ICE, Blocks.END_ROD, Blocks.PURPLE_CONCRETE,
						18, true, false, false, false, false);
				case ABANDONED_RESEARCH -> base(Layout.SPHERE,
						Blocks.DEEPSLATE_BRICKS, Blocks.IRON_BLOCK, Blocks.RESPAWN_ANCHOR,
						Blocks.BLUE_ICE, Blocks.SEA_LANTERN, Blocks.LIGHT_GRAY_CONCRETE,
						18, true, true, true, true, false);
			};
		}

		private static StyleProfile base(Layout layout,
				Block wall, Block trim, Block core, Block accent, Block lamp, Block hab,
				int mainR, boolean rings, boolean cooling, boolean shaft, boolean spiral, boolean locatorDecor) {
			return new StyleProfile(layout,
					wall.getDefaultState(), trim.getDefaultState(), core.getDefaultState(),
					accent.getDefaultState(), lamp.getDefaultState(), hab.getDefaultState(),
					mainR, mainR + 10, 0.55f, rings, cooling, shaft, spiral, locatorDecor, 40);
		}

		StyleProfile withShaft(int half) {
			return new StyleProfile(layout, wall, trim, core, accent, lamp, hab,
					mainR, clearR, satelliteChance, rings, cooling, true, spiral, locatorDecor, half);
		}
	}
}
