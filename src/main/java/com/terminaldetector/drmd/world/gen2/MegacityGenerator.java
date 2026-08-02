package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.WorldRules;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;

import java.util.UUID;

/**
 * Cyberpunk megacity — towers, street canyons, a sewer network, and a reactor pyramid.
 *
 * <p>Built on a street grid rather than scattered, because the point of the place is the canyons
 * between the towers: they are what makes it fly-through space instead of scenery. Every street
 * line is mirrored underground by a tunnel, and the two are stitched together at the intersections,
 * so the city is one continuous volume from the sewer floor to the tower roofs — you can enter at
 * any manhole and come out anywhere.
 *
 * <p>Towers are shells with floor plates, not solid prisms. That is what keeps the whole district
 * inside a sane block budget, and it also means they are enterable, which a solid one would not be.
 */
public final class MegacityGenerator {
	/** City blocks per side. Odd, so one block is dead centre for the pyramid. */
	private static final int GRID = 5;
	/** Footprint of one city block. */
	private static final int BLOCK_SIZE = 16;
	/** Street canyon width between blocks. */
	private static final int STREET = 8;
	/** Total span, used for the macro entry and the tunnel runs. */
	private static final int SPAN = GRID * BLOCK_SIZE + (GRID - 1) * STREET;

	private static final int TOWER_MIN = 22;
	private static final int TOWER_MAX = 58;
	/** Floor plate spacing inside a tower. */
	private static final int STOREY = 6;

	/** Depth of the sewer deck below street level. */
	private static final int SEWER_DROP = 14;
	private static final int SEWER_H = 4;

	private static final int PYRAMID_STEPS = 11;
	private static final int PYRAMID_STEP_H = 3;

	private MegacityGenerator() {}

	public static MacroEntry generate(WorldAccess world, BlockPos origin, Random random) {
		int half = SPAN / 2;
		int y = origin.getY();

		streets(world, origin, half, y);
		sewers(world, origin, half, y, random);

		int mid = GRID / 2;
		for (int gx = 0; gx < GRID; gx++) {
			for (int gz = 0; gz < GRID; gz++) {
				if (gx == mid && gz == mid) continue;
				BlockPos corner = blockCorner(origin, half, gx, gz);
				tower(world, corner, y, random);
			}
		}

		reactorPyramid(world, blockCorner(origin, half, mid, mid).add(BLOCK_SIZE / 2, 0, BLOCK_SIZE / 2), y);

		if (inLimit(world, origin)) {
			world.setBlockState(origin, Blocks.LODESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		return new MacroEntry(UUID.randomUUID(), MacroEntry.Kind.MEGACITY,
				WorldRules.practicalLayer(y), origin.toImmutable(),
				SPAN, TOWER_MAX + SEWER_DROP, SPAN, 0x22D3EE, "Megacity");
	}

	private static BlockPos blockCorner(BlockPos origin, int half, int gx, int gz) {
		int x = origin.getX() - half + gx * (BLOCK_SIZE + STREET);
		int z = origin.getZ() - half + gz * (BLOCK_SIZE + STREET);
		return new BlockPos(x, origin.getY(), z);
	}

	/** Deck the whole footprint, then let the towers stand on it. */
	private static void streets(WorldAccess world, BlockPos origin, int half, int y) {
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				BlockPos p = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
				set(world, p, Blocks.POLISHED_ANDESITE.getDefaultState());
				set(world, p.down(), Blocks.DEEPSLATE_TILES.getDefaultState());
				// Clear the canyon so the streets are flyable rather than filled with terrain.
				for (int h = 1; h <= 4; h++) set(world, p.up(h), Blocks.AIR.getDefaultState());
			}
		}
		// Street lighting on a regular pitch — also the visual grid that makes the layout legible
		// from the air, which matters when the district is mostly seen from above.
		for (int dx = -half; dx <= half; dx += 8) {
			for (int dz = -half; dz <= half; dz += 8) {
				BlockPos p = new BlockPos(origin.getX() + dx, y + 1, origin.getZ() + dz);
				if (world.getBlockState(p.down()).isOf(Blocks.POLISHED_ANDESITE)) {
					set(world, p, Blocks.SEA_LANTERN.getDefaultState());
				}
			}
		}
	}

	/**
	 * Tunnel network mirroring the street grid, one deck down.
	 *
	 * <p>Runs the full span on both axes so every tunnel meets every other, and drops a shaft with
	 * a ladder at each street intersection. A sewer you cannot get out of is a pit, not a level.
	 */
	private static void sewers(WorldAccess world, BlockPos origin, int half, int y, Random random) {
		int deck = y - SEWER_DROP;
		int pitch = BLOCK_SIZE + STREET;

		for (int i = 0; i < GRID - 1; i++) {
			int off = -half + BLOCK_SIZE + i * pitch + STREET / 2;
			tunnel(world, origin, half, deck, off, true, random);
			tunnel(world, origin, half, deck, off, false, random);
		}

		for (int i = 0; i < GRID - 1; i++) {
			for (int j = 0; j < GRID - 1; j++) {
				int ox = -half + BLOCK_SIZE + i * pitch + STREET / 2;
				int oz = -half + BLOCK_SIZE + j * pitch + STREET / 2;
				shaft(world, new BlockPos(origin.getX() + ox, deck, origin.getZ() + oz), y);
			}
		}
	}

	private static void tunnel(WorldAccess world, BlockPos origin, int half, int deck, int off,
							   boolean alongX, Random random) {
		for (int t = -half; t <= half; t++) {
			int x = alongX ? t : off;
			int z = alongX ? off : t;
			BlockPos base = new BlockPos(origin.getX() + x, deck, origin.getZ() + z);
			for (int w = -2; w <= 2; w++) {
				int wx = alongX ? 0 : w;
				int wz = alongX ? w : 0;
				BlockPos c = base.add(wx, 0, wz);
				// Shell first, then hollow: floor, walls, ceiling.
				set(world, c.down(), Blocks.MOSSY_STONE_BRICKS.getDefaultState());
				for (int h = 0; h < SEWER_H; h++) {
					boolean edge = Math.abs(w) == 2;
					set(world, c.up(h), edge
							? Blocks.MOSSY_STONE_BRICKS.getDefaultState()
							: Blocks.AIR.getDefaultState());
				}
				set(world, c.up(SEWER_H), Blocks.MOSSY_STONE_BRICKS.getDefaultState());
				// Central channel carries the water; the flanks stay walkable.
				if (w == 0) set(world, c.down(), Blocks.WATER.getDefaultState());
			}
			if (Math.floorMod(t, 11) == 0) {
				set(world, base.up(SEWER_H - 1), Blocks.SHROOMLIGHT.getDefaultState());
			}
			if (Math.floorMod(t, 23) == 0 && random.nextInt(3) == 0) {
				set(world, base.add(alongX ? 0 : 1, 0, alongX ? 1 : 0), Blocks.IRON_BARS.getDefaultState());
			}
		}
	}

	/** Manhole from the sewer deck up to the street, with a ladder to climb back out. */
	private static void shaft(WorldAccess world, BlockPos deckPos, int streetY) {
		for (int yy = deckPos.getY(); yy <= streetY; yy++) {
			BlockPos p = new BlockPos(deckPos.getX(), yy, deckPos.getZ());
			set(world, p, Blocks.AIR.getDefaultState());
			set(world, p.north(), Blocks.DEEPSLATE_TILES.getDefaultState());
			set(world, p.south(), Blocks.DEEPSLATE_TILES.getDefaultState());
			set(world, p.east(), Blocks.DEEPSLATE_TILES.getDefaultState());
			set(world, p.west(), Blocks.DEEPSLATE_TILES.getDefaultState());
			set(world, p, Blocks.LADDER.getDefaultState()
					.with(net.minecraft.block.LadderBlock.FACING, net.minecraft.util.math.Direction.SOUTH));
		}
		set(world, new BlockPos(deckPos.getX(), streetY, deckPos.getZ()), Blocks.AIR.getDefaultState());
	}

	/** One tower: shell, floor plates, glazing bands, neon crown. */
	private static void tower(WorldAccess world, BlockPos corner, int y, Random random) {
		int inset = 2;
		int size = BLOCK_SIZE - inset * 2;
		int height = TOWER_MIN + random.nextInt(TOWER_MAX - TOWER_MIN);
		BlockState wall = random.nextInt(3) == 0
				? Blocks.POLISHED_DEEPSLATE.getDefaultState()
				: Blocks.DEEPSLATE_TILES.getDefaultState();
		BlockState glass = switch (random.nextInt(3)) {
			case 0 -> Blocks.CYAN_STAINED_GLASS.getDefaultState();
			case 1 -> Blocks.PURPLE_STAINED_GLASS.getDefaultState();
			default -> Blocks.TINTED_GLASS.getDefaultState();
		};

		for (int h = 0; h < height; h++) {
			boolean band = h > 0 && h % STOREY != 0 && h % STOREY != STOREY - 1;
			for (int dx = 0; dx < size; dx++) {
				for (int dz = 0; dz < size; dz++) {
					boolean edge = dx == 0 || dz == 0 || dx == size - 1 || dz == size - 1;
					BlockPos p = corner.add(inset + dx, y + 1 + h, inset + dz);
					if (edge) {
						set(world, p, band ? glass : wall);
					} else if (h % STOREY == 0) {
						// Floor plate: makes the tower enterable and stops it being a light well.
						set(world, p, Blocks.SMOOTH_STONE.getDefaultState());
					} else {
						set(world, p, Blocks.AIR.getDefaultState());
					}
				}
			}
		}

		// Crown: a lit rim so the skyline reads at night and from altitude.
		int top = y + 1 + height;
		BlockState neon = random.nextInt(2) == 0
				? Blocks.SEA_LANTERN.getDefaultState()
				: Blocks.SHROOMLIGHT.getDefaultState();
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				boolean edge = dx == 0 || dz == 0 || dx == size - 1 || dz == size - 1;
				BlockPos p = corner.add(inset + dx, top, inset + dz);
				set(world, p, edge ? neon : Blocks.SMOOTH_STONE.getDefaultState());
			}
		}
		if (random.nextInt(3) == 0) {
			for (int h = 1; h <= 8; h++) {
				set(world, corner.add(BLOCK_SIZE / 2, top + h, BLOCK_SIZE / 2),
						h == 8 ? Blocks.REDSTONE_LAMP.getDefaultState() : Blocks.IRON_BARS.getDefaultState());
			}
		}
	}

	/**
	 * The pyramid at the centre, which is also the reactor.
	 *
	 * <p>Stepped shell with a hollow interior and the core suspended in the middle of it, so the
	 * thing you have come for is visible from the entrance rather than buried in fill.
	 */
	private static void reactorPyramid(WorldAccess world, BlockPos centre, int y) {
		int base = PYRAMID_STEPS * 2 + 1;
		for (int step = 0; step < PYRAMID_STEPS; step++) {
			int r = (base / 2) - step * 2;
			if (r < 1) break;
			int levelY = y + 1 + step * PYRAMID_STEP_H;
			for (int h = 0; h < PYRAMID_STEP_H; h++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
						BlockPos p = centre.add(dx, levelY + h, dz);
						if (edge) {
							boolean seam = (Math.abs(dx) + Math.abs(dz) + levelY + h) % 7 == 0;
							set(world, p, seam
									? Blocks.OXIDIZED_COPPER.getDefaultState()
									: Blocks.POLISHED_DEEPSLATE.getDefaultState());
						} else if (h == 0 && step == 0) {
							set(world, p, Blocks.DEEPSLATE_TILES.getDefaultState());
						} else {
							set(world, p, Blocks.AIR.getDefaultState());
						}
					}
				}
			}
		}

		// Way in at ground level, on the south face.
		int r0 = base / 2;
		for (int h = 1; h <= 3; h++) {
			for (int dx = -1; dx <= 1; dx++) {
				set(world, centre.add(dx, y + h, r0), Blocks.AIR.getDefaultState());
			}
		}

		int coreY = y + 1 + (PYRAMID_STEPS * PYRAMID_STEP_H) / 2;
		BlockPos core = centre.up(coreY - y);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos p = core.add(dx, dy, dz);
					boolean shell = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1;
					set(world, p, shell
							? Blocks.CRYING_OBSIDIAN.getDefaultState()
							: ModWorldBlocks.UNSTABLE_REACTOR.getDefaultState());
				}
			}
		}
		// Support column so the core reads as mounted rather than floating.
		for (int yy = y + 1; yy < core.getY() - 1; yy++) {
			set(world, new BlockPos(core.getX(), yy, core.getZ()), Blocks.COPPER_BLOCK.getDefaultState());
		}
		set(world, centre.up(PYRAMID_STEPS * PYRAMID_STEP_H + 1), Blocks.BEACON.getDefaultState());
	}

	/**
	 * Write a block, unless it is already that block.
	 *
	 * <p>Most of a tower's interior and most of the street clearing is air being written over air —
	 * two thirds of the district's writes, and every one of them still costs a block update and a
	 * lighting recalculation. The read is far cheaper than the write it avoids.
	 */
	private static void set(WorldAccess world, BlockPos pos, BlockState state) {
		if (!inLimit(world, pos)) return;
		if (world.getBlockState(pos) == state) return;
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
	}

	private static boolean inLimit(WorldAccess world, BlockPos pos) {
		int y = pos.getY();
		return y >= world.getBottomY() && y < world.getBottomY() + world.getHeight();
	}
}
