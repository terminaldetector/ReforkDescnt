package com.terminaldetector.drmd.world.structure;

import com.terminaldetector.drmd.world.structure.StructureDelta.Cell;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure hull face-visibility core: which of a cell's six axis faces actually border empty space, given
 * a caller-supplied "these cells are opaque" set — zero Minecraft dependency, same idiom as
 * {@link StructureDelta}. No notion of {@code BlockState} here at all; the caller decides what counts
 * as solid before calling this, which is what keeps this file (and its test) plain-{@code javac}-
 * compilable rather than needing the game's block registry bootstrapped just to ask a geometry
 * question.
 *
 * <p>This is the standard greedy/culled-mesh trick: a face shared between two solid cells is never
 * seen from outside either one, so skipping it is a real, safe cost reduction for a baked mesh — not
 * an approximation.
 */
public final class StructureFaceCuller {
	/** The six axis-aligned neighbor offsets, in a fixed order so output is deterministic. */
	private static final int[][] NEIGHBOR_OFFSETS = {
			{1, 0, 0}, {-1, 0, 0},
			{0, 1, 0}, {0, -1, 0},
			{0, 0, 1}, {0, 0, -1},
	};

	private StructureFaceCuller() {}

	/**
	 * The offsets (from {@link #NEIGHBOR_OFFSETS}) of {@code cell}'s faces that border a cell not in
	 * {@code solid} — i.e. the faces a baked mesh actually needs to emit a quad for.
	 */
	public static List<int[]> visibleFaceOffsets(Set<Cell> solid, Cell cell) {
		List<int[]> visible = new ArrayList<>(NEIGHBOR_OFFSETS.length);
		for (int[] offset : NEIGHBOR_OFFSETS) {
			Cell neighbor = new Cell(cell.x() + offset[0], cell.y() + offset[1], cell.z() + offset[2]);
			if (!solid.contains(neighbor)) visible.add(offset);
		}
		return visible;
	}
}
