package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.structure.StructureDelta;
import com.terminaldetector.drmd.world.structure.StructureTemplate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enterable flying saucer hull shape — captured once into a {@link StructureTemplate} ({@link #TEMPLATE})
 * and placed/moved by {@code StructureMover}/{@code StructureOccupants} (owned by {@link SkyUfoEntity}).
 *
 * <p>{@code build}/{@code clear}/{@code collectInterior}/{@code shiftEntities}/{@code contains}/
 * {@code inLimit} are gone — replaced by the generic {@code world.structure} infra
 * ({@code StructureMover.place}/{@code clear}/{@code moveTo}, {@code StructureOccupants.collect}/
 * {@code shiftBy}, {@code instance.interior().contains(...)}). {@link #shatter} stays exactly as it was —
 * destruction VFX is unrelated to the movement/placement rewrite.
 */
public final class SkyUfoHull {
	public static final int MAJOR = 11;
	public static final int MINOR = 4;

	/**
	 * Captured once at class-load, immutable, reused by every UFO instance — the whole hull sweep
	 * (~5000 cells) no longer needs to re-run on every materialize/move, unlike the old {@code build()},
	 * which rebuilt from scratch on every single call including every ~0.9s relocation tick.
	 */
	public static final StructureTemplate TEMPLATE = captureTemplate();

	private SkyUfoHull() {}

	private static StructureTemplate captureTemplate() {
		Map<StructureDelta.Cell, BlockState> cells = new HashMap<>();
		Map<String, StructureDelta.Cell> markers = new HashMap<>();

		for (int x = -MAJOR; x <= MAJOR; x++) {
			for (int y = -MINOR; y <= MINOR + 1; y++) {
				for (int z = -MAJOR; z <= MAJOR; z++) {
					BlockState state = stateFor(SkyUfoShape.classify(x, y, z));
					if (state != null) cells.put(new StructureDelta.Cell(x, y, z), state);
				}
			}
		}

		// Widen the interior cavity beyond the ellipsoid formula's own radius, and lay a full deck plate
		// at y=-1 across the same disc. Unconditional, unlike the old build() (which only laid deck plate
		// where the target position's pre-existing terrain happened to already be air): a template has no
		// target position yet to check against, and the diff-based mover needs the same cell set wherever
		// it's ever placed — so "was it air here" can't survive as part of capture. Visible behavior
		// change, named plainly rather than ported silently: the floor is now always solid deck, never a
		// patch of whatever terrain happened to be underneath at first materialize.
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				if (x * x + z * z > 55) continue;
				for (int y = 0; y <= 2; y++) {
					StructureDelta.Cell c = new StructureDelta.Cell(x, y, z);
					if (isCapturedHullMaterial(cells.get(c))) cells.put(c, Blocks.AIR.getDefaultState());
				}
				cells.put(new StructureDelta.Cell(x, -1, z), Blocks.DARK_PRISMARINE.getDefaultState());
			}
		}

		// Side bay door (open toward -Z) — a second, separate opening from the underside bay
		// SkyUfoShape.classify's own bay check already carves.
		for (int x = -2; x <= 2; x++) {
			for (int y = 0; y <= 2; y++) {
				for (int z = -MAJOR; z <= -MAJOR + 2; z++) {
					cells.put(new StructureDelta.Cell(x, y, z), Blocks.AIR.getDefaultState());
				}
			}
		}

		// Reactor core pedestal + ring lights
		cells.put(new StructureDelta.Cell(0, 0, 0), Blocks.OBSIDIAN.getDefaultState());
		StructureDelta.Cell core = new StructureDelta.Cell(0, 1, 0);
		cells.put(core, ModWorldBlocks.UNSTABLE_REACTOR.getDefaultState());
		markers.put("core", core);
		cells.put(new StructureDelta.Cell(0, 2, 0), Blocks.SEA_LANTERN.getDefaultState());
		for (int a = 0; a < 360; a += 45) {
			double rad = Math.toRadians(a);
			cells.put(new StructureDelta.Cell((int) (Math.cos(rad) * 8), 2, (int) (Math.sin(rad) * 8)),
					Blocks.SEA_LANTERN.getDefaultState());
		}

		return new StructureTemplate(cells, markers, -8.5, -1.5, -8.5, 8.5, 3.5, 8.5);
	}

	private static BlockState stateFor(SkyUfoShape.Cell cell) {
		return switch (cell) {
			case OUTER_HULL -> Blocks.OXIDIZED_COPPER.getDefaultState();
			case INNER_SHELL -> Blocks.PRISMARINE_BRICKS.getDefaultState();
			case GLASS -> Blocks.CYAN_STAINED_GLASS.getDefaultState();
			case DECK -> Blocks.DARK_PRISMARINE.getDefaultState();
			case INTERIOR_AIR -> Blocks.AIR.getDefaultState();
			case NONE -> null;
		};
	}

	/**
	 * The three materials the main sweep above can place at y=0..2 — narrower than, and distinct from,
	 * {@link #isHullMaterial}, which also covers {@link #shatter}'s broader damaged/deck cases.
	 */
	private static boolean isCapturedHullMaterial(BlockState st) {
		return st != null && (st.isOf(Blocks.OXIDIZED_COPPER) || st.isOf(Blocks.PRISMARINE_BRICKS)
				|| st.isOf(Blocks.CYAN_STAINED_GLASS));
	}

	/** Shatter hull with leftover debris / fire. Unchanged — destruction VFX, not movement/placement. */
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

	private static boolean isHullMaterial(BlockState st) {
		return st.isOf(Blocks.OXIDIZED_COPPER)
				|| st.isOf(Blocks.PRISMARINE_BRICKS)
				|| st.isOf(Blocks.CYAN_STAINED_GLASS)
				|| st.isOf(Blocks.COPPER_BLOCK)
				|| st.isOf(Blocks.DARK_PRISMARINE);
	}
}
