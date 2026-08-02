package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.world.WorldRules;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;

import java.util.UUID;

/**
 * Experimental multi-scale celestial / terrain megastructures.
 * Generates "wrong-looking but fully flyable" space: rifts, canyons, arches,
 * rings, floating continents, spiral ranges, inverted islands.
 */
public final class MegaStructureGenerator {
	private MegaStructureGenerator() {}

	public static MacroEntry generate(WorldAccess world, BlockPos origin, MacroEntry.Kind kind, Random random) {
		if (world.getBlockState(origin).isOf(Blocks.LODESTONE)) {
			// Already generated this cell
			return entry(kind, WorldRules.practicalLayer(origin.getY()), origin, 8, 8, 8, 0x888888, kind.name());
		}
		MacroEntry e = switch (kind) {
			case RIFT -> rift(world, origin, random);
			case CANYON -> canyon(world, origin, random);
			case ARCH -> arch(world, origin, random);
			case RING -> naturalRing(world, origin, random);
			case FLOATING_CONTINENT -> floatingContinent(world, origin, random);
			case SPIRAL_RANGE -> spiralRange(world, origin, random);
			case INVERTED_ISLAND -> invertedIsland(world, origin, random);
			case LUNAR_BASE -> LunarBaseGenerator.generate(world, origin, random);
			case CRASHED_UFO -> CrashedUfoGenerator.generate(world, origin, random);
			case MEGACITY -> MegacityGenerator.generate(world, origin, random);
			case MEGA_LOCATOR -> MegaLocatorGenerator.generateMega(world, origin, random);
			case LOCATOR -> MegaLocatorGenerator.generateSmall(world, origin, random);
			case SCORCHED_TOWN -> ScorchedSurfaceGenerator.generateTown(world, origin, random);
			case IRON_GUILD -> IronGuildSurfaceGenerator.generateHall(world, origin, random);
			case KLONDIKE_ISLAND -> KlondikeIslandGenerator.generate(world, origin, random);
			case END_ISLAND -> EndIslandGenerator.generate(world, origin, random);
			// A complex has had a generator all along; it was just never reachable from here, so
			// `/d6 mega complex` quietly built an arch instead.
			case INDUSTRIAL_COMPLEX, STATION -> industrialComplex(world, origin, random);
			// WORM / SWARM / KEEPER / UFO are entity macros, not terrain. ModWorldgen2 spawns those.
			// Building an arch for them was worse than doing nothing: it looked like it worked.
			case WORM, SWARM, KEEPER, UFO -> entry(kind, WorldRules.practicalLayer(origin.getY()),
					origin, 8, 8, 8, 0x888888, kind.name());
		};
		// Lunar / crashed / megacity / locators / scorched / klondike place their own LODESTONE marker
		if (kind != MacroEntry.Kind.LUNAR_BASE && kind != MacroEntry.Kind.CRASHED_UFO
				&& kind != MacroEntry.Kind.MEGACITY
				&& kind != MacroEntry.Kind.MEGA_LOCATOR
				&& kind != MacroEntry.Kind.LOCATOR
				&& kind != MacroEntry.Kind.SCORCHED_TOWN
				&& kind != MacroEntry.Kind.IRON_GUILD
				&& kind != MacroEntry.Kind.KLONDIKE_ISLAND
				&& kind != MacroEntry.Kind.END_ISLAND
				&& inLimit(world, origin)) {
			world.setBlockState(origin, Blocks.LODESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		// Ensure the catalogue knows about city / lunar / crash / locators (helpers already put some).
		if (e != null
				&& (kind == MacroEntry.Kind.MEGACITY
				|| kind == MacroEntry.Kind.LUNAR_BASE
				|| kind == MacroEntry.Kind.CRASHED_UFO
				|| kind == MacroEntry.Kind.INDUSTRIAL_COMPLEX
				|| kind == MacroEntry.Kind.STATION
				|| kind == MacroEntry.Kind.MEGA_LOCATOR
				|| kind == MacroEntry.Kind.LOCATOR
				|| kind == MacroEntry.Kind.SCORCHED_TOWN
				|| kind == MacroEntry.Kind.IRON_GUILD)) {
			MacroWorld.put(e);
		}
		return e;
	}

	/** Enormous vertical rift — multi-chunk deep fracture. */
	public static MacroEntry rift(WorldAccess world, BlockPos origin, Random random) {
		int length = 80 + random.nextInt(80);
		int depth = 40 + random.nextInt(40);
		int width = 6 + random.nextInt(8);
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int i = -length / 2; i <= length / 2; i++) {
			double wobble = Math.sin(i * 0.08) * 8;
			for (int y = -depth; y <= depth / 3; y++) {
				for (int w = -width; w <= width; w++) {
					m.set(origin.getX() + i, origin.getY() + y, origin.getZ() + (int) wobble + w);
					if (inLimit(world, m)) {
						world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
						if (Math.abs(w) == width && random.nextInt(5) == 0) {
							world.setBlockState(m, Blocks.BLACKSTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
						}
					}
				}
			}
		}
		MacroEntry e = entry(MacroEntry.Kind.RIFT, WorldRules.Layer.SURFACE, origin, length, depth * 2, width * 4, 0x442222, "Rift");
		MacroWorld.put(e);
		return e;
	}

	/** Endless-feeling vertical canyon corridor. */
	public static MacroEntry canyon(WorldAccess world, BlockPos origin, Random random) {
		int length = 120 + random.nextInt(100);
		int height = 50 + random.nextInt(40);
		int width = 10 + random.nextInt(10);
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int z = -length / 2; z <= length / 2; z++) {
			double curve = Math.sin(z * 0.05) * 12;
			for (int y = 0; y < height; y++) {
				for (int x = -width; x <= width; x++) {
					m.set(origin.getX() + (int) curve + x, origin.getY() + y - height / 3, origin.getZ() + z);
					if (!inLimit(world, m)) continue;
					if (Math.abs(x) < width - 1) {
						world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					} else {
						world.setBlockState(m, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
		MacroEntry e = entry(MacroEntry.Kind.CANYON, WorldRules.Layer.SURFACE, origin, width * 3, height, length, 0x665544, "Vertical Canyon");
		MacroWorld.put(e);
		return e;
	}

	/** Multi-kilometre-feel natural arch (scaled to practical chunk budget). */
	public static MacroEntry arch(WorldAccess world, BlockPos origin, Random random) {
		int radius = 28 + random.nextInt(24);
		int thickness = 4 + random.nextInt(3);
		BlockState mat = Blocks.CALCITE.getDefaultState();
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int a = 0; a <= 180; a++) {
			double rad = Math.toRadians(a);
			int x = (int) (Math.cos(rad) * radius);
			int y = (int) (Math.sin(rad) * radius);
			for (int t = -thickness; t <= thickness; t++) {
				for (int d = -thickness; d <= thickness; d++) {
					if (t * t + d * d > thickness * thickness) continue;
					m.set(origin.getX() + x + t, origin.getY() + y + d, origin.getZ());
					if (inLimit(world, m)) world.setBlockState(m, mat, Block.NOTIFY_LISTENERS);
					// Extrude arch depth
					for (int z = -6; z <= 6; z++) {
						m.set(origin.getX() + x + t, origin.getY() + y + d, origin.getZ() + z);
						if (inLimit(world, m) && Math.abs(z) <= 4) {
							world.setBlockState(m, mat, Block.NOTIFY_LISTENERS);
						}
					}
				}
			}
		}
		MacroEntry e = entry(MacroEntry.Kind.ARCH, WorldRules.Layer.SKY_ARCHIPELAGO, origin, radius * 2, radius + 10, 16, 0xE8E0D0, "Sky Arch");
		MacroWorld.put(e);
		return e;
	}

	/** Natural torus / ring in the sky. */
	public static MacroEntry naturalRing(WorldAccess world, BlockPos origin, Random random) {
		int major = 36 + random.nextInt(20);
		int minor = 5 + random.nextInt(4);
		BlockState mat = Blocks.END_STONE.getDefaultState();
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int a = 0; a < 360; a += 2) {
			double rad = Math.toRadians(a);
			double cx = Math.cos(rad) * major;
			double cz = Math.sin(rad) * major;
			for (int b = 0; b < 360; b += 15) {
				double br = Math.toRadians(b);
				int x = (int) (cx + Math.cos(br) * minor * Math.cos(rad));
				int y = (int) (Math.sin(br) * minor);
				int z = (int) (cz + Math.cos(br) * minor * Math.sin(rad));
				m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
				if (inLimit(world, m)) world.setBlockState(m, mat, Block.NOTIFY_LISTENERS);
			}
		}
		MacroEntry e = entry(MacroEntry.Kind.RING, WorldRules.Layer.SKY_ARCHIPELAGO, origin, major * 2, minor * 3, major * 2, 0xC8C090, "Celestial Ring");
		MacroWorld.put(e);
		return e;
	}

	/** Floating continent slab with underside stalactites. */
	public static MacroEntry floatingContinent(WorldAccess world, BlockPos origin, Random random) {
		int rx = 40 + random.nextInt(30);
		int rz = 40 + random.nextInt(30);
		int thick = 8 + random.nextInt(6);
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -rx; x <= rx; x++) {
			for (int z = -rz; z <= rz; z++) {
				double nx = x / (double) rx;
				double nz = z / (double) rz;
				if (nx * nx + nz * nz > 1.0) continue;
				int h = thick - (int) ((nx * nx + nz * nz) * thick * 0.6);
				for (int y = 0; y < h; y++) {
					m.set(origin.getX() + x, origin.getY() - y, origin.getZ() + z);
					if (!inLimit(world, m)) continue;
					BlockState mat = y == 0 ? Blocks.GRASS_BLOCK.getDefaultState()
							: y < 3 ? Blocks.DIRT.getDefaultState()
							: Blocks.STONE.getDefaultState();
					world.setBlockState(m, mat, Block.NOTIFY_LISTENERS);
				}
				if (random.nextInt(12) == 0) {
					int len = 3 + random.nextInt(8);
					for (int y = h; y < h + len; y++) {
						m.set(origin.getX() + x, origin.getY() - y, origin.getZ() + z);
						if (inLimit(world, m)) world.setBlockState(m, Blocks.POINTED_DRIPSTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
		MacroEntry e = entry(MacroEntry.Kind.FLOATING_CONTINENT, WorldRules.Layer.SKY_ARCHIPELAGO, origin, rx * 2, thick + 20, rz * 2, 0x4A8F3C, "Floating Continent");
		MacroWorld.put(e);
		return e;
	}

	/** Spiral mountain / ridge system rising through altitude. */
	public static MacroEntry spiralRange(WorldAccess world, BlockPos origin, Random random) {
		int turns = 4 + random.nextInt(3);
		int height = 60 + random.nextInt(40);
		int radius = 20;
		BlockPos.Mutable m = new BlockPos.Mutable();
		int steps = turns * 40;
		for (int i = 0; i < steps; i++) {
			double t = i / (double) steps;
			double ang = t * turns * Math.PI * 2;
			int y = (int) (t * height);
			int x = (int) (Math.cos(ang) * radius);
			int z = (int) (Math.sin(ang) * radius);
			for (int ox = -4; ox <= 4; ox++) {
				for (int oz = -4; oz <= 4; oz++) {
					for (int oy = -2; oy <= 6; oy++) {
						if (ox * ox + oz * oz > 16) continue;
						m.set(origin.getX() + x + ox, origin.getY() + y + oy, origin.getZ() + z + oz);
						if (inLimit(world, m)) {
							world.setBlockState(m, oy > 4 ? Blocks.SNOW_BLOCK.getDefaultState() : Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
						}
					}
				}
			}
		}
		MacroEntry e = entry(MacroEntry.Kind.SPIRAL_RANGE, WorldRules.Layer.SKY_ARCHIPELAGO, origin, radius * 3, height + 10, radius * 3, 0x8899AA, "Spiral Range");
		MacroWorld.put(e);
		return e;
	}

	/** Inverted island — flat underside, peaks pointing down. */
	public static MacroEntry invertedIsland(WorldAccess world, BlockPos origin, Random random) {
		int r = 24 + random.nextInt(16);
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				double d = Math.sqrt(x * x + z * z) / r;
				if (d > 1) continue;
				int depth = (int) ((1 - d) * (12 + random.nextInt(8)));
				// Flat top (actually the "ceiling" walkable surface from below)
				m.set(origin.getX() + x, origin.getY(), origin.getZ() + z);
				if (inLimit(world, m)) world.setBlockState(m, Blocks.MOSS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
				for (int y = 1; y <= depth; y++) {
					m.set(origin.getX() + x, origin.getY() - y, origin.getZ() + z);
					if (inLimit(world, m)) {
						world.setBlockState(m, y == depth ? Blocks.POINTED_DRIPSTONE.getDefaultState() : Blocks.DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
		MacroEntry e = entry(MacroEntry.Kind.INVERTED_ISLAND, WorldRules.Layer.SKY_ARCHIPELAGO, origin, r * 2, 20, r * 2, 0x3A5A40, "Inverted Island");
		MacroWorld.put(e);
		return e;
	}

	/** DRMD reactor complex — the styles rotate so two never look the same. */
	private static MacroEntry industrialComplex(WorldAccess world, BlockPos origin, Random random) {
		WorldRules.ComplexStyle[] styles = WorldRules.ComplexStyle.values();
		WorldRules.ComplexStyle style = styles[random.nextInt(styles.length)];
		com.terminaldetector.drmd.world.gen.IndustrialComplexGenerator.generateAt(world, origin, style, random);
		return entry(MacroEntry.Kind.INDUSTRIAL_COMPLEX, WorldRules.practicalLayer(origin.getY()),
				origin, 56, 40, 56, 0xE0A040, style.name());
	}

	private static MacroEntry entry(MacroEntry.Kind kind, WorldRules.Layer layer, BlockPos c,
									int sx, int sy, int sz, int color, String label) {
		return new MacroEntry(UUID.randomUUID(), kind, layer, c.toImmutable(), sx, sy, sz, color, label);
	}

	private static boolean inLimit(WorldAccess world, BlockPos pos) {
		int y = pos.getY();
		return y >= world.getBottomY() && y < world.getBottomY() + world.getHeight();
	}
}
